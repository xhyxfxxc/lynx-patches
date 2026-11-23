# Copyright (C) 2026 xhyxfxxc
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Tests for tools/pack_sync.py. Run from the repo root, no network needed:

    python -m unittest tools/test_pack_sync.py -v

Every test builds a throwaway fake upstream repo and a throwaway fake "lynx" repo in a temp dir and runs a copy
of the tool against them. Git is isolated from this machine's own config (no system or global config), so the
results do not depend on whose PC runs them. The tests of `check` never reach GitHub: the tool reads the releases
from a JSON file named by PACK_SYNC_TEST_RELEASES, and the lookup code itself is tested with mocks.
"""

import datetime
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
import urllib.error
from unittest import mock

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.dont_write_bytecode = True      # keep tools/ free of __pycache__ files
import pack_sync  # noqa: E402

TOOL_SOURCE = os.path.join(HERE, "pack_sync.py")
CRLF_BYTES = b"one\r\ntwo\r\nthree\r\n"
BINARY_BYTES = b"\x00\x01\x02\xff\x00binary\x00"
A_LINES = [f"line {n:02d}\n" for n in range(1, 31)]


def lf(text):
    return text.replace("\r\n", "\n")


class Result:
    def __init__(self, proc):
        self.code = proc.returncode
        self.out = proc.stdout.decode("utf-8", "replace")
        self.err = proc.stderr.decode("utf-8", "replace")

    @property
    def text(self):
        return self.out + self.err


class PackTestCase(unittest.TestCase):
    def setUp(self):
        kw = {"ignore_cleanup_errors": True} if sys.version_info >= (3, 10) else {}
        self._tmp = tempfile.TemporaryDirectory(prefix="pack-sync-test-", **kw)
        self.addCleanup(self._tmp.cleanup)
        base = self._tmp.name
        empty = os.path.join(base, "empty.gitconfig")
        open(empty, "w").close()
        self.env = dict(os.environ, GIT_CONFIG_GLOBAL=empty, GIT_CONFIG_NOSYSTEM="1", GIT_TERMINAL_PROMPT="0")
        for key in ("GIT_DIR", "GIT_INDEX_FILE", "GIT_WORK_TREE"):
            self.env.pop(key, None)
        self.upstream = os.path.join(base, "upstream")
        self.lynx = os.path.join(base, "lynx")
        self._init(self.upstream, autocrlf="false")
        self.git(self.upstream, "config", "uploadpack.allowAnySHA1InWant", "true")
        self._init(self.lynx, autocrlf="true")   # the owner's PC runs with autocrlf on, so the tests do too
        self.write(self.lynx, ".gitignore", "*.zip\n**/build/\n")
        os.makedirs(os.path.join(self.lynx, "tools"))
        shutil.copy(TOOL_SOURCE, os.path.join(self.lynx, "tools", "pack_sync.py"))
        self.write_packs({})
        self.git(self.lynx, "add", "-A")
        self.git(self.lynx, "commit", "-q", "-m", "initial")

    # --- helpers

    def _init(self, path, autocrlf):
        os.makedirs(path)
        self.git(path, "init", "-q", "-b", "main")
        self.git(path, "config", "user.name", "Test")
        self.git(path, "config", "user.email", "test@example.invalid")
        self.git(path, "config", "core.autocrlf", autocrlf)
        self.git(path, "config", "commit.gpgsign", "false")

    def git(self, cwd, *args, input=None):
        proc = subprocess.run(["git", *args], cwd=cwd, input=input, env=self.env, capture_output=True)
        if proc.returncode != 0:
            raise AssertionError(f"git {' '.join(args)} failed: {proc.stderr.decode('utf-8', 'replace')}")
        return proc.stdout.decode("utf-8", "replace").rstrip("\r\n")

    def git_rc(self, cwd, *args):
        return subprocess.run(["git", *args], cwd=cwd, env=self.env, capture_output=True).returncode

    @staticmethod
    def write(root, rel, data):
        path = os.path.join(root, *rel.split("/"))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as handle:
            handle.write(data if isinstance(data, bytes) else data.encode("utf-8"))

    @staticmethod
    def read(root, rel):
        with open(os.path.join(root, *rel.split("/")), "rb") as handle:
            return handle.read()

    def exists(self, root, rel):
        return os.path.exists(os.path.join(root, *rel.split("/")))

    def edit(self, root, rel, old, new):
        text = lf(self.read(root, rel).decode("utf-8"))
        self.assertIn(old, text)
        self.write(root, rel, text.replace(old, new))

    def up_commit(self, message, tag=None):
        self.git(self.upstream, "add", "-A")
        self.git(self.upstream, "commit", "-q", "-m", message)
        if tag:
            self.git(self.upstream, "tag", tag)
        return self.git(self.upstream, "rev-parse", "HEAD")

    def seed_upstream(self):
        """Upstream v1: the files the import tests look at."""
        up = self.upstream
        self.write(up, "README.md", "readme v1\n")
        self.write(up, "a.txt", "".join(A_LINES))
        self.write(up, "b.txt", "b v1\n")
        self.write(up, "d.txt", "d goes away\n")
        self.write(up, "crlf.txt", CRLF_BYTES)
        self.write(up, "bin.dat", BINARY_BYTES)
        self.write(up, "x.zip", b"PK-not-really-a-zip")
        self.write(up, "build/keep.txt", "tracked build dir\n")
        self.write(up, "patches-bundle.json", '{"version": "upstream"}\n')
        self.write(up, ".github/workflows/x.yml", "name: x\n")
        self.write(up, "sub/patches-bundle.json", '{"nested": true}\n')
        self.write(up, "keep/one.txt", "one\n")
        self.write(up, "top.txt", "top\n")
        return self.up_commit("v1", "v1")

    def entry(self, **over):
        entry = {
            "title": "Demo (Lynx build)", "app": "com.example.demo", "upstream": self.upstream, "ref": "v1",
            "exclude": ["patches-bundle.json", ".github/**"], "version": "1.0.0-lynx.1",
            "build": {"java": "21", "tasks": ":patches:buildAndroid", "output": "patches/build/libs/patches-*.mpp",
                      "args": ""},
        }
        entry.update(over)
        return entry

    def write_packs(self, packs):
        self.write(self.lynx, "packs/packs.json", json.dumps({"_about": "test", "packs": packs}, indent=2) + "\n")

    def set_pack(self, name="demo", **over):
        """Rewrite packs.json with one entry and commit it, so the tree stays clean."""
        self.write_packs({name: self.entry(**over)})
        self.git(self.lynx, "add", "-A")
        self.git(self.lynx, "commit", "-q", "-m", "test: set pack")

    def update_pack(self, name="demo", **over):
        """Change fields of an existing entry (keeping the recorded commit) and commit packs.json."""
        data = json.loads(self.read(self.lynx, "packs/packs.json").decode("utf-8"))
        data["packs"][name].update(over)
        self.write(self.lynx, "packs/packs.json", json.dumps(data, indent=2) + "\n")
        self.git(self.lynx, "add", "-A")
        self.git(self.lynx, "commit", "-q", "-m", "test: update pack")

    def packs(self):
        return json.loads(self.read(self.lynx, "packs/packs.json").decode("utf-8"))["packs"]

    def tool(self, *args, cwd=None):
        proc = subprocess.run([sys.executable, os.path.join(self.lynx, "tools", "pack_sync.py"), *args],
                              cwd=cwd or self.lynx, env=self.env, capture_output=True)
        return Result(proc)

    def blobs(self, root, rev, prefix=""):
        """{path: blob sha} for a tree, optionally below a prefix (which is stripped)."""
        out = self.git(root, "ls-tree", "-r", rev + (f":{prefix}" if prefix else ""))
        table = {}
        for line in out.splitlines():
            meta, path = line.split("\t", 1)
            table[path] = meta.split()[2]
        return table

    def snapshot_state(self):
        return (self.git(self.lynx, "rev-parse", "HEAD"), self.git(self.lynx, "status", "--porcelain"),
                self.read(self.lynx, "packs/packs.json"))

    def import_demo(self, **over):
        self.seed_upstream()
        self.set_pack(**over)
        result = self.tool("import", "demo")
        self.assertEqual(result.code, 0, result.text)
        return result


class ImportTests(PackTestCase):
    def test_import_is_byte_identical_and_filtered(self):
        v1 = self.seed_upstream()
        self.set_pack()
        result = self.tool("import", "demo")
        self.assertEqual(result.code, 0, result.text)

        upstream = self.blobs(self.upstream, "v1")
        ours = self.blobs(self.lynx, "HEAD", "packs/demo")
        expected = {p: s for p, s in upstream.items() if p not in ("patches-bundle.json", ".github/workflows/x.yml")}
        self.assertEqual(ours, expected)
        self.assertIn("sub/patches-bundle.json", ours)         # only the ROOT bundle is excluded
        self.assertNotIn("patches-bundle.json", ours)
        self.assertNotIn(".github/workflows/x.yml", ours)
        self.assertIn("x.zip", ours)                           # tracked although .gitignore ignores *.zip
        self.assertIn("build/keep.txt", ours)                  # and **/build/
        self.assertEqual(ours["crlf.txt"], upstream["crlf.txt"])
        self.assertEqual(ours["bin.dat"], upstream["bin.dat"])
        for rel in expected:                                    # the working tree has them too
            self.assertTrue(self.exists(self.lynx, "packs/demo/" + rel), rel)

        self.assertEqual(self.packs()["demo"]["commit"], v1)
        subject = self.git(self.lynx, "log", "-1", "--format=%s")
        self.assertEqual(subject, f"chore(pack-demo): import {self.upstream} v1 ({v1[:7]})")
        body = self.git(self.lynx, "log", "-1", "--format=%b")
        self.assertIn(f"{len(expected)} kept, 2 dropped", body)
        self.assertIn("Exclude: patches-bundle.json, .github/**", body)
        self.assertEqual(self.git(self.lynx, "status", "--porcelain"), "")
        self.assertFalse(self.exists(self.lynx, ".git/shallow"))   # the depth-1 fetch left no mark on the repo

    def test_import_refuses_a_dirty_tree(self):
        self.seed_upstream()
        self.set_pack()
        self.write(self.lynx, "junk.txt", "untracked\n")
        before = self.git(self.lynx, "rev-parse", "HEAD")
        result = self.tool("import", "demo")
        self.assertEqual(result.code, 1)
        self.assertIn("not clean", result.err)
        self.assertEqual(self.git(self.lynx, "rev-parse", "HEAD"), before)
        self.assertFalse(self.exists(self.lynx, "packs/demo"))

    def test_import_refuses_an_already_imported_pack(self):
        self.import_demo()
        result = self.tool("import", "demo")
        self.assertEqual(result.code, 1)
        self.assertIn("already exists", result.err)

    def test_import_refuses_an_unknown_pack(self):
        self.seed_upstream()
        self.set_pack()
        result = self.tool("import", "nope")
        self.assertEqual(result.code, 1)
        self.assertIn("unknown pack", result.err)

    def test_import_refuses_when_the_filter_lets_the_root_bundle_through(self):
        self.seed_upstream()
        self.set_pack(exclude=[".github/**"])
        before = self.snapshot_state()
        result = self.tool("import", "demo")
        self.assertEqual(result.code, 1)
        self.assertIn("patches-bundle.json", result.err)
        self.assertEqual(self.snapshot_state(), before)
        self.assertFalse(self.exists(self.lynx, "packs/demo"))

    def test_import_refuses_a_bad_entry(self):
        self.seed_upstream()
        self.set_pack(exclud=["x"])                             # a typo must not silently drop the filter
        result = self.tool("import", "demo")
        self.assertEqual(result.code, 1)
        self.assertIn("unknown key", result.err)

    def test_import_dry_run_changes_nothing(self):
        self.seed_upstream()
        self.set_pack()
        before = self.snapshot_state()
        result = self.tool("import", "demo", "--dry-run")
        self.assertEqual(result.code, 0, result.text)
        self.assertEqual(self.snapshot_state(), before)
        self.assertFalse(self.exists(self.lynx, "packs/demo"))
        self.assertRegex(result.out, r"filtered tree [0-9a-f]{40}")
        self.assertIn("dropped patches-bundle.json", result.out)
        self.assertIn("dropped .github/workflows/x.yml", result.out)
        self.assertEqual(self.git(self.lynx, "ls-files", "packs/demo"), "")

    def test_import_resolves_annotated_tags_branches_and_shas(self):
        v1 = self.seed_upstream()
        self.git(self.upstream, "tag", "-a", "v1-annotated", "-m", "annotated", v1)
        for ref in ("v1-annotated", "main", v1):
            with self.subTest(ref=ref):
                self.set_pack(ref=ref)
                result = self.tool("import", "demo")
                self.assertEqual(result.code, 0, result.text)
                self.assertEqual(self.packs()["demo"]["commit"], v1)         # the commit, not the tag object
                self.git(self.lynx, "reset", "--hard", "-q", "HEAD~1")       # forget the import for the next ref

    def test_include_filter(self):
        self.seed_upstream()
        self.set_pack(include=["keep/**", "top.txt"], exclude=[])
        result = self.tool("import", "demo")
        self.assertEqual(result.code, 0, result.text)
        self.assertEqual(sorted(self.blobs(self.lynx, "HEAD", "packs/demo")), ["keep/one.txt", "top.txt"])


class MatcherTests(unittest.TestCase):
    TABLE = [
        # pattern, path, expected
        ("**", "a", True),
        ("**", "a/b/c.txt", True),
        ("*", "a", True),
        ("*", "a/b", False),
        ("*.md", "README.md", True),
        ("*.md", "docs/README.md", False),
        ("a*b", "aXXb", True),
        ("a*b", "ab", True),
        ("a*b", "a/b", False),
        ("docs/", "docs/a.md", True),
        ("docs/", "docs/x/y.md", True),
        ("docs/", "x/docs/a.md", False),
        ("docs/", "docs", False),
        ("docs", "docs/a.md", False),
        ("docs/**", "docs/a.md", True),
        ("docs/**", "docs/x/y.md", True),
        ("docs/**", "docs", False),
        (".github/**", ".github/workflows/x.yml", True),
        ("**/*.kt", "A.kt", True),
        ("**/*.kt", "a/b/A.kt", True),
        ("**/*.kt", "a/b/A.kts", False),
        ("a/**/b", "a/b", True),
        ("a/**/b", "a/x/b", True),
        ("a/**/b", "a/x/y/b", True),
        ("a/**/b", "a/b/c", False),
        ("a/**/b", "x/a/b", False),
        ("src/*/x.txt", "src/a/x.txt", True),
        ("src/*/x.txt", "src/a/b/x.txt", False),
        ("patches-bundle.json", "patches-bundle.json", True),
        ("patches-bundle.json", "sub/patches-bundle.json", False),
        ("LICENSE", "LICENSE", True),
        ("LICENSE", "LICENSE.md", False),
        ("a.b", "aXb", False),                                  # '.' is a plain character
        ("**/", "a/b", True),
    ]

    def test_pattern_table(self):
        for pattern, path, expected in self.TABLE:
            with self.subTest(pattern=pattern, path=path):
                self.assertEqual(pack_sync.pattern_matches(pattern, path), expected)

    def test_include_and_exclude_combine(self):
        keep = pack_sync.path_kept
        self.assertTrue(keep("a/b.txt", [], []))
        self.assertTrue(keep("a/b.txt", ["a/**"], []))
        self.assertFalse(keep("z.txt", ["a/**"], []))
        self.assertFalse(keep("a/b.txt", ["a/**"], ["a/b.txt"]))
        self.assertTrue(keep("a/c.txt", ["a/**"], ["a/b.txt"]))
        self.assertFalse(keep("patches-bundle.json", [], ["patches-bundle.json"]))
        self.assertTrue(keep("sub/patches-bundle.json", [], ["patches-bundle.json"]))

    def test_pattern_validation(self):
        for bad in ("", "/abs", "a\\b", "a//b", "../x", "./x"):
            with self.subTest(pattern=bad):
                with self.assertRaises(pack_sync.PackError):
                    pack_sync.check_patterns("demo", "exclude", [bad])
        pack_sync.check_patterns("demo", "exclude", ["a/", "**/x", "a/**", "*.md"])


class SyncTests(PackTestCase):
    def local_changes(self):
        """Our own fix to a.txt line 3, our own LYNX.md and our own patches-bundle.json, committed."""
        self.edit(self.lynx, "packs/demo/a.txt", "line 03\n", "line 03 LOCAL FIX\n")
        self.write(self.lynx, "packs/demo/LYNX.md", "Lynx changes\n")
        self.write(self.lynx, "packs/demo/patches-bundle.json", '{"version": "lynx"}\n')
        self.git(self.lynx, "add", "-A")
        self.git(self.lynx, "commit", "-q", "-m", "chore(pack-demo): local fix")

    def upstream_v2(self, a_old="line 25\n", a_new="line 25 UPSTREAM\n"):
        self.edit(self.upstream, "a.txt", a_old, a_new)
        self.write(self.upstream, "b.txt", "b v2\n")
        self.write(self.upstream, "c.txt", "c is new\n")
        self.write(self.upstream, "new.zip", b"PK-added-in-v2")             # ignored by .gitignore, still tracked
        self.write(self.upstream, "lib/build/out.txt", "added in v2\n")
        os.remove(os.path.join(self.upstream, "d.txt"))
        return self.up_commit("v2", "v2")

    def test_sync_clean_merge_keeps_our_changes(self):
        self.import_demo()
        v1 = self.git(self.upstream, "rev-parse", "v1^{commit}")
        self.local_changes()
        v2 = self.upstream_v2()
        head = self.git(self.lynx, "rev-parse", "HEAD")

        result = self.tool("sync", "demo", "v2")
        self.assertEqual(result.code, 0, result.text)

        a = lf(self.read(self.lynx, "packs/demo/a.txt").decode("utf-8"))
        self.assertIn("line 03 LOCAL FIX\n", a)
        self.assertIn("line 25 UPSTREAM\n", a)
        self.assertEqual(lf(self.read(self.lynx, "packs/demo/b.txt").decode()), "b v2\n")
        self.assertTrue(self.exists(self.lynx, "packs/demo/c.txt"))
        self.assertFalse(self.exists(self.lynx, "packs/demo/d.txt"))
        tracked = self.git(self.lynx, "diff", "--cached", "--name-only").splitlines()
        self.assertIn("packs/demo/new.zip", tracked)
        self.assertIn("packs/demo/lib/build/out.txt", tracked)
        self.assertEqual(lf(self.read(self.lynx, "packs/demo/LYNX.md").decode()), "Lynx changes\n")
        self.assertTrue(self.exists(self.lynx, "packs/demo/patches-bundle.json"))

        self.assertEqual(self.git(self.lynx, "rev-parse", "HEAD"), head)            # nothing committed
        self.assertEqual(self.packs()["demo"]["ref"], "v2")
        self.assertEqual(self.packs()["demo"]["commit"], v2)
        staged = self.git(self.lynx, "diff", "--cached", "--name-only").splitlines()
        self.assertIn("packs/packs.json", staged)
        self.assertIn("packs/demo/c.txt", staged)
        self.assertEqual(self.git(self.lynx, "diff", "--name-only"), "")             # all of it staged
        self.assertEqual(self.git(self.lynx, "diff", "--name-only", "--diff-filter=U"), "")

        diff_file = os.path.join(self.lynx, "build", "pack-sync", f"demo-{v1[:7]}..{v2[:7]}.diff")
        self.assertTrue(os.path.isfile(diff_file), result.text)
        with open(diff_file, encoding="utf-8") as handle:
            diff = handle.read()
        self.assertIn("packs/demo/c.txt", diff)
        self.assertIn("packs/demo/d.txt", diff)
        self.assertNotIn("LYNX.md", diff)                                            # the pure upstream diff
        self.assertIn(f'git commit -m "chore(pack-demo): upstream v1 -> v2 ({v2[:7]})"', result.out)

        # Running the printed command finishes the sync; "our changes" are then exactly our own edits.
        self.git(self.lynx, "commit", "-q", "-m", f"chore(pack-demo): upstream v1 -> v2 ({v2[:7]})")
        self.assertEqual(self.git(self.lynx, "status", "--porcelain"), "")
        shown = self.tool("diff", "demo", "--name-only")
        self.assertEqual(sorted(shown.out.split()), ["LYNX.md", "a.txt", "patches-bundle.json"])

    def test_sync_conflict_leaves_markers_and_exit_code_two(self):
        self.import_demo()
        self.local_changes()
        self.upstream_v2(a_old="line 03\n", a_new="line 03 UPSTREAM\n")
        result = self.tool("sync", "demo", "v2")
        self.assertEqual(result.code, 2, result.text)
        conflicted = self.git(self.lynx, "diff", "--name-only", "--diff-filter=U").splitlines()
        self.assertEqual(conflicted, ["packs/demo/a.txt"])
        self.assertIn("<<<<<<<", self.read(self.lynx, "packs/demo/a.txt").decode("utf-8"))
        self.assertIn("packs/demo/a.txt", result.out)
        self.assertIn("CONFLICTS", result.out)
        self.assertEqual(self.packs()["demo"]["ref"], "v2")                          # recorded; the human resolves

    def test_sync_when_already_up_to_date(self):
        self.import_demo()
        self.git(self.upstream, "tag", "v1-again")
        before = self.snapshot_state()
        result = self.tool("sync", "demo", "v1-again")
        self.assertEqual(result.code, 0, result.text)
        self.assertIn("already up to date", result.out)
        self.assertEqual(self.snapshot_state(), before)

    def test_sync_dry_run_changes_nothing(self):
        self.import_demo()
        self.local_changes()
        self.upstream_v2()
        before = self.snapshot_state()
        result = self.tool("sync", "demo", "v2", "--dry-run")
        self.assertEqual(result.code, 0, result.text)
        self.assertEqual(self.snapshot_state(), before)
        self.assertFalse(self.exists(self.lynx, "build/pack-sync"))
        self.assertIn("c.txt", result.out)
        self.assertRegex(result.out, r"\d+ files? changed")

    def test_sync_refuses_a_dirty_tree_and_an_unimported_pack(self):
        self.seed_upstream()
        self.set_pack()
        result = self.tool("sync", "demo", "v1")
        self.assertEqual(result.code, 1)
        self.assertIn("import it first", result.err)
        self.tool("import", "demo")
        self.write(self.lynx, "packs/demo/a.txt", "dirty\n")
        result = self.tool("sync", "demo", "v1")
        self.assertEqual(result.code, 1)
        self.assertIn("not clean", result.err)

    def test_sync_refuses_a_filter_that_lets_the_root_bundle_through(self):
        self.import_demo()
        self.upstream_v2()
        self.update_pack(exclude=[".github/**"])                 # the filter now lets the root bundle through
        before = self.snapshot_state()
        result = self.tool("sync", "demo", "v2")
        self.assertEqual(result.code, 1)
        self.assertIn("patches-bundle.json", result.err)
        self.assertEqual(self.snapshot_state(), before)

    def test_sync_fetches_the_recorded_commit_again_when_it_is_gone(self):
        self.import_demo()
        v1 = self.git(self.upstream, "rev-parse", "v1^{commit}")
        self.upstream_v2()
        os.remove(os.path.join(self.lynx, ".git", "FETCH_HEAD"))
        self.git(self.lynx, "repack", "-a", "-d", "-q")          # fetched objects arrive packed; this drops unreachable ones
        self.git(self.lynx, "prune", "--expire=now")
        self.assertNotEqual(self.git_rc(self.lynx, "cat-file", "-e", v1 + "^{commit}"), 0,
                            "test setup: the upstream commit should be pruned")
        result = self.tool("sync", "demo", "v2")
        self.assertEqual(result.code, 0, result.text)
        self.assertIn("fetching recorded upstream commit", result.out)
        self.assertEqual(lf(self.read(self.lynx, "packs/demo/b.txt").decode()), "b v2\n")


class DiffAndStatusTests(PackTestCase):
    def test_diff_lists_exactly_our_changes(self):
        self.import_demo()
        self.assertEqual(self.tool("diff", "demo", "--name-only").out, "")
        self.edit(self.lynx, "packs/demo/README.md", "readme v1\n", "readme lynx\n")
        self.write(self.lynx, "packs/demo/LYNX.md", "ours\n")
        self.write(self.lynx, "packs/demo/patches-bundle.json", '{"version": "lynx"}\n')
        os.remove(os.path.join(self.lynx, "packs", "demo", "top.txt"))
        self.git(self.lynx, "add", "-A")
        self.git(self.lynx, "commit", "-q", "-m", "chore(pack-demo): ours")
        result = self.tool("diff", "demo", "--name-only")
        self.assertEqual(result.code, 0, result.text)
        self.assertEqual(sorted(result.out.split()), ["LYNX.md", "README.md", "patches-bundle.json", "top.txt"])
        stat = self.tool("diff", "demo", "--stat")
        self.assertEqual(stat.code, 0, stat.text)
        self.assertIn("4 files changed", stat.out)
        patch = self.tool("diff", "demo")
        self.assertIn("+readme lynx", patch.out)

    def test_status_has_one_line_per_pack_and_copes_with_unimported(self):
        self.seed_upstream()
        self.write_packs({"demo": self.entry(), "later": self.entry(ref="v9", title="Later")})
        self.git(self.lynx, "add", "-A")
        self.git(self.lynx, "commit", "-q", "-m", "test: two packs")
        before = self.tool("status")
        self.assertEqual(before.code, 0, before.text)
        lines = before.out.strip().splitlines()
        self.assertEqual(len(lines), 2, before.out)
        self.assertTrue(all("not imported" in line for line in lines), before.out)

        self.assertEqual(self.tool("import", "demo").code, 0)
        self.write(self.lynx, "packs/demo/LYNX.md", "ours\n")
        self.git(self.lynx, "add", "-A")
        self.git(self.lynx, "commit", "-q", "-m", "chore(pack-demo): ours")
        after = self.tool("status")
        self.assertEqual(after.code, 0, after.text)
        lines = after.out.strip().splitlines()
        self.assertEqual(len(lines), 2, after.out)
        demo = next(line for line in lines if line.startswith("demo "))
        later = next(line for line in lines if line.startswith("later "))
        self.assertIn("differs-from-upstream=1 files", demo)
        self.assertIn("at-HEAD=yes", demo)
        self.assertIn("not imported", later)

    def test_status_without_packs_and_from_a_subfolder(self):
        result = self.tool("status", cwd=os.path.join(self.lynx, "tools"))
        self.assertEqual(result.code, 0, result.text)
        self.assertIn("no packs", result.out)


class CheckTests(PackTestCase):
    """`check` against fake upstream releases. The fixture file stands in for GitHub's "latest release" answer."""

    def setUp(self):
        super().setUp()
        self.fixture = os.path.join(self._tmp.name, "releases.json")
        self.releases = {}
        self.env["PACK_SYNC_TEST_RELEASES"] = self.fixture
        self.write_releases()                                      # no release known for any upstream yet

    # --- helpers

    def release(self, tag, hours, upstream=None):
        """GitHub reports `tag` as the newest stable release of an upstream (default: the demo one), `hours` old."""
        moment = datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(hours=hours)
        self.releases[upstream or self.upstream] = {
            "tag_name": tag, "published_at": moment.strftime("%Y-%m-%dT%H:%M:%SZ")}
        self.write_releases()

    def release_fails(self, message, upstream=None):
        self.releases[upstream or self.upstream] = {"error": message}
        self.write_releases()

    def write_releases(self):
        with open(self.fixture, "w", encoding="utf-8") as handle:
            json.dump(self.releases, handle)

    def commit_all(self, message):
        self.git(self.lynx, "add", "-A")
        self.git(self.lynx, "commit", "-q", "-m", message)

    def released_demo(self, **over):
        """An imported pack whose lynx version and published bundle both belong to ref v1: no flag expected."""
        self.import_demo(version="1-lynx.1", **over)
        self.write(self.lynx, "packs/demo/patches-bundle.json", json.dumps({"version": "1-lynx.1"}) + "\n")
        self.commit_all("chore(pack-demo): bundle")

    def add_pack(self, name, **over):
        data = json.loads(self.read(self.lynx, "packs/packs.json").decode("utf-8"))
        data["packs"][name] = self.entry(**over)
        self.write(self.lynx, "packs/packs.json", json.dumps(data, indent=2) + "\n")
        self.commit_all(f"test: add {name}")

    def upstream_release(self, tag, files):
        """A new upstream commit that writes {path: text} and carries the tag."""
        for rel, data in files.items():
            self.write(self.upstream, rel, data)
        return self.up_commit(tag, tag)

    def check(self, *args):
        result = self.tool("check", *args)
        self.assertEqual(result.code, 0, result.text)
        return result

    @staticmethod
    def line(result, name="demo"):
        lines = [line for line in result.out.splitlines() if line.startswith(name + " ")]
        assert len(lines) == 1, result.out
        return lines[0]

    @staticmethod
    def summary(update="-", too_new="-", unreleased="-", in_progress="-", unknown="-"):
        return (f"summary: UPDATE: {update} | TOO-NEW: {too_new} | UNRELEASED: {unreleased} | "
                f"IN-PROGRESS: {in_progress} | UNKNOWN: {unknown}")

    # --- states

    def test_ok_when_the_latest_tag_is_the_recorded_ref(self):
        self.released_demo()
        self.release("v1", hours=15)
        result = self.check()
        line = self.line(result)
        for part in ("ours=v1", "latest=v1 (15 h old)", "lynx=1-lynx.1", "published=1-lynx.1"):
            self.assertIn(part, line)
        self.assertTrue(line.endswith("-> OK"), line)
        self.assertEqual(result.out.splitlines()[-1], self.summary())
        self.assertEqual(len(result.out.splitlines()), 2, result.out)            # one pack line plus the summary

    def test_update_when_a_newer_release_changes_a_file_we_keep(self):
        self.released_demo()
        self.upstream_release("v2", {"b.txt": "b v2\n"})
        self.release("v2", hours=72)
        result = self.check()
        line = self.line(result)
        self.assertIn("latest=v2 (3 d old)", line)
        self.assertTrue(line.endswith("-> UPDATE"), line)
        self.assertEqual(result.out.splitlines()[-1], self.summary(update="demo"))

    def test_too_new_until_the_release_is_old_enough(self):
        self.released_demo()
        self.upstream_release("v2", {"b.txt": "b v2\n"})
        self.release("v2", hours=2)
        young = self.check()
        self.assertIn("latest=v2 (2 h old)", self.line(young))
        self.assertTrue(self.line(young).endswith("-> TOO-NEW (younger than 24 h)"), young.out)
        self.assertEqual(young.out.splitlines()[-1], self.summary(too_new="demo"))
        for limit, state in (("0", "UPDATE"), ("1.5", "UPDATE"), ("3", "TOO-NEW"), ("48", "TOO-NEW")):
            with self.subTest(min_age_hours=limit):
                result = self.check("--min-age-hours", limit)
                self.assertRegex(self.line(result), rf"-> {state}\b")

    def test_ok_same_files_when_only_filtered_files_changed(self):
        self.released_demo()
        self.upstream_release("v2", {".github/workflows/x.yml": "name: changed\n",
                                     "patches-bundle.json": '{"version": "newer upstream"}\n'})
        self.release("v2", hours=120)
        result = self.check()
        self.assertTrue(self.line(result).endswith("-> OK-SAME-FILES"), result.out)
        self.assertEqual(result.out.splitlines()[-1], self.summary())

    def test_older_when_the_latest_release_is_lower_than_ours(self):
        self.released_demo()
        self.release("v0.9", hours=500)
        result = self.check()
        self.assertTrue(self.line(result).endswith("-> OLDER"), result.out)

    def test_unknown_when_the_lookup_fails_and_the_other_packs_still_run(self):
        self.released_demo()
        self.add_pack("other", upstream=self.upstream + "-other", ref="v7", version="7-lynx.1")
        self.release_fails("rate limited, gh not found")
        self.release("v7", hours=5, upstream=self.upstream + "-other")
        result = self.check()
        self.assertIn("latest=?", self.line(result))
        self.assertTrue(self.line(result).endswith("-> UNKNOWN (rate limited, gh not found)"), result.out)
        other = self.line(result, "other")
        self.assertIn("latest=v7 (5 h old)", other)
        self.assertIn("-> OK +UNRELEASED", other)                                # no bundle was ever published
        self.assertEqual(result.out.splitlines()[-1], self.summary(unreleased="other", unknown="demo"))

    def test_unknown_when_there_is_no_stable_release(self):
        self.released_demo()                                       # the fixture knows no release for this upstream
        result = self.check()
        self.assertTrue(self.line(result).endswith("-> UNKNOWN (no stable release)"), result.out)

    def test_unknown_when_a_tag_cannot_be_parsed(self):
        self.released_demo()
        self.release("nightly-1", hours=5)
        result = self.check()
        self.assertIn("latest=nightly-1 (5 h old)", self.line(result))
        self.assertTrue(self.line(result).endswith("-> UNKNOWN (cannot compare v1 with nightly-1)"), result.out)
        self.update_pack(ref="main")                               # now the recorded ref is the odd one
        self.release("v2", hours=5)
        result = self.check()
        self.assertIn("-> UNKNOWN (cannot compare main with v2)", self.line(result))

    def test_unknown_when_the_recorded_commit_is_missing(self):
        self.released_demo()
        data = json.loads(self.read(self.lynx, "packs/packs.json").decode("utf-8"))
        del data["packs"]["demo"]["commit"]
        self.write(self.lynx, "packs/packs.json", json.dumps(data, indent=2) + "\n")
        self.commit_all("test: forget the commit")
        self.upstream_release("v2", {"b.txt": "b v2\n"})
        self.release("v2", hours=72)
        result = self.check()
        self.assertIn("-> UNKNOWN (demo has no recorded commit", self.line(result))

    # --- flags

    def test_unreleased_when_the_version_does_not_belong_to_the_ref(self):
        self.released_demo()
        self.update_pack(version="0.9-lynx.1")
        self.write(self.lynx, "packs/demo/patches-bundle.json", '{"version": "0.9-lynx.1"}\n')
        self.commit_all("chore(pack-demo): bundle 0.9")
        self.release("v1", hours=5)
        result = self.check()
        self.assertTrue(self.line(result).endswith(
            "-> OK +UNRELEASED (version 0.9-lynx.1 does not belong to ref v1)"), result.out)
        self.assertEqual(result.out.splitlines()[-1], self.summary(unreleased="demo"))

    def test_unreleased_when_the_published_bundle_is_missing_unreadable_or_another_version(self):
        self.released_demo()
        self.release("v1", hours=5)
        bundle = "packs/demo/patches-bundle.json"
        cases = [
            ("another version", '{"version": "1-lynx.0"}\n', "bundle version 1-lynx.0 differs from version 1-lynx.1"),
            ("unreadable", "{not json", f"{bundle} is unreadable"),
            ("no version", '{"name": "x"}\n', f"{bundle} has no version"),
            ("missing", None, f"no {bundle}"),
        ]
        for label, content, note in cases:
            with self.subTest(label):
                if content is None:
                    os.remove(os.path.join(self.lynx, *bundle.split("/")))
                else:
                    self.write(self.lynx, bundle, content)
                self.commit_all(f"chore(pack-demo): bundle {label}")
                result = self.check()
                self.assertTrue(self.line(result).endswith(f"-> OK +UNRELEASED ({note})"), result.out)
        self.write(self.lynx, bundle, '{"version": "1-lynx.1"}\n')
        self.commit_all("chore(pack-demo): bundle again")
        self.assertTrue(self.line(self.check()).endswith("-> OK"))

    def test_in_progress_when_the_pack_folder_has_uncommitted_changes(self):
        self.released_demo()
        self.release("v1", hours=5)
        self.assertTrue(self.line(self.check()).endswith("-> OK"))

        self.write(self.lynx, "junk.txt", "outside the pack\n")                  # other changes do not count
        self.write(self.lynx, "tools/notes.txt", "outside the pack\n")
        self.assertTrue(self.line(self.check()).endswith("-> OK"))

        note = "+IN-PROGRESS (uncommitted changes in packs/demo)"
        self.edit(self.lynx, "packs/demo/README.md", "readme v1\n", "readme edited\n")          # unstaged
        result = self.check()
        self.assertTrue(self.line(result).endswith(f"-> OK {note}"), result.out)
        self.assertEqual(result.out.splitlines()[-1], self.summary(in_progress="demo"))
        self.git(self.lynx, "add", "packs/demo/README.md")                                      # staged only
        self.assertTrue(self.line(self.check()).endswith(f"-> OK {note}"))
        self.git(self.lynx, "commit", "-q", "-m", "chore(pack-demo): readme")
        self.assertTrue(self.line(self.check()).endswith("-> OK"))
        self.write(self.lynx, "packs/demo/new-file.txt", "untracked\n")
        self.assertTrue(self.line(self.check()).endswith(f"-> OK {note}"))

    def test_both_flags_come_after_the_state(self):
        self.released_demo()
        self.update_pack(ref="v2", version="1-lynx.1")                 # the real-world case: ref moved, version not
        self.write(self.lynx, "packs/demo/README.md", "edited in the middle of a sync\n")
        self.release("v2", hours=5)
        result = self.check()
        self.assertTrue(self.line(result).endswith(
            "-> OK +UNRELEASED (version 1-lynx.1 does not belong to ref v2) "
            "+IN-PROGRESS (uncommitted changes in packs/demo)"), result.out)
        self.assertEqual(result.out.splitlines()[-1], self.summary(unreleased="demo", in_progress="demo"))

    # --- output, safety

    def test_columns_line_up_and_age_is_hours_then_days(self):
        self.released_demo()
        self.add_pack("a-much-longer-name", upstream=self.upstream + "-other", ref="v7", version="7-lynx.1")
        self.upstream_release("v2", {"b.txt": "b v2\n"})
        self.release("v2", hours=72)
        self.release("v7", hours=47, upstream=self.upstream + "-other")
        result = self.check()
        demo, other = self.line(result), self.line(result, "a-much-longer-name")
        self.assertIn("(3 d old)", demo)
        self.assertIn("(47 h old)", other)
        for marker in ("ours=", "latest=", "lynx=", "published=", "-> "):
            self.assertEqual(demo.index(marker), other.index(marker), marker)
        self.assertEqual(result.out.splitlines()[-1],
                         self.summary(update="demo", unreleased="a-much-longer-name"))

    def test_check_changes_nothing_in_a_dirty_tree(self):
        self.released_demo()
        self.upstream_release("v2", {"b.txt": "b v2\n"})
        self.release("v2", hours=72)
        self.edit(self.lynx, "packs/demo/a.txt", "line 03\n", "line 03 staged edit\n")
        self.git(self.lynx, "add", "packs/demo/a.txt")
        self.edit(self.lynx, "packs/demo/b.txt", "b v1\n", "b unstaged edit\n")
        self.write(self.lynx, "packs/demo/untracked.txt", "new\n")
        self.write(self.lynx, "junk.txt", "outside\n")
        data = json.loads(self.read(self.lynx, "packs/packs.json").decode("utf-8"))
        data["_note"] = "unstaged edit of packs.json"
        self.write(self.lynx, "packs/packs.json", json.dumps(data, indent=2) + "\n")

        def state():
            return {"status": self.git(self.lynx, "status", "--porcelain"),
                    "packs.json": self.read(self.lynx, "packs/packs.json"),
                    "index": self.read(self.lynx, ".git/index"),
                    "staged": self.git(self.lynx, "diff", "--cached"),
                    "head": self.git(self.lynx, "rev-parse", "HEAD"),
                    "refs": self.git(self.lynx, "for-each-ref")}

        before = state()               # the git calls in here may refresh the index file; check itself must not
        result = self.check()
        index_after = self.read(self.lynx, ".git/index")
        self.assertTrue(self.line(result).endswith(
            "-> UPDATE +IN-PROGRESS (uncommitted changes in packs/demo)"), result.out)
        self.assertEqual(index_after, before["index"])
        self.assertEqual(state(), before)
        self.assertFalse(self.exists(self.lynx, ".git/shallow"))
        self.assertFalse(self.exists(self.lynx, "build"))

    def test_check_with_a_broken_packs_json_exits_with_one(self):
        self.write(self.lynx, "packs/packs.json", "{not json")
        result = self.tool("check")
        self.assertEqual(result.code, 1)
        self.assertIn("not valid JSON", result.err)

    def test_check_without_packs(self):
        result = self.check()
        self.assertIn("no packs", result.out)

    def test_check_works_from_a_subfolder_and_rejects_a_bad_age(self):
        self.released_demo()
        self.release("v1", hours=5)
        proc = self.tool("check", cwd=os.path.join(self.lynx, "tools"))
        self.assertEqual(proc.code, 0, proc.text)
        self.assertTrue(self.line(proc).endswith("-> OK"))
        self.assertEqual(self.tool("check", "--min-age-hours", "soon").code, 2)


class LookupTests(unittest.TestCase):
    """latest_release and its helpers without the network: urlopen, gh and PATH are replaced by mocks."""

    class Response:
        def __init__(self, body):
            self.body = body

        def __enter__(self):
            return self

        def __exit__(self, *exc):
            return False

        def read(self):
            return self.body

    GOOD = json.dumps({"tag_name": "v3.2.1", "published_at": "2026-11-20T08:30:00Z"}).encode("utf-8")

    def setUp(self):
        patcher = mock.patch.dict(os.environ)
        patcher.start()
        self.addCleanup(patcher.stop)
        os.environ.pop("PACK_SYNC_TEST_RELEASES", None)

    @staticmethod
    def http_error(code):
        return urllib.error.HTTPError("https://api.github.com/x", code, "msg", {}, io.BytesIO(b""))

    @staticmethod
    def gh_result(code, stdout=b"", stderr=b""):
        return mock.Mock(return_value=subprocess.CompletedProcess([], code, stdout=stdout, stderr=stderr))

    def lookup(self, upstream="https://github.com/owner/repo", urlopen=None, which=None, run=None):
        """latest_release with the network, PATH and gh replaced; an unset mock fails the test when it is used."""
        with mock.patch.object(pack_sync.urllib.request, "urlopen", urlopen or mock.Mock(side_effect=AssertionError)), \
                mock.patch.object(pack_sync.shutil, "which", which or mock.Mock(return_value=None)), \
                mock.patch.object(pack_sync.subprocess, "run", run or mock.Mock(side_effect=AssertionError)):
            return pack_sync.latest_release(upstream)

    def lookup_error(self, **kw):
        with self.assertRaises(pack_sync.PackError) as caught:
            self.lookup(**kw)
        return str(caught.exception)

    def test_a_good_answer_gives_the_tag_and_the_time(self):
        urlopen = mock.Mock(return_value=self.Response(self.GOOD))
        tag, published = self.lookup(urlopen=urlopen)
        self.assertEqual(tag, "v3.2.1")
        self.assertEqual(published, datetime.datetime(2026, 11, 20, 8, 30, tzinfo=datetime.timezone.utc))
        request = urlopen.call_args.args[0]
        self.assertEqual(request.full_url, "https://api.github.com/repos/owner/repo/releases/latest")
        headers = {key.lower(): value for key, value in request.header_items()}
        self.assertEqual(headers, {"accept": "application/vnd.github+json", "user-agent": "pack-sync"})   # no token
        self.assertEqual(urlopen.call_args.kwargs["timeout"], 30)

    def test_an_upstream_outside_github_is_refused(self):
        self.assertEqual(self.lookup_error(upstream="https://gitlab.com/owner/repo"), "upstream is not on GitHub")

    def test_404_means_no_stable_release_and_gh_is_not_asked(self):
        urlopen = mock.Mock(side_effect=self.http_error(404))
        self.assertEqual(self.lookup_error(urlopen=urlopen, which=mock.Mock(return_value="gh")), "no stable release")

    def test_other_http_errors_do_not_try_gh(self):
        urlopen = mock.Mock(side_effect=self.http_error(500))
        self.assertEqual(self.lookup_error(urlopen=urlopen, which=mock.Mock(return_value="gh")),
                         "GitHub answered HTTP 500")

    def test_rate_limit_without_gh(self):
        for code in (403, 429):
            with self.subTest(code=code):
                urlopen = mock.Mock(side_effect=self.http_error(code))
                self.assertEqual(self.lookup_error(urlopen=urlopen), "rate limited, gh not found")

    def test_network_error_without_gh(self):
        urlopen = mock.Mock(side_effect=urllib.error.URLError("no route to host"))
        self.assertEqual(self.lookup_error(urlopen=urlopen), "network error: no route to host, gh not found")
        urlopen = mock.Mock(side_effect=TimeoutError("timed out"))
        self.assertIn("network error", self.lookup_error(urlopen=urlopen))

    def test_rate_limit_falls_back_to_gh_without_passing_a_token(self):
        urlopen = mock.Mock(side_effect=self.http_error(429))
        run = self.gh_result(0, stdout=self.GOOD)
        tag, published = self.lookup(urlopen=urlopen, which=mock.Mock(return_value="C:/gh.exe"), run=run)
        self.assertEqual(tag, "v3.2.1")
        run.assert_called_once()
        self.assertEqual(run.call_args.args[0], ["gh", "api", "repos/owner/repo/releases/latest"])
        self.assertEqual(sorted(run.call_args.kwargs), ["capture_output", "timeout"])       # no env, no token
        self.assertEqual(run.call_args.kwargs["timeout"], 60)

    def test_gh_failures_are_one_line_reasons(self):
        urlopen = mock.Mock(side_effect=self.http_error(403))
        which = mock.Mock(return_value="gh")
        not_found = self.gh_result(1, stderr=b"gh: Not Found (HTTP 404)\n")
        self.assertEqual(self.lookup_error(urlopen=urlopen, which=which, run=not_found), "no stable release")
        failing = self.gh_result(1, stderr=b"gh: bad credentials\nmore\n")
        self.assertEqual(self.lookup_error(urlopen=urlopen, which=which, run=failing),
                         "rate limited, gh failed: gh: bad credentials")
        garbage = self.gh_result(0, stdout=b"<html>")
        self.assertIn("not JSON", self.lookup_error(urlopen=urlopen, which=which, run=garbage))

    def test_an_answer_without_a_usable_tag_or_time_is_an_error(self):
        for body in (b'{"published_at": "2026-11-20T08:30:00Z"}', b'{"tag_name": "v1"}',
                     b'{"tag_name": "v1", "published_at": "yesterday"}', b"[]"):
            with self.subTest(body=body):
                self.assertTrue(self.lookup_error(urlopen=mock.Mock(return_value=self.Response(body))))

    def test_version_parsing(self):
        table = [("v0.24.0", (0, 24, 0)), ("2.10", (2, 10)), ("v1", (1,)), ("1.2.3-rc1", None), ("nightly", None),
                 ("v", None), ("1..2", None), ("", None), ("vv1", None), ("1.2.3.4", (1, 2, 3, 4))]
        for text, expected in table:
            with self.subTest(text=text):
                self.assertEqual(pack_sync.parse_version(text), expected)
        self.assertLess(pack_sync.parse_version("v2.9.0"), pack_sync.parse_version("v2.10.0"))   # numbers, not text

    def test_age_text(self):
        table = [(0, "0 h old"), (0.5, "0 h old"), (15.9, "15 h old"), (47.99, "47 h old"), (48, "2 d old"),
                 (80, "3 d old"), (24 * 12 + 5, "12 d old")]
        for hours, expected in table:
            with self.subTest(hours=hours):
                self.assertEqual(pack_sync.age_text(hours), expected)


if __name__ == "__main__":
    unittest.main()

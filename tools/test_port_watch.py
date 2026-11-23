# Copyright (C) 2026 xhyxfxxc
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Tests for tools/port_watch.py. Run from the repo root, no network needed:

    python -m unittest tools/test_port_watch.py -v

Every test builds a throwaway fake upstream repo and a throwaway fake "lynx" repo in a temp dir and runs a copy of
the tool (plus its sibling pack_sync.py) against them. Git is isolated from this machine's own config. The
releases come from a JSON file named by PACK_SYNC_TEST_RELEASES, so GitHub is never reached.
"""

import copy
import datetime
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.dont_write_bytecode = True      # keep tools/ free of __pycache__ files
import port_watch  # noqa: E402

REPO_ROOT = os.path.dirname(HERE)
A_LINES = [f"a v1 line {n:02d}\n" for n in range(1, 31)]
NOTE = "waits for the owner"
RELEASES_ENV = "PACK_SYNC_TEST_RELEASES"


class Result:
    def __init__(self, proc):
        self.code = proc.returncode
        self.out = proc.stdout.decode("utf-8", "replace")
        self.err = proc.stderr.decode("utf-8", "replace")

    @property
    def text(self):
        return self.out + self.err


class FakeRepo:
    """Just enough of pack_sync.Repo for load_registry."""

    def __init__(self, root):
        self.root = root


class WatchCase(unittest.TestCase):
    """A fake upstream (watched files under watched/ and lists/*.txt, unwatched ones elsewhere) and a fake lynx."""

    def setUp(self):
        kw = {"ignore_cleanup_errors": True} if sys.version_info >= (3, 10) else {}
        self._tmp = tempfile.TemporaryDirectory(prefix="port-watch-test-", **kw)
        self.addCleanup(self._tmp.cleanup)
        base = self._tmp.name
        empty = os.path.join(base, "empty.gitconfig")
        open(empty, "w").close()
        self.env = dict(os.environ, GIT_CONFIG_GLOBAL=empty, GIT_CONFIG_NOSYSTEM="1", GIT_TERMINAL_PROMPT="0")
        for key in ("GIT_DIR", "GIT_INDEX_FILE", "GIT_WORK_TREE"):
            self.env.pop(key, None)
        self.upstream = os.path.join(base, "upstream")
        self.other = self.upstream + "/"        # the same repo under another name: a second upstream for the releases
        self.lynx = os.path.join(base, "lynx")
        self._init(self.upstream)
        self.git(self.upstream, "config", "uploadpack.allowAnySHA1InWant", "true")
        self._init(self.lynx)
        self.write(self.lynx, ".gitignore", "**/build/\n")
        self.write(self.lynx, "ours/code/app.txt", "ours v1\n")
        self.write(self.lynx, "ours/tests/app_test.txt", "ours test v1\n")
        os.makedirs(os.path.join(self.lynx, "tools"))
        for name in ("port_watch.py", "pack_sync.py"):
            shutil.copy(os.path.join(HERE, name), os.path.join(self.lynx, "tools", name))
        self.fixture = os.path.join(base, "releases.json")
        self.releases = {}
        self.env[RELEASES_ENV] = self.fixture
        self.write_releases()
        self.set_registry()

    # --- helpers

    def _init(self, path):
        os.makedirs(path)
        self.git(path, "init", "-q", "-b", "main")
        self.git(path, "config", "user.name", "Test")
        self.git(path, "config", "user.email", "test@example.invalid")
        self.git(path, "config", "core.autocrlf", "false")
        self.git(path, "config", "commit.gpgsign", "false")

    def git(self, cwd, *args):
        proc = subprocess.run(["git", *args], cwd=cwd, env=self.env, capture_output=True)
        if proc.returncode != 0:
            raise AssertionError(f"git {' '.join(args)} failed: {proc.stderr.decode('utf-8', 'replace')}")
        return proc.stdout.decode("utf-8", "replace").rstrip("\r\n")

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

    def commit_all(self, message="test: change"):
        self.git(self.lynx, "add", "-A")
        self.git(self.lynx, "commit", "-q", "-m", message)

    def tool(self, *args, cwd=None):
        proc = subprocess.run([sys.executable, os.path.join(self.lynx, "tools", "port_watch.py"), *args],
                              cwd=cwd or self.lynx, env=self.env, capture_output=True)
        return Result(proc)

    def check(self, *args):
        result = self.tool("check", *args)
        self.assertEqual(result.code, 0, result.text)
        return result

    # --- the fake upstream

    def up_commit(self, tag):
        self.git(self.upstream, "add", "-A")
        self.git(self.upstream, "commit", "-q", "-m", tag)
        self.git(self.upstream, "tag", tag)
        return self.git(self.upstream, "rev-parse", "HEAD")

    def seed_upstream(self):
        """v1.0.0: three watched files, one watched list, and files no pattern selects."""
        up = self.upstream
        self.write(up, "watched/a.txt", "".join(A_LINES))
        self.write(up, "watched/b.txt", "b v1\n")
        self.write(up, "watched/c.txt", "c v1\n")
        self.write(up, "lists/one.txt", "one v1\n")
        self.write(up, "lists/skip.dat", "no pattern selects this\n")
        self.write(up, "docs/readme.md", "readme v1\n")
        return self.up_commit("v1.0.0")

    def upstream_release(self, tag, files=None, delete=()):
        """A new upstream commit that writes {path: text}, removes the paths in delete, and carries the tag."""
        for rel, data in (files or {}).items():
            self.write(self.upstream, rel, data)
        for rel in delete:
            os.remove(os.path.join(self.upstream, *rel.split("/")))
        return self.up_commit(tag)

    def standard_upstream(self):
        self.seed_upstream()
        self.later_releases()

    def later_releases(self):
        """v1.1.0 (unwatched files only), then v1.2.0 (watched: one modified, one added, one deleted)."""
        self.upstream_release("v1.1.0", {"docs/readme.md": "readme v2\n", "lists/skip.dat": "changed\n"})
        self.upstream_release("v1.2.0", {"watched/a.txt": "".join(A_LINES).replace("line 05", "line 05 changed"),
                                         "watched/new.txt": "new 1\nnew 2\n"}, delete=["watched/c.txt"])

    # --- releases and the registry

    def write_releases(self):
        with open(self.fixture, "w", encoding="utf-8") as handle:
            json.dump(self.releases, handle)

    def release(self, tag, hours, upstream=None):
        """GitHub reports `tag` as the newest stable release of an upstream (default: the fake one), `hours` old."""
        moment = datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(hours=hours)
        self.releases[upstream or self.upstream] = {
            "tag_name": tag, "published_at": moment.strftime("%Y-%m-%dT%H:%M:%SZ")}
        self.write_releases()

    def release_fails(self, message, upstream=None):
        self.releases[upstream or self.upstream] = {"error": message}
        self.write_releases()

    def source(self, **over):
        entry = {"upstream": self.upstream, "reviewed": "v1.0.0", "paths": ["watched/**", "lists/*.txt"]}
        entry.update(over)
        return entry

    def port(self, sources=None, **over):
        entry = {"title": "Demo", "ours": ["ours/code"], "sources": sources or {"main": self.source()}}
        entry.update(over)
        return entry

    def set_registry(self, ports=None):
        """Write tools/port_watch.json (default: port demo with source main) and commit it."""
        self.write(self.lynx, "tools/port_watch.json",
                   json.dumps({"ports": ports if ports is not None else {"demo": self.port()}}, indent=2) + "\n")
        self.git(self.lynx, "add", "-A")
        self.git(self.lynx, "commit", "-q", "-m", "test: registry", "--allow-empty")

    # --- reading the output

    @staticmethod
    def line(result, name="demo/main"):
        lines = [line for line in result.out.splitlines() if line.startswith(name + " ")]
        assert len(lines) == 1, result.out
        return lines[0]

    @staticmethod
    def summary(update="-", too_new="-", parked="-", in_progress="-", unknown="-"):
        return (f"summary: UPDATE: {update} | TOO-NEW: {too_new} | PARKED: {parked} | "
                f"IN-PROGRESS: {in_progress} | UNKNOWN: {unknown}")


class StateTests(WatchCase):
    def test_ok_when_the_latest_tag_is_the_reviewed_tag(self):
        self.seed_upstream()
        self.release("v1.0.0", hours=15)
        result = self.check()
        line = self.line(result)
        for part in ("reviewed=v1.0.0", "latest=v1.0.0 (15 h old)"):
            self.assertIn(part, line)
        self.assertTrue(line.endswith("-> OK"), line)
        self.assertEqual(result.out.splitlines()[-1], self.summary())
        self.assertEqual(len(result.out.splitlines()), 2, result.out)

    def test_ok_same_files_when_only_unwatched_files_changed(self):
        self.standard_upstream()
        self.release("v1.1.0", hours=120)
        result = self.check()
        self.assertTrue(self.line(result).endswith("-> OK-SAME-FILES"), result.out)
        self.assertEqual(len(result.out.splitlines()), 2, result.out)
        self.assertEqual(result.out.splitlines()[-1], self.summary())

    def test_update_lists_the_added_modified_and_deleted_files(self):
        self.standard_upstream()
        self.release("v1.2.0", hours=72)
        result = self.check()
        lines = result.out.splitlines()
        self.assertIn("latest=v1.2.0 (3 d old)", lines[0])
        self.assertTrue(lines[0].endswith("-> UPDATE (3 watched file(s) changed)"), lines[0])
        self.assertEqual(lines[1:4], ["    M watched/a.txt", "    D watched/c.txt", "    A watched/new.txt"])
        self.assertEqual(lines[-1], self.summary(update="demo/main"))
        self.assertEqual(len(lines), 5, result.out)

    def test_too_new_until_the_release_is_old_enough(self):
        self.standard_upstream()
        self.release("v1.2.0", hours=2)
        young = self.check()
        self.assertIn("latest=v1.2.0 (2 h old)", self.line(young))
        self.assertTrue(self.line(young).endswith("-> TOO-NEW (younger than 24 h)"), young.out)
        self.assertIn("    M watched/a.txt", young.out.splitlines())
        self.assertEqual(young.out.splitlines()[-1], self.summary(too_new="demo/main"))
        for limit, state in (("0", "UPDATE"), ("1.5", "UPDATE"), ("3", "TOO-NEW"), ("48", "TOO-NEW")):
            with self.subTest(min_age_hours=limit):
                self.assertRegex(self.line(self.check("--min-age-hours", limit)), rf"-> {state}\b")

    def test_parked_when_the_parked_release_is_the_newest(self):
        self.standard_upstream()
        parked = {"ref": "v1.2.0", "date": "2026-11-23", "note": NOTE}
        self.set_registry({"demo": self.port({"main": self.source(parked=parked)})})
        for hours in (72, 2):                                    # parked wins over TOO-NEW
            with self.subTest(hours=hours):
                self.release("v1.2.0", hours=hours)
                result = self.check()
                lines = result.out.splitlines()
                self.assertTrue(lines[0].endswith(f"-> PARKED ({NOTE})"), lines[0])
                self.assertEqual(lines[1:4], ["    M watched/a.txt", "    D watched/c.txt", "    A watched/new.txt"])
                self.assertEqual(lines[-1], self.summary(parked="demo/main"))

    def test_update_carries_a_flag_when_an_older_release_is_parked(self):
        self.standard_upstream()
        parked = {"ref": "v1.1.0", "date": "2026-11-20", "note": NOTE}
        self.set_registry({"demo": self.port({"main": self.source(parked=parked)})})
        self.release("v1.2.0", hours=72)
        result = self.check()
        self.assertTrue(self.line(result).endswith(
            f"-> UPDATE (3 watched file(s) changed) +PARKED-OLDER (v1.1.0: {NOTE})"), result.out)
        self.assertEqual(result.out.splitlines()[-1], self.summary(update="demo/main"))
        self.release("v1.2.0", hours=2)                          # only an UPDATE carries the flag
        self.assertTrue(self.line(self.check()).endswith("-> TOO-NEW (younger than 24 h)"))

    def test_older_when_the_latest_release_is_lower_than_reviewed(self):
        self.seed_upstream()
        self.release("v0.9", hours=500)
        self.assertTrue(self.line(self.check()).endswith("-> OLDER"))

    def test_unknown_when_a_tag_cannot_be_compared(self):
        self.standard_upstream()
        self.release("nightly-1", hours=5)
        result = self.check()
        self.assertIn("latest=nightly-1 (5 h old)", self.line(result))
        self.assertTrue(self.line(result).endswith("-> UNKNOWN (cannot compare v1.0.0 with nightly-1)"), result.out)
        self.assertEqual(result.out.splitlines()[-1], self.summary(unknown="demo/main"))
        self.set_registry({"demo": self.port({"main": self.source(reviewed="main")})})
        self.release("v1.2.0", hours=5)
        self.assertIn("-> UNKNOWN (cannot compare main with v1.2.0)", self.line(self.check()))

    def test_unknown_when_the_release_lookup_fails(self):
        self.seed_upstream()
        self.release_fails("rate limited, gh not found")
        line = self.line(self.check())
        self.assertIn("latest=-", line)
        self.assertTrue(line.endswith("-> UNKNOWN (rate limited, gh not found)"), line)
        del self.releases[self.upstream]
        self.write_releases()
        self.assertTrue(self.line(self.check()).endswith("-> UNKNOWN (no stable release)"))

    def test_unknown_when_a_tag_does_not_exist_upstream(self):
        self.standard_upstream()
        self.release("v9.0.0", hours=5)                           # GitHub names a release the repo has no tag for
        line = self.line(self.check())
        self.assertIn("latest=v9.0.0 (5 h old)", line)
        self.assertIn("-> UNKNOWN (git fetch", line)
        self.set_registry({"demo": self.port({"main": self.source(reviewed="v0.5.0")})})       # reviewed tag is gone
        self.release("v1.2.0", hours=5)
        self.assertIn("-> UNKNOWN (git fetch", self.line(self.check()))

    def test_an_unexpected_error_is_a_one_line_unknown(self):
        row_source = {"upstream": "u", "reviewed": "v1", "paths": ["x"]}
        with mock.patch.object(port_watch.ps, "latest_release", side_effect=RuntimeError("boom\nsecond line")):
            row = port_watch.check_one(None, "demo", "main", row_source, 24.0, None)
        self.assertEqual((row.state, row.note), ("UNKNOWN", "unexpected RuntimeError: boom"))

    def test_one_failing_source_does_not_stop_the_others(self):
        self.standard_upstream()
        sources = {"broken": self.source(upstream=self.other), "fine": self.source()}
        self.set_registry({"demo": self.port(sources), "second": self.port({"main": self.source()})})
        self.release_fails("network error: no route", upstream=self.other)
        self.release("v1.2.0", hours=72)
        result = self.check()
        self.assertTrue(self.line(result, "demo/broken").endswith("-> UNKNOWN (network error: no route)"), result.out)
        self.assertTrue(self.line(result, "demo/fine").endswith("-> UPDATE (3 watched file(s) changed)"), result.out)
        self.assertTrue(self.line(result, "second/main").endswith("-> UPDATE (3 watched file(s) changed)"))
        self.assertEqual(result.out.splitlines()[-1],
                         self.summary(update="demo/fine, second/main", unknown="demo/broken"))

    def test_more_than_twenty_changed_files_are_cut(self):
        self.seed_upstream()
        self.upstream_release("v1.1.0", {f"watched/many/f{n:02d}.txt": f"{n}\n" for n in range(25)})
        self.release("v1.1.0", hours=72)
        result = self.check()
        self.assertTrue(self.line(result).endswith("-> UPDATE (25 watched file(s) changed)"), result.out)
        shown = [line for line in result.out.splitlines() if line.startswith("    A ")]
        self.assertEqual(shown, [f"    A watched/many/f{n:02d}.txt" for n in range(20)])
        self.assertIn("    ... and 5 more", result.out.splitlines())

    def test_columns_line_up(self):
        self.standard_upstream()
        sources = {"main": self.source(), "a-much-longer-name": self.source(upstream=self.other, reviewed="v1.2.0")}
        self.set_registry({"demo": self.port(sources)})
        self.release("v1.2.0", hours=72)
        self.release("v1.2.0", hours=47, upstream=self.other)
        result = self.check()
        first, second = self.line(result), self.line(result, "demo/a-much-longer-name")
        self.assertIn("(3 d old)", first)
        self.assertIn("(47 h old)", second)
        for marker in ("reviewed=", "latest=", "-> "):
            self.assertEqual(first.index(marker), second.index(marker), marker)

    def test_odd_characters_in_upstream_paths_print_as_question_marks(self):
        self.seed_upstream()
        wanted = {"watched/caf\u00e9.txt": "watched/caf?.txt", "watched/ctl\x01name.txt": "watched/ctl?name.txt"}
        made = {}
        for name, shown in wanted.items():
            try:
                self.write(self.upstream, name, "x\n")
            except (OSError, ValueError):
                continue                                          # this file system does not take the name
            made[name] = shown
        if not made:
            self.skipTest("the file system accepts none of the odd file names")
        self.upstream_release("v1.1.0")
        self.release("v1.1.0", hours=72)
        result = self.check()
        lines = result.out.splitlines()
        for shown in made.values():
            self.assertIn(f"    A {shown}", lines)
        self.assertIsNone(re.search(r"[^\x20-\x7e\r\n]", result.out), result.out)


class InProgressTests(WatchCase):
    def setUp(self):
        super().setUp()
        self.seed_upstream()
        self.release("v1.0.0", hours=5)
        ports = {"demo": self.port({"main": self.source(), "second": self.source()},
                                   ours=["ours/code", "ours/tests"])}
        self.set_registry(ports)

    def test_flag_names_the_changed_folder_and_goes_on_every_row_of_the_port(self):
        self.assertTrue(self.line(self.check()).endswith("-> OK"))
        self.write(self.lynx, "junk.txt", "outside the port\n")                      # other changes do not count
        self.write(self.lynx, "tools/notes.txt", "outside the port\n")
        self.assertTrue(self.line(self.check()).endswith("-> OK"))
        note = "+IN-PROGRESS (uncommitted changes in ours/tests)"
        self.write(self.lynx, "ours/tests/app_test.txt", "edited\n")                 # unstaged
        result = self.check()
        for name in ("demo/main", "demo/second"):
            self.assertTrue(self.line(result, name).endswith(f"-> OK {note}"), result.out)
        self.assertEqual(result.out.splitlines()[-1], self.summary(in_progress="demo/main, demo/second"))
        self.git(self.lynx, "add", "ours/tests/app_test.txt")                        # staged only
        self.assertTrue(self.line(self.check()).endswith(f"-> OK {note}"))
        self.git(self.lynx, "commit", "-q", "-m", "test: tests")
        self.assertTrue(self.line(self.check()).endswith("-> OK"))
        self.write(self.lynx, "ours/code/new/deep/file.txt", "untracked\n")          # untracked, in a new folder
        self.assertTrue(self.line(self.check()).endswith("-> OK +IN-PROGRESS (uncommitted changes in ours/code)"))
        self.write(self.lynx, "ours/tests/more.txt", "untracked\n")
        self.assertTrue(self.line(self.check()).endswith(
            "-> OK +IN-PROGRESS (uncommitted changes in ours/code, ours/tests)"))

    def test_flag_comes_after_the_state_flags(self):
        self.later_releases()
        parked = {"ref": "v1.1.0", "date": "2026-11-20", "note": NOTE}
        self.set_registry({"demo": self.port({"main": self.source(parked=parked)})})
        self.release("v1.2.0", hours=72)
        self.write(self.lynx, "ours/code/app.txt", "edited\n")
        result = self.check()
        self.assertTrue(self.line(result).endswith(
            f"-> UPDATE (3 watched file(s) changed) +PARKED-OLDER (v1.1.0: {NOTE}) "
            "+IN-PROGRESS (uncommitted changes in ours/code)"), result.out)


class SafetyTests(WatchCase):
    def state(self):
        return {"status": self.git(self.lynx, "status", "--porcelain"),
                "registry": self.read(self.lynx, "tools/port_watch.json"),
                "index": self.read(self.lynx, ".git/index"),
                "staged": self.git(self.lynx, "diff", "--cached"),
                "head": self.git(self.lynx, "rev-parse", "HEAD"),
                "refs": self.git(self.lynx, "for-each-ref")}

    def test_check_changes_nothing_in_a_dirty_tree_and_leaves_no_shallow_file(self):
        self.standard_upstream()
        self.release("v1.2.0", hours=72)
        self.write(self.lynx, "ours/code/app.txt", "staged edit\n")
        self.git(self.lynx, "add", "ours/code/app.txt")
        self.write(self.lynx, "ours/tests/app_test.txt", "unstaged edit\n")
        self.write(self.lynx, "ours/code/untracked.txt", "new\n")
        self.write(self.lynx, "junk.txt", "outside\n")
        before = self.state()           # the git calls in here may refresh the index file; check itself must not
        result = self.check()
        self.assertIn("-> UPDATE (3 watched file(s) changed)", self.line(result))
        self.assertIn("+IN-PROGRESS", self.line(result))
        self.assertEqual(self.state(), before)
        self.assertFalse(self.exists(self.lynx, ".git/shallow"))
        self.assertFalse(self.exists(self.lynx, "build"))

    def test_check_keeps_a_shallow_file_that_was_there(self):
        self.standard_upstream()
        self.release("v1.2.0", hours=72)
        head = self.git(self.lynx, "rev-parse", "HEAD")
        self.write(self.lynx, ".git/shallow", head + "\n")
        self.check()
        self.assertEqual(self.read(self.lynx, ".git/shallow"), (head + "\n").encode())


class RegistryTests(WatchCase):
    def good(self):
        return {"ports": {"demo": self.port({"main": self.source(
            parked={"ref": "v1.2.0", "date": "2026-11-23", "note": NOTE})})}}

    def problem(self, data, root=None):
        """The error text load_registry gives for a registry (a dict or raw text), or None when it loads."""
        root = root or self._tmp.name
        os.makedirs(os.path.join(root, "tools"), exist_ok=True)
        with open(os.path.join(root, "tools", "port_watch.json"), "w", encoding="utf-8") as handle:
            handle.write(data if isinstance(data, str) else json.dumps(data))
        try:
            port_watch.load_registry(FakeRepo(root))
        except port_watch.ps.PackError as exc:
            return str(exc)
        return None

    def test_the_shipped_registry_is_valid(self):
        self.assertIsNone(self.problem(self.read(REPO_ROOT, "tools/port_watch.json").decode("utf-8"),
                                       root=self.lynx))

    def test_a_good_registry_loads_and_an_empty_one_too(self):
        self.assertIsNone(self.problem(self.good()))
        self.assertIsNone(self.problem({"ports": {}}))
        data = self.good()
        data["ports"]["demo"]["sources"]["main"]["parked"]["note"] = "x" * 200
        self.assertIsNone(self.problem(data))

    def test_every_mistake_is_one_line_naming_the_registry_and_the_problem(self):
        def port(data):
            return data["ports"]["demo"]

        def source(data):
            return data["ports"]["demo"]["sources"]["main"]

        def rename(data, old, new):
            data["ports"][new] = data["ports"].pop(old)

        cases = [
            ("unknown top-level key", lambda d: d.update(extra=1), "unknown key 'extra'"),
            ("top level is a list", None, "top level must be an object"),
            ("ports is a list", lambda d: d.update(ports=[]), "ports must be an object"),
            ("bad port name", lambda d: rename(d, "demo", "Bad_Name"), "port name 'Bad_Name'"),
            ("bad source name", lambda d: port(d)["sources"].update({"Main": port(d)["sources"].pop("main")}),
             "source name 'Main'"),
            ("unknown port key", lambda d: port(d).update(titel="x"), "unknown key 'titel'"),
            ("unknown source key", lambda d: source(d).update(path=[]), "unknown key 'path'"),
            ("empty title", lambda d: port(d).update(title=" "), "demo.title must be non-empty text"),
            ("ours empty", lambda d: port(d).update(ours=[]), "demo.ours must be a non-empty list"),
            ("ours not a list", lambda d: port(d).update(ours="ours/code"), "demo.ours must be a non-empty list"),
            ("ours with ..", lambda d: port(d).update(ours=["ours/../x"]), "demo.ours holds 'ours/../x'"),
            ("ours with a dot", lambda d: port(d).update(ours=["./ours"]), "demo.ours holds"),
            ("ours with a leading slash", lambda d: port(d).update(ours=["/ours"]), "demo.ours holds"),
            ("ours with a trailing slash", lambda d: port(d).update(ours=["ours/"]), "demo.ours holds"),
            ("ours with a backslash", lambda d: port(d).update(ours=["ours\\code"]), "demo.ours holds"),
            ("no sources", lambda d: port(d).update(sources={}), "demo.sources must be a non-empty object"),
            ("no upstream", lambda d: source(d).pop("upstream"), "demo/main.upstream must be non-empty text"),
            ("empty reviewed", lambda d: source(d).update(reviewed=""), "demo/main.reviewed must be non-empty text"),
            ("empty paths", lambda d: source(d).update(paths=[]), "demo/main.paths must not be empty"),
            ("paths not a list", lambda d: source(d).update(paths="watched/**"), "demo/main.paths must be a list"),
            ("pattern with a backslash", lambda d: source(d).update(paths=["watched\\x"]), "demo/main.paths pattern"),
            ("pattern with a leading slash", lambda d: source(d).update(paths=["/watched"]), "demo/main.paths pattern"),
            ("pattern with ..", lambda d: source(d).update(paths=["a/../b"]), "demo/main.paths pattern"),
            ("empty pattern", lambda d: source(d).update(paths=[""]), "demo/main.paths holds an empty"),
            ("parked is not an object", lambda d: source(d).update(parked="v1"), "demo/main.parked must be an object"),
            ("parked unknown key", lambda d: source(d)["parked"].update(why="x"), "unknown key 'why'"),
            ("parked without ref", lambda d: source(d)["parked"].pop("ref"), "demo/main.parked has no ref"),
            ("parked without note", lambda d: source(d)["parked"].pop("note"), "demo/main.parked has no note"),
            ("parked empty ref", lambda d: source(d)["parked"].update(ref=""), "demo/main.parked.ref must be"),
            ("parked wrong date format", lambda d: source(d)["parked"].update(date="23.11.2026"),
             "demo/main.parked.date must be a date"),
            ("parked impossible date", lambda d: source(d)["parked"].update(date="2026-13-40"),
             "demo/main.parked.date must be a date"),
            ("parked date not text", lambda d: source(d)["parked"].update(date=20261123),
             "demo/main.parked.date must be a date"),
            ("parked note too long", lambda d: source(d)["parked"].update(note="x" * 201),
             "demo/main.parked.note must be text of at most 200"),
            ("parked note not text", lambda d: source(d)["parked"].update(note=5),
             "demo/main.parked.note must be text"),
        ]
        for label, edit, expected in cases:
            with self.subTest(label):
                data = self.good()
                if edit is None:
                    data = []
                else:
                    edit(data)
                message = self.problem(data)
                self.assertIsNotNone(message, "the registry was accepted")
                self.assertTrue(message.startswith("tools/port_watch.json"), message)
                self.assertIn(expected, message)
                self.assertNotIn("packs.json", message)
                self.assertEqual(len(message.splitlines()), 1, message)

    def test_missing_and_unreadable_registries(self):
        self.assertIn("not valid JSON", self.problem("{not json"))
        self.assertIn("not valid JSON", self.problem(""))
        os.remove(os.path.join(self._tmp.name, "tools", "port_watch.json"))
        with self.assertRaisesRegex(port_watch.ps.PackError, "tools/port_watch.json not found"):
            port_watch.load_registry(FakeRepo(self._tmp.name))

    def test_a_broken_registry_is_exit_code_one_with_one_error_line(self):
        cases = [("missing", None, "not found"), ("not json", "{not json", "not valid JSON"),
                 ("unknown key", json.dumps({"ports": {}, "extra": 1}), "unknown key 'extra'")]
        for label, content, expected in cases:
            with self.subTest(label):
                path = os.path.join(self.lynx, "tools", "port_watch.json")
                if content is None:
                    os.remove(path)
                else:
                    self.write(self.lynx, "tools/port_watch.json", content)
                for args in (("check",), ("diff", "demo", "main")):
                    result = self.tool(*args)
                    self.assertEqual(result.code, 1, result.text)
                    self.assertEqual(result.out, "")
                    self.assertTrue(result.err.startswith("error: tools/port_watch.json"), result.err)
                    self.assertIn(expected, result.err)
                    self.assertEqual(len(result.err.splitlines()), 1, result.err)

    def test_no_ports(self):
        self.set_registry({})
        result = self.check()
        self.assertEqual(result.out.strip(), "no ports in tools/port_watch.json")

    def test_check_works_from_a_subfolder_and_rejects_a_bad_age(self):
        self.seed_upstream()
        self.release("v1.0.0", hours=5)
        result = self.tool("check", cwd=os.path.join(self.lynx, "tools"))
        self.assertEqual(result.code, 0, result.text)
        self.assertTrue(self.line(result).endswith("-> OK"))
        self.assertEqual(self.tool("check", "--min-age-hours", "soon").code, 2)


class DiffTests(WatchCase):
    DIFF = "build/port-watch/demo-main-v1.0.0_to_v1.2.0.diff"

    def state(self):
        return (self.git(self.lynx, "status", "--porcelain"), self.read(self.lynx, ".git/index"),
                self.git(self.lynx, "rev-parse", "HEAD"))

    def test_diff_writes_only_the_watched_changes_and_prints_the_totals(self):
        self.standard_upstream()
        before = self.state()
        result = self.tool("diff", "demo", "main", "--to", "v1.2.0")
        self.assertEqual(result.code, 0, result.text)
        self.assertEqual(result.out.splitlines(), [
            "+1 -1 M watched/a.txt", "+0 -1 D watched/c.txt", "+2 -0 A watched/new.txt",
            "total: 3 file(s), +3 -2", f"diff saved to {self.DIFF}"])
        patch = self.read(self.lynx, self.DIFF)
        for rel in ("watched/a.txt", "watched/c.txt", "watched/new.txt"):
            self.assertIn(f"diff --git a/{rel} b/{rel}".encode(), patch)
        for rel in ("docs/readme.md", "lists/skip.dat"):
            self.assertNotIn(rel.encode(), patch)
        expected = self.git(self.upstream, "diff", "--no-color", "--no-renames", "v1.0.0", "v1.2.0", "--", "watched")
        self.assertEqual(patch.decode("utf-8").replace("\r\n", "\n").rstrip("\n"), expected.replace("\r\n", "\n"))
        self.assertEqual(self.state(), before)                    # build/ is ignored, nothing else moved
        self.assertFalse(self.exists(self.lynx, ".git/shallow"))

    def test_from_and_to_are_honoured_and_the_defaults_are_reviewed_and_latest(self):
        self.standard_upstream()
        self.upstream_release("v1.3.0", {"watched/b.txt": "b v3\n"})
        self.release("v1.3.0", hours=72)
        result = self.tool("diff", "demo", "main", "--from", "v1.2.0", "--to", "v1.3.0")
        self.assertEqual(result.code, 0, result.text)
        self.assertEqual(result.out.splitlines()[:2], ["+1 -1 M watched/b.txt", "total: 1 file(s), +1 -1"])
        patch = self.read(self.lynx, "build/port-watch/demo-main-v1.2.0_to_v1.3.0.diff").decode("utf-8")
        self.assertIn("watched/b.txt", patch)
        self.assertNotIn("watched/a.txt", patch)
        result = self.tool("diff", "demo", "main")                # reviewed v1.0.0 to the release the fixture names
        self.assertEqual(result.code, 0, result.text)
        self.assertEqual(result.out.splitlines()[-2], "total: 4 file(s), +4 -3")
        self.assertTrue(self.exists(self.lynx, "build/port-watch/demo-main-v1.0.0_to_v1.3.0.diff"))

    def test_a_full_ref_or_a_sha_is_fetched_as_given_and_the_file_name_is_made_safe(self):
        self.standard_upstream()
        sha = self.git(self.upstream, "rev-parse", "v1.2.0")
        for target, name in ((sha, f"demo-main-v1.0.0_to_{sha}.diff"),
                             ("refs/tags/v1.2.0", "demo-main-v1.0.0_to_refs_tags_v1.2.0.diff")):
            with self.subTest(target):
                result = self.tool("diff", "demo", "main", "--to", target)
                self.assertEqual(result.code, 0, result.text)
                self.assertIn("total: 3 file(s), +3 -2", result.out)
                self.assertTrue(self.exists(self.lynx, f"build/port-watch/{name}"), result.out)

    def test_no_watched_difference_prints_a_message_and_writes_nothing(self):
        self.standard_upstream()
        result = self.tool("diff", "demo", "main", "--to", "v1.1.0")
        self.assertEqual(result.code, 0, result.text)
        self.assertEqual(result.out.strip(), "no watched file differs between v1.0.0 and v1.1.0")
        self.assertFalse(self.exists(self.lynx, "build"))

    def test_unknown_port_or_source_and_failed_fetch_are_exit_code_one(self):
        self.standard_upstream()
        cases = [(("nope", "main"), "unknown port 'nope'"), (("demo", "nope"), "unknown source 'nope'"),
                 (("demo", "main", "--to", "v9.9.9"), "error: git fetch"),
                 (("demo", "main", "--from", "v0.0.1", "--to", "v1.2.0"), "error: git fetch")]
        for args, expected in cases:
            with self.subTest(args):
                result = self.tool("diff", *args)
                self.assertEqual(result.code, 1, result.text)
                self.assertIn(expected, result.err)
                self.assertFalse(self.exists(self.lynx, "build"))

    def test_refuses_when_build_is_not_git_ignored(self):
        self.standard_upstream()
        self.write(self.lynx, ".gitignore", "*.zip\n")
        self.commit_all("test: no ignore for build")
        before = self.state()
        result = self.tool("diff", "demo", "main", "--to", "v1.2.0")
        self.assertEqual(result.code, 1, result.text)
        self.assertIn("not git-ignored", result.err)
        self.assertFalse(self.exists(self.lynx, "build"))
        self.assertEqual(self.state(), before)

    def test_refuses_a_watched_path_that_is_not_safe(self):
        self.seed_upstream()
        proc = subprocess.run(["git", "hash-object", "-w", "--stdin"], cwd=self.upstream, env=self.env,
                              input=b"x\n", capture_output=True)
        oid = proc.stdout.decode().strip()
        try:                  # a backslash cannot be a file name on Windows, so the entry goes in with plumbing
            self.git(self.upstream, "-c", "core.protectNTFS=false", "update-index", "--add", "--cacheinfo",
                     f"100644,{oid},watched/back\\slash.txt")
        except AssertionError:
            self.skipTest("this git refuses a backslash in a path")
        self.git(self.upstream, "commit", "-q", "-m", "v1.1.0")
        self.git(self.upstream, "tag", "v1.1.0")
        result = self.tool("diff", "demo", "main", "--to", "v1.1.0")
        self.assertEqual(result.code, 1, result.text)
        self.assertIn("not safe to handle", result.err)
        self.assertFalse(self.exists(self.lynx, "build"))


if __name__ == "__main__":
    unittest.main()

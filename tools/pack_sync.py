#!/usr/bin/env python3
# Copyright (C) 2026 xhyxfxxc
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Third-party Morphe patch packs kept as source snapshots under packs/<name>/.

    python tools/pack_sync.py status
    python tools/pack_sync.py import <name> [--dry-run]
    python tools/pack_sync.py sync <name> <new-ref> [--dry-run]
    python tools/pack_sync.py diff <name> [--stat] [--name-only]
    python tools/pack_sync.py check [--min-age-hours N]

The pack list lives in packs/packs.json (schema and filter rules: packs/README.md). The tool only moves git
objects: it never checks an upstream tree out into a working directory of its own and never runs anything from
it. Trees are built in throwaway index files and grafted in with git plumbing, so blobs stay byte-identical to
upstream whatever core.autocrlf says on this machine.

check is read-only (it changes no file and no index entry, and works in a dirty tree). It asks GitHub for the
newest stable release of every upstream (api.github.com, no token; gh is only a fallback) and says per pack
whether a newer release exists that changes files we keep.

Exit codes: 0 ok, 1 error, 2 (sync only) the merge left conflicts.
"""

import argparse
import contextlib
import datetime
import http.client
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request

PACKS_JSON = "packs/packs.json"
ROOT_BUNDLE = "patches-bundle.json"
NAME_RE = re.compile(r"^[a-z0-9][a-z0-9-]*$")
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
ENTRY_KEYS = ("title", "app", "upstream", "ref", "commit", "include", "exclude", "version", "build")
BUILD_KEYS = ("java", "tasks", "output", "args")
# Files that change how a pack is built; a sync that touches them gets flagged for the review gate.
BUILD_NAMES = {
    "gradlew", "gradlew.bat", "settings.gradle", "settings.gradle.kts", "build.gradle", "build.gradle.kts",
    "gradle.properties", "gradle-wrapper.jar", "gradle-wrapper.properties", "libs.versions.toml",
}
IDENTITY = {
    "GIT_AUTHOR_NAME": "pack-sync", "GIT_AUTHOR_EMAIL": "pack-sync@invalid",
    "GIT_COMMITTER_NAME": "pack-sync", "GIT_COMMITTER_EMAIL": "pack-sync@invalid",
}
SHOWN = 20
VERSION_RE = re.compile(r"^v?(\d+(?:\.\d+)*)$")
RELEASES_FIXTURE_ENV = "PACK_SYNC_TEST_RELEASES"


class PackError(Exception):
    """An expected failure: printed as one line, no stack trace."""

    def __init__(self, message, code=1):
        super().__init__(message)
        self.code = code


# --- path filter -------------------------------------------------------------------------------------------

def _segment_matches(pattern, segment):
    """One path segment against a pattern whose only wildcard is '*' (any run of characters except '/')."""
    pattern = re.sub(r"\*+", "*", pattern)
    regex = "".join("[^/]*" if ch == "*" else re.escape(ch) for ch in pattern)
    return re.fullmatch(regex, segment, re.DOTALL) is not None


def _segments_match(pattern, path):
    if not pattern:
        return not path
    if pattern[0] == "**":
        if len(pattern) == 1:
            return len(path) >= 1          # a trailing ** means "everything under here", so at least one segment
        return any(_segments_match(pattern[1:], path[i:]) for i in range(len(path) + 1))
    if not path:
        return False
    return _segment_matches(pattern[0], path[0]) and _segments_match(pattern[1:], path[1:])


def pattern_matches(pattern, path):
    """
    Does one glob pattern select one file? Paths are relative to the upstream root with forward slashes.

    '**' as a whole segment matches any number of segments (none included, except as the last segment, where it
    means everything under the directory). '*' matches inside one segment and never crosses '/'. A pattern ending
    in '/' means "dir/**". Patterns are anchored at the root: 'name' matches only the root file 'name', never
    'sub/name'. Nothing else is special.
    """
    if pattern.endswith("/"):
        pattern += "**"
    return _segments_match(pattern.split("/"), path.split("/"))


def path_kept(path, include, exclude):
    """Kept = (include is empty OR it matches an include pattern) AND it matches no exclude pattern."""
    if include and not any(pattern_matches(p, path) for p in include):
        return False
    return not any(pattern_matches(p, path) for p in exclude)


def check_patterns(name, key, patterns):
    if not isinstance(patterns, list):
        raise PackError(f"{PACKS_JSON}: {name}.{key} must be a list of glob patterns")
    for pattern in patterns:
        if not isinstance(pattern, str) or not pattern:
            raise PackError(f"{PACKS_JSON}: {name}.{key} holds an empty or non-text pattern")
        if "\\" in pattern or "\0" in pattern or pattern.startswith("/"):
            raise PackError(f"{PACKS_JSON}: {name}.{key} pattern {pattern!r}: use forward slashes and no leading '/'")
        parts = pattern.rstrip("/").split("/") if pattern != "/" else [""]
        if any(part in ("", "..", ".") for part in parts):
            raise PackError(f"{PACKS_JSON}: {name}.{key} pattern {pattern!r} has an empty, '.' or '..' segment")


# --- packs.json --------------------------------------------------------------------------------------------

def validate_entry(name, entry):
    """Check one pack entry against the schema in packs/README.md. Only upstream and ref are needed to import."""
    if not NAME_RE.match(name):
        raise PackError(f"pack name {name!r} must match [a-z0-9][a-z0-9-]*")
    if not isinstance(entry, dict):
        raise PackError(f"{PACKS_JSON}: {name} must be an object")
    for key in entry:
        if key not in ENTRY_KEYS and not key.startswith("_"):
            raise PackError(f"{PACKS_JSON}: {name} has unknown key {key!r} (allowed: {', '.join(ENTRY_KEYS)})")
    for key in ("title", "app", "upstream", "ref", "commit", "version"):
        if key in entry and not isinstance(entry[key], str):
            raise PackError(f"{PACKS_JSON}: {name}.{key} must be a string")
    for key in ("upstream", "ref"):
        value = entry.get(key, "")
        if not value:
            raise PackError(f"{PACKS_JSON}: {name} has no {key}")
        if value.startswith("-") or "\n" in value:
            raise PackError(f"{PACKS_JSON}: {name}.{key} {value!r} is not a usable git {key}")
    if entry.get("commit") and not SHA_RE.match(entry["commit"]):
        raise PackError(f"{PACKS_JSON}: {name}.commit must be a full 40 character lowercase sha")
    check_patterns(name, "include", entry.get("include", []))
    check_patterns(name, "exclude", entry.get("exclude", []))
    build = entry.get("build", {})
    if not isinstance(build, dict):
        raise PackError(f"{PACKS_JSON}: {name}.build must be an object")
    for key, value in build.items():
        if key not in BUILD_KEYS or not isinstance(value, str):
            raise PackError(f"{PACKS_JSON}: {name}.build.{key} is not allowed (keys: {', '.join(BUILD_KEYS)}; text values)")


def load_packs(repo):
    path = os.path.join(repo.root, *PACKS_JSON.split("/"))
    if not os.path.isfile(path):
        raise PackError(f"{PACKS_JSON} not found")
    try:
        with open(path, encoding="utf-8-sig") as handle:
            data = json.load(handle)
    except ValueError as exc:
        raise PackError(f"{PACKS_JSON} is not valid JSON: {exc}")
    if not isinstance(data, dict) or not isinstance(data.get("packs"), dict):
        raise PackError(f'{PACKS_JSON} must be an object with a "packs" object')
    return data


def save_packs(repo, data):
    """Canonical format (2 space indent, LF, final newline), so the file's diffs stay small."""
    path = os.path.join(repo.root, *PACKS_JSON.split("/"))
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(data, handle, indent=2, ensure_ascii=False)
        handle.write("\n")


def pick_entry(data, name):
    if not NAME_RE.match(name):
        raise PackError(f"pack name {name!r} must match [a-z0-9][a-z0-9-]*")
    entry = data["packs"].get(name)
    if entry is None:
        known = ", ".join(data["packs"]) or "none"
        raise PackError(f"unknown pack {name!r} (not in {PACKS_JSON}; known: {known})")
    validate_entry(name, entry)
    return entry


def filter_of(entry):
    return list(entry.get("include") or []), list(entry.get("exclude") or [])


# --- git ---------------------------------------------------------------------------------------------------

def split_z(data):
    return [item.decode("utf-8", "surrogateescape") for item in data.split(b"\0") if item]


def join_z(paths):
    return b"".join(path.encode("utf-8", "surrogateescape") + b"\0" for path in paths)


def describe_command(args):
    """The git arguments for an error message, without the -c name=value pairs."""
    parts, skip = [], False
    for arg in args:
        if skip:
            skip = False
        elif arg == "-c":
            skip = True
        else:
            parts.append(arg)
    return " ".join(parts)[:200]


class Repo:
    """The lynx repo plus a temp dir for throwaway index files. Use as a context manager."""

    def __init__(self):
        self.env = dict(os.environ)
        self.env.pop("GIT_INDEX_FILE", None)       # the real index unless a call asks for a temporary one
        self.env["GIT_TERMINAL_PROMPT"] = "0"
        self.root = None
        self.tmp = None
        self._counter = 0
        self._shallow = None
        self.root = self.out("rev-parse", "--show-toplevel", cwd=os.getcwd())
        self.tmp = tempfile.mkdtemp(prefix="pack-sync-")

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.close()

    def close(self):
        self._restore_shallow()
        if self.tmp:
            shutil.rmtree(self.tmp, ignore_errors=True)
            self.tmp = None

    def run(self, *args, input=None, env=None, check=True, cwd=None):
        try:
            proc = subprocess.run(["git", *args], cwd=cwd or self.root, input=input,
                                  env=env or self.env, capture_output=True)
        except FileNotFoundError:
            raise PackError("git was not found on PATH")
        if check and proc.returncode != 0:
            shown = describe_command(args)
            detail = proc.stderr.decode("utf-8", "replace").strip() or proc.stdout.decode("utf-8", "replace").strip()
            raise PackError(f"git {shown} failed ({proc.returncode}): {detail}")
        return proc

    def out(self, *args, **kw):
        try:
            return self.run(*args, **kw).stdout.decode("utf-8", "replace").rstrip("\r\n")
        except PackError as exc:
            if "rev-parse --show-toplevel" in str(exc):
                raise PackError("not inside a git repository")
            raise

    def rev(self, spec):
        """Resolve a rev to its full sha, or None."""
        proc = self.run("rev-parse", "--verify", "--quiet", spec, check=False)
        return proc.stdout.decode().strip() if proc.returncode == 0 else None

    def temp_index_env(self):
        self._counter += 1
        env = dict(self.env)
        env["GIT_INDEX_FILE"] = os.path.join(self.tmp, f"index{self._counter}")
        return env

    # A depth-1 fetch records the fetched upstream commit in .git/shallow, which would make this whole repo
    # shallow for later fetches from other remotes. Only the commit's tree matters here, so the file is put back
    # the way it was when the command ends.
    def note_shallow(self):
        if self._shallow is None:
            path = self.out("rev-parse", "--git-path", "shallow")
            path = path if os.path.isabs(path) else os.path.join(self.root, path)
            self._shallow = (path, self._read(path))

    @staticmethod
    def _read(path):
        try:
            with open(path, "rb") as handle:
                return handle.read()
        except FileNotFoundError:
            return None

    def _restore_shallow(self):
        if self._shallow is None:
            return
        path, before = self._shallow
        self._shallow = None
        try:
            if self._read(path) == before:
                return
            if before is None:
                os.remove(path)
            else:
                with open(path, "wb") as handle:
                    handle.write(before)
        except OSError:
            pass


def require_head(repo):
    if repo.rev("HEAD^{commit}") is None:
        raise PackError("this repository has no commits yet")


def require_clean(repo):
    status = split_z(repo.run("status", "--porcelain", "-z").stdout)
    if status:
        shown = ", ".join(item.strip() for item in status[:5])
        more = f" (+{len(status) - 5} more)" if len(status) > 5 else ""
        raise PackError(f"the working tree or index is not clean: {shown}{more}. Commit or stash first")


def fetch_commit(repo, upstream, ref):
    """Shallow-fetch one ref (or sha) and return the full sha of the commit it names."""
    repo.note_shallow()
    repo.run("-c", "transfer.fsckObjects=true", "fetch", "--no-tags", "--depth", "1", "--quiet", "--", upstream, ref)
    sha = repo.rev("FETCH_HEAD^{commit}")
    if not sha:
        raise PackError(f"{ref!r} fetched from {upstream} does not name a commit")
    return sha


def ensure_commit(repo, upstream, sha):
    if repo.run("cat-file", "-e", f"{sha}^{{commit}}", check=False).returncode == 0:
        return
    print(f"fetching recorded upstream commit {sha[:7]} from {upstream}")
    got = fetch_commit(repo, upstream, sha)
    if got != sha:
        raise PackError(f"fetched {got} when {sha} was asked for")


# --- snapshots and trees -----------------------------------------------------------------------------------

class Snapshot:
    def __init__(self, tree, kept, dropped):
        self.tree = tree
        self.kept = kept
        self.dropped = dropped


def unsafe_path(path):
    parts = path.split("/")
    return "\\" in path or any(part in ("..", ".") or part.lower() == ".git" for part in parts)


def snapshot(repo, commit, include, exclude):
    """The filtered tree of an upstream commit, built in a temporary index. Nothing is checked out."""
    env = repo.temp_index_env()
    plumbing = ("-c", "core.ignorecase=false")
    repo.run(*plumbing, "read-tree", commit, env=env)
    paths = split_z(repo.run(*plumbing, "ls-files", "-z", env=env).stdout)
    kept = [p for p in paths if path_kept(p, include, exclude)]
    dropped = [p for p in paths if not path_kept(p, include, exclude)]
    if not kept:
        raise PackError("the filter keeps no file at all; check include and exclude in " + PACKS_JSON)
    bad = [p for p in kept if unsafe_path(p)]
    if bad:
        raise PackError(f"upstream tree holds paths that must never be checked out: {bad[0]!r}")
    if dropped:
        repo.run(*plumbing, "update-index", "--force-remove", "-z", "--stdin", input=join_z(dropped), env=env)
    tree = repo.out(*plumbing, "write-tree", env=env)
    return Snapshot(tree, kept, dropped)


def enforce_bundle_rule(snap, where):
    """Only our CI may write patches-bundle.json at a pack's root, or Manager would serve upstream's own binary."""
    if ROOT_BUNDLE in snap.kept:
        raise PackError(
            f"{where} would put {ROOT_BUNDLE} at the pack root. At packs/<name>/{ROOT_BUNDLE} it would make "
            f"Morphe Manager serve the upstream author's own binary from our URL; only our CI writes that file. "
            f'Add "{ROOT_BUNDLE}" to this pack\'s exclude list in {PACKS_JSON}.')


def warn_special_entries(repo, tree):
    """Symlinks and submodule links are legal but deserve a look in review."""
    listing = split_z(repo.run("ls-tree", "-r", "-z", tree).stdout)        # "<mode> <type> <sha>\t<path>"
    special = [item.split("\t", 1)[1] for item in listing if item.startswith(("120000 ", "160000 "))]
    if special:
        print(f"note: {len(special)} symlink or submodule entries, look at them in review: " + ", ".join(special[:5]))


def graft_tree(repo, name, subtree):
    """A full-repo tree: HEAD with everything under packs/<name>/ replaced by subtree."""
    prefix = f"packs/{name}/"
    env = repo.temp_index_env()
    plumbing = ("-c", "core.ignorecase=false")
    repo.run(*plumbing, "read-tree", "HEAD", env=env)
    old = split_z(repo.run(*plumbing, "ls-files", "-z", "--", prefix, env=env).stdout)
    if old:
        repo.run(*plumbing, "update-index", "--force-remove", "-z", "--stdin", input=join_z(old), env=env)
    repo.run(*plumbing, "read-tree", f"--prefix={prefix}", subtree, env=env)
    return repo.out(*plumbing, "write-tree", env=env)


def make_commit(repo, tree, parent, message):
    env = dict(repo.env)
    env.update(IDENTITY)
    return repo.out("-c", "commit.gpgsign=false", "commit-tree", tree, "-p", parent, "-m", message, env=env)


def tree_changes(repo, old, new):
    """[(status, path)] between two trees, no rename detection (a rename is a delete plus an add)."""
    data = repo.run("diff", "--no-renames", "--name-status", "-z", old, new).stdout
    items = [item.decode("utf-8", "surrogateescape") for item in data.split(b"\0") if item]
    return list(zip(items[0::2], items[1::2]))


def tree_stat_line(repo, old, new):
    lines = repo.out("diff", "--no-ext-diff", "--stat", old, new).splitlines()
    return lines[-1].strip() if lines else ""


def print_changes(changes, limit=30):
    for status, path in changes[:limit]:
        print(f"  {status} {path}")
    if len(changes) > limit:
        print(f"  ... and {len(changes) - limit} more")


def build_files(changes):
    hits = []
    for _, path in changes:
        base = path.rsplit("/", 1)[-1]
        if base in BUILD_NAMES or path.startswith((".github/", "buildSrc/", "gradle/")):
            hits.append(path)
    return hits


def github_slug(url):
    match = re.match(r"^(?:https?://(?:www\.)?github\.com/|git@github\.com:|ssh://git@github\.com/)"
                     r"([^/\s]+)/([^/\s]+?)(?:\.git)?/?$", url)
    return f"{match.group(1)}/{match.group(2)}" if match else None


def describe_upstream(url):
    return github_slug(url) or url


def describe_filter(include, exclude):
    return (", ".join(include) if include else "(everything)"), (", ".join(exclude) if exclude else "(nothing)")


# --- upstream releases (used by check) ---------------------------------------------------------------------

class TryGh(Exception):
    """The direct GitHub call failed in a way the gh command might get around (rate limit, network)."""

    def __init__(self, reason):
        super().__init__(reason)
        self.reason = reason


def first_line(text, limit=120):
    lines = str(text).strip().splitlines()
    return (lines[0] if lines else "")[:limit]


def parse_published(value):
    """GitHub's published_at ('2026-11-22T10:00:00Z') as an aware UTC datetime."""
    try:
        moment = datetime.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except (AttributeError, ValueError):
        raise PackError(f"release has an unusable published_at {str(value)[:40]!r}")
    if moment.tzinfo is None:
        moment = moment.replace(tzinfo=datetime.timezone.utc)
    return moment.astimezone(datetime.timezone.utc)


def release_fields(data):
    """(tag_name, published_at) out of a GitHub release object."""
    tag = data.get("tag_name") if isinstance(data, dict) else None
    if not isinstance(tag, str) or not tag.strip():
        raise PackError("release answer has no tag_name")
    return tag, parse_published(data.get("published_at"))


def fixture_release(path, upstream):
    """Test seam, see latest_release."""
    try:
        with open(path, encoding="utf-8") as handle:
            table = json.load(handle)
    except (OSError, ValueError) as exc:
        raise PackError(f"{RELEASES_FIXTURE_ENV} file {path!r} is unusable: {first_line(exc, 80)}")
    item = table.get(upstream) if isinstance(table, dict) else None
    if item is None:
        raise PackError("no stable release")
    if isinstance(item, dict) and "error" in item:
        raise PackError(first_line(item["error"]))
    return release_fields(item)


def github_latest(slug):
    """GET the latest-release object with no token. TryGh when gh might do better, PackError when nothing will."""
    request = urllib.request.Request(
        f"https://api.github.com/repos/{slug}/releases/latest",
        headers={"Accept": "application/vnd.github+json", "User-Agent": "pack-sync"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            raise PackError("no stable release")
        if exc.code in (403, 429):
            raise TryGh("rate limited")
        raise PackError(f"GitHub answered HTTP {exc.code}")
    except (urllib.error.URLError, http.client.HTTPException, OSError, ValueError) as exc:
        # network trouble, or an answer that is no JSON
        raise TryGh(f"network error: {first_line(getattr(exc, 'reason', exc), 80)}")


def gh_latest(slug, reason):
    """The same call through the gh command line tool, which uses its own stored login (never read here)."""
    try:
        proc = subprocess.run(["gh", "api", f"repos/{slug}/releases/latest"], capture_output=True, timeout=60)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise PackError(f"{reason}, gh failed: {first_line(exc, 80)}")
    if proc.returncode != 0:
        detail = first_line(proc.stderr.decode("utf-8", "replace") or proc.stdout.decode("utf-8", "replace"), 80)
        if "404" in detail or "Not Found" in detail:
            raise PackError("no stable release")
        raise PackError(f"{reason}, gh failed: {detail or 'no message'}")
    try:
        return json.loads(proc.stdout.decode("utf-8"))
    except ValueError:
        raise PackError(f"{reason}, gh answered something that is not JSON")


def latest_release(upstream):
    """
    (tag_name, published_at as an aware UTC datetime) of GitHub's "latest release" of an upstream, which leaves
    out pre-releases and drafts. Raises PackError with a one-line reason when there is none or it cannot be read.

    Test seam: when the environment variable PACK_SYNC_TEST_RELEASES names a JSON file, that file is read
    instead of the network. It maps an upstream URL to {"tag_name": ..., "published_at": ...}, or to
    {"error": "<message>"} for a failing lookup; an upstream that is missing from it has "no stable release".
    """
    fixture = os.environ.get(RELEASES_FIXTURE_ENV)
    if fixture:
        return fixture_release(fixture, upstream)
    slug = github_slug(upstream)
    if slug is None:
        raise PackError("upstream is not on GitHub")
    try:
        data = github_latest(slug)
    except TryGh as exc:
        if not shutil.which("gh"):
            raise PackError(f"{exc.reason}, gh not found")
        data = gh_latest(slug, exc.reason)
    return release_fields(data)


def parse_version(text):
    """'v1.2.3' or '1.2' as a tuple of ints, or None when the text is not a plain dotted number."""
    match = VERSION_RE.match(text)
    return tuple(int(part) for part in match.group(1).split(".")) if match else None


def age_hours(published, now):
    return max((now - published).total_seconds() / 3600.0, 0.0)


def age_text(hours):
    return f"{int(hours)} h old" if hours < 48 else f"{int(hours // 24)} d old"


def printable(text):
    return re.sub(r"[^\x20-\x7e]", "?", str(text))


# --- commands ----------------------------------------------------------------------------------------------

def pristine_snapshot(repo, name, entry):
    """The filtered upstream snapshot of the recorded commit, fetching that commit when it is not local."""
    commit = entry.get("commit")
    if not commit:
        raise PackError(f"{name} has no recorded commit in {PACKS_JSON}; was it imported?")
    ensure_commit(repo, entry["upstream"], commit)
    include, exclude = filter_of(entry)
    return snapshot(repo, commit, include, exclude)


def cmd_status(repo, args):
    data = load_packs(repo)
    if not data["packs"]:
        print(f"no packs in {PACKS_JSON}")
        return 0
    for name, entry in data["packs"].items():
        try:
            validate_entry(name, entry)
        except PackError as exc:
            print(f"{name}  INVALID: {exc}")
            continue
        head = f"{name}  upstream={entry['upstream']}  ref={entry['ref']}  commit={(entry.get('commit') or '-')[:7]}" \
               f"  version={entry.get('version') or '-'}"
        if repo.rev(f"HEAD:packs/{name}") is None:
            print(f"{head}  at-HEAD=no  not imported")
            continue
        try:
            snap = pristine_snapshot(repo, name, entry)
            names = split_z(repo.run("diff", "--no-renames", "--name-only", "-z", snap.tree, f"HEAD:packs/{name}").stdout)
            tail = f"differs-from-upstream={len(names)} files"
        except PackError as exc:
            tail = f"differs-from-upstream=unknown ({str(exc).splitlines()[0][:100]})"
        print(f"{head}  at-HEAD=yes  {tail}")
    return 0


def cmd_import(repo, args):
    name = args.name
    data = load_packs(repo)
    entry = pick_entry(data, name)
    upstream, ref = entry["upstream"], entry["ref"]
    include, exclude = filter_of(entry)
    prefix = f"packs/{name}"

    require_head(repo)
    if repo.rev(f"HEAD:{prefix}") is not None:
        raise PackError(f"{prefix}/ already exists at HEAD; use sync to move it to a newer upstream version")
    if repo.run("ls-files", "-z", "--", prefix).stdout:
        raise PackError(f"{prefix}/ is already in the index")
    folder = os.path.join(repo.root, "packs", name)
    if os.path.isdir(folder) and os.listdir(folder):
        raise PackError(f"{prefix}/ exists on disk (ignored leftovers of a build?); remove it first")
    require_clean(repo)

    sha = fetch_commit(repo, upstream, ref)
    snap = snapshot(repo, sha, include, exclude)
    enforce_bundle_rule(snap, f"importing {ref}")
    warn_special_entries(repo, snap.tree)

    if args.dry_run:
        print(f"dry run: nothing was changed. {name} {ref} is commit {sha}")
        print(f"filtered tree {snap.tree}: {len(snap.kept)} files kept, {len(snap.dropped)} dropped")
        for path in snap.dropped[:SHOWN]:
            print(f"  dropped {path}")
        if len(snap.dropped) > SHOWN:
            print(f"  ... and {len(snap.dropped) - SHOWN} more")
        return 0

    short = describe_upstream(upstream)
    inc, exc = describe_filter(include, exclude)
    message = (f"chore(pack-{name}): import {short} {ref} ({sha[:7]})\n\n"
               f"Source snapshot of {upstream} at {ref} ({sha}).\n"
               f"Files: {len(snap.kept)} kept, {len(snap.dropped)} dropped by the filter.\n"
               f"Include: {inc}\nExclude: {exc}\n")
    try:
        repo.run("read-tree", f"--prefix={prefix}/", "-u", snap.tree)
        entry["commit"] = sha
        save_packs(repo, data)
        repo.run("add", PACKS_JSON)
        repo.run("commit", "-q", "-F", "-", input=message.encode("utf-8"))
    except PackError:
        # The tree was clean when we started, so putting HEAD back loses nothing.
        repo.run("reset", "--hard", "-q", "HEAD", check=False)
        raise
    print(f"imported {name}: {len(snap.kept)} files from {short} {ref} ({sha[:7]}) into {prefix}/")
    print(f"committed {repo.out('rev-parse', '--short', 'HEAD')}. Review the code before any build (packs/README.md), "
          f"then add {prefix}/LYNX.md.")
    return 0


def cmd_sync(repo, args):
    name, new_ref = args.name, args.new_ref
    if new_ref.startswith("-"):
        raise PackError(f"{new_ref!r} is not a usable git ref")
    data = load_packs(repo)
    entry = pick_entry(data, name)
    upstream, old_ref, old_sha = entry["upstream"], entry["ref"], entry.get("commit")
    include, exclude = filter_of(entry)
    prefix = f"packs/{name}"

    require_head(repo)
    if not old_sha:
        raise PackError(f"{name} has no recorded commit in {PACKS_JSON}; import it first")
    if repo.rev(f"HEAD:{prefix}") is None:
        raise PackError(f"{prefix}/ does not exist at HEAD; import it first")
    require_clean(repo)

    ensure_commit(repo, upstream, old_sha)
    new_sha = fetch_commit(repo, upstream, new_ref)
    t_old = snapshot(repo, old_sha, include, exclude)
    t_new = snapshot(repo, new_sha, include, exclude)
    enforce_bundle_rule(t_old, f"the recorded commit {old_sha[:7]}")
    enforce_bundle_rule(t_new, f"{new_ref}")
    warn_special_entries(repo, t_new.tree)

    if t_old.tree == t_new.tree:
        print(f"already up to date: {name} {new_ref} ({new_sha[:7]}) has no change in the files we keep "
              f"(recorded: {old_ref} {old_sha[:7]})")
        return 0

    changes = tree_changes(repo, t_old.tree, t_new.tree)
    stat = tree_stat_line(repo, t_old.tree, t_new.tree)
    old7, new7 = old_sha[:7], new_sha[:7]
    print(f"{name}: {old_ref} ({old7}) -> {new_ref} ({new7})")
    print(f"upstream change: {stat}")
    print_changes(changes)
    risky = build_files(changes)
    if risky:
        print("BUILD FILES CHANGED, read these first: " + ", ".join(risky[:10]))
    slug = github_slug(upstream)
    if slug:
        print(f"compare: https://github.com/{slug}/compare/{old_sha}...{new_sha}")

    if args.dry_run:
        print("dry run: nothing was changed")
        return 0

    head = repo.rev("HEAD")
    base_tree = graft_tree(repo, name, t_old.tree)
    theirs_tree = graft_tree(repo, name, t_new.tree)
    base = make_commit(repo, base_tree, head, f"pack-sync: {name} base {old7}")
    theirs = make_commit(repo, theirs_tree, base, f"pack-sync: {name} upstream {old7} -> {new7}")

    pick = repo.run("cherry-pick", "--no-commit", theirs, check=False)
    conflicts = split_z(repo.run("diff", "--name-only", "--diff-filter=U", "-z").stdout)
    if pick.returncode != 0 and not conflicts:
        repo.run("reset", "--hard", "-q", "HEAD", check=False)
        raise PackError("cherry-pick of the upstream change failed: "
                        + (pick.stderr.decode("utf-8", "replace").strip() or "no message"))

    entry["ref"], entry["commit"] = new_ref, new_sha
    save_packs(repo, data)
    repo.run("add", PACKS_JSON)

    diff = repo.run("diff", "--binary", "--no-color", "--no-ext-diff", base, theirs, "--", prefix).stdout
    out_dir = os.path.join(repo.root, "build", "pack-sync")
    os.makedirs(out_dir, exist_ok=True)
    diff_path = os.path.join(out_dir, f"{name}-{old7}..{new7}.diff")
    with open(diff_path, "wb") as handle:
        handle.write(diff)
    print(f"upstream diff saved to {os.path.relpath(diff_path, repo.root).replace(os.sep, '/')}")

    command = f'git commit -m "chore(pack-{name}): upstream {old_ref} -> {new_ref} ({new7})"'
    if conflicts:
        print(f"CONFLICTS in {len(conflicts)} files (our changes and upstream's touch the same lines):")
        for path in conflicts:
            print(f"  {path}")
        print("Resolve them (markers are in the files), git add each, then review as below.")
    else:
        print("merge is clean; changes are staged, nothing is committed")
    print("REVIEW GATE: read the upstream diff before committing (packs/README.md says what to look for). Then run:")
    print(f"  {command}")
    return 2 if conflicts else 0


def cmd_diff(repo, args):
    data = load_packs(repo)
    entry = pick_entry(data, args.name)
    require_head(repo)
    if repo.rev(f"HEAD:packs/{args.name}") is None:
        raise PackError(f"packs/{args.name}/ does not exist at HEAD; import it first")
    snap = pristine_snapshot(repo, args.name, entry)
    cmd = ["-c", "core.quotepath=false", "--no-pager", "diff", "--no-color", "--no-ext-diff", "--no-textconv",
           "--no-renames"]
    if args.stat:
        cmd.append("--stat")
    if args.name_only:
        cmd.append("--name-only")
    out = repo.run(*cmd, snap.tree, f"HEAD:packs/{args.name}").stdout
    sys.stdout.flush()
    sys.stdout.buffer.write(out)
    sys.stdout.buffer.flush()
    return 0


class CheckRow:
    """What `check` knows about one pack: the columns, the state with its note, and the flags."""

    def __init__(self, name, entry):
        entry = entry if isinstance(entry, dict) else {}
        self.name = printable(name)
        self.ours = printable(entry.get("ref") or "?")
        self.lynx = printable(entry.get("version") or "-")
        self.latest = "?"
        self.published = "-"
        self.state = "UNKNOWN"
        self.note = ""
        self.flags = []                    # [(FLAG, note)]


def check_state(repo, name, entry, min_age_hours, now, row):
    """(state, note) of one pack against the newest stable release of its upstream."""
    validate_entry(name, entry)
    ours, upstream = entry["ref"], entry["upstream"]
    tag, published = latest_release(upstream)
    hours = age_hours(published, now)
    row.latest = f"{printable(tag)[:40]} ({age_text(hours)})"
    if tag == ours:
        return "OK", ""
    old_version, new_version = parse_version(ours), parse_version(tag)
    if old_version is None or new_version is None:
        return "UNKNOWN", printable(f"cannot compare {ours} with {tag}")[:120]
    if new_version < old_version:
        return "OLDER", ""
    # Not lower: higher, or the same number spelled differently. Either way the files decide.
    include, exclude = filter_of(entry)
    with contextlib.redirect_stdout(io.StringIO()):         # one line per pack: no "fetching recorded commit" chatter
        ours_snap = pristine_snapshot(repo, name, entry)
        sha = fetch_commit(repo, upstream, f"refs/tags/{tag}")
    if snapshot(repo, sha, include, exclude).tree == ours_snap.tree:
        return "OK-SAME-FILES", ""
    if hours < min_age_hours:
        return "TOO-NEW", f"younger than {min_age_hours:g} h"
    return "UPDATE", ""


def read_bundle_version(repo, name):
    """(version, problem) of packs/<name>/patches-bundle.json in the working tree; one of them is None."""
    rel = f"packs/{name}/patches-bundle.json"
    path = os.path.join(repo.root, *rel.split("/"))
    if not NAME_RE.match(name) or not os.path.isfile(path):
        return None, f"no {rel}"
    try:
        with open(path, encoding="utf-8-sig") as handle:
            version = json.load(handle).get("version")
    except (OSError, ValueError, AttributeError):
        return None, f"{rel} is unreadable"
    if not isinstance(version, str) or not version:
        return None, f"{rel} has no version"
    return version, None


def add_flags(repo, name, entry, row):
    entry = entry if isinstance(entry, dict) else {}
    ref, version = entry.get("ref"), entry.get("version")
    notes = []
    if isinstance(ref, str) and ref:
        base = ref[1:] if ref.startswith("v") else ref
        if not isinstance(version, str) or not version.startswith(f"{base}-lynx."):
            notes.append(f"version {printable(version) if version else '-'} does not belong to ref {printable(ref)}")
    bundle, problem = read_bundle_version(repo, name)
    if bundle is not None:
        row.published = printable(bundle)
        if bundle != version:
            notes.append(f"bundle version {row.published} differs from version {row.lynx}")
    else:
        notes.append(problem)
    if notes:
        row.flags.append(("UNRELEASED", "; ".join(notes)))
    # --no-optional-locks: a plain `git status` may refresh the index file; this one never writes it
    if NAME_RE.match(name):
        status = repo.run("--no-optional-locks", "status", "--porcelain", "--", f"packs/{name}").stdout
        if status.strip():
            row.flags.append(("IN-PROGRESS", f"uncommitted changes in packs/{name}"))


def check_pack(repo, name, entry, min_age_hours, now):
    row = CheckRow(name, entry)
    try:
        row.state, row.note = check_state(repo, name, entry, min_age_hours, now, row)
    except PackError as exc:
        row.state, row.note = "UNKNOWN", printable(first_line(exc))
    except Exception as exc:                                # one pack must never stop the others
        row.state, row.note = "UNKNOWN", printable(f"unexpected {type(exc).__name__}: {first_line(exc, 80)}")
    add_flags(repo, name, entry, row)
    return row


def cmd_check(repo, args):
    data = load_packs(repo)
    if not data["packs"]:
        print(f"no packs in {PACKS_JSON}")
        return 0
    now = datetime.datetime.now(datetime.timezone.utc)
    rows = [check_pack(repo, name, entry, args.min_age_hours, now) for name, entry in data["packs"].items()]
    cells = [[r.name, f"ours={r.ours}", f"latest={r.latest}", f"lynx={r.lynx}", f"published={r.published}"]
             for r in rows]
    widths = [max(len(cell[i]) for cell in cells) for i in range(5)]
    for row, cell in zip(rows, cells):
        line = "  ".join(text.ljust(width) for text, width in zip(cell, widths))
        line += f"  -> {row.state}" + (f" ({row.note})" if row.note else "")
        for flag, note in row.flags:
            line += f" +{flag} ({note})"
        print(line)

    def names(pick):
        return ", ".join(r.name for r in rows if pick(r)) or "-"

    def flagged(flag):
        return lambda r: any(f == flag for f, _ in r.flags)

    print("summary: " + " | ".join([
        "UPDATE: " + names(lambda r: r.state == "UPDATE"),
        "TOO-NEW: " + names(lambda r: r.state == "TOO-NEW"),
        "UNRELEASED: " + names(flagged("UNRELEASED")),
        "IN-PROGRESS: " + names(flagged("IN-PROGRESS")),
        "UNKNOWN: " + names(lambda r: r.state == "UNKNOWN"),
    ]))
    return 0


def build_parser():
    parser = argparse.ArgumentParser(
        prog="pack_sync.py", description="Keep third-party patch packs as source snapshots under packs/<name>/.")
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("status", help="list every pack with its upstream, version and local-change count") \
        .set_defaults(func=cmd_status)

    p_import = sub.add_parser("import", help="import a pack from upstream into packs/<name>/ and commit it")
    p_import.add_argument("name")
    p_import.add_argument("--dry-run", action="store_true", help="show what would be imported, change nothing")
    p_import.set_defaults(func=cmd_import)

    p_sync = sub.add_parser("sync", help="3-way merge a newer upstream ref into packs/<name>/ (staged, not committed)")
    p_sync.add_argument("name")
    p_sync.add_argument("new_ref")
    p_sync.add_argument("--dry-run", action="store_true", help="show the upstream change, touch nothing")
    p_sync.set_defaults(func=cmd_sync)

    p_diff = sub.add_parser("diff", help="show OUR changes to a pack against its pristine upstream snapshot")
    p_diff.add_argument("name")
    group = p_diff.add_mutually_exclusive_group()
    group.add_argument("--stat", action="store_true")
    group.add_argument("--name-only", action="store_true")
    p_diff.set_defaults(func=cmd_diff)

    p_check = sub.add_parser(
        "check", help="compare every pack with the newest stable release of its upstream (read-only)")
    p_check.add_argument("--min-age-hours", type=float, default=24.0, metavar="N",
                         help="a release younger than N hours is TOO-NEW instead of UPDATE (default 24)")
    p_check.set_defaults(func=cmd_check)
    return parser


def main(argv=None):
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")
    args = build_parser().parse_args(argv)
    try:
        with Repo() as repo:
            return args.func(repo, args)
    except PackError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return exc.code
    except KeyboardInterrupt:
        print("interrupted", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())

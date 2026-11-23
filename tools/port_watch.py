#!/usr/bin/env python3
# Copyright (C) 2026 xhyxfxxc
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Watches the upstream projects that code of ours was ported from by hand (Amazon Shopping today).

    python tools/port_watch.py check [--min-age-hours N]
    python tools/port_watch.py diff <port> <source> [--from REF] [--to REF]

The registry is tools/port_watch.json. A "port" is code of ours (the "ours" folders) that was rewritten by hand
from one or more upstream "sources". Per source it records the upstream repo, the newest stable release tag
already looked at ("reviewed"), and glob patterns ("paths", same language as pack_sync) for the upstream files our
code came from. A source may carry "parked": a release that was reviewed and waits for the owner's decision.

check is read-only (it changes no file, no index entry and leaves the shallow state of the repo as it was). It
asks GitHub for the newest stable release of every source and says whether a newer one changes a watched file.
diff writes the unified diff of the watched files between two upstream tags to build/port-watch/ (git-ignored)
and prints a short summary. It never checks anything out and never builds or runs anything from upstream.

Exit codes: 0 ok (check: whatever the states are), 1 error.
"""

import argparse
import datetime
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.dont_write_bytecode = True      # keep tools/ free of __pycache__ files
import pack_sync as ps  # noqa: E402

REGISTRY = "tools/port_watch.json"
PORT_KEYS = ("title", "ours", "sources")
SOURCE_KEYS = ("upstream", "reviewed", "paths", "parked")
PARKED_KEYS = ("ref", "date", "note")
DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
NOTE_MAX = 200
OUT_DIR = "build/port-watch"
SHOW_FILES = ("UPDATE", "TOO-NEW", "PARKED")


# --- registry ----------------------------------------------------------------------------------------------

def fail(where, problem):
    raise ps.PackError(f"{REGISTRY}: {where} {problem}")


def check_keys(where, value, allowed):
    """The value is an object and holds no key that is not allowed (this catches typos)."""
    if not isinstance(value, dict):
        fail(where, "must be an object")
    for key in value:
        if key not in allowed:
            fail(where, f"has unknown key {key!r} (allowed: {', '.join(allowed)})")


def check_name(kind, name):
    if not ps.NAME_RE.fullmatch(name):
        raise ps.PackError(f"{REGISTRY}: {kind} name {name!r} must match [a-z0-9][a-z0-9-]*")


def check_text(where, value):
    if not isinstance(value, str) or not value.strip():
        fail(where, "must be non-empty text")


def check_folders(where, folders):
    if not isinstance(folders, list) or not folders:
        fail(where, "must be a non-empty list of repo folders")
    for folder in folders:
        if (not isinstance(folder, str) or "\\" in folder or "\0" in folder
                or any(part in ("", ".", "..") for part in folder.split("/"))):
            fail(where, f"holds {folder!r}: use repo-relative folders with forward slashes, "
                        "no leading '/', no empty, '.' or '..' segment")


def is_date(value):
    if not isinstance(value, str) or not DATE_RE.match(value):
        return False
    try:
        datetime.date.fromisoformat(value)
    except ValueError:
        return False
    return True


def check_parked(where, parked):
    check_keys(where, parked, PARKED_KEYS)
    for key in PARKED_KEYS:
        if key not in parked:
            fail(where, f"has no {key}")
    check_text(f"{where}.ref", parked["ref"])
    if not is_date(parked["date"]):
        fail(f"{where}.date", "must be a date like 2026-11-23")
    if not isinstance(parked["note"], str) or len(parked["note"]) > NOTE_MAX:
        fail(f"{where}.note", f"must be text of at most {NOTE_MAX} characters")


def check_source(port, name, source):
    where = f"{port}/{name}"
    check_name("source", name)
    check_keys(where, source, SOURCE_KEYS)
    for key in ("upstream", "reviewed"):
        check_text(f"{where}.{key}", source.get(key))
    try:
        ps.check_patterns(where, "paths", source.get("paths"))
    except ps.PackError as exc:
        raise ps.PackError(str(exc).replace(ps.PACKS_JSON, REGISTRY))
    if not source["paths"]:
        fail(f"{where}.paths", "must not be empty")
    if "parked" in source:
        check_parked(f"{where}.parked", source["parked"])


def check_port(name, port):
    check_name("port", name)
    check_keys(name, port, PORT_KEYS)
    check_text(f"{name}.title", port.get("title"))
    check_folders(f"{name}.ours", port.get("ours"))
    if not isinstance(port.get("sources"), dict) or not port["sources"]:
        fail(f"{name}.sources", "must be a non-empty object")
    for source_name, source in port["sources"].items():
        check_source(name, source_name, source)


def load_registry(repo):
    """The validated {port name: port} table of tools/port_watch.json."""
    path = os.path.join(repo.root, *REGISTRY.split("/"))
    if not os.path.isfile(path):
        raise ps.PackError(f"{REGISTRY} not found")
    try:
        with open(path, encoding="utf-8-sig") as handle:
            data = json.load(handle)
    except OSError as exc:
        raise ps.PackError(f"{REGISTRY} cannot be read: {ps.first_line(exc, 80)}")
    except ValueError as exc:
        raise ps.PackError(f"{REGISTRY} is not valid JSON: {ps.first_line(exc, 80)}")
    check_keys("top level", data, ("ports",))
    if not isinstance(data.get("ports"), dict):
        fail("ports", "must be an object")
    for name, port in data["ports"].items():
        check_port(name, port)
    return data["ports"]


# --- upstream trees ----------------------------------------------------------------------------------------

def ref_spec(ref):
    """A tag name is fetched as refs/tags/<name>; a full ref or a 40 character sha is fetched as it is."""
    return ref if ref.startswith("refs/") or ps.SHA_RE.fullmatch(ref) else "refs/tags/" + ref


def tree_map(repo, commit, patterns):
    """{path: (mode, object id)} for the files of a commit that match a watched pattern."""
    data = repo.run("-c", "core.quotepath=false", "ls-tree", "-r", "-z", commit).stdout
    table = {}
    for item in ps.split_z(data):                 # "<mode> <type> <object>\t<path>"
        meta, _, path = item.partition("\t")
        mode, kind, oid = meta.split()
        if kind == "blob" and any(ps.pattern_matches(pattern, path) for pattern in patterns):
            table[path] = (mode, oid)
    return table


def watched_changes(repo, upstream, old_ref, new_ref, patterns):
    """(old commit, new commit, [(A|D|M, path)] sorted by path) for the watched files between two upstream refs."""
    old = ps.fetch_commit(repo, upstream, ref_spec(old_ref))
    new = ps.fetch_commit(repo, upstream, ref_spec(new_ref))
    before, after = tree_map(repo, old, patterns), tree_map(repo, new, patterns)
    changes = []
    for path in sorted(before.keys() | after.keys()):
        if path not in before:
            changes.append(("A", path))
        elif path not in after:
            changes.append(("D", path))
        elif before[path] != after[path]:
            changes.append(("M", path))
    return old, new, changes


# --- check -------------------------------------------------------------------------------------------------

class Row:
    """What `check` knows about one source: the columns, the state with its note, the flags, the changed files."""

    def __init__(self, port, name, source):
        self.name = f"{port}/{name}"
        self.reviewed = ps.printable(source["reviewed"])
        self.latest = "-"
        self.state = "UNKNOWN"
        self.note = ""
        self.flags = []                    # [(FLAG, note)]
        self.changes = []                  # [(A|D|M, path)]


def source_state(repo, source, min_age_hours, now, row):
    """(state, note) of one source against the newest stable release of its upstream."""
    upstream, reviewed = source["upstream"], source["reviewed"]
    tag, published = ps.latest_release(upstream)
    hours = ps.age_hours(published, now)
    row.latest = f"{ps.printable(tag)[:40]} ({ps.age_text(hours)})"
    if tag == reviewed:
        return "OK", ""
    old_version, new_version = ps.parse_version(reviewed), ps.parse_version(tag)
    if old_version is None or new_version is None:
        return "UNKNOWN", f"cannot compare {reviewed} with {tag[:40]}"
    if new_version < old_version:
        return "OLDER", ""
    row.changes = watched_changes(repo, upstream, reviewed, tag, source["paths"])[2]
    if not row.changes:
        return "OK-SAME-FILES", ""
    parked = source.get("parked")
    if parked and parked["ref"] == tag:
        return "PARKED", parked["note"]
    if hours < min_age_hours:
        return "TOO-NEW", f"younger than {min_age_hours:g} h"
    if parked:
        row.flags.append(("PARKED-OLDER", f"{parked['ref']}: {parked['note']}"))
    return "UPDATE", f"{len(row.changes)} watched file(s) changed"


def check_one(repo, port, name, source, min_age_hours, now):
    row = Row(port, name, source)
    try:
        row.state, row.note = source_state(repo, source, min_age_hours, now, row)
    except ps.PackError as exc:
        row.state, row.note = "UNKNOWN", ps.first_line(exc, 200)
    except Exception as exc:                                # one source must never stop the others
        row.state, row.note = "UNKNOWN", f"unexpected {type(exc).__name__}: {ps.first_line(exc, 80)}"
    return row


def folders_in_progress(repo, ours):
    """The ours folders that hold uncommitted changes. --no-optional-locks: this status never writes the index."""
    status = repo.run("--no-optional-locks", "status", "--porcelain", "--no-renames", "-z", "--", *ours).stdout
    paths = [item[3:] for item in ps.split_z(status)]
    return [folder for folder in ours if any(path.startswith(folder + "/") or path == folder for path in paths)]


def print_changes(changes):
    for status, path in changes[:ps.SHOWN]:
        print(f"    {status} {ps.printable(path)}")
    if len(changes) > ps.SHOWN:
        print(f"    ... and {len(changes) - ps.SHOWN} more")


def print_rows(rows):
    cells = [[r.name, f"reviewed={r.reviewed}", f"latest={r.latest}"] for r in rows]
    widths = [max(len(cell[i]) for cell in cells) for i in range(3)]
    for row, cell in zip(rows, cells):
        line = "  ".join(text.ljust(width) for text, width in zip(cell, widths))
        line += f"  -> {row.state}" + (f" ({ps.printable(row.note)})" if row.note else "")
        for flag, note in row.flags:
            line += f" +{flag} ({ps.printable(note)})"
        print(line)
        if row.state in SHOW_FILES:
            print_changes(row.changes)


def print_summary(rows):
    def names(pick):
        return ", ".join(r.name for r in rows if pick(r)) or "-"

    def flagged(row):
        return any(flag == "IN-PROGRESS" for flag, _ in row.flags)

    print("summary: " + " | ".join([
        "UPDATE: " + names(lambda r: r.state == "UPDATE"),
        "TOO-NEW: " + names(lambda r: r.state == "TOO-NEW"),
        "PARKED: " + names(lambda r: r.state == "PARKED"),
        "IN-PROGRESS: " + names(flagged),
        "UNKNOWN: " + names(lambda r: r.state == "UNKNOWN"),
    ]))


def cmd_check(repo, args):
    ports = load_registry(repo)
    if not ports:
        print(f"no ports in {REGISTRY}")
        return 0
    now = datetime.datetime.now(datetime.timezone.utc)
    rows = []
    for port_name, port in ports.items():
        busy = folders_in_progress(repo, port["ours"])
        for source_name, source in port["sources"].items():
            row = check_one(repo, port_name, source_name, source, args.min_age_hours, now)
            if busy:
                row.flags.append(("IN-PROGRESS", "uncommitted changes in " + ", ".join(busy)))
            rows.append(row)
    print_rows(rows)
    print_summary(rows)
    return 0


# --- diff --------------------------------------------------------------------------------------------------

def pick_source(ports, port_name, source_name):
    port = ports.get(port_name)
    if port is None:
        raise ps.PackError(f"unknown port {port_name!r} (not in {REGISTRY}; known: {', '.join(ports) or 'none'})")
    source = port["sources"].get(source_name)
    if source is None:
        raise ps.PackError(f"unknown source {source_name!r} in port {port_name} "
                           f"(known: {', '.join(port['sources'])})")
    return source


def diff_git(repo, options, old, new, pathspecs):
    """git diff of two commits, limited to literal paths. Nothing is checked out."""
    return repo.run("-c", "core.quotepath=false", "diff", *options, "--no-ext-diff", "--no-textconv",
                    "--no-renames", old, new, "--", *pathspecs).stdout


def numstat(repo, old, new, pathspecs):
    """{path: (added, deleted)} as git counts them ('-' for both when the file is binary)."""
    table = {}
    for item in ps.split_z(diff_git(repo, ("--numstat", "-z"), old, new, pathspecs)):
        added, deleted, path = item.split("\t", 2)
        table[path] = (added, deleted)
    return table


def cmd_diff(repo, args):
    source = pick_source(load_registry(repo), args.port, args.source)
    old_ref = args.from_ref or source["reviewed"]
    new_ref = args.to_ref or ps.latest_release(source["upstream"])[0]
    old, new, changes = watched_changes(repo, source["upstream"], old_ref, new_ref, source["paths"])
    if not changes:
        print(f"no watched file differs between {old_ref} and {new_ref}")
        return 0
    unsafe = [path for _, path in changes if ps.unsafe_path(path)]
    if unsafe:
        raise ps.PackError(f"upstream holds a watched path that is not safe to handle: {ps.printable(unsafe[0])!r}")
    name = re.sub(r"[^A-Za-z0-9._-]", "_", f"{args.port}-{args.source}-{old_ref}_to_{new_ref}.diff")
    rel = f"{OUT_DIR}/{name}"
    if repo.run("check-ignore", "-q", rel, check=False).returncode != 0:
        raise ps.PackError(f"{rel} is not git-ignored here (is build/ in .gitignore?); refusing to write it")
    pathspecs = [":(literal)" + path for _, path in changes]
    patch = diff_git(repo, ("--no-color",), old, new, pathspecs)
    stats = numstat(repo, old, new, pathspecs)
    out_path = os.path.join(repo.root, *rel.split("/"))
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "wb") as handle:
        handle.write(patch)
    added = deleted = 0
    for status, path in changes:
        plus, minus = stats.get(path, ("0", "0"))
        if plus == "-":
            counts = "binary"
        else:
            counts = f"+{plus} -{minus}"
            added, deleted = added + int(plus), deleted + int(minus)
        print(f"{counts} {status} {ps.printable(path)}")
    print(f"total: {len(changes)} file(s), +{added} -{deleted}")
    print(f"diff saved to {rel}")
    return 0


# --- command line ------------------------------------------------------------------------------------------

def build_parser():
    parser = argparse.ArgumentParser(
        prog="port_watch.py", description="Watch the upstream projects our hand-made ports came from.")
    sub = parser.add_subparsers(dest="command", required=True)

    p_check = sub.add_parser(
        "check", help="compare every source with the newest stable release of its upstream (read-only)")
    p_check.add_argument("--min-age-hours", type=float, default=24.0, metavar="N",
                         help="a release younger than N hours is TOO-NEW instead of UPDATE (default 24)")
    p_check.set_defaults(func=cmd_check)

    p_diff = sub.add_parser("diff", help="write the diff of the watched files between two upstream tags")
    p_diff.add_argument("port")
    p_diff.add_argument("source")
    p_diff.add_argument("--from", dest="from_ref", metavar="REF", help="old tag (default: the reviewed tag)")
    p_diff.add_argument("--to", dest="to_ref", metavar="REF", help="new tag (default: the newest stable release)")
    p_diff.set_defaults(func=cmd_diff)
    return parser


def main(argv=None):
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")
    args = build_parser().parse_args(argv)
    try:
        with ps.Repo() as repo:
            return args.func(repo, args)
    except ps.PackError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return exc.code
    except KeyboardInterrupt:
        print("interrupted", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
# Copyright (C) 2026 xhyxfxxc
# SPDX-License-Identifier: GPL-3.0-or-later
"""List the host names found inside a built patch bundle, or compare two bundles. Read-only.

    python -I tools/bundle_hosts.py <bundle.mpp>
    python -I tools/bundle_hosts.py <bundle.mpp> <newer bundle.mpp>

A bundle is a zip with classes.dex, .class files and nested zips under extensions/ (*.mpe, *.rve). Every entry
is walked; an entry that starts with the zip magic is opened as a zip (at most 3 levels deep) and every other
entry is scanned as raw bytes. Two classes of host are reported:

    url   the host of an http, https, ws, wss or ftp URL (lower case, port and user info removed; a name without
          a dot is ignored unless it is localhost; IP addresses are kept)
    bare  a dotted name without a scheme that ends in a common top-level domain. This class has false positives
          (a package or a word can look like a host); it is only ever reported under its own class.

One bundle: a table of class, host, count and the first entry path, then the totals.
Two bundles: "+" lines for hosts only the newer one has, "-" lines for hosts only the older one has, then the
totals. A host seen as url in either bundle counts as url. Release gate: an added host has to be explained.

The bundles are untrusted data: nothing in them is imported or run, and entries are only read as bytes.
Exit codes: 0 ok, 1 a file that is not a zip (or cannot be read), 2 wrong usage.
"""
import argparse
import io
import re
import sys
import zipfile
import zlib

ZIP_MAGIC = b"PK\x03\x04"
MAX_DEPTH = 3                       # nested zips below the bundle itself
MAX_ENTRY = 256 * 1024 * 1024       # an entry that claims more than this is skipped
MAX_TOTAL = 1024 * 1024 * 1024      # bytes read from one bundle, nested zips included

TLDS = ("com", "net", "org", "io", "me", "app", "dev", "co", "gg", "tv", "ai", "xyz", "info", "link", "to", "ly",
        "be", "cc", "us", "uk", "de", "fr", "ru", "cn", "jp", "in", "br", "au", "ca", "nl", "se", "ch", "top",
        "site", "online", "store", "tech", "cloud", "page")

URL_RE = re.compile(rb"(?:https?|wss?|ftp)://([A-Za-z0-9._~%@:\[\]-]+)", re.IGNORECASE)
LABEL = rb"[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"
BARE_RE = re.compile(
    rb"(?<![A-Za-z0-9._/-])((?:" + LABEL + rb"\.)+(?:" + b"|".join(t.encode() for t in TLDS) + rb"))"
    rb"(?![A-Za-z0-9._/-])", re.IGNORECASE)
HOST_LABEL_RE = re.compile(r"^[a-z0-9%_]+(?:-+[a-z0-9%_]+)*$")
IPV6_RE = re.compile(r"^\[[0-9a-f:.]*:[0-9a-f:.]*\]$")


def url_host(raw):
    """The normalised host of a URL authority (bytes), or None when it is not a usable host."""
    host = raw.decode("ascii").lower()
    host = host.rsplit("@", 1)[-1]                      # user info
    if host.startswith("["):                            # IPv6 literal, with or without a port
        end = host.find("]")
        literal = host[:end + 1] if end > 0 else ""
        return literal if IPV6_RE.match(literal) else None
    host = re.sub(r":[0-9]*$", "", host).rstrip(".")    # port, and the dot of "see http://example.com."
    if host == "localhost":
        return host
    labels = host.split(".")
    if len(labels) < 2 or not all(HOST_LABEL_RE.match(label) for label in labels):
        return None
    return host


def clean(text):
    """A zip entry name for the terminal: untrusted names must not carry control characters."""
    return re.sub(r"[^\x20-\x7e]", "?", text)


class Found:
    """host -> {"url": seen as url at least once, "count": hits of either class, "first": path of the first hit}."""

    def __init__(self):
        self.hosts = {}
        self.notes = []
        self.total = 0

    def add(self, host, kind, path):
        item = self.hosts.get(host)
        if item is None:
            item = self.hosts[host] = {"url": False, "count": 0, "first": path}
        item["count"] += 1
        if kind == "url":
            item["url"] = True


def scan_bytes(data, path, found):
    for match in URL_RE.finditer(data):
        host = url_host(match.group(1))
        if host:
            found.add(host, "url", path)
    for match in BARE_RE.finditer(data):
        found.add(match.group(1).decode("ascii").lower(), "bare", path)


def scan_zip(archive, prefix, depth, found):
    for info in archive.infolist():
        if info.is_dir():
            continue
        path = prefix + info.filename
        if info.file_size > MAX_ENTRY or found.total + info.file_size > MAX_TOTAL:
            found.notes.append(f"skipped (too large): {clean(path)}")
            continue
        try:
            data = archive.read(info)
        except (zipfile.BadZipFile, RuntimeError, NotImplementedError, zlib.error, EOFError, OSError) as exc:
            found.notes.append(f"skipped (cannot read: {type(exc).__name__}): {clean(path)}")
            continue
        found.total += len(data)
        if data.startswith(ZIP_MAGIC):
            if depth >= MAX_DEPTH:
                found.notes.append(f"skipped (nested zip deeper than {MAX_DEPTH}): {clean(path)}")
                continue
            try:
                inner = zipfile.ZipFile(io.BytesIO(data))
            except zipfile.BadZipFile:
                inner = None                            # looks like a zip but is not one: scan it as bytes
            if inner is not None:
                with inner:
                    scan_zip(inner, path + "!/", depth + 1, found)
                continue
        scan_bytes(data, path, found)


def scan_bundle(path):
    """Found for one bundle file. Raises zipfile.BadZipFile or OSError when it cannot be opened as a zip."""
    found = Found()
    with zipfile.ZipFile(path) as archive:
        scan_zip(archive, "", 0, found)
    return found


def sort_key(host, is_url):
    return (0 if is_url else 1, host)


def kind_of(is_url):
    return "url" if is_url else "bare"


def print_table(found):
    rows = [(kind_of(item["url"]), host, str(item["count"]), clean(item["first"]))
            for host, item in sorted(found.hosts.items(), key=lambda pair: sort_key(pair[0], pair[1]["url"]))]
    header = ("class", "host", "count", "first entry path")
    widths = [max(len(row[i]) for row in rows + [header]) for i in range(3)]
    for row in [header] + rows:
        print(f"{row[0].ljust(widths[0])}  {row[1].ljust(widths[1])}  {row[2].rjust(widths[2])}  {row[3]}")
    urls = sum(1 for item in found.hosts.values() if item["url"])
    print(f"hosts: {urls} url, {len(found.hosts) - urls} bare")


def print_diff(old, new):
    is_url = {host: old.hosts.get(host, {}).get("url", False) or new.hosts.get(host, {}).get("url", False)
              for host in set(old.hosts) | set(new.hosts)}
    added = sorted(set(new.hosts) - set(old.hosts), key=lambda host: sort_key(host, is_url[host]))
    removed = sorted(set(old.hosts) - set(new.hosts), key=lambda host: sort_key(host, is_url[host]))
    for host in added:
        print(f"+ {kind_of(is_url[host])} {host}  {clean(new.hosts[host]['first'])}")
    for host in removed:
        print(f"- {kind_of(is_url[host])} {host}")
    print(f"added: {len(added)}, removed: {len(removed)}")


def load(path):
    try:
        return scan_bundle(path)
    except zipfile.BadZipFile:
        raise SystemExit(f"error: {path} is not a zip file")
    except OSError as exc:
        raise SystemExit(f"error: cannot read {path}: {exc.strerror or exc}")


def main(argv=None):
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(
        prog="bundle_hosts.py", description="List the host names inside a patch bundle, or compare two bundles.")
    parser.add_argument("bundle", help="a bundle (.mpp), or the older one when a second bundle is given")
    parser.add_argument("newer", nargs="?", help="the newer bundle: print the hosts it adds and removes")
    args = parser.parse_args(argv)
    first = load(args.bundle)
    if args.newer is None:
        print_table(first)
        found_all = [first]
    else:
        second = load(args.newer)
        print_diff(first, second)
        found_all = [first, second]
    for found in found_all:
        for note in found.notes[:10]:
            print(f"note: {note}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
# Copyright (C) 2026 xhyxfxxc
# SPDX-License-Identifier: GPL-3.0-or-later
"""Compare two zip archives entry by entry. Read-only.

    python -I tools/zipcmp.py <tested bundle> <ci artifact or release asset>

Prints a summary: identical entries, entries that differ only in line endings
(CRLF vs LF), and every other difference (listed). Exit code 0 when all code
entries (.dex, extensions/*) are byte-identical, 2 otherwise.
"""
import hashlib
import sys
import zipfile


def entries(path):
    out = {}
    with zipfile.ZipFile(path) as z:
        for info in z.infolist():
            if info.is_dir():
                continue
            out[info.filename] = z.read(info)
    return out


def is_code(name):
    return name.endswith(".dex") or name.startswith("extensions/") or name.endswith(".so")


def main():
    a_path, b_path = sys.argv[1], sys.argv[2]
    a, b = entries(a_path), entries(b_path)
    same = eol = 0
    other = []
    code_bad = []
    for name in sorted(set(a) | set(b)):
        if name not in a or name not in b:
            other.append("ONLY-%s %s" % ("B" if name not in a else "A", name))
            if is_code(name):
                code_bad.append(name)
            continue
        if a[name] == b[name]:
            same += 1
            continue
        if is_code(name):
            code_bad.append(name)
        if a[name].replace(b"\r\n", b"\n") == b[name].replace(b"\r\n", b"\n"):
            eol += 1
        else:
            other.append("DIFF %s A=%d B=%d" % (name, len(a[name]), len(b[name])))
    print("entries A=%d B=%d identical=%d eol-only=%d other=%d" % (len(a), len(b), same, eol, len(other)))
    for line in other[:25]:
        print("  " + line)
    for name in sorted(a):
        if is_code(name):
            mark = "DIFFERENT" if name in code_bad else "same"
            print("  code %-52s %9d %s %s" % (name, len(a[name]), hashlib.sha256(a[name]).hexdigest()[:16], mark))
    print("CODE IDENTICAL" if not code_bad else "CODE DIFFERS: %s" % ", ".join(code_bad))
    sys.exit(0 if not code_bad else 2)


if __name__ == "__main__":
    main()

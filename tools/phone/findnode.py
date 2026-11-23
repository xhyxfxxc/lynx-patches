# Copyright (C) 2026 xhyxfxxc
# SPDX-License-Identifier: GPL-3.0-or-later
"""Read a uiautomator dump on stdin; print 'x y | text | desc | bounds' for the first node whose text or content-desc fully matches the regex."""
import re
import sys

pattern = re.compile(sys.argv[1], re.I)
data = sys.stdin.buffer.read().decode("utf-8", "replace")
for m in re.finditer(r"<node ([^>]*?)/?>", data):
    attrs = dict(re.findall(r'([\w:-]+)="([^"]*)"', m.group(1)))
    text, desc = attrs.get("text", ""), attrs.get("content-desc", "")
    if pattern.fullmatch(text) or pattern.fullmatch(desc):
        b = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", attrs.get("bounds", ""))
        if not b:
            continue
        x1, y1, x2, y2 = map(int, b.groups())
        if x2 <= x1 or y2 <= y1:
            continue
        sys.stdout.buffer.write(("%d %d | %s | %s | %s\n" % ((x1 + x2) // 2, (y1 + y2) // 2, text[:60], desc[:60], attrs.get("bounds"))).encode("utf-8", "replace"))
        sys.exit(0)
sys.exit(1)

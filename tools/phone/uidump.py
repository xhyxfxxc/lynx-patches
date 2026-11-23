# Copyright (C) 2026 xhyxfxxc
# SPDX-License-Identifier: GPL-3.0-or-later
"""Print labelled nodes of a uiautomator XML dump read from stdin: text | content-desc | class tail | bounds."""
import re
import sys

limit = int(sys.argv[1]) if len(sys.argv) > 1 else 70
data = sys.stdin.buffer.read().decode("utf-8", "replace")
count = 0
for m in re.finditer(r"<node ([^>]*?)/?>", data):
    attrs = dict(re.findall(r'([\w:-]+)="([^"]*)"', m.group(1)))
    text = attrs.get("text", "")
    desc = attrs.get("content-desc", "")
    if not text and not desc:
        continue
    cls = attrs.get("class", "").rsplit(".", 1)[-1]
    flags = ("C" if attrs.get("clickable") == "true" else "") + ("K" if attrs.get("checked") == "true" else "")
    line = "%s | %s | %s%s | %s" % (text[:80], desc[:80], cls, (":" + flags) if flags else "", attrs.get("bounds", ""))
    sys.stdout.buffer.write((line + "\n").encode("utf-8", "replace"))
    count += 1
    if count >= limit:
        sys.stdout.buffer.write(b"... (more nodes cut)\n")
        break
if count == 0:
    sys.stdout.buffer.write(("no labelled nodes (dump length %d)\n" % len(data)).encode())

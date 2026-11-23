# Copyright (C) 2026 xhyxfxxc
# SPDX-License-Identifier: GPL-3.0-or-later
"""Write a small JPEG copy of a screenshot (width 480) so it is cheap to look at."""
import sys

from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGB")
w, h = im.size
im = im.resize((480, int(h * 480 / w)))
im.save(dst, "JPEG", quality=70)
print("saved %s (%dx%d from %dx%d)" % (dst, im.size[0], im.size[1], w, h))

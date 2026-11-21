# Copyright (C) 2026 xhyxfxxc
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Draws the Lynx Patches logo: an amber lynx head outline with a soft glow on a near-black background.

Shapes only (no fonts), so the result does not depend on what is installed. The same shapes are drawn twice:
once sharp, once on a separate layer that is blurred and composited underneath, which is the glow.

    python -I tools/make_lynx_logo.py

Writes the identical 512x512 PNG to both places the build and the audit expect it:
  patches/src/main/resources/loki/loki_logo.png   (the logo the patched apps draw)
  patches-bundle.png                              (the logo Morphe Manager shows for this source)
File names keep their upstream spelling on purpose, so merges from upstream stay cheap.
"""

import io
import math
import os

from PIL import Image, ImageDraw, ImageFilter

SIZE = 512
SUPER = 4  # draw at 4x and scale down, so every edge is anti-aliased
BACKGROUND = (0x0B, 0x0F, 0x14)
AMBER = (0xF5, 0xA5, 0x24)
STROKE = 18  # outline width in output pixels
EYE_STROKE = 10  # the eyes are small, so their outline is thinner
GLOW_RADIUS = 13  # blur radius in output pixels
GLOW_STRENGTH = 0.85

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TARGETS = (
    os.path.join(ROOT, "patches", "src", "main", "resources", "loki", "loki_logo.png"),
    os.path.join(ROOT, "patches-bundle.png"),
)

CX = SIZE / 2


def mirror(points):
    """The same points on the other side of the vertical centre line."""
    return [(2 * CX - x, y) for x, y in points]


# Face: a hexagon, flat on top and a little narrower at the chin. Listed clockwise from the left cheek.
FACE = [(72, 300), (160, 208), (352, 208), (440, 300), (322, 432), (190, 432)]

# Ears: tall pointed triangles standing on the top edge of the face, leaning slightly outwards.
LEFT_EAR = [(160, 208), (164, 88), (246, 208)]
RIGHT_EAR = mirror(LEFT_EAR)[::-1]

# Tufts: a short vertical line continuing straight up from each ear tip.
TUFT_LENGTH = 40
LEFT_TUFT = [(LEFT_EAR[1][0], LEFT_EAR[1][1]), (LEFT_EAR[1][0], LEFT_EAR[1][1] - TUFT_LENGTH)]
RIGHT_TUFT = mirror(LEFT_TUFT)

# Eyes: almond shapes, outer corners raised, so the look is narrow and watchful.
EYE_CENTER = (203, 296)
EYE_HALF_WIDTH = 32
EYE_HALF_HEIGHT = 15
EYE_TILT = 14  # degrees


def almond(center, half_width, half_height, tilt_degrees, steps=40):
    """A lens-shaped outline: two arcs that meet in points at the corners, tilted around its centre."""
    cx, cy = center
    tilt = math.radians(tilt_degrees)
    upper, lower = [], []
    for i in range(steps + 1):
        t = -1 + 2 * i / steps
        bulge = 1 - t * t
        upper.append((t * half_width, -half_height * bulge))
        lower.append((t * half_width, half_height * bulge * 0.9))
    points = upper + lower[::-1][1:-1]
    out = []
    for x, y in points:
        # Positive tilt raises the left (outer) corner of this eye.
        rx = x * math.cos(tilt) - y * math.sin(tilt)
        ry = x * math.sin(tilt) + y * math.cos(tilt)
        out.append((cx + rx, cy + ry))
    return out


LEFT_EYE = almond(EYE_CENTER, EYE_HALF_WIDTH, EYE_HALF_HEIGHT, EYE_TILT)
RIGHT_EYE = mirror(LEFT_EYE)

# A small nose: a downward triangle between and below the eyes.
NOSE = [(238, 352), (274, 352), (256, 376)]


def vertical_shift():
    """How far everything moves down so the drawing is centred in the square, stroke included."""
    ys = [y for shape in (FACE, LEFT_EAR, RIGHT_EAR, LEFT_TUFT, RIGHT_TUFT) for _, y in shape]
    return SIZE / 2 - (min(ys) - STROKE / 2 + max(ys) + STROKE / 2) / 2


def draw_polyline(draw, points, width, closed):
    """A stroked path with round joins and round ends, in supersampled coordinates."""
    pts = [(x * SUPER, y * SUPER) for x, y in points]
    if closed:
        pts = pts + [pts[0]]
    w = width * SUPER
    draw.line(pts, fill=255, width=int(round(w)), joint="curve")
    r = w / 2
    for x, y in pts:
        draw.ellipse((x - r, y - r, x + r, y + r), fill=255)


def draw_filled(draw, points):
    draw.polygon([(x * SUPER, y * SUPER) for x, y in points], fill=255)


def draw_shapes(draw, shift):
    """Every shape of the logo as white on black into one mask. Called for the sharp layer and the glow layer."""

    def moved(points):
        return [(x, y + shift) for x, y in points]

    draw_polyline(draw, moved(FACE), STROKE, closed=True)
    for ear in (LEFT_EAR, RIGHT_EAR):
        draw_polyline(draw, moved(ear), STROKE, closed=True)
    for tuft in (LEFT_TUFT, RIGHT_TUFT):
        draw_polyline(draw, moved(tuft), STROKE, closed=False)
    for eye in (LEFT_EYE, RIGHT_EYE):
        draw_filled(draw, moved(eye))
        draw_polyline(draw, moved(eye), EYE_STROKE / 2, closed=True)
    draw_filled(draw, moved(NOSE))
    draw_polyline(draw, moved(NOSE), EYE_STROKE / 2, closed=True)


def render():
    big = SIZE * SUPER
    shift = vertical_shift()

    sharp = Image.new("L", (big, big), 0)
    draw_shapes(ImageDraw.Draw(sharp), shift)

    glow = Image.new("L", (big, big), 0)
    draw_shapes(ImageDraw.Draw(glow), shift)
    glow = glow.filter(ImageFilter.GaussianBlur(GLOW_RADIUS * SUPER))
    glow = glow.point(lambda v: min(255, int(v * 2.2 * GLOW_STRENGTH)))

    canvas = Image.new("RGB", (big, big), BACKGROUND)
    amber = Image.new("RGB", (big, big), AMBER)
    canvas = Image.composite(amber, canvas, glow)  # the glow goes down first
    canvas = Image.composite(amber, canvas, sharp)  # the sharp shapes on top
    return canvas.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    image = render()
    buffer = io.BytesIO()
    image.save(buffer, format="PNG", optimize=True)
    data = buffer.getvalue()
    for path in TARGETS:
        with open(path, "wb") as handle:
            handle.write(data)
        print(f"wrote {os.path.relpath(path, ROOT)} ({len(data)} bytes)")


if __name__ == "__main__":
    main()

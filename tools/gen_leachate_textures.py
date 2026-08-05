"""Generate the animated leachate fluid sprites.

Fluid sprites are animation frame strips (vanilla water_still is 16x512, 32 frames of 16x16;
water_flow is 32x1024, 32 frames of 32x32), which is a shape texgen does not produce - texgen
emits single 16px stills. So leachate's art is generated here instead, and this script is the
source of truth for it: rerun it to change the look, never edit the PNGs by hand.

The pattern is a sum of sinusoids whose frequencies are whole numbers of cycles across the
sprite in x, y and t. That makes it exactly periodic on all three axes, so the texture tiles
against itself horizontally and vertically AND the animation loops with no seam - which a
noise function sampled per-frame will not do.

Usage:  python tools/gen_leachate_textures.py
"""

import math
import os

from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "recompile", "textures", "block")

FRAMES = 32

# Murky landfill runoff: olive-brown, nearly black in the troughs, with a thin sickly-green
# scum on the crests. Deliberately far from water's blue and from coarse dirt's tan, since a
# pool has to read as "not water" at a glance and against the ground it sits in.
PALETTE = [
    (0x13, 0x14, 0x0D),
    (0x1A, 0x1B, 0x11),
    (0x22, 0x23, 0x16),
    (0x2A, 0x2B, 0x1B),
    (0x33, 0x34, 0x20),
    (0x3C, 0x3E, 0x26),
    (0x46, 0x4A, 0x2C),
    (0x51, 0x57, 0x33),
]

# (x cycles, y cycles, t cycles, amplitude, phase). Mixed signs and coprime-ish frequencies
# keep the drift from resolving into an obvious marching grid.
STILL_WAVES = [
    (1, 1, 1, 1.00, 0.00),
    (2, -1, 1, 0.55, 1.70),
    (-1, 2, 2, 0.40, 3.10),
    (3, 2, -1, 0.25, 0.60),
    (2, 3, 2, 0.18, 4.40),
]

# The flow sprite reads as running downhill, so y frequencies dominate and every wave advances
# in the same temporal direction rather than milling about.
FLOW_WAVES = [
    (0, 1, 1, 1.00, 0.00),
    (1, 2, 1, 0.45, 2.20),
    (-1, 3, 1, 0.30, 0.90),
    (2, 4, 2, 0.20, 3.60),
]


def sample(waves, u, v, t):
    """Sum the wave set at normalised position (u, v) and normalised time t, in [-1, 1]."""
    total = 0.0
    weight = 0.0
    for fx, fy, ft, amp, phase in waves:
        total += amp * math.sin(2.0 * math.pi * (fx * u + fy * v + ft * t) + phase)
        weight += amp
    return total / weight


def strip(size, waves, path):
    """Write a vertical frame strip of `FRAMES` square frames, `size` px on a side."""
    image = Image.new("RGB", (size, size * FRAMES))
    pixels = image.load()
    for frame in range(FRAMES):
        t = frame / FRAMES
        for y in range(size):
            for x in range(size):
                value = sample(waves, x / size, y / size, t)
                # -1..1 -> palette index. Rounded rather than floored so the extremes get
                # half-width bands like every other level, instead of appearing half as often.
                index = int(round((value + 1.0) / 2.0 * (len(PALETTE) - 1)))
                pixels[x, frame * size + y] = PALETTE[max(0, min(len(PALETTE) - 1, index))]
    image.save(path)
    return image.size


def mcmeta(path, frametime):
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write('{\n  "animation": {\n    "frametime": %d\n  }\n}\n' % frametime)


def main():
    os.makedirs(OUT, exist_ok=True)

    still = os.path.join(OUT, "leachate_still.png")
    flow = os.path.join(OUT, "leachate_flow.png")

    # Slower than water's frametime of 2: leachate is viscous, and the fluid type says so too.
    print("leachate_still.png", strip(16, STILL_WAVES, still))
    mcmeta(still + ".mcmeta", 4)

    print("leachate_flow.png ", strip(32, FLOW_WAVES, flow))
    mcmeta(flow + ".mcmeta", 3)

    mean = sum(sum(c) / 3 for c in PALETTE) / len(PALETTE)
    print("mean palette luma %.1f (coarse dirt is 90.4, mound ground 66.4)" % mean)


if __name__ == "__main__":
    main()

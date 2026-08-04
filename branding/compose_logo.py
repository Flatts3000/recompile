"""Draw the Recompile logo - a sprout coming out of a scrap pile - and stamp REC across it.

Output: gen/logo_<variant>.png plus a 64px thumbnail for review, and the APPROVED variant
        written straight to both places it has to live: branding/logo.png (the artifact
        uploaded to CurseForge and Modrinth as the project AVATAR) and
        src/main/resources/logo.png (what ships in the jar for the in-game mod list).
        Writing both here rather than copying by hand is the point: two copies of an image
        drift, and the one that drifts is always the one nobody looks at.

**NOTHING HERE IS AI-GENERATED, AND THAT IS A CONTEST RULE** (2026-08-04). ModJam 2026 states
that "AI-generated project avatars and gallery images are not allowed", and this file used to
pixelate a painterly AI render (gen/logo_src.png) into the mark - which put a disqualifying
asset on the project page. The artwork is now drawn from geometry in `draw_artwork`, so the
avatar is provably not AI output and the claim survives someone checking. The gallery is real
in-game screenshots and was never at risk.

The composition is unchanged, because the composition was never the problem: a green sprout
rising out of a pile of junk is the mod's pitch in one image.

Three decisions worth knowing:

  * Everything is drawn ON THE LOW-RES GRID, before the upscale, so every edge lands exactly
    on a pixel boundary. Drawing at full size and downscaling produces anti-aliased fringes on
    a grid that has no room for them - the same reason the Puzzle Cube's faces, the Luggage
    sprites and the pigeon's skin are procedural rather than AI.
  * The leaves are rotated ellipses rather than a hand-typed bitmap. A leaf is a smooth convex
    shape and the maths gives a cleaner edge at this size than counting cells does; the pile
    underneath is the opposite case and is deliberately noisy.
  * GRID divides the output size exactly. 50 into 400 gives 8px cells. A grid that does not
    divide evenly (64 into 400) forces a non-integer cell and the pixels come out uneven,
    which is visible and looks like a mistake rather than a style.

Run:
    python branding/compose_logo.py
"""

from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image

HERE = Path(__file__).parent
REPO = HERE.parent
BRANDING = HERE / "logo.png"
IN_JAR = REPO / "src" / "main" / "resources" / "logo.png"

# Which variant ships. The rest are generated for comparison only.
APPROVED = "bottom"

# ---- design constants ---------------------------------------------------
OUT_SIZE = 400          # CurseForge/Modrinth project logo, square
GRID = 50               # low-res cells across; must divide OUT_SIZE exactly

# The palette, chosen rather than sampled. Every colour is here so the mark survives the 64px
# thumbnail: the greens are separated from the rusts by hue AND by value, because a thumbnail
# loses hue first.
SKY_TOP = (58, 56, 40)
SKY_BOTTOM = (30, 29, 21)
PILE = [(107, 74, 42), (125, 90, 51), (74, 58, 38), (138, 106, 63), (90, 90, 84), (110, 110, 102)]
PILE_SHADOW = (44, 35, 24)
STEM = (111, 156, 47)
LEAF_LIGHT = (184, 220, 85)
LEAF_MID = (140, 190, 58)
LEAF_DARK = (93, 138, 34)

TEXT = "REC"
LETTER_SCALE = 2        # cells per font pixel. 3 letters at 6x7 + gaps = 20 * scale wide.
TEXT_FILL = (245, 245, 238)
TEXT_SHADOW = (18, 16, 12)

# 6x7 blocky face, hand-set. Wide strokes so the letters hold at thumbnail size.
GLYPHS = {
    "R": [
        "XXXXX.",
        "X....X",
        "X....X",
        "XXXXX.",
        "X..X..",
        "X...X.",
        "X....X",
    ],
    "E": [
        "XXXXXX",
        "X.....",
        "X.....",
        "XXXXX.",
        "X.....",
        "X.....",
        "XXXXXX",
    ],
    "C": [
        ".XXXX.",
        "X....X",
        "X.....",
        "X.....",
        "X.....",
        "X....X",
        ".XXXX.",
    ],
}

# Row on the low-res grid where the wordmark's top edge sits, per variant.
# Bottom only. The sprout owns rows 5-22 and it is the whole idea of the mark - green coming
# out of the scrap - so anything stamped over it removes the thing the logo is about. The pile
# is the part that can carry text.
VARIANTS = {
    "bottom": 33,
}


def draw_artwork() -> Image.Image:
    """The mark itself, drawn on the GRID x GRID cells. Seeded, so it is the same every run."""
    rng = random.Random(20260804)
    img = Image.new("RGB", (GRID, GRID))
    px = img.load()

    # Sky: a plain vertical ramp, darkest at the bottom so the pile has something to sit against.
    for y in range(GRID):
        t = y / (GRID - 1)
        px_row = tuple(round(SKY_TOP[i] + (SKY_BOTTOM[i] - SKY_TOP[i]) * t) for i in range(3))
        for x in range(GRID):
            px[x, y] = px_row

    # THE PILE. A mound profile, then junk scattered over it in 2x1 chunks - deliberately noisy,
    # because a pile of scrap that reads as a smooth hill reads as a hill.
    def crest(x: int) -> int:
        edge = abs(x - (GRID / 2 - 0.5)) / (GRID / 2)
        return int(26 + 11 * edge * edge)

    for x in range(GRID):
        top = crest(x)
        for y in range(top, GRID):
            px[x, y] = PILE_SHADOW
    for _ in range(340):
        x = rng.randrange(0, GRID - 1)
        y = rng.randrange(crest(x), GRID)
        if y < crest(x) or y < crest(x + 1):
            continue
        colour = PILE[rng.randrange(len(PILE))]
        for dx in range(rng.choice((1, 2, 2, 3))):
            if x + dx < GRID and y >= crest(x + dx):
                px[x + dx, y] = colour

    # THE SPROUT. Stem first so the leaves overlap it rather than the other way round.
    stem_x, stem_top, stem_base = GRID // 2 - 1, 13, 30
    for y in range(stem_top, stem_base):
        for x in (stem_x, stem_x + 1):
            px[x, y] = STEM

    def leaf(cx: float, cy: float, major: float, minor: float, degrees: float) -> None:
        rad = math.radians(degrees)
        cos, sin = math.cos(rad), math.sin(rad)
        cells = []
        for y in range(GRID):
            for x in range(GRID):
                ox, oy = x - cx, y - cy
                u = ox * cos + oy * sin
                v = -ox * sin + oy * cos
                if (u / major) ** 2 + (v / minor) ** 2 <= 1.0:
                    cells.append((x, y, v))
        inside = {(x, y) for x, y, _ in cells}
        for x, y, v in cells:
            # Light along the midrib, mid over the body, dark on the outer half - one light
            # source, so the two leaves do not read as flat cut-outs.
            px[x, y] = LEAF_LIGHT if abs(v) < 0.9 else (LEAF_MID if v < 0 else LEAF_DARK)
        for x, y, _ in cells:
            if any((x + dx, y + dy) not in inside
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = LEAF_DARK

    # Sign matters and is easy to get backwards: y grows DOWNWARD, so the angle that makes a
    # leaf rise to the left is the POSITIVE one. Getting it the other way round drew a pair of
    # leaves drooping off the stem like an umbrella, which reads as a palm rather than a sprout.
    leaf(GRID / 2 - 6.5, 12.5, 8.0, 4.2, 32.0)
    leaf(GRID / 2 + 5.5, 11.5, 8.0, 4.2, -32.0)
    return img


def stamp(small: Image.Image, top_row: int) -> Image.Image:
    """Draw the wordmark onto the low-res grid, shadow first."""
    out = small.copy()
    px = out.load()

    glyph_w, glyph_h = 6, 7
    gap = 1
    total = len(TEXT) * glyph_w * LETTER_SCALE + (len(TEXT) - 1) * gap * LETTER_SCALE
    left = (GRID - total) // 2

    def draw(dx: int, dy: int, colour: tuple[int, int, int]) -> None:
        x0 = left + dx
        for index, char in enumerate(TEXT):
            rows = GLYPHS[char]
            ox = x0 + index * (glyph_w + gap) * LETTER_SCALE
            for row in range(glyph_h):
                for col in range(glyph_w):
                    if rows[row][col] != "X":
                        continue
                    for sy in range(LETTER_SCALE):
                        for sx in range(LETTER_SCALE):
                            x = ox + col * LETTER_SCALE + sx
                            y = top_row + dy + row * LETTER_SCALE + sy
                            if 0 <= x < GRID and 0 <= y < GRID:
                                px[x, y] = colour

    # A one-cell offset shadow in every direction is an outline, which is what keeps the
    # letters legible over both the dark background and the pale rust.
    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, -1), (-1, 1), (1, 1)):
        draw(dx, dy, TEXT_SHADOW)
    draw(0, 0, TEXT_FILL)
    return out


def main() -> None:
    small = draw_artwork()
    scale = OUT_SIZE // GRID
    assert GRID * scale == OUT_SIZE, "GRID must divide OUT_SIZE exactly"

    plain = small.resize((OUT_SIZE, OUT_SIZE), Image.NEAREST)
    plain.save(REPO / "gen" / "logo_pixel_only.png")
    print("wrote logo_pixel_only.png")

    gen = REPO / "gen"
    for name, row in VARIANTS.items():
        stamped = stamp(small, row).resize((OUT_SIZE, OUT_SIZE), Image.NEAREST)
        stamped.save(gen / f"logo_{name}.png")
        # The 64px thumbnail is how most people meet a mod, in a list. If the mark does not
        # survive this it does not matter how it looks at 400.
        stamped.resize((64, 64), Image.NEAREST).save(gen / f"logo_{name}_64.png")
        print(f"wrote gen/logo_{name}.png and its 64px thumbnail")
        if name == APPROVED:
            stamped.save(BRANDING)
            stamped.save(IN_JAR)
            print(f"  approved: also wrote {BRANDING} and {IN_JAR}")


if __name__ == "__main__":
    main()

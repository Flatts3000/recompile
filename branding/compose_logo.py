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

The composition is a green sprout rising out of a pile of junk - the mod's pitch in one image - with
the RECOMPILE wordmark across the pile. The wordmark is `branding/wordmark_single_row.png`, rendered in
the Minecraft Title Generator (see `docs/branding.md`); this file only places it. That replaced a
hand-stamped `REC` bitmap, which is why the glyph table below is gone.

**The full name costs thumbnail legibility and that is a deliberate trade** (owner, 2026-08-04). Nine
glyphs across a 64px icon is about six pixels a letter, where the old three-letter stamp stayed
readable. The mark is scaled and placed for the 400px view; at 64 it reads as a sprout over a coloured
bar, which is a recognisable silhouette even when the word is not.

Three decisions worth knowing:

  * Everything is drawn ON THE LOW-RES GRID, before the upscale, so every edge lands exactly
    on a pixel boundary. Drawing at full size and downscaling produces anti-aliased fringes on
    a grid that has no room for them - the same reason the Puzzle Cube's faces, the Luggage
    sprites and the pigeon's skin are procedural rather than AI.
  * The leaves are rotated ellipses rather than a hand-typed bitmap. A leaf is a smooth convex
    shape and the maths gives a cleaner edge at this size than counting cells does; the pile
    underneath is the opposite case and is deliberately noisy.
  * The wordmark is composited AFTER the upscale, at full resolution, rather than being forced onto
    the 50x50 grid. It is a 3D render with real shading - quantising it to the grid would destroy the
    bevel that makes it read as the Minecraft title font, which is the whole reason it exists.
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

WORDMARK = HERE / "wordmark_single_row.png"
WORDMARK_WIDTH_PCT = 0.92   # of the canvas, after trimming the render's transparent padding
WORDMARK_BOTTOM_PCT = 0.07  # gap under it, as a fraction of the canvas

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


def place_wordmark(canvas: Image.Image) -> Image.Image:
    """Composite the rendered wordmark across the bottom of the mark.

    Trimmed to its glyphs first, so placement follows the letters rather than whatever transparent
    canvas the renderer emitted - the render is 2048x352 and the padding is not symmetric.
    """
    out = canvas.convert("RGBA")
    mark = Image.open(WORDMARK).convert("RGBA")
    box = mark.getbbox()
    if box:
        mark = mark.crop(box)

    width = round(out.width * WORDMARK_WIDTH_PCT)
    height = max(1, round(mark.height * width / mark.width))
    # LANCZOS, not NEAREST: this is a shaded 3D render being made smaller, which is the one case in
    # this file where a smooth resample is right. NEAREST would drop whole rows of the bevel.
    mark = mark.resize((width, height), Image.LANCZOS)

    x = (out.width - width) // 2
    y = out.height - height - round(out.height * WORDMARK_BOTTOM_PCT)
    out.alpha_composite(mark, (x, y))
    return out.convert("RGB")


def main() -> None:
    small = draw_artwork()
    scale = OUT_SIZE // GRID
    assert GRID * scale == OUT_SIZE, "GRID must divide OUT_SIZE exactly"
    assert WORDMARK.exists(), f"missing {WORDMARK} - render it per docs/branding.md"

    gen = REPO / "gen"
    gen.mkdir(exist_ok=True)

    art = small.resize((OUT_SIZE, OUT_SIZE), Image.NEAREST)
    art.save(gen / "logo_artwork_only.png")
    print("wrote gen/logo_artwork_only.png")

    logo = place_wordmark(art)
    logo.save(gen / "logo.png")
    # The 64px thumbnail is how most people meet a mod, in a list. It is written every run so the
    # cost of the full wordmark stays visible rather than being something you find out later.
    logo.resize((64, 64), Image.LANCZOS).save(gen / "logo_64.png")
    print("wrote gen/logo.png and its 64px thumbnail")

    logo.save(BRANDING)
    logo.save(IN_JAR)
    print(f"  also wrote {BRANDING} and {IN_JAR}")


if __name__ == "__main__":
    main()

"""Pixelate the Recompile logo and stamp REC across it.

Input:  gen/logo_src.png  (the painterly AI render; gen/ is gitignored, so pull it from the
        CurseForge project avatar - project id 1625740 - if you need to regenerate)
Output: gen/logo_<variant>.png plus a 64px thumbnail for review, and the APPROVED variant
        written straight to both places it has to live: branding/logo.png (the artifact
        uploaded to CurseForge and Modrinth) and src/main/resources/logo.png (what ships in
        the jar for the in-game mod list). Writing both here rather than copying by hand is
        the point: two copies of an image drift, and the one that drifts is always the one
        nobody looks at.

The source is a soft-focus digital painting, which reads as generic AI art next to a
Minecraft mod list and turns to mush at the 64x64 CurseForge thumbnail. This pixelates it
onto a real grid and stamps a wordmark that survives that thumbnail.

Two decisions worth knowing:

  * The letters are drawn from hand-defined bitmaps on the LOW-RES grid, before the
    upscale, so every letter edge lands exactly on a pixel boundary. A TTF rendered at
    full size and then downscaled would produce anti-aliased fringes on a grid that has no
    room for them - the same reason the Puzzle Cube's faces are procedural rather than AI.
  * GRID divides the output size exactly. 50 into 400 gives 8px cells. A grid that does not
    divide evenly (64 into 400) forces a non-integer cell and the pixels come out uneven,
    which is visible and looks like a mistake rather than a style.

Run:
    python branding/compose_logo.py
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageEnhance

HERE = Path(__file__).parent
REPO = HERE.parent
SRC = REPO / "gen" / "logo_src.png"
BRANDING = HERE / "logo.png"
IN_JAR = REPO / "src" / "main" / "resources" / "logo.png"

# Which variant ships. The rest are generated for comparison only.
APPROVED = "bottom"

# ---- design constants ---------------------------------------------------
OUT_SIZE = 400          # CurseForge/Modrinth project logo, square
GRID = 50               # low-res cells across; must divide OUT_SIZE exactly
PALETTE = 28            # colour cap. Low enough to read as pixel art, high enough for
                        # the sprout's greens and the rust to stay separate.
SATURATION = 1.25       # the source is muddy; lift it so it survives a 64px thumbnail
CONTRAST = 1.12

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


def pixelate(src: Image.Image) -> Image.Image:
    """Down to the grid, palette-capped, and back up with hard edges."""
    img = src.convert("RGB")
    img = ImageEnhance.Color(img).enhance(SATURATION)
    img = ImageEnhance.Contrast(img).enhance(CONTRAST)
    # BOX averages the block it collapses, so the low-res image keeps the source's tones
    # rather than sampling one arbitrary pixel out of each cell.
    small = img.resize((GRID, GRID), Image.BOX)
    return small.quantize(colors=PALETTE, method=Image.MEDIANCUT).convert("RGB")


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
    src = Image.open(SRC)
    small = pixelate(src)
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

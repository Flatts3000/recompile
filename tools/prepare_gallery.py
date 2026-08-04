"""Crop the raw showcase shots to their subject and fit them under CurseForge's 2 MB limit.

    python tools/prepare_gallery.py

Reads `run/screenshots/<scene>.png` (whatever `shoot_scenes.py` last took) and writes the numbered
files in `docs/cf image gallery/`. The raw captures stay where they are: `_originals/` in that folder
is deliberately untracked, and `run/` is gitignored, so this is the step that puts an image *in the
project* rather than in a scratch directory.

Two rules it enforces rather than trusts:

**The reclamation pair shares one crop.** The whole point of that pair is that the two frames differ
only in the plot, and cropping them separately would reintroduce exactly the difference the identical
camera was there to remove. They read their box from the same constant and a test asserts it.

**Nothing leaves over 2 MB.** CurseForge rejects a larger gallery image, and finding that out in the
upload dialog is a bad time to find it out. PNG first because these are flat-coloured screenshots and
it usually wins; JPEG at descending quality only if PNG will not fit.
"""

from __future__ import annotations

import io
from pathlib import Path

from PIL import Image

REPO = Path(__file__).parent.parent
SHOTS = REPO / "run" / "screenshots"
GALLERY = REPO / "docs" / "cf image gallery"

LIMIT = 2 * 1024 * 1024   # CurseForge's per-image ceiling

# Crop boxes as fractions of the frame: (left, top, right, bottom). Fractions rather than pixels so a
# change of window size does not silently reframe every image.
FULL = (0.0, 0.0, 1.0, 1.0)
# One box for both reclamation frames. Do not split this into two constants.
RECLAIM_CROP = (0.07, 0.09, 0.93, 0.80)

# The numbers are the gallery's ORDER, and the order is an argument: theme first, because Theme Fit is
# the pillar this entry is weakest on and a judge skims the strip before reading a word. See
# ../mod-jam-2026/round_1_rewards_analysis.md. The rest of the gallery is numbered around these, so
# changing a number here means renumbering there too.
PLAN = [
    ("museum", 1, (0.19, 0.10, 0.81, 1.0)),
    ("reclaim_before", 2, RECLAIM_CROP),
    ("reclaim_after", 3, RECLAIM_CROP),
    ("machine_wall", 7, (0.17, 0.20, 0.83, 1.0)),
]


def crop(image: Image.Image, box: tuple[float, float, float, float]) -> Image.Image:
    w, h = image.size
    left, top, right, bottom = box
    return image.crop((round(w * left), round(h * top), round(w * right), round(h * bottom)))


def encode(image: Image.Image) -> tuple[bytes, str]:
    """Smallest acceptable encoding under the limit, preferring PNG."""
    buffer = io.BytesIO()
    image.save(buffer, format="PNG", optimize=True)
    if buffer.tell() <= LIMIT:
        return buffer.getvalue(), "png"

    for quality in (95, 92, 88, 84, 80):
        buffer = io.BytesIO()
        image.convert("RGB").save(buffer, format="JPEG", quality=quality, optimize=True)
        if buffer.tell() <= LIMIT:
            return buffer.getvalue(), "jpg"
    raise SystemExit("could not fit an image under 2 MB even at quality 80")


def main() -> None:
    # The pair must be cropped identically, and the cheapest way to be sure is to refuse to run if
    # somebody has given them different boxes.
    boxes = {name: box for name, _, box in PLAN}
    assert boxes["reclaim_before"] == boxes["reclaim_after"], \
        "the reclamation pair must share one crop, or the comparison is not honest"

    for name, number, box in PLAN:
        source = SHOTS / f"{name}.png"
        if not source.is_file():
            print(f"{name}: no capture at {source}, skipped")
            continue

        image = crop(Image.open(source), box)
        data, extension = encode(image)

        # Only clear this scene's own file, matched by name as well as number. Globbing on the
        # number alone would delete whatever else happened to hold that slot.
        stem = f"{number:02d}-{name.replace('_', '-')}"
        for stale in GALLERY.glob(f"{stem}.*"):
            stale.unlink()
        out = GALLERY / f"{stem}.{extension}"
        out.write_bytes(data)
        print(f"{out.name}: {image.size[0]}x{image.size[1]}, {len(data) // 1024} KB")


if __name__ == "__main__":
    main()

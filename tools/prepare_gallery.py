"""Crop the raw showcase shots to their subject and fit them under CurseForge's 2 MB limit.

    python tools/prepare_gallery.py museum machine_wall

Reads `run/screenshots/<scene>.png` (whatever `shoot_scenes.py` last took) and writes the numbered
files in `docs/cf image gallery/`. The raw captures stay where they are: `_originals/` in that folder
is deliberately untracked, and `run/` is gitignored, so this is the step that puts an image *in the
project* rather than in a scratch directory.

**IT WILL NOT RUN OVER EVERYTHING.** Naming at least one scene is required, and that is a guard rather
than an interface preference: the numbers in PLAN are the gallery's upload order and several have
drifted from what is actually uploaded, so a run over the whole list writes files into slots that are
already occupied by something else. It deletes only the number-and-name it is about to write, so a
duplicate lands quietly beside the original instead of replacing it. Until the numbers are reconciled
against the live gallery, the safe unit of work is the scene you just re-shot.

Three rules it enforces rather than trusts:

**The reclamation pair shares one crop.** The whole point of that pair is that the two frames differ
only in the plot, and cropping them separately would reintroduce exactly the difference the identical
camera was there to remove. They read their box from the same constant and a test asserts it.

**A name that matches nothing is an error.** The output files are hyphenated and the scene names are
not, so `sewer-corridor` is the natural typo; unvalidated it matched no scene, printed nothing, and
exited 0, which is indistinguishable from a run that worked.

**Nothing leaves over 2 MB.** CurseForge rejects a larger gallery image, and finding that out in the
upload dialog is a bad time to find it out. PNG first because these are flat-coloured screenshots and
it usually wins; JPEG at descending quality only if PNG will not fit.
"""

from __future__ import annotations

import io
import sys
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
    # RECONCILED against the live gallery 2026-08-30. These four had drifted: museum and machine_wall
    # named numbers that other images hold, and the reclamation pair claimed 2 and 3 while not being in
    # the gallery at all. The pair is being uploaded (owner), and it is appended rather than inserted -
    # a number here is the upload order, so putting the pair early would mean re-uploading every image
    # after it to keep the strip in step.
    ("museum", 2, (0.19, 0.10, 0.81, 1.0)),
    ("machine_wall", 3, (0.17, 0.20, 0.83, 1.0)),
    ("reclaim_before", 19, RECLAIM_CROP),
    ("reclaim_after", 20, RECLAIM_CROP),
    # The sewers and the radioactive dump, appended rather than interleaved: a number here is the
    # gallery's order, and inserting one in the middle means re-uploading every image after it.
    ("sewer_corridor", 14, FULL),
    ("sewer_sump", 15, (0.12, 0.04, 0.88, 0.78)),
    ("sewer_den", 16, FULL),
    ("radioactive_dump", 17, FULL),
    ("radioactive_museum", 18, (0.06, 0.26, 0.94, 1.0)),
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

    known = {name for name, _, _ in PLAN}
    wanted = set(sys.argv[1:])
    if not wanted:
        raise SystemExit(
            "name at least one scene: python tools/prepare_gallery.py <scene> [<scene> ...]\n"
            "  scenes: " + ", ".join(sorted(known)) + "\n"
            "A run over all of them writes into gallery slots that are already occupied - see the "
            "module docstring.")
    unknown = wanted - known
    if unknown:
        raise SystemExit("not a scene: " + ", ".join(sorted(unknown)) + "\n  scenes: "
                         + ", ".join(sorted(known)))
    # THE PAIR IS ONE UNIT. Naming half of it would rewrite one frame from a new capture and leave the
    # other from the old one, which is the identical-camera guarantee gone - the same invariant the
    # assert above protects, defeated from the other side.
    pair = {"reclaim_before", "reclaim_after"}
    if wanted & pair:
        wanted |= pair
    for name, number, box in PLAN:
        if wanted and name not in wanted:
            continue
        source = SHOTS / f"{name}.png"
        if not source.is_file():
            print(f"{name}: no capture at {source}, skipped")
            continue

        image = crop(Image.open(source), box)
        data, extension = encode(image)

        # Only clear this scene's own file, matched by name as well as number. Globbing on the
        # number alone would delete whatever else happened to hold that slot.
        stem = f"{number:02d}-{name.replace('_', '-')}"
        # AND REFUSE IF SOMETHING ELSE HOLDS THE SLOT. Not deleting a stranger's file is only half the
        # job: writing anyway leaves two images numbered the same, which is a duplicate in the gallery
        # rather than a replacement, and nothing says so. This is the drift in PLAN's numbers made
        # visible at the moment it would do damage, rather than described in a comment.
        held = [q for q in GALLERY.glob(f"{number:02d}-*") if q.stem != stem]
        if held:
            raise SystemExit(
                f"slot {number:02d} is held by {held[0].name}, not by {stem}. PLAN's number for "
                f"'{name}' does not match the gallery. Reconcile the number before re-running; "
                f"writing would add a second image under the same number rather than replace one.")
        for stale in GALLERY.glob(f"{stem}.*"):
            stale.unlink()
        out = GALLERY / f"{stem}.{extension}"
        out.write_bytes(data)
        print(f"{out.name}: {image.size[0]}x{image.size[1]}, {len(data) // 1024} KB")


if __name__ == "__main__":
    main()

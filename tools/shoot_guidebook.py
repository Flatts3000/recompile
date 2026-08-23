#!/usr/bin/env python3
"""Open every guidebook category and entry in the dev client and screenshot it.

**The guidebook is the mod's least-covered surface, and this is the only layer that can see it.**
`GuidebookTests` proves a lang key exists, an icon resolves to a registered item, and a paragraph
break is well-formed. None of that is the same as *the page draws*. #241 is what the gap costs: every
paragraph break in all 71 text pages was swallowed for several releases while those tests passed
throughout, because welded paragraphs are perfectly valid data.

Usage, with `./gradlew runClient` already up:

    python tools/shoot_guidebook.py                 # every category, every entry
    python tools/shoot_guidebook.py --category depths

Shots land in `run/screenshots/book_<category>.png` and `book_<category>_<entry>.png`. Exits non-zero
if the book will not open, if a category draws nothing, or if any entry fails to open.

Needs devbridge with the `use` verb (Flatts3000/devbridge#60). Before it, opening the book at all was
impossible from outside: `click` drives a screen that is already up, and the only way in was
synthesizing an OS-level mouse event, which needs the game window foregrounded and silently does
nothing when it is not. That is why #259 sat blocked.

Two things about the geometry, both measured against a running client:

  * **Category buttons are real widgets** and `screen` reports a click point for each, so the rail is
    never computed here. Prefer that everywhere it is possible.
  * **Entry nodes are NOT widgets.** Modonomicon draws them itself, so `screen` cannot see them and
    their positions have to come from the book data: every entry JSON carries a grid `x`/`y`, and the
    screen places them on a fixed lattice. The mapping below was measured, and it is checked rather
    than trusted - every predicted click must actually open an entry screen, so a calibration that
    goes stale fails loudly instead of quietly photographing empty parchment.
"""
from __future__ import annotations

import argparse
import json
import pathlib
import sys
import time

try:
    from gamebridge.devbridge import DevBridge
except ImportError:
    sys.exit("gamebridge is not installed. It ships with the devbridge mod it talks to:\n"
             '  pip install "gamebridge @ git+https://github.com/Flatts3000/devbridge.git'
             '#subdirectory=gamebridge"')

REPO = pathlib.Path(__file__).resolve().parent.parent
BOOKS = REPO / "src/main/resources/data/recompile/modonomicon/books/guide"
PORT = 8605          # claimed for this repo; see CLAUDE.md on why there is no default
INSTANCE = REPO / "run"

BOOK_ITEM = 'modonomicon:modonomicon[modonomicon:book_id="recompile:guide"]'

# The entry lattice, in GUI-scaled units. Measured: the depths' entries carry grid x=0..6 at y=1 and
# landed on screen x=212..340, y=149. Checked rather than trusted - see the module docstring.
NODE_ORIGIN_X = 212.0
NODE_ORIGIN_Y = 149.0 - 21.333      # grid y=1 sat at 149
NODE_STEP = 21.333

# Where the back affordance sits on an entry screen. Also measured.
BACK_X, BACK_Y = 455.0, 250.0


def categories() -> list[tuple[int, str]]:
    """Every category, in the order the rail draws them - which is by sort_number."""
    found = []
    for path in sorted((BOOKS / "categories").glob("*.json")):
        body = json.loads(path.read_text(encoding="utf-8"))
        found.append((body.get("sort_number", 0), path.stem))
    found.sort()
    return [(index, name) for index, (_, name) in enumerate(found)]


def entries(category: str) -> list[tuple[int, int, str]]:
    """Grid positions of a category's entries, from the book data."""
    found = []
    for path in sorted((BOOKS / "entries" / category).glob("*.json")):
        body = json.loads(path.read_text(encoding="utf-8"))
        if "x" in body and "y" in body:
            found.append((body["x"], body["y"], path.stem))
    return found


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--category", default=None, help="only this category")
    parser.add_argument("--port", type=int, default=PORT)
    args = parser.parse_args()
    sys.stdout.reconfigure(line_buffering=True)

    wanted = categories()
    if args.category:
        wanted = [(i, n) for i, n in wanted if n == args.category]
        if not wanted:
            sys.exit(f"no category named {args.category!r} in {BOOKS / 'categories'}")

    failures: list[str] = []
    with DevBridge(port=args.port, timeout=120) as game:
        # Assert what answered before anything else. Every project that keeps devbridge's default
        # port lands on one socket, and a verifier that connects to the wrong game reports a clean
        # pass about somebody else's world.
        game.ping(expect_instance=str(INSTANCE))
        game.hud(False)
        for command in ["gamemode creative", "time set noon", "gamerule advance_time false",
                        "clear @s", f"give @s {BOOK_ITEM}"]:
            game.command(command, player="@s")
        time.sleep(1.0)

        # target=item, not auto: the player may be standing in front of anything, and a block that
        # answers the right-click would win and the book would never open.
        opened = game.use(target="item")
        if not opened.get("openedScreen"):
            sys.exit(f"the guide did not open: {opened}")
        print(f"opened {opened['screen'].split('.')[-1]}")

        rail = [w for w in (game.screen().get("widgets") or [])
                if w.get("type") == "CategoryButton"]
        print(f"{len(rail)} category buttons on the rail, {len(categories())} in the data")
        if len(rail) != len(categories()):
            failures.append(f"the rail draws {len(rail)} categories against {len(categories())} "
                            f"in the data - one is missing or one is drawn that should not be")

        for index, name in wanted:
            if index >= len(rail):
                failures.append(f"{name}: no button at rail index {index}")
                continue
            game.click(rail[index]["centerX"], rail[index]["centerY"])
            time.sleep(1.0)
            game.screenshot(f"book_{name}")

            found = entries(name)
            print(f"{name}: {len(found)} entries in the data")
            for gx, gy, entry in found:
                px = NODE_ORIGIN_X + NODE_STEP * gx
                py = NODE_ORIGIN_Y + NODE_STEP * gy
                game.click(px, py)
                time.sleep(1.0)
                now = (game.screen().get("screen") or "").split(".")[-1]
                if "Entry" not in now:
                    # THE CALIBRATION IS CHECKED, NOT TRUSTED. A miss here means the lattice moved,
                    # and saying so beats photographing empty parchment and reporting a pass.
                    failures.append(f"{name}/{entry}: grid ({gx},{gy}) predicted ({px:.0f},{py:.0f}) "
                                    f"opened {now or 'nothing'}, not an entry screen")
                    continue
                game.screenshot(f"book_{name}_{entry}")
                print(f"  {entry}")
                game.click(BACK_X, BACK_Y)
                time.sleep(0.8)

        game.screen(open=False)
        game.hud(True)

    if failures:
        print("\n" + "\n".join(failures), file=sys.stderr)
        return 1
    print("\nevery category and entry drew")
    return 0


if __name__ == "__main__":
    sys.exit(main())

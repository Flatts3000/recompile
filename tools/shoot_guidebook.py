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
if the book will not open, if two entries share a grid position, if a category is missing from the
rail, or if any entry's node is not where the book data says it is.

Needs devbridge with the `use` verb (Flatts3000/devbridge#60). Before it, opening the book at all was
impossible from outside: `click` drives a screen that is already up, and the only way in was
synthesizing an OS-level mouse event, which needs the game window foregrounded and silently does
nothing when it is not. That is why #259 sat blocked.

**Everything that can be a widget is read as one; only the entry nodes are computed.** Category
buttons are real widgets and `screen` reports a ready-to-click point for each, so the rail is never
computed here. Modonomicon draws the entry nodes itself, so `screen` cannot see them and their
positions come from the book data: every entry JSON carries a grid `x`/`y` on a fixed lattice.

**Moving an entry needs a client RESTART, not a reload.** Modonomicon caches the built book, so
neither `/reload` nor `/modonomicon reload` rebuilds node positions - measured, after syncing
`build/resources/main` and reloading twice, with the node still at its old square. Expect one stale
failure from this tool after editing an entry's `x`/`y`, and restart before believing it.

**A click that opens "an entry screen" proves almost nothing, and the first version of this script
was fooled by exactly that.** Two demolition entries shared a grid position, so one node was drawn on
top of the other and was unreachable; clicking the same point twice opened an entry both times, wrote
two differently-named screenshots of the same page, and reported that everything drew. The overlap was
a real book defect this tool exists to catch and instead certified. It now refuses to start when two
entries claim one position - a data check, which is cheaper and stricter than anything done in-game.

**Checking the open screen's TITLE was tried and does not work: Modonomicon's book screens report an
empty `getTitle()`.** The discriminator is the state transition instead. Every entry starts from a
freshly opened book, so the click begins on the category map and must end on an entry screen; a miss
leaves the map up and is reported. Re-opening per entry rather than navigating back is deliberate -
**the map keeps its pan between visits**, and after walking one category the next one's nodes are no
longer where the lattice predicts. Measured: every demolition entry opened and then every depths entry
missed, at coordinates that worked when depths was opened from a fresh book.
"""
from __future__ import annotations

import argparse
import json
import pathlib
import sys
import time
from collections import defaultdict

try:
    from gamebridge.devbridge import DevBridge
except ImportError:
    sys.exit("gamebridge is not installed. It ships with the devbridge mod it talks to:\n"
             '  pip install "gamebridge @ git+https://github.com/Flatts3000/devbridge.git'
             '#subdirectory=gamebridge"')

REPO = pathlib.Path(__file__).resolve().parent.parent
BOOKS = REPO / "src/main/resources/data/recompile/modonomicon/books/guide"
LANG = REPO / "src/main/resources/assets/recompile/lang/en_us.json"
PORT = 8605          # claimed for this repo; see CLAUDE.md on why there is no default
INSTANCE = REPO / "run"

BOOK_ITEM = 'modonomicon:modonomicon[modonomicon:book_id="recompile:guide"]'

# The entry lattice, in GUI-scaled units. Measured: the depths' entries carry grid x=0..6 at y=1 and
# landed on screen x=212..340, y=149. Checked rather than trusted - a predicted click that does not
# open the entry it was aimed at is a failure, not a shrug.
NODE_ORIGIN_X = 212.0
NODE_ORIGIN_Y = 149.0 - 21.333      # grid y=1 sat at 149
NODE_STEP = 21.333


def translations() -> dict:
    return json.loads(LANG.read_text(encoding="utf-8"))


def categories() -> list[tuple[int, str]]:
    """Every category, in the order the rail draws them - which is by sort_number."""
    found = []
    for path in sorted((BOOKS / "categories").glob("*.json")):
        body = json.loads(path.read_text(encoding="utf-8"))
        found.append((body.get("sort_number", 0), path.stem))
    found.sort()
    return [(index, name) for index, (_, name) in enumerate(found)]


def entries(category: str, lang: dict) -> list[tuple[int, int, str, str]]:
    """Grid position and id for each of a category's entries, with its lang key checked."""
    directory = BOOKS / "entries" / category
    if not directory.is_dir():
        raise SystemExit(f"{directory} does not exist, so {category} has no entries to walk. A "
                         f"category with no entries is a book defect, not an empty result.")
    found = []
    for path in sorted(directory.glob("*.json")):
        body = json.loads(path.read_text(encoding="utf-8"))
        if "x" not in body or "y" not in body:
            # NOT SKIPPED. Modonomicon defaults a missing coordinate rather than erroring, so a
            # silent skip would drop the entry from the walk, from the count, and from the report -
            # in a tool whose whole purpose is not passing silently.
            raise SystemExit(f"{path} has no x/y, so it cannot be located on the node grid.")
        if body.get("name", "") not in lang:
            raise SystemExit(f"{path} names {body.get('name')!r}, which is not in en_us.json - the "
                             f"entry would render its raw key to the player.")
        found.append((body["x"], body["y"], path.stem))
    return found


def check_unique_positions(lang: dict) -> None:
    """Two entries on one square means one of them is unreachable. Refuse to walk a book like that."""
    seen = defaultdict(list)
    for _, category in categories():
        for gx, gy, entry in entries(category, lang):
            seen[(category, gx, gy)].append(entry)
    clashes = {k: v for k, v in seen.items() if len(v) > 1}
    if clashes:
        lines = [f"  {cat} ({x},{y}): {', '.join(names)}" for (cat, x, y), names in clashes.items()]
        raise SystemExit("two entries share a node position, so one of each pair is drawn on top of "
                         "the other and cannot be opened at all:\n" + "\n".join(lines))


def open_book(game: DevBridge) -> None:
    """Close whatever is up and open the guide, leaving the category rail showing.

    <p>Called once per entry. `use` refuses outright while a screen is open, so the close is not
    optional tidiness - it is what makes the next open possible at all.
    """
    game.screen(open=False)
    time.sleep(0.4)
    opened = game.use(target="item")
    if not opened.get("openedScreen"):
        raise SystemExit(f"the guide did not open: {opened}")


def widget(game: DevBridge, kind: str) -> dict | None:
    """The first widget of a type with a usable click point."""
    for found in game.screen().get("widgets") or []:
        # centerX/centerY are NULL for a widget with an empty rectangle, and clicking null sends a
        # JSON null that fails on the mod side with an unrelated-looking error.
        if found.get("type") == kind and found.get("centerX") is not None:
            return found
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--category", default=None, help="only this category")
    parser.add_argument("--port", type=int, default=PORT)
    args = parser.parse_args()
    sys.stdout.reconfigure(line_buffering=True)

    lang = translations()
    check_unique_positions(lang)

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
        try:
            game.screen(open=False)     # a leftover screen makes `use` refuse outright
            game.hud(False)
            for command in ["gamemode creative", "time set noon", "gamerule advance_time false"]:
                game.command(command, player="@s")
            # INTO THE SELECTED SLOT, not just into the inventory. `use` reads the selected hand, and
            # `give` only promises the first free slot - so with any other slot highlighted the run
            # aborts with an empty hand and the book sitting in the hotbar.
            game.command(f"item replace entity @s weapon.mainhand with {BOOK_ITEM}", player="@s")
            time.sleep(1.0)

            # target=item, not auto: the player may be standing in front of anything, and a block
            # that answers the right-click would win and the book would never open.
            open_book(game)
            rail = [w for w in (game.screen().get("widgets") or [])
                    if w.get("type") == "CategoryButton" and w.get("centerX") is not None]
            print(f"{len(rail)} category buttons on the rail, {len(categories())} in the data")
            if len(rail) != len(categories()):
                failures.append(f"the rail draws {len(rail)} categories against "
                                f"{len(categories())} in the data")

            for index, name in wanted:
                if index >= len(rail):
                    failures.append(f"{name}: no button at rail index {index}")
                    continue
                game.click(rail[index]["centerX"], rail[index]["centerY"])
                time.sleep(1.0)
                game.screenshot(f"book_{name}")

                found = entries(name, lang)
                print(f"{name}: {len(found)} entries")
                for gx, gy, entry in found:
                    # FROM A FRESH BOOK EVERY TIME. The map keeps its pan between visits, so walking
                    # back through it puts the next category's nodes somewhere the lattice does not
                    # predict. See the module docstring.
                    open_book(game)
                    game.click(rail[index]["centerX"], rail[index]["centerY"])
                    time.sleep(0.8)
                    px = NODE_ORIGIN_X + NODE_STEP * gx
                    py = NODE_ORIGIN_Y + NODE_STEP * gy
                    game.click(px, py)
                    time.sleep(0.9)
                    now = (game.screen().get("screen") or "").split(".")[-1]
                    # The click started on the category map, so anything but an entry screen means it
                    # landed on nothing - the node is not where the lattice says it is.
                    if "Entry" not in now:
                        failures.append(f"{name}/{entry}: grid ({gx},{gy}) -> ({px:.0f},{py:.0f}) "
                                        f"left {now or 'nothing'} up, so no node is there")
                        continue
                    game.screenshot(f"book_{name}_{entry}")
                    print(f"  {entry}")
        finally:
            # ALWAYS, because a run that dies with the book open and the HUD off leaves the client
            # in a state where the NEXT run cannot even start: `use` refuses while a screen is up.
            try:
                game.screen(open=False)
                game.hud(True)
            except Exception:
                pass

    if failures:
        print("\n" + "\n".join(failures), file=sys.stderr)
        return 1
    print("\nevery category and entry drew")
    return 0


if __name__ == "__main__":
    sys.exit(main())

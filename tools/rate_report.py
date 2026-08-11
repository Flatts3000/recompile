#!/usr/bin/env python3
"""How often a player meets each thing, in minutes of play - without anyone having to play.

What this replaces
------------------
Drop rates used to be checked by playing for a while and looking in the barrel. That
gave the wrong answer twice: fifteen minutes of play is far too small a sample for
anything rarer than about one pull in fifty, and the 2026-08-11 session recorded zero
pulls because the player sifted at a Sorting Tarp, which the instrumentation could not
see at the time.

Rates do not need a person. `RateCensusTests` rolls each real pull stream a quarter of a
million times inside a real server and writes what came out to logs/recompile-rates.tsv;
this turns that into the unit the balance targets are actually stated in.

The one number that IS still human
----------------------------------
Pulls per hour. How much of an hour someone spends picking through garbage rather than
shovelling, walking, building or reading a guidebook is a fact about people, and no
amount of rolling loot tables will produce it. It defaults to the 4,500 that
FindRateTest derives (5 pulls a second at a quarter of playtime) and is a FLAG here
precisely so it stays visibly an assumption. Every minutes column scales linearly with
it; the "one in N pulls" column does not, because that one is measured.

Usage
-----
    ./gradlew runGameTestServer          # produces the census
    python tools/rate_report.py          # reads it
    python tools/rate_report.py --pulls-per-hour 3000 --only bucket,shears
"""
from __future__ import annotations

import argparse
import math
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

# Same figure FindRateTest derives, and stated as an assumption in both places rather than
# shared, because sharing it would make it look like a measurement in the one place it is not.
DEFAULT_PULLS_PER_HOUR = 4500.0


def read_census(path: Path):
    rows = []
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            parts = line.rstrip("\n").split("\t")
            if len(parts) != 5 or parts[0] == "stream":
                continue
            try:
                rows.append((parts[0], parts[1], int(parts[2]), int(parts[3]), float(parts[4])))
            except ValueError:
                continue
    return rows


def human_time(minutes: float) -> str:
    """Minutes, hours or 'never in a playthrough', whichever a person can picture."""
    if minutes < 1:
        return f"{minutes * 60:.0f} sec"
    if minutes < 90:
        return f"{minutes:.0f} min"
    hours = minutes / 60
    if hours < 100:
        return f"{hours:.1f} h"
    return f"{hours:.0f} h"


def confidence(predicted_hits: float) -> str:
    """Whether the census actually SAW enough of this to say anything about it.

    A rate of one in half a million is arithmetic, not observation - 250,000 rolls will
    turn up zero or one of it either way. Saying so beats printing a number that looks
    equally solid as the one next to it.

    Keyed on the PREDICTED count, not the observed one, and deliberately: how many the
    census happened to see is the thing being qualified, so using it to qualify itself
    would let one lucky drop promote a row to "measured".
    """
    if predicted_hits >= 100:
        return "measured"
    if predicted_hits >= 10:
        return "thin"
    return "arithmetic"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--census", type=Path,
                    default=REPO / "run" / "logs" / "recompile-rates.tsv")
    ap.add_argument("--pulls-per-hour", type=float, default=DEFAULT_PULLS_PER_HOUR,
                    help=f"the human assumption (default {DEFAULT_PULLS_PER_HOUR:.0f})")
    ap.add_argument("--only", help="comma-separated substrings to filter items by")
    ap.add_argument("--stream", help="only this stream (e.g. household)")
    args = ap.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")

    if not args.census.is_file():
        print(f"no census at {args.census}")
        print("\nRun ./gradlew runGameTestServer first - RateCensusTests writes it.")
        return 1

    rows = read_census(args.census)
    if not rows:
        print(f"{args.census} has no rows.")
        return 1

    wanted = [s.strip() for s in args.only.split(",")] if args.only else None

    print(f"census      : {args.census}")
    print(f"pulls/hour  : {args.pulls_per_hour:,.0f}   <- ASSUMED, not measured; "
          "every minutes column scales with it")
    print()

    streams = sorted({r[0] for r in rows})
    for stream in streams:
        if args.stream and args.stream not in stream:
            continue
        items = [r for r in rows if r[0] == stream]
        rolls = items[0][3]
        print(f"--- {stream}   ({rolls:,} rolls measured) ---")
        print(f"  {'item':<36}{'one per':>12}{'play time':>12}{'basis':>13}")
        # Rarest last: the interesting reading is the top of the list for materials and the
        # bottom for treasure, and sorting by rarity puts both where they are looked for.
        for _stream, item, _seen, _rolls, predicted in sorted(items, key=lambda r: -r[4]):
            if predicted <= 0:
                continue
            if wanted and not any(w in item for w in wanted):
                continue
            per = 1.0 / predicted
            minutes = per / args.pulls_per_hour * 60.0
            basis = confidence(predicted * rolls)
            print(f"  {item:<36}{per:>11,.0f}{human_time(minutes):>12}{basis:>13}")
        print()

    print("basis: 'measured' = the census saw 100+ of it, so the model is checked against the")
    print("       game directly. 'arithmetic' = too rare to sample here; the number is the")
    print("       table's own division, which the measured rows are what validate.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

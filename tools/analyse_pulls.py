#!/usr/bin/env python3
"""Turn a recorded sorting log into the numbers the drop rates are tuned against.

Why this exists
---------------
Every rate in this mod was tuned against an ESTIMATE of how many pulls an hour a
player makes, and that estimate was wrong twice on the day it was written:

  * once by reading a session's barrel contents as if it were the whole session,
  * once by confusing how fast a mound can be shovelled FLAT with how long it takes
    to pick THROUGH one - they differ by about 2x, and only one rolls loot.

Both were reasonable. Both were out by more than a factor of two. A weight is only
as good as the pull count you think it is being rolled against, so this reads the
count instead of deriving it.

What it prints
--------------
  * pulls an hour, measured over the session's own wall clock
  * the sorted/mined split, which is the thing the two estimates disagreed about
  * average pulls per block, measured - the crumble curve says 2.5 for garbage
  * every item's observed rate, next to what the loot table says it should be,
    with the sample size so a one-off is not mistaken for a trend

Usage
-----
    python tools/analyse_pulls.py                     # find the dev-run log
    python tools/analyse_pulls.py --log PATH          # a specific file
    python tools/analyse_pulls.py --instance PATH     # a CurseForge instance
"""
from __future__ import annotations

import argparse
import json
import math
import sys
from collections import Counter, defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
LOOT = REPO / "src" / "main" / "resources" / "data" / "recompile" / "loot_table" / "gameplay"

# Which stream each sortable rolls. Mirrors the pullTable() overrides; a block missing
# from here still counts toward throughput, it just gets no expected-rate column.
STREAM = {
    # Hand pulls record the BLOCK id; a tarp or Separator records the ITEM it consumed. They are
    # the same string for a block-item, which is why one map serves both.
    "recompile:garbage_block": "household_pulls",
    "recompile:trash_bag": "bag_pulls",
    "recompile:compacted_bale": "household_pulls",
    "recompile:stone_rubble": "rubble_pulls",
    "recompile:mechanical_waste": "mechanical_pulls",
}


def read_events(path: Path):
    rows = []
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            parts = line.rstrip("\n").split("\t")
            if len(parts) != 5 or parts[0] == "epoch_millis":
                continue
            try:
                rows.append((int(parts[0]), parts[1], parts[2], parts[3], int(parts[4])))
            except ValueError:
                continue
    return rows


def expected_rates(stream: str) -> dict[str, float]:
    """Chance per pull for each item, straight from the shipped loot JSON."""
    path = LOOT / f"{stream}.json"
    if not path.is_file():
        return {}
    table = json.loads(path.read_text(encoding="utf-8"))
    out: dict[str, float] = defaultdict(float)
    for pool in table.get("pools", []):
        entries = pool.get("entries", [])
        total = sum(e.get("weight", 1) for e in entries)
        for entry in entries:
            name = entry.get("name")
            if not name or not total:
                continue
            out[name] += entry.get("weight", 1) / total
    return out


def wilson_low(hits: int, trials: int) -> float:
    """Lower bound of a 95% interval - how rare the item could plausibly be.

    Included so a single lucky drop cannot be read as a rate. Nine buckets in fifteen
    minutes was real signal; one collectible in eighty pulls is not, and the two look
    identical if you only print the ratio.
    """
    if trials == 0:
        return 0.0
    z = 1.96
    p = hits / trials
    denom = 1 + z * z / trials
    centre = p + z * z / (2 * trials)
    margin = z * math.sqrt((p * (1 - p) + z * z / (4 * trials)) / trials)
    return max(0.0, (centre - margin) / denom)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--log", type=Path)
    ap.add_argument("--instance", type=Path,
                    help="a game directory; its logs/recompile-pulls.tsv is used")
    ap.add_argument("--min-samples", type=int, default=5,
                    help="hide items with fewer observations than this (default 5)")
    args = ap.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")

    candidates = []
    if args.log:
        candidates.append(args.log)
    if args.instance:
        candidates.append(args.instance / "logs" / "recompile-pulls.tsv")
    candidates.append(REPO / "run" / "logs" / "recompile-pulls.tsv")
    candidates.append(Path.home() / "curseforge" / "minecraft" / "Instances" / "Trashlands"
                      / "logs" / "recompile-pulls.tsv")

    path = next((c for c in candidates if c.is_file()), None)
    if path is None:
        print("no pull log found. Looked in:")
        for c in candidates:
            print(f"  {c}")
        print("\nPlay with analyticsEnabled on (the default), then re-run.")
        return 1

    rows = read_events(path)
    if not rows:
        print(f"{path} has no events yet.")
        return 1

    # SIFT_* is a machine rolling the same tables. Rates combine across all of them; throughput
    # does not, which is why they stay distinguishable in the log.
    hand = [r for r in rows if r[1] == "PULL"]
    sifted = [r for r in rows if r[1].startswith("SIFT_")]
    forage = [r for r in rows if r[1] == "FORAGE"]
    pulls = hand + sifted
    roaches = [r for r in rows if r[1] == "ROACH"]
    crumbles = [r for r in rows if r[1] == "CRUMBLE"]
    breaks = [r for r in rows if r[1] == "BREAK"]

    # Wall clock from the events themselves. A session that was left idle overnight would
    # otherwise drag the rate to nothing, so gaps longer than five minutes are dropped -
    # the question is pulls per hour of PLAYING, not per hour of the world being open.
    stamps = sorted(r[0] for r in rows)
    active_ms = 0
    for a, b in zip(stamps, stamps[1:]):
        gap = b - a
        if gap <= 5 * 60 * 1000:
            active_ms += gap
    hours = active_ms / 3_600_000

    # One PULL line IS one pull - the mod emits it that way precisely so this does not have to
    # guess. An earlier version collapsed lines by timestamp and undercounted badly, because
    # separate pulls share a millisecond at any real sorting speed.
    clicks = len(pulls) + len(roaches)

    by_method = Counter(r[1] for r in sifted)
    print(f"log: {path}")
    print(f"active play : {hours * 60:.1f} min")
    print(f"rolls       : {clicks:,}   (hand {len(hand):,}"
          + "".join(f", {k.replace('SIFT_','').lower()} {v:,}" for k, v in sorted(by_method.items()))
          + ")")
    if hours > 0:
        print(f"rolls/hour  : {clicks / hours:,.0f}")
    if forage:
        print(f"pigeon finds: {len(forage):,}")
    print(f"roaches     : {len(roaches)}"
          + (f"  (one per {clicks / len(roaches):,.0f} pulls)" if roaches else ""))
    print()

    sorted_blocks = Counter(r[2] for r in crumbles)
    mined_blocks = Counter(r[2] for r in breaks)
    total_sorted = sum(sorted_blocks.values())
    total_mined = sum(mined_blocks.values())
    if total_sorted or total_mined:
        share = total_sorted / (total_sorted + total_mined) * 100
        print(f"blocks sorted to destruction : {total_sorted:,}")
        print(f"blocks mined instead         : {total_mined:,}")
        print(f"  -> {share:.0f}% of blocks were picked through rather than shovelled")
    # Per block, not overall: a bag and a bale have different curves, so one blended number
    # compares against nothing. Garbage is the one the "per mound" conversion rests on.
    pulls_by_block = Counter(r[2] for r in pulls)
    MIN_CRUMBLES = 20
    for block, crumbled in sorted(sorted_blocks.items()):
        if crumbled >= MIN_CRUMBLES:
            print(f"  -> {block}: {pulls_by_block[block] / crumbled:.2f} pulls per block "
                  f"over {crumbled} crumbles")
        else:
            # A block sorted but never finished has pulls and no crumble, so the ratio runs away
            # on a short log. Better to say the sample is thin than to print 23.00 and have it
            # read as a measurement.
            print(f"  -> {block}: only {crumbled} crumble(s) - too few to measure pulls per block")
    print("     (the crumble curve predicts 2.5 for garbage, 2.0 for a bag, 3.5 for a bale)")
    print()

    by_stream: dict[str, Counter] = defaultdict(Counter)
    stream_clicks: Counter = Counter()
    for _stamp, _event, source, detail, _count in pulls:
        stream = STREAM.get(source)
        if not stream:
            continue
        stream_clicks[stream] += 1
        if detail == "-":
            continue
        # "item*n;item*n" - the whole yield of one pull. Count STACKS, not items: how many
        # are in the stack is a set_count function on top of the entry winning, so counting
        # items makes scrap metal (which rolls 1-2) read as 50% commoner than its weight.
        # That is a defect in the counter, not a finding about the table.
        for part in detail.split(";"):
            name, _, _qty = part.partition("*")
            by_stream[stream][name] += 1

    for stream, items in sorted(by_stream.items()):
        trials = stream_clicks[stream]
        expected = expected_rates(stream)
        print(f"--- {stream}  ({trials:,} pulls) ---")
        print(f"  {'item':<34}{'seen':>7}{'observed':>11}{'expected':>11}{'ratio':>8}")
        for item, seen in items.most_common():
            if seen < args.min_samples and item != "-":
                continue
            obs = seen / trials if trials else 0
            exp = expected.get(item, 0.0)
            ratio = (obs / exp) if exp else float("nan")
            flag = ""
            if exp and trials:
                # Only call it out when the interval clears the expectation - otherwise
                # a small sample reads as a finding.
                if wilson_low(seen, trials) > exp * 1.5:
                    flag = "  <-- commoner than the table says"
            print(f"  {item:<34}{seen:>7}{obs:>11.4f}{exp:>11.4f}"
                  f"{ratio:>8.2f}{flag}")
        print()

    if hours > 0 and clicks / hours > 50_000:
        print("NOTE: this rate is far above anything a person can click. The log is almost "
              "certainly from a GameTest run rather than play - point --log at the instance's "
              "file instead.")
    if clicks < 500:
        print(f"NOTE: {clicks} pulls is a small sample. Rare items need thousands before "
              "an observed rate means much - the ratio column will look wild until then.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

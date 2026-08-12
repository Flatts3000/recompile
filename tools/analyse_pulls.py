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

    # AN EXPLICIT PATH IS NOT A HINT. Asking for one file and getting a report about another is
    # the failure that made devbridge claim its own port: a tool that quietly answers about the
    # wrong thing looks exactly like a tool that worked. A typo'd --log used to fall through to
    # the GameTest log, whose rates are a bot's and whose numbers look entirely reasonable.
    for flag, given in (("--log", args.log),
                        ("--instance", args.instance / "logs" / "recompile-pulls.tsv"
                         if args.instance else None)):
        if given is not None and not given.is_file():
            print(f"{flag} points at {given}, which does not exist.")
            print("Refusing to fall back to another log - it would report on the wrong session.")
            return 1

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

    # Wall clock from the events themselves. A session left idle would otherwise drag the rate
    # to nothing, so gaps longer than five minutes are dropped - the question is pulls per hour
    # of PLAYING, not per hour of the world being open.
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
    print(f"wall clock  : {(stamps[-1] - stamps[0]) / 60000:.0f} min "
          f"({sum(1 for r in rows if r[1] == 'SESSION' and r[3] == 'start')} session(s))")
    print(f"active play : {hours * 60:.1f} min")
    print(f"rolls       : {clicks:,}   (hand {len(hand):,}"
          + "".join(f", {k.replace('SIFT_','').lower()} {v:,}" for k, v in sorted(by_method.items()))
          + ")")
    if forage:
        print(f"pigeon finds: {len(forage):,}")
    print(f"roaches     : {len(roaches)}"
          + (f"  (one per {clicks / len(roaches):,.0f} pulls)" if roaches else ""))
    print()

    # THE RATE THE WHOLE BALANCE MODEL RESTS ON, AND IT IS TWO NUMBERS, NOT ONE.
    #
    # "Rolls per hour" over active play answers a different question depending on what the player
    # was doing, and a short log makes it meaningless: a two-minute poke at a mound reported 7,041
    # rolls/hour, which is neither the rate while working nor the rate over a session. So the rate
    # while WORKING is reported separately, with idle stripped at a 5-second gap rather than a
    # 5-minute one, and the session rate is only printed when there is enough of a session to
    # divide by.
    #
    # Both matter and they are not interchangeable: the working rate is a property of tools and
    # blocks and can be measured in seconds, while what fraction of an hour a person spends
    # working is a property of PEOPLE and needs a real session. Printing one number invites
    # reading it as the other, which is how 4,500 got into the balance model.
    def burst_rate(events, gap_ms=5000):
        """Events per second with idle stripped - the rate while actually doing the thing."""
        ts = sorted(e[0] for e in events)
        if len(ts) < 2:
            return None, 0.0
        span = sum(b - a for a, b in zip(ts, ts[1:]) if b - a <= gap_ms) / 1000
        return (len(ts) / span if span else None), span

    def presses(events, within_ms=50):
        """Group sift rolls into the right-clicks that produced them.

        One press of a Sorting Tarp rolls the block's whole allowance in a single tick, so its
        rolls land in the same millisecond while consecutive presses are tenths of a second
        apart. Clustering recovers BLOCKS SIFTED and, from that, the measured rolls per block.

        Derived rather than assumed on purpose: rolls per block differs by variant (a bag is
        not a bale is not rubble), so a constant here would be right for garbage and quietly
        wrong for everything else - and this file already had a hardcoded 6.0 doing exactly
        that. The log knows; ask it.
        """
        out = []
        for event in sorted(events, key=lambda e: e[0]):
            if out and event[0] - out[-1][0] <= within_ms and event[2] == out[-1][1]:
                out[-1] = (event[0], out[-1][1], out[-1][2] + 1)
            else:
                out.append((event[0], event[2], 1))
        return out

    sift_rate, sift_span = burst_rate(sifted)
    hand_rate, hand_span = burst_rate(hand)
    mine_rate, mine_span = burst_rate(breaks)
    sift_presses = presses(sifted)
    rolls_per_press = len(sifted) / len(sift_presses) if sift_presses else 0.0

    print("rate while actually doing it (idle stripped at 5s):")
    if sift_rate:
        print(f"  sifting   : {sift_rate:>6.1f} rolls/sec   over {sift_span:.0f}s"
              f"   -> {sift_rate * 3600:,.0f} rolls/hour of solid sifting")
        print(f"              {len(sift_presses):>6,} blocks sifted at {rolls_per_press:.1f} "
              f"rolls each, {sift_rate / rolls_per_press:.1f} blocks/sec")
    if hand_rate:
        print(f"  hand      : {hand_rate:>6.1f} pulls/sec   over {hand_span:.0f}s"
              f"   -> {hand_rate * 3600:,.0f} pulls/hour of solid sorting")
    if mine_rate:
        print(f"  mining    : {mine_rate * 60:>6.0f} blocks/min  over {mine_span:.0f}s")
    if mine_rate and sift_rate and rolls_per_press:
        # Mining feeds sifting, so the loop runs at whichever handles fewer BLOCKS per second.
        # Compared in blocks rather than rolls so the rolls-per-block figure only has to be
        # applied once, at the end, and only to whichever side actually won.
        sift_blocks = sift_rate / rolls_per_press
        limiter = "mining" if mine_rate < sift_blocks else "sifting"
        loop = min(mine_rate, sift_blocks) * rolls_per_press
        print(f"  -> the mine-and-sift loop is {limiter}-limited at {loop * 3600:,.0f} rolls/hour "
              "of solid work")
        print("     (a rate target is that, times the fraction of a session spent doing it)")
    if hours > 0:
        if active_ms >= 10 * 60 * 1000:
            print(f"\nover the session : {clicks / hours:,.0f} rolls/hour")
        else:
            print(f"\nover the session : NOT REPORTED - only {hours * 60:.1f} min of activity. "
                  "A session\n                   rate needs 10+ minutes or it is measuring one "
                  "burst, not play.")
    print()

    sorted_blocks = Counter(r[2] for r in crumbles)
    mined_blocks = Counter(r[2] for r in breaks)
    total_sorted = sum(sorted_blocks.values())
    total_mined = sum(mined_blocks.values())
    # A TARP SIFT DESTROYS A BLOCK TOO, AND IT EMITS NO CRUMBLE.
    #
    # CRUMBLE only fires when a PLACED block is picked apart by hand; the tarp consumes an item.
    # Counting crumbles alone made this section report "0% of blocks were picked through rather
    # than shovelled" about a session that sifted 64 of the 278 it mined - a flat falsehood, and
    # about the one split the whole balance model turns on. Same failure as the pull hook that
    # only saw hand sorting: the number was right for the path it watched and wrong as an answer
    # to the question being asked.
    total_processed = total_sorted + len(sift_presses)
    if total_processed or total_mined:
        print(f"blocks picked apart by hand  : {total_sorted:,}")
        print(f"blocks sifted at a machine   : {len(sift_presses):,}")
        print(f"blocks mined                 : {total_mined:,}")
        if total_mined:
            print(f"  -> {total_processed / total_mined * 100:.0f}% of what was mined got processed"
                  " (the rest is still in a barrel somewhere)")
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
    if sorted_blocks:
        # Gated: with no hand sorting there is nothing above for this to be a caption to, and an
        # unconditional note reads as a comment on whatever numbers happen to precede it.
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

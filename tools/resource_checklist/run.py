#!/usr/bin/env python3
"""Run the whole resource-checklist pipeline and rewrite docs/vanilla_resource_checklist.md.

    python tools/resource_checklist/run.py              # everything
    python tools/resource_checklist/run.py --from reach # skip the vanilla stages
    python tools/resource_checklist/run.py --version 26.2.0

The vanilla stages (extract, index, domains, catalogue) only change when the Minecraft version
does, and they are the slow ones. The mod stages (reach, render) change every time the mod's loot,
recipes, worldgen or structures change, which is what `--from reach` is for.

Stage order is load-bearing: each stage writes a json into `build/resource_checklist/` that the
next one reads. Nothing talks to anything except through those files, so a stage can be re-run on
its own while debugging.
"""
import os
import subprocess
import sys

from paths import MCDATA, OUTPUT, WORK

HERE = os.path.dirname(os.path.abspath(__file__))

STAGES = [
    ("extract", "extract.py", "unpack vanilla data from the client jar"),
    ("index", "index_vanilla.py", "index loot tables, recipes, trades, tags"),
    ("nbt", "nbt.py", "parse structure templates for their block palettes"),
    ("domains", "domains.py", "map biomes to domains via biome tags and features"),
    ("catalogue", "catalogue.py", "assign each resource a primary domain"),
    ("reach", "reachability.py", "close over what Recompile can actually reach"),
    ("render", "render.py", "write the markdown"),
]


def main():
    version = None
    if "--version" in sys.argv:
        version = sys.argv[sys.argv.index("--version") + 1]

    start = 0
    if "--from" in sys.argv:
        want = sys.argv[sys.argv.index("--from") + 1]
        names = [s[0] for s in STAGES]
        if want not in names:
            print("unknown stage %r; pick one of %s" % (want, ", ".join(names)))
            return 2
        start = names.index(want)

    if start > 0 and not os.path.isdir(MCDATA):
        print("no extracted vanilla data in %s - run without --from first." % MCDATA)
        return 2

    for name, script, blurb in STAGES[start:]:
        print("\n=== %s: %s ===" % (name, blurb))
        cmd = [sys.executable, os.path.join(HERE, script)]
        if name == "extract" and version:
            cmd.append(version)
        r = subprocess.run(cmd, cwd=HERE)
        if r.returncode != 0:
            print("stage %r failed (exit %d)" % (name, r.returncode))
            return r.returncode

    print("\nwrote %s" % OUTPUT)
    print("intermediates in %s" % WORK)
    return 0


if __name__ == "__main__":
    sys.exit(main())

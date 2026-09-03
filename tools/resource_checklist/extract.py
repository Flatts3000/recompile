#!/usr/bin/env python3
"""Unpack the vanilla data the checklist is derived from, out of the client jar.

**The jar is the source of truth, not the wiki.** Loot tables, recipes, villager trades, biomes,
features, surface rules, structure templates and item tags all ship inside it, and reading them is
the difference between a checklist that is right for 26.1.2 and one that is right for whatever
version the person writing it last played.

The jar is already on this machine: moddev downloads it into the NeoForm cache to build against, so
there is nothing to fetch. It is located by version rather than hardcoded, so bumping the mod's
Minecraft version and re-running is the whole upgrade path.

Two directories in the jar matter and are easy to get wrong:

- `data/minecraft/loot_table/` at the TOP level. The jar also carries
  `data/minecraft/datapacks/trade_rebalance/data/minecraft/loot_table/`, which is an OPTIONAL
  datapack that is off unless a world enables it. A `loot_table/*` glob picks up both and silently
  mixes an experimental trade rebalance into the answer.
- `unzip` needs `**` rather than `*` for these nested trees. With `*` it creates the directory and
  extracts nothing, and reports success either way - which cost real time the first time round.

Usage:

    python tools/resource_checklist/extract.py            # default version
    python tools/resource_checklist/extract.py 26.1.2
"""
import glob
import os
import sys
import shutil
import zipfile

from paths import MCDATA, WORK

DEFAULT_VERSION = "26.1.2"

# Everything the later stages read. Anything not listed here is left in the jar.
WANTED = ("data/minecraft/loot_table/", "data/minecraft/recipe/", "data/minecraft/tags/",
          "data/minecraft/worldgen/", "data/minecraft/villager_trade/",
          "data/minecraft/structure/", "assets/minecraft/lang/en_us.json")

# The optional datapacks shipped inside the jar. Their loot tables are NOT vanilla behaviour.
EXCLUDE = "data/minecraft/datapacks/"


def find_jar(version):
    """The client jar in the NeoForm cache moddev already populated."""
    roots = [os.path.expanduser("~/.gradle/caches/neoformruntime/artifacts")]
    for root in roots:
        hit = os.path.join(root, "minecraft_%s_client.jar" % version)
        if os.path.isfile(hit):
            return hit
        for cand in glob.glob(os.path.join(root, "*client*.jar")):
            if version in os.path.basename(cand):
                return cand
    return None


def main():
    version = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_VERSION
    jar = find_jar(version)
    if not jar:
        print("no client jar for %s in the NeoForm cache." % version)
        print("run any gradle task once (JAVA_HOME=\"/c/Program Files/Java/jdk-25\" ./gradlew "
              "compileJava) to populate it.")
        return 1

    # Wipe first. Overwrite-in-place leaves files upstream DELETED between versions sitting there,
    # and every later stage keeps indexing loot tables and recipes that no longer exist.
    if os.path.isdir(MCDATA):
        shutil.rmtree(MCDATA)
    os.makedirs(MCDATA, exist_ok=True)
    written = 0
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            if name.endswith("/") or name.startswith(EXCLUDE):
                continue
            if not name.startswith(WANTED):
                continue
            target = os.path.join(MCDATA, name.replace("/", os.sep))
            os.makedirs(os.path.dirname(target), exist_ok=True)
            with z.open(name) as src, open(target, "wb") as dst:
                dst.write(src.read())
            written += 1

    print("extracted %d files from %s" % (written, os.path.basename(jar)))
    print("  into %s" % MCDATA)
    for label, pattern in (("loot tables", "data/minecraft/loot_table/**/*.json"),
                           ("recipes", "data/minecraft/recipe/**/*.json"),
                           ("trades", "data/minecraft/villager_trade/**/*.json"),
                           ("biomes", "data/minecraft/worldgen/biome/*.json"),
                           ("structures", "data/minecraft/structure/**/*.nbt")):
        n = len(glob.glob(os.path.join(MCDATA, pattern.replace("/", os.sep)), recursive=True))
        print("  %-12s %5d" % (label, n))
    open(os.path.join(WORK, "VERSION"), "w", encoding="utf-8").write(version)
    return 0


if __name__ == "__main__":
    sys.exit(main())

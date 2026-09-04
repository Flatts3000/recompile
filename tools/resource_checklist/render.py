import json, os, collections, re

from paths import WORK as SP, OUTPUT

# Read back what extract.py actually unpacked rather than restating a constant.
try:
    MC_VERSION = open(SP + "/VERSION", encoding="utf-8").read().strip()
except OSError:
    MC_VERSION = "unknown"
rows = json.load(open(SP + "/rows.json"))
dropped = json.load(open(SP + "/dropped.json"))
RJ = json.load(open(SP + "/reach.json"))
R, MOBS = RJ["reach"], RJ["mobs"]
from renewability import FINITE, NO_SURVIVAL_SOURCE, NON_RENEWABLE, CONTRADICTED

ORDER = ["Wood", "Overworld (other)", "Forest", "Jungle", "Desert", "Badlands", "Savanna", "Taiga",
         "Swamp", "Snowy", "Mountain", "Mushroom Fields", "Beach", "River", "Ocean",
         "Cave & Underground", "Nether", "End", "Structures & Chest Loot", "Trading", "Fishing",
         "Archaeology", "Other"]
TITLE = {"Overworld (other)": "Overworld - General Surface"}
BLURB = {
    "Wood": "Every log/leaf/sapling family, plus the stripped variants (axe on a log).",
    "Overworld (other)": "Found across three or more surface biomes, so not biome-specific.",
    "Cave & Underground": "Ores and everything below the surface.",
    "Ocean": "Oceans and their structures: monuments, shipwrecks, ocean ruins, buried treasure.",
    "Nether": "The Nether dimension, its biomes, fortresses, bastions, and piglin bartering.",
    "End": "The End dimension, end cities, and the dragon.",
    "Structures & Chest Loot": "Only reachable from a generated structure's chest or block palette.",
    "Trading": "Villager trades, wandering trader, and Hero of the Village gifts.",
    "Fishing": "The fishing loot tables.", "Archaeology": "Brushing suspicious sand and gravel.",
}
METHOD_ORDER = ["Mine / break a block", "Mob & entity drops", "Harvest & interact",
                "Piglin bartering", "Structure chests", "Trading", "Fishing", "Archaeology", "Other"]
INTERACT_P = ("shear ", "harvest ", "brush an ", "use an axe", "grow ", "charged creeper",
              "cat morning", "sniffer", "panda", "armadillo", "chicken lays", "turtle",
              "ender dragon", "a wither", "a charged creeper", "mine from the end ship",
              "decorated pot")


def method_of(why):
    w = " ".join(why)
    if any("piglin barter" in x for x in why):
        return "Piglin bartering"
    if why and all(x.startswith("mine ") for x in why):
        return "Mine / break a block"
    if any(x.startswith("kill ") for x in why):
        return "Mob & entity drops"
    if any(x.startswith(INTERACT_P) for x in why):
        return "Harvest & interact"
    if any(x.startswith("chest ") for x in why):
        return "Structure chests"
    if any(x.startswith("trade ") or "hero of the village" in x for x in why):
        return "Trading"
    if "fishing" in w:
        return "Fishing"
    if any(x.startswith("brush ") for x in why):
        return "Archaeology"
    if any(x.startswith("mine ") for x in why):
        return "Mine / break a block"
    return "Other"


TRIM = re.compile(r"_armor_trim_smithing_template$")
SHERD = re.compile(r"_pottery_sherd$")
ORE = re.compile(r"(^|_)ore$|^raw_(iron|copper|gold)$")


def why_not(item, r):
    n = item.replace("minecraft:", "")
    d = r["primary"]
    if d == "End":
        return "the End is locked - RCDimensionLockout blocks travel and portal formation"
    if d == "Ocean":
        return "no ocean, monument, shipwreck or ocean ruin generates"
    if ORE.search(n) or n.startswith("deepslate_") and n.endswith("_ore"):
        return "the garbage world generates no ore; metal comes from scrap instead"
    if SHERD.search(n):
        return "only the heart sherd is in the loot; the other sherds have no source"
    if TRIM.search(n):
        return "the structure that carries this template does not generate"
    if n in ("trial_key", "ominous_trial_key", "ominous_bottle", "heavy_core", "flow_banner_pattern",
             "guster_banner_pattern", "music_disc_creator", "music_disc_creator_music_box",
             "music_disc_precipice", "lingering_potion"):
        return "no trial chambers generate"
    if n == "ominous_banner":
        # totem_of_undying used to share this line and no longer belongs to it: the Buy Terminal
        # sells one outright (docs/market_spec.md section 14), so it is reachable and this string
        # would never be reached for it. A dead name in a reason list is how the nylium string went
        # on claiming a whole family was lost after half of it had shipped.
        return "no raids: evokers and pillagers never spawn"
    if n in ("disc_fragment_5", "music_disc_otherside", "music_disc_relic", "sniffer_egg"):
        return "the structure that carries it (ancient city / stronghold / trail ruins) is absent"
    if n in ("nether_gold_ore", "nether_quartz_ore"):
        return "the depths are solid techno-organic waste - no ore blocks generate (the quartz " \
               "ITEM still comes from bartering)"
    if n in ("twisting_vines", "weeping_vines"):
        # The rest of this group moved to reachable when the bone-meal edges were added to
        # reachability.INTERACT. These two are held back deliberately: they were not asserted by
        # the GameTests that back those edges, and this string used to claim the whole family was
        # lost because the depths grow no nylium - which was false, since both nyliums are crafted
        # from shards. Saying "not modelled" rather than "no source" is the honest form, and the
        # distinction is the one that produced a wrong issue when it was missing.
        return "reachable only through a growth mechanic the closure does not model (bone meal), " \
               "so not verified either way"
    if n.startswith(("azalea", "flowering_azalea")) or n in (
            "big_dripleaf", "hanging_roots", "spore_blossom", "glow_lichen"):
        return "no lush caves generate"
    if "amethyst" in n:
        return "no amethyst geodes generate"
    if n == "player_head":
        return "needs a charged creeper to kill another player"
    if n == "large_fern":
        return "needs a fern, which nothing here provides"
    return "nothing in this world, its structures, its mobs or its recipes produces one"


byd = collections.defaultdict(lambda: collections.defaultdict(list))
demoted = collections.defaultdict(list)
for i, r in rows.items():
    if r.get("struct_only"):
        demoted[r["primary"]].append(i)
    else:
        byd[r["primary"]][method_of(r["why"])].append(i)

out = []
A = out.append
nreach = sum(1 for i in rows if i in R)
A("# Vanilla Resource Checklist, checked against Recompile")
A("")
A("Every resource vanilla Minecraft gives a player **without a crafting grid**, grouped by where you")
A("go to get it - and for each one, whether a player of **Recompile standalone** can actually get it.")
A("")
A("- `[x]` reachable, followed by the route that reaches it.")
A("- `[ ]` not reachable, followed by why not.")
A("")
A("| | |")
A("|---|---|")
A("| Minecraft version | %s |" % MC_VERSION)
A("| Catalogued | %d |" % len(rows))
A("| **Reachable in Recompile** | **%d (%d%%)** |" % (nreach, round(100 * nreach / len(rows))))
A("| Not reachable | %d |" % (len(rows) - nreach))
A("| Mobs obtainable | %d |" % len(MOBS))
A("")
A("**How the checkmarks were decided.** Not by judgement: by a reachability closure over the mod's")
A("own data. Seeded from what the garbage world actually generates (its 4 biomes, its terrain rules,")
A("its sewers/cooling towers/smokestacks, and the vanilla nether fortress and bastion its biome tags")
A("let through), plus every mob that can exist, plus the mod's loot tables. Then closed under every")
A("recipe that still loads - vanilla minus the 30 the mod disables, plus the mod's own 170 and its")
A("seven custom recipe types - until nothing new appeared. Interactions that are neither loot nor")
A("recipe are encoded explicitly (bucket fills, axe-stripping, oxidation, the Compost Heap volunteer,")
A("the Sequencer's byproduct, the Dry Clay Body cauldron step).")
A("")
A("**Where the mobs come from.** The starting biome is creature-free by design, so the roster is")
A("assembled: the frontier regions spawn the hostile set, the compacted depths spawn the nether set,")
A("the sewers seat a drowned spawner and house turtles and frogs, the landmarks seat a parched and a")
A("husk, **Animal Bait** draws 16 farm and wild species, the **Sequencer** turns amber into spawn")
A("eggs for 29 more, and **curing a zombie villager** opens the whole villager trade tree.")
A("")
A("**Coverage by domain**")
A("")
A("| Domain | Reachable | Total |")
A("|---|---:|---:|")
for d in ORDER:
    items = [i for i, r in rows.items() if r["primary"] == d]
    if items:
        A("| %s | %d | %d |" % (TITLE.get(d, d), sum(1 for i in items if i in R), len(items)))
A("")
A("**Legend.** `(c)` also craftable. `(finite)` non-renewable in vanilla terms.")
A("")
A("---")
A("")

for d in ORDER:
    if d not in byd and d not in demoted:
        continue
    items_all = [i for i, r in rows.items() if r["primary"] == d]
    A("## %s  <sub>%d/%d</sub>" % (TITLE.get(d, d), sum(1 for i in items_all if i in R),
                                   len(items_all)))
    A("")
    if d in BLURB:
        A("*%s*" % BLURB[d])
        A("")
    for m in METHOD_ORDER:
        items = sorted(byd[d].get(m, []))
        if not items:
            continue
        A("### %s" % m)
        A("")
        for i in items:
            r = rows[i]
            nm = i.replace("minecraft:", "")
            c = " `(c)`" if r["craftable"] else ""
            f = " `(finite)`" if i in FINITE else ""
            if i in R:
                A("- [x] `%s`%s%s - %s" % (nm, c, f, R[i]))
            else:
                A("- [ ] `%s`%s%s - %s" % (nm, c, f, why_not(i, r)))
        A("")
    if demoted.get(d):
        names = sorted(demoted[d])
        ok = sum(1 for x in names if x in R)
        A("<details><summary>Also mineable from structures here, but craftable "
          "(%d, %d reachable) - decoration, not a resource</summary>" % (len(names), ok))
        A("")
        A(", ".join(("`%s`" % x.replace("minecraft:", "")) + ("" if x in R else " (no)")
                    for x in names))
        A("")
        A("</details>")
        A("")
    A("")

A("---")
A("")
A("## What Recompile cannot give you")
A("")
A("The %d unreachable rows, by cause. This is the interesting half: each one is a deliberate closure"
  % (len(rows) - nreach))
A("of the vanilla economy, not an oversight, unless noted.")
A("")
causes = collections.Counter()
for i, r in rows.items():
    if i not in R:
        causes[why_not(i, r)] += 1
for w, n in causes.most_common():
    A("- **%d** - %s" % (n, w))
A("")
A("Two are worth calling out because they are one flower away from being reachable, and both now are:")
A("")
A("- **The whole honey chain** hangs on a single vanilla rule: a birch, oak or cherry sapling grown")
A("  within 2 blocks of a flower has a 5% chance of carrying a bee nest. No bee nest generates in this")
A("  world, and a beehive costs honeycomb, so without that rule honeycomb, candles, honey blocks and")
A("  every waxed copper block would be unobtainable.")
A("- **The tree line** runs weedgrass -> Compost Heap -> a volunteer seedling -> Tree Nursery ->")
A("  sapling. Saplings are stripped from every loot roll in the game, so that chain is the only wood")
A("  in Recompile, and every plank, stick, apple and bee nest is downstream of it.")
A("")
A("---")
A("")
A("## Index: by acquisition method")
A("")
A("Items are filed above by *where*; this lists them by *how*. `~` marks one not reachable in Recompile.")
A("")
XREF = [("Fishing", lambda w: any("fishing" in x for x in w)),
        ("Piglin bartering", lambda w: any("piglin barter" in x for x in w)),
        ("Shearing", lambda w: any(x.startswith("shear ") for x in w)),
        ("Charged creeper (mob heads)", lambda w: any(x.startswith("charged creeper") for x in w)),
        ("Sniffer", lambda w: any("sniffer" in x for x in w)),
        ("Archaeology (brushing)", lambda w: any(x.startswith("brush ") for x in w)),
        ("Hero of the Village gifts", lambda w: any("hero of the village" in x for x in w)),
        ("Trial chambers", lambda w: any("trial_chamber" in x or "trial chambers" in x for x in w)),
        ("Villager & wandering trader", lambda w: any(x.startswith("trade ") for x in w))]
for nm, fn in XREF:
    hits = sorted(i for i, r in rows.items() if fn(r["why"]))
    if not hits:
        continue
    A("**%s** <sub>%d, %d reachable</sub>" % (nm, len(hits), sum(1 for h in hits if h in R)))
    A("")
    A(", ".join(("`%s`" % h.replace("minecraft:", "")) + ("" if h in R else "~") for h in hits))
    A("")
A("---")
A("")
A("## Appendix: excluded from the catalogue")
A("")
A("%d items are excluded because their only loot table is the block dropping itself, and nothing in"
  % len(dropped))
A("worldgen, a structure, a mob, a chest or a trade produces one. Those are crafted goods, not")
A("resources. Also excluded: everything with no survival source in any version (bedrock, barrier,")
A("command blocks, spawn eggs, `budding_amethyst`, `petrified_oak_slab`), and the three pottery")
A("sherds - `flow`, `guster`, `scrape` - that name no loot table, trade or structure anywhere in")
A("26.1.2.")
A("")

open(OUTPUT, "w", encoding="utf-8").write("\n".join(out))
print("rows %d  reachable %d (%d%%)" % (len(rows), nreach, round(100 * nreach / len(rows))))

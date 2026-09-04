"""What can a player actually get playing Recompile standalone?

Reachability closure over the mod's world, its loot, and every recipe that still loads.
Seeded from what the world generates and which mobs can exist, then closed under recipes
until nothing new appears. Every reachable item records the route that first reached it.
"""
import json
import re
import os, sys, glob, collections

from paths import WORK as SP, MCDATA, MOD_DATA as MOD, MOD_JAVA
MC = MCDATA + "/data/minecraft"


def jl(p):
    try:
        return json.load(open(p, encoding="utf-8"))
    except Exception:
        return None


# --------------------------------------------------------------- tags
TAGS = {}


def load_tags(root, ns):
    d = os.path.join(root, ns, "tags", "item")
    for p in sorted(glob.glob(d + "/**/*.json", recursive=True)):
        rel = os.path.relpath(p, d).replace("\\", "/")[:-5]
        TAGS.setdefault(ns + ":" + rel, []).extend((jl(p) or {}).get("values", []))


load_tags(MCDATA + "/data", "minecraft")
for ns in ("recompile", "c", "minecraft", "neoforge"):
    if os.path.isdir(os.path.join(MOD, ns)):
        load_tags(MOD, ns)


def tag_items(t, seen=None):
    seen = seen or set()
    t = t.replace("#", "")
    if ":" not in t:
        t = "minecraft:" + t
    if t in seen:
        return set()
    seen.add(t)
    out = set()
    for v in TAGS.get(t, []):
        if isinstance(v, dict):
            v = v.get("id", "")
        if not isinstance(v, str):
            continue
        if v.startswith("#"):
            out |= tag_items(v, seen)
        else:
            out.add(v)
    return out


ABSENT_MODS = {"ae2", "enderio", "simplemagnets", "modonomicon", "jei", "jade"}


def cond_true(c):
    """Evaluate one neoforge condition for a vanilla-only install.

    Substring-matching modids inverts `neoforge:not`: a recipe guarded "load only when AE2 is
    ABSENT" is exactly the configuration being modelled, and would be discarded. That idiom already
    ships one directory over in loot_modifiers/no_sky_stone.json.
    """
    if not isinstance(c, dict):
        return True
    t = c.get("type", "")
    if t == "neoforge:never":
        return False
    if t == "neoforge:true":
        return True
    if t == "neoforge:mod_loaded":
        return c.get("modid") not in ABSENT_MODS
    if t == "neoforge:not":
        return not cond_true(c.get("value"))
    if t == "neoforge:and":
        return all(cond_true(x) for x in c.get("values", []))
    if t == "neoforge:or":
        return any(cond_true(x) for x in c.get("values", []))
    return True          # a condition type we do not model is treated as satisfied


def cond_ok(j):
    """Whether this file would load in a vanilla-only install."""
    return all(cond_true(c) for c in j.get("neoforge:conditions", []))


# --------------------------------------------------------------- mobs
MOBS = {}


def mob(name, why):
    MOBS.setdefault(name, why)


for m in ("cat", "wolf"):
    mob(m, "spawns in household sprawl")
for m in ("creeper", "enderman", "skeleton", "slime", "spider", "witch", "zombie", "zombie_horse",
          "zombie_villager"):
    mob(m, "spawns in the demolition yard / radioactive dump")
for m in ("ghast", "magma_cube", "piglin", "zombified_piglin"):
    mob(m, "spawns in the compacted depths")
for m in ("blaze", "wither_skeleton"):
    mob(m, "nether fortress (generates in the compacted depths)")
for m in ("piglin_brute", "hoglin"):
    mob(m, "bastion remnant (generates in the compacted depths)")
mob("drowned", "sewer spawner")
mob("turtle", "sewer resident")
mob("frog", "sewer resident")
mob("parched", "cooling tower spawner")
mob("husk", "smokestack spawner")
BAIT = ["cow", "mooshroom", "sheep", "rabbit", "horse", "donkey", "mule", "camel", "sniffer",
        "cat", "ocelot", "fox", "armadillo", "chicken", "pig", "parrot"]
for m in BAIT:
    mob(m, "Animal Bait")
AMBER = ["cow", "pig", "sheep", "chicken", "rabbit", "horse", "donkey", "goat", "llama", "fox",
         "wolf", "cat", "parrot", "frog", "turtle", "panda", "polar_bear", "mooshroom", "bee",
         "axolotl", "cod", "salmon", "squid", "bat", "ocelot", "camel", "sniffer", "villager",
         "blaze"]
for m in AMBER:
    mob(m, "spawn egg (amber -> Sequencer -> Blueprint)")
mob("villager", "cure a zombie villager")
mob("wandering_trader", "spawns naturally (no village needed)")
mob("trader_llama", "arrives with the wandering trader")
mob("iron_golem", "built")
mob("snow_golem", "built")
mob("wither", "built from soul sand and wither skeleton skulls")
mob("phantom", "insomnia")
mob("tadpole", "breed frogs")
mob("endermite", "throw an ender pearl")
mob("zoglin", "lure a hoglin into the overworld")

# --------------------------------------------------------------- world blocks
VANILLA_IN_WORLD = {
    "coarse_dirt": "overworld terrain", "deepslate": "overworld terrain",
    "lava": "the compacted depths",
    # NOT the sewers. Nothing in the sewer sources places Blocks.WATER at all - the standing
    # fluid down there is leachate, which is a different fluid on purpose. Exactly two things in
    # the mod place vanilla water: TailingsHeapFeature's decant ponds, and the Municipal
    # Aquarium's guardian tank. The aquarium is the nearer of the two, at the demolition yard's
    # onset rather than the radioactive dump's.
    "water": "a tailings decant pond, or the aquarium's guardian tank",
    "sand": "sewers", "gravel": "sewers", "mud": "sewers", "cobweb": "sewers",
    "red_mushroom": "sewers", "mycelium": "mycelium patches",
    "bricks": "sewers", "brick_stairs": "sewers", "mossy_stone_bricks": "sewers",
    "cracked_stone_bricks": "sewers", "iron_bars": "sewers", "ladder": "sewers",
    "lantern": "sewers", "campfire": "sewers", "barrel": "sewers",
    "suspicious_sand": "sewer silt", "suspicious_gravel": "sewer silt",
}

# --------------------------------------------------- the Municipal Aquarium, read from the Java
#
# THE BUILDING IS PROCEDURAL JAVA, SO THE STRUCTURE INDEX CANNOT SEE IT. Every other structure here
# is NBT and `structblocks.json` reads its palette; this one is built by code, so what it places was
# invisible and every vanilla block whose only source is this building read as unreachable. That was
# #366: the heart of the sea on the centrepiece pedestal, prismarine crystals off the sea lanterns,
# and both sponges, all confidently filed under "no ocean, monument, shipwreck or ocean ruin
# generates".
#
# THE LIST IS NOT RETYPED HERE. It is parsed out of `AquariumStructure.VANILLA_PLACED`, which is the
# one place it is declared, sitting next to the geometry it belongs to and guarded in BOTH
# directions by `the_aquarium_places_exactly_the_vanilla_blocks_it_declares`. A second copy in this
# file is exactly the drift this pipeline's README warns about, and this repo has evidence that a
# second copy drifts.
#
# It fails LOUDLY. A rename or a refactor that this regex cannot follow raises here rather than
# quietly contributing an empty set, because an empty set is indistinguishable from a correct run
# and would silently restore the bug this closes.
def _aquarium_blocks() -> set:
    src = os.path.join(MOD_JAVA, "content", "worldgen", "aquarium", "AquariumStructure.java")
    text = open(src, encoding="utf-8").read()
    start = text.find("VANILLA_PLACED = Set.of(")
    if start == -1:
        raise SystemExit("AquariumStructure.VANILLA_PLACED is gone or renamed. It is the only "
                         "record of what the Municipal Aquarium places; without it every vanilla "
                         "block whose sole source is that building silently reads as unreachable "
                         "(#366). Fix this parse rather than deleting it.")
    # Comments are stripped BEFORE the terminator is located. Finding ");" in the raw text lets a
    # future comment containing one truncate the list silently, and a truncation that still leaves
    # 20+ blocks slips past the guard below - an under-read that reintroduces #366 for the tail.
    clean = " ".join(line.split("//")[0] for line in text[start:].splitlines())
    body = clean[:clean.find(");")]
    found = set(re.findall(r"Blocks\.([A-Z0-9_]+)", body))
    if len(found) < 20:
        raise SystemExit("only %d blocks parsed out of AquariumStructure.VANILLA_PLACED, which is "
                         "far fewer than the building places - the declaration's shape has changed "
                         "and this parse no longer follows it." % len(found))
    return {name.lower() for name in found}


# Worth naming precisely; everything else gets the building itself as its reason.
AQUARIUM_DETAIL = {
    "moss_block": "the Municipal Aquarium's filtration hall",
    "pale_moss_block": "the Municipal Aquarium's centrepiece tank",
    "pale_hanging_moss": "the Municipal Aquarium's centrepiece tank",
    "sponge": "the Municipal Aquarium's filtration hall",
    "wet_sponge": "the Municipal Aquarium's filtration hall",
    "sea_lantern": "the Municipal Aquarium's tank lighting",
    "prismarine": "the Municipal Aquarium's cladding",
    "prismarine_bricks": "the Municipal Aquarium's cladding",
    "dark_prismarine": "the Municipal Aquarium's cladding",
}
def _silk_touch_only(block: str) -> bool:
    """True when every pool of a block's vanilla loot table is gated on Silk Touch.

    WITHOUT THIS THE FIX IS WORSE THAN THE BUG. `walk_items` ignores loot conditions, so seeding a
    block simply credits whatever its table names - and ten of the forty are silk-touch-only (the
    five dead coral plants and their five fans, plus glass and tinted glass). The doc would have
    printed "mine dead_brain_coral (the Municipal Aquarium)" to a player who breaks one and gets
    nothing. That is the direction this whole guard exists to prevent: a reachable-looking row
    nothing in the game will ever contradict.

    They are still credited, because Silk Touch is obtainable here and so the resource genuinely is.
    What changes is that the row SAYS so.
    """
    path = os.path.join(MC, "loot_table", "blocks", block + ".json")
    try:
        table = json.load(open(path, encoding="utf-8"))
    except OSError:
        return False
    pools = table.get("pools") or []
    if not pools:
        return False
    for pool in pools:
        conditions = json.dumps(pool.get("conditions") or [])
        if "silk_touch" not in conditions:
            return False
    return True


for _b in _aquarium_blocks():
    _why = AQUARIUM_DETAIL.get(_b, "the Municipal Aquarium")
    if _silk_touch_only(_b):
        _why += ", with Silk Touch"
    VANILLA_IN_WORLD.setdefault(_b, _why)

SB = json.load(open(SP + "/structblocks.json"))
for b in SB.get("bastion", []):
    VANILLA_IN_WORLD.setdefault(b.replace("minecraft:", ""), "bastion remnant")
for b in ["nether_bricks", "nether_brick_fence", "nether_brick_stairs", "nether_wart", "soul_sand"]:
    VANILLA_IN_WORLD.setdefault(b, "nether fortress")

# --------------------------------------------------------------- loot tables
reach_tables = {}          # table path -> why


def add_table(path, why):
    reach_tables.setdefault(path, why)


for p in sorted(glob.glob(MOD + "/recompile/loot_table/**/*.json", recursive=True)):
    rel = os.path.relpath(p, MOD + "/recompile/loot_table").replace("\\", "/")[:-5]
    if rel.startswith(("gameplay/", "chests/", "archaeology/", "entities/", "equipment/")):
        pretty = {"gameplay/household_pulls": "household pull stream (sort garbage)",
                  "gameplay/bag_pulls": "trash bag pull stream",
                  "gameplay/depths_pulls": "techno-organic waste pull stream",
                  "gameplay/mechanical_pulls": "mechanical waste pull stream",
                  "gameplay/rubble_pulls": "rubble pull stream",
                  "gameplay/slag_rubble_pulls": "slag rubble pull stream",
                  "gameplay/tailings_pulls": "mill tailings pull stream",
                  "gameplay/waste_drum_pulls": "waste drum pull stream",
                  "gameplay/bulky_spine": "bulky waste (pry it open)",
                  "gameplay/bulky_windfall": "bulky waste (pry it open)",
                  "gameplay/hydroponics_seedling": "Hydroponics Bay seedling",
                  "chests/sewer": "sewer chest", "chests/sump": "sewer sump crate",
                  "archaeology/sewer_silt": "brush sewer silt",
                  "entities/roach": "kill a roach", "equipment/sun_cap": "sun cap"}
        add_table("recompile:" + rel, pretty.get(rel, rel))
add_table("recompile:blocks/*", "break a mod block")

for b, why in VANILLA_IN_WORLD.items():
    add_table("minecraft:blocks/" + b, "mine " + b + " (" + why + ")")

for m, why in sorted(MOBS.items()):
    add_table("minecraft:entities/" + m, "kill a " + m.replace("_", " ") + " (" + why + ")")

# mob-gated gameplay/harvest/shearing tables
GATED = [("minecraft:gameplay/cat_morning_gift", "cat", "a tamed cat's morning gift"),
         ("minecraft:gameplay/sniffer_digging", "sniffer", "sniffer digging"),
         ("minecraft:gameplay/armadillo_shed", "armadillo", "an armadillo sheds its scute"),
         ("minecraft:gameplay/chicken_lay", "chicken", "a chicken lays an egg"),
         ("minecraft:gameplay/turtle_grow", "turtle", "a turtle grows up"),
         ("minecraft:gameplay/panda_sneeze", "panda", "a panda sneezes"),
         ("minecraft:gameplay/piglin_bartering", "piglin", "piglin bartering"),
         ("minecraft:brush/armadillo", "armadillo", "brush an armadillo"),
         ("minecraft:shearing/snow_golem", "snow_golem", "shear a snow golem"),
         ("minecraft:shearing/mooshroom", "mooshroom", "shear a mooshroom"),
         ]
for t, need, why in GATED:
    if need in MOBS:
        add_table(t, why)
for col in ("black blue brown cyan gray green light_blue light_gray lime magenta orange pink "
            "purple red white yellow").split():
    if "sheep" in MOBS:
        add_table("minecraft:shearing/sheep/" + col, "shear a sheep")
if "mooshroom" in MOBS:
    for v in ("brown", "red"):
        add_table("minecraft:shearing/mooshroom/" + v, "shear a mooshroom")
for job in ("armorer baby butcher cartographer cleric farmer fisherman fletcher leatherworker "
            "librarian mason shepherd toolsmith unemployed weaponsmith").split():
    add_table("minecraft:gameplay/hero_of_the_village/" + job + "_gift", "Hero of the Village gift")
for t in ("minecraft:gameplay/fishing", "minecraft:gameplay/fishing/fish",
          "minecraft:gameplay/fishing/junk", "minecraft:gameplay/fishing/treasure"):
    add_table(t, "fishing (water from a Rain Collector or the sewers)")
for t in ("creeper", "piglin", "skeleton", "wither_skeleton", "zombie"):
    add_table("minecraft:charged_creeper/" + t, "a charged creeper kills a " + t.replace("_", " "))
# vanilla nether structure chests
for t in ("bastion_bridge", "bastion_hoglin_stable", "bastion_other", "bastion_treasure"):
    add_table("minecraft:chests/" + t, "bastion remnant chest")
add_table("minecraft:chests/nether_bridge", "nether fortress chest")

# --------------------------------------------------------------- seed items
def loot_items(path, depth=0):
    ns, rel = path.split(":", 1)
    root = (MCDATA + "/data/minecraft/loot_table") if ns == "minecraft" \
        else (MOD + "/recompile/loot_table")
    if rel.endswith("/*"):
        files = sorted(glob.glob(root + "/" + rel[:-1] + "**/*.json", recursive=True))
    else:
        files = [root + "/" + rel + ".json"]
    out = set()
    for f in files:
        j = jl(f)
        if j is None:
            continue
        walk_items(j, out, depth)
    return out


def walk_items(o, acc, depth=0):
    """Collect items from a loot json, expanding tags and following nested table references.

    Vanilla sheep, guardian and elder guardian all reach their drops through a
    `minecraft:loot_table` entry, as does the mod's own bulky_waste. Not following those makes a
    table look empty with nothing reporting it.
    """
    if depth > 6:
        return
    if isinstance(o, dict):
        t, nm = o.get("type"), o.get("name")
        if t in ("minecraft:item", "item") and isinstance(nm, str):
            acc.add(nm)
        elif t in ("minecraft:tag", "tag") and isinstance(nm, str):
            acc |= tag_items(nm)
        elif t in ("minecraft:loot_table", "loot_table"):
            ref = o.get("value") if isinstance(o.get("value"), str) else nm
            if isinstance(ref, str):
                acc |= loot_items(ref if ":" in ref else "minecraft:" + ref, depth + 1)
        if isinstance(o.get("item"), str):
            acc.add(o["item"])
        for v in o.values():
            walk_items(v, acc, depth)
    elif isinstance(o, list):
        for v in o:
            walk_items(v, acc, depth)


def stripped_items():
    """Items a global loot modifier removes from every roll in a vanilla-only install.

    CLAUDE.md records that SortingData honours strip modifiers for exactly this reason: a table
    listing an item a modifier deletes is telling the truth about itself and a lie about the world.
    `no_sky_stone` is the live case - it strips the Sky Stone Shard precisely when AE2 is absent,
    which is the configuration modelled here.
    """
    out = set()
    for p2 in sorted(glob.glob(MOD + "/*/loot_modifiers/*.json")):
        j = jl(p2)
        if j is None or not cond_ok(j):
            continue
        t = j.get("type", "")
        if t == "recompile:strip_item" and isinstance(j.get("item"), str):
            out.add(j["item"])
        elif t == "recompile:strip_saplings":
            out |= tag_items("minecraft:saplings")
    return out


STRIPPED = stripped_items()

REACH = {}


def gain(item, why, from_loot=False):
    if from_loot and item in STRIPPED:
        return False          # a global loot modifier deletes it before the player sees it
    if item and item not in REACH:
        REACH[item] = why
        return True
    return False


# "Municipal Aquarium" is LATE for the reason the other two are: these tables should fill a gap,
# never win a race. Seeded in phase 1 they beat the recipe closure - `gain` is first-wins - and
# `glass` regressed from "smelted from red sand" to "mine it in the aquarium", which is true and
# useless. Late, the building only supplies what nothing else does, and the closure still runs to
# a fixpoint afterwards so its downstream chains (a sea lantern into prismarine crystals) close.
LATE = ("fishing", "Hero of the Village", "Municipal Aquarium")


def seed(late):
    for path, why in sorted(reach_tables.items()):
        if (any(k in why for k in LATE)) != late:
            continue
        for it in sorted(loot_items(path)):
            gain(it, why, from_loot=True)


def seed_trades():
    if "villager" not in MOBS:
        return
    base = MCDATA + "/data/minecraft/villager_trade"
    for p in sorted(glob.glob(base + "/**/*.json", recursive=True)):
        j = jl(p) or {}
        g = j.get("gives")
        prof = os.path.relpath(p, base).replace("\\", "/").split("/")[0]
        for e in (g if isinstance(g, list) else [g]):
            if isinstance(e, dict) and isinstance(e.get("id"), str):
                gain(e["id"], "buy from a " + prof.replace("_", " "))



def _aquarium_items() -> set:
    """The vanilla ITEMS the aquarium seats in a block entity, read from the same Java declaration.

    A block sweep cannot see these and neither can a loot table: the heart of the sea sits on the
    centrepiece Display Pedestal and nowhere else in the game, so without this it reads as
    unreachable with a confident "no ocean, monument, shipwreck or ocean ruin generates" beside it.
    Guarded in-game by the same manifest test as the blocks.
    """
    src = os.path.join(MOD_JAVA, "content", "worldgen", "aquarium", "AquariumStructure.java")
    text = open(src, encoding="utf-8").read()
    start = text.find("VANILLA_ITEMS_PLACED = Set.of(")
    if start == -1:
        raise SystemExit("AquariumStructure.VANILLA_ITEMS_PLACED is gone or renamed (#366).")
    clean = " ".join(line.split("//")[0] for line in text[start:].splitlines())
    body = clean[:clean.find(");")]
    found = re.findall(r"Items\.([A-Z0-9_]+)", body)
    if not found:
        raise SystemExit("no items parsed out of AquariumStructure.VANILLA_ITEMS_PLACED.")
    return {name.lower() for name in found}


seed(False)

for _i in _aquarium_items():
    gain("minecraft:" + _i, "the Municipal Aquarium's centrepiece pedestal")

for m in MOBS:
    gain("minecraft:" + m + "_spawn_egg", "Sequencer spawn egg") if m in AMBER else None

# growth unlocks: sapling -> tree, seed -> crop
GROWTH = {"minecraft:oak_sapling": ["oak_log", "oak_leaves"],
          "minecraft:birch_sapling": ["birch_log", "birch_leaves"],
          "minecraft:spruce_sapling": ["spruce_log", "spruce_leaves"],
          "minecraft:jungle_sapling": ["jungle_log", "jungle_leaves"],
          "minecraft:acacia_sapling": ["acacia_log", "acacia_leaves"],
          "minecraft:dark_oak_sapling": ["dark_oak_log", "dark_oak_leaves"],
          "minecraft:cherry_sapling": ["cherry_log", "cherry_leaves"],
          "minecraft:pale_oak_sapling": ["pale_oak_log", "pale_oak_leaves"],
          "minecraft:mangrove_propagule": ["mangrove_log", "mangrove_leaves", "mangrove_roots"],
          "minecraft:wheat_seeds": ["wheat"], "minecraft:beetroot_seeds": ["beetroots"],
          "minecraft:melon_seeds": ["melon"], "minecraft:pumpkin_seeds": ["pumpkin"],
          "minecraft:potato": ["potatoes"], "minecraft:carrot": ["carrots"],
          "minecraft:torchflower_seeds": ["torchflower"], "minecraft:pitcher_pod": ["pitcher_crop"],
          "minecraft:sugar_cane": ["sugar_cane"], "minecraft:bamboo": ["bamboo"],
          "minecraft:cactus": ["cactus"], "minecraft:brown_mushroom": ["brown_mushroom"],
          "minecraft:red_mushroom": ["red_mushroom"], "minecraft:kelp": ["kelp"],
          "minecraft:nether_wart": ["nether_wart"], "minecraft:cocoa_beans": ["cocoa"],
          "minecraft:sweet_berries": ["sweet_berry_bush"], "minecraft:glow_berries": ["cave_vines"],
          "minecraft:moss_block": ["moss_block"], "minecraft:vine": ["vine"],
          "minecraft:sea_pickle": ["sea_pickle"], "minecraft:lily_pad": ["lily_pad"],
          "minecraft:chorus_flower": ["chorus_flower"],
          }

# --------------------------------------------------------------- interactions
# Routes that are neither a loot table nor a recipe. Each is (prerequisites, item, reason).
INTERACT = [(["minecraft:coarse_dirt"], "minecraft:grass_block",
             "Grass Spreader converts coarse dirt to grass"),
            (["minecraft:bone_meal", "minecraft:grass_block"], "minecraft:short_grass",
             "bone meal on grass"),
            (["minecraft:bone_meal", "minecraft:grass_block"], "minecraft:tall_grass",
             "bone meal on grass"),
            (["minecraft:bone_meal", "minecraft:red_mushroom"], "minecraft:red_mushroom_block",
             "bone meal a red mushroom into a huge one"),
            (["minecraft:bone_meal", "minecraft:brown_mushroom"], "minecraft:brown_mushroom_block",
             "bone meal a brown mushroom into a huge one"),
            (["minecraft:bone_meal", "minecraft:red_mushroom"], "minecraft:mushroom_stem",
             "bone meal a mushroom into a huge one"),
            (["minecraft:bucket"], "minecraft:water_bucket",
             "fill a bucket from a tailings decant pond or the aquarium's guardian tank"),
            (["minecraft:bucket"], "minecraft:lava_bucket",
             "fill a bucket from lava in the compacted depths"),
            ]

# THE NETHER FLORA IS BOOTSTRAPPED, and the closure could not see it. Nothing generates nylium in the
# compacted depths - the fill is solid - so both nyliums are CRAFTED from shards, and the whole crimson
# and warped families hang off bone meal applied to them. Reachability is derived from recipes and loot
# and bone meal is neither, so all 13 dependents were reported unreachable with a reason string saying
# the depths have no nylium, in a document that marked both nyliums reachable two hundred lines away.
#
# It cost a wrong issue: #329 was filed asserting both Nether wood families were lost, a fortnight
# AFTER the recipes shipped, written entirely off that reason string.
#
# EVERY EDGE BELOW IS MEASURED, not reasoned about. This file's own hazard is that a hand-declared
# INTERACT route is a claim nothing re-checks, unlike a derived stage which is recomputed each run - so
# the claims are pinned by GameTests instead: bone_meal_on_crafted_nylium_grows_the_nether_flora and
# a_fungus_on_its_nylium_grows_a_huge_fungus in CompactedDepthsTests. Change one of these lines and
# that pair is what should be re-run.
for _hue, _fungus, _stem, _hat in (("crimson", "crimson_fungus", "crimson_stem", "nether_wart_block"),
                                   ("warped", "warped_fungus", "warped_stem", "warped_wart_block")):
    _nylium = "minecraft:%s_nylium" % _hue
    INTERACT.append((["minecraft:bone_meal", _nylium], "minecraft:%s_roots" % _hue,
                     "bone meal on %s nylium" % _hue))
    INTERACT.append((["minecraft:bone_meal", _nylium], "minecraft:" + _fungus,
                     "bone meal on %s nylium" % _hue))
    # The huge fungus is what carries the wood. It needs the fungus standing on its OWN nylium.
    for _out in ("minecraft:" + _stem, "minecraft:" + _hat, "minecraft:shroomlight"):
        INTERACT.append((["minecraft:bone_meal", _nylium, "minecraft:" + _fungus], _out,
                         "bone meal a %s fungus on %s nylium into a huge one" % (_hue, _hue)))
INTERACT.append((["minecraft:bone_meal", "minecraft:warped_nylium"], "minecraft:nether_sprouts",
                 "bone meal on warped nylium"))

# NOT DECLARED: twisting_vines and weeping_vines. They plausibly come off the same two mechanics and
# they were NOT asserted by either test, so they stay unreachable here rather than being claimed on a
# hunch. That is the whole point of the paragraph above.
for w in ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak"):
    _a = "an" if w[0] in "aeiou" else "a"
    INTERACT.append((["minecraft:%s_log" % w], "minecraft:stripped_%s_log" % w,
                     "use an axe on %s %s log" % (_a, w.replace("_", " "))))
    INTERACT.append((["minecraft:%s_wood" % w], "minecraft:stripped_%s_wood" % w,
                     "use an axe on %s wood" % w))
for w in ("crimson", "warped"):
    INTERACT.append((["minecraft:%s_stem" % w], "minecraft:stripped_%s_stem" % w,
                     "use an axe on a %s stem" % w.replace("_", " ")))
    INTERACT.append((["minecraft:%s_hyphae" % w], "minecraft:stripped_%s_hyphae" % w,
                     "use an axe on %s hyphae" % w))
INTERACT.append((["minecraft:bamboo_block"], "minecraft:stripped_bamboo_block",
                 "use an axe on a bamboo block"))
# The Sequencer hands the emptied husk back in its byproduct slot - a Java byproduct, not a recipe.
INTERACT.append((["recompile:amber"], "recompile:spent_amber",
                 "Sequencer byproduct when it reads a piece of amber"))
# Dry Clay Body is hydrated on a filled cauldron, which is an interaction rather than a recipe.
INTERACT.append((["recompile:dry_clay_body"], "minecraft:clay_ball",
                 "hydrate a Dry Clay Body on a filled cauldron"))
INTERACT.append((["minecraft:anvil"], "minecraft:chipped_anvil", "an anvil degrading with use"))
INTERACT.append((["minecraft:chipped_anvil"], "minecraft:damaged_anvil",
                 "an anvil degrading with use"))
# A birch, oak or cherry sapling grown within 2 blocks of a flower has a 5% chance of a bee nest
# (vanilla's *_bees_005 configured features). That is the only bee nest in this world, and the whole
# honey chain - honeycomb, candles, waxed copper - hangs off it.
for _sap in ("birch_sapling", "oak_sapling", "cherry_sapling"):
    for _fl in ("poppy", "dandelion", "torchflower"):
        INTERACT.append((["minecraft:" + _sap, "minecraft:" + _fl], "minecraft:bee_nest",
                         "grow a %s within 2 blocks of a flower (5%% bee nest)"
                         % _sap.replace("_", " ")))
INTERACT.append((["minecraft:bee_nest", "minecraft:shears"], "minecraft:honeycomb",
                 "shear a bee nest grown on a birch"))
INTERACT.append((["minecraft:bee_nest", "minecraft:glass_bottle"], "minecraft:honey_bottle",
                 "bottle a full bee nest"))
# The Compost Heap turns compostables into fertilizer, and a finished layer may turn out a
# "volunteer" seedling with it. That volunteer is the only seed the Tree Nursery accepts.
INTERACT.append((["recompile:weedgrass"], "recompile:fertilizer",
                 "Compost Heap (feed it weedgrass and other compostables)"))
INTERACT.append((["recompile:weedgrass"], "recompile:unknown_seedling",
                 "Compost Heap volunteer"))

# Saplings are stripped from every loot roll in the game, so the Tree Nursery is the only source.
for _sp in ("oak_sapling", "birch_sapling", "spruce_sapling", "jungle_sapling", "acacia_sapling",
            "dark_oak_sapling", "cherry_sapling", "mangrove_propagule", "pale_oak_sapling"):
    INTERACT.append((["recompile:fertilizer", "recompile:unknown_seedling"], "minecraft:" + _sp,
                     "Tree Nursery (fertilizer + unknown seedling)"))
MOB_GIVES = [("cow", "milk_bucket", "milk a cow into a bucket", ["minecraft:bucket"]),
             ("cod", "cod_bucket", "bucket a cod", ["minecraft:bucket"]),
             ("salmon", "salmon_bucket", "bucket a salmon", ["minecraft:bucket"]),
             ("axolotl", "axolotl_bucket", "bucket an axolotl", ["minecraft:bucket"]),
             ("tadpole", "tadpole_bucket", "bucket a tadpole", ["minecraft:bucket"]),
             ("goat", "goat_horn", "a goat rams a hard block", []),
             ("drowned", "trident", "a naturally-spawned drowned drops its trident", []),
             ("wither", "nether_star", "kill the wither", []),
             ("wither", "wither_rose", "the wither kills a mob", []),
             ("turtle", "turtle_egg", "breed turtles on sand", []),
             ]
for m, item, why, pre in MOB_GIVES:
    if m in MOBS:
        INTERACT.append((pre, "minecraft:" + item, why))

LANG = set(json.load(open(SP + "/index.json"))["lang_ids"])
# the bare oxidation stages are named after "copper", but the block you place is copper_block
for _st in ("exposed", "weathered", "oxidized"):
    INTERACT.append((["minecraft:copper_block"], "minecraft:%s_copper" % _st,
                     "leave a copper block out to oxidize"))
for it in sorted(LANG):
    n = it.replace("minecraft:", "")
    for pre in ("exposed_", "weathered_", "oxidized_"):
        if n.startswith(pre):
            base = "minecraft:" + n[len(pre):]
            if base in LANG:
                INTERACT.append(([base], it, "leave copper out to oxidize"))
    if n.endswith("_concrete"):
        powder = "minecraft:" + n + "_powder"
        if powder in LANG:
            INTERACT.append(([powder], it, "drop concrete powder into water"))

# --------------------------------------------------------------- recipes
DISABLED = set()
for p in glob.glob(MOD + "/minecraft/recipe/*.json"):
    if "neoforge:never" in open(p, encoding="utf-8").read():
        DISABLED.add("minecraft:" + os.path.basename(p)[:-5])

def ing_options(x):
    """An ingredient -> the set of items that satisfy it."""
    if x is None:
        return set()
    if isinstance(x, str):
        return tag_items(x) if x.startswith("#") else {x}
    if isinstance(x, list):
        out = set()
        for e in x:
            out |= ing_options(e)
        return out
    if isinstance(x, dict):
        if isinstance(x.get("item"), str):
            return {x["item"]}
        if isinstance(x.get("tag"), str):
            return tag_items(x["tag"])
        if isinstance(x.get("id"), str):
            return {x["id"]}
    return set()


def result_of(j):
    r = j.get("result")
    if isinstance(r, str):
        return [r]
    if isinstance(r, dict):
        if isinstance(r.get("id"), str):
            return [r["id"]]
        if isinstance(r.get("item"), str):
            return [r["item"]]
    return []


RULES = []          # (list_of_ingredient_option_sets, [outputs], label)


UNRESOLVED = set()


def add_rule(ings, outs, label, rid=None):
    """Record a rule, keeping track of ingredient slots that resolved to nothing.

    Dropping an unresolvable slot silently turns an unknown tag into "free", and if every slot were
    unresolvable `all()` over an empty list is True and the output would be reachable from tick
    zero. The slots are still dropped (a `#c:` tag NeoForge ships but we do not parse is a real
    ingredient the player can satisfy), but they are counted and reported so the over-approximation
    is visible rather than silent.
    """
    kept = [i for i in ings if i]
    if len(kept) != len(ings) and rid:
        UNRESOLVED.add(rid)
    outs = [o for o in outs if o]
    if outs:
        RULES.append((kept, outs, label))


def load_recipes(root, ns, disabled=()):
    d = os.path.join(root, ns, "recipe")
    for p in sorted(glob.glob(d + "/**/*.json", recursive=True)):
        rid = ns + ":" + os.path.relpath(p, d).replace("\\", "/")[:-5]
        if rid in disabled:
            continue
        j = jl(p)
        if j is None or not cond_ok(j):
            continue
        t = j.get("type", "")
        outs, ings, label = result_of(j), [], None
        if t in ("minecraft:crafting_shaped", "recompile:blueprint_crafting"):
            ings = [ing_options(v) for v in (j.get("key") or {}).values()]
            label = "crafted"
            if t == "recompile:blueprint_crafting":
                # `blueprint` names a SET, not an item (BlueprintCraftingRecipe: "blueprint names a
                # set, not a recipe"). Three of the shipped values are not item ids at all and the
                # rest equal their own result, so requiring it as an ingredient makes every
                # blueprint recipe inert. What the bench actually requires is a Blueprint sheet.
                ings.append({"recompile:blueprint"})
                label = "crafted from a Blueprint at the Scrap Crafting Table"
        elif t == "minecraft:crafting_shapeless":
            ings = [ing_options(v) for v in j.get("ingredients", [])]
            label = "crafted"
        elif t in ("minecraft:smelting", "minecraft:blasting", "minecraft:smoking",
                   "minecraft:campfire_cooking"):
            ings = [ing_options(j.get("ingredient"))]
            label = {"minecraft:smelting": "smelted", "minecraft:blasting": "blasted in the Cupola",
                     "minecraft:smoking": "smoked",
                     "minecraft:campfire_cooking": "cooked on a campfire"}[t]
        elif t == "minecraft:stonecutting":
            ings = [ing_options(j.get("ingredient"))]
            label = "cut on a stonecutter"
        elif t == "minecraft:smithing_transform":
            ings = [ing_options(j.get(k)) for k in ("template", "base", "addition")]
            label = "smithing"
        elif t == "minecraft:crafting_transmute":
            ings = [ing_options(j.get("input")), ing_options(j.get("material"))]
            label = "crafted"
        elif t == "recompile:teardown":
            ings = [ing_options(j.get("input"))]
            outs = sorted(collect_out(j))
            # A pool marked `teaches` grants an Idea Fragment for whatever it drew, and fragments
            # are the only route to a Blueprint. Without this the whole knowledge tier is invisible.
            if "teaches" in json.dumps(j):
                outs = sorted(set(outs) | {"recompile:idea_fragment"})
            label = "torn down at the Recompile Workbench"
        elif t == "recompile:separating":
            ings = [ing_options(j.get("input") or j.get("ingredient"))]
            outs = sorted(collect_out(j))
            label = "separated in the Separator"
        elif t == "recompile:pulverizing":
            ings = [ing_options(j.get("input") or j.get("ingredient"))]
            outs = outs or sorted(collect_out(j))
            label = "ground in the Pulverizer"
        elif t == "recompile:vitrifying":
            ings = [ing_options(j.get("ingredient") or j.get("input"))]
            label = "vitrified in the Slag Furnace"
        elif t == "recompile:sintering":
            ings = [ing_options(j.get("ingredient") or j.get("input"))]
            label = "fired in the Sintering Kiln"
        elif t == "recompile:fragment_assembly":
            # A bare marker type; the fragments-to-sheet logic lives in Java.
            add_rule([{"recompile:idea_fragment"}], ["recompile:blueprint"],
                     "assembled at the Scrap Crafting Table")
            continue
        elif t == "recompile:market_offer":
            # THE THIRD ACQUISITION AXIS, and the closure was blind to it (docs/market_spec.md
            # section 14). A market line is neither a loot table nor a recipe with ingredients, so
            # without this the two things whose ONLY source is the Buy Terminal read as unreachable
            # - which is the exact failure mode this whole tool exists to catch, pointed the wrong
            # way. An `item` line hands over the thing; a `blueprint` line hands over a sheet, which
            # the closure models as the single `recompile:blueprint` item that gates every
            # blueprint_crafting recipe.
            #
            # The terminal is the prerequisite rather than nothing at all: scrip is not an item, so
            # there is no currency to require, but you cannot buy without the block.
            ings = [{"recompile:buy_terminal"}]
            outs = ([j["item"]] if "item" in j else ["recompile:blueprint"])
            # The renderer appends "from <prerequisites>", so the label must not name the
            # terminal again or the row reads "bought at the Buy Terminal from buy terminal".
            label = "bought for scrip"
        elif t == "recompile:spawn_egg_crafting":
            continue
        else:
            continue
        add_rule(ings, outs, label, rid)


def collect_out(j):
    """Outputs of a mod recipe that names results/extras/pools rather than one result."""
    acc = set()
    for k in ("results", "extras", "pools", "byproducts", "result", "output"):
        if k in j:
            walk_items(j[k], acc)
    return acc


load_recipes(MCDATA + "/data", "minecraft", DISABLED)
load_recipes(MOD, "recompile")
load_recipes(MOD, "minecraft", DISABLED)

# --------------------------------------------------------------- closure
PHASE2 = [False]
changed = True
while changed:
    changed = False
    for item, blocks in list(GROWTH.items()):
        if item in REACH:
            for b in blocks:
                for it in loot_items("minecraft:blocks/" + b):
                    if gain(it, "grow " + item.replace("minecraft:", "").replace("_", " ")):
                        changed = True
    for pre, item, why in INTERACT:
        if all(p in REACH for p in pre) and gain(item, why):
            changed = True
    for ings, outs, label in RULES:
        if all(any(o in REACH for o in opt) for opt in ings):
            used, seen_i = [], set()
            for opt in ings:
                pick = next((o for o in sorted(opt) if o in REACH), None)
                if pick and pick not in seen_i:
                    seen_i.add(pick)
                    used.append(pick.split(":")[-1].replace("_", " "))
            note = label + (" from " + " + ".join(used[:2]) if used else "")
            for o in outs:
                if gain(o, note):
                    changed = True
    if not changed and not PHASE2[0]:
        PHASE2[0] = True
        seed(True)
        seed_trades()
        changed = True

# ------------------------------------------- what the closure closed over, derived not retyped
#
# THE DOC USED TO HARDCODE THE WORD "seven" HERE (#371) and it went stale the day market_offer
# shipped, while the closure itself had already grown the arm to handle it - so the methodology
# paragraph misreported the very run printed beside it. A number describing the code belongs to the
# code.
#
# It also cross-checks, because the count alone would not have caught the thing that actually bit.
# `recipe_rules` is one if/elif chain over `type`, so a registered type with no arm contributes
# NOTHING and reports nothing: the file parses, the run is green, and whatever it produces reads as
# unreachable. That is exactly how the two market-only items came out unreachable. If the mod
# registers a type this closure cannot read, say so loudly and put it in the doc rather than
# printing a confident number over a silent hole.
def _registered_recipe_types() -> list:
    src = os.path.join(MOD_JAVA, "registry", "RCRecipeTypes.java")
    text = open(src, encoding="utf-8").read()
    found = re.findall(r'RECIPE_TYPES\.register\("([a-z_]+)"', text)
    if not found:
        raise SystemExit("no recipe types parsed out of RCRecipeTypes.java - the registration shape "
                         "has changed and this parse no longer follows it (#371).")
    return sorted(set(found))


def _handled_recipe_types() -> set:
    """Types `recipe_rules` actually dispatches on.

    Scoped to the dispatch lines rather than the whole file. Grepping every "recompile:<name>"
    literal counts item ids (`recompile:blueprint`, `recompile:amber`) and anything written in a
    comment as handled, so a newly registered type whose name collided with an item id, or one
    merely mentioned in a TODO, would be reported as covered while having no arm - precisely the
    silent hole this cross-check exists to catch.
    """
    mine = open(os.path.abspath(__file__), encoding="utf-8").read()
    handled = set()
    for line in mine.splitlines():
        stripped = line.split("//")[0].strip()
        if stripped.startswith(("if t ==", "elif t ==", "if t in", "elif t in")):
            handled.update(re.findall(r'"recompile:([a-z_]+)"', stripped))
    return handled


RECIPE_TYPES = _registered_recipe_types()
UNHANDLED_RECIPE_TYPES = sorted(set(RECIPE_TYPES) - _handled_recipe_types())
if UNHANDLED_RECIPE_TYPES:
    print("WARNING: recipe types with no arm in recipe_rules, so anything they produce will read "
          "as unreachable: " + ", ".join(UNHANDLED_RECIPE_TYPES))

json.dump({"reach": REACH, "mobs": MOBS, "recipe_types": RECIPE_TYPES,
           "unhandled_recipe_types": UNHANDLED_RECIPE_TYPES}, open(SP + "/reach.json", "w"), indent=0)
print("reachable items:", len(REACH))
print("mobs available :", len(MOBS))
print("stripped by a loot modifier:", len(STRIPPED))
if UNRESOLVED:
    print("recipes with an unresolvable ingredient slot (treated as satisfiable): %d"
          % len(UNRESOLVED))
    for rid in sorted(UNRESOLVED)[:8]:
        print("   ", rid)

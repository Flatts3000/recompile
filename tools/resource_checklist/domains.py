import json, os, sys, collections

from paths import MCDATA as R, WORK
W = R + "/data/minecraft/worldgen"
T = R + "/data/minecraft/tags"
SP = WORK


def jl(p):
    try:
        return json.load(open(p, encoding="utf-8"))
    except Exception:
        return None


def tagmembers(name):
    j = jl(T + "/worldgen/biome/" + name + ".json") or {}
    out = set()
    for v in j.get("values", []):
        if isinstance(v, dict):
            v = v.get("id", "")
        if isinstance(v, str) and not v.startswith("#"):
            out.add(v.replace("minecraft:", ""))
    return out


TAGNAMES = ["is_ocean", "is_deep_ocean", "is_river", "is_nether", "is_end", "is_overworld", "is_forest",
            "is_jungle", "is_badlands", "is_beach", "is_savanna", "is_taiga", "is_mountain", "is_hill"]
TAGS = {t: tagmembers(t) for t in TAGNAMES}
biomes = sorted(os.path.splitext(f)[0] for f in os.listdir(W + "/biome"))


def domain_of(b):
    if b in TAGS["is_nether"]:
        return "Nether"
    if b in TAGS["is_end"]:
        return "End"
    if b in TAGS["is_ocean"] or b in TAGS["is_deep_ocean"]:
        return "Ocean"
    if b in TAGS["is_river"]:
        return "River"
    if b in TAGS["is_beach"]:
        return "Beach"
    if "cave" in b or b in ("deep_dark", "dripstone_caves", "lush_caves"):
        return "Cave & Underground"
    if b in TAGS["is_badlands"]:
        return "Badlands"
    if b in TAGS["is_jungle"]:
        return "Jungle"
    if b in TAGS["is_savanna"]:
        return "Savanna"
    if b in TAGS["is_taiga"]:
        return "Taiga"
    if b in TAGS["is_mountain"]:
        return "Mountain"
    if b in TAGS["is_forest"]:
        return "Forest"
    if "desert" in b:
        return "Desert"
    if "swamp" in b or "mangrove" in b:
        return "Swamp"
    if "snow" in b or "frozen" in b or "ice" in b:
        return "Snowy"
    if "mushroom" in b:
        return "Mushroom Fields"
    return "Overworld (other)"


BD = {b: domain_of(b) for b in biomes}

CORAL = set()
for c in ("tube", "brain", "bubble", "fire", "horn"):
    for suf in ("_coral", "_coral_fan", "_coral_block"):
        CORAL.add("minecraft:" + c + suf)
        CORAL.add("minecraft:dead_" + c + suf)

HARD = {
    "minecraft:kelp": {"minecraft:kelp"},
    "minecraft:sea_pickle": {"minecraft:sea_pickle"},
    "minecraft:bamboo": {"minecraft:bamboo"},
    "minecraft:chorus_plant": {"minecraft:chorus_flower", "minecraft:chorus_plant"},
    "minecraft:glowstone_blob": {"minecraft:glowstone"},
    "minecraft:basalt_pillar": {"minecraft:basalt"},
    "minecraft:basalt_columns": {"minecraft:basalt"},
    "minecraft:delta_feature": {"minecraft:basalt", "minecraft:magma_block"},
    "minecraft:desert_well": {"minecraft:sandstone", "minecraft:suspicious_sand"},
    "minecraft:ice_spike": {"minecraft:packed_ice"},
    "minecraft:blue_ice": {"minecraft:blue_ice"},
    "minecraft:iceberg": {"minecraft:packed_ice", "minecraft:blue_ice"},
    "minecraft:coral_tree": CORAL, "minecraft:coral_claw": CORAL, "minecraft:coral_mushroom": CORAL,
    "minecraft:sculk_patch": {"minecraft:sculk", "minecraft:sculk_vein", "minecraft:sculk_catalyst",
                              "minecraft:sculk_sensor", "minecraft:sculk_shrieker"},
    "minecraft:monster_room": {"minecraft:spawner", "minecraft:mossy_cobblestone", "minecraft:cobblestone"},
    "minecraft:fossil": {"minecraft:bone_block", "minecraft:coal_ore", "minecraft:diamond_ore"},
    "minecraft:end_island": {"minecraft:end_stone"},
    "minecraft:end_spike": {"minecraft:obsidian", "minecraft:bedrock", "minecraft:iron_bars"},
    "minecraft:end_gateway": {"minecraft:bedrock"},
    "minecraft:vines": {"minecraft:vine"},
    "minecraft:weeping_vines": {"minecraft:weeping_vines"},
    "minecraft:twisting_vines": {"minecraft:twisting_vines"},
    "minecraft:glow_lichen": {"minecraft:glow_lichen"},
    "minecraft:multiface_growth": {"minecraft:glow_lichen"},
    "minecraft:dripstone_cluster": {"minecraft:pointed_dripstone", "minecraft:dripstone_block"},
    "minecraft:large_dripstone": {"minecraft:pointed_dripstone", "minecraft:dripstone_block"},
    "minecraft:pointed_dripstone": {"minecraft:pointed_dripstone", "minecraft:dripstone_block"},
    "minecraft:root_system": {"minecraft:rooted_dirt", "minecraft:hanging_roots"},
    "minecraft:geode": {"minecraft:amethyst_block", "minecraft:budding_amethyst",
                        "minecraft:amethyst_cluster", "minecraft:calcite", "minecraft:smooth_basalt"},
    "minecraft:seagrass": {"minecraft:seagrass"},
    "minecraft:sea_grass": {"minecraft:seagrass"},
    "minecraft:waterlily": {"minecraft:lily_pad"},
    "minecraft:freeze_top_layer": {"minecraft:snow", "minecraft:ice"},
    "minecraft:huge_red_mushroom": {"minecraft:red_mushroom_block", "minecraft:mushroom_stem"},
    "minecraft:huge_brown_mushroom": {"minecraft:brown_mushroom_block", "minecraft:mushroom_stem"},
    "minecraft:huge_fungus": {"minecraft:shroomlight", "minecraft:nether_wart_block",
                              "minecraft:warped_wart_block"},
}

# ---- load configured + placed features
CF, PF = {}, {}
for f in os.listdir(W + "/configured_feature"):
    j = jl(W + "/configured_feature/" + f)
    if j is not None:
        CF[f[:-5]] = j
for f in os.listdir(W + "/placed_feature"):
    j = jl(W + "/placed_feature/" + f)
    if j is None:
        continue
    fe = j.get("feature")
    PF[f[:-5]] = fe if isinstance(fe, str) else "@inline"
    if not isinstance(fe, str):
        CF["@pf:" + f[:-5]] = fe


def collect(o, blocks, refs):
    """Walk a feature json: gather block Names, feature-type HARD hits, and named refs."""
    if isinstance(o, dict):
        n = o.get("Name")
        if isinstance(n, str) and n.startswith("minecraft:"):
            blocks.add(n)
        t = o.get("type")
        if isinstance(t, str) and t in HARD:
            blocks |= HARD[t]
        for k, v in o.items():
            if k == "feature" and isinstance(v, str):
                refs.add(v.replace("minecraft:", ""))
            else:
                collect(v, blocks, refs)
    elif isinstance(o, list):
        for v in o:
            collect(v, blocks, refs)


def resolve(name, seen=None):
    """Resolve a feature name that may be a configured OR placed feature id."""
    seen = seen or set()
    if name in seen:
        return set()
    seen.add(name)
    node = CF.get(name)
    if node is None:
        if name in PF:                       # placed feature -> its configured feature
            tgt = PF[name]
            if tgt == "@inline":
                node = CF.get("@pf:" + name)
            else:
                return resolve(tgt.replace("minecraft:", ""), seen)
        if node is None:
            return set()
    blocks, refs = set(), set()
    collect(node, blocks, refs)
    for r in refs:
        blocks |= resolve(r, seen)
    return blocks


bio_blocks = collections.defaultdict(set)
bio_mobs = collections.defaultdict(set)
unresolved = collections.Counter()
for b in biomes:
    j = jl(W + "/biome/" + b + ".json") or {}
    for tier in j.get("features", []):
        for p in tier:
            if not isinstance(p, str):
                continue
            nm = p.replace("minecraft:", "")
            got = resolve(nm)
            if not got:
                unresolved[nm] += 1
            bio_blocks[b] |= got
    for grp, lst in (j.get("spawners") or {}).items():
        for e in lst:
            if isinstance(e, dict) and isinstance(e.get("type"), str):
                bio_mobs[b].add(e["type"])

json.dump({"biome_domain": BD,
           "biome_blocks": {k: sorted(v) for k, v in bio_blocks.items()},
           "biome_mobs": {k: sorted(v) for k, v in bio_mobs.items()}},
          open(SP + "/worldgen.json", "w"), indent=0)
print("biomes:", len(biomes))
print("blocks mapped:", len(set().union(*bio_blocks.values())))
print("mobs mapped:", len(set().union(*bio_mobs.values())))
print("features yielding no blocks:", len(unresolved))
print("  ", ", ".join(sorted(unresolved)[:25]))

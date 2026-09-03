import json, os, sys, collections

from paths import MCDATA as ROOT, WORK as SP
LT = os.path.join(ROOT, "data/minecraft/loot_table")
RC = os.path.join(ROOT, "data/minecraft/recipe")
VT = os.path.join(ROOT, "data/minecraft/villager_trade")
TG = os.path.join(ROOT, "data/minecraft/tags/item")
LANG = json.load(open(os.path.join(ROOT, "assets/minecraft/lang/en_us.json"), encoding="utf-8"))


def jl(p):
    try:
        return json.load(open(p, encoding="utf-8"))
    except Exception:
        return None


# ---- item tags, so loot entries of type minecraft:tag can be expanded
def tag_items(name, seen=None):
    seen = seen or set()
    name = name.replace("minecraft:", "")
    if name in seen:
        return set()
    seen.add(name)
    j = jl(os.path.join(TG, name + ".json"))
    if j is None:
        return set()
    out = set()
    for v in j.get("values", []):
        if isinstance(v, dict):
            v = v.get("id", "")
        if not isinstance(v, str):
            continue
        if v.startswith("#"):
            out |= tag_items(v[1:], seen)
        else:
            out.add(v)
    return out


def walk(o, hits):
    """Collect items from arbitrary loot json, expanding tag entries."""
    if isinstance(o, dict):
        t = o.get("type")
        nm = o.get("name")
        if t in ("minecraft:item", "item") and isinstance(nm, str):
            hits.add(nm)
        elif t in ("minecraft:tag", "tag") and isinstance(nm, str):
            hits |= tag_items(nm)
        for v in o.values():
            walk(v, hits)
    elif isinstance(o, list):
        for v in o:
            walk(v, hits)


src = collections.defaultdict(set)
tbl_items = {}
for dp, _, fns in os.walk(LT):
    for fn in fns:
        if not fn.endswith(".json"):
            continue
        p = os.path.join(dp, fn)
        rel = os.path.relpath(p, LT).replace("\\", "/")[:-5]
        j = jl(p)
        if j is None:
            print("ERR", rel)
            continue
        hits = set()
        walk(j, hits)
        tbl_items[rel] = hits
        for it in hits:
            src[it].add(rel)

craft = set()
for dp, _, fns in os.walk(RC):
    for fn in fns:
        if not fn.endswith(".json"):
            continue
        j = jl(os.path.join(dp, fn)) or {}
        r = j.get("result")
        if isinstance(r, dict) and isinstance(r.get("id"), str):
            craft.add(r["id"])
        elif isinstance(r, str):
            craft.add(r)

trade = set()
for dp, _, fns in os.walk(VT):
    for fn in fns:
        if not fn.endswith(".json"):
            continue
        j = jl(os.path.join(dp, fn)) or {}
        rel = os.path.relpath(os.path.join(dp, fn), VT).replace("\\", "/")[:-5]
        g = j.get("gives")
        gs = g if isinstance(g, list) else [g]
        for e in gs:
            if isinstance(e, dict) and isinstance(e.get("id"), str):
                trade.add(e["id"])
                src[e["id"]].add("trade/" + rel)

items = set()
for k in LANG:
    if k.startswith(("item.minecraft.", "block.minecraft.")) and k.count(".") == 2:
        items.add("minecraft:" + k.split(".")[2])

json.dump({"src": {k: sorted(v) for k, v in sorted(src.items())},
           "craftable": sorted(craft), "trade": sorted(trade),
           "lang_ids": sorted(items),
           "tables": {k: sorted(v) for k, v in sorted(tbl_items.items())}},
          open(SP + "/index.json", "w"), indent=0)
print("items with a loot/trade source:", len(src))
print("craftable results:", len(craft))
print("sourced but NOT craftable:", len([i for i in src if i not in craft]))

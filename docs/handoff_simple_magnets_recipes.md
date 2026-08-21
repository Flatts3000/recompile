# Handoff: re-theme Simple Magnets onto Magnet Scrap

**From:** the Trashlands pack (`../trashlands`), issue
[#40](https://github.com/Flatts3000/trashlands/issues/40).
**Analysed against:** Recompile **v0.13.0**, Simple Magnets `1.1.12-neoforge-mc26.1` (CF project
394140, file 8370420), MC 26.1.2 / NeoForge 26.1.2.94.
**Status:** specced, not implemented. This is a request, not a change.

## Why this is a Recompile job and not a pack job

The pack cannot ship data of its own on 26.1.2. It has no datapack loader (Open Loader and Datapack
Loader have no 26.1.2 NeoForge build), KubeJS crashes the client on load
([kube-mods/kubejs#1178](https://github.com/kube-mods/kubejs/issues/1178), reproduced 2026-08-20 on
NeoForge 26.1.2.94 with the bundled tooltip jar alone in an empty mods folder), CraftTweaker has not
ported, and `release.yml` rejects a loose jar in the CurseForge export so a datapack-as-mod is out.

Recompile already ships conditional data for another mod: `data/recompile/recipe/guide_book.json`
produces a Modonomicon item behind `neoforge:mod_loaded`. This is the same pattern.

## What Simple Magnets ships today

Four shaped recipes, all on materials this world does not have a story for:

| Item | Inputs |
|---|---|
| `simplemagnets:basicmagnet` | 5x `#c:ingots/iron`, `#c:gems/lapis`, `#c:ender_pearls`, `#c:dusts/redstone` |
| `simplemagnets:advancedmagnet` | 4x `#c:ingots/gold`, `#c:gems/lapis`, the basic magnet, `minecraft:ender_eye`, `#c:gems/diamond`, `#c:dusts/redstone` |
| `simplemagnets:basic_demagnetization_coil` | `#c:ingots/gold`, 2x `#c:dusts/redstone`, 4x `#c:ingots/iron` |
| `simplemagnets:advanced_demagnetization_coil` | `#c:dusts/glowstone`, 2x `#c:dusts/redstone`, 3x `#c:ingots/gold`, the basic coil |

An ender pearl in a magnet is a placeholder, not a recipe.

## The material this already has

**`recompile:magnet_scrap`.** Weight 15 of 227 in `loot_table/gameplay/mechanical_pulls.json`, and
its only use today is `recipe/separating_redstone.json`, one scrap to one redstone with a scrap metal
byproduct.

It is also the world's only redstone source, which is the good part rather than a problem: spending
magnet scrap on magnets means not spending it on redstone. That is a real decision of the kind the
mod is built on. It is also true, which matters more here than it usually would: neodymium is
recovered from hard drive actuators and speaker voice coils, and that is exactly what magnet scrap is.

## Proposed recipes

Substitution rule: iron becomes `recompile:scrap_metal`, gold becomes `minecraft:copper_ingot`, lapis
and ender pearls become `recompile:magnet_scrap`, and diamond and the ender eye become
`recompile:fused_circuitry`, which is a Nether material and so a real tier step. Redstone stays,
because it exists here and comes out of the same scrap.

**Basic Magnet** - a salvaged permanent magnet in a scrap housing. Horseshoe on purpose.

```
M M      M = recompile:magnet_scrap  (4)
M M      S = recompile:scrap_metal   (2)
SCS      C = minecraft:copper_ingot  (1)
```

**Advanced Magnet** - an electromagnet, so it needs control as well as mass.

```
 F       F = recompile:fused_circuitry     (1)
MBM      M = recompile:magnet_scrap        (2)
CCC      B = simplemagnets:basicmagnet     (1)
         C = minecraft:copper_ingot        (3)
```

**Basic Demagnetization Coil** - a degausser: copper winding on a scrap core. Real e-waste plants
degauss drives before shredding them, so this one needs no invention.

```
 C       C = minecraft:copper_ingot  (1)
RSR      R = minecraft:redstone      (2)
SSS      S = recompile:scrap_metal   (4)
```

**Advanced Coil** - the powered version.

```
 G       G = minecraft:glowstone_dust           (1)
RCR      R = minecraft:redstone                 (2)
CBC      C = minecraft:copper_ingot             (3)
         B = simplemagnets:basic_demagnetization_coil (1)
```

Every id verified present at v0.13.0: `magnet_scrap`, `scrap_metal`, `fused_circuitry` in Recompile,
and `copper_ingot`, `redstone`, `glowstone_dust` all reachable in this world.

**Balance is a first pass and wants playtesting.** Four magnet scrap per basic magnet is roughly
sixty Mechanical Waste pulls, and it competes with the only redstone in the world.

## The part that is easy to get wrong

**Overriding another mod's recipe means shipping a file at the same resource location** -
`data/simplemagnets/recipe/basicmagnet.json` and the other three - and **load order decides who
wins**.

This mod already has the scar. `neoforge.mods.toml` carries a long comment on its `neoforge`
dependency explaining that with `ordering = "NONE"` its data lost to NeoForge's own, and seventeen
found-only recipe overrides were silently ignored with no error anywhere - the bug was only caught
because corrupting one of the ignored files produced no parse error at all.

So this needs an optional dependency that orders Recompile after Simple Magnets:

```toml
[[dependencies.recompile]]
modId = "simplemagnets"
type = "optional"
ordering = "AFTER"
side = "BOTH"
```

and each of the four files needs the usual guard so it is inert without the mod:

```json
"neoforge:conditions": [
  { "type": "neoforge:mod_loaded", "modid": "simplemagnets" }
]
```

**Verify in game that the override actually took**, not merely that the pack loads. That is the
lesson the seventeen-recipe incident already paid for once.

## What the pack will do

Nothing until this ships. Simple Magnets is pinned and shipping with its stock recipes, and the pack
issue stays open pointing here.

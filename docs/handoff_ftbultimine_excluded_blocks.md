# Handoff: keep FTB Ultimine off the salvage terrain

**From:** the Trashlands pack (`../trashlands`), issue
[#62](https://github.com/Flatts3000/trashlands/issues/62).
**Analysed against:** Recompile **v0.19.0-dev**, FTB Ultimine (NeoForge) `26.1.2.5` (CF project
386134, file 8231335), MC 26.1.2 / NeoForge 26.1.2.100.
**Status:** **WITHDRAWN 2026-09-07, same day it was raised. Do not build this.** The premise was
that the pack cannot ship data on 26.1.2. That stopped being true hours later: KubeJS is in the
pack, pinned alongside standalone `better-advanced-tooltips-2601.1.0-build.9`, which displaces the
broken bundled `build.8` that used to crash the client at bootstrap. The tag now ships from the
pack at `pack/kubejs/data/ftbultimine/tags/block/excluded_blocks.json`, which is where a curation
decision belongs under the engine/pack split. Nothing is being asked of Recompile. The pack's file has
since moved past the list below (it adds `minecraft:deepslate`, pack PR #68), so read it
there rather than here.

The rest is kept for two reasons: the two observations at the end are still live questions for
this repo, and the same route now exists to take back the Simple Magnets overrides and the Ender
IO grains, which their own handoffs said to reclaim the moment it opened. **Simple Magnets took
that route on 2026-09-08** (`trashlands#47`, engine half #420) and its four files are gone from
this repo; Ender IO's are still here pending `trashlands#52`.

## What the pack needs

One file:

    src/main/resources/data/ftbultimine/tags/block/excluded_blocks.json

    {
      "replace": false,
      "values": [
        "minecraft:coarse_dirt",
        "recompile:garbage_block",
        "recompile:trash_bag",
        "recompile:compacted_bale",
        "recompile:cardboard_pile",
        "recompile:bulky_waste",
        "recompile:mound_ground",
        "recompile:stained_ground",
        "recompile:stone_rubble",
        "recompile:mechanical_waste",
        "recompile:mill_tailings",
        "recompile:waste_drum",
        "recompile:techno_organic_waste",
        "recompile:slag_rubble",
        "recompile:ancient_sculk",
        "recompile:tire"
      ]
    }

`"replace": false` matters. Ultimine ships this tag empty in its own jar; replacing it rather than
appending would be correct today and wrong the moment Ultimine or another mod puts something in it.

## Why the pack is adding a vein-miner at all

FTB Ultimine was cut from Trashlands on 2026-08-02 for one reason, recorded in that pack's
`docs/pack_setup.md`: digging Blocks of Garbage out of mounds *is* the core loop, so an ungated
vein-miner takes a mound down in one hold and rewrites the pick-through economy. The same entry adds
that gated off garbage it has almost nothing left to do, "because there is no ore and no wood".

That second half has aged. There is wood now - the reclamation ladder runs soil to vegetation to
nursery to animals, and a healed footprint grows trees and crops. There is stone in the Nether and in
shard-crafted terrain. So excluding the salvage terrain no longer leaves Ultimine with nothing; it
leaves it pointed at the **rebuilt** world instead of the garbage one. Trees, crops, farmland, Nether
stone, player builds. That is a convenience, not an economy skip, and it draws a line the pack
actually wants drawn: the junkyard is dug by hand, the world you make from it is not.

## Why this was raised as a Recompile job

*(The premise as it stood on the morning of 2026-09-07; it was false by the end of that day, see
Status.)* The pack cannot ship data of its own on 26.1.2. No datapack loader has a 26.1.2 NeoForge build (Open
Loader 354339 and Datapack Loader 309529 both stop short), KubeJS is still the build that crashed the
client on load, CraftTweaker has not ported, and `release.yml` rejects a loose jar in the CurseForge
export so a datapack-as-mod is out. A world datapack is per-save and cannot ship with the pack.

Identical constraint to `handoff_simple_magnets_recipes.md` and the Ender IO grains, and the same
answer: it lives in the engine because it has nowhere else to live.

## This is temporary, and it should be built to be deleted

Same terms as the other two cross-mod handoffs. Track it for removal alongside #46 and #47 on the
pack side.

- **One file, deletable wholesale.** Do not fold these entries into `hostile_ground`,
  `vacuumable/*` or any other engine tag that carries real behaviour.
- **Nothing may come to depend on it.** No guidebook entry, no `docs/` reference outside this
  handoff, no GameTest asserting the tag's contents - a test pinning it turns the removal into a code
  change instead of a delete.
- **Not in `pack_extension.md`.** That documents what a pack may rely on. This is the opposite: a
  thing a pack should take back the moment it can.
- The removal trigger is a working datapack route on the pack side, not a release number, and nothing
  will announce it.

A tag naming a mod that is absent is inert - the block tag is built and never queried - so no
`neoforge:mod_loaded` guard is needed or possible here, and a Recompile-only world is unaffected.

## The list, and why it is flat

Nine of the sixteen entries are already enumerated by `#recompile:vacuumable/netherite`, which
transitively covers copper, iron and diamond: `garbage_block`, `trash_bag`, `compacted_bale`,
`stone_rubble`, `mechanical_waste`, `mill_tailings`, `waste_drum`, `techno_organic_waste` and
`slag_rubble`. Referencing that tag instead of listing blocks was
considered and **not** recommended: it couples what Ultimine may break to the Garbage Vacuum's tier
ladder, so a future retier of the vacuum would silently change vein-mining. The failure would be
quiet and would surface as a balance complaint, not an error.

The flat list has the opposite failure mode: a new garbage block added to Recompile is **not**
excluded until someone remembers this file. That is the trade taken, and it is the safer one because
the failure is visible the first time anyone swings a pick at the new block. Whoever adds a garbage
block should add it here.

## Two observations for the engine owner, not requests

- **Four garbage blocks are not vacuumable:** `cardboard_pile`, `bulky_waste`, `mound_ground` and
  `tire`. Everything else the worldgen features pile up is in the `vacuumable/*` ladder. That may be
  deliberate - a vacuum that pulls loose garbage but not bulky items or the ground itself is a
  coherent rule - but the pack could not tell from the tags alone, and `tire` in particular reads
  like an omission now that tire dumps are a harvest target.
- **`minecraft:coarse_dirt` is in the list on owner call.** It is the world's ground, and a shovel
  vein-miner flattens the plain with it. It is also vanilla and shared with contexts Recompile does
  not own, which is the one entry here that is genuinely a pack opinion rather than an engine one.
  If Recompile would rather not assert that globally, say so and the pack will drop it from the
  request and live with terraforming being fast.

## How to verify

`dev.ftb.mods.ftbultimine.shape.BlockMatcher.check(BlockState, BlockState)` reads: if the block
whitelist is non-empty and the target is not in it, reject; then if the target is in
`ftbultimine:excluded_blocks`, reject; only then delegate to the shape's own matcher. So the tag
short-circuits selection outright.

In game: hold the Ultimine key on a Block of Garbage in a mound. The highlight should cover the one
block under the crosshair and nothing else. Then do the same on a tree grown on healed ground and
confirm the whole trunk highlights.

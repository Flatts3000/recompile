# Worldgen notes

How the garbage world is generated - the preset chain, the compacted depths Nether, Ancient Sculk, pile regrowth, and encroachment - plus the silent traps in each.

Relocated from CLAUDE.md on 2026-09-10; CLAUDE.md keeps a one-line index entry pointing here.

- [Worldgen chain](#worldgen-chain)
- [The starting biome's spawners](#the-starting-biomes-spawners)
- [The compacted depths (the Nether)](#the-compacted-depths-the-nether)
- [Ancient Sculk](#ancient-sculk)
- [Three things that bite in worldgen](#three-things-that-bite-in-worldgen)
- [Regrowing ground](#regrowing-ground)
- [Encroachment](#encroachment)

## Worldgen chain

All custom and all singular-dir:
`world_preset/garbage.json` (inlines the level stem; injected into the world-creation list by `data/minecraft/tags/worldgen/world_preset/normal.json`) -> `noise_settings/garbage.json` (a flat coarse-dirt slab on ~60 blocks of deepslate, bedrock on the underside, void below that; sea level -64, no aquifers/ores - the rock is deep because the sewers need somewhere to be, and `the_world_has_rock_enough_to_hold_a_sewer` measures it against vanilla's own mineshaft descent rather than against the gradient, so retuning terrain cannot quietly take the room back) -> the `recompile:region` biome source (`RegionBiomeSource`, which places `household_sprawl`, `demolition_yard` from 512 and `radioactive_dump` from 1024 on a distance gradient; see `demolition_yard_spec.md`) -> `biome/household_sprawl.json` (and the two frontier biomes) -> its `features` array -> `placed_feature/*` -> `configured_feature/*` -> a `Feature<NoneFeatureConfiguration>` registered in `RCFeatures` (`MoundFeature`, `MyceliumPatchFeature`, and the other pile and structure features - read `registry/RCFeatures.java` for the current set).

## The starting biome's spawners

`household_sprawl` has an **empty `monster` list by design** - nothing hostile spawns in the starting biome, which is why food comes from tin cans and foraged mushrooms rather than from hunting. Its other spawner lists are **not** all empty: `creature` carries `minecraft:cat` and `minecraft:wolf` at weight 2 each, and `ambient` carries `recompile:pigeon` at weight 3. The design rationale holds regardless - none of the three yields meat, and the mod ships no entity loot table for the pigeon at all - but do not assume the biome is creature-free or that its `spawners` block needs no looking at.

## The compacted depths (the Nether)

**The same preset defines the Nether, and it is the compacted depths** (P3.5, owner 2026-08-19). `noise_settings/compacted_depths.json` is vanilla's nether shape - min_y 0, height 128, bedrock shell both ends - with `final_density` a **constant 1** and `default_block` set to `recompile:techno_organic_waste`. Solid every column, floor to ceiling: *the overworld is a dump you clear, the Nether is a dump you mine.* The only voids are embedded structures, and vanilla fortresses and bastions generate because both are **biome-tag driven** - `#minecraft:has_structure/nether_fortress` and `.../bastion_remnant` - so a themed biome hosts them with a two-line data change and hosts nothing at all without it. Slag rubble and lava arrive as `minecraft:ore` features, which is block REPLACEMENT rather than an ore, so neither needed Java. **No ancient debris in worldgen** (owner ruling).

## Ancient Sculk

**Ancient Sculk is the only deep dark in the game** (#266, v0.14.0). There is no deep dark biome and no
ancient city, so nine vanilla items had no source at all. One `minecraft:ore` feature - size 6, count 8,
about 1 in 680 - lays seams of `recompile:ancient_sculk` through the fill, and the block breaks into
Sculk Powder that crafts the family (sculk, veins, a sensor around redstone, a shrieker around soul
sand, a catalyst around an **echo shard**, which is one per sewer and the only other thing here that
came out of the deep dark). Two things worth keeping:

- **Vanilla's `sculk_patch` feature is unusable here.** It decorates an exposed surface and needs air
  beside it; the depths are solid floor to ceiling, so it would place nothing, log nothing and throw
  nothing. An ore feature is block REPLACEMENT, which is the only shape that works in solid fill.
- **It is the first block in this mod to gate on tool TIER rather than tool TYPE**, and it does it with
  no Java: `#recompile:mineable/sledgehammer` for the type plus `#minecraft:needs_diamond_tool` for the
  tier. That works only because `RCItems.COPPER_TIER` is built on `INCORRECT_FOR_STONE_TOOL` - retier
  the copper sledgehammer and this silently opens.

## Three things that bite in worldgen

- **A missing `minecraft:not` on the roof gradient fills the whole dimension with bedrock, and nothing errors.** Vanilla's nether wraps its bedrock-roof `vertical_gradient` in `minecraft:not`; without it the condition is true for every block below top-5, so the surface rule paints the entire column. It parses, it generates, every test passes, and the density function is innocent the whole time. This shipped once and was only found by walking in, which is why `CompactedDepthsTests` asserts the fill and the shell separately.
- **The generator is baked into `level.dat` at world creation.** An existing save keeps whatever Nether it was made with, so changing these files only affects NEW worlds - the same shape as the regrowth note below ([Only new worlds regrow](#only-new-worlds-regrow)). Testing a worldgen change therefore needs a fresh world, and the dev client's `--quickPlaySingleplayer` world is **not** one: quickPlay creates a DEFAULT world that silently ignores the preset. Use `./gradlew runServer` with `level-type=recompile\:garbage` in `run/server.properties` and probe it over RCON.
- **RCON here closes the connection after each command**, so a probe loop has to reconnect per call rather than hold one session. And `data get block` only answers for block ENTITIES: on ordinary terrain it reports "not a block entity", which is easy to misread as the chunk being absent. `execute if block <pos> <id> run <anything>` is the probe that actually works.

## Regrowing ground

**Piles regrow, and the memory is a block** (`RegrowingGroundBlock`, design P1.6 + P1.6-R, Phase 5). A pile's feature writes a ground block under every footprint cell carrying **how many blocks belong on that column** - so the exact footprint and profile survive with no `SavedData`, no worldgen-thread concurrency and no region tracking, the same palette-flyweight idiom as `SortableBlock`'s `sorted`. It random-ticks: short column, and one block is spawned above and falls in, so replenishing piles are visible across the plain.

### Three regions, three grounds, one mechanic

(Owner, 2026-09-08.) Mound Ground grows Blocks of Garbage in the household sprawl, **Rubble Ground** grows Stone Rubble in the demolition yard, and **Stained Ground** grows Mill Tailings in the radioactive dump. A subclass supplies the product and nothing else. **This REVERSED a 2026-08-22 ruling** that the frontier does not regrow - *the sprawl regrows because you live in it; the frontier does not, because you leave* - whose supporting sentence, "the yard already does not regrow", described an accident rather than a decision: nobody had ruled the frontier out, `MoundGroundBlock` simply had one writer. **Mechanical Waste is deliberately NOT in scope** and neither is anything in the Nether; the owner named three piles.

### Only two of the three can be retired

**That asymmetry IS the dump's design.** Greening a footprint takes the memory with it, and both Mound Ground and Rubble Ground are in `#recompile:spreadable`. Stained Ground deliberately is not - *contamination that scrubs clean is not contamination* - so **a tailings impoundment never retires and the radioactive dump is permanently non-reclaimable**. Scarcity there moved from the deposit, which now refills, to the land, which never comes back. Nothing in Java enforces this; it falls out of the tag, which is why `the_yard_can_be_retired_and_the_dump_cannot` asserts the tag directly.

### The decant pond

**The decant pond survives regrowth because the MEMORY never claims its cell**, not because anything checks what is in it. `TailingsHeapFeature` records the count of tailings rather than the column height, and a pond column is cut one block below the plateau - so the highest cell regrowth can target is the top tailings block and the fluid above it is out of reach. Every liquid is `canBeReplaced`, so the obvious version would have filled the basin in one block at a time, silently, over a long time, in a region nobody watches. The pond holds Tailings Slurry (#423); what matters is that a fluid block is replaceable, not which fluid it is.

### Five things that bite

- **Mound Ground is coarse dirt with a different name and a darker face** (owner, 2026-08-05), and everything follows: coarse dirt's hardness, sound, shovel, and no tool gate. Its texture is a *retint* of vanilla coarse dirt calibrated to mean luma 66 against coarse dirt's 90.4 - the same material, unmistakably darker ground. (Rubble Ground and Stained Ground have the same relationship to coarse dirt; see their javadoc in `RCBlocks`.) It is deliberately **out of `#minecraft:dirt`**, because membership would reach `#encroachable` through `#substrate_overworld` and the junkyard would eat its own memory.
- **`HEIGHT` is a COUNT, and 0 means inert.** The feature fills `dy = 0..column` *inclusive*, so a rim cell of column 0 still carries one block; storing the top offset builds every mound one block short and leaves 0 ambiguous. As a count, 0 can only mean "nobody remembers a mound here", which is what makes a hand-placed block inert rather than the seed of a mound that never existed.
- **A bed is written into coarse dirt and nothing else** (`RegrowingGroundBlock.isBedGround`, #432). It was "any solid block that is not a pile", which is wider than ground: a sewer entrance's Reinforced Concrete pad is solid and placed before features run, so a rubble pile landing on it turned the pad into Rubble Ground. All three writers ask the one predicate; `no_pile_writes_its_bed_over_a_sewer_entrance` pins it.
- **Overlapping piles must take the taller.** A feature only writes into air, so piles interleave - and a later pile's rim (column 0) would otherwise overwrite a tall neighbour's memory and permanently flatten what regrows there. All three writers carry this rule; it is the sort of thing a fourth would forget.
- **Retirement is the block being gone.** Rung 1 greens it (`#recompile:spreadable`) and the memory goes with it. Encroachment reverts grass to *plain* coarse dirt and never to Mound Ground (P1.7-R item 5), so only the green is contested and mound retirement is permanent.

### Only new worlds regrow

**Worldgen writes the memory, so only new worlds regrow.** A save made before this shipped has no memory block and its piles stay finite; accepted (owner). That applies twice more: a world generated before P1.6-R has no Rubble Ground at all and its Stained Ground carries height 0, so an existing yard and dump stay as finite as they were.

### The column walk is blind to pile kind

**The column walk is deliberately blind to WHICH pile block it finds**, and the kind-aware version is strictly worse rather than merely different. A cell holding another pile's block was never this column's to fill, because a feature only writes into air - so counting it as filled and growing above it is correct. Checking the kind instead targets that cell, finds it occupied by something not replaceable, and returns BLOCKED every tick forever, so the column never grows PAST the foreign block. `a_foreign_pile_block_does_not_stall_the_column` pins the forgiving behaviour so nobody corrects it into the stalling one.

## Encroachment

**The junkyard fights back, and it needs no saved state** (`RCEncroachment`, design P1.7-R). Healed grass bordering unhealed ground reverts to coarse dirt; the reclamation ladder is the defence (bare grass reverts, cover is stripped *instead*, logs/leaves make it permanent). Three facts make the whole system cheap and are worth keeping in mind:

- **Coarse dirt is the universal world surface** (the `noise_settings` surface rule), so every healed patch is by definition ringed by unhealed ground. The frontier test is a local neighbour check - no mound memory, no `SavedData`, no region tracking.
- **That same fact is why nothing renews on its own:** vanilla grass cannot spread onto coarse dirt. **So the rung-1 soil spreader must convert coarse dirt *straight* to grass** - leave plain dirt as an intermediate and vanilla spread quietly finishes the job for free, breaking P2.4-R item 3.
- The sweep samples **around players**, not loaded chunks, so an unattended base cannot rot while its owner is away. The mod has **no mixins**, so vanilla `grass_block` behaviour is not injected; the sweep is the mechanism.

Only the *green* is contested. Encroachment reverts to **plain** coarse dirt, never to the Phase 5 mound bed, so mound retirement stays permanent.

### Targeting

**Targeting is an allowlist tag minus a denylist tag, and both are built from other tags** so chisel-style mods that add dirt variants are covered without a mod release. `encroachable` is `#minecraft:substrate_overworld` plus farmland plus the optional common tag `#c:dirt`; `encroachment_immune` carves back out **coarse dirt** (the revert target - otherwise the sweep churns bare ground forever) and **mycelium** (the substrate `MyceliumPatchFeature` places and dump mushrooms grow on, so eating it would erode the P1.9 forage economy). Tags can union but not subtract, which is why the second tag exists rather than a shorter first one.

### Wet farmland holds, dry farmland is taken

**One rule is blockstate, not block, so no tag can express it:** *wet farmland holds, dry farmland is taken.* Irrigation defends a plot and an abandoned one dries out and goes back to the dump, which makes the P1.10 water economy a reclamation defence rather than only an input. Keyed on `BlockStateProperties.MOISTURE` rather than on `minecraft:farmland`, so modded farmland is covered without being named. It is the one place encroachment displaces player investment, and the severity is bounded: a crop on dry farmland comes off with the soil but vanilla's `updateOrDestroy` **drops** it, so you lose the plot and the growth, never the seed. Tuning is those two plus `hostile_ground`, `frontier_anchor`, `frontier_cover` and the biome tag `encroaches`, over the `reclamation` config block - the mechanic is inert outside the garbage biomes. `encroachOnce` is the static test entry point; the sweep owns targeting (config gate, biome, heightmap), which is why the GameTests can run it on a plain plot.

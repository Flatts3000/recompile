# Extending Recompile from a pack

**Everything on this page is a datapack change.** No mod release, no Java, no dependency on Recompile
beyond having it installed. This exists because the question that prompted it - *can a modpack alter
sewer loot?* - has the same answer as a dozen others, and that answer was not written down anywhere.

The split it follows is the engine/pack one: **Recompile is the engine and Trashlands is the pack.**
Curation, tuning and most cross-mod content belong on the pack side, and the engine is built so they can
live there.

## How overriding works at all

A pack ships a file at the same namespaced path the mod uses, and the pack's copy wins. A world or
global datapack sits **above** every mod's datapack in the pack stack, so there is no ordering question
to get wrong.

> **This is not the trap CLAUDE.md documents for the sixteen bed recipes.** That one is *mod versus
> mod*: NeoForge re-ships 17 vanilla recipe ids, and overriding those needed `ordering = "AFTER"` in
> `neoforge.mods.toml`. Pack-over-mod has no such ambiguity, and nothing on this page is affected by it.

If a pack wants to **add** rather than **replace**, a NeoForge global loot modifier does that without
touching the mod's file at all. The directory is `data/<pack>/loot_modifiers/` - **plural**, it is
NeoForge's folder rather than one of the dirs 26.1 singularised - and in 26.1 there is **no index file**:
every JSON in it is loaded by directory scan.

## Loot

### Sewers

Three tables, and one of them is not where you would look for it:

| Table | Fills |
|---|---|
| `recompile:chests/sewer` | the access-chamber barrels |
| `recompile:chests/sump` | the sump crate |
| `recompile:archaeology/sewer_silt` | every brushable suspicious sand and gravel deposit |

`SewerPieces` holds these as `ResourceKey<LootTable>` and calls `setBlockEntityLootTable` /
`setLootTable` at generation time, so the id is resolved from the **live registry** when the container is
opened. Nothing is baked into the structure and nothing is hardcoded in Java.

**The echo shard is the load-bearing entry.** `chests/sump` is its only source in the entire game. A pack
that edits that table without keeping one will make an item unobtainable, and nothing will report it -
`SewerLootTests` asserts that exclusivity against the mod's *bundled* files, which a pack override does
not touch.

### Pull streams

The weighted streams that a sortable block yields when you pick through it, rolled from Java via
`getLootTable` + `getRandomItems`. **Tuning drop rates means editing these, not Java.**

`recompile:gameplay/` holds `household_pulls`, `bag_pulls`, `mechanical_pulls`, `rubble_pulls`,
`slag_rubble_pulls`, `depths_pulls`, `bulky_spine`, `bulky_windfall`, `hydroponics_seedling` and
`dried_bouquet` (what a Dried Bouquet turns into in a water cauldron; the interaction is Java, the
odds are yours).

They declare `"type": "minecraft:chest"` despite never being a chest - that is what gates loot-context
param validation - so a replacement must keep it.

### Finds

Adding a find to Bulky Waste is **a line in `recompile:blocks/bulky_waste`** and nothing else. There are
no per-find models, no structure templates and no entities; a find only becomes a thing when it is an
item in a hand.

## Recipes

Seven public recipe types. A pack writes these the way it writes a vanilla one, in
`data/<pack>/recipe/`:

| Type | Shape |
|---|---|
| `recompile:teardown` | pools, weighted draws, and `teaches` for the blueprint system |
| `recompile:separating` | one feed into several distinct outputs plus byproducts |
| `recompile:pulverizing` | one input, one finer output |
| `recompile:vitrifying` | vanilla's cooking schema; the Slag Furnace |
| `recompile:sintering` | vanilla's cooking schema; the Sintering Kiln |
| `recompile:blueprint_crafting` | a grid recipe gated on holding a Blueprint |
| `recompile:spawn_egg_crafting` | a grid recipe whose result is read off a Blueprint IN the grid |

`docs/teardown_schema_spec.md` is the reference for the first one and is treated as public API.

**`spawn_egg_crafting` is the odd one and the reason it exists is worth knowing before copying it.**
It has no `result` field at all: the result is computed from the Blueprint sitting in the grid, whose
set is `recompile:spawn_egg/<namespace>/<path>` and whose species must have a `<path>_spawn_egg` item.
So one recipe covers every creature, including ones from mods this pack has never heard of, and a pack
extends the set by adding amber to a pull stream rather than by writing recipes.

It could not be a `blueprint_crafting` recipe. That schema names one set per recipe, so a per-species
family means one recipe per species sharing a single 3x3 arrangement - and the Scrap Crafting Table
resolves a blueprint recipe by taking the first whose sheet is within reach, so a player holding two
sheets would get whichever iterated first. The sheet has to be IN the grid for the player to name which
one they mean, which is also why this is the only recipe in the mod where a Blueprint is an input. It
is not consumed: the table's result slot puts it back, gated on a spawn-egg recipe having matched.

**One rule binds all of them:** on the three GUI-less machines - Trommel, Separator, Pulverizer - a
recipe must not consume more than one input (owner, 2026-08-19). Those machines have no screen and are
not `Container`s, so a partial batch is invisible and unrecoverable except by breaking the block. The
engine will not brick if a pack ignores this - both head scans skip a slot they cannot run rather than
stalling on it - but the machine will be slower and stranger than the pack intends.

## Tags

**`#recompile:vacuumable/<tier>` - what a Garbage Vacuum of each tier may take** (#336). Four files, `copper` / `iron` / `diamond` / `netherite`, and each one **includes the band below it** as a `#tag` entry rather than restating it - so widening copper widens every tier above it, and the ladder is written once. Add a modded pile to a band and that tier can vacuum it, with no mod release.

Two things to know before extending it. The gate **fails closed**: a `SortableBlock` in no band is takeable by nobody, and `every_sortable_block_is_in_a_vacuum_band` fails the build on one rather than letting the tool silently ignore it. And the cost comes from `SortableBlock.sortRolls`, not from the tag - a pile that is banded but absent from that table is vacuumed for free and can never run a vacuum flat, which `every_vacuumable_pile_costs_charge` also fails the build on.


Every one of these is data, so a pack extends the behaviour without a mod release:

| Tag | Means |
|---|---|
| `#recompile:found_only` | no recipe may produce this; it is found. Two tests enforce both halves |
| `#recompile:binnable` | a Scrap Bin will accept it |
| `#recompile:scrap_connectable` | **block** tag; placed touching, these form one Scrap Network cluster |
| `#recompile:vitrifiable` / `#recompile:sinterable` | what those two machines accept on a shift-click |
| `#recompile:burn_barrel_smeltable` | the Burn Barrel's refuse allowlist |
| `#recompile:stone_shards` / `#recompile:nether_shards` | the two terrain-shard families |
| `#recompile:compostable` / `#recompile:hydroponic` | inputs for those machines |
| `#recompile:undiscoverable` | kept out of the viewers |

Encroachment is tuned the same way: `#recompile:encroachable` minus `#recompile:encroachment_immune`,
both built from other tags so a chisel-style mod's dirt variants are covered automatically.

## The guidebook

Modonomicon scans a fixed `modonomicon/books` folder under **every** namespace, so a pack adds entries to
`recompile:guide` by shipping `data/<pack>/modonomicon/books/guide/entries/...`. An entry does not list
its pages - the `pages/` directory is scanned - so adding a page is adding a file.

Two things that fail silently and are worth repeating here: a `text` naming a lang key that does not
exist renders the raw key to the player, and **a blank line is not a paragraph break**. A break is a
blank line followed by two backslash-terminated lines; a lone newline renders as a space.

## What a pack cannot change from data

- **The viewers will not follow.** `SortingData` and the JEI categories read the mod's **bundled** JSON
  rather than the live registry, because loot tables are not client-synced. A pack that retunes a pull
  stream gets the new behaviour in-world and the old numbers in JEI. Accepted limitation, recorded in
  CLAUDE.md.
- **The mod's own tests do not see pack data either**, for the same reason. That is correct - they are
  testing the mod - but it means a pack cannot lean on them to catch its own mistakes.
- **Worldgen shape.** Sewer layout, the region gradient and the compacted depths' fill are Java. A pack
  can change what is *in* them; it cannot change what they are.

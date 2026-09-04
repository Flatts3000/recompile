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

### The Municipal Aquarium

| Table | Fills |
|---|---|
| `recompile:chests/aquarium_curator` | the curator's chest in Back of House: the four nautilus armours, the sixteen trim templates, four wetlands plants, the arid vivarium's five, spare sponges, turtle scute |
| `recompile:archaeology/aquarium_silt` | the filtration hall's brushable silt bed: the nineteen pottery sherds the sewers do not carry |

### Tires

`recompile:blocks/tire` is worth reading before retuning: **what a tire drops is tool-gated inside the
loot table**, with a `match_tool` on the Scrap Knife choosing rubber over the tire itself. The order of
the `alternatives` children is load-bearing - the knife entry must come first, or a knife yields a tire
and the gate silently does not exist. A pack that reorders them will not get an error.

`recompile:rubber_scrap` is deliberately **not** in `furnace_fuels`. Tire dumps do not replenish, so a
fuel entry would pair a finite material with an infinite sink; a pack adding one is choosing to let
players burn the only rubber in the game.

Same mechanism as the sewers (`setBlockEntityLootTable` / `setLootTable` at generation, resolved live
when opened or brushed). **Several of these entries are the item's only source in the game** - the
nautilus armours, the trim templates, `turtle_scute`, and eighteen of the nineteen sherds - so a pack
that trims the table is removing an item from the world, and the resource checklist will say so on
its next run.

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
`slag_rubble_pulls`, `depths_pulls`, `tailings_pulls`, `waste_drum_pulls`, `bulky_spine`,
`bulky_windfall`, `hydroponics_seedling` and `dried_bouquet` (what a Dried Bouquet turns into in a
water cauldron; the interaction is Java, the odds are yours). That is all twelve - this line was
short by two until #344 and nobody noticed across three edits, so count the directory rather than
trusting the sentence.

They declare `"type": "minecraft:chest"` despite never being a chest - that is what gates loot-context
param validation - so a replacement must keep it.

### Finds

Adding a find to Bulky Waste is **a line in `recompile:blocks/bulky_waste`** and nothing else. There are
no per-find models, no structure templates and no entities; a find only becomes a thing when it is an
item in a hand.

## Recipes

Eight public recipe types. A pack writes these the way it writes a vanilla one, in
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
| `recompile:market_offer` | one line of the Buy Terminal's stock: a price, plus exactly one of a Blueprint set (`blueprint`) or an item (`item` + optional `count`) |

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


Every one of these is data, so a pack extends the behaviour without a mod release. **The mod ships 39
tag files under `data/recompile/tags/`; this table is the pack-relevant subset, not the whole set** -
it listed eleven for a while under a heading that reads as exhaustive, which is the wrong way round
for a page whose whole job is to say what a pack can reach. `find data/recompile/tags -name "*.json"`
is the authority.

| Tag | Means |
|---|---|
| `#recompile:found_only` | no recipe may produce this; it is found. Two tests enforce both halves |
| `#recompile:sellable` | what the Sell Terminal will buy. Membership only; the price is the `recompile:scrip_value` data map, and a member with no price fails the build. Nothing binnable, and nothing craftable from binnable inputs alone - `nothing_sellable_is_raw_scrap_or_one_step_from_junk` enforces both |
| `#recompile:binnable` | a Scrap Bin will accept it |
| `#recompile:scrap_connectable` | **block** tag; placed touching, these form one Scrap Network cluster |
| `#recompile:vitrifiable` / `#recompile:sinterable` | what those two machines accept on a shift-click |
| `#recompile:burn_barrel_smeltable` | the Burn Barrel's refuse allowlist |
| `#recompile:stone_shards` / `#recompile:nether_shards` | the two terrain-shard families |
| `#recompile:compostable` / `#recompile:hydroponic` | inputs for those machines |
| `#recompile:undiscoverable` | kept out of the viewers |
| `#recompile:bait/{herbivore,carnivore,omnivore}` | **entity_type** tags, one per `AnimalBaitBlock` diet state. The animal tier's whole tuning surface: which creatures a bait of that diet will draw. A modded animal is baitable by adding one line |
| `#recompile:mineable/{knife,prybar,sledgehammer,cutting_torch}` | **block** tags, the four bespoke tool types. Which blocks each tool is the correct tool for |
| `#recompile:spreadable` / `#recompile:spread_immune` | **block** tags read by `GrassSpreaderCoreBlock.isSpreadable`: what the Grass Spreader converts, and what it must leave alone (mycelium today). **Deliberately separate from the encroachment pair** - `encroachment_immune` contains coarse dirt, which is this machine's primary TARGET, so the two systems mean opposite things by "immune" |
| `#recompile:dump_plants` | **block** tag; this mod's own ground cover (weedgrass, fireweed), and a member of `#frontier_cover` |
| `#recompile:pigeon_forageable` | **block** tag; what a pigeon will pick at |
| `#recompile:has_structure/{sewer,cooling_tower,smokestack,municipal_aquarium}` | **worldgen/biome** tags. See below - these are the landmark dial |

**A `market_offer` can sell an ITEM, not just knowledge, and that is the one extension point here
that adds a wholly new way to obtain something** (`docs/market_spec.md` section 14). `"blueprint"`
sells a sheet, so the buyer still needs the materials and the bench; `"item"` sells the thing itself,
which is the only route in this mod by which an object enters the world without being found, grown or
built. Use it for what this world genuinely cannot produce - the two shipped lines are a Totem of
Undying and, as knowledge, a Bucket of Powder Snow, both of which
`docs/vanilla_resource_checklist.md` lists as unreachable. **Two guards bind a pack here**: nothing in
`#recompile:found_only` may be sold, nor the knowledge to make it (the found-only rule is enforced by
a sweep over recipes, and a shop counter is not a recipe), and a line must carry exactly one of
`blueprint` or `item` or it is refused at parse.

**The market is three files a pack can touch and nothing it cannot** (`docs/market_spec.md`).
`tags/item/sellable.json` says what the Sell Terminal takes; `data_maps/item/scrip_value.json` says
what each pays, as a bare integer per item or tag; and every line of the Buy Terminal's stock is one
`recompile:market_offer` recipe, `{"blueprint": "<set>", "price": N}`, so a pack adds, reprices or
removes an offer by adding, editing or overriding one file. The balance itself is a data attachment
on the player and is not reachable from data, which is the point of it.

**Every landmark structure is retargetable from data.** All four of `worldgen/structure/*.json` set
`"biomes"` to `#recompile:has_structure/<name>` rather than naming a biome, so overriding one biome
tag moves a landmark to a different region, adds it to a modded biome, or removes it from the world
entirely by emptying the tag. Nothing about it is in Java. This page never said so, which is exactly
the sort of question it exists to answer.

Encroachment is tuned the same way, and it reads **six** tags rather than the two this line used to
name: `#recompile:encroachable` minus `#recompile:encroachment_immune` decides the soil (both built
from other tags, so a chisel-style mod's dirt variants are covered automatically), `#frontier_cover`
is what gets stripped instead of the ground, `#frontier_anchor` is what makes a patch permanent,
`#hostile_ground` is what counts as unhealed for the frontier test, and the biome tag
`#recompile:encroaches` gates the whole mechanic to the garbage biomes. `RCEncroachment` is where all
six are read; `docs/roadmap.md` already had this right, so this copy was the drifted one.

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

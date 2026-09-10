# Machines and materials notes

The demolition yard and the iron gate, the Cupola and its slag, the six processing machines and the material chains they open, and the public recipe types that carry them.

Relocated from CLAUDE.md on 2026-09-10; CLAUDE.md keeps a one-line index entry pointing here.

- [The demolition yard and the iron gate](#the-demolition-yard-and-the-iron-gate)
- [The Cupola, the Burn Barrel and slag](#the-cupola-the-burn-barrel-and-slag)
- [Steel I-Beams](#steel-i-beams)
- [Six machines, six verbs](#six-machines-six-verbs)
- [The chains the machines unblocked](#the-chains-the-machines-unblocked)
- [Breaking machines and recipe collisions](#breaking-machines-and-recipe-collisions)
- [The data spine: public recipe types](#the-data-spine-public-recipe-types)

## The demolition yard and the iron gate

**The demolition yard is the first frontier region, and it is where iron comes from** (Phase 4, spec `docs/demolition_yard_spec.md`). `RegionBiomeSource` places biomes on a **distance gradient** from origin, not by climate noise - household sprawl is guaranteed inside `core_radius` (512) and frontier regions appear past their own onset, so travel is the gate. The yard supplies stone (Stone Rubble -> shards -> the vanilla stone family), concrete, and steel.

**The iron gate is a recipe type, and that is the second design of it.** `../trashlands/docs/material_economy.md` makes copper the everyman metal and iron the gated upgrade. What enforces it now: **both iron recipes are `minecraft:blasting`** (Steel Offcut -> ingot, rebar -> nugget; `iron_from_steel_offcut.json`, `iron_nugget_from_rebar.json`) and the **Cupola Furnace is a `RecipeType.BLASTING` machine**. A vanilla furnace cannot run a blasting recipe at all, and a vanilla blast furnace costs 5 iron ingots, so it is circular and unreachable before iron. The gate is a property of the machine, and no fact about the world's materials has to hold for it to work.

**The first design failed silently and is worth knowing about** (#91). It was: iron recipes are ordinary `smelting`, gated because the Burn Barrel refuses them *and no other furnace is craftable*. That second clause died when the Tree Nursery shipped - wood makes a wooden pickaxe, a wooden pickaxe drops cobbled deepslate (plain `deepslate` is in `mineable/pickaxe` and in no `needs_*_tool` tag), and cobbled deepslate is in `#minecraft:stone_crafting_materials`. `stone_from_shards` gives a second route and world deepslate a third. Worse, **`rebar` is a weight-40 entry in `household_pulls`**, so a player could stockpile it on day one and make iron at rung 4 with no demolition yard, no Cutting Torch and no Cupola. The old comment named that exact failure mode as a risk and it happened anyway, because a gate built from *the absence of a material* dies the moment anything adds the material. `no_smelting_recipe_turns_a_mod_item_into_iron` now asserts it instead. Check `../trashlands/docs/progression_gates.md` before touching this.

## The Cupola, the Burn Barrel and slag

**The Cupola rakes slag off every few smelts** (#236, owner 2026-08-18). Slag is a byproduct and has no recipe: it is the non-metallic fraction that floats off any remelt, and the machine hands one over whether you want it or not. It **cannot** be a recipe output - the Cupola is a `RecipeType.BLASTING` machine because that IS the iron gate, and vanilla blasting has one result and no byproduct slot - so it lives in `CupolaFurnaceBlock.getTicker`'s wrapper, the same seam `drainOutput` uses, and detects a finished smelt by sampling the result slot ACROSS the tick (vanilla refuses every other route into slot 2, so growth there can only be a smelt). Counted, not rolled: one per eight (config `cupolaSmeltsPerSlag`, default 8), which is about the real slag-to-metal ratio.

It goes to the two machines whose verbs fit it: the **Separator** divides it into concrete powder with recovered scrap metal as the byproduct (slag is a mixture, and reprocessing it for entrained metal is real practice), and the **Pulverizer** grinds it into Fertilizer (ground slag was sold as phosphate fertiliser for a century). The third is the point of the whole chain: the **Slag Furnace** vitrifies it into **obsidian**, which `material_economy.md` has always said is made only, and which the Nether gate rides on. No new item either way - Reinforced Concrete already drops concrete powder when broken, so the Separator route is that same material arriving by manufacture rather than by demolition.

**Being blast-only means the Cupola does not cook food**, deliberately: a cupola furnace melts metal. The Burn Barrel keeps refuse and food and is still craftable on its own, and Scrap Metal has a blasting twin so copper survives the upgrade.

**The barrel's refuse-only rule is an allowlist gated in the ticker**, not on the slot. 26.1's `Slot.mayPlace` returns true unconditionally and vanilla's `FurnaceMenu` uses a plain `Slot`, so `Container.canPlaceItem` is never consulted; and `AbstractFurnaceBlockEntity` keeps `quickCheck`/`recipeType` private with a static `serverTick`, so recipe lookup cannot be overridden. Skipping the tick is the only seam, and it fails closed without burning fuel.

## Steel I-Beams

**Steel I-Beams draw their run, not their connections** (`SteelBeamBlock`, ported from Create's Metal Girder - MIT code, its assets are All Rights Reserved and none are reproduced). `AXIS` is fixed at placement from the clicked face; `X`/`Z` mean "part of a horizontal run on that axis"; neither set means a vertical column, which is why a lone beam is a full member rather than a stub.

**Worldgen must decide whether to resolve connections.** Blocks placed with flag 2 skip neighbour updates, so each keeps the state it was given: correct for the steel stack (wreckage must not fuse into a lattice) and wrong for the Building Husk (a frame must), which resolves its joints in a second pass through `SteelBeamBlock.updateState`.

## Six machines, six verbs

**Six machines, six verbs, and the split is the design** (#187, #188, #189 shipped 2026-08-16/17, #236 on 2026-08-19, #248 on 2026-08-20, #294 on 2026-08-29). The test is what an operation does to the material:

| Operation | Changes | Machine |
|---|---|---|
| a garbage block into its drops | what it is | **Trommel** (a size cut) |
| Spent Abrasive into a diamond | what it is | **Separator** (it divides) |
| E-Scrap into circuit powder | only fineness | **Pulverizer** (it reduces) |
| slag into obsidian | its **state** | **Slag Furnace** (it vitrifies) |
| pressed powder into a rod | its **direction** | **Sintering Kiln** (it consolidates) |
| amber into the idea of a spawn egg | **nothing** | **Sequencer** (it reads) |

### The first three: Trommel, Separator, Pulverizer

**The Separator used to do all three and now does one.** Sorting was a second MODE on it for four releases and moved to the Trommel; a shear shredder destroys distinctions and sorting requires one, so the machine that sorts is the one that makes a size cut. The word "grinder" deliberately no longer names the Separator (see `SeparatorCoreBlock`'s javadoc) - it names the Pulverizer now, and a tree where one word names two machines is one where the confusion recurs.

The first three share one contract - powered, GUI-less, no `Container` and no item capability, fed by reaching out (loose items in the mouth, a container parked on them drained), output routed to the Scrap Network then a chute then the floor. `MachineParityTests` derives that list from the REGISTRY (every multiblock core answering `Capabilities.Energy.BLOCK`) rather than from a hand-list, and asserts Jade coverage, network membership, the closed door, and that each says how to feed it. They were built by copying each other, which is how they agreed and also how they came apart: the Pulverizer shipped with zero Jade providers against the Separator's four.

### The fourth: the Slag Furnace

**The fourth is the odd one out on purpose, and it breaks the shared contract above rather than bending to it.** The first three change what a material *is* or how fine it is; vitrifying changes its state, and the same silicates come out the other side as an amorphous glass. It is also the only one that is not a powered multiblock: it is a single block with a furnace screen, because the other three are conveyor machines you feed and walk away from and this is one you put a lump into and watch. So it runs on fuel rather than FE, subclasses `AbstractFurnaceBlock`, and does not appear in `MachineParityTests` at all - that sweep derives its list from multiblock cores answering `Capabilities.Energy.BLOCK`, and this answers neither. **Its parity is with the Burn Barrel and the Cupola instead**, and `SlagFurnaceTests` is where that is asserted.

### The fifth: the Sintering Kiln

**The fifth runs the other way, and that is the point of it** (#248). The first four all take something apart - a size cut, a division, a reduction, a state change - and the Pulverizer alone ships **eight** recipes (measured 2026-09-10), seven of which produce a POWDER (the eighth grinds tires to rubber scrap). Nothing turned powder back into a solid, which is a missing DIRECTION rather than a missing recipe, and blaze rods sat behind it. Sintering fuses a pressed compact below its melting point, which is how powder metallurgy makes rod stock. Like the Slag Furnace it is a single block with a screen burning fuel rather than FE, so it is outside `MachineParityTests` too; its parity is with the Burn Barrel, the Cupola and the Slag Furnace.

**A cooking recipe consumes exactly one item, and that is why there is a briquette.** Vanilla crafts one blaze rod into TWO blaze powder, so a one-for-one powder-to-rod recipe would print rods forever. Four powder press into a Blaze Briquette at a bench (`blaze_briquette.json`) and the kiln fires the briquette, which is also the real sequence - powder metallurgy compacts a green body first and sinters it second.

### The sixth: the Sequencer

**The sixth changes nothing at all, which is what earns it a row** (#294). Every other operation here alters the material: a size cut, a division, a reduction, a state change, a consolidation. Sequencing reads information OUT of a piece of amber and hands its contents back as knowledge, which is a new column rather than a sixth instance of an existing one - and a new column is this repo's own bar for adding a machine. It is also the only one that is neither a multiblock nor a furnace: a powered single block, so like the Slag Furnace and the Kiln it sits outside `MachineParityTests`, whose sweep derives its list from multiblock cores answering `Capabilities.Energy.BLOCK`.

## The chains the machines unblocked

**The chains they unblocked**, each of which had been parked with nowhere correct to run:

- **Gold** (#120): E-Scrap -> Circuit Powder (pulverizing, **1:1**) -> gold nugget (Cupola, blasting). Grinding is what liberates metal from the resin and glass holding it; blasting is also the gate, since a vanilla furnace cannot run a blasting recipe.
- **Clay** (#115): a pottery sherd -> Grog (pulverizing) -> + Kitty Litter -> Dry Clay Body (grid) -> clay (right-click a filled water cauldron). **Firing is irreversible** - kaolinite dehydroxylates above ~550C and cannot be rehydrated - so crushed ceramic is grog, a NON-plastic temper, and the plasticity has to come from the bentonite in cat litter. The two halves are useless apart. It unlocks 43 vanilla items, and it needed a source added for sherds: this world has no archaeology, so they were unobtainable and the whole chain was a dead end until one entered `household_pulls`.
- **Rubber** (#155): tire dumps in the household sprawl. `material_economy.md` has listed rubber as an intermediate since P2.2 and it was the only one of the five with no origin at all. A tire is a plain slab-shaped block rather than a `SortableBlock` (owner: a tire is not something you pick through), and **what it drops is decided by the tool in the loot table** - bare hand gives the tire, a Scrap Knife gives the rubber, which is a `minecraft:match_tool` condition and no Java. Fire on a tire is netherrack's, through `IBlockExtension.isFireSource` rather than an `infiniburn` tag, so it survives rain and does not consume the block.
  Two things about the FEATURE are worth keeping. **The sprawl surface is 86 to 92 percent Mound Ground** (measured: 943 and 884 of 1024 columns in two fresh chunks), so the owner's "no Mound Ground under a pile" rule implemented as a survey is not rarity, it is a total ban - the dump **retires** the ground instead, converting it to coarse dirt, which is also a correctness fix because live Mound Ground would drop Blocks of Garbage onto the tires. And **each pile retries its offset** ten times, because piles refuse actual mounds and the first build put all of a dump's tires in one blob.
- **Dried Bouquet**, a sibling of the Clay chain's cauldron step that runs the other way (#331, #335, 2026-09-03): a **Dried Bouquet** from `household_pulls` rehydrates into one of the five two-block plants - the four tall flowers and the large fern - or tears down for Fiber Scrap. **The five are missing for two different reasons** (#344). The trader stocks every small flower and has never sold a tall one, so those four are simply absent; the large fern is placed as a BLOCK already by `FertilizerScatter` and the trader sells a fern, so what is missing there is the `large_fern` ITEM, because a placed one shears into `minecraft:fern`. That also splits renewability: `TallFlowerBlock` is `BonemealableBlock` so one flower lasts forever, while a large fern is a plain `DoublePlantBlock` and is spent when placed. A fired pot lost something that cannot be put back; a dried flower lost only water. Which plant is a loot table (`gameplay/dried_bouquet`, the seedling lottery's shape), so a pack retunes it; the interaction itself is still Java, and both cauldron interactions live in `RCCauldronInteractions`.
- **Resin** (#231, owner 2026-08-29): Amber -> Sequencer -> **Spent Amber** + a fragment; Spent Amber + **Turpentine** (found) -> `resin_clump` (grid), and all nine resin items hang off the clump. **This is the clay chain's argument applied a second time, and it had to be.** Amber is polymerised and cross-linked, so softening it back into fresh sap is the fired-clay problem verbatim - the trade refused above. Turpentine is not a stand-in for the missing fraction, it IS that fraction: it is distilled from pine resin, which is exactly what fossilisation drove off. So the pair puts back the one thing that left rather than reversing anything, and neither half does a thing alone.
  **The husk is why the two amber chains compose instead of competing.** Every amber in both pull streams is stamped, so reading one for a spawn egg used to destroy the only material a resin clump could be made from; the Sequencer now hands the emptied body back in a byproduct slot (the Cupola's shape, for the Cupola's reason - a machine that returns two things cannot say so with one output). Vanilla's own clump recipe consumes a `resin_block` and `creaking_heart` consumes the resin it is the source of, so both look like ways in and are self-referential. `the_resin_family_has_a_non_circular_entry_point` (`ResinTests`) asserts a real entry exists rather than that a recipe exists - a "does a recipe produce each of the nine" check passes on a world with no resin in it at all.

## Breaking machines and recipe collisions

**A machine comes back however you break it** (owner, 2026-08-16, #195). No multiblock core declares `requiresCorrectToolForDrops`: the gate was opt-out, because breaking the CORE with the wrong tool destroyed it while breaking any CELL handed it back. A formed cell drops nothing of its own and the blueprint decides what disassembly returns on every path, so a cell break returns the component you put in that cell rather than whatever that shared formed block's loot table happened to name.

**Two recipes that accept the same grid are one recipe, and the loser is silent.** A crafting grid resolves to a single result, so when two recipes match the same arrangement of the same items only one can ever be crafted: no error, no log line, a JEI page saying it works, and the other thing coming out. **`trommel` and `pulverizer` were byte-identical from v0.10.0 to v0.14.0** - one of those two machines could not be made for four releases, and nobody noticed. `every_crafting_recipe_is_reachable_at_a_bench` (`RecipeReachabilityTests`) now builds each shipped recipe's grid from its bundled JSON and asks the **live recipe manager** what matches.

**It asks vanilla's matcher rather than comparing JSON, and that is not fastidiousness.** A static comparison has to reimplement the matcher to be right: shaped recipes are distinguished by their PATTERN, so the three stairs/wall pairs here are not collisions, while a shapeless recipe swallows every arrangement of its multiset and so CAN collide with a shaped one. A first pass in Python got exactly that wrong and cried wolf on all three. `getRecipesFor` returns every match rather than the first, which is the only reason the shadowed half is visible at all. It also asserts each recipe matches its OWN grid, so a wrongly built grid fails loudly instead of quietly making the check vacuous.

## The data spine: public recipe types

**The founding type is `recompile:teardown`.** `TeardownRecipe` registers it - JSON in `data/<ns>/recipe/`, with `results` (deterministic core), `extras` (weighted bonus), and `teaches` (recipes to study). It was registered from day one so the Phase 3 knowledge system is never retrofitted into a live schema. `pools` (v0.9.0) is the weighted-draw form: N draws from a weighted list, an entry with no `item` is the filler, and a pool marked `teaches` granted the fragment for whichever item it drew until #390; both `teaches` forms still parse, and the Workbench reads neither now (see [`systems_notes.md`](systems_notes.md#blueprints-and-the-bed)). **Packs and addons extend the teardown tree through this schema without a mod release - treat it as public API** (reference: `docs/teardown_schema_spec.md`).

**`RCRecipeTypes` registers nine recipe types and ten serializers** (measured 2026-09-10; derive the list from `RCRecipeTypes`, not from here). The nine types, each with a serializer of the same name:

- `recompile:teardown` - above.
- `recompile:blueprint_crafting` (#95) - a recipe that only runs while the player holds the Blueprint it names.
- `recompile:spawn_egg_crafting` (#294) - see below.
- `recompile:separating` - one feed into several distinct outputs plus byproducts.
- `recompile:pulverizing` (#189) - one input, one finer output.
- `recompile:vitrifying` (#236) - the Slag Furnace's verb. Its own type IS the obsidian gate: `minecraft:smelting` would hand it to a vanilla furnace and `minecraft:blasting` to a vanilla blast furnace.
- `recompile:sintering` (#248) - the Sintering Kiln's verb, the first that consolidates rather than reduces.
- `recompile:market_offer` (#370) - see below.
- `recompile:freight_phase` (issue #387, shipped in PR #392) - see below.

The tenth serializer is **`recompile:fragment_assembly`, which registers a serializer and no type**. Its javadoc in `RCRecipeTypes` says why: *"A SPECIAL crafting recipe, not a type of its own: it has to be findable through `RecipeType.CRAFTING`"* so it works in any 3x3 the player can reach, and its ingredients are distinguished by a data component rather than by item id.

**Pulverizing's `count` is 1 in every recipe this mod ships** (the input count; results may be more than one). Owner, 2026-08-19: *a GUI-less machine cannot take N > 1 inputs to make an output*, because with no GUI and no Container a partial batch is invisible and unrecoverable, and a partial batch is the ordinary state when the pull streams hand scrap out one at a time. The field stays because the schema is public, and both queue machines now SKIP a slot they cannot run instead of stalling on it, so a pack using it gets a slow machine rather than a bricked one.

**Teardown, separating and pulverizing are separate types rather than one flexible one** because a schema expressing all three expresses none, and separating is already extended by packs - overloading it would redefine what their existing recipes mean.

**`recompile:spawn_egg_crafting` is the only type with no `result` field**: the result is read off a Blueprint sitting IN the grid, so one recipe covers every creature. It could not be a `blueprint_crafting` recipe, because that schema names one set per recipe and a per-species family would be 29 recipes sharing one arrangement, which the bench resolves by taking the first whose sheet is in reach. It is also the only recipe where a Blueprint is an INPUT, and the table's own result slot hands it straight back - 26.1's `ResultSlot.getRemainingItems` is private and resolves `RecipeType.CRAFTING` only, and the item-level `craftRemainder` would return a BLANK sheet and destroy the species the player earned.

**`recompile:market_offer` and `recompile:freight_phase` are never matched against anything** - `matches` returns false by construction in both. A `market_offer` is one line of the Buy Terminal's stock, carrying exactly one of `blueprint` (knowledge) or `item` (the thing). A `freight_phase` is one rung of the delivery ladder - a tier, a name and a list of goods with counts. Each is a recipe type rather than a data map because a Blueprint set is an `Identifier` on an item component rather than a registry entry, so there is no registry to key a data map on - and because a recipe is the other thing a pack already extends by dropping in a file.

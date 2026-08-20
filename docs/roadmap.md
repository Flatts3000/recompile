# Recompile - implementation roadmap

**Status:** Phases 0 through 2.17 shipped to `main`, **Phase 3 shipped 2026-08-02**, **Phase 4's region system and its first frontier region shipped**, **Phase 5 (mound regrowth) shipped 2026-08-05**, and **Phase 7's themed Nether shipped 2026-08-19** (pulled forward - see that phase). **v0.12.0 is released** (2026-08-19, the compacted depths; v0.11.0 2026-08-18 the sewers, v0.10.0 2026-08-17, v0.9.0 2026-08-12, v0.8.0 2026-08-11, v0.7.0 2026-08-05, v0.6.0 2026-08-04, v0.5.0 2026-08-02, v0.4.0 2026-08-01, v0.3.0 2026-07-30, v0.1.0 and v0.2.0 2026-07-27). **Phase 6 (the full loop) is what remains.**
the latter as the CurseForge ModJam 2026 entry). The mod is a
playable alpha, tuned against real play. The **reclamation ladder is complete end to end** - Grass,
Vegetation, Farming, Trees, Animals (rungs 1-5), so the grey-to-living arc the ModJam entry is built
around now plays through. Recent tiers: the **multiblock framework + Rain Collector** (2.11),
**reclamation rungs 1-3** - the Grass Spreader, Vegetation, and Farming (2.12-2.14), **Collectibles**
(2.15, the Puzzle Cube + ported voxel curios on Display Pedestals), the **Tree Nursery** (2.16, rung 4),
and **animal baits** (2.17, rung 5). Phase 3's **materials teardown** (the
Recompile Workbench) shipped 2026-07-16; its **knowledge/function axis was decided on 2026-08-01 as
knowledge**, built as Blueprint items (see Phase 3). Its data spine (`recompile:teardown`) has been registered since Phase 0. Phases
are ordered by
**gameplay discovery** - the sequence a player actually lives, so each phase delivers a
coherent playable increment. The locked feature design is the source of truth in the
Trashlands repo (`../trashlands/docs/design_decisions.md` + `feature_matrix.md`); this
file is the engineering build order and maps to those P-codes. Everything ships
config-gated, but "defaults are the design."

> **Before beta - tune all drop numbers (gate, 2026-07-16).** Every loot weight and recipe cost
> shipped so far is a **first-pass placeholder** chosen to prove the mechanic, not balanced against
> play: the pull tables (`household_pulls` / `bag_pulls`), the Bulky Waste find table and its 5%
> mound chance, the glass-bottle weight (half of tin cans), dump-mushroom density, the Rain
> Collector fill rate, the building-block recipe costs (the material-sink balance), and the
> teardown yields + breakdown time (the mattress -> 4 string / 2 fiber / 1 scrap at 80 ticks, and
> the workbench recipe cost). Before
> launch, do **one playtest-driven balance pass across all loot tables + recipes together**, so the
> found-economy rates, sinks, and rarity curves are coherent rather than tuned piecemeal. Tuning is
> pack responsibility (Trashlands), even though the numbers live in this mod's JSON.

**Confirmed toolchain (mirrors `../productive-frogs`):** MC `26.1.2`, NeoForge `26.1.2.76`,
ModDevGradle `2.0.141`, Java 25, Gradle wrapper `9.5.1`, pack format `84`. Mod id
`recompile`, package `com.flatts.recompile`.

**Engine / pack split:** Recompile is the *engine* (systems + the public `recompile:teardown`
schema + tag-driven defaults). Trashlands is the *pack* (curation, quests, tuning, most
cross-mod teardown tables). The mod never *requires* Create or Mekanism; it ships standalone
config fallbacks.

**Organizing principle - discovery order:** build in the order the player encounters things.
The garbage world comes first (you spawn in it), then the early hand loop, then the tools and
sorting that loop demands, and only then teardown-as-knowledge - the mod's distinct axis, which
is the *payoff* of the early loop, not its entry (its on-ramp is the prybar + Bulky Waste). A
few systems (dimension lockout, the knowledge system's risky internals) are pulled earlier than
their discovery slot for a concrete reason, called out where they occur.

---

## Phase 0 - Project scaffold + data spine  *(DONE)*

Gradle NeoForge MDK; main `@Mod` class + config; the public `recompile:teardown` recipe type
with its full P0.5 schema (`input`/`station`/`results`/`extras`/`pools`/`teaches`), shipped up front so
the knowledge axis is never retrofitted; CI (`build` + `gameTest`).

## Phase 1 - The garbage world  *(DONE, design P0)*

The go/no-go slice - spawn, dig, sort, get materials, in a world that reads as a dump.
- P0.1 world preset (rolling thin crust: coarse-dirt cap, variable deepslate, bedrock, void below).
- P0.2 garbage mounds (varied height 3-15, width 4-15).
- P0.3 Block of Garbage (drops itself, shovel-mineable, randomized variants).
- P0.4 hand-sorting (empty-hand pull; crumbles after 4-6 pulls) + the 7 base materials.
- P0.5 a real teardown table proving the schema.
- Plus: the texgen texture pipeline, JEI/Jade dev tooling, GameTest harness.

**Playtest-confirmed.** Gravity on the garbage block (P0.3) is the one deferred slice item.

**JEI + Jade plugins shipped (2026-07-16).** The custom mechanics that are not vanilla recipes
now surface in-viewer: JEI **Sorting / Cutting / Prying** categories + the Scrap Crafting Table
as the crafting station; Jade tool hints + sort progress. Reads bundled pull-table JSON
(loot tables are not client-synced). **Teardown JEI stays a Phase 3 item** (the locked-recipe
overlay). See CLAUDE.md "JEI / Jade integration".

---

## Phase 2 - The early loop  *(DONE, design P1.1 / P1.2 / P1.3 / P1.8)*

What the player reaches for the moment hand-sorting palls: better tools and a faster sort.
- **Trash tools (P1.2):** scrap knife, prybar, junk shovel, rebar (universal handle). No
  pickaxe, on purpose - nothing to mine, and its absence tells the player that.
- **Garbage variants (P1.1):** bags (hand/instant), bales (scrap knife), Bulky Waste (prybar).
  One interlocking tool+block matrix. Bulky Waste is introduced here as the future teardown
  input - the on-ramp to Phase 3.
- **Sorting Tarp (P1.3):** the batch upgrade to the sort verb. Shipped *without* the GUI or
  screen slot this originally called for - the locked revision makes it stateless: right-click
  it holding garbage, sorted materials drop into the world. Hopper-proof by construction.
- **Dimension lockout (P1.8):** cheap config disabling Nether/End portals. Pulled in here
  (ahead of its "try a portal" discovery slot) to plug the vanilla-resource leak early.
  **Half of this was reversed on 2026-08-19 (owner): the Nether is OPEN.** Its resources and
  progression are the reason to go, and the themed Nether below is what you arrive in. The **End
  is still held** - travel and portal formation together, so there are no dead frames.
- Fold in garbage-block gravity (deferred from Phase 1).

**Exit:** the full pre-knowledge loop - scavenge with real tools, meet every garbage type, sort
at two speeds.

**Recovery ladder (tuned 2026-07-15, in play).** The pull table says what is *in* a block; the
method decides how much of it you get out. The ladder is **hand << tarp << automation**, and it
is load-bearing enough to have broken twice - the numbers live on `SortableBlock`'s javadoc, and
the reasoning in `../trashlands/docs/design_decisions.md` (P1.3). Two rules for anyone retuning:

- `minPulls` is a **floor**, not a knob: it guarantees a block never comes apart in one touch.
  At 1, a third of garbage blocks vanished on the first click and bare hands out-cleared tools.
- **Pulls are yield *and* time.** Cutting pulls to slow the economy silently speeds up clearing.
  Trade yield against the tarp's rolls instead. By hand you get one roll per pull cooldown, so
  the material rate (~2.65 items/s) is set by the cooldown alone, not by pull counts.

**One tool per block:** garbage digs with the junk shovel, a bale is cut with the scrap knife
(`recompile:mineable/knife` - it both opens *and* frees a bale, or the tarp's best input would
be stranded where it generated), Bulky Waste is pried out. No bare-hand action may out-clear a tool.

## Phase 2.5 - Food, the survive tier  *(DONE, design P1.9)*

Pulled in ahead of Phase 3: Minecraft ships a hunger bar, so a playable alpha needs an answer
before the knowledge system, and this is not a survival-pressure pack (no thirst, no grind).

- **Creature-free starting biome, on purpose** - a silent plain sells the dead world and makes
  the reclamation payoff land. So food is forage + scavenge, never hunting.
- **Tin cans (scavenge):** drop from the pull tables, sealed -> opened with the scrap knife,
  and eating one rolls a random effect. Suspicious Stew's risk, moved onto the dump's food.
- **Dump mushrooms (forage):** a first-party edible growing on vanilla-mycelium patches between
  mounds. Vanilla mushrooms are left untouched.
- **Deferred:** the scrap planter + muck compost (waits on Phase 3 so its knowledge gating is
  real), and roaches / infested blocks.

## Phase 2.6 - Building blocks, the shelter tier  *(DONE, design P1.11 shelter / P1.12)*

Pulled in ahead of Phase 3, like food: crude shelter is already free (the Block of Garbage is
buildable, P0.3), but stacking raw trash is not a home. This is the **deliberate** building
tier - refine scrap into blocks you would *choose* to build with - and, just as importantly, a
**material sink** for the bulk scrap the sort produces. Tier-0 and ungated (survival/shelter is
free, tech is locked); crafted at the Scrap Crafting Table. Not defense - the starting biome is
creature-free and nothing threatens builds; this is the WALL-E move of rebuilding from garbage.

- **Four full-kit families** (block + slab + stairs + wall): Pressed Junk (the junk sink),
  Scrap Plating, Corrugated Metal (the shanty aesthetic), Plastic Panel. Plus **Cullet Glass**
  as **block + pane** only - glass has no honest slab or stairs form (vanilla ships neither).
- **Hand-breakable, drop themselves** - no pickaxe exists and reclaiming your own walls must not
  be punishing; the prybar is only the faster tool on metal, never required.
- The ~110 repetitive JSON files are derived from the vanilla `cobblestone_*` / `glass_pane_*`
  templates by namespace substitution, so the stairs rotation table and double-slab loot are
  correct by construction.

## Phase 2.7 - Water, the Rain Collector  *(DONE, design P1.10)*

This world has no water at all (sea level -64, `default_fluid: air`); only rain. The **Rain
Collector** is the one source - a scrap frame + tarp (1x2x1) that fills a **real water tank**
from rain, dispenses water bottles to a glass bottle, and moves water through pipes/buckets like
any tank. The mod's first fluids, on 26.1's new transfer/`ResourceHandler` API (the second
BlockEntity, after the Scrap Barrel). **Owner override of P1.10 #5:** shipped standalone rather
than gated on a consumer; washing-salvage decoupled to a later tier. See CLAUDE.md for the
26.1 fluid-API delta.

## Phase 2.8 - Lighting  *(DONE, design P1.4-A)*

No wood (no sticks) and no coal, so vanilla torches can't be crafted - but survival-tier light is
craftable from minute one (P1.4-A). Two items solve it:
- **Oily Rag** (`fiber_scrap` + `organic_muck`) - the trash world's **"coal"**: a general furnace
  fuel at charcoal parity (`neoforge:furnace_fuels`, 1600 ticks), and the head of the torch. The
  fuel primitive the burn tier then consumes.
- **Scrap Torch** (Oily Rag over rebar -> 4) - a **1:1 vanilla-torch reskin** (light 14, floor/wall,
  permanent). Textured as a retint of the vanilla torch so it keeps the exact silhouette + model UV.

Parked (design's second rung): a refined **Grease/Biogas Lantern**.

## Phase 2.9 - Smelting, the Burn Barrel  *(DONE, design P2.2)* - pulled ahead of Phase 6

The garbage world's first **smelter**, and the start of the metal economy toward Create ("Recompile
converts, Create moves", P2.3). A **vanilla-furnace reskin** (`AbstractFurnace` on
`RecipeType.SMELTING`, the vanilla furnace screen) with a LIT active/idle state. **"Worse" = not
automatable**: it exposes no slots to any face, so it is loaded and emptied by hand - automation is
the reward for a later, better furnace. Smelts **`scrap_metal -> copper_nugget`** (the copper-first
gating; iron is the gated upgrade - see `../trashlands/docs/material_economy.md`), fueled by the
Oily Rag + junk. JEI smelting station + a fuel-value item tooltip. The excavated-and-repaired
vanilla furnace (P2.2's second rung) is still unbuilt.

## Phase 2.10 - Encroachment, the junkyard fights back  *(DONE, design P1.7-R)* - pulled ahead of Phase 5

Healed ground does not stay healed for free. Coarse earth takes back grass that **borders unhealed
ground**; interior grass and anything built are never touched. The reclamation ladder is the answer:
bare grass reverts, grass under cover loses the **cover** instead, and grass near logs or leaves is
**permanent** - so permanence is earned at the top of the chain rather than granted at the bottom,
and the first forest is what locks a border.

**This reverses P1.6/P1.7's "healed land is permanent"** and is recorded as P1.7-R. What made the
reversal safe is P2.4-R: healed land became a second economy, so retiring a mound no longer costs
net income, which was the reason permanence had to be free.

**Only the green is contested.** Encroachment reverts grass to *plain* coarse dirt, never to the
mound bed below - so mound retirement (Phase 5) stays permanent and the P1.7 endgame thesis ("you
no longer need the dump") is untouched.

Needs **no saved state**: coarse dirt is the universal surface, so every healed patch is ringed by
unhealed ground and the frontier test is a local neighbour check. Runs as a player-anchored sampling
sweep (`RCEncroachment`), not a mixin - so an unattended base cannot rot while its owner is away.

Targets the **whole dirt family, not just grass** (`#minecraft:substrate_overworld`), so a rung-1
spreader that leaves bare dirt at the frontier gets no free pass. Two carve-outs: **coarse dirt**
(the revert target) and **mycelium** (the dump-mushroom substrate - the P1.9 forage economy).
**Wet farmland holds, dry farmland is taken** - irrigation defends a plot, so P1.10 water becomes a
reclamation defence and not only an input. Five block tags plus a biome tag are the whole tuning
surface, each built from other tags so modded dirt variants are covered without a mod release; the
mod is inert outside the garbage biomes. Deliberately **untuned** - the rates join the pre-beta
balance pass.

## Phase 2.11 - The multiblock framework + the Rain Collector rebuilt  *(DONE)*

The shared machine framework the reclamation chain (P2.4-R) and the Overworld Gate all need, built
with the **Rain Collector** as its first consumer. Spec: [`multiblock_system_spec.md`](multiblock_system_spec.md).

**Rain Collector = core + Machine Frame.** The core holds the tank; the frame becomes the tarp
funnel that catches the rain. Placing the core always succeeds as an inert **unformed** block - if
you are carrying a frame it is placed and consumed in the same action, otherwise stack one by hand.
**Collection is gated on being formed**, so the unformed state means something and the funnel is
visibly the part that does the work.

Three types carry it: `Multiblock` (the blueprint - one source of truth for validation, the
auto-assemble step and the tests), `MultiblockCoreBlock` (owns `FORMED`, revalidates on neighbour
change, disbands on break), and `MultiblockDummyBlock` (IE's dummy - redirects use and break to the
master so a formed machine is *one object*). **No BlockEntity for the structure**: `FORMED` is
blockstate and cells are read from the world, so nothing serialises and nothing desyncs.

**The formed look is bespoke** - the funnel is a per-machine dummy, not the shared frame restacked
(vanilla hopper geometry, `hopper_inside` recoloured to tarp blue). The **Machine Frame** is the
shared, craftable component every later machine reuses.

P1.10 behaviour is intact (tank, fluid capability, bottle draw, water surviving break+replace). Art
is deterministic recolours of vanilla + existing textures - **no hand-drawn art, and no wood**,
since this world has no trees.

**Migration:** the old `DoubleBlockHalf` rain collector is retired, so **rain collectors placed in
existing saved worlds will not resolve and will vanish.** Accepted pre-beta.

Deferred here: rungs 2-4, which reuse this framework. Rung 1 shipped as Phase 2.12.

## Phase 2.12 - The Grass Spreader, reclamation rung 1  *(DONE 2026-07-24, design P2.4-R3)* (#21)

The first machine that heals ground, and the first consumer of the framework that is not the machine
it was built with. Spec: [`grass_spreader_spec.md`](grass_spreader_spec.md).

**A drip irrigator, not a sprinkler.** A four-cell tower - core, Water Tank, Pump, Solar Panel - with
four Copper Pipes ringing the pump as drip spigots. It converts the **nearest** eligible ground to
grass, one block at a time, forever, **consuming nothing**. Nearest-first is the load-bearing rule:
green reads as growing outward, and ground the frontier just took back is closer than untouched
ground, so it is repaired before new ground is broken. No repair pass, no stored progress.

**Straight to grass, never via plain dirt.** Vanilla grass cannot spread onto coarse dirt but *can*
spread onto dirt, so leaving an intermediate would hand the player free healing and break "nothing
renews on its own."

**No BlockEntity.** Radius and rate are config; the search recomputes from the world. It runs on a
self-rescheduling block tick, which survives save/load with nothing serialised.

**Radius 12**, which is the real statement: at that reach it repairs roughly 6x faster than
encroachment erodes, so the constraint is **area, not attrition** - a spreader holds exactly as much
land as it can reach, and no more.

**The Pump is teardown-only**, out of a **Washing Machine** found in Bulky Waste, so rung 1 sits
behind the teardown spine and a find. The washing machine is placeable and carries its own four-face
art. Supply analysis: [`pump_sourcing.md`](pump_sourcing.md).

**Design note (settled):** the Water Tank is craftable from raw materials, so a spreader does not
require a Rain Collector anywhere in its chain. The two are deliberately siblings built from the
same tank, not an ordered chain - P2.4-R3 item 8's "no collector, no spreader" is retired, not a gap.
Rung 1's gate is the Pump (teardown-only, behind a find) and the multiblock build.

## Phase 2.13 - Vegetation, reclamation rung 2  *(DONE 2026-07-26, design P2.4-R2)* (#25, #26)

Fertilizer, composted in the **Compost Heap** (a 2x2x2 salvage-cage multiblock; feed it anything in
`#recompile:compostable`, harvest one Fertilizer per ripe layer), is a **surface-aware "fancy
bonemeal"**: right-click reclaimed grass to scatter weeds and wildflowers, mycelium to scatter
mushrooms, over a short outward ripple. Custom dump plants (weedgrass, fireweed) ride along with vanilla
weeds and grasses; flowers are a rare accent (~1-2 per Fertilizer). The scattered plants are
`frontier_cover`, so encroachment strips them before it takes the grass - green visibly goes bare before
it goes brown. No BlockEntity, no saved state (the ripple is a static queue drained on the server tick).
Spec: [`vegetation_tier_spec.md`](vegetation_tier_spec.md).

## Phase 2.14 - Farming, reclamation rung 3  *(DONE 2026-07-26, design P2.4-R3)* (#27, #28)

Farmland is gated behind compost, not a hoe: **`Fertilizer + dirt`** (or coarse dirt + 4) crafts vanilla
`minecraft:farmland`, and hoe-tilling is disabled, so the compost recipe is the only path. Seeds come
from **compost volunteers** - the Heap drops an **Unknown Seedling** (~1 in 8 layers) that resolves to a
random one of the six farmland crops at plant time. Irrigation is the **Grass Spreader** (it pins
farmland moisture across its radius) plus rain; an unwatered plot dries and the junkyard takes it, so the
water economy is a reclamation defence. Foraged **Dump Mushrooms replant** (a placeable BlockItem), so
forage is renewable. Spec: [`farming_tier_spec.md`](farming_tier_spec.md).

**Deferred with a home:** whole-plant farmables (sugar cane, bamboo, cactus, sweet berries) are held for
a later **hydroponics** option rather than an in-ground source now - so this tier ships the six
seed-crops only. The P1.9 scrap planter stays parked as an alternate grower.

## Phase 2.15 - Collectibles  *(DONE 2026-07-26, design I-2)*

Curios the player finds in the garbage and displays - the WALL-E hoard. Two acquisition shapes: the
**Puzzle Cube** is *assembled* (nine found `puzzle_cube_piece` -> a solved/scrambled cube block, ~1/1000
per pull for a piece, all nine in ~20h), and **ported voxel collectibles** are *found whole*. The latter
come from the **voxel-porter** (`../mc-pack-toolkit/voxel-porter`): it voxelizes an open-source CC0 model
(mesh or `.vox`) to Minecraft's 16px grid, samples per-voxel colour, greedy-meshes it, and emits the block
model + palette texture + data files. v1 ports four CC0 objects - **avocado, present, gold_coin, toy_car** -
each a rare whole find (~1/4000 per pull in `household_pulls`/`bag_pulls`, a few times rarer than a cube
piece) that displays on the **Display Pedestal** (a ProjectE-style plinth, the mod's one BlockEntityRenderer).
An earlier hand-authored **era-artifact** set (obelisk/column/chalice/hourglass) read as museum decor and was
dropped for ported real objects. Spec: [`collectibles_spec.md`](collectibles_spec.md).

## Phase 2.16 - The Tree Nursery, reclamation rung 4  *(DONE 2026-07-27, design P2.4)* (#38, #40)

Trees are machine-only: a global loot modifier (`StripSaplingsModifier`, P2.4-R2) strips saplings from
every roll, so the player never holds one loose - the **Tree Nursery** is the sole forest source. It is a
**2x2x1 wall multiblock** (a core + a water tank on the bottom row, two solar panels on top) that raises a
**vanilla sapling of the player's choice** from **water + Fertilizer + an Unknown Seedling** over a slow
cook, and **glows while it works** (a furnace-shaped producer). Eight species (oak, birch, spruce, jungle,
acacia, dark oak, cherry, mangrove) picked in its **bespoke screen** - the mod's second custom menu, a
scoped reversal like the Scrap Crafting Table's, because a species picker + water gauge + progress arrow is
not a vanilla screen. Water is the one automatable input (a fluid capability, pipe/pump-fed); Fertilizer
and Seedlings go in by hand, saplings come out by hand. A **copper bucket** (copper, not iron - iron is
scarce here) moves water into it. Spec: [`tree_nursery_spec.md`](tree_nursery_spec.md).

## Phase 2.17 - Animals, reclamation rung 5  *(DONE 2026-07-27, design P2.4)* (#41, #42)

The start biome is creature-free by design (P1.9), so animals return only through **bait**, never ambient
spawns. Three diets - **herbivore, carnivore, omnivore** - each with a **Rich** grade, each placed on grass:
when **no player is near** and it is **not crowded by another bait**, it settles over a countdown and spawns
**one allowlisted mob** from its diet tag (`#recompile:bait/<diet>`), then is spent. **Rich bait seeds a
breeding pair** (one an adult, one a baby). Per-mob **spawn weights** make common livestock turn up far more
than rare picks; **per-diet placed textures** read the bait apart on the ground. A cluster resolves one at a
time (a bait yields only to an earlier-sorted neighbour, so no mutual deadlock). **Apple-gated recipes** put
the tier behind trees: herbivore = apple + wheat, carnivore = apples + any `#recompile:raw_meat`, omnivore =
one of each, Rich = two of the basic. **JEI and Jade list every reason a bait is held** (disabled, off
grass, player near, crowded). Spec: [`animals_tier_spec.md`](animals_tier_spec.md).

## Phase 3 - Teardown  *(design P1.4) - the distinct axis*

Tear a found item down at the **Recompile Workbench** into materials. This is the teardown exit
the found economy needs - the P1.11.5 invariant ("finds in, materials out"), which was blocked on
the bench existing.

**The materials workbench is SHIPPED (2026-07-16).** GUI-free, keeping the mod's no-machine-screen
identity: two tools (scrap knife, prybar) rest on the table (a baked multipart model, no BER),
hold right-click with a found item to run a per-recipe timed breakdown, outputs pop into the world,
the racked tool wears. The `recompile:teardown` schema gained optional `tool` + `ticks` fields.
The mattress migrated onto it (its in-hand knife-cut retired), so string is now bench-gated. JEI
Teardown category + a Jade diagnostic ship with it.

**The axis is decided: KNOWLEDGE** (owner, 2026-08-01) and **the system shipped 2026-08-02** (#95,
PRs #108 and #110). Spec: `docs/blueprints_spec.md`. The `teaches` field, parsed and ignored since
Phase 0, is finally read - which immediately turned the schema's own example recipe into live
content, because it pointed at a blueprint that did not exist.

The old objections to gating are answered by **not gating vanilla crafting at all.** Knowledge is an
**Immersive-Engineering-style Blueprint item** and blueprint-only recipes live on their own bench, so
there is no `doLimitedCrafting` flag to leak through Create autocrafting, nothing player-scoped to
sync, and no catalog-wide lockout to justify. The item *is* the knowledge, which also means no saved
state - the same grain as encroachment, the scrap network, and `FORMED`.

Function (recover working components) was not chosen and is not blocked; it could layer onto the same
bench later.

**Built:** all of it. Tearing something down at the Workbench grants an **Idea Fragment**; enough
fragments about one thing craft into a **Blueprint**; a **Filing Cabinet** found in Bulky Waste files
them and joins the Scrap Network by placement; and the Scrap Crafting Table runs a
`recompile:blueprint_crafting` recipe only while the sheet is in the player's inventory or in a
cabinet in the same cluster. The proof of concept is the bed, and it is now the only bed in the game:
all sixteen wool-to-bed recipes are deleted, and a Clean Mattress (blueprint-only) plus three planks
is the sole route. The **Hydroponics Bay** followed as the second gated item, taught by a Broken
Hydroponics Bay find.

The JEI locked-recipe overlay concern is moot under this design: blueprint recipes are their own JEI
category, and a vanilla crafting table needs no code to be excluded, since they are not of type
`minecraft:crafting` and it cannot see them at all. The blueprint shows as a **crafting station**
rather than a catalyst - JEI's roles here are INPUT / OUTPUT / CRAFTING_STATION / RENDER_ONLY, and
declaring it an input made the transfer button demand it as a tenth ingredient.

## Phase 4 - Garbage regions  *(design P1.5)*

"I've stripped this area" - venture out. Real biomes distance-banded from spawn; launch trio
household / scrapyard / e-waste; per-region garbage blocks (the drop table travels with the
block). One region = one datapack bundle.

## Phase 5 - Mound regrowth  *(design P1.6 / P1.7-R)*

"Wait, the mounds grew back." Deorbit falling-block delivery (reuses P0.3 gravity). The
quarry-vs-heal tension is the pack's engine.

**Healed-land immunity already shipped, inverted, as Phase 2.10** - the surface is contested, not
immune. What remains here is the mound layer, whose retirement *is* permanent.

**Original-bounds memory is settled as blockstate, not `SavedData`:** `MoundFeature` writes a
`recompile:mound_bed` under each footprint cell carrying that cell's original column height in an
`IntegerProperty` (0-15, matching `MAX_HEIGHT`). Exact footprint and profile, no save file, no
worldgen-thread concurrency - the same palette-flyweight idiom as `SortableBlock`'s `sorted`. Make
it **visually distinct** (dark, saturated earth) so the player learns to read the ground: dark means
this one comes back, which puts the quarry-vs-heal decision underfoot. Rung 1 converting mound bed
to grass is what retires it forever.

**Status: SHIPPED.** `MoundGroundBlock` carries the memory and the regrowth; `MoundFeature` writes
it; rate, on/off and drop height are config. Pre-existing saved worlds have no mound ground, so their
mounds stay finite - accepted (owner, 2026-08-05).

Three things in the plan above did not survive building it:

- **The texture was never a blocker.** "Dark saturated earth" is a retint of vanilla coarse dirt, not
  a drawing: same grain, calibrated to mean luma 66 against coarse dirt's 90.4, so it reads as the
  same material and unmistakably darker ground.
- **It is not a `mound_bed`, it is `mound_ground`, named Mound Ground** (owner). "Bed" collides with
  the vanilla noun in a mod that ships a real bed, and the block is coarse dirt with a different name
  and face - so it takes coarse dirt's hardness, sound, shovel and lack of a tool gate, and stays out
  of `#minecraft:dirt` so `#encroachable` cannot reach it.
- **The property stores a COUNT, not the top offset the plan described.** The feature fills
  `dy = 0..column` inclusive, so a rim cell of column 0 still carries a block; storing the offset
  builds every mound one short, and leaves 0 meaning both "one-block rim" and "nothing here". As a
  count, 0 can only mean the latter, which is what makes a hand-placed block inert instead of the
  seed of a mound that never existed.
- **Delivery drops from 30 blocks, not the top of the world.** The build limit is 320 over a surface
  near -60; a 380-block fall is nine seconds of live entity per block of garbage for a beat that
  reads identically from thirty. The flight path is checked clear first, so a roof stops regrowth
  rather than collecting it.

---

## Phase 6 - The full loop  *(design P2)*

Discovered as you climb tiers; leans on curation + sibling mods.
- Tier-2 processing (P2.2): the **Burn Barrel shipped early as Phase 2.9**; the
  excavated-and-repaired furnace (its second rung) is what remains here. Purity-as-yield, no energy.
- Reclamation chain: grass -> crops -> trees, one machine per rung (soil spreader -> vegetation
  seeder -> nursery -> animals); nothing renews on its own; the payout is the returning overworld, so
  healed land is a second economy (P2.4 as revised by **P2.4-R**). **Rung 1 (the soil spreader) and
  the shared multiblock framework are specced** in [`grass_spreader_spec.md`](grass_spreader_spec.md)
  and [`multiblock_system_spec.md`](multiblock_system_spec.md) - the machines are IE-style
  multiblocks and the spreader **consumes nothing** (its cost is the one-time build; P1.7-R's erosion
  is the ongoing pressure). Design background + rungs 2-4: [`reclamation_handoff.md`](reclamation_handoff.md).
  **The nursery / tree planter is the only source of trees in the game** (P2.4-R2, shipped): saplings
  are stripped from all loot, so they exist only already-planted and only where the planter puts them.
  A "tree farm" is therefore a running planter, not hand-replanting; and **until the planter ships,
  P1.7-R's rung-3 anchor is unreachable**, so the encroachment frontier currently has no permanent
  stop. Correct for now (the world has no trees), but it makes the planter the load-bearing rung
  rather than the last one.
- E-waste recovery chains, two-stage purity-as-yield + battery mini-tree (P2.6).
- Tier-3 logistics seam: "Recompile converts, Create moves"; never *require* Create (P2.3).
- Hazmat gating via Mekanism radiation + suit; Recompile ships biome/blocks/caches only (P2.5).
- Cross-mod teardown tables at scale: tag-driven defaults + landmark hand-authoring + a
  completeness-check build step (P2.8) - **pack content**.
- Quest line in FTB Quests, exile fiction, spoiler-safe hidden post-twist chapters (P2.7) -
  **pack content**; the opening spectacle + scripted deorbit (P2.1).

## Phase 7 - Themed dimensions + polish  *(design P3)*

- ~~Themed Nether (Hard) - solid techno-organic waste; first RF power + osmium originate here.~~
  **DONE 2026-08-19, pulled forward** (v0.12.0). The compacted depths: solid techno-organic waste
  floor to ceiling with slag rubble and lava pockets, vanilla fortresses and bastions, terrain
  rebuilt four shards at a time, and three scrap categories feeding the machines you already own.
  **Two halves of the original line did not come true and are not going to.** RF power arrived far
  earlier, in the power tier (#72), so the Nether was not its origin; and **osmium is Mekanism's**,
  which this mod does not require, so nothing osmium-shaped shipped with it. What the depths
  actually originate is coal, quartz, glowstone, nether wart, blaze powder and magma cream, each out
  of a machine rather than out of the ground.
- Themed End (Medium-Hard) - the found-economy capstone.
- Field Manual (whichever guide-book mod is on 26.x; not a lore vehicle).

**Explicitly parked, out of scope until reopened:** the endgame redesign (P3.9) and the final
chapter/postgame. Do not build against these yet.

---

## Notes carried into implementation

- Discovery order governs sequencing; where technical risk argues for building something earlier
  than its discovery slot (the Phase 3 knowledge spike, the Phase 2 dimension lockout), it's
  called out at that phase rather than reordering the player's journey.
- Minimize authored prose (Jason's hard rule): only two sanctioned writing surfaces - quests
  (via the quest-voice skill) and terse technical guidance. No ambient lore. ASCII punctuation
  only, no em/en-dashes, no emoji.

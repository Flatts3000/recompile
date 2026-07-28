# Demolition Yard + the Region System - spec

**Status: design locked (2026-07-27), not built.** The demolition yard is the first **frontier region**: the
dangerous, gated place you travel to for the two structural materials the closed economy withholds -
**stone and iron**. It also introduces the **region system** (a distance-gated, noise-filled `BiomeSource`),
which is the reusable Phase 4 foundation every future region plugs into.

Design source of truth stays in the pack repo (`../trashlands/docs/design_decisions.md`, P1.5 regions,
P2.4 material economy). This is the engineering spec. Everything ships config-gated; defaults are the design.

---

## 0. The concept in one paragraph

You start in a **large, safe, resource-complete home** (household_sprawl, the current world) and can play the
entire base game there - salvage, food, water, the whole reclamation ladder (grass -> vegetation -> farming
-> trees -> animals) - without ever leaving. The **frontier** beyond the home is a deliberate trade: you give
up "safe and friendly" for **structural materials** (stone, iron, later the rest of #46). The frontier is
**gated by the reclamation ladder itself** - its iron needs a sledgehammer, the sledgehammer needs sticks,
sticks need trees - so you finish the friendly-core game *before* the frontier is exploitable. Three economies
stack: **garbage** (salvage), **healed land** (biological), **frontier** (structural).

---

## 1. The region system (reusable - the Phase 4 foundation)

### 1.1 Model: distance gradient + noise

A custom `BiomeSource` (`RegionBiomeSource`) places overworld biomes by a **continuous distance gradient** from
world origin - not discrete rings:

- **Core, `d < coreRadius` (512): P(household) = 100%.** Guaranteed pure household_sprawl - the safe,
  resource-complete home. `coreRadius = 512` puts the whole hostile spawn range (~128 blocks, plus margin for
  simulation distance and a roomy homeland) inside the empty-spawner biome, so **standing at spawn you are 100%
  protected from natural hostile spawns**, with a civilization-sized safe area.
- **Beyond, `d >= coreRadius`: P(household) decays with distance** from ~100% at the core edge toward a small
  non-zero floor - so safe household patches *thin* outward but never fully vanish (remote homes stay
  possible). Frontier biomes take the complementary share, each **ramping in from its own onset distance**
  (demolition yard earliest; exotic regions further out), so biome **variety grows with distance** too.
- A **low-frequency noise** picks the local patch, weighted by those distance-dependent probabilities. Result:
  household forms large regions near the core and rare islands far out; the demolition yard does the opposite;
  and the core edge **blends** - demolition starts as rare patches in a household sea, not an abrupt wall at
  512.

This is the natural danger/rarity curve - safe ground rarer the further you push - with no hard boundary
anywhere. Distance sets *the odds*; noise sets *the patch*.

### 1.2 Why custom Java (not data-only)

Vanilla has **no distance-from-origin density function**, so a plain `multi_noise` source cannot guarantee
household fills the spawn area - it would just be another blob that could land anywhere, breaking the 100%-safe
requirement. `RegionBiomeSource` is a registered `BiomeSource` subtype (**not a mixin**); it computes `d`,
selects the ring pool, and samples a low-frequency noise to pick a coherent biome blob from that pool.

### 1.3 Data-driven edges

The source reads a small config/JSON: `coreRadius` (512), a `falloff` distance (how fast P(household) decays
past the core), a `householdFloor` (its minimum far-out share), the noise frequency (blob size), and a
`frontier: [{biome, onset}]` list (each region's onset distance). A new region (or a pack) adds **one frontier
entry**, no Java. Spawn is pinned to origin so the player always starts in the core.

### 1.4 Multiplayer

Shared world, one safe core, uniform noisy frontier -> **no per-player-ring problem** (the earlier concern is
dissolved by this model). Household patches scatter through the frontier, so a remote base can find safe
ground. The optional **Sanctuary Ward** (a placeable spawn-suppression block for hardening a base anywhere) is
**deferred** - not needed by this model; revisit if remote homesteading wants a guarantee.

### 1.5 Build/verify

- Switch the overworld `biome_source` in `world_preset/garbage.json` from `fixed` to the new type.
- GameTest / headless check: sample biome at many points within 512 of origin, assert all `household_sprawl`;
  sample beyond, assert the pool appears. `runGameTestServer` hard-fails on registry errors, so it validates
  the wiring fast. A `runClient` pass confirms spawn lands in the core and the frontier reads right.

---

## 2. The demolition yard biome

- **Surface: unchanged.** Shares the coarse-dirt surface rule with household (the reclamation ladder rides on
  coarse dirt; re-surfacing would silently break the Grass Spreader/encroachment). Identity comes from the
  **rubble on top**, not the ground.
- **Hostile spawns: on.** Populated `monster` spawner list (zombie, skeleton, spider, creeper); creature and
  other lists stay empty. This is the mod's first hostile-spawning biome (scoped intro of the #46 threat axis).
- **Features:** the Building Husk feature (S3) + surface rubble scatter. Not the garbage mound.
- **Not encroaching** - stays out of the `#recompile:encroaches` tag (it is a resource region, not contested
  green).

---

## 3. Worldgen features: husks + piles

The demolition yard scatters **three** feature types - a few standing husks in a field of debris:

**(a) Building Husks** (rare landmarks). A **procedural** `BuildingHuskFeature` (a `Feature<NoneFeatureConfiguration>` in `RCFeatures`, same shape as
`MoundFeature`) - **no NBT structure templates** (keeps the mod's no-hand-authored-structures record; and
ruins are the one structure procedural gen nails, because raggedness *is* the aesthetic). Grounded in real
demolition morphology - the **concrete/steel skeleton frame** (reference: Rio's "Skeleton Hotel", Hotel
Esqueleto): a husk is a **skeleton, not a pile** - you see straight through it.

- **A steel-frame skeleton wearing broken concrete floors.** Vertical **Steel I-Beam** columns on a grid
  (spacing ~3-4), horizontal I-beam girders across their tops per story, and **partial Reinforced Concrete
  floor slabs** laid on the frame (some cells intact, some sheared off, some collapsed). The repeating grid IS
  the "it's a building" read - not a rounded blob.
- **Taller than wide, mostly open air.** Height ~6-16, footprint ~3x3-7x7. Walls almost entirely gone (rare
  standing wall/facade fragments; the stair/elevator **core** is the most-intact vertical element). Seed-varied.
- **Ragged, uneven top** - floor-by-floor demolition leaves an irregular height profile; the top story is
  always half-present, columns and beams ending at different heights.
- **Exposed rebar at every concrete break** - protruding bars at snapped column tops and slab edges.
- **Rubble spilled** at the base and across the partial floors.
- **Role split, made physical:** Steel I-Beams = the **frame** (bulk iron); Reinforced Concrete = **columns +
  floor decks** (concrete + rebar iron); Rubble = the **basal debris** (stone shards). The shape makes all
  three materials legible on sight.
- **Dark open interior = a daytime spawn pocket** for the biome's hostiles (the danger is "don't go inside").
- **Density: sparse landmarks** you travel between, not every-chunk clutter (sparser than garbage mounds).
- **Tuning:** the skeleton needs a `runClient` pass to read as a deliberate gutted building rather than noise -
  the one part that cannot be fully nailed on paper. One parameterized generator for v1 (archetypes later).

**(b) Rubble piles** and **(c) Steel piles** (common). Low mounds - of the **Rubble** block, and of **Steel
I-Beam / twisted-steel** debris respectively - reusing the `MoundFeature` shape (like garbage mounds). These
are the **workaday resource source** (stone shards from rubble, bulk iron from steel) and they carry the
biome's read: a debris field with hostiles already looks like a demolition site. Crucially they are the **easy
part** - a pile is just a mound of a block and looks right immediately.

**The difficulty is confined to the husk, and it is not load-bearing.** Build the piles first: the biome ships
correct, functional (both stone and iron flow from the piles), and demolition-flavoured without the husk. The
husk is an enhancement layered on - if procedural husks fight us, the biome still works. Husk de-risk order:
mock the silhouette on paper, add a debug `/place` command for second-by-second iteration in a flat world,
lean on the three cheap cues (grid + rebar + ragged top), and fall back to hand-tuned archetypes or a small
NBT template set if pure-procedural will not behave (ruins are forgiving; the no-template rule was an
inference, not a hard constraint).

---

## 4. Blocks

### 4.1 Rubble (the stone path)
- A **`SortableBlock` variant** - works like garbage: **bare-hand pick-through / sift**, no tool.
- Loot: **stone shards, one item per vanilla overworld stone type**, from a weighted pull stream *just like
  scrap* (`loot_table/gameplay/*` style): `stone, granite, diorite, andesite, deepslate, tuff, calcite`.
- Bare-hand, so **stone is the ungated entry reward** for braving the region.

### 4.2 Reinforced Concrete (the iron + masonry path)
- Solid full cube, `requiresCorrectToolForDrops`, mineable **only with the Sledgehammer** (min copper tier);
  carries `#recompile:mineable/sledgehammer`.
- Loot: **always** exactly one of `{concrete_powder(grayscale shade) | gravel | sand}`; **sometimes** also
  drops **rebar**.
- Solid + tool-gated, so **iron is the deeper, gated prize** (the sledgehammer is tree-gated, S6).

### 4.3 Steel I-Beam (the bulk-iron path)
- The husk's structural steel - a directional block (vertical column / horizontal girder), and the bulk of the
  **Steel piles**. `requiresCorrectToolForDrops`, harvested **only with the Cutting Torch** - *not* the
  sledgehammer (you crush concrete, you **cut** steel). Carries `#recompile:mineable/cutting_torch`.
- Loot: **steel / raw iron in bulk** - the **higher-volume iron source** vs the incidental rebar from concrete.

---

## 5. Items / materials

- **Stone shards** (7 items, one per stone type) - a base-material vocabulary parallel to the 7 scrap
  materials. **Assemble by crafting** into their vanilla stone block (e.g. shapeless/shaped N shards -> 1
  block; count tuned in the pre-beta pass, #36). This is the **stone** payoff.
- **Rebar** (common, from crushing concrete) and **steel** (bulk, from cutting Steel I-Beams) - the two
  **iron** feedstocks. Both -> Makeshift Forge -> iron ingot. Rebar bootstraps your first iron; the Cutting
  Torch + I-beams scale it up.
- **Concrete powder (grayscale ramp: white / light gray / gray / black)** -> concrete via vanilla water. The
  world's masonry, and the crafting material for the Makeshift Forge. (Full 16 colors rejected - pink concrete
  breaks the demolition read.)
- Gravel and sand are incidental vanilla drops (flavor, and feed vanilla concrete-powder crafting).

---

## 6. Tools: Sledgehammer (crush) + Cutting Torch (cut)

Two demolition verbs, two tools - you crush concrete but you cut steel.

### Sledgehammer (crush -> concrete + rebar)

- **Tiered ladder: copper -> iron -> diamond -> netherite**, upgraded the vanilla way. Climbs as far as
  materials exist: **copper (you have it) and iron (this biome) are reachable now**; **diamond and netherite
  light up later** with the crystals gap (#46) and the Nether unlock - a full ladder, top rungs gated by
  future content (forward-compatible with the dimension lockout).
- **Handle takes sticks -> gated behind trees** (reclamation rung 4). This is the frontier's gate: you must
  climb to trees before you can break Reinforced Concrete for iron.
- Mines the `#recompile:mineable/sledgehammer` tag (Reinforced Concrete) via the `tool()`-over-a-tag pattern
  the knife/prybar already use. Copper needs a custom `ToolMaterial`; iron/diamond/netherite use vanilla ones.
- It gives the world back its one **quarry-like verb** - but you dismantle *ruins*, not natural stone, so it
  stays on-theme rather than importing vanilla mining.

### Cutting Torch (cut -> steel / bulk iron)

- Harvests **Steel I-Beams** (the `#recompile:mineable/cutting_torch` tag) - a sledgehammer cannot; steel is
  cut, not crushed. Single tool, not a tier ladder (an upgrade path is a later option).
- **Consumes fuel as it cuts** - burns **Oily Rag** (the P1.4-A fuel), turning that line into a real sink; a
  gas-canister item is an alternative. (Plain durability is the simpler fallback - a balance-pass call, #36.)
- **Recipe takes a little iron**, so it sits one step past first-iron: the sledgehammer + rebar bootstraps your
  starting iron, then the torch unlocks bulk steel. Crafted from copper + iron + the fuel head.

---

## 7. The Makeshift Forge (processor)

- A **retextured vanilla blast furnace**: `AbstractFurnaceBlock`/`AbstractFurnaceBlockEntity` subtype on
  `RecipeType.BLASTING`, reusing **`BlastFurnaceMenu`** (no bespoke screen - same path as the Burn Barrel).
  FACING + LIT + open-on-use come from the abstract block.
- **Built from concrete** (concrete powder -> concrete -> forge).
- **Hopper-automatable** - do **not** override `getSlotsForFace` (the Burn Barrel's manual-only trick). This is
  the mod's **first automatable machine**: a deliberate departure from the automation-averse stance (manual
  Burn Barrel, stateless Sorting Tarp). **Recorded design decision** - check/append
  `../trashlands/docs/design_decisions.md`. Tiering rationale: Burn Barrel = manual early smelter, Makeshift
  Forge = the automatable blast-furnace upgrade unlocked via the frontier (furnace -> blast furnace, cleanly).
- Blasting recipes: **rebar -> iron ingot**, **scrap -> copper ingot**, and other metal reductions. Ingots
  out directly (no melt/cast step), furnace-style.

---

## 8. Progression / gating summary

```
reclamation ladder (grass -> veg -> farm -> TREES) ->  sticks
sticks + copper            -> Copper Sledgehammer
travel to demolition yard (survive hostiles)
  sift Rubble (bare hand)     -> stone shards -> assemble -> STONE       [ungated once you survive the trip]
  sledge Reinforced Concrete  -> rebar        -> Forge     -> first IRON  [gated behind trees, via the hammer]
  first iron + copper + fuel  -> Cutting Torch
  cut Steel I-Beams           -> steel        -> Forge     -> bulk IRON   [gated behind the torch]
iron -> Iron Sledgehammer (faster) ; diamond/netherite rungs await #46 / the Nether
```

Stone is the entry reward; rebar-iron bootstraps; steel-iron via the torch is the bulk, deepest tier.

---

## 9. Textures (texgen surfaces)

`rubble`, `reinforced_concrete`, `steel_i_beam` (column + girder), the 7 `*_shard` items, `rebar`, the
sledgehammer heads (per tier), the `cutting_torch`, `makeshift_forge` (lit + unlit fronts, like the Burn
Barrel). Concrete powder/concrete are vanilla. All procedural/AI per the texgen pipeline; only finalized 16px
PNGs committed.

---

## 10. Registry / config / tests / compat

- **Registry:** `RCBlocks` (rubble, reinforced_concrete, steel_i_beam, makeshift_forge + BE), `RCItems`
  (shards x7, rebar, steel, the 4 sledgehammer tiers, the cutting_torch, block-items), `RCBlockEntities`
  (forge), `RCFeatures` (BuildingHuskFeature + rubble/steel pile features), `RCTags`
  (`mineable/sledgehammer`, `mineable/cutting_torch`), `RCCreativeTabs` (slot the new items into the existing
  categories),
  `RCDataComponents`/`RCMenus` as needed. New `RegionBiomeSource` registered as a `BiomeSource` codec.
- **Config (`RCConfig`):** `DEMOLITION_YARD_ENABLED`, `REGION_CORE_RADIUS` (512), region ring table + noise
  scale, husk density, rubble pull weights, reinforced-concrete drop rates, rebar chance, forge speed.
- **GameTests:** biome-safety sweep (all household within 512), rubble sift -> shards, reinforced-concrete
  sledge drops + tool gate, shard -> stone assembly, forge rebar -> iron / scrap -> copper, husk feature places
  without error. Register in `RCGameTests`.
- **JEI/Jade:** a JEI category for rubble sifting + reinforced-concrete sledging (like the existing Sorting/
  Prying categories); the Makeshift Forge as a blasting station; Jade tool-hint on Reinforced Concrete
  ("Break with a Sledgehammer"). Guidebook chapter for the frontier.

---

## 11. Build order + risks

1. **Region system first** (`RegionBiomeSource` + gradient config + world_preset switch) - it is the foundation and
   the biggest unknown; validate the 512 safe-core guarantee with a headless biome sweep before anything else.
2. **The demolition_yard biome** (spawners, coarse-dirt shared surface) - trivial once the source exists.
3. **Blocks + items + loot** (Rubble/shards, Reinforced Concrete/drops, rebar) - reuses `SortableBlock`.
4. **Sledgehammer** (tiers, tag, tree gate).
5. **Makeshift Forge** (blast-furnace variant, blasting recipes, automatable) - record the design reversal.
6. **Building Husk feature** - build last, `runClient`-tune the collapse look (the soft risk).
7. texgen art pass; GameTests throughout; JEI/Jade + guidebook; balance numbers join #36.

**Risks:** (a) `RegionBiomeSource` correctness + the safe-core guarantee (mitigated by the sweep test);
(b) the husk reading as deliberate ruins vs noise (mitigated by the runClient loop); (c) the automatable-forge
reversal (deliberate, recorded). Existing saved worlds keep the old `fixed` source - the region system applies
to new worlds only; acceptable pre-beta.

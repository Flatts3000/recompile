# Multiblock system - implementation spec

**Written 2026-07-23.** The shared framework the reclamation machines (P2.4-R) and later the
Overworld Gate are built on.

> **Status: the framework and its first machine are SHIPPED (2026-07-23).** `Multiblock`,
> `MultiblockCoreBlock` and `MultiblockDummyBlock` exist, and the **Rain Collector** is rebuilt on
> them as core + Machine Frame -> tarp funnel. What this doc still describes as future is the
> **grass spreader** (deferred) and rungs 2-4. Divergences from the spec as built, all deliberate:
> the shared component is registered as a plain `Block` (it needs no behaviour), and a machine's
> formed cell is a **per-machine dummy block** (`rain_collector_funnel`) rather than a variant of
> the shared frame - which is the "formed look is bespoke" rule made concrete.

Design source of truth is the pack repo: `../trashlands/docs/design_decisions.md`. This system is
**not yet recorded there** - see "Design record still owed".

> **Pattern lineage:** the assembly/formation model is **Immersive Engineering's** (verified against
> its 1.21.1 jar - see Rendering): build heterogeneous component blocks, they become a master + dummy
> structure on formation. We take IE's **master/dummy semantics** but drop its two heavy pieces (NBT
> `StructureTemplate` shapes and the master block-entity renderer), because our machines are tiny
> static stacks - see the two deviations under Rendering. We copy the *pattern*, never the code: IE
> targets 1.21.1 not 26.1 (the productive-frogs fluid trap `CLAUDE.md` records), and its license is
> not cleared for source reuse. The whole shape is chosen to minimise the one cost that actually
> gates this repo: **bespoke art per machine** - hence the shared frame/motor/panel vocabulary and a
> single bespoke core per machine.

---

## The shape of the decision

A machine is a **stack of component blocks built around one core block** (a cuboid in general; the
first two machines are simple vertical columns). You place the core and the components; when the
exact blueprint is satisfied the machine forms and runs. Break any block and it disbands.

Four decisions are locked:

1. **Heterogeneous parts, not one block type.** Frame, motor, solar panel - the machine is visibly
   assembled from recognisable salvage, which is the mod's whole identity.
2. **A per-machine core block** is the placeable the structure is built around, and the *only*
   bespoke thing per machine. Everything else is shared.
3. **A strict per-machine blueprint.** One exact layout per machine, validated position by position.
   No rotation search, no mirror, no size scaling, no composition freedom. (Kept deliberately simple
   for now - see "Reversal recorded" and "Deferred".)
4. **Presence, not counts.** The blueprint says which block sits in which cell; it does not read
   "more motors = faster". Stats are per-machine config, not derived from the structure.

### Reversal recorded

An earlier turn approved "machine size drives radius." The strict-blueprint decision **supersedes
that** - a fixed layout has one size. Each machine's radius/rate is a **per-machine config
constant** instead. Size-scaling is not gone, only deferred (below).

---

## The component vocabulary (shared across all machines)

Built once, reused by every machine. This is the payoff of the heterogeneous-but-shared approach:
**per machine, the only new art is the core.**

| Block | Role | Source | Notes |
|---|---|---|---|
| **Frame** | shared component | crafted from `scrap_plating` / `rebar` | the Rain Collector's top cell; the cheap structural block of any stack |
| **Motor Block** | shared component | teardown-only (from a `broken_appliance` find) | placed form of the spreader spec's motor; the Grass Spreader's middle cell |
| **Solar Panel Block** | shared component | crafted from `e_scrap` + `cullet_glass` | placed form of the spreader spec's panel; the Grass Spreader's top cell |
| **`<machine>` Core** | identity + controller | per-machine recipe | the master; holds formed/unformed state; the only bespoke art per machine |

The **Rain Collector is not a shared stack component** - it is a *machine in its own right* (Core +
Frame, below) and separately a *crafting ingredient* consumed in the Grass Spreader Core's recipe.
It never sits as a cell inside another machine's stack.

**These are now blocks, not the crafting items the Soil Spreader spec described.** That spec had the
components *consumed in a recipe*; here they are *placed in the world* and visible. Same economics,
better legibility - you see what the machine is made of. The core's own recipe gets cheap because
the cost now lives in the placed structure.

**Motor and panel remain inert as machines** - no RF (P3.5: no power before the Nether), no Create
kinetics (P2.3). They are structural blocks whose only job is to be the right block in the right
cell of a blueprint. If either ever gains behaviour that is a new design decision.

---

## Formation - place the core, assemble the stack

The first two machines are **vertical stacks** (core on the bottom, components straight up), so the
whole "blueprint" is a column and there is no rotation or footprint to reason about. The flow:

1. **Place the core.** It always places, as an **unformed single block.** The core owns everything;
   the cells above are the stack.
2. **Convenience: assemble from inventory.** If the required component block(s) are in the player's
   inventory when the core is placed, they are **auto-placed up the column and consumed**, and the
   structure forms in that one action. Placing a Rain Collector Core while carrying a Frame builds
   the whole collector at once.
3. **Or complete it by hand.** If the parts are not on hand, the core sits unformed; the player
   stacks the components on top later and it forms when the column is complete. The auto-place path
   is a shortcut over manual building, not a separate mechanism - one validation, two ways in.
   (Decision, 2026-07-23: unformed-until-completed; placement is never refused.)
4. **Form -> convert to a machine.** On a complete column the core sets `FORMED` and the component
   cells become **dummy blocks** that belong to the machine (see Architecture). The loose blocks
   stop being loose blocks - they read as one machine.
5. **Break anything -> disband.** Breaking the core or any dummy disbands the whole: the dummies
   redirect the break to the core, the structure drops its component items (and the core its own,
   carrying any stored state such as the collector's water), and the cells clear.

**No assembly tool.** IE uses a hammer to avoid accidental formation of large hand-built structures;
a deliberate core-plus-two-blocks stack has no such risk, so formation is placement-driven and the
prybar/knife stay out of it.

---

## Rendering - IE's pattern, and where we deviate

**How IE actually does it** (verified against `ImmersiveEngineering-1.21.1-12.4.2`):

- The shape is a vanilla **`StructureTemplate` NBT file** (`IETemplateMultiblock extends
  TemplateMultiblock`, `getTemplate` -> `StructureTemplate`).
- On assembly, `replaceStructureBlock` swaps every built component block for **one generic
  multiblock-part block**. The block entities are **master or dummy** (`MultiblockBEHelperMaster` /
  `MultiblockBEHelperDummy`): the master holds the logic/state, dummies store an offset and redirect
  to it.
- The whole formed machine is drawn by **one block-entity renderer on the master**
  (`IEMultiblockRenderer extends IEBlockEntityRenderer<MultiblockBlockEntityMaster>`). The dummy
  cells render nothing of their own; the master's BER paints the entire model, which is also how IE
  animates moving parts (crusher bar, bucket wheel).

**Our two deviations, both because the machines are trivially small and static:**

1. **Code blueprint, not an NBT template.** A 2-3 block vertical column is a two-line offset list
   (Validation, below). IE uses `StructureTemplate` because its structures are large and irregular;
   ours are not, and an NBT file per machine would be ceremony.
2. **Per-cell static models, not a master BER.** IE needs the master BER because its machines have
   moving parts and span dozens of cells. Ours have **no moving parts**, so each cell can carry its
   own static "formed" model and the column reads as one machine with correct per-cell lighting and
   culling - the same static-multipart approach the Workbench already uses, and it keeps the mod's
   no-BER line. **If a later machine (a pump, the Gate) needs animation, that machine adds a master
   BER then** - which is exactly IE's split, just deferred to the machine that needs it.

**The formed model is bespoke, and this is the load-bearing correction.** IE's machines *do not look
like the blocks they are built from* - a formed coke oven reads as a coke oven, not as a stack of the
sheetmetal you placed. So the per-cell formed models above are **purpose-authored machine slices**,
not the loose component models restacked. Do not compose a formed machine from its loose parts and
call it done; that reads as three blocks on a pole, not a device. Corollary: **the earlier "composite
of component sub-models = minimal new art" claim was wrong.** A formed machine is real per-machine
art, and per-machine art is exactly the gate on this repo. The one genuine save is the **Rain
Collector**, whose formed look is the already-shipped tank + catch-tray (`rain_collector_base` +
`rain_collector_tarp`) - a device silhouette that exists today; the Grass Spreader and every later
machine need a new formed model.

**What we keep from IE unchanged:** the **master/dummy semantics.** The core is the master; on
formation the cells above become **dummy blocks** that store their offset to the core, redirect
breaking and `useItemOn` to it, and drop nothing on their own, and swap to the machine's bespoke
formed slices. That is the piece that makes a formed machine "one machine" rather than a stack of
loose blocks, and it is worth copying exactly.

---

## Validation - the blueprint

A blueprint is a **static list of `(offset, predicate)`** relative to the core, built once at
class-init:

- `offset` - a `Vec3i` from the core (for the first two machines, straight up: `(0,1,0)`,
  `(0,2,0)`).
- `predicate` - "this cell must be Frame" / "Motor Block" / "Solar Panel" / air. Predicates are
  **block tags**, not block ids, so a pack could sub a reskinned frame.

Validation walks the list once and returns true only if every cell matches. O(cells), no allocation.
A strict blueprint is what makes it this small. (Kept trivial now; a rotated offset table is the
additive change if a future machine is not a vertical column - see Deferred.)

**A single source of truth.** The same blueprint object drives runtime validation, the auto-place
"assemble from inventory" step, the Jade/JEI **preview**, and the GameTest. Define it once so none of
them can drift.

---

## Architecture

- **`Multiblock`** (record/class): the blueprint - its offset/predicate list and a
  `place(level, corePos)` helper used by the auto-assemble step and by tests to stamp a valid stack.
- **`MultiblockCoreBlock`** (abstract): owns `FORMED`, validation on placement and on the
  column-cell neighbour changes, the auto-assemble-from-inventory step, disband-on-break, and
  abstract `blueprint()` + `onFormed()/onDisbanded()`. Each machine's core extends it.
- **`MultiblockDummyBlock`**: the formed component cell. Holds a link back to its master core
  (relative offset), redirects break + `useItemOn` to the master, drops nothing on its own. This is
  the IE dummy/slave, trimmed to a stack.
- **Component item-blocks** (Frame, Motor, Solar Panel): plain palette blocks in their *loose*
  state; they become dummies only inside a formed machine. No BE.
- **The machine's work** hangs off the master core's formed tick - the multiblock layer decides
  *whether* it runs, not *what* it does.

No `SavedData`. `FORMED` and the dummy links are blockstate/nbt on the blocks; re-derived on load.

---

## The two first machines

Both are vertical stacks assembled by placing the core.

### 1. Rain Collector - **a redesign of the shipped block**

Today the Rain Collector is a `DoubleBlockHalf` two-cell block (`RainCollectorBlock`): the lower half
holds the tank (`RainCollectorBlockEntity` - rain fill, fluid capability, glass-bottle draw, and
water that survives break via a data component), the upper half is the tarp.

Reframe as **Rain Collector Core (bottom) + Frame (top)**:

- **All tank behaviour moves onto the core unchanged** - the BlockEntity, rain fill, capability,
  bottle interaction, and the water data-component. This is a structural/visual reframe, not a
  behaviour change; do not regress P1.10.
- The **Frame** is the shared component block, replacing the bespoke `HALF=UPPER` tarp cell. The
  existing base+tarp art becomes the formed model.
- Migration: the old two-`HALF` block is retired. **Pre-existing placed collectors in saved worlds**
  need handling - simplest is to leave the old block registered as a deprecated form that still
  works, or accept the break pre-beta and note it. Decide at implementation; flag in the roadmap.
- It is the smallest possible multiblock (2 cells), which is why it is the first one built - it
  proves the framework on shipped, already-understood behaviour.

### 2. Grass Spreader - the next machine (specced, not built)

**A four-cell tower, and the machine's identity changed** from a soil hopper to a **sprinkler** that
constantly spreads water. Bottom to top: **Grass Spreader Core** (its own texture, never the
collector's) / **Rain Collector** / **Sprinkler Head** / **Solar Panel**.

**No Machine Frame here** - the head is placed directly rather than transformed from a generic
frame. Two consequences for the framework, both already supported: a `Cell` may name the same block
as component *and* formed (only the collector cell transforms), and a **craftable block may itself
extend `MultiblockDummyBlock`** - standalone it behaves normally because `findCore` returns null,
and inside a machine it redirects to the master. That beats shipping a placeable component plus a
near-identical formed twin.

- **The Rain Collector is a literal component**, not just an ingredient - you build the spreader
  around the machine you already made. That is the progression beat, and it is the first blueprint to
  name one of our own machines as a part.
- **Nothing here needs sky**, because the incorporated collector is fiction rather than a running
  rain tick. If that ever changes, `canSeeSky(pos.above(2))` comes back and the shape must change.
- **The core runs only while `FORMED`** - break a cell and healing stops; the frontier begins to win.
  That intact-structure requirement is the ongoing "cost", with still no consumable.
- **The solar panel is a recoloured no-op daylight detector** - vanilla already ships a block that
  *is* a solar panel. Inert: no light detection, no redstone, no power.
- **This machine is where the BER arrives** (a spinning head), which the Rendering section above
  deferred to "whatever machine first needs animation". Water particles come first and need no
  renderer at all.

Full detail, including the conversion behaviour: [`grass_spreader_spec.md`](grass_spreader_spec.md).

---

## Legibility

- **Jade** - on the core: formed/unformed, and *what is missing* when unformed ("needs Solar Panel
  at ..."), reading the blueprint diff. This is the whole UX for "why won't it form", so it is not
  optional.
- **JEI** - a **multiblock preview** category showing the blueprint exploded, driven off the same
  `Multiblock` object. Deferred-acceptable if Jade covers the missing-block hint at ship.

## Tests (GameTest)

The framework is testable independently of any machine, using a throwaway test core + the real
components on the `empty_5x5x5` plot (may need a larger structure):

1. a completed column forms (`FORMED` true), and the cells above become dummies
2. a core placed alone sits **unformed**, then forms when the last component is stacked by hand
3. **auto-assemble:** a core placed while the components are in inventory forms in one action and
   consumes them from inventory
4. a core placed with the components *absent* from inventory does not consume anything and is not
   refused - it just sits unformed
5. one wrong block in a cell does not form
6. breaking any dummy, or the core, disbands the whole and drops the component items once each (no
   dupe, no loss); the core drop carries its stored state (collector water)
7. `Multiblock.place` then validate round-trips (guards preview-vs-runtime drift)
8. the machine's work runs only while formed (spreader spreads when formed, idle when disbanded;
   collector fills when formed)

## Deferred (explicitly, so the door stays open)

- **Size-scaling via tiered blueprints.** A larger prescribed shape -> larger radius, shipped as a
  second blueprint + core tier. Additive; nothing here blocks it.
- **Composition-driven stats** ("more motors = faster", panel-as-budget). Rejected for now as more
  fiddliness than the mod wants; the strict blueprint is the deliberate opposite choice.
- **Shared component capabilities** (item/fluid routing through faces). The mod has none today and
  this system assumes none.

## Open questions

1. **Exact blueprints** - all of them, including the spreader's. Placeholders until the balance pass.
2. **Do components flip to a `formed` visual variant, or only the core?** Cheap either way; pick at
   implementation.
3. **Core recipe gating** - plain craft (consistent with the spreader spec) vs teardown-knowledge.
   Same open question the spreader already carries.
4. **Naming** - "Machine Frame", "Motor Block", "core" are working names.

## Before implementing: study both jars

Directive (Jason, 2026-07-23): when this gets built, **read both the IE and Powah jars first** for
placement, validation, and the user-facing guide/preview - do not implement from this spec alone.
Both are available locally (1.21.1, so 26.1 API deltas apply - port the pattern, not the code; and
neither license is cleared for source reuse):

- **IE** `ImmersiveEngineering-1.21.1-12.4.2-194.jar` - `blockimpl/MultiblockBEHelperMaster` +
  `...Dummy` (master/dummy split), `IETemplateMultiblock` / `TemplateMultiblock` (validation against
  a `StructureTemplate`), `ManualElementMultiblock` (the exploded structure preview in its manual),
  `IEMultiblockRenderer`. This is the placement/validation/formation reference.
- **Powah** `Powah-6.2.10.jar` - the reactor's in-place formation and its guide/JEI preview. Compare
  how it communicates "what to build" to the player against IE's manual element, and take whichever
  reads cleaner for a first-time builder.

Specifically compare, before locking the implementation: how each **validates** and reports *which
cell is wrong*, how each drives its **preview/guide** off the structure definition, and whether the
**auto-assemble-from-inventory** flow this spec wants has a precedent in either (IE's Placers
integration is close). Fold anything better than this spec back into it.

## Cost of building this

- **Framework Java:** `Multiblock`, `MultiblockCoreBlock`, `MultiblockDummyBlock`, validation,
  auto-assemble, Jade hint. No art.
- **Shared components:** Frame, Motor Block, Solar Panel Block = **3 texgen surfaces, once, ever**
  (each needs a loose model and a formed-cell model, but that is variants of one surface). The Frame
  is genuinely new art even though the Rain Collector ships - the collector's tarp cell is being
  *replaced* by the shared Frame, so the Frame is drawn fresh.
- **Per machine after that:** the loose core texture **plus a bespoke formed model** (the machine's
  own device silhouette - see the Rendering correction). This is the real per-machine art cost, and
  the honest gate on the whole chain - not the near-free "reuse the component models" it was first
  written as. The shared component vocabulary still amortises the *loose* blocks; the *formed* look
  does not come free.
- **Rain Collector redesign:** the cheap one - mostly Java (retire the `HALF` block, move behaviour
  onto the core), and its formed model is the **already-shipped** base/tarp art (tank + catch tray),
  so it needs no new formed model. Every other machine does.

## Design record still owed

Record in `../trashlands/docs/design_decisions.md` once settled, likely folded into **P2.4-R3**
alongside the spreader: the multiblock pattern (IE-style master/dummy formed-in-place, heterogeneous
shared components + per-machine core, strict blueprint, presence-not-counts, place-core-to-assemble
with unformed-until-completed), the two deviations from IE (code blueprint + per-cell static models,
both because the machines are small and static), that it supersedes the "size drives radius"
direction for now, and that components stay inert (no RF, no kinetics).

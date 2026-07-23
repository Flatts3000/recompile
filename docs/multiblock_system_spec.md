# Multiblock system - implementation spec

**Written 2026-07-23.** The shared framework the reclamation machines (P2.4-R) and later the
Overworld Gate are built on. Spec only - nothing here is built yet.

Design source of truth is the pack repo: `../trashlands/docs/design_decisions.md`. This system is
**not yet recorded there** - see "Design record still owed".

> **Pattern lineage:** modelled on Powah's reactor (one repeated structure, formed in place), not
> Immersive Engineering's hammer-assembled heterogeneous templates. We copy the *pattern*, never the
> code: both target 1.20/1.21 not 26.1 (the productive-frogs fluid trap `CLAUDE.md` already records),
> and neither mod's license has been cleared for source reuse. The final shape is a hybrid of Powah
> (formed in place, blocks keep their own models) and IE (visible heterogeneous parts), chosen to
> minimise the one cost that actually gates this repo: **bespoke art per machine.**

---

## The shape of the decision

A machine is a **fixed cuboid of component blocks built around one core block.** You place the
components and the core; when the exact blueprint is satisfied the machine forms and runs. Break any
block and it disbands.

Four decisions are locked:

1. **Heterogeneous parts, not one block type.** Frame, motor, solar panel, rain collector - the
   machine is visibly assembled from recognisable salvage, which is the mod's whole identity.
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
| **Machine Frame** | structural filler | crafted from `scrap_plating` / `rebar` | the cheap bulk of any structure |
| **Motor Block** | required component | teardown-only (from a `broken_appliance` find) | placed form of the spreader spec's motor |
| **Solar Panel Block** | required component | crafted from `e_scrap` + `cullet_glass` | placed form of the spreader spec's panel |
| **Rain Collector** | water component | **already shipped** | reused as-is; its tank/rain fill is the machine's water. Zero new art. |
| **`<machine>` Core** | identity + controller | per-machine recipe, cheap | holds the formed/unformed state; the only bespoke art per machine |

**These are now blocks, not the crafting items the Soil Spreader spec described.** That spec had the
components *consumed in a recipe*; here they are *placed in the world* and visible. Same economics,
better legibility - you see what the machine is made of. The core's own recipe gets cheap because
the cost now lives in the placed structure.

**Motor and panel remain inert as machines** - no RF (P3.5: no power before the Nether), no Create
kinetics (P2.3). They are structural blocks whose only job is to be the right block in the right
cell of a blueprint. If either ever gains behaviour that is a new design decision.

---

## Formation

**Auto-form, no tool** (the Powah behaviour, and it needs no new wrench):

- The **core block owns validation.** On placement, and on any neighbour change within the
  blueprint's bounding box, the core re-checks the blueprint.
- **Match -> form:** set the core's `FORMED` blockstate true. Optionally, component blocks flip to a
  `formed` variant for a visual tell (a config/state boolean, no new blocks).
- **Any break -> disband:** a broken component fails the next validation and the core clears
  `FORMED`. Because validation is cheap and driven by neighbour-change events, there is no polling.

**Why the core owns it, not a BlockEntity on every part:** only the core needs to know. Components
are dumb blocks that merely need to *be there*. This mirrors the mod's standing rule - a BlockEntity
only when something is genuinely stored - and keeps the frame/panel/motor as pure palette blocks.

**Orientation:** the blueprint is stored in **one canonical orientation keyed off the core's
`FACING`**, so validation rotates the offset table to the core's facing (4 yaw rotations, no mirror).
This is the one concession to not forcing the player to build facing a fixed compass direction; it is
a table rotation, not a search.

---

## Validation - the blueprint

A blueprint is a **static list of `(offset, predicate)`** relative to the core, built once at
class-init:

- `offset` - a `Vec3i` from the core.
- `predicate` - "this cell must be Machine Frame" / "Motor Block" / "Solar Panel" / "Rain Collector"
  / air. Predicates are **block tags**, not block ids, so a pack could sub a reskinned frame.

Validation walks the list once, rotating each offset by the core's `FACING`, and returns true only
if every predicate matches. O(cells), no allocation beyond the rotated cursor. This is the whole
algorithm - a strict blueprint is what makes it this small.

**A single source of truth.** The same blueprint object drives three things: runtime validation, the
Jade/JEI **preview**, and the GameTest that builds a known-good structure. Define it once so the
preview cannot drift from what the game actually checks.

---

## Architecture

- **`Multiblock`** (record/class): the blueprint - its offset/predicate list, its bounding box, and
  a `place(level, corePos, facing)` helper used only by tests to stamp a valid structure.
- **`MultiblockCoreBlock`** (abstract): owns `FORMED` + `FACING`, the neighbour-change revalidation,
  and an abstract `blueprint()` and `onFormed()/onDisbanded()`. Each machine's core extends it.
- **Component blocks**: `Block` subclasses or even plain `Block`s carrying a tag. No BE.
- **The machine's actual work** (e.g. the spreader's nearest-first conversion) hangs off the core's
  formed tick, exactly as the single-block spreader spec described - the multiblock layer only
  decides *whether* it runs, not *what* it does.

No `SavedData`. `FORMED` is blockstate; re-derived on load by the same neighbour-change path.

---

## First consumer: the Soil Spreader

Rewrites the recipe/placement section of `soil_spreader_spec.md`. The spreader's *behaviour*
(nearest-first, converts to grass, holds its radius) is unchanged - only how you build it changes.

- **Structure (placeholder, for the balance pass):** a small cuboid - e.g. a 3x3 frame base with a
  Motor Block, a Solar Panel on top, a Rain Collector, and the **Soil Spreader Core**. Exact cells
  are a blueprint constant, tuned later.
- **Radius** is a per-machine config constant (`soilSpreaderRadius`), not size-derived.
- **The core runs the spread tick only while `FORMED`.** Break a panel and healing stops; the
  frontier starts to win. That is the ongoing "cost" - keeping the machine intact - with still no
  consumable.

The rest of `soil_spreader_spec.md` (behaviour, eligibility, `mound_bed` interaction, tests) stands.

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

1. a fully-built blueprint forms (`FORMED` true)
2. an incomplete structure does not form
3. one wrong block in one cell does not form
4. breaking a component after formation disbands it
5. the same structure forms in all four core facings (rotation table)
6. `Multiblock.place` then validate round-trips (guards preview-vs-runtime drift)
7. the machine's work runs only while formed (spreader: spreads formed, idle when disbanded)

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

## Cost of building this

- **Framework Java:** `Multiblock`, `MultiblockCoreBlock`, validation, Jade hint. No art.
- **Shared components:** Machine Frame, Motor Block, Solar Panel Block = **3 texgen surfaces, once,
  ever** (Rain Collector already shipped).
- **Per machine after that:** one core texture + one blueprint constant. This is the whole point of
  the hybrid - the art gate is paid once for the vocabulary, then a single surface per machine.

## Design record still owed

Record in `../trashlands/docs/design_decisions.md` once settled, likely folded into **P2.4-R3**
alongside the spreader: the multiblock pattern (Powah-style formed-in-place, heterogeneous shared
components + per-machine core, strict blueprint, presence-not-counts), that it supersedes the
"size drives radius" direction for now, and that components stay inert (no RF, no kinetics).

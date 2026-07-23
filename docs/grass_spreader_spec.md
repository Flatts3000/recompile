# Grass Spreader - implementation spec

**Written 2026-07-23.** Rung 1 of the reclamation chain (design P2.4-R). Spec only - not built.

> **Renamed from `soil_spreader_spec.md`, because the machine changed identity.** It is not a soil
> hopper that spreads dirt; it is a **sprinkler** that spreads *water*, constantly, from a rain
> collector built into it. How it converts ground is unchanged; what it *is* changed, and with it
> the whole silhouette and structure.

Design source of truth is the pack repo: `../trashlands/docs/design_decisions.md` (**P2.4** the
original chain, **P2.4-R** the economy revision, **P1.7-R** encroachment). The decisions here are
**not yet recorded there** - see "Design record still owed".

---

## What it is

A four-block sprinkler tower. It draws water from a Rain Collector built into its own structure and
throws it over the surrounding ground, turning dead earth to grass within a radius, forever,
**consuming nothing**.

**Why it consumes nothing, and why that is now honest.** An earlier draft had it eat compost and
clean water per P2.4 item 1. That is superseded, and the sprinkler framing is what stops the
reversal feeling like a shortcut: the machine *contains* a rain collector, so it supplies itself.
The cost is one steep build, not a drip. The ongoing pressure comes from P1.7-R instead - the
junkyard takes healed ground back, so a spreader has to out-pace erosion, and permanence still needs
trees.

**A consequence worth stating:** because the incorporated collector is fiction rather than a running
rain-collection tick, **nothing in this machine needs sky access.** That is what frees the stack to
be four tall with parts on top. If the collector is ever made to genuinely collect, the constraint
returns and the shape must change - `RainCollectorBlockEntity` checks `canSeeSky(pos.above(2))`.

---

## Structure - four cells, bottom to top

**No Machine Frame in this machine.** The Machine Frame stays the Rain Collector's component, so the
shared vocabulary keeps its user; here the moving part is a **Motor**.

| Cell | You place | Formed as | Notes |
|---|---|---|---|
| 3 (top) | **Solar Panel** | *unchanged* | Unshaded, caps the tower. Shared component. |
| 2 | **Motor** | **sprinkler head** | A motor is what *spins* a sprinkler head - the fiction is exact, and it is why this machine is the one that eventually needs rotation. Bespoke art. |
| 1 | **Rain Collector** | `grass_spreader_tank` | The incorporated water - a literal collector, consumed into the structure. |
| 0 (bottom) | *(the core itself)* | **Grass Spreader Core** | The master, and the pump base. **Its own texture** - deliberately not the collector's palette, so the two machines never read as the same object. |

**The motor is the machine's gate.** Per the component vocabulary it is **teardown-only** - torn out
of a found appliance (`broken_appliance`, a Bulky Waste line) at the Recompile Workbench, never
crafted. So rung 1 sits behind the teardown spine and a find, which orders progression well: you
salvage a motor before you can water anything. It also means the spreader cannot be rushed.

**The Solar Panel keeps its own appearance** - the framework supports a `Multiblock.Cell` naming the
same block as component *and* formed, so a cell that does not change costs one block, not two. The
collector and motor cells both transform: a collector is a core with a tank and must not stay one
inside another machine, and a motor visibly becomes the head.

**The Solar Panel is a craftable block that also extends `MultiblockDummyBlock`.** Standalone it
behaves like an ordinary block (`findCore` returns null and every override falls through); inside a
formed spreader it redirects break and use to the core, which is what keeps the machine one object.
One block doing both jobs beats a placeable component plus a near-identical formed twin.

Built with the shipped framework (`multiblock_system_spec.md`): place the core, and it auto-assembles
from your inventory if you are carrying the parts, or waits while you stack them by hand.
Sneak-place gives a bare core. Breaking any cell disbands the whole and returns every part.

**The rain collector is a literal component**, not a borrowed model - you craft a Rain Collector and
build the spreader around it. That is the progression beat: your first machine becomes part of your
second. It also means the spreader cannot be reached before the collector, which is the right order
for the water thread.

---

## New blocks

Three bespoke to this machine, plus two shared components later machines reuse.

| Block | Kind | Notes |
|---|---|---|
| `grass_spreader` | core, bespoke | Holds `FORMED`; runs the conversion tick and the particles. |
| `grass_spreader_tank` | dummy, bespoke | The formed collector cell; reuses the rain-collector tote model. |
| `grass_spreader_head` | dummy, bespoke | The formed motor cell - the sprinkler head. Sprays particles, spins later. |
| `motor` | **shared component** | **Teardown-only**, from a `broken_appliance` find. Inert - see below. |
| `solar_panel` | craftable **and** dummy, **shared** | Inert - see below. Reusable by later machines. |

The `broken_appliance` find and its teardown recipe (`-> motor + scrap_metal + plastic_scrap`) come
with this machine: one line in `loot_table/blocks/bulky_waste.json` plus one `recompile:teardown`
recipe, no new systems. It quietly restores the appliance P1.11 dropped when Bulky Waste replaced it,
as a *find item* this time - the shape the design settled on.

**Both shared components are inert, and their names invite exactly the opposite** - so state it
plainly, because either would break locked design:

- **Solar Panel** - a recoloured, **no-op** daylight detector: vanilla's `template_daylight_detector`
  model with its texture recoloured to the palette, because vanilla already ships a block that *is* a
  solar panel. It does **not** detect light, emit redstone, or generate power. P3.5 locks "no RF
  before the Nether."
- **Motor** - it does **not** rotate anything, expose kinetics, or require Create. P2.3 locks
  "Recompile converts, Create moves", and the mod never *requires* Create.

Same rule the Machine Frame already follows. The eventual spinning head is a **client-side visual on
the formed machine**, not the motor gaining behaviour - if either component ever grows real
mechanics, that is a new design decision, not an implementation detail.

---

## Behaviour - the conversion

**Nearest-first.** Each work pulse, convert the single closest eligible block. One rule buys two
behaviours: growth reads outward from the machine, and ground the frontier took back is *closer* than
untouched ground, so it is repaired first - no separate repair pass, no stored progress.

**Eligibility.** Convert a surface block when all hold:

1. it is in the `recompile:spreadable` tag (ships as `minecraft:coarse_dirt` + `minecraft:dirt`);
2. it is **not** mycelium (the dump-mushroom substrate, the P1.9 forage economy - the same carve-out
   encroachment makes);
3. its surface is within a vertical tolerance of the machine, so it cannot reach up cliffs or down
   pits (resolve via `MOTION_BLOCKING_NO_LEAVES`, matching `RCEncroachment`);
4. **grass would survive there** - non-opaque above, enough light. Without this the machine converts
   roofed ground, vanilla kills it back to dirt, and the spreader churns the same block forever.

**Plain `dirt` is deliberately a target.** Vanilla grass cannot spread onto coarse dirt but *can*
spread onto dirt, so leaving dirt as an intermediate would let vanilla finish the job free and break
P2.4-R item 3. For the same reason the conversion goes **straight to grass, never via dirt**.

**Cadence.** One block per pulse on a config interval; drop to a slow idle re-scan when the radius is
fully healed, so it still notices ground the frontier has since taken.

**Radius** is a per-machine config constant. (Size-driven radius was considered and dropped when the
blueprint became strict - see the multiblock spec.)

---

## The spray - two pieces, very different costs

**1. Water particles - ship with the machine.** `Block.animateTick(state, level, pos, random)`
(verified present in 26.1 at `Block.java:355` - the hook torches use) spawns client-side particles
with **no BlockEntity and no renderer**. Throw `ParticleTypes.SPLASH` / `FALLING_WATER` / `RAIN`
outward in a ring from the head. This is most of the effect for almost none of the cost, and it is
what makes the machine read as *working*.

**2. A spinning head - a follow-up, budgeted separately.** Smooth rotation needs a
`BlockEntityRenderer`, and **26.1's BER API is not the one any tutorial or reference mod uses**:

```
BlockEntityRenderer<T extends BlockEntity, S extends BlockEntityRenderState>
    S createRenderState()
    void extractRenderState(T be, S state, float partialTicks, Vec3 cameraPos, CrumblingOverlay breakProgress)
    void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
```

A render-state / submit-node architecture, not the old
`render(be, partialTick, poseStack, buffers, light, overlay)`. **IE's 1.21.1 renderers do not port.**
It also means giving a cell a BlockEntity purely to hang the renderer on - the first BER in this mod.

**Do these in order and keep them separate.** A sprinkler with a convincing spray and a static head
reads fine; a spinning head with no water does not. Ship the particles; treat rotation as its own
task with "learn the new BER API" priced in honestly. The multiblock spec already anticipated this
("if a later machine needs animation, that machine adds a master BER then") - this is that machine.

**Open:** whether the renderer hangs off the head cell (simplest) or the core draws it IE-style (a
BlockEntity on a dummy is a wrinkle the framework has not needed yet).

---

## Interactions

- **Encroachment (P1.7-R)** - the headline one. A running spreader repairs inside its radius, so
  **its radius is exactly the land you can hold at rung 1.** Beyond it, erosion wins. Expanding means
  more spreaders, or climbing to trees for permanence.
- **Mound retirement (Phase 5)** - add `mound_bed` to `spreadable` when it exists, so spreading over
  a footprint **retires that mound forever**. The quarry-vs-heal decision made physical, and the most
  important interaction in the chain.
- **Rungs 2-3** - spreader output *is* the precondition terrain the seeder and nursery test for
  (P2.4-R item 4). No counters, no flags.
- **The water thread (P1.10)** - the collector stops being a one-off utility and becomes a component
  of the machine that heals the world.

---

## Assets

Leaning on vanilla and what exists, per the strategy that carried the rain collector:

- **Solar panel**: vanilla `template_daylight_detector` model + its texture recoloured.
- **Tank cell**: the shipped rain-collector tote model, reused.
- **Sprinkler head**: bespoke. Vanilla's closest shape is the brewing stand's radiating arms; worth
  starting there and cutting it down.
- **Core**: bespoke, and **must not share the collector's texture**. Shape is open - **no real-world
  reference exists for this machine** (unlike the collector's IBC tote), so it is iterated
  **in-world** rather than settled on paper.
- Recolours are deterministic luminance ramps (`scratchpad/recolor.py`), not hand-drawn art.

## Data surface

- Tag `recompile:spreadable`.
- Config under `reclamation`: `grassSpreaderEnabled`, `grassSpreaderRadius`,
  `grassSpreaderIntervalTicks`, `grassSpreaderIdleIntervalTicks`, `grassSpreaderVerticalTolerance`.
- Recipes: the core, and the shared `solar_panel`. All numbers are first-pass placeholders and join
  the **pre-beta balance pass** - do not tune them in isolation.

## Tests (GameTest)

Structure tests come free from the framework's shape; these are the machine's own, driven through a
static entry point per the `sortOnce` / `encroachOnce` convention:

1. converts coarse dirt to grass, in one step, never via dirt
2. converts plain dirt too (the vanilla-spread loophole)
3. **nearest-first**: with two candidates, the closer converts
4. **repairs before extending**: a reverted block near the machine beats untouched ground further out
5. never converts mycelium
6. never converts a built block
7. skips a roofed position where grass would immediately die
8. respects the radius and the vertical tolerance
9. **only runs while formed** (the framework's rule, asserted for this machine)
10. round-trip with `RCEncroachment`: spread, encroach, spread again -> stable

## Open questions

1. **Core shape** - iterate in-world.
2. **Sprinkler head shape** - brewing-stand arms vs something cut down.
3. **Where the spinning renderer lives** (head cell vs core), when rotation is built.
4. **Naming** - "Grass Spreader" is the working name; P2.4 item 3 calls it the soil spreader.
5. All numbers: radius, rate, recipe costs. Deferred to the balance pass.

## Verification

1. `JAVA_HOME="/c/Program Files/Java/jdk-25" ./gradlew build` - redirect to a file, check `$?`;
   never trust an exit code through a pipe.
2. `JAVA_HOME="/c/Program Files/Java/jdk-25" ./gradlew runGameTestServer` - full suite; the reported
   total is ours **plus one** (a `minecraft:default` batch runs alongside).
3. `runClient` - build the tower, confirm it forms, watch the particles, and **iterate on the look
   from there** rather than from renders.
4. **Run a code review before merging**, not after.

## Design record still owed

Record in `../trashlands/docs/design_decisions.md` as **P2.4-R3**: rung 1 is a sprinkler fed by an
incorporated rain collector; it consumes nothing, and why; rung 1 is machine-only (superseding P2.4
item 3's manual-first "monument" beat); the shared component vocabulary (Machine Frame, Solar Panel)
and that those components are **inert** - no RF, no kinetics - which is what keeps P3.5 and P2.3
intact.

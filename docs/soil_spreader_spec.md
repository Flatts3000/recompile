# Soil Spreader - implementation spec

**Written 2026-07-23.** Rung 1 of the reclamation chain (design P2.4-R). Spec only - nothing here
is built yet.

Design source of truth stays in the pack repo: `../trashlands/docs/design_decisions.md`
(**P2.4** original chain, **P2.4-R** the economy revision, **P1.7-R** encroachment). The decisions
this spec makes are **not yet recorded there** - see "Design record still owed" at the end.

> **Name:** called the "grass spreader" in conversation; **Soil Spreader** is the name P2.4 item 3
> already uses, so this doc uses that. Worth settling before the block is registered.

---

## What it is

A placed block that turns dead ground into grass within a radius, **forever, consuming nothing**.
No fuel, no power, no inventory, no GUI. You build it, you place it, it works.

## The one big reversal: it consumes nothing

P2.4 item 1 said *"healing is a recipe, not a right-click: compost + clean water + seed"*, and
P2.4-R item 3 said each rung machine consumes *"compost + clean water (+ power at tier)"*.
**That is superseded.** The Soil Spreader has no running cost.

**Why it holds.** Three reasons, and the first is the real one:

1. **The cost moved into the recipe.** The machine *is* a rain collector (its water), a solar panel
   (its power) and a motor (its mechanism). It runs free because it carries its own supply - the
   fiction explains the mechanic rather than hand-waving past it. The price is one steep one-time
   build, not a drip.
2. **P1.7-R already supplies the ongoing pressure.** When healed land was permanent, input cost was
   the only thing keeping healing honest. Now the junkyard fights back, so the ongoing cost is
   *spatial*: a spreader has to out-pace erosion, and permanence still needs trees. The tension no
   longer has to come from consumables.
3. **The declared inputs do not exist.** There is no `compost` item and no clean-water resource;
   compost sits behind the P1.4 knowledge system, which is *under review and may never land*. A
   consuming spreader could not ship at all. (P1.10 item 6 had already ruled healing a *later* water
   consumer, not the first - washing salvage was - so little is lost.)

**What it costs the design, honestly:** `organic_muck` loses its "healing currency" job from P2.4
item 1, and leachate purification loses a headline consumer. Muck gets a partial role back by
sitting in the recipe (below).

**Also dropped:** P2.4 item 3's *"manual per-block first - the first grass patch is a monument"*.
Rung 1 is machine-only. The monument beat moves to building the spreader.

---

## Recipe and the two new components

**Soil Spreader** = `rain_collector` + `solar_panel` + `motor` + structural scrap
(`scrap_plating` / `rebar`), plus `organic_muck` to keep muck tied to healing.

Consuming a whole Rain Collector is deliberate: it costs a real, useful block, and makes the water
thread load-bearing again after healing stopped consuming water.

Motor and solar panel are **generic components, not spreader parts.** All four P2.4-R machines
(spreader, seeder, nursery, tree planter) will want them, so they are specced as a shared
vocabulary - the first entries in a salvaged-component tier. Both are **inert crafting items with
no behaviour of any kind** (see below).

| Component | Source | Why |
|---|---|---|
| **Motor** | **Teardown only.** Torn out of a found appliance at the Recompile Workbench. | Precision machinery you cannot fabricate in a dump. Uses the shipped teardown spine and the existing find vector. |
| **Solar Panel** | **Crafted:** `e_scrap` + `cullet_glass` (+ plating frame). | Recovered cells bodged behind salvaged glass. Gives `e_scrap` its first real job. |

### The motor needs a find to come out of

Nothing currently tears down into a motor. Per the standing invariant - *"nothing enters the found
economy without a teardown exit"* - this is **two additive changes**, no new systems:

1. A new find item (working name `broken_appliance`) added as **one line in
   `loot_table/blocks/bulky_waste.json`**.
2. A `recompile:teardown` recipe: `broken_appliance` -> `motor` + `scrap_metal` + `plastic_scrap`.

This quietly restores the appliance that P1.11 dropped when Bulky Waste replaced it - as a *find
item* this time, which is the shape that design settled on.

### Both are crafting components ONLY - they do nothing

Neither the motor nor the solar panel has any behaviour. They are **inert items that exist to be
consumed in recipes**: no capability, no block form, no placement, no tick, nothing reads a value
off either one.

Stating it explicitly because both names actively invite the opposite, and either would break
locked design:

- **"Solar panel" invites RF.** P3.5 locks *"before the Nether, no RF - fuel/manual only."* No
  energy generation, storage, transmission, wires, or capability.
- **"Motor" invites Create kinetics.** P2.3 locks *"Recompile converts, Create moves"*, and the mod
  never *requires* Create. No rotation, no stress, no kinetic capability.

If either ever grows behaviour, that is a new design decision - not an implementation detail of
this spec.

---

## Behaviour

### Target selection: nearest-first

Each work pulse, convert **the single closest eligible block** to the spreader.

This one rule buys two behaviours for free:
- **Growth reads outward** - green creeps out from the machine, so the radius is legible on the
  ground without any UI.
- **Repair is automatic and prioritised** - ground the frontier took back is closer than unbroken
  ground, so it gets fixed first. No separate repair pass, no stored progress.

### Eligibility

Convert a surface block when **all** hold:

1. It is in the **`recompile:spreadable`** tag - ships as `minecraft:coarse_dirt` and
   `minecraft:dirt`. Allowlist, so no unrecognised modded block is ever converted.
2. It is **not** mycelium (the dump-mushroom substrate - same carve-out encroachment makes, for the
   same reason: it is the P1.9 forage economy).
3. Its column surface is within **+/- 3 blocks** of the spreader's own Y, so it does not reach up
   cliffs or down pits. Surface resolved via the `MOTION_BLOCKING_NO_LEAVES` heightmap, matching
   `RCEncroachment`.
4. **Grass would survive there** - the block above is non-opaque and light is sufficient. Without
   this the machine converts roofed ground, vanilla kills the grass back to dirt, and the spreader
   churns the same block forever.

**Plain `dirt` is deliberately in the tag.** Converting it closes the loophole from the encroachment
work: vanilla grass cannot spread onto coarse dirt, but it *can* spread onto dirt, so leaving dirt
as an intermediate would let vanilla finish the job free and break P2.4-R item 3.

### Conversion

`spreadable` -> **`minecraft:grass_block` directly.** Never via an intermediate dirt step, for the
reason immediately above.

### Cadence

One block per pulse on a config interval. When nothing in radius is eligible, drop to a **slow idle
re-scan** so the machine still notices ground the frontier has since taken, without a full radius
scan every pulse.

---

## Architecture

**No BlockEntity.** The spreader stores nothing - radius and rate are config, and nearest-first
recomputes from the world each pulse. Use **self-rescheduling block ticks**
(`scheduleTick` in `onPlace`, reschedule at the end of `tick`); scheduled ticks persist across
save/load, so the machine survives a reload with no serialisation at all. This keeps the mod's
"a BlockEntity only when something is genuinely stored" line - the same reasoning that made
`SortableBlock` a palette flyweight.

One blockstate property: **`WORKING`** (boolean), for the active/idle visual tell and for Jade.
Derived each pulse, not authoritative state.

**Scan:** a static, distance-sorted offset table built once at class-init. Walk it and stop at the
first eligible column, so the common case exits almost immediately and only a fully-healed radius
pays for a complete walk - which is exactly the case that then drops to the idle interval.

---

## Interactions

- **Encroachment (P1.7-R)** - the headline one. A running spreader repairs inside its radius, so
  **its radius is precisely the land you can hold at rung 1.** Beyond it, erosion wins. Expanding
  means more spreaders, or climbing to trees for permanence. That makes the machine's radius a
  legible statement about territory rather than a tuning number.
- **Mound retirement (Phase 5)** - `mound_bed` should be added to `spreadable` when it exists, so
  spreading over a mound's footprint **retires it forever**. That is the quarry-vs-heal decision
  made physical, and it is the single most important interaction in the chain. Spec it now, wire it
  when the block lands.
- **Rungs 2-3** - spreader output *is* the precondition terrain the seeder and nursery test for
  (P2.4-R item 4). No counters, no flags.
- **Vanilla grass spread** - deliberately never relied on. See eligibility rule 4 and Conversion.

---

## Data surface

- **Tag** `recompile:spreadable` (block).
- **Config**, under the existing `reclamation` block: `soilSpreaderEnabled`,
  `soilSpreaderRadius`, `soilSpreaderIntervalTicks`, `soilSpreaderIdleIntervalTicks`,
  `soilSpreaderVerticalTolerance`.
- **Recipes**: spreader, solar panel (both plain crafting); motor via `recompile:teardown`.
- **Loot**: one line in `loot_table/blocks/bulky_waste.json` for the find.

All numbers are first-pass placeholders and join the **pre-beta balance pass** with every other
weight and cost in the repo - do not tune them in isolation.

## Legibility

- **Jade provider** - radius, working/idle, and blocks remaining. Mirrors the existing
  `SortProgressProvider`, which already surfaces otherwise-hidden blockstate.
- **JEI** - nothing new needed. The spreader and panel are ordinary crafting recipes and appear
  automatically; the motor appears in the existing Teardown category.

## Tests (GameTest)

Static entry point (`spreadOnce(ServerLevel, BlockPos)`) called directly, per the
`SortableBlock.sortOnce` / `RCEncroachment.encroachOnce` convention:

1. converts coarse dirt to grass, in one step, never to dirt
2. converts plain dirt too (the vanilla-spread loophole)
3. **nearest-first**: with two candidates, the closer one converts
4. **repairs before extending**: a reverted block near the machine beats untouched ground further out
5. never converts mycelium
6. never converts a built block
7. skips a roofed position where grass would immediately die
8. respects the radius bound, and the vertical tolerance
9. reports idle when the radius is fully healed
10. round-trip with `RCEncroachment`: spread, encroach, spread again -> stable

## Open questions

1. **Name** - Soil Spreader vs grass spreader. Settle before registering.
2. **Radius, rate, recipe quantities** - all placeholders, deferred to the balance pass.
3. **Off switch?** Currently none: break it to stop it. A right-click toggle on a blockstate is
   cheap if placing one near a base turns out to be annoying, but it is not specced in.
4. **`broken_appliance` naming and art** - and whether one find yields one motor or several.
5. **Does the spreader convert the block it stands on?** Harmless either way; pick at implementation.

## Cost of building this

Four new registrations - `soil_spreader` (block), `motor`, `solar_panel`, `broken_appliance`
(items) - so **four texgen surfaces and an art-approval gate** before it can ship. That is the
long pole, not the Java.

## Design record still owed

The reversals here are **not yet in the pack repo**. Once this spec settles, record in
`../trashlands/docs/design_decisions.md` as **P2.4-R3**: the spreader consumes nothing and why
(recipe-carried cost + P1.7-R supplying pressure), rung 1 is machine-only (superseding P2.4 item
3's manual-first monument beat), the motor/solar-panel component vocabulary and their split
acquisition, and that **both components are inert crafting items** - no RF, no kinetics - which is
what keeps P3.5 and P2.3 intact.

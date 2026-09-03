# Garbage Vacuum - bulk block collection

**Status: specced 2026-09-03.** Issue #336. Owner rulings are recorded inline and dated; everything
else is implementation detail that a playtest may move.

**The one-line version:** a powered handheld tool that takes `SortableBlock` blocks out of the world
several at a time instead of one mine-swing at a time, and a Charging Station to refill it.

---

## Why this does not automate the mod's heart

Stated first, because "bulk garbage removal" sounds exactly like automating the pick-through loop.

- **Breaking a garbage block already drops the block itself.** Every member of the family has a plain
  self-drop loot table, so a player can already collect garbage blocks by mining them. The vacuum only
  makes that fast. Sorting still happens afterwards - by hand at a Sorting Tarp or Workbench, or in a
  Trommel.
- **There is already a machine to feed.** `TrommelBlockEntity` takes garbage block *items* and reads
  their pull table through `SortableBlock.sortRolls(item)` / `pullTableFor(item)`. Vacuum into a
  Trommel is the intended pairing and needs no new plumbing on the machine side.
- **Phase 5 makes bulk collection safe.** `MoundGroundBlock` sits under the footprint carrying the
  column count, so stripping the garbage above it does not touch the memory and the mound still
  regrows. Mounds are renewable quarries, which is what makes a bulk tool reasonable rather than
  extractive. **Do not "optimise" the mound ground away while implementing this.**

It is a throughput tool, not automation: player-held, player-aimed, consuming power per block. It moves
nothing on its own, so it does not touch "Recompile converts, Create moves". A placed, self-running
form would be a different issue and a different ruling.

---

## What it targets

Every `SortableBlock` subclass, **derived with `instanceof SortableBlock` over the block registry**,
never hand-listed. `SortingTarpTests` and `SortingData.sortingSources` both already derive it, and the
prose list has gone stale more than once.

**`BulkyWasteBlock` is out of scope.** It is not a `SortableBlock`; it is a one-shot prybar find, and
hoovering up buried treasure removes the beat it exists to create.

---

## Owner rulings

### Gravity: let it collapse (2026-09-03)

`SortableBlock extends FallingBlock`, so vacuuming from the bottom of a mound collapses everything
above it. **That is the feature, not the bug.** The vacuum takes blocks in range without regard for
height; the mound slumps into the intake and the fallen blocks land back in range to be taken on a
following tick.

The alternative considered was a top-down peel - always take the highest block first, so a mound
peels rather than cascades. Rejected as more code for a less interesting result; a slumping mound
reads better than a peeling one. A GameTest pins the collapse behaviour so a later refactor cannot
quietly turn it into the other thing.

### Tiers, not an upgrade matrix (2026-09-03)

Ship a tier ladder matching the established sledgehammer pattern rather than a component-based upgrade
system. Radius and buffer climb together:

| Tier | Radius | Buffer |
|---|---|---|
| Copper | 2 | 4,000 FE |
| Iron | 3 | 8,000 FE |
| Diamond | 4 | 16,000 FE |
| Netherite | 5 | 24,000 FE |

The five upgrades the issue floated (radius, filter, void, capacity, network link) split cleanly:
radius and capacity are a **ladder** and become the tiers above; filter, void and network link are a
**matrix** and are deferred to a follow-up issue as data components. No new system ships here.

### Charging: a Charging Station block, no battery item (2026-09-03)

Nothing in this mod holds a charge as an item today and there is no `Capabilities.Energy.ITEM`
registration anywhere, so this needs two new things: the item-side energy capability, and somewhere to
charge it.

**The P2.6 battery mini-tree is NOT pulled forward.** It stays parked on the roadmap. What ships is
the smallest thing that makes a powered tool work: a **Charging Station** block that takes FE from
whatever generator is touching it and pushes it into the vacuum set on it.

**It is NOT in `#recompile:scrap_connectable`**, and a first draft of this page said it was. That tag
routes ITEMS between bins and barrels and nothing on the dock is routable; power moves here the way
it does everywhere in this mod, by adjacency ("No wires", the guidebook's own words). The Solar
Panel, the Burner and the Sequencer sit outside the tag for the same reason. Joining it would also
have obliged a guidebook edit, since `prose_lists_name_every_member_of_the_set_they_describe` holds
the Scrap Network entry to the tag's placeable members.

### The Charging Station gets no screen (assistant call, 2026-09-03)

All eight custom machine screens are recorded exceptions and a ninth would need a reversal. The
station does not get one:

- **Right-click holding a vacuum** parks it in the station, swapping out whatever was there.
- **Right-click empty-handed** takes the parked vacuum back.
- **Jade reports the charge**, reusing the generator provider's existing shape.

This is the Display Pedestal's interaction verbatim - place and take *is* the interaction - applied to
a block that happens to also push power.

---

## Sorting progress is discarded, deliberately

Each subclass carries its own `sorted` `IntegerProperty` - a palette flyweight, deliberately not a
BlockEntity. A block item does not carry blockstate, so vacuuming a half-sorted block **discards the
pulls already taken out of it**.

Accepted as-is. The two alternatives are both worse:

- **Refusing partially-sorted blocks** means a mound half-worked by hand becomes a patchwork the tool
  silently ignores, which reads as broken.
- **Carrying it in a data component** means a new component and a new place for it to desync, for a
  loss the player can avoid by vacuuming fresh blocks.

Revisit only if playtest says the loss stings.

---

## Automation policy

The Charging Station holds an item, so it takes a row in `automation_policy_spec.md`:
**no hoppers, no pipes, energy in only.** Same terms as the Display Pedestal (holds one item, placing
and taking is the interaction) plus the Sequencer's insert-only `Capabilities.Energy.BLOCK`.

---

## Cost calibration

The anchor is the Trommel: 40 ticks at 16 FE/tick is **640 FE to fully sort one garbage block**.
Collecting a block is far less work than sorting one, so the vacuum's per-block cost sits well under
that, and scales with the block's worth - a Compacted Bale is 8 rolls against a Trash Bag's 4, so
bulk-clearing the good stuff is not free.

---

## The Battery, and what powers the tier (owner, 2026-09-03)

The vacuum and the Charging Station are both built around a **Battery**, and the chain to one is the
mod's thesis stated as literally as it gets: **the garbage gives you dead cells, and the dead cell is
where the idea comes from.**

- **Depleted Battery** - found loose in `household_pulls`, at the Bulb's weight. Not a component and
  not spendable as one. It is a teardown input: cut one open at the workbench with a Scrap Knife for
  scrap metal, e-scrap and plastic.
- **The knowledge rides on that teardown**, declared as a top-level `teaches` entry rather than a
  `teaches: true` pool. That distinction is load-bearing: a teaching POOL grants the fragment for
  whatever item it drew, so saying it that way would have handed back a live battery for tearing up a
  dead one. Four of them completes the blueprint.
- **Battery** - manufactured, never found. The first component here that is **blueprint only**: the
  Pump, Motor and Bulb are salvage first and blueprint second, and this one has no salvage route at
  all, because a dead cell is not a live one. So it is deliberately absent from
  `ComponentBlueprintTests`' salvage-and-blueprint list, and covered instead by
  `a_blueprint_result_has_no_other_route`, which is the sweep that actually means something for it.

**This is also what settles the Water Tank problem** (#229: a component named for a capacity that holds
nothing reads as broken, reported by two playtesters ninety minutes apart). A *depleted* battery holding
nothing is not a bug, it is the noun. The live one is an ingredient you spend, which is what a crafting
component is here.

**The cell gates the whole powered tier at both ends**, since the Charging Station needs one as well as
the vacuum does: no cell, no charger, no vacuum, and no way to learn the cell but to take the rubbish
apart.

---

## How the animation is built

Slime Rancher's vacpack, in this game's idiom. Mining Gadgets was the reference for the block half
and deliberately not the mechanism: it swaps the target for a transient block entity whose
BlockEntityRenderer draws the original shrinking, and this mod records the Display Pedestal as its
ONE BlockEntityRenderer. Entity renderers are ordinary here (Roach, Pigeon), so:

- **The held item animates itself.** The client item definition switches models on `using_item`
  (the bow's mechanism): at rest the tier's body, in use the same body with `vacuum_intake` as a
  second layer - a six-frame animated texture of debris streaks converging on the nozzle, drawn by a
  texgen procedural style for the Trommel drum's reason (frames of one motion have to agree).
- **A taken block is an entity in flight.** `VacuumedBlockEntity` carries the block state, steers to
  the nozzle with a ramping speed and a small corkscrew, and `VacuumedBlockRenderer` draws it through
  vanilla's own `submitMovingBlock` path, scaled about its centre by distance to the mouth. The world
  block is gone the instant it is taken, so whatever stood on it falls while this flies.
- **Dust and air.** Client-side block particles stream from the piles in range into the nozzle;
  a puff at the mouth says the vacuum is on even over bare ground. Sound is vanilla's breeze-air
  loop, a pop per block taken and a pickup on arrival; a bespoke suction loop would need an `.ogg`
  the art pipeline does not produce, and is a follow-up if the vanilla sounds read wrong.
- **Delivery is a contract, not a hope.** The entity delivers to the owner's inventory (or drops at
  their feet when full) on arrival or at an 80-tick timeout, drops where it is if the owner is gone,
  and re-resolves its owner by UUID after a reload. `a_vacuumed_block_arrives_in_the_owners_inventory`
  runs the real flight.

---

## Done when

- The vacuum exists, holds a charge, drains it per block taken, and stops when flat.
- It takes only `SortableBlock` blocks, derived by type rather than by a list.
- The Charging Station charges it from any generator touching it, with no screen.
- GameTests cover the energy drain, the radius, the collapse behaviour, and that a stripped mound's
  `MoundGroundBlock` still regrows.
- A unit test covers the tier table - pure logic, no world needed.
- Jade reports stored FE on both the vacuum and the station.
- JEI shows the crafts.
- The station has a row in `automation_policy_spec.md`.
- A guidebook entry, since it deviates from vanilla.

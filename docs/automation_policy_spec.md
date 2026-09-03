# Automation policy - what pipes and hoppers can touch

**Status: locked 2026-07-31.** Written after four automation bugs shipped in a row, all with the same
root cause: **no block ever decided its automation policy.** It fell out of which capability happened to
have been registered for some unrelated reason. The Scrap Bin had one because it needed custom binding
logic; nothing else did; so nothing else was reachable by a pipe, and nobody noticed until Pipez was put
in a dev run.

This page is the decision, per block. **A new block that holds items adds a row here or it is not done.**

---

## The two doors, and why confusing them costs a day

An item-holding block can be reached two entirely separate ways. They do not consult each other, and a
block can be open to one and closed to the other without anything warning you.

| Door | Who uses it | What it reads |
|---|---|---|
| **Container path** | Vanilla hoppers, droppers, and anything using `Container` / `WorldlyContainer` | `getSlotsForFace`, `canPlaceItemThroughFace`, `canTakeItemThroughFace` |
| **Capability path** | Pipe and automation mods - Pipez, AE2, and effectively all of them | `Capabilities.Item.BLOCK`, resolved per `Direction` |

Three consequences that each cost real time:

- **Overriding `getSlotsForFace` does not stop a pipe.** It is not consulted on the capability path.
- **Registering no capability does not stop a hopper.** The hopper never looks there.
- **Refusing an insert does not stop a pipe from CONNECTING.** A pipe decides whether to attach based on
  whether a handler exists at all, not on whether it accepts anything. A block that exposes a handler
  which always says no renders as hooked-up and then does nothing, which reads as a broken machine
  rather than a manual one. **To keep pipes away, expose no handler.**
- **`WorldlyContainerWrapper` ignores sided access when the side is `null`.** Its `size()`
  short-circuits to `getContainerSize()` without ever calling `getSlotsForFace`, so a non-sided query
  hands out the whole container. Any test that walks `Direction.values()` misses this - there is no null
  entry in that array. **Always test the null side.**

## The default: parity with the vanilla equivalent

**A block that is a reskin of a vanilla block automates exactly like that vanilla block** (owner call,
2026-07-31). Not "similar to" - identical, on every face and on the non-sided query, in both directions.

`VanillaParityTests` enforces this by **comparison rather than by expected values**: it runs the same
operations against our block and its vanilla counterpart and requires matching answers. A hardcoded slot
mapping silently becomes wrong the next time Mojang changes one; a comparison keeps tracking.

That test also guards against its own worst failure mode - it first proves the *vanilla* reference
accepts items somewhere, because otherwise two mutually unreachable blocks compare `-1` to `-1` on every
face and the suite passes while asserting nothing.

**Departures from parity are allowed, but each one is a decision that gets a row and a reason.**

---

## The table

**The three reach-out machines are one contract, and a test enforces it.** The Separator, the Trommel
and the Pulverizer are all powered, GUI-less, expose no `Container` and no item capability, are fed by
reaching out, and route output to the Scrap Network first. `MachineParityTests` derives that list from
the REGISTRY - every multiblock core answering `Capabilities.Energy.BLOCK` - rather than from a list of
names, and asserts Jade coverage, network membership, the closed door on all of them at once, and that
each tells the player how to feed it. They were built by copying each other, which is how they came to
agree and also how they came apart: the Pulverizer shipped with zero Jade providers against the
Separator's four, and only an audit found it.


| Block | Backing type | Hoppers | Pipes | Policy |
|---|---|---|---|---|
| **Scrap Barrel** | `RandomizableContainerBlockEntity` | in + out | in + out | **Parity with `minecraft:barrel`.** Bulk overflow storage - the thing the network dumps into - so it is the member that should be freely automatable. |
| **Cupola Furnace** | `AbstractFurnaceBlockEntity` | sided, vanilla | sided, vanilla | **Parity with `minecraft:furnace`, with one departure.** Automation is half of what the upgrade buys. **Departure:** automation may only insert into the input slot what a smelting recipe consumes. Vanilla accepts anything there, which is harmless from a human hand and destructive from a pipe - a non-smeltable fills the slot and bricks the machine until cleared by hand (found in playtest, with the Cupola's own iron output looped back in). Restricted on `canPlaceItemThroughFace` only, so **placing by hand stays exactly vanilla**. Fuel and output slots untouched. |
| **Scrap Bin** | custom `ResourceHandler` | in + out | in + out | **Deliberate departure.** Gated to its bound material. Extraction added 2026-07-31, reversing P2.9's "hopper in, no out". Draining to empty **keeps the binding**, or a pipe would silently un-type a bin and the next unrelated insert would re-bind it, quietly scrambling a sorted wall. |
| **Burn Barrel** | `AbstractFurnaceBlockEntity` | **none** | **none** | **The exception, and it is load-bearing** (owner call, 2026-07-31). Manual-only is why the Cupola is worth building - "unlike the barrel it takes hoppers" is its stated selling point. Empty `getSlotsForFace` closes the Container path; registering **no capability at all** closes the pipe path *and* stops pipes from connecting. |
| **Tree Nursery** | `WorldlyContainer` | sided | sided, **null side refused** | **Reversed 2026-09-03 (owner): fully automatable.** Inputs from the sides, saplings from the bottom - the furnace convention, matching the Hydroponics Bay. **The top is unusable on a built nursery** and that is geometry, not policy: the blueprint puts a Solar Panel directly above the core in every orientation, and a dummy cell forwards no item capability. `canPlaceItem` routes each input by item and refuses the output slot; `canTakeItemThroughFace` allows only the output, so a hopper cannot drain the machine it is feeding. A **non-sided** capability query is handed no handler at all, the Burner Generator's pattern - see the changelog. Its **water tank** stays exposed as before. |
| **Hydroponics Bay** | `WorldlyContainer` | sided | sided, **null side refused** | **The automation tier** (#43): a machine that cannot be plumbed is not one, so it exposes items, fluid and energy. Input from the top and sides, harvest and byproduct from the bottom - both harvest slots pull from below, or a hopper under a potato farm drains the good potatoes and leaves the poisonous ones to stall it. `canTakeItemThroughFace` refuses `SLOT_INPUT`, and since 2026-09-03 a **non-sided** query is refused outright so that rule holds against every caller. **It had no row here until then**, which is this table's own rule going unenforced on the one machine built to be automated. |
| **Sequencer** | `WorldlyContainer` | **none** | **none** | Manual-only by design (#294): the "one precious thing at a time" machine, not an automation-tier one. Amber arrives at about 1 in 700 pulls, so a pipe would be feeding it nothing most of the time. **Energy IS exposed** (`Capabilities.Energy.BLOCK`, insert-only), because it has to be charged. It shipped closed on the capability door and OPEN on the Container one - a plain `Container`, which `HopperBlockEntity.getContainerAt` takes directly - so a hopper underneath pulled the amber out mid-read. Caught in review, fixed to `WorldlyContainer` with no slots on any face, the Tree Nursery's shape exactly. |
| **Rain Collector** | plain `BlockEntity` | n/a | fluid only | Its tank is the point; it holds no items. |
| **Display Pedestal** | plain `BlockEntity` | **none** | **none** | Holds one item and is **never hopper-fed** by design - placing and taking is the interaction. |
| **Charging Station** | plain `BlockEntity` | **none** | **none** | The pedestal's terms, plus power (#336): holds one Garbage Vacuum, set down and picked up by hand, no `Container` and no item capability so neither a hopper nor a pipe can lift the tool off the dock. **Energy IS exposed** (`Capabilities.Energy.BLOCK`, insert-only), the Sequencer's shape, because it has to be fed. It charges the docked item through `Capabilities.Energy.ITEM` - the first item capability in the mod - so the door any other charger would use is the door this block proves every tick. |
| **Compost Heap**, **Recompile Workbench**, **Scrap Crafting Table** | plain `BlockEntity` | n/a | n/a | Not `Container`s. Nothing to expose. |
| **Separator** | *no container at all* | **none** | **none** | Closed on both doors, and it **joined `#scrap_connectable` anyway** (2026-08-03) without opening either. It is a SOURCE: it pushes separated material into the cluster and can never receive, because routing only ever lands in a Scrap Bin or the Scrap Barrel by block id and this machine has no `Container` to land in. Its formed cells are RELAYs, in the tag only so a bin against any face of the machine joins the cluster. **Being reachable and being writable are different questions** - the machine reaches out at both ends (it swallows what lands in its bay, drains a container on it, and pushes into the network or its chute) and is still reachable-into by nothing. |
| **Trommel** | *no container at all* | **none** | **none** | Same terms as the Separator, and for the same reason. It swallows loose items along the drum, drains a container parked on it, and discharges off the END of the drum at drum height - into a container if one is there, thrown clear if not. A SOURCE in the network; its formed cells are RELAYs. Took automated sorting off the Separator in #187. |
| **Pulverizer** | *no container at all* | **none** | **none** | Same terms again. Fed from the ROOF by gravity, which is what a hammer mill does, so the roof carries a painted hatch - it is the only machine of the three whose opening is not visible from its shape, and a sealed box has to say where the input is. Powder leaves by the front. A SOURCE; its cells are RELAYs. |
| **Sorting Tarp** | *no block entity* | n/a | n/a | Stateless by identity - no input slot, no output buffer. |
| **Solar Panel** | plain `BlockEntity` | n/a | **energy out** | Holds no items, so no item capability. Exposes `Capabilities.Energy.BLOCK` and pushes to neighbours each tick, so a generator against a machine works with no pipe mod installed at all. |
| **Burner Generator** | `WorldlyContainer` (5 fuel slots) | **fuel in, nothing out** | **fuel in, nothing out**, plus **energy out** | Has a bespoke screen for its power meter (see CLAUDE.md on the three exceptions); the automation rules below are unaffected by that. `canPlaceItem` is "is this fuel", so neither a player nor a pipe can park something unburnable in the buffer, and `canTakeItemThroughFace` refuses every face - a pipe pulling fuel back out of the generator it just filled is nobody's intent. **Null side returns no handler**, the Burn Barrel's lesson: `WorldlyContainerWrapper` skips `getSlotsForFace` entirely on a non-sided query. |

---

## What is enforced, and what is not

`every_container_block_declares_its_automation` makes this table executable: every block whose
BlockEntity is a `Container` must expose `Capabilities.Item.BLOCK`, or appear in that test's
`NO_ITEM_CAPABILITY` list with a reason. It exists because the Cupola shipped advertising automation
that no capability-based pipe could reach - hoppers use the vanilla `Container` path and worked, so
nothing looked wrong.

**Still not enforced, and worth knowing:** the test checks that a capability *exists*, not that its
sided behaviour matches the row above. Direction-by-direction rules are covered per block
(`burn_barrel_refuses_pipe_insertion`, `VanillaParityTests`) rather than swept, so a new block can
satisfy the sweep and still get its faces wrong.

## Adding a block that holds items

1. **Is it a reskin of a vanilla block?** Then parity is the answer. Register the matching wrapper
   (`VanillaContainerWrapper.of` for a plain `Container`, `new WorldlyContainerWrapper(be, side)` for a
   sided one) and add it to `VanillaParityTests`.
2. **Is it meant to refuse automation?** Then close **both** doors: empty `getSlotsForFace` plus the two
   face checks returning false, **and** register no capability. Half-closing it is what shipped four
   times.
3. **Is it something else entirely?** Write the handler, and state in its javadoc what it accepts and
   what it refuses, in those words.
4. **Add a row to the table above**, with the reason. A policy that lives only in code is how this
   document came to be necessary.
5. **Test the null side.** Not just `Direction.values()`.

## Changelog

- **2026-09-03** - The **Tree Nursery** was opened to hoppers and pipes, reversing its manual-only row.
  It came from a playtester asking whether items could be supplied that way (`#trashlands-playtest`);
  the honest answer was no, by a decision nothing in the game stated. A nursery is the only source of
  trees in this world, so "you must stand at it" made a tree farm impossible rather than manual.

  **A THIRD DOOR THIS TABLE HAD NEVER NAMED: the null side.** NeoForge's
  `WorldlyContainerWrapper.extract` is guarded by `side != null &&`, so a capability query with **no**
  side skips `canTakeItemThroughFace` entirely and can pull any slot, inputs included. A machine that
  states "automation may not take my inputs" does not actually state it unless the non-sided caller is
  handled too.

  **Closed here by handing a non-sided caller no handler at all**
  (`side == null ? null : new WorldlyContainerWrapper(be, side)`), which is the pattern the **Burner
  Generator** has used since #72. It was nearly written up as an accepted hole on the grounds that
  closing it meant wrapping every sided handler in the mod; that was wrong, and the counter-example was
  eighty lines below in the same method. `a_non_sided_pipe_gets_no_handler_on_the_nursery` pins both
  halves - nothing for a null side, a working handler for a real one.

  **The Hydroponics Bay had it too, and is fixed as well** (owner call, same day). It had declared
  `slot != SLOT_INPUT` since #43 and was simply not enforcing it against a non-sided caller, so a pipe
  could pull the seed back out of a bay it was feeding. Fixing it also surfaced that the Bay had **no
  row in the table above at all** - this page's central rule, unenforced on the one machine built to be
  automated. It has one now.

  **It costs null-side INSERTION too.** `insert` has no `side != null` guard, so a non-sided caller
  could legitimately feed both machines under their own rules and now cannot reach them at all.
  Accepted, to keep them identical to the Burner Generator rather than mint a bespoke insert-only
  wrapper; a pipe attached to a face is unaffected, and that is what almost all of them are.

  **The FURNACES keep the plain wrapper, and that is not an oversight.** The **Cupola** is held to
  vanilla furnace parity: a vanilla furnace *does* answer a non-sided query, and `VanillaParityTests`
  compares every face **plus** the null one against `Blocks.FURNACE`, so guarding it would break the
  parity it exists to keep. The **Slag Furnace** and **Sintering Kiln** are furnace subclasses meant to
  behave the same way, but **nothing pins them** - neither appears in `VanillaParityTests`, which
  covers only the Scrap Barrel and the Cupola. That gap is #341; this page said all three were parity
  tested until review checked, which is the "reads as complete" failure it exists to prevent.

  So the rule is not "always refuse the null side" - it is **a machine that restricts extraction by
  face must refuse it, and a parity block must match vanilla instead.**

- **2026-09-03** - The Charging Station (#336) added a row: manual-only on both item doors like the
  pedestal, energy in only like the Sequencer. Worth a line because it is the first block here whose
  job is to write into an ITEM's capability - `Capabilities.Energy.ITEM` on the docked Garbage
  Vacuum - which is a third door this table had never needed a column for. It is closed to the
  world (the stack is only reachable by hand) and open to the block, and
  `every_vacuum_tier_exposes_the_item_energy_capability` pins that every tier answers it.

- **2026-08-29** - The Sequencer (#294) added a row, and the reason it is worth reading is that it was
  wrong when it shipped. Declaring no item capability closes the pipe door and leaves the Container
  door wide open, because a hopper never consults the capability. The guard that polices this table in
  executable form (`every_container_block_declares_its_automation`) only checked the capability half,
  which is why its own javadoc said "hoppers travel the vanilla Container path and never consult the
  capability" while testing the other one. It now proves every block listed here as manual-only is shut
  to hoppers as well.

- **2026-07-31** - Power tier (#72) added two energy-only rows. Neither generator holds items, so neither
  exposes an item capability; the Burner is fed by hand.
- **2026-07-31** - Created. Captures the parity rule, the Burn Barrel exception, the Scrap Bin's P2.9
  reversal, and the two-door model. Written after the Cupola was found unreachable to every pipe mod
  despite advertising automation, and the Burn Barrel was found accepting pipes despite advertising the
  opposite.

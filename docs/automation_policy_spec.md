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

| Block | Backing type | Hoppers | Pipes | Policy |
|---|---|---|---|---|
| **Scrap Barrel** | `RandomizableContainerBlockEntity` | in + out | in + out | **Parity with `minecraft:barrel`.** Bulk overflow storage - the thing the network dumps into - so it is the member that should be freely automatable. |
| **Cupola Furnace** | `AbstractFurnaceBlockEntity` | sided, vanilla | sided, vanilla | **Parity with `minecraft:furnace`, with one departure.** Automation is half of what the upgrade buys. **Departure:** automation may only insert into the input slot what a smelting recipe consumes. Vanilla accepts anything there, which is harmless from a human hand and destructive from a pipe - a non-smeltable fills the slot and bricks the machine until cleared by hand (found in playtest, with the Cupola's own iron output looped back in). Restricted on `canPlaceItemThroughFace` only, so **placing by hand stays exactly vanilla**. Fuel and output slots untouched. |
| **Scrap Bin** | custom `ResourceHandler` | in + out | in + out | **Deliberate departure.** Gated to its bound material. Extraction added 2026-07-31, reversing P2.9's "hopper in, no out". Draining to empty **keeps the binding**, or a pipe would silently un-type a bin and the next unrelated insert would re-bind it, quietly scrambling a sorted wall. |
| **Burn Barrel** | `AbstractFurnaceBlockEntity` | **none** | **none** | **The exception, and it is load-bearing** (owner call, 2026-07-31). Manual-only is why the Cupola is worth building - "unlike the barrel it takes hoppers" is its stated selling point. Empty `getSlotsForFace` closes the Container path; registering **no capability at all** closes the pipe path *and* stops pipes from connecting. |
| **Tree Nursery** | `WorldlyContainer` | **none** | **none** | Items are manual by design; only its **water tank** is exposed (`Capabilities.Fluid.BLOCK`), so a pipe from a Rain Collector can fill it. Closed on both doors, correctly. |
| **Rain Collector** | plain `BlockEntity` | n/a | fluid only | Its tank is the point; it holds no items. |
| **Display Pedestal** | plain `BlockEntity` | **none** | **none** | Holds one item and is **never hopper-fed** by design - placing and taking is the interaction. |
| **Compost Heap**, **Recompile Workbench**, **Scrap Crafting Table** | plain `BlockEntity` | n/a | n/a | Not `Container`s. Nothing to expose. |
| **Sorting Tarp** | *no block entity* | n/a | n/a | Stateless by identity - no input slot, no output buffer. |

---

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

- **2026-07-31** - Created. Captures the parity rule, the Burn Barrel exception, the Scrap Bin's P2.9
  reversal, and the two-door model. Written after the Cupola was found unreachable to every pipe mod
  despite advertising automation, and the Burn Barrel was found accepting pipes despite advertising the
  opposite.

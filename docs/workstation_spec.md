# The Scrap Workstation - spec

**Written 2026-07-24. Design, not built.** A connected cluster of the scrap-interaction blocks - the
storage, the smelter, the teardown table, the sorter, the crafting table - that assembles from a
template and then behaves as **one system**: junk moves between the members the way a player expects,
with no belts and no external mod. Design decisions here are locked in the 2026-07-24 session; record
them in `../trashlands/docs/design_decisions.md` as **P2.10** before building.

## Why - and the rule this deliberately revises

The mod's logistics stance is **"Recompile converts, Create moves"** (P2.3): item transport is
Create's job, which is why the Sorting Tarp drops onto the floor (P1.3), the Burn Barrel and Workbench
expose no item-handler capability, and the Scrap Bin is hopper-in-no-out (P2.9).

**That rule assumed Create is in the lineup. Create is not ported to 26.1.** So the rule currently
leaves a *logistics void* - the mod defers all item movement to a mod that is not there, and the player
is left hand-shuffling stacks between blocks with zero automation. The Workstation fills that void, and
it does so in a way that honors the harder constraint (**"Recompile machines never REQUIRE Create"**):

- It is **local and bounded** - a connected cluster you reach across, not world-spanning transport.
- It is **standalone** - needs no other mod, and does not try to *be* Create.
- When Create does arrive it **coexists**: Create is belt transport between distant machines; the
  Workstation is a kitchen where everything is in arm's reach. Different scales, no conflict.

So this is not so much reversing the seam rule as **owning the half of it that currently has no owner**
- and the "everything flows within one workstation" cluster is genuine novelty; other mods go either
full AE2/RS network or fully isolated blocks, not this.

**This revises P1.3, P2.3, and the Workbench/Burn Barrel no-item-handler notes.** The revision is
deliberate and its rationale (Create's absence) is recorded so the *why* survives.

## The blueprint (strawman)

The scrap-interaction blocks only - **no Rain Collector, no Grass Spreader** (those are the water /
reclamation axis, not scrap).

**Front counter (hand level) - the five workstations you stand at:**
Scrap Crafting Table (craft) · Recompile Workbench (break down) · Sorting Tarp (sort) · Burn Barrel
(smelt) · Scrap Barrel (bulk / overflow storage).

**The bins - seven, split six-and-one:** six on the high back shelf, and `junk` (the bulk filler you
dump most) at hand level in the counter, so it is in easiest reach. Each shelf bin sits on a **Machine
Frame** that holds it up. The file-all fills the junk bin the same as any other - position does not
matter to the network.

```
 SIDE VIEW (player at the front, left):

  y=1                    [ BIN ]        <- shelf bins, high, shifted back one (z=1)
  y=0   [ WORKSTATION ][ FRAME ]        <- counter at hand level; frame under the bin
         z=0            z=1

 TOP VIEW (player approaches from the bottom):

  back  z=1:  [bin][bin][bin][bin][bin][bin]                  y=1  (6 material bins, on frames at y=0)
              [frm][frm][frm][frm][frm][frm]                  y=0
  front z=0:  [craft][wbench][sort][CORE][burn][barrel][junk] y=0  (5 workstations + core + junk bin)
```

Footprint ~7 wide x 2 deep x 2 tall, ~19 blocks. The core sits centre-counter, where you stand to
build and interact.

**Two blueprint specifics still open (finalize here before building):**

1. **The core's exact cell** - centre-counter as drawn is the default; tucked at the back is the
   alternative.
2. **Bins bind at runtime.** The blueprint requires "a Scrap Bin here," not "the scrap-metal bin" -
   binding happens when you deposit. So the structure has generic bin slots and you bind each by use.

## Architecture - reuse the core, keep every block

**It is a template + network, not a machine that merges into one object.** There is deliberately **no
bespoke 3D model and no formed appearance** - and that is the whole reason this shape is right.

Reuse `MultiblockCoreBlock` (blueprint validation + auto-assemble-from-inventory), with one twist:

- **`formed` == `component` for every cell.** The framework already allows a cell whose formed block
  is itself (the Grass Spreader's Solar Panel does exactly this). Apply it to every cell, and `form()`
  swaps nothing - each block stays itself, keeping its own model, its own interface, its own behavior.
- The core's **`onFormed` does the new work: link the members into one system** (below). The members
  keep functioning as standalone blocks; being formed just wires them to the core's shared storage.
- Members find their core the way `MultiblockDummyBlock.findCore` does - a bounded scan, cheap because
  the blueprint is a fixed small size.

So the framework covers the *shape*; the only genuinely new logic is the *network*.

**The core is a new block: the Workstation Core.** It is not one of the functional blocks (those may
not be a machine's core - the no-nested-cores rule), so it is a dedicated controller you place and
build around.

## Cross-functional behavior - what flows where

Once formed, the core mediates a shared view of the connected storage (the bins + the barrel). Each
member reads or writes it. Rough order of increasing difficulty (build the cheap ones first):

1. **Sorting Tarp -> bins**, two ways. Its normal right-click sifts *garbage* into materials, which
   now land in the matching connected bin (overflow / the non-binnable remainder to the barrel)
   instead of dropping on the floor. And **shift-right-click files your loose *materials*** - one
   action walks the player inventory and sends every `#binnable` stack to its matching bin, overflow
   or bin-less scrap to the barrel. This is the workstation's payoff QoL: dump a whole scavenging haul
   into storage with one click. *Both reverse P1.3's world-drop - the deliberate revision, only while
   part of a workstation; a standalone tarp still drops.*
   - **Open:** does the file-all **auto-bind an empty bin** for a material it has no bin for (lean:
     yes, the first empty bin per material), or only fill already-bound bins and send the rest to the
     barrel (respects bins set aside on purpose)?
2. **Recompile Workbench -> storage.** Teardown outputs route into the connected bins / barrel instead
   of popping into the world.
3. **Burn Barrel -> storage.** Smelt outputs move to the connected storage on completion. This is the
   most automation-like piece (time-based), so it is the one to weigh hardest against the seam rule.
4. **Scrap Crafting Table reads storage.** Crafting can draw ingredients from the connected bins /
   barrel, not just the player inventory - the "crafting station" pattern. **The capstone and the most
   work:** it means combining the player inventory with the connected storage as the crafting menu's
   ingredient source. Feeds directly into the **P1.4 knowledge review** - craft-from-storage is exactly
   the autocrafting the recipe-lock cannot survive, so building this informs that open decision.

**Scope guardrails** (keep it from sprawling into AE2): bounded to the connected cluster (no
world-spanning routing), standalone-first (never needs Create), and members keep their manual
interfaces - the workstation adds reach, it does not remove the hand.

## Placement outline - a framework addition (Powah-style)

The framework has no build-time preview, and a ~20-block structure needs one. **Put it in
`MultiblockCoreBlock` so every multiblock gets it** - the Grass Spreader and Rain Collector included,
retroactively.

- **Hold the core item** -> a translucent ghost / wireframe of the blueprint renders at the aimed
  position, each cell boxed.
- **Green where the cell is clear, red where it is blocked**, so conflicts are visible before you
  place and you know exactly what to clear.
- Reads the blueprint straight off the core block (already the single source of truth for the shape),
  so it costs no new data - just a client render.

This is a **held-item world preview** (like a schematic mod's ghost), **not a BlockEntityRenderer** -
it never renders a placed block, so it does not touch the no-BER rule. It is also what makes the build
tractable: it guides manual placement, and it turns auto-assemble from "silently fails if something is
in the way" into "you saw the red cell coming."

## Build UX

Two paths, both supported by the framework:

- **Auto-assemble**: place the core with all ~20 blocks in your inventory and it places them into the
  blueprint (all-or-nothing - if you cannot supply every block, it places none). The outline previews
  where they land and flags conflicts.
- **Manual**: build the shape by hand into the outline; the core forms when the blueprint validates on
  a neighbour change.

## Blocks and registry

| Registry name | Type | Notes |
|---|---|---|
| `workstation_core` | new `MultiblockCoreBlock` | Holds `FORMED`, the blueprint, and the shared-storage network. No formed model. |

Everything else is an existing block used as an unchanged component (Scrap Crafting Table, Recompile
Workbench, Sorting Tarp, Burn Barrel, Scrap Barrel, Scrap Bin, Machine Frame), each a cell whose formed
block equals its component.

## Tests

- **GameTests**: the blueprint validates and forms (formed == component, no blocks replaced);
  auto-assemble is all-or-nothing; a member finds its core; each routing behavior lands its output in
  the right storage (sorter -> bin, teardown -> bin/barrel, smelt -> storage); crafting reads connected
  storage; disband unlinks cleanly and every member survives (nothing consumed). Negative-control the
  routing (a standalone tarp still drops on the floor; only a workstation tarp fills bins).
- **Unit tests**: the blueprint's cell math and any conflict/overlap detection for the outline are
  pure logic - unit-test them (the outline's conflict check especially, since it is client-only and
  not GameTest-able otherwise).

## Open questions

1. The three blueprint specifics above (bin count / width, core position, runtime binding confirmation).
2. **The Burn Barrel routing** (item 3) is the one piece that is genuinely time-based automation -
   confirm it belongs, or leave the smelter manual-out and route only the manual-action blocks.
3. **The crafting-from-storage capstone** and its P1.4 interaction - may be phased in after the routing
   pieces, and may wait on the knowledge review.
4. All numbers and the exact recipe/cost of the Workstation Core. Pre-beta balance pass.

## Verification

1. `JAVA_HOME="/c/Program Files/Java/jdk-25" ./gradlew build` - redirect to a file, check `$?`.
2. `runGameTestServer` - full suite; reported total is ours **plus one**.
3. `runClient` - place the core, watch the outline, auto-assemble, then confirm the four flows by hand.
4. Code review before the PR, not after.

# The Scrap Workstation - spec

**Written 2026-07-24. Design, not built; decisions locked 2026-07-24.** A connected cluster of the
scrap-interaction blocks - the storage, the smelter, the teardown table, the sorter, the crafting
table - that assembles from a template and then behaves as **one system**: junk moves between the
members the way a player expects, with no belts and no external mod. Recorded in
`../trashlands/docs/design_decisions.md` as **P2.10**.

## Why - and the rule this deliberately revises

The mod's logistics stance is **"Recompile converts, Create moves"** (P2.3): item transport is
Create's job, which is why the Sorting Tarp drops onto the floor (P1.3), the Burn Barrel and Workbench
expose no item-handler capability, and the Scrap Bin is hopper-in-no-out (P2.9).

**That rule assumed Create is in the lineup. Create is not ported to 26.1.** So the rule currently
leaves a *logistics void* - the mod defers all item movement to a mod that is not there, and the player
is left hand-shuffling stacks between blocks with zero automation. The Workstation fills that void, and
it honors the harder constraint (**"Recompile machines never REQUIRE Create"**):

- It is **local and bounded** - a connected cluster you reach across, not world-spanning transport.
- It is **standalone** - needs no other mod, and does not try to *be* Create.
- When Create arrives it **coexists**: Create is belt transport between distant machines; the
  Workstation is a kitchen where everything is in arm's reach. Different scales, no conflict.

**This revises P1.3, P2.3, and the Workbench/Burn Barrel no-item-handler notes** - only *inside a
formed workstation*. A standalone Sorting Tarp still drops on the floor; a standalone Workbench still
pops outputs into the world. The revision is scoped to the cluster, and its rationale (Create's
absence) is recorded so the *why* survives.

## The blueprint (locked)

The scrap-interaction blocks only - **no Rain Collector, no Grass Spreader** (those are the water /
reclamation axis, not scrap). A compact **6 wide × 2 deep × 2 tall** box, 18 blocks.

- **Front counter (`z=0`, `y=0`), 6 wide, hand level:** Scrap Crafting Table, Recompile Workbench,
  Sorting Tarp, Burn Barrel, Scrap Barrel, and the **junk bin** (bulk filler at hand level).
- **Back supports (`z=1`, `y=0`), 6 wide:** the **Workstation Core** and five **Machine Frames**.
- **Back shelf (`z=1`, `y=1`):** six material bins, one sitting on each support - **including one on
  the core itself** (the core doubles as a shelf leg; it is not a standalone controller).
- **Above the counter (`z=0`, `y=1`): air.** The bins are only over the back row.

```
 TOP VIEW:

  z=1 (back):   [core][frame][frame][frame][frame][frame]   y=0  ->  6 bins on top (y=1)
  z=0 (front):  [craft][wbench][sort][burn][barrel][junk]   y=0  ->  air on top (y=1)

 SIDE VIEW (one column):

  y=1:  [air] [bin]             <- bins sit on the back row only
  y=0:  [work][frame-or-core]   <- counter in front, frame/core support behind
        z=0   z=1
```

18 blocks: 7 bins (6 shelf + junk on the counter), 5 workstations, 5 frames, 1 core. Bins **bind at
runtime** - the blueprint requires "a Scrap Bin here," not a specific material; you bind each by use,
and the file-all can bind them for you (below).

## Architecture - reuse the core, keep every block

**Template + network, not a machine that merges into one object.** There is deliberately **no bespoke
3D model and no formed appearance** - every block keeps its own model, interface, and behavior. That is
the whole reason this shape is right.

Reuse `MultiblockCoreBlock` (blueprint validation + auto-assemble-from-inventory) with **`formed` ==
`component` for every cell** - the Grass Spreader's Solar Panel already does this for one cell; the
Workstation does it for all of them, so `form()` swaps nothing.

**The one required framework change: `form()` must not re-set a cell whose block already matches.**
Today `form()` calls `setBlock(cell.formed().defaultBlockState())` on *every* cell unconditionally.
That is fine when a component is *replaced* by a different formed block (the Grass Spreader), but for a
cell where `formed == component` it re-sets the block that is already there. MC keeps a BlockEntity
across a same-block `setBlock`, so the **contents survive** - but the derived **blockstate snaps to
default**: a bound bin's `content`/`fill` reset (it renders empty while its BE still holds the scrap),
the barrel's open state and the furnace's lit state reset, and a wave of redundant block updates
fires. So the effect is a **visual desync plus churn**, not data loss - still wrong, still worth
fixing. The fix is small and safe for both machines:

```java
BlockPos at = cell.at(core);
if (level.getBlockState(at).is(cell.formed())) continue;   // already the right block, leave it alone
// ... else set the formed block as before
```

Grass Spreader: `water_tank != frame` → replaced (unchanged behavior, its own tests still pass).
Workstation: `barrel == barrel` → skipped, blockstate preserved. **Done and tested 2026-07-24** -
`MultiblockTests` pins both sides (blockstate survives when formed == component; a differing cell is
still replaced), negative-controlled so the preserve test bites without the guard.

## Network architecture - the shared-storage system

Once formed, the core is the hub; the members read and write its view of the connected storage (the
seven bins + the Scrap Barrel).

- **Membership.** The core knows its cells from the blueprint. On `onFormed` it records the member
  positions; on `onDisbanded` it clears them.
- **Member → core lookup.** A functional member (tarp, workbench, burn barrel, crafting table) finds
  its core by a bounded scan for a **formed** Workstation Core whose blueprint covers this position -
  the `MultiblockDummyBlock.findCore` pattern, but the member is not a dummy, it keeps functioning.
  No core, or an unformed one → the member behaves **standalone** (the tarp drops on the floor, the
  workbench pops outputs into the world). The linking is entirely gated on `FORMED`.
- **The core's storage API** (server-side), the single place routing rules live:
  - `insert(ItemStack, boolean autoBind)` → matching **bound** bin first; then, if `autoBind`, the
    first **empty** bin binds to the material and takes it; then the **Scrap Barrel**; the remainder is
    returned. `autoBind` is `true` only for the file-all, `false` for machine outputs (so a teardown
    or smelt never surprise-binds a bin - it fills bound bins, else the barrel).
  - `ingredientView()` → a combined read-only view over the bins + barrel, for the crafting table to
    source from. Player-scoped access, so a recipe-lock (P1.4, if it ships) still gates it - this is
    manual crafting, not an automated crafter.

## Cross-functional behavior - what flows where

All active only while the core is FORMED. Build the cheap ones first; the crafting table is the
capstone.

1. **Sorting Tarp -> bins, two ways.**
   - **Right-click** sifts *garbage* into materials, which now land via `insert(stack, autoBind=false)`
     (matching bound bin, else barrel) instead of dropping on the floor.
   - **Shift-right-click files your loose *materials*** - the payoff QoL. One action walks the player
     inventory and sends every `#binnable` stack through `insert(stack, autoBind=true)`, so it fills
     matching bins and **binds an empty bin** for any material that has none yet. Dump a whole
     scavenging haul into storage with one click.
2. **Recompile Workbench -> storage.** Teardown outputs route through `insert(..., autoBind=false)`
   into the bins / barrel instead of popping into the world.
3. **Burn Barrel -> storage.** On smelt completion the output moves through `insert(..., false)` to the
   connected storage. **This is the one genuinely time-based flow** (decided 2026-07-24: auto-route,
   accepting that it is the piece furthest from the manual-first line - the Burn Barrel gains an
   output route only while part of a workstation).
4. **Scrap Crafting Table reads storage (v1).** Crafting draws ingredients from the player inventory
   **plus the bins plus the Scrap Barrel** via `ingredientView()`. Still manual and player-scoped, so
   the P1.4 recipe-lock (if it ships) applies - this is the "crafting station" pattern, not an
   autocrafter. The build work is a crafting menu that presents the combined inventory as its source.

**Scope guardrails:** bounded to the connected cluster (no world-spanning routing), standalone-first
(never needs Create), members keep their manual interfaces. The workstation adds reach, not automation
of the hand - except the Burn Barrel's output, the one deliberate exception.

## Placement outline - a framework addition (Powah-style)

The framework has no build-time preview, and a 19-block structure needs one. **Put it in
`MultiblockCoreBlock` so every multiblock gets it** - Grass Spreader and Rain Collector included,
retroactively.

- **Hold the core item** → a translucent ghost / wireframe of the blueprint renders at the aimed
  position, each cell boxed, **green where clear, red where blocked**.
- Reads the blueprint straight off the core block (already the single source of truth), so it costs no
  new data - just a client render.

A **held-item world preview** (like a schematic mod's ghost), **not a BlockEntityRenderer** - it never
renders a placed block, so it does not touch the no-BER rule. It is also what makes the build
tractable: it guides manual placement, and turns auto-assemble from "silently fails if something is in
the way" into "you saw the red cell coming."

## Build UX

- **Auto-assemble**: place the core with all 17 other blocks in your inventory and it places them into
  the blueprint (all-or-nothing - if you cannot supply every block, it places none). The outline
  previews placement and flags conflicts.
- **Manual**: build the shape into the outline; the core forms when the blueprint validates on a
  neighbour change. Thanks to the `form()` fix, functional blocks you placed with contents keep them.

## Blocks and registry

| Registry name | Type | Notes |
|---|---|---|
| `workstation_core` | new `MultiblockCoreBlock` | Holds `FORMED`, the blueprint, the member list, and the storage API. No formed model. Placed at the back-centre; you build around it. |

Everything else is an existing block used as an unchanged component (Scrap Crafting Table, Recompile
Workbench, Sorting Tarp, Burn Barrel, Scrap Barrel, Scrap Bin, Machine Frame), each a cell whose formed
block equals its component. A recipe for the core (scrap plating + a bit of e-scrap, first-pass) is a
balance-pass number.

## Tests

- **GameTests:**
  - The blueprint validates and forms with **no block replaced** and **no state lost** (place a barrel
    with contents / a bound bin, form, assert contents + binding survive) - the `form()` fix, and its
    negative control: without the fix, forming clears them.
  - Auto-assemble is all-or-nothing; a member finds its formed core; disband unlinks and every member
    survives (nothing consumed).
  - Each routing flow lands its output correctly: sift → bound bin, sift → barrel when no bin,
    teardown → storage, smelt → storage. Negative-control statelessness: a **standalone** tarp still
    drops on the floor (only a workstation tarp routes).
  - `insert` rules: matching bound bin first, `autoBind` binds an empty bin only for the file-all,
    overflow to the barrel, remainder returned when full.
  - The file-all: shift-right-click clears every binnable stack from the inventory into storage,
    auto-binding empties.
  - Craft-from-storage: a recipe craftable only with an ingredient that lives in a connected bin / the
    barrel succeeds while formed, and fails (standalone) when it is not.
- **Unit tests:** the blueprint's cell math, and the outline's conflict/overlap detection (client-only,
  so not GameTest-able) - both pure logic.

## Open questions

1. **All numbers**: the Workstation Core recipe/cost, and any per-flow tuning. Pre-beta balance pass.
2. **Outline render details** (color, opacity, whether it also shows on the placed formed core for a
   moment) - a `runClient` polish pass.
3. **Overflow when all storage is full**: machine outputs and the file-all return a remainder; confirm
   the fallback is "leave it in hand / drop at the source" rather than the void (default: never void).

## Verification

1. `JAVA_HOME="/c/Program Files/Java/jdk-25" ./gradlew build` - redirect to a file, check `$?`.
2. `runGameTestServer` - full suite; reported total is ours **plus one**.
3. `runClient` - place the core, watch the outline, auto-assemble, then confirm the four flows + the
   file-all by hand.
4. Code review before the PR, not after.

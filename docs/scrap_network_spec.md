# The Scrap Network - spec

**Written 2026-07-24. Supersedes the Scrap Workstation multiblock spec (same P2.10 slot).** Scrap
blocks placed touching each other form one connected cluster, and junk routes between them - no
controller, no blueprint, no saved structure. Recorded in `../trashlands/docs/design_decisions.md` as
**P2.10**.

## Why - and the rule this revises

The mod's logistics stance is **"Recompile converts, Create moves"** (P2.3): item transport is
Create's job, which is why the Sorting Tarp drops onto the floor (P1.3), the Burn Barrel and Workbench
expose no item-handler capability, and the Scrap Bin is hopper-in-no-out (P2.9).

**That rule assumed Create is in the lineup. Create is not ported to 26.1**, which leaves a *logistics
void*: the mod defers all item movement to a mod that is not there, and the player hand-shuffles stacks
with zero automation. The Scrap Network fills that void while honoring the harder constraint
(**"Recompile machines never REQUIRE Create"**): it is **local and bounded** (a connected cluster you
reach across, not world-spanning transport), **standalone** (needs no other mod), and **coexists** with
Create when it arrives (Create is belt transport between distant machines; the network is a kitchen
where everything is in arm's reach).

**This revises P1.3, P2.3, and the Workbench/Burn Barrel no-item-handler notes** - only *inside a
connected network*. A standalone Sorting Tarp still drops on the floor; a standalone Workbench still
pops outputs into the world. The revision is scoped to the cluster.

### Why this shape, not a multiblock (the reversal)

P2.10 was first built as a rigid multiblock: a `WorkstationCoreBlock` you place, a fixed 6x2x2 / 18
block blueprint, auto-assemble, form/disband, and facing-aware placement so the fixed shape could
rotate. **That was the wrong model** - it dictated one prescribed bench when the point is for players
to arrange their scrap blocks however they like. Adjacency is the mechanic that delivers that: place
the blocks touching and they wire up; move one away and it drops off. The core, the blueprint, the
auto-assemble, and the facing machinery are all gone.

## How it connects (locked)

- **Auto-adjacency, flood-fill.** Any scrap blocks sharing a face are one network. No tool, no
  controller, no link step. Face adjacency only (the six orthogonal neighbours).
- **Only scrap blocks conduct.** The member set is a block tag, `#recompile:scrap_connectable`: **Scrap
  Bin, Scrap Barrel, Sorting Tarp, Recompile Workbench, Burn Barrel, Scrap Crafting Table**. All six
  conduct; a Machine Frame does not (it was only the old blueprint's shelf support, and is now
  unused). Open by design - a pack adds a modded scrap block to the tag without a mod release.
- **No saved state, no BlockEntity for the structure.** Each interaction floods outward from the acting
  block and reads the members live. Clusters are small and interactions are user-paced, so a fresh
  flood per call is cheap. A `MAX_MEMBERS` cap (256) is a runaway backstop, logged if hit. This keeps
  the framework's no-BE-for-structure line without any of the multiblock machinery.

## Routing - `ScrapNetwork` (the engine)

`content/block/ScrapNetwork.java`, server-side, the single place the rules live:

- `collect(Level, BlockPos start)` - bounded BFS over `#scrap_connectable`, returns the connected
  member positions.
- `insertFromMember(Level, BlockPos member, ItemStack, boolean autoBind)` - the one entry point every
  producer calls. Route order: a bin already **bound** to the item first; then, only if `autoBind`, an
  **empty** bin that binds to it; then the **Scrap Barrel**. Mutates the stack, returns the remainder
  (unchanged if there is no network or no storage - the caller then does its standalone thing).
  `autoBind` is `true` only for the file-all; machine outputs pass `false`, so a teardown or smelt
  fills bound bins or the barrel but never surprise-binds an empty one.
- `reachesStorage(Level, BlockPos)` - does the cluster contain any sink; gates the file-all.

**Only two of the six member types are sinks:** a Scrap Bin (`ScrapBinBlockEntity`), and the Scrap
Barrel (its `Container`, **matched by block id**). The Burn Barrel conducts but is deliberately never a
sink - it is a furnace `WorldlyContainer`, and routing must not land in its smelt slots; the sorter,
workbench and crafting table are conductors too, never sinks.

## Cross-functional behavior - what flows where

**Build status (2026-07-24):** the network and flows 1-3 + the file-all are **built** on
`feat/workstation` and GameTested (adjacency clusters fit the `empty_5x5x5` plot, which the old 6-wide
bench never did). **Flow 4 (craft-from-storage) is the remaining capstone.**

1. **Sorting Tarp -> bins, two ways.**
   - **Right-click** sifts *garbage* into materials, which land via `insertFromMember(..., false)`
     (matching bound bin, else barrel) instead of dropping on the floor.
   - **Shift-right-click files your loose *materials*** - the payoff QoL. One action walks the player
     inventory and sends every `#binnable` stack through `insertFromMember(..., true)`, filling
     matching bins and **binding an empty bin** for any material with none yet. Gated on
     `reachesStorage`; a standalone tarp shift-click does nothing.
2. **Recompile Workbench -> storage.** Teardown outputs route through `insertFromMember(..., false)`.
3. **Burn Barrel -> storage.** On smelt the result slot drains each tick through
   `insertFromMember(..., false)`. The one genuinely time-based flow (auto-route, accepted 2026-07-24
   as the piece furthest from the manual-first line - the barrel gains an output route only while
   wired to storage).
4. **Scrap Crafting Table reads storage (v1) - not built.** Crafting draws ingredients from the player
   inventory **plus the connected bins plus the Scrap Barrel**. Still manual and player-scoped (the
   "crafting station" pattern, not an autocrafter). The build work is a crafting menu that presents the
   flood-collected storage as a combined ingredient source. Its own focused piece.

## Placement guidelines - kept, and generalized to every multiblock

The held-item footprint preview the workstation introduced is **kept**, but since the network is no
longer a multiblock it moves onto the real multiblock cores. `MultiblockPlacementPreview` (client-only)
triggers for **any held `BlockItem` whose block is a `MultiblockCoreBlock`** - Rain Collector, Grass
Spreader, and any future one - and dusts each blueprint cell at the aimed position: **green** where
clear, **red** where blocked. It reads the core's blueprint (the multiblock system's single source of
truth) and renders at `Rotation.NONE` (the shipped cores are vertical columns). Particles, not a
render-pipeline overlay - 26.1's `RenderLevelStageEvent` lost the camera / partial-tick hooks a
world-space outline needs.

The Scrap Network itself needs no placement preview: you just set blocks next to each other.

## Tests - `gametest/ScrapNetworkTests.java`

Driven through `ScrapNetwork.insertFromMember` (the deterministic engine every producer shares; the
sift's own loot roll is random and covered by `SortingTarpTests`):

1. Routes a drop into an adjacent bound bin.
2. No matching bin -> overflow into an adjacent barrel (a differently-bound bin is skipped).
3. `autoBind` binds + fills an empty bin; **negative control**: without `autoBind` an empty bin is
   never hijacked.
4. The Burn Barrel is never a sink - a route past it with no bin/barrel lands nowhere, its smelt slots
   stay empty.
5. Multi-hop flood-fill: a bin reachable only *through* an intermediate member (tarp - table - bin) is
   found.
6. **Gap** (negative control): a bin one block away, not touching, is not reached.
7. `reachesStorage` is false with only conductors, true once a bin is connected.
8. Integration: the Burn Barrel's `drainOutput` moves its result into a connected bin.

## Verification

1. `JAVA_HOME="/c/Program Files/Java/jdk-25" ./gradlew build` - redirect to file, check `$?`.
2. `./gradlew test runGameTestServer` - JUnit + full GameTest suite (reported total is ours **plus
   one**).
3. `runClient` - place a sorter next to a bin next to a barrel; sift and watch junk file into the bin
   then overflow to the barrel; break the middle block and confirm the halves stop sharing; hold a Rain
   Collector / Grass Spreader core and confirm the green/red guideline footprint still shows.

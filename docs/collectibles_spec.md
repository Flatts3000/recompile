# Collectibles spec - artifacts from the past, pieces in, cube out

Engineering notes for the Collectibles system (design I-2 in `../trashlands/docs/ideas.md`). v1 built
2026-07-26. The pack's thesis as a mechanic and the literal WALL-E anchor: a trash-picker who hoards
found curios. "The world called this worthless and I proved it wrong," made displayable.

## What it is

**Artifacts from points in time**, assembled from **thematic pieces** the player finds rare in the
garbage. v1 ships one artifact, the **Puzzle Cube** (a twisty cube), as the reference implementation;
the system is a data-driven catalog, so more are add-a-line.

## The Puzzle Cube (reference implementation)

- **Piece** (`puzzle_cube_piece`): a rare drop in the pull streams (`household_pulls` weight 2,
  `bag_pulls` weight 1 - deliberately low, so a find is an event; pre-beta placeholder weights). It is a
  small **3D cubie model** (a real cube with three coloured faces + a dark internal face), not a flat
  icon - a downsampled sprite came out fuzzy, so the piece is modelled the same way the cube is.
- **Craft**: nine pieces **fill the 3x3 crafting grid** at the Scrap Crafting Table into the solved
  **Puzzle Cube** block.
- **The Puzzle Cube is a placeable full block, not an item trophy.** Two states -
  `puzzle_cube` (solved: each face a solid-colour 3x3 sticker grid) and `puzzle_cube_scrambled` (mixed
  faces). It is a `minecraft:block/cube` with **six per-face textures**, so it renders as a genuine 3D
  cube everywhere: in hand, in the inventory, placed in the world, and displayed on a pedestal. That is
  what finally made it *read as a cube* - a real block model just is one, so there is no 2D projection
  to fake.
- **Craft it with itself to swap states**: shapeless one-in/one-out recipes toggle solved <-> scrambled.

**Adding a collectible is data**: a piece item + a cube block (or two, if it has states) + a recipe +
loot lines + face textures. Nothing hard-coded.

## Textures - procedural, not AI

The cube is fixed geometry, so its faces are drawn procedurally (the shared texgen engine's
`sticker_face` style: a 3x3 grid of beveled stickers; one colour = a solved face, six = scrambled),
not AI-generated - AI mush and downsampled 3D renders both failed at 16px. Twelve face textures (6
solved + 6 scrambled). The piece's cubie faces are the `single_sticker` style; the pedestal is a
`plinth` stone tone. All committed as 16px PNGs like every other texture.

## The Display Pedestal

- `DisplayPedestalBlock` / `DisplayPedestalBlockEntity`: a **ProjectE-style tiered plinth** (stepped
  base, slim column, cap plate) that holds **one** item. Like the Recompile Workbench's rack it is
  **not** a `Container` and exposes **no capability**, so it is never hopper-fed - it is a display, not
  storage. It drops the item on any removal (`preRemoveSideEffects`) and syncs it to the client
  (`getUpdatePacket`/`getUpdateTag`) so the renderer can draw it. It takes **any item** (owner's call),
  so it is a general display, not a tag-gated trophy stand - collectibles are the star use.
- Crafts from **6 Pressed Junk Blocks + 1 Rebar** in a plinth shape.
- **The renderer is a scoped, recorded reversal of P1.11.6** ("baked model, no BlockEntityRenderer").
  That rule was written for dump-scale finds (thousands in view); a handful of pedestals is nowhere near
  that. `client/DisplayPedestalRenderer` mirrors vanilla `CampfireRenderer`'s retained-mode pattern
  (resolve the item into an `ItemStackRenderState`, position + `submit` it, turning slowly), registered
  client-only in `client/RecompileClientEvents` (`@EventBusSubscriber(value = Dist.CLIENT)`). Because it
  renders the item's **model**, a placed block-item (like the Puzzle Cube) shows as a real 3D cube up
  there, not a flat card. **Every other block still bakes its model.**

## Found-economy invariant

Satisfied: pieces enter via garbage, and the **exit is craft-into-cube** (a placeable/displayable
sink), so collectibles never become clutter.

## Not this (v1)

- No generic curio shard - pieces are **per-collectible** (owner's call), so each artifact is its own hunt.
- No completion advancement yet - the flex is the display. An advancement per collectible is cheap follow-up.
- No teardown of a finished cube back into pieces - the cube is the point.

## Tests

`CollectiblesTests`: the 3x3 recipe yields the solved cube (a gapped grid does not); the solved <->
scrambled toggle recipes resolve both ways; the pedestal holds/returns any item, and drops it on break.
The renderers are a client concern verified in `runClient`.

## v2 candidates

More artifacts spanning eras (cassette, floppy disk, wind-up robot, rubber duck, ...); region-flavored
piece rarity (Phase 4); a completion advancement per collectible and a collection view.

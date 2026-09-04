# Collectibles spec - artifacts from the past, pieces in, cube out

Engineering notes for the Collectibles system (design I-2 in `../trashlands/docs/ideas.md`). v1 built
2026-07-26. The pack's thesis as a mechanic and the literal WALL-E anchor: a trash-picker who hoards
found curios. "The world called this worthless and I proved it wrong," made displayable.

## What it is

**Curios the player finds in the garbage and displays** - the WALL-E hoard. Two acquisition shapes:

- **Assembled** - found as thematic **pieces** that craft into the finished artifact. The **Puzzle
  Cube** (a twisty cube) is the reference: nine pieces fill the grid into the cube. Pieces are earned
  only where assembly is the point.
- **Found whole** - intact objects dug straight out of the pull streams and set on a pedestal. This is
  how the **ported voxel collectibles** (below) work: a gold coin is not built from coin-shards.

The system is a data-driven catalog either way, so more are add-a-line (or add-a-model).

## The Puzzle Cube (reference implementation)

- **Piece** (`puzzle_cube_piece`): a rare bonus in the pull streams - a **dedicated 1-in-120,000 pool**
  (a `minecraft:empty` filler at weight 119,999 + the piece at weight 1) in `household_pulls` and
  `bag_pulls`, so it drops *alongside* a material, not instead of one, and the rate is a single clean
  tunable (not a weight fighting the material pool's granularity). It is a small **3D cubie model**
  (three coloured faces + a dark internal face), not a flat icon - a downsampled sprite came out fuzzy,
  so the piece is modelled like the cube.

  **Rarity: a long-tail trophy, and the twenty-hour target is superseded.** This section read "a
  dedicated 1-in-1000 pool" and "all nine pieces in ~20 hours" long after the owner retuned it. The
  1/1000 figure is what **v0.8.0** shipped; on **2026-08-11** the owner ruled collectibles should be
  **120 times rarer than that**, which is why the shipped filler is 119,999 rather than 999. At the
  4,500-pulls-an-hour model `FindRateTest` uses that is roughly **240 hours for a whole Puzzle Cube**,
  and the forty-hour figure floated in the same conversation is superseded rather than approximated.
  `FindRateTest.collectiblesAre120TimesRarer` is where the ratio is pinned and where the arithmetic is
  written down; treat that test as the source and this paragraph as commentary on it. (`SortingData`
  counts the empty weight so JEI shows the true odds.)
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

## Ported collectibles (voxel-porter)

Beyond hand-authored artifacts, collectibles can be **ported from open-source 3D models**. The
**voxel-porter** (`../../mc-pack-toolkit/voxel-porter`) voxelizes a CC0/CC-BY model - mesh or `.vox` -
to Minecraft's 16px grid, samples color per voxel, greedy-meshes it, and emits the block model, a
generated palette texture, and every data file. Adding one is `voxel-porter emit <model> <id> <res>`,
then register the block + lang + creative-tab line + one loot line.

v1 ports four, all **CC0**: **avocado** (Khronos glTF sample), **present**, **gold coin**, and
**toy car** (Kenney Holiday + Toy Car kits). Each is a placeable block that displays on a pedestal.

**Acquisition - found whole, 1 in 480,000 per pull for a named object.** These are intact objects, so
they drop complete from the same pick-through streams as cube pieces (`household_pulls` +
`bag_pulls`). All four share **one** empty-weighted pool: weight 1 each against a `minecraft:empty`
filler of **479,996**, so the pool hands over *some* collectible once in 120,000 pulls and a
*particular* one once in 480,000. The Puzzle Cube piece has its own pool, weight 1 against a filler of
**119,999** - **1 in 120,000** - so any single collectible is four times rarer than a cube piece,
while the two pools fire at the same rate as each other. No pieces, no recipe: the Puzzle Cube is the
one artifact that earns an assembly step, because a puzzle is literally assembled.

*(This paragraph said "~1-in-4000 per pull ... a dedicated empty-weighted pool at 1/4000 - a few times
rarer than a Puzzle Cube piece (1/1000)". Those are the **v0.8.0** numbers, superseded by the owner's
2026-08-11 ruling that collectibles be 120 times rarer; 4,000 x 120 is the 480,000 the file now
carries. CLAUDE.md carried the same stale 1/4000. The ratio to the cube piece survived the retune
because both denominators moved by the same factor, which is exactly why nobody noticed the absolute
numbers had. Read the filler `weight` in the two loot tables, or read
`FindRateTest.collectiblesAre120TimesRarer`, which measures it.)*

**What ports well:** simple, iconic, colorful objects whose identity survives 16px (a coin, an
avocado). Detailed / grey / complex models mush at block scale - see the voxel-porter README.
Licensing: **CC0 or CC-BY only**, generic names, attribution file for CC-BY.

**Era artifacts, tried and dropped (2026-07-26):** an earlier pass hand-authored four blocky
period-piece artifacts (obelisk, column, chalice, hourglass). They read as museum decor rather than
desirable finds, so they were cut in favor of ported real objects. The lesson: a collectible is
desirable when it is an iconic object you recognize, not a generic shape.

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

More ported collectibles from CC0 kits; region-flavored drop rarity (Phase 4); a completion
advancement per collectible and a collection view.

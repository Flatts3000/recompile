# The Separator: model and animation - spec (issue #119)

**Status: design in progress, not built.** The art half of the gem tier's machine. Mechanics live in
[`gem_tier_spec.md`](gem_tier_spec.md); this document is only about **what it looks like and how it
appears to move**, which is the hard part.

## 0. The reference, and what survives at 16px

The owner's reference (2026-08-02) is a **twin-shaft shear shredder**, photographed into the open
cutting chamber:

- Two parallel shafts running the width of the machine, each carrying a row of thick **hooked cutter
  teeth** in blackened steel.
- The teeth of adjacent shafts **interleave**, with fixed light-coloured **comb plates** slotted between
  them.
- A **painted steel housing** around the chamber, blue in the reference.
- Open topped. You look down into the teeth.

**What survives downsampling is the sawtooth band, not the tooth.** At 16 pixels per block a single
hooked cutter is three or four pixels and will read as noise. A **repeating dark comb across a lighter
plate** reads instantly at any size, and it is what makes a shredder recognisable in silhouette.

That leads to the governing art decision:

> **The top face carries the identity, and the top face is the one the interaction points at.**

A player drops material into the top. That is the face they look at, the face they aim at, and the face
that has to say "this grinds things". Everything else is housing.

## 1. Decisions

| Decision | Answer | Why |
|---|---|---|
| Silhouette | **Open-topped box, teeth visible in the well** | The reference's single most legible feature, and it explains the interaction without a tooltip |
| Where the identity lives | **The top face** | Where the player looks and aims. Sides are context |
| Motion | **Animated texture plus particles**, never a BlockEntityRenderer | The BER ban has one recorded exception (the Display Pedestal) and this is not a second |
| Running state | A **blockstate boolean** swapping to the animated variant | Exactly what the Burner Generator's `LIT` already does |
| Tooth geometry | **Texture, not geometry**, in the chamber floor | See the rotation problem below |
| Output | **A chute on one side**, both streams from the same opening | Two openings would imply sorting the player does not control |
| Chamber tiling | **One tile, repeated across all three cells.** Not a continuous strip | A shredder chamber genuinely is repetitive, the footprint is not confirmed yet, and a positional mural would have to be re-authored the moment it changes |
| Emissive when running | **No** | A shredder does not glow. Sparks are particles; emission would read as a furnace |

## 2. The constraint that makes this hard

**Baked models cannot rotate.** The mod has no BlockEntityRenderer available to it, so the one thing the
reference is *about* - shafts turning, teeth sweeping - cannot be done the way Immersive Engineering
does it.

There are exactly three tools, and only one of them is cheap:

| Tool | Cost | Verdict |
|---|---|---|
| **Animated texture** (`.mcmeta` frame animation) | Free. Pure vanilla, no code, how fire, water, magma and prismarine work | **Use this** |
| **Blockstate model cycling** (N tooth positions swapped on a tick) | A block update and a network packet per frame, per machine | **No.** Genuine rotation, unaffordable at any tick rate that reads as motion |
| **BlockEntityRenderer** | A recorded design reversal | **No** |

### Why an animated texture actually works here

A rotating cylinder's **silhouette does not change**; only the pattern on its surface moves. So a static
model with a scrolling or cycling texture reads as rotation, and the eye supplies the rest.

That holds **only if the teeth are texture rather than geometry.** Modelled protruding teeth would have a
silhouette that changes as they turn, and a static silhouette with a moving skin would read as broken
rather than as spinning.

So: **the chamber floor is a flat plane wearing an animated comb texture.** The teeth are painted on. At
16px, with the player looking down into a recessed well, this is indistinguishable from modelled teeth
and it is the only version that can move.

The mod ships **no `.mcmeta` today**, so this is a first. It costs nothing and needs no exception.

### What sells it beyond the texture

- **Particles while running.** Dust and the occasional spark out of the chamber mouth. The Burner
  Generator already emits smoke off its top on a `LIT` state, so the idiom exists.
- **Sound.** A looping grind on the running state.
- **Depth.** The chamber floor should sit **recessed** a few pixels below the rim, so the animated plane
  is seen down a shaft rather than flush. Recession is what makes a flat texture read as a mechanism.

## 3. Footprint and the cell budget

**The art budget is distinct cell types, not volume.** The Compost Heap is 2x2x2, eight cells, and its
entire formed appearance is **one** bespoke block repeated across seven dummies.

A shear shredder is **wide and shallow**, which suits a low, broad footprint rather than a tower. Starting
proposal, to be confirmed in-world:

**3 wide x 2 deep x 2 tall**, twelve cells, **four distinct types**:

| Type | Count | What it is |
|---|---|---|
| Chamber | 3 | Top row. Recessed well, animated comb texture. The face that matters |
| Housing | 6 | Painted plate sides and back. Static |
| Chute | 2 | Lower front. Where material falls out |
| Core | 1 | The block the player places. Carries `FORMED` and the running state |

Four types is affordable. Twenty-seven would be a different project.

## 4. The palette: faded chipped paint over rusted steel

**Decided (owner, 2026-08-03).** Colour is present as **history rather than as finish**: paint that was
applied at a factory and has spent years in a landfill since, worn back to rust on every edge and wear
surface.

Colour is **blue**, inherited from the reference rather than separately chosen. Say so if it should be
the other industrial standby, safety yellow or green.

This settles the tension it was chosen to settle. A cleanly painted machine would have been the first
manufactured-looking object in a game whose palette has been salvage-coloured since launch, and a bare
rusted one would have looked welded from scrap rather than **recovered**, which is the wrong story: this
is the tier where the player starts using the dump's own machinery instead of improvising.

Three consequences for the surfaces:

- **It is the easiest of the three options to generate.** Worn painted metal is texture rather than
  geometry, which is the side of the line texgen's AI backend is strong on. The Puzzle Cube's lesson was
  the opposite case, where precise geometry at 16px failed.
- **Use `match_hue` across the machine's surfaces.** Four cell types generated independently will come
  back as four different blues. Casting the housing, chute and chamber onto one shipped sibling keeps
  them the same object. This is exactly the drift `match_hue` exists for.
- **Calibrate tone against its neighbours, not in isolation.** The Separator stands in the demolition
  yard against Reinforced Concrete, Stone Rubble and Steel I-Beams. Judge its `brightness` against
  those, and remember that a value tuned against one neighbour is wrong the moment that neighbour is
  regenerated.

### One chamber tile, not three

**Decided 2026-08-03.** The chamber is a single 16px tile repeated across all three cells, rather than
three tiles that continue across the width.

The continuous version looks better in a mockup and is the wrong long-term choice:

- **A shredder chamber really is repetitive.** The same tooth profile repeats along the shaft in the
  reference. Repetition here is accurate rather than a compromise, which is not true of, say, a mural.
- **It would lock the footprint.** 3x2x2 is explicitly a proposal, and step 1 of the build order is to
  look at it in-world and decide. Art authored to span three cells has to be redrawn the moment that
  becomes two or four. One tile does not care.
- **The chamber is the surface that will be regenerated most**, being the one the machine is judged on.
  One tile is one review cycle; three that must stay aligned is three, plus an alignment check.
- **There is no precedent for position-dependent art here.** `garbage_block` uses `variants = 3` chosen
  randomly by the blockstate, never by position.

If repetition reads badly at step 1, the escape hatch is a left/middle/right set - but only **after** the
footprint is settled, so the art is not authored against a guess. Random variants are not the answer for
a machine: manufactured parts are identical by definition, and varying them would look like damage.

### The surface list

Five surfaces, once the footprint is confirmed:

| Surface | Faces | Notes |
|---|---|---|
| `separator_chamber` | Top of the chamber cells | The comb over plate. **The one the machine is judged on** |
| `separator_chamber_running` | Same | Animated variant, `.mcmeta` alongside |
| `separator_housing` | Sides and back | Painted plate, chipped to rust at edges |
| `separator_chute` | Lower front | Darker, stained by what falls through |
| `separator_core` | The placed block | Must read as unformed machinery on its own, since a bare core is placeable |

## 5. Build order

1. **Silhouette in-world, untextured.** Place the twelve cells with placeholder colours and look at it
   from player height. Legibility is decided here and nowhere else: if it does not read as a shredder in
   grey blocks, no texture will save it.
2. **The chamber texture, static.** One 16px comb-over-plate tile. This is the surface the whole machine
   is judged on; generate it first and review it alone.
3. **The chamber texture, animated.** Add the `.mcmeta` and confirm the cycle reads as rotation rather
   than as flicker. Frame count and `frametime` are the whole trick.
4. **Housing and chute.** Cheap once the palette is settled.
5. **Particles and sound** on the running state.

Steps 1 and 3 are the ones that can fail. Both are cheap to try and cheap to abandon.

## Open

- Footprint confirmation. 3x2x2 is a proposal, not a measurement.
- **Frame count and `frametime` are set and want an in-world look.** Built 2026-08-03: **16 frames at
  `frametime` 2**, so a revolution takes 32 ticks. The strip is generated by rolling the promoted static
  frame one pixel per frame through its full height, which means frame 16 IS frame 0 and the loop is
  seamless by construction rather than by eye. It also cannot drift from the still frame, because it is
  made from it. Rolling vertically because the teeth run as horizontal bands and a turning shaft moves
  its surface across them. If it reads as a crawl rather than a spin, drop `frametime` to 1.

## What this spec does not cover

Mechanics, recipes, power, automation and the item flow all live in
[`gem_tier_spec.md`](gem_tier_spec.md). The only requirement crossing over is that the machine is
**entity-in from above and entity-out below**, which is why the chamber must be open-topped and the chute
must be reachable by a hopper.

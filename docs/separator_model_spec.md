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

## 4. The palette question, which is a real decision

The reference machine is **painted blue**. Every machine this mod ships is salvage-coloured: grey steel,
rust, concrete, beige plastic.

A painted housing would be **the first manufactured-looking object in the game**, and that cuts both ways:

- **For:** it reads as *recovered industrial equipment* rather than something welded from scrap, which is
  what it is. The yard is full of real machinery; this is the tier where the player starts using the
  dump's own tools instead of improvising.
- **Against:** it breaks a palette that has been consistent since launch, and a bright blue box in a grey
  landfill may read as imported from another mod.

A middle option worth testing: **faded and chipped paint over rusted steel**, so the colour is present as
history rather than as finish. That is also the easiest thing for texgen's AI backend to do well, since
worn painted metal is texture rather than geometry.

Not decided. It should be decided before any surface is generated, because it governs every one of them.

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

- **The palette question** in section 4. Blocks texture generation.
- Footprint confirmation. 3x2x2 is a proposal, not a measurement.
- Whether the chamber is one animated tile repeated across three cells, or three tiles that continue
  across the width. The second looks better and costs three times as much.
- Whether the running state also changes the chamber's emissive-ness, or whether that is overreach.
- Frame count and `frametime` for the animation, which is a look-at-it decision.

## What this spec does not cover

Mechanics, recipes, power, automation and the item flow all live in
[`gem_tier_spec.md`](gem_tier_spec.md). The only requirement crossing over is that the machine is
**entity-in from above and entity-out below**, which is why the chamber must be open-topped and the chute
must be reachable by a hopper.

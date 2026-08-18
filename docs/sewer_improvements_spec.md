# Sewer improvements - spec

**Written 2026-08-17, after phases 0-5 shipped (#90) and the first playtest.** The sewer works: it
generates, it is reachable, it is occupied, and it pays out. This is the list of what would make it
*good*, sized honestly.

**Phased 2026-08-17.** The build order is four phases and then a deliberate stop; the appendix keeps the
full inventory it was drawn from, so nothing considered is lost and items outside the phases are
understood as *later* rather than as *rejected*.

Each item states what it changes, **what it actually takes**, and what it risks, so the decision to do
it or drop it can be made on real numbers rather than on enthusiasm.

---

## How to read the sizes

Sizes are given in terms of what this codebase actually charges for a change, which is not lines of
code:

| Size | Means |
|---|---|
| **XS** | Data only. A JSON edit, no new registration, no new test. Under an hour. |
| **S** | Data plus a few lines in an existing piece. One assertion added to an existing test. |
| **M** | A new `StructurePiece`: class, `StructurePieceType` registration, geometry that has to survive rotation and chunk-splitting, its own test, palette entries. |
| **L** | Touches the piece graph or the structure's placement decisions - the two places where every bug this feature has had actually lived. Needs its own red-driven test before it is believed. |
| **XL** | New systems (a fluid, a block with behaviour, an entity). Carries art, lang, loot, Jade, guidebook and gate-doc obligations on top of the code. |

**A note on M and L.** The gap between them is not effort, it is *blast radius*. An M is wrong in one
room. An L is wrong in every sewer, silently, and the tests that exist will not notice - that is not
pessimism, it is the record of this feature: the quadrant bug, the sealed dens, the buried manhole and
the transposed carve were all L-shaped, all passed CI, and all needed a human to go and look.

---

## The constraints that bind every item on this list

These are not style preferences. Every one of them has already cost this feature a bug, and several
cost it two.

**1. Nothing the structure places may drop a member of `#minecraft:stone_crafting_materials`.**
That tag is exactly `cobblestone`, `blackstone`, `cobbled_deepslate`, and any of them crafts a vanilla
furnace, which skips the Cupola and opens the iron gate. **The classic mossy-cobblestone sewer is the
one thing that cannot be built.** `mossy_stone_bricks` gets the same read and is not in the tag.
Enforced by `the_sewer_palette_opens_no_gate`, which walks `SewerPalette.ALL`.

**2. Every block the structure places must end up in `SewerPalette.ALL`** - but this is a **soft
constraint at planning time** (owner, 2026-08-17). It is an implementation obligation, not a design one,
and it must not shape what gets considered. The list only enforces constraint 1 while it is complete,
and it has silently stopped being complete twice - the ladder, pad, cover and barrel were all placed
from constants declared elsewhere - so the rule at *build* time is still absolute. At *spec* time,
ignore it.

**3. Lights are wanted, sparse, and must not interfere with spawners** (owner, 2026-08-17). This
revises the flat prohibition that was here.

The mechanism to design around: since 1.18 a hostile spawn needs block light **0**, so *any* light
source suppresses spawning in its radius - the light level does not soften this, only the radius
changes. `ignoresLightRequirements` is `TRIAL_SPAWNER`-only in 26.1, so the drowned spawner is subject
to it too, and the slime rule tests darkness directly.

**Therefore light is a placement question, not a brightness question.** The rule that follows:

> **Light belongs only in spaces that are deliberately unspawnable, and stays out of the ones that are
> not.** Lit: the root chamber (already deliberately quiet), the entrance shaft, the dens, and any
> maintenance room. Dark: every corridor and junction, which is where the spawner and the slimes live.

Sparse is the second half. A lantern every few blocks turns a sewer into a lit corridor with a roof; the
target is enough to read the space by and not enough to walk it comfortably. Dim sources buy atmosphere
rather than safety - `glow_lichen` (7), soul lanterns (10), candles (3-12) - but note they suppress
spawns exactly as hard as a lantern does, so "dim" is an aesthetic choice, never a compromise.

**4. Anything the structure places becomes obtainable, and that is allowed** (owner, 2026-08-17): more
obtainables in the sewer are wanted. The obligation is to **record** each one in
`progression_gates.md`, not to avoid it.

What does *not* relax is the measuring. Mud went in as a frog-den floor and a false justification was
written the same hour - *"nothing else in this world produces mud"* - untrue, because
`#minecraft:convertable_to_mud` contains **coarse dirt**, this world's entire surface, and a water
bottle converts it. That was the **third** instance of one mistake (the glass-bottle gate and the iron
gate #91 were the first two). **Scarcity measured by reading the mod's own content is not scarcity; what
decides it is what vanilla does with the world's substrate.** Adding a material is cheap; claiming it is
new without checking is what costs.

**5. Local coordinates are not world coordinates.** `getWorldX(x, z)` swaps local X and Z on EAST/WEST
pieces and passes local values through **as absolute** when `getOrientation()` is null. Derive extents
from declared dimensions, never from the piece's own bounding box, and build boxes with a
direction-aware helper - `StructurePiece.makeBoundingBox` is for *root* pieces and always extends +x/+z,
so chaining with it silently drops every NORTH and WEST branch.

**6. `postProcess` runs once per chunk the piece overlaps**, each time with a different `limit` **and a
different `RandomSource`**. Anything *rolled* there is rolled several times over. Fixed positions plus
`limit.isInside` is the only way to mean "exactly one".

**7. `findCollisionPiece` only ever sees the sewer currently being built**, and only pieces added
through `grow`. Anything the structure attaches directly - the entrance, the dens - is invisible to it,
`postProcesses` last, and therefore **wins every overlap silently.**

**8. The fluid may never be `minecraft:water`.** It ends the Rain Collector's monopoly (locked P1.10) the
moment sewers are reachable. Leachate exists precisely so this constraint costs nothing.

**9. `getBaseHeight` returns the first *air* block**, not the top solid one - the heightmap stores
`y + 1`. Every improvement that touches the surface hits this, and it is what generated the manhole
buried under a block of coarse dirt.

**10. `placeBlock` mirrors the state it is given, and `mirror` is null unless `setOrientation` was
called.** Almost every block ignores mirroring, so this bites only on **directional** blocks - which is
most of category A: vines, pointed dripstone, stairs, trapdoors, lanterns, anything wall-mounted.

**11. The sewer generates at `underground_structures`; the yard's own features run later at
`vegetal_decoration`.** Anything the sewer places at or near the surface can have rubble stacked on it
afterwards, and clearing the column at generation time does **not** help - the features run after the
clearing. This taxes every entrance idea in category C.

**12. The player should be able to drown in leachate** (owner, 2026-08-17). **This reverses the
2026-08-17 depth ruling** recorded in `RCFluids` and in `sewers_spec.md`, which set `canDrown(false)` on
the fluid because one-block depth could not deliver a no-drowning guarantee on its own.

Two consequences to carry:

- It is a property of the **fluid**, so it applies everywhere leachate exists, including the surface
  pools from #156. Those are one block deep, which does not make drowning impossible - the check is at
  the **eye**, and `canSwim(true)` is set, so a crawling or swimming player already has eyes in the
  fluid.
- It removes the argument that kept deep sections out of category D. **Deep sections are permitted
  where they serve the improvement** (owner, same day) - a conditional rather than a default, and the
  conditions are in category D. With drowning live the fluid is lethal rather than atmospheric, so the
  cost of getting a placement wrong went up at the same moment the permission arrived.

---

## Build order

Four phases, then a stop.

**The rule that governs all of them: a feature has to follow from what a sewer is.** Not "we need a new
piece, put a room somewhere" - a sewer *has* access chambers because somebody had to maintain it, it
*has* a low point because water runs downhill, and it is *lit where people worked and dark where they
stopped coming*. When the fiction and the mechanics want the same thing, the feature stops reading as
content and starts reading as a place. Where they disagree, the fiction is usually telling you the
mechanic is in the wrong room.

Each phase therefore states **how it fits** before what it takes, and says plainly whether it changes
generation that already exists or adds new generation - those are different risks and the appendix sizes
them differently.

**The phases are also meant to leave the next one somewhere to attach.** Phase 3 builds the low point;
phase 4 puts the thing worth finding at the bottom of it, because that is where things wash to. If a
phase can only be reached by inventing a reason, it is in the wrong order.

**Ordered by uncertainty, not cost.** Phases 1-3 are cheap and low-risk, which makes them good warm-ups
and poor information. Phase 4 answers whether a sewer is a destination or a resource stop, and if that
answer is no, 1-3 were polish. There is a real argument for doing 4 first; it is declined here only
because 3 gives 4 a natural home.

---

### Phase 1 - decay and light

**Ships:** a sewer that reads as old, wet and abandoned by degrees.

**How it fits.** Decay is not random, it follows water and air. Moss and cracks belong **where the
channel runs and where the ceiling drips**, so masonry variation should be a function of distance from
the fluid rather than a scatter - a dry upper wall stays clean brick, and the course beside the channel
is the one that goes green. Silt collects **where flow slows**: the insides of bends, the corners of
junctions, the dead ends. Growth needs damp and dark, so it thins out near the entrance and thickens
away from it.

Light is the same idea pointed at people rather than water. **A sewer is lit where somebody worked**,
and this system was abandoned - so light clusters at the shaft foot and the chamber, persists in the
maintenance room when phase 2 adds one, and stops. Deeper in, there is nothing, because nobody went
there. That fiction and the spawn rule want exactly the same thing, which is the tell that it is the
right design: **the lit rooms are the ones that must not spawn, and they are the ones a person would
have lit.**

**Generation:** changes existing gen only. `SewerCorridor.postProcess` and `SewerCrossing.postProcess`
pick their wall block from proximity to the channel; the entrance and chamber gain light. No new piece,
no new registration.

**Takes:** S.

**Acceptance:**
- Wall variation correlates with the channel, not with a die roll - a dry wall reads differently from a
  wet one.
- **No light source in any corridor or junction.** The phase's real criterion: a lantern in a junction
  silently switches off that junction's spawner, and nothing else would report it.
- Silt never fills the channel or blocks a walkway.
- Nothing placed is in `#minecraft:stone_crafting_materials`, and everything placed is in
  `SewerPalette.ALL`, or that check is not enforcing anything.

**Verification:** palette walk; a per-piece-type test for the light rule; in-world for the look, which is
the only part a test cannot judge.

---

### Phase 2 - access chambers

**Ships:** rooms off the main run that explain why anything is down here.

**How it fits.** Every real sewer has access: valve chambers, inspection points, a place the crew left
their things. That is also the honest answer to a question the loot currently dodges - **the barrels are
in the entrance chamber because that is where the code could put them**, not because anyone would have
stored anything there. An access chamber gives them a reason, and gives the sewer somewhere that is not
tunnel.

They belong **off a corridor, through a door**, not attached to the entrance chamber - a store room at
the bottom of the ladder is the first thing you see, and the point is to reward looking. One or two per
sewer, at a corridor that is already past the first branch.

**Generation:** new gen (a room piece) **plus a small change to existing gen** - a corridor gains a
chance to open a side door, which is the same doorway-carving the dens already do and the first time a
corridor decides anything about its own neighbours. That is the risky half, not the room.

**Takes:** M. Dry, flat, no fluid, no vertical work - chosen so the add-a-piece path is proven on
something that cannot hide a geometry bug before phase 3 spends it on something that can.

**Acceptance:**
- Generates, connects, and can be walked into and out of.
- Lands on no corridor, den, sump or other chamber - asserted as geometry across every chamber size.
- Dry and lit, per phase 1.
- The barrels move here from the entrance chamber, so the loot is somewhere a person would have put it.

**Verification:** the geometry assertions extended, driven red against a deliberately overlapping box.

---

### Phase 3 - the sump

**Ships:** the bottom of the system.

**How it fits.** This is the most natural piece on the whole list, because a sewer **must** have one:
everything the channels carry has to go somewhere, and it goes to the lowest point. That makes the sump
the one room whose position is not a design choice at all - **it belongs at the deepest end of the piece
tree**, wherever the stairs happened to descend furthest, and attaching it anywhere else would be the
ham-fisted version of exactly this feature.

Three things fall out of that placement rather than being added to it:

- **The deep leachate is not a hazard we chose to install**, it is what a low point in a drainage system
  contains. That is the difference between a hazard and a trap.
- **The drowned belong here.** They are what accumulates in standing water, and this is where the
  standing water is - which is a better reason for the guaranteed spawner than "somewhere deterministic
  was needed".
- **It is dark.** Nobody maintained the bottom.

**Generation:** new gen (the sump piece) **plus a change to how the structure assembles** - it has to
inspect the finished piece tree, find the lowest reachable end, and attach there. That is a genuine
change to placement, which is the category where every silent bug in this feature has lived.

**Takes:** M, and the placement change is the L-shaped part of it.

**This phase closes an open guarantee.** A sewer can currently generate with no drowned at all, because
junctions carry a spawner only past depth 2 and only when their box hashes even. The sump is attached
deterministically, so it can hold the one that is always there.

**Acceptance:**
- Exactly one sump per sewer, and therefore at least one spawner - a guarantee replacing today's
  measured 80% coverage. **Shipped.**
- The drop is **telegraphed** - visible before it is entered. Leachate is opaque and this room is dark;
  a drop you cannot see is a death with no decision in front of it. **Shipped**, as a walkway across the
  entrance rows with the pool's surface flush against it.
- It is **not on the only path** to anything. If it becomes one, that is a gate and belongs in
  `progression_gates.md`.

**Two acceptance lines this phase did not meet, both recorded rather than quietly dropped.**

**"At the deepest end" is best-effort, not a guarantee.** The sump hangs exactly `DEPTH` below whatever
it attaches to, so it is the low point of its own branch in every seed - but the bottom of a sewer is the
busy end and a nine-block room often collides there, so `attachSump` walks the pieces ascending by `minY`
and takes the first that fits. Measured over 200 seeds: 2 below the tree minimum in 56, at or below it in
102, more than a stair flight above it in 7. The strict property was asserted first and failed 98 of 200,
which is how the gap was found. Buying it costs a room tall enough to carry a variable-height door
(`TALL` 14 against 7, since the door has to meet its host's floor wherever that is) - a design change
rather than a fix, and an owner call.

**It stacks three hazards where this line says pick two.** Deep, guarded and dark; only draining was left
out. The three are not independent here - the room is dark because nobody maintained the bottom, and the
drowned are there because standing water is where drowned accumulate - so they arrive as one consequence
rather than three decisions. That is an argument, not a waiver, and it is the owner's to accept.

**Verification:** an every-seed spawner guarantee, and the spawner is checked to hold a **drowned** rather
than merely to be a spawner block; a containment test asserting the pool has no face open to air, since
`placeBlock` only *schedules* the fluid tick and a "look for leachate outside the room" test passes at
tick 0 against a room with no walkway at all; in-world for whether the drop reads as telegraphed, which no
test answers.

---

### Phase 4 - what washed down

**Ships:** the reason to go to the bottom.

**How it fits.** Phase 3 built the place everything drains to, so phase 4 is not a new idea, it is the
consequence of the last one: **things wash to the low point and stay there.** A find recovered from the
sump's silt needs no justification invented for it - the room's whole function is accumulation, and the
player has already been told that by walking down channels that all lead the same way.

That also settles what kind of thing it should be. Not a crafted component - those wash away like
anything else. Something that **only makes sense as sediment**: lost, deposited, or grown in the dark.
The sewer currently pays in materials the player already has routes to; one thing that exists nowhere
else is what turns a resource stop into a destination.

**Generation:** no new gen. It attaches to phase 3's sump - a silt layer that yields it, or a container
the room already justifies. This is the phase with the least generation risk and the most design risk,
which is the opposite of every phase before it.

**Takes:** M if it is an existing item given a sewer-only route, XL if it is a new one - art, lang,
model, loot, a gate-doc entry and a guidebook line.

**Shipped 2026-08-18: the echo shard.** Owner's pick from three candidates that have no route in this
world at all (the others were a sniffer egg and a sponge; a nautilus shell and amethyst were ruled out
because both already have one).

**It arrives in a crate settled in the sump's gravel, not as a silt drop.** Three routes were considered
and the container won on being the only one that is both guaranteed and testable. A dedicated silt block
is the XL path and could not ship the same day regardless, because a new block needs a texture and a
texture needs the owner's `select`. A global loot modifier keyed on silt-broken-inside-a-sewer reads best
of the three, but it cannot promise a yield - and "clearing a sewer yields one" is an acceptance line
here, not a preference. A crate is deterministic, needs no new block, and reuses the barrel-loot
mechanism the access chambers already proved.

**The reward is under the water, and that is the whole placement.** Nothing was added to guard it - the
sump was already deep, dark and spawner-bearing, so putting the crate on the pool floor means recovering
it costs a swim down into the one thing in the mod that drowns you, with the clock already running.

**Acceptance:**
- Exactly one thing, and only sewers produce it.
- It does not skip a tier, checked against `progression_gates.md` **before** it lands, with what vanilla
  does with it measured rather than assumed. **This is the most likely place to make the mud mistake a
  fourth time.**
- It is reachable: clearing a sewer yields one, rather than it existing in a table nobody rolls.
- Recorded in `progression_gates.md` as a new obtainable.

**Verification:** a test that it has a sewer source and no other, in the shape of the `found_only`
twin-check that already exists for this class of mistake.

---

### Phase 5 - stop, and wait for evidence

**Ships:** nothing.

Everything else - more entrances, hazards, the junction hall, depth-scaled loot, more den types - waits
until playtest says which a player actually misses. The first playtest of this structure found a zoo in
one screenshot, which is more information than all the reasoning that preceded it.

**Explicitly not recommended without that evidence:** the hazard category. Every entry is M to XL for a
structure that already has a working threat, and none of them follow from what a sewer is in the way the
sump does - which is the test this build order is built around.

---

## Appendix - the full option list

What follows is the unphased inventory the build order was drawn from: every idea considered, with its
size and its risk. Items not in phases 1-4 are not rejected, they are phase 5.

### A. Dressing - the palette

The cheapest category by a wide margin, and the one with the best ratio of look to work. Every item is
XS-S; the cost is not the code, it is constraint 4 on each block.

| Item | Takes | Risk |
|---|---|---|
| **Aged masonry** - mix `mossy_stone_bricks` and `cracked_stone_bricks` into corridor walls | **S.** Two palette entries, a weighted pick in `line()`. | Low. Neither is in the furnace tag. Gives age without cobblestone. |
| **Silt beds** - `gravel` and `clay` along the channel edges | **S.** Two palette entries, a loop in `channel()`. | Low-medium. Both already exist in this economy; **clay is the chain from #115**, so a free source needs a glance at the gates doc. |

**Shipped, and the silt is suspicious** (owner, 2026-08-18). Clay lost to sand for the reason above (a
free clay source retires the whole #115 chain), and then both deposits went one further: they are
**`suspicious_gravel` and `suspicious_sand`**, so a sewer is the only place in this world with
archaeology in it. Silt is a slow accumulation of everything that came down the pipe, which is what a
brushable block already means - the fiction and the vanilla mechanic wanted the same thing.

Three consequences, all of them worth knowing before tuning:

- **A brushable drops nothing when mined.** `loot_table/blocks/suspicious_gravel.json` has no pools at
  all. The silt is therefore no longer a free source of gravel and flint; you brush it into ordinary
  gravel first, and *that* drops. Net: slightly stingier than before, not more generous.
- **A brushable with no loot table on its block entity is a lie**, and a silent one - it brushes away
  into plain sand or gravel, drops nothing, and logs nothing. Every deposit is therefore placed through
  `SewerPiece.deposit`, which is the only thing allowed to write one, and
  `the_silt_has_something_buried_in_it` walks the pieces and fails if any deposit is hollow.
- **The payout is deliberately mostly nothing.** `recompile:archaeology/sewer_silt` is 30/77 empty. The
  sump beds fifteen deposits (a 5x3 patch inset from the pool walls) and a corridor rolls up to four,
  so a cleared sewer is a few dozen brushes; a find in every one of them would out-pay the crate that
  room is built around. Contents are small lost things: bone, string, junk, e-scrap, glass shards, cullet, a
  bottle, and at the thin end a heart pottery sherd (the #115 chain's second source) and a nautilus
  shell.

**The brush is a soft gate, and it is worth knowing which side of it the sewer sits on.**
`minecraft:brush` is a feather, a stick and a copper ingot. Copper and sticks are early; the feather is
not, because this world has no mobs until the animals rung and a chicken arrives on omnivore bait. So a
player who reaches the demolition yard before they reach chickens finds a sewer full of deposits they
cannot open. That is a delay rather than a dead end - nothing about the silt is consumed by being seen -
but it means the silt is **not** the sewer's introduction to itself, and the crate in the sump (which
needs no tool) still is.

**Open for playtest:** fifteen brushables on the sump floor is fifteen brushes underwater, in leachate,
with the drowned spawner running. That is on-theme - the spec already says the room's hazard is
what guards its reward - but it is a lot of brushing, and the dial if it reads as a chore is the *count*
of deposits rather than the table.
| **Damp growth** - `vine` on walls, `brown_mushroom` on the floor | **S.** Two entries plus placement. Mushrooms are already the P1.9 forage vocabulary. | Low. `vine` needs a supporting face and will look wrong placed blind. |
| **Pipe** - `copper_grate` at junctions, oxidised copper stubs | **S.** Palette plus placement. | **Medium: copper is this world's everyman metal**, gated behind the Burn Barrel. Free copper blocks is a gate question, not a dressing question. |
| **Limescale** - `dripstone_block` and `pointed_dripstone` where the ceiling drips | **M.** Pointed dripstone needs a supporting block and an up/down state; placing it blind produces floating spikes. | Low on gates, medium on geometry. |

| **Light, sparse** - lanterns or soul lanterns in the chamber, the shaft, the dens and any maintenance room | **S.** Palette plus placement, and a placement *rule* rather than a scatter. | **Medium, and it is a spawn question not a light question.** Any source suppresses hostile spawns in its radius, so this only works while it stays out of corridors and junctions. A lantern in a junction quietly turns off that junction's spawner. |

**Recommended first:** aged masonry and silt beds. They change how the whole structure reads, cost two
palette entries each, and neither touches a gate.

**And the light pass alongside them**, because it is the same size and the rule it needs - lit rooms,
dark corridors - is easiest to get right while the room types are few. Adding light later, once there
are maintenance rooms and sumps and junction halls, means auditing every one of them for whether it is
meant to spawn.

---

### B. Piece vocabulary - new rooms

The category that most changes what exploring feels like, and the most expensive per unit. Every entry
here is **M minimum**, because a new piece is a class, a registration, geometry that must survive
rotation and chunk-splitting, and a test - and because constraint 7 means anything attached outside the
graph wins overlaps silently.

| Item | Takes | Risk |
|---|---|---|
| **Sump** - a deep pooled chamber at a dead end, the "bottom" of the sewer | **M.** New piece; reuses the chamber's shape. Best home for a guaranteed spawner (see H). | Medium. Deep leachate reopens the `canDrown` question that depth-1 currently settles. |
| **Collapsed section** - a corridor half-filled with rubble, passable but slow | **M.** New piece or a variant flag on the corridor. | Medium. Rubble means `stone_rubble`, which is a real material - constraint 4. |
| **Maintenance room** - dry, brick, a barrel and a workbench-ish dressing | **M.** New piece. Gives the loot somewhere that reads as *why* it is there. | Low. Dry room, no fluid, no gate exposure. |
| **Outfall** - a wide grated mouth where the sewer would discharge | **M-L.** New piece plus a decision about whether it connects to the surface. If it does, it is a **second entrance** and lands in category C. | High if it opens outside: a hole in the ground that bypasses the manhole is a gate hole. |
| **Junction hall** - a larger crossing, two levels, a walkway | **L.** Multi-level geometry inside one piece is where local-vs-world (constraint 5) bites hardest. | High. This is the shape of piece that has produced every geometry bug so far. |

**Recommended first:** the maintenance room. It is the only M here with no fluid, no gate exposure and
no vertical geometry, so it is the cheapest way to prove the "add a piece" path is repeatable before
spending an L on the junction hall.

---

### C. Entrances

Currently exactly one per sewer, which is a deliberate simplification and also a single point of
failure - the yard's own surface features can settle rubble on a cover (recorded, accepted).

| Item | Takes | Risk |
|---|---|---|
| **Storm drain** - a second, smaller entrance at a corridor end rather than the chamber | **M.** A second entrance piece, plus the structure choosing where it lands. | Medium. Two entrances means the "one cover, one sewer" promise in the guidebook needs rewriting. |
| **Clear the column above a cover** so a rubble pile cannot bury it | **S.** A few blocks of air at generation. | **Does not actually work alone:** the yard's features run at `vegetal_decoration`, *after* `underground_structures`, so they place into that air afterwards. Real fix is a feature-side check, which is **L** and touches four features. |
| **Make the pad bigger** (5x5) if playtest says covers are missable | **XS.** One loop bound. The spec already says the pad grows before the rarity changes. | None. |

**Recommended first:** the XS pad widening, if and only if playtest reports missing them. Do not
pre-emptively fix a problem nobody has hit.

---

### D. Water and the channel

| Item | Takes | Risk |
|---|---|---|
| **Flowing leachate** - sources at corridor heads, flowing downhill | **M.** Placement change plus a real look at flood behaviour during generation. | **Medium-high.** `levelDecreasePerBlock 2` limits spread, but generation-time fluid is exactly the acceptance criterion phase 2 was written against. |
| **A sluice or weir** - a visual break in the channel | **S.** Dressing, using existing blocks. | Low. |
| **Deep sections** - two-block leachate in a sump | **M.** No longer blocked on a ruling. | **Permitted where it serves the improvement** (owner, 2026-08-17) - a conditional, not a default. See below. |

**Deep leachate is allowed when it serves the need of the improvement, and not otherwise** (owner,
2026-08-17). The rules ruling is settled - `canDrown` is `true` on the fluid - so this is now a design
question rather than a permission question, and the bar it has to clear is that it is doing a job.

**Doing a job** looks like: a sump that reads as the bottom of the system because you cannot simply walk
through it; a flooded section that gates a reward behind a decision to swim; a drop that makes a
stairwell feel like a descent. **Not doing a job** looks like deep fluid because a room seemed empty, or
because a corridor wanted variety.

Three things to hold when placing any of it, because with drowning live the fluid is now lethal rather
than atmospheric:

- **Telegraph it.** A player should see a deep section before entering it. Leachate is opaque and the
  sewer is dark, so a drop that is invisible until you are in it is a death with no decision in front of
  it - which is the difference between a hazard and a trick.
- **Do not put it on the only path.** One entrance per sewer already means a single point of failure;
  a mandatory swim through drowning fluid makes the whole structure gated on a hazard rather than
  decorated by one. If it *is* the only path, that is a deliberate gate and belongs in
  `progression_gates.md`.
- **Watch what it stacks with.** Deep leachate compounds with the Hunger tax, with darkness, and with a
  drowned spawner if a sump also carries one (which is the sump's other job, per H). Deep **and**
  guarded **and** dark **and** draining is four things at once; pick which of them a given room is
  actually for.

---

### E. Inhabitants

The category with the most surface already built, and therefore the cheapest additions.

| Item | Takes | Risk |
|---|---|---|
| **More den types** (bats, silverfish-analogue) | **M each**, following the existing `SewerDen` base - `doorSide()`, `bed()`, `population()`, `resident()`. The pattern is proven now. | Low. The abstract base already carries the door and the placement rules. |
| **Wandering roach density** - tune the `spawn_overrides` weights | **XS.** JSON. | Low, and it belongs to the balance pass (#36) anyway. |
| **A named or unique inhabitant** | **XL.** A new entity is art, model, renderer, lang, spawn egg, loot, and a Jade line. | High. The mod has exactly two custom entities and both were substantial. |

---

### F. Reward

| Item | Takes | Risk |
|---|---|---|
| **Depth-scaled loot** - richer barrels further from the entrance | **M.** The piece knows its `genDepth`; the barrel placement would need to pick a table from it. | Low mechanically. **Medium in design:** it rewards exploring, which is the point, but it needs #36's numbers to mean anything. |
| **A unique find** - one item that only a sewer produces | **M-XL** depending on whether it is an existing item or a new one. | **This is the item most likely to be worth doing.** The sewer currently pays in materials the player already has routes to; one thing that exists nowhere else changes it from a resource stop into a destination. |
| **More component variety** in the barrels | **XS.** Loot table only. | Low - all `blueprint_crafting`, so a found one teaches nothing. |

---

### G. Hazard

Currently: drowned, slimes, and a Hunger tax. That is thin for a structure whose premise is that it is
*cleared*.

| Item | Takes | Risk |
|---|---|---|
| **Gas pockets** - a damaging or blinding zone | **XL.** A new fluid or a block with behaviour, plus its own tick logic and cleanup story. | High. The mod has one custom fluid and it took real work. |
| **Weak floors** - a block that gives way | **M-L.** Behaviour block plus falling logic. | Medium-high. |
| **Deeper darkness** - actively suppress light | **Not possible without mixins.** The mod has none. | N/A - this is phase 0's answer again. |

**Honest read: this category is the worst value on the list.** Every entry is at least M and most are XL,
and the sewer already has a threat that works. Spend here last, if at all.

---

### H. The two guarantees that are currently not guaranteed

Both are recorded, both are open, and both are **L** because they touch placement.

**Two sewers can overlap.** `RADIUS_CAP` bounds one sewer's reach; nothing checks against a neighbour,
because `findCollisionPiece` only sees the sewer being built (constraint 7). Enforcing separation via the
structure set costs roughly **400x the rarity** the spec pins, which is why it is an owner call and not
a fix. **Takes:** either a rarity decision (XS, and it is yours) or a real cross-structure check (**L**,
and it is genuinely hard).

**A sewer can generate with no drowned.** The chamber used to place a spawner unconditionally; junctions
now place one only past depth 2 and only when the box hashes even.
`most_sewers_get_a_drowned_spawner` measures the coverage rather than assuming it. **Takes:** a
guaranteed home for one spawner - which is exactly what a **sump** piece (category B) would provide, and
the best argument for building that piece first.

---

## Verification, for anything on this list

Every item above ships with the same three obligations, because every bug this feature has had escaped
one of them:

1. **A test that is driven red against the bug it describes.** Every geometry test written for this
   feature passed while the bug was live until it was deliberately broken first.
2. **A palette entry** if it places a block, or constraint 1 stops being enforced.
3. **A look in-world, in a freshly generated world.** The dev save bakes worldgen settings into
   `level.dat`, so an old world will show old terrain and lie to you - that cost an hour once already.

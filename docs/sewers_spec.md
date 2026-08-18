# Explorable sewers - spec

**Status: design locked 2026-08-01, not built.** The mod's first real structure, its first finite
content, and the home for the aquatic life deferred from rung 5. Every decision below was made in the
design session; what remains is build order and the risks each phase carries.

## 0. The idea, and why it fits

You find a manhole in the demolition yard, pry it open, and go down into brick corridors.

**It is this mod's mineshaft.** Everything shipped so far renews or is gated; a sewer is cleared once
and is done. That is a genuinely new kind of content here, and it is the reason the loot has to be
worth the trip rather than incidental.

**It gives the aquatic life somewhere to be.** Frogs, turtles and drowned have no home in a world with
no ocean. Issue #44 asked for a bait-style mechanism to bring water mobs back, on the assumption they
had to be *farmed* into existence. A sewer answers the same want a different way, so **#44 is
superseded rather than closed by this** - see the progression note below.

## 1. Decisions

| Decision | Answer | Why |
|---|---|---|
| Where | **In the demolition yard** | Travel already gates the yard, so the sewer needs no gate of its own |
| Depth | **Thicken the deepslate to hold them** | Shipped 2026-08-17. The slab measured 7-11 blocks with void beneath (this row said ~13, which nobody had checked); it is now 59-63, of which 55-61 is tunnelable rock. The layering needed no work |
| Entry | **Prybar on a manhole** | Reuses the Bulky Waste loop exactly: a surface find, tool-gated, one action. No new verb, no new tool |
| Surface marker | **A 3x3 of Reinforced Concrete with the manhole at its centre** | Reads as deliberate rather than as terrain noise, and it is built from a block the yard already has |
| Rarity | **Vanilla mineshaft frequency** | `frequency: 0.004`, `spacing: 1`, `separation: 0`, `legacy_type_3`. Copied from `structure_set/mineshafts.json` rather than guessed |
| Shape | **Vanilla mineshaft sprawl and levels** | Corridors that branch and descend, not a single floor |
| Look | **Brick corridors, large brick rooms, scattered pipe, flowing leachate** | Said "flowing water" until 2026-08-17, six rows above the decision that there is no water down here - the row a phase 2 implementer reads first |
| Extent | **Finite per sewer** | One is cleared and done. The world holds more |
| Inhabitants | **Roaches, frogs, turtles, drowned, slime** | Slime added 2026-08-02; **both a mob and a material**, decided 2026-08-17 |
| Cobwebs | **Generated in the corridors** | Decided 2026-08-02. The mineshaft parallel, and the only source in the game |
| Reward | **Barrels with real loot** | Finite content needs a reason to clear it |
| Generation | **A custom Java `StructureType`** | Vanilla mineshaft sprawl is code-backed; jigsaw would read like a bastion |
| Water | **Leachate, one block deep** (owner, 2026-08-17) | The fluid the dump already drains. Not water, asserted; no route to water at all; no new fluid and no filter machine |
| Drowned loot | **Vanilla, trident included** | By sewer depth the player has iron and sticks, so armour and tools exist. A trident is a prize, not a spike |
| Held light | **Torches light while carried** | See phase 0; this is the one item that may not be buildable |

## 2. The constraints that shape everything below

**The iron gate is the thing most likely to be broken by accident.** The mod ships no stone-mining tool
of its own - sledgehammers are tag-gated to `recompile:reinforced_concrete` alone - and that absence is
what `CupolaFurnaceBlockEntity` documents as keeping a vanilla furnace uncraftable. Brick was checked
and is safe: `#minecraft:stone_crafting_materials` is only cobblestone, blackstone and cobbled
deepslate. **Any new sewer block must be checked against that tag before it ships**, and
`progression_gates.md` in the Trashlands repo tracks what is reachable when.

That the gate already leaks (below) does not make this check optional. A second hole would still have to
be found and closed, and the sewer is the largest new block palette the mod has ever added at once.

**Brick is harvestable, and that resolved itself.** An earlier draft of this spec said players could not
take brick home because the mod has no pickaxe. That is wrong at this point in the game: the Tree
Nursery gives wood, and a wooden pickaxe is a vanilla recipe the mod does not remove. So brick behaves
normally and needs no bespoke rule. (The same chain is why the iron gate has a hole - see the note at
the end of this section.)

**This mod has no mixins.** That is a standing architectural rule, and it is what makes the held-light
requirement a research task rather than a feature.

**Sewer water is leachate** (owner, 2026-08-17). A bucket is three iron, iron comes from the yard, and
the sewer is *in* the yard, so a plain water source down there would end the Rain Collector's monopoly
(a locked P1.10 decision) the moment sewers become reachable.

This spec's answer was a bespoke sewage fluid plus a filter machine that "this spec creates and does not
design". **Leachate is better on every axis and it already ships** (#156):

- **It is not water, and that is asserted, not assumed.** `leachate_is_not_water` proves the fluid is
  neither `WATER` nor `FLOWING_WATER`, and the Rain Collector's tank takes water only.
- **It cannot become water.** Nothing in the mod converts it - no recipe, no machine, no cauldron
  interaction. The monopoly is protected by the *absence of a route*, not by a cost, which is the
  stronger form: there is nothing to rebalance later.
- **It cannot irrigate.** `FluidType.canHydrate` defaults false and `RCFluids` deliberately never sets
  it, so a flooded corridor is not a free farm and `RCEncroachment`'s wet-farmland rule is untouched.
- **No filter machine.** The mod keeps machines to a minimum; the original plan added one to solve a
  problem this fluid does not have.
- **Its spread numbers already suit a corridor.** `levelDecreasePerBlock` 2 halves water's reach and
  `tickRate` 15 makes it crawl - tuned, in `RCFluids`' own words, so "a broken pond edge weeps rather
  than floods". Phase 2's acceptance criterion asks that the fluid "does not flood the corridors on
  generation": different wording, same requirement, already met.
- **It is what a sewer under a landfill would actually carry.** Rain falls through refuse and comes out
  the bottom as this. The fiction needed no invention.

**Depth is one block, matching the pools** (owner, 2026-08-17): leachate is atmosphere plus a Hunger
tax, not a drowning hazard. **Superseded the same day - see below: leachate drowns you now, and the
corridors stay one block deep for the other reasons.** Corridors stay walkable and the generator never
has to reason about a body
deep enough to trap a player.

**Depth alone never delivered that, and the ruling has since reversed: leachate drowns you**
(owner, 2026-08-17). `canDrown` is `true` on the fluid again. An earlier version of this
paragraph said one block keeps `canDrown` inert. That does not follow: drowning is evaluated at the
**eye**, not from the depth of the pool. `canSwim(true)` is set, a crawling or swimming player has eyes
inside a one-block body, and the Shape row above commits to corridors that branch and descend - so a
source on an upper level weeps down a stair and puts a falling column at head height on the level below.
The reasoning stands and the conclusion flipped: because the check is at the **eye** and
`canSwim(true)` is set, depth cannot decide this either way - so the value belongs on the fluid, and the
owner has set it to drown. `leachate_can_drown_you` holds it there. It applies **everywhere leachate
exists**, the surface pools included, and a one-block pool is genuinely enough for a player who crawls
or swims: that is the accepted cost of the ruling, not an oversight in it.

**Two consequences, because leachate was built for puddles and a sewer is not one.**

1. ~~**A deeper body would make `canDrown` reachable for the first time.**~~ **Wrong, and wrong in a way
   worth keeping.** `canDrown` was never reachable at any depth: NeoForge's
   `CommonHooks.onLivingBreathe` - the only consumer of `canDrownIn` - is **commented out** in
   26.1.2.76, and the patched `LivingEntity.baseTick` calls vanilla `isEyeInFluid(FluidTags.WATER)`
   directly, which leachate can never satisfy because it is deliberately in no fluid tag. The flag has
   been inert since the fluid shipped. Both stories told about it here - "unreachable because pools are
   one block deep" and later "reachable, so set it false" - were guesses at a mechanism nobody had
   read, and `RCLeachateContact`'s own javadoc had already recorded the truth. Deep sections are
   **permitted** (see `sewer_improvements_spec.md`), and drowning is delivered by that class draining
   air itself.

**A gate hole found while writing this, filed separately.** Plain `minecraft:deepslate` sits in
`mineable/pickaxe` and in no `needs_*_tool` tag, so a **wooden** pickaxe drops cobbled deepslate - which
is in `#minecraft:stone_crafting_materials` and crafts a vanilla furnace. The Tree Nursery supplies the
wood. So the Cupola can be skipped today. It is not a sewer problem, but it is the same trap
`CupolaFurnaceBlockEntity` warns about, and it changes what "the player has at this depth" means.

---

## Phase 0 - the held-light spike

**Ships:** an answer, not a feature. Whether a carried torch can light the world without a mixin.

Minecraft has no dynamic lighting. The two candidate approaches:

- **Depend on a dynamic-lights mod.** Cleanest if it exists. Assume it does not: nothing checked in
  this session has published past 1.21.1, which is the same wall Mekanism, Create and Crafting Station
  hit. Verify before relying on it.
- **Place and remove `minecraft:light` blocks** as the player moves. Needs no mixin and is a known
  approach, but it triggers a lighting recalculation on every step and leaves blocks behind if it ever
  desyncs.

**Acceptance:**
- A written answer with a measured frame-time cost for the light-block approach, taken in a real sewer
  corridor rather than on flat ground.
- A stated cleanup story: what happens on disconnect, on death, on dimension change, and on a crash.
- If neither approach is acceptable, **say so and cut the requirement.** A dark sewer with torch
  placement is a worse experience but a shippable one, and finding that out in phase 0 costs a day
  rather than a phase.

**Risk:** this is the only item in the spec that might be impossible. It is phase 0 so it cannot
silently become phase 5's problem.

---

### Answer (2026-08-17): **cut it.** Held light is not worth having here, and the strongest reason is not cost

The spec authorises this outcome explicitly, and the phase-3 work turned up an argument the spec could
not have known when it was written.

**Held light would fight the sewer's own mob design.** Everything that lives down there is gated on
darkness: the drowned spawner needs it (`ignoresLightRequirements` is `TRIAL_SPAWNER`-only in 26.1, so a
normal spawner still checks light), and the slime rule in `RCSewerSpawns` tests it directly. A torch
that lit the corridor as the player walked would suppress the spawns immediately around them - the
player would carry a bubble of emptiness through the one structure built to be inhabited. That is not a
tuning problem; it is the feature working against itself.

**The two candidate approaches, assessed:**

- **Depend on a dynamic-lights mod.** Not adopted, and availability is not the deciding factor - I did
  not check whether one has published for 26.1, because it does not change the answer. This mod ships
  **standalone with no hard dependencies**; every viewer it integrates with (JEI, Jade, Modonomicon) is
  `runtimeOnly` and absent-safe. A lighting feature that only exists when another mod is installed is a
  feature most players do not have, and one the guidebook cannot describe.
- **Place and remove `light` blocks as the player moves.** Technically viable with no mixin, and it
  fails on the cleanup story rather than on frame time. A crash or a hard kill leaves light blocks
  behind, and an orphaned `minecraft:light` is an *invisible* block a player cannot find or remove
  without a command. That is a save-corruption class of bug - mild, permanent, and invisible - traded
  for an ambience improvement in one structure.

**What ships instead:** torches, placed. A dark sewer you light as you clear it is a worse experience
than a lit one and a better experience than a buggy one, and it keeps the spawns the structure exists
for. The guidebook says "bring a light" for exactly this reason.

**Reopen this if** a dynamic-lights mod becomes something the pack depends on for other reasons, in
which case the cost of the dependency is already paid - but the spawn-suppression argument stands even
then and would need answering on its own.

## Phase 1 - the manhole

**Ships:** you can find a manhole in the yard and open it. **Shipped 2026-08-17, and built after phase
2 rather than before it, which changed the design for the better.**

**The sewer brings its own entrance.** This phase originally described scattering covers at mineshaft
density over a stub shaft, with the density measured and tuned. Once the structure existed, that was
clearly the wrong shape: a separately-placed cover can open onto nothing, and the density question is
really "how often is there a sewer", which the structure set already answers. `SewerEntrance` is a piece
of the structure, so **every cover a player finds opens onto a real sewer, and every sewer has exactly
one way in**. No second rarity dial to keep in sync.

**The cover is scrap steel, not cast iron.** Nothing in this world is municipal, so the plate is rusted
and pitted rather than foundry-cast.

**It has its own art, generated and approved** (owner picked candidate 0, 2026-08-17). Two rulings came
out of getting there and both are worth keeping, because the reasoning that lost sounded good:

- **AI, not procedural.** The procedural case was the Puzzle Cube's - concentric circles and a repeating
  tread land on whole pixels when drawn as code - and the owner's answer was that procedural does not
  best AI. Fixed geometry is a reason procedural *can* work, not a reason it wins. The procedural
  `manhole_cover` style stays in the engine as the keyless fallback.
- **Square and full-bleed.** The first pass drew a round cover, which left the tile's corners showing a
  surround. A block face is a square, and anything that does not fill it reads as a sticker laid on the
  ground rather than as the ground. The prompt now says square three different ways, because "manhole"
  pulls an image model toward a circle hard enough that asking once does not hold.

**Ladders the whole way up.** Nothing else in the palette is climbable, and a shaft you can fall down
but not walk out of is a trap rather than a door.

- A `manhole` block, plus the 3x3 Reinforced Concrete surface pad that marks it.
- Prying it: the Bulky Waste pattern (`BulkyWasteBlock`), right-click with the prybar, one action.
  Without a prybar, the same "you need a Prybar" nudge. Prying leaves **air** - the shaft below is
  already built, so a second "open" state would be another thing to model, light and test for nothing.
- Placement comes from the structure, so it is the sewer's own rarity and needs no separate dial.

**One trap found building it, and it will recur.** `StructurePiece.placeBlock` mirrors the state it is
given, and `mirror` is **null** on any piece that never calls `setOrientation`. Almost every block
ignores mirroring - a brick returns itself untouched - so the root chamber never noticed. A **ladder**
rotates by `mirror.getRotation(facing)` and throws on the null. The shaft is the first piece here to
place a directional block, so it is the first to find out. Calling `setOrientation` would fix the null
and break the coordinates, since this piece works in absolute positions and an orientation switches
`getWorldX/Z` into transforming them; it writes directly instead, as the chamber already does for its
spawner.

**Acceptance:**
- ~~Manholes at roughly vanilla mineshaft density, measured over a sampled area.~~ **Retired**: the
  entrance is a piece of the structure, so its density IS the sewer's density and there is no second
  dial to measure. Measuring it would be measuring the structure set twice.
- The pad is visible from the ground, not only from the air. **This is the one that nearly shipped
  broken:** the shaft was capped at the *footprint minimum* surface - a `Math.min` over nine samples
  spanning up to 80 blocks - so wherever the ground above the chamber was higher than the lowest ground
  anywhere under the sewer, the pad and cover generated **buried**. The cap now reads the shaft's own
  column.
- Prying without a prybar does nothing and says why. Prying with one opens the way down. **And nothing
  else opens it:** the cover is unbreakable, because `requiresCorrectToolForDrops` gates a *drop* and
  the reward here is the shaft - with hardness alone, fifteen seconds of bare-handed mining achieved
  exactly what prying achieves.
- A GameTest covers the tool gate, driving `useItemOn` rather than the static entry point, so the
  branch that decides prybar-against-not-prybar is the one under test.

**Known and accepted:** the yard's own features (`rubble_pile`, `steel_stack`, `building_husk`) run at
`vegetal_decoration`, after `underground_structures`, and they write into air - so a pile can settle on
top of a cover. It obscures rather than seals: rubble is breakable and the concrete pad still reads
through it. Worth revisiting if playtest says a buried-in-rubble cover is missable, since there is
exactly one per sewer.

**Risk:** a 1x1 hole in a large biome is either exciting or miserable. The 3x3 pad exists to make it
findable; if playtest says it still is not, the pad grows before the rarity changes.

## Phase 2 - the sewer itself

**Ships:** the structure. Corridors, rooms, levels, pipes, leachate.

**A custom `StructureType` in Java, mirroring vanilla's `MineshaftPieces`.** Decided rather than open.
`minecraft:mineshaft` is code-backed, not data-driven - its `mineshaft_type` picks block palettes
hardcoded in Java - so it cannot be reskinned to brick from a datapack. Jigsaw with template pools was
the cheaper alternative and was rejected: it assembles authored rooms rather than generating corridor
runs, so it reads like a bastion rather than a mineshaft, and the sprawl is the point.

**The slab had to grow first, and it has (2026-08-17).** The floor gradient in
`noise_settings/garbage.json` moved from `55 -> 58` to `3 -> 6`, so the terrain went from **7-11 blocks
thick** to **59-63**, measured over 81 columns across a 4000x4000 span by
`the_world_has_rock_enough_to_hold_a_sewer`. (This spec said "roughly 13" - that was optimistic, and
nobody had measured it.) The layering needed no work: the surface rule writes bedrock at
`stone_depth ceiling` and coarse dirt at the top three, so both followed the floor down on their own.

**45 blocks is the requirement, and it is derived from the machine this mirrors.** `MineshaftPieces`
caps recursion at `MAX_DEPTH = 8` and `MineShaftStairs.findStairs` builds a box spanning y `-5` to `+2`,
so a stairs piece drops **5** and a worst-case chain descends about **40**. Add the root room, which is
5 to 10 tall, and round up. The test asserts that number rather than the gradient, so retuning the
terrain cannot quietly take the room back, and it asserts the surface has **not** moved in the same
pass - a slab that grew upward would break every mound, spreader and farm plot in every existing save.

**This is a worldgen change, so every existing world keeps its thin slab and will never have sewers.**
That is the same trap that cost a playtester 90 minutes looking for a demolition yard in a v0.2.0 save.
Accepted deliberately here (the mod is alpha, per the #87 close), but the release notes have to say it.

This is **the mod's first real structure either way.** There is exactly one `.nbt` in the repo today
and it is the gametest plot, so structure sets, template pools and processors are all new surface.

**Shipped 2026-08-17.** `SewerStructure` + `SewerPieces` + `SewerPalette`, with the placement data at
`worldgen/structure/sewer.json`, `worldgen/structure_set/sewers.json` and the
`#recompile:has_structure/sewer` biome tag. Verified in a freshly generated world: `/locate structure
recompile:sewer` finds one 690 blocks out in the demolition yard, its brick sits at y=53-56 with
deepslate at y=62 above and y=10-15 below, and a screenshot shows corridors with cobwebs, iron grates
and leachate in the channels.

**Four more bugs, all in the placement layer, none of which any test could see.** The geometry tests
run over `StructurePiecesBuilder` - bounding boxes and nothing else - so every one of them passed while
`postProcess` put blocks in the wrong places. Recorded because the shape of the mistake will recur:

- **Local coordinates are not world coordinates.** `getWorldX(x, z)` returns `minX + z` for an EAST
  piece and `maxX - z` for WEST, so local X and local Z **swap** on that axis. Deriving extents from
  the world-space bounding box and feeding them back as local bounds carved every east-west piece
  across its own width. Invisible in a north-south sewer.
- **A null orientation makes local coordinates absolute.** `getWorldX` hands back what it is given when
  `getOrientation()` is null, so the un-oriented room built itself at world **origin** regardless of
  where its box was - a sewer 690 blocks out simply had no root chamber, and its corridors dead-ended
  into rock. Vanilla's own `MineShaftRoom` passes absolute coordinates for exactly this reason.
- **`generateBox`'s two-state form walls all six faces**, and every child is anchored one block past its
  parent, so the sewer was a chain of sealed brick boxes with two solid layers between them. You could
  stand in one segment and never walk to the next.
- **The bore was the outer size**, so after shelling, the interior was one wide and two tall - and the
  channel then filled its entire floor, making every corridor a crawlspace that applied Hunger for its
  full length. The bore is now the *interior* (three across, three tall) with the shell added around it.

`a_corridor_is_carved_along_its_own_length` and `the_root_room_is_built_at_its_own_bounding_box` call
`postProcess` for real and read the blocks back. The corridor test faces **EAST** on purpose - north-south
is the one orientation where local and world axes agree, so it proves nothing - and corridors are
**seven long against five wide** for the same reason: a square piece makes a transposed carve
indistinguishable from a correct one.

**A second review round found four more, two of them severe, and the pattern is the point.** Every one
lived in `postProcess`, which the graph tests cannot see, and the fix for an earlier bug caused one of
them:

- **`StructurePiece.makeBoundingBox` is for ROOT pieces.** It always extends in +x/+z from the anchor;
  `direction` only swaps width against depth. A chained NORTH branch anchored at `minZ - 1` therefore
  gets a box running back *into* its parent, `findCollisionPiece` rejects it, and the branch is dropped
  with no error. **Every sewer was confined to the +X/+Z quadrant** with half of every branch roll
  discarded - and nothing caught it, because a quadrant is smaller than the bound, not larger. Boxes
  are direction-aware again, which is what vanilla's mineshaft does by hand.
- **Side walls sealed every turn.** Each piece walls its own two sides for its full length and a child
  is anchored one block past its parent, so a left or right branch started on the far side of a solid
  brick layer. Only straight-ahead children connected. A child now hollows the plane one step before it
  begins, cutting its own doorway back through whatever the parent placed; parents postProcess first,
  so the child always has the last word on the shared face.
- The stairs had no landing, so the doorway was a five-deep pit into the cavity under the staircase.
- The root chamber had no vertical walls - a brick floor and ceiling with raw deepslate sides, visible
  wherever a corridor's own brick met bare stone.

**Rarity is back to what section 1 pins** (`frequency 0.004`, `spacing 1`, `separation 0`). It was
briefly `spacing 20 / separation 8` to guarantee two sewers cannot meet, which also made them about
four hundred times rarer than specified - a design change, and not one to make silently.
**Open, for the owner:** `RADIUS_CAP` bounds one sewer's reach but nothing checks against a neighbour,
because `findCollisionPiece` only sees the sewer being built. So "two sewers do not merge" is not
currently enforced; at this rarity an overlap is unlikely rather than impossible. Enforcing it costs
rarity, which is why it is a question rather than a commit.

**One bug worth recording, because it was invisible from inside the game.** The first version clamped
only the FLOOR when sinking the piece tree, so a tree deeper than the available rock was pushed bodily
upward until it broke daylight - a tree spanning 0..57 under a surface at 65 came out at 12..69, ten
blocks of corridor in the open air. `SewerStructure.sink` now clamps both ends and returns empty when
they conflict, because a tree taller than the rock has no correct placement and no sewer is a better
answer than a broken one. It is pure arithmetic on purpose: both failures look like broken code from
in-world and neither throws.

**Acceptance:**
- A sewer generates with more than one level and branching corridors.
- The slab is deep enough that a sewer never punches into the void or through the surface.
- It is bounded. Two sewers do not merge, and one does not run for a thousand blocks.
- Nothing it places drops a member of `#minecraft:stone_crafting_materials`. **Asserted by a test that
  walks every block the structure can place**, not by reading the palette.
- It never opens into the void or the surface unintentionally.
- Water behaves per the decision in section 2 and does not flood the corridors on generation.

## Phase 3 - the inhabitants

**Ships:** the sewer is occupied.

Spawns are `spawn_overrides` on the structure rather than biome spawners, so the yard's surface stays
as it is.

**`spawn_overrides` is not enough, and phase 3 cannot start until this is answered** (found
2026-08-17, while reviewing the leachate decision). It replaces the *list* of mobs a structure offers;
it does **not** bypass `SpawnPlacements`, whose per-type predicate still runs. Measured against 26.1's
source, most of the inhabitant list cannot spawn in this world at all:

| Mob | Placement | Verdict here |
|---|---|---|
| **Drowned** | `IN_WATER`, which tests `getFluidState(pos).is(FluidTags.WATER)` | **Impossible.** Leachate is deliberately outside that tag - the property `leachate_is_not_water` exists to assert. The sewer's headline threat, and the source of the trident, would ship empty |
| **Turtle** | `ON_GROUND` + `y < seaLevel + 4` + `onSand` + bright | **Impossible three ways.** Sea level here is **-64**, so the y test alone demands y < -60, and this world has no sand |
| **Slime** | `ON_GROUND`, two routes | **Partly.** The slime-chunk route works (1 chunk in 10, `y < 40`); the surface route needs the biome in `#minecraft:allows_surface_slime_spawns` and y 50-70 |
| **Frog** | `ON_GROUND` + `#frog_spawnable_on` | Needs checking, same shape |

**This invalidates the premise of two decisions taken on 2026-08-17** - "the trident stays" and "slime
is both a mob and a material" - because both assumed the mobs can appear. Neither is wrong as a
*preference*; they are simply not yet deliverable.

Three ways out, none free:

1. **Pockets of real water.** Cheapest, and it walks straight back into the Rain Collector monopoly that
   choosing leachate was meant to protect. Would need the water to be unreachable or unbucketable, which
   is a gate built from geometry.
2. **Custom spawn placements**, via NeoForge's `RegisterSpawnPlacementsEvent` with `REPLACE`. Honest and
   small, but it changes those mobs' rules **globally** rather than inside the structure. Contained here
   only because the world has no other water.
3. **Structure-placed spawners**, the actual mineshaft parallel - vanilla puts a cave spider spawner in
   its corridors, and `SPAWNER` is already one of the six blocks the corridor hardcodes. Sidesteps
   `SpawnPlacements` and makes the encounter authored rather than ambient.

Option 3 is the one that fits this mod: it is what the structure being mirrored already does, it needs
no global change, and an authored encounter suits a finite structure that is cleared rather than farmed.

**Decided 2026-08-17 (owner): spawners for the drowned, and turtles stay.** They need different
mechanisms, and the difference is in vanilla's own code:

- **Drowned: a spawner, and nothing else is needed.** `Drowned.checkDrownedSpawnRules` has an explicit
  `EntitySpawnReason.isSpawner` branch that skips the water test entirely, so a plain spawner with no custom
  rules works underground in leachate. One per sewer, in the root chamber rather than at a corridor
  mouth, so meeting it is something you walk into. This is the mineshaft parallel exactly - vanilla puts
  a cave spider spawner in its corridors.
- **Turtles: placed as entities at generation.** Their predicate has no spawner branch, so a spawner
  would need `custom_spawn_rules` to bypass it - and a spawner endlessly producing a passive animal
  reads wrong. Placing them directly sidesteps every rule and makes the population **finite**, which is
  what this spec already wanted: they cannot breed here (no seagrass) or lay eggs (no sand), so a
  sewer's turtles are the turtles it was built with. **Three turtles and two frogs, one den each** - this
  said "two to four per chamber" until the dens landed, and they are not in the chamber at all now. Since
  2026-08-18 the count is a **ceiling the room has to earn**, not a constant: see the revision below.

**Revised 2026-08-17 after the first playtest, which found a zoo.** The report: *"the first thing I see
is a pool with lots of drowned, frog and turtle."* Three things were stacked in one room and each was
wrong on its own:

- **The animals stood in the leachate.** They were placed at the chamber centre, which is inside the
  pool, so every sewer generated with its turtles and frogs permanently sickened by
  `RCLeachateContact`.
- **The spawner sat five blocks from the ladder**, so a player climbed down into a crowd that had been
  accumulating since the chunk loaded. You arrived at the payoff instead of walking to it.
- **The chamber was the entrance and all the content at once**, which is backwards for a structure whose
  premise is exploring a sprawl.

**A den each, sand and mud** (owner). **Neither is a new material, and the first version of this
paragraph said mud was** - measured, and wrong:

- `#minecraft:convertable_to_mud` is dirt, **coarse dirt** and rooted dirt, and coarse dirt is this
  world's universal surface. The mod overrides no vanilla block tag except `mineable`.
- `PotionItem.useOn` turns any of them into mud with a **water bottle**, no tool and no gate.
- A water bottle needs no travel: `RainCollectorCoreBlock` hands one back for a glass bottle, and glass
  bottles are a **household** find in the starting biome.

So mud has been available on day one since the Rain Collector shipped, and packed mud and mud bricks
with it. The frog den is **the first place mud appears as terrain, not the first place it exists** -
which is a far weaker claim than the one that was nearly written into the record as a justification.

This is the third time this exact mistake has been made here, and the first two are already written
down: the glass-bottle gate that rested on the world having no `minecraft:glass`, and the iron gate
(#91) that rested on there being no pickaxe. **Scarcity measured by reading the mod's own content is
not scarcity** - what decides it is what vanilla does with the world's substrate. Sand is likewise not
new; sledgehammering Reinforced Concrete already yields it.

**Still worth an owner's eye:** if mud was meant to be sewer-gated rather than merely sewer-*flavoured*,
that is a code change (an override of `convertable_to_mud`) and not a doc one.

The substrate is the mechanism as much as the look:
`#minecraft:frogs_spawnable_on` is grass block, mud and the two mangrove roots, so **mud is the one
member a sewer could plausibly hold**; and `TurtleEggBlock.onSand` is half of vanilla's turtle rule. The
animals stand on ground their own game logic names. Neither becomes renewable - the other half of the
turtle rule is `y < seaLevel + 4` against a sea level of **-64**, and a frog needs light this place does
not have.

The dens hang off the chamber's high-X wall, which every corridor mouth is far from (children all grow
from the min corner), and they are attached **deterministically** rather than grown from the graph: "one
module each" is a promise about the population, and a random walk cannot promise exactly one of
anything.

**Revised 2026-08-18 after playtest: "turtles are getting stuck in the wall and dying."** They were, and
the cause is a number nobody looked up. **`EntityType.TURTLE` is `sized(1.2F, 0.4F)` - a turtle is wider
than the block it stands on.** The den was 6x4 with a 4x2 interior and seeded its three residents on a
one-block pitch starting in the corner, which is correct only for something under a block across: each
turtle spawned overlapping a neighbour by 0.2 and the wall behind it by 0.1, *on the tick it generated*.
`Entity.isInWall` then samples a box 0.8x the body width across the eye, so the first shove put one
inside the brick and started a suffocation clock. Measured on a fresh den: **one dead and one on 18/30
health within 120 ticks.**

Three things changed, and only one of them is the turtle den:

- **Placement is derived from the resident's hitbox** (`SewerDen.residents`). Residents spread along the
  interior's longer axis at a pitch of body width plus 0.2, centred as a group so the clearance goes to
  the walls rather than to the gaps. The same function serves both dens: the turtle den is long in X and
  the frog den, rotated against the south wall, is long in Z.
- **The room decides the headcount.** `population()` is a request; `residents()` places what fits. A den
  that ever gets smaller generates short rather than lethal, and
  `the_dens_animals_can_live_in_them` fails in CI when the two disagree.
- **The turtle den is 8x4, not 6x4.** Four of interior holds two turtles with elbow room and three only
  by pressing all three into the brick, so the third turtle costs two blocks. It grows along **X**, away
  from the chamber, which is the only free direction: deepening it in Z walks into the east corridor's
  mouth on the smallest chamber the room can roll.

**The old den test could not see any of it**, and that is the more useful half. It counted heads
immediately after `postProcess` - the one moment the room is correct, because nothing has moved or been
hurt yet. Suffocation is a tick-loop fact, so a test that never ticks asserts the placement and calls it
the habitat. The new one runs 120 ticks and then asks whether the animals are alive, unhurt, and out of
the walls.

**The threat moved deeper.** Junctions past the second link carry the spawner now, selected by a hash of
the piece's own box rather than a roll - `postProcess` runs once per chunk a piece overlaps, so a random
draw would answer differently on each pass and a junction could get a spawner in one half of itself and
not the other.

`each_den_holds_its_animals_on_its_own_ground`, `the_root_chamber_is_quiet` and
`a_deep_crossing_carries_the_spawner` assert the three halves of that.

**Two more came out of review, and both were bugs the first three could not see:**

- **A den needs a door.** Every wall was written and nothing was ever carved, so each den generated as
  an airtight box with three turtles sealed inside - a feature no player could enter, see, or know
  existed. Floor material and head count are both perfectly true inside a sealed box, which is exactly
  why the original test passed it. `a_den_opens_into_the_chamber` asserts the opening.
- **A den must land on nothing.** Dens are added after the graph is built, so `findCollisionPiece` never
  sees them, and they `postProcess` last - wherever they overlap something, they win silently. The first
  placement claimed the high-X wall was clear of every corridor mouth; it is clear of **three**. The
  east child anchors at `maxX + 1` over `z = minZ..minZ+4`, exactly where the turtle den sat, and it
  walled that branch shut on every sewer that grew one. The dens also overlapped each other whenever the
  chamber rolled its two smallest sizes. `the_dens_land_on_no_corridor_and_not_on_each_other` sweeps
  every chamber size, because both bugs were size-dependent.

**And the spawner guarantee was traded away unnoticed.** The chamber placed one unconditionally;
junctions place one only past depth 2 and only when the box hashes even, so a sewer could generate with
**no drowned at all** - and `IN_WATER` means there is no other route to one. Loosened to one in two, and
`most_sewers_get_a_drowned_spawner` measures the coverage across 200 layouts rather than assuming it.

**Extended 2026-08-17 (owner): slimes spawn naturally; frogs and turtles are limited.** Three
mechanisms for four mobs, and each one is the cheapest thing that actually works:

| Mob | How | Why not the others |
|---|---|---|
| **Drowned** | spawner | `IN_WATER` can never be satisfied by leachate; `checkDrownedSpawnRules` has an `isSpawner` branch |
| **Slime** | natural, via an `OR` predicate gated on being inside a sewer | Vanilla's two routes are both closed here: the surface route needs a swamp biome tag, and the slime-chunk route is y<40 in one chunk in ten - slimes in a tenth of the lower corridors is a coincidence, not a population |
| **Roach** | natural, its own `REPLACE` rule | Ours, in no biome's list, and previously reachable only by being disturbed out of a garbage block |
| **Turtle, Frog** | placed, finite | A turtle wants `y < seaLevel + 4` against sea level **-64**; a frog wants `#minecraft:frogs_spawnable_on` - grass block, mud and the two mangrove roots - plus a brightness check. Owner call: limited populations, so placing them is the design as well as the mechanism |

**The slime relaxation is contained by the predicate, not by an argument.** It tests
`getStructureWithPieceAt`, so it cannot fire outside a sewer even if some future biome or structure
lists slimes - relying on "nothing else offers them" would be true today and silently false later.
`no_biome_offers_the_sewer_only_mobs` guards the second line of defence anyway.

The spawner stays **drowned-only**: slimes and roaches now have natural routes, so putting them in the
spawner as well would be a second mechanism for a solved problem.

Worth knowing before tuning: **most of these cannot renew here, which suits a finite sewer.** Turtles
need sand to lay eggs and this world has none. Frogs need magma cubes for froglights and the Nether is
locked. So they are finds, not farms.

- **Roaches** already exist and already have a food line. Free, and thematically exact.
- **Drowned** are the threat, and their loot stays **vanilla, trident included.** An earlier draft
  called the trident a power spike "in a world with no weapons tier at all"; that is wrong at this
  depth. A player who has reached a sewer has iron and sticks, so iron tools and armour already exist.
  The trident is a prize, not a break.
- **Frogs and turtles** are atmosphere and a payoff for a player who wanted life back.

**Cobwebs and slime (decided 2026-08-02).** Both are sewer-exclusive, and a reachability closure
confirms neither has any other route in this world.

- **Cobwebs** generate in the corridors the way they do in a vanilla mineshaft, which this structure
  already mirrors. Harvesting one needs **shears** (a sword yields string instead), and found used
  shears were decided the same day, so a player may arrive with them; iron shears are craftable in the
  yard regardless. Their value here is **atmosphere rather than material** - string already comes from
  mattress teardown and from the yard's spiders, so nothing downstream waits on it.
- **Slime** is the reverse: near-worthless now, real later. A slimeball unlocks almost nothing on its
  own, because its payoff is the **sticky piston** and a piston needs redstone, which does not exist
  yet. Slime is a deposit against the redstone tier.
- **Both, decided 2026-08-17 (owner).** Slimes spawn via `spawn_overrides` **and** slimeballs appear as
  sewer material. The consequence is deliberate and worth stating: the inhabitant list stops being
  passive-apart-from-drowned, so the sewer becomes somewhere you fight through rather than somewhere you
  pick through. Paired with the trident staying in, this is the mod's first genuinely combat-shaped
  content, and phase 3 should be judged as such rather than as a loot pass.

**Acceptance:**
- The yard's surface spawn list is unchanged.
- Density is survivable for a player in the gear the sewer implies: iron armour, iron tools.

## Phase 4 - the loot

**Ships:** a reason to have come.

**Shipped 2026-08-17; moved out of the root chamber the same day.** Two barrels, at fixed positions for
the reason everything else placed by a piece is: `postProcess` runs once per chunk the piece overlaps,
so anything *rolled* there is rolled several times over.

**They live in an access chamber now, not at the foot of the ladder.** The root chamber held them
because that is where the code could reach, not because anyone would store anything in the first room
off the entrance - see the improvements phase 2 below.

**The table is set, not rolled.** `setLootTable` defers the roll to the first time a player opens the
barrel, so generation decides nothing, two players on the same seed do not see each other's rolls, and
the contents stay a datapack question - which is where the balance of this belongs.

**A component pool, added 2026-08-17 (owner): "loot should include one or more components."** Its own
roll rather than an entry competing with bulk salvage for a slot, offering Bulb, Pump, Motor and Machine
Frame.

**Why this does not skip a tier, which is the criterion it has to clear.** All three of the interesting
ones are `blueprint_crafting` - gated on a blueprint that teardown teaches - so a found component is
**one unit that teaches nothing**. The player still cannot make a second without doing the teardown, and
the machines those parts go into need Steel I-Beams and the yard the sewer is already in. It is a
head start, not a shortcut.

**And it answers the acceptance line the rest of the table could not.** "The reward is worth a cleared
sewer, stated as a comparison against what the same time spent picking garbage yields" - bulk salvage
*is* what picking garbage yields, so a table made only of it could never clear that bar however the
weights were set. A Motor is something sorting will never hand you.

**It is otherwise deliberately dull, and that is the acceptance criterion working.** "Nothing in it skips a tier"
rules out everything exciting: no iron, no gems, no blueprints, no bucket. What is left is bulk salvage
(scrap metal, plastic scrap, e-scrap, rebar, cullet glass), string and bone, and three uncommon lines -
a glass bottle, a nautilus shell, a name tag. The *value* question is #36's and is not answered here.

**One thing flagged rather than decided:** the glass bottle is `found_only` and its scarcity is what the
P1.10 water economy leans on. A sewer barrel is a second route to one, bounded by travel and a finite
structure. Recorded in `progression_gates.md` for the balance pass.

**Acceptance:**
- The reward is worth a cleared sewer. Stated as a comparison against what the same time spent picking
  garbage yields, rather than as a vibe.
- Nothing in it skips a tier. Check `progression_gates.md` before adding any item.
- No loot is renewable, or the sewer stops being finite in the way that matters.

## Phase 5 - the surrounding work

- **Guidebook:** *shipped.* `demolition/sewers`, two pages - what the pad looks like from above and that
  a Prybar is the only thing that lifts it, then what is down there. A player who finds the pad and
  cannot act on it reads the whole structure as scenery.
- **Jade:** *shipped.* The manhole is registered against the existing `ToolHintProvider`, which already
  answers "Prybar" for Bulky Waste - the same gate, so the same hint. Nothing new was written.
- **JEI:** nothing. There is no recipe here.
- `progression_gates.md`: *shipped.* A section under the yard's tier listing every yield and its gate,
  plus the glass-bottle note for #36.
- **#44** gets a comment explaining that sewers answer its want a different way, and a decision on
  whether renewable water life is still wanted on top.

---

## The progression question, stated rather than buried

A sewer is the first content in this mod that is **cleared rather than worked**. Every other system is
a loop: garbage regrows, mounds retire, the ladder climbs. This is a place you use up.

That is fine and probably good for pacing, but it changes what the yard is for. Today the yard is where
iron comes from and you return to it. With sewers it also becomes somewhere you go *hunting*, and the
two want different densities. If sewers turn out to be the reason people travel, the yard's own
material economy may need retuning with them rather than around them.

## Open

- **~~How much deeper the slab goes.~~** Answered 2026-08-17 and shipped: 55-61 blocks of tunnelable
  rock against a requirement of 45. See phase 2.
- **~~What sewage is, mechanically.~~** Answered 2026-08-17: it is leachate, and there is no filter.
- **~~How deep leachate lies in a corridor.~~** Answered 2026-08-17: one block. The drowning half of
  that answer was reversed the same day - leachate drowns you now, by an explicit air drain rather than
  by the fluid flag, which never worked.
- **Whether Hunger-on-contact is right for a structure you spend minutes in**, or wants its own number.
- **Phase 0's answer.** Held torch light may not survive contact.
- **~~Slime as a mob or as a found substance.~~** Answered 2026-08-17: both.

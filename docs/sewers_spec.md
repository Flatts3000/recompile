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
| Look | **Brick corridors, large brick rooms, scattered pipe, flowing water** | |
| Extent | **Finite per sewer** | One is cleared and done. The world holds more |
| Inhabitants | **Roaches, frogs, turtles, drowned, slime** | Slime added 2026-08-02; see phase 3 for the mob-or-substance question |
| Cobwebs | **Generated in the corridors** | Decided 2026-08-02. The mineshaft parallel, and the only source in the game |
| Reward | **Barrels with real loot** | Finite content needs a reason to clear it |
| Generation | **A custom Java `StructureType`** | Vanilla mineshaft sprawl is code-backed; jigsaw would read like a bastion |
| Water | **Leachate** (owner, 2026-08-17) | The fluid the dump already drains. Not water, asserted; no route to water at all; no new fluid and no filter machine |
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
  `tickRate` 15 makes it crawl - tuned so "a broken pond edge weeps rather than floods", which is
  verbatim the phase 2 acceptance criterion about not flooding corridors on generation.
- **It is what a sewer under a landfill would actually carry.** Rain falls through refuse and comes out
  the bottom as this. The fiction needed no invention.

**Two consequences, because leachate was built for puddles and a sewer is not one.**

1. **The sewer would be leachate's first deep body, which makes `canDrown` reachable for the first
   time.** The flag is set in `RCFluids` and has been inert since the fluid shipped - its own javadoc
   says so: "`canDrown` is set but unreachable because pools are one block deep". `LeachatePoolFeature`
   has `DEPTH = 1`, and `RCLeachateContact` checks the entity's **feet** with a comment explaining that
   an eye check would never fire on anything taller than a chicken. Two blocks of leachate in a corridor
   turns all three of those statements false at once and adds drowning to a structure whose hazard
   budget nobody has set. **Corridor depth is therefore a decision, not a detail.**
2. **Hunger was tuned for a puddle you cross, not a corridor you wade.** `LeachateBlock.sicken` applies
   Hunger, refreshed rather than stacked, so it holds steady instead of banking - correct for a pool you
   step through in a second, and an open question for a structure you spend minutes inside.

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

## Phase 1 - the manhole

**Ships:** you can find a manhole in the yard and open it. It leads to a stub shaft, not a sewer yet.

- A `manhole` block, plus the 3x3 Reinforced Concrete surface pad that marks it.
- Prying it: the Bulky Waste pattern (`BulkyWasteBlock`), right-click with the prybar, one action.
  Without a prybar, the same "you need a Prybar" nudge.
- Placement at vanilla mineshaft frequency, restricted to the `demolition_yard` biome.

**Acceptance:**
- Walking a fresh yard finds manholes at roughly vanilla mineshaft density. Measured over a sampled
  area, in the manner of the region distribution measurement in #88, not eyeballed.
- The pad is visible from the ground, not only from the air.
- Prying without a prybar does nothing and says why. Prying with one opens the way down.
- A GameTest covers the tool gate. Density is a worldgen measurement, not a GameTest.

**Risk:** a 1x1 hole in a large biome is either exciting or miserable. The 3x3 pad exists to make it
findable; if playtest says it still is not, the pad grows before the rarity changes.

## Phase 2 - the sewer itself

**Ships:** the structure. Corridors, rooms, levels, pipes, water.

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
- **Undecided: slime as a mob or as a found substance.** A spawning slime adds a combat encounter to an
  inhabitant list that is otherwise passive apart from drowned, which changes what the sewer feels
  like. That is a design call, not a loot-table entry.

**Acceptance:**
- The yard's surface spawn list is unchanged.
- Density is survivable for a player in the gear the sewer implies: iron armour, iron tools.

## Phase 4 - the loot

**Ships:** a reason to have come.

Barrels, per the design call. The loot table is the whole design here and it is a balance question, so
it lands with #36 rather than being invented now.

**Acceptance:**
- The reward is worth a cleared sewer. Stated as a comparison against what the same time spent picking
  garbage yields, rather than as a vibe.
- Nothing in it skips a tier. Check `progression_gates.md` before adding any item.
- No loot is renewable, or the sewer stops being finite in the way that matters.

## Phase 5 - the surrounding work

- **Guidebook:** what a manhole looks like, and that a prybar opens it. A player who finds the pad and
  cannot act on it will read it as scenery.
- **Jade:** the manhole gets the existing tool-hint provider. Nothing new.
- **JEI:** nothing. There is no recipe here.
- `progression_gates.md`: add the sewer and everything it yields.
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
- **How deep leachate lies in a corridor**, which decides whether drowning enters the sewer at all -
  see the two consequences in section 2.
- **Whether Hunger-on-contact is right for a structure you spend minutes in**, or wants its own number.
- **Phase 0's answer.** Held torch light may not survive contact.
- **Slime as a mob or as a found substance.**

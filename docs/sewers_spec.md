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
| Entry | **Prybar on a manhole** | Reuses the Bulky Waste loop exactly: a surface find, tool-gated, one action. No new verb, no new tool |
| Surface marker | **A 3x3 of Reinforced Concrete with the manhole at its centre** | Reads as deliberate rather than as terrain noise, and it is built from a block the yard already has |
| Rarity | **Vanilla mineshaft frequency** | `frequency: 0.004`, `spacing: 1`, `separation: 0`, `legacy_type_3`. Copied from `structure_set/mineshafts.json` rather than guessed |
| Shape | **Vanilla mineshaft sprawl and levels** | Corridors that branch and descend, not a single floor |
| Look | **Brick corridors, large brick rooms, scattered pipe, flowing water** | |
| Extent | **Finite per sewer** | One is cleared and done. The world holds more |
| Inhabitants | **Roaches, frogs, turtles, drowned** | |
| Reward | **Barrels with real loot** | Finite content needs a reason to clear it |
| Held light | **Torches light while carried** | See phase 0; this is the one item that may not be buildable |

## 2. The four constraints that shape everything below

**The iron gate is the thing most likely to be broken by accident.** Nothing in this mod can mine
stone: sledgehammers are tag-gated to `recompile:reinforced_concrete` alone and no tool carries
`mineable/pickaxe`. That absence is the *entire* mechanism keeping a vanilla furnace uncraftable, and
the Cupola upgrade path with it. Bricks were checked and are safe -
`#minecraft:stone_crafting_materials` is only cobblestone, blackstone and cobbled deepslate. **Any new
sewer block must be checked against that tag before it ships**, and `progression_gates.md` in the
Trashlands repo is the place that tracks what is reachable when.

**Brick cannot be taken home, and that is a decision to make rather than a bug to find.** Brick needs a
pickaxe to drop. With no pickaxe, a player will walk brick corridors they cannot harvest. Either that
is intentional (the sewer is a place, not a quarry) or a bespoke drop rule is needed. **Do not solve it
by adding a pickaxe.**

**This mod has no mixins.** That is a standing architectural rule, and it is what makes the held-light
requirement a research task rather than a feature.

**Sewer water arrives at almost exactly the same time as buckets.** A bucket is three iron, iron comes
from the yard, and the sewer is *in* the yard. So a source block down there ends the Rain Collector's
monopoly (a locked P1.10 decision) the moment sewers are reachable. Two ways out, both fine, but one
must be chosen: flowing water with no source blocks (unbucketable, still swimmable for the mobs), or a
sewage fluid that has to be filtered, which earns a machine instead of undercutting one.

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

**The architectural fork, which has to be decided first.** `minecraft:mineshaft` is a **code-backed
structure type**, not a data-driven one - its `mineshaft_type` picks block palettes hardcoded in Java,
so it cannot be reskinned to brick from a datapack. So either:

- **A custom `StructureType` in Java**, mirroring vanilla's `MineshaftPieces`. Gives true mineshaft
  sprawl and branching, and is the larger job.
- **Jigsaw with template pools.** Data-driven and far cheaper, but the sprawl reads differently:
  jigsaw builds from authored rooms rather than generating corridor runs, so it feels more like a
  bastion than a mineshaft.

This is **the mod's first real structure either way.** There is exactly one `.nbt` in the repo today
and it is the gametest plot, so structure sets, template pools and processors are all new surface.

**Acceptance:**
- A sewer generates with more than one level and branching corridors.
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
- **Drowned** are the threat. They drop copper ingots, which is harmless since copper is already the
  everyman metal, **but they also drop tridents.** In a world with no weapons tier at all, one trident
  is a far bigger power spike than in vanilla. Decide the loot rather than accepting vanilla's.
- **Frogs and turtles** are atmosphere and a payoff for a player who wanted life back.

**Acceptance:**
- The yard's surface spawn list is unchanged.
- A test asserts the trident decision, whatever it is. This is the kind of thing that ships by
  accident.
- Density is survivable for a player in the gear the yard implies: no armour tier, a sledgehammer.

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

- **Which structure approach** (custom Java pieces vs jigsaw). Phase 2 cannot start without it.
- **Water or sewage.** Section 2 names the two options; the choice is a water-economy decision.
- **Whether brick is harvestable**, and if so by what.
- **The trident.**

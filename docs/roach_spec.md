# Roach - spec (issue #78)

**Status: SHIPPED** (#78). All four phases landed 2026-07-31 to 2026-08-01 (PRs #79 entity, #80 food,
#81 trigger and copy, #83 skin and spawn egg), and the spawn rate was retuned to one per 128 blocks of
garbage on 2026-08-02 (PR #98). The dump's one native creature, and the mod's **first entity**. Every
decision below was made in the design session; the phase order it describes is the order it was built.

## 0. The idea, and why it fits

A roach scuttles out of a garbage block you are picking through.

**It introduces threat without touching the empty-spawner design.** `household_sprawl` has every spawner
list empty on purpose (P1.9) - which is why food comes from tin cans and foraged mushrooms rather than
mobs. A roach that comes out of a *block you disturbed* is not a spawn: it is a consequence of an action
the player chose. The biome stays creature-free and the dump still bites.

## 1. Decisions

| Decision | Answer | Why |
|---|---|---|
| Entity or reskin | **A real `recompile:roach`** | Owning it makes the no-swarm rule free rather than a fight with vanilla, and nothing leaks into other mods' silverfish |
| Trigger | **The pick-through pull** | `SortableBlock` already rolls per right-click; no new block, and the thing you rummage in is what bites |
| Swarm | **One per disturbance** | The starting biome is trash tools and no armour; a cascade there is a spike in the wrong place |
| Drop | **Raw Roach -> Cooked Roach** | A protein is its own line; organic muck would have made roaches compete with the Compost Heap for the material the food economy runs on |
| Spawn egg | **Yes** | An entity with no egg cannot be placed by hand in creative, which is the loop this gets tuned through |

**The Burn Barrel needs no change.** Its rule is `input.has(DataComponents.FOOD) || input.is(BURN_BARREL_SMELTABLE)`,
so anything edible burns by construction. Raw Roach qualifies the moment it carries `food(...)`. No tag
edit, and the iron gate is untouched because this is food, not metal.

---

## Phase 1 - the entity layer

**Ships:** a roach you can spawn from an egg in creative, that walks, takes damage and dies. No world
integration, no drops.

- `RCEntities` (new) - the mod's first `DeferredRegister<EntityType<?>>`. Wire it into `Recompile.java`
  in the existing order, after `RCItems` (the spawn egg is an item and references the type).
- `EntityType` built from `Monster` or a trimmed subclass. **Do not extend `Silverfish`** - inheriting it
  inherits the summon behaviour, which is the one thing being deliberately left out.
- Attributes via `EntityAttributeCreationEvent`. Silverfish-ish: 8 health, low damage, fast, small hitbox.
- Renderer registered client-only through `RegisterRenderersEvent`, reusing **`SilverfishModel` and
  `ModelLayers.SILVERFISH`** (both confirmed present in 26.1). The entity is ours; the mesh and animation
  are vanilla's, so the art budget is one skin.
- Spawn egg item + creative tab entry + lang.

**Risks, both real:**

- **26.1 entity registration is unverified here.** Every other subsystem in this mod hit an API delta
  (`Identifier`, singular data dirs, `BreakBlockEvent`, energy moving to the transfer API). Assume entity
  registration and renderer events have moved too, and check the jar rather than a tutorial.
- **texgen has no entity kind.** `Surface.subdir` returns `item` or `block` and nothing else, so an
  entity skin has nowhere to go. Either add an `entity` kind to the toolkit (small, and it is where the
  pipeline should own it) or place this one texture by hand and document the exception. **Decide before
  generating**, not after.

**Proves it works:** spawn from the egg in `runClient` and confirm it renders and moves. A GameTest can
assert the type registers and spawns; it cannot see the model.

## Phase 2 - the food line

**Ships:** Raw Roach and Cooked Roach as items, cookable, edible. Independent of Phase 1 and buildable in
parallel - nothing here needs the entity to exist.

- `RAW_ROACH` with `food(...)`, `COOKED_ROACH` with better `food(...)`.
- `recipe/cooked_roach.json` - ordinary `minecraft:smelting`.
- texgen surfaces for both icons (normal `kind = "item"`, no toolkit change needed).
- Loot table for the entity, dropping Raw Roach.

**The claim worth pinning with a test:** the Burn Barrel accepts Raw Roach *without any tag change*,
because the FOOD component matches. That is the whole reason this drop was chosen, and it rests on
behaviour rather than on a list, so it should fail loudly if the barrel's rule ever narrows.

**Numbers are first-pass** and land in #36. See the progression note below - nutrition is a lever there,
not just flavour.

**Anti-farm, stated so it is not mistaken for an oversight:** the entity loot table carries
`minecraft:killed_by_player`. A roach that dies to fall damage, to another mob, or in a grinder drops
nothing. This is the earliest renewable food in the game and it comes out of a block anyone can reach on
day one, so it has to stay *hand-earned* - automatable roach protein would undercut the ladder far more
than its nutrition value suggests. Both halves are tested.

## Phase 3 - the trigger

**Ships:** roaches actually come out of garbage. Needs Phase 1.

- Hook `SortableBlock`'s pull: one pull in N releases a roach *instead of* an item.
- Config-gated with the rate in `RCConfig`, per the mod's standing "everything ships config-gated,
  defaults are the design".
- Static entry point for tests, the `sortOnce` convention - so a GameTest can force the roach branch
  rather than rolling for it.

**Tests:** a forced pull releases exactly one roach; it never summons others; a normal pull is unchanged.

## Phase 4 - the surrounding work

- **Jade**: nothing needed - it is a mob, not a block.
- **JEI**: an info panel for Raw Roach, since no recipe explains where it comes from.
- **Guidebook**: one short entry. This is the first thing in the game that fights back, and a player who
  meets it with no warning will read it as a bug rather than a mechanic - which is exactly what happened
  with the demolition yard's grass.
- `progression_gates.md`: add Raw Roach to Tier 0. It is a real gate change (see below).

---

## The progression question, stated rather than buried

**This is the earliest renewable food in the game, and it arrives at tier 0.**

The early food line is deliberately thin: an opened tin can (nutrition 4) and dump mushrooms
(nutrition 2), both *found*, not farmed. Renewable protein does not exist until **rung 5** - Animal Bait,
which sits behind the Compost Heap, Fertilizer and the whole reclamation ladder.

Roaches come out of the first garbage block a player touches. So this puts repeatable, cookable protein
ahead of farming, animals, and everything the ladder was built to gate. That may be exactly right - the
dump feeding you badly is good theme, and hunger that never resolves is not fun - but it is a
**progression change, not a flavour addition.**

Levers, in order of preference: **low nutrition** (below the tin can's 4), then **drop rate**, then a
**saturation penalty** so it reads as survival food rather than good food.

## Open

- **Does it appear in the demolition yard too?** The yard already has four hostile spawns; the mechanic
  is about the *starting* biome having one thing that reacts to being disturbed. Leaning sprawl-only.
- ~~**Does it burn in the Cupola as well?**~~ Answered by #91: no. The Cupola became a blast-only metal
  furnace, so it does not cook. Food stays with the Burn Barrel, which is where a roach belongs anyway.

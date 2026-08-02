# Blueprints: teardown-as-knowledge - spec

**Status: design locked 2026-08-01, phase 1 proven, rest not built.** The mod's namesake mechanic.
Recompile has always been described as "disassemble items to recover their recipes, not just their
materials", and recovering recipes is the one thing it does not do. `TeardownRecipe` has parsed a
`teaches` field since Phase 0 and nothing has ever consumed it; `RecompileWorkbenchBlock`'s own javadoc
says it "ignores `teaches` entirely. No knowledge."

## 0. The idea

You tear down the junk of a dead world until you work out how something was made, and then you can make
it again. Knowledge is a **Blueprint item**, the Immersive Engineering model: the item *is* the
knowledge, you keep it, and it goes in a bench that will not work without it.

**Why an item rather than a per-player unlock.** It needs no saved state, which is how this mod does
everything else: encroachment has no memory, the scrap network has no core, `FORMED` is a blockstate
rather than a BlockEntity. A blueprint you can hold, lose, and hand to somebody else is also a better
object in a mod about objects.

**Why it is the right thing to build for the jam.** The theme is the past. The reclamation ladder is
the past-as-ecology, a green world you bring back. This is the past-as-knowledge, and it is the more
literal read of the theme. Originality is the single largest slice of the judging, and "salvage yields
materials" is a well-populated genre while "salvage yields recipes, and you cannot make what you have
not recovered" is a distinct progression axis that reframes every shipped system as archaeology.

## 1. The proof of concept: the bed

The whole loop, in one artifact a player already understands.

1. A **Dirty Mattress** is a Bulky Waste find. It is already this world's bed: it sleeps and it sets
   spawn.
2. Tear it down at the Recompile Workbench. Repeated teardowns eventually **teach the Clean Mattress
   blueprint**.
3. Blueprint plus materials at the blueprint bench makes a **Clean Mattress**, which is craftable no
   other way.
4. **Clean Mattress plus 3 planks makes a vanilla bed**, and that is the only bed recipe left in the
   game.

**The motivation is dignity, not stats** (owner, 2026-08-01). The bed gives no mechanical advantage
over the mattress and does not need to. Nobody wants to sleep on a filthy mattress on the floor, and
the recipe change makes the clean version the only way out of that.

**It reverses a locked decision, deliberately.** `MattressBlock`'s javadoc states in bold: *"It is not a
stepping stone to a real bed - it IS the bed."* That was P1.11, written when there was no wood and no
path to a bed at all. Recorded here rather than quietly contradicted.

**There is a second route and that is the point.** Sheep are in `bait/herbivore`, so wool and a vanilla
bed are reachable through the reclamation ladder at rung 5. Blueprints reach the same object far
earlier, because mattresses are day-one finds. The entry gets a real thesis out of that: **heal the
world until it can give you wool, or learn how they used to make it.** The two axes converge on the
same object from opposite directions.

> **Correction (2026-08-02).** The two-route framing above did not survive phase 1. Phase 1 deleted
> every wool-to-bed recipe, so healing the world to rung 5 no longer reaches a bed at all - there is
> exactly **one** route now and it goes through the blueprint. Wool did not stop mattering; it changed
> sides. Under the string-and-wool recipe it is an **ingredient to** the blueprint route rather than an
> alternative to it. The convergence was a good idea that the gate it depends on made impossible, and it
> is recorded here rather than quietly left in place, because a spec that describes a route the code
> removed is how a wrong plan gets built twice.

## 2. Decisions

| Decision | Answer | Why |
|---|---|---|
| Knowledge is | **A Blueprint item** | The item is the knowledge. No saved state, tradeable, losable |
| Model | **Immersive Engineering's** | Blueprint sits in a bench; the bench does nothing without one |
| Gating | **Blueprint-only recipes** | A Clean Mattress cannot be made anywhere else, including the Scrap Crafting Table |
| Source | **`teaches` on teardown** | The schema has been waiting since Phase 0 |
| First object | **Clean Mattress -> vanilla bed** | Uses shipped content, legible gate, payoff the player already understands |
| Clean Mattress recipe | **Shapeless: string + wool** | Ordinary materials; the lock is the blueprint, not an exotic ingredient |
| Where knowledge lives | **Inventory or a Filing Cabinet** | A collection that grows without bound cannot live in a backpack |
| Where it can be used | **This mod's crafting table only** | A vanilla table would bypass the whole system the moment a player has wood |
| Bed recipe | **Clean Mattress + 3 planks, and nothing else** | 16 wool recipes removed |
| The draw | **Dignity, not stats** | The mattress stays fully functional |

## 3. Constraints

**The bed gate is 32 recipes and splits two ways.** Sixteen `<colour>_bed` recipes turn wool into a bed
and are the door being closed. Sixteen `dye_<colour>_bed` recipes recolour a bed that already exists,
create nothing, and **stay** - a player who earns a bed should still be able to paint it. Sixteen files
is exactly the surface where fifteen get done, which is why the gate has a test rather than a checklist.

**This needs a fourth custom screen, and that is a recorded reversal.** CLAUDE.md holds the rule that no
new machine screen ships without one being written down, after the count silently drifted from one to
three. A blueprint slot plus a recipe list has no vanilla screen to borrow, which is the same test the
Burner Generator and Tree Nursery passed. Write it down; do not let it drift again.

**`scraps_required` implies state this mod has never had.** The schema's shape is
`{ "recipe": ..., "chance": 0.25, "scraps_required": 3 }`, which reads as "accumulate partial
understanding across several teardowns". A blueprint that is an item has nowhere to keep half of
itself. This is the one genuine design hole and it is called out in phase 3 rather than discovered
there.

## Phase 1 - close the bed door (DONE, proven)

**Ships:** wool no longer makes a bed, in any colour.

- 16 overrides at `data/minecraft/recipe/<colour>_bed.json`, each carrying a `neoforge:false` condition.
  Overriding a vanilla recipe path from a mod datapack replaces it; a never-true condition then stops
  the replacement loading, which deletes a vanilla recipe with no mixin. The mod already uses
  `neoforge:conditions` for the guidebook, so the mechanism was known to work here.
- The 16 dye recipes are untouched.

**Acceptance (met):**
- `wool_can_no_longer_make_a_bed` drives a real 3x3 crafting input for **every** wool in the registry
  against the recipe manager and asserts nothing comes back. It also asserts it swept at least 16 wools,
  so a broken sweep fails rather than passes.
- 253 GameTests green.

**Why this went first:** it was the only part that could have invalidated the design. If vanilla recipes
could not be removed cleanly, the Clean Mattress would have been a decorative alternative to a bed
anyone could already craft.

## Phase 2 - the blueprint item and the gate it opens

**Ships:** a Blueprint item that exists, names a recipe set, and is required by nothing yet.

- `BlueprintItem` plus a `DataComponentType` naming which blueprint it is (`RCDataComponents` is the
  established place; the Rain Collector's water component is the pattern).
- A `recompile:blueprint_crafting` recipe type whose recipes carry the blueprint they need.
- The Clean Mattress item, craftable by nothing.

**Acceptance:**
- A Clean Mattress cannot be produced by any `minecraft:crafting` recipe. Asserted by sweeping the
  recipe manager, in the manner of `no_smelting_recipe_turns_a_mod_item_into_iron` - that test exists
  because a gate documented in a comment and proven nowhere is how the iron gate stayed broken for
  weeks.
- A blueprint item with no component, or an unknown one, is inert rather than a crash.
- Blueprints stack sanely or do not stack; decide and assert it.

## Phase 3 - Idea Fragments

**Ships:** tearing down a find yields fragments of an idea, and enough fragments craft the blueprint.

**This is the answer to the open question**, decided 2026-08-02: partial progress is an item.

- An **Idea Fragment** item carrying the same blueprint-set component the Blueprint does. A fragment
  for the Clean Mattress and a fragment for something else are different items, not one generic scrap.
- The Workbench reads `teaches` and grants a fragment on a successful roll. `scraps_required` becomes
  what it always read as: how many fragments make the sheet.
- **Fragments craft into the Blueprint** at the Scrap Crafting Table, N of the same set into one sheet.

Everything stays in items, which is how this mod does everything: encroachment has no memory, the
scrap network has no core, `FORMED` is a blockstate. Progress is visible in the inventory rather than
in a number nobody can see, and "tear down four mattresses and watch the pile grow" is a better beat
than four independent coin flips. It makes progress tradeable, which is a consequence accepted rather
than a flaw.

**Acceptance:**
- A forced teardown roll grants a fragment of the right set and never a fragment of another.
- Fragments of different sets do not stack together and do not combine into a blueprint.
- The rate is config-gated, per the standing rule.

## Phase 4 - the Filing Cabinet and the gate at the table

**Ships:** blueprint-gated recipes run at the mod's crafting table, and only while the sheet is
reachable.

**The Filing Cabinet is a Bulky Waste find** - one more thing pried out of a pile, not a crafted
machine. It holds an unbounded number of blueprints and nothing else. It joins
`#recompile:scrap_connectable`, so it is part of the Scrap Network by placement rather than by wiring:
put it against the crafting table and the table can read it.

**A blueprint recipe runs only if the sheet is reachable**, which means one of two things:

- it is in the crafting player's **inventory**, or
- it is in a **Filing Cabinet in the same scrap cluster** as the table.

The sheet is never consumed. Knowledge does not wear out.

**And only at one of this mod's tables.** A vanilla crafting table cannot run a blueprint recipe, which
is what stops the whole system being bypassed the moment a player has wood. The Scrap Crafting Table
already reimplements crafting over `AbstractContainerMenu` (vanilla's `CraftingMenu` hard-locks itself
to `MenuType.CRAFTING`), so the second recipe lookup goes in the same place the first one already is.

**Why a cabinet at all**, rather than leaving blueprints in the inventory: a collection that grows
without bound cannot live in a backpack, and the point of the mechanic is that it accumulates. The
cabinet is also what makes a base a workshop rather than a chest room, which is the same argument the
Scrap Network already won.

**Acceptance:**
- A blueprint recipe produces nothing at a vanilla crafting table, asserted rather than assumed.
- It produces nothing at the Scrap Crafting Table with no sheet anywhere.
- It produces with the sheet in the inventory, and with the sheet in an adjacent cabinet, and both
  paths are tested - one working is not evidence for the other.
- Crafting does not consume the blueprint.
- A cabinet holds more than a chest's worth, and survives break and replace with its contents.

## Phase 5 - content and framing

- `clean_mattress + 3 planks -> minecraft:white_bed`. White only; the 16 dye recipes handle colour, so
  you recover one design and choose your own paint.
- JEI: the blueprint recipes as their own category, with the blueprint as the catalyst. The Burn Barrel
  is the precedent for why this must be its own category rather than an entry under crafting.
- Guidebook: how a blueprint is found and what the bench is for. Without it the mechanic is invisible.
- `progression_gates.md` in the Trashlands repo: beds move behind blueprints, and the wool route is
  gone.

## Open

- **Partial progress**: fragment item, or flat chance. Phase 3 cannot start without it.
- **One blueprint, many recipes, or one each?** IE's blueprints are categories. Many-per-blueprint means
  fewer and more meaningful drops; one-each is a longer collection ladder and a simpler screen.
- **What else gets a blueprint after the bed?** The POC only earns its cost if there is a second and a
  third.

# The freight conversion

**Status: all five steps SHIPPED in v0.20.0 (epic #386).** Step 1 #392 (issue #387), step 2 #394
(#388), step 3 #396 (#389), step 4 #397 (#390), step 5 #398 (#391), and the conditional network sink
#395 (#393). The eight completion advancements ruled in section 2.5 followed in #434:
`recompile:freight/root` and `recompile:freight/tier_1..8`, granted to every player in the world when
a rung ships and again at login, alongside `FreightCompletion`'s bus event. **Where the build differs
from this plan:** step 4's
`every_shipped_blueprint_has_a_name_a_recipe_and_a_route` was left accepting a teardown teacher OR a
market offer rather than narrowed to the single market route.

**What this is.** The build plan for P3.10 (`../trashlands/docs/design_decisions.md`), which made the
economy the spine: tiers open by shipping named processed goods, the market is the only source of tier
knowledge, teardown yields function, and sorting yields bulk. Five build **steps**, each one shippable
on its own, ordered so the game is never broken between them.

**"Phase" means the in-game freight phase throughout** (there are eight, section 1.5). The build work is
numbered in **steps** (there are five, section 2). The first draft used "phase" for both and it was
unreadable.

**The audit it comes out of** is [`spine_audit.md`](spine_audit.md). Findings 1 and 2 are ruled;
findings 3, 4 and 5 are referenced below where they bite.

---

## 0. The shape, in one paragraph

A **Freight Terminal** accepts goods from pipes and the Scrap Network and counts them against the
current **freight phase**, a data-driven list of required item stacks. Completing a phase advances the
**world's tier**, which unlocks the next band of Blueprints at the **Buy Terminal**. Teardown stops
teaching and starts handing back working components, some of which cannot be crafted, so a tier can
want a thing that only comes out of the garbage. The Sell Terminal keeps doing what it does now and is
untouched.

## 1. Decisions this spec makes

### 1.1 Tier state is per-WORLD, not per-player

The scrip balance is per-player and `market_spec.md` calls that "the strong part", so this needs saying
explicitly: **tier is not.** Two reasons. In multiplayer a shared factory that advances one player's
tier and not their partner's is a bug nobody would call a feature, and Satisfactory (the reference for
the whole ladder) tracks phases per save. And the secondary spine restages the *world*, not a player.

Implementation is a `SavedData` on the overworld. Not a player attachment, and not config.

### 1.2 Phases are a recipe type, for the same reason market offers are

`recompile:freight_phase`, never matched against anything, `matches` returns false by construction.
This is the `market_offer` precedent (#370) and the reasoning carries: a phase is keyed by its own id
rather than by a registry entry, so there is no registry to hang a data map off, and a recipe file is
the other thing a pack already extends by dropping a file in.

```json
{
  "type": "recompile:freight_phase",
  "tier": 3,
  "name": "freight.recompile.phase.3",
  "requires": [
    { "item": "recompile:reinforced_concrete", "count": 200 },
    { "item": "recompile:steel_offcut",        "count": 50  },
    { "item": "recompile:circuit_powder",      "count": 20  }
  ]
}
```

**Counts are first-pass placeholders**, like every other number in this mod, and belong to the balance
pass (#36) rather than to this spec. **Eight phase files ship**, two per region, per section 1.5.

### 1.3 The terminal consumes on insert, and REFUSES what the phase does not want

Goods are consumed as they arrive and counted; there is no buffer to reclaim. That is Satisfactory's
behaviour and it avoids a chest-sized hole in the middle of the machine.

**The safety comes from refusing rather than from holding.** An item not on the current phase's list is
rejected at the slot, so a pipe backs up and the player sees it, instead of the terminal eating a stack
of something valuable. This mod's standing idiom is to fail closed and say why, the way the vacuum
names a pile it cannot take.

### 1.4 The Buy Terminal shows locked offers rather than hiding them

`market_offer` gains an optional `tier`. An offer above the world's tier is **listed, greyed, and
labelled with the tier that opens it**. Hiding them would make the shop look complete and the ladder
invisible; showing them is what teaches the player the ladder exists at all. It is also the only
teaching surface this gets, because the mod has no recipe book by standing decision.

### 1.5 Eight phases, two per region (owner, 2026-09-06)

Sprawl, demolition yard, radioactive dump, depths, two apiece. Eight unlock moments across a
playthrough, which is roughly Satisfactory's cadence and about twice what one-per-region would give.

The first of each pair should be satisfiable from that region's ordinary output; the second should
want something the region only yields once you have built for it. That is what stops a phase being a
wait rather than a problem, which is the failure mode section 4 warns about.

### 1.6 Late phases require GROWN goods, and this is what makes reclamation load-bearing

**Owner, 2026-09-06.** The late phases ask for wood, crops and animal products alongside processed
scrap. You cannot finish the ladder without healing land.

**This closes a gap the first draft of this spec admitted to** ("nothing here creates a reason to heal
the land") and it closes it in the engine rather than in the pack, so it works standalone. It also
does something bigger: it makes quarry-versus-heal a **live balance** rather than a deferred one.
Healing a region retires its mounds and removes its garbage supply; the same act creates the only
supply of grown goods. Late in the ladder you need both at once, so the player is running two economies
against each other rather than picking one.

**Ordering consequence for the phase data**: grown goods cannot appear before the reclamation ladder is
reachable, so they belong in phases 5 through 8 at the earliest. A phase 2 that wants wheat is a
softlock.

### 1.7 The final phase emits a signal and nothing more

Completing the last phase fires an advancement and a game event. **The engine attaches no meaning to
it.** What that completion triggers is pack content and lives in Trashlands. This is the standing
engine/pack split (`market_spec.md` section 5, and the split recorded in `../trashlands/docs/the_twist.md`),
and here it is also the reason a public spec can describe the whole ladder without describing what it
is for.

---

## 2. The build steps

Ordered by what breaks. **The load-bearing constraint is finding 3**: nine teardown recipes are
currently the only route to what they teach, so the market must stock those Blueprints *before*
teardown stops teaching them, or the items become unreachable and `FoundNotCraftedTests`' twin goes red.

### Step 1 - The Freight Terminal exists and does nothing yet

- `recompile:freight_terminal` block: a `Container` with open faces, joined to
  `#recompile:scrap_connectable` so the Scrap Network routes into it. **This is the ruled distinction
  from the Sell Terminal**, which takes no pipe input and stays exactly as it is.
- `recompile:freight_phase` recipe type + codec, and the shipped phase files.
- `FreightState` SavedData: current tier, progress against the current phase.
- A screen: the current phase's requirements, progress per line, tier number.
- Tests: insertion counts, refusal of off-list items, progress survives save/load, completion advances
  the tier, the last phase fires its advancement.

**Shippable and inert.** Tiers advance and unlock nothing. Nothing else in the mod changes.

### Step 2 - Tiers gate the Buy Terminal

- `market_offer` gains an optional `tier` (absent means tier 0, always available).
- The Buy Terminal reads world tier, lists locked offers greyed with their tier named, and refuses
  purchase of a locked line server-side as well as client-side.
- Tests: a locked offer cannot be bought by a crafted packet, not just by a disabled button.

**Now the ladder means something.** Still nothing removed.

### Step 3 - The market stocks everything teardown teaches

- Add a `market_offer` for each of the nine Blueprints currently taught by teardown, each with a price
  and a tier: `broken_hauler`, `broken_hydroponics_bay`, `broken_spawner`, `broken_terminal`,
  `depleted_battery`, `fridge`, `mattress`, `washing_machine`, `worn_forging_die`.
- Deliberately redundant for one step: both routes work.

**This is the step that makes step 4 safe.** It must land first.

### Step 4 - Teardown becomes function

- Strip `teaches` from those nine recipes; give each component outputs instead (`results`/`extras`).
- ~~Delete the Idea Fragment item, the `recompile:fragment_assembly` recipe type and its one
  recipe.~~ **WRONG, corrected during step 4.** The Sequencer *makes* those fragments and spawn-egg
  Blueprints are four of them, so deleting the item would have broken the exception ruled sanctioned
  in the same pass. What actually dies is teardown GRANTING them - the Workbench's `teach` and
  `grantFragment` are gone. The item survives, **renamed to Spawn Egg Fragment** (owner's suggestion),
  because a thing that only ever comes from amber and only ever makes a creature Blueprint should not
  be called an Idea Fragment.
- Update `every_shipped_blueprint_has_a_name_a_recipe_and_a_route` for the new single route.
- Blueprints, the Filing Cabinet and the Scrap Crafting Table are untouched: you still hold sheets and
  still craft from them, they just come from a counter now.

**The point of no return.** After this the market is the only source of tier knowledge.

### Step 5 - Tier-gating components go find-only

The narrow reversal of #228. **#228 still binds on every component this step does not name.**

- A new tag, `#recompile:function_only`, for components that exist only via teardown.
- Remove the `blueprint_crafting` recipes for its members.
- A test in the shape of `FoundNotCraftedTests`: nothing in the tag is craftable, and everything in it
  has a teardown route.
- Membership is deliberately left to the step rather than fixed here. The Motor is the obvious first
  member and is P1.4's own example.

**Renewability is the safety check.** A find-only component must come from something a mound regrows,
or it is a wall rather than a gate. That is the argument that reconciles the #228 reversal and it has
to be true per member, not in general.

---

## 2.5 Rulings taken 2026-09-06, after step 2 shipped

Fifteen open questions were cleared in one pass. The ones that change this spec:

**Tiers are assigned by IMPACT, not by materials.** A Blueprint's tier is set by how much it changes
the game rather than by where its parts come from, so the Hauler and the Hydroponics Bay sit late as
rewards even though their materials are cheap. This is a judgement call by construction and cannot be
derived, so step 3 drafts the nine with reasoning and stops for sign-off before proceeding.

**The second phase of each region must demand a machine.** The first of a pair is satisfiable from that
region's ordinary output; the second wants something only a built production chain yields. That is the
property section 4 says stops a phase being a wait rather than a problem, and it is now a requirement
on the phase data rather than an aspiration.

**The Scrap Network gains a conditional third sink** (#393): the Freight Terminal accepts a route only
for goods the CURRENT PHASE is asking for, and everything else flows past to the bins and the barrel.
That solves the priority problem by making it conditional rather than ordered. The cost is recorded:
routing now depends on live phase state, which nothing else in `ScrapNetwork` does, so
`insertFromMember` gains its first read of world state.

**Completion grants eight advancements, one per tier, under a shared root.** Not a custom criterion
trigger - a plain named advancement per rung, which FTB Quests can already watch and which needs no new
API surface. The root exists so they group in the advancement screen instead of appearing as eight
orphans. This supersedes the bus-event deviation recorded in `FreightCompletion`; the event stays, the
advancements are what the pack actually hooks.

**Teardown yields a signature component PLUS ordinary salvage** (step 4): the working part in
`results`, weighted scrap in `extras`. A teardown is then never a total loss once you already have the
part, which is also the shape the existing twelve teardowns already have.

**Find-only is the Motor alone** (step 5). One member, P1.4's own worked example, and the smallest
reversal of #228 that proves the mechanic. Recorded as deliberately narrow rather than as a first
instalment: widening it is a separate decision with the playtest that produced #228 arguing against.

**The release waits for all five steps.** v0.20.0 ships the whole conversion, so players never meet a
half-converted state where teardown still teaches AND the market sells the same knowledge. Art and the
CurseForge page's three missing screenshots ride the same release.

## 3. Out of scope, and why

- **The Gate itself** is pack content. The engine ships the signal in step 1 and stops.
- **Encroachment's rescope** (audit finding 4) is a design question with no ruling. It serves the
  secondary spine and is currently too weak for it, but that is a separate decision.
- **The structures as bounded restoration sites** (audit finding 5) is recorded as a deliberate loss,
  not work.
- **Balance** (#36) is parked until this lands, per the analogs doc: tuning throughput before the
  ladder exists tunes the wrong thing.
- **The Sell Terminal** needs no work. Its manual-only behaviour is the ruled distinction and it
  already has it, having never had a block entity or a container.

## 4. What this does not solve, stated plainly

**A phase list is a chore list until the goods are interesting.** The ladder's quality is entirely in
what each phase asks for, and this spec deliberately does not choose those. If phase 3 asks for two
hundred of something you already automate, the tier is a wait rather than a problem to solve. The
non-substitutable-regions property in P3.10 is the lever: a phase should want goods from places you
have not been.

**~~Nothing here creates a reason to heal the land.~~ Closed 2026-09-06 by section 1.6**: late phases
require grown goods, so the ladder cannot be finished without reclamation. Left visible rather than
deleted because it was the largest hole in the first draft and the fix is the most interesting decision
in the spec.

**What is still unsolved is the balance between the two economies.** Healing removes garbage supply and
creates grown supply, and nothing here says at what rate. Get it wrong one way and players strip
everything and stall at phase 7; wrong the other way and healing is free. That is #36's problem and it
is now a harder one than it was.

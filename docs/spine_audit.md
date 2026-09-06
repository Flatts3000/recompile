# Spine audit

**Every shipped system, checked against the four jobs.** P3.10 (`../trashlands/docs/design_decisions.md`)
locked the spine on 2026-09-06 and its own closing line says the audit had not been done. This is it.

The four jobs, plus the secondary spine:

| Job | What it is |
|---|---|
| **Market** | Knowledge. Every Blueprint bought with scrip. One source, no second route. |
| **Freight** | Tier progression. Delivery of named processed goods. |
| **Teardown** | Function. Working components you cannot forge from scrap. |
| **Sorting** | Bulk materials. The base economy that feeds freight. |
| *Reclamation* | *The secondary spine (family 2). Activates as the goal at the reveal.* |

**Verdicts.** SERVES means it feeds a job as built. RESCOPE means it has a job but its current shape is
wrong for it. DECORATIVE means it serves no job and that is accepted. CONFLICT means it contradicts a
job and needs a ruling.

**Derived from the registries, not from prose.** The block, screen, recipe-type and entity lists were
read out of `RCBlocks`, `client/gui`, `RCRecipeTypes` and the recipe directory on 2026-09-06, because
this file's whole purpose is to be true and CLAUDE.md's hand-maintained lists have been stale before.

---

## Processing: the production graph

These convert what sorting yields into what freight wants. All of them are on the critical path, and
under a delivery-gated ladder they stop being optional conveniences.

| System | Job | Verdict |
|---|---|---|
| Trommel | Sorting -> Freight | SERVES |
| Separator | Freight | SERVES |
| Pulverizer | Freight | SERVES |
| Cupola Furnace | Freight (the iron gate) | SERVES |
| Slag Furnace | Freight (obsidian, and the Nether behind it) | SERVES |
| Sintering Kiln | Freight | SERVES |
| Burn Barrel | Freight (the first smelting tier) | SERVES |
| **Sequencer** | **Knowledge** | **CONFLICT - see finding 1** |

## Collection: the base economy

| System | Job | Verdict |
|---|---|---|
| `SortableBlock` family (9 blocks) | Sorting | SERVES |
| Sorting Tarp | Sorting | SERVES |
| Garbage Vacuum (4 tiers) | Sorting throughput | SERVES |
| Scrap Hauler + Hauler Depot | Sorting throughput, unattended | SERVES |
| Scrap Network, Bin, Barrel | Logistics for bulk | SERVES |
| Bulky Waste | Teardown (the object source) | SERVES |
| Mound regrowth | Sorting (renewable supply) | SERVES |

**The Vacuum and the Hauler are vindicated by this ruling rather than threatened by it**, and that
reverses what was said in chat before the audit was run. The worry was that "clear faster" served a
spine that no longer exists. Under the four jobs it serves the one that does: sorting is bulk
materials, freight eats bulk materials, and anything that raises collection throughput is on the
critical path. The old objection was that the Vacuum destroyed **the filter** (family 4) by making
every object worth hoovering. Family 4 was explicitly not promoted, so that objection retires with it.

## Teardown

| System | Job | Verdict |
|---|---|---|
| Recompile Workbench | Teardown | SERVES |
| 13 `recompile:teardown` recipes | Teardown | **RESCOPE** - 9 carry `teaches` and must lose it, gaining component outputs |
| Idea Fragments + `fragment_assembly` (1 recipe) | none | **CUT** - decided in P3.10 |
| Blueprints, Filing Cabinet, Scrap Crafting Table | Market (now fed by purchase) | SERVES |

## The market

| System | Job | Verdict |
|---|---|---|
| Buy Terminal (13 `market_offer`) | Knowledge | SERVES |
| Sell Terminal + scrip | Optional sink | **RESCOPE - see finding 2** |
| Freight terminal and quotas | Freight | **DOES NOT EXIST** |

## Reclamation: the secondary spine

| System | Job | Verdict |
|---|---|---|
| Grass Spreader (rung 1) | Reclamation | SERVES |
| Vegetation (rung 2), Farming (rung 3) | Reclamation | SERVES |
| Tree Nursery (rung 4) | Reclamation | SERVES |
| Animals (rung 5), Animal Bait | Reclamation | SERVES |
| Compost Heap, Hydroponics Bay | Reclamation | SERVES |
| Rain Collector | Reclamation (water) | SERVES |
| Encroachment | Reclamation (pressure) | **DECORATIVE** - ruled flavour, finding 4 |

## Worldgen

| System | Job | Verdict |
|---|---|---|
| The four regions (sprawl, yard, dump, depths) | Freight (non-substitutable goods) | SERVES |
| Sewers, Aquarium, Cooling Tower, Smokestacks, Tire dumps | Freight (unique materials) | SERVES |
| Dimension lockout (the End) | Constraint on the closed economy | SERVES |

## Accepted as decorative

No job, no rescope, kept anyway, and recorded here so nobody re-audits them:

- **Collectibles** - the Puzzle Cube and its nine pieces, the avocado, gold coin, present, toy car, and
  the six recovered paintings. They serve nothing on the critical path and are not meant to. They are
  cheap (adding one is data, not code) and they are the only thing in the mod that is purely for
  finding something.
- **The Display Pedestal** - exists to show the above.
- **The guidebook** - teaching infrastructure, not a game system.

---

## Findings

**1, 2 and 4 are ruled** (2026-09-06), recorded inline below. **3** is implementation planning and is
now issue #389. **5** is recorded as a deliberate loss rather than an open question.

### 1. The Sequencer is a second source of knowledge - RESOLVED 2026-09-06

`recompile:spawn_egg_crafting` reads a Blueprint out of the grid, and the Sequencer is what produces
those Blueprints from stamped amber. P3.10 says **the market is the only source of knowledge, one
source, no second route.** The Sequencer is a second route, and it is a machine rather than a counter.

Three ways out, in the order I would take them:

1. **Narrow the rule to TIER knowledge.** The market is the only source of *progression* Blueprints;
   the Sequencer produces creature Blueprints, which gate nothing and buy nothing. Cheapest, and it
   keeps a well-liked machine intact. Costs the rule its absoluteness.
2. **Move spawn eggs to the market.** The buyer stocks them like anything else. Clean against the
   rule, and there is a reading where it is thematically better. Costs the amber chain its only
   payoff, which would strand Spent Amber and the resin chain hanging off it.
3. **Rescope the Sequencer to yield function.** It reads amber and returns *the creature*, not the
   knowledge of it. Consistent with teardown-as-function. Largest change.

**Ruled: option 1** (Jason, 2026-09-06). The Sequencer is a sanctioned exception and the rule narrows
to **tier knowledge**: the market is the only source of *progression* Blueprints. Creature Blueprints
gate nothing and buy nothing, so they sit off the ladder and cannot short-circuit it. The amber chain
and the Spent Amber feeding the resin family both survive untouched.

### 2. The Sell Terminal and the freight terminal will read as the same block - RESOLVED 2026-09-06

Both take goods and both send them off-site. One pays scrip for an optional shop; the other satisfies a
tier quota. A player will not distinguish them by looking, and putting a load in the wrong one is a
silent mistake with no feedback. Either they merge into one block with two modes, or the freight side
needs a visibly different object.

**Ruled: separate blocks, split on AUTOMATION** (Jason, 2026-09-06). **The Sell Terminal takes no pipe
input** and stays manual-only, which is what it already is - no block entity, no container, a
menu-local grid. **The Freight Terminal takes pipe input**: hoppers, AE2 and the Scrap Network can all
push into it, because quotas are bulk and sustained and are meant to be fed by a factory rather than by
hand. The distinction is mechanical rather than cosmetic, so a player learns it by trying to automate
one, and it is already an idiom here - the Burn Barrel returns an empty `getSlotsForFace` to be
manual-only, and membership in `#recompile:scrap_connectable` is the switch that decides whether items
route to a block at all.

**It also gives the logistics tier a terminus.** Scrap Hauler collects, Depot pushes into the Scrap
Network, the machines process, the Network routes to the Freight Terminal, the tier advances. Every
logistics feature shipped in the last month gains an end it did not have.

### 3. Nine teardown recipes are currently the only route to what they teach

There are **thirteen** `recompile:teardown` files, twelve real ones plus the schema's own
`example_iron_door`, and **nine carry `teaches`**: `broken_hauler`, `broken_hydroponics_bay`,
`broken_spawner`, `broken_terminal`, `depleted_battery`, `fridge`, `mattress`, `washing_machine`,
`worn_forging_die`. Stripping `teaches` is not a one-line change. Each of the nine needs its taught
Blueprint added to the Buy Terminal's stock at a price, or the thing it taught becomes unreachable. `FoundNotCraftedTests` has
a twin that fails the build when a tagged item has no route at all, so this will surface as a red test
rather than as a silent gap - but it has to be planned, not discovered.

### 4. Encroachment now serves the secondary spine and is too weak for the job - RULED 2026-09-06

It was audited as family 6 (holding ground) and family 6 was not promoted. But under family 2 it is the
*pressure* that makes reclamation a contest rather than a checklist, which is a real job on the
secondary spine. As built it is local, slow, permanently defeated by trees, and touches player
investment in exactly one place (wet farmland holds, dry farmland is taken). If reclamation is the
second act, this is the thing that has to push back during it, and it currently cannot take anything a
player would mourn.

**Ruled: accept it as flavour** (Jason, 2026-09-06). It stays mild on purpose, costs nothing to leave,
and the reclamation ladder still reads as progress without it. **Stop auditing it** - this finding is
closed rather than deferred, so a future pass should not rediscover it as a defect. Reclamation's
pressure now comes from the economy instead: healing removes garbage supply while late freight phases
require grown goods, so the contest is between two economies rather than against an eroding frontier.
Encroachment moves to the decorative list above in spirit; it is left in this section so the reasoning
survives.

### 5. Family 5 is unpromoted, so the structures' best property goes unused

The sewer, the aquarium, the cooling tower and a tire dump are each a **bounded site with a state you
can finish changing**, which is the restoration family the owner separately named as one of the three
things pulling people in. Under this ruling they are freight sources: places you strip. Not a defect,
and no action proposed, but it is the largest piece of value the spine choice leaves on the table and
it should be a deliberate loss rather than an unnoticed one.

---

## What the audit did not find

**No shipped system needs cutting.** One recipe type dies (`fragment_assembly`), nine recipes get
rewritten, two systems get rescoped, and everything else either serves a job or is accepted as
decorative. For a mod that was described a day ago as "a lot of distinct features that aren't a game",
the machinery turned out to be almost entirely load-bearing once a spine existed to be load-bearing
*for*. The problem was never the features. It was that nothing said what they were for.

# The gem tier - spec (issue #119)

**Status: design locked 2026-08-02, not built.** How this world reaches diamond, redstone, amethyst
and lapis. **Gold was split out to #120** and is not part of this spec. Every decision below was made
in the 2026-08-02 design session; what remains is build order, art, and the numbers, which join the
pre-beta balance pass (#36).

## 0. The idea, and why it fits

**Iron is where this world currently ends.** A reachability closure over every vanilla and mod recipe
confirms it: past the iron gate there is no gold, no diamond, no redstone, no lapis, no amethyst, no
emerald, and worldgen carries no ores at all. Everything above iron is greenfield. (**Gold is tracked
separately at #120**, because its input already exists and it can move without any of this.)

In a real dump, the step past scrap iron is not a deeper hole, it is **a different stream**. Ferrous
scrap is heavy, bulky and cheap; the valuable material stops being *the thing you find* and starts being
*inside the thing you find*. A tonne of circuit boards carries 200 to 350 grams of gold where a tonne of
gold ore carries 1 to 5, which is why urban mining exists as an industry rather than as a slogan.

That gives the tier its verb. **Iron is won by force. This is won by separation.** You cannot hit a
motherboard with a sledgehammer and get gold out of it.

It also gives the tier its shape, which is new here. Every tier so far is **one object in, one object
out**: pull a garbage block, get an item; tear a mattress down, get string. **One circuit board is worth
nothing.** So this is the first tier where a player must accumulate before they can convert, which is
both the real economics and a gate that cannot be cheesed.

## 1. Decisions

| Decision | Answer | Why |
|---|---|---|
| Governing principle | **Refine, do not make** | Outside plants nothing in this mod is farmable; everything is found. Synthesis is the second half of an arc whose first half is unfinished. No press, no lab-grown diamond, no synthetic ultramarine, however real those are |
| Diamond | **Refined from Mechanical Waste** | Real: worn tooling. Saw blades, core bits, grinding wheels |
| Redstone | **Refined from Mechanical Waste** | Real: rare-earth magnets in motors and speakers. **This is the tier's gate** |
| Amethyst | **Refined from Mechanical Waste** | Weakest real fit; defensible read is recovered **quartz** that happens to be purple |
| Lapis | **Printer teardown** (#112) | Lapis is a pigment and a printer is full of pigment. Machinery contains none |
| Emerald | **Deferred** | No industrial stream exists in reality, and vanilla uses it only as villager currency, which this world has none of |
| Mechanical Waste | **A fourth `SortableBlock`**, generated in the demolition yard beside Stone Rubble | The yard already generates piles; no new region needed |
| What the pile drops | **Intermediates, never gems** | The pile is the found half; the gem is the refined half |
| How many intermediates | **Several, one per gem** | One shared intermediate would put every gem on a single difficulty curve, and redstone being the hard gate is a decision |
| The machine | **One Separator**: an industrial grinder in the IE Crusher mould. Drop material in the top, materials fall out the bottom | A separator is the actual machine in real recycling. "Teardown on steroids" |
| Power | **Forge Energy** | Second FE consumer after the Hydroponics Bay; the tier already exists |
| GUI | **None** | The interaction is a world interaction, so the no-new-machine-screen rule holds untouched |
| Waste output | **Slag**, sink undecided | See Phase 4; it may be cut |

## 2. The constraints that shape everything below

**The gate is arithmetic, and that is the point.** #91 killed the first iron gate because it was built
from *the absence of a material*, and it died the moment something else added the material. A ratio has
no such failure mode: if another mod floods the player with circuit boards, they reach gold using that
mod's boards, which is the correct outcome rather than a leak. **Never re-express this tier's gate as a
missing item or an uncraftable machine.**

**Teardown is an allowlist and must stay one.** `RecompileWorkbenchBlockEntity.findRecipe` returns empty
and nothing happens unless a `recompile:teardown` recipe names the input, which is why the jukebox can
hold a diamond (8 planks and one diamond, #117) without leaking it. The guard test below is **scoped to
teardown deliberately** - the Separator is the sanctioned route and does produce these materials, so a
test written any broader fails on the intended design.

**Teardown is one-in; concentration is N-in.** Reusing `recompile:teardown` with a different `station`
looks tempting because the schema already carries that field. It does not fit: teardown consumes a
single item, and this tier's entire point is that many inputs become a little output. This needs its own
recipe type, or an explicit input count added to the schema.

**The automation policy has two doors and this machine needs a third.**
`docs/automation_policy_spec.md` is **locked** (2026-07-31, written after four automation bugs shipped
with the same root cause) and names exactly two: the Container path that hoppers use, and the Capability
path that pipes use. It carries a hard rule: *a new block that holds items adds a row here or it is not
done.*

An entity-eating, entity-dropping grinder uses **neither**, and is still automatable: a dropper throws
items in from above and a hopper beneath catches what falls, because hoppers pick up item entities. That
is a good outcome, and it satisfies the spec's own "to keep pipes away, expose no handler" for free, so
no pipe can ever extract from it. But it is a third kind of automation surface the locked table does not
model, and it must be added there before the block exists.

**No BlockEntityRenderer, and no fifth screen.** The BER ban has exactly one recorded exception (the
Display Pedestal) and this is not a second. Motion comes from **animated textures** (`.mcmeta` frame
animation, pure vanilla, no code, how fire and prismarine work) plus **particles and a running
blockstate**, which the Burner Generator already does. The mod ships no `.mcmeta` today, so that is a
first, but it needs no exception.

**The art budget is distinct cell types, not volume.** The Compost Heap is 2x2x2, eight cells, and its
entire formed appearance is **one** bespoke block repeated across seven dummies. A 3x3x3 Separator with
four cell types is cheap; one with twenty-seven unique cells is a different project. Choose the
footprint by cell-type count.

**Two texture rules that already cost time here.** Any non-cube model needs `noOcclusion()` or it punches
a hole in the world. And **do not reach for voxel-porter**: what ports well is simple, iconic and
colourful, and a grey, detailed industrial machine is the documented failure profile. Author cuboids by
hand and generate the surfaces, where texgen's AI backend is strong (rusted metal is texture, not
geometry - the opposite of the Puzzle Cube's lesson).

---

## Phase 0 - amend the automation policy

**Ships:** a decision, on paper, before any block exists.

Add the third door to `docs/automation_policy_spec.md` as a concept, and a row for the Separator: no
Container, no capability, items in as entities from above, items out as entities below. State explicitly
that this is automatable through the world rather than through the block, and that no pipe can extract.

Blocks nothing else and costs an hour. It goes first because the spec it amends was written precisely to
stop a block shipping without deciding this.

## Phase 1 - Mechanical Waste

**Ships:** the found half of the tier, playable on its own.

A fourth `SortableBlock` beside `GarbageBlock`, `TrashBagBlock` and `CompactedBaleBlock`, with its own
`mechanical_pulls` table, its own crumble window and its own tool gate. Placed by a
`mechanical_waste_pile` feature in the demolition yard's biome features, in the same step as
`rubble_pile`, `steel_stack` and `building_husk`.

The pull table yields the three intermediates and nothing precious. Names not final:

| Intermediate | Real source | Refines to |
|---|---|---|
| Spent abrasive | Saw blades, core bits, grinding wheels | Diamond |
| Magnet scrap | Hard drive and speaker magnets, motor windings | Redstone |
| Quartz grit | Oscillators, optics, abrasives | Amethyst |

Putting the luck in the pull stream rather than in the Separator keeps the Separator deterministic and
therefore tunable.

**This phase goes first because gold left.** The earlier draft opened with the Separator proven against
gold from E-Scrap, which needed no new worldgen at all; with gold at #120 there is no longer any input
for the machine to chew on until this exists. The ordering is now the natural one, at the cost of the
de-risking that gold was providing.

**Acceptance:** the pile generates at a measured density in the yard and nowhere else, no entry in
`mechanical_pulls` is a vanilla gem, and each intermediate is obtainable by hand.

## Phase 2 - the Separator, proven on amethyst

**Ships:** the machine, working, with one recipe.

The multiblock structure and its formed art, FE consumption, the entity-in and entity-out interaction,
the new recipe type with its input count, and **one recipe: quartz grit to amethyst.**

**Amethyst is the proving material deliberately.** It is the lowest-stakes output in the tier - it
unlocks four things and one of them is the spyglass, which #113 already sources as a found tool, so its
unique contribution is two decorative blocks. If the ratio is wrong, or the machine is rebuilt, or the
art is redone, nothing important is disturbed. Diamond and redstone arrive in Phase 3 against a machine
that has already been played.

**Acceptance:**
- Dropping a stack in the top consumes power and drops amethyst out the bottom.
- A hopper beneath catches the output; a dropper above feeds it.
- No item capability and no Container are exposed on any face, including the **null side** (the
  `WorldlyContainerWrapper` trap the policy spec records).
- Breaking any cell disbands it and returns the components, and a **two-or-more-dummy disband test counts
  the core item** (the duplication bug that a single-dummy machine cannot catch).

## Phase 3 - diamond and redstone, and the guard

**Ships:** the two that matter.

Two further Separator recipes, each with its own input count and time. **Redstone gets the
harshest ratio**: it drags fifteen vanilla items behind it (piston, dispenser, dropper, observer,
comparator, repeater, crafter, clock, compass, daylight detector, target, redstone lamp and torch, plus
map through the compass), so it is the automation tier in a single material. Real rare-earth recycling
rates are under 1%, which is a factual justification for a punishing number rather than an arbitrary one.

**The guard:** `no_teardown_recipe_yields_a_gated_material`, asserting that no shipped
`recompile:teardown` recipe produces diamond, emerald, lapis, redstone or gold. Gold is included even
though it lives at #120, because the guard is cheaper to write once than to remember to extend. Direct analogue of the
existing `no_smelting_recipe_turns_a_mod_item_into_iron`, written after #91 for exactly this class of
bug. Scoped to teardown; the Separator's own recipes are the sanctioned route.

## Phase 4 - slag, or cut it

**Ships:** a waste output with somewhere to go, or nothing.

`material_economy.md` already records **obsidian as "not found, made only (melt slag/glass), Slag
furnace"** and a **slag field** as a planned later region. So slag is an existing thread this machine
could feed rather than a new one.

Two things to settle before it is built:

- **A waste output nobody can use is clutter**, and this mod already holds that line for finds. Slag
  needs a sink from the moment it exists: a building block, a later tier's input, or obsidian.
- **Slag to obsidian may cross the line this spec draws.** "Refine, do not make" was decided the same
  day, and melting slag into obsidian is closer to synthesis than to refining. It needs an explicit call
  rather than arriving by momentum.

If neither resolves, **cut slag**. The Separator works without it.

## Phase 5 - the surrounding work

**Ships:** the tier is discoverable.

Guidebook entry (a player who finds a Mechanical Waste pile and cannot act on it reads it as scenery), a
JEI category for Separator recipes, Jade reporting stored FE and progress, and a row in
`../trashlands/docs/progression_gates.md`.

---

## The progression question, stated rather than buried

This tier sits **after the demolition yard**, because Mechanical Waste generates there and travel already
gates the yard. Every material in it is therefore travel-gated, with no early exception now that gold has
moved to #120.

**Redstone is the real gate and it should feel like one.** It is the automation tier, and it is the last
thing in this progression that is genuinely scarce.

One thing the tier does **not** complete: **enchanting needs obsidian, diamond and lapis.** This spec
delivers diamond and lapis. Obsidian is unbuilt and sits behind the slag question in Phase 4, so the
headline payoff of the whole gem tier is not reachable until that is decided. Worth knowing before the
tier is announced as finished.

## Open

- The Separator's footprint, and how many **distinct cell types** it needs. The second number is the art
  budget.
- Whether the Scrap Network should feed the Separator as well as hand-dropping, or whether that muddies a
  deliberately physical machine.
- Whether Mechanical Waste piles need a tool gate, the way rubble and bales do, and which tool.
- Final intermediate names.
- All ratios and weights, which join #36.
- Slag, per Phase 4.

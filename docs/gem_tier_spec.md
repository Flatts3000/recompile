# The gem tier - spec (issue #119)

**Status: BUILT 2026-08-03** (branch `feat/gem-tier`), phases 1 to 4. How this world reaches diamond, redstone, amethyst
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
| Emerald | **Deferred** | No industrial stream exists in reality, and vanilla uses it only as villager currency. *(That clause used to read "which this world has none of", and it is false: villagers are reachable by curing a zombie villager, amber carries a villager entry in both pull streams, and `emerald` is marked reachable in the resource checklist. The decision stands on the first clause alone.)* |
| Mechanical Waste | **A fourth `SortableBlock`**, generated in the demolition yard beside Stone Rubble | The yard already generates piles; no new region needed |
| What the pile sorts into | **Industrial scrap variants, never gems** | The pile is the found half and is picked through like any other sortable block; the gem is the refined half |
| How many variants | **Several, one per gem** | One shared scrap would put every gem on a single difficulty curve, and redstone being the hard gate is a decision |
| Variants are | **Distinct items**, not blockstate variants of one scrap | Not a preference. Each variant feeds a different Separator recipe, and a recipe keys on an item; variants of one item would need a data component to tell them apart, which is worse in every way |
| Scrap Network feed | **No.** Hand-dropped and hopper-fed only | The machine's identity is that it is physical. A network feed would make it two things at once, and a hopper above already provides automation |
| Mechanical Waste tool gate | **None. Bare hand**, matching Stone Rubble | Rubble sits beside it and needs no tool; a gate the adjacent block does not have is an inconsistency the player has to learn for nothing. The yard is already gated by travel |
| The machine | **One Separator**: an industrial grinder in the IE Crusher mould. Drop material in the top, materials fall out the bottom | A separator is the actual machine in real recycling. "Teardown on steroids" |
| Power | **Forge Energy** | Second FE consumer after the Hydroponics Bay; the tier already exists |
| GUI | **None** | The interaction is a world interaction, so the no-new-machine-screen rule holds untouched |
| What the grinder outputs | **The raw material AND recovered ordinary scrap** | Real separation yields several streams, not one. The by-product is material the mod already has and already has sinks for, rather than a novel waste item |

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

**The recipe type is `recompile:separating`, a new type.** Reusing `recompile:teardown` with a different
`station` looks tempting because the schema already carries that field, and it is the wrong call for
three reasons. Teardown consumes a **single item** and this tier's whole point is that many inputs become
a little output. Teardown is **public API** - packs extend it without a mod release - so adding a count
field would silently redefine every existing pack recipe as count-1. And the guard test needs to tell the
two apart cleanly, which two types give for free and a station discriminator does not.

The schema, following `TeardownRecipe`'s shape so the two read as siblings:

```json
{
  "type": "recompile:separating",
  "input": "recompile:magnet_scrap",
  "count": 1,
  "ticks": 200,
  "energy": 16,
  "results":    [ { "item": "minecraft:redstone", "count": 4 } ],
  "byproducts": [ { "item": "recompile:scrap_metal", "count": 2 } ]
}
```

- `input` is an `Ingredient`, so a tag works, matching teardown.
- `count` is how many of the input one operation consumes, and for any recipe THIS MOD ships it is
  **1** (owner, 2026-08-19): *a GUI-less machine cannot take N > 1 inputs to make an output.* It was
  the concentration dial, and it cannot be, because these machines have no GUI and are not Containers -
  there is nothing to open and nothing to take back out, so a feed the recipe cannot use yet is
  invisible and unrecoverable except by breaking the block. A remainder is also the ordinary state
  rather than an edge case: a recipe wanting four leaves one whenever the feed is not a multiple of
  four, and the pull streams hand scrap out one item at a time. `no_gui_less_machine_recipe_takes_more_than_one_input`
  binds it. The field stays in the schema because it is public and a pack may use it - and both head
  scans skip a slot they cannot run rather than stalling on it, so a pack that does gets a slow machine
  rather than a bricked one. **The concentration dial is now the drop weight**, which is #36's anyway.
- `energy` is **FE per tick**, not per operation, matching `hydroponicsFePerTick`. Per-tick is what lets an
  underpowered machine visibly stall rather than silently refuse.
- `byproducts` are **deterministic, not weighted**. A separator splits a feed; it does not roll for a
  bonus. Determinism is also what keeps the machine tunable, since the luck already lives in the pull
  stream.

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

## Phase 1 - Mechanical Waste

**Ships:** the found half of the tier, playable on its own.

A fourth `SortableBlock` beside `GarbageBlock`, `TrashBagBlock` and `CompactedBaleBlock`, with its own
`mechanical_pulls` table, its own crumble window and its own tool gate. Placed by a
`mechanical_waste_pile` feature in the demolition yard's biome features, in the same step as
`rubble_pile`, `steel_stack` and `building_husk`.

The pull table yields **industrial scrap variants** and nothing precious. Sorting works exactly as it
does everywhere else in the mod: right-click to pull one at a time, with a crumble window. Names not
final:

| Scrap variant | Real source | Separates into |
|---|---|---|
| Spent abrasive | Saw blades, core bits, grinding wheels | Diamond |
| Magnet scrap | Hard drive and speaker magnets, motor windings | Redstone |
| Quartz grit | Oscillators, optics, abrasives | Amethyst |

Putting the luck in the pull stream rather than in the Separator keeps the Separator deterministic and
therefore tunable.

**The bulk of the table is Scrap Metal, not E-Scrap** (owner, 2026-08-03). Both were in it at first,
and E-Scrap was the wrong call twice over: it is *household* waste, so it read as the dump's material
turning up in a demolition yard, and it is the one entry the Separator has no recipe for - so the
commonest thing a player pulled from a machine pile was the one thing that machine refuses. Its weight
folded into Scrap Metal rather than being deleted, which holds the table at 222 and leaves every gem
rate exactly where it was tuned. E-Scrap keeps its household source, so nothing is orphaned.

**This phase goes first because gold left.** The earlier draft opened with the Separator proven against
gold from E-Scrap, which needed no new worldgen at all; with gold at #120 there is no longer any input
for the machine to chew on until this exists. The ordering is now the natural one, at the cost of the
de-risking that gold was providing.

### The block

Values chosen to sit inside the range its neighbours already occupy, not invented:

| Property | Value | Why |
|---|---|---|
| `minPulls` / `maxPulls` | **3 / 4** | Matches the Compacted Bale, the other dense and rich stream. Rubble is 2/4, a garbage block 2/3, a bag 2/2 |
| Required tool | **None** | Rubble sits beside it and needs none |
| Gravity | **Falls**, config-gated | Every `SortableBlock` extends `FallingBlock`; this is inherited, not a new decision |
| Texture variants | **3** | What `garbage_block` uses. A pile that generates in quantity needs them or the tiling shows |
| Placement | `count: 2` per chunk | `rubble_pile` is `count: 6`. This is the valuable pile, so a third as common |

### The three scrap variants

Distinct items, named for what they actually are:

| Item | Reads as | Separates into |
|---|---|---|
| `recompile:spent_abrasive` | Worn saw blades, core bits, grinding wheels | Diamond |
| `recompile:magnet_scrap` | Hard drive and speaker magnets, motor windings | Redstone |
| `recompile:quartz_grit` | Oscillators, optics, abrasive media | Amethyst |

### Art

Four surfaces, all in the yard's palette of rusted steel and oil:

`mechanical_waste` (block, 3 variants), `spent_abrasive`, `magnet_scrap`, `quartz_grit` (items).

The pile is the only one that has to read at distance, since it is what a player spots across the yard.
The three items only have to be told apart in an inventory row, so **silhouette and colour separation
matter more than detail**: pale grey grit, dark iron-blue magnets, translucent grit.

**Acceptance:** the pile generates at the stated density in the yard and nowhere else, no entry in
`mechanical_pulls` is a vanilla gem, and each variant is obtainable by hand.

## The Separator sorted, and no longer does (added 2026-08-03, removed 2026-08-16, #187)

For four releases the machine had a **second mode**: feed it a sortable block and it rolled that
block's pull stream instead of running a recipe. **That is gone. One machine, one verb.**

**Why it was wrong.** A shear shredder destroys distinctions, and sorting requires one - screening,
density, magnetism, conductivity, reflectance. A real facility uses a different machine for each cut
because each exploits a different property, and interleaved teeth exploit none of them. The one thing
this machine provably cannot do is tell things apart.

The design had already argued against itself in its own comment: *sorting is a weighted loot roll and
separating is deterministic, so sorting is a second MODE rather than a second recipe type* - stating
the principle and taking the exemption in the same breath. The complexity was the tell: two tick
rates, two energy rates, two code paths, a precedence rule for anything that could be both, and a test
that existed only because one machine had two jobs.

**Where it went:** the Trommel (#188), which makes the SIZE cut and is the first sorting stage in a
real plant. Rates are unchanged, because both still call `SortableBlock.sortRolls`.

**What a player notices.** A placed Separator keeps working - the block, the multiblock, the chute and
the separating recipes are untouched. What stops is feeding it garbage blocks.

**Do not re-thicken it with a second verb.** Sorting was added because the Separator looked thin at
three recipes, and it is back to three. Thin is a content problem; a second verb is an identity
problem, and trading the first for the second is what produced #187. The fix is more work of its own
kind - "liberate the valuable thing from the matrix holding it" has room in it, and every new
Mechanical Waste material is a candidate.

## Phase 2 - the Separator, proven on amethyst

**Ships:** the machine, working, with one recipe.

The multiblock structure and its formed art, FE consumption, the entity-in and entity-out interaction,
the `recompile:separating` type, and **one recipe: quartz grit to amethyst.**

### What it is built from

Twelve cells: one core the player places, eleven dummies. **Two component types only** - auto-assemble is
all-or-nothing and quantity-correct, so every extra component type is another way for a player to stand
in front of a core that will not form.

| Cells | Component placed | Formed block |
|---|---|---|
| 4 bay | **Steel I-Beam** x4 | `separator_chamber` |
| 6 housing | **Machine Frame** x6 | `separator_housing` |
| 1 chute | **Machine Frame** x1 | `separator_chute` |

```
y=1   hous bay   bay      (a solid column for bulk, then the 2x2 grinding bay)
      hous bay   bay
y=0   CORE chute hous     (front: ONE chute, where everything comes out)
      hous hous  hous     (back)
```

**The bay is a 2x2 square, and that shape took three tries.** It was first the front row only with
housing behind it, which was a genuine trap: housing looks exactly like a lid, so material dropped on
the back half was silently refused by a surface that appeared to be the opening. Widening it to the
whole top fixed the trap and lost the machine, which read as a hole in the ground rather than a grinder.
A 2x2 bay beside a full-height column keeps both - the column plainly is not the opening, so nothing is
ambiguous, and the machine has bulk.

**The four bay cells read as one opening.** Each is stamped at assembly with which quarter of the
grinder it shows; the quadrant models draw a rim only on their **outer** edges and the floor textures are
quarters of a single image, so both the border and the teeth run continuously across the seams. The rim
being geometry is the part that bit: the first build gave every cell a full rim on all four sides, which
drew a complete box around each block, and no amount of texture slicing can fix a border that is in the
model.

**One chute, and everything leaves through it** (owner, 2026-08-03). A recipe may one day split a feed
into several outputs at once; they all still come out of the same opening, so catching a machine's whole
output never takes more than one hopper.

Machine Frame is the established multiblock component (the Compost Heap takes seven). Steel is the
yard's own material and a shredder's cutters are steel, so the chamber costing steel ties the machine to
the region it stands in. Note the same component forms two different blocks, which the framework already
allows and the Grass Spreader already does.

**The core must cost iron.** It sits directly above the iron gate, and paying iron for it is what makes
the Cupola feel like a step toward something rather than a terminus. Proposal: Machine Frame, two Steel
I-Beam and four Iron Ingot, shaped. The exact recipe joins #36; the constraint is that iron appears in it.

### How material actually gets in and out

- **A bounded internal queue** (owner, 2026-08-03). The machine swallows what lands in its bay into
  nine internal slots and works through them in order, so several kinds of scrap go in at once and come
  back out as their raw materials without the player standing over it. Bounded because a machine that
  swallows an unbounded amount is a storage block, and this one is a grinder with a hopper on it.
- **Internal is the load-bearing word.** The queue is **not** a `Container` and the machine exposes no
  item handler on any side, so no hopper can insert and no pipe can connect - the closed door
  `automation_policy_spec.md` describes. Automation still works; it goes through the world, with a
  dropper over the bay and a hopper under the chute.
- **Only what it can grind is let in.** An item with no `recompile:separating` recipe is never swallowed,
  so the queue cannot jam on junk and a player cannot lose something by dropping it in the wrong place -
  it simply lies in the bay where they can pick it back up. This matters more than it looks, because
  nothing can extract from this block: anything it takes and cannot process would be gone for good.
- **Everything queued drops on break**, so the machine is never an item sink.
- **A ticker scans, nothing collides.** The mouth spans the bay cells **and** the block above them,
  because the bay's top face is recessed and a dropped stack settles down inside the well rather than on
  top of it. Scanning only the block above shipped once and meant a player could watch an item sit
  visibly in the mouth while the machine ignored it.
- **It drains a container on the bay.** Nothing can push into the machine, so it pulls. A hopper pointed
  down at the bay is the first thing anyone reaches for and can never work, because there is nothing
  there to insert into; reaching out is how a hopper itself works and it costs none of the properties
  above.
- **No power means the material waits.** Items do not bounce out and are not refused. The machine simply
  does not consume them, the way a furnace with no fuel sits full and cold. Legible without a screen.
- **Output spawns at the chute face with a small outward velocity**, so it lands in front of the machine
  rather than inside it, and a hopper under the chute catches it.

### Numbers

Placeholders, chosen against the machines either side of it, and joining #36:

| Value | Setting | Why |
|---|---|---|
| Energy | **16 FE/tick** | The Hydroponics Bay is 8. This is the top tier and should cost visibly more |
| Time | **200 ticks** (10s) per operation | Half the Bay's cook. This used to read "because the input count is doing the work instead", which stopped being true when every count went to 1 (owner, 2026-08-19) |

**The grinder separates, it does not transmute.** One run yields the raw material **plus recovered
ordinary scrap** - metal, plastic, glass - because that is what a real separator does: it splits a mixed
feed into several streams rather than converting one thing into another. This is also the answer to the
waste-output question raised earlier: the by-product is material this mod already ships and already has
sinks for, so nothing is invented and nothing becomes clutter.

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

Two further `recompile:separating` recipes.

**The ratios are comparable; the pull weights carry the difficulty.** This is the important part and it
is easy to get backwards. Redstone is the gate, but a player needs redstone in *quantity* - fifteen items
hang off it - so making a single redstone cost thirty inputs would be punishing rather than gating. The
scarcity belongs in `mechanical_pulls`, where magnet scrap is the rare entry, not in a recipe that makes
each unit agonising.

Starting placeholders:

| Recipe | In | Out | Byproduct |
|---|---|---|---|
| Amethyst | 12 quartz grit | 2 amethyst shard | 1 glass shards |
| Diamond | 16 spent abrasive | 1 diamond | 2 scrap metal |
| Redstone | 16 magnet scrap | 4 redstone | 2 scrap metal |

Pull weights, inversely: quartz grit common, spent abrasive uncommon, **magnet scrap rare**. **Redstone gets the
harshest ratio**: it drags fifteen vanilla items behind it (piston, dispenser, dropper, observer,
comparator, repeater, crafter, clock, compass, daylight detector, target, redstone lamp and torch, plus
map through the compass), so it is the automation tier in a single material. Real rare-earth recycling
rates are under 1%, which is a factual justification for a punishing number rather than an arbitrary one.

**The guard:** `no_teardown_recipe_yields_a_gated_material`, asserting that no shipped
`recompile:teardown` recipe produces diamond, emerald, redstone or gold. Gold is included even
though it lives at #120, because the guard is cheaper to write once than to remember to extend. Direct analogue of the
existing `no_smelting_recipe_turns_a_mod_item_into_iron`, written after #91 for exactly this class of
bug. Scoped to teardown; the Separator's own recipes are the sanctioned route.

**Lapis is the one thing the guard no longer covers**, and that is the decision at line 36 arriving in
code (#112, shipped 2026-08-04). The Printer's teardown is a teardown, so lapis had to leave the
teardown half of the guard for the find to exist at all. It stays in the *pile* half - Mechanical Waste
must still never drop it - so the two lists are now genuinely different and the test carries both. What
makes the opening safe is that lapis gates nothing on its own: its only real job is enchanting, and an
enchanting table needs obsidian and diamond too, both still behind the yard.

## Phase 4 - the surrounding work *(done)*

**Shipped:** the tier is discoverable.

Two guidebook entries in the demolition category (Mechanical Waste, and the Separator with a multiblock
render page locked to the blueprint by `GuidebookMultiblockTests`), a **Separating** JEI category reading
the bundled recipe JSON with the input at its real count, a Jade pair reporting stored FE and either a
grind percentage or **which** of the two idle reasons applies, and a gem tier section in
`../trashlands/docs/progression_gates.md`.

The JEI category shows **no odds column**: a separator splits a feed rather than rolling on it, so "100%"
beside every row would be noise. (This sentence used to end "the input carries its count because that
count *is* the tier". The count is 1 everywhere now - see the `count` bullet above - so the tier is
carried by the drop weight of the feed and by the energy cost, not by the input ratio.)

---

## The progression question, stated rather than buried

This tier sits **after the demolition yard**, because Mechanical Waste generates there and travel already
gates the yard. Every material in it is therefore travel-gated, with no early exception now that gold has
moved to #120.

**Redstone is the real gate and it should feel like one.** It is the automation tier, and it is the last
thing in this progression that is genuinely scarce.

**Obsidian and slag are out of scope** (owner, 2026-08-02). Neither is part of this tier. The grinder
returning ordinary recovered scrap removes any need for slag here, and `material_economy.md` already
queues a **slag field** region where slag, fluorite and oily scrap belong together.

That leaves one thing worth stating plainly, because it will otherwise be assumed: **this tier does not
complete enchanting.** An enchanting table needs obsidian, diamond and lapis. This spec delivers the
diamond and the Printer delivers the lapis (#112); obsidian is elsewhere and unbuilt. So the gem tier
can ship complete and enchanting will still not be reachable, and that is by design rather than an
oversight.

## Open

Everything below is a look-at-it decision. Nothing here blocks starting work.

- **Footprint confirmation.** 3x2x2 with four cell types is the working assumption and step 1 of the
  model spec's build order is to look at it in-world. If it changes, the cell counts in Phase 2 change
  with it; nothing else does.
- **Every number above is a first-pass placeholder**, as the standing pre-beta gate requires. Pull
  weights, FE rate, times, input counts and byproduct amounts all join #36 together, because tuning them
  piecemeal is what that gate exists to prevent.
- Whether `spent_abrasive`, `magnet_scrap` and `quartz_grit` survive contact with the guidebook, which is
  where item names usually get their last edit.

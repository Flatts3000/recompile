# The Municipal Aquarium - landmark spec

**Status: rulings in, unbuilt.** Proposed 2026-09-03 as the answer to #324 (ocean materials
unreachable). All five open questions were decided by the owner on 2026-09-03 and are recorded in
section 8, which is now a record of decisions rather than a list of questions. One technical blocker
and one new question came out of those rulings; both are in 8.6.

Sibling specs: [`demolition_yard_spec.md`](demolition_yard_spec.md),
[`radioactive_dump_spec.md`](radioactive_dump_spec.md). Landmark precedent: the decrepit cooling tower
and the brick smokestacks, both shipped in v0.16.0.

---

## 0. The concept in one paragraph

**A drained municipal aquarium, forty years abandoned.** The tanks are cracked and empty, the acrylic
is crazed, the filtration hall is silted up, and the only things left in it are what nobody could sell:
the cladding, the light fittings, and the dead coral that was decoration rather than livestock. It is a
building people threw away, which is this mod's thesis at building scale rather than object scale.

**It exists because the ocean does not.** 39 vanilla resources have no source here because no ocean
biome, monument, shipwreck or ocean ruin generates, and the overworld noise settings put sea level at
-64 with no aquifers - so there is no water body for an ocean structure to sit in. An aquarium needs
none. It is the one building that can be full of sea materials on dry land, and being **drained** is
not a compromise to dodge the terrain, it is what an abandoned one looks like.

**It is not bone dry, though.** Ruling 8.1: shallow pools of **leachate** have collected in the tank
floors and the filtration sumps. Rain has been draining through forty years of refuse into the lowest
points of a derelict building, which is the definition of leachate rather than a decoration on top of
it.

---

## 1. What it is for

Closing #324 without adding an ocean.

| Family | Count | How it arrives |
|---|---|---|
| Prismarine, dark prismarine, prismarine bricks, sea lantern | 4 | **cladding and lighting**, placed as blocks |
| Prismarine shard, prismarine crystals | 2 | **guardian drops** - see the renewability note below |
| Sponge, wet sponge | 2 | the **filtration** hall, placed |
| Dead coral, dead coral fans, dead coral blocks (5 colours) | 15 | **exhibit remains**, placed and found |
| Live coral, fans, blocks (5 colours) | 15 | the revival chain, section 4 - **approved, ruling 8.2** |
| `heart_of_the_sea` | 1 | the centrepiece exhibit, one per building |
| Armor trim smithing templates | 16 | **the chest** - ruling 8.4 reversed, see 5.1 |
| `turtle_scute` | 1 | **the chest** - the only route, see #345 |

**Renewability is the whole argument for the guardian, and it was not in the first draft of this
spec.** Every prismarine block in the game is crafted from prismarine shards: 4 shards make prismarine,
9 make prismarine bricks, 8 plus a black dye make dark prismarine, and a sea lantern is shards plus
crystals. Shards and crystals drop from exactly one thing, a **guardian**. So cladding the building in
prismarine is a **finite** one-time strip of the structure, and a guardian tank is what makes the whole
family **renewable**. That reframes the spawner from flavour into the load-bearing half of the
prismarine route, and it is why 8.3 changed from "no" to "yes, if it can be made to work".

**The aquatic animals were mostly never the gap, with two exceptions worth knowing.** Cod, salmon,
tropical fish, pufferfish, ink sacs, glow ink sacs, nautilus shells, sea pickles, kelp and even a
trident are all reachable today, through fishing, the Sequencer's spawn eggs, the sewer residents and
the Printer teardown. Kelp in particular is solid: it is a weight-3 entry in the Hydroponics Bay's
seedling table, so it is renewable rather than found. **What is missing is materials**, and only two of
those are mob drops: shards and crystals from a guardian, and wet sponge from an elder guardian, which
is ruled out.

**The two exceptions are the turtle's, and the resource checklist is wrong about the second one.**
`SewerTurtleDen` places three adult turtles in every sewer, persistent, and a turtle drops seagrass -
so seagrass is real but **finite and one-way**, capped at the sewers a player finds times three
turtles, and each unit costs a turtle permanently. `turtle_scute` is worse than finite: the checklist
says "a turtle grows up", and **no turtle here can**. Scute drops only when a baby matures, egg-laying
needs `y < seaLevel + 4` against a sea level of -64 (which `SewerLifeTests` already records as the
reason turtles are not renewable), and the den cannot spawn a baby either: it calls `finalizeSpawn`
with a null group data once per turtle, and `AgeableMob` gates its baby roll behind
`getGroupSize() > 0`, which a fresh group data never satisfies. So every den turtle is an adult,
forever. **Not this structure's problem to fix**, but it is a live checklist error and it is filed
rather than folded in here.

**Correction to the first draft on the nautilus armours.** It said they "need nothing from this"
because `nautilus_shell` is already reachable. That is wrong: the copper, iron, golden and diamond
nautilus armours have **no recipe at all**. They are chest loot from buried treasure, the three
shipwreck tables and the two underwater ruin tables, none of which generate here, and the shell is not
an ingredient in any of them. Only the netherite one is a smithing upgrade. So they are genuinely
blocked, and whether this building is where they come from is a new open question (8.6).

**What it deliberately does NOT do:** no ocean biome, no elder guardian, no water body beyond what
section 5 needs, no conduit tutorial. It supplies materials, not an ecosystem.

---

## 2. Placement

**Region: the demolition yard** (decided, ruling 8.5). It is the region of ruined buildings - Building
Husks, smokestacks, steel stacks - and an aquarium is a building. Household sprawl is where people
lived rather than where civic buildings stood, and the radioactive dump already carries the cooling
tower.

**Rare, like the cooling tower rather than common like the smokestacks.** One aquarium is a landmark
you travel to and remember; three would be scenery. Proposed `random_spread` with spacing well above
the smokestacks', tuned so a player crosses one per few thousand blocks of yard.

Mechanically it is the shipped landmark pattern with nothing new:

- a `StructureType` + `StructurePiece` under `content/worldgen/aquarium/`
- `worldgen/structure/municipal_aquarium.json` naming `#recompile:has_structure/municipal_aquarium`,
  `step: top_layer_modification`, `terrain_adaptation: beard_thin`
- `worldgen/structure_set/municipal_aquarium.json` with `random_spread`
- the biome tag holding `recompile:demolition_yard`

---

## 3. The shape

Proposed, and the part most likely to change once something is standing in the world.

- **A low, wide hall** rather than a tower. The two existing landmarks are both vertical and read at
  distance; this one should read on approach instead, so the skyline does not gain a third silhouette
  competing with the tower.
- **Tank walls of cracked glass and prismarine**, some collapsed inward so the interior is enterable
  without being a puzzle. The player should not have to break in - the smokestack ruling (a husk that
  reaches out past the brick, so walking past is an encounter) applies.
- **Shallow leachate in the tank floors and the sumps** (ruling 8.1). One block deep, the depth the
  existing household-sprawl pools already use, so it reads as standing runoff rather than as an exhibit
  anyone maintained.
- **A filtration hall below or behind**, silted, where the sponges are.
- **One centrepiece tank**, the largest, holding the heart of the sea.
- **One guardian tank**, which is the only part of the building holding real water (section 5).
- **Signage and railings** as dressing, in the mod's existing salvage palette so it reads as continuous
  with the yard rather than as an imported vanilla build.

### 3.1 The room graph

**This is the plan, and it is deliberately a room list rather than a drawing.** The two landmark
precedents are solids of revolution - the cooling tower is one function of height,
`r = throat * sqrt(1 + (dy/c)^2)`, in a single piece - so a profile test could check the whole
building. Rooms cannot be expressed that way, which makes **the sewer the real precedent** and not the
towers. See section 9, which has been corrected accordingly.

Sizes below are starting points for the fixtures, not measurements to honour. **The numbers live in
code once it exists**; this table exists to settle what rooms there are and how they connect, which is
the part that is cheaper to argue here than in Java.

| Room | Holds | Connects to | Rough footprint |
|---|---|---|---|
| **Forecourt** | signage, railings, the collapsed frontage that is the way in | Lobby | 12x6, open to sky |
| **Lobby** | ticket desk, more signage, leachate puddles | Forecourt, Gallery | 10x8 |
| **Reef Gallery** | the tank rows: **most of the 15 dead corals**, cracked glass, shallow leachate in the tank floors | Lobby, Big Tank, Guardian Tank, Back of House | 20x10, the largest room |
| **Big Tank** | the centrepiece, **heart of the sea**, prismarine and sea lanterns at their densest | Reef Gallery | 8x8, tallest volume |
| **Guardian Tank** | **the only real water in the building**, one guardian spawner | Reef Gallery | 6x6, breached |
| **Filtration Hall** | **sponge and wet sponge**, silt, pipework, the sump | Reef Gallery, by ramp | 12x8, half-sunk |
| **Back of House** | the curator's chest, the drowned spawner | Reef Gallery, Filtration Hall | 8x6 |

**Four rules that the arrangement has to satisfy**, each of them a decision rather than a preference:

1. **Every room is reachable from the forecourt without breaking a block.** This is the "enterable
   without being a puzzle" line made checkable. Collapse is the mechanism - a fallen frontage, a
   breached tank wall, a ramp where a stair rotted out - so the ruin is what opens the building rather
   than the player's pickaxe.
2. **The Guardian Tank is breached, not sealed.** A guardian behind intact glass is inert to anyone who
   does not break in, which is the mistake the smokestacks already made once and which section 5 warns
   about in its own words. Open the top or take out a wall panel, so its spawn range reaches somebody
   standing in the gallery. It is also the only room a player can drown in on purpose, which is a fair
   trade for the only bucket-fillable water in the region.
3. **The Filtration Hall is behind and half-sunk, not below.** "Below" was left open in the first
   draft; a basement under a low wide hall needs a stair, and a stair is the one thing a player can
   walk past without noticing, which would hide both sponges. Half-sunk off the gallery keeps the whole
   building one connected walk and gives the sump a reason to be the lowest point in the structure,
   which is where leachate should pool.
4. **Exactly one room holds water and every other wet surface is leachate.** Stated as a rule because
   the two fluids are one block id apart in a generator and the failure is silent in both directions:
   leachate in the guardian tank spawns nothing forever, and water anywhere else quietly doubles the
   free-water route this structure was careful to bound.

**What is deliberately not decided here:** exact dimensions, wall thickness, roof treatment, how many
tank rows the gallery holds, and whether the building is one `StructurePiece` per room or a few larger
ones. Those are build-time questions, and the sewer answers the last one by example - it assembles
named pieces with computed boxes rather than one piece that draws everything.

---

## 4. The coral revival chain

**Approved, ruling 8.2.** Dead coral is what the building holds. Live coral is what a player makes.
This is the mod's established "put back what left" pattern, and it is the third instance of it:

| Chain | What left | What puts it back |
|---|---|---|
| Clay (#115) | bound hydroxyls, driven off by firing | bentonite in cat litter |
| Resin (#231) | volatiles, driven off by fossilisation | turpentine |
| **Coral** | **the symbiotic algae, driven off by bleaching** | **the Hydroponics Bay: water + light** |

**Mechanism decided (owner, 2026-09-03): the Hydroponics Bay.** Put dead coral in, get live coral out.
It is the machine that already consumes water and power together, which is precisely what an algae
culture needs, so "water + light" stopped being a placeholder and became a machine that already
exists.

**It is almost free to build, and that is a property of the bay rather than luck.** `yieldOf` reads the
`HYDROPONIC_CROP` data map and `isGrowable` reads the `#recompile:hydroponic` item tag, so each of the
five colours is one tag line plus one data-map line. **Zero Java**, and a pack can retune or extend it.

**One consequence falls straight out and is worth stating rather than discovering.** The bay does not
consume what you seed it with - its own javadoc is explicit that a seeded plant is a crop and the input
stack is never consumed, because the bay replants itself. So **one dead coral is a permanent source of
that colour**, and the fifteen dead coral items the building holds become fifteen renewable lines
rather than fifteen one-shots. That reads as correct here: it makes a living reef the reward for having
built water and power infrastructure, which is the same shape as every other thing the bay grows.

**Which of the three forms revives into which** is the one detail left to build time. There are fifteen
dead items across five colours and three forms (coral, fan, block), and the obvious mapping is each
form to its own live counterpart, which keeps the data map honest and needs no special cases.

Coral bleaching is the expulsion of zooxanthellae under stress; the skeleton survives and the colour
does not. Reviving bleached coral is real restoration practice, so the chain is honest in the way the
clay chain is honest and a "grind it and add water" shortcut would not have been.

**The constraint that shapes the whole feature, and it cannot be engineered away here.** Vanilla kills
live coral out of water: a coral block, plant or fan not touching water turns to its dead form on the
next scheduled tick. That behaviour lives in vanilla's own block classes, and **this mod has no
mixins**, so it cannot be overridden. Revived coral is therefore **placeable only waterlogged or
submerged**, permanently and by construction.

**That is a feature rather than a defect, and it is the honest reading.** It makes live coral a
decorative flex that costs standing water to maintain, in a world where water is scarce and comes from
Rain Collectors and the sewers. A player who has built enough water infrastructure to keep a reef alive
has earned the reef. What it must not do is ship as a surprise: the guidebook entry has to say that
coral dies dry, or the first thing a player does with a revived coral is lose it.

**Leachate does not count**, for the same reason it does not count anywhere else: it is in no fluid tag,
so vanilla's water checks do not see it.

---

## 5. Spawners, and the chest

**A guardian spawner in the guardian tank** (ruling 8.3), plus the drowned spawner the landmark
precedent calls for (`Spawners.java`, owner 2026-08-31: a spawner is not loot but it is not nothing
either). Drowned in a drained aquarium is the obvious occupant and needs no explanation; the guardian
is the prismarine route.

**The guardian tank must hold real water, not leachate, and the mechanism is worth writing down
because it fails silently.** `BaseSpawner` DOES consult spawn placement rules before spawning, and
`Guardian.checkGuardianSpawnRules` requires `#minecraft:water` in the block BELOW **unconditionally** -
that clause has no spawner exemption, unlike the clause above it. `RCFluids.LEACHATE_TYPE` is
deliberately in no fluid tag, so a guardian spawner sitting over leachate spawns **nothing, ever, with
nothing logged**. Force one in anyway and it is worse, not better: `Entity.isInWater` is tag-gated, so
the guardian never enters its swimming travel mode, `WaterBoundPathNavigation` never repaths, it flops
on the tank floor forever, and this mod's own `RCLeachateContact` drowns it at 2 damage a tick. Owner
accepted the fallback in advance: **real water in that one pool.**

**The drowned spawner is fine in leachate, and that contrast is the useful part.** `Drowned`'s
predicate short-circuits on the spawner flag before it reaches its water check, which is exactly why
the sewer sump's drowned spawner already works over leachate today (`RCSewerSpawns`). Drowned is the
only vanilla water-flavoured mob with that escape hatch, so it is the only one that can stand in a
leachate pool at all.

**What the water actually costs, corrected twice over.** The first draft of 8.1 said a pool would be
"a bigger free-water route than anything currently in the game"; a later draft of this section said
the opposite, that sewer water made it a second instance of an existing route. Both were wrong:

- **Sewer water is not the precedent.** No sewer code places `Blocks.WATER` at all. The checklist's
  "fill a bucket from sewer water" is a hand-declared `INTERACT` line in `reachability.py`, and it is
  the same shape of unverified claim as the `large_fern` premise that #344 had to correct.
- **The real precedent is the tailings decant pond**, which `TailingsHeapFeature` fills with plain
  vanilla water, in the radioactive dump. So free infinite water already exists in this world.
- **Volume is not a dial, so "keep it small" is not a mitigation.** Two source blocks regenerate each
  other forever. A two-block tank and a room-sized reservoir are the same infinite tap, and any
  argument that reaches for a counted number of blocks is answering the wrong question.

**The one dial that is real is distance.** The demolition yard has onset 512 and the radioactive dump
1024, so putting water in this building does not create free water, it moves the existing free water
**twice as close**. That is the decision to make knowingly, and it is an economy call rather than a set
dressing one. Section 8.6 lists the way out if it is judged too cheap.

The smokestack lesson applies to both spawners: give them a spawn range wide enough that mobs appear
around a player walking past rather than staying sealed in a tank until somebody breaks the glass. A
landmark inert to anyone who does not attack it was the wrong call once already.

**Not elder guardians.** They are the only mob source of wet sponge, which is a real cost of this
ruling: sponges stay placed-only and therefore finite. An elder guardian is a boss-weight encounter
with a mining-fatigue aura, and putting one in a landmark a player walks into is a different feature.

### 5.1 The curator's chest

**Decided (owner, 2026-09-03): the building carries a loot chest with its own table**, and its job is
to clear esoteric items that have no source anywhere else. That settles the nautilus armours, which
were the open question in 8.6, and it gives the building a second job beyond supplying blocks.

**The selection rule matters more than the list, because a chest with no rule becomes a vending
machine.** 146 vanilla items are unreachable today and most of them have nothing to do with a public
aquarium; dumping the interesting ones in one crate would make this building the answer to every gap
in the game and would read as exactly that. The rule that keeps it honest: **an abandoned public
aquarium is a small zoo and a museum**, so what is left in its storage is what a CURATOR had, not what
a player wants. Exhibit stock, dive kit, the gift shop's inventory, the archive.

**Contents, as ruled (owner, 2026-09-03):**

| Group | Count | Note |
|---|---|---|
| **Armor trim smithing templates** | 16 | **reverses ruling 8.4**, see below |
| **Wetlands-wing plants** | 6 | `glow_lichen`, `big_dripleaf`, `spore_blossom`, `hanging_roots`, `azalea`, `flowering_azalea` |
| **Sponges** | 2 | also placed in the filtration hall; the chest carries spares |
| **`turtle_scute`** | 1 | its only route in the game, see #345 |
| **Enchanted books** | 0 | flavour rather than a gap: already reachable from a librarian |
| **Ocean-related resources** | ? | called for but not enumerated; candidates below |

**Three things about that list need saying rather than quietly implementing.**

**The trims reverse 8.4, and the reversal is bigger than the ruling it replaces.** 8.4 cut the two
OCEAN trims, `coast` and `tide`, from this structure. Putting trims in the chest brings back all
**sixteen** unreachable templates, which is every trim in the game that has no source here, and it
makes this one chest the sole route to the entire trim system. #328 leans toward ruling trims out as
cosmetic; if that ruling ever lands it takes this group with it. Recorded as a reversal so nobody has
to reconstruct why 8.4 says one thing and 5.1 says another.

**"Ocean-related resources" is not yet a list.** What is actually left unreachable and marine, once the
placed blocks and the guardian are accounted for: the **four nautilus armours** (chest loot in vanilla,
no recipe, and **no other possible home in this mod** - if they are not here they stay unreachable
forever) and `heart_of_the_sea` (currently specced as the placed centrepiece, so the chest is an
alternative rather than an addition). Both are proposed for this slot.

**The 19 pottery sherds were not selected, and that is the largest single gap left open.** Nineteen of
the twenty have no source, `heart_pottery_sherd` is the only reachable one, and maritime archaeology is
about as natural an aquarium exhibit as exists. Left out because it was not asked for, not because a
reason was given; worth one more look before the table is written.

**Explicitly out**, so the rule is not quietly abandoned later: everything End-locked (`shulker_shell`,
the dragon set, `elytra`, `chorus_*`), everything from the Nether, every ore, and the trial-chamber
group (`heavy_core`, `trial_key`, `ominous_*`, `totem_of_undying`). None of them is something a curator
had in a cupboard.

**One tension to settle when the table is written.** The mod's rule is that finished goods are found
rather than crafted, which a chest satisfies perfectly - but the plants and sherds are materials while
the armours and trims are finished goods, so this one table straddles both. That is allowed and
probably right for a gift shop; it just means pools with different weights rather than one flat list,
and the armours and trims want to be rare.

---

## 6. What this does to the checklist

If built as specced, `tools/resource_checklist` should move these from unreachable to reachable:

| Piece | Items | Renewable? |
|---|---|---|
| Placed cladding, lighting, sponges, dead coral, heart of the sea | 22 | no, finite per building |
| Guardian tank (shards and crystals) | 2 | **yes** - and it makes the 4 prismarine blocks renewable too |
| Coral revival chain | 15 | yes, from the dead coral the building holds |

The nautilus armours are **not** covered by any of the above; see 8.6. The two ocean trims are out of
scope by ruling 8.4.

---

## 7. Why not the alternatives

- **An ocean biome.** Needs aquifers or a sea level the noise settings do not have, and a dump with a
  coastline is a different game. Rejected on concept.
- **Put the materials in a pull stream.** Prismarine in a bin bag is not a thing anyone threw away, and
  the mod's "would a person throw this away" test refuses it. Dead coral passes that test easily, but a
  souvenir alone leaves prismarine and the heart of the sea unsourced.
- **A shipwreck.** Same water problem, and a wreck on dry land in a landfill is a stretch the aquarium
  does not need.

---

## 8. The rulings (owner, 2026-09-03)

### 8.1 Does it hold any water? DECIDED: leachate, plus one small water tank

**Shallow pools of leachate** in the tank floors and the sumps. Leachate costs the economy nothing by
construction - `canHydrate` is false so it never irrigates, the Rain Collector's tank rejects anything
that is not water, and `leachate_is_not_water` pins both - so a pool is atmosphere with no economic
side effect. It is also the more honest fluid for the building: what pools in an abandoned structure is
runoff, not sea water.

**One exception**, forced by 8.3: the guardian tank holds real water, because a guardian cannot use
leachate and fails silently rather than visibly. Section 5 has the mechanism and the corrected cost.
The short version: free infinite water already exists in the tailings ponds, volume is not a dial
because two source blocks regenerate forever, and what this actually changes is the **distance** at
which free water becomes available - onset 512 rather than 1024.

### 8.2 Is the coral revival chain worth building? DECIDED: yes

Build it. The vanilla "coral dies dry" behaviour stands and cannot be modded out without mixins, which
this mod does not have, so the product is a water-gated decoration. Section 4 argues why that is
acceptable and what the guidebook has to say about it.

### 8.3 Does it answer #44 (aquatic life)? DECIDED: a guardian, and nothing more

The first draft recommended no mob content at all. The ruling admits **one guardian spawner** and
nothing else, on the strength of the renewability argument in section 1 - guardians are the only source
of prismarine shards and crystals, so without them the prismarine family is a finite strip of the
building. It is still not the answer to #44: no fish, no dolphins, no turtles, no ecosystem, and #44
stays a separate question. The aquatic animals it might have supplied are already reachable anyway.

### 8.4 Are the two ocean trims wanted at all? DECIDED: out, then REVERSED

Originally cut: `coast` and `tide` left section 1 and the structure lost nothing. **Superseded the same
day** by the chest ruling, which puts all sixteen unreachable trim templates in the curator's chest and
so makes this building the only route to the trim system. See 5.1. The original reasoning is left
standing above because #328 may yet rule trims out globally, and if it does this is the decision that
gets revisited.

### 8.5 Region? DECIDED: the demolition yard

On the "it is a building" argument. Section 2 stands as written.

### 8.6 What came out of the rulings, and is still open

1. **DECIDED: the guardian AND a machine recipe** (owner, 2026-09-03). The tank stays, and prismarine
   shards also get a manufactured route, which is this mod's established practice for a gated material
   - exactly how #277 sourced AE2's certus and fluix, on the reasoning that only a machine produces at
   playthrough scale. Belt and braces: prismarine stays renewable even if the tank is ever cut.
   **What the machine eats is still open.** It should be a `separating` recipe, because dividing a
   mixture is the Separator's verb and prismarine is a silicate fraction. The leading candidate input is
   **silt** - the filtration hall is full of it, the sewers already have it as a brushable material, and
   it keeps the route tied to this building rather than appearing from nowhere. Not decided, and it must
   not be anything downstream of prismarine itself or the recipe is circular.
2. **DECIDED: the building carries a loot chest** (owner, 2026-09-03), and 5.1 now records the ruled
   contents. Two things there are still open: what "ocean-related resources" resolves to (the four
   nautilus armours are proposed, and they have no other home in the game), and whether the nineteen
   pottery sherds go in, which is the largest gap this building could close and was not asked for
   either way.
3. **`leachate_is_not_water` does not assert what its name says.** It compares fluid identity, so it
   would stay green if leachate were ever added to `#minecraft:water` - which is the single edit that
   would undo the fluid's whole reason for existing, and the one this structure creates a temptation to
   make. Not an aquarium defect and not this spec's to fix, but it is the guard that would have to hold
   while this gets built.

---

## 9. Build notes

- **Follow the SEWER, not the towers.** The first draft of these notes said to follow
  `CoolingTowerStructure` / `SmokestackStructure`, and that is wrong for this building. Both of those
  are solids of revolution built as a single piece from a radius-by-height function, which is why a
  profile test could check them end to end. This is a set of connected rooms, so the model is
  `SewerStructure`: named pieces with computed boxes, assembled by one method that owns the layout.
- **Build the fixtures class on day one, not after the first drift.** `SewerFixtures` exists because
  the sewer's layout drifted from its real build order **twice**, once when access chambers landed and
  once when the sump did, and its own comment says three copies would be three chances to drift again.
  It was still not enough on its own: a habitability test hand-built a den at a size the game had
  stopped generating, so it measured a room that does not exist and would have gone on passing while
  the real one broke. **Tests ask the structure for its boxes; they never retype them.**
- **A shape test before a world test.** `CoolingTowerProfileTest` and `SmokestackProfileTest` are JUnit
  and catch shape errors without booting anything - the cooling tower's first pass read as a chimney
  and that is how it was caught. The equivalent here is not a profile but a **reachability** check:
  every room in 3.1 entered from the forecourt without breaking a block, which is rule 1 as a test.
- `ShellHasNoFloatersTest` is the precedent for asserting a structure has no disconnected blocks.
- **Assert the guardian tank is water and the rest is leachate**, in a test. The two fluids are one
  block id apart in a structure template and the failure is silent in the strongest sense: the spawner
  checks placement rules, the water-below clause fails, and it spawns nothing forever with nothing
  logged. Nobody looks inside a sealed tank to find out it is empty on purpose.
- Verify in a fresh world: the generator is baked into `level.dat` at creation, so an existing save
  will never show it. `tools/make_dev_world.py`, then `gamebridge check` with a `locate` command.

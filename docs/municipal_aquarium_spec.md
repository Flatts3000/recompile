# The Municipal Aquarium - landmark spec

**Status: DRAFT, unbuilt.** Proposed 2026-09-03 as the answer to #324 (ocean materials unreachable) and
a partial answer to #328 (armor trims). Nothing here is decided; the open rulings are collected in
section 8 and three of them are load-bearing enough that building without them would be guessing.

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

---

## 1. What it is for

Closing #324 without adding an ocean. Specifically:

| Family | Count | How it arrives |
|---|---|---|
| Prismarine, dark prismarine, prismarine bricks, sea lantern | 4 | **cladding and lighting**, placed as blocks |
| Sponge, wet sponge | 2 | the **filtration** hall |
| Dead coral, dead coral fans, dead coral blocks (5 colours) | 15 | **exhibit remains**, placed and found |
| Live coral, fans, blocks (5 colours) | 15 | **not placed** - see section 4, the revival chain |
| `heart_of_the_sea` | 1 | the centrepiece exhibit, one per building |
| `nautilus_shell` | - | already reachable; the gift shop is flavour, not a route |
| `coast` / `tide` armor trim templates | 2 | a marine building is the natural carrier (#328) |

**Prismarine crystals** fall out of sea lanterns. **Nautilus armour** follows from shells, which are
already reachable, so it is not this structure's job.

**What it deliberately does NOT do:** no ocean biome, no guardians, no elder guardian, no water body,
no conduit tutorial. It supplies materials, not an ecosystem. #44 (aquatic life) stays a separate
question, and section 8.3 says why this structure should not quietly answer it.

---

## 2. Placement

**Region: the demolition yard** (proposed). It is the region of ruined buildings - Building Husks,
smokestacks, steel stacks - and an aquarium is a building. Household sprawl is where people lived
rather than where civic buildings stood, and the radioactive dump already carries the cooling tower.

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
- **A filtration hall below or behind**, silted, where the sponges are.
- **One centrepiece tank**, the largest, holding the heart of the sea.
- **Signage and railings** as dressing, in the mod's existing salvage palette so it reads as continuous
  with the yard rather than as an imported vanilla build.

**It must not be a wet build.** See 8.1.

---

## 4. The coral revival chain

**Dead coral is what the building holds. Live coral is what a player makes.** This is the mod's
established "put back what left" pattern, and it is the third instance of it:

| Chain | What left | What puts it back |
|---|---|---|
| Clay (#115) | bound hydroxyls, driven off by firing | bentonite in cat litter |
| Resin (#231) | volatiles, driven off by fossilisation | turpentine |
| **Coral** | **the symbiotic algae, driven off by bleaching** | **proposed: water + light** |

Coral bleaching is the expulsion of zooxanthellae under stress; the skeleton survives and the colour
does not. Reviving bleached coral is real restoration practice, so the chain is honest in the way the
clay chain is honest and a "grind it and add water" shortcut would not have been.

**Vanilla already models half of this and it is worth not fighting:** live coral placed out of water
dies. Whatever the revival step is, the product has to be storable and placeable on dry land or it is
useless in this world. That may mean the revived coral is a **block** rather than a plant, or that this
chain is not worth building at all and the dead half is the whole answer. **Open, 8.2.**

---

## 5. Spawners

**One vanilla `drowned` spawner**, per the landmark precedent (`Spawners.java`, owner 2026-08-31: a
spawner is not loot but it is not nothing either). Drowned in a drained aquarium is the obvious
occupant and needs no explanation.

The smokestack lesson applies: give it a spawn range wide enough that the mobs appear around a player
walking past rather than staying sealed in a tank until somebody breaks the glass. A landmark inert to
anyone who does not attack it was the wrong call once already.

**Not guardians.** They are the ocean's, they would imply prismarine is farmable here, and vanilla
guardians need water to spawn in.

---

## 6. What this does to the checklist

If built as specced, `tools/resource_checklist` should move roughly 24 items from unreachable to
reachable: 4 prismarine-family blocks, 2 sponges, 15 dead coral, the heart of the sea, and 2 trims.
The 15 **live** coral items move only if section 4 is built.

That leaves #324's remainder as the nautilus armours (already reachable via shells) and nothing else.

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

## 8. Open rulings, which are the reason this is a draft

### 8.1 Does it hold any water? (load-bearing)

**Recommend: no, or one bounded pocket at most.** Water is the P1.10 economy's scarce input. The Rain
Collector's monopoly and the fridge-ice ruling (2026-08-12, allowed *because* it costs a Bulky Waste
find, a prybar and a 1-in-4 draw) both show that a water source is a real economic decision, not
dressing. `leachate_is_not_water` exists for the same reason.

A structure-scale pool of source blocks would be a bigger free-water route than anything in the game,
arriving at whatever range the yard begins. If the tanks hold water at all it should be a deliberate,
counted number in one place, argued the way the ice was.

### 8.2 Is the coral revival chain worth building? (load-bearing)

The dead half closes 15 items on its own. The live half adds 15 more, needs a new interaction, and runs
into vanilla's "coral dies out of water" behaviour, which in a world with almost no water may make live
coral unplaceable and therefore pointless. **Recommend deciding this before any code**, because it
changes whether section 4 exists at all.

### 8.3 Does this answer #44 (aquatic life)?

**Recommend: no, explicitly.** The temptation is preserved specimens in the tanks feeding the
Sequencer into fish spawn eggs. That is a coherent idea and a separate one; letting a materials
structure quietly become the mob answer would repeat the pattern where a feature's second job is
discovered later rather than decided. Note it in #44 and leave it there.

### 8.4 Are the two ocean trims wanted at all?

#328 leans toward ruling all trims out as cosmetic. If that ruling lands, coast and tide come out of
section 1 and this structure loses nothing.

### 8.5 Region

Demolition yard is proposed on the "it is a building" argument. Household sprawl is defensible on
"municipal means a town had it". Worth a moment, since it decides how early the prismarine tier opens.

---

## 9. Build notes

- The landmark pattern is proven twice; nothing here needs a new mechanism. Follow
  `CoolingTowerStructure` / `SmokestackStructure` rather than inventing.
- **A profile test before a world test.** `CoolingTowerProfileTest` and `SmokestackProfileTest` are
  JUnit and catch shape errors without booting anything; the cooling tower's first pass read as a
  chimney and that is how it was caught.
- `ShellHasNoFloatersTest` is the precedent for asserting a structure has no disconnected blocks.
- Verify in a fresh world: the generator is baked into `level.dat` at creation, so an existing save
  will never show it. `tools/make_dev_world.py`, then `gamebridge check` with a `locate` command.

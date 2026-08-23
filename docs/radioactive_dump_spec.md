# Radioactive Dump - region spec

**Issue:** #285. **Status:** V1 shipped 2026-08-22. **Owner decisions are dated.**

The second frontier region, beyond the demolition yard. It exists because **Powah is unstartable in
this world** - every non-circular route to `powah:uraninite` runs through an ore whose biome modifiers
gate on `#minecraft:is_overworld`, and this mod ships no entry for that tag by owner ruling
(2026-08-20). Rather than open that door, the material is **found** in a region we place ourselves.

---

## 0. The concept in one paragraph

Somewhere past the demolition yard, someone buried the things nobody would take. Not a crater and not a
science-fiction wasteland - a **landfill with drums in it**: mill tailings left in open heaps, steel
drums with a trefoil stencilled on the side, and the ordinary domestic radioactive objects that really
do end up in refuse - dial clocks, welding rods, smoke detectors, uranium glass. You go because it is
the only uranium in the world. You leave when you have stripped it, because **nothing here grows back**.

---

## 1. Placement

**DECIDED (2026-08-22): onset 1024.** A second entry in the `frontier` list of
`world_preset/garbage.json`, which today holds exactly one:

```json
{ "biome": "recompile:demolition_yard",  "onset": 512  }
{ "biome": "recompile:radioactive_dump", "onset": 1024 }
```

with `core_radius: 512`, `falloff: 256.0`, `household_floor: 0.2`, `noise_scale: 0.5`.

Double the yard: **a short trip past somewhere the player already goes**, so they will stumble into it
while working the yard rather than having to go looking. That makes Powah an early-mid unlock,
reachable well before the Nether, and it removes the main risk of a distant region - that it is never
found at all. It also sets up a deliberate tension with section 8: **you find the place before you can
exploit it**, which turns the tool into a goal rather than a wall.

Placement is by distance gradient plus noise, so a world holds **many** radioactive dumps. That matters
given section 3: each deposit is finite, the world is not.

**The generator is baked into `level.dat` at world creation**, so this only affects NEW worlds. Testing
needs a fresh one, and `runClient`'s quickPlay world is not it - quickPlay creates a DEFAULT world that
silently ignores the preset. Use `runServer` with `level-type=recompile\:garbage` and probe over RCON.

---

## 2. The biome

- **Surface: unchanged coarse dirt**, as the yard is. Identity comes from what is scattered on top,
  never from re-surfacing: the reclamation ladder and encroachment both ride on the coarse-dirt surface
  rule, and re-surfacing would silently break the Grass Spreader. Stained Ground (section 4) is the one
  qualification and is deliberate.
- **DECIDED (2026-08-22): hostile spawns ON, the same set as the yard** - zombie, skeleton, spider,
  creeper; creature and other lists empty. Consistent with the only other frontier region, and it
  gives V1 some teeth while radiation is deferred, which matters because V1 otherwise has no threat at
  all.
- **DECIDED (2026-08-22): in `#recompile:encroaches`.** Same as both existing biomes. The yard's
  exception was reversed on 2026-07-31 for a reason that applies identically here: **the asymmetry was
  undiscoverable.** A player who learns "grass reverts unless you anchor it" in the sprawl carries that
  rule here, watches it not happen, and reads the design as a bug - which is exactly how it surfaced,
  as a playtest report. One rule everywhere beat the tuning the exception bought.

### 2.1 Atmosphere (owner, 2026-08-23)

**DECIDED: windblown dust and a sallow haze. No ambient sound.**

V1 ships no radiation, so the air is one of the few things carrying the region's identity, and the
honest version of it is dust: radon and windblown fines off an uncovered impoundment are what the real
remediation programmes are about. It is `minecraft:white_ash` at probability **0.025**, calibrated
against vanilla's own four ambient-particle biomes (0.00625 soul sand valley, 0.01428 warped forest,
0.025 crimson forest, 0.118 basalt deltas). The last of those is a blizzard and reads as weather
rather than as a place.

**No fog distance.** `visual/fog_start_distance` and `visual/fog_end_distance` exist and are tempting,
and no vanilla biome sets either - the only distance vanilla touches is water fog, with a MULTIPLY
modifier rather than an absolute. Pulling the horizon in on a region a player has to walk 1024 blocks
to reach is a playability change dressed up as art.

**A sound was considered and declined.** `audio/ambient_sounds` takes a `loop`, and the only sounds
available without authoring audio are vanilla's - which are all nether or cave loops. One of those
under an open overworld sky reads as a bug rather than as atmosphere.

**The mechanism is `attributes`, not `effects`, and that is not a detail.** 26.1 split
`BiomeSpecialEffects`; see CLAUDE.md's API-deltas section. Every biome in this mod had been shipping
its fog and sky in the dead half.

---

## 3. Nothing here regrows (owner, 2026-08-22)

**Tailings do not regrow.** A deposit is stripped once and stays stripped.

This is **consistent with existing behaviour rather than a new exception**, which is worth stating
because it reads like one. `MoundGroundBlock` - the block that remembers a mound's footprint and
respawns it - is written by `MoundFeature` alone. The demolition yard's four features (Building Husk,
Rubble Pile, Steel Stack, Mechanical Waste Pile) write none of it, so **the yard already does not
regrow**. The rule was there and unstated:

> The sprawl regrows because you live in it. The frontier does not, because you leave.

**The consequence, accepted:** `powah:uraninite` is reactor *fuel*, a running cost rather than a
one-time build cost, so a player exhausts a deposit and travels to the next. That is what uranium
extraction is, and the noisy gradient means there is always a next one.

**A note for V2**, not a commitment: once Mekanism is in, the renewable radioactive material is the
**nuclear waste the player produces themselves**. You clear their dump, then you make your own - which
resolves finiteness without ever making waste replenish itself.

---

## 4. Blocks

**DECIDED (2026-08-22): three.** Two sortables and one dressing block. The yard has four features and
three block families and cost an entire phase; this is deliberately smaller.

A `SortableBlock` variant supplies five things - a `sorted` property, a pull table, a min/max crumble
window, and a required tool - and there are already seven variants, so **no new mechanic is needed**
(but see section 8 for the one change the tool gate does need).

Each sortable does three jobs at once, and that is what makes the design tight: it **holds the loot**,
it **will emit the dose** (section 6), and **clearing it removes both**.

### 4.0 The shape they come in (owner, 2026-08-23)

**DECIDED: one broad impoundment per ~3 chunks, not a field of mounds.**

The first shape was 5-11 wide heaps at four per chunk and it was wrong in a way the numbers could not
see: a census found tailings, stain and drums all in correct proportion, and a screenshot showed
cupcakes with a candle on each. The tells were a one-block centre spire (which the drum was then
perched on), a single sharp step for a side, and - at radius 2-3 - a literal plus sign for a
silhouette.

The reference is Moab and Church Rock: **one enormous engineered pile**, roughly 1:12 across to high,
flat on top, with a pale turquoise decant pond and a barren stained ring. The flat top was never the
problem; the scale was.

So: radius 9-12, height 3-4, a skirt whose width is derived from the height at the angle of repose, a
lobed outline from two sine harmonics, drums clustered at the **toe** rather than the summit, and a
decant pond cut one block into the plateau.

**The radius ceiling is an engine limit, not taste.** `ChunkStatus.FEATURES` carries
`blockStateWriteRadius(1)` and `WorldGenRegion.ensureCanWrite` compares chunk coordinates against it,
so a feature may only write 16 blocks from its origin in the worst case. The longest lobe plus the
stain ring is `12 * 1.21 + 1 = 15.5`. A draft of this rewrite used radius 16 with drums thrown to 23;
every block past the limit is silently rejected and logged at ERROR, and the pile comes out sheared
flat along a chunk line. The pond is plain water tinted by the biome's
`water_color` - deliberately **not** the mod's Leachate block, which is rain drained through refuse and
sprawl-only by the 2026-08-05 ruling.

**The pond was the trap.** At the first radius and height ranges the skirt ate almost the whole
footprint, so only 26% of piles had a plateau big enough to hold one - and nothing failed, because a
pile without a pond is a valid pile. A census of a real world finding zero water anywhere is what
caught it. `a_decant_pond_is_not_a_coin_flip` pins the rate at 80% or better.

### 4.1 Mill Tailings (bulk)

Sandy uranium-processing waste, genuinely left in open heaps for decades - Moab, Church Rock. Gravity
affected like every `SortableBlock`. This is the ground cover, the main uraninite stream, and the
block the region's economy runs on.

### 4.2 Waste Drum (punctuation)

The 55-gallon steel drum, yellow, trefoil stencilled on the side. Low-level waste really is drummed.
Rarer than tailings, better pulls, and the object that says what the place is.

### 4.3 Stained Ground (dressing, no loot)

A discoloured patch where something leaked. No pull table, no drops - it exists so the ground the drums
sit on reads as contaminated.

**It is a SURFACE block, which is the one qualification to section 2, and it has a consequence worth
deciding deliberately: it cannot be healed.** If it sits outside `#minecraft:substrate_overworld` then
grass will never spread onto it and the Grass Spreader will not convert it.

**That is proposed as correct rather than as a limitation.** Contamination that scrubs clean is not
contamination, and there is precedent: `MoundGroundBlock` is deliberately kept out of
`#minecraft:dirt`, because membership would reach `#encroachable` through `#substrate_overworld` and
the junkyard would eat its own memory.

It does mean the two ground types behave differently inside one biome: **grass is contested on the
clean ground (section 2) and impossible on the stained patches.** Unlike the yard's reverted
exception, this one is *discoverable* - the ground looks different, which is the whole point of the
block.

---

## 5. Finds

**The requirement is uraninite.** Sixteen Powah recipes consume it and the entire energy tier is
downstream; the reachability closure reaches 126 of 133 Powah items once the root exists.

**Shipped as a `minecraft:tag` entry over `#c:raw_materials/uraninite` with `expand: true`**, rather
than a `neoforge:mod_loaded` guard. Naming `powah:uraninite_raw` directly would kill the whole pull
table at parse when Powah is absent, because an item id resolves against the registry when the file is
read; a `TagKey` does not. And `expand: true` is what makes an absent tag contribute **no entries at
all** rather than one that wins rolls and hands back nothing - measured at 341 items from 400 rolls
when flipped to `false`.

**Raw rather than refined, deliberately:** the tag resolves to `powah:uraninite_raw`, and Powah's own
`uraninite_from_raw` smelts it. Finding raw ore in tailings and processing it is what tailings *are* -
spent rock that still has some uranium in it.

**DECIDED (2026-08-22): all four consumer-scale objects ship in V1.** They are the reason this region
is not a science-fiction set piece - household things in a household world, tying it back to the
sprawl instead of making it a separate place. All four pass *would a person throw this away* outright:

| find | referent |
|---|---|
| **Radium dial clock** | Watch and clock faces painted with radium, and the Radium Girls behind them |
| **Uranium glass** | Collectible in real life, glows under UV |
| **Smoke detector** | An americium sealed source inside an utterly mundane object |
| **Thoriated welding rods** | Still sold, mildly radioactive, thrown away constantly |

**OPEN: whether uranium glass is a collectible rather than a material.** The collectibles system exists
for exactly this kind of found object - displayed on a pedestal rather than processed - and uranium
glass is the only one of the four that is genuinely collected in real life. Deciding this decides
whether it needs a display model or a teardown exit.

**Orphan sources** - radiography cameras, teletherapy heads - are the dangerous version, and the
objects behind Goiânia and Ciudad Juárez, both of which were **scrapyards that melted a source into
the metal supply**. Deliberately held for V2: they land far better once radiation is real.

---

## 6. V2: radiation, via Mekanism (owner, 2026-08-21)

**V1 ships no radiation at all.** Mekanism ships a complete system - dose accumulation, Geiger counter,
hazmat suit, poisoning - and building a parallel one now is work that gets deleted.

**The design, when it lands: the blocks are the sources.** Not a biome-wide debuff.

```java
IRadiationManager.INSTANCE.radiate(Level, BlockPos, double);   // a drum, a tailings pile
IRadiationManager.INSTANCE.radiate(LivingEntity, double);      // direct dose
```

This is what Mekanism's model is built for: `getRadiationSources()` is a table keyed by chunk and
position, and sources are things placed in the world. **There is no data-driven or biome-wide
radiation** - no biome tag, nothing a datapack can point at a region - so this needs our Java either
way. Confirmed against `Mekanism-1.21.1-10.7.19.85`.

Why block-as-source is the better shape:

- The hazard **concentrates around the waste and falls off with distance**, which a biome-wide sweep
  cannot express.
- It **recedes as you clear the dump**, which is the correct feeling for a mod about clearing dumps.
- A **per-block dose is free** - a drum is hotter than tailings because it is a different `double`.
- `IRadiationShielding` is a capability, so Mekanism's hazmat suit works with no knowledge of ours.

**Three things V1 must therefore NOT build**, because Mekanism supersedes each:

1. **No Geiger counter.** Mekanism's reads *its* radiation, not ours.
2. **No shielding or armour.** This mod has no armour system and should not gain one for this.
3. **No damage mechanic.** See section 7.

A shielded container - a lead pig or cask - is also held back, both because it belongs with radiation
and because **lead does not exist in this world today**; Mekanism brings it.

---

## 7. The hazard ruling this must not walk into

**Leachate is the precedent** (owner, 2026-08-05): *"Standing in it makes you ill, and deliberately
nothing worse."* No damage, no Poison, no Wither, cannot kill. Hunger for a few seconds, refreshed
rather than stacked. Two reasons recorded with it:

- **Hunger over Nausea** - nausea is a screen-wobble a player reads as the game being unpleasant
  without learning anything; hunger costs a resource this world meters.
- **The effect is the SECOND penalty, not the first.** The real cost of leachate is that it is water
  you cannot use.

So *radiation damages you unless you wear a suit* is a **reversal of a recorded decision**, not a
default. Deferring the whole hazard to Mekanism keeps that ruling intact by construction rather than by
argument - and when it lands, the cost is positional and recedes as you work, which is much closer to
leachate's shape than a flat debuff would be.

---

## 8. Gating

**DECIDED (2026-08-22): Mill Tailings takes a sledgehammer; the Waste Drum takes a prybar.**

The drum half is free - the prybar already exists and Bulky Waste is opened with one. The tailings half
carries two consequences that were **corrected after the decision**, and both change what it buys.

### 8.1 It is a ladder gate, not a yard gate

The option this was chosen from said the yard becomes a prerequisite. **That is wrong.** The lowest
sledgehammer tier is `copper_sledgehammer` - one copper block and two sticks. Copper comes from Scrap
Metal in `household_pulls`; sticks come from the Tree Nursery. Both are available at home.

So a sledgehammer is gated by **the reclamation ladder**, which is exactly the gate the demolition yard
already has: *"the frontier is gated by the reclamation ladder itself - its iron needs a sledgehammer,
the sledgehammer needs sticks, sticks need trees."* The two frontier regions come out **parallel**, not
sequential.

That is still a real gate and a good one - it is strictly stronger than bare hands, and it means a
player finds the dump at 1024 while working the yard and cannot strip it until the ladder is finished.

**OPEN: copper tier or iron tier.** If sequencing is actually wanted, the lever is the **tier**, not the
tool. Iron comes only from Steel Offcut, which drops only from the yard's Steel I-Beam - so requiring
an **iron** sledgehammer would genuinely put the yard first. There is precedent for tier-gating with no
Java: Ancient Sculk uses `#recompile:mineable/sledgehammer` for the type plus
`#minecraft:needs_diamond_tool` for the tier, which works only because `RCItems.COPPER_TIER` is built
on `INCORRECT_FOR_STONE_TOOL`.

### 8.2 The mechanism cannot express "any sledgehammer" today

`SortableBlock.requiredTool()` returns a **single `Item`**, and there are **four** sledgehammers -
copper, iron, diamond, netherite. Naming one means the other three do not work.

`tags/item/sledgehammer.json` already exists, so the fix is small: teach the pull gate to take a tag
rather than an item, or add a tag-based sibling to `requiredTool()`. But it is **a change to a shared
base class that seven blocks extend**, not the data-only choice the prybar would have been, and the
tier question above rides on the same change.

Note the tier gate and the pull gate are different mechanisms: `#minecraft:needs_iron_tool` governs
**breaking** a block, while `requiredTool()` governs **picking through** it. Doing tiers on the pull
means expressing the tier in Java; doing it on the break is data-only but gates the wrong verb.

---

## 9. Decisions, closed

**Both remaining questions were settled on 2026-08-22 and V1 is implemented.**

1. **Copper tier**, not iron. The deciding argument is coherence with the onset: 1024 was chosen so a
   player finds the dump while working the yard and reaches Powah before the Nether, and an iron gate
   would push Powah *behind* the yard's full iron chain, fighting that. The regions stay parallel. The
   tier lever remains available if playtesting says it is too soft - it is a one-line change now that
   the family mechanism exists.
2. **Uranium glass is a collectible**, not a material. It is the only one of the four genuinely
   collected in real life, and as a material it would only have duplicated Mill Tailings' job. Shipped
   as a placeable light-emitting block - which is what the Puzzle Cube already is - so it needed no
   voxel port.

### What shipped

Three blocks, four finds, one biome at onset 1024, two pull streams, and one shared-code change:
`SortableBlock` gained `requiredToolFamily()`, because the gate named a single `Item` and there are
four sledgehammers. A variant with a family still declares a **representative** in `requiredTool()`,
because Jade draws an item and a family alone would render as "sort by hand".

Three tests, each mutation-tested: the tool family (reverting it fails on three of four tiers), the
`expand: true` guard (flipping it gives 341 items from 400 rolls), and the region's onset ordering.

**Verified with Powah installed** - `powah:uraninite_raw` drops from Mill Tailings, so the mod is
startable. Powah needs `cloth_config` and `guideme` alongside it to load at all.

---

## 10. Build notes

- **Textures are generated, never hand-drawn** - `texgen.toml` surfaces, and Jason running `select` is
  approval.
- **`RegionBiomeSourceTests` exists** and asserts the gradient, so a second frontier entry has a place
  to be pinned.
- **The pull gate change (8.2) needs its own test**, because it touches a base class seven blocks
  extend and a regression there is silent - a variant that stops requiring its tool just becomes
  bare-hand sortable.
- **This is engine content, not a cross-mod stopgap.** The biome and its blocks belong here
  permanently; only the Powah item ids are foreign and need the usual guard. Unlike #268/#269 there is
  no move-back issue, because the region is ours.

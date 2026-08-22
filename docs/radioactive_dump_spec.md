# Radioactive Dump - region spec

**Issue:** #285. **Status:** design, not implemented. **Owner decisions recorded here are dated.**

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

A second entry in the `frontier` list of `world_preset/garbage.json`. Today that list holds exactly one:

```json
{ "biome": "recompile:demolition_yard", "onset": 512 }
```

with `core_radius: 512`, `falloff: 256.0`, `household_floor: 0.2`, `noise_scale: 0.5`.

- **OPEN: the onset number.** The yard sits at the core radius; this is one step further out. The
  figure should be decided against how much of Powah is meant to be reachable before the Nether, not
  picked for roundness.
- **Placement is by distance gradient plus noise**, so there are **many** radioactive dumps in a world.
  That matters given section 3: each deposit is finite, the world is not.

**The generator is baked into `level.dat` at world creation**, so this only affects NEW worlds. Testing
needs a fresh one, and `runClient`'s quickPlay world is not it - quickPlay creates a DEFAULT world that
silently ignores the preset. Use `runServer` with `level-type=recompile\:garbage` and probe over RCON.

---

## 2. The biome

- **Surface: unchanged coarse dirt**, as the yard is. Identity comes from what is scattered on top,
  never from re-surfacing. The reclamation ladder and encroachment both ride on the coarse-dirt surface
  rule, and re-surfacing would silently break the Grass Spreader.
- **OPEN: hostile spawns.** The yard turned them on as a scoped introduction of the threat axis.
  Repeating that is free; not repeating it needs a reason.
- **OPEN: encroachment.** The yard is in `#recompile:encroaches`, and the reasoning for putting it
  there is worth re-reading before deciding: the original exception was reversed in 2026-07-31 because
  **the asymmetry was undiscoverable** - a player who learns "grass reverts unless you anchor it" in
  the sprawl carries that rule here, watches it not happen, and reads the design as a bug. One rule
  everywhere beat the tuning the exception bought. The same argument applies to this region.

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
one-time build cost, so a player will exhaust a deposit and have to travel to the next one. That is
what uranium extraction actually is, and the noisy gradient means there is always a next one.

**A note for V2**, not a commitment: once Mekanism is in, the renewable radioactive material is the
**nuclear waste the player produces themselves**. You clear their dump, then you make your own. That
resolves the finiteness question without ever making waste replenish itself.

---

## 4. Blocks

Every scatter block here should be a `SortableBlock`, which is the mod's pick-through loop and already
has seven variants. A new one supplies five things: a `sorted` property, a pull table, a min/max
crumble window, and a required tool. **No new mechanic is needed for V1.**

The block is doing three jobs at once, and that is what makes the design tight: it **holds the loot**,
it **will emit the dose** (section 6), and **clearing it removes both**.

**Bulk - the biome's Garbage Block equivalent:**

- **Mill Tailings.** Sandy uranium-processing waste, genuinely left in open heaps for decades (Moab,
  Church Rock). Gravity-affected like every `SortableBlock`. This is the ground cover and the main
  uraninite stream.

**Containers - the punctuation:**

- **Waste Drum.** The 55-gallon steel drum, yellow with the trefoil. Low-level waste really is drummed.
- **OPEN: a shielded container** - a lead pig or cask, as the rare one. Depends on whether lead exists
  here, which today it does not; Mekanism brings it.

**OPEN: how many block types V1 ships.** The yard has four features and three block families and cost
an entire phase. This should be smaller, and deciding *how much* smaller up front is what stops it
sprawling.

---

## 5. Finds

**The requirement is `powah:uraninite`**, guarded with `neoforge:mod_loaded` like every other foreign
id. Sixteen Powah recipes consume it and the whole energy tier is downstream; the reachability closure
reaches 126 of 133 Powah items once the root exists.

**Beyond that, the consumer-scale objects are the ones that matter most**, and they are the reason this
region is not a science-fiction set piece:

- **Radium dial clock** - watch and clock faces painted with radium, and the Radium Girls behind them.
- **Thoriated welding rods** - still sold, mildly radioactive, thrown away constantly.
- **Americium smoke detector** - a genuine sealed source in a domestic object.
- **Uranium glass** - collectible in real life, glows under UV. A natural fit for the collectibles
  system, which exists for exactly this kind of object.

They pass *would a person throw this away* outright, they are household things in a household world,
and they tie the region back to the sprawl rather than making it a separate place.

**Orphan sources** - radiography cameras, teletherapy heads - are the dangerous version and the objects
behind Goiânia and Ciudad Juárez, both of which were **scrapyards that melted a source into the metal
supply**. Worth having somewhere, but they land better once radiation is real (section 6).

---

## 6. V2: radiation, via Mekanism (owner, 2026-08-21)

**V1 ships no radiation at all.** Mekanism ships a complete radiation system - dose accumulation,
Geiger counter, hazmat suit, poisoning - and building a parallel one now is work that gets deleted.

**The design, when it lands: the blocks are the sources.** Not a biome-wide debuff.

```java
IRadiationManager.INSTANCE.radiate(Level, BlockPos, double);   // a drum, a tailings pile
IRadiationManager.INSTANCE.radiate(LivingEntity, double);      // direct dose
```

This is what Mekanism's model is actually built for: `getRadiationSources()` is a table keyed by chunk
and position, and sources are things placed in the world. **There is no data-driven or biome-wide
radiation** - no biome tag, nothing a datapack can point at a region - so this needs our Java either
way. Confirmed against `Mekanism-1.21.1-10.7.19.85`.

Why block-as-source is the better shape:

- The hazard **concentrates around the waste and falls off with distance**, which a biome-wide sweep
  cannot express.
- It **recedes as you clear the dump**, which is the correct feeling for a mod about clearing dumps.
- A **per-block dose is free** - a drum is hotter than tailings because it is a different `double`.
- `IRadiationShielding` is a capability, so Mekanism's own hazmat suit works with no knowledge of ours.

**Three things V1 must therefore NOT build**, because Mekanism supersedes each:

1. **No Geiger counter.** Mekanism's reads *its* radiation, not ours.
2. **No shielding or armour.** This mod has no armour system and should not gain one for this.
3. **No damage mechanic.** See section 7.

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
argument - and when it does land, the cost is positional and recedes as you work, which is much closer
to leachate's shape than a flat debuff would be.

---

## 8. What V1 is gated by, stated plainly

With no hazard until Mekanism, the gate is what the yard's is: **travel past the onset, plus a tool.**
Nothing else stops a player stripping a deposit on arrival.

That is acceptable but should be deliberate: **V1 is the easier trip**, and radiation arriving later
*raises the cost of a place the player already knows*. That is a fine progression beat. What it means
for V1 is that **the finds and the scatter are carrying the region's whole identity** - there is no
danger to lean on - so they have to be interesting on their own.

**OPEN: the tool gate.** The prybar already exists and opening a drum with one is natural.

---

## 9. Open decisions, collected

1. The onset distance (section 1).
2. Hostile spawns yes or no (section 2).
3. Encroachment membership (section 2).
4. How many block types V1 ships (section 4).
5. Which consumer-scale finds make V1, and whether uranium glass is a collectible (section 5).
6. The tool gate (section 8).
7. Whether a shielded container waits for lead (section 4).

---

## 10. Build notes

- **Textures are generated, never hand-drawn** - `texgen.toml` surfaces, and Jason running `select` is
  approval.
- **`RegionBiomeSourceTests` exists** and asserts the gradient, so a second frontier entry has a place
  to be pinned.
- **This is engine content, not a cross-mod stopgap.** The biome and its blocks belong here
  permanently; only the Powah item ids are foreign and need the usual guard. Unlike #268/#269 there is
  no move-back issue, because the region is ours.

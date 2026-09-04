# Tire piles - spec

**Status: DRAFT, unbuilt.** Owner design 2026-09-04, against issue #155 (`enhancement`, `blocked`),
which is itself I-4 in `../trashlands/docs/ideas.md` scoped to work. The owner's design revises several
of #155's decisions; section 7 lists every delta rather than quietly overwriting them.

Sibling specs: [`municipal_aquarium_spec.md`](municipal_aquarium_spec.md),
[`demolition_yard_spec.md`](demolition_yard_spec.md). Worldgen precedent: `MoundFeature`.

---

## 0. The concept in one paragraph

**A heap of tires, circular in plan, burning in places and never going out.** Individual tires are
slab-shaped and stack the way tires actually stack, so a pile is a low circular mound of rings rather
than a new silhouette. Some of them are alight when the world generates, and that fire is permanent:
a tire fire is the most famous thing about a tire dump precisely because nobody can put it out. Piles
are household-sprawl only, they do not touch mounds, and nothing regrows them.

---

## 1. What it is for

**Rubber has been an orphan since P2.2.** `material_economy.md` has listed it as an intermediate in the
same sentence as scrap, cullet, muck and plastic sheet since the beginning, and unlike all four it has
never had an origin. A grep over this repo confirms it: **the string "rubber" appears nowhere** in the
sources, the data or the lang file today. Tires are the obvious home, and giving an orphaned material a
real source is the strongest argument here.

The second argument is that a stack of tires reads instantly at 16px, the way the Compacted Bale does.

---

## 2. Placement

**Household sprawl only** (owner, 2026-09-04). Not the demolition yard, which #155 proposed.

It joins the biome's `vegetal_decoration` array (step 9), which today holds `garbage_mound`,
`mycelium_patch` and `leachate_pool`, and **it must be ordered after `garbage_mound`**. That ordering
is the whole avoidance mechanism, and it is worth being explicit about why it is enough:

- **A feature can read blocks at its own position safely.** Mound Ground and the mound's own garbage
  are ordinary blocks in the chunk the feature is decorating, so a tire feature that runs later simply
  looks and declines. It needs none of the structure-manager machinery the aquarium uses.
- **That machinery is exactly what crashed world generation in #349**, because a structure lookup reads
  a chunk at `STRUCTURE_REFERENCES` and the allowed status degrades with distance. A block read at the
  feature's own origin has no such problem. **Do not reach for `StructureManager` here.**

**Three placement rules, all owner-stated:**

1. **No pile intersects a mound.** Decline the whole pile if any cell of its footprint holds a
   `SortableBlock`, a `BulkyWasteBlock` or `MoundGroundBlock`.
2. **No Mound Ground is written under a pile.** Tires are not garbage and the ground does not remember
   them.
3. **Piles do not replenish.** What you take is gone. Section 8.4 is the ruling that follows from this.

**Rule 1 is load-bearing beyond tidiness**, and #155 spotted why: `MoundGroundBlock.isMound` counts
`SortableBlock` and `BulkyWasteBlock` when it measures a column, so anything of that kind standing on
Mound Ground is read as part of the mound and regrown as garbage. A tire pile on a mound footprint
would be slowly eaten and replaced. Keeping the two apart avoids the problem rather than narrowing the
check, which is the cheaper of the two fixes and the one that cannot regress.

---

## 3. The tire

**Slab-shaped, circular in model, stacking.** A tire is a single block occupying half a block's height,
so two make a metre and a pile is a column of them. `PRESSED_JUNK_SLAB` is the mod's existing slab, so
the shape is not new here.

- **`noOcclusion()` is mandatory.** A non-cube model on a block without it punches a hole in the world:
  the game still culls the neighbour's face and you see straight through the ground. This is one of the
  two traps CLAUDE.md warns about by name, and a tire is emphatically not a full cube.
- **The model is a ring, not a cylinder.** A torus at 16px reads as a tire from above and in hand; a
  solid disc reads as a hockey puck. The hole in the middle is the whole silhouette.
- **They do not fall.** Gravity belongs to the garbage blocks; a stack of tires is stable, and a falling
  tire would be a different mechanic asking to be explained.
- **No `sorted` progress and no crumble window.** See section 7 for why this departs from #155.

---

## 4. The fire

**Some tires generate alight, and they never go out.**

**Recommendation: a `LIT` blockstate on the tire itself, and no vanilla fire block anywhere.** This is
the single most consequential build decision in the spec and it is worth arguing rather than assuming:

- **Vanilla fire spreads**, and the P2 pressure-loop rule is explicit that a hazard must never threaten
  builds or cleared land. A real fire block on a tire dump next to a player's base is precisely that,
  and containing it would mean overriding flammability on every neighbour rather than on the tire.
- **"Never goes out" is free with a blockstate** and costs a tag plus a fire block without one. There is
  nothing to burn out because there is no fire entity to tick.
- **It is the mod's own idiom.** `sorted` on `SortableBlock` and `HEIGHT` on `MoundGroundBlock` are both
  palette flyweights carrying state on a bulk block for exactly this reason, and a dump full of tires is
  a bulk block.
- **It is deterministic at generation**, so which tires are alight does not depend on fire ticking
  having run, which worldgen cannot wait for.

**The alternative, recorded so nobody has to rediscover its cost.** Vanilla's eternal fire is
`#minecraft:infiniburn_overworld`, which holds netherrack and magma block and which this mod does not
use at all today. Putting the tire in it and placing a real fire block on top would give genuine vanilla
behaviour, at the price of spread and of a fire block per lit tire. Not recommended.

**What a lit tire does:** emits light, emits smoke, and is hot to stand in. What it must not do is
spread, consume its neighbours, or reach anything the player built.

---

## 5. Harvest, and the rubber chain

**Break a tire, get a tire. Shred a tire, get rubber.**

The processing step should be the **Pulverizer**, and it needs no argument beyond the machine's own:
its verb is *it reduces*, size reduction is the only thing that happens to it, and shredding scrap tires
into crumb rubber is a real industry with exactly that shape. This is the six-verb table working as
designed rather than a new machine.

```
tire (block, broken)  ->  Pulverizer  ->  rubber_scrap
```

`rubber_scrap` names consistently with `scrap_metal`, `plastic_scrap` and `fiber_scrap`.

**A fuel entry** in `data/neoforge/data_maps/item/furnace_fuels.json`. Tire-derived fuel is a real
recycling stream and tires burn hot and long. The current scale is `junk` 400, `lignite` 800,
`oily_rag` 1600; #155 proposed "above junk, below oily rag", but 800 is taken, so **1200** is the
free slot that still reads as hotter than lignite and cooler than a solvent-soaked rag.

**The steel belts.** A real tire is steel-reinforced, so an occasional `scrap_metal` alongside the
rubber is free flavour that happens to be true.

---

## 6. What this does to the checklist

Nothing directly. Every item involved is this mod's own, so `tools/resource_checklist` will not move,
and that is correct rather than a gap.

**Worth doing anyway: a `#c:rubber` item tag.** Immersive Engineering, Mekanism and Create all consume
rubber under common tags, and joining one costs nothing and no dependency. It is the same argument the
power tier already makes for Forge Energy: speak the standard and interoperate for free.

---

## 7. Deltas from #155, which are reversals rather than refinements

#155 is a good issue and four of its decisions are now overturned. Recording them here so the issue and
the spec do not quietly disagree.

| #155 said | This spec says | Why |
|---|---|---|
| A `tire_pile` block extending `SortableBlock`, Scrap Knife as `sortTool`, "mirroring `CompactedBaleBlock` exactly" | Individual slab-shaped tires you break | The owner's design is a stack of tires, not a bale. A pull stream and a crumble window describe one object you pick apart; a pile of stacking slabs is many objects. |
| Piles in household sprawl **and the demolition yard** | Household sprawl only | Owner, 2026-09-04. |
| Tire fire "probably cut for v1" | Fire is in v1, and eternal | Owner, 2026-09-04. It is the most recognisable thing about a tire dump. |
| "Either keep piles off mound footprints **or** make the check narrower" | Keep them off, and do not touch the check | The narrower check is a change to Phase 5's regrowth for the benefit of a feature that can simply stand elsewhere. |

**One #155 point that stands unchanged and is the most important line in it:** rubber needs a use that
does not require Create. Without a mod-side consumer, `rubber_scrap` is a dead end in every install
without that mod, which is worse than not shipping it. See 8.1.

---

## 8. Open questions

### 8.1 What consumes rubber, standalone? (load-bearing)

The one thing #155 says must not be skipped, and still open.

- **The Pump** is #155's candidate and still the best one: it already gates the Rain Collector and the
  Hydroponics Bay, and a pump without a seal is the most obvious rubber part in the building. Its
  recipe today is `blueprint_crafting`, ` C ` / `CMC` / ` P `, copper plus scrap metal plus plastic
  scrap. **The cost is that this edits a shipped recipe**, which every existing world's players have
  learned, so it wants a deliberate ruling rather than a quiet substitution.
- **A new component** (a gasket, a seal) that the Pump then needs is the same change with an extra
  item and an extra step.
- **The Garbage Vacuum is a tempting third option and should probably be resisted.** It shipped in
  v0.17.0 with `copper_pipe` in the hose slot, so retrofitting rubber there rewrites a recipe from the
  most recent release.

Recommendation: the Pump, with the recipe change made explicitly and noted in the changelog.

### 8.2 Does fire destroy the rubber, and can a player put it out?

The answer decides whether fire is scenery or a mechanic.

**Recommendation: a lit tire yields nothing, and water puts it out.** That turns a burning pile into a
cost rather than a decoration: the rubber is there, you can see it, and taking it means spending water
from a Rain Collector or the sewers. It ties tires to the P1.10 water economy the way the fridge ice
ruling ties ice to it, and it gives the eternal fire a reason to exist beyond looking right.

The alternative, that lit tires burn away over time, is worse: it makes the resource decay on a timer
the player cannot see and did not cause.

### 8.3 Do tires also appear in the pull streams?

#155's own open question, unchanged. A tire in `household_pulls` would make rubber available before a
player finds a pile, at the cost of making the piles less of a destination. Leaning no: the pile is the
point, and a find that duplicates a landmark weakens it.

### 8.4 Rubber is finite. Is that acceptable?

Piles do not replenish (owner), and mounds do. So rubber is the first material in the game whose supply
is bounded by how much of the world a player has explored.

**This is the same question the amethyst issue (#332) resolved and should get the same answer**:
whether a stream runs out belongs to the balance pass (#36), answered once for the stream rather than
once per material. Worth stating in the guidebook so a player who strips their local piles knows to
travel rather than assuming the material is gone.

### 8.5 What does a burning pile do to a player standing in it?

`RCLeachateContact` is the precedent for a contact effect and it does real damage. The P2 rule
constrains threats to builds and cleared land and says nothing about the player, so damage is available.

Leaning: fire damage only while inside a lit tire's own block, no smoke inhalation effect, no lingering
debuff. A hazard the player can see and walk around is fair; an area effect around a dump is a mechanic
that wants its own design.

### 8.6 Shape of a pile

Circular in plan is decided. Open: whether the height falls off toward the rim the way `MoundFeature`
does (`height * (1 - dist/radius)`), or whether tires stack to a flat top like a real dumped heap. The
falloff reads more natural at distance and reuses arithmetic that already exists.

Also open: how many piles per chunk and at what spacing. Mounds are 5% and playtested; tires should be
rarer, because a landmark you travel to beats scenery you walk past.

### 8.7 Smoke at dump scale

A permanent particle column per lit tire is a performance question when a field of piles is in view.
Leaning: particles on the top tire of a lit stack only, not on every lit tire in a column.

---

## 9. Build notes

- **The feature goes after `garbage_mound` in the biome's step 9 array**, and reads blocks at its own
  position to decline. No `StructureManager`, for the reason in section 2.
- **`noOcclusion()`** on the tire block, or the world gets a hole. This has bitten twice already.
- **A JUnit test for the pile's shape** before any world test, the way `CoolingTowerProfileTest` works:
  the circle and the height falloff are pure arithmetic and want measuring without booting anything.
- **A GameTest that a pile never stands on Mound Ground or in a mound**, which is rule 1 and is the one
  that silently corrupts Phase 5 regrowth if it regresses.
- **A GameTest that a lit tire stays lit**, since "never goes out" is the claim a player would notice
  breaking and nothing else would.
- Textures are generated, never hand-drawn: a `tire` surface in `texgen.toml`, and a lit variant if the
  model needs one.

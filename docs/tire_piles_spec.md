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
4. **They arrive as a DUMP, not as a tire** (owner, 2026-09-04). One roll of the feature places a
   cluster of piles rather than a single stack, and the roll itself is well below the mound's 5%.
   A real tire dump is many piles in one place, and a lone stack on a hillside reads as clutter
   rather than as somewhere. Proposed: three to six piles inside a radius, so the site has a shape
   and an edge; the exact numbers are a playtest dial and the mound's 5% is explicitly not to be
   retuned to match.

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
- **And no `LIT` state either.** Ruling 4 puts a real fire block above a burning tire, so the tire
  carries no fire state of its own and needs no second model.

---

## 4. The fire

**DECIDED, and nonnegotiable (owner, 2026-09-04): a tire behaves with fire exactly as netherrack does.**
Some tires generate with a real fire burning on them, and that fire never goes out.

**The mechanism is `isFireSource`, not the tag.** NeoForge routes the eternal-fire question through the
block: `FireBlock.tick` asks `belowState.isFireSource(level, pos.below(), Direction.UP)`, whose default
implementation consults the dimension's infiniburn tag. A mod block can answer directly, which is the
better of the two routes here:

- **Override `isFireSource` on the tire** to return true. Block-scoped, needs no vanilla-namespace tag
  file, and cannot be broken by a datapack editing `#minecraft:infiniburn_overworld` for other reasons.
- Joining `#minecraft:infiniburn_overworld` (netherrack and magma block today; this mod does not touch
  it) reaches the same place with a file the mod does not otherwise own.

**Four consequences, all of them netherrack's, and all intended:**

1. **It never self-extinguishes, including in rain.** `FireBlock.tick` skips its rain-extinguish branch
   when the block below is a fire source. Worth stating because this world rains and the Rain Collector
   depends on it: a downpour will not clear a tire dump.
2. **The fire does not consume the tire.** Netherrack is absent from the flammable registrations and the
   tire must be too, or a pile slowly eats itself and the resource decays on a timer the player cannot
   see. The rubber is still there under the flames.
3. **A player can still put it out**, with water or by breaking the fire block. That is netherrack as
   well: eternal means it does not go out on its own, not that it cannot be dealt with.
4. **Fire spreads by ordinary vanilla rules**, which is the half worth reading carefully. See below.

**On spread, which is the concern this ruling overrides, and which is smaller here than in a vanilla
world.** Measured rather than assumed:

- The sprawl's surface is **coarse dirt**, which is not flammable.
- **This mod registers no flammable blocks at all** (no `setFlammable`, no `getFlammability` override
  anywhere in the sources), so weedgrass, fireweed and the rest cannot catch either.
- So a pile standing in unreclaimed sprawl has **nothing to spread to** and is self-contained by the
  terrain rather than by a rule.

What can burn is what the **player** brings: reclaimed grass, a grown tree, a wooden build. That is a
proximity the player chooses, and "do not build against a burning tire dump" is a fair thing for a game
to expect. It does mean the P2 pressure-loop rule is satisfied by circumstance rather than by design
here, which is worth knowing if the sprawl ever gains a flammable block.

**A `LIT` blockstate with no fire block was considered and rejected** (owner). It would have made spread
impossible by construction, at the cost of the fire not being real: no water interaction, no
extinguishing, no light and smoke from vanilla's own fire, and a hazard that behaves like nothing else
in the game. Recorded because this repo records reversals, not to reopen it.

**What a lit tire does:** carries a vanilla fire block above it, which lights and smokes on its own. The
tire itself needs no `LIT` state and no extra model.

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

### 5.1 What spends it: the Pump

**DECIDED (owner, 2026-09-04).** A pump without a seal is the most obvious rubber part in the building,
and the Pump already gates the Rain Collector and the Hydroponics Bay, so rubber lands upstream of the
entire water tier rather than in a cul-de-sac. That answers the one thing #155 said must not be skipped.

The Pump ships today as a `recompile:blueprint_crafting` recipe:

```
 C        C = minecraft:copper_ingot
CMC       M = recompile:scrap_metal
 P        P = recompile:plastic_scrap
```

**Proposed change: the bottom cell becomes `recompile:rubber_scrap`.** One key, same pattern, same
shape on the bench, and it reads correct - a pump's diaphragm and seals are rubber, and plastic never
was the part doing that job.

**Two things this costs, both to be handled rather than discovered:**

- **It edits a shipped recipe.** Players who already hold the Pump blueprint will find it wants a
  different ingredient. That needs a changelog line in the player's voice, not a silent substitution.
- **It removes a `plastic_scrap` sink, and that was checked rather than left as a worry.** Four recipes
  consume plastic scrap today: the Cutting Torch, the Plastic Panel, the Rain Collector Funnel and the
  Pump. Taking the Pump leaves three, and the Panel is an open-ended building-block sink, so plastic is
  not devalued by the swap. Worth re-checking if that ever drops to one.

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

### 8.1 What consumes rubber, standalone? DECIDED: the Pump

Owner, 2026-09-04. Section 5.1 has the recipe and the two costs. The gasket-component variant was
declined as two new items for one material, and the Garbage Vacuum was never a candidate worth taking:
it shipped in v0.17.0 with `copper_pipe` in its hose slot, and retrofitting rubber there would rewrite a
recipe from the current release.

### 8.2 Does a burning tire still yield its rubber?

**Mostly answered by ruling 4.** Netherrack semantics settle the two halves that mattered: the fire does
not consume the tire, so the rubber survives, and a player can put the fire out with water, so a burning
pile is workable rather than lost.

**DECIDED (owner, 2026-09-04): the same yield, burning or not.** It follows directly from ruling 4 -
the fire does not damage the tire, so it does not damage what the tire gives - and it is the simplest
thing to build and to explain.

**One consequence to be clear about rather than to discover.** Putting a fire out now has no economic
reason. A player will mostly harvest straight through the flames and eat the fire damage, because that
is cheaper than a bucket. Extinguishing survives as a choice about comfort and safety rather than about
yield: stop burning while you work, and stop the fire reaching anything of yours.

That makes the eternal fire a hazard and a light source rather than a cost, which is a coherent thing
for it to be. It does mean tires are NOT tied to the P1.10 water economy, which the rejected
reduced-yield option would have done.

### 8.3 Do tires also appear in the pull streams? DECIDED: no

Owner, 2026-09-04. Piles only. #155's own open question, closed the way it leaned: the pile is the
point, a find that duplicates a landmark weakens it, and rubber stays behind travel the way the mod
gates its other materials by region.

**Note what this makes true.** The Pump is now gated on finding a tire dump, and the Pump gates the Rain
Collector and the Hydroponics Bay. Rubber is not a side material any more; it is on the critical path
to the water tier, and 8.4's finiteness question inherits that weight.

### 8.4 Rubber is finite. Is that acceptable?

Piles do not replenish (owner), and mounds do. So rubber is the first material in the game whose supply
is bounded by how much of the world a player has explored.

**This is the same question the amethyst issue (#332) resolved and should get the same answer**:
whether a stream runs out belongs to the balance pass (#36), answered once for the stream rather than
once per material. Worth stating in the guidebook so a player who strips their local piles knows to
travel rather than assuming the material is gone.

### 8.5 Does a burning pile need anything beyond the fire itself?

Ruling 4 gives the player vanilla fire damage for free, which is already a hazard they can see and walk
around. So the open question is only whether anything is added on top.

`RCLeachateContact` is the precedent for a contact effect and it does real damage, so a smoke effect
around a burning pile is available. Leaning against: vanilla fire is legible and an invisible area
effect around a dump is a mechanic that wants its own design and its own config gate.

### 8.6 Shape of a pile

**Density is decided** (owner, 2026-09-04): a dump, clustered, rare. See placement rule 4.

**Still open, and small:** whether a single pile's height falls off toward the rim the way
`MoundFeature` does (`height * (1 - dist/radius)`), or whether tires stack to a flat top like a heap
somebody actually tipped. The falloff reads more natural at distance and reuses arithmetic that already
exists; the flat top reads more like human dumping. Either is a few lines, and it is the kind of thing
to settle by looking at one in the world rather than by argument.

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
- **A GameTest that fire on a tire survives what puts ordinary fire out**, since "never goes out" is the
  claim a player would notice breaking and nothing else would. Rain is the case worth asserting: this
  world rains, and `FireBlock` only skips its rain-extinguish branch when the block below answers
  `isFireSource`. A test that only waits for the fire tick would pass on a broken implementation.
- **A GameTest that fire does not consume the tire**, which is the other half of netherrack and the one
  that would turn every pile into a slow leak of the only rubber in the game.
- Textures are generated, never hand-drawn: a `tire` surface in `texgen.toml`, and a lit variant if the
  model needs one.

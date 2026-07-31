# Animals - implementation spec (reclamation rung 5)

**Written 2026-07-27.** The final reclamation tier and the Mod Jam **climax** (the trailer's closing
shot): life returns to the healed world. Design source: `../trashlands/docs/design_decisions.md`
(**P2.4-R** the economy revision, **P1.9** the creature-free start). This spec is **not built yet**.

## What it is

The starting biome is **creature-free** - `household_sprawl` has every spawner category empty, so
nothing spawns naturally. So an animal existing in this world is, by definition, something the player
brought back. The mechanism is **bait**: you place it on reclaimed grass, walk away, and wildlife
settles onto the quiet, livable land while you are not watching. The bait is consumed; the animal is
yours; **vanilla breeding takes over** from there. Baits only *seed* the population.

**This is not a machine** (no GUI, no BlockEntity for a screen) - it is the one rung that is an item +
a placed block, which is the right shape for "wildlife wanders in" and keeps the mod's one-custom-screen
budget intact (spent on the Tree Nursery).

## The three baits

Three bait types, each carrying a **diet allowlist as an entity-type tag** so packs can retune or
extend without a mod release:

| Bait | Tag | Spawns (one of) |
|---|---|---|
| **Herbivore** | `#recompile:bait/herbivore` | Cow, Mooshroom, Sheep, Rabbit, Horse, Donkey, Mule, Camel, Sniffer |
| **Carnivore** | `#recompile:bait/carnivore` | Cat, Ocelot, Fox, Armadillo |
| **Omnivore** | `#recompile:bait/omnivore` | Chicken, Pig, Parrot |

**Excluded from all lists** (Jason, 2026-07-27): every hostile and neutral mob, **Bee**, **Wandering
Trader**, **Villager**, and everything aquatic. Also dropped as non-animals: Allay and Bat (ambient,
eat nothing) and Skeleton Horse (undead, trap-only). Goat is out (treated as neutral - it rams); Fox
sits in Carnivore (a hunter) though it eats berries too; Parrot in Omnivore. These four are the tunable
judgment calls and live in the tags, not code.

## The core loop

Place a bait on **grass**, and on a self-scheduled tick it checks, in order:

1. **Still on grass?** If the block beneath is not grass, it is inert (no settling).
2. **Any player within `baitPlayerRadius`?** If so, **settling resets to 0** - wildlife will not come
   while watched. (Mechanic 4, the undisturbed timer.)
3. **Another bait within `baitSpacing`?** If so, inert - baits do not stack up a spot.
4. Otherwise **settle**: advance a coarse `settle` progress. When it reaches full, **fire**.

On firing: pick a mob (below), spawn it on the bait's block via vanilla `finalizeSpawn` (so it gets the
biome-appropriate variant for free - snow fox, ground-matched rabbit), play the feedback, and **destroy
the bait**.

### 1 - Environment-weighted pick

The spawn is **not** a flat roll over the tag. The bait scans its surroundings (the same local
block-scan the Grass Spreader and encroachment already do) and **weights** the allowlist by what the
land actually is, so the animal reflects the ground you built:

- open **grass** area -> grazers (cow, sheep, horse) weighted up
- **sand / badlands** nearby -> rabbit, camel weighted up
- **trees / leaves** overhead -> fox (carnivore), parrot (omnivore) weighted up
- **water** in range -> a small general bonus (livable land)

Weights are a data table (blocks -> per-mob nudges), tunable. A bare grass tile still yields the
grazers; a richer patch yields more variety. No hard success gate (mechanic 2 was **not** taken) - the
grass + undisturbed + spacing checks are the whole gate; the environment only decides *which*.

### 3 - Bait is crafted from the biological economy

Each bait is made from the tier that precedes it, so an animal is paid for by farmed food:

- **Herbivore** - crops / hay / seeds (grazer feed)
- **Carnivore** - meat / eggs (predator feed)
- **Omnivore** - a mix of both

Exact recipes settle at the balance pass; the constraint is that bait comes out of the **farming +
early-animal** economy, closing the ladder loop (grass -> veg -> trees -> farm -> bait -> herds).

### 6 - Herd-seeding (a richer grade)

A **Rich** grade of each bait (a costlier recipe) seeds a **bonded pair** - a mother + baby, or two
adults - instead of a lone adult, so the player is not stranded with one un-breedable animal. Basic
bait = one; Rich bait = a pair. (Open: 3 baits + 3 rich = 6 items, vs a single data-driven "rich" flag.)

### 10 - Feedback (so the mechanic is learnable)

- While **settling**: faint idle particles + the `settle` blockstate driving a subtle model change, so
  a waiting bait reads as *working*, not placed-and-forgotten.
- On **player-too-near reset**: a small deterrent puff, so "it stopped because you are here" is visible.
- On **fire**: a sound + a spawn puff as the animal appears.

## Surfacing - the roadblocks must be visible (first-class requirement)

Every gate above is an invisible failure mode ("I placed it and nothing happened"). It **must** read
without guesswork, in two places:

**Item tooltip** (quest-voice, teach the deviation, no flavor):
> Place on grass and step away - wildlife will not come while you are near. Keep baits apart. Attracts
> grazing animals. [per diet]

**Jade on the placed bait** - a status line naming the exact current blocker, plus what the land will
bring:

- `No grass under it` - invalid placement
- `Too close to another bait`
- `Waiting - step away` (a player is in range; settling is held/reset)
- `Settling - N s` (counting down, undisturbed)
- `Ready` (about to fire)
- `Expecting: <weighted shortlist>` - the top environment-weighted candidates, so the player sees the
  land is drawing (say) rabbits, not cows

This mirrors `MachineStatusProvider`'s "name the one thing the player can act on" principle. A Jade
**data provider** sends the settle ticks + the environment read; a **client component** renders the
line. Same two-class split as the Compost Heap / Tree Nursery.

## Architecture

- **No BlockEntity.** The only state is the settle progress, which is a coarse `settle` IntegerProperty
  (blockstate) - the same flyweight the `SortableBlock` `sorted` property uses. A self-rescheduling
  block tick (the Grass Spreader pattern) advances or resets it; nothing serialises beyond the state.
- **Diet:** one `animal_bait` block with a `diet` EnumProperty (herbivore/carnivore/omnivore), placed by
  three (or six, with Rich) items - or three blocks. Lean: one block + a `diet` property, so one ticker
  and one Jade provider cover all baits; the item carries the diet.
- **Spawn** through `finalizeSpawn` for variants; herd-seeding spawns 1-2 with the baby flag set.
- **Tags** own the allowlists (`#recompile:bait/*`) and the environment weights are a datapack table -
  no Java edit to retune. **Shipped as a NeoForge data map** (#45): `recompile:bait_weight` over the
  entity-type registry, values in `data/recompile/data_maps/entity_type/bait_weight.json`, one entry per
  mob carrying `weight` and an optional `terrain` affinity. Both fields are optional and a mob with no
  entry rides `DEFAULT_WEIGHT` unaffiliated, so a pack makes a mob reachable with a diet tag alone and
  tunes it only if it wants to. `SpawnPlacements`/`checkSpawnRules` validate the mob can legally stand there.
- **No mixins**, consistent with the rest of the mod; the tick + `finalizeSpawn` are the mechanism.

## Config (`RCConfig`, `reclamation`)

- `animalBaitEnabled`
- `baitSettleTicks` - undisturbed time before firing (minutes, treasure-grade like the nursery)
- `baitPlayerRadius` - how near a player holds/resets settling
- `baitSpacing` - minimum distance between working baits
- All first-pass; balance pass (#36) tunes them. The *undisturbed + slow* intent is design, not placeholder.

## Data surface

- Three bait items (+ Rich grade) with recipes; the `animal_bait` block (blockstate `diet` + `settle`,
  models, loot = drops itself if broken before firing, lang).
- Entity-type tags `bait/herbivore|carnivore|omnivore`; the `recompile:bait_weight` data map.
- texgen surfaces: the three bait items + the placed bait block (a small baited lure on the ground).
- Jade lang for the status lines.

## Tests (GameTest) - `gametest/AnimalBaitTests.java`

Driven through a static entry point (`settleOnce` / `tryFire`), per the `sortOnce` convention:

1. On grass, undisturbed, past `settleTicks` -> spawns exactly one mob from the diet tag; bait removed.
2. A player within `baitPlayerRadius` -> settling resets, nothing spawns.
3. A second bait within `baitSpacing` -> inert.
4. Not on grass -> inert.
5. Herbivore bait only ever spawns a `#bait/herbivore` mob (tag-respecting); same for carn/omni.
6. Environment weighting: with sand around, rabbit/camel out-weight cow (statistical over N rolls).
7. Rich bait spawns two (one flagged baby).
8. Never spawns an excluded mob (hostile/neutral/bee/villager) even if a pack mis-tags - the spawn is
   tag-gated, so this asserts the tag is the only source.

## Fiction (spoiler-safe)

Wildlife survives beyond the dump and returns to land that can support it - the healed patch is the
signal. Never restate `the_twist.md`; the animals are the visible half of "the world was alive once."

## Out of scope (parked)

Reclamation-scaled success chance (#2), emergent predator/prey (#7, foxes hunting chickens), first-of-
each advancements (#8, pack-side quest hook instead), diminishing-returns-per-area (#9), walk-in
pathfinding, and any genetics system.

## Verification

1. `JAVA_HOME=... ./gradlew build` (redirect, check `$?`) + `runGameTestServer` (total is ours +1).
2. `runClient`: place each bait on healed grass, walk away, confirm the settle particles, the
   player-near reset, the spacing block, and a spawn; read the Jade line at each stage.
3. Code review before merge.

# The Scrap Hauler - automated gathering, and this mod's quarry

**Status: SPECCED 2026-09-05.** Issue #376. Owner rulings are numbered and dated in section 3;
everything else is derivation and is arguable. Nothing is built.

**The one-line version:** a **Hauler Depot** block holds a **Scrap Hauler** item, deploys it as an
entity that gathers whole garbage blocks from a radius around the Depot, and receives what it brings
back. It is solar-powered, indestructible, and it wakes itself when the mounds regrow.

## 1. Why it exists

**This is the slot a BuildCraft Quarry, an IE Excavator, a Digital Miner or a laser drill fills in
other packs**: the machine that gathers raw material out of the world without you standing there.

**The mod already has two thirds of that pattern.** A quarry is gather, process, distribute:

| Stage | Status |
|---|---|
| **Gather** blocks out of the world | **MISSING. This spec.** Manual only: break them, or hold a Garbage Vacuum |
| **Process** them into materials | **Shipped.** `TrommelBlockEntity` takes any item with `SortableBlock.sortRolls > 0`, one per 40 ticks, powered, no GUI |
| **Distribute** the output | **Shipped.** `ScrapNetwork.insertFromMember` routes to a bound Scrap Bin, then the Scrap Barrel |

A Trommel is fed by **items in its mouth or a container parked on it**, so a Depot that pushes blocks
needs no new interface. **The chain closes the moment something puts blocks in front of it.**

**The pick-through loop is not what this automates.** The Trommel has automated the sorting end since
#192. The only un-automated step is going and getting the blocks, and **taking whole blocks is what
keeps the Trommel relevant** - a Hauler that sorted in the field would bypass it and make it
pointless. That also matches the Garbage Vacuum, which discards the `sorted` flyweight deliberately.

### What makes it unlike every other pack's quarry

**Mounds regrow.** `MoundGroundBlock` remembers a footprint and ticks a Block of Garbage back into
place. Every other quarry is a strip miner that exhausts a chunk and stops.

Combined with self-waking (ruling 6), this is **a pump on a renewable supply**: you activate it once.
The balance dial is therefore **rate against regrowth**, not time to exhaustion, and no other pack's
quarry has that dial. It is also the argument for a steep build cost and a deliberately slow rate.

## 2. Shape

**A Depot block, a Hauler ITEM, and a deployed ENTITY.** The Hauler is never a block.

1. Place a **Hauler Depot**. It is the home, the charger and the destination.
2. Put a **Scrap Hauler** item in its GUI slot.
3. Press **Deploy**. The Depot spawns the Hauler as an entity.
4. It searches a radius around the Depot and takes **whole blocks**.
5. When **full, or out of blocks**, it returns and dumps into the Depot.
6. It **wakes itself** when garbage is back in range.
7. It runs on a **solar trickle** in the field and **charges while docked**.

## 3. The rulings (owner, 2026-09-05)

These are the contract. Everything after this section derives from them.

| # | Ruling |
|---|---|
| 1 | Named the **Scrap Hauler** (item) and the **Hauler Depot** (block) |
| 2 | The Depot is the block; the Hauler is an **item it deploys as an entity**, never a block itself |
| 3 | It takes **whole blocks**, never sorting in the field |
| 4 | It carries a local cargo and returns when **full or out of work** |
| 5 | Its search is a **radius around the Depot** |
| 6 | It **wakes itself** when garbage returns; you activate it once |
| 7 | **Solar trickle** in the field; **charges while docked** in the Depot |
| 8 | The Depot **optionally takes RF**. Power is a speed-up, never a gate |
| 9 | The Depot has a **large inventory** |
| 10 | The Depot **joins the Scrap Network automatically** and **pushes continuously**; the inventory is a surge tank |
| 11 | The Hauler is inserted via a **slot in the Depot's GUI** |
| 12 | **Both must be crafted**, and **both are blueprint-gated** |
| 13 | **While deployed, the Hauler item cannot leave the Depot** |
| 14 | **One button, two states**: Deploy while docked, Recall while out |
| 15 | A **Mob for the navigation, with the biology opted out** |
| 16 | It **cannot be destroyed** |
| 17 | **Hostile mobs ignore it** |
| 18 | It **avoids fire, leachate and cliffs** |
| 19 | Breaking the Depot while deployed **auto-recalls first**, then breaks normally |
| 20 | **No region gating.** One Hauler, works everywhere |
| 21 | Flat or after dark, it **parks where it stands and recharges in the sun** |
| 22 | Recall brings it home to **dump as normal**, then it folds up |
| 23 | Depot backed up: it **waits at the Depot** until there is room |
| 24 | Works **only while chunks are loaded**; parks otherwise; **force-loads nothing** |
| 25 | **Blocks only**, never loose items |
| 26 | **One Hauler per Depot**, unlimited Depots |
| 27 | **About one block**, roughly a player's footprint |
| 28 | Audio: a **core set** of idle loop, pickup, deploy, recall |
| 29 | First-pass numbers **size one Hauler to feed roughly one Trommel** |
| 30 | Status on **Jade for both**, plus gauges in the GUI |

## 4. What already exists to build on

**Most of this is composition rather than new systems.**

| Need | Precedent |
|---|---|
| A **powered item** | The Garbage Vacuum is "the first item capability in the mod": `Capabilities.Energy.ITEM` over a charge component via `ItemAccessEnergyHandler`. The Hauler is the second and needs no new mechanism |
| **Charging it while docked** | `ChargingStationBlockEntity` is this loop whole: a `SimpleEnergyHandler` buffer, **insert-only** so "a Solar Panel or Burner against it" fills it, charging the docked stack through that same capability |
| A **screenless dock interaction** | `ChargingStationBlock` uses `useItemOn` / `useWithoutItem` |
| **Taking a garbage block, costed in FE** | `GarbageVacuumItem`, priced by the block's `sortRolls` |
| **Which piles are takeable** | `#recompile:vacuumable/*`, cumulative and fails closed |
| A **goal-driven entity that paths** | `PigeonEntity` + `PigeonForageGoal`; `RoachEntity extends Monster` |
| A **block leaving the world as an entity** | `VacuumedBlockEntity extends Entity` with an `EntityDataAccessor<BlockState>` |
| **Charge surviving on a dropped item** | `RCDataComponents.VACUUM_CHARGE` |
| **Menu buttons with no custom packet** | Vanilla's Stonecutter/Loom path, used in four menus here: `handleInventoryButtonClick` plus `clickMenuButton` |
| **Sunlight into FE** | `SolarPanelBlockEntity`, pinned by `solar_panel_makes_nothing_under_a_roof` |
| **One find teaching two blueprints** | `broken_terminal` teaches both market terminals from one teardown, two `teaches` entries at four scraps each |

**So the Depot is close to "a Charging Station that also deploys a Hauler and holds cargo."** That is
why the dedicated block is affordable.

## 5. The conservation invariant

> **The Hauler exists exactly once: as an item in the Depot, or as a deployed entity. Never both,
> never neither.**

Rulings 13, 16 and 19 exist to hold this. **Six separate code paths can break it, and they are
separate paths, so they need separate tests** - the lesson this repo already paid for on the
multiblock disband bug, where breaking the core and breaking a dummy cell were different paths, only
one was covered, and a machine handed back N cores from one break.

| Path | Guard |
|---|---|
| Taking it from the GUI slot | a custom `Slot` whose `mayPickup` is false while deployed |
| **Shift-clicking it out** | `quickMoveStack`, the classic bypass - a naive one moves the stack without consulting the slot guard |
| A hopper or pipe through a face | `canTakeItemThroughFace` refuses the Hauler slot. Six block entities here already use it |
| **Breaking the Depot while deployed** | ruling 19: auto-recall runs **before** drops are rolled. The mod paid for this shape once already, in #362, where creative-breaking a mattress by the foot duplicated it |
| **A repeated or lagged Deploy click** | the server re-derives state before acting. A button is an integer id on the wire, and two Deploys in flight must not put two Haulers in the world |
| Despawning or a chunk unload | the only remaining loss path now that it cannot be destroyed. `MobCategory.MISC` plus `setPersistenceRequired()` closes it |

**Charge and cargo have to survive item -> entity -> item.** Every hop has a precedent above, but
nothing here has chained them. Write the round trip as the **first** test: dock, deploy, load, return,
dump, break the Depot, replace it, and assert charge, cargo and binding all survived.

## 6. How it meets the world

**A Mob for the navigation, biology switched off, and nothing destroys it.** Damage is off the table
entirely; what ruling 17 buys is that nothing swarms or chases it.

### The failure mode is not death, it is being STUCK

This inverts the usual hazard design. An indestructible Hauler can still:

- **Stand in an eternal tire fire forever**, looking broken while being fine. Those fires are in the
  household sprawl, exactly where a first Depot goes.
- **Fall off a ledge** and be unable to path home.
- **Be buried by the garbage it is collecting.** `SortableBlock` is a `FallingBlock` and taking the
  foot of a stack collapses it (`taking_the_foot_of_a_stack_lets_it_collapse`). Landing garbage
  cannot hurt it now, but it can entomb it. **It must not be trappable by its own work.**

So ruling 18's avoid-goals are about **not getting stuck**, which is a better reason than damage was.
**It is also why Recall teleports rather than paths** (ruling 22): a machine that cannot die but can
strand itself needs retrieval that always works, and walking home is exactly what has already failed.

### What "biology opted out" must cover

| Opt-out | Note |
|---|---|
| **Leachate sickening** | **The trap.** `LeachateBlock.sicken` applies a mob EFFECT, and effects ignore `isInvulnerable`, so invulnerability does **not** cover it. `RCLeachateContact` only skips non-`LivingEntity`, which this is not. Needs an explicit exemption |
| Drowning | `drown` hurts, so invulnerability covers the damage, but the air counter still ticks |
| Fall, fire, suffocation | covered by invulnerability, though it may still visibly burn unless fire ticks are cleared |
| **Spawn cap** | `MobCategory.MISC`, as `VacuumedBlockEntity` already uses. A Mob in a spawning category would eat cap for a machine |
| **Despawning** | `setPersistenceRequired()` |
| Leashing, breeding, healing | nonsense on a machine; deny deliberately. A name tag is harmless, leave it |

## 7. Gating, reach and flow

**Both blueprint-gated (ruling 12), so both need a route.**
`every_shipped_blueprint_has_a_name_a_recipe_and_a_route` requires a teardown teacher **or** a market
offer per sheet. **The answer the market already used: a single Broken Hauler find in Bulky Waste
teaching both sets**, exactly as `broken_terminal` does. That is one line in
`loot_table/blocks/bulky_waste.json`, a weight in `bulky_spine` (currently 7 members, weight 15), and
one teardown recipe.

**No region gating (ruling 20), and it holds up because the Depot is a fixed installation.** A tier
gate matters on a handheld vacuum, because otherwise you strip the radioactive dump from your
doorstep. A Hauler only works around its Depot, so to work the dump you must carry a Depot into the
dump and place it. **Travel is still the gate; it is structural rather than tiered.** Blueprint gating
also means this is not an early craft.

**It should still respect the tag, just not the tiers:** take anything in
`#recompile:vacuumable/netherite`, the top of the cumulative band and therefore every takeable pile.
That keeps both properties the tier system exists for - it **fails closed**, so an untagged pile is
takeable by nobody, and a pack adding a pile is covered with no mod release.
`every_sortable_block_is_in_a_vacuum_band` already fails the build if a `SortableBlock` is in no band.

**Continuous push with the inventory as surge** (ruling 10): the network is the destination, and the
buffer exists because the Hauler gathers far faster than a Trommel eats at one block per 40 ticks.

## 8. The GUI is the eleventh custom screen

Ruling 11 makes this a new custom machine screen, so the standing rule applies: *no new custom
machine screen without recording a reversal; reuse a vanilla screen when you can.*

**The justification is clean, because the two nearest neighbours each solve half of it:** the Scrap
Barrel reuses vanilla `ChestMenu` because it is *only* storage, and the Charging Station has **no**
screen because it holds *one item*. The Depot is both at once plus power - a **dedicated Hauler
slot**, a **large bulk inventory**, and an **FE gauge**. Vanilla has no screen shaped like that.

Consequences that are part of the work:

- **CLAUDE.md's screen count goes ten to eleven.** That count has already been wrong three times, twice
  found by a SCRUB rather than by the person adding a screen.
- It runs on the GUI framework like the other ten: a `ScreenLayout` in **common** code with no
  `net.minecraft.client` imports, a `static final LAYOUT` computable before the screen exists that
  must not transitively touch a registry-backed class at class-init, and no `blit`, `RenderPipelines`
  or `leftPos` in the screen - `GuiFrameworkDisciplineTest` fails the build on any of those.
- **No recipe book**, per the standing decision of 2026-08-19.
- **The FE gauge puts this block in range of #369.** Every value a menu syncs travels through
  `ClientboundContainerSetDataPacket`, which writes a **short**: past 32,767 it reaches the screen
  wrapped negative, past 65,535 truncated, silently. Use the split-slot helper from day one
  (`content/menu/BalanceSync`, arithmetic on `Market` so a JUnit test can drive it with no world).
  The deployed flag is a boolean and is nowhere near the ceiling.

## 9. Presentation

**Model and animations are authored in this repo, in code. Sounds are sourced.** (owner)

| Asset | Kind | Pipeline |
|---|---|---|
| **Hauler Depot** | ordinary baked block model | normal, and it keeps the standing rule that exactly one `BlockEntityRenderer` exists here (the Display Pedestal) and everything else bakes |
| **Scrap Hauler, in the hand** | item model | an icon, or a small 3D model as the Puzzle Cube pieces already are |
| **Scrap Hauler, deployed** | articulated entity model plus keyframe animations | Java, authored here |
| **Textures** | `texgen`, `kind = "entity"` | existing pipeline, two precedents on disk (`roach.png`, `pigeon.png`) |
| **Audio** | sourced | four sounds: idle loop, pickup, deploy, recall |

**Three of these are firsts.** No bespoke entity model has ever been authored here - `RoachRenderer`
uses `SilverfishModel` and `PigeonRenderer` uses `ParrotModel`, and the Roach's javadoc makes that the
point ("the whole art budget for the mod's first entity is one texture"). No `AnimationDefinition`,
`KeyframeAnimations` or `AnimationState` appears anywhere in the tree. And the mod ships **zero**
custom audio: no `sounds/` directory, no `sounds.json`, no `.ogg`, only vanilla `SoundEvent`s.

**Particles are the cheap part**, well precedented, and the Garbage Vacuum already streams block dust
into its nozzle - directly reusable for the pickup.

### Both are Java in 26.1, verified rather than assumed

- **Model:** `MeshDefinition` / `PartDefinition` / `CubeListBuilder` / `PartPose` into
  `LayerDefinition.create(mesh, w, h)`. Boxes with dimensions and offsets, arithmetic not
  draughtsmanship.
- **Animation:** `AnimationDefinition.Builder.withLength(f).looping().addAnimation(part, new
  AnimationChannel(Targets.ROTATION, new Keyframe(t, KeyframeAnimations.degreeVec(...),
  Interpolations.LINEAR)))`, driven by an `AnimationState`.

A throwaway probe using every class above **compiled clean in this project**, so
`net.minecraft.client.animation.*` exists with that shape here. **No new dependency** - no GeckoLib,
no Blockbench round trip - and the result is diffable, the same argument that made the Puzzle Cube's
faces procedural.

### The silhouette is chosen the way art already is

Several `LayerDefinition`s, spawned side by side in a dev client and photographed with the existing
tooling (`tools/shoot_scenes.py` and friends drive a running client over devbridge). **The owner's
pick is the approval**, as `select` is for textures.

**Then the ordering constraint:** an entity texture is UV-mapped, and bespoke geometry means a
bespoke UV layout. Both existing entity textures were drawn against *vanilla* models whose UV maps
were already fixed, so texgen would otherwise be generating blind. Order is **silhouette -> UV layout
-> texture prompt**, never the reverse, and the mesh should be generation-friendly (few boxes,
predictable grid, generous texels). If that proves awkward, draw it procedurally, as the Puzzle Cube
stickers and the pedestal plinth already are.

**Audio has no rule yet, and needs one.** Textures have a hard one: generated by texgen, never
hand-drawn, no raw AI output committed, `gen/` and `art_src/` gitignored. Where sound comes from,
under what licence, and what lands in the repo wants deciding the same way and before the work.

## 10. Build order

**Navigation first, before any model or animation.** Ruling 27 puts a full-block footprint on terrain
that is uneven and made of `FallingBlock`s that move, and **it is the only part of this feature that
can fail in a way the rest cannot design around.** Mitigations are part of the build: a generous step
height so it climbs slumped garbage rather than stalling, the cliff-avoiding navigation ruling 18
already calls for, and target selection that prefers a reachable pile over the nearest one.

Then, roughly: the Depot block and its GUI; the powered item and charging; deploy and recall with the
conservation tests; the gather loop; presentation last.

## 11. Deferred

- **The numbers.** The rule is ruling 29: size one Hauler to feed roughly one Trommel, which eats a
  block every 40 ticks, so about 30 a minute. First pass is roughly a stack of cargo per trip against
  a work radius near 16. **The dial that matters is rate against regrowth** - faster and it strips the
  field and idles, slower and it is a trickle you leave running. Measure it in a world; belongs with
  the balance pass (#36).
- **A row in `docs/automation_policy_spec.md`.** It has none for a mobile collector - every row there
  is a placed block with faces - and per that document's own first rule this needs one before it
  ships. It needs a new row *shape*.

## Not in scope

No narrative or story framing. **WALL-E is this project's named aesthetic and tonal anchor**
(`../trashlands/docs/concept.md`), and per that document's own discipline note the reference is about
look, block language and tone only.

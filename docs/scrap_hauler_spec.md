# The Scrap Hauler - automated gathering, and this mod's quarry

**Status: BUILT 2026-09-05, on the branch for #376.** Owner rulings are numbered and dated in
section 3; everything else is derivation and is arguable. Section 12 records what the build decided
where this document had left a choice open, and each of those is the assistant's call rather than the
owner's.

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

## 12. As built (2026-09-05)

Where the spec left a choice open, the build made one. Every item here is the assistant's call, made
to ship, and is the first thing to revisit if it reads wrong. The rulings table in section 3 is
untouched; nothing below reverses one, and the one place the build reads a ruling narrowly (ruling
21) is called out as such.

| Open point | What was built | Why |
|---|---|---|
| Ruling 14, "one button, two states" | One button on the screen, **two ids on the wire**: `HaulerDepotMenu.DEPLOY_BUTTON` (0) and `RECALL_BUTTON` (1). The screen sends whichever id matches the label it was showing, and `clickMenuButton` dispatches to `deploy` or `recall` by id | A toggle would make a laggy double-click deploy and then immediately recall. With intent on the wire, a duplicated Deploy against a Hauler that is already out is a no-op, and a stale Recall against one already home is too. The block entity re-derives its preconditions either way (`deploy` refuses while `deployed`, or with no Hauler in slot 0), which is the sixth conservation row; `a_second_deploy_does_not_make_a_second_hauler` pins it |
| Ruling 8, "RF is optional", made concrete | The **docked** Hauler trickles from sky light exactly as the deployed one does: `HaulerDepotBlockEntity.trickleDocked` reads the sky brightness of the block above the Depot minus the world's darkening, and at 12 or more adds `SOLAR_PER_TICK` to the item. The Depot recipe deliberately carries no Solar Panel | A Depot with nothing wired to it still turns its Hauler around, just slowly. The FE buffer (`CAPACITY` 20,000, insert-only, `TRANSFER_PER_TICK` 200 into the item through `Capabilities.Energy.ITEM`) is the speed-up, never the gate |
| Section 10, "target selection that prefers a reachable pile" | **Reachability is decided by trying**, not by a path pre-check. `ScrapHaulerGoal` blacklists a target for 400 ticks after 3 refused `moveTo` calls or 100 ticks without getting closer, and picks the nearest pile to the Hauler inside the Depot's radius that is not blacklisted | The first build asked the navigation for a path before committing, and that read a transient refusal as "unreachable": the tick after spawning, the mob is not yet on the ground and the navigation refuses everything, so the nearest pile was blacklisted for twenty seconds before the machine had moved. On terrain made of `FallingBlock`s that move, "nearest" is often briefly unreachable; the answer is patience, not prediction |
| Ruling 18, what "avoids" covers | Two layers. Pathing maluses refuse `FIRE`, `FIRE_IN_NEIGHBOR`, `DAMAGING`, `DAMAGING_IN_NEIGHBOR`, `LAVA` and `WATER` outright (-1) and penalise `WATER_BORDER` (4). And `ScrapHaulerGoal.takeable` **refuses as a target** any pile with fire, lava or leachate on any of its six sides. No cliff-specific rule was written | The maluses stop it walking through a hazard; the target rule stops it choosing a block whose neighbour is one, which would leave it standing in the hazard once it arrived. `the_hauler_refuses_a_pile_beside_fire` pins the second half. A drop cannot hurt it (`causeFallDamage` returns false) and Recall teleports, so a cliff strands it at worst, and retrieval always works |
| Ruling 21, "flat or after dark, it parks" | Built as **parks when flat**: below `FLAT_BELOW` it stops where it stands (`PARKED_FLAT`) and moves again at `WAKE_AT`. Night is when the trickle stops (it needs sky brightness minus darkening of 12 or more), so a Hauler working after dark runs its charge down and then parks; one with charge keeps working in the dark | The charge level is the one fact that covers both halves of the ruling, and reading `isDay` separately would park a fully charged machine for no reason. If the owner wants a hard stop at dusk, that is one branch in `ScrapHaulerGoal.seek` |
| Section 6, "nothing destroys it" | `isInvulnerableTo` lets through only `#minecraft:bypasses_invulnerability` (`/kill` and the void), fall damage is refused, it cannot be leashed, `removeWhenFarAway` is false, and `tickDeath` **removes the entity on the spot** rather than playing the twenty-tick death animation. `remove` notifies the Depot (`onHaulerGone`) whenever the removal was not a recall | An operator has to be able to delete one, and a machine does not lie down first. Immediate removal is what lets `/kill` unlock the Depot's slot on the same tick; `a_killed_hauler_unlocks_the_depot_without_an_extra_item` pins that no item is duplicated by it |
| The Depot broken while the Hauler is somewhere unloaded | The entity's tick notices its Depot position is loaded and no longer a Depot, spills its cargo at its feet and discards itself **without dropping a Hauler item** | Ruling 19's auto-recall runs on the Depot side (`preRemoveSideEffects` recalls first, then drops the contents) and cannot reach an unloaded entity. The Depot already dropped the item in its slot when it broke; a second one here would be the duplication the invariant exists to prevent |
| A replacement Depot on the same coordinates | `HaulerDepotBlockEntity.owns(uuid)`: the entity asks every tick whether the Depot at its home position is the one that DEPLOYED it, and folds without an item if not | Found live, not by reasoning. A Depot broken while its Hauler was somewhere unloaded drops the Hauler item; if a new Depot is then placed on the same spot before the old entity loads, the entity finds a Depot at home, adopts it and works for it, and the new Depot has two machines in the field and one item in its slot. A dev client that rebuilt its stage on the same coordinates measured exactly that (`hauler entity present: Count: 2`). `a_stale_hauler_does_not_adopt_a_replacement_depot` pins it |
| Being buried by its own work | `ScrapHaulerEntity.unstick`: if the cell it occupies has become solid, it teleports to the first non-solid cell in the eight above and stops navigating | Taking the foot of a stack collapses it (`taking_the_foot_of_a_stack_lets_it_collapse`), and a landed garbage block cannot hurt this machine but can entomb it |
| Section 11, the intake cadence | One block per **4 ticks** while in reach (`INTAKE_PERIOD_TICKS`, read off `GarbageVacuumItem` so the two machines take at one speed) | The first build took a block every tick it stood inside a cluster: twenty a second, a full hold in three, and a trail of flying blocks it had outrun. That is both the wrong look and a rate no balance pass could reason about; the vacuum's own cadence is the honest first-pass number |
| Section 11, the numbers (first pass, #36) | Cargo **64** (`CARGO_CAPACITY`); work radius **16** around the Depot (`WORK_RADIUS`); reach **2.6** blocks; solar **2 FE/tick** (`SOLAR_PER_TICK`, one Solar Panel's output); Hauler charge **16,000 FE** (`ScrapHaulerItem.CAPACITY`, the diamond vacuum's); Depot buffer **20,000 FE**; **27** hold slots plus the Hauler slot; **200 FE/tick** docked transfer; parks below `VacuumTier.costFor(4)` = **40 FE** and resumes at **1,600** (a tenth of capacity); idle rescan every **40** ticks; step height **1.0**, movement speed **0.28**, follow range **48**; a block costs what the vacuum charges for it (`VacuumTier.costFor(sortRolls)`) | Sized so one Hauler feeds roughly one Trommel (ruling 29). Both capacities sit under 32,767 on purpose, since the Depot's screen syncs each through one menu data slot, and `ScrapHaulerSyncTest` (JUnit) fails the build if either is raised past the wire ceiling |
| Section 7, the route to the sheets | `broken_hauler`, a plain item, weight **1** in `gameplay/bulky_spine` (now 8 spine finds, total weight 16). Its teardown (`recipe/broken_hauler.json`, prybar, 120 ticks) draws four times from scrap metal, e-scrap, rubber and plastic with no filler, and `teaches` **both** `recompile:scrap_hauler` and `recompile:hauler_depot` at 4 scraps each | The Broken Terminal's shape exactly: one find, two sheets. No component in the pool, per that recipe's rule that the thing yielding a component should be the thing teaching it |
| Ruling 28, the sounds | `RCSounds` registers four events, `entity.scrap_hauler.{idle,pickup,deploy,recall}`, the mod's first. `sounds.json` **redirects** each to a vanilla sound EVENT for now (`type: event`): `block.beacon.ambient`, `entity.item.pickup`, `block.piston.extend`, `block.piston.contract` | The audio is sourced rather than generated (owner), and none has been sourced yet. A bare file path into the `minecraft` namespace does NOT resolve from a mod's `sounds.json` - the first build did that and the SoundEngine logged `Missing sound for event` for all four - and an event redirect does. One more trap on the way: a `_comment` key in `sounds.json` is deserialised as a sound entry and takes the WHOLE file down (`Invalid sounds.json in resourcepack`), so that file carries no comment and the note lives on `RCSounds`. The redirects make the events resolve and exercise every hook; swapping in real audio is a change to that JSON and nothing in Java |
| Section 9, the textures | Four texgen surfaces, all promoted and all **pending owner approval** (none is in `gen/approved.json`): `hauler_depot` (block, top/bottom/side), `scrap_hauler` (item), `broken_hauler` (item, `reference = "scrap_hauler"` so the two read as one object in two states), `scrap_hauler_skin` (entity) | `select` by the owner is approval, and none has been issued. All four ship as candidates in the meantime, which is the `mound_ground` situation this repo has already recorded once |
| Section 9, the ordering constraint on the skin | The entity skin is **procedural**: a new `hauler_skin` style in texgen's procedural backend (`../mc-pack-toolkit/texgen/texgen/backends/procedural.py`) paints the 64x64 atlas from the exact box layout `ScrapHaulerModel` declares (hull, head, arm, two treads) | This is the first entity here whose UV layout was not fixed by a vanilla model, so an AI generator would be painting blind into a layout it knows nothing about. The spec's fallback ("draw it procedurally, as the Puzzle Cube stickers are") became the primary route |
| Section 9, the silhouette | Candidates are selected at launch with `-Drecompile.hauler.silhouette=N`; every candidate keeps the same box dimensions and differs only in where the parts sit, so one skin fits all of them. **0 ships** until the owner picks | A model cannot go on the texture review page; what it can do is be photographed in a dev client. Same box sizes is what keeps the UV contract true for every candidate |
| Section 8, the screen | `HaulerDepotMenu.LAYOUT` is 176x206: a header band with the Hauler slot, the Deploy/Recall button, a status line and a vertical FE gauge, then the hold as a chest's 9x3, then the player inventory. `quickMoveStack` checks `slot.mayPickup` explicitly | Vanilla's shift-click path never consults a slot's pickup guard, so the second conservation row lives there; `the_hauler_cannot_be_shift_clicked_out_while_deployed` pins it |
| Section 11, the automation policy row | Written: `docs/automation_policy_spec.md` has a Hauler Depot row, and says in prose that the entity itself exposes nothing to automation | The row shape did not need to change. The Hauler is a mobile collector, but its only automation surface is its Depot |

Eighteen GameTests in `ScrapHaulerTests` and three JUnit tests in `ScrapHaulerSyncTest` cover the
above; the GameTest names are the invariant table in executable form.

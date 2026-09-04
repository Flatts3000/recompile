# Tree Nursery - implementation spec

**Written 2026-07-27.** Rung 4 of the reclamation chain (design P2.4-R), the Mod Jam "Trees" tier.
**SHIPPED** as reclamation rung 4 (Phase 2.16, 2026-07-27): `TreeNurseryCoreBlock`, `TreeNurseryBlockEntity`, `TreeNurseryMenu`, `TreeNurseryScreen`, `TreeNurseryTests`, and the `reclamation/nursery` guidebook entry. Design source of truth: `../trashlands/docs/design_decisions.md`
(**P2.4-R** the economy revision, **P2.4-R2** the sapling lockout, **P1.7-R** encroachment). This
spec **revises P2.4-R2** - see "The reversal" - and that revision is **not yet recorded there**; see
"Design record owed".

---

## What it is

A multiblock machine with a bespoke GUI that turns compost inputs into **tree saplings the player
plants themselves**. Feed it **water + Fertilizer + an Unknown Seedling**, pick a species in its
screen, and over time it yields that species' vanilla sapling into an output slot. The player carries
the saplings out and plants a forest by hand.

It is deliberately **not** a spreader. Earlier drafts had rung 4 auto-plant trees over a radius like
the Grass Spreader (rung 1); this is a *producer* instead - inputs in, sapling out, furnace-shaped.
The player owns where the forest goes. (Decision this session: player agency reads better for this
pack than a machine that plants for you.)

---

## The reversal - P2.4-R2 revised (saplings are made, never found)

P2.4-R2 locked "**a player can never obtain a sapling as an item**" - narrowed on 2026-08-20 to "no loot roll yields one", because #227 brought the EMERALDS to buy one from a wandering trader (the traders themselves always spawned here - no village is needed) and #263 closed not-planned rather than curate trades - and enforced it with
`StripSaplingsModifier`, a global loot modifier stripping `#minecraft:saplings` from every loot roll.
This machine hands the player sapling **items**, so it revises that decision. The revision is
**narrower than it looks**, and the load-bearing half of P2.4-R2 survives intact:

- **Keep the loot strip.** `StripSaplingsModifier` stays exactly as-is. Saplings still cannot be
  *found* - not in pulls, not in chests, not from a chopped tree's drops. The modifier strips loot;
  the nursery is a machine output, which the modifier never touches.
- **The nursery becomes the only sapling source a player can reach without a villager.** That is
  P2.4-R2 item 2's intent ("the tree planter is the only source of trees"), preserved where it
  matters. Wood is still metered by a machine you build and feed. *(Narrowed 2026-08-20: a wandering trader sells saplings for emeralds, so this is now "no loot roll yields one" rather than an absolute. See `StripSaplingsModifier`.)*
- **No self-perpetuating forest.** A chopped tree drops no sapling, so a tree farm is a *nursery
  running* (consuming water + Fertilizer + Unknown Seedling per sapling), not hand-replanting from
  drops. P2.4-R2 item 3's intent survives.

**What changes:** *the machine plants* becomes *the player plants*. That is the whole delta.

**The one consequence to decide (open question).** A nursery sapling is an ordinary item, so vanilla
lets it be planted on **raw coarse dirt** - the exact thing P2.4-R2 was written to stop (rung 3
permanence with no rung 1 or 2). The consequence is **milder than that doc feared**, because
encroachment only ever contests *green* ("Only the green is contested", P1.7-R): a tree planted in
raw grey anchors a spot nobody is fighting over and grants no free reclamation. The only softening is
that a rung-1 grass edge could be anchored with a nursery tree without building rung 2 first.

- **Default (this spec): ship plain vanilla saplings, plantable anywhere.** Simplest, most
  player-friendly, and the exploit is mild per the above.
- **Alternative if we want to protect the ladder story:** a custom sapling item gated to healed
  ground (refuses coarse dirt). Moderately more work - a custom `BlockItem`/placement check per
  species, or one seed item carrying species in a data component. Deferred unless Jason calls for it.

---

## Structure - a 2x2x1 wall (Jason, this session)

A **wall: 2 wide, 2 tall, 1 deep** (4 blocks). Bottom row is the core and the water tank; the top row
is two solar panels. Reuses the shipped framework (`multiblock_system_spec.md`) and the Grass
Spreader's exact parts - Water Tank and Solar Panel - so **no new
component art and no Machine Frame** in this machine.

```
[Solar] [Solar]      top row  (y = 1)
[Core ] [Tank ]      bottom   (y = 0)
```

| Offset (x,y,z) | You place | Formed as | Notes |
|---|---|---|---|
| (0,0,0) core | *(the core itself)* | **Tree Nursery Core** | The master, bottom row. Holds the BE (water tank, slots, species, output), opens the GUI, and accepts the bucket. Its own texture. |
| (1,0,0) | **Water Tank** | *unchanged* | Bottom, beside the core. **Inert** - the shared `water_tank` dummy, reused as-is; holds no fluid. The **core** stores and accepts the water. |
| (0,1,0) | **Solar Panel** | *unchanged* | Top, above the core. Shared inert no-op decorator (craftable + dummy), same as the Grass Spreader's cap. |
| (1,1,0) | **Solar Panel** | *unchanged* | Top, above the tank. |

Auto-assembles from **1 Water Tank + 2 Solar Panels** carried in inventory when you place the core;
sneak-place gives a bare core; breaking any cell disbands and returns every part (the framework
handles all of this). Same "no BlockEntity for the structure" rule - `FORMED` is blockstate; the BE
holds only the machine's *contents* (water, items, species), the sanctioned exception the Rain
Collector and Scrap Barrel already sit on.

**Facing:** the wall is flat (1 deep), so unlike the rotation-invariant towers it has a front. Give
the core `HORIZONTAL_FACING` and a `rotationFor` so the blueprint builds relative to the player and
the GUI face reads front-on (the framework already rotates blueprints per `rotationFor`; the
Workstation was the first directional user). The "wide" axis is the core's left-right; "deep" (1) is
front-back.

**Solar panels are inert.** Two on top read as "this machine is powered", but per P3.5 (no RF before
the Nether) they do **not** generate or gate power - the nursery runs on its inputs, not on daylight.
Same standing rule the Grass Spreader's panel follows.

**RF status changed 2026-07-31.** P3.5's "no RF before the Nether" was reversed: the energy tier now arrives with hydroponics, and the Solar Panel becomes a real generator. See `../trashlands/docs/design_decisions.md` P3.5 and `docs/hydroponics_spec.md`. The **Pump stays inert** - that is P2.3, a separate decision.

**Why the tank is inert and the core holds the water** (Jason, this session). One BE, no new
fluid-storage block. The core carries a water-only `FluidStacksResourceHandler` (the Rain Collector's
exact tank) and **accepts the bucket itself**: right-click the core with a water bucket and
`FluidUtil.interactWithFluidHandler` fills it, the same path `RainCollectorCoreBlock` already uses.
The tank cell is the visual "here is where the water lives", nothing more.

---

## The bucket - vanilla item, copper recipe

The player needs a vessel to carry water from a Rain Collector to the nursery. The **plumbing already
exists**: vanilla buckets interoperate with every fluid handler in the mod (the Rain Collector
explicitly accepts `Items.BUCKET` / `Items.WATER_BUCKET` via `FluidUtil`). The only gap is *obtaining*
one - there is no iron path in this world (only `scrap_metal` -> copper).

**Add a copper crafting recipe for the vanilla `minecraft:bucket`** (Jason, this session): the vanilla
bucket shape with **copper ingots** swapped for iron.

```
C C
 C     ->  minecraft:bucket
```

`C = minecraft:copper_ingot` (from copper nuggets via vanilla 3x3; nuggets from smelting `scrap_metal`
in the Burn Barrel). Copper is this world's first metal and the mod already runs on it (copper pipe,
spigots), so a copper bucket is consistent with P1.10's "improvised, pre-iron". Zero new item code -
the vanilla bucket already works with the Rain Collector and will with the nursery core. The bucket is
the bigger vessel (1000 mB) that makes filling a tank practical versus the current bottle-by-bottle
(250 mB) path. Loop: Rain Collector catches rain -> bucket it out -> fill the nursery core -> the
nursery consumes it.

(If the dump theme later wants a "Scrap Bucket" reskin, that is a texture/name pass over the same
item, not new behaviour. A bespoke `BucketItem` on 26.1's transfer API is real fiddliness for a
cosmetic win, so not now.)

---

## The GUI - the mod's second bespoke screen (recorded reversal)

The mod's standing rule is **no new custom machine screen without recording a reversal** (CLAUDE.md;
`design_decisions.md`). There is exactly one today: the Scrap Crafting Station's connected-storage
panel. The nursery earns the second, and the justification is concrete: **species selection has no
vanilla-screen analog**, and it cannot be an inserted-item template because the player has no sapling to
insert - the nursery is what produces them, so requiring one as input would be circular. A picker means buttons; no `FurnaceMenu`/`ChestMenu` has them.

**Reuse the proven pattern**, scoped to this block:

- `TreeNurseryMenu extends AbstractContainerMenu` with a custom `MenuType` in `RCMenus`, exactly like
  `ScrapCraftingStationMenu` (which reimplements over `AbstractContainerMenu` for the same "vanilla
  menu hard-locks its type" reason). Slots: Fertilizer input, Unknown Seedling input, sapling output
  (result-slot semantics - take-only), plus the player inventory.
- `TreeNurseryScreen extends AbstractContainerScreen<TreeNurseryMenu>`, registered client-only in
  `RCMenuScreens`. Drawing is 26.1 retained-mode: `extractBackground(...)`, `blit` with a
  `RenderPipelines` pipeline + explicit atlas dims, `graphics.item(...)` / `graphics.text(font, ...)`,
  `imageWidth`/`imageHeight` final via the 5-arg ctor - the same shape `ScrapCraftingStationScreen`
  already uses.
- **Species picker = the vanilla Stonecutter/Loom button pattern.** A row of selectable species
  buttons; a click routes through `AbstractContainerMenu.clickMenuButton(player, id)`, the server sets
  the BE's selected species, and it syncs back. This is exactly how the Loom selects a pattern and the
  Stonecutter a recipe - a well-trodden, no-custom-packet path.
- **Water gauge + progress arrow** via `ContainerData` (`DataSlot`s), the furnace pattern: stored
  water mB and cook progress travel to the screen with no bespoke networking.

**Species set:** the vanilla saplings/propagules - oak, birch, spruce, jungle, acacia, dark oak,
mangrove, cherry, pale oak (whatever `26.1` ships). Each button shows the sapling icon; the selected
one is what the machine produces.

---

## Behaviour - the production loop

Furnace-shaped, on the core's BE server ticker (the `RainCollectorCoreBlock` / `BurnBarrelBlock`
`getTicker` pattern):

1. **Gate:** only runs while `FORMED`, and only with a species selected, water >= per-sapling cost,
   one Fertilizer present, one Unknown Seedling present, and room in the output slot.
2. **Cook:** advance a progress counter each tick (config `TREE_NURSERY_COOK_TICKS`). Mid-cook the
   inputs are reserved; the arrow fills in the GUI via `ContainerData`. **This is deliberately slow**
   (Jason, this session) - a sapling takes a *rather long time* to raise, so wood stays treasure: the
   first sapling is a monument, and a tree farm is a patient nursery, not a tap. Default should sit in
   minutes-per-sapling, not seconds (e.g. multiple in-game minutes at `20 ticks/s`); the exact number
   is balance-pass (#36), but the *intent that it is long* is design, not a placeholder.
3. **Finish:** on completion, consume `TREE_NURSERY_WATER_PER_SAPLING` mB + 1 Fertilizer + 1 Unknown
   Seedling, and place one sapling of the selected species in the output. Reset progress.
4. **Idle** when any input is missing or the output is full; resume when supplied.

Static entry points for GameTests (`produceOnce`, or a "fully load + tick to completion" helper),
per the `sortOnce` / `encroachOnce` / Compost Heap convention - tests drive the BE directly, never a
simulated click.

**Inputs recap:** water (core's fluid tank, filled by bucket) + Fertilizer (Compost Heap) + Unknown
Seedling (Compost Heap volunteer). The Unknown Seedling becomes **dual-purpose**: hand-plant it on
farmland and it is a random *crop* (`UnknownSeedlingItem`, unchanged); feed it to the nursery and it
becomes the *tree* species you chose. Coherent with "unknown" - the context decides what it grows
into - and it means the tree line and the crop line both come out of the same composter, no new seed
item.

---

## New blocks / items

| Thing | Kind | Notes |
|---|---|---|
| `tree_nursery` | core block, bespoke | `extends MultiblockCoreBlock implements EntityBlock`. Holds `FORMED`; BE + GUI + water accept. `noOcclusion()` if its model is not a full cube. |
| BE `tree_nursery` | BlockEntity | Water tank (`FluidStacksResourceHandler`, water-only) + Fertilizer/Seedling/output slots + selected-species field + cook progress. Save/load via `ValueOutput`/`ValueInput` (Scrap Barrel pattern). |
| `TreeNurseryMenu` | menu | Custom `MenuType` in `RCMenus`. |
| `TreeNurseryScreen` | screen | Registered in `RCMenuScreens`, client-only. |
| `minecraft:bucket` | vanilla item | New copper crafting recipe only - no new item. |
| `water_tank` | existing shared dummy | **Reused inert** (bottom cell), no change. |
| `solar_panel` | existing shared component | **Reused** for both top cells, no change. |

No Machine Frame and no new component blocks - the wall is core + Water Tank + 2 Solar Panels, all
existing.

No new sapling items in the default plan (vanilla saplings are the output). The custom gated-sapling
lives only in the alternative branch of the open question above.

---

## Data surface

- **Config** under `reclamation` (`RCConfig`): `treeNurseryEnabled`, `treeNurseryCookTicks`,
  `treeNurseryWaterPerSapling`, `treeNurseryTankCapacity`. Numbers are first-pass placeholders for the
  pre-beta balance pass (issue #36), with **one design constraint that is not a placeholder**:
  `treeNurseryCookTicks` is **long** (minutes per sapling, not seconds) so raising a tree stays a
  patient, treasure-grade act. Tune the magnitude at the balance pass; keep it slow.
- **Recipes:**
  - **Tree Nursery** core - a scrap recipe in the machine family (frame + plating + copper, settle
    exact shape with the other machines).
  - **Bucket** - `C C / _C_` copper ingots (above).
  - Fertilizer, Unknown Seedling - already ship from the Compost Heap; no change.
- **Loot:** core drops itself; the tank and both solar cells disband-return their components (Water
  Tank / Solar Panel) - they are unchanged cells, so their existing loot tables already do this.
- **Tags:** `#recompile:scrap_connectable`? No - the nursery is a reclamation machine, not a
  scrap-network member. Leave it off the network tag.
- **Lang** for the block, the GUI title, and any species-button tooltips.

---

## Assets (texgen)

Lean on vanilla + existing, per the strategy that carried the other machines:

- **Core** - bespoke, iterated in-world (no real-world reference for "garbage-world tree nursery";
  start from a planter/bench silhouette). Must not reuse the collector or spreader palette.
- **Tank cell** - the shipped `water_tank` model, unchanged.
- **Solar panels** - the shipped `solar_panel` model, unchanged (two on top).
- **GUI background** - a bespoke 176-wide panel following the Scrap Crafting Station's texture layout
  (slots + picker row + water gauge + arrow).
- **Bucket** - vanilla icon (copper reskin deferred).

~2-3 texgen surfaces (`core`, GUI background, maybe `frame`), declared in `texgen.toml`. No raw AI
output in the repo; only the finalized 16px PNGs commit.

---

## Tests (GameTest) - `gametest/TreeNurseryTests.java`

Driven through the BE static entry points:

1. Fully supplied (water + Fertilizer + Seedling + species) produces one sapling of the selected
   species after `cookTicks`.
2. Missing any single input -> no output (negative-controlled: no water; no Fertilizer; no Seedling;
   no species selected).
3. Consumes exactly one Fertilizer + one Seedling + `waterPerSapling` mB per sapling.
4. Species selection is honoured: selecting birch yields a birch sapling, not oak.
5. Output blocked when the output slot is full; resumes when cleared.
6. Only runs while `FORMED`; unformed core is a no-op.
7. Water accept: right-click-with-bucket path adds `1000` mB to the core tank (or the BE fill helper).
8. Save/load round-trip: water + progress + selected species survive `ValueOutput`/`ValueInput`.

Register in `RCGameTests.register`. `RegistryCompletenessTests` will sweep the new block/item for
lang + model + blockstate + loot automatically.

---

## Open questions

1. **Coarse-dirt planting** (the big one, above): ship vanilla saplings (default, plant anywhere) vs
   a healed-ground-gated custom sapling. **Needs Jason's call before build if we want the gate.**
2. **Exact blueprint offsets + core silhouette** - iterate in-world.
3. **Species button layout** - single row of ~9 vs a small grid; settle against the GUI width.
4. **All numbers** - cook time, water per sapling, recipe costs. Deferred to the balance pass (#36).

## Verification

1. `JAVA_HOME="/c/Program Files/Java/jdk-25" ./gradlew build` - redirect to a file, check `$?`.
2. `JAVA_HOME="/c/Program Files/Java/jdk-25" ./gradlew runGameTestServer` - full suite incl.
   `TreeNurseryTests`; reported total is ours **plus one**.
3. `runClient` - build the nursery, fill it with a copper bucket from a Rain Collector, pick a
   species, watch it cook a sapling, plant it. Iterate on the core look and GUI layout from there.
4. **Code review before merge.**

## Design record owed

Record in `../trashlands/docs/design_decisions.md` as **P2.4-R2b** (revises P2.4-R2): saplings ARE
obtainable, but only as a Tree Nursery output; the loot strip stays, so the nursery is the sole
sapling source and wood stays machine-metered. Capture the coarse-dirt decision (whichever way it
lands) in the same entry. Also record the **second bespoke-screen reversal** (the species-picker GUI),
scoped to this block, alongside the Scrap Crafting Station's.

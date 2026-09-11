# CLAUDE.md

Guidance for Claude Code in this repo. **This file is an index.** Each rule or trap gets one line
here, and the full story (owner rulings, issue numbers, the failure that taught it) lives in the doc
it points to. Read that doc before changing the area.

## What this is

- A standalone **NeoForge** mod for **MC 26.1.2**. Mod id / package: `recompile` / `com.flatts.recompile`.
  It ships the garbage-world systems the **Trashlands** modpack is built on: worldgen, Blocks of Garbage,
  sorting, mound regrowth, the reclamation ladder, the market and the freight ladder.
- **Four systems, four jobs, no overlap** (owner 2026-09-06, P3.10 in `../trashlands/docs/design_decisions.md`):
  the **market** is the source of knowledge (every Blueprint but a spawn egg's is bought with scrip), **freight
  quotas** are tier progression (delivering named processed goods, not a scrip balance), **teardown**
  yields **function** (working components), and **sorting** yields bulk materials. Teardown no longer
  teaches: no shipped teardown carries `teaches`, and the Workbench no longer reads it (#390). The
  field stays in the schema because packs write it.
- *Infinite scrap, no infinite supply of working motors.* The **Motor** is the one
  `#recompile:function_only` item: no recipe and no market offer, so it is salvaged or nothing. Mounds
  regrow, so find-only is a rate limit rather than a wall. Every other component keeps both routes (#228).
- **Finished goods are found, not crafted** (P2.11, #161). The test is *would a person throw this away?*
  Membership is the `#recompile:found_only` tag. Building blocks and components stay craftable.
- **Status** lives in `CHANGELOG.md` (releases) and `docs/roadmap.md` (phases; Phase 6, the full loop,
  is what remains). Do not restate release history here.

Narrative and rationale: `docs/systems_notes.md`.

## Build and test

The system `JAVA_HOME` is stale (a nonexistent JDK 17), so **every** gradle call needs it overridden:

```bash
JAVA_HOME="/c/Program Files/Java/jdk-25" ./gradlew build
```

| Task | Command |
| --- | --- |
| Compile only | `./gradlew compileJava` |
| Full build + jar (runs `test`) | `./gradlew build` |
| JUnit layer | `./gradlew test` |
| In-world GameTests | `./gradlew runGameTestServer` |
| Dev client (JEI + Jade) | `./gradlew runClient` |
| Regenerate IDE run configs after `clean` | `./gradlew prepareAllRuns` |

- **Never pipe gradle to `tail`/`head` and trust the exit code.** Redirect to a file and check `$?`, or use `PIPESTATUS`.
- `runGameTestServer` runs the whole suite (no filter, seconds) and hard-fails on any worldgen JSON parse
  error, so it is also the fastest datapack validator. Our tests run in the `recompile:default`
  environment, and the total is **ours plus one** (a vanilla `minecraft:default` batch runs alongside).
- **JUnit** (`src/test/java`, moddev's `unitTest` against a loaded mod context) is for pure logic such as
  `GeneratorState` or `ScrapBinContent`: no world, no rendering, no server. **GameTests** are where
  in-world behaviour is proven. Count tests from `build/test-results`, never from a doc.
- CI (`.github/workflows/ci.yml`) runs `build` and `gameTest` as separate jobs. The **`build` job name is
  load-bearing**, because branch protection on `main` requires it.

## Releasing

A release is a **tag push**, nothing else. `.github/workflows/release.yml` builds the jar, creates the
GitHub release and uploads to CurseForge through the API (`CF_API_TOKEN` secret, `CF_PROJECT_ID` variable).

1. Bump `mod_version`, turn `## Unreleased` into `## vX.Y.Z` in `CHANGELOG.md`, and update the status in
   `README.md` and `docs/roadmap.md`.
2. Commit, tag `vX.Y.Z`, push both. Confirm with `gh run list --workflow=release.yml`. The log line
   `HTTP 200: {"id":...}` is the CurseForge file id.
3. If only the upload step failed, re-run via `workflow_dispatch` with `ref=<tag>`. **Do not move the tag.**
4. **Never upload a jar through the CurseForge website** (owner 2026-09-05). That creates a duplicate
   file, not a second route. The browser is for page copy only, and `docs/curseforge_page.md` is the
   source of truth for that copy.

## Design lives in the Trashlands repo

Feature design is decided there, not here. Read these before changing gameplay:

- `../trashlands/docs/design_decisions.md`: locked per-feature decisions. **Start here.**
- `../trashlands/docs/concept.md` (vision), `../trashlands/docs/material_economy.md` (sourcing),
  `../trashlands/docs/progression_gates.md` (read it before touching the iron gate).
- `../trashlands/docs/the_twist.md` is **FULL SPOILERS**. Never restate its contents in player-facing
  copy, and never hint at it in any other doc.

**Engine / pack split:** Recompile is the engine (systems, public schema, tag-driven defaults).
Trashlands is the pack (curation, quests, tuning, cross-mod tables). The mod never *requires* Create or
Mekanism. `docs/pack_extension.md` lists what a pack can change without a mod release, and what it cannot.

## Invariants the build enforces

Read the rate or rule off the test, not off prose. A test named here is the source of truth.

| Rule | Enforced by |
| --- | --- |
| Nothing in `#recompile:found_only` is craftable, and each has a loot source | `FoundNotCraftedTests` (twin tests) |
| Every `SortableBlock` is in a vacuum band (bands fail closed) | `every_sortable_block_is_in_a_vacuum_band` |
| No two objects yield the same signature component (reads guaranteed output) | `no_two_objects_yield_the_same_signature_component` |
| Every Blueprint has a name, a recipe and a route (teardown teacher or market offer) | `every_shipped_blueprint_has_a_name_a_recipe_and_a_route` |
| The market never sells a found-only item or the knowledge to make one | `the_market_never_sells_what_is_meant_to_be_found` |
| Terminals come only from repairing a Broken Terminal | `TerminalRoutesTest` |
| No crafting recipe is shadowed by another matching the same grid | `every_crafting_recipe_is_reachable_at_a_bench` |
| No smelting recipe turns a mod item into iron (the iron gate is blasting) | `no_smelting_recipe_turns_a_mod_item_into_iron` |
| Powered multiblock machines share the Jade / network / feed contract | `MachineParityTests` |
| Every item and block has a name, client item def, blockstate, loot table, item form | `RegistryCompletenessTests` (exceptions: justified entries in `NO_LOOT_TABLE` / `NO_ITEM_FORM`, never a looser check) |
| Every mod item is in the creative tab (**membership only, not order**) | `every_mod_item_is_in_the_creative_tab` |
| The Scrap Hauler exists exactly once, as the Depot item XOR the entity | `ScrapHaulerTests` |
| Guidebook paragraphs break; lang keys and icons resolve; multiblock pages match `Multiblock.java` | `GuidebookTests`, `GuidebookMultiblockTests` |
| A file shipped at another mod's id is `ordering = "AFTER"` that mod (none ship since #420; the guard waits for the next) | `every_cross_mod_override_is_ordered_after_its_mod` |
| Slot geometry comes from the layout; synced values survive the 16-bit wire; screens use `VanillaGui` only | `MenuLayoutTests`, `MenuWireCeilingTest`, `GuiFrameworkDisciplineTest` |
| Drop rates (bucket, collectibles, and more) | `FindRateTest` |
| Stained Ground never retires (radioactive dump is non-reclaimable) | `the_yard_can_be_retired_and_the_dump_cannot` |
| Biome `effects` holds no key that moved to `attributes` | `BiomeEffectsPlacementTest` |

## Silent-failure traps

Everything here fails with **no error**. One line each; follow the pointer for the full story.

**Worldgen** (`docs/worldgen_notes.md`)
- A world preset applies only if selected at world creation. quickPlay and GUI-created worlds silently
  ignore `recompile:garbage`. Use `tools/make_dev_world.py`, or `runServer` with `level-type=recompile\:garbage`.
- The generator is baked into `level.dat`, so worldgen changes (and regrowth memory) reach **new worlds only**.
- A missing `minecraft:not` on the depths roof gradient fills the whole dimension with bedrock (`CompactedDepthsTests`).
- Biome `effects` holds only water/foliage/grass colours in 26.1. Fog, sky, particles and sounds moved to
  a top-level `attributes` map, and a stray key parses and does nothing.
- `#minecraft:dirt` is three blocks in 26.1. "Overworld ground" is `#minecraft:substrate_overworld`.
- Vanilla `sculk_patch` places nothing in solid fill. Use a `minecraft:ore` feature (block replacement).
- Mound Ground stays out of `#minecraft:dirt`, or encroachment eats the mound memory.
- A pile's regrowth bed is written into coarse dirt only (`RegrowingGroundBlock.isBedGround`). "Any solid block" buried sewer entrances (#432).
- The rung-1 soil spreader must convert coarse dirt **straight** to grass. A plain-dirt step lets vanilla spread finish the job for free.
- The regrowth column walk is deliberately blind to *which* pile block it finds. Do not "fix" it (`a_foreign_pile_block_does_not_stall_the_column`).

**Data, loot, recipes** (`docs/data_and_api_notes.md`)
- NeoForge re-ships 17 vanilla recipes (`minecraft:bucket`, the 16 `dye_*_carpet`). Our override at those
  ids is **never read** without `ordering = "AFTER"` on the neoforge dependency.
- `neoforge:conditions` works only at the top of a whole loot-table, recipe, advancement or `loot_modifiers`
  file. On a pool, entry or tag it is ignored.
- A `minecraft:loot_table` entry pointing at a condition-gated table keeps its weight and yields nothing.
  A mod-gated drop is an unconditional entry in its own pool plus a conditional strip (`StripItemModifier`).
- `neoforge:loot_table_id` DOES match a table rolled from Java (measured, #420); this line said the opposite for weeks. A pack adds to our tables with an aimed `add_table`, but an added roll rides along and cannot displace a weighted entry.
- An unresolvable **item** id kills a whole loot table at parse. A **tag** entry does not.
- The GLM dir is `loot_modifiers` (plural), and there is no `global_loot_modifiers.json` index in 26.1.
  Modonomicon's `multiblocks/` is plural too.
- A dev run reads `src/main/resources`, not `build/`. To prove a data feature works, neuter the Java, not the file.
- JEI and `SortingData` read bundled JSON, not the live registry. There is one condition evaluator (`ConditionEvaluationTest`).
- A recipe codec or constructor may not build an `ItemStack` ("Components not bound yet"). Throw
  `IllegalArgumentException`/`JsonParseException` so a bad file costs one row, not the world.
- A gate built from the *absence* of a material dies when anything adds it. Disable recipes explicitly (see the iron gate in `docs/machines_notes.md`).
- Disabling another mod's recipe means an override at its id guarded by `neoforge:never`, plus `ordering = "AFTER"` (`docs/cross_mod_stopgaps.md`).

**Blocks, multiblocks, machines** (`docs/systems_notes.md`, `docs/machines_notes.md`)
- A non-cube model without `noOcclusion()` culls its neighbour's face and punches a hole in the world.
- Disband recursion: in 26.1, `setBlock(AIR)` re-enters sibling break hooks, so `MultiblockCoreBlock.disband`
  unforms the core first. A disband test must break a **dummy** of a 2+ dummy machine and **count the core item**.
- Disband returns the blueprint's `cell.component()`. Only the hand-placed cells keep loot tables (`Multiblock.isHandPlaced`).
- A machine that drains must be in `#recompile:scrap_connectable`, or `ScrapNetwork.collect` returns nothing.
- The Burn Barrel is a furnace `WorldlyContainer`, so a route must never land in it.
- BlockEntity state survives break-and-replace only through a data component plus `copy_components` (`RainCollectorBlockEntity`).
- A custom fluid with `canHydrate` gives every plot in range permanent encroachment immunity.
- The Hauler is a `LivingEntity` that must not act like one. Effects ignore invulnerability, so it needs
  `MobCategory.MISC`, persistence and an instant `tickDeath`.

**GUI** (`docs/gui_notes.md`)
- A menu data slot is **16 bits** on the wire. Sync what the screen displays (`WideSync`), not the quantity behind it.
- `RCConfig` is COMMON and **not synced**. A client must never recompute a synced value from it.
- Nothing in `gui/` may import `net.minecraft.client`. A static `LAYOUT` that touches a registry-backed
  class breaks mod load. Chrome comes only from `client/gui/VanillaGui`.
- Screens are blind to both test layers. `python tools/shoot_screens.py` and `tools/shoot_guidebook.py` are the evidence.

**Tests**
- `makeMockServerPlayerInLevel()` is not survival. Call `setGameMode(GameType.SURVIVAL)` first.
- `GameTestHelper.destroyBlock` drops nothing. Use `helper.getLevel().destroyBlock(abs, true)`.
- `canSeeSky` is a light query answered on another thread. Use `succeedWhen`, never a fixed delay, and pair it with its opposite.
- Vanilla assets are on neither test classpath, so vanilla model parents are checked against `VANILLA_PARENTS`.
- With Ender IO in `run/mods`, about 58 mock-player tests fail on a payload. Filter those before reading red.

**Assets and rendering** (`docs/data_and_api_notes.md`)
- Block and item atlases are split, so a block model cannot use an `item/` texture.
- Every item needs `assets/<ns>/items/<id>.json`. `template_spawn_egg` is gone, so an egg needs its own PNG.
- Custom fluid textures are a `FluidModel` registered on `RegisterFluidModelsEvent`. Without one you get a pink pond.
- Audio: a stereo file, a `sounds.json` naming a missing file, and a loop seam all fail silently (`docs/dev_tooling.md`).

**Guidebook** (`docs/guidebook_spec.md`)
- A blank line does **not** break a paragraph, and a lone newline renders as a space. Use backslash line ends.
- A missing lang key renders raw, a missing icon renders the missing texture, and a plain string in a text field is a translation key.

**Driving the game** (`docs/dev_tooling.md`)
- devbridge is on port **8605**. Dial `localhost`, never `127.0.0.1`. Pass `--player @s`, or `@s` matches
  nothing. `ports check` cannot see devbridge's IPv6 socket.
- RCON closes after each command. `data get block` answers only for block entities, so probe with
  `execute if block`, and `forceload add` first.

## 26.1 API deltas

Most tutorials target 1.20/1.21. Full notes are in `docs/data_and_api_notes.md`.

- Event buses merged: `@EventBusSubscriber` takes no `bus`.
- `Identifier`, not `ResourceLocation`. `ResourceKey.identifier()`, not `.location()`.
- Data dirs are singular (`loot_table/`, `recipe/`, `structure/`, `tags/block/`, `worldgen/placed_feature/`).
- `DirectionProperty` is gone. Use `EnumProperty<Direction>` (`BlockStateProperties.HORIZONTAL_FACING`).
- `GameRules` moved to `net.minecraft.world.level.gamerules`, and every id is snake_case (`doTileDrops` is `block_drops`). This breaks datapack functions.
- `DyeItem` has no colour. Map through the registry id `<colour>_dye`; an unknown id resolves to AIR, not null.
- `pack.mcmeta` uses `min_format`/`max_format` (84).
- `Player.displayClientMessage` is gone: use `sendOverlayMessage` (action bar) or `sendSystemMessage`. No-arg `Item.getName()` is gone.
- `MobEffects.SLOWNESS`/`SPEED`. `Properties.noCollision()`. Food through `Item.Properties.food(...)`. Tools through `props.shovel(...)`.
- `CraftingMenu` is locked to `MenuType.CRAFTING` and `stillValid` hard-codes the vanilla table. Reimplement over `AbstractContainerMenu` (`ScrapCraftingStationMenu`).
- A custom bed needs `isBed` and `getRespawnPosition` overrides (`MattressBlock`).
- `BlockEvent.BreakEvent` is now `event.level.block.BreakBlockEvent`.
- BlockEntity serialization uses `ValueOutput`/`ValueInput`.
- Fluids and energy use the **transfer API**: `Capabilities.Fluid.BLOCK` returns `ResourceHandler<FluidResource>`,
  `EnergyHandler`, transactional insert/extract. `IFluidHandler`/`IEnergyStorage` snippets are wrong here.
- `IClientFluidTypeExtensions` lost its texture and tint getters. `FarmBlock` is `FarmlandBlock`, and `canHydrate` defaults to false.
- `noise_router` needs `preliminary_surface_level`, biome `carvers` is a flat list, and biome `features` is 11 arrays.
- Custom furnace: subclass `AbstractFurnace{Block,BlockEntity}` with vanilla `FurnaceMenu`. An empty `getSlotsForFace` makes it manual-only.
- Entity animation: `AnimationDefinition.bake(root)` then `KeyframeAnimation.apply`. `EntityModel.animate` is gone. `PathType` hazards are `FIRE`/`DAMAGING`.
- Registry renames alias through `IRegistryExtension.addAlias`. `MissingMappingsEvent` does not exist (`RCRegistryAliases`).
- GUI drawing is retained-mode (`GuiGraphicsExtractor`, `extractBackground`), and only `VanillaGui` touches it.

## Architecture map

| Subsystem | Entry point | Detail |
| --- | --- | --- |
| Registry spine | `Recompile.java`: `RCBlocks` -> `RCItems` -> `RCCreativeTabs` -> `RCFeatures`, then `RCRecipeTypes`, `RCGameTests`, config. Use the **factory form** `registerBlock(name, factory, props)`. | `docs/systems_notes.md` |
| Pick-through loop | `SortableBlock` (a `FallingBlock`; `sorted` is a blockstate flyweight, deliberately no BE). Derive members with `instanceof SortableBlock`. `BulkyWasteBlock` is not one. | `docs/systems_notes.md` |
| Finds and pull streams | `loot_table/gameplay/*`: one pull stream per `SortableBlock` (`pullTable()`), rolled from Java (grep `getRandomItems` for the roll sites). `blocks/bulky_waste.json` is a routing table, so add finds to `gameplay/bulky_spine.json` / `bulky_windfall.json`. | `docs/systems_notes.md`, `docs/data_and_api_notes.md` |
| Multiblocks | `content/block/multiblock/`. `Multiblock` is the single source of truth for validation, auto-assemble, guidebook and disband. | `docs/multiblock_system_spec.md` |
| Scrap Network | `ScrapNetwork` floods `#recompile:scrap_connectable`. Sinks: Freight Terminal (conditional), bins, Scrap Barrel. | `docs/scrap_network_spec.md` |
| Machines | Trommel, Separator, Pulverizer, Slag Furnace, Sintering Kiln, Sequencer: six verbs. | `docs/machines_notes.md` |
| Market and freight | Sell/Buy/Freight terminals, `recompile:market_offer`, `recompile:freight_phase` | `docs/market_spec.md`, `docs/freight_conversion_spec.md` |
| GUI framework | `gui/` (common `ScreenLayout`) + `client/gui/` (rendering) | `docs/gui_notes.md`, `docs/gui_framework_spec.md` |
| Worldgen | `world_preset/garbage.json`, `RegionBiomeSource` (distance gradient), compacted depths, `RegrowingGroundBlock`, `RCEncroachment` | `docs/worldgen_notes.md` |
| Garbage Vacuum / Scrap Hauler | first powered item / a Mob with the biology switched off | `docs/garbage_vacuum_spec.md`, `docs/scrap_hauler_spec.md` |
| Municipal Aquarium | `AquariumStructure` (layout exists once, as arithmetic) | `docs/municipal_aquarium_spec.md` |
| Collectibles | Display Pedestal is the mod's one BlockEntityRenderer (a scoped reversal) | `docs/collectibles_spec.md` |
| Public recipe types | `RCRecipeTypes` (derive the list there). The teardown schema is public API. | `docs/teardown_schema_spec.md`, `docs/machines_notes.md` |
| JEI / Jade | `compat/jei`, `compat/jade`, loaded only when the viewer is present. `MultiblockParts` hides uncraftable cells. | `docs/data_and_api_notes.md` |
| Guidebook | `data/recompile/modonomicon/` (Modonomicon, `runtimeOnly`) | `docs/guidebook_spec.md` |
| Config | `RCConfig` (COMMON). `RCDimensionLockout` holds the End; the Nether is open. Read its javadoc before assuming a gate holds. | `docs/systems_notes.md` |
| Cross-mod stopgaps | AE2's presses and Ender IO's blaze disable moved to the pack (#420). Still here: AE2's sourcing recipes (ruled engine content) and Ender IO's grains find (awaiting a ruling, trashlands#52). | `docs/cross_mod_stopgaps.md` |
| Textures, audio, devbridge | texgen, sfxgen, `tools/make_dev_world.py`, `tools/shoot_*.py` | `docs/dev_tooling.md` |

## Conventions

- **No em-dashes or en-dashes, no emoji** in any authored text. ASCII punctuation only.
- **Minimize authored prose.** Only quests and technical guidance get writing, because players distrust AI writing. Carry meaning through mechanics.
- **Data-driven first.** Tuning, drop rates and variants belong in JSON, not Java.
- Conventional commits (`feat(food):`, `fix(...)`, `docs:`). Phases land as squash-merged PRs.
- **Textures are generated, never hand-drawn** (texgen). No raw AI output lands in the repo. Jason running
  `select` IS approval; a `select` the assistant runs is not (`docs/dev_tooling.md`).
- **Keep machine GUIs minimal.** Any block that stores items or opens a machine screen is a design reversal,
  so check `design_decisions.md` first. Reuse a vanilla screen where one fits. A new custom screen needs a
  recorded reversal in `docs/gui_notes.md`. There are no recipe-book buttons in this mod's machines (owner 2026-08-19).
- **`RCCreativeTabs` order is public output**: JEI sorts by it, so it is what a player scrolls. Group by
  **kind**, and order by progression within a kind. Check it on every SCRUB, because only membership is
  tested. An out-of-group item with a comment explaining why is a decision, not drift. Formed multiblock
  cells are hidden from JEI by `MultiblockParts`, so do not "fix" them in the tab. Details are in `docs/systems_notes.md`.
- **Counts and lists in docs go stale, so derive them from code** rather than trusting a sentence. The
  sources: screens `client/*Screen.java`; recipe types `RCRecipeTypes`; Jade `compat/jade/`; JEI categories
  `RecompileJeiPlugin.registerCategories`; network members `tags/block/scrap_connectable.json`;
  sortables `instanceof SortableBlock`; cross-mod ordering `META-INF/neoforge.mods.toml`; tests
  `build/test-results`. If a doc must list something, update it in the same change that alters the set.
- The mod was working-named "Salvage" and was renamed because materials-recovery mods already own that name.

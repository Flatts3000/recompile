# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**What this is:** a standalone **NeoForge** mod (MC 26.1.2). Core mechanic: **teardown-as-knowledge** - disassemble items to recover their recipes, not just their materials. Also ships the garbage-world systems (worldgen, Blocks of Garbage, sorting, mound regrowth) that the **Trashlands** modpack is built on. Mod id / package: `recompile` / `com.flatts.recompile`.

**Status:** Phases 0-2 plus the P1.9 food tier are shipped. `docs/roadmap.md` is the engineering build order and tracks per-phase status; Phase 3 (teardown-as-knowledge, the distinct axis) is the next major system and is **not built yet** - only its data spine exists.

## Build and test

The system `JAVA_HOME` on this machine is stale and points at a nonexistent JDK 17, so **every** gradle invocation needs it overridden:

```bash
JAVA_HOME="/c/Program Files/Java/jdk-25" ./gradlew build
```

| Task | Command |
| --- | --- |
| Compile only (fast feedback) | `./gradlew compileJava` |
| Full build + jar | `./gradlew build` |
| In-world GameTests (the real test layer) | `./gradlew runGameTestServer` |
| Dev client (JEI + Jade included) | `./gradlew runClient` |
| Regenerate IntelliJ run configs after `clean` | `./gradlew prepareAllRuns` |

**Never pipe gradle to `tail`/`head` and trust the exit code** - the pipe reports the pager's status (0) and masks a Gradle failure. Redirect to a file and check `$?`, or use `PIPESTATUS`.

`runGameTestServer` boots a headless server, runs every registered test in a scripted plot, and exits non-zero on failure. It also loads all worldgen registries and hard-fails on any JSON parse error, so it is the fastest way to validate datapack changes without a GUI. It has no per-test filter - it runs the whole suite (seconds).

Its pass count includes a vanilla built-in test: the mod's own tests run in the `recompile:default` environment, and a `minecraft:default` batch of 1 runs alongside them. So the reported total is always ours **plus one** - don't read it as a count of this mod's tests.

CI (`.github/workflows/ci.yml`) runs `build` and `gameTest` as two independent jobs. The `build` job name is load-bearing: main's branch protection requires that status check.

`unitTest` is enabled in `build.gradle` (moddev's JUnit integration, which runs `src/test/java` against a loaded mod context), but **no JUnit tests exist yet** - `./gradlew test` currently does nothing. GameTests are where behaviour is proven.

## Architecture

**Registry spine.** `Recompile.java` (the `@Mod` entry point) wires `DeferredRegister` holders in a load-bearing order: `RCBlocks` -> `RCItems` (block-items reference blocks) -> `RCCreativeTabs` -> `RCFeatures`, then `RCRecipeTypes`, `RCGameTests`, and the config. Registries use the **factory form** (`registerBlock(name, factory, propsSupplier)` / `registerItem(name, factory)`) because 26.1 sets the `ResourceKey` on Properties before the constructor runs; the older `new Block(props)` form breaks.

**The pick-through loop is the mod's heart.** `SortableBlock` (abstract, extends `FallingBlock`) is shared by `GarbageBlock`, `TrashBagBlock`, and `CompactedBaleBlock`. Right-click a placed block to pull one drop from its loot table; progress persists in a blockstate `sorted` IntegerProperty - **a palette flyweight, deliberately not a BlockEntity**, because garbage is the mod's bulk block. Each variant supplies its own pull table, crumble window (`minPulls`/`maxPulls`, rising chance between), and required tool (null = bare hand). Gravity is config-gated in the overridden `tick`. `BulkyWasteBlock` is *not* one of these - it extends `FallingBlock` directly and has no pull stream. It mirrors the bale's tool gate instead: right-click with the prybar to pry it open (one action, drops the find, breaks the block), right-click without one to get the "you need a Prybar" nudge, and `requiresCorrectToolForDrops` so bare-hand mining yields nothing. The find is the block loot table, not a pull table.

**Finds come from Bulky Waste, and worldgen places exactly one block type.** A find doesn't become a mattress until it's an item in your hand, so there are no per-find models, no structure templates, and no entities - **adding a find is a line in `loot_table/blocks/bulky_waste.json`**, nothing more. Two rarity dials do different jobs: the block's 5% in `MoundFeature` sets how often the *"something's buried here"* beat fires (already playtested - don't retune it for a new find), and the table's weights set *which* thing it is. The standing invariant: **nothing enters the found economy without a teardown exit**, or the piles become clutter rather than unprocessed inventory.

**The mod has zero BlockEntities, and no machine GUI, on purpose.** The Sorting Tarp was deliberately rewritten to be stateless: right-click it holding garbage to sift drops into the world, no GUI, no internal inventory, no hopper automation. Any block that stores items or opens a machine screen is a design reversal - check `../trashlands/docs/design_decisions.md` before adding one. The single `Menu` (`ScrapCraftingMenu`) is not an exception to that: it is vanilla's own crafting menu with `stillValid` re-pointed, holding no state of its own.

**Loot: two distinct kinds.** `loot_table/blocks/*` are ordinary block drops. `loot_table/gameplay/{household_pulls,bag_pulls}.json` are **weighted pull streams rolled programmatically** from Java via `reloadableRegistries().getLootTable(key)` + `getRandomItems`, then dropped with `Block.popResource`. They declare `"type": "minecraft:chest"` (which gates loot-context param validation) despite never being a chest. Tuning drop rates means editing these two files, not Java.

**Worldgen chain**, all custom and all singular-dir:
`world_preset/garbage.json` (inlines the level stem; injected into the world-creation list by `data/minecraft/tags/worldgen/world_preset/normal.json`) -> `noise_settings/garbage.json` (a flat coarse-dirt slab, sea level -64, no aquifers/ores) -> `biome/household_sprawl.json` -> its `features` array -> `placed_feature/*` -> `configured_feature/*` -> a `Feature<NoneFeatureConfiguration>` registered in `RCFeatures` (`MoundFeature`, `MyceliumPatchFeature`).

`household_sprawl` has **all spawner lists empty by design** - the starting biome is creature-free, which is why food comes from tin cans and foraged mushrooms rather than mobs.

**The data spine.** `TeardownRecipe` registers the public `recompile:teardown` recipe type - JSON in `data/<ns>/recipe/`, with `results` (deterministic core), `extras` (weighted bonus), and `teaches` (recipes to study). It was registered from day one so the Phase 3 knowledge system is never retrofitted into a live schema. **Packs and addons extend the teardown tree through this schema without a mod release - treat it as public API.**

**Config.** `RCConfig` (COMMON). The governing principle is "everything ships config-gated, but defaults are the design" - config is for tuning, not for dodging a decision. `RCDimensionLockout` blocks Nether/End travel (and portal formation) until each themed dimension ships, keeping vanilla dimensions from leaking free resources into the closed trash economy.

**GameTests.** `RCGameTests.test(name, maxTicks, body)` hides 26.1's two-step registration (a `Consumer<GameTestHelper>` in `Registries.TEST_FUNCTION` plus a `FunctionGameTestInstance` carrying `TestData` at `RegisterGameTestsEvent`). Each domain class is just test bodies plus one `register()` line called from `RCGameTests.register`. All tests share the `empty_5x5x5` structure. Blocks expose a **static entry point** for tests to call directly (`SortableBlock.sortOnce`, `SortingTarpBlock.siftInput`) rather than simulating a player interaction.

## Textures (texgen)

Textures are **generated, never hand-drawn**, by the shared engine at `../mc-pack-toolkit/texgen/` (install: `pip install -e F:/minecraft-repos/mc-pack-toolkit/texgen`). This repo carries `texgen.toml`, which declares every surface (prompt, backend, variants/faces). Workflow: `texgen generate --surface X` -> `texgen sheet` -> `select X <batch>/<idx>` -> `promote` -> `validate`.

`texgen sheet` builds `gen/recompile_textures_review.html`, the page Jason reviews art in: pending surfaces on top with their `select` commands, approved ones at the bottom. **Re-run it after anything that changes a texture** - it is a build artifact, not a live view. Approval is explicit in `gen/approved.json`; drop a surface's id back out when its art changes so it returns to the pending queue.

**Hard rule: no raw AI output lands in the repo.** `gen/` and `art_src/` are gitignored; only the finalized 16px PNGs under `src/main/resources/assets/recompile/textures/` are committed. A texture change should show up in the diff as *only* the small PNG plus the manifest.

Two surfaces that must read as the same object in different states use `reference = "<other-surface>"` (e.g. `tin_can_open` -> `tin_can`), which derives one from the other's source art instead of generating it blind. Generating each state from its own prompt makes them drift into different objects.

`kind` picks both the output directory and whether the art keeps an alpha background. When those need to differ - a cross-model plant is a transparent sprite that must live in `textures/block/` - set `transparent = true` explicitly (see `dump_mushroom`).

Changing a surface's `kind` and re-running `promote` re-finalizes from `art_src/` with **no API call**, so moving art between atlases costs nothing and cannot drift.

Blocks with `variants = N` get randomized variants from the **blockstate JSON, not code**.

## 26.1 API deltas that bite

Most tutorials target 1.20/1.21 and will mislead you:

- **Event buses are merged.** `@EventBusSubscriber` takes no `bus` parameter; `EventBusSubscriber.Bus` is gone.
- `net.minecraft.resources.Identifier`, not `ResourceLocation`.
- **Data directories are singular**: `loot_table/`, `recipe/`, `structure/`, `tags/block/`, `tags/item/`, `worldgen/configured_feature/`, `worldgen/placed_feature/`.
- `pack.mcmeta` uses the `min_format`/`max_format` range form (both `84`), not scalar `pack_format`.
- `Player.displayClientMessage` is gone - use `player.sendSystemMessage(Component)`. `Item.getName()` (no-arg) is gone - use `Component.translatable(item.getDescriptionId())`.
- `MobEffects.SLOWNESS` / `SPEED` (renamed from `MOVEMENT_*`). `Properties.noCollision()` (one `s`; also clears occlusion).
- Food is data-component driven: `Item.Properties.food(FoodProperties)`. `SuspiciousStewItem` / `MushroomStewItem` no longer exist.
- Tools come from `Item.Properties` helpers: `props.shovel(ToolMaterial.STONE, dmg, speed)`.
- **Item rendering needs `assets/<ns>/items/<id>.json`** client item definitions *in addition to* `models/item/`.
- **Atlases are split and a model can only use its own.** `atlases/blocks.json` stitches `textures/block/**`; `atlases/items.json` stitches `textures/item/**`. A *block* model referencing `<ns>:item/foo` renders as the pink/black missing texture even though the PNG exists. A sprite that feeds both (a cross-model plant and its icon) lives in `textures/block/` and the item model points there - vanilla does exactly this for `brown_mushroom`.
- **`CraftingMenu.stillValid` hard-codes `Blocks.CRAFTING_TABLE`.** A custom crafting station must subclass it and override `stillValid`, or the menu opens and closes on the first server tick, which looks exactly like "right-click does nothing" (see `ScrapCraftingMenu`). Expect the same pattern in other vanilla menus.
- **A custom bed needs two NeoForge overrides, and both fail silently.** Sleeping does *not* require `instanceof BedBlock`, but `IBlockExtension.isBed` must return true (else patched `LivingEntity.checkBedExists()` ejects the sleeper next tick) and `IBlockExtension.getRespawnPosition` must be overridden (its default `Optional.empty()` is exactly vanilla's "no respawn block available"). `HorizontalDirectionalBlock.FACING` is read unconditionally, and you must call `startSleepInBed` yourself. See `MattressBlock`. `BlockTags.BEDS` is *not* the answer - it gates only villager and cat AI.
- **`Player.displayClientMessage` doesn't exist**; use `sendOverlayMessage` for the action bar (what vanilla's bed uses) or `sendSystemMessage` for chat.
- **`GameTestHelper.destroyBlock` passes `dropBlock=false`** - no loot table runs. A test asserting a block's drops must call `helper.getLevel().destroyBlock(abs, true)` or it asserts nothing.
- BlockEntity serialization uses `ValueOutput`/`ValueInput`, not `CompoundTag` (not currently used here - the mod has no BlockEntities).
- Custom worldgen: `noise_router` requires `preliminary_surface_level` (16 fields, not 15); biome `carvers` is a flat list; biome `features` is 11 arrays (index 9 = vegetal_decoration). A world preset must be **selected** at world creation via the World Type button - a default world silently ignores it, the #1 cause of "worldgen isn't working."

## Design lives in the Trashlands repo (source of truth)

Feature design is decided there, not here. Read before changing gameplay:

- `../trashlands/docs/design_decisions.md` - per-feature locked decisions + session bookmark. **Start here.**
- `../trashlands/docs/concept.md` - the vision (plus cross-medium influences: Planet Crafter, WALL-E).
- `../trashlands/docs/material_economy.md` - metals/gems/mod-spine sourcing.
- `../trashlands/docs/the_twist.md` - **FULL SPOILERS** (the hidden narrative hook). Never restate its contents in player-facing copy, and never hint at it in any other doc.

**Engine / pack split:** Recompile is the engine (systems, the public schema, tag-driven defaults). Trashlands is the pack (curation, quests, tuning, most cross-mod teardown tables). The mod never *requires* Create or Mekanism; it ships standalone config fallbacks.

## Conventions

- **No em-dashes or en-dashes, no emoji** in any authored text (hard rule). ASCII punctuation only.
- **Minimize authored prose.** Only quests and technical guidance get writing; players distrust AI writing. Carry meaning through mechanics.
- Data-driven first: tuning, drop rates, and variants belong in JSON, not Java.
- Conventional commits (`feat(food):`, `fix(...)`, `docs:`). Phases land as squash-merged PRs.
- The mod was working-named "Salvage" during design; renamed because several materials-recovery mods already own that name on CurseForge/Modrinth - exactly the mods this would be confused with.

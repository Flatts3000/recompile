# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**What this is:** a standalone **NeoForge** mod (MC 26.1.2). Core mechanic: **teardown-as-knowledge** - disassemble items to recover their recipes, not just their materials. Also ships the garbage-world systems (worldgen, Blocks of Garbage, sorting, mound regrowth) that the **Trashlands** modpack is built on. Mod id / package: `recompile` / `com.flatts.recompile`.

**Status:** Phases 0 through 2.17 are shipped (through the full reclamation ladder - Grass, Vegetation, Farming, Trees, Animals), **Phase 3 (teardown-as-knowledge) shipped 2026-08-02**, **Phase 4's region system and its first frontier region (the demolition yard) are shipped**, **Phase 5 (mound regrowth) shipped 2026-08-05**, and **Phase 7's themed Nether was pulled forward and shipped 2026-08-19** (the compacted depths, plus the slag chain that gets you there: the Cupola's byproduct, the Slag Furnace, and the only obsidian in the game). **v0.14.0 is released** (2026-08-21, Ancient Sculk plus the two cross-mod stopgaps and the recipe-collision fix; v0.13.0 2026-08-20 the Sintering Kiln and the last of the resource gaps - brewing, netherite and emeralds; v0.12.0 2026-08-19 the compacted depths, v0.11.0 on 2026-08-18 the sewers, v0.10.0 on 2026-08-17, v0.9.0 on 2026-08-12, v0.8.0 on 2026-08-11, v0.7.0 on 2026-08-05, v0.6.0 on 2026-08-04, v0.5.0 on 2026-08-02, v0.4.0 on 2026-08-01, v0.3.0 on 2026-07-30, v0.1.0 and v0.2.0 on 2026-07-27). The grey-to-living arc the ModJam entry is built around now plays end to end, and mounds are renewable quarries rather than a finite stock. `docs/roadmap.md` is the engineering build order and tracks per-phase status; **Phase 6 (the full loop) is what remains**. Phase 3's long-open **knowledge-vs-function question was decided on 2026-08-01: knowledge**, as Immersive-Engineering-style **Blueprint items** (spec `docs/blueprints_spec.md`, issue #95). Tear something down at the Workbench and you may come away with an **Idea Fragment**; enough fragments about one thing craft into a **Blueprint**; a **Filing Cabinet** found in Bulky Waste files them and joins the Scrap Network by placement; and the **Scrap Crafting Table** will run a `recompile:blueprint_crafting` recipe only while the sheet is in the player's inventory or in a cabinet in the same cluster. A vanilla crafting table needs no code to be excluded - blueprint recipes are not of type `minecraft:crafting`, so it cannot see them at all.

**The proof of concept is the bed, and it is now the only bed in the game.** All sixteen wool-to-bed recipes are deleted; a Clean Mattress (blueprint-only, three wool and three string) plus three planks is the sole route, and dyeing the mattress at an ordinary table picks the colour. **The teardown schema's `teaches` field, parsed and ignored since Phase 0, is finally read** - which immediately turned the schema's own example recipe into live content, because it carried a `teaches` pointing at a blueprint that does not exist.

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

`unitTest` is enabled in `build.gradle` (moddev's JUnit integration, which runs `src/test/java` against a loaded mod context) and **`./gradlew test` runs 81 tests across 19 classes**. `build` depends on `test`, so CI gates them. *(This line previously said no JUnit tests existed. That was wrong from PR #22 onward and went unnoticed until someone counted - a doc claiming a layer is empty is how it stays empty.)* **Use a unit test when the logic is pure** - `GeneratorState` (which reason a generator is idle), `ScrapBinContent` (item to bin appearance), the crumble curve's expected yield. No world, no rendering, no server means a GameTest is the wrong instrument and a slower one. GameTests remain where in-world behaviour is proven.

## Architecture

**Registry spine.** `Recompile.java` (the `@Mod` entry point) wires `DeferredRegister` holders in a load-bearing order: `RCBlocks` -> `RCItems` (block-items reference blocks) -> `RCCreativeTabs` -> `RCFeatures`, then `RCRecipeTypes`, `RCGameTests`, and the config. Registries use the **factory form** (`registerBlock(name, factory, propsSupplier)` / `registerItem(name, factory)`) because 26.1 sets the `ResourceKey` on Properties before the constructor runs; the older `new Block(props)` form breaks.

**The pick-through loop is the mod's heart.** `SortableBlock` (abstract, extends `FallingBlock`) is shared by `GarbageBlock`, `TrashBagBlock`, and `CompactedBaleBlock`. Right-click a placed block to pull one drop from its loot table; progress persists in a blockstate `sorted` IntegerProperty - **a palette flyweight, deliberately not a BlockEntity**, because garbage is the mod's bulk block. Each variant supplies its own pull table, crumble window (`minPulls`/`maxPulls`, rising chance between), and required tool (null = bare hand). Gravity is config-gated in the overridden `tick`. `BulkyWasteBlock` is *not* one of these - it extends `FallingBlock` directly and has no pull stream. It mirrors the bale's tool gate instead: right-click with the prybar to pry it open (one action, drops the find, breaks the block), right-click without one to get the "you need a Prybar" nudge, and `requiresCorrectToolForDrops` so bare-hand mining yields nothing. The find is the block loot table, not a pull table.

**Collectibles are pieces-in, cube-out, and drive the mod's one BlockEntityRenderer** (design I-2, spec `docs/collectibles_spec.md`). An artifact from the past (v1: the **Puzzle Cube**) is assembled from thematic **pieces** found rare in the pull streams: nine `puzzle_cube_piece` fill the 3x3 crafting grid into the cube. **The Puzzle Cube is a placeable full block, not an item trophy** - two states, `puzzle_cube` (solved) and `puzzle_cube_scrambled`, that craft into each other with shapeless one-in/one-out recipes. It is a `minecraft:block/cube` with **six per-face 3x3-sticker textures**, so it renders as a genuine 3D cube in hand, inventory, world, and on a pedestal - which is what finally made it read as a cube (a real block model *is* one; every 2D-icon and downsampled-3D attempt was fuzzy or read flat). **The cube's faces, the piece's cubie faces, and the pedestal stone are all procedural** (texgen `sticker_face` / `single_sticker` / `plinth` styles) - AI and downsampling both failed a twisty cube at 16px; fixed geometry draws crisper as code. The piece is likewise a small **3D cubie model**, not a flat icon. Adding a collectible is data (piece item + cube block + recipe + loot + face textures); no code.

**Beyond the assembled cube, collectibles can be ported from open-source CC0 3D models** via the **voxel-porter** (`../mc-pack-toolkit/voxel-porter`, a pip package like texgen): it voxelizes a mesh or `.vox` to the 16px grid, samples per-voxel colour from the texture, greedy-meshes + face-culls, and emits the block model + a generated palette texture + every data file (`voxel-porter emit <model> <id> <res_root>`; you still register the block + lang + tab + one loot line). v1 ports four CC0 objects - **avocado** (Khronos glTF sample), **present**, **gold_coin**, **toy_car** (Kenney kits) - each **found whole**, not pieced, in a dedicated ~1/4000 pool in `household_pulls`/`bag_pulls` (a few times rarer than a cube piece). The Puzzle Cube is the *one* artifact that earns an assembly step, because a puzzle is literally assembled; whole objects (a coin, an avocado) just drop intact. What ports well is a **simple, iconic, colourful** object whose identity survives 16px; detailed/grey/complex models mush at block scale. An earlier hand-authored **era-artifact** set (obelisk/column/chalice/hourglass) read as museum decor and was dropped (2026-07-26) for ported real objects.

Anything (collectibles the star use) displays on the **Display Pedestal** (`content/block/DisplayPedestalBlock`), a **ProjectE-style tiered plinth** that holds one item (a BE, no capability, never hopper-fed, drops the item on any removal, crafts from 6 Pressed Junk + 1 Rebar) and floats + spins it above the cap. It takes **any item**, not a tag-gated trophy stand. **That live display is a scoped, recorded reversal of P1.11.6's "baked model, no BlockEntityRenderer" rule** - written for dump-scale finds (thousands in view), and a handful of stands is nowhere near that. The BER (`client/DisplayPedestalRenderer`) mirrors vanilla `CampfireRenderer`'s retained-mode pattern (resolve the item into an `ItemStackRenderState` in `extractRenderState`, position + `submit` it), registered client-only via `client/RecompileClientEvents` (`@EventBusSubscriber(value = Dist.CLIENT)`). Because it renders the item's **model**, a placed block-item shows as a real 3D object up there. **Every other block still bakes its model** - this is the one exception, scoped to the pedestal.

**Finds come from Bulky Waste, and worldgen places exactly one block type.** A find doesn't become a mattress until it's an item in your hand, so there are no per-find models, no structure templates, and no entities - **adding a find is a line in `loot_table/blocks/bulky_waste.json`**, nothing more. Two rarity dials do different jobs: the block's 5% in `MoundFeature` sets how often the *"something's buried here"* beat fires (already playtested - don't retune it for a new find), and the table's weights set *which* thing it is. **The old "nothing enters the found economy without a teardown exit" invariant is retired** (owner, 2026-08-01). It said a pile is only unprocessed inventory if every find has an exit, and it had already stopped being true: the collectibles (avocado, present, gold coin, toy car, Puzzle Cube) are found and displayed with no teardown exit, and the mod ships six teardown recipes in total. A find may now exist to be **displayed** rather than processed. What still holds is the reason behind it - a find that is neither useful nor wanted is clutter - so a new find needs *a* point, just not necessarily a teardown one.

**The mod keeps machine GUIs to a minimum, on purpose.** The Sorting Tarp was deliberately rewritten to be stateless: right-click it holding garbage to sift drops into the world, no GUI, no internal inventory, no hopper automation. Any block that stores items or opens a machine screen is a design reversal - check `../trashlands/docs/design_decisions.md` before adding one. Most containers reuse a vanilla screen: the Scrap Barrel reuses `ChestMenu`, and the Burn Barrel (a furnace-variant) reuses `FurnaceMenu` - neither mints a bespoke screen. **There are eight custom screens, and each is a recorded exception** - the count was wrong here for months, claiming one while the Tree Nursery quietly shipped a second. They are the **Scrap Crafting Table** (`ScrapCraftingStationScreen`, P2.10 flow 4), the **Tree Nursery** (`TreeNurseryScreen`, its species picker), the **Burner Generator** (`BurnerGeneratorScreen`, #72, its power meter), and the **Hydroponics Bay** (`HydroponicsBayScreen`, #43, water and power gauges together). The fourth is the first where a genuine alternative was on the table and lost on its merits rather than on feasibility: a chest screen for the two slots plus Jade for the gauges would have obeyed this rule without reversing it, since Jade already reports the nursery's water and the generators' FE. The owner called for a real GUI (2026-08-02) because the bay is the only machine consuming water AND power at once, so "why is it not running" has two answers and three resources on hover is worse than three gauges in front of you. The **fifth** is the Cupola Furnace (#236, owner 2026-08-18), and it widens that pattern by one word: vanilla's `AbstractFurnaceMenu` calls `checkContainerSize(container, 3)` in its constructor, so a furnace with a **second output slot** cannot subclass it any more than the Scrap Crafting Table could subclass `CraftingMenu`. Every vanilla cooking screen has one output because every vanilla cooking recipe has one result; a machine that hands back a byproduct is outside what they were built to show. **It cost two things, and both are now settled** (#240). Leaving `BlastFurnaceMenu` gave up vanilla's **recipe book** and **JEI's furnace transfer button**; neither was a defect of the slot, both were integrations the borrowed menu supplied for free. The JEI half was rebuilt (#244) - a transfer handler is twenty lines and its absence reads as "this recipe is uncraftable" rather than "this button is missing". **The recipe book is not coming back, and that is a standing decision rather than a backlog item** (owner, 2026-08-19): *no recipe-book buttons in this mod's machines.* So do not reimplement `RecipeBookMenu` for the Cupola, and do not reach for vanilla's `AbstractFurnaceScreen` on a machine merely because its menu would fit - the book comes with it. It also explains a thing that would otherwise look like an oversight: `VitrifyingRecipe.isSpecial()` returns true, keeping obsidian out of every recipe book, and with no book of our own to serve there is nothing lost by that. The pattern that has actually emerged: **containers reuse a vanilla screen; producers with a gauge, a picker or an output vanilla has no shape for cannot.** Energy bars, tank gauges and pickers have no vanilla equivalent to borrow. The rule that still binds is that each one is a deliberate call written down, not that there is only ever one. The first exception's reasoning: its connected-storage panel could not be done by reusing vanilla's crafting screen - and could not even be a `CraftingMenu` subclass, because vanilla `CraftingMenu`'s constructor hard-locks itself to `MenuType.CRAFTING`, so `ScrapCraftingStationMenu` reimplements crafting over `AbstractContainerMenu` with a custom `MenuType` (`RCMenus`) + screen. Adopting the proven Tinkers/Crafting-Station pattern justified the one exception; it is scoped to this block. The **sixth** is the Slag Furnace (#236): three slots, so `AbstractFurnaceMenu`'s `checkContainerSize` is satisfied and it **subclasses** the vanilla menu, inheriting the slots, `quickMoveStack` and the progress data sync that the Cupola had to reimplement over a bare `AbstractContainerMenu`. It owns a `MenuType` and a screen class only because a `MenuType` is what binds a screen to a menu, and vanilla's `FurnaceScreen` is typed to `FurnaceMenu`. That makes it the thinnest of the six by a distance - no gauge, no picker, no extra slot. **It does NOT get the recipe book or JEI's transfer button back**, which this paragraph claimed until review checked: both belong to the SCREEN rather than the menu (vanilla's furnace screens build their own recipe-book component; ours extends `AbstractContainerScreen`), and JEI's furnace transfer handler keys on vanilla's menu classes rather than subclasses of them. #240's account of what the Cupola gave up against a *vanilla* furnace stands - what was wrong was reading that as something this machine recovered. A menu subclass saves reimplementing a menu, and that is the whole of it. The **seventh** is the Sintering Kiln (#248), and it is the thinnest of the lot for the same reason the sixth was: three slots satisfy `checkContainerSize`, so it subclasses `AbstractFurnaceMenu` and inherits the slots, `quickMoveStack` and the progress sync. It owns a `MenuType` and a screen class only because a `MenuType` is what binds a screen to a menu and vanilla's `FurnaceScreen` is typed to `FurnaceMenu`. It gets no recipe book and no JEI transfer button by inheritance either - both belong to the SCREEN - and the book is deliberate rather than a gap. The **eighth** is the Sequencer (#294), and it is the first of these that is neither a furnace nor a multiblock: a powered single block with two slots, a power gauge and a progress arrow. It adds a case to the Burner Generator's exception rather than opening a new kind of one - a machine burning FE needs an energy bar and no vanilla screen has one. **This count has now been wrong three times** (once claiming one while the Tree Nursery had shipped a second, and again here, found by a SCRUB rather than by the person adding the screen), which is the argument for reading the `client/` directory rather than this sentence. The rule still holds everywhere else: **no new custom machine screen** without recording a reversal; reuse a vanilla screen when you can.

**All eight run on the GUI framework** (`com.flatts.recompile.gui` + `com.flatts.recompile.client.gui`, spec `docs/gui_framework_spec.md`, #164). **A machine declares its screen once, as a `ScreenLayout`, and the menu places slots from that declaration rather than from numbers of its own.** That is the whole point: slot coordinates used to be baked into `Slot` objects in the menu while the drawing that had to line up with them lived in a client-only class, with nothing connecting the two - the Burner Generator shipped with its FE readout drawn straight through its own fuel row, and the Tree Nursery's screen declared `FERT_X = 44` while its menu independently passed `44` to a `Slot`. Four rules that bite:

- **The declaration is common code and rendering is a client-side visitor over it.** A menu is constructed on a dedicated server, so nothing in `gui/` may import `net.minecraft.client`. This is a deliberate choice, not the only option - **owo-lib solves the same problem the other way**, letting a client layout move slots after the menu has placed them (sound, since a click sends a slot *index*, so coordinates are purely visual). We keep the truth server-side because that is what lets `MenuLayoutTests` measure it; adopting owo's shape would leave those tests measuring numbers the client overwrites. owo has a live `26.1-Neo` branch on 26.1.2 and is MIT, so this was a real decision - it also needs mixins, which this mod has none of, and would be a hard runtime dependency.
- **`imageWidth`/`imageHeight` are final and pass through a 5-arg `super(...)`, so a layout must be computable before the screen object exists.** Hence a pure declaration in a `static final LAYOUT`, never something accumulated while rendering.
- **A static `LAYOUT` that transitively touches a registry-backed class cannot be named from another class's static initialiser during mod construction.** `MenuLayoutTests` referencing `TreeNurseryMenu.LAYOUT` eagerly pulled in `TreeNurseryBlockEntity`, whose static `FluidResource.of(Fluids.WATER)` throws *"Components not bound yet"* - and the whole mod fails to load with a bare `ExceptionInInitializerError`. Hold suppliers, not layouts.
- **26.1 renders through a retained-mode "extract" model, and exactly one class still knows it.** `GuiGraphicsExtractor`, drawing in `extractBackground(...)` (not `renderBg`), `blit` with a `RenderPipelines` pipeline + explicit atlas dims. That lives in `client/gui/VanillaGui`, which is also the only place a screen's chrome comes from; `GuiFrameworkDisciplineTest` fails the build if a screen mentions a pipeline, a blit, or even `leftPos`. Before it, three screens carried a private `panel()`/`slot()`/`recess()` that approximated vanilla rather than borrowing it, so the mod shipped two panels that did not look alike.

**Screens are the one layer GameTest and JUnit are blind to.** Geometry is asserted server-side and the layout algebra has unit tests, but a gauge filled from the wrong end passes both. `python tools/shoot_screens.py` opens all eight in a running `runClient` and screenshots them - that is the acceptance evidence. **The guidebook was in the same blind spot and is now covered too**, by `python tools/shoot_guidebook.py`, which walks all 11 categories and all 69 entries and fails if any of them does not open as ITSELF (#259). That gap is how #241 went unnoticed: every paragraph break in all 71 of the book's text pages was swallowed, so paragraphs ran together, and it shipped that way for releases while `GuidebookTests` - which proves a lang key exists and an icon resolves - passed throughout.

**`shoot_guidebook.py` needs devbridge's `use` verb; `shoot_screens.py` still does not use it.** `click` drives a screen that is already up and refuses when none is, so before `use` the only way in was synthesizing an OS-level mouse event through PowerShell - which is what `shoot_screens.py` still does, and why **it still needs the game window foregrounded** and silently does nothing when it is not. Porting it to `use` would remove that requirement and has not been done. `use` is a real right-click: it mirrors vanilla's own ordering (entity, then block, then the item in the air), waits for the screen because a container's arrives a tick later on a packet, and names what was in hand so an empty hotbar slot is distinguishable from an item that opened nothing. That last distinction matters here more than anywhere: holding nothing used to report exactly the same "no screen opened" as a page that failed to draw.

**Entry nodes are not widgets.** Category buttons are, and `screen` reports a click point for each, so the rail is never computed. Modonomicon draws the entry nodes itself, so their positions come from the book data - every entry JSON carries a grid `x`/`y` on a fixed lattice - and `shoot_guidebook.py` requires each predicted click to actually open an entry, so a stale calibration fails loudly rather than photographing empty parchment.

**Finished goods are found, not crafted** (design P2.11, issue #161). *The dump gives you objects; your machines give you materials.* The test is **would a person throw this away?** A bucket, a rug, a bottle: yes. An iron ingot, a stone block, a plank: no - nobody discards stock. Building blocks are materials and stay craftable, which is consistent rather than an exception: the stone came from shards, so the dump already gave it to you. Owner ruling 2026-08-08 on the case that decided it: **players should find buckets, not craft them.**

**The rule is about finished goods, and components are carved out of it** (owner, 2026-08-18, #228). **A component needs to be craftable even if it also comes out of the garbage.** Playtest found the Bulb blueprint-gated at the Scrap Crafting Table while being weight 6 in `household_pulls`, and read the two as contradicting each other - reasonably, because a dead light bulb passes the *would a person throw this away* test easily. It does not contradict: a component is an **input**, and an input you cannot manufacture is a rate limit on everything downstream of it. So the Bulb keeps both routes and stays out of `#recompile:found_only`, and `FoundNotCraftedTests` is right never to have applied to it. The tag and its twin tests continue to bind on finished goods - buckets, bowls, rugs, bottles - and only those.

Membership is the `#recompile:found_only` item tag (data, so a pack extends it without a mod release) and enforcement is `FoundNotCraftedTests`, which walks every loaded recipe and fails if anything in the tag can be crafted - plus its twin, which fails if anything in the tag has no loot table at all. Both halves matter: disabling a recipe without adding a source does not make an item found, it makes it unobtainable, and the symptom is a player who simply never sees one.

**The bucket is the only way to move water in a standalone install, so its drop weight gates the whole green tier.** The Rain Collector exposes its tank as a capability but pushes to nobody, and the Pump and Copper Pipe are inert by design (P2.3, *Recompile converts, Create moves*) - so with no fluid-transport mod installed, a bucket is the sole route from a Rain Collector into a Tree Nursery or a Hydroponics Bay. That makes it a **required tool rather than a trinket**, which is why it sits at weight 12 in `household_pulls` (about 1 in 52 pulls) instead of alongside the conveniences like the bowl at 5. Final number belongs to the balance pass (#36); the reason it cannot be small belongs here.

**Scarcity is not enforcement, and this is the second time that lesson has been paid for.** The original plan leaned on vanilla's glass bottle being unobtainable because the world has no `minecraft:glass`. That was measured and is false: stone shards craft `minecraft:stone`, which is in `#minecraft:stone_crafting_materials`, which crafts a **vanilla furnace**, which smelts the sand Reinforced Concrete drops. Same shape as the iron gate's first design (#91) - a gate built from the absence of a material dies the moment anything adds the material, and neither failure announces itself. Recipes are disabled explicitly.

**Loot: two distinct kinds.** `loot_table/blocks/*` are ordinary block drops. `loot_table/gameplay/{household_pulls,bag_pulls}.json` are **weighted pull streams rolled programmatically** from Java via `reloadableRegistries().getLootTable(key)` + `getRandomItems`, then dropped with `Block.popResource`. They declare `"type": "minecraft:chest"` (which gates loot-context param validation) despite never being a chest. Tuning drop rates means editing these two files, not Java.

**Worldgen chain**, all custom and all singular-dir:
`world_preset/garbage.json` (inlines the level stem; injected into the world-creation list by `data/minecraft/tags/worldgen/world_preset/normal.json`) -> `noise_settings/garbage.json` (a flat coarse-dirt slab on ~60 blocks of deepslate, bedrock on the underside, void below that; sea level -64, no aquifers/ores - the rock is deep because the sewers need somewhere to be, and `the_world_has_rock_enough_to_hold_a_sewer` measures it against vanilla's own mineshaft descent rather than against the gradient, so retuning terrain cannot quietly take the room back) -> `biome/household_sprawl.json` -> its `features` array -> `placed_feature/*` -> `configured_feature/*` -> a `Feature<NoneFeatureConfiguration>` registered in `RCFeatures` (`MoundFeature`, `MyceliumPatchFeature`).

`household_sprawl` has **all spawner lists empty by design** - the starting biome is creature-free, which is why food comes from tin cans and foraged mushrooms rather than mobs.

**The same preset defines the Nether, and it is the compacted depths** (P3.5, owner 2026-08-19). `noise_settings/compacted_depths.json` is vanilla's nether shape - min_y 0, height 128, bedrock shell both ends - with `final_density` a **constant 1** and `default_block` set to `recompile:techno_organic_waste`. Solid every column, floor to ceiling: *the overworld is a dump you clear, the Nether is a dump you mine.* The only voids are embedded structures, and vanilla fortresses and bastions generate because both are **biome-tag driven** - `#minecraft:has_structure/nether_fortress` and `.../bastion_remnant` - so a themed biome hosts them with a two-line data change and hosts nothing at all without it. Slag rubble and lava arrive as `minecraft:ore` features, which is block REPLACEMENT rather than an ore, so neither needed Java. **No ancient debris in worldgen** (owner ruling).

**Ancient Sculk is the only deep dark in the game** (#266, v0.14.0). There is no deep dark biome and no
ancient city, so nine vanilla items had no source at all. One `minecraft:ore` feature - size 6, count 8,
about 1 in 680 - lays seams of `recompile:ancient_sculk` through the fill, and the block breaks into
Sculk Powder that crafts the family (sculk, veins, a sensor around redstone, a shrieker around soul
sand, a catalyst around an **echo shard**, which is one per sewer and the only other thing here that
came out of the deep dark). Two things worth keeping:

- **Vanilla's `sculk_patch` feature is unusable here.** It decorates an exposed surface and needs air
  beside it; the depths are solid floor to ceiling, so it would place nothing, log nothing and throw
  nothing. An ore feature is block REPLACEMENT, which is the only shape that works in solid fill.
- **It is the first block in this mod to gate on tool TIER rather than tool TYPE**, and it does it with
  no Java: `#recompile:mineable/sledgehammer` for the type plus `#minecraft:needs_diamond_tool` for the
  tier. That works only because `RCItems.COPPER_TIER` is built on `INCORRECT_FOR_STONE_TOOL` - retier
  the copper sledgehammer and this silently opens.

Three things bite here:

- **A missing `minecraft:not` on the roof gradient fills the whole dimension with bedrock, and nothing errors.** Vanilla's nether wraps its bedrock-roof `vertical_gradient` in `minecraft:not`; without it the condition is true for every block below top-5, so the surface rule paints the entire column. It parses, it generates, every test passes, and the density function is innocent the whole time. This shipped once and was only found by walking in, which is why `CompactedDepthsTests` asserts the fill and the shell separately.
- **The generator is baked into `level.dat` at world creation.** An existing save keeps whatever Nether it was made with, so changing these files only affects NEW worlds - the same shape as the Mound Ground note below. Testing a worldgen change therefore needs a fresh world, and the dev client's `--quickPlaySingleplayer` world is **not** one: quickPlay creates a DEFAULT world that silently ignores the preset. Use `./gradlew runServer` with `level-type=recompile\:garbage` in `run/server.properties` and probe it over RCON.
- **RCON here closes the connection after each command**, so a probe loop has to reconnect per call rather than hold one session. And `data get block` only answers for block ENTITIES: on ordinary terrain it reports "not a block entity", which is easy to misread as the chunk being absent. `execute if block <pos> <id> run <anything>` is the probe that actually works.

**Mounds regrow, and the memory is a block** (`MoundGroundBlock`, design P1.6, Phase 5). `MoundFeature` writes **Mound Ground** (`recompile:mound_ground`) under every footprint cell carrying **how many blocks belong on that column** - so the exact footprint and profile survive with no `SavedData`, no worldgen-thread concurrency and no region tracking, the same palette-flyweight idiom as `SortableBlock`'s `sorted`. It random-ticks: short column, and one Block of Garbage is spawned above and falls in, so replenishing mounds are visible across the plain. Four things bite:

- **It is coarse dirt with a different name and a darker face** (owner, 2026-08-05), and everything follows: coarse dirt's hardness, sound, shovel, and no tool gate. Its texture is a *retint* of vanilla coarse dirt calibrated to mean luma 66 against coarse dirt's 90.4 - the same material, unmistakably darker ground. It is deliberately **out of `#minecraft:dirt`**, because membership would reach `#encroachable` through `#substrate_overworld` and the junkyard would eat its own memory.
- **`HEIGHT` is a COUNT, and 0 means inert.** The feature fills `dy = 0..column` *inclusive*, so a rim cell of column 0 still carries one block; storing the top offset builds every mound one block short and leaves 0 ambiguous. As a count, 0 can only mean "nobody remembers a mound here", which is what makes a hand-placed block inert rather than the seed of a mound that never existed.
- **Overlapping mounds must take the taller.** `MoundFeature` only writes into air, so mounds interleave - and a later mound's rim (column 0) would otherwise overwrite a tall neighbour's memory and permanently flatten what regrows there.
- **Retirement is the block being gone.** Rung 1 greens it (`#recompile:spreadable`) and the memory goes with it. Encroachment reverts grass to *plain* coarse dirt and never to Mound Ground (P1.7-R item 5), so only the green is contested and mound retirement is permanent.

**Worldgen writes it, so only new worlds regrow.** A save made before this shipped has no Mound Ground and its mounds stay finite; accepted (owner).

**Encroachment: the junkyard fights back, and it needs no saved state** (`RCEncroachment`, design P1.7-R). Healed grass bordering unhealed ground reverts to coarse dirt; the reclamation ladder is the defence (bare grass reverts, cover is stripped *instead*, logs/leaves make it permanent). Three facts make the whole system cheap and are worth keeping in mind:

- **Coarse dirt is the universal world surface** (the `noise_settings` surface rule), so every healed patch is by definition ringed by unhealed ground. The frontier test is a local neighbour check - no mound memory, no `SavedData`, no region tracking.
- **That same fact is why nothing renews on its own:** vanilla grass cannot spread onto coarse dirt. **So the rung-1 soil spreader must convert coarse dirt *straight* to grass** - leave plain dirt as an intermediate and vanilla spread quietly finishes the job for free, breaking P2.4-R item 3.
- The sweep samples **around players**, not loaded chunks, so an unattended base cannot rot while its owner is away. The mod has **no mixins**, so vanilla `grass_block` behaviour is not injected; the sweep is the mechanism.

Only the *green* is contested. Encroachment reverts to **plain** coarse dirt, never to the Phase 5 mound bed, so mound retirement stays permanent.

**Targeting is an allowlist tag minus a denylist tag, and both are built from other tags** so chisel-style mods that add dirt variants are covered without a mod release. `encroachable` is `#minecraft:substrate_overworld` plus farmland; `encroachment_immune` carves back out **coarse dirt** (the revert target - otherwise the sweep churns bare ground forever) and **mycelium** (the substrate `MyceliumPatchFeature` places and dump mushrooms grow on, so eating it would erode the P1.9 forage economy). Tags can union but not subtract, which is why the second tag exists rather than a shorter first one.

**One rule is blockstate, not block, so no tag can express it:** *wet farmland holds, dry farmland is taken.* Irrigation defends a plot and an abandoned one dries out and goes back to the dump, which makes the P1.10 water economy a reclamation defence rather than only an input. Keyed on `BlockStateProperties.MOISTURE` rather than on `minecraft:farmland`, so modded farmland is covered without being named. It is the one place encroachment displaces player investment, and the severity is bounded: a crop on dry farmland comes off with the soil but vanilla's `updateOrDestroy` **drops** it, so you lose the plot and the growth, never the seed. Tuning is those two plus `hostile_ground`, `frontier_anchor`, `frontier_cover` and the biome tag `encroaches`, over the `reclamation` config block - the mechanic is inert outside the garbage biomes. `encroachOnce` is the static test entry point; the sweep owns targeting (config gate, biome, heightmap), which is why the GameTests can run it on a plain plot.

**Components come in two kinds, and the distinction is the design** (owner, 2026-08-06, locked as P2.11's sibling). **Placeable** components are blocks you stack into a multiblock - Machine Frame, Water Tank, Pump, Copper Pipe, Solar Panel, **Motor**. **Crafting** components are ingredients you spend - the **Bulb** is the first. They are **inert** (P2.4-R item 6): the Pump moves no fluid, the Solar Panel detects no light, the Motor turns nothing. Their names invite the opposite, which is why it is written down. **One exception, since 2026-08-18: the Water Tank holds water** (owner ruling from playtest, #229). It is the only component named for a **capacity** rather than an action, and a tank that does not hold reads as broken rather than as a part - two playtesters reported it as a bug ninety minutes apart. Every other component is still inert. The carve-out is scoped to that one block and to fluid: a formed machine still redirects a bucket to its core, so the tank is a tank when it is loose and the machine's when it is not. Before this split the vocabulary had exactly one gated part and it did not work - the Pump appeared in **zero** crafting recipes, its only use was one Grass Spreader cell, and both this file and the guidebook claimed it gated the Rain Collector and the Hydroponics Bay, which neither ever did.

**Multiblock machines: core + component, master/dummy** (`content/block/multiblock/`, spec `docs/multiblock_system_spec.md`). A machine is a core block you place plus shared components stacked on it; the pattern is Immersive Engineering's, trimmed. `Multiblock` is the blueprint (offset -> component -> formed block) and is the **single source of truth** for validation, the auto-assemble-from-inventory step, and the GameTests, so those three cannot drift. `MultiblockCoreBlock` owns the `FORMED` blockstate; `MultiblockDummyBlock` redirects use and break to the master so a formed machine behaves as one object. **No BlockEntity for the structure** - `FORMED` is blockstate and cells are read from the world, so nothing about the assembly serialises (a machine may still have a BE for its own contents, as the Rain Collector's tank does).

Three rules that bite:

- **The formed look is bespoke.** IE machines do not look like the parts they are built from. A formed cell is a **per-machine dummy block** (`rain_collector_funnel`), never the shared component's model restacked - so the shared vocabulary is cheap but each machine's formed appearance is real art.
- **Disband must not recurse, and `setBlock(AIR)` does *not* make it safe.** Breaking either cell tears the machine down. `destroyBlock` on a partner obviously ping-pongs, but so does the "safe" `Block.dropResources` + `setBlock(AIR)`: **in 26.1 `affectNeighborsAfterRemoval` (the dummy break hook) fires on `setBlock`-to-AIR too**, so clearing a cell re-enters its siblings' hooks. While the core is still `FORMED` those re-entries each re-drop the core (`Block.dropResources(core)`), duplicating the core item once per sibling - a machine with N dummy cells hands back N cores from one break. It hides well: cell-clearing skips already-air cells so *component* drops stay correct (7 frames), and only the unguarded core drop multiplies. The fix that holds: **`MultiblockCoreBlock.disband` flips the core to unformed BEFORE clearing its cells**, so every re-entrant hook bails at the dummy `isFormed` guard. A single-dummy machine (Rain Collector) is immune - no siblings to cascade through - which is why a two-cell test never caught it; **a disband test must break a dummy cell of a machine with 2+ dummies AND count the core item** (breaking the core is the safe path and proves nothing here). Breaking the core and breaking a dummy are **separate code paths** and need separate tests.
- **Auto-assemble is all-or-nothing.** If the player cannot supply every component, place none - a half-built machine from a partial inventory is worse than a plainly unformed core.

**The Scrap Network is adjacency, not a multiblock** (`ScrapNetwork`, design P2.10, spec `docs/scrap_network_spec.md`). The scrap-interaction blocks - Scrap Bin, Scrap Barrel, Sorting Tarp, Recompile Workbench, Burn Barrel, Scrap Crafting Table, Cupola Furnace, Slag Furnace, Sintering Kiln, Filing Cabinet, Separator, Trommel, Pulverizer (plus all three multiblocks' formed cells, so any face of an assembled machine connects) - carry the `#recompile:scrap_connectable` tag; placed sharing a face they are one cluster, and junk routes between them with **no core, no blueprint, and no saved state**. `ScrapNetwork.insertFromMember` floods the tag from the acting block each call (bounded BFS, small clusters, user-paced) and routes: a **bound** bin matching the item first, then (only with `autoBind`) an **empty** bin that binds to it, then the Scrap Barrel. Two traps: only **two** of the twelve placeable member types are routing sinks - a Scrap Bin, and the Scrap Barrel *matched by block id* - because the **Burn Barrel is itself a furnace `WorldlyContainer`** and a route must never land in its smelt slots; and **exactly two callers pass `autoBind=true`** - the Tarp's file-all (`SortingTarpBlock.fileAllIntoNetwork`) and the Scrap Crafting Table's `depositCarried` - so a sift, teardown, smelt or drain never surprise-binds an empty bin. *(This previously said the file-all was the only one, and listed seven members. Both were wrong, and the guidebook had copied the same two mistakes - which is how a stale list spreads: it reads as complete. It went stale again when the Trommel joined in #192 and a THIRD time when the Pulverizer joined in #197 - each time caught by review rather than by the person editing the tag. A FIFTH time with the Sintering Kiln (#248) - caught by review again, not by the person editing the tag. A FOURTH time with the Slag Furnace (#236), and that one was worse than stale: the machine called `insertFromMember` from its ticker without being in the tag at all, and `ScrapNetwork.collect` returns an EMPTY member list when the block it floods FROM is not a member - so the drain was a no-op with nothing logged. Joining the tag is part of shipping a machine that drains, not a decoration on top of one. If you add a member, this sentence is part of the change; the list reading as complete is exactly why nobody checks it.)* This slot was first built as a fixed multiblock ("Scrap Workstation") and reversed 2026-07-24 - players arrange the blocks freely instead. The held-item placement guideline that came out of it lives on for real multiblocks (`MultiblockPlacementPreview`, any `MultiblockCoreBlock`).

**Saplings are machine-only** (`StripSaplingsModifier`, design P2.4-R2). A **global loot modifier** strips `#minecraft:saplings` out of every loot roll, so **no loot roll anywhere yields a sapling** - not a broken sapling, not decaying leaves, not chest loot. The **Tree Nursery** (rung 4, Phase 2.16) is where trees come from. *This said "a player can never hold a sapling" and named a Phase 6 tree planter; both were wrong by 2026-08-20 - the nursery had shipped long since, and #227 brought EMERALDS, which is what makes a wandering trader's sapling offer purchasable - the traders themselves always spawned here, since `WanderingTraderSpawner` needs no village and the only suppression is `#minecraft:without_wandering_trader_spawns`, which holds `the_void` alone. The gate was never the trader, it was the currency. Trades are not loot rolls, so nothing here sees them, and curating trade tables was declined (#263, not-planned). The rule that still binds is that one cannot be FOUND, which is the half the ladder depends on.* This exists because vanilla lets a sapling be planted *and grown* on raw coarse dirt (`#supports_vegetation` reaches coarse dirt via `#dirt`, and 26.1's `TreeFeature` has no ground-material gate at all), which would let a found sapling grow a tree that permanently anchors the frontier - rung 3 with no rung 1 or 2 and no machine. Gating on *what can place a sapling* rather than *what soil accepts one* means no vanilla tag override, so planting still works normally on everything the player heals.

Two traps in that area:

- **A recipe override can lose to NeoForge silently.** Shipping a file at a vanilla recipe id replaces vanilla's - that is how the sixteen beds are deleted. But **NeoForge ships its own copies of 17 vanilla recipes**, retagged to the `#c:` common tags: `minecraft:bucket` and all sixteen `dye_*_carpet`. Mod datapacks are ordered by mod load order, so with `ordering = "NONE"` on the neoforge dependency our file is **never read** for those ids, and nothing is logged - corrupting one produces no parse error at all, which is the only way it was caught. `ordering = "AFTER"` in `neoforge.mods.toml` fixes it, and the split is a perfect tell: overrides for recipes NeoForge does not re-ship work, the ones it does are ignored. See #161.
- **`neoforge:conditions` works on a whole FILE and nowhere else, and the near-misses all fail
  silently.** A condition on a loot POOL or on a loot ENTRY is not read. A condition in a TAG file is
  **ignored in 26.1** - measured in #276, where the tag kept its member with the named mod absent. What
  works is a condition at the top of a loot table file, a recipe file, an advancement, or - measured in
  #277 - a **`loot_modifiers` file**. On a RECIPE the condition is load-bearing: strip it and the file
  fails to PARSE on its own result id, leaving one ERROR line in an otherwise green run.
- **Gating the TARGET of a reference is not gating the reference, and a nested table is the trap this
  file used to recommend.** #276 made a drop conditional by putting it in its own guarded table and
  reaching it with a `minecraft:loot_table` entry at weight 15. Without the mod the table did not load
  **and the entry still did**: it kept its weight, kept winning 15 rolls in 405, and handed back
  nothing - a silent one-in-27 empty pull in the DEFAULT install, measured at 291 items from 300 rolls,
  plus a permanent `Missing element` loot-validation WARN on every world load pointing at an engine
  file. Neither symptom names the guard.
- **So a mod-gated drop is an UNCONDITIONAL entry plus a conditional STRIP** (`StripItemModifier`,
  `loot_modifiers/no_sky_stone.json`, guarded by `neoforge:not`). Name your own item, so the id always
  resolves and cannot take the table down at parse; put it in a **pool of its own**, so it rides along
  instead of displacing a weighted sibling; and let a modifier remove it. **The obvious inverse does
  not work:** `neoforge:add_table` is a NeoForge built-in and does fire on this mod's pull streams
  (measured at 3.6% against an intended 3.7%), but aiming it needs `neoforge:loot_table_id`, which
  compares `LootContext.getQueriedLootTableId()` - **never set on a table rolled programmatically**,
  which is how all five of this mod's roll sites work. With the condition it dropped nothing; without
  it, it fired on every table in the game.
- **A viewer that reads bundled files reads files the game did not load.** `RecipeFiles` and
  `SortingData` both evaluate conditions themselves for exactly this reason; without it JEI advertises
  recipes that do not exist and the rate census predicts drops that cannot happen (both caught by tests
  when #276 landed). `SortingData` also honours **strip modifiers**, because a table listing an item a
  modifier deletes is telling the truth about itself and a lie about the world. **The evaluator lives
  in one place** - it was duplicated byte for byte, and two evaluators of a silently-failing condition
  drift in both directions at once. It understands `mod_loaded` and `not` and treats anything else as
  satisfied; adding a condition type to data means adding it there too, and `ConditionEvaluationTest`
  is where that is pinned.
- **The GLM directory is `loot_modifiers`, plural** - it is NeoForge's folder, not one of the vanilla dirs 26.1 singularised. "Fixing" it to match `loot_table/` silently stops the modifier loading, with no error anywhere.
- **There is no `global_loot_modifiers.json` index in 26.1.** Every JSON in `data/<ns>/loot_modifiers/` is loaded as a modifier by directory scan, so the old `data/neoforge/loot_modifiers/global_loot_modifiers.json` index is not just unnecessary - it gets parsed *as a modifier*, has no `type` field, and logs `Couldn't parse data file 'neoforge:global_loot_modifiers'` at ERROR on every load. The modifier works regardless, so the error is pure noise that points at the wrong thing. Deleted 2026-07-23; the sapling-lockout GameTests (which break a real sapling and can only pass with the modifier live) prove it was never load-bearing.
- **A dev run reads resources from `src/main/resources`, not `build/resources/main`.** Deleting a datapack file from the build output does *not* disable it at runtime. To prove a data-driven feature is actually doing something, neuter the **Java** and re-run - a resource-file negative control will lie to you.

**Two traps that cost real time on the machines**, both silent:

- **A non-cube model on a block without `noOcclusion()` punches a hole in the world.** The game still treats it as a full cube for face culling, so it culls the neighbouring block's face and you see straight through the ground. It looks like a rendering glitch with no obvious cause. Every block whose model is not a full cube needs `noOcclusion()`.
- **Disband returns the component the blueprint names, not the formed block's loot** (changed 2026-08-07). It used to run the formed block's loot table, which worked only while every formed block mapped from exactly ONE component and its table was kept in sync by hand. That broke the moment two components shared a formed appearance: the Separator's Motor cell forms into ordinary housing, whose table drops a Machine Frame, so disbanding converted the rarest part in the machine into the commonest one in silence. `Multiblock.disband` now reads `cell.component()`, so the blueprint is the single source of truth for validation, auto-assemble, the guidebook pattern **and** what you get back - those four cannot disagree. A formed cell's loot table is now only what a stray `setBlock` would drop - **except for the three cells that are hand-placed components** (`water_tank`, `solar_panel`, `rain_collector_funnel`), where the block you place and the formed block are the same one. Those keep their loot tables and `MultiblockDummyBlock.getDrops` defers to them, because returning nothing for every dummy took those three with it and a placed one silently vanished when broken while JEI still listed it as craftable (#204). `Multiblock.isHandPlaced` decides, off the blueprint rather than off the world - a player break removes the block before drops are rolled, so a "am I in a formed machine right now" test answers no on exactly the path that matters.

**The demolition yard is the first frontier region, and it is where iron comes from** (Phase 4, spec `docs/demolition_yard_spec.md`). `RegionBiomeSource` places biomes on a **distance gradient** from origin, not by climate noise - household sprawl is guaranteed inside `core_radius` (512) and frontier regions appear past their own onset, so travel is the gate. The yard supplies stone (Stone Rubble -> shards -> the vanilla stone family), concrete, and steel.

**The iron gate is a recipe type, and that is the second design of it.** `material_economy.md` makes copper the everyman metal and iron the gated upgrade. What enforces it now: **both iron recipes are `minecraft:blasting`** (Steel Offcut -> ingot, rebar -> nugget) and the **Cupola Furnace is a `RecipeType.BLASTING` machine**. A vanilla furnace cannot run a blasting recipe at all, and a vanilla blast furnace costs 5 iron ingots, so it is circular and unreachable before iron. The gate is a property of the machine, and no fact about the world's materials has to hold for it to work.

**The first design failed silently and is worth knowing about** (#91). It was: iron recipes are ordinary `smelting`, gated because the Burn Barrel refuses them *and no other furnace is craftable*. That second clause died when the Tree Nursery shipped - wood makes a wooden pickaxe, a wooden pickaxe drops cobbled deepslate (plain `deepslate` is in `mineable/pickaxe` and in no `needs_*_tool` tag), and cobbled deepslate is in `#minecraft:stone_crafting_materials`. `stone_from_shards` gives a second route and world deepslate a third. Worse, **`rebar` is a weight-40 entry in `household_pulls`**, so a player could stockpile it on day one and make iron at rung 4 with no demolition yard, no Cutting Torch and no Cupola. The old comment named that exact failure mode as a risk and it happened anyway, because a gate built from *the absence of a material* dies the moment anything adds the material. `no_smelting_recipe_turns_a_mod_item_into_iron` now asserts it instead. Check `../trashlands/docs/progression_gates.md` before touching this.

**The Cupola rakes slag off every few smelts** (#236, owner 2026-08-18). Slag is a byproduct and has no recipe: it is the non-metallic fraction that floats off any remelt, and the machine hands one over whether you want it or not. It **cannot** be a recipe output - the Cupola is a `RecipeType.BLASTING` machine because that IS the iron gate, and vanilla blasting has one result and no byproduct slot - so it lives in `CupolaFurnaceBlock.getTicker`'s wrapper, the same seam `drainOutput` uses, and detects a finished smelt by sampling the result slot ACROSS the tick (vanilla refuses every other route into slot 2, so growth there can only be a smelt). Counted, not rolled: one per eight, which is about the real slag-to-metal ratio. It goes to the two machines whose verbs fit it: the **Separator** divides it into concrete powder with recovered scrap metal as the byproduct (slag is a mixture, and reprocessing it for entrained metal is real practice), and the **Pulverizer** grinds it into Fertilizer (ground slag was sold as phosphate fertiliser for a century). The third is the point of the whole chain: the **Slag Furnace** vitrifies it into **obsidian**, which `material_economy.md` has always said is made only, and which the Nether gate rides on. No new item either way - Reinforced Concrete already drops concrete powder when broken, so the Separator route is that same material arriving by manufacture rather than by demolition.

**Being blast-only means the Cupola does not cook food**, deliberately: a cupola furnace melts metal. The Burn Barrel keeps refuse and food and is still craftable on its own, and Scrap Metal has a blasting twin so copper survives the upgrade.

**The barrel's refuse-only rule is an allowlist gated in the ticker**, not on the slot. 26.1's `Slot.mayPlace` returns true unconditionally and vanilla's `FurnaceMenu` uses a plain `Slot`, so `Container.canPlaceItem` is never consulted; and `AbstractFurnaceBlockEntity` keeps `quickCheck`/`recipeType` private with a static `serverTick`, so recipe lookup cannot be overridden. Skipping the tick is the only seam, and it fails closed without burning fuel.

**Steel I-Beams draw their run, not their connections** (`SteelBeamBlock`, ported from Create's Metal Girder - MIT code, its assets are All Rights Reserved and none are reproduced). `AXIS` is fixed at placement from the clicked face; `X`/`Z` mean "part of a horizontal run on that axis"; neither set means a vertical column, which is why a lone beam is a full member rather than a stub. **Worldgen must decide whether to resolve connections.** Blocks placed with flag 2 skip neighbour updates, so each keeps the state it was given: correct for the steel stack (wreckage must not fuse into a lattice) and wrong for the Building Husk (a frame must), which resolves its joints in a second pass through `SteelBeamBlock.updateState`.

**Six machines, six verbs, and the split is the design** (#187, #188, #189 shipped 2026-08-16/17,
#236 on 2026-08-19, #248 on 2026-08-20, #294 on 2026-08-29). The test is what an operation does to the
material:

| Operation | Changes | Machine |
|---|---|---|
| a garbage block into its drops | what it is | **Trommel** (a size cut) |
| Spent Abrasive into a diamond | what it is | **Separator** (it divides) |
| E-Scrap into circuit powder | only fineness | **Pulverizer** (it reduces) |
| slag into obsidian | its **state** | **Slag Furnace** (it vitrifies) |
| pressed powder into a rod | its **direction** | **Sintering Kiln** (it consolidates) |
| amber into the idea of a spawn egg | **nothing** | **Sequencer** (it reads) |

**The sixth changes nothing at all, which is what earns it a row** (#294). Every other operation here
alters the material: a size cut, a division, a reduction, a state change, a consolidation. Sequencing
reads information OUT of a piece of amber and hands its contents back as knowledge, which is a new
column rather than a sixth instance of an existing one - and a new column is this repo's own bar for
adding a machine. It is also the only one that is neither a multiblock nor a furnace: a powered single
block, so like the Slag Furnace and the Kiln it sits outside `MachineParityTests`, whose sweep derives
its list from multiblock cores answering `Capabilities.Energy.BLOCK`.

**The fifth runs the other way, and that is the point of it** (#248). The first four all take
something apart - a size cut, a division, a reduction, a state change - and the Pulverizer alone ships
seven recipes every one of which produces a POWDER. Nothing turned powder back into a solid, which is a
missing DIRECTION rather than a missing recipe, and blaze rods sat behind it. Sintering fuses a pressed
compact below its melting point, which is how powder metallurgy makes rod stock. Like the Slag Furnace
it is a single block with a screen burning fuel rather than FE, so it is outside `MachineParityTests`
too; its parity is with the Burn Barrel, the Cupola and the Slag Furnace.

**A cooking recipe consumes exactly one item, and that is why there is a briquette.** Vanilla crafts one
blaze rod into TWO blaze powder, so a one-for-one powder-to-rod recipe would print rods forever. Four
powder press into a Blaze Briquette at a bench and the kiln fires the briquette, which is also the real
sequence - powder metallurgy compacts a green body first and sinters it second.

**The fourth is the odd one out on purpose, and it breaks the shared contract below rather than
bending to it.** The first three change what a material *is* or how fine it is; vitrifying changes its
state, and the same silicates come out the other side as an amorphous glass. It is also the only one
that is not a powered multiblock: it is a single block with a furnace screen, because the other three
are conveyor machines you feed and walk away from and this is one you put a lump into and watch. So it
runs on fuel rather than FE, subclasses `AbstractFurnaceBlock`, and does not appear in
`MachineParityTests` at all - that sweep derives its list from multiblock cores answering
`Capabilities.Energy.BLOCK`, and this answers neither. **Its parity is with the Burn Barrel and the
Cupola instead**, and `SlagFurnaceTests` is where that is asserted.

**The Separator used to do all three and now does one.** Sorting was a second MODE on it for four
releases and moved to the Trommel; a shear shredder destroys distinctions and sorting requires one, so
the machine that sorts is the one that makes a size cut. The word "grinder" is deliberately gone from
the Separator's javadoc - it names the Pulverizer now, and a tree where one word names two machines is
one where the confusion recurs.

The first three share one contract - powered, GUI-less, no `Container` and no item capability,
fed by reaching out (loose items in the mouth, a container parked on them drained), output routed to the
Scrap Network then a chute then the floor. `MachineParityTests` derives that list from the REGISTRY
(every multiblock core answering `Capabilities.Energy.BLOCK`) rather than from a hand-list, and
asserts Jade coverage, network membership, the closed door, and that each says how to feed it. They
were built by copying each other, which is how they agreed and also how they came apart: the
Pulverizer shipped with zero Jade providers against the Separator's four.

**The two chains they unblocked**, both of which had been parked with nowhere correct to run:

- **Gold** (#120): E-Scrap -> Circuit Powder (pulverizing, 4:1) -> gold nugget (Cupola, blasting).
  Grinding is what liberates metal from the resin and glass holding it; blasting is also the gate,
  since a vanilla furnace cannot run a blasting recipe.
- **Clay** (#115): a pottery sherd -> Grog (pulverizing) -> + Kitty Litter -> Dry Clay Body (grid) ->
  clay (right-click a filled water cauldron). **Firing is irreversible** - kaolinite dehydroxylates
  above ~550C and cannot be rehydrated - so crushed ceramic is grog, a NON-plastic temper, and the
  plasticity has to come from the bentonite in cat litter. The two halves are useless apart. It
  unlocks 43 vanilla items, and it needed a source added for sherds: this world has no archaeology, so
  they were unobtainable and the whole chain was a dead end until one entered `household_pulls`.
- **Resin** (#231, owner 2026-08-29): Amber -> Sequencer -> **Spent Amber** + a fragment; Spent Amber
  + **Turpentine** (found) -> `resin_clump` (grid), and all nine resin items hang off the clump.
  **This is the clay chain's argument applied a second time, and it had to be.** Amber is polymerised
  and cross-linked, so softening it back into fresh sap is the fired-clay problem verbatim - the trade
  refused above. Turpentine is not a stand-in for the missing fraction, it IS that fraction: it is
  distilled from pine resin, which is exactly what fossilisation drove off. So the pair puts back the
  one thing that left rather than reversing anything, and neither half does a thing alone.
  **The husk is why the two amber chains compose instead of competing.** Every amber in both pull
  streams is stamped, so reading one for a spawn egg used to destroy the only material a resin clump
  could be made from; the Sequencer now hands the emptied body back in a byproduct slot (the Cupola's
  shape, for the Cupola's reason - a machine that returns two things cannot say so with one output).
  Vanilla's own clump recipe consumes a `resin_block` and `creaking_heart` consumes the resin it is the
  source of, so both look like ways in and are self-referential;
  `the_resin_family_has_a_non_circular_entry_point` asserts a real entry exists rather than that a
  recipe exists, which is a distinction the first version of that test got wrong and passed anyway.

**A machine comes back however you break it** (owner, 2026-08-16, #195). No multiblock core declares
`requiresCorrectToolForDrops`: the gate was opt-out, because breaking the CORE with the wrong tool
destroyed it while breaking any CELL handed it back. A formed cell drops nothing of its own and the
blueprint decides what disassembly returns on every path, so a cell break returns the component you
put in that cell rather than whatever that shared formed block's loot table happened to name.

**Two recipes that accept the same grid are one recipe, and the loser is silent.** A crafting grid
resolves to a single result, so when two recipes match the same arrangement of the same items only one
can ever be crafted: no error, no log line, a JEI page saying it works, and the other thing coming out.
**`trommel` and `pulverizer` were byte-identical from v0.10.0 to v0.14.0** - one of those two machines
could not be made for four releases, and nobody noticed. `every_crafting_recipe_is_reachable_at_a_bench`
(`RecipeReachabilityTests`) now builds each shipped recipe's grid from its bundled JSON and asks the
**live recipe manager** what matches.

**It asks vanilla's matcher rather than comparing JSON, and that is not fastidiousness.** A static
comparison has to reimplement the matcher to be right: shaped recipes are distinguished by their
PATTERN, so the three stairs/wall pairs here are not collisions, while a shapeless recipe swallows every
arrangement of its multiset and so CAN collide with a shaped one. A first pass in Python got exactly
that wrong and cried wolf on all three. `getRecipesFor` returns every match rather than the first, which
is the only reason the shadowed half is visible at all. It also asserts each recipe matches its OWN
grid, so a wrongly built grid fails loudly instead of quietly making the check vacuous.

**The data spine.** `TeardownRecipe` registers the public `recompile:teardown` recipe type - JSON in `data/<ns>/recipe/`, with `results` (deterministic core), `extras` (weighted bonus), and `teaches` (recipes to study). It was registered from day one so the Phase 3 knowledge system is never retrofitted into a live schema. **Four public recipe types now, not one**: `recompile:teardown`, `recompile:separating` (one feed into several distinct outputs plus byproducts), `recompile:pulverizing` (one input, one finer output; `count` exists but **is 1 in every recipe this mod ships** - owner, 2026-08-19: *a GUI-less machine cannot take N > 1 inputs to make an output*, because with no GUI and no Container a partial batch is invisible and unrecoverable, and a partial batch is the ordinary state when the pull streams hand scrap out one at a time. The field stays because the schema is public, and both queue machines now SKIP a slot they cannot run instead of stalling on it, so a pack using it gets a slow machine rather than a bricked one). The fourth is **`recompile:spawn_egg_crafting`** (#294, the only one with no `result` field: the result is read off a Blueprint sitting IN the grid, so one recipe covers every creature. It could not be a `blueprint_crafting` recipe, because that schema names one set per recipe and a per-species family would be 29 recipes sharing one arrangement, which the bench resolves by taking the first whose sheet is in reach. It is also the only recipe where a Blueprint is an INPUT, and the table's own result slot hands it straight back - 26.1's `ResultSlot.getRemainingItems` is private and resolves `RecipeType.CRAFTING` only, and the item-level `craftRemainder` would return a BLANK sheet and destroy the species the player earned). They are separate types rather than one flexible one because a schema expressing all three expresses none, and separating is already extended by packs - overloading it would redefine what their existing recipes mean. **Packs and addons extend the teardown tree through this schema without a mod release - treat it as public API** (reference: `docs/teardown_schema_spec.md`). `pools` (v0.9.0) is the weighted-draw form: N draws from a weighted list, an entry with no `item` is the filler, and a pool marked `teaches` grants the fragment for whichever item it drew.

**Config.** `RCConfig` (COMMON). The governing principle is "everything ships config-gated, but defaults are the design" - config is for tuning, not for dodging a decision. `RCDimensionLockout` holds the **End** (travel and portal formation together, so there are no dead frames), keeping it from leaking free resources into the closed trash economy. **The Nether is OPEN** (owner, 2026-08-19: its resources and progression are the reason to go). Until the themed generation lands that is the *vanilla* Nether, which routes around several designed gates - read the class javadoc before assuming a gate still holds, because iron and wood are both reachable down there and neither is obvious.

**GameTests.** `RCGameTests.test(name, maxTicks, body)` hides 26.1's two-step registration (a `Consumer<GameTestHelper>` in `Registries.TEST_FUNCTION` plus a `FunctionGameTestInstance` carrying `TestData` at `RegisterGameTestsEvent`). Each domain class is just test bodies plus one `register()` line called from `RCGameTests.register`. All tests share the `empty_5x5x5` structure. Blocks expose a **static entry point** for tests to call directly (`SortableBlock.sortOnce`, `SortingTarpBlock.siftInput`) rather than simulating a player interaction. `RegistryCompletenessTests` is a **coverage sweep**, not a behaviour test: it walks every mod item and block and asserts each has a translated name, a `assets/<ns>/items/<id>.json` client definition, a blockstate, a loot table and an item form - the silent, never-compiled gaps that have repeatedly shipped here. It reads assets as classpath resources (a dev run has them even though a server never loads `assets/`) and lang via NeoForge's server-side `en_us`. Legitimate exceptions live in two explicit lists in that file (`NO_LOOT_TABLE`, `NO_ITEM_FORM`), each with a stated reason - add a justified entry, never loosen a check.

## Textures (texgen)

Textures are **generated, never hand-drawn**, by the shared engine at `../mc-pack-toolkit/texgen/` (install: `pip install -e F:/minecraft-repos/mc-pack-toolkit/texgen`). This repo carries `texgen.toml`, which declares every surface (prompt, backend, variants/faces). Workflow: `texgen generate --surface X` -> `texgen sheet` -> `select X <idx>...` -> `promote` -> `validate`.

`select` takes **one ref per slot, in slot order** - so a 4-face surface takes four refs. Pass **bare indices** (`select washing_machine 3 3 3 2`), which is also what the review page prints. The `<batch>/<idx>` form only resolves for a single-slot surface: a multiface surface pools each face in its own dated directory (`washing_machine_front-<date>/`), so a shared batch prefix resolves to a path that does not exist and `promote` dies with `candidate not found`.

**Jason running `select` IS approval** - there is no `approve` verb, and `promote` does not grant it. The review page prints a `select` line per candidate; when he issues one, add that surface id to `gen/approved.json` so the page stops listing it as pending. A `select` run by the assistant while generating is *not* approval, so the id stays out until he picks. (`mound_ground` shipped in v0.7.0 while still pending, which is how a texture reaches a release with no approval record.)

`texgen sheet` builds `gen/recompile_textures_review.html`, the page Jason reviews art in: pending surfaces on top with their `select` commands, approved ones at the bottom. **Re-run it after anything that changes a texture** - it is a build artifact, not a live view. Approval is explicit in `gen/approved.json`; drop a surface's id back out when its art changes so it returns to the pending queue.

**Hard rule: no raw AI output lands in the repo.** `gen/` and `art_src/` are gitignored; only the finalized 16px PNGs under `src/main/resources/assets/recompile/textures/` are committed. A texture change should show up in the diff as *only* the small PNG plus the manifest.

Two surfaces that must read as the same object in different states use `reference = "<other-surface>"` (e.g. `tin_can_open` -> `tin_can`), which derives one from the other's source art instead of generating it blind. Generating each state from its own prompt makes them drift into different objects.

`kind` picks both the output directory and whether the art keeps an alpha background. When those need to differ - a cross-model plant is a transparent sprite that must live in `textures/block/` - set `transparent = true` explicitly (see `dump_mushroom`).

Changing a surface's `kind` and re-running `promote` re-finalizes from `art_src/` with **no API call**, so moving art between atlases costs nothing and cannot drift.

Two promote-time keys exist for tone, and both beat re-prompting for it: `match_hue = "<surface>"` re-casts a surface onto a shipped sibling's colour while keeping its own luminance (parts of one object generated separately otherwise come back as different materials), and `brightness = <float>` trims tone deterministically. **Calibrate tone against neighbours, not in isolation** - a `brightness` tuned against one block is wrong the moment that neighbour is regenerated.

**Trust the shipped PNG over the manifest's `backend`.** `garbage_block` and `stone_rubble` both declared `procedural` while shipping AI art for months (since corrected - both declare `ai` now). A procedural draw can only produce its palette's colours; anything at the 16-colour quantize cap came from AI. **Procedural is still the right call in places and is not a fallback there:** every Puzzle Cube face, the cube pieces and the Display Pedestal are deliberately procedural, because AI and downsampling both failed a twisty cube at 16px.

Blocks with `variants = N` get randomized variants from the **blockstate JSON, not code**.

## JEI / Jade integration (`compat/`)

JEI and Jade are `runtimeOnly` viewers **plus** `compileOnly` APIs (`jei-...-neoforge-api`; the Jade jar bundles `snownee.jade.api`). The plugins live in `com.flatts.recompile.compat.{jei,jade}` and load **only when the viewer mod is present** - `@JeiPlugin` / `@WailaPlugin` are never referenced otherwise, so the mod ships and runs without either.

- **A viewer must not list an uncraftable multiblock part** (owner, 2026-08-03). A formed cell exists
  only once a machine is assembled - a Separator Chamber, a Compost Cage - so offering it in JEI teaches
  nothing except that the mod has a block with no recipe. `MultiblockParts` derives the set
  **structurally**: a cell whose formed block differs from the component you place is a transformation,
  so the formed half is unobtainable, while a cell where the two are the *same* block is a part you craft
  and place by hand (the Rain Collector Funnel, the Solar Panel) and stays visible. Nothing names a block,
  so a new machine is covered the day it is written. They stay in the creative tab, where a builder wants
  them; only JEI hides them. `jei_hides_only_multiblock_parts_that_cannot_be_crafted` asserts both
  directions.
- **JEI** (`RecompileJeiPlugin`): **fourteen categories**, and the count is deliberately not enumerated here - this line said "three instances" and named three for months while eleven more shipped, which is exactly how a stale list survives: it reads as complete. The list lives in `registerCategories`. What is worth knowing is **when one is needed**, because that is not obvious and has three separate answers.
  - **A non-recipe mechanic.** Sorting, Cutting, Prying, Hydrating: real behaviour with no recipe object behind it, so JEI can find nothing. Most are instances of one reusable `SalvageCategory` (input on the left, weighted outputs on the right, odds on the tooltip when they are not a certainty).
  - **A modded `RecipeType`.** Teardown, separating, pulverizing, vitrifying. JEI shows *vanilla-typed* recipes for free and a modded type is not one however closely it copies the shape - `recompile:vitrifying` is vanilla's cooking schema exactly and still gets nothing. Without a category the mechanic is invisible, which for vitrifying meant the only route to obsidian in the game.
  - **A vanilla recipe type whose display cannot hold the whole truth** - one case, the Cupola (#243). It runs `minecraft:blasting` and always has (that IS the iron gate), so its recipes DO show for free. But vanilla blasting has one result slot and the machine hands back two things: every Nth completed smelt also rakes off slag, which is the sole input to the Separator, the Pulverizer and the Slag Furnace. The whole obsidian chain hung off a byproduct the shared category structurally could not draw. Same shape as why the machine needed a bespoke **menu** (#240).
  - **And the Cupola is NOT a catalyst on vanilla Blasting** (owner, 2026-08-19). It shipped in both for a day: every one of the mod's four blasting recipes then appeared twice, once as "scrap to copper nugget" and once as "scrap to copper nugget and slag", with the Cupola named in both. The second entry is a superset of the first, so the pair was noise. Nothing is hidden by the removal because the Cupola category carries every recipe it runs. Registering one item for two categories also doubles its catalyst tooltip's mod-name line, which is what surfaced it.
  Plus the Scrap Crafting Table as the crafting **station** (the world has no vanilla table). The old rule still holds where it applies: a real *vanilla-typed* crafting recipe shows automatically and needs nothing here.
- **Jade** (`RecompileJadePlugin`): a tool-hint provider ("Salvage with a Prybar/Scrap Knife" / "Sort by hand"), a sort-progress provider ("Sorted N/max", reading the otherwise-hidden `sorted` blockstate), and a generator provider (stored FE, current rate, burn remaining). **A provider that needs server-side state is two classes** - an `IServerDataProvider` plus an `IBlockComponentProvider` - because since MC 1.21.6 one class may not be both. `SortableBlock` exposes public read-only accessors (`sortTool`/`sortedCount`/`sortCrumbleAt`) so the compat package can see its protected internals.
- **`SortingData`** parses the **bundled loot JSON** (not a live table) because loot tables are not client-synced - so the categories work in singleplayer and on servers. It is server-safe and the only JEI/Jade logic a GameTest can cover (`SortingDataTests`); the categories/providers are thin renderers verified in `runClient`. Datapack-retuned pulls are not reflected in JEI - accepted, revisit if needed.
- **The power tier speaks Forge Energy** (#72): `Capabilities.Energy.BLOCK` is a NeoForge standard, so the Solar Panel and Burner Generator interoperate with any energy mod at zero dependency cost. Energy moved to the **transfer API** in 26.1 exactly as fluids did (`EnergyHandler`, `SimpleEnergyHandler`, transactional insert/extract), so pre-1.21 `IEnergyStorage` snippets are wrong here.
- **Teardown JEI is deferred to Phase 3** (only an example recipe exists; its locked-recipe overlay is the Phase 3 risk spike). EMI is not wired.

## The guidebook (`data/recompile/modonomicon/`)

An in-game **Modonomicon** book, `recompile:guide` (spec `docs/guidebook_spec.md`, #29). Engine is
`runtimeOnly` and `transitive = false` - the book is pure data, so the mod ships and runs without
Modonomicon present, and the guide item's recipe is gated `mod_loaded: modonomicon`. **One rule
decides content: if a mechanic deviates from vanilla it earns an entry, if it behaves exactly like
vanilla it does not.** Eleven categories today, the newest being the compacted depths (#252).

**Layout:** `books/guide/{book.json, categories/, entries/<cat>/<entry>.json, entries/<cat>/<entry>/pages/}`.
An entry does **not** list its pages - the `pages/` directory is scanned, so adding a page is adding
a file. Modonomicon scans a fixed `modonomicon/books` folder under every namespace, which is also how
a pack extends this book without touching the mod.

**Multiblock render pages are the one part with a drift risk, and it is locked** (#37). Patterns live
in `modonomicon/multiblocks/` - **plural**, Modonomicon's own folder, not one of the dirs 26.1
singularised, and the same silent-nonload trap as `loot_modifiers`. A `modonomicon:dense` pattern
lists **layers top-first** (`stateMatchers[x][height - 1 - y][z]`), each string in a layer is one X
and each character one Z. The shape therefore exists twice - there and in `Multiblock.java` - so
`GuidebookMultiblockTests` compares them in both directions and fails if a page draws a machine that
would not form. The pages draw the **loose components**, not the formed machine, because their job is
to teach the build.

**Four things that fail silently.** A `text` naming a lang key that does not exist renders the raw
key to the player; an entry icon naming a missing item renders the pink-and-black missing texture on
the category map; and a plain string in any text field is treated as a **translation key**
(`BookTextHolder` runs it through `I18n`), so literal prose in one of those fields renders as itself.
`GuidebookTests` covers the first two off the classpath. Everything else is
**client-render-only** - GameTest and the JUnit layer are blind to it, so a `runClient` pass is the
only proof a page draws.

And the fourth, which cost the most: **a blank line does not break a paragraph** (#241). Modonomicon's
`CoreComponentNodeRenderer` claims `Paragraph` in `getNodeTypes()` and has **no `visit(Paragraph)`
override**, so `AbstractVisitor` walks a paragraph's children and emits nothing at the boundary - the
last word of one paragraph is welded to the first word of the next, which reads as a typo rather than
a layout fault. It shipped that way in all 71 of the book's text pages for releases. The only node
that emits a **newline** is `HardLineBreak`, and commonmark makes one from a **backslash at end of
line** - so a paragraph gap is a blank line followed by TWO backslash-terminated lines, and a single
line break is ONE, which is the idiom Modonomicon's own demo book uses. **A lone newline is not a
break either**: it parses to a `SoftLineBreak`, and `BookTextRenderer` sets `renderSoftLineBreaks(false)`
with `replaceSoftLineBreaksWithSpace(true)`, so it renders as a **space** - which is how a nine-item
list shipped as one wrapped sentence, and how the first version of this fix walked past it. `every_guidebook_paragraph_break_actually_breaks`
fails the build on a bare blank line. Proved offline by parsing each candidate with the commonmark
0.29 jar Modonomicon jarjars, rather than by guessing at markdown: `A\n\nB` parses to two `Paragraph`s and
renders as `AB`.

Worth knowing when judging page length: **Modonomicon shrinks the font as a text page grows**, so the
book's longest entry (1237 characters, six paragraphs) still fits one page with room to spare. Blank
lines are not the page-budget risk they look like.

## 26.1 API deltas that bite

Most tutorials target 1.20/1.21 and will mislead you:

- **Event buses are merged.** `@EventBusSubscriber` takes no `bus` parameter; `EventBusSubscriber.Bus` is gone.
- `net.minecraft.resources.Identifier`, not `ResourceLocation`. **And `ResourceKey.location()` is `ResourceKey.identifier()`** - the accessor was renamed with the class it returns, so every 1.21-era snippet that reads a key's id fails to compile with a bare `cannot find symbol` that names neither the old method nor the new one.
- **Data directories are singular**: `loot_table/`, `recipe/`, `structure/`, `tags/block/`, `tags/item/`, `worldgen/configured_feature/`, `worldgen/placed_feature/`.
- **`DirectionProperty` is gone.** Horizontal facing is `EnumProperty<Direction>` now (`BlockStateProperties.HORIZONTAL_FACING`); the old dedicated class does not exist, so 1.21-era snippets that declare one will not compile.
- **`GameRules` moved to `net.minecraft.world.level.gamerules`**, and **every rule was renamed to snake_case** - not just the Java constants, the *command ids too*. `doTileDrops` is `block_drops` (`GameRules.BLOCK_DROPS`, a `GameRule<Boolean>`), `doDaylightCycle` is `advance_time`, `doWeatherCycle` is `advance_weather`. This bites in **datapack functions**, where the only symptom is the whole function refusing to load: `Incorrect argument for command at position 9: gamerule <--[HERE]`, which names the command and not the rule. Every 1.21-era snippet uses the old ids.
- **A `DyeItem` no longer knows its colour.** 26.1 dropped the `DyeColor` constructor parameter, the `getDyeColor()` accessor, and the static `DyeItem.byColor(DyeColor)` that every 1.21-era snippet reaches for; the colour moved into item data. `DyeColor` itself is unchanged (still an enum, still 16 values, still `getName()`), so the mapping from colour to item is now the registry id - `BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(colour.getName() + "_dye"))`. Note that an id which resolves to nothing comes back as **AIR, not null**, so a typo fails as a content bug rather than an NPE (see `PrinterTests.upstreamOf`).
- **`BiomeSpecialEffects` was SPLIT, and the half that moved fails silently.** In 26.1 the biome's
  `effects` block holds `water_color`, `foliage_color`, `dry_foliage_color`, `grass_color` and
  `grass_color_modifier` and **nothing else**. Fog, sky, water fog, ambient particles and every sound
  moved to the new environment-attribute system under a **top-level `attributes`** map on the biome,
  keyed by registered attribute id: `visual/fog_color`, `visual/sky_color`, `visual/water_fog_color`,
  `visual/ambient_particles` (a LIST now), `audio/ambient_sounds` (which bundles `loop`, `mood` and
  `additions`). Colours are `"#RRGGBB"` strings or bare ints. There are new dials with no 1.21
  equivalent at all - `visual/fog_start_distance`, `visual/sky_fog_end_distance`, `visual/cloud_color`
  - and a modifier form (`{"modifier": ..., "argument": ...}`) for multiplying a value rather than
  overriding it, which is how vanilla shortens water fog in swamps.
  **A record codec ignores keys it does not know**, so a `fog_color` left in `effects` parses, logs
  nothing, and renders nothing. All four of this mod's biomes shipped that way for releases; #286
  changed the two frontier regions' fog specifically so they would stop looking identical at range, and
  only the grass and foliage half of that change ever ran. Nothing in-game names the problem - the
  biome just quietly keeps the default. The `attributes` map itself IS validated (an unknown attribute
  id is a hard registry error), so the failure is entirely on the `effects` side.
  `BiomeEffectsPlacementTest` now fails the build on a moved key found in `effects`, and it caught a
  fifth one - `ambient_sound` in the depths - on its first run.

- **`#minecraft:dirt` is only three blocks now** (dirt, coarse dirt, rooted dirt) - it does *not* contain grass, podzol, mud or moss, so 1.20-era guides that use it as "the dirt family" are wrong. The union that still means "overworld ground" is **`#minecraft:substrate_overworld`** (`#dirt + #mud + #moss_blocks + #grass_blocks`). This fails *silently* - a tag reference resolves fine and simply matches less than you expect, so it surfaces as a mechanic quietly not firing on most of its intended targets (see `RCTags.ENCROACHABLE`).
- `pack.mcmeta` uses the `min_format`/`max_format` range form (both `84`), not scalar `pack_format`.
- `Player.displayClientMessage` is gone - use `player.sendSystemMessage(Component)`. `Item.getName()` (no-arg) is gone - use `Component.translatable(item.getDescriptionId())`.
- `MobEffects.SLOWNESS` / `SPEED` (renamed from `MOVEMENT_*`). `Properties.noCollision()` (one `s`; also clears occlusion).
- Food is data-component driven: `Item.Properties.food(FoodProperties)`. `SuspiciousStewItem` / `MushroomStewItem` no longer exist.
- Tools come from `Item.Properties` helpers: `props.shovel(ToolMaterial.STONE, dmg, speed)`.
- **Item rendering needs `assets/<ns>/items/<id>.json`** client item definitions *in addition to* `models/item/`.
- **`minecraft:item/template_spawn_egg` is gone.** Spawn eggs stopped being a tinted two-layer template and became ordinary `item/generated` models with **their own PNG each**, so a mod egg now needs real art rather than two tint colours. A model parenting to the old template resolves to nothing and the egg renders as the missing model - visible in the creative tab and in JEI, and silent everywhere else. This is also why `RegistryCompletenessTests` verifies *vanilla* parents against `VANILLA_PARENTS`: vanilla assets are on the classpath of neither test layer (both were probed, not assumed), so the substitute is an allowlist checked against the client jar per MC version.
- **Atlases are split and a model can only use its own.** `atlases/blocks.json` stitches `textures/block/**`; `atlases/items.json` stitches `textures/item/**`. A *block* model referencing `<ns>:item/foo` renders as the pink/black missing texture even though the PNG exists. A sprite that feeds both (a cross-model plant and its icon) lives in `textures/block/` and the item model points there - vanilla does exactly this for `brown_mushroom`.
- **`CraftingMenu.stillValid` hard-codes `Blocks.CRAFTING_TABLE`.** A custom crafting station must subclass it and override `stillValid`, or the menu opens and closes on the first server tick, which looks exactly like "right-click does nothing" (see `ScrapCraftingMenu`). Expect the same pattern in other vanilla menus.
- **A custom bed needs two NeoForge overrides, and both fail silently.** Sleeping does *not* require `instanceof BedBlock`, but `IBlockExtension.isBed` must return true (else patched `LivingEntity.checkBedExists()` ejects the sleeper next tick) and `IBlockExtension.getRespawnPosition` must be overridden (its default `Optional.empty()` is exactly vanilla's "no respawn block available"). `HorizontalDirectionalBlock.FACING` is read unconditionally, and you must call `startSleepInBed` yourself. See `MattressBlock`. `BlockTags.BEDS` is *not* the answer - it gates only villager and cat AI.
- **`Player.displayClientMessage` doesn't exist**; use `sendOverlayMessage` for the action bar (what vanilla's bed uses) or `sendSystemMessage` for chat.
- **`BlockEvent.BreakEvent` is gone.** The player-breaks-a-block hook is now `net.neoforged.neoforge.event.level.block.BreakBlockEvent` (its own class in a new `event.level.block` package, not a `BlockEvent` inner class). It is still cancellable and still carries `getPlayer()` / `getState()`, so only the type and import move - but every 1.20/1.21 tutorial names the old one, and the compile error (`cannot find symbol: class BreakEvent`) does not hint at the replacement. `getPlayer()` should be null-checked. See `RCTorchFuel`.
- **`GameTestHelper.makeMockServerPlayerInLevel()` does NOT default to survival.** Its abilities have `instabuild` set, so any test whose subject exempts creative players silently asserts the exemption instead of the rule, and passes for the wrong reason. Call `player.setGameMode(GameType.SURVIVAL)` first.
- **`GameTestHelper.destroyBlock` passes `dropBlock=false`** - no loot table runs. A test asserting a block's drops must call `helper.getLevel().destroyBlock(abs, true)` or it asserts nothing.
- **`canSeeSky` is a light query, not a heightmap query**, and that makes any test that asserts on it after changing blocks a race. It is literally `getBrightness(LightLayer.SKY, pos) >= 15` (`BlockAndLightGetter`), and on a server the sky layer belongs to `ThreadedLevelLightEngine` **on its own thread** - its `runLightUpdates()` throws `UnsupportedOperationException("Ran automatically on a different thread!")`, so a test cannot drain lighting and **no fixed `runAfterDelay` is ever sound**; a longer delay only narrows the window. Use `helper.succeedWhen(...)`, which retries the assertion every tick and fails by timeout. This is not hypothetical: `solar_panel_makes_nothing_under_a_roof` waited 5 ticks, passed locally for months, and went red on CI reading full sky light with a stone block sitting on the panel. Pair any such test with its opposite (open sky generates) so neither half can pass vacuously.
- BlockEntity serialization uses `ValueOutput`/`ValueInput`, not `CompoundTag` (the Scrap Barrel and Rain Collector use it).
- **Fluids moved to the new transfer API - the old capability path is gone.** `Capabilities.FluidHandler.BLOCK` / `IFluidHandler` / `FluidTank` no longer exist as the capability (the nested `FluidHandler` class is not in 26.1's `Capabilities`). Use: `Capabilities.Fluid.BLOCK` returns `ResourceHandler<FluidResource>`; the tank is `FluidStacksResourceHandler(size, capacity)` (water-only via an `isValid` override); inserts/extracts are **transactional** - open a `Transaction.openRoot()`, run `insert`/`extract`, then `commit()`, else it rolls back; serialize with `tank.serialize(output.child("tank"))` / `deserialize`; the bucket helper is `net.neoforged.neoforge.transfer.fluid.FluidUtil`, NOT the old `fluids.FluidUtil`. See `RainCollectorBlockEntity`. **Productive-frogs (1.21.1) uses the old `IFluidHandler` API - do not crib its fluid code for 26.1.**
- **Registering a *custom* fluid is a second, separate set of changes, and the client half is where the tutorials break.** Registration is roughly familiar: `FluidType` into `NeoForgeRegistries.Keys.FLUID_TYPES` (registry id `fluid_type`, singular), and a `BaseFlowingFluid.Source` / `.Flowing` pair sharing one `BaseFlowingFluid.Properties(fluidType, still, flowing)` with `.block(...)` / `.bucket(...)`. **Rendering is not.** `IClientFluidTypeExtensions` has exactly four members in 26.1 - `getRenderOverlayTexture`, `renderOverlay`, `modifyFogColor`, `modifyFogRender` - and the `getStillTexture` / `getFlowingTexture` / `getTintColor` overrides that every 1.20/1.21 fluid tutorial is built around **do not exist**. Fluid textures are a model now: build a `FluidModel.Unbaked(still, flowing, overlay, FluidTintSource)` and register it on **`RegisterFluidModelsEvent`** (mod bus, client only, fires on a worker thread during model loading). Tint is a `FluidTintSource`, not an int from an override. A fluid with no registered model renders as `FluidStateModelSet`'s missing model rather than crashing, so the symptom is a pond of pink-and-black. Still/flowing sprites are animated, so each needs a `.png.mcmeta` alongside it, which the texgen pipeline does not currently produce.
- **`FluidType.Properties.canHydrate` defaults to `false`, and farmland irrigation runs through it - not through `#minecraft:water`.** `FarmlandBlock.isNearWater` (renamed from `FarmBlock` in 26.1) calls `BlockState.canBeHydrated` -> `FluidState.canHydrate` -> `FluidType.canHydrate`, plus a second independent path via `FarmlandWaterManager.hasBlockWaterTicket`. So a custom fluid cannot wet farmland unless someone opts in, and no fluid *tag* edit can stop one that has. This matters here because `RCEncroachment`'s one blockstate rule is *wet farmland holds, dry farmland is taken* - a fluid that hydrates is permanent free encroachment immunity for every plot in range (see #156).
- Custom worldgen: `noise_router` requires `preliminary_surface_level` (16 fields, not 15); biome `carvers` is a flat list; biome `features` is 11 arrays (index 9 = vegetal_decoration). A world preset must be **selected** at world creation via the World Type button - a default world silently ignores it, the #1 cause of "worldgen isn't working."
- **A custom furnace is an `AbstractFurnace{Block,BlockEntity}` subclass + vanilla `FurnaceMenu`** (see `BurnBarrelBlock`/`BurnBarrelBlockEntity`). `AbstractFurnaceBlock` supplies FACING, the `LIT` state, placement, and open-on-use; the BE takes a `RecipeType` in its ctor (`RecipeType.SMELTING` for a plain furnace) and only implements `getDefaultName` + `createMenu` (`new FurnaceMenu(id, inv, this, this.dataAccess)`). **To make it manual-only (no hopper / Create automation), override `getSlotsForFace` to return an empty `int[]`** (and `canPlace/TakeItemThroughFace` -> false): a furnace is a `WorldlyContainer`, so empty faces cut off all automation while the GUI still loads by hand. Fuel is the `data/neoforge/data_maps/item/furnace_fuels.json` data map (`{"values": {"<id>": {"burn_time": N}}}`), read live via `level.fuelValues().burnDuration(stack)`.
- **Carry BlockEntity state through break+replace with an item data component, not just `saveAdditional`.** `saveAdditional`/`loadAdditional` survive *save/load* only; breaking the block destroys the BE, so its state is lost on pickup. To keep it on the dropped item (the Rain Collector's water), register a `DataComponentType` (`RCDataComponents`), write it in `BlockEntity.collectImplicitComponents` (read back in `applyImplicitComponents` on placement), and copy it onto the drop with a `minecraft:copy_components` loot function (`"source": "block_entity"`, `"include": [...]`) - the mechanism vanilla beehives use for bees. See `RainCollectorBlockEntity`.

## Three cross-mod stopgaps, which are pack content living in the engine

**The pack cannot ship data on 26.1.2** - no datapack loader has a NeoForge build, KubeJS crashes the
client, CraftTweaker has not ported - so things that belong to Trashlands ship here instead. **This
cuts against the engine/pack split rather than revising it**, and they all leave when KubeJS is fixed
(`Flatts3000/trashlands#46`, `#47` and `#52`). Specs and removal instructions:
`docs/handoff_ae2_presses_sewer_loot.md`, `docs/handoff_simple_magnets_recipes.md`,
`docs/handoff_enderio_grains_and_blaze.md`.

**Ender IO needed no sourcing work, unlike AE2**, and that is worth knowing before anyone re-audits it:
a reachability closure over its 1187 recipes puts 897 of 924 items in reach from a vanilla-only seed,
its whole alloy spine included, and it makes its own silicon by SAG-milling sand. What ships for it is
a Grains of Infinity find in Mechanical Waste (#279, owner call - the material was already obtainable
via Ender IO's own fire crafting on deepslate), plus the two invariant fixes below.

**The third one SUBTRACTS rather than adds, and that is a shape worth knowing** (#280, owner
2026-08-21). Ender IO's SAG Mill grinds a blaze rod back into **four** blaze powder. This mod's chain
runs the other way - four powder press into a Blaze Briquette and the Sintering Kiln fires it into one
rod - so that recipe alone makes the round trip break even, which is exactly what the Briquette exists
to prevent. It is worse than break-even in practice: Ender IO's `data_maps/item/grinding_ball.json`
runs from 1.0 up to an **OutputMultiplier of 1.75** on the vibrant alloy ball, so a rod returns up to
**seven** powder against the four it cost - a 75 percent gain per automated cycle. Blaze rods gate
brewing here.

**There is no remove-recipe primitive, so a disable is an override that never loads.** A file at
another mod's recipe id replaces it wholesale (only the top file at a path is read), and a
`neoforge:never` condition means the replacement itself is skipped - net, the id is gone. The body
still has to be well-formed JSON but is never decoded, which is the same mechanism that lets a guarded
recipe safely name an absent mod's items. Two things make it work and both are silent if missed:
`ordering = "AFTER"` on an optional `enderio` dependency (without it Ender IO's file stays on top and
nothing is logged), and the condition itself. `every_cross_mod_override_is_ordered_after_its_mod` now
pins the ordering for **all three** mods - it was unasserted for the other two as well, described in a
test message without ever being checked - and `the_blaze_grinding_override_can_never_load` pins the
condition.

**A found-only item gained a route and it was accepted rather than fixed** (owner, 2026-08-21).
Emptying an experience bottle in an Ender IO tank hands back a `minecraft:glass_bottle`, which is in
`#recompile:found_only`. It is a container conversion rather than manufacture - you already had the
bottle - so `enderio:tank` joins `RETURNS_ITS_OWN_INPUT` in `FoundNotCraftedTests`. The caveat is
recorded there rather than glossed: an experience bottle can be **bought**, so a player with emeralds
has a narrow route that does not involve finding one, judged the same shape as the wandering trader's
saplings.

**None of this is visible to CI, which has no Ender IO**, so both defects were found by dropping the
jar into `run/mods` and running the suite - and the guards that caught them are this mod's own. Worth
repeating for the pack's other majors. One caveat when doing so: **about 58 unrelated tests fail with
Ender IO present** because it registers a payload the headless harness refuses (`Payload
enderio:powered_spawner_soul may not be sent to the client`), which breaks every test using a mock
player. Filter those before reading a red run as a regression.

- **AE2 is playable here, and it takes both halves to be so.** The **four Inscriber presses** are a
  pool on `chests/sump.json` plus a lang override correcting AE2's own tooltip - that was #270, and it
  cleared ONE of two gates while being reported as clearing both (#276). The second gate is the
  materials: 330 of AE2's 364 items have a recipe and everything traces back to
  `certus_quartz_crystal`, whose only non-circular source is a `quartz_cluster`, which drops only from
  budding blocks, which generate only inside a meteorite. AE2's entire worldgen is
  `structure/meteorite.json`, no AE2 chest table carries certus, and so the presses gave a player an
  Inscriber and nothing to put in it.
  **#277 closed it with four routes, none of them a find**: silicon separates out of E-Scrap, certus
  out of the demolition yard's granite, fluix likewise, and Sky Stone Shards ride the slag rubble
  stream. Manufacture rather than a drop because the owner puts a playthrough at 4 to 8 stacks each of
  certus and fluix, and only a machine produces at that scale. *(This entry said "unstartable" for a
  fortnight after the thing that started it had shipped - it was written for the #270 state and #277
  never came back to it.)*
  **No meteorites, and that ruling stands.** Meteorites gate on `#minecraft:is_overworld` and this mod
  ships **no entry for it, by owner ruling 2026-08-20**. Adding the tag would fix AE2 and every other
  mod keyed on it at once, and that breadth is the objection: it admits anything gating worldgen on it,
  sight-unseen, into a closed economy. Reopen only if a vanilla mechanic turns out to be silently not
  firing, or if enough mods are blocked that per-mod handoffs stop scaling. The sourcing routes are
  what made that ruling affordable.
- **Simple Magnets' four recipes** are overridden onto Magnet Scrap at that mod's own recipe ids.

Three traps live here, all measured rather than reasoned about:

- **An unresolvable ITEM id kills a whole loot table at parse; a TagKey does not resolve at parse
  time.** Naming `ae2:silicon_press` in `sump.json` without AE2 gives `Unknown registry key` and takes
  the entire file down - the crate at the bottom of every sewer comes up empty, which reads in-game as
  bad luck. A `minecraft:tag` entry is inert instead, and `expand: false` yields EVERY member per roll
  rather than picking one, which is how AE2's own `mysterious_cube` hands the set over.
- **`neoforge:conditions` gates a whole loot table FILE, not a pool or an entry inside one.** So a
  mod-gated loot entry is not available; the tag entry is what makes the guard unnecessary. On a
  RECIPE the condition does work, and it is load-bearing: strip it and the file fails to PARSE on its
  own result id.
- **Language files MERGE; recipes and resources do not.** The client applies every lang resource for a
  namespace in ascending priority, so a one-key `assets/<their-ns>/lang/en_us.json` override only has
  to be LATER, not complete. A recipe at another mod's id is a whole-file replacement and only the top
  file at a path is read at all - which also means a typo there does not degrade to their recipe, it
  deletes the id. Both need `ordering = "AFTER"` on an optional dependency; `neoforge.mods.toml` now
  carries four such entries (neoforge, simplemagnets, ae2, enderio).

**CI cannot see either override working**, because neither mod is present at test time. What the tests
assert is inertness WITHOUT the mod, plus the reason for it; the with-mod half was verified by dropping
the jars into `run/mods` and running the gametest server against them.

## Design lives in the Trashlands repo (source of truth)

Feature design is decided there, not here. Read before changing gameplay:

- `../trashlands/docs/design_decisions.md` - per-feature locked decisions + session bookmark. **Start here.**
- `../trashlands/docs/concept.md` - the vision (plus cross-medium influences: Planet Crafter, WALL-E).
- `../trashlands/docs/material_economy.md` - metals/gems/mod-spine sourcing.
- `../trashlands/docs/the_twist.md` - **FULL SPOILERS** (the hidden narrative hook). Never restate its contents in player-facing copy, and never hint at it in any other doc.

**Engine / pack split:** Recompile is the engine (systems, the public schema, tag-driven defaults). Trashlands is the pack (curation, quests, tuning, most cross-mod teardown tables). The mod never *requires* Create or Mekanism; it ships standalone config fallbacks.

**What a pack can change without a mod release is written down**: `docs/pack_extension.md` - the three sewer loot tables and every pull stream, the six public recipe types, the tag surface, and how to add guidebook entries from another namespace. It also states what a pack CANNOT reach from data, which is the half people discover the hard way: the viewers read the mod's bundled JSON rather than the live registry, so a retuned pull stream is right in-world and stale in JEI.

## Conventions

- **No em-dashes or en-dashes, no emoji** in any authored text (hard rule). ASCII punctuation only.
- **Minimize authored prose.** Only quests and technical guidance get writing; players distrust AI writing. Carry meaning through mechanics.
- Data-driven first: tuning, drop rates, and variants belong in JSON, not Java.
- Conventional commits (`feat(food):`, `fix(...)`, `docs:`). Phases land as squash-merged PRs.
- The mod was working-named "Salvage" during design; renamed because several materials-recovery mods already own that name on CurseForge/Modrinth - exactly the mods this would be confused with.

## Driving a running game from outside (gamebridge / devbridge)

**devbridge is its own repo** (`F:\devbridge`, MIT), and its onboarding doc (`docs/onboarding.md`
there) is the reference - this section is only what is specific to this repo. It is two halves of one
wire protocol: a dev-only NeoForge jar and the `gamebridge` Python client, kept in one repo so they
cannot drift.

**Already wired here, but the world has to exist first.** Run `python tools/make_dev_world.py` once:
it drives `runServer` to generate a world with **this mod's preset**, stops it over RCON, installs it
at `run/saves/devworld` and sets `confirmedExperimentalSettings` in its `level.dat`. After that
`./gradlew runClient` opens the devbridge socket on **8605** and boots straight into it.

**Three separate things block that boot and NONE of them logs anything** (#289), which is why this
paragraph is long. Quick play does not create a missing world - it shows a *"Could not find world with
the provided identifier"* screen. A custom-preset world is flagged **experimental**, so the client
stops on a *"Here be dragons!"* confirmation. And moddev **quotes any program argument containing a
space** into `clientRunProgramArgs.txt`, so the old `'New World'` reached the game as `'"New World"'`
and matched no directory. All three are screens or silent mismatches, so the only symptom is a client
sitting at a menu with a socket that never opens; four debugging attempts and one wrong issue went by
before anyone looked at the game window. Do not let the client create the world itself either - a world
made through the GUI is a vanilla default one that ignores `recompile:garbage` silently.

Also: `run/mods/devbridge-26.1.2-0.5.0.jar` is the mod half (`run/` is gitignored, so a fresh
clone needs the jar from that repo's Releases).

```bash
pip install "gamebridge @ git+https://github.com/Flatts3000/devbridge.git#subdirectory=gamebridge"

gamebridge --devbridge 8605 cmd "function recompile:showcase/museum" --player @s
gamebridge --devbridge 8605 shot museum

# Or RCON against ./gradlew runServer, which needs no mod at all.
bash tools/verify_showcase.sh     # places the museum and asserts it landed
```

**8605 is claimed for this repo**, deliberately not devbridge's own 25580 default. Every project that
keeps the default shares one socket, and that has already produced a wrong answer rather than an
error: Trashlands' quest verifier connected to *this* client and reported six item ids resolving, a
clean pass about the wrong game. Claiming prevents a clash but cannot detect one - `ports check`
enumerates IPv4 only while devbridge binds `getLoopbackAddress()` (`::1` here), so the registry calls
the port free while a game holds it. **For the same reason the client must dial `localhost`, never the
`127.0.0.1` literal**, which otherwise gets connection refused from a socket the log says is listening.

**So a tool that must be sure asks what answered - and since devbridge 0.2.0, `ping` can tell it.** It
reports the game's own `gameDir` and world name, and the client checks it: `ping(expect_instance=...)`
raises rather than let you drive the wrong game. `tools/shoot_scenes.py` passes `run/`, which is the
moddev default game directory and NOT the repo root - pointing it at the repo fails the check against
the game that is behaving correctly. This replaced a hand-rolled sentinel here (a Recompile-only item
id handed to the command parser, which rejects an unknown item as a *parse* error); same guard, now
the tool's job.

The point is **verification**: `gamebridge check "entity @e[type=minecraft:painting]" --count 6` exits
non-zero, which is a check a screenshot cannot make - a painting that fails to hang deletes itself
silently and just leaves the picture looking emptier.

**Two limits that are properties of Minecraft, not of the tool.** RCON cannot reach a singleplayer
world (its integrated server listens on nothing) or take a screenshot (a dedicated server has no
framebuffer); that is what devbridge is for. And chunks unload with nobody standing in them, so a
playerless server answers `data get block` with "That position is not loaded" and otherwise looks like
it worked - `forceload add` first.

**Commands run as the console unless you pass a player, so `@s` matches nothing and `~` is
spawn-relative.** Every showcase function ends in `tp @s`, so driving one over the bridge places the
set correctly and silently does not move the camera. **The fix is `--player @s`** (`player=` from
Python), which devbridge resolves to the only player online; an unmatched name fails loudly rather
than falling back to the console. `tools/shoot_scenes.py` passes it on every command.

Beyond `cmd` and `shot` the verbs are `ping`, `hud`, `input`, `look`, `pause`, `stop`, since
0.2.0 `screen`, `cursor`, `click` and `log`, and since 0.6.0 `use` - so a tool can drive an open GUI and read what the game logged.
A toggle with no argument restores vanilla behaviour (`hud on`, `input on`, `pause on`).

**0.2.0 reversed the mouse default and it matters here.** Loading a world still turns off
pause-on-lost-focus - an unfocused singleplayer client stops ticking and would answer nothing - but it
no longer takes the mouse unless asked, because a person who opened the game to fly around has no idea
a mod took it and the symptom looks like a broken game. An unattended run has to ask: `shoot_scenes.py`
calls `input(False)` explicitly, or a stray alt-tab between framing a shot and grabbing it silently
changes the picture, and gives it back in a `finally`.

**`shot` can capture at an exact size** (0.2.0), resizing the window for the moment of the grab. The
`--width 1920 --height 1080` in the client run block predates that and is now belt-and-braces rather
than the only way to get a usable frame.

**devbridge must never ship.** It binds loopback only and is inert without `-Ddevbridge.port`, but it
executes arbitrary commands and is deliberately a separate jar rather than a dependency.

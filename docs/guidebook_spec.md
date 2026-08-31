# In-game guidebook - build spec

> **Status: SHIPPED.** The book landed as seven chapters on 2026-07-27 (#29, PR #35) and has
> **The map below lags the book.** It is the original contract and entries have been added without it
> since - the sewers, clay, gold, the Trommel and the Pulverizer are all in the book and not in the
> table. Read it as the plan it was, not as an inventory of what ships.
>
> grown to **eleven categories**; an audit on 2026-08-02 (PR #105) found and filled twelve
> systems that had shipped without an entry, and the four multiblock render pages landed the
> same week (#37, PR #111). This stays the contract for the work: the engine, the dependency
> posture, the voice, the content map (every mechanic that deviates from vanilla), and
> verification. The build order below is the order it was actually built in.

## The governing rule

**The guidebook exists to educate and guide, not to entertain.** It teaches a player
how to play a world that does not behave like vanilla Minecraft. One rule decides what
goes in:

> **If a mechanic deviates from vanilla, it earns an entry. If it behaves exactly like
> vanilla, it does not.**

A player who knows Minecraft already knows how to mine stone, craft at a table, and
smelt in a furnace. What they do not know is that this world has no trees, that garbage
is picked through one item at a time, that farmland is crafted not tilled, that the
junkyard eats reclaimed ground back. Those are the entries. Anything a player would do
identically in vanilla is out (see Out of scope).

## Engine and dependency posture

Same engine and posture as productive-frogs (`../productive-frogs/docs/guidebook.md`).

- **Engine: Modonomicon** (`com.klikli_dev:modonomicon-26.1.2-neoforge:2.1.0`) - stable
  26.1.2 release, MIT, JSON book/category/entry/page model.
- **Book id:** `recompile:guide` (stable forever - packs key off it).
- **Maven:** Cloudsmith `https://dl.cloudsmith.io/public/klikli-dev/mods/maven/`
  (`content { includeGroup "com.klikli_dev" }`). **Installed** in `build.gradle`.
- **Gradle:** `runtimeOnly(...) { transitive = false }` - **installed**. The book is pure
  data/assets, no Java plugin, no compile-time reference, so no `compileOnly`. `runtimeOnly`
  pulls Modonomicon into dev runs so `runClient` renders the book; it does **not** bundle
  into the published jar. No `run/mods` drop-in to double-load against (unlike Jade), so
  `runtimeOnly` is safe.
- **Data layout:** `data/recompile/modonomicon/books/guide/` - `book.json`, `categories/`,
  `entries/<cat>/`, `entries/<cat>/<entry>/pages/`. Modonomicon scans the fixed
  `modonomicon/books` folder under every namespace (not a double-namespace). Confirm the
  exact 2.x schema against the live docs at skeleton stage - it differs from Patchouli.
- **Obtaining in-game:** a craftable guide item (from Junk or a base material), recipe gated
  `neoforge:conditions -> mod_loaded: modonomicon`. Inert data if the engine is absent,
  live if present. Modonomicon's own book-open path also works.

## Voice

Source of truth is the toolkit: `../mc-pack-toolkit/quest-voice/voice_spec.md`, the
**Guides** surface. This section only calibrates it to this book.

- **Register:** explanatory reference - calm, factual, second or third person, more complete
  than a quest. No marketing verbs, always concrete. Every sentence carries real information;
  **length is never license to pad.**
- **Educate and guide, nothing else (maintainer directive).** No prose written to entertain,
  no flavor, no wit. This is stricter than the general Guides surface: **entry titles are
  plain functional labels here** - the subtitle-personality slot (voice-spec Do #4) is *not*
  used in this book. A title names the thing; the body teaches it.
- **Decompose every entry** like a quest, at system scale: name the **teach payload** (what a
  player must learn to use this system - the concept, the non-obvious mechanic, the one
  gotcha) and the **guide payload** (what to point an experienced player at - the specific
  block, the number that matters). Write only those two. If JEI/Jade already shows it, it is
  not the teach payload.
- **Tables and diagrams over prose** wherever a layout is clearer: the base-materials table,
  the reclamation ladder, the multiblock patterns, the pull-stream sources.
- **The spine test, by hand:** would someone who actually played this write it, or does it
  read like a marketer describing the mod? The linter cannot judge register.
- **Lint as a backstop:** `python ../mc-pack-toolkit/quest-voice/lint_quest_voice.py <dir>`.
  Clean lint is necessary, not sufficient.
- **House rule:** no em/en-dashes anywhere in the copy.

## Scope: the chapter map

Every entry below teaches a specific vanilla-deviation. `sortnum`s spaced 100 apart so packs
can slot content between. Ordered by discovery - the sequence a player actually lives.

| # | Category | Entries | The vanilla-deviation it teaches |
|---|----------|---------|----------------------------------|
| 1 | **Getting Started** | The Garbage World; The core loop; No table, sealed dimensions | You **must pick the "Garbage World" world type** at world creation (World Type button) - a default world ignores the preset. Coarse-dirt slab, no ores, no trees, no mobs. Materials come from garbage, not mining. No vanilla crafting table (no wood) -> Scrap Crafting Table. The **Nether is open**; the End is sealed until later. |
| 2 | **Sorting & Materials** | Pick-through blocks; The base materials; Sorting Tarp | Right-click a Block of Garbage to pull **one** drop at a time; it crumbles after a few pulls. Trash Bag and Compacted Bale are richer streams with their own tools; the Cardboard Pile is a bare-hand one that yields Cardboard. Garbage falls like gravel. The Sorting Tarp sifts held garbage onto the ground (no GUI, no storage). |
| 3 | **Tools** | The salvage tools | Prybar (opens Bulky Waste), Scrap Knife (cuts cans/mattresses), Junk Shovel - crafted from base materials, not the vanilla wood/stone/iron ladder. |
| 4 | **Bulky Waste & Finds** | Prying open a find | Pry Bulky Waste with a Prybar: one action, drops a find, breaks. Bare-hand mining yields nothing. Finds (mattress, appliances) are the only source of machine parts. Nothing enters the world without a teardown exit. |
| 5 | **The Recompile Workbench** | Breaking things down | Rack a Scrap Knife or Prybar, hold right-click an item to tear it into materials. (Materials teardown only; the knowledge/recipe axis is Phase 3, unbuilt - see Out of scope.) |
| 6 | **Storage & the Scrap Network** | Scrap Barrel & Bin; The Scrap Network | No wood means no vanilla chest/barrel early; use the Scrap Barrel, and a Scrap Bin that binds to one item type. Place scrap blocks touching and junk **routes between them** on a file-all - no pipes, no GUI. |
| 7 | **Food** | Tin Can; Dump Mushrooms | Cut a Tin Can open with a Scrap Knife to eat it. Forage and **replant** Dump Mushrooms. There are no animals - food is not from mobs. |
| 8 | **Smelting: the Burn Barrel** | The manual smelter | A furnace variant that takes **no hoppers or automation** (manual only); fuel is Oily Rag and the data-mapped fuels, not just coal. |
| 9 | **Lighting & sleep** | Scrap Torch; Sleeping | Lit from Oily Rag fuel, not coal+stick. One item places the floor and wall forms. A **mattress is the bed** - a vanilla bed needs planks, so the Bulky Waste find is the bed rather than a stand-in for one, and it sets respawn the same way. |
| 10 | **Building Blocks** | The material families | Pressed Junk, Scrap Plating, Corrugated Metal, Plastic Panel, Cardboard, Cullet Glass (base/slab/stairs/wall/pane) - the sinks that absorb bulk materials, plus Cardboard, which is the one reachable before there is any bulk to sink. |
| 11 | **Water: the Rain Collector** | Catching rain | Rain is the **only** fresh water. Build a Rain Collector under open sky; draw water with a bucket or bottle. Water survives break + replace. |
| 12 | **Multiblock Machines** | How they assemble | Place the **core** to auto-build from parts in your inventory, or stack the components by hand; a grey/red preview shows the footprint; break any cell to disband. (Frames the Rain Collector, Grass Spreader, Compost Heap, Tree Nursery, each of which carries a render page of its structure.) |
| 13 | **Reclamation** | Encroachment; Grass Spreader (rung 1); Vegetation (rung 2); Farming (rung 3); Compost Heap; Saplings are machine-only | The junkyard **eats reclaimed ground back**: border grass reverts to coarse dirt, plant cover is stripped first, a dry farm plot is taken (a watered one holds). Rung 1 waters coarse dirt straight to grass (a Grass Spreader tower). Rung 2: Fertilizer on grass scatters weeds/wildflowers. Rung 3: Fertilizer + dirt **crafts** farmland (a hoe won't till the dump); seeds are compost volunteers; keep plots irrigated. The Compost Heap (2x2x2 cage) makes Fertilizer from organics, layer by layer. **You will not find a sapling** - none drops from anything; the **Tree Nursery** (rung 4) is the only thing that grows one. (A wandering trader sells them for emeralds since #227, which is why the book's copy says "find" rather than "hold".) **Animal Bait** (rung 5) is how animals return: place it on reclaimed grass and walk away, and the Rich grade brings a bonded pair. |
| 14 | **Collectibles** | The Puzzle Cube; Found curios; The Display Pedestal | Find nine Puzzle Cube pieces in the garbage and craft the cube (craft it with itself to scramble/solve). Rare whole finds (avocado, present, gold coin, toy car). The Display Pedestal floats and spins any item. **Recovered paintings** are six specific works found in Bulky Waste that keep their identity through break and replace, unlike a vanilla painting. |
| 15 | **The Demolition Yard** | Travelling Out; Reinforced Concrete; Steel & the Cutting Torch; The Cupola Furnace | Biomes are placed by **distance from origin**, not climate: household is guaranteed within 512 blocks and the yard starts past it, so **travel is the gate**. A Sledgehammer is the only tool Reinforced Concrete answers to, and its sand was the **only sand in the world** until v0.11.0 put suspicious sand in the sewers (both are glass); it is still the only **red** sand you can dig. A Steel I-Beam only parts under a Cutting Torch, charged with Oily Rags (1 rag = 8 cuts, holds 64). Offcuts and rebar become iron **only** in a Cupola Furnace: these are blast recipes, so no ordinary furnace can run them and the Burn Barrel refuses metal outright. |
| 16 | **Power & Automation** | Power; Solar Panel & Burner Generator; The Hydroponics Bay | Generators **push into whatever they touch** - no cable, no network. Each machine buffers its own, which is why it keeps going after its generator stops. Solar needs open sky and fades at dusk; the Burner burns anything a furnace would. The Hydroponics Bay needs water **and** power at once, keeps the one plant you give it, takes seed crops **as their seed**, and has a second harvest slot for byproducts. |
| 18 | **The Compacted Depths** | Getting there; the solid fill; terrain from shards; scrap and which machine takes it; lignite and coal; the forging die and netherite | Added #252. Almost nothing in that dimension behaves like vanilla, which is the test this book applies. |
| 17 | **Config & viewers** | Config toggles; JEI & Jade | The one entry that enumerates the config gates (gravity, encroachment, dimension lockout, ...) for pack authors. JEI's Sorting/Cutting/Prying categories and Jade's tool-hint / sort-progress tooltips surface the non-vanilla mechanics in-game. |

## Out of scope

- **Not yet built - document when it ships:** the **knowledge/recipe axis** of teardown
  (Phase 3 - only materials teardown exists at the workbench today); **mound regrowth**
  (Phase 5); **themed dimensions** (Phase 6). No entries until they ship. *The tree planter used
  to be listed here; it shipped as the **Tree Nursery** and now has its own entry, and the
  saplings entry was corrected - it still described the planter as a later tier.*
- **Pure vanilla - never restate:** anything a player does identically to vanilla. Standard
  crafting once you have the Scrap Crafting Table, mining stone at depth, the vanilla furnace
  UI the Burn Barrel reuses, bucket/bottle mechanics. Teach only the deviation, not the
  vanilla base it sits on.
- **Internal - never document:** registry/lifecycle wiring, the blockstate-flyweight `sorted`
  property, the encroachment sweep's neighbour test, the Scrap Network BFS, GameTest layout.
  The guide documents **observable behavior**, not implementation.

## Config-gated policy

Cover every shipped feature regardless of default state. A gated entry carries one factual
line - "off by default; a pack enables it in the config" - never hype, never a full config
dump. The **Config toggles** entry (category 15) is the one place that enumerates the flags,
for pack authors.

## Multiblock sourcing (no drift)

**Settled (#37).** Modonomicon reads its patterns from data files
(`data/recompile/modonomicon/multiblocks/<machine>.json`, a `modonomicon:dense` pattern), so
the shape necessarily lives in two places: that file, and the `Multiblock` blueprint in Java
that actually validates and assembles. There is no seam to source one from the other at
runtime, so the second half of the original instruction applies: **`GuidebookMultiblockTests`
locks them together**, comparing the pattern's cells against `blueprint().cells()` in both
directions, and failing if a page draws a machine that would not form. A separate check makes
adding a fifth machine's page without extending that test a failure rather than a silent gap.

Three facts the four shipped patterns encode:

- **Layers run top-first.** `DenseMultiblock` stores `stateMatchers[x][height - 1 - y][z]`, so
  `pattern[0]` is the highest layer. (This is also why Modonomicon's own demo patterns put
  their decorative floor last.) The lock test decodes them the same way, so it cannot catch an
  upside-down reading - that one is confirmed by source, and by looking at the page.
- **Within a layer, each string is one X and each character is one Z**, Patchouli's convention.
- **The page draws the loose components, not the formed machine.** A formed cell becomes the
  machine's bespoke block, so projecting onto an already-built machine reads as unsatisfied.
  That is correct: the projection is a build aid.

The directory is `multiblocks`, **plural** - Modonomicon's own folder, not one of the vanilla
data dirs 26.1 singularised. Same trap as `loot_modifiers`.

## Build order

Prove the engine before committing the full content rewrite.

1. **Skeleton proof-of-fit** (one PR): the Gradle dep is wired [done]; add `book.json` +
   landing entry; two categories (Getting Started + Reclamation); one text entry (the core
   loop, proving text/spotlight pages); **one multiblock page** (the Grass Spreader or Rain
   Collector, proving the highest-risk page type against a real blueprint); the
   `mod_loaded:modonomicon`-gated guide recipe + lang keys. Verify by `runClient`.
2. **Content chapters** in discovery order (the table above), decomposed per the voice
   section. Each category is a reasonable PR unit.
3. **Pack-extension contract:** once category ids are stable, document them for pack authors
   (which ids exist, how a pack appends entries under `data/<pack>/modonomicon/books/guide/`).

## Verification

The guide is **client-render-only** - GameTest and the JUnit build are blind to it. Every
check is a manual `runClient` pass: the book opens, categories render in `sortnum` order,
text/spotlight/multiblock pages draw, each multiblock diagram matches the in-world structure,
icons resolve (a wrong item id renders a silent missing-texture), and the guide recipe appears
only with Modonomicon present.

## Open items (not blockers)

- Decide the guide item + its recipe (which base material gates it).

*Two items were closed by #37: the Modonomicon 2.x page schema is confirmed against the shipped
book, and multiblock pattern sourcing is settled above.*

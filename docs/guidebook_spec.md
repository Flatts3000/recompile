# In-game guidebook - build spec

> **Status: SPEC / not yet built.** Tracks issue #29. This is the contract for the
> guidebook work: the engine, the dependency posture, the voice, the content map
> (every mechanic that deviates from vanilla), the build order, and verification.

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
| 1 | **Getting Started** | The Garbage World; The core loop; No table, sealed dimensions | You **must pick the "Garbage World" world type** at world creation (World Type button) - a default world ignores the preset. Coarse-dirt slab, no ores, no trees, no mobs. Materials come from garbage, not mining. No vanilla crafting table (no wood) -> Scrap Crafting Table. Nether/End are sealed until later. |
| 2 | **Sorting & Materials** | Pick-through blocks; The base materials; Sorting Tarp | Right-click a Block of Garbage to pull **one** drop at a time; it crumbles after a few pulls. Trash Bag and Compacted Bale are richer streams with their own tools. Garbage falls like gravel. The Sorting Tarp sifts held garbage onto the ground (no GUI, no storage). |
| 3 | **Tools** | The salvage tools | Prybar (opens Bulky Waste), Scrap Knife (cuts cans/mattresses), Junk Shovel - crafted from base materials, not the vanilla wood/stone/iron ladder. |
| 4 | **Bulky Waste & Finds** | Prying open a find | Pry Bulky Waste with a Prybar: one action, drops a find, breaks. Bare-hand mining yields nothing. Finds (mattress, appliances) are the only source of machine parts. Nothing enters the world without a teardown exit. |
| 5 | **The Recompile Workbench** | Breaking things down | Rack a Scrap Knife or Prybar, hold right-click an item to tear it into materials. (Materials teardown only; the knowledge/recipe axis is Phase 3, unbuilt - see Out of scope.) |
| 6 | **Storage & the Scrap Network** | Scrap Barrel & Bin; The Scrap Network | No wood means no vanilla chest/barrel early; use the Scrap Barrel, and a Scrap Bin that binds to one item type. Place scrap blocks touching and junk **routes between them** on a file-all - no pipes, no GUI. |
| 7 | **Food** | Tin Can; Dump Mushrooms | Cut a Tin Can open with a Scrap Knife to eat it. Forage and **replant** Dump Mushrooms. There are no animals - food is not from mobs. |
| 8 | **Smelting: the Burn Barrel** | The manual smelter | A furnace variant that takes **no hoppers or automation** (manual only); fuel is Oily Rag and the data-mapped fuels, not just coal. |
| 9 | **Lighting** | Scrap Torch | Lit from Oily Rag fuel, not coal+stick. One item places the floor and wall forms. |
| 10 | **Building Blocks** | The material families | Pressed Junk, Scrap Plating, Corrugated Metal, Plastic Panel, Cullet Glass (base/slab/stairs/wall/pane) - the sinks that absorb bulk materials. |
| 11 | **Water: the Rain Collector** | Catching rain | Rain is the **only** fresh water. Build a Rain Collector under open sky; draw water with a bucket or bottle. Water survives break + replace. |
| 12 | **Multiblock Machines** | How they assemble | Place the **core** to auto-build from parts in your inventory, or stack the components by hand; a grey/red preview shows the footprint; break any cell to disband. (Frames the Rain Collector, Grass Spreader, Compost Heap.) |
| 13 | **Reclamation** | Encroachment; Grass Spreader (rung 1); Vegetation (rung 2); Farming (rung 3); Compost Heap; Saplings are machine-only | The junkyard **eats reclaimed ground back**: border grass reverts to coarse dirt, plant cover is stripped first, a dry farm plot is taken (a watered one holds). Rung 1 waters coarse dirt straight to grass (a Grass Spreader tower). Rung 2: Fertilizer on grass scatters weeds/wildflowers. Rung 3: Fertilizer + dirt **crafts** farmland (a hoe won't till the dump); seeds are compost volunteers; keep plots irrigated. The Compost Heap (2x2x2 cage) makes Fertilizer from organics, layer by layer. **You can never hold a sapling** - trees are machine-planted only. |
| 14 | **Collectibles** | The Puzzle Cube; Found curios; The Display Pedestal | Find nine Puzzle Cube pieces in the garbage and craft the cube (craft it with itself to scramble/solve). Rare whole finds (avocado, present, gold coin, toy car). The Display Pedestal floats and spins any item. |
| 15 | **Config & viewers** | Config toggles; JEI & Jade | The one entry that enumerates the config gates (gravity, encroachment, dimension lockout, ...) for pack authors. JEI's Sorting/Cutting/Prying categories and Jade's tool-hint / sort-progress tooltips surface the non-vanilla mechanics in-game. |

## Out of scope

- **Not yet built - document when it ships:** the **knowledge/recipe axis** of teardown
  (Phase 3 - only materials teardown exists at the workbench today); **mound regrowth**
  (Phase 5); **themed dimensions** and the **tree planter** (Phase 6 - the saplings entry
  states the machine-only rule but the planter itself is future). No entries until they ship.
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

Recompile's multiblocks (Rain Collector, Grass Spreader, Compost Heap) are defined by the
`Multiblock` blueprint in Java - the **single source of truth** for validation, assembly, and
the GameTests. The guide's multiblock pages must match that blueprint. Unlike PF's altar
`.nbt` structures, these are blueprint-defined (offset -> component), so at skeleton stage
**confirm whether Modonomicon's multiblock page can build from a pattern we author, or needs
an inline mapping**; either way keep it in sync with `Multiblock.java`, or lock it with a
GameTest the way PF locks its altar `.nbt`s.

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

- Confirm the exact Modonomicon 2.x JSON schema (book/category/entry/page/multiblock fields)
  against the live docs at the skeleton stage.
- Confirm how a Modonomicon multiblock page sources its pattern (author-supplied vs imported)
  and wire it to `Multiblock.java` so the two cannot drift.
- Decide the guide item + its recipe (which base material gates it).

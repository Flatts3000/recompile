# Recompile

A standalone **NeoForge** mod (target MC 26.1). Its core is **teardown-as-knowledge**: tear an item down and you recover not just its materials but its **recipe** - you reverse-engineer how the old world was made by picking through its pieces. Unlearned technology can't be crafted until you have studied it.

This is the distinct axis. Plenty of mods recycle equipment back into materials; Recompile recovers the *know-how*.

## What's in the mod

- **Teardown-as-knowledge** - a workbench where you disassemble found items for components plus a chance to study their recipe. Deterministic study points (repeat teardowns complete the study); learned recipes unlock permanently. World-agnostic - works in any pack.
- **The garbage-world systems** that power the [Trashlands](https://github.com/Flatts3000/trashlands) modpack: the coarse-dirt world preset, Blocks of Garbage, garbage regions, and mound regrowth (quarried mounds grow back toward the footprint and height they had, delivered as garbage falling out of the sky; grass their footprint and that one is retired for good).
- **Reclamation, and a junkyard that fights back.** Healed ground is held, not owned: coarse earth takes back grass that borders unhealed ground, so a healed patch erodes from its edge inward. The answer is a ladder of machines - bare grass reverts, plant cover absorbs a hit and is stripped instead, trees hold a border for good. Builds are never touched, and nothing erodes while you are away. Nothing renews on its own either: every green block is paid for by a machine you built.
- **Multiblock machines** - a core you place plus components stacked on it, formed in place. No machine GUIs, no BlockEntity for the structure.
- **Data-driven teardown tables** (JSON) - a public schema so packs and addons extend the teardown tree without a mod release. Cross-mod teardown is the content.

## Relationship to Trashlands

Recompile is the engine; **Trashlands** is its showcase modpack (the Productive Frogs -> Sky Frogs pattern: a standalone mod, plus a pack built to show it off). The full design lives in the Trashlands repo:

- Design docs: https://github.com/Flatts3000/trashlands (`docs/`)

## Status

**Alpha - released.** **v0.1.0** shipped 2026-07-27 to CurseForge and [GitHub Releases](https://github.com/Flatts3000/recompile/releases), built against MC `26.1.2` / NeoForge `26.1.2.76`. Recompile is the CurseForge ModJam 2026 ("Echoes of the Past") entry.

Shipped: the garbage world and pick-through loop (Blocks of Garbage, bags, bales, Bulky Waste); trash-tier tools; the workstations (Scrap Crafting Table, Sorting Tarp, Recompile Workbench, Burn Barrel) and storage (Scrap Barrel, Scrap Bin, the Scrap Network); food, water (Rain Collector), lighting, smelting, and building blocks; encroachment and the multiblock framework; the full **reclamation ladder** (Grass Spreader, Vegetation, Farming, Compost Heap); **collectibles** (the Puzzle Cube, ported voxel curios, the Display Pedestal); and an in-game **guidebook** (Modonomicon).

**The Recompile Workbench is in** - hold right-click with a found item to break it down into materials, on a timer, with the right tool. That is the *materials* half. The **knowledge** half (studying a recipe, then unlocking it) is the mod's distinct axis and is **not built yet**; its design is under review, because recipe-locking does not survive contact with modded autocrafting. Its data spine - the public `recompile:teardown` recipe type - has been in place since day one, so the schema will not be retrofitted.

Next: the knowledge half of teardown (the mod's distinct axis, design under review) and the remaining reclamation rungs (trees, animals). See [`docs/roadmap.md`](docs/roadmap.md) for the build order and per-phase status.

## License

MIT (see [LICENSE](LICENSE)).

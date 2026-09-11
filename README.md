# Recompile

A standalone **NeoForge** mod (target MC 26.1). Tear apart what the old world threw away and you get back the parts that still work - a motor out of a washing machine, a pump out of a hydroponics bay. Plenty of mods recycle equipment into materials. **Recompile gives you FUNCTION**: in an infinite dump, materials are worthless and a working motor is precious, and there is no amount of scrap you can melt down into one.

Four systems, four jobs, no overlap. **Teardown** yields function. **Sorting** yields bulk materials. The **market** is the source of knowledge - every Blueprint except a creature's spawn egg sheet is bought with scrip you earned selling goods. **Freight quotas** are progression: deliver the named goods a rung asks for and the next rung opens.

*(Teardown taught you recipes until 2026-09-06, and this file said so until 2026-09-08. It does not any more - see P3.10 in the pack's `design_decisions.md`. The knowledge half moved to the market wholesale, and no shipped teardown recipe carries a `teaches` field.)*

## What's in the mod

- **Teardown-as-function** - a workbench where you disassemble found items, on a timer, with the right tool. What you get is the **signature component** the object IS: a washing machine has a motor in it, a dead Hauler has a solar panel. The Motor is salvage or nothing - no recipe, no market offer - which works as a gate where a recipe-based one could not, because "do you have a motor" is a question any mod can answer. World-agnostic; works in any pack.
- **Blueprints, bought rather than learned** - a Buy Terminal repaired from a Broken Terminal sells every recipe sheet in the game but the spawn egg sheets for company scrip, and only up to the tier your freight deliveries have opened. The Scrap Crafting Table runs a blueprint recipe only while that sheet is in your inventory or in a Filing Cabinet in the same cluster.
- **The garbage-world systems** that power the [Trashlands](https://github.com/Flatts3000/trashlands) modpack: the coarse-dirt world preset, Blocks of Garbage, garbage regions, and mound regrowth (quarried mounds grow back toward the footprint and height they had, delivered as garbage falling out of the sky; grass their footprint and that one is retired for good).
- **Reclamation, and a junkyard that fights back.** Healed ground is held, not owned: coarse earth takes back grass that borders unhealed ground, so a healed patch erodes from its edge inward. The answer is a ladder of machines - bare grass reverts, plant cover absorbs a hit and is stripped instead, trees hold a border for good. Builds are never touched, and nothing erodes while you are away. Nothing renews on its own either: every green block is paid for by a machine you built.
- **Multiblock machines** - a core you place plus components stacked on it, formed in place. No BlockEntity for the structure, and a screen only where a machine needs one (the Tree Nursery's species picker); the conveyor machines have none.
- **Data-driven teardown tables** (JSON) - a public schema so packs and addons extend the teardown tree without a mod release. Cross-mod teardown is the content.

## Relationship to Trashlands

Recompile is the engine; **Trashlands** is its showcase modpack (the Productive Frogs -> Sky Frogs pattern: a standalone mod, plus a pack built to show it off). The full design lives in the Trashlands repo:

- Design docs: https://github.com/Flatts3000/trashlands (`docs/`)

## Status

**Alpha - released.** **v0.21.0** shipped 2026-09-11 to CurseForge and [GitHub Releases](https://github.com/Flatts3000/recompile/releases), built against MC `26.1.2` / NeoForge `26.1.2`. Recompile is the CurseForge ModJam 2026 ("Echoes of the Past") entry.

Shipped: the garbage world and pick-through loop (Blocks of Garbage, bags, bales, Bulky Waste); trash-tier tools; the workstations (Scrap Crafting Table, Sorting Tarp, Recompile Workbench, Burn Barrel) and storage (Scrap Barrel, Scrap Bin, the Scrap Network); food, water (Rain Collector), lighting, smelting, and building blocks; encroachment and the multiblock framework; the full **reclamation ladder** (Grass Spreader, Vegetation, Farming, Tree Nursery, animals); **collectibles** (the Puzzle Cube, ported voxel curios, the Display Pedestal); the **demolition yard** and the **sewers** beneath it; six machines with six verbs (Trommel, Separator, Pulverizer, Slag Furnace, Sintering Kiln, Sequencer) plus the Cupola Furnace; the **compacted depths**, this world's Nether; the **Sintering Kiln**, whose verb is the only one that puts a material back together; the **radioactive dump**, the second frontier region; **spawn eggs** via amber and the **Sequencer**; three landmark structures (a decrepit cooling tower, brick smokestacks, and the **Municipal Aquarium** - the only prismarine, coral, sponge and guardian in the game) and **cardboard**; the **Garbage Vacuum**, its screenless **Charging Station** and the battery chain that gates them; **tire dumps** across the household sprawl, the only rubber in the game and the only fire that never goes out; the **Scrap Hauler**, a robot that clears piles around its Depot; the **market** (Sell and Buy Terminals and company scrip) and the **freight ladder** that sets what the Buy Terminal will sell; and an in-game **guidebook** (Modonomicon).

**The long-open knowledge-vs-function question was decided in favour of FUNCTION** (P3.10, 2026-09-06). Teardown was the mod's knowledge system from 2026-08-02 until then: tear something down, collect Idea Fragments, craft a Blueprint. That is gone. Every Blueprint but a creature's is bought at the Buy Terminal now, and what teardown yields instead is the working component an object is built around.

**The Idea Fragment survived the change under a new name.** It is the **Spawn Egg Fragment**, and the Sequencer is now its only source - amber read for the creature inside it, four fragments to a spawn egg Blueprint. Deleting the item would have broken the one exception the same ruling had just sanctioned, so `RCRegistryAliases` keeps `recompile:idea_fragment` resolving in old saves.

The data spine - the public `recompile:teardown` recipe type - has been in place since day one, so none of this was retrofitted into a live schema. Its `teaches` field is still parsed, because packs write it, but no shipped recipe carries one and the Workbench no longer reads it (#390), so it grants nothing.

Next: **Phase 6, the full loop**, plus the balance pass (#36). See [`docs/roadmap.md`](docs/roadmap.md) for the build order and per-phase status.

## License

MIT (see [LICENSE](LICENSE)).

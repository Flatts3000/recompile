# CurseForge project page

Source of truth for the Recompile CurseForge listing (project `1625740`, slug `recompile`).
Edit here, then paste to CurseForge. The pack has its own page copy at
`../trashlands/docs/curseforge_page.md`.

**Voice for this page: spec sheet, not sales copy.** State what the mod adds and how it behaves.
Lists over paragraphs, no selling, and nothing personified - blocks and the world do not want,
fight, or take. "Soil bordering unhealed ground reverts to coarse dirt", not "the junkyard takes it
back".

**Claim discipline.** Every line describes something in the current release. Designed-but-unbuilt
systems go under "Not in yet" or are left out. Check `docs/roadmap.md` phase status and the code
before restating a feature - the design docs describe the intended end state, not what ships.

---

## Project fields

| Field | Value |
|---|---|
| **Summary** | `A garbage world with no ore or trees. Materials come out of the trash, and tearing found items down is how you learn new recipes.` |
| **Categories** | Processing, World Gen, Technology |
| **License** | MIT |
| **Source / issues** | `https://github.com/Flatts3000/recompile` |

The old summary ("...and reclaim the wasteland back to life") sold the premise rather than stating
it. This one names the world, the material source, and the distinct axis.

---

<!-- PASTE MARKER - everything below this line goes in the CurseForge Description field, as-is -->

# Recompile

A standalone NeoForge mod for Minecraft 26.1.2. It adds a garbage world: coarse dirt, mounds of
garbage blocks, no ore, no trees, and no water anywhere. Materials come from the trash, and tearing
found items apart is how you learn recipes.

The teardown and machine systems work in any world. The garbage world is a preset you select at
world creation.

## Picking through

Right-click a Block of Garbage, a bag, or Bulky Waste to pull items out of it. Sorting by hand
crumbles the block after a few pulls. A Sorting Tarp removes the crumbling, and later machines sort
unattended.

Pulls return base materials, buried containers, and found items that are intact. One pull in 320
releases a roach instead, which works out to about one roach per 128 blocks picked through.

## Teardown and blueprints

Hold right-click on a found item at the Recompile Workbench with the matching tool. You get the
materials it was made of, plus an Idea Fragment toward whatever recipe that item teaches. Every
teardown grants one; there is no chance roll.

- Enough fragments about one thing craft into a Blueprint sheet. A Clean Mattress takes 4, a
  Hydroponics Bay takes 6.
- The Scrap Crafting Table runs a blueprint recipe while the sheet is in your inventory or in a
  Filing Cabinet in the same scrap cluster. A vanilla crafting table cannot see these recipes, so
  there is no greyed-out recipe to click at.
- Fragments stop dropping for a blueprint you can already reach.
- The Filing Cabinet is found in Bulky Waste. It holds sheets, accepts loose fragments, condenses
  them into sheets on its own, and discards the surplus.
- `blueprintsEnabled` turns the system off and leaves the workbench materials-only.

Teardown tables are JSON on a public `recompile:teardown` recipe type, so a pack or addon extends
the teardown tree without a mod release.

## Machines

Multiblocks are built by placing a core and stacking components on it. They form in place. No
machine GUIs and no BlockEntity for the structure.

| Block | Behaviour |
|---|---|
| Rain Collector | The only water source in the world. Water does not spread here, so a poured bucket is a spent bucket. |
| Grass Spreader | Drip irrigator. Converts coarse dirt to grass within a radius. Consumes nothing once built. |
| Compost Heap | Turns organic muck into fertilizer. Fertilizer speeds crops and saplings the way bone meal would, which this world has no source of. |
| Tree Nursery | The only source of trees. Saplings are not obtainable. |
| Cupola Furnace | Makes iron. Rebar and Steel Offcuts are blasting recipes, so an ordinary furnace will not take them. It does not cook food. |
| Burn Barrel | Burns refuse, and cooks food. |
| Hydroponics Bay | Grows a plant from water and power with no soil. 20 seconds a batch, 100 mB, 8 FE/tick. The input crop is never consumed and replants itself; a second slot catches byproducts. |

The Scrap Network is a cluster: Scrap Bins each bound to one material, plus barrels for overflow,
joined by placing them touching each other.

## Reclamation and erosion

Grass does not spread on this world, and dirt, podzol, mud, and moss revert to coarse dirt at the
frontier. The reclamation ladder determines what survives:

1. **Grass Spreader** - puts grass down.
2. **Plant cover** - erodes first, leaving the soil under it intact. A border loses its plants
   before it loses its grass.
3. **Farming** - wet farmland does not erode. Dry farmland does, and a crop on it drops rather than
   being destroyed.
4. **Trees** - stop erosion permanently.
5. **Animals** - Animal Bait must be placed on a grass block, and it reads the surrounding terrain.
   Whichever of grass, sand, or leaves dominates decides which animals it draws.

Erosion rules:

- Only soil bordering unhealed ground erodes. Interior ground is unaffected until the edge reaches it.
- Placed blocks never erode.
- Erosion does not run while you are logged off.
- Mycelium is exempt, because it is the substrate the world's only renewable food grows on.

## Also in

- **Food** - tin cans, which apply a random effect on eating the way Suspicious Stew does, and
  foraged dump mushrooms.
- **Demolition yard** - rubble, steel beams, a Cutting Torch, reinforced concrete.
- **Collectibles** - a Puzzle Cube in nine pieces, intact found objects, six recovered paintings
  that keep their variant when broken and replaced, and a Display Pedestal.
- **Guidebook** - in-game, via Modonomicon. Multiblock entries have 3D pages that project the build
  into the world in front of you.
- **JEI and Jade plugins** - the mod's own recipe categories, and tooltips reporting which tool a
  block requires and how far a sort has progressed.

## Not in yet

Alpha, in active development.

- **Mounds do not regrow.** Renewable mounds and the choice between keeping one as a quarry or
  healing its footprint are designed and not built. Garbage in a world is currently finite.
- **One garbage region.** The distance-banded region system is in; the scrapyard and e-waste regions
  are not.
- **Balance numbers are first-pass.** Drop rates, recipe costs, and teardown yields were picked to
  prove the mechanics, not tuned against play.

## Links

- **Source and issues:** <https://github.com/Flatts3000/recompile>
- **The modpack built on it:** <https://www.curseforge.com/minecraft/modpacks/trashlands>

## Art and licensing

Art sourced from elsewhere is public domain or CC0. The paintings are PD works off Wikimedia and the
collectibles are ported from CC0 asset kits. The rest was made for the mod.

Mod content is MIT.

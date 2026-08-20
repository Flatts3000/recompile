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
| **Summary** | `A world buried under its own rubbish, where the recipes went in the bin with everything else. Tear found objects apart to relearn them, and heal the ground a tier at a time.` |
| **Categories** | Processing, World Gen, Technology |
| **License** | MIT |
| **Source / issues** | `https://github.com/Flatts3000/recompile` |

The first summary sold the premise ("...and reclaim the wasteland back to life"). The second stated
the mechanic and nothing else, which is the opposite failure: it named the material source and left
out what the mod is *about*. This one leads with the loss and still names the two axes, teardown and
reclamation.

**Written for ModJam 2026 ("Echoes of the Past"), and that is a deliberate reframing rather than a
rewrite of the mod.** Theme Fit is one of the three judging pillars, every worked example the
organisers gave is literal history, and ours is interpretive - so the interpretation has to be on the
page instead of in a design doc. The literal half was already built and buried under "Also in": six
recovered masterworks and a shelf of found objects. See
`../mod-jam-2026/round_1_rewards_analysis.md`.

---

<!-- PASTE MARKER - everything below this line goes in the CurseForge Description field, as-is -->

# Recompile

**Something lived here before the landfill.** The ground is coarse dirt under mounds of garbage.
There is no ore, no trees and no water anywhere, and the only things left of the world that came
first are the ones somebody threw away.

**The recipes went in the bin too.** Tearing a found object apart at the Teardown Workbench gives
you the materials it was made of and an idea of how it was made. Enough ideas about one thing and you
can build it again. That is what the mod is named for: what you recover from a discarded object is
the knowledge, not only the metal.

A standalone NeoForge mod for Minecraft 26.1.2. The teardown and machine systems work in any world.
The garbage world is a preset you select at world creation.

## What comes back out

Bulky Waste is the buried find. Pry one open with a Prybar and you get something intact rather than a
material: a Dirty Mattress, a Washing Machine, a Filing Cabinet, a Printer, a Broken Hydroponics Bay,
or about one time in ten something out of a rarer pool.

- **Recovered paintings.** Six real works turn up in the trash: the Great Wave, the Starry Night, the
  Mona Lisa, the Scream, Girl with a Pearl Earring, and La Grande Jatte. A painting keeps its variant
  when broken and replaced, so the one you hang stays the one you found.
- **Collectibles.** A Puzzle Cube assembled from nine pieces found separately, and objects that turn
  up whole: a toy car, a gold coin, a wrapped present, an avocado.
- **The Display Pedestal** holds one item and turns it above the cap. It takes any item, not a
  tag-gated trophy list.

None of these appear in JEI's salvage categories. Finding one is meant to be a surprise.

## Picking through

Right-click a Block of Garbage, a bag, or Bulky Waste to pull items out of it. Sorting by hand
crumbles the block after a few pulls. A Sorting Tarp removes the crumbling, and later machines sort
unattended.

Pulls return base materials, buried containers, and found items that are intact. One pull in 320
releases a roach instead, which works out to about one roach per 128 blocks picked through.

## Teardown and blueprints

Rack a Scrap Knife or a Prybar on the Teardown Workbench by right-clicking the bench with the tool,
then hold right-click with a found item. You get the materials it was made of, plus an Idea Fragment
toward whatever recipe that item teaches. Every teardown grants one; there is no chance roll. The
racked tool takes the durability, and a broken one stops the line.

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
| Rain Collector | The only water source in the world. Water does not spread here, so two sources never fill in a third and water cannot be duplicated. |
| Grass Spreader | Drip irrigator. Converts coarse dirt to grass within a radius. Consumes nothing once built. |
| Compost Heap | Turns organic muck into fertilizer. Fertilizer speeds crops and saplings the way bone meal would, which this world has no source of. |
| Tree Nursery | The only source of trees. Saplings are not obtainable. |
| Cupola Furnace | Makes iron. Rebar and Steel Offcuts are blasting recipes, so an ordinary furnace will not take them. It does not cook food. |
| Burn Barrel | Burns refuse, and cooks food. |
| Hydroponics Bay | Grows a plant from water and power with no soil. 20 seconds a batch, 100 mB, 8 FE/tick. The input crop is never consumed and replants itself; a second slot catches byproducts. |
| Trommel | Sorts a garbage block into its drops, unattended. The automated rung of the same job the Sorting Tarp does by hand. |
| Separator | Divides a mixed feed into several materials plus a byproduct. Spent Abrasive into diamond, slag into concrete powder, circuitry into quartz. |
| Pulverizer | Reduces something to a finer form. E-Scrap to circuit powder for gold, phosphor to glowstone, slag to fertilizer. |
| Slag Furnace | Vitrifies slag into obsidian, one lump to a block. The only obsidian in this world. Runs on fuel, not power. |
| Sintering Kiln | Fires a pressed powder until the grains fuse. The only machine that puts a material back together rather than taking it apart. Four blaze powder press into a briquette; the kiln turns it into a blaze rod, and that is a brewing stand. |

Three of those - Trommel, Separator, Pulverizer - have **no GUI and no inventory anything can reach
into**. You feed them by dropping items on them or parking a container on top, and their output goes
to a connected Scrap Network, then a chute, then the floor. Jade reports what they are holding and why
they have stopped.

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

## Under the demolition yard

Brick tunnels running with leachate, the first place in this world that was built rather than dumped.

- The way in is a **manhole**: a square of pale concrete set flush in the ground with a rusted plate
  in the middle. The plate comes up with a Prybar and nothing else.
- Corridors, junctions and stairwells branching from one chamber and running downhill, with a leachate
  channel down the middle and dry brick either side.
- **Decay follows the water** and **light follows the people**. Mossy and cracked brick gather at the
  waterline; the chamber and the shaft are lit and the corridors are not, because dark is where things
  spawn.
- **Roaches live here and nowhere else.** Slimes did too until the demolition yard's hostile mobs were
  brought in line with a vanilla plains; they are still far more reliable down here, because a sewer
  gets them by being a sewer rather than by being one chunk in ten. Both vanilla routes to a slime need
  something this world does not
  have.
- An **echo shard** in a crate settled in the sump silt, one per sewer, and the only one anywhere.
- **Leachate can drown you.** It does no damage on contact and still leaves you hungry.

## The compacted depths

The overworld is a dump you clear. The Nether is a dump you mine.

- **Every column is full**, bedrock floor to bedrock ceiling. No caverns, no lava sea, no ceiling to
  fall from. The only open spaces are the things buried in it.
- **Techno-organic waste** is the fill and behaves like a Block of Garbage: right-click to pick through
  it. It does not fall, because a dimension of falling blocks would bury you.
- **Slag rubble** is the spoil inside it, and it does fall.
- **Fortresses and bastions generate normally.**
- **No vanilla nether terrain generates at all.** Sorting slag rubble gives shards, and four shards
  craft the block. Netherrack, basalt, blackstone, both nyliums, soul sand and soul soil are each
  reachable only that way.
- **The waste gives scrap and your machines give materials**, the same rule the surface runs on.
  Circuitry separates into quartz, phosphor grinds into glowstone, cooked organics separate into nether
  wart, and swarf answers to two machines for blaze powder and magma cream.
- **Lignite** is brown coal, found in the waste, and smelting it is the only route to coal here. It
  burns on its own at half a coal, so it is useful before you upgrade it.
- **Getting in is the point of the slag chain.** The Cupola rakes off slag every eighth smelt, the Slag
  Furnace vitrifies it into obsidian, and that is the only obsidian there is.

## Also in

- **Food** - tin cans, which apply a random effect on eating the way Suspicious Stew does, and
  foraged dump mushrooms.
- **Demolition yard** - rubble, steel beams, a Cutting Torch, reinforced concrete. Some of its
  sand comes up red, stained by the rebar rusting in it, and that is the only red sand there is.
- **The only villagers in the world** are the zombie villagers out in the yard. There are no
  villages here. Cure one and you have the only villager you will ever trade with, and the only
  emeralds. Weakness can be brewed or thrown at you by a witch, which spawns out here at the same
  rarity; the golden apple is the part that costs, since gold is ground-up circuit boards blasted in a
  Cupola and the apple wants an oak.
- **Netherite** - the scrap is in the depths, and a Worn Forging Die found down there teaches the
  smithing pattern vanilla will only sell you out of a bastion chest.
- **Guidebook** - in-game, via Modonomicon. Multiblock entries have 3D pages that project the build
  into the world in front of you.
- **JEI and Jade plugins** - the mod's own recipe categories, and tooltips reporting which tool a
  block requires and how far a sort has progressed.

## Not in yet

Alpha, in active development.

- **Mound regrowth needs a new world.** Quarried mounds grow back, but the memory of what a mound
  was is written into the ground when the world generates, so a save made before this update has
  none and its mounds stay finite.
- **Two overworld regions.** The distance-banded region system is in and the demolition yard is its
  first frontier region; the scrapyard and e-waste regions are not.
- **The compacted depths need a new world.** A dimension's generator is written into a save when the
  world is made, so an existing world keeps whatever Nether it already had.
- **Villager trading is not curated.** A villager or a wandering trader will sell you saplings, buckets
  and iron gear, all of which this world otherwise gates behind a machine or a find. Getting a villager
  at all is the cost that stands in for those gates; whether that is the right trade is still open.
- **The End is closed**, travel and portal formation together, so there are no dead frames.
- **Balance numbers are first-pass.** Drop rates, recipe costs, and teardown yields were picked to
  prove the mechanics, not tuned against play.

## Links

- **Source and issues:** <https://github.com/Flatts3000/recompile>
- **The modpack built on it:** <https://www.curseforge.com/minecraft/modpacks/trashlands>

## Art and licensing

Art sourced from elsewhere is public domain or CC0. The paintings are PD works off Wikimedia and the
collectibles are ported from CC0 asset kits. The rest was made for the mod.

Mod content is MIT.

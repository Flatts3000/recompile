# Changelog

## v0.3.0

The demolition yard: a second region, and the first iron that is not a trickle.

**Breaking: worlds from v0.2.0 will lose their Rubble.** The block was renamed from `recompile:rubble` to `recompile:stone_rubble` now that there is concrete debris to tell it apart from. There is no way to migrate it, so start a new world or expect gaps where Rubble used to be.

### The demolition yard
- A new region past the household sprawl. Keep walking. It starts around 512 blocks out and thickens the further you go, so the first one you hit will be thin.
- **Building Husks:** stripped steel frames standing in rows, floors half gone. The landmark, and where most of the steel is.
- **Steel stacks:** salvage gathered into low piles of I-beams, copper pipe and broken deck, with rubble scattered between them.
- **Reinforced Concrete** and **Steel I-Beams** are findable now. Both were creative-only.

### Steel and iron
- **Cutting Torch:** the only tool that cuts a Steel I-Beam. A sledgehammer will not; you crush concrete and you cut steel. Craft it from copper pipe, plastic scrap, rebar and an Oily Rag, all of which you can get before you have any iron.
- Right-click holding the torch to feed it a rag. One rag is worth 8 cuts and it holds 64. An empty torch refuses to cut rather than breaking the beam for nothing.
- Cutting a beam gives **Steel Offcuts**. They are scrap, not ore, and only one machine will melt them down.
- **Cupola Furnace:** upgrade the Burn Barrel with concrete and copper pipe. It smelts everything the barrel does, plus offcuts into iron and rebar into iron nuggets, and it takes hoppers.
- **The Burn Barrel now burns refuse only:** food, and scrap metal for copper. No ore, no sand, no stone, no logs. A drum fire does not get that hot. Everything else waits for the Cupola.

### Fixes
- Enabling a resource pack no longer disabled every resource pack. Two Jade settings were missing a name, which threw an error while the game loaded, and Minecraft answered by dropping every pack you had on.
- JEI no longer claims you can smelt iron or glass in the Burn Barrel. It has its own category now.
- Terrain no longer opens holes clean through the world. Nor pillars of stone to build height.

### Tweaks
- Garbage, Stone Rubble and Reinforced Concrete each come in three variants, so a wall or floor of them stops looking like one tile repeated.
- Jade tells you a Steel I-Beam needs a Cutting Torch, the same way it does for the Prybar.

## v0.2.0

Two more rungs of the reclamation ladder, trees and animals, plus a multiblock dupe fix.

### Reclamation
- **Tree Nursery (rung 4):** a 2x2x1 machine (a core and a water tank, solar panels on top) that raises a vanilla sapling of your choice from water, Fertilizer, and an Unknown Seedling. Pick the species on its screen; the cook is slow and the machine glows while it works. Water can be piped in, Fertilizer and Seedlings go in by hand. Eight species: oak, birch, spruce, jungle, acacia, dark oak, cherry, and mangrove.
- **Animal bait (rung 5):** herbivore, carnivore, and omnivore bait, each with a Rich grade. Place one on grass and leave; when no player is near, one of that diet's animals settles onto the spot and the bait is spent. Rich bait seeds a breeding pair. Two baits too close together, or a nearby player, hold it instead of firing. Crafted from apples so the animal tier waits on trees: herbivore from an apple and wheat, carnivore from apples and any raw meat, omnivore from one of each. JEI and Jade show every reason a bait is held.

### Fixes
- Breaking one cell of a formed multiblock (Rain Collector, Compost Heap) no longer duplicated the core block.

### Tweaks
- The creative-tab and JEI item list is grouped into categories and ordered to match play order.

## v0.1.0

First public alpha. Recompile is a standalone NeoForge mod for Minecraft 26.1.2: you spawn in a world made of garbage, with no ores, no trees, and no animals. Everything you need is picked out of the trash and torn back down into materials.

### The world and the loop
- A custom **Garbage World** type (pick it on the More tab when you create the world): a coarse-dirt slab, no ores, no mobs.
- Dig Blocks of Garbage and right-click to pull one item at a time; blocks crumble as you work them. Trash Bags and Compacted Bales are richer streams.
- Nine base materials, salvage tools (Prybar, Scrap Knife, Junk Shovel), and Bulky Waste finds you pry open for machine parts.

### Stations and storage
- No wood, so no vanilla table or chest: craft at the **Scrap Crafting Table**, store in Scrap Barrels and item-bound Scrap Bins, and let the **Scrap Network** route junk between blocks placed face to face.
- The **Recompile Workbench** tears salvage back into materials. The **Burn Barrel** is a manual, no-automation smelter. Light comes from the **Scrap Torch** (Oily Rag fuel).

### Living here
- Food from Tin Cans (cut open with a knife) and replantable Dump Mushrooms.
- Rain is the only fresh water: build a **Rain Collector**.
- Building-block families (Pressed Junk, Scrap Plating, Corrugated Metal, Plastic Panel, Cullet Glass) as material sinks.

### Reclamation
- The junkyard fights back: reclaimed grass reverts at the edges, and a dry farm plot is taken back.
- Rung 1, the **Grass Spreader**, waters coarse dirt to grass. Rung 2 scatters weeds and wildflowers with **Fertilizer**. Rung 3 crafts farmland (a hoe will not till the dump) and grows crops from compost-volunteer seeds. Fertilizer comes from the **Compost Heap**.

### Collectibles
- The **Puzzle Cube**, assembled from nine pieces found in the garbage. Rare whole curios found intact (an avocado, a present, a gold coin, a toy car). A **Display Pedestal** that floats and turns any item.

### Guides and tooling
- An in-game guidebook, **The Salvager's Manual**, when Modonomicon is installed.
- JEI and Jade integration for the mechanics that are not vanilla recipes.

### Requirements
- Minecraft **26.1.2**, NeoForge for 26.1.2, Java **25** (the version the launcher ships for this MC).
- Optional, none bundled or required: JEI, Jade, Modonomicon (the guidebook engine).

### Alpha notes
- Drop rates and recipe costs are first-pass placeholders and will be tuned before beta.
- The Nether and the End are sealed until their themed dimensions ship.

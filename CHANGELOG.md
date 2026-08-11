# Changelog

## v0.8.0

**The dump gives you objects; your machines give you materials.** A bucket, a rug, a glass bottle - anything a person would actually throw away - is found now, not crafted. Materials and what you build out of them are still yours to make. The bucket is the one you will hit first: it turns up in ordinary household sorting, and there is no longer a recipe for one.

**And the dump has started leaking.** Pools of leachate sit on the open ground between the mounds, the runoff a landfill really produces. It looks like water and is not: it will not fill a Rain Collector, it will not water a crop, and standing in it makes you hungry. Water is still something you collect from the sky.

### Found, not crafted
- **Buckets, glass bottles and all sixteen wool carpets can no longer be crafted.** Every one of them already turned up in sorting, competing with a recipe that made finding one pointless.
- **The bucket now drops from household sorting**, at a weight that respects what it does: with no fluid pipes in this world, a bucket is the only way to move water out of a Rain Collector and into a Tree Nursery or a Hydroponics Bay.
- Building blocks are materials and stay craftable. The stone came out of shards you sorted, so putting it back together is ordinary.
- Packs can extend the rule without touching the mod: membership is the `recompile:found_only` item tag.

### Leachate
- **Leachate pools generate on open ground in the household sprawl**, on the surface only, never buried under a mound.
- **It is not water.** It will not fill a tank, will not irrigate farmland, and a Rain Collector ignores it.
- Standing in it gives **Hunger for five seconds**, refreshed while you stay in. It never damages, never poisons and cannot kill you. Both the effect and its length are config.
- It is a real fluid, so it flows, it has a bucket, and it behaves the way you expect a liquid to.
- Pools appear in newly generated ground, so an existing save picks them up as you explore outward.

### Components, and how to learn them
- **The Motor** comes out of sorting Mechanical Waste, and the Separator's back row wants one.
- **The Bulb** comes out of household sorting. The Hydroponics Bay needs one, and **the Tree Nursery now needs one too** - a nursery built under a mound has no other light.
- **Components come in two kinds and it is worth knowing which is which.** A Motor is placeable: you stack it into a machine. A Bulb is spent in a recipe. Both are inert on their own - the Motor turns nothing.
- **The Pump, the Motor and the Bulb can now be learned rather than only found.** Tear down the thing each comes out of four times and you come away with its Blueprint, after which the Scrap Crafting Table will build them from copper and salvage.
- Finding one is still better than making one, and that is deliberate: salvage hands you the part and its scrap for a single prybar action.

### Two new finds
- **The Broken Fan** turns up in Bulky Waste and tears down into a Motor.
- **The Light Fixture** turns up beside it and tears down into a Bulb.
- Neither replaces sorting. Both components still come up in their own streams, so these are the route to the Blueprint rather than a new bottleneck.

### Printers carry the whole dye set
- A printer teardown can now yield **fourteen of the sixteen dye colours**. Cyan, magenta and yellow come up several times more often than the rest, because those are the cartridges actually in the machine.
- **Blue and black are the other two, and they still arrive as lapis lazuli and an ink sac** rather than as the dye items. Vanilla already grinds each into its dye, and both are worth more than the dye alone - lapis is this world's only lapis, and an ink sac is also the bait and the book-and-quill.
- **White is the colour this really changes.** Its only other route is bone meal, which means either skeleton bones from the demolition yard or a composter, which needs wood. Gray, pink and light blue are each white plus something, and magenta needs pink, so five colours used to wait on one of those two trips.

### Screens
- **The Burner Generator and the Tree Nursery now look like the rest of the game.** Both were drawing an approximation of a Minecraft panel - flat grey with holes in it - while the Hydroponics Bay used the real thing. All four screens are now cut from vanilla's own chrome, so a resource pack that restyles containers restyles these too.
- The Tree Nursery's water gauge was a different blue from the Hydroponics Bay's. There is one water colour now.

### Fixes
- **Two guide panels in JEI had been blank for months.** Looking up a Printer or a Broken Hydroponics Bay - the find that gates the dye set, and the one that teaches the Hydroponics Bay - showed nothing at all.
- The in-game guide's blueprint entry, the Washing Machine entry and the Bulky Waste list all said things that stopped being true. They now say what the game does.
- A malformed line in the language file could have shown raw text to players in place of a description.

## v0.7.0

**Mounds grow back.** Quarry one out and it starts rebuilding itself toward the footprint and height it had, delivered as garbage falling out of the sky - so you can see across the plain which mounds are refilling. It never grows past what it was, and it never seeds a new one.

Which makes the choice underneath it the real change. **Grass a mound's footprint and that mound is retired for good.** A regrowing mound is income; healed ground is permanent. Healing the world shrinks your garbage economy, and that trade is now something you make deliberately instead of something the world decides for you.

**This one needs a new world.** The memory of what a mound was is written into the ground when the world generates, so a save made before this update has none, and its mounds stay finite.

**Bare hands no longer carry off a pile.** A Block of Garbage, Stone Rubble or Mechanical Waste stays where it is unless you bring the right tool, and tells you which one. You can still pick through any of them by hand, which is the point: sorting is free, hauling is not.

### Mound regrowth
- Quarried mounds regrow toward their original footprint and height, never beyond, one block at a time.
- Delivery is a falling block from above, so a replenishing mound is visible from a distance.
- **Mound Ground** is the dark earth under a mound's footprint. It is coarse dirt with a different name and a darker face: same hardness, same shovel. Dark ground means that mound comes back.
- Greening Mound Ground with the Grass Spreader retires that mound permanently. Encroachment can take the grass back, but never back to Mound Ground - only the green is contested.
- A roof or a build over a mound stops it regrowing rather than being buried by it.
- Rate, on/off and the drop height are all config. Regrowth only runs near a player, so an unattended world does not refill behind your back.
- With `garbageGravityEnabled` off the block is placed instead of dropped - the switch governs the fall, not whether mounds come back.

### Tools to move a pile
- **Block of Garbage** and **Stone Rubble** need a shovel; **Mechanical Waste** needs a pickaxe; a **Compacted Bale** needs the Scrap Knife. Any vanilla shovel or pickaxe works - the Junk Shovel is not special-cased.
- **Trash Bags still come up by hand.** They are loose litter and the first block you meet.
- Swing without the tool and the pile stays put and says what it wants, so nothing is lost while you learn it.
- Sorting is untouched. Every pile still picks through bare-handed at the same rate.

### Corrections
- Six claims in the in-game guide were wrong and are fixed: the Workbench holds a Scrap Knife **and** a Prybar rather than either; the Sorting Tarp takes one block per press rather than a stack; its accepted-input list had never learned about Mechanical Waste; the Scrap Network entry listed six members when there are nine; the Burn Barrel is an allowlist rather than an ordinary furnace; and a Block of Garbage no longer "drops itself like gravel".

## v0.6.0

Iron was where this world ended. It is not any more: the demolition yard now generates **Mechanical Waste**, and a machine called the **Separator** pulls diamond, redstone and amethyst back out of it. Nothing is transmuted and nothing is mined - the valuable material was always inside the thing you found, and this is the machine that gets it out.

**One change reaches worlds you already have.** A **Dirty Mattress is now spent by sleeping on it.** It survives one night and breaks when you get up. Playtest was blunt about why: a found mattress did the whole job, so the Clean Mattress it was meant to lead to added nothing you could feel. If you want a respawn point without burning the mattress, **sneak-right-click** it - setting spawn is not sleeping, and only one of the two wears it out.

**A bed colour that could not be made now can.** Black dye comes from an ink sac or a wither rose, the Nether is closed, and so grey dye was unreachable too - v0.5.0 shipped sixteen Clean Mattresses and two of them had no route. Printers fix it.

### The gem tier
- **Mechanical Waste** generates in the demolition yard and picks through by hand into industrial scrap: quartz grit, spent abrasive, magnet scrap.
- The **Separator** is a multiblock built from steel and machine frames. Feed it scrap and power and it separates: **12 quartz grit into amethyst, 16 spent abrasive into diamond, 16 magnet scrap into redstone**.
- The ratios are the gate, not a missing recipe. One piece of scrap is worth nothing; a stack is worth a gem. **Magnet scrap is the rare one**, which makes redstone - and therefore every piston, comparator and observer - the thing you work toward.
- Nothing precious ever falls out of a pile. Gems are separated out of scrap, never found in it.
- This does not complete enchanting. A table needs obsidian as well, and obsidian is somewhere else.

### The Separator sorts, too
- Once built, it **sorts garbage automatically** at exactly the Sorting Tarp's rate - drop Blocks of Garbage, bags, bales or Mechanical Waste in and it works through them unattended.
- Same rate, deliberately. What you get for building it is that it runs while you are elsewhere, not that it yields more. The Sorting Tarp still works and is still the early answer; it just stops being the thing you stand at.
- Output goes into the Scrap Network first, then the chute, then the floor.

### Printers, and what comes out of them
- A **Printer** is a new Bulky Waste find. Tear it down at the Workbench with a Prybar for paper, an **ink sac**, plastic and scrap, and about half the time a **lapis lazuli**.
- Ink is the only black dye in the world, so printers are the only route to the grey and black mattresses.
- Lapis is a pigment and a printer is full of pigment. It is deliberately not in Mechanical Waste - machinery contains no lapis at all.

### Found tools
- **Shears, flint and steel and a spyglass** now turn up in the dump, already worn. Shears and flint and steel are an earlier route to things you could eventually craft; the spyglass is not, because it needs an amethyst shard and there is no other amethyst before the Separator.

### The dump is not empty any more
- **Cats, dogs and pigeons** live here now, rarely. They are ambiance: the landfill has something alive on it. Cats and wolves come in every vanilla variant.
- A **pigeon** will walk to a nearby pile of garbage and peck at it, and once in a while pull something out. It only ever finds what that pile would have given you anyway, and it never wears the pile down.

### Steel you can build with
- A **pickaxe now returns a Steel I-Beam** instead of destroying it, so a girder can be taken down and put up somewhere else. The **Cutting Torch** still cuts one into Steel Offcut, which is the only thing that feeds the iron path. One beam either way, so nothing is created.

### Rope and Luggage
- A lead is now **Rope** and a bundle is **Luggage**, with new art: a wheeled suitcase rather than a drawstring pouch, in all sixteen colours. Searching JEI for "lead" or "bundle" still finds them.

### Smaller things
- **Shift-clicking a craft no longer empties your whole Scrap Network.** Craft-from-storage and vanilla shift-crafting were jointly unbounded, so one keypress could spend a sorted wall. A shift-click is now one batch, and the grid is restocked afterwards.
- Ordinary household rubbish - a bowl, a name tag, a music disc, leather scraps - now turns up in the pull streams.
- The guidebook has a **Bulky Waste chapter** with an entry per find, instead of one page under Tools.
- The Tree Nursery wears a single skin across its bottom row rather than reading as a stack of separate blocks.

## v0.5.0

The mod does the thing it is named after. Tear something down at the Workbench and you can come away knowing how to build it.

**Beds changed, and it reaches worlds you already have.** All sixteen wool-to-bed recipes are gone. A bed is now a Clean Mattress plus three planks, and a Clean Mattress can only be made from a blueprint. If you are mid-world and sleeping in a Dirty Mattress, nothing breaks; if you were about to craft a vanilla bed, you need to tear down Dirty Mattresses at the Workbench first.

**Two other changes reach existing worlds.** Water no longer breeds (see below), because it is applied as a game rule on every load. And iron now needs a Cupola Furnace, so an ordinary furnace with rebar in it stopped working.

### Blueprints
- Tearing anything down at the Recompile Workbench now grants an **Idea Fragment** toward whatever that recipe teaches. Every teardown, not a chance roll.
- Enough fragments about one thing craft into a **Blueprint** sheet. The Clean Mattress takes 4, the Hydroponics Bay takes 6.
- A **Filing Cabinet** turns up in Bulky Waste. It holds sheets, accepts loose fragments, condenses them into sheets on its own, and throws the surplus away. Place it touching your other scrap blocks and it joins the cluster.
- The **Scrap Crafting Table** runs a blueprint recipe only while the sheet is in your inventory or in a Filing Cabinet in the same cluster. A vanilla crafting table cannot see these recipes at all, so there is no locked-recipe grey-out to click at.
- Fragments stop dropping for a blueprint you can already reach, so a cabinet full of sheets does not keep collecting scraps of paper.
- If you lay out a recipe correctly and cannot run it, the table says so: **"No blueprint in your inventory or attached storage."** The empty result slot used to mean both "wrong arrangement" and "no sheet".
- JEI shows blueprint recipes with a working transfer button, and it fills the grid from connected storage as well as your inventory.
- `blueprintsEnabled` turns the whole system off and leaves the workbench materials-only.

### The Clean Mattress, in sixteen colours
- Three wool over three string at the Scrap Crafting Table, with the blueprint to hand.
- Each colour of wool makes its own Clean Mattress, the same way wool is its own block per colour. Dye one at an ordinary table to change it.
- A coloured bed takes the matching mattress. A black bed no longer accepts a white one.

### The Hydroponics Bay
- Grows a plant from water and power with no soil at all. The **first machine in the mod that spends FE**, which the last release promised and did not have.
- 20 seconds a batch, 100 mB of water, 8 FE/tick. That is 3,200 FE per batch, roughly 160 seconds of one Solar Panel or 8 seconds of a Burner Generator.
- **The crop you put in is never consumed.** One input is the output forever, until you take it out. It replants itself.
- A second slot catches byproducts.
- Right-click it with a water bucket to fill the tank. The screen shows water and power as separate gauges, because "why is it not running" has two answers.
- If the output slot is full of something else, growth stops rather than converting. Sugar cane in a bay with potatoes in the output used to come out as potatoes.
- It is now behind a blueprint: find a **Broken Hydroponics Bay** in Bulky Waste and tear it down.

### Recovered paintings
- Six real paintings, pixelated to Minecraft resolution, found in Bulky Waste at about 7% of finds.
- They keep their identity through break and replace, which a vanilla painting does not, and the item in your hand names the work rather than saying "Painting".

### Water does not breed
- Two water sources no longer fill in a third. Every bucket you pour out is a bucket gone, which is why the Rain Collector stays worth having after you can make buckets.
- Applied by setting vanilla's `water_source_conversion` game rule on load, so it reaches worlds made before this release. `disableInfiniteWater` stops the mod touching the rule at all.

### Iron is gated on the machine now, not on an absence
- Steel Offcut and rebar became **blasting** recipes, so only the Cupola Furnace makes iron. A vanilla blast furnace costs five iron ingots, so it cannot be the way in.
- The old gate assumed no other furnace was craftable, and the Tree Nursery quietly ended that by supplying wood. Rebar is a common pull from household garbage, so iron was reachable on day one with no demolition yard, no Cutting Torch and no Cupola.
- The Cupola still does not cook food. It melts metal. The Burn Barrel keeps refuse and food.

### Fertilizer grows things
- Fertilizer now also speeds up planted crops and saplings, the way bone meal does. This world has no bone meal and cannot have any, since it comes from skeletons.

### The guidebook
- Twelve shipped systems had no entry at all. They do now: the demolition yard, the power tier, the Hydroponics Bay, the Tree Nursery, recovered paintings, Animal Bait, sleeping, and the rule that water does not spread.
- The four multiblocks (Rain Collector, Grass Spreader, Compost Heap, Tree Nursery) have **3D structure pages** with a button that projects the build into the world in front of you.

### Smaller things
- Roaches are much rarer: about one per 128 blocks of garbage picked through, up from one per 16. They were competing with the pull streams they live in.
- The seven stone shards have real art instead of placeholders.
- Both biomes have names. They were showing a raw translation key.
- Multiblock status checks work on rotated structures, so a Tree Nursery placed facing any direction reports correctly.
- The creative tab is regrouped into the order you actually meet things, and JEI's panel follows it.
- The logo is pixelated, with REC on it.

## v0.4.0

Something lives in the garbage, and the first power that is not a torch.

**Worlds from v0.3.0 carry over.** Nothing was renamed this time. Worlds made before v0.3.0 still will not have the demolition yard, and no amount of walking will find one: a world's terrain generator is fixed when the world is created, so new chunks in an old save still come from the old one.

### Roaches
- Picking through a Block of Garbage can turn one up instead of an item, about one pull in forty. Garbage blocks only. Bags and bales are clean.
- One at a time. Hitting it does not call more out of the ground.
- Kill it yourself for **Raw Roach**. It has to be your kill, so a fall, another mob, or a grinder leaves nothing behind.
- Cook it in the Burn Barrel for **Cooked Roach**. The barrel already takes anything edible, so this needed no change to it.
- Roaches are the earliest renewable food here, which makes their rate a progression lever as much as a difficulty one. `roachChanceDenominator` (default 40) makes them rarer as you raise it, and `roachesEnabled` turns them off.
- There is a spawn egg.

### Power
- Machines speak **Forge Energy** now, the same standard the rest of the modded ecosystem uses, so anything that moves FE can wire straight into these.
- **Burner Generator:** burns any furnace fuel at 20 FE/tick and holds 20,000. Five fuel slots, a power meter on its screen, and smoke off the top while it runs.
- **Solar Panel:** generates. 2 FE/tick under open sky, less as the light drops, holding 4,000. It has been craftable since the Tree Nursery shipped and until now it did nothing but sit there.
- Both push power out and refuse to take any in. Two panels side by side used to hand the same energy back and forth forever, and a Tree Nursery puts two of them next to each other.
- **Nothing in the mod spends power yet.** The generators are here so the tier exists and other mods can draw on it. The first Recompile machine to use FE is the hydroponics grower, which is not built.
- Jade reads out stored charge, current rate, and how much burn is left.

### Pipes and automation
- **Pipez** moves items in and out of Scrap Bins, the Scrap Barrel and the Cupola Furnace.
- A Scrap Barrel now behaves like a vanilla barrel and a Cupola like a vanilla furnace. Hoppers and pipes both work on them, from any face a vanilla one would allow.
- **The Burn Barrel still only takes your hands.** A pipe will not even connect to it.
- Three gravel makes a flint.

### The demolition yard joins the network
- The Sorting Tarp takes Stone Rubble.
- Scrap Bins bind to stone shards and to Junk, so yard material files away with everything else instead of piling up loose.
- The Scrap Crafting Table and the Cupola Furnace count as network blocks, so junk routes through them like the rest.

### Fixes
- The Scrap Crafting Table's connected-storage panel scrolls. It showed seven materials, said "+6 more", and gave you no way to reach them. A barrel holds twenty-seven.
- Clicking a material in that panel takes one item. It took sixty-four. Shift takes a stack, right-click takes half.
- The crafting grid no longer empties when you close the screen.
- Stone Rubble has an item texture again.
- The demolition yard encroaches the way the sprawl does. It was the one region where healed ground stayed healed for nothing.
- The Rain Collector's guide text asks for a Rain Collector Funnel instead of a Machine Frame, which is what it actually wants.
- Enabling a resource pack no longer throws an error while the game loads. A Jade setting added with the generators had no name.

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

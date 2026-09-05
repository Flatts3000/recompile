# Changelog

## Unreleased

### The Sledgehammer swings back

- **The Sledgehammer is a weapon now, not only a demolition tool.** It always hit harder than a sword
  per swing, but it swings half as often, so it lost every fight it started. The hit is heavier to pay
  for the wait, and it knocks what it hits away from you, further the better the head.
- **It gets through a shield**, locking one out for the same five seconds an axe does.
- **It takes enchantments at last**, having accepted none at all before: Sharpness and its cousins,
  Knockback on top of the knockback it already has, Looting, Fire Aspect, Efficiency, Unbreaking and
  Mending. Not Silk Touch or Fortune, which do nothing to any of the three blocks it mines.

### The market

- **Two terminals and an account.** The **Sell Terminal** buys components and finished goods - a
  Pump, a Motor, a Bulb, a Battery, a Clean Mattress, the machine parts - and credits your balance
  in **company scrip**. The **Buy Terminal** sells **Blueprints** against that balance. Scrip is
  never an item: it cannot be dropped, stored in a chest or lost on death, and a hopper cannot sell
  for you.
- **The screen quotes what a load pays before you sell it.** Raw scrap and anything pressed straight
  from junk are refused at the slot, so junk still has no price and its only sink is still the Burn
  Barrel.
- **A bought Blueprint is the same sheet fragments make.** You still need every material and the
  Scrap Crafting Table; you are paying past the fragment grind, not past the gate.
- **Both are learned from a Broken Terminal** pried out of Bulky Waste and torn down at the
  Teardown Workbench. One find teaches both.
- **Every sellable thing carries its price on its own tooltip**, per item and per stack, so you can
  tell what is worth carrying home without walking to a terminal. Nothing shows on things the market
  will not take.
- **The Buy Terminal also sells a few things outright, not just the knowledge to make them.** A
  **Totem of Undying** (2,500 scrip) has no other source in this world at all - totems come off
  evokers and there are no raids here. A **heavy core** (3,000 scrip) is the same story with trial
  chambers, and it is the mace's head rather than the mace: the handle is a breeze rod, which you
  already fire in the Sintering Kiln, so buying the core finishes a chain instead of skipping it.
  A **Bucket of Powder Snow** is sold as knowledge instead
  (600 scrip): buy the sheet, then make one from a bucket and four snowballs, which is the first
  blueprint in the game that is bought rather than earned by tearing something down.
- **A pack changes all of it without a mod release**: what sells is `#recompile:sellable`, what it
  pays is the `recompile:scrip_value` data map, and every line of the Buy Terminal's stock is one
  `recompile:market_offer` recipe file.

## v0.18.0

**The sea, and the tires.** v0.17.0 was about clearing mounds faster. This one is about two places
worth walking to, and about the plants and materials that had no way into this world at all.

### The Municipal Aquarium

- **A drained public aquarium out in the demolition yard**, seven rooms of it: a forecourt, a lobby,
  a gallery of tank bays, a centrepiece tank, a guardian tank, a half-sunk filtration hall and the
  back of house. Leachate has pooled in the floors and the glass is cracked where the tanks leaked.
- **It is the only prismarine, coral, sponge and sea lantern in the game.** Fifteen dead corals in
  the tank rows, sponges down in the filtration hall, and a heart of the sea still on its stand in
  the middle of the centrepiece tank.
- **One tank still holds water, and there is a guardian in it.** That is not decoration. Every
  prismarine block and the sea lantern are crafted from shards and crystals that drop from exactly
  one mob, so the tank is what makes the whole family renewable rather than a fixed stock.
- **A dead coral is a permanent supply.** Put one in a Hydroponics Bay and it comes back alive, and
  the bay never consumes its seed, so one of each colour is all you ever need.
- **Prismarine Grit** comes out of Mill Tailings if you would rather manufacture it. It cannot be
  bucketed dry the way the tank can, and tailings do not grow back.
- **The curator's chest and a brushable silt bed.** The chest carries exhibit stock; the silt is the
  only archaeology in this world and holds the nineteen pottery sherds the sewers do not.
- **Moss where it is wet, pale moss where it is dark.** Ordinary moss has taken the filtration hall
  and crept out of the leaking gallery bays; pale moss is inside the centrepiece tank and nowhere
  else. Both were wandering-trader purchases and nothing else. Now you can mine them out of a
  derelict building.

### Tire dumps

- **Somebody tipped a few thousand tires across the household sprawl.** Circular heaps, stacked, and
  some of them are burning. Nothing regrows one, so a dump you strip is a dump you leave.
- **A tire fire never goes out.** Not in rain, not with time. It does not eat the tires either, and
  there is nothing on bare dump ground for it to spread to, so a burning heap is a hazard you walk
  around rather than a loss. Water still puts it out.
- **Break a tire by hand and you get the tire. Break it with a Scrap Knife and you get the rubber.**
- **Carrying them home pays better.** Tear a tire down at the Teardown Workbench with the knife for
  three rubber and, often, the steel belts out of the middle. Cutting one where it stands cannot
  reach the wire. A Pulverizer shreds them in bulk and loses the wire too.
- **The Pump recipe wants Rubber Scrap in the bottom cell instead of Plastic Scrap.** A pump seals
  with rubber. If you already hold the blueprint, the sheet is unchanged and the ingredient is not,
  so check it before you go hunting for plastic. Plastic still builds the Cutting Torch, the Plastic
  Panel and the Rain Collector Funnel.

### Plants that had nowhere to come from

- **A Dried Bouquet turns up in household waste.** Right-click it on a water cauldron and it
  rehydrates into one of the four tall flowers or a large fern, at random, for a level of water. None
  of those five had any source before: the wandering trader has never sold a tall flower, and a large
  fern needs a fern to bone-meal that nothing here provides. One of each is a permanent supply,
  because bone meal duplicates a tall plant once it is planted.
- **Or pull the bouquet apart at the workbench** for Fiber Scrap and string. No tool: it is stems and
  a ribbon.
- **Six more plants found homes.** A bush, a cactus flower, short dry grass, a dead bush and pink
  petals are exhibit stock in the aquarium's arid vivarium. The closed eyeblossom rides the sewer's
  access-chamber barrels, which is the one permanently unlit place in this world and the only place a
  flower that blooms with the lights off belongs.

### Machines you can walk away from

- **A Tree Nursery can be run by hoppers and pipes now.** Fertilizer and seedlings in from the top
  and sides, saplings out of the bottom, the same layout as the Hydroponics Bay. It was manual-only
  on both doors, which made a tree farm impossible rather than merely hands-on, and a nursery is the
  only source of trees in this world.
- **A pipe can no longer pull the seedling back out of a Hydroponics Bay** it was feeding.

### Fixes

- **Walls connect to each other.** All five wall blocks - pressed junk, scrap plating, corrugated
  metal, cardboard and plastic panel - were missing from the tag a wall reads to know its neighbour
  is a wall, so a row of them stood as separate posts. Reported from playtest.
- **World generation no longer crashes near an aquarium.**

### Guide

- **A Tire Dumps entry** covering the fire, the tool split, and where the rubber goes.

## v0.17.0

**Clearing a mound stops being a mining job.** v0.16.0 put things on the skyline to walk toward. This
one is about the walk back: a powered vacuum that pulls garbage blocks out of the ground several a
second, and the battery chain you have to work out before you can build one.

### The Garbage Vacuum

- **Hold right-click and the piles in front of you leave the ground and fly into the nozzle**, about
  five a second. It takes whole blocks, not what is inside them, so you still sort them afterwards at
  a tarp, a workbench or a Trommel.
- **Take a mound from the bottom and the rest comes down on top of you.** That is the fast way to
  clear one. The ground under a mound still remembers it, so a cleared mound grows back exactly as it
  did before.
- **A block you had half picked through comes out fresh.** The pulls you already took are gone. Vacuum
  the ones you have not started on.
- **It costs power per block, and a bigger block costs more.** A Compacted Bale is eight rolls against
  a Trash Bag's four, so bulk-clearing the good stuff is not free.
- **Point it at something out of its league and it names the pile** instead of doing nothing.

### Each vacuum is rated for the waste it was built for

- **Copper handles household rubbish.** Iron adds the demolition yard, diamond the tailings and drums
  out past it, and only a netherite one will touch the depths. Each tier also handles everything
  easier than itself.
- **Better tiers also reach further and hold more.** Two blocks and 4,000 FE at copper, five and
  24,000 at netherite.

### The Charging Station

- **A flat vacuum does nothing.** Set it down on a Charging Station with a generator touching the
  station and it fills.
- **No screen.** Right-click the station holding a vacuum to leave it there, right-click it
  empty-handed to take it back. Look at the station and Jade tells you what it is holding and how full
  the vacuum is.
- **Nothing can reach in and take the tool off it.** No hopper, no pipe. Putting it down and picking
  it up is the whole interaction.

### Batteries, and the dead ones you find first

- **Depleted Batteries turn up loose in household waste**, about as often as a dead bulb. They are not
  a part and you cannot build anything out of one.
- **Cut one open at a Teardown Workbench with a Scrap Knife** for its scrap metal, e-scrap and
  plastic. Four of them and you will have worked out what is inside.
- **Then you can make live ones.** A Battery is copper, scrap metal and e-scrap at the Scrap Crafting
  Table, and it is the only route: nothing in this world hands you a working cell.
- **Both the vacuum and the Charging Station need one**, so the batteries gate the whole tier. No
  cell, no charger, no vacuum.

### Fixed

- **Zombies in the demolition yard drop iron again.** A husk loot override shipped in v0.16.0 to stop
  a spawner farm handing out iron; it only covered the husks at the smokestacks while the yard's
  ordinary zombie spawns and any husk you drowned went on dropping it as before. It read as a
  guarantee it never was, so it is gone.

## v0.16.0

**The skyline has two things on it now, and there is finally something you can build with on the
first day.** v0.15.0 filled in what the world could not give you. This one is about what you can see
and what you can put up: a cooling tower you can pick out from the next region over, chimneys smoking
across the demolition yard, and cardboard.

### A decrepit cooling tower

- **You can see it from the next region.** Sixty to seventy-five blocks of concrete on a plain that
  sits flat at about y 66, so it breaks the horizon from a long way out and gives the radioactive dump
  something to walk toward.
- **It is a real hyperboloid**, wide at the ground, pinched at the waist, opening out again at the top.
  The first one had its waist where a real cooling tower has it and read as a chimney, because at block
  resolution the flare above it was about a tenth. It is unmistakable now.
- **The rim is eaten away and the shell is torn open on one side**, so it reads as forty years
  abandoned rather than as a shape someone placed. The floor inside is silted over.
- **Rare on purpose.** You will not trip over a second one.

### Brick smokestacks

- **Common across the demolition yard**, where the tower is rare. The yard is where things were made,
  and it should look like it.
- **One in three has come down**, lying across the ground in a line of broken brick with a stump where
  it stood. The two read as one place, which is the point of having both.
- **The standing ones smoke.** There is a fire buried up near the top, and the plume is visible at
  distance. It is not how a chimney works and it is what makes the yard look inhabited.
- **Slender.** The first pass was four to one and generated brick keeps; a real industrial stack is
  nearer ten to one, and these are between six and nine.

### And something lives at the foot of each

- **A husk at the bottom of every standing chimney.** It reaches out past the brick on purpose, so
  walking past one is an encounter rather than scenery. You do not have to break in.
- **Parched in the tower's basin**, wearing leather caps, because a large part of that floor sees sky
  straight up the throat and a skeleton in the open burns.
- **Both work in daylight.** An ordinary spawner does not, which is the sort of thing you only find out
  by walking up to a landmark in the afternoon and watching nothing happen.

### Cardboard

- **The first building family you can reach.** Everything else in this world is gated behind a machine,
  a tool or a journey. Piles of flattened boxes sit on top of the mounds; break one by hand and it
  gives you cardboard, four of which make a block, and the block makes a slab, stairs and a wall.
- **No tool, no station, no recipe to learn.** It is the plainest thing in the mod and that is the
  whole feature.
- **A pile is worth about one block**, and an average mound carries about four of them.

### Smaller things

- **A snack cake in the household stream.** The cream-filled sponge famous for outlasting everything,
  found intact in a landfill. It is the one food down here that does not gamble and does not need
  cooking: it feeds you as well as a cooked roach and holds you nowhere near as long, which is what
  empty calories are.
- **The Roach got the per-part treatment the Pigeon has**, so it no longer reads as one flat colour,
  and its spawn egg stopped being a blob.
- The radioactive dump guidebook entry said "trefoil". It says what the symbol looks like now.

## v0.15.0

**Two mods that could not be started in this world now can, the plain gained a second frontier region,
and there are spawn eggs for the first time.** v0.14.0 went after the vanilla items no ancient city
could hand you. This one goes after the living things: a world with no animals in it, and two of the
pack's biggest mods sitting on materials that never generate here.

### Amber, and the only spawn eggs in the game

- **Amber turns up in household waste with an insect in it**, about one pull in nine hundred, and the
  insect is carrying the blood of whatever it last fed on. Hold a piece and it names the creature.
- **The Sequencer reads it out.** A powered block with a screen: feed it a stamped piece and it hands
  back an Idea Fragment naming that creature. A Teardown Workbench cannot do it, because what is in the
  amber is not a part.
- **Four fragments of the same creature make a Blueprint, and that sheet makes the egg.** Lay it in the
  middle at the Scrap Crafting Table with Glass Shards around it and Rendered Organics beneath. It is
  the one recipe in the game where a Blueprint goes into the grid rather than into your pocket, and the
  sheet comes back out of it - it will make that egg again for as long as you can find glass.
- **Nothing else in this world produces a spawn egg.** A plain with no mobs on it stays that way until
  you go and find one in the rubbish.

### Spent Amber, and the only resin there is

- **The stone does not survive being read.** Breaking it up is how you reach what is inside, so what
  comes out beside the fragment is a heap of dull chips.
- **Turpentine puts back the part that left.** Amber is pine resin with the volatiles driven off over
  millions of years, and turpentine is distilled from pine resin - it is that missing fraction rather
  than a substitute for it. One heap of chips and one tin, at any bench, make a lump of fresh resin.
- **Neither half does anything alone**, and that is the point. Vanilla's own resin recipes are circular:
  a clump costs a resin block, and a creaking heart costs the resin it is the source of. This is the
  only way into the family, and all nine resin items hang off it.

### The radioactive dump, the second frontier region

- **Powah was unstartable here.** Every non-circular route to its uraninite runs through an ore whose
  biome modifiers gate on a tag this world deliberately ships no entry for. Rather than open that door
  to every mod at once, the material is found, in a region we place ourselves.
- **A landfill with drums in it**, at onset 1024 - double the demolition yard, so it is a real journey.
  Mill tailings in open heaps, steel drums with a trefoil on the side, and the ordinary domestic
  radioactive objects that genuinely do end up in refuse.
- **The tailings are an impoundment, not a heap.** The first pass built 5 to 11 wide mounds and a block
  census said the proportions were right; a screenshot said cupcakes with a candle on each, and the
  screenshot was right. They are now one enormous engineered pile after Moab and Church Rock: radius 10
  to 16, flat on top, with a pale turquoise decant pond and a barren stained ring.
- **No radiation yet.** That is deferred to Mekanism; hostile spawns are on, the same set as the yard.

### Applied Energistics 2 is playable

- **The presses were half the answer and shipped as if they were all of it.** v0.14.0 put the four
  Inscriber presses in the sewer sump, which gave you an Inscriber and nothing to put in it: every one
  of AE2's 364 items traces back to certus quartz, and certus comes only from meteorites, which do not
  fall here.
- **Four routes now, none of them a find.** Silicon separates out of E-Scrap, certus out of the
  demolition yard's granite, fluix likewise, and Sky Stone Shards ride the slag rubble stream. A
  playthrough wants four to eight stacks each of certus and fluix, and only a machine produces at that
  scale.
- Separating silicon out of E-Scrap also decouples the tree: AE2 smelts silicon from certus dust, so
  certus used to bottleneck everything rather than only the things that need a crystal.

### Breeze Rods

- **No breeze can spawn in this world.** A Breeze Rod drops only from a Breeze, a Breeze only from a
  trial spawner in a trial chamber, and this world opts into three structures: nether fortress, bastion
  remnant, and its own sewer.
- **Four gunpowder press into a Propellant Briquette, and the Sintering Kiln fires it into a rod.**
  Vanilla's one rod to four wind charges does the rest, so a wind charge costs one gunpowder.

### Ender IO

- **Grains of Infinity turn up in Mechanical Waste.**
- **Its SAG Mill no longer grinds a blaze rod back into four powder.** This mod's chain runs the other
  way - four powder press into a briquette and the kiln fires it into one rod - so that recipe alone
  made the round trip break even, and with a vibrant alloy grinding ball it returned seven powder for
  four. Blaze rods gate brewing here.

### Fixes

- **A frontier region's share of the world depended on its array position.** The picker sliced a bell
  curve into equal-width buckets, so three regions came out 16/68/16 and four came out 7/43/43/7.
  Appending a region rather than inserting it silently made it the rarest thing in the game, for a
  reason nobody reading the preset could have guessed.
- **The sorting page showed twenty-nine ambers.** Every species is its own loot entry, so JEI drew 29
  identical orange slots and buried the twelve materials above them - while the number you actually
  care about, how often amber turns up at all, was split 29 ways and never shown. One slot now, at the
  real combined rate, cycling every species.
- **Amber said "A trapped Turtle."** It is the insect that is trapped; the turtle is DNA.
- **The Sequencer had two textures and no facing**, so its lens was on all four sides and its underside
  was a copy of its lid. It has four faces now and turns to face you when placed.
- **The radioactive dump's fog never rendered**, along with three other biomes' - 26.1 moved fog, sky
  and ambient sound out of the biome's effects block, and a key left behind there parses fine and does
  nothing. The two frontier regions look different at range now, which was the point of setting it.

## v0.14.0

**The deep dark comes to the Nether, and two mods that could not be played here now can.** v0.13.0
closed the last of the resource gaps in this mod's own tree. This one goes after the things that were
missing *around* it: the nine vanilla items no ancient city could ever hand you, and the two mods in the
pack that were either unplayable or built on materials this world has never heard of.

It also fixes a machine that has been uncraftable for four releases without anybody noticing.

### Ancient Sculk

- **There is no deep dark here, and there is no city under one.** Nine vanilla items had no source at
  all because of it.
- **Rare seams of Ancient Sculk now run through the compacted depths**, lit from inside by a blue-green
  glow, roughly one block in seven hundred. Sculk takes hold on dead organic matter, and techno-organic
  waste is exactly that: a city's flesh and machinery fused and buried.
- **A diamond sledgehammer or better**, and nothing else. Not a hand, not a pick, not a copper or iron
  hammer however long you swing. It is the first block in this mod that cares how good your tool is
  rather than just which tool it is, and the netherite sledgehammer finally has something to be better
  at.
- **It breaks into Sculk Powder, three to five a block**, and never into itself. The powder is the whole
  family: it packs back into sculk, spreads into veins, and builds a sensor around redstone or a
  shrieker around soul sand.
- **A catalyst costs an echo shard**, and there is one of those at the bottom of every sewer. That is a
  hard price and it is the right one - a catalyst grows sculk wherever something dies near it, so you
  only ever need the one, and the only other thing in this world that came out of the deep dark is the
  shard in a sump.

### The Trommel or the Pulverizer was uncraftable, and had been since v0.10.0

- **Their recipes were byte-identical.** Same shape, same five steel offcuts, one motor, three plating.
- **A crafting grid gives one result, so two recipes that accept the same grid are one recipe.** One of
  those two machines simply could not be made, and it failed in the quietest way this mod has: no
  error, no log line, a JEI page saying it works, and the other machine coming out instead.
- **The Trommel's motor now sits at the end of the drum** rather than the middle. Same items, same
  counts, only the arrangement - a trommel is a drum driven from one end and a pulverizer's rotor is
  central, so each is now built the way it actually works.
- Every crafting recipe this mod ships is checked against every other one from now on.

### Applied Energistics 2, which could not be started at all

- **AE2's whole tree hangs off Sky Stone, Sky Stone comes from meteorites, and no meteorite can fall in
  this world.** Its own recipes for the Inscriber presses take the same press as the stamp, so they
  copy a press rather than make one. There was no way in.
- **All four presses are now in the crate at the bottom of a sewer**, together, guaranteed. That is how
  AE2 hands them over anyway - one Mysterious Cube gives the set - and a sewer is a good deal rarer
  than a meteorite.
- **Its own tooltip used to send you after meteorites.** It now tells you where they really are.
- Only when AE2 is installed. Nothing about the crate changes without it.
- **Correction, one day on:** this clears one of AE2's two gates, not both. Certus quartz is
  meteorite-only as well, so the presses give you an Inscriber with nothing to put in it yet. Tracked
  as #276.

### Simple Magnets, re-themed onto Magnet Scrap

- **A basic magnet wanted an ender pearl.** An ender pearl in a magnet is a placeholder, not a recipe.
- **Magnet Scrap is the honest material** - neodymium is recovered from hard drive actuators and speaker
  voice coils, which is exactly what that item is. All four recipes are rebuilt on it, on scrap metal
  and on copper, with Fused Circuitry standing in for the diamond so the advanced magnet costs a trip
  to the Nether.
- **Spending Magnet Scrap on magnets means not spending it on redstone**, since it is the only source of
  either. That tension is the point.
- **The Demagnetization Coil is wound on a magnet scrap core.** Real e-waste plants degauss drives
  before shredding them, so a coil of copper around a recovered magnet is what the machine is.
- Only when Simple Magnets is installed.

### Fixes

- **The guidebook's account of curing a villager was wrong** about what you need and where to get it.
- **The sapling lockout said "never"** where the truth is narrower: one cannot be found, but a wandering
  trader will sell you one now that emeralds exist.

## v0.13.0

**Three things that could not be reached now can be: brewing, netherite, and emeralds.** v0.12.0 gave
you a place to go. This one is about what was still missing once you got there, and it closes the last
of the resource gaps that had been sitting open since the reachability sweep.

It also adds the fifth machine, and the fifth machine is the first one that puts something back together
instead of taking it apart.

### The Sintering Kiln

- **Every machine until now ran one direction.** The Trommel cuts a block into its drops, the Separator
  divides a mixture, the Pulverizer grinds things finer, the Slag Furnace melts rock to glass. All four
  take something apart, and the Pulverizer alone had seven recipes that every one of them turned
  something into a powder. Nothing turned powder back into a solid.
- **The kiln fires a pressed powder until the grains fuse.** Not melting: a kiln holds a heat below
  that and waits, which is how you make a solid out of dust without turning it into a puddle.
- It is brick, a Bulb and a Steel Offcut, and it burns fuel rather than power.
- **Press four blaze powder into a Blaze Briquette at any bench, fire the briquette, get a blaze rod.**
  That is a brewing stand, and every potion behind it, without setting foot in a fortress.
- **Four is not arbitrary.** A rod breaks back down into two powder, so at any lower price you could
  make rods out of nothing by going round in circles. It costs four and refunds two.

### Netherite

- **Netherite scrap was already down there and the ingots were already yours.** What you could not do
  was upgrade anything, because that needs a smithing template and vanilla's only recipe for one
  consumes a template to make it. A bastion chest was the sole way in.
- **A Worn Forging Die** now turns up in the compacted depths, a bit over two percent of pulls. A forging die is
  the block a press shapes metal against, so the pattern lives in the die, which is what a smithing
  template is.
- **Tear one down at the Workbench with a prybar** and it gives up almost nothing, because a worn-out
  die has nothing left to give but its shape. Four of them and you have the pattern for good.
- **It is expensive on purpose:** making a template from the pattern costs more than copying one you
  already have, so a bastion is still the quick way and this is the way that does not need one.

### Emeralds, and the only villager in the world

- **There are no villages here and there never will be.** What there are now is zombie villagers, among
  the ordinary zombies out in the demolition yard.
- **Cure one the usual way** - a splash potion of weakness and a golden apple - and you have the only
  trade in the game. Both halves of the cure are reachable, and one of them only became so with the
  kiln above.
- **The yard's hostile mobs are now vanilla plains' list outright**, entry for entry, rather than the
  short list it had. That is what makes the zombie villager as rare here as it is anywhere else instead
  of a third more common by accident.

### Red sand

- **Some of the sand out of Reinforced Concrete now comes up red.** That is rust: the aggregate nearest
  the steel has been staining for as long as this place has been standing, and red sand is iron-stained
  sand wherever you find it.
- Nothing else you can dig up here is red, and the whole red sandstone family comes off it - eleven
  blocks that had no route at all. (A wandering trader will sell you some too, now that emeralds
  exist.)

### The compacted depths get a chapter

- Six entries in the guidebook: getting there, why the dimension is solid, terrain from shards, which
  machine each scrap goes to, lignite, and the forging die.
- The book had nine categories and none of them was the Nether, while almost nothing down there behaves
  the way vanilla does.

### Changed behaviour

- **Slimes are no longer sewers-only.** Bringing the demolition yard's hostile mobs in line with a
  vanilla plains brought slimes with them, and because this world's ground sits low, they turn up out in
  the yard the way they would in any ordinary overworld chunk.
- **A sewer is still much the better place to find them.** Down there they come from the sewer being a
  sewer; up in the yard they come from being in the right chunk, which is about one in ten.

### Worth knowing

- **The compacted depths still need a new world**, as in v0.12.0: a dimension's generator is written
  into a save when the world is made.
- **Villagers will sell you things this world otherwise gates.** Saplings, buckets and iron gear are all
  on vanilla's trade tables, and trading is not yet curated. If that matters to how you want to play,
  it is worth knowing before you cure one.
- **The crimson and warped families are still short**, and crying obsidian and resin still have no
  source.

## v0.12.0

**There is a Nether now, and it is a dump you mine.** The overworld is a dump you clear: you sort it,
green it, and push the grey back. Down here nothing is going to be cleared. The compacted depths are
solid from the bedrock floor to the bedrock ceiling, wall to wall, and the only open spaces are the
things buried in it.

Getting there is the point of the whole slag chain that came with it. Obsidian is made, never found,
and until this release there was nothing that made it.

### The way in
- **Slag comes out of the Cupola Furnace whether you want it or not.** Every eighth smelt it rakes off
  a lump into a second output slot. It is the non-metallic fraction that floats off any remelt, so it
  has no recipe and you cannot ask for it.
- **The Slag Furnace vitrifies it into obsidian**, one lump to a block, over a burn twice the length of
  a normal smelt. It runs on fuel rather than power, and it is the only obsidian in this world.
- **So the portal is earned rather than found**, and the cost is upstream: the eight smelts it takes to
  rake one lump ARE the price.
- Slag has two other exits if you would rather not build a portal. The **Separator** divides it into
  concrete powder with recovered scrap metal as the byproduct, and the **Pulverizer** grinds it into
  Fertilizer. Ground slag really was sold as phosphate fertiliser for a century.

### The compacted depths
- **Every column is full.** Techno-organic waste, floor to ceiling, with pockets of slag rubble and lava
  through it. There is no open cavern, no lava sea and no ceiling to fall from.
- **Techno-organic waste is the Nether's Block of Garbage.** Right-click to pick through it, same as a
  mound. It has four faces so a tunnel does not read as wallpaper, and unlike a Block of Garbage **it
  does not fall** - a dimension of falling blocks would bury you the moment you started.
- **Slag rubble is the Nether's Stone Rubble**, and it does fall.
- **Fortresses and bastions generate normally.** Everything vanilla puts in them is still there.

### Nether terrain is rebuilt, not mined
- **The depths generate no vanilla nether blocks at all.** No netherrack, no basalt, no soul sand,
  nothing. Sorting slag rubble gives **shards**, and four shards craft the block.
- Seven of them: netherrack, basalt, blackstone, crimson nylium, warped nylium, and clumps of soul sand
  and soul soil. The soul pair are clumps rather than shards because a shard of sand reads as a
  mistake.
- **Each one is the only route to its block**, which is what makes the dimension worth mining rather
  than a place to walk through.
- **The nylium pair may be worth more than the block they make.** Bone meal on nylium grows fungus, and
  a fungus bone-mealed on its own nylium becomes a huge one. Nothing down there grows a fungus forest,
  so those two shards are the only seed for one.

### The waste gives scrap; your machines give materials
This is the same rule the overworld runs on. Copper is not in a household mound, scrap metal is, and
the Cupola smelts it. So quartz is not in the waste either.

- **Fused Circuitry** separates into **quartz**, with circuit powder as the byproduct - which is stage
  one of the gold chain you already had. A quartz crystal really is what keeps a circuit board's clock.
- **Phosphor Scrap** pulverizes into **glowstone dust**. A fluorescent tube is coated on the inside with
  a phosphor powder; grinding the glass to recover it is what a lamp recycler does.
- **Rendered Organics** separates into **nether wart**, with organic muck as the byproduct. Wart is a
  fungus and spores survive being cooked and buried better than anything else in a dump. It pairs with
  the soul sand the rubble already gives you, since wart grows on nothing else.
- **Oily Swarf** answers to two machines. The mill reduces it to **blaze powder**; magnesium and
  titanium swarf is a real and well-known fire hazard, fine enough to ignite in air. The separator
  divides it into **magma cream** and scrap metal, because swarf comes off the tool soaked in cutting
  fluid and real swarf recycling starts by separating the metal from the oil.
- **Netherite scrap** comes out of the waste directly, about one pull in 260. Vanilla
  already calls it a scrap.

### Coal, at last
- **Lignite** is found in the waste, and smelting it gives **coal** - the only route to coal in this
  world.
- Lignite is not a stand-in. It is a real rank of coal, the one between peat and the coal vanilla
  ships, and the depths are buried compacted organics under heat and weight, which is not like a coal
  seam forming, it is one.
- **It burns on its own, at half a coal.** So it is useful the moment you find it, in a dimension with
  no wood, and upgrading it visibly pays: one lignite of heat smelts four.
- Charcoal already covered every coal recipe except the storage block, so this is a material and a
  found fuel rather than a wave of new recipes.

### Machines
- **The Cupola Furnace has a second output slot** for the slag it rakes off.
- **No machine recipe takes more than one input any more.** The Separator, Trommel and Pulverizer have
  no screen and nothing can reach into them, so a part-finished batch was invisible and could only be
  recovered by breaking the block - and a part-finished batch was the normal case, since the pull
  streams hand scrap out one item at a time. This affects six recipes; each one now consumes one item
  per run.
- **A Water Tank holds water when it is loose.** It was the only component named for a capacity rather
  than an action, and a tank that does not hold reads as broken rather than as a part. Every other
  component is still inert. Once it is part of a formed machine, the machine's tank takes over.
- **The Tree Nursery grows pale oak**, the ninth species. It grew eight and vanilla has nine, and since
  saplings are stripped out of every loot roll, a species the picker does not list did not exist.

### Fixes
- **Every paragraph break in the guidebook was being swallowed.** All 71 text pages ran their
  paragraphs together with no space between them, and had done for several releases. It reads as a
  typo rather than a layout fault, which is why it survived so long.
- **The Cupola Furnace has its own JEI category.** It runs vanilla blasting recipes, so they always
  showed - but vanilla blasting draws one result and the machine hands back two things, so the slag
  that the whole obsidian chain hangs off was invisible.
- **Both smelters have a JEI transfer button again.** Its absence read as "this recipe is uncraftable"
  rather than "this button is missing".
- **The Cupola no longer shows every blasting recipe twice in JEI**, once with slag and once without.

### Worth knowing
- **The Nether generator is baked into a save when the world is made**, so an existing world keeps
  whatever Nether it already had. The compacted depths need a new world.
- **Brewing is still fortress-gated.** Nether wart has a route now, but a brewing stand needs a blaze
  rod and nothing down there grinds into one.
- **Netherite gear is still unreachable.** The scrap is findable; the smithing template it needs comes
  only from a bastion and is self-referential.
- **The crimson and warped families are still short.** The nylium shards may open them by cultivation;
  that is untested.
- **The depths have no guidebook chapter yet.**

## v0.11.0

**There are sewers under the demolition yard.** Brick tunnels running with leachate, dug into rock
that had to be made deep enough to hold them. They are the first place in this world that is built
rather than dumped, and the first place worth going down instead of out.

### The ground got deep
- **The world now has real rock under it.** It was a coarse-dirt slab 7 to 11 blocks thick sitting on
  about 120 blocks of empty space; it is now 59 to 63 blocks thick, with bedrock still on the
  underside and the void still below that.
- **Nothing on the surface changed** - same height, same shape, same everything you can see or stand
  on. The change is entirely underneath.
- **This is groundwork for the sewers**, which need somewhere to be. There was not enough room down
  there for a structure of any size.
- **There is roughly ten times as much deepslate underground.** It changes no recipe and opens no gate -
  deepslate was already craftable from stone shards without limit, and the iron gate has been a
  blasting-recipe gate rather than a scarcity one since v0.6.0 - but the material is there now where it
  was not.
- **It only affects newly generated land.** Chunks an existing save has already visited keep the thin
  slab and will never hold a sewer, and the boundary between old and new terrain will be visible
  where you walk into fresh chunks. A new world avoids both.

### Getting in
- **Look for a square of pale concrete set flush in the ground, with a rusted plate in the middle.**
  That is a manhole, and under it is a ladder that goes a long way down.
- **The plate comes up with a Prybar and nothing else.** Bare hands will tell you so.
- Sewers are under the **demolition yard**, not the household sprawl, so finding one is a reason to
  travel rather than something that happens at spawn.

### What a sewer is
- **Brick corridors, junctions and stairwells**, branching out from one chamber and running downhill.
- **A leachate channel down the middle of every run**, with dry brick either side to walk on.
- **Cobwebs**, which come off with shears.
- **Decay follows the water.** Mossy and cracked brick gather at the waterline rather than scattering
  evenly, silt settles in the corners where the flow slows, and mushrooms grow in the damp.
- **Light follows the people.** The chamber, the entrance shaft and the animal dens are lit; the
  corridors are not, and that is deliberate. Dark is where things spawn, so a lantern in the wrong
  place would quietly switch the sewer off.

### What lives down there
- **Slimes.** This is the only place in the world one spawns - the two routes vanilla gives them both
  need something this world does not have - and a slimeball has no other source here.
- **Roaches**, which until now could only arrive by being disturbed out of a garbage block. They still
  can, anywhere; a sewer is the only place they live on their own.
- **Drowned**, around what is spawning them.
- **Turtles and frogs**, in a den each - sand for the turtles, mud for the frogs, which is the ground
  their own game logic names. **There are only ever as many as you find.** They cannot breed or lay
  eggs down here, so a sewer's animals are the animals it was built with.

### The bottom
- **Every channel runs downhill and they all run to the same room.** The sump is the low point of the
  system.
- **Standing leachate deep enough to go over your head, no lamp, and a drowned spawner on the
  walkway.** A walkway crosses the entrance so you see the water before you are in it.
- This is the one place in a sewer that is guaranteed to be dangerous. Everything above it is a roll.

### What you take home
- **Every sewer has an access chamber**: a dry, lit side room off one of the runs, with barrels in it.
  The loot used to sit in the room you arrive in; it is now somewhere you have to walk to, and
  somewhere that explains why it is there.
- Barrels carry scrap and, less often, a machine part - a Bulb, a Pump, a Motor, a Machine Frame.
- **An echo shard, in a crate settled in the sump's silt.** One per sewer, and **the only source of
  one in this world.** It is under the water rather than beside it: the hazard the room already had is
  what guards it.

### Digging the silt
- **The silt is suspicious sand and suspicious gravel.** Brush it. Most of what you dig is silt,
  because that is what silt is, and now and then something that went down a drain a long time ago
  comes back out of it.
- **Mining a deposit gives you nothing.** Brush it into ordinary sand or gravel first, then mine that.
- **A brush is a feather, a stick and a copper ingot, so you want chickens before you want this.**
  Nothing else in a sewer needs a tool you might not have.

### Leachate can drown you
- **It could not before, and now it can - everywhere, including the surface pools out in the sprawl.**
- Drowning is checked at the eye, so one block deep is enough if you are crawling or swimming. Walking
  through a pool is still fine.
- It still does no damage on contact, and it still leaves you hungry.

### Fixes
- **Turtles no longer suffocate in their own den.** A turtle is 1.2 blocks wide - wider than the block
  it stands on - and three of them were being placed a block apart in a room sized for something
  narrower, so they spawned inside each other and inside the walls. The den is bigger and the animals
  are spaced by how wide they actually are.
- **A Water Tank, Solar Panel or Rain Collector Funnel placed on its own comes back when you break
  it.** They were vanishing: the block you place and the block a formed machine uses are the same one
  for those three, and the rule that stops a formed machine dropping loose parts was taking them too.

## v0.10.0

**Two new machines, and the two chains they unblock.** The Separator used to do three jobs; it now
does one, and the jobs it gave up went to machines that can actually do them. Out of that come gold
and clay - the last big gaps in what this world can make.

### The Trommel: sorting, unattended
- **A four-block-long rotating drum that sorts while you are elsewhere.** Built in the demolition
  yard from a core, four Steel I-Beams, a Motor and two Machine Frames.
- **It yields exactly what a Sorting Tarp yields per block.** Not more. At one block every two
  seconds a determined player at a tarp is faster - what you get is that it does not need you. Build
  a second one rather than waiting on the first.
- **Two ways in.** Drop scrap anywhere along the drum, or stand a chest, barrel or hopper on it and
  the machine empties it a little at a time. Nothing can push into it; it reaches out and takes.
- Output leaves by the open end of the drum, into any wired Scrap Bins first, then whatever you park
  there, then thrown clear.

### The Pulverizer: a hammer mill
- **A sealed two-by-two-by-two box that grinds things finer.** A core, a Motor, two Machine Frames
  and four Steel I-Beams.
- **You cannot see inside it, and that is the machine.** The Separator shows you its bay and the
  Trommel shows you its screen because both are open. This one is a closed steel box with a rotor in
  it, so the roof carries a hatch to show you where the material goes in.
- Feed it from the top - drop material on the hatch, or park a container up there. Powder leaves by
  the front.
- Bone grinds to bone meal, four from a bone and nine from a block.

### Gold, out of the boards
- **Grind four E-Scrap into Circuit Powder, then blast it in a Cupola Furnace for a gold nugget.**
- A tonne of circuit boards carries far more gold than a tonne of ore. It is why anyone takes
  electronics apart, and why this world has no gold in the ground and plenty in the rubbish.
- Burning a board whole gets you nothing - the metal is locked in resin and glass, and grinding is
  what frees it.

### Clay, out of broken pots
- **Crush a pottery sherd for Grog, mix three Grog with a Kitty Litter, then right-click the result
  on a water cauldron.** That is clay, and it costs a level of water.
- **Pottery sherds and kitty litter now turn up in the dump.** Keep the litter - it is the only
  thing here that will make a clay body hold together.
- Clay unlocks 43 vanilla items: every brick, all sixteen terracotta, all sixteen glazed terracotta,
  the flower pot and the decorated pot.
- You cannot un-fire a pot. Crushed pottery is grog, which controls cracking and does the opposite of
  sticking - the stickiness has to come from the bentonite in the cat litter.

### The Separator sorts no longer
- **Feeding it a Block of Garbage, Trash Bag, Compacted Bale, Stone Rubble or Mechanical Waste no
  longer does anything.** Those go to a Trommel now.
- **Everything else is unchanged.** The machine, the build, the chute and all three separating
  recipes work as they did, and a Separator you have already built keeps running.
- Why: a shear shredder tears things apart, which is the opposite of telling them apart.

### Fixed
- **Breaking a machine now gives back every part, including the machine itself.** Breaking a
  Separator or Trommel core with the wrong tool used to destroy it outright, while breaking any of
  its other blocks handed it back - so the rule was opt-out and you could lose an expensive build to
  a wrong swing.
- **Breaking one cell of a machine returns the part you put in that cell.** A Separator's Motor came
  back as a Machine Frame, and a Trommel's cells came back as pieces with no recipe at all.
- **Machines wider than three blocks now come apart properly.** A cell far enough from the core
  never found it, so the machine stayed assembled with a hole in it and kept running.
- Trommel and Pulverizer can be powered at all - neither accepted energy from a generator when first
  built.
- The Trommel's drum no longer turns when the machine is stopped.

### For pack authors
- **`recompile:pulverizing`** is a new recipe type: one input, one finer output, with `count` as the
  ratio dial. `recompile:separating` is unchanged.
- The Pulverizer and Trommel join `#recompile:scrap_connectable`.

## v0.9.0

**If you played 0.8.0, the game was lying to you about salvage.** Jade said "No salvage value" for a Dirty Mattress, and for everything else, while JEI's Teardown panel sat empty. Teardown worked the whole time - the bench did its job, the fragments arrived - but nothing in the game would admit it existed. That is fixed, and it is the reason to update.

**And the fridge is the first thing in the dump that gives you a choice you do not get to make.** It holds a motor, a pump and a bulb, the way a real one does. Tear it down and you recover exactly one of the three, and whichever it is, that is the one you come away knowing.

### Fixed: the viewers could not read a single recipe
- **Jade and JEI could not see any teardown at all in a packaged install.** They read the mod's own recipe files, and the code that found those files only worked when the mod was an unpacked folder, which is a development layout and not one you have ever had.
- The symptom was the worst kind: nothing looked broken enough to report. Salvage worked, so the only tell was every viewer quietly insisting your mattress was worthless.
- This never affected a development run, which is why it survived a release.

### The Dead Fridge
- **A two-block-tall fridge now turns up in Bulky Waste**, and it replaces the Broken Fan and the Light Fixture. Pry it open with a Prybar the way you would any bulky find.
- Tearing one down gives you **eight pieces of scrap** split between metal, plastic and electronics, **something out of the freezer**, and **one of a motor, a pump or a bulb**.
- **Knowledge follows the draw.** Pull a motor out and you learn about motors. Pull a bulb out and you learn about bulbs. Four of the same lesson still makes the Blueprint.
- **The freezer is the only ice or snow in this world.** Nothing here snows and nothing freezes, so if you want either, you go looking for fridges.
- Appliance finds arrive exactly as often as they did before. The fridge carries the weight the two removed finds had between them.

### Teardowns roll their materials now
- **What a teardown gives you varies.** A fridge does not hand over a fixed pile of scrap; it rolls eight times across metal, plastic and electronics, so no two come apart the same way.
- The Printer, the Washing Machine, the Dirty Mattress and the Broken Hydroponics Bay all work this way now. What you get on average is unchanged, but any single teardown is its own draw.
- **What was guaranteed stays guaranteed.** A Washing Machine still always gives a Pump, and a Printer still always gives its ink sac.
- Packs can write their own teardowns in this form. It is the same weighted-list shape the sorting tables already use.

### One workstation, not three objects in a row
- **The Scrap Crafting Table, the Sorting Tarp and the Teardown Workbench now share a bench.** Lined up they read as one continuous work surface instead of three unrelated blocks, which is what a Scrap Network cluster is meant to look like.
- Their tops run the full width of the block, so neighbouring stations actually touch. There used to be a two-pixel gap.
- The Sorting Tarp is tarp blue, and the Teardown Workbench has proper bench art rather than a cube's.

### Found, not crafted
- **Everything that comes out of a pull stream is now something you find rather than make.** Bowls, shears, flint and steel, leads, name tags, paper, books, bundles and all four pieces of leather armour lost their recipes.
- The rule is enforced rather than remembered: anything tagged this way is checked against every recipe in the game at load.
- **Music discs are gone from the streams.** A disc every couple of hours was not worth the slot.

### Rarer
- **Buckets, shears, flint and steel, and leads** now turn up about **once every half hour each**. A bucket was arriving roughly every two minutes.
- **Name tags** about hourly.
- **Collectibles are 480 times rarer than they were.** They are meant to be the thing you remember finding, not something the barrel fills up with. A whole one now runs to hundreds of hours, and a Puzzle Cube longer still.
- **Roaches** were interrupting a pull two and a half times per mound sorted. Now about once a mound, or one every ten minutes or so.
- Bulk material is untouched. Junk, scrap, plastic, glass shards and the rest come out just as fast as before.
- These are derived from playtime rather than guessed. A held right-click sorts at five pulls a second and sorting is about a quarter of play, so roughly 4,500 pulls an hour. Every rate above was converted into minutes before it was chosen.

### Carpets
- **Wool carpets no longer turn up in trash bags, and are craftable again.** A rug you find every few minutes is not worth finding. Wool is still a bag pull, so you make them the ordinary way.

### Other fixes
- **The Scrap Crafting Table could not see everything in a connected barrel.** What the network reported was capped at eighteen distinct materials, fewer than a single barrel holds, so a well-stocked cluster had items the shelf could not show and that JEI's recipe transfer called missing. A barrel holding nineteen Rebar would report "Not in your inventory or any connected storage". The cap is gone, and connecting several barrels aggregates all of them.
- **The Rain Collector Funnel is built like a hopper**, with the Machine Frame in the middle and plastic around it.

### Updating an existing world
- **The Broken Fan and the Light Fixture no longer exist.** Any you have placed in a world, or holding in a chest, are gone after the update, and the game will log an unknown-block warning for each one it clears. Tear them down for their Motor and Bulb before you update if you want to keep the value.
- Everything else carries over. The fridge only appears in Bulky Waste you have not opened yet.

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

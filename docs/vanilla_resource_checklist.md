# Vanilla Resource Checklist, checked against Recompile

Every resource vanilla Minecraft gives a player **without a crafting grid**, grouped by where you
go to get it - and for each one, whether a player of **Recompile standalone** can actually get it.

- `[x]` reachable, followed by the route that reaches it.
- `[ ]` not reachable, followed by why not.

| | |
|---|---|
| Minecraft version | 26.1.2 |
| Catalogued | 886 |
| **Reachable in Recompile** | **784 (88%)** |
| Not reachable | 102 |
| Mobs obtainable | 59 |

**How the checkmarks were decided.** Not by judgement: by a reachability closure over the mod's
own data. Seeded from what the garbage world actually generates (its 4 biomes, its terrain rules,
its sewers/cooling towers/smokestacks, and the vanilla nether fortress and bastion its biome tags
let through), plus every mob that can exist, plus the mod's loot tables. Then closed under every
recipe that still loads - vanilla minus the 30 the mod disables, plus the mod's own 170 and its
seven custom recipe types - until nothing new appeared. Interactions that are neither loot nor
recipe are encoded explicitly (bucket fills, axe-stripping, oxidation, the Compost Heap volunteer,
the Sequencer's byproduct, the Dry Clay Body cauldron step).

**Where the mobs come from.** The starting biome is creature-free by design, so the roster is
assembled: the frontier regions spawn the hostile set, the compacted depths spawn the nether set,
the sewers seat a drowned spawner and house turtles and frogs, the landmarks seat a parched and a
husk, **Animal Bait** draws 16 farm and wild species, the **Sequencer** turns amber into spawn
eggs for 29 more, and **curing a zombie villager** opens the whole villager trade tree.

**Coverage by domain**

| Domain | Reachable | Total |
|---|---:|---:|
| Wood | 58 | 66 |
| Overworld - General Surface | 94 | 99 |
| Forest | 10 | 10 |
| Jungle | 6 | 6 |
| Desert | 7 | 7 |
| Badlands | 4 | 4 |
| Taiga | 2 | 2 |
| Swamp | 9 | 9 |
| Snowy | 5 | 6 |
| Mountain | 1 | 1 |
| Mushroom Fields | 5 | 5 |
| River | 2 | 2 |
| Ocean | 67 | 95 |
| Cave & Underground | 107 | 128 |
| Nether | 73 | 82 |
| End | 11 | 25 |
| Structures & Chest Loot | 224 | 238 |
| Trading | 62 | 62 |
| Fishing | 1 | 1 |
| Archaeology | 36 | 38 |

**Legend.** `(c)` also craftable. `(finite)` non-renewable in vanilla terms.

---

## Wood  <sub>58/66</sub>

*Every log/leaf/sapling family, plus the stripped variants (axe on a log).*

### Mine / break a block

- [x] `acacia_leaves` - grow acacia sapling
- [ ] `azalea_leaves` - no lush caves generate
- [x] `birch_leaves` - grow birch sapling
- [x] `cherry_leaves` - grow cherry sapling
- [ ] `crimson_stem` - the compacted depths has no nylium - only slag rubble and lava break the fill
- [x] `dark_oak_leaves` - grow dark oak sapling
- [ ] `flowering_azalea_leaves` - no lush caves generate
- [x] `jungle_leaves` - grow jungle sapling
- [x] `mangrove_leaves` - grow mangrove propagule
- [x] `oak_leaves` - grow oak sapling
- [x] `pale_oak_leaves` - grow pale oak sapling
- [x] `spruce_leaves` - grow spruce sapling
- [x] `stripped_acacia_log` - use an axe on an acacia log
- [x] `stripped_oak_log` - use an axe on an oak log
- [x] `stripped_spruce_log` - use an axe on a spruce log
- [ ] `warped_stem` - the compacted depths has no nylium - only slag rubble and lava break the fill

### Mob & entity drops

- [x] `bamboo` - kill a panda (spawn egg (amber -> Sequencer -> Blueprint))
- [x] `stick` `(c)` - kill a witch (spawns in the demolition yard / radioactive dump)

### Harvest & interact

- [x] `stripped_acacia_wood` `(c)` - crafted from stripped acacia log
- [x] `stripped_bamboo_block` - use an axe on a bamboo block
- [x] `stripped_birch_log` - use an axe on a birch log
- [x] `stripped_birch_wood` `(c)` - crafted from stripped birch log
- [x] `stripped_cherry_log` - use an axe on a cherry log
- [x] `stripped_cherry_wood` `(c)` - crafted from stripped cherry log
- [ ] `stripped_crimson_hyphae` `(c)` - the compacted depths has no nylium - only slag rubble and lava break the fill
- [ ] `stripped_crimson_stem` - the compacted depths has no nylium - only slag rubble and lava break the fill
- [x] `stripped_dark_oak_log` - use an axe on a dark oak log
- [x] `stripped_dark_oak_wood` `(c)` - crafted from stripped dark oak log
- [x] `stripped_jungle_log` - use an axe on a jungle log
- [x] `stripped_jungle_wood` `(c)` - crafted from stripped jungle log
- [x] `stripped_mangrove_log` - use an axe on a mangrove log
- [x] `stripped_mangrove_wood` `(c)` - crafted from stripped mangrove log
- [x] `stripped_pale_oak_log` - use an axe on a pale oak log
- [x] `stripped_pale_oak_wood` `(c)` - crafted from stripped pale oak log
- [ ] `stripped_warped_hyphae` `(c)` - the compacted depths has no nylium - only slag rubble and lava break the fill
- [ ] `stripped_warped_stem` - the compacted depths has no nylium - only slag rubble and lava break the fill

### Structure chests

- [x] `acacia_log` - grow acacia sapling
- [x] `acacia_planks` `(c)` - crafted from acacia log
- [x] `acacia_sapling` - Tree Nursery (fertilizer + unknown seedling)
- [x] `bamboo_planks` `(c)` - crafted from bamboo block
- [x] `birch_log` - grow birch sapling
- [x] `dark_oak_log` - grow dark oak sapling
- [x] `jungle_log` - grow jungle sapling
- [x] `mangrove_log` - grow mangrove propagule
- [x] `oak_log` - grow oak sapling
- [x] `oak_planks` `(c)` - crafted from oak log
- [x] `oak_sapling` - Tree Nursery (fertilizer + unknown seedling)
- [x] `spruce_log` - grow spruce sapling
- [x] `spruce_sapling` - Tree Nursery (fertilizer + unknown seedling)

### Trading

- [x] `birch_sapling` - Tree Nursery (fertilizer + unknown seedling)
- [x] `cherry_log` - grow cherry sapling
- [x] `cherry_sapling` - Tree Nursery (fertilizer + unknown seedling)
- [x] `dark_oak_sapling` - Tree Nursery (fertilizer + unknown seedling)
- [x] `jungle_sapling` - Tree Nursery (fertilizer + unknown seedling)
- [x] `mangrove_propagule` - Tree Nursery (fertilizer + unknown seedling)
- [x] `pale_oak_log` - grow pale oak sapling
- [x] `pale_oak_sapling` - Tree Nursery (fertilizer + unknown seedling)

<details><summary>Also mineable from structures here, but craftable (9, 9 reachable) - decoration, not a resource</summary>

`acacia_wood`, `birch_planks`, `dark_oak_planks`, `jungle_planks`, `mangrove_wood`, `spruce_planks`, `spruce_wood`, `stripped_oak_wood`, `stripped_spruce_wood`

</details>


## Overworld - General Surface  <sub>94/99</sub>

*Found across three or more surface biomes, so not biome-specific.*

### Mine / break a block

- [ ] `amethyst_cluster` - no amethyst geodes generate
- [x] `bee_nest` - grow a birch sapling within 2 blocks of a flower (5% bee nest)
- [x] `coarse_dirt` `(c)` - mine coarse_dirt (overworld terrain)
- [x] `creaking_heart` `(c)` - crafted from pale oak log + resin block
- [x] `dirt` - mine mycelium (mycelium patches)
- [x] `grass_block` - Grass Spreader converts coarse dirt to grass
- [ ] `large_amethyst_bud` - no amethyst geodes generate
- [ ] `medium_amethyst_bud` - no amethyst geodes generate
- [x] `mossy_cobblestone` `(c)` - crafted from cobblestone + moss block
- [x] `sandstone` `(c)` - crafted from sand
- [ ] `small_amethyst_bud` - no amethyst geodes generate
- [x] `sunflower` - gameplay/dried_bouquet
- [x] `turtle_egg` - breed turtles on sand

### Mob & entity drops

- [x] `arrow` `(c)` - bastion remnant chest
- [x] `beef` - kill a cow (Animal Bait)
- [x] `black_wool` `(c)` - kill a sheep (Animal Bait)
- [x] `blue_wool` `(c)` - kill a sheep (Animal Bait)
- [x] `bone` - kill a parched (cooling tower spawner)
- [x] `brown_wool` `(c)` - kill a sheep (Animal Bait)
- [x] `carrot` - kill a husk (smokestack spawner)
- [x] `chicken` - kill a chicken (Animal Bait)
- [x] `cyan_wool` `(c)` - kill a sheep (Animal Bait)
- [x] `feather` - kill a chicken (Animal Bait)
- [x] `glass_bottle` `(c)` - kill a witch (spawns in the demolition yard / radioactive dump)
- [x] `glow_ink_sac` - torn down at the Recompile Workbench from printer
- [x] `gray_wool` `(c)` - kill a sheep (Animal Bait)
- [x] `green_wool` `(c)` - kill a sheep (Animal Bait)
- [x] `gunpowder` - kill a creeper (spawns in the demolition yard / radioactive dump)
- [x] `iron_ingot` `(c)` - bastion remnant chest
- [x] `light_blue_wool` `(c)` - kill a sheep (Animal Bait)
- [x] `light_gray_wool` `(c)` - kill a sheep (Animal Bait)
- [x] `lime_wool` `(c)` - kill a sheep (Animal Bait)
- [x] `magenta_wool` `(c)` - kill a sheep (Animal Bait)
- [x] `music_disc_11` - kill a creeper (spawns in the demolition yard / radioactive dump)
- [x] `music_disc_13` - kill a creeper (spawns in the demolition yard / radioactive dump)
- [x] `music_disc_blocks` - kill a creeper (spawns in the demolition yard / radioactive dump)
- [x] `music_disc_cat` - kill a creeper (spawns in the demolition yard / radioactive dump)
- [x] `music_disc_chirp` - kill a creeper (spawns in the demolition yard / radioactive dump)
- [x] `music_disc_far` - kill a creeper (spawns in the demolition yard / radioactive dump)
- [x] `music_disc_lava_chicken` - kill a zombie (spawns in the demolition yard / radioactive dump)
- [x] `music_disc_mall` - kill a creeper (spawns in the demolition yard / radioactive dump)
- [x] `music_disc_mellohi` - kill a creeper (spawns in the demolition yard / radioactive dump)
- [x] `music_disc_stal` - kill a creeper (spawns in the demolition yard / radioactive dump)
- [x] `music_disc_strad` - kill a creeper (spawns in the demolition yard / radioactive dump)
- [x] `music_disc_wait` - kill a creeper (spawns in the demolition yard / radioactive dump)
- [x] `music_disc_ward` - kill a creeper (spawns in the demolition yard / radioactive dump)
- [x] `mutton` - kill a sheep (Animal Bait)
- [x] `orange_wool` `(c)` - kill a sheep (Animal Bait)
- [x] `phantom_membrane` - kill a phantom (insomnia)
- [x] `pink_wool` `(c)` - kill a sheep (Animal Bait)
- [x] `poppy` - kill a iron golem (built)
- [x] `porkchop` - bastion remnant chest
- [x] `potato` - kill a husk (smokestack spawner)
- [x] `purple_wool` `(c)` - kill a sheep (Animal Bait)
- [x] `rabbit` - kill a rabbit (Animal Bait)
- [x] `rabbit_foot` - kill a husk (smokestack spawner)
- [x] `rabbit_hide` - kill a rabbit (Animal Bait)
- [x] `red_mushroom` - mine red_mushroom (sewers)
- [x] `red_wool` `(c)` - kill a sheep (Animal Bait)
- [x] `rotten_flesh` - kill a drowned (sewer spawner)
- [x] `slime_ball` `(c)` - kill a slime (spawns in the demolition yard / radioactive dump)
- [x] `snowball` - kill a snow golem (built)
- [x] `spider_eye` - kill a spider (spawns in the demolition yard / radioactive dump)
- [x] `sugar` `(c)` - kill a witch (spawns in the demolition yard / radioactive dump)
- [x] `tipped_arrow` `(c)` - kill a parched (cooling tower spawner)
- [x] `white_wool` `(c)` - kill a sheep (Animal Bait)
- [x] `yellow_wool` `(c)` - kill a sheep (Animal Bait)

### Harvest & interact

- [x] `armadillo_scute` - brush an armadillo
- [x] `beetroot` - grow beetroot seeds
- [x] `blue_egg` - a chicken lays an egg
- [x] `brown_egg` - a chicken lays an egg
- [x] `brown_mushroom` - shear a mooshroom
- [x] `creeper_head` - a charged creeper kills a creeper
- [x] `piglin_head` - a charged creeper kills a piglin
- [x] `pitcher_plant` - grow pitcher pod
- [x] `pitcher_pod` - sniffer digging
- [ ] `player_head` - needs a charged creeper to kill another player
- [x] `torchflower` - grow torchflower seeds
- [x] `torchflower_seeds` - sniffer digging
- [x] `turtle_scute` - a turtle grows up
- [x] `wither_rose` - the wither kills a mob
- [x] `zombie_head` - a charged creeper kills a zombie

### Piglin bartering

- [x] `crying_obsidian` - bastion remnant chest
- [x] `dried_ghast` `(c)` - piglin bartering
- [x] `ender_pearl` - kill a enderman (spawns in the demolition yard / radioactive dump)
- [x] `leather` `(c)` - bastion remnant chest
- [x] `nether_brick` `(c)` - piglin bartering

### Structure chests

- [x] `apple` - grow oak sapling
- [x] `dandelion` - buy from a wandering trader
- [x] `dead_bush` `(finite)` - chests/aquarium_curator
- [x] `pumpkin` - grow pumpkin seeds
- [x] `resin_clump` `(c)` - crafted from spent amber + turpentine
- [x] `sand` - mine sand (sewers)
- [x] `short_grass` - bone meal on grass
- [x] `wheat_seeds` - Hydroponics Bay seedling

### Trading

- [x] `clay` `(c)` - crafted from clay ball
- [x] `firefly_bush` - buy from a wandering trader
- [x] `pale_hanging_moss` - buy from a wandering trader
- [x] `sugar_cane` - Hydroponics Bay seedling


## Forest  <sub>10/10</sub>

### Mine / break a block

- [x] `closed_eyeblossom` - sewer chest
- [x] `leaf_litter` `(c)` - smelted from acacia leaves
- [x] `lilac` - gameplay/dried_bouquet
- [x] `pale_moss_carpet` `(c)` - crafted from pale moss block
- [x] `peony` - gameplay/dried_bouquet
- [x] `rose_bush` - gameplay/dried_bouquet

### Trading

- [x] `allium` - buy from a wandering trader
- [x] `lily_of_the_valley` - buy from a wandering trader
- [x] `pale_moss_block` - buy from a wandering trader
- [x] `wildflowers` - buy from a wandering trader


## Jungle  <sub>6/6</sub>

### Mine / break a block

- [x] `cocoa_beans` - Hydroponics Bay seedling
- [x] `melon` `(c)` - grow melon seeds
- [x] `melon_slice` - grow melon seeds

### Structure chests

- [x] `fern` - buy from a wandering trader
- [x] `tripwire_hook` `(c)` - crafted from bamboo planks + iron ingot

<details><summary>Also mineable from structures here, but craftable (1, 1 reachable) - decoration, not a resource</summary>

`dispenser`

</details>


## Desert  <sub>7/7</sub>

### Mine / break a block

- [x] `cactus_flower` - chests/aquarium_curator
- [x] `short_dry_grass` - chests/aquarium_curator

### Structure chests

- [x] `cactus` - Hydroponics Bay seedling

### Trading

- [x] `blue_terracotta` `(c)` - crafted from terracotta + blue dye
- [x] `orange_terracotta` `(c)` - crafted from terracotta + orange dye
- [x] `tall_dry_grass` - buy from a wandering trader

<details><summary>Also mineable from structures here, but craftable (1, 1 reachable) - decoration, not a resource</summary>

`sandstone_slab`

</details>


## Badlands  <sub>4/4</sub>

### Mine / break a block

- [x] `red_sandstone` `(c)` - crafted from red sand
- [x] `terracotta` `(c)` - smelted from clay

### Trading

- [x] `red_sand` - break a mod block
- [x] `white_terracotta` `(c)` - crafted from terracotta + white dye


## Taiga  <sub>2/2</sub>

### Harvest & interact

- [x] `sweet_berries` - Hydroponics Bay seedling

### Trading

- [x] `podzol` - buy from a wandering trader


## Swamp  <sub>9/9</sub>

### Mine / break a block

- [x] `mangrove_roots` - grow mangrove propagule
- [x] `mud` - mine mud (sewers)
- [x] `muddy_mangrove_roots` `(c)` - crafted from mud + mangrove roots

### Structure chests

- [x] `flower_pot` `(c)` - crafted from brick

### Trading

- [x] `blue_orchid` - buy from a wandering trader
- [x] `lily_pad` - fishing (water from a Rain Collector or the sewers)

### Other

- [x] `tadpole_bucket` - bucket a tadpole

<details><summary>Also mineable from structures here, but craftable (2, 2 reachable) - decoration, not a resource</summary>

`cauldron`, `crafting_table`

</details>


## Snowy  <sub>5/6</sub>

### Mine / break a block

- [x] `ice` - torn down at the Recompile Workbench from fridge
- [x] `snow` `(c)` - crafted from snow block

### Structure chests

- [x] `blue_ice` `(c)` - crafted from packed ice
- [x] `packed_ice` `(c)` - crafted from ice
- [x] `snow_block` `(c)` - crafted from snowball

### Other

- [ ] `powder_snow_bucket` - no powder snow in this world


## Mountain  <sub>1/1</sub>

### Mine / break a block

- [x] `pink_petals` - chests/aquarium_curator


## Mushroom Fields  <sub>5/5</sub>

### Mine / break a block

- [x] `brown_mushroom_block` - bone meal a brown mushroom into a huge one
- [x] `mushroom_stem` - bone meal a mushroom into a huge one
- [x] `mycelium` - mine mycelium (mycelium patches)
- [x] `red_mushroom_block` - bone meal a red mushroom into a huge one

### Other

- [x] `mushroom_stew` `(c)` - crafted from brown mushroom + red mushroom


## River  <sub>2/2</sub>

### Mine / break a block

- [x] `bush` - chests/aquarium_curator

### Other

- [x] `salmon_bucket` - bucket a salmon


## Ocean  <sub>67/95</sub>

*Oceans and their structures: monuments, shipwrecks, ocean ruins, buried treasure.*

### Mine / break a block

- [ ] `brain_coral` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `brain_coral_fan` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `bubble_coral` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `bubble_coral_fan` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `dead_brain_coral` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `dead_brain_coral_block` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `dead_brain_coral_fan` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `dead_bubble_coral` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `dead_bubble_coral_block` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `dead_bubble_coral_fan` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `dead_fire_coral` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `dead_fire_coral_block` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `dead_fire_coral_fan` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `dead_horn_coral` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `dead_horn_coral_block` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `dead_horn_coral_fan` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `dead_tube_coral` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `dead_tube_coral_block` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `dead_tube_coral_fan` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `fire_coral` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `fire_coral_fan` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `horn_coral` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `horn_coral_fan` - no ocean, monument, shipwreck or ocean ruin generates
- [x] `magma_block` `(c)` - mine magma_block (bastion remnant)
- [ ] `tube_coral` - no ocean, monument, shipwreck or ocean ruin generates
- [ ] `tube_coral_fan` - no ocean, monument, shipwreck or ocean ruin generates

### Mob & entity drops

- [x] `cod` - kill a cod (spawn egg (amber -> Sequencer -> Blueprint))
- [x] `ink_sac` - kill a squid (spawn egg (amber -> Sequencer -> Blueprint))
- [x] `nautilus_shell` - brush sewer silt
- [ ] `prismarine_crystals` - no ocean, monument, shipwreck or ocean ruin generates
- [x] `prismarine_shard` - separated in the Separator from prismarine grit
- [x] `pufferfish` - fishing (water from a Rain Collector or the sewers)
- [x] `salmon` - kill a polar bear (spawn egg (amber -> Sequencer -> Blueprint))
- [x] `seagrass` - kill a turtle (sewer resident)
- [x] `tide_armor_trim_smithing_template` `(c)` - chests/aquarium_curator
- [x] `wet_sponge` `(finite)` - chests/aquarium_curator

### Piglin bartering

- [x] `potion` - piglin bartering

### Structure chests

- [x] `clock` `(c)` - crafted from gold ingot + redstone
- [x] `coast_armor_trim_smithing_template` `(c)` - chests/aquarium_curator
- [x] `compass` `(c)` - crafted from iron ingot + redstone
- [x] `cooked_cod` `(c)` - smelted from cod
- [x] `cooked_salmon` `(c)` - smelted from salmon
- [x] `copper_nautilus_armor` `(finite)` - chests/aquarium_curator
- [x] `diamond_nautilus_armor` `(finite)` - chests/aquarium_curator
- [x] `experience_bottle` - buy from a cleric
- [x] `fishing_rod` `(c)` - crafted from stick + string
- [x] `gold_block` `(c)` - mine gold_block (bastion remnant)
- [x] `golden_helmet` `(c)` - bastion remnant chest
- [x] `golden_nautilus_armor` `(finite)` - chests/aquarium_curator
- [ ] `heart_of_the_sea` `(finite)` - no ocean, monument, shipwreck or ocean ruin generates
- [x] `iron_nautilus_armor` `(finite)` - chests/aquarium_curator
- [x] `iron_spear` `(c)` - crafted from stick + iron ingot
- [x] `leather_boots` `(c)` - trash bag pull stream
- [x] `leather_chestplate` `(c)` - trash bag pull stream
- [x] `leather_helmet` `(c)` - sun cap
- [x] `leather_leggings` `(c)` - trash bag pull stream
- [x] `map` `(c)` - crafted from paper + compass
- [x] `paper` `(c)` - trash bag pull stream
- [x] `poisonous_potato` - grow potato
- [x] `stone_axe` `(c)` - crafted from stick + blackstone
- [x] `stone_spear` `(c)` - crafted from stick + blackstone
- [x] `suspicious_stew` `(c)` - crafted from bowl + brown mushroom
- [x] `wheat` `(c)` - grow wheat seeds

### Trading

- [x] `brain_coral_block` - buy from a wandering trader
- [x] `bubble_coral_block` - buy from a wandering trader
- [x] `fire_coral_block` - buy from a wandering trader
- [x] `horn_coral_block` - buy from a wandering trader
- [x] `kelp` - Hydroponics Bay seedling
- [x] `light_blue_terracotta` `(c)` - crafted from terracotta + light blue dye
- [x] `polished_diorite` `(c)` - crafted from diorite
- [x] `polished_granite` `(c)` - crafted from granite
- [x] `purple_glazed_terracotta` `(c)` - smelted from purple terracotta
- [x] `sea_pickle` - Hydroponics Bay seedling
- [x] `tube_coral_block` - buy from a wandering trader

<details><summary>Also mineable from structures here, but craftable (21, 20 reachable) - decoration, not a resource</summary>

`birch_fence`, `birch_slab`, `birch_stairs`, `bricks`, `dark_oak_door`, `dark_oak_stairs`, `dark_oak_trapdoor`, `dark_prismarine`, `jungle_door`, `jungle_fence`, `jungle_slab`, `jungle_stairs`, `jungle_trapdoor`, `oak_stairs`, `oak_trapdoor`, `prismarine`, `prismarine_bricks`, `sea_lantern` (no), `spruce_door`, `spruce_slab`, `spruce_trapdoor`

</details>


## Cave & Underground  <sub>107/128</sub>

*Ores and everything below the surface.*

### Mine / break a block

- [x] `amethyst_block` `(c)` - crafted from amethyst shard
- [x] `andesite` `(c)` - crafted from andesite shard
- [ ] `azalea` - no lush caves generate
- [x] `big_dripleaf` - chests/aquarium_curator
- [x] `calcite` `(finite)` - crafted from calcite shard
- [ ] `coal_ore` `(finite)` - the garbage world generates no ore; metal comes from scrap instead
- [x] `cobbled_deepslate` `(c)` - mine deepslate (overworld terrain)
- [x] `cobblestone` `(c)` - cut on a stonecutter from stone
- [x] `cobweb` - mine cobweb (sewers)
- [ ] `copper_ore` `(finite)` - the garbage world generates no ore; metal comes from scrap instead
- [x] `deepslate` `(c)` `(finite)` - mine deepslate (overworld terrain)
- [ ] `deepslate_coal_ore` `(finite)` - the garbage world generates no ore; metal comes from scrap instead
- [ ] `deepslate_copper_ore` `(finite)` - the garbage world generates no ore; metal comes from scrap instead
- [ ] `deepslate_diamond_ore` `(finite)` - the garbage world generates no ore; metal comes from scrap instead
- [ ] `deepslate_emerald_ore` `(finite)` - the garbage world generates no ore; metal comes from scrap instead
- [ ] `deepslate_gold_ore` `(finite)` - the garbage world generates no ore; metal comes from scrap instead
- [ ] `deepslate_iron_ore` `(finite)` - the garbage world generates no ore; metal comes from scrap instead
- [ ] `deepslate_lapis_ore` `(finite)` - the garbage world generates no ore; metal comes from scrap instead
- [ ] `deepslate_redstone_ore` `(finite)` - the garbage world generates no ore; metal comes from scrap instead
- [ ] `diamond_ore` `(finite)` - the garbage world generates no ore; metal comes from scrap instead
- [x] `diorite` `(c)` - crafted from diorite shard
- [ ] `emerald_ore` `(finite)` - the garbage world generates no ore; metal comes from scrap instead
- [ ] `flowering_azalea` - no lush caves generate
- [x] `glow_lichen` - chests/aquarium_curator
- [ ] `gold_ore` `(finite)` - the garbage world generates no ore; metal comes from scrap instead
- [x] `granite` `(c)` - crafted from granite shard
- [x] `hanging_roots` - chests/aquarium_curator
- [x] `iron_bars` `(c)` - mine iron_bars (sewers)
- [ ] `iron_ore` `(finite)` - the garbage world generates no ore; metal comes from scrap instead
- [ ] `lapis_ore` `(finite)` - the garbage world generates no ore; metal comes from scrap instead
- [x] `moss_carpet` `(c)` - crafted from moss block
- [ ] `raw_copper` `(c)` - the garbage world generates no ore; metal comes from scrap instead
- [ ] `raw_gold` `(c)` - the garbage world generates no ore; metal comes from scrap instead
- [ ] `raw_iron` `(c)` - the garbage world generates no ore; metal comes from scrap instead
- [ ] `redstone_ore` `(finite)` - the garbage world generates no ore; metal comes from scrap instead
- [x] `sculk_shrieker` - crafted from sculk powder + soul sand
- [x] `sculk_vein` - crafted from sculk powder
- [x] `smooth_basalt` `(c)` - smelted from basalt
- [x] `spore_blossom` `(finite)` - chests/aquarium_curator

### Mob & entity drops

- [x] `bone_meal` `(c)` - kill a cod (spawn egg (amber -> Sequencer -> Blueprint))
- [x] `coal` `(c)` - kill a wither skeleton (nether fortress (generates in the compacted depths))
- [x] `copper_ingot` `(c)` - kill a drowned (sewer spawner)
- [x] `emerald` `(c)` - buy from a armorer
- [x] `redstone` `(c)` - kill a witch (spawns in the demolition yard / radioactive dump)
- [x] `sculk_catalyst` - crafted from sculk powder + echo shard
- [x] `tropical_fish` - fishing (water from a Rain Collector or the sewers)

### Harvest & interact

- [x] `diamond` `(c)` - bastion remnant chest
- [x] `glow_berries` - Hydroponics Bay seedling
- [x] `skeleton_skull` - a charged creeper kills a skeleton

### Piglin bartering

- [x] `gravel` - mine gravel (sewers)
- [x] `obsidian` - bastion remnant chest
- [x] `soul_sand` - mine soul_sand (bastion remnant)
- [x] `string` - mine cobweb (sewers)

### Structure chests

- [x] `amethyst_shard` - separated in the Separator from quartz grit
- [x] `bone_block` `(c)` - bastion remnant chest
- [x] `candle` `(c)` - crafted from honeycomb + string
- [x] `clay_ball` - hydrate a Dry Clay Body on a filled cauldron
- [x] `detector_rail` `(c)` - crafted from stone pressure plate + redstone
- [x] `echo_shard` `(finite)` - sewer sump crate
- [x] `flint` - mine gravel (sewers)
- [x] `furnace` `(c)` - crafted from blackstone
- [x] `lapis_lazuli` `(c)` - torn down at the Recompile Workbench from printer
- [x] `moss_block` - buy from a wandering trader
- [x] `powered_rail` `(c)` - crafted from stick + redstone
- [x] `rail` `(c)` - crafted from stick + iron ingot
- [x] `sculk` - crafted from sculk powder
- [x] `sculk_sensor` - crafted from sculk powder + redstone
- [x] `stone` `(c)` - crafted from stone shard
- [x] `torch` `(c)` - crafted from stick + charcoal
- [x] `tuff` `(finite)` - crafted from tuff shard

### Trading

- [x] `azure_bluet` - buy from a wandering trader
- [x] `blue_carpet` `(c)` - crafted from blue wool
- [x] `bookshelf` `(c)` - break a mod block
- [x] `campfire` `(c)` - mine campfire (sewers)
- [x] `cornflower` - buy from a wandering trader
- [x] `cyan_carpet` `(c)` - crafted from cyan wool
- [x] `dripstone_block` `(c)` - buy from a mason
- [x] `glass` `(c)` - smelted from red sand
- [x] `gray_carpet` `(c)` - crafted from gray wool
- [x] `light_blue_carpet` `(c)` - crafted from light blue wool
- [x] `orange_tulip` - buy from a wandering trader
- [x] `oxeye_daisy` - buy from a wandering trader
- [x] `pink_tulip` - buy from a wandering trader
- [x] `pointed_dripstone` - buy from a wandering trader
- [x] `red_tulip` - buy from a wandering trader
- [x] `rooted_dirt` - buy from a wandering trader
- [x] `small_dripleaf` - buy from a wandering trader
- [x] `vine` - buy from a wandering trader
- [x] `white_tulip` - buy from a wandering trader

### Other

- [x] `axolotl_bucket` - bucket an axolotl
- [x] `lava_bucket` - fill a bucket from lava in the compacted depths

<details><summary>Also mineable from structures here, but craftable (37, 37 reachable) - decoration, not a resource</summary>

`charcoal`, `chiseled_deepslate`, `cobbled_deepslate_slab`, `cobbled_deepslate_stairs`, `cobbled_deepslate_wall`, `comparator`, `cracked_deepslate_bricks`, `cracked_deepslate_tiles`, `deepslate_brick_slab`, `deepslate_brick_stairs`, `deepslate_brick_wall`, `deepslate_bricks`, `deepslate_tile_slab`, `deepslate_tile_stairs`, `deepslate_tile_wall`, `deepslate_tiles`, `glass_pane`, `iron_door`, `iron_trapdoor`, `lectern`, `note_block`, `polished_basalt`, `polished_deepslate`, `polished_deepslate_slab`, `polished_deepslate_stairs`, `polished_deepslate_wall`, `redstone_block`, `redstone_lamp`, `redstone_torch`, `repeater`, `soul_lantern`, `sticky_piston`, `stone_brick_slab`, `stone_button`, `stone_pressure_plate`, `target`, `white_candle`

</details>


## Nether  <sub>73/82</sub>

*The Nether dimension, its biomes, fortresses, bastions, and piglin bartering.*

### Mine / break a block

- [x] `basalt` - mine basalt (bastion remnant)
- [ ] `nether_gold_ore` `(finite)` - the garbage world generates no ore; metal comes from scrap instead
- [ ] `nether_quartz_ore` `(finite)` - the garbage world generates no ore; metal comes from scrap instead
- [ ] `nether_sprouts` - the compacted depths has no nylium - only slag rubble and lava break the fill
- [x] `nether_wart_block` `(c)` - crafted from nether wart
- [x] `netherrack` - mine netherrack (bastion remnant)
- [ ] `shroomlight` - the compacted depths has no nylium - only slag rubble and lava break the fill
- [x] `soul_soil` - crafted from soul soil shard
- [ ] `twisting_vines` - the compacted depths has no nylium - only slag rubble and lava break the fill
- [ ] `warped_fungus` - the compacted depths has no nylium - only slag rubble and lava break the fill
- [x] `warped_nylium` - crafted from warped nylium shard
- [ ] `warped_roots` - the compacted depths has no nylium - only slag rubble and lava break the fill
- [ ] `warped_wart_block` - the compacted depths has no nylium - only slag rubble and lava break the fill
- [ ] `weeping_vines` - the compacted depths has no nylium - only slag rubble and lava break the fill

### Mob & entity drops

- [x] `blaze_rod` - kill a blaze (nether fortress (generates in the compacted depths))
- [x] `ghast_tear` - kill a ghast (spawns in the compacted depths)
- [x] `glowstone_dust` - mine glowstone (bastion remnant)
- [x] `gold_ingot` `(c)` - bastion remnant chest
- [x] `gold_nugget` `(c)` - mine gilded_blackstone (bastion remnant)
- [x] `magma_cream` `(c)` - bastion remnant chest
- [x] `music_disc_tears` - kill a ghast (spawns in the compacted depths)
- [x] `nether_star` - kill the wither
- [x] `ochre_froglight` - kill a magma cube (spawns in the compacted depths)
- [x] `pearlescent_froglight` - kill a magma cube (spawns in the compacted depths)
- [x] `verdant_froglight` - kill a magma cube (spawns in the compacted depths)
- [x] `wither_skeleton_skull` - a charged creeper kills a wither skeleton

### Piglin bartering

- [x] `blackstone` - mine blackstone (bastion remnant)
- [x] `fire_charge` `(c)` - piglin bartering
- [x] `quartz` `(c)` - bastion remnant chest
- [x] `spectral_arrow` `(c)` - bastion remnant chest

### Structure chests

- [x] `ancient_debris` `(finite)` - bastion remnant chest
- [x] `bell` - break a mod block
- [x] `cooked_porkchop` `(c)` - bastion remnant chest
- [x] `crimson_fungus` - bastion remnant chest
- [x] `crimson_nylium` - bastion remnant chest
- [x] `crimson_roots` - bastion remnant chest
- [x] `crossbow` `(c)` - bastion remnant chest
- [x] `diamond_boots` `(c)` - bastion remnant chest
- [x] `diamond_shovel` `(c)` - bastion remnant chest
- [x] `diamond_spear` `(c)` - bastion remnant chest
- [x] `enchanted_golden_apple` - bastion remnant chest
- [x] `flint_and_steel` `(c)` - nether fortress chest
- [x] `gilded_blackstone` `(finite)` - mine gilded_blackstone (bastion remnant)
- [x] `glistering_melon_slice` `(c)` - crafted from gold nugget + melon slice
- [x] `glowstone` `(c)` - mine glowstone (bastion remnant)
- [x] `golden_axe` `(c)` - bastion remnant chest
- [x] `golden_boots` `(c)` - bastion remnant chest
- [x] `golden_carrot` `(c)` - bastion remnant chest
- [x] `golden_chestplate` `(c)` - bastion remnant chest
- [x] `golden_hoe` `(c)` - crafted from stick + gold ingot
- [x] `golden_leggings` `(c)` - bastion remnant chest
- [x] `golden_pickaxe` `(c)` - crafted from stick + gold ingot
- [x] `golden_shovel` `(c)` - crafted from stick + gold ingot
- [x] `golden_sword` `(c)` - bastion remnant chest
- [x] `iron_block` `(c)` - bastion remnant chest
- [x] `iron_chain` `(c)` - mine iron_chain (bastion remnant)
- [x] `light_weighted_pressure_plate` `(c)` - crafted from gold ingot
- [x] `lodestone` `(c)` - bastion remnant chest
- [x] `music_disc_pigstep` `(finite)` - bastion remnant chest
- [x] `nether_wart` - mine nether_wart (bastion remnant)
- [x] `netherite_ingot` `(c)` `(finite)` - bastion remnant chest
- [x] `netherite_scrap` `(c)` `(finite)` - bastion remnant chest
- [x] `netherite_upgrade_smithing_template` `(c)` - bastion remnant chest
- [x] `piglin_banner_pattern` - bastion remnant chest
- [x] `rib_armor_trim_smithing_template` `(c)` - nether fortress chest
- [x] `snout_armor_trim_smithing_template` `(c)` - bastion remnant chest

### Trading

- [x] `lantern` `(c)` - mine lantern (sewers)
- [x] `quartz_block` `(c)` - mine quartz_block (bastion remnant)

<details><summary>Also mineable from structures here, but craftable (14, 14 reachable) - decoration, not a resource</summary>

`blackstone_slab`, `blackstone_stairs`, `blackstone_wall`, `chiseled_polished_blackstone`, `cracked_polished_blackstone_bricks`, `nether_brick_fence`, `nether_brick_stairs`, `nether_bricks`, `polished_blackstone_brick_stairs`, `polished_blackstone_bricks`, `smooth_quartz`, `smooth_quartz_slab`, `stone_brick_wall`, `stone_slab`

</details>


## End  <sub>11/25</sub>

*The End dimension, end cities, and the dragon.*

### Mine / break a block

- [ ] `chorus_flower` - the End is locked - RCDimensionLockout blocks travel and portal formation
- [ ] `chorus_fruit` - the End is locked - RCDimensionLockout blocks travel and portal formation
- [ ] `dragon_head` `(finite)` - the End is locked - RCDimensionLockout blocks travel and portal formation
- [ ] `end_stone` - the End is locked - RCDimensionLockout blocks travel and portal formation

### Mob & entity drops

- [ ] `shulker_shell` - the End is locked - RCDimensionLockout blocks travel and portal formation

### Harvest & interact

- [ ] `dragon_egg` `(finite)` - the End is locked - RCDimensionLockout blocks travel and portal formation

### Piglin bartering

- [x] `iron_boots` `(c)` - piglin bartering

### Structure chests

- [x] `beetroot_seeds` - Hydroponics Bay seedling
- [x] `iron_chestplate` `(c)` - crafted from iron ingot
- [x] `iron_helmet` `(c)` - crafted from iron ingot
- [x] `iron_leggings` `(c)` - crafted from iron ingot
- [x] `iron_pickaxe` `(c)` - crafted from stick + iron ingot
- [x] `iron_shovel` `(c)` - crafted from stick + iron ingot
- [x] `spire_armor_trim_smithing_template` `(c)` - chests/aquarium_curator

### Other

- [ ] `dragon_breath` - the End is locked - RCDimensionLockout blocks travel and portal formation
- [ ] `elytra` `(finite)` - the End is locked - RCDimensionLockout blocks travel and portal formation

<details><summary>Also mineable from structures here, but craftable (9, 3 reachable) - decoration, not a resource</summary>

`brewing_stand`, `end_rod` (no), `end_stone_bricks` (no), `ender_chest`, `magenta_stained_glass`, `purpur_block` (no), `purpur_pillar` (no), `purpur_slab` (no), `purpur_stairs` (no)

</details>


## Structures & Chest Loot  <sub>224/238</sub>

*Only reachable from a generated structure's chest or block palette.*

### Mine / break a block

- [x] `damaged_anvil` - an anvil degrading with use
- [x] `oxidized_copper_trapdoor` - leave copper out to oxidize
- [x] `red_concrete` - drop concrete powder into water
- [x] `white_concrete` - drop concrete powder into water

### Mob & entity drops

- [x] `breeze_rod` - fired in the Sintering Kiln from propellant briquette
- [ ] `ominous_banner` `(finite)` - no raids: evokers and pillagers never spawn
- [ ] `ominous_bottle` - no trial chambers generate
- [x] `saddle` `(c)` - bastion remnant chest
- [ ] `totem_of_undying` - no raids: evokers and pillagers never spawn
- [x] `trident` - a naturally-spawned drowned drops its trident

### Harvest & interact

- [x] `carved_pumpkin` - shear a snow golem
- [x] `diamond_block` `(c)` - crafted from diamond
- [x] `egg` - a chicken lays an egg
- [x] `emerald_block` `(c)` - crafted from emerald
- [x] `honeycomb` - shear a bee nest grown on a birch
- [ ] `music_disc_creator_music_box` `(finite)` - no trial chambers generate
- [x] `pumpkin_seeds` `(c)` - Hydroponics Bay seedling
- [ ] `trial_key` - no trial chambers generate

### Piglin bartering

- [x] `book` `(c)` - bastion remnant chest
- [x] `iron_nugget` `(c)` - bastion remnant chest
- [x] `splash_potion` - piglin bartering

### Structure chests

- [x] `activator_rail` `(c)` - crafted from redstone torch + stick
- [x] `baked_potato` `(c)` - smelted from potato
- [x] `bamboo_hanging_sign` `(c)` - crafted from stripped bamboo block + iron chain
- [x] `barrel` `(c)` - mine barrel (sewers)
- [x] `beetroot_soup` `(c)` - crafted from bowl + beetroot
- [x] `bolt_armor_trim_smithing_template` `(c)` - chests/aquarium_curator
- [x] `bow` `(c)` - crafted from stick + string
- [x] `bread` `(c)` - crafted from wheat
- [x] `bucket` `(c)` - household pull stream (sort garbage)
- [x] `bundle` `(c)` - trash bag pull stream
- [x] `cake` `(c)` - crafted from milk bucket + sugar
- [x] `chainmail_chestplate` - Hero of the Village gift
- [x] `copper_horse_armor` `(finite)` - nether fortress chest
- [x] `copper_spear` `(c)` - crafted from stick + copper ingot
- [x] `diamond_axe` `(c)` - crafted from stick + diamond
- [x] `diamond_chestplate` `(c)` - bastion remnant chest
- [x] `diamond_helmet` `(c)` - bastion remnant chest
- [x] `diamond_hoe` `(c)` - crafted from stick + diamond
- [x] `diamond_horse_armor` `(finite)` - nether fortress chest
- [x] `diamond_leggings` `(c)` - bastion remnant chest
- [x] `diamond_pickaxe` `(c)` - bastion remnant chest
- [x] `diamond_sword` `(c)` - bastion remnant chest
- [ ] `disc_fragment_5` `(finite)` - the structure that carries it (ancient city / stronghold / trail ruins) is absent
- [x] `dune_armor_trim_smithing_template` `(c)` - chests/aquarium_curator
- [x] `eye_armor_trim_smithing_template` `(c)` - chests/aquarium_curator
- [x] `flow_armor_trim_smithing_template` `(c)` - chests/aquarium_curator
- [ ] `flow_banner_pattern` - no trial chambers generate
- [x] `goat_horn` - a goat rams a hard block
- [x] `golden_apple` `(c)` - bastion remnant chest
- [x] `golden_horse_armor` `(finite)` - nether fortress chest
- [x] `green_dye` `(c)` - smelted from cactus
- [ ] `guster_banner_pattern` - no trial chambers generate
- [ ] `heavy_core` - no trial chambers generate
- [x] `honey_bottle` `(c)` - bottle a full bee nest
- [x] `iron_axe` `(c)` - crafted from stick + iron ingot
- [x] `iron_horse_armor` `(finite)` - nether fortress chest
- [x] `iron_sword` `(c)` - bastion remnant chest
- [x] `large_fern` - gameplay/dried_bouquet
- [x] `lead` `(c)` - household pull stream (sort garbage)
- [x] `melon_seeds` `(c)` - Hydroponics Bay seedling
- [x] `milk_bucket` - milk a cow into a bucket
- [ ] `music_disc_creator` - no trial chambers generate
- [ ] `music_disc_otherside` `(finite)` - the structure that carries it (ancient city / stronghold / trail ruins) is absent
- [ ] `music_disc_precipice` - no trial chambers generate
- [x] `name_tag` `(c)` - sewer chest
- [x] `pumpkin_pie` `(c)` - crafted from pumpkin + sugar
- [x] `scaffolding` `(c)` - crafted from bamboo + string
- [x] `sentry_armor_trim_smithing_template` `(c)` - chests/aquarium_curator
- [x] `shears` `(c)` - household pull stream (sort garbage)
- [x] `shield` `(c)` - crafted from bamboo planks + iron ingot
- [x] `silence_armor_trim_smithing_template` `(c)` - chests/aquarium_curator
- [x] `smooth_stone` `(c)` - smelted from stone
- [x] `soul_torch` `(c)` - crafted from stick + soul sand
- [x] `spruce_sign` `(c)` - crafted from spruce planks + stick
- [x] `stone_bricks` `(c)` - crafted from stone
- [x] `stone_pickaxe` `(c)` - crafted from stick + blackstone
- [x] `tall_grass` - bone meal on grass
- [x] `tnt` `(c)` - crafted from red sand + gunpowder
- [x] `vex_armor_trim_smithing_template` `(c)` - chests/aquarium_curator
- [x] `ward_armor_trim_smithing_template` `(c)` - chests/aquarium_curator
- [x] `water_bucket` - fill a bucket from sewer water
- [x] `wild_armor_trim_smithing_template` `(c)` - chests/aquarium_curator
- [x] `wind_charge` `(c)` - crafted from breeze rod
- [x] `wooden_axe` `(c)` - crafted from stick + bamboo planks
- [x] `wooden_pickaxe` `(c)` - crafted from stick + bamboo planks
- [x] `yellow_dye` `(c)` - crafted from sunflower

### Trading

- [x] `black_bed` `(c)` - crafted from black dye + white bed
- [x] `black_carpet` `(c)` - crafted from black wool
- [x] `black_glazed_terracotta` `(c)` - smelted from black terracotta
- [x] `blue_bed` `(c)` - crafted from blue dye + black bed
- [x] `brown_bed` `(c)` - crafted from brown dye + black bed
- [x] `brown_carpet` `(c)` - crafted from brown wool
- [x] `brown_terracotta` `(c)` - crafted from terracotta + brown dye
- [x] `chainmail_helmet` - Hero of the Village gift
- [x] `chiseled_stone_bricks` `(c)` - cut on a stonecutter from stone
- [x] `cooked_beef` `(c)` - smelted from beef
- [x] `cooked_chicken` `(c)` - smelted from chicken
- [x] `cyan_bed` `(c)` - crafted from cyan dye + black bed
- [x] `cyan_glazed_terracotta` `(c)` - smelted from cyan terracotta
- [x] `cyan_terracotta` `(c)` - crafted from terracotta + cyan dye
- [x] `gray_bed` `(c)` - crafted from gray dye + black bed
- [x] `gray_terracotta` `(c)` - crafted from terracotta + gray dye
- [x] `green_bed` `(c)` - crafted from green dye + black bed
- [x] `green_carpet` `(c)` - crafted from green wool
- [x] `light_blue_bed` `(c)` - crafted from light blue dye + black bed
- [x] `light_blue_glazed_terracotta` `(c)` - smelted from light blue terracotta
- [x] `light_gray_bed` `(c)` - crafted from light gray dye + black bed
- [x] `light_gray_carpet` `(c)` - crafted from light gray wool
- [x] `light_gray_glazed_terracotta` `(c)` - smelted from light gray terracotta
- [x] `light_gray_terracotta` `(c)` - crafted from terracotta + light gray dye
- [x] `lime_bed` `(c)` - crafted from lime dye + black bed
- [x] `lime_carpet` `(c)` - crafted from lime wool
- [x] `lime_glazed_terracotta` `(c)` - smelted from lime terracotta
- [x] `lime_terracotta` `(c)` - crafted from terracotta + lime dye
- [x] `magenta_bed` `(c)` - crafted from magenta dye + black bed
- [x] `magenta_carpet` `(c)` - crafted from magenta wool
- [x] `orange_bed` `(c)` - crafted from orange dye + black bed
- [x] `orange_carpet` `(c)` - crafted from orange wool
- [x] `orange_glazed_terracotta` `(c)` - smelted from orange terracotta
- [x] `pink_bed` `(c)` - crafted from pink dye + black bed
- [x] `pink_carpet` `(c)` - crafted from pink wool
- [x] `polished_andesite` `(c)` - crafted from andesite
- [x] `purple_bed` `(c)` - crafted from purple dye + black bed
- [x] `purple_carpet` `(c)` - crafted from purple wool
- [x] `red_bed` `(c)` - crafted from red dye + black bed
- [x] `red_candle` `(c)` - crafted from candle + red dye
- [x] `red_carpet` `(c)` - crafted from red wool
- [x] `red_glazed_terracotta` `(c)` - smelted from red terracotta
- [x] `red_terracotta` `(c)` - crafted from terracotta + red dye
- [x] `white_bed` `(c)` - crafted from white clean mattress + acacia planks
- [x] `white_carpet` `(c)` - crafted from white wool
- [x] `white_glazed_terracotta` `(c)` - smelted from white terracotta
- [x] `yellow_bed` `(c)` - crafted from yellow dye + black bed
- [x] `yellow_carpet` `(c)` - crafted from yellow wool
- [x] `yellow_glazed_terracotta` `(c)` - smelted from yellow terracotta
- [x] `yellow_terracotta` `(c)` - crafted from terracotta + yellow dye

### Archaeology

- [x] `yellow_stained_glass_pane` `(c)` - crafted from yellow stained glass

### Other

- [ ] `lingering_potion` - no trial chambers generate
- [ ] `ominous_trial_key` - no trial chambers generate

<details><summary>Also mineable from structures here, but craftable (98, 98 reachable) - decoration, not a resource</summary>

`acacia_door`, `acacia_fence`, `acacia_fence_gate`, `acacia_pressure_plate`, `acacia_slab`, `acacia_stairs`, `black_stained_glass`, `blast_furnace`, `brick_slab`, `brick_stairs`, `brick_wall`, `brown_stained_glass`, `cartography_table`, `chest`, `chiseled_sandstone`, `chiseled_tuff`, `chiseled_tuff_bricks`, `coal_block`, `cobblestone_slab`, `cobblestone_stairs`, `cobblestone_wall`, `composter`, `copper_block`, `cracked_stone_bricks`, `cut_sandstone`, `dark_oak_fence`, `dark_oak_fence_gate`, `dark_oak_slab`, `decorated_pot`, `diorite_slab`, `diorite_stairs`, `diorite_wall`, `fletching_table`, `granite_stairs`, `granite_wall`, `grindstone`, `hay_block`, `hopper`, `jungle_button`, `jungle_fence_gate`, `ladder`, `lapis_block`, `lever`, `light_gray_stained_glass`, `loom`, `mossy_cobblestone_slab`, `mossy_cobblestone_stairs`, `mossy_cobblestone_wall`, `mossy_stone_bricks`, `mud_brick_slab`, `mud_brick_stairs`, `mud_brick_wall`, `mud_bricks`, `oak_button`, `oak_door`, `oak_fence`, `oak_fence_gate`, `oak_pressure_plate`, `oak_slab`, `orange_stained_glass_pane`, `oxidized_cut_copper`, `packed_mud`, `polished_tuff`, `polished_tuff_slab`, `sandstone_stairs`, `sandstone_wall`, `smithing_table`, `smoker`, `smooth_sandstone`, `smooth_sandstone_slab`, `smooth_sandstone_stairs`, `smooth_stone_slab`, `spruce_fence`, `spruce_fence_gate`, `spruce_pressure_plate`, `spruce_stairs`, `stone_brick_stairs`, `stonecutter`, `trapped_chest`, `tuff_bricks`, `waxed_chiseled_copper`, `waxed_copper_block`, `waxed_copper_bulb`, `waxed_copper_door`, `waxed_copper_grate`, `waxed_cut_copper`, `waxed_cut_copper_slab`, `waxed_cut_copper_stairs`, `waxed_oxidized_chiseled_copper`, `waxed_oxidized_copper`, `waxed_oxidized_copper_door`, `waxed_oxidized_copper_grate`, `waxed_oxidized_copper_trapdoor`, `waxed_oxidized_cut_copper`, `waxed_oxidized_cut_copper_slab`, `waxed_oxidized_cut_copper_stairs`, `white_stained_glass`, `white_stained_glass_pane`

</details>


## Trading  <sub>62/62</sub>

*Villager trades, wandering trader, and Hero of the Village gifts.*

### Trading

- [x] `black_banner` `(c)` - crafted from black wool + stick
- [x] `black_dye` `(c)` - crafted from ink sac
- [x] `black_terracotta` `(c)` - crafted from terracotta + black dye
- [x] `blue_banner` `(c)` - crafted from blue wool + stick
- [x] `blue_dye` `(c)` - crafted from lapis lazuli
- [x] `blue_glazed_terracotta` `(c)` - smelted from blue terracotta
- [x] `brick` `(c)` - smelted from clay ball
- [x] `brown_banner` `(c)` - crafted from brown wool + stick
- [x] `brown_dye` `(c)` - crafted from cocoa beans
- [x] `brown_glazed_terracotta` `(c)` - smelted from brown terracotta
- [x] `chainmail_boots` - Hero of the Village gift
- [x] `chainmail_leggings` - Hero of the Village gift
- [x] `cod_bucket` - bucket a cod
- [x] `cooked_mutton` `(c)` - smelted from mutton
- [x] `cooked_rabbit` `(c)` - smelted from rabbit
- [x] `cookie` `(c)` - crafted from wheat + cocoa beans
- [x] `cyan_banner` `(c)` - crafted from cyan wool + stick
- [x] `cyan_dye` `(c)` - crafted from pitcher plant
- [x] `enchanted_book` - buy from a librarian
- [x] `globe_banner_pattern` - buy from a cartographer
- [x] `golden_dandelion` `(c)` - buy from a wandering trader
- [x] `gray_banner` `(c)` - crafted from gray wool + stick
- [x] `gray_dye` `(c)` - crafted from closed eyeblossom
- [x] `gray_glazed_terracotta` `(c)` - smelted from gray terracotta
- [x] `green_banner` `(c)` - crafted from green wool + stick
- [x] `green_glazed_terracotta` `(c)` - smelted from green terracotta
- [x] `green_terracotta` `(c)` - crafted from terracotta + green dye
- [x] `item_frame` `(c)` - crafted from stick + leather
- [x] `leather_horse_armor` `(c)` - crafted from leather
- [x] `light_blue_banner` `(c)` - crafted from light blue wool + stick
- [x] `light_blue_dye` `(c)` - torn down at the Recompile Workbench from printer
- [x] `light_gray_banner` `(c)` - crafted from light gray wool + stick
- [x] `light_gray_dye` `(c)` - torn down at the Recompile Workbench from printer
- [x] `lime_banner` `(c)` - crafted from lime wool + stick
- [x] `lime_dye` `(c)` - smelted from sea pickle
- [x] `magenta_banner` `(c)` - crafted from magenta wool + stick
- [x] `magenta_dye` `(c)` - crafted from lilac
- [x] `magenta_glazed_terracotta` `(c)` - smelted from magenta terracotta
- [x] `magenta_terracotta` `(c)` - crafted from terracotta + magenta dye
- [x] `open_eyeblossom` - buy from a wandering trader
- [x] `orange_banner` `(c)` - crafted from orange wool + stick
- [x] `orange_dye` `(c)` - crafted from torchflower
- [x] `painting` `(c)` - break a mod block
- [x] `pink_banner` `(c)` - crafted from pink wool + stick
- [x] `pink_dye` `(c)` - crafted from cactus flower
- [x] `pink_glazed_terracotta` `(c)` - smelted from pink terracotta
- [x] `pink_terracotta` `(c)` - crafted from terracotta + pink dye
- [x] `pufferfish_bucket` - buy from a wandering trader
- [x] `purple_banner` `(c)` - crafted from purple wool + stick
- [x] `purple_dye` `(c)` - torn down at the Recompile Workbench from printer
- [x] `purple_terracotta` `(c)` - crafted from terracotta + purple dye
- [x] `quartz_pillar` `(c)` - crafted from quartz block
- [x] `rabbit_stew` `(c)` - crafted from baked potato + cooked rabbit
- [x] `red_banner` `(c)` - crafted from red wool + stick
- [x] `red_dye` `(c)` - crafted from beetroot
- [x] `stone_hoe` `(c)` - crafted from stick + blackstone
- [x] `stone_shovel` `(c)` - crafted from stick + blackstone
- [x] `tropical_fish_bucket` - buy from a wandering trader
- [x] `white_banner` `(c)` - crafted from white wool + stick
- [x] `white_dye` `(c)` - crafted from bone meal
- [x] `yellow_banner` `(c)` - crafted from yellow wool + stick
- [x] `yellow_candle` `(c)` - crafted from candle + yellow dye


## Fishing  <sub>1/1</sub>

*The fishing loot tables.*

### Mob & entity drops

- [x] `bowl` `(c)` - kill a turtle (sewer resident)


## Archaeology  <sub>36/38</sub>

*Brushing suspicious sand and gravel.*

### Archaeology

- [x] `angler_pottery_sherd` `(finite)` - archaeology/aquarium_silt
- [x] `archer_pottery_sherd` `(finite)` - archaeology/aquarium_silt
- [x] `arms_up_pottery_sherd` `(finite)` - archaeology/aquarium_silt
- [x] `blade_pottery_sherd` `(finite)` - archaeology/aquarium_silt
- [x] `blue_stained_glass_pane` `(c)` - crafted from blue stained glass
- [x] `brewer_pottery_sherd` `(finite)` - archaeology/aquarium_silt
- [x] `brown_candle` `(c)` - crafted from candle + brown dye
- [x] `burn_pottery_sherd` `(finite)` - archaeology/aquarium_silt
- [x] `danger_pottery_sherd` `(finite)` - archaeology/aquarium_silt
- [x] `explorer_pottery_sherd` `(finite)` - archaeology/aquarium_silt
- [x] `friend_pottery_sherd` `(finite)` - archaeology/aquarium_silt
- [x] `green_candle` `(c)` - crafted from candle + green dye
- [x] `heart_pottery_sherd` `(finite)` - brush sewer silt
- [x] `heartbreak_pottery_sherd` `(finite)` - archaeology/aquarium_silt
- [x] `host_armor_trim_smithing_template` `(c)` - chests/aquarium_curator
- [x] `howl_pottery_sherd` `(finite)` - archaeology/aquarium_silt
- [x] `light_blue_stained_glass_pane` `(c)` - crafted from light blue stained glass
- [x] `magenta_stained_glass_pane` `(c)` - crafted from magenta stained glass
- [x] `miner_pottery_sherd` `(finite)` - archaeology/aquarium_silt
- [x] `mourner_pottery_sherd` `(finite)` - archaeology/aquarium_silt
- [ ] `music_disc_relic` `(finite)` - the structure that carries it (ancient city / stronghold / trail ruins) is absent
- [x] `oak_hanging_sign` `(c)` - crafted from stripped oak log + iron chain
- [x] `pink_stained_glass_pane` `(c)` - crafted from pink stained glass
- [x] `plenty_pottery_sherd` `(finite)` - archaeology/aquarium_silt
- [x] `prize_pottery_sherd` `(finite)` - archaeology/aquarium_silt
- [x] `purple_candle` `(c)` - crafted from candle + purple dye
- [x] `purple_stained_glass_pane` `(c)` - crafted from purple stained glass
- [x] `raiser_armor_trim_smithing_template` `(c)` - chests/aquarium_curator
- [x] `red_stained_glass_pane` `(c)` - crafted from red stained glass
- [x] `shaper_armor_trim_smithing_template` `(c)` - chests/aquarium_curator
- [x] `sheaf_pottery_sherd` `(finite)` - archaeology/aquarium_silt
- [x] `shelter_pottery_sherd` `(finite)` - archaeology/aquarium_silt
- [x] `skull_pottery_sherd` `(finite)` - archaeology/aquarium_silt
- [ ] `sniffer_egg` - the structure that carries it (ancient city / stronghold / trail ruins) is absent
- [x] `snort_pottery_sherd` `(finite)` - archaeology/aquarium_silt
- [x] `spruce_hanging_sign` `(c)` - crafted from stripped spruce log + iron chain
- [x] `wayfinder_armor_trim_smithing_template` `(c)` - chests/aquarium_curator
- [x] `wooden_hoe` `(c)` - crafted from stick + bamboo planks


---

## What Recompile cannot give you

The 102 unreachable rows, by cause. This is the interesting half: each one is a deliberate closure
of the vanilla economy, not an oversight, unless noted.

- **28** - no ocean, monument, shipwreck or ocean ruin generates
- **21** - the garbage world generates no ore; metal comes from scrap instead
- **14** - the End is locked - RCDimensionLockout blocks travel and portal formation
- **13** - the compacted depths has no nylium - only slag rubble and lava break the fill
- **10** - no trial chambers generate
- **4** - no amethyst geodes generate
- **4** - no lush caves generate
- **4** - the structure that carries it (ancient city / stronghold / trail ruins) is absent
- **2** - no raids: evokers and pillagers never spawn
- **1** - needs a charged creeper to kill another player
- **1** - no powder snow in this world

Two are worth calling out because they are one flower away from being reachable, and both now are:

- **The whole honey chain** hangs on a single vanilla rule: a birch, oak or cherry sapling grown
  within 2 blocks of a flower has a 5% chance of carrying a bee nest. No bee nest generates in this
  world, and a beehive costs honeycomb, so without that rule honeycomb, candles, honey blocks and
  every waxed copper block would be unobtainable.
- **The tree line** runs weedgrass -> Compost Heap -> a volunteer seedling -> Tree Nursery ->
  sapling. Saplings are stripped from every loot roll in the game, so that chain is the only wood
  in Recompile, and every plank, stick, apple and bee nest is downstream of it.

---

## Index: by acquisition method

Items are filed above by *where*; this lists them by *how*. `~` marks one not reachable in Recompile.

**Fishing** <sub>22, 22 reachable</sub>

`bamboo`, `bone`, `book`, `bow`, `bowl`, `cod`, `fishing_rod`, `ink_sac`, `leather`, `leather_boots`, `lily_pad`, `name_tag`, `nautilus_shell`, `potion`, `pufferfish`, `rotten_flesh`, `saddle`, `salmon`, `stick`, `string`, `tripwire_hook`, `tropical_fish`

**Piglin bartering** <sub>18, 18 reachable</sub>

`blackstone`, `book`, `crying_obsidian`, `dried_ghast`, `ender_pearl`, `fire_charge`, `gravel`, `iron_boots`, `iron_nugget`, `leather`, `nether_brick`, `obsidian`, `potion`, `quartz`, `soul_sand`, `spectral_arrow`, `splash_potion`, `string`

**Shearing** <sub>20, 20 reachable</sub>

`black_wool`, `blue_wool`, `brown_mushroom`, `brown_wool`, `carved_pumpkin`, `cyan_wool`, `gray_wool`, `green_wool`, `light_blue_wool`, `light_gray_wool`, `lime_wool`, `magenta_wool`, `orange_wool`, `pink_wool`, `pumpkin_seeds`, `purple_wool`, `red_mushroom`, `red_wool`, `white_wool`, `yellow_wool`

**Charged creeper (mob heads)** <sub>5, 5 reachable</sub>

`creeper_head`, `piglin_head`, `skeleton_skull`, `wither_skeleton_skull`, `zombie_head`

**Sniffer** <sub>4, 4 reachable</sub>

`pitcher_plant`, `pitcher_pod`, `torchflower`, `torchflower_seeds`

**Archaeology (brushing)** <sub>64, 62 reachable</sub>

`angler_pottery_sherd`, `archer_pottery_sherd`, `armadillo_scute`, `arms_up_pottery_sherd`, `beetroot_seeds`, `blade_pottery_sherd`, `blue_dye`, `blue_stained_glass_pane`, `brewer_pottery_sherd`, `brick`, `brown_candle`, `burn_pottery_sherd`, `clay`, `coal`, `danger_pottery_sherd`, `dead_bush`, `diamond`, `emerald`, `explorer_pottery_sherd`, `flower_pot`, `friend_pottery_sherd`, `gold_nugget`, `green_candle`, `gunpowder`, `heart_pottery_sherd`, `heartbreak_pottery_sherd`, `host_armor_trim_smithing_template`, `howl_pottery_sherd`, `iron_axe`, `lead`, `light_blue_dye`, `light_blue_stained_glass_pane`, `magenta_stained_glass_pane`, `miner_pottery_sherd`, `mourner_pottery_sherd`, `music_disc_relic`~, `oak_hanging_sign`, `orange_dye`, `pink_stained_glass_pane`, `plenty_pottery_sherd`, `prize_pottery_sherd`, `purple_candle`, `purple_stained_glass_pane`, `raiser_armor_trim_smithing_template`, `red_candle`, `red_stained_glass_pane`, `shaper_armor_trim_smithing_template`, `sheaf_pottery_sherd`, `shelter_pottery_sherd`, `skull_pottery_sherd`, `sniffer_egg`~, `snort_pottery_sherd`, `spruce_hanging_sign`, `stick`, `string`, `suspicious_stew`, `tnt`, `wayfinder_armor_trim_smithing_template`, `wheat`, `wheat_seeds`, `white_dye`, `wooden_hoe`, `yellow_dye`, `yellow_stained_glass_pane`

**Hero of the Village gifts** <sub>47, 47 reachable</sub>

`arrow`, `black_wool`, `blue_wool`, `book`, `bread`, `brown_wool`, `chainmail_boots`, `chainmail_chestplate`, `chainmail_helmet`, `chainmail_leggings`, `clay`, `cod`, `cooked_beef`, `cooked_chicken`, `cooked_mutton`, `cooked_porkchop`, `cooked_rabbit`, `cookie`, `cyan_wool`, `golden_axe`, `gray_wool`, `green_wool`, `iron_axe`, `lapis_lazuli`, `leather`, `light_blue_wool`, `light_gray_wool`, `lime_wool`, `magenta_wool`, `map`, `orange_wool`, `paper`, `pink_wool`, `poppy`, `pumpkin_pie`, `purple_wool`, `red_wool`, `redstone`, `salmon`, `stone_axe`, `stone_hoe`, `stone_pickaxe`, `stone_shovel`, `tipped_arrow`, `wheat_seeds`, `white_wool`, `yellow_wool`

**Trial chambers** <sub>72, 63 reachable</sub>

`acacia_planks`, `amethyst_shard`, `arrow`, `baked_potato`, `bamboo_hanging_sign`, `bamboo_planks`, `bolt_armor_trim_smithing_template`, `bone_meal`, `book`, `bow`, `bread`, `bucket`, `cake`, `chainmail_chestplate`, `chainmail_helmet`, `compass`, `cooked_beef`, `cooked_chicken`, `crossbow`, `diamond`, `diamond_axe`, `diamond_block`, `diamond_chestplate`, `diamond_helmet`, `diamond_pickaxe`, `diamond_sword`, `egg`, `emerald`, `emerald_block`, `enchanted_golden_apple`, `ender_pearl`, `fire_charge`, `flow_armor_trim_smithing_template`, `flow_banner_pattern`~, `glow_berries`, `golden_apple`, `golden_axe`, `golden_carrot`, `golden_pickaxe`, `guster_banner_pattern`~, `heavy_core`~, `honey_bottle`, `honeycomb`, `iron_axe`, `iron_block`, `iron_chestplate`, `iron_helmet`, `iron_ingot`, `iron_sword`, `lingering_potion`~, `milk_bucket`, `moss_block`, `music_disc_creator`~, `music_disc_precipice`~, `ominous_bottle`~, `ominous_trial_key`~, `potion`, `scaffolding`, `shield`, `snowball`, `splash_potion`, `stick`, `stone_axe`, `stone_pickaxe`, `tipped_arrow`, `torch`, `trial_key`~, `trident`, `tuff`, `water_bucket`, `wind_charge`, `wooden_axe`

**Villager & wandering trader** <sub>265, 265 reachable</sub>

`acacia_log`, `acacia_sapling`, `allium`, `apple`, `arrow`, `azure_bluet`, `beetroot_seeds`, `bell`, `birch_log`, `birch_sapling`, `black_banner`, `black_bed`, `black_carpet`, `black_dye`, `black_glazed_terracotta`, `black_terracotta`, `black_wool`, `blue_banner`, `blue_bed`, `blue_carpet`, `blue_dye`, `blue_glazed_terracotta`, `blue_ice`, `blue_orchid`, `blue_terracotta`, `blue_wool`, `bookshelf`, `bow`, `brain_coral_block`, `bread`, `brick`, `brown_banner`, `brown_bed`, `brown_carpet`, `brown_dye`, `brown_glazed_terracotta`, `brown_mushroom`, `brown_terracotta`, `brown_wool`, `bubble_coral_block`, `cactus`, `cake`, `campfire`, `chainmail_boots`, `chainmail_chestplate`, `chainmail_helmet`, `chainmail_leggings`, `cherry_log`, `cherry_sapling`, `chiseled_stone_bricks`, `clock`, `cod_bucket`, `compass`, `cooked_chicken`, `cooked_cod`, `cooked_porkchop`, `cooked_salmon`, `cookie`, `cornflower`, `crossbow`, `cyan_banner`, `cyan_bed`, `cyan_carpet`, `cyan_dye`, `cyan_glazed_terracotta`, `cyan_terracotta`, `cyan_wool`, `dandelion`, `dark_oak_log`, `dark_oak_sapling`, `diamond_axe`, `diamond_boots`, `diamond_chestplate`, `diamond_helmet`, `diamond_hoe`, `diamond_leggings`, `diamond_pickaxe`, `diamond_shovel`, `diamond_sword`, `dripstone_block`, `emerald`, `enchanted_book`, `ender_pearl`, `experience_bottle`, `fern`, `fire_coral_block`, `firefly_bush`, `fishing_rod`, `flint`, `glass`, `glistering_melon_slice`, `globe_banner_pattern`, `glowstone`, `golden_carrot`, `golden_dandelion`, `gray_banner`, `gray_bed`, `gray_carpet`, `gray_dye`, `gray_glazed_terracotta`, `gray_terracotta`, `gray_wool`, `green_banner`, `green_bed`, `green_carpet`, `green_dye`, `green_glazed_terracotta`, `green_terracotta`, `green_wool`, `gunpowder`, `horn_coral_block`, `iron_axe`, `iron_boots`, `iron_chestplate`, `iron_helmet`, `iron_leggings`, `iron_pickaxe`, `iron_shovel`, `iron_sword`, `item_frame`, `jungle_log`, `jungle_sapling`, `kelp`, `lantern`, `lapis_lazuli`, `leather_boots`, `leather_chestplate`, `leather_helmet`, `leather_horse_armor`, `leather_leggings`, `light_blue_banner`, `light_blue_bed`, `light_blue_carpet`, `light_blue_dye`, `light_blue_glazed_terracotta`, `light_blue_terracotta`, `light_blue_wool`, `light_gray_banner`, `light_gray_bed`, `light_gray_carpet`, `light_gray_dye`, `light_gray_glazed_terracotta`, `light_gray_terracotta`, `light_gray_wool`, `lily_of_the_valley`, `lily_pad`, `lime_banner`, `lime_bed`, `lime_carpet`, `lime_dye`, `lime_glazed_terracotta`, `lime_terracotta`, `lime_wool`, `magenta_banner`, `magenta_bed`, `magenta_carpet`, `magenta_dye`, `magenta_glazed_terracotta`, `magenta_terracotta`, `magenta_wool`, `mangrove_log`, `mangrove_propagule`, `map`, `melon_seeds`, `moss_block`, `name_tag`, `nautilus_shell`, `oak_log`, `oak_sapling`, `open_eyeblossom`, `orange_banner`, `orange_bed`, `orange_carpet`, `orange_dye`, `orange_glazed_terracotta`, `orange_terracotta`, `orange_tulip`, `orange_wool`, `oxeye_daisy`, `packed_ice`, `painting`, `pale_hanging_moss`, `pale_moss_block`, `pale_oak_log`, `pale_oak_sapling`, `pink_banner`, `pink_bed`, `pink_carpet`, `pink_dye`, `pink_glazed_terracotta`, `pink_terracotta`, `pink_tulip`, `pink_wool`, `podzol`, `pointed_dripstone`, `polished_andesite`, `polished_diorite`, `polished_granite`, `poppy`, `potion`, `pufferfish_bucket`, `pumpkin`, `pumpkin_pie`, `pumpkin_seeds`, `purple_banner`, `purple_bed`, `purple_carpet`, `purple_dye`, `purple_glazed_terracotta`, `purple_terracotta`, `purple_wool`, `quartz_block`, `quartz_pillar`, `rabbit_stew`, `red_banner`, `red_bed`, `red_candle`, `red_carpet`, `red_dye`, `red_glazed_terracotta`, `red_mushroom`, `red_sand`, `red_terracotta`, `red_tulip`, `red_wool`, `redstone`, `rooted_dirt`, `saddle`, `sand`, `sea_pickle`, `shears`, `shield`, `slime_ball`, `small_dripleaf`, `spruce_log`, `spruce_sapling`, `stone_axe`, `stone_hoe`, `stone_pickaxe`, `stone_shovel`, `sugar_cane`, `suspicious_stew`, `tall_dry_grass`, `tipped_arrow`, `tropical_fish_bucket`, `tube_coral_block`, `vine`, `wheat_seeds`, `white_banner`, `white_bed`, `white_carpet`, `white_dye`, `white_glazed_terracotta`, `white_terracotta`, `white_tulip`, `white_wool`, `wildflowers`, `yellow_banner`, `yellow_bed`, `yellow_candle`, `yellow_carpet`, `yellow_dye`, `yellow_glazed_terracotta`, `yellow_terracotta`, `yellow_wool`

---

## Appendix: excluded from the catalogue

383 items are excluded because their only loot table is the block dropping itself, and nothing in
worldgen, a structure, a mob, a chest or a trade produces one. Those are crafted goods, not
resources. Also excluded: everything with no survival source in any version (bedrock, barrier,
command blocks, spawn eggs, `budding_amethyst`, `petrified_oak_slab`), and the three pottery
sherds - `flow`, `guster`, `scrape` - that name no loot table, trade or structure anywhere in
26.1.2.

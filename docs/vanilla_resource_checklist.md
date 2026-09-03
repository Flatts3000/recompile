# Vanilla Minecraft Resource Checklist

Every resource vanilla Minecraft gives a player **without a crafting grid** - mined, killed,
harvested, fished, brushed, traded, or looted - grouped by where you go to get it.

**Scope.** A row is here if the game has at least one non-crafting way to obtain it. That includes
found-only manufactured goods (horse armor, music discs, pottery sherds, tridents), because those
are found and not craftable. It excludes items whose only "source" is breaking one you placed
yourself - a crafted block dropping itself is not acquisition.

| | |
|---|---|
| Minecraft version | 26.1.2 (client jar shipped with this repo's NeoForge toolchain) |
| Rows | 886 |
| Generated | 2026-09-02 |

**How this was built.** Derived from the game's own data, not from memory:

- all 1,326 vanilla loot tables (`blocks/`, `entities/`, `chests/`, `gameplay/`, `harvest/`,
  `shearing/`, `carve/`, `brush/`, `archaeology/`, `pots/`, `spawners/`, `equipment/`, `dispensers/`)
- all 387 villager/wandering-trader trades (data-driven since 26.1)
- 65 biome definitions, resolved through placed -> configured features, for what generates where
- 1,202 structure NBT templates, palette-parsed, plus the code-generated structures
- the `noise_settings` surface rules, for terrain blocks no feature places
- all 1,515 vanilla recipes, to mark which resources are *also* craftable

Domain is assigned from biome tags (`is_ocean`, `is_nether`, ...) and structure membership. An item
found across three or more surface biomes is filed as general Overworld rather than to whichever
biome sorts first.

**Legend.**

- `(c)` - also obtainable by crafting, so the entry here is about the *found* route.
- `(finite)` - non-renewable: the world holds a fixed amount and mining it out is permanent.
  Taken from the wiki's non-renewable list, minus everything its renewable list also names
  (those two pages contradict each other on 52 items, mostly Peaceful-difficulty rows and
  Bedrock-only entries). Absence of the tag means "not confidently finite", not "renewable".
- Sub-headings are the acquisition method.

---

## Wood  <sub>57</sub>

*Every log/leaf/sapling family, plus the stripped variants (axe on a log).*

### Mine / break a block

- [ ] `acacia_leaves` - mine acacia_leaves
- [ ] `azalea_leaves` - mine azalea_leaves
- [ ] `birch_leaves` - mine birch_leaves
- [ ] `cherry_leaves` - mine cherry_leaves
- [ ] `crimson_stem` - mine crimson_stem
- [ ] `dark_oak_leaves` - mine dark_oak_leaves
- [ ] `flowering_azalea_leaves` - mine flowering_azalea_leaves
- [ ] `jungle_leaves` - mine jungle_leaves
- [ ] `mangrove_leaves` - mine mangrove_leaves
- [ ] `oak_leaves` - mine oak_leaves
- [ ] `pale_oak_leaves` - mine pale_oak_leaves
- [ ] `spruce_leaves` - mine spruce_leaves
- [ ] `stripped_acacia_log` - mine stripped_acacia_log
- [ ] `stripped_oak_log` - mine stripped_oak_log
- [ ] `stripped_spruce_log` - mine stripped_spruce_log
- [ ] `warped_stem` - mine warped_stem

### Mob & entity drops

- [ ] `bamboo` - chest jungle_temple; chest shipwreck_supply; fishing; +2 more
- [ ] `stick` `(c)` - brush desert_well; chest spawn_bonus_chest; chest trial_chambers/entrance; +17 more

### Harvest & interact

- [ ] `stripped_acacia_wood` `(c)` - use an axe on acacia wood
- [ ] `stripped_bamboo_block` - use an axe on a bamboo block
- [ ] `stripped_birch_log` - use an axe on a birch log
- [ ] `stripped_birch_wood` `(c)` - use an axe on birch wood
- [ ] `stripped_cherry_log` - use an axe on a cherry log
- [ ] `stripped_cherry_wood` `(c)` - use an axe on cherry wood
- [ ] `stripped_crimson_hyphae` `(c)` - use an axe on crimson hyphae
- [ ] `stripped_crimson_stem` - use an axe on a crimson stem
- [ ] `stripped_dark_oak_log` - use an axe on a dark_oak log
- [ ] `stripped_dark_oak_wood` `(c)` - use an axe on dark_oak wood
- [ ] `stripped_jungle_log` - use an axe on a jungle log
- [ ] `stripped_jungle_wood` `(c)` - use an axe on jungle wood
- [ ] `stripped_mangrove_log` - use an axe on a mangrove log
- [ ] `stripped_mangrove_wood` `(c)` - use an axe on mangrove wood
- [ ] `stripped_pale_oak_log` - use an axe on a pale_oak log
- [ ] `stripped_pale_oak_wood` `(c)` - use an axe on pale_oak wood
- [ ] `stripped_warped_hyphae` `(c)` - use an axe on warped hyphae
- [ ] `stripped_warped_stem` - use an axe on a warped stem

### Structure chests

- [ ] `acacia_log` - chest spawn_bonus_chest; mine acacia_log; trade wandering_trader
- [ ] `acacia_planks` `(c)` - chest trial_chambers/supply; mine acacia_planks
- [ ] `acacia_sapling` - chest village/village_savanna_house; mine acacia_leaves; mine acacia_sapling; +1 more
- [ ] `bamboo_planks` `(c)` - chest trial_chambers/corridor; chest trial_chambers/intersection_barrel
- [ ] `birch_log` - chest spawn_bonus_chest; mine birch_log; trade wandering_trader
- [ ] `dark_oak_log` - chest pillager_outpost; chest spawn_bonus_chest; mine dark_oak_log; +1 more
- [ ] `jungle_log` - chest spawn_bonus_chest; mine jungle_log; trade wandering_trader
- [ ] `mangrove_log` - chest spawn_bonus_chest; mine mangrove_log; trade wandering_trader
- [ ] `oak_log` - chest spawn_bonus_chest; mine oak_log; trade wandering_trader
- [ ] `oak_planks` `(c)` - chest spawn_bonus_chest; mine oak_planks
- [ ] `oak_sapling` - chest village/village_plains_house; chest village/village_weaponsmith; mine oak_leaves; +1 more
- [ ] `spruce_log` - chest spawn_bonus_chest; chest village/village_taiga_house; mine spruce_log; +1 more
- [ ] `spruce_sapling` - chest village/village_taiga_house; mine potted_spruce_sapling; mine spruce_leaves; +1 more

### Trading

- [ ] `birch_sapling` - mine birch_leaves; mine potted_birch_sapling; trade wandering_trader
- [ ] `cherry_log` - mine cherry_log; trade wandering_trader
- [ ] `cherry_sapling` - mine cherry_leaves; trade wandering_trader
- [ ] `dark_oak_sapling` - mine dark_oak_leaves; mine dark_oak_sapling; trade wandering_trader
- [ ] `jungle_sapling` - mine jungle_leaves; trade wandering_trader
- [ ] `mangrove_propagule` - mine mangrove_propagule; trade wandering_trader
- [ ] `pale_oak_log` - mine pale_oak_log; trade wandering_trader
- [ ] `pale_oak_sapling` - mine pale_oak_leaves; trade wandering_trader

<details><summary>Also mineable from structures here, but craftable (9) - decoration, not a resource</summary>

`acacia_wood`, `birch_planks`, `dark_oak_planks`, `jungle_planks`, `mangrove_wood`, `spruce_planks`, `spruce_wood`, `stripped_oak_wood`, `stripped_spruce_wood`

</details>


## Overworld - General Surface  <sub>99</sub>

*Found across three or more surface biomes, so not biome-specific: farm animals, wool, eggs, common plants.*

### Mine / break a block

- [ ] `amethyst_cluster` - mine amethyst_cluster
- [ ] `bee_nest` - mine bee_nest
- [ ] `coarse_dirt` `(c)` - mine coarse_dirt
- [ ] `creaking_heart` `(c)` - mine creaking_heart
- [ ] `dirt` - mine dirt; mine dirt_path; mine farmland; +3 more
- [ ] `grass_block` - mine grass_block
- [ ] `large_amethyst_bud` - mine large_amethyst_bud
- [ ] `medium_amethyst_bud` - mine medium_amethyst_bud
- [ ] `mossy_cobblestone` `(c)` - mine mossy_cobblestone
- [ ] `sandstone` `(c)` - mine sandstone
- [ ] `small_amethyst_bud` - mine small_amethyst_bud
- [ ] `sunflower` - mine sunflower
- [ ] `turtle_egg` - mine turtle_egg

### Mob & entity drops

- [ ] `arrow` `(c)` - chest bastion_bridge; chest bastion_hoglin_stable; chest bastion_other; +15 more
- [ ] `beef` - chest village/village_butcher; kill cow; kill mooshroom
- [ ] `black_wool` `(c)` - chest village/village_shepherd; hero of the village gift; kill sheep; +3 more
- [ ] `blue_wool` `(c)` - hero of the village gift; kill sheep; mine blue_wool; +2 more
- [ ] `bone` - chest ancient_city; chest desert_pyramid; chest jungle_temple; +9 more
- [ ] `brown_wool` `(c)` - chest village/village_shepherd; hero of the village gift; kill sheep; +3 more
- [ ] `carrot` - chest pillager_outpost; chest shipwreck_supply; kill husk; +2 more
- [ ] `chicken` - cat morning gift; kill chicken
- [ ] `cyan_wool` `(c)` - hero of the village gift; kill sheep; mine cyan_wool; +2 more
- [ ] `feather` - cat morning gift; chest shipwreck_map; chest village/village_fletcher; +3 more
- [ ] `glass_bottle` `(c)` - kill witch
- [ ] `glow_ink_sac` - kill glow_squid
- [ ] `gray_wool` `(c)` - chest village/village_shepherd; hero of the village gift; kill sheep; +3 more
- [ ] `green_wool` `(c)` - hero of the village gift; kill sheep; mine green_wool; +2 more
- [ ] `gunpowder` - brush desert_pyramid; chest desert_pyramid; chest shipwreck_supply; +6 more
- [ ] `iron_ingot` `(c)` - chest abandoned_mineshaft; chest bastion_bridge; chest bastion_other; +21 more
- [ ] `light_blue_wool` `(c)` - hero of the village gift; kill sheep; mine light_blue_wool; +2 more
- [ ] `light_gray_wool` `(c)` - chest village/village_shepherd; hero of the village gift; kill sheep; +3 more
- [ ] `lime_wool` `(c)` - hero of the village gift; kill sheep; mine lime_wool; +2 more
- [ ] `magenta_wool` `(c)` - hero of the village gift; kill sheep; shear sheep; +1 more
- [ ] `music_disc_11` - kill creeper
- [ ] `music_disc_13` - chest ancient_city; chest simple_dungeon; chest woodland_mansion; +1 more
- [ ] `music_disc_blocks` - kill creeper
- [ ] `music_disc_cat` - chest ancient_city; chest simple_dungeon; chest woodland_mansion; +1 more
- [ ] `music_disc_chirp` - kill creeper
- [ ] `music_disc_far` - kill creeper
- [ ] `music_disc_lava_chicken` - kill zombie
- [ ] `music_disc_mall` - kill creeper
- [ ] `music_disc_mellohi` - kill creeper
- [ ] `music_disc_stal` - kill creeper
- [ ] `music_disc_strad` - kill creeper
- [ ] `music_disc_wait` - kill creeper
- [ ] `music_disc_ward` - kill creeper
- [ ] `mutton` - chest village/village_butcher; kill sheep
- [ ] `orange_wool` `(c)` - hero of the village gift; kill sheep; mine orange_wool; +2 more
- [ ] `phantom_membrane` - cat morning gift; kill phantom
- [ ] `pink_wool` `(c)` - hero of the village gift; kill sheep; shear sheep; +1 more
- [ ] `poppy` - chest village/village_plains_house; hero of the village gift; kill iron_golem; +3 more
- [ ] `porkchop` - chest bastion_hoglin_stable; chest village/village_butcher; kill hoglin; +1 more
- [ ] `potato` - chest pillager_outpost; chest shipwreck_supply; chest village/village_plains_house; +5 more
- [ ] `purple_wool` `(c)` - hero of the village gift; kill sheep; shear sheep; +1 more
- [ ] `rabbit` - kill rabbit
- [ ] `rabbit_foot` - cat morning gift; kill husk; kill rabbit
- [ ] `rabbit_hide` - cat morning gift; kill rabbit
- [ ] `red_mushroom` - kill zombie; mine red_mushroom; mine red_mushroom_block; +3 more
- [ ] `red_wool` `(c)` - hero of the village gift; kill sheep; mine red_wool; +2 more
- [ ] `rotten_flesh` - cat morning gift; chest desert_pyramid; chest igloo_chest; +16 more
- [ ] `slime_ball` `(c)` - kill slime; panda sneeze; trade wandering_trader
- [ ] `snowball` - chest ancient_city_ice_box; chest village/village_snowy_house; dispensers (trial chambers); +4 more
- [ ] `spider_eye` - chest desert_pyramid; kill cave_spider; kill spider; +1 more
- [ ] `sugar` `(c)` - kill witch
- [ ] `tipped_arrow` `(c)` - chest trial_chambers/reward_common; chest trial_chambers/reward_ominous_common; chest trial_chambers/supply; +5 more
- [ ] `white_wool` `(c)` - chest village/village_shepherd; hero of the village gift; kill sheep; +3 more
- [ ] `yellow_wool` `(c)` - hero of the village gift; kill sheep; mine yellow_wool; +2 more

### Harvest & interact

- [ ] `armadillo_scute` - armadillo shed; brush an armadillo
- [ ] `beetroot` - grow beetroot seeds
- [ ] `blue_egg` - chicken lays egg
- [ ] `brown_egg` - chicken lays egg
- [ ] `brown_mushroom` - mine brown_mushroom; mine brown_mushroom_block; shear bogged; +2 more
- [ ] `creeper_head` - charged creeper kills creeper
- [ ] `piglin_head` - charged creeper kills piglin
- [ ] `pitcher_plant` - grow a pitcher pod (sniffer)
- [ ] `pitcher_pod` - sniffer digging
- [ ] `player_head` - a charged creeper kills a player
- [ ] `torchflower` - grow torchflower seeds (sniffer)
- [ ] `torchflower_seeds` - sniffer digging
- [ ] `turtle_scute` - turtle grows up
- [ ] `wither_rose` - a wither kills a mob
- [ ] `zombie_head` - charged creeper kills zombie

### Piglin bartering

- [ ] `crying_obsidian` - chest bastion_bridge; chest bastion_hoglin_stable; chest bastion_other; +3 more
- [ ] `dried_ghast` `(c)` - piglin bartering
- [ ] `ender_pearl` - chest stronghold_corridor; chest trial_chambers/corridor; kill enderman; +2 more
- [ ] `leather` `(c)` - chest ancient_city; chest bastion_bridge; chest bastion_hoglin_stable; +16 more
- [ ] `nether_brick` `(c)` - piglin bartering

### Structure chests

- [ ] `apple` - chest igloo_chest; chest spawn_bonus_chest; chest stronghold_corridor; +6 more
- [ ] `dandelion` - chest village/village_plains_house; mine dandelion; mine potted_dandelion; +1 more
- [ ] `dead_bush` `(finite)` - brush trail_ruins_common; chest village/village_desert_house; mine dead_bush; +1 more
- [ ] `pumpkin` - chest shipwreck_supply; mine pumpkin; trade wandering_trader
- [ ] `resin_clump` `(c)` - chest woodland_mansion; mine creaking_heart
- [ ] `sand` - chest desert_pyramid; mine sand; trade wandering_trader
- [ ] `short_grass` - chest village/village_savanna_house; mine short_grass; mine tall_grass
- [ ] `wheat_seeds` - brush trail_ruins_common; chest village/village_fisher; chest village/village_savanna_house; +7 more

### Trading

- [ ] `clay` `(c)` - brush trail_ruins_common; hero of the village gift; mine clay
- [ ] `firefly_bush` - mine firefly_bush; trade wandering_trader
- [ ] `pale_hanging_moss` - mine pale_hanging_moss; trade wandering_trader
- [ ] `sugar_cane` - mine sugar_cane; trade wandering_trader


## Forest  <sub>10</sub>

### Mine / break a block

- [ ] `closed_eyeblossom` - mine closed_eyeblossom
- [ ] `leaf_litter` `(c)` - mine leaf_litter
- [ ] `lilac` - mine lilac
- [ ] `pale_moss_carpet` `(c)` - mine pale_moss_carpet
- [ ] `peony` - mine peony
- [ ] `rose_bush` - mine rose_bush

### Trading

- [ ] `allium` - mine allium; mine potted_allium; trade wandering_trader
- [ ] `lily_of_the_valley` - mine lily_of_the_valley; trade wandering_trader
- [ ] `pale_moss_block` - mine pale_moss_block; trade wandering_trader
- [ ] `wildflowers` - mine wildflowers; trade wandering_trader


## Jungle  <sub>5</sub>

### Mine / break a block

- [ ] `cocoa_beans` - mine cocoa
- [ ] `melon` `(c)` - mine melon
- [ ] `melon_slice` - mine melon

### Structure chests

- [ ] `fern` - chest village/village_taiga_house; mine fern; mine large_fern; +1 more
- [ ] `tripwire_hook` `(c)` - chest pillager_outpost; fishing; mine tripwire_hook

<details><summary>Also mineable from structures here, but craftable (1) - decoration, not a resource</summary>

`dispenser`

</details>


## Desert  <sub>6</sub>

### Mine / break a block

- [ ] `cactus_flower` - mine cactus_flower
- [ ] `short_dry_grass` - mine short_dry_grass

### Structure chests

- [ ] `cactus` - chest village/village_desert_house; mine cactus; mine potted_cactus; +1 more

### Trading

- [ ] `blue_terracotta` `(c)` - mine blue_terracotta; trade mason
- [ ] `orange_terracotta` `(c)` - mine orange_terracotta; trade mason
- [ ] `tall_dry_grass` - mine tall_dry_grass; trade wandering_trader

<details><summary>Also mineable from structures here, but craftable (1) - decoration, not a resource</summary>

`sandstone_slab`

</details>


## Badlands  <sub>4</sub>

### Mine / break a block

- [ ] `red_sandstone` `(c)` - mine red_sandstone
- [ ] `terracotta` `(c)` - mine terracotta

### Trading

- [ ] `red_sand` - mine red_sand; trade wandering_trader
- [ ] `white_terracotta` `(c)` - mine white_terracotta; trade mason


## Taiga  <sub>2</sub>

### Harvest & interact

- [ ] `sweet_berries` - chest village/village_taiga_house; harvest sweet_berry_bush; mine sweet_berry_bush

### Trading

- [ ] `podzol` - mine podzol; trade wandering_trader


## Swamp  <sub>7</sub>

### Mine / break a block

- [ ] `mangrove_roots` - mine mangrove_roots
- [ ] `mud` - mine mud
- [ ] `muddy_mangrove_roots` `(c)` - mine muddy_mangrove_roots

### Structure chests

- [ ] `flower_pot` `(c)` - brush trail_ruins_common; chest village/village_mason; mine flower_pot; +12 more

### Trading

- [ ] `blue_orchid` - mine blue_orchid; mine potted_blue_orchid; trade wandering_trader
- [ ] `lily_pad` - fishing; mine lily_pad; trade wandering_trader

### Other

- [ ] `tadpole_bucket` - bucket a tadpole

<details><summary>Also mineable from structures here, but craftable (2) - decoration, not a resource</summary>

`cauldron`, `crafting_table`

</details>


## Snowy  <sub>6</sub>

### Mine / break a block

- [ ] `ice` - mine ice
- [ ] `snow` `(c)` - mine snow

### Structure chests

- [ ] `blue_ice` `(c)` - chest village/village_snowy_house; mine blue_ice; trade wandering_trader
- [ ] `packed_ice` `(c)` - chest ancient_city_ice_box; mine packed_ice; trade wandering_trader
- [ ] `snow_block` `(c)` - chest village/village_snowy_house; mine snow; mine snow_block

### Other

- [ ] `powder_snow_bucket` - fill a bucket from powder snow


## Mountain  <sub>1</sub>

### Mine / break a block

- [ ] `pink_petals` - mine pink_petals


## Mushroom Fields  <sub>5</sub>

### Mine / break a block

- [ ] `brown_mushroom_block` - mine brown_mushroom_block
- [ ] `mushroom_stem` - mine mushroom_stem
- [ ] `mycelium` - mine mycelium
- [ ] `red_mushroom_block` - mine red_mushroom_block

### Other

- [ ] `mushroom_stew` `(c)` - milk a mooshroom with a bowl


## River  <sub>2</sub>

### Mine / break a block

- [ ] `bush` - mine bush

### Other

- [ ] `salmon_bucket` - bucket a salmon


## Ocean  <sub>74</sub>

*Warm/cold/frozen oceans and their structures: monuments, shipwrecks, ocean ruins, buried treasure.*

### Mine / break a block

- [ ] `brain_coral` - mine brain_coral
- [ ] `brain_coral_fan` - mine brain_coral_fan
- [ ] `bubble_coral` - mine bubble_coral
- [ ] `bubble_coral_fan` - mine bubble_coral_fan
- [ ] `dead_brain_coral` - mine dead_brain_coral
- [ ] `dead_brain_coral_block` - mine brain_coral_block; mine dead_brain_coral_block
- [ ] `dead_brain_coral_fan` - mine dead_brain_coral_fan
- [ ] `dead_bubble_coral` - mine dead_bubble_coral
- [ ] `dead_bubble_coral_block` - mine bubble_coral_block; mine dead_bubble_coral_block
- [ ] `dead_bubble_coral_fan` - mine dead_bubble_coral_fan
- [ ] `dead_fire_coral` - mine dead_fire_coral
- [ ] `dead_fire_coral_block` - mine dead_fire_coral_block; mine fire_coral_block
- [ ] `dead_fire_coral_fan` - mine dead_fire_coral_fan
- [ ] `dead_horn_coral` - mine dead_horn_coral
- [ ] `dead_horn_coral_block` - mine dead_horn_coral_block; mine horn_coral_block
- [ ] `dead_horn_coral_fan` - mine dead_horn_coral_fan
- [ ] `dead_tube_coral` - mine dead_tube_coral
- [ ] `dead_tube_coral_block` - mine dead_tube_coral_block; mine tube_coral_block
- [ ] `dead_tube_coral_fan` - mine dead_tube_coral_fan
- [ ] `fire_coral` - mine fire_coral
- [ ] `fire_coral_fan` - mine fire_coral_fan
- [ ] `horn_coral` - mine horn_coral
- [ ] `horn_coral_fan` - mine horn_coral_fan
- [ ] `magma_block` `(c)` - mine magma_block
- [ ] `tube_coral` - mine tube_coral
- [ ] `tube_coral_fan` - mine tube_coral_fan

### Mob & entity drops

- [ ] `cod` - chest village/village_fisher; fishing; hero of the village gift; +5 more
- [ ] `ink_sac` - fishing; kill squid
- [ ] `nautilus_shell` - fishing; kill nautilus; trade wandering_trader
- [ ] `prismarine_crystals` - chest buried_treasure; kill elder_guardian; kill guardian; +1 more
- [ ] `prismarine_shard` - kill elder_guardian; kill guardian
- [ ] `pufferfish` - fishing; kill pufferfish
- [ ] `salmon` - chest spawn_bonus_chest; chest village/village_fisher; fishing; +3 more
- [ ] `seagrass` - kill turtle; mine seagrass
- [ ] `tide_armor_trim_smithing_template` `(c)` - kill elder_guardian
- [ ] `wet_sponge` `(finite)` - kill elder_guardian; mine wet_sponge

### Piglin bartering

- [ ] `potion` - chest ancient_city; chest buried_treasure; chest trial_chambers/supply; +4 more

### Structure chests

- [ ] `clock` `(c)` - chest ruined_portal; chest shipwreck_map; trade librarian
- [ ] `coast_armor_trim_smithing_template` `(c)` - chest shipwreck_map; chest shipwreck_supply; chest shipwreck_treasure
- [ ] `compass` `(c)` - chest ancient_city; chest shipwreck_map; chest stronghold_library; +3 more
- [ ] `cooked_cod` `(c)` - chest buried_treasure; trade fisherman
- [ ] `cooked_salmon` `(c)` - chest buried_treasure; trade fisherman
- [ ] `copper_nautilus_armor` `(finite)` - chest buried_treasure; chest shipwreck_map; chest shipwreck_supply; +3 more
- [ ] `diamond_nautilus_armor` `(finite)` - chest buried_treasure; chest shipwreck_map; chest shipwreck_supply; +3 more
- [ ] `experience_bottle` - chest ancient_city; chest pillager_outpost; chest shipwreck_treasure; +1 more
- [ ] `fishing_rod` `(c)` - chest underwater_ruin_big; chest underwater_ruin_small; fishing; +1 more
- [ ] `gold_block` `(c)` - chest bastion_bridge; chest bastion_hoglin_stable; chest bastion_other; +3 more
- [ ] `golden_helmet` `(c)` - chest bastion_bridge; chest bastion_other; chest ruined_portal; +2 more
- [ ] `golden_nautilus_armor` `(finite)` - chest buried_treasure; chest shipwreck_map; chest shipwreck_supply; +3 more
- [ ] `heart_of_the_sea` `(finite)` - chest buried_treasure
- [ ] `iron_nautilus_armor` `(finite)` - chest buried_treasure; chest shipwreck_map; chest shipwreck_supply; +3 more
- [ ] `iron_spear` `(c)` - chest buried_treasure; chest village/village_weaponsmith
- [ ] `leather_boots` `(c)` - chest shipwreck_supply; chest village/village_tannery; fishing; +1 more
- [ ] `leather_chestplate` `(c)` - chest buried_treasure; chest shipwreck_supply; chest underwater_ruin_big; +3 more
- [ ] `leather_helmet` `(c)` - chest shipwreck_supply; chest village/village_tannery; trade leatherworker
- [ ] `leather_leggings` `(c)` - chest shipwreck_supply; chest village/village_tannery; trade leatherworker
- [ ] `map` `(c)` - chest shipwreck_map; chest stronghold_library; chest underwater_ruin_big; +4 more
- [ ] `paper` `(c)` - chest shipwreck_map; chest shipwreck_supply; chest stronghold_library; +2 more
- [ ] `poisonous_potato` - chest shipwreck_supply
- [ ] `stone_axe` `(c)` - chest igloo_chest; chest spawn_bonus_chest; chest trial_chambers/corridor; +3 more
- [ ] `stone_spear` `(c)` - chest underwater_ruin_big; chest underwater_ruin_small
- [ ] `suspicious_stew` `(c)` - brush desert_well; chest ancient_city_ice_box; chest shipwreck_supply; +1 more
- [ ] `wheat` `(c)` - brush ocean_ruin_cold; brush ocean_ruin_warm; brush trail_ruins_common; +11 more

### Trading

- [ ] `brain_coral_block` - mine brain_coral_block; trade wandering_trader
- [ ] `bubble_coral_block` - mine bubble_coral_block; trade wandering_trader
- [ ] `fire_coral_block` - mine fire_coral_block; trade wandering_trader
- [ ] `horn_coral_block` - mine horn_coral_block; trade wandering_trader
- [ ] `kelp` - mine kelp; trade wandering_trader
- [ ] `light_blue_terracotta` `(c)` - mine light_blue_terracotta; trade mason
- [ ] `polished_diorite` `(c)` - mine polished_diorite; trade mason
- [ ] `polished_granite` `(c)` - mine polished_granite; trade mason
- [ ] `purple_glazed_terracotta` `(c)` - mine purple_glazed_terracotta; trade mason
- [ ] `sea_pickle` - mine sea_pickle; trade wandering_trader
- [ ] `tube_coral_block` - mine tube_coral_block; trade wandering_trader

<details><summary>Also mineable from structures here, but craftable (21) - decoration, not a resource</summary>

`birch_fence`, `birch_slab`, `birch_stairs`, `bricks`, `dark_oak_door`, `dark_oak_stairs`, `dark_oak_trapdoor`, `dark_prismarine`, `jungle_door`, `jungle_fence`, `jungle_slab`, `jungle_stairs`, `jungle_trapdoor`, `oak_stairs`, `oak_trapdoor`, `prismarine`, `prismarine_bricks`, `sea_lantern`, `spruce_door`, `spruce_slab`, `spruce_trapdoor`

</details>


## Cave & Underground  <sub>91</sub>

*Ores and everything below the surface: deepslate, amethyst, dripstone, lush caves, deep dark, mineshafts, strongholds.*

### Mine / break a block

- [ ] `amethyst_block` `(c)` - mine amethyst_block
- [ ] `andesite` `(c)` - mine andesite
- [ ] `azalea` - mine azalea; mine azalea_leaves
- [ ] `big_dripleaf` - mine big_dripleaf; mine big_dripleaf_stem
- [ ] `calcite` `(finite)` - mine calcite
- [ ] `coal_ore` `(finite)` - mine coal_ore
- [ ] `cobbled_deepslate` `(c)` - mine cobbled_deepslate; mine deepslate
- [ ] `cobblestone` `(c)` - mine cobblestone; mine infested_cobblestone; mine stone; +1 more
- [ ] `cobweb` - mine cobweb
- [ ] `copper_ore` `(finite)` - mine copper_ore
- [ ] `deepslate` `(c)` `(finite)` - mine deepslate; mine infested_deepslate
- [ ] `deepslate_coal_ore` `(finite)` - mine deepslate_coal_ore
- [ ] `deepslate_copper_ore` `(finite)` - mine deepslate_copper_ore
- [ ] `deepslate_diamond_ore` `(finite)` - mine deepslate_diamond_ore
- [ ] `deepslate_emerald_ore` `(finite)` - mine deepslate_emerald_ore
- [ ] `deepslate_gold_ore` `(finite)` - mine deepslate_gold_ore
- [ ] `deepslate_iron_ore` `(finite)` - mine deepslate_iron_ore
- [ ] `deepslate_lapis_ore` `(finite)` - mine deepslate_lapis_ore
- [ ] `deepslate_redstone_ore` `(finite)` - mine deepslate_redstone_ore
- [ ] `diamond_ore` `(finite)` - mine diamond_ore
- [ ] `diorite` `(c)` - mine diorite
- [ ] `emerald_ore` `(finite)` - mine emerald_ore
- [ ] `flowering_azalea` - mine flowering_azalea; mine flowering_azalea_leaves
- [ ] `glow_lichen` - mine glow_lichen
- [ ] `gold_ore` `(finite)` - mine gold_ore
- [ ] `granite` `(c)` - mine granite
- [ ] `hanging_roots` - mine hanging_roots
- [ ] `iron_bars` `(c)` - mine iron_bars
- [ ] `iron_ore` `(finite)` - mine iron_ore
- [ ] `lapis_ore` `(finite)` - mine lapis_ore
- [ ] `moss_carpet` `(c)` - mine moss_carpet
- [ ] `raw_copper` `(c)` - mine copper_ore; mine deepslate_copper_ore
- [ ] `raw_gold` `(c)` - mine deepslate_gold_ore; mine gold_ore
- [ ] `raw_iron` `(c)` - mine deepslate_iron_ore; mine iron_ore
- [ ] `redstone_ore` `(finite)` - mine redstone_ore
- [ ] `sculk_shrieker` - mine sculk_shrieker
- [ ] `sculk_vein` - mine sculk_vein
- [ ] `smooth_basalt` `(c)` - mine smooth_basalt
- [ ] `spore_blossom` `(finite)` - mine spore_blossom

### Mob & entity drops

- [ ] `bone_meal` `(c)` - chest trial_chambers/supply; kill cod; kill pufferfish; +3 more
- [ ] `coal` `(c)` - brush ocean_ruin_cold; brush ocean_ruin_warm; brush trail_ruins_common; +16 more
- [ ] `copper_ingot` `(c)` - kill copper_golem; kill drowned
- [ ] `emerald` `(c)` - brush desert_pyramid; brush desert_well; brush ocean_ruin_cold; +46 more
- [ ] `redstone` `(c)` - chest abandoned_mineshaft; chest simple_dungeon; chest stronghold_corridor; +9 more
- [ ] `sculk_catalyst` - chest ancient_city; kill warden; mine sculk_catalyst
- [ ] `tropical_fish` - fishing; kill tropical_fish

### Harvest & interact

- [ ] `diamond` `(c)` - brush desert_pyramid; chest abandoned_mineshaft; chest bastion_treasure; +16 more
- [ ] `glow_berries` - chest abandoned_mineshaft; chest ancient_city; chest trial_chambers/supply; +3 more
- [ ] `skeleton_skull` - charged creeper kills skeleton; mine skeleton_skull

### Piglin bartering

- [ ] `gravel` - mine gravel; piglin bartering
- [ ] `obsidian` - chest bastion_other; chest nether_bridge; chest ruined_portal; +5 more
- [ ] `soul_sand` - chest bastion_hoglin_stable; mine soul_sand; piglin bartering
- [ ] `string` - brush trail_ruins_common; cat morning gift; chest bastion_bridge; +14 more

### Structure chests

- [ ] `amethyst_shard` - chest ancient_city; chest trial_chambers/intersection; mine amethyst_cluster
- [ ] `bone_block` `(c)` - chest bastion_other; mine bone_block
- [ ] `candle` `(c)` - chest ancient_city; mine candle
- [ ] `clay_ball` - chest village/village_desert_house; chest village/village_mason; mine clay; +1 more
- [ ] `detector_rail` `(c)` - chest abandoned_mineshaft; mine detector_rail
- [ ] `echo_shard` `(finite)` - chest ancient_city
- [ ] `flint` - chest ruined_portal; chest village/village_fletcher; mine gravel; +1 more
- [ ] `furnace` `(c)` - chest village/village_snowy_house; mine furnace
- [ ] `lapis_lazuli` `(c)` - chest abandoned_mineshaft; chest shipwreck_treasure; chest village/village_temple; +4 more
- [ ] `moss_block` - chest shipwreck_supply; chest trial_chambers/supply; mine moss_block; +1 more
- [ ] `powered_rail` `(c)` - chest abandoned_mineshaft; mine powered_rail
- [ ] `rail` `(c)` - chest abandoned_mineshaft; mine rail
- [ ] `sculk` - a sculk catalyst consumes a mob death; chest ancient_city; mine sculk
- [ ] `sculk_sensor` - chest ancient_city; mine sculk_sensor
- [ ] `stone` `(c)` - chest village/village_mason; mine infested_stone; mine stone
- [ ] `torch` `(c)` - chest abandoned_mineshaft; chest trial_chambers/corridor; chest trial_chambers/supply; +2 more
- [ ] `tuff` `(finite)` - chest trial_chambers/corridor; chest trial_chambers/supply; mine tuff

### Trading

- [ ] `azure_bluet` - mine azure_bluet; mine potted_azure_bluet; trade wandering_trader
- [ ] `blue_carpet` `(c)` - mine blue_carpet; trade shepherd
- [ ] `bookshelf` `(c)` - mine bookshelf; trade librarian
- [ ] `campfire` `(c)` - mine campfire; trade fisherman
- [ ] `cornflower` - mine cornflower; trade wandering_trader
- [ ] `cyan_carpet` `(c)` - mine cyan_carpet; trade shepherd
- [ ] `dripstone_block` `(c)` - mine dripstone_block; trade mason
- [ ] `glass` `(c)` - mine glass; trade librarian
- [ ] `gray_carpet` `(c)` - mine gray_carpet; trade shepherd
- [ ] `light_blue_carpet` `(c)` - mine light_blue_carpet; trade shepherd
- [ ] `orange_tulip` - mine orange_tulip; trade wandering_trader
- [ ] `oxeye_daisy` - mine oxeye_daisy; mine potted_oxeye_daisy; trade wandering_trader
- [ ] `pink_tulip` - mine pink_tulip; trade wandering_trader
- [ ] `pointed_dripstone` - mine pointed_dripstone; trade wandering_trader
- [ ] `red_tulip` - mine potted_red_tulip; mine red_tulip; trade wandering_trader
- [ ] `rooted_dirt` - mine rooted_dirt; trade wandering_trader
- [ ] `small_dripleaf` - mine small_dripleaf; trade wandering_trader
- [ ] `vine` - mine vine; trade wandering_trader
- [ ] `white_tulip` - mine potted_white_tulip; mine white_tulip; trade wandering_trader

### Other

- [ ] `axolotl_bucket` - bucket an axolotl (lush caves)
- [ ] `lava_bucket` - fill a bucket from lava

<details><summary>Also mineable from structures here, but craftable (37) - decoration, not a resource</summary>

`charcoal`, `chiseled_deepslate`, `cobbled_deepslate_slab`, `cobbled_deepslate_stairs`, `cobbled_deepslate_wall`, `comparator`, `cracked_deepslate_bricks`, `cracked_deepslate_tiles`, `deepslate_brick_slab`, `deepslate_brick_stairs`, `deepslate_brick_wall`, `deepslate_bricks`, `deepslate_tile_slab`, `deepslate_tile_stairs`, `deepslate_tile_wall`, `deepslate_tiles`, `glass_pane`, `iron_door`, `iron_trapdoor`, `lectern`, `note_block`, `polished_basalt`, `polished_deepslate`, `polished_deepslate_slab`, `polished_deepslate_stairs`, `polished_deepslate_wall`, `redstone_block`, `redstone_lamp`, `redstone_torch`, `repeater`, `soul_lantern`, `sticky_piston`, `stone_brick_slab`, `stone_button`, `stone_pressure_plate`, `target`, `white_candle`

</details>


## Nether  <sub>68</sub>

*The Nether dimension, its biomes, fortresses, bastions, and piglin bartering.*

### Mine / break a block

- [ ] `basalt` - mine basalt
- [ ] `nether_gold_ore` `(finite)` - mine nether_gold_ore
- [ ] `nether_quartz_ore` `(finite)` - mine nether_quartz_ore
- [ ] `nether_sprouts` - mine nether_sprouts
- [ ] `nether_wart_block` `(c)` - mine nether_wart_block
- [ ] `netherrack` - mine crimson_nylium; mine netherrack; mine warped_nylium
- [ ] `shroomlight` - mine shroomlight
- [ ] `soul_soil` - mine soul_soil
- [ ] `twisting_vines` - mine twisting_vines
- [ ] `warped_fungus` - mine warped_fungus
- [ ] `warped_nylium` - mine warped_nylium
- [ ] `warped_roots` - mine warped_roots
- [ ] `warped_wart_block` - mine warped_wart_block
- [ ] `weeping_vines` - mine weeping_vines

### Mob & entity drops

- [ ] `blaze_rod` - kill blaze
- [ ] `ghast_tear` - kill ghast
- [ ] `glowstone_dust` - kill witch; mine glowstone
- [ ] `gold_ingot` `(c)` - chest abandoned_mineshaft; chest bastion_bridge; chest bastion_other; +16 more
- [ ] `gold_nugget` `(c)` - brush ocean_ruin_cold; brush ocean_ruin_warm; brush trail_ruins_common; +12 more
- [ ] `magma_cream` `(c)` - chest bastion_other; chest bastion_treasure; kill magma_cube
- [ ] `music_disc_tears` - kill ghast
- [ ] `nether_star` - kill the wither
- [ ] `ochre_froglight` - kill magma_cube
- [ ] `pearlescent_froglight` - kill magma_cube
- [ ] `verdant_froglight` - kill magma_cube
- [ ] `wither_skeleton_skull` - charged creeper kills wither_skeleton; kill wither_skeleton

### Piglin bartering

- [ ] `blackstone` - mine blackstone; piglin bartering
- [ ] `fire_charge` `(c)` - chest ruined_portal; dispensers (trial chambers); piglin bartering; +1 more
- [ ] `quartz` `(c)` - chest bastion_treasure; mine nether_quartz_ore; piglin bartering
- [ ] `spectral_arrow` `(c)` - chest bastion_bridge; chest bastion_other; chest bastion_treasure; +1 more

### Structure chests

- [ ] `ancient_debris` `(finite)` - chest bastion_hoglin_stable; chest bastion_other; chest bastion_treasure; +1 more
- [ ] `bell` - chest ruined_portal; mine bell; trade smith
- [ ] `cooked_porkchop` `(c)` - chest bastion_hoglin_stable; chest bastion_other; hero of the village gift; +1 more
- [ ] `crimson_fungus` - chest bastion_hoglin_stable; mine crimson_fungus
- [ ] `crimson_nylium` - chest bastion_hoglin_stable; mine crimson_nylium
- [ ] `crimson_roots` - chest bastion_hoglin_stable; mine crimson_roots
- [ ] `crossbow` `(c)` - chest bastion_bridge; chest bastion_other; chest pillager_outpost; +3 more
- [ ] `diamond_boots` `(c)` - chest bastion_treasure; chest end_city_treasure; trade armorer
- [ ] `diamond_shovel` `(c)` - chest bastion_hoglin_stable; chest bastion_other; chest end_city_treasure; +1 more
- [ ] `diamond_spear` `(c)` - chest bastion_treasure; chest end_city_treasure
- [ ] `enchanted_golden_apple` - chest abandoned_mineshaft; chest ancient_city; chest bastion_treasure; +5 more
- [ ] `flint_and_steel` `(c)` - chest nether_bridge; chest ruined_portal
- [ ] `gilded_blackstone` `(finite)` - chest bastion_bridge; chest bastion_hoglin_stable; chest bastion_other; +2 more
- [ ] `glistering_melon_slice` `(c)` - chest ruined_portal; trade farmer
- [ ] `glowstone` `(c)` - chest bastion_hoglin_stable; mine glowstone; trade cleric; +1 more
- [ ] `golden_axe` `(c)` - chest bastion_bridge; chest bastion_hoglin_stable; chest bastion_other; +3 more
- [ ] `golden_boots` `(c)` - chest bastion_bridge; chest bastion_other; chest ruined_portal
- [ ] `golden_carrot` `(c)` - chest ancient_city_ice_box; chest bastion_hoglin_stable; chest bastion_other; +4 more
- [ ] `golden_chestplate` `(c)` - chest bastion_bridge; chest bastion_other; chest nether_bridge; +1 more
- [ ] `golden_hoe` `(c)` - chest ruined_portal
- [ ] `golden_leggings` `(c)` - chest bastion_bridge; chest bastion_other; chest ruined_portal
- [ ] `golden_pickaxe` `(c)` - chest ruined_portal; chest trial_chambers/intersection_barrel
- [ ] `golden_shovel` `(c)` - chest ruined_portal
- [ ] `golden_sword` `(c)` - chest bastion_bridge; chest bastion_other; chest nether_bridge; +1 more
- [ ] `iron_block` `(c)` - chest bastion_other; chest bastion_treasure; chest trial_chambers/intersection; +1 more
- [ ] `iron_chain` `(c)` - chest bastion_other; mine iron_chain
- [ ] `light_weighted_pressure_plate` `(c)` - chest ruined_portal
- [ ] `lodestone` `(c)` - chest bastion_bridge; chest ruined_portal
- [ ] `music_disc_pigstep` `(finite)` - chest bastion_other
- [ ] `nether_wart` - chest nether_bridge; mine nether_wart
- [ ] `netherite_ingot` `(c)` `(finite)` - chest bastion_treasure
- [ ] `netherite_scrap` `(c)` `(finite)` - chest bastion_hoglin_stable; chest bastion_other; chest bastion_treasure
- [ ] `netherite_upgrade_smithing_template` `(c)` - chest bastion_bridge; chest bastion_hoglin_stable; chest bastion_other; +1 more
- [ ] `piglin_banner_pattern` - chest bastion_other
- [ ] `rib_armor_trim_smithing_template` `(c)` - chest nether_bridge
- [ ] `snout_armor_trim_smithing_template` `(c)` - chest bastion_bridge; chest bastion_hoglin_stable; chest bastion_other; +1 more

### Trading

- [ ] `lantern` `(c)` - mine lantern; trade librarian
- [ ] `quartz_block` `(c)` - mine quartz_block; trade mason

<details><summary>Also mineable from structures here, but craftable (14) - decoration, not a resource</summary>

`blackstone_slab`, `blackstone_stairs`, `blackstone_wall`, `chiseled_polished_blackstone`, `cracked_polished_blackstone_bricks`, `nether_brick_fence`, `nether_brick_stairs`, `nether_bricks`, `polished_blackstone_brick_stairs`, `polished_blackstone_bricks`, `smooth_quartz`, `smooth_quartz_slab`, `stone_brick_wall`, `stone_slab`

</details>


## End  <sub>16</sub>

*The End dimension, end cities, and the dragon.*

### Mine / break a block

- [ ] `chorus_flower` - mine chorus_flower
- [ ] `chorus_fruit` - mine chorus_plant
- [ ] `dragon_head` `(finite)` - mine from the end ship's bow
- [ ] `end_stone` - mine end_stone

### Mob & entity drops

- [ ] `shulker_shell` - kill shulker

### Harvest & interact

- [ ] `dragon_egg` `(finite)` - ender dragon's first death; move with a piston

### Piglin bartering

- [ ] `iron_boots` `(c)` - chest end_city_treasure; chest stronghold_corridor; chest village/village_weaponsmith; +2 more

### Structure chests

- [ ] `beetroot_seeds` - brush trail_ruins_common; chest abandoned_mineshaft; chest end_city_treasure; +4 more
- [ ] `iron_chestplate` `(c)` - chest end_city_treasure; chest stronghold_corridor; chest trial_chambers/reward_rare; +3 more
- [ ] `iron_helmet` `(c)` - chest end_city_treasure; chest stronghold_corridor; chest village/village_armorer; +3 more
- [ ] `iron_leggings` `(c)` - chest ancient_city; chest end_city_treasure; chest stronghold_corridor; +2 more
- [ ] `iron_pickaxe` `(c)` - chest abandoned_mineshaft; chest end_city_treasure; chest stronghold_corridor; +5 more
- [ ] `iron_shovel` `(c)` - chest end_city_treasure; chest village/village_toolsmith; trade toolsmith
- [ ] `spire_armor_trim_smithing_template` `(c)` - chest end_city_treasure

### Other

- [ ] `dragon_breath` - bottle the ender dragon's breath cloud
- [ ] `elytra` `(finite)` - take from the item frame on an end ship

<details><summary>Also mineable from structures here, but craftable (9) - decoration, not a resource</summary>

`brewing_stand`, `end_rod`, `end_stone_bricks`, `ender_chest`, `magenta_stained_glass`, `purpur_block`, `purpur_pillar`, `purpur_slab`, `purpur_stairs`

</details>


## Structures & Chest Loot  <sub>140</sub>

*Only reachable from a generated structure's chest or its block palette.*

### Mine / break a block

- [ ] `damaged_anvil` - mine damaged_anvil
- [ ] `oxidized_copper_trapdoor` - mine oxidized_copper_trapdoor
- [ ] `red_concrete` - mine red_concrete
- [ ] `white_concrete` - mine white_concrete

### Mob & entity drops

- [ ] `breeze_rod` - kill breeze
- [ ] `ominous_banner` `(finite)` - kill a raid captain
- [ ] `ominous_bottle` - chest trial_chambers/reward_common; chest trial_chambers/reward_ominous_common; kill pillager
- [ ] `saddle` `(c)` - chest bastion_hoglin_stable; chest end_city_treasure; chest nether_bridge; +6 more
- [ ] `totem_of_undying` - kill evoker
- [ ] `trident` - chest trial_chambers/reward_unique; kill a naturally-spawned drowned holding one

### Harvest & interact

- [ ] `carved_pumpkin` - mine carved_pumpkin; shear snow_golem
- [ ] `diamond_block` `(c)` - chest trial_chambers/intersection; chest trial_chambers/reward_ominous_rare; decorated pot; +1 more
- [ ] `egg` - chest village/village_fletcher; chicken lays egg; dispensers (trial chambers)
- [ ] `emerald_block` `(c)` - chest trial_chambers/intersection; chest trial_chambers/reward_ominous_rare; decorated pot
- [ ] `honeycomb` - chest trial_chambers/corridor; chest trial_chambers/entrance; harvest beehive
- [ ] `music_disc_creator_music_box` `(finite)` - decorated pot
- [ ] `pumpkin_seeds` `(c)` - chest abandoned_mineshaft; chest simple_dungeon; chest village/village_taiga_house; +5 more
- [ ] `trial_key` - chest trial_chambers/entrance; decorated pot; spawners (trial chambers)

### Piglin bartering

- [ ] `book` `(c)` - chest abandoned_mineshaft; chest ancient_city; chest bastion_other; +18 more
- [ ] `iron_nugget` `(c)` - chest bastion_bridge; chest bastion_other; chest ruined_portal; +3 more
- [ ] `splash_potion` - dispensers (trial chambers); piglin bartering

### Structure chests

- [ ] `activator_rail` `(c)` - chest abandoned_mineshaft
- [ ] `baked_potato` `(c)` - chest ancient_city_ice_box; chest trial_chambers/intersection_barrel; chest trial_chambers/supply; +1 more
- [ ] `bamboo_hanging_sign` `(c)` - chest trial_chambers/corridor
- [ ] `barrel` `(c)` - chest village/village_fisher; mine barrel
- [ ] `beetroot_soup` `(c)` - chest village/village_snowy_house
- [ ] `bolt_armor_trim_smithing_template` `(c)` - chest trial_chambers/reward_unique
- [ ] `bow` `(c)` - chest trial_chambers/reward_rare; equipment (trial chambers); fishing; +1 more
- [ ] `bread` `(c)` - chest abandoned_mineshaft; chest simple_dungeon; chest spawn_bonus_chest; +18 more
- [ ] `bucket` `(c)` - chest simple_dungeon; chest trial_chambers/intersection_barrel; chest village/village_savanna_house; +1 more
- [ ] `bundle` `(c)` - chest village/village_cartographer; chest village/village_desert_house; chest village/village_plains_house; +5 more
- [ ] `cake` `(c)` - chest trial_chambers/intersection; trade farmer
- [ ] `chainmail_chestplate` - chest woodland_mansion; equipment (trial chambers); hero of the village gift; +1 more
- [ ] `copper_horse_armor` `(finite)` - chest desert_pyramid; chest end_city_treasure; chest jungle_temple; +4 more
- [ ] `copper_spear` `(c)` - chest village/village_weaponsmith
- [ ] `diamond_axe` `(c)` - chest trial_chambers/intersection; chest trial_chambers/intersection_barrel; chest trial_chambers/reward_ominous_rare; +3 more
- [ ] `diamond_chestplate` `(c)` - chest bastion_treasure; chest end_city_treasure; chest trial_chambers/reward_ominous_rare; +4 more
- [ ] `diamond_helmet` `(c)` - chest bastion_treasure; chest end_city_treasure; equipment (trial chambers); +1 more
- [ ] `diamond_hoe` `(c)` - chest ancient_city; chest woodland_mansion; trade toolsmith
- [ ] `diamond_horse_armor` `(finite)` - chest ancient_city; chest desert_pyramid; chest end_city_treasure; +5 more
- [ ] `diamond_leggings` `(c)` - chest ancient_city; chest bastion_treasure; chest end_city_treasure; +1 more
- [ ] `diamond_pickaxe` `(c)` - chest bastion_hoglin_stable; chest bastion_other; chest end_city_treasure; +3 more
- [ ] `diamond_sword` `(c)` - chest bastion_treasure; chest end_city_treasure; equipment (trial chambers); +1 more
- [ ] `disc_fragment_5` `(finite)` - chest ancient_city
- [ ] `dune_armor_trim_smithing_template` `(c)` - chest desert_pyramid
- [ ] `eye_armor_trim_smithing_template` `(c)` - chest stronghold_corridor; chest stronghold_library
- [ ] `flow_armor_trim_smithing_template` `(c)` - chest trial_chambers/reward_ominous_unique
- [ ] `flow_banner_pattern` - chest trial_chambers/reward_ominous_unique
- [ ] `goat_horn` - a goat rams a hard block; chest pillager_outpost
- [ ] `golden_apple` `(c)` - chest abandoned_mineshaft; chest bastion_hoglin_stable; chest bastion_other; +9 more
- [ ] `golden_horse_armor` `(finite)` - chest desert_pyramid; chest end_city_treasure; chest jungle_temple; +5 more
- [ ] `green_dye` `(c)` - chest village/village_desert_house; trade wandering_trader
- [ ] `guster_banner_pattern` - chest trial_chambers/reward_unique
- [ ] `heavy_core` - chest trial_chambers/reward_ominous_unique
- [ ] `honey_bottle` `(c)` - chest trial_chambers/reward_common; use a glass bottle on a full beehive
- [ ] `iron_axe` `(c)` - brush ocean_ruin_cold; brush ocean_ruin_warm; chest trial_chambers/corridor; +4 more
- [ ] `iron_horse_armor` `(finite)` - chest desert_pyramid; chest end_city_treasure; chest jungle_temple; +4 more
- [ ] `iron_sword` `(c)` - chest bastion_other; chest buried_treasure; chest end_city_treasure; +4 more
- [ ] `large_fern` - chest village/village_taiga_house
- [ ] `lead` `(c)` - brush trail_ruins_common; chest ancient_city; chest woodland_mansion
- [ ] `melon_seeds` `(c)` - chest abandoned_mineshaft; chest simple_dungeon; chest woodland_mansion; +3 more
- [ ] `milk_bucket` - chest trial_chambers/supply; milk a cow, goat or mooshroom
- [ ] `music_disc_creator` - chest trial_chambers/reward_ominous_unique
- [ ] `music_disc_otherside` `(finite)` - chest ancient_city; chest simple_dungeon; chest stronghold_corridor
- [ ] `music_disc_precipice` - chest trial_chambers/reward_unique
- [ ] `name_tag` `(c)` - chest abandoned_mineshaft; chest simple_dungeon; fishing; +1 more
- [ ] `pumpkin_pie` `(c)` - chest village/village_taiga_house; hero of the village gift; trade farmer
- [ ] `scaffolding` `(c)` - chest trial_chambers/corridor
- [ ] `sentry_armor_trim_smithing_template` `(c)` - chest pillager_outpost
- [ ] `shears` `(c)` - chest village/village_shepherd; trade shepherd
- [ ] `shield` `(c)` - chest trial_chambers/reward_rare; trade armorer
- [ ] `silence_armor_trim_smithing_template` `(c)` - chest ancient_city
- [ ] `smooth_stone` `(c)` - chest village/village_mason; mine smooth_stone
- [ ] `soul_torch` `(c)` - chest ancient_city
- [ ] `spruce_sign` `(c)` - chest village/village_taiga_house
- [ ] `stone_bricks` `(c)` - chest village/village_mason; mine infested_stone_bricks; mine stone_bricks
- [ ] `stone_pickaxe` `(c)` - chest spawn_bonus_chest; chest trial_chambers/corridor; chest trial_chambers/supply; +2 more
- [ ] `tall_grass` - chest village/village_savanna_house
- [ ] `tnt` `(c)` - brush desert_pyramid; chest buried_treasure; chest shipwreck_supply; +1 more
- [ ] `vex_armor_trim_smithing_template` `(c)` - chest woodland_mansion
- [ ] `ward_armor_trim_smithing_template` `(c)` - chest ancient_city
- [ ] `water_bucket` - chest village/village_fisher; dispensers (trial chambers); fill a bucket from water
- [ ] `wild_armor_trim_smithing_template` `(c)` - chest jungle_temple
- [ ] `wind_charge` `(c)` - chest trial_chambers/reward_common; chest trial_chambers/reward_ominous_common; spawners (trial chambers)
- [ ] `wooden_axe` `(c)` - chest spawn_bonus_chest; chest trial_chambers/entrance
- [ ] `wooden_pickaxe` `(c)` - chest spawn_bonus_chest
- [ ] `yellow_dye` `(c)` - brush trail_ruins_common; chest village/village_mason; trade wandering_trader

### Trading

- [ ] `black_bed` `(c)` - mine black_bed; trade shepherd
- [ ] `black_carpet` `(c)` - mine black_carpet; trade shepherd
- [ ] `black_glazed_terracotta` `(c)` - mine black_glazed_terracotta; trade mason
- [ ] `blue_bed` `(c)` - mine blue_bed; trade shepherd
- [ ] `brown_bed` `(c)` - mine brown_bed; trade shepherd
- [ ] `brown_carpet` `(c)` - mine brown_carpet; trade shepherd
- [ ] `brown_terracotta` `(c)` - mine brown_terracotta; trade mason
- [ ] `chainmail_helmet` - equipment (trial chambers); hero of the village gift; trade armorer
- [ ] `chiseled_stone_bricks` `(c)` - mine chiseled_stone_bricks; mine infested_chiseled_stone_bricks; trade mason
- [ ] `cooked_beef` `(c)` - hero of the village gift; spawners (trial chambers)
- [ ] `cooked_chicken` `(c)` - hero of the village gift; spawners (trial chambers); trade butcher
- [ ] `cyan_bed` `(c)` - mine cyan_bed; trade shepherd
- [ ] `cyan_glazed_terracotta` `(c)` - mine cyan_glazed_terracotta; trade mason
- [ ] `cyan_terracotta` `(c)` - mine cyan_terracotta; trade mason
- [ ] `gray_bed` `(c)` - mine gray_bed; trade shepherd
- [ ] `gray_terracotta` `(c)` - mine gray_terracotta; trade mason
- [ ] `green_bed` `(c)` - mine green_bed; trade shepherd
- [ ] `green_carpet` `(c)` - mine green_carpet; trade shepherd
- [ ] `light_blue_bed` `(c)` - mine light_blue_bed; trade shepherd
- [ ] `light_blue_glazed_terracotta` `(c)` - mine light_blue_glazed_terracotta; trade mason
- [ ] `light_gray_bed` `(c)` - mine light_gray_bed; trade shepherd
- [ ] `light_gray_carpet` `(c)` - mine light_gray_carpet; trade shepherd
- [ ] `light_gray_glazed_terracotta` `(c)` - mine light_gray_glazed_terracotta; trade mason
- [ ] `light_gray_terracotta` `(c)` - mine light_gray_terracotta; trade mason
- [ ] `lime_bed` `(c)` - mine lime_bed; trade shepherd
- [ ] `lime_carpet` `(c)` - mine lime_carpet; trade shepherd
- [ ] `lime_glazed_terracotta` `(c)` - mine lime_glazed_terracotta; trade mason
- [ ] `lime_terracotta` `(c)` - mine lime_terracotta; trade mason
- [ ] `magenta_bed` `(c)` - mine magenta_bed; trade shepherd
- [ ] `magenta_carpet` `(c)` - mine magenta_carpet; trade shepherd
- [ ] `orange_bed` `(c)` - mine orange_bed; trade shepherd
- [ ] `orange_carpet` `(c)` - mine orange_carpet; trade shepherd
- [ ] `orange_glazed_terracotta` `(c)` - mine orange_glazed_terracotta; trade mason
- [ ] `pink_bed` `(c)` - mine pink_bed; trade shepherd
- [ ] `pink_carpet` `(c)` - mine pink_carpet; trade shepherd
- [ ] `polished_andesite` `(c)` - mine polished_andesite; trade mason
- [ ] `purple_bed` `(c)` - mine purple_bed; trade shepherd
- [ ] `purple_carpet` `(c)` - mine purple_carpet; trade shepherd
- [ ] `red_bed` `(c)` - mine red_bed; trade shepherd
- [ ] `red_candle` `(c)` - brush trail_ruins_common; mine red_candle; trade librarian
- [ ] `red_carpet` `(c)` - mine red_carpet; trade shepherd
- [ ] `red_glazed_terracotta` `(c)` - mine red_glazed_terracotta; trade mason
- [ ] `red_terracotta` `(c)` - mine red_terracotta; trade mason
- [ ] `white_bed` `(c)` - mine white_bed; trade shepherd
- [ ] `white_carpet` `(c)` - mine white_carpet; trade shepherd
- [ ] `white_glazed_terracotta` `(c)` - mine white_glazed_terracotta; trade mason
- [ ] `yellow_bed` `(c)` - mine yellow_bed; trade shepherd
- [ ] `yellow_carpet` `(c)` - mine yellow_carpet; trade shepherd
- [ ] `yellow_glazed_terracotta` `(c)` - mine yellow_glazed_terracotta; trade mason
- [ ] `yellow_terracotta` `(c)` - mine yellow_terracotta; trade mason

### Archaeology

- [ ] `yellow_stained_glass_pane` `(c)` - brush trail_ruins_common; mine yellow_stained_glass_pane

### Other

- [ ] `lingering_potion` - dispensers (trial chambers); spawners (trial chambers)
- [ ] `ominous_trial_key` - spawners (trial chambers)

<details><summary>Also mineable from structures here, but craftable (98) - decoration, not a resource</summary>

`acacia_door`, `acacia_fence`, `acacia_fence_gate`, `acacia_pressure_plate`, `acacia_slab`, `acacia_stairs`, `black_stained_glass`, `blast_furnace`, `brick_slab`, `brick_stairs`, `brick_wall`, `brown_stained_glass`, `cartography_table`, `chest`, `chiseled_sandstone`, `chiseled_tuff`, `chiseled_tuff_bricks`, `coal_block`, `cobblestone_slab`, `cobblestone_stairs`, `cobblestone_wall`, `composter`, `copper_block`, `cracked_stone_bricks`, `cut_sandstone`, `dark_oak_fence`, `dark_oak_fence_gate`, `dark_oak_slab`, `decorated_pot`, `diorite_slab`, `diorite_stairs`, `diorite_wall`, `fletching_table`, `granite_stairs`, `granite_wall`, `grindstone`, `hay_block`, `hopper`, `jungle_button`, `jungle_fence_gate`, `ladder`, `lapis_block`, `lever`, `light_gray_stained_glass`, `loom`, `mossy_cobblestone_slab`, `mossy_cobblestone_stairs`, `mossy_cobblestone_wall`, `mossy_stone_bricks`, `mud_brick_slab`, `mud_brick_stairs`, `mud_brick_wall`, `mud_bricks`, `oak_button`, `oak_door`, `oak_fence`, `oak_fence_gate`, `oak_pressure_plate`, `oak_slab`, `orange_stained_glass_pane`, `oxidized_cut_copper`, `packed_mud`, `polished_tuff`, `polished_tuff_slab`, `sandstone_stairs`, `sandstone_wall`, `smithing_table`, `smoker`, `smooth_sandstone`, `smooth_sandstone_slab`, `smooth_sandstone_stairs`, `smooth_stone_slab`, `spruce_fence`, `spruce_fence_gate`, `spruce_pressure_plate`, `spruce_stairs`, `stone_brick_stairs`, `stonecutter`, `trapped_chest`, `tuff_bricks`, `waxed_chiseled_copper`, `waxed_copper_block`, `waxed_copper_bulb`, `waxed_copper_door`, `waxed_copper_grate`, `waxed_cut_copper`, `waxed_cut_copper_slab`, `waxed_cut_copper_stairs`, `waxed_oxidized_chiseled_copper`, `waxed_oxidized_copper`, `waxed_oxidized_copper_door`, `waxed_oxidized_copper_grate`, `waxed_oxidized_copper_trapdoor`, `waxed_oxidized_cut_copper`, `waxed_oxidized_cut_copper_slab`, `waxed_oxidized_cut_copper_stairs`, `white_stained_glass`, `white_stained_glass_pane`

</details>


## Trading  <sub>62</sub>

*Villager trades, wandering trader, and Hero of the Village gifts.*

### Trading

- [ ] `black_banner` `(c)` - trade cartographer; trade shepherd
- [ ] `black_dye` `(c)` - trade wandering_trader
- [ ] `black_terracotta` `(c)` - trade mason
- [ ] `blue_banner` `(c)` - trade cartographer; trade shepherd
- [ ] `blue_dye` `(c)` - brush trail_ruins_common; trade wandering_trader
- [ ] `blue_glazed_terracotta` `(c)` - trade mason
- [ ] `brick` `(c)` - brush desert_well; brush trail_ruins_common; trade mason
- [ ] `brown_banner` `(c)` - trade cartographer; trade shepherd
- [ ] `brown_dye` `(c)` - trade wandering_trader
- [ ] `brown_glazed_terracotta` `(c)` - trade mason
- [ ] `chainmail_boots` - hero of the village gift; trade armorer
- [ ] `chainmail_leggings` - hero of the village gift; trade armorer
- [ ] `cod_bucket` - trade fisherman
- [ ] `cooked_mutton` `(c)` - hero of the village gift
- [ ] `cooked_rabbit` `(c)` - hero of the village gift
- [ ] `cookie` `(c)` - hero of the village gift; trade farmer
- [ ] `cyan_banner` `(c)` - trade cartographer; trade shepherd
- [ ] `cyan_dye` `(c)` - trade wandering_trader
- [ ] `enchanted_book` - trade librarian
- [ ] `globe_banner_pattern` - trade cartographer
- [ ] `golden_dandelion` `(c)` - trade wandering_trader
- [ ] `gray_banner` `(c)` - trade cartographer; trade shepherd
- [ ] `gray_dye` `(c)` - trade wandering_trader
- [ ] `gray_glazed_terracotta` `(c)` - trade mason
- [ ] `green_banner` `(c)` - trade cartographer; trade shepherd
- [ ] `green_glazed_terracotta` `(c)` - trade mason
- [ ] `green_terracotta` `(c)` - trade mason
- [ ] `item_frame` `(c)` - trade cartographer
- [ ] `leather_horse_armor` `(c)` - trade leatherworker
- [ ] `light_blue_banner` `(c)` - trade cartographer; trade shepherd
- [ ] `light_blue_dye` `(c)` - brush trail_ruins_common; trade wandering_trader
- [ ] `light_gray_banner` `(c)` - trade shepherd
- [ ] `light_gray_dye` `(c)` - trade wandering_trader
- [ ] `lime_banner` `(c)` - trade cartographer; trade shepherd
- [ ] `lime_dye` `(c)` - trade wandering_trader
- [ ] `magenta_banner` `(c)` - trade cartographer; trade shepherd
- [ ] `magenta_dye` `(c)` - trade wandering_trader
- [ ] `magenta_glazed_terracotta` `(c)` - trade mason
- [ ] `magenta_terracotta` `(c)` - trade mason
- [ ] `open_eyeblossom` - trade wandering_trader
- [ ] `orange_banner` `(c)` - trade cartographer; trade shepherd
- [ ] `orange_dye` `(c)` - brush trail_ruins_common; trade wandering_trader
- [ ] `painting` `(c)` - trade shepherd
- [ ] `pink_banner` `(c)` - trade cartographer; trade shepherd
- [ ] `pink_dye` `(c)` - trade wandering_trader
- [ ] `pink_glazed_terracotta` `(c)` - trade mason
- [ ] `pink_terracotta` `(c)` - trade mason
- [ ] `pufferfish_bucket` - trade wandering_trader
- [ ] `purple_banner` `(c)` - trade cartographer; trade shepherd
- [ ] `purple_dye` `(c)` - trade wandering_trader
- [ ] `purple_terracotta` `(c)` - trade mason
- [ ] `quartz_pillar` `(c)` - trade mason
- [ ] `rabbit_stew` `(c)` - trade butcher
- [ ] `red_banner` `(c)` - trade cartographer; trade shepherd
- [ ] `red_dye` `(c)` - trade wandering_trader
- [ ] `stone_hoe` `(c)` - hero of the village gift; trade toolsmith
- [ ] `stone_shovel` `(c)` - hero of the village gift; trade toolsmith
- [ ] `tropical_fish_bucket` - trade wandering_trader
- [ ] `white_banner` `(c)` - trade cartographer; trade shepherd
- [ ] `white_dye` `(c)` - brush trail_ruins_common; trade wandering_trader
- [ ] `yellow_banner` `(c)` - trade cartographer; trade shepherd
- [ ] `yellow_candle` `(c)` - trade librarian


## Fishing  <sub>1</sub>

*The fishing loot tables (fish / junk / treasure).*

### Mob & entity drops

- [ ] `bowl` `(c)` - fishing; kill turtle


## Archaeology  <sub>38</sub>

*Brushing suspicious sand and gravel.*

### Archaeology

- [ ] `angler_pottery_sherd` `(finite)` - brush ocean_ruin_warm
- [ ] `archer_pottery_sherd` `(finite)` - brush desert_pyramid
- [ ] `arms_up_pottery_sherd` `(finite)` - brush desert_well
- [ ] `blade_pottery_sherd` `(finite)` - brush ocean_ruin_cold
- [ ] `blue_stained_glass_pane` `(c)` - brush trail_ruins_common
- [ ] `brewer_pottery_sherd` `(finite)` - brush desert_well
- [ ] `brown_candle` `(c)` - brush trail_ruins_common
- [ ] `burn_pottery_sherd` `(finite)` - brush trail_ruins_rare
- [ ] `danger_pottery_sherd` `(finite)` - brush trail_ruins_rare
- [ ] `explorer_pottery_sherd` `(finite)` - brush ocean_ruin_cold
- [ ] `friend_pottery_sherd` `(finite)` - brush trail_ruins_rare
- [ ] `green_candle` `(c)` - brush trail_ruins_common
- [ ] `heart_pottery_sherd` `(finite)` - brush trail_ruins_rare
- [ ] `heartbreak_pottery_sherd` `(finite)` - brush trail_ruins_rare
- [ ] `host_armor_trim_smithing_template` `(c)` - brush trail_ruins_rare
- [ ] `howl_pottery_sherd` `(finite)` - brush trail_ruins_rare
- [ ] `light_blue_stained_glass_pane` `(c)` - brush trail_ruins_common
- [ ] `magenta_stained_glass_pane` `(c)` - brush trail_ruins_common
- [ ] `miner_pottery_sherd` `(finite)` - brush desert_pyramid
- [ ] `mourner_pottery_sherd` `(finite)` - brush ocean_ruin_cold
- [ ] `music_disc_relic` `(finite)` - brush trail_ruins_rare
- [ ] `oak_hanging_sign` `(c)` - brush trail_ruins_common
- [ ] `pink_stained_glass_pane` `(c)` - brush trail_ruins_common
- [ ] `plenty_pottery_sherd` `(finite)` - brush ocean_ruin_cold
- [ ] `prize_pottery_sherd` `(finite)` - brush desert_pyramid
- [ ] `purple_candle` `(c)` - brush trail_ruins_common
- [ ] `purple_stained_glass_pane` `(c)` - brush trail_ruins_common
- [ ] `raiser_armor_trim_smithing_template` `(c)` - brush trail_ruins_rare
- [ ] `red_stained_glass_pane` `(c)` - brush trail_ruins_common
- [ ] `shaper_armor_trim_smithing_template` `(c)` - brush trail_ruins_rare
- [ ] `sheaf_pottery_sherd` `(finite)` - brush trail_ruins_rare
- [ ] `shelter_pottery_sherd` `(finite)` - brush ocean_ruin_warm
- [ ] `skull_pottery_sherd` `(finite)` - brush desert_pyramid
- [ ] `sniffer_egg` - brush ocean_ruin_warm
- [ ] `snort_pottery_sherd` `(finite)` - brush ocean_ruin_warm
- [ ] `spruce_hanging_sign` `(c)` - brush trail_ruins_common
- [ ] `wayfinder_armor_trim_smithing_template` `(c)` - brush trail_ruins_rare
- [ ] `wooden_hoe` `(c)` - brush ocean_ruin_cold; brush ocean_ruin_warm; brush trail_ruins_common


---

## Index: by acquisition method

Cross-reference. Items are filed above by *where*; this lists them by *how*, so a method-shaped
question ("what does bartering actually give me?") is answerable without reading every section.
An item appears under every method that yields it.

**Fishing** <sub>22</sub>

`bamboo`, `bone`, `book`, `bow`, `bowl`, `cod`, `fishing_rod`, `ink_sac`, `leather`, `leather_boots`, `lily_pad`, `name_tag`, `nautilus_shell`, `potion`, `pufferfish`, `rotten_flesh`, `saddle`, `salmon`, `stick`, `string`, `tripwire_hook`, `tropical_fish`

**Piglin bartering** <sub>18</sub>

`blackstone`, `book`, `crying_obsidian`, `dried_ghast`, `ender_pearl`, `fire_charge`, `gravel`, `iron_boots`, `iron_nugget`, `leather`, `nether_brick`, `obsidian`, `potion`, `quartz`, `soul_sand`, `spectral_arrow`, `splash_potion`, `string`

**Shearing** <sub>20</sub>

`black_wool`, `blue_wool`, `brown_mushroom`, `brown_wool`, `carved_pumpkin`, `cyan_wool`, `gray_wool`, `green_wool`, `light_blue_wool`, `light_gray_wool`, `lime_wool`, `magenta_wool`, `orange_wool`, `pink_wool`, `pumpkin_seeds`, `purple_wool`, `red_mushroom`, `red_wool`, `white_wool`, `yellow_wool`

**Charged creeper (mob heads)** <sub>5</sub>

`creeper_head`, `piglin_head`, `skeleton_skull`, `wither_skeleton_skull`, `zombie_head`

**Sniffer** <sub>4</sub>

`pitcher_plant`, `pitcher_pod`, `torchflower`, `torchflower_seeds`

**Archaeology (brushing)** <sub>64</sub>

`angler_pottery_sherd`, `archer_pottery_sherd`, `armadillo_scute`, `arms_up_pottery_sherd`, `beetroot_seeds`, `blade_pottery_sherd`, `blue_dye`, `blue_stained_glass_pane`, `brewer_pottery_sherd`, `brick`, `brown_candle`, `burn_pottery_sherd`, `clay`, `coal`, `danger_pottery_sherd`, `dead_bush`, `diamond`, `emerald`, `explorer_pottery_sherd`, `flower_pot`, `friend_pottery_sherd`, `gold_nugget`, `green_candle`, `gunpowder`, `heart_pottery_sherd`, `heartbreak_pottery_sherd`, `host_armor_trim_smithing_template`, `howl_pottery_sherd`, `iron_axe`, `lead`, `light_blue_dye`, `light_blue_stained_glass_pane`, `magenta_stained_glass_pane`, `miner_pottery_sherd`, `mourner_pottery_sherd`, `music_disc_relic`, `oak_hanging_sign`, `orange_dye`, `pink_stained_glass_pane`, `plenty_pottery_sherd`, `prize_pottery_sherd`, `purple_candle`, `purple_stained_glass_pane`, `raiser_armor_trim_smithing_template`, `red_candle`, `red_stained_glass_pane`, `shaper_armor_trim_smithing_template`, `sheaf_pottery_sherd`, `shelter_pottery_sherd`, `skull_pottery_sherd`, `sniffer_egg`, `snort_pottery_sherd`, `spruce_hanging_sign`, `stick`, `string`, `suspicious_stew`, `tnt`, `wayfinder_armor_trim_smithing_template`, `wheat`, `wheat_seeds`, `white_dye`, `wooden_hoe`, `yellow_dye`, `yellow_stained_glass_pane`

**Hero of the Village gifts** <sub>47</sub>

`arrow`, `black_wool`, `blue_wool`, `book`, `bread`, `brown_wool`, `chainmail_boots`, `chainmail_chestplate`, `chainmail_helmet`, `chainmail_leggings`, `clay`, `cod`, `cooked_beef`, `cooked_chicken`, `cooked_mutton`, `cooked_porkchop`, `cooked_rabbit`, `cookie`, `cyan_wool`, `golden_axe`, `gray_wool`, `green_wool`, `iron_axe`, `lapis_lazuli`, `leather`, `light_blue_wool`, `light_gray_wool`, `lime_wool`, `magenta_wool`, `map`, `orange_wool`, `paper`, `pink_wool`, `poppy`, `pumpkin_pie`, `purple_wool`, `red_wool`, `redstone`, `salmon`, `stone_axe`, `stone_hoe`, `stone_pickaxe`, `stone_shovel`, `tipped_arrow`, `wheat_seeds`, `white_wool`, `yellow_wool`

**Trial chambers** <sub>72</sub>

`acacia_planks`, `amethyst_shard`, `arrow`, `baked_potato`, `bamboo_hanging_sign`, `bamboo_planks`, `bolt_armor_trim_smithing_template`, `bone_meal`, `book`, `bow`, `bread`, `bucket`, `cake`, `chainmail_chestplate`, `chainmail_helmet`, `compass`, `cooked_beef`, `cooked_chicken`, `crossbow`, `diamond`, `diamond_axe`, `diamond_block`, `diamond_chestplate`, `diamond_helmet`, `diamond_pickaxe`, `diamond_sword`, `egg`, `emerald`, `emerald_block`, `enchanted_golden_apple`, `ender_pearl`, `fire_charge`, `flow_armor_trim_smithing_template`, `flow_banner_pattern`, `glow_berries`, `golden_apple`, `golden_axe`, `golden_carrot`, `golden_pickaxe`, `guster_banner_pattern`, `heavy_core`, `honey_bottle`, `honeycomb`, `iron_axe`, `iron_block`, `iron_chestplate`, `iron_helmet`, `iron_ingot`, `iron_sword`, `lingering_potion`, `milk_bucket`, `moss_block`, `music_disc_creator`, `music_disc_precipice`, `ominous_bottle`, `ominous_trial_key`, `potion`, `scaffolding`, `shield`, `snowball`, `splash_potion`, `stick`, `stone_axe`, `stone_pickaxe`, `tipped_arrow`, `torch`, `trial_key`, `trident`, `tuff`, `water_bucket`, `wind_charge`, `wooden_axe`

**Villager & wandering trader** <sub>265</sub>

`acacia_log`, `acacia_sapling`, `allium`, `apple`, `arrow`, `azure_bluet`, `beetroot_seeds`, `bell`, `birch_log`, `birch_sapling`, `black_banner`, `black_bed`, `black_carpet`, `black_dye`, `black_glazed_terracotta`, `black_terracotta`, `black_wool`, `blue_banner`, `blue_bed`, `blue_carpet`, `blue_dye`, `blue_glazed_terracotta`, `blue_ice`, `blue_orchid`, `blue_terracotta`, `blue_wool`, `bookshelf`, `bow`, `brain_coral_block`, `bread`, `brick`, `brown_banner`, `brown_bed`, `brown_carpet`, `brown_dye`, `brown_glazed_terracotta`, `brown_mushroom`, `brown_terracotta`, `brown_wool`, `bubble_coral_block`, `cactus`, `cake`, `campfire`, `chainmail_boots`, `chainmail_chestplate`, `chainmail_helmet`, `chainmail_leggings`, `cherry_log`, `cherry_sapling`, `chiseled_stone_bricks`, `clock`, `cod_bucket`, `compass`, `cooked_chicken`, `cooked_cod`, `cooked_porkchop`, `cooked_salmon`, `cookie`, `cornflower`, `crossbow`, `cyan_banner`, `cyan_bed`, `cyan_carpet`, `cyan_dye`, `cyan_glazed_terracotta`, `cyan_terracotta`, `cyan_wool`, `dandelion`, `dark_oak_log`, `dark_oak_sapling`, `diamond_axe`, `diamond_boots`, `diamond_chestplate`, `diamond_helmet`, `diamond_hoe`, `diamond_leggings`, `diamond_pickaxe`, `diamond_shovel`, `diamond_sword`, `dripstone_block`, `emerald`, `enchanted_book`, `ender_pearl`, `experience_bottle`, `fern`, `fire_coral_block`, `firefly_bush`, `fishing_rod`, `flint`, `glass`, `glistering_melon_slice`, `globe_banner_pattern`, `glowstone`, `golden_carrot`, `golden_dandelion`, `gray_banner`, `gray_bed`, `gray_carpet`, `gray_dye`, `gray_glazed_terracotta`, `gray_terracotta`, `gray_wool`, `green_banner`, `green_bed`, `green_carpet`, `green_dye`, `green_glazed_terracotta`, `green_terracotta`, `green_wool`, `gunpowder`, `horn_coral_block`, `iron_axe`, `iron_boots`, `iron_chestplate`, `iron_helmet`, `iron_leggings`, `iron_pickaxe`, `iron_shovel`, `iron_sword`, `item_frame`, `jungle_log`, `jungle_sapling`, `kelp`, `lantern`, `lapis_lazuli`, `leather_boots`, `leather_chestplate`, `leather_helmet`, `leather_horse_armor`, `leather_leggings`, `light_blue_banner`, `light_blue_bed`, `light_blue_carpet`, `light_blue_dye`, `light_blue_glazed_terracotta`, `light_blue_terracotta`, `light_blue_wool`, `light_gray_banner`, `light_gray_bed`, `light_gray_carpet`, `light_gray_dye`, `light_gray_glazed_terracotta`, `light_gray_terracotta`, `light_gray_wool`, `lily_of_the_valley`, `lily_pad`, `lime_banner`, `lime_bed`, `lime_carpet`, `lime_dye`, `lime_glazed_terracotta`, `lime_terracotta`, `lime_wool`, `magenta_banner`, `magenta_bed`, `magenta_carpet`, `magenta_dye`, `magenta_glazed_terracotta`, `magenta_terracotta`, `magenta_wool`, `mangrove_log`, `mangrove_propagule`, `map`, `melon_seeds`, `moss_block`, `name_tag`, `nautilus_shell`, `oak_log`, `oak_sapling`, `open_eyeblossom`, `orange_banner`, `orange_bed`, `orange_carpet`, `orange_dye`, `orange_glazed_terracotta`, `orange_terracotta`, `orange_tulip`, `orange_wool`, `oxeye_daisy`, `packed_ice`, `painting`, `pale_hanging_moss`, `pale_moss_block`, `pale_oak_log`, `pale_oak_sapling`, `pink_banner`, `pink_bed`, `pink_carpet`, `pink_dye`, `pink_glazed_terracotta`, `pink_terracotta`, `pink_tulip`, `pink_wool`, `podzol`, `pointed_dripstone`, `polished_andesite`, `polished_diorite`, `polished_granite`, `poppy`, `potion`, `pufferfish_bucket`, `pumpkin`, `pumpkin_pie`, `pumpkin_seeds`, `purple_banner`, `purple_bed`, `purple_carpet`, `purple_dye`, `purple_glazed_terracotta`, `purple_terracotta`, `purple_wool`, `quartz_block`, `quartz_pillar`, `rabbit_stew`, `red_banner`, `red_bed`, `red_candle`, `red_carpet`, `red_dye`, `red_glazed_terracotta`, `red_mushroom`, `red_sand`, `red_terracotta`, `red_tulip`, `red_wool`, `redstone`, `rooted_dirt`, `saddle`, `sand`, `sea_pickle`, `shears`, `shield`, `slime_ball`, `small_dripleaf`, `spruce_log`, `spruce_sapling`, `stone_axe`, `stone_hoe`, `stone_pickaxe`, `stone_shovel`, `sugar_cane`, `suspicious_stew`, `tall_dry_grass`, `tipped_arrow`, `tropical_fish_bucket`, `tube_coral_block`, `vine`, `wheat_seeds`, `white_banner`, `white_bed`, `white_carpet`, `white_dye`, `white_glazed_terracotta`, `white_terracotta`, `white_tulip`, `white_wool`, `wildflowers`, `yellow_banner`, `yellow_bed`, `yellow_candle`, `yellow_carpet`, `yellow_dye`, `yellow_glazed_terracotta`, `yellow_terracotta`, `yellow_wool`

---

## Appendix: excluded

383 items were considered and excluded because their only loot table is the block dropping
itself, and nothing in worldgen, a structure, a mob, a chest or a trade produces one. These are
crafted goods, not resources. A few are worth naming because they look like resources and are not:

- **Concrete** (all 16) - concrete powder placed in water. Made, not found.
- **Copper oxidation states** (exposed / weathered / oxidized, and their doors, bars, chains,
  lanterns, chests, trapdoors, lightning rods, golem statues) - these form over time on copper you
  placed. None generate in any structure palette.
- **Chipped / damaged anvil** - an anvil degrading with use.
- **`petrified_oak_slab`** - no recipe and no generation. Not obtainable in survival at all.

### No survival source at all

Present in the game, reachable only with commands or creative. Listed so the audit is provably
complete rather than merely long:

`barrier`, `bedrock`, `budding_amethyst`, `chain_command_block`, `command_block`, `command_block_minecart`, `debug_stick`, `end_portal_frame`, `infested_chiseled_stone_bricks`, `infested_cobblestone`, `infested_cracked_stone_bricks`, `infested_deepslate`, `infested_mossy_stone_bricks`, `infested_stone`, `infested_stone_bricks`, `jigsaw`, `knowledge_book`, `light`, `petrified_oak_slab`, `reinforced_deepslate`, `repeating_command_block`, `spawner`, `structure_block`, `structure_void`, `test_block`, `test_instance_block`, `trial_spawner`, `vault`

...plus all 87 spawn eggs (`*_spawn_egg`), and `suspicious_sand` / `suspicious_gravel`, which
break rather than drop when mined.

### Named in the game, but with no source in the data

`flow_pottery_sherd`, `guster_pottery_sherd`, `scrape_pottery_sherd`

These three are real items and are members of `#minecraft:decorated_pot_sherds`, but in 26.1.2
they appear in **no loot table, no trade, and no structure**. Every loot table referenced by a
structure block entity was checked (all 1,202 templates parsed; the vault and trial-chamber
reward tables among them) and none yields a sherd. Either the drop moved into code or they are
genuinely unobtainable in this version. Worth confirming in-game before relying on either.

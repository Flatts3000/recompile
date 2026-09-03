# Renewability, read off the wiki's "Non-renewable resource" and "List of renewable resources".
#
# Those two pages contradict each other in places: the non-renewable page's Peaceful-difficulty
# sections list things that are plainly renewable above Peaceful, and it mixes Bedrock-only rows
# into the Java list. So an item is tagged finite ONLY if the non-renewable page names it and
# nothing on the renewable side does. Where the pages disagree, the item gets no tag rather than
# a wrong one.

NON_RENEWABLE_RAW = """
flow_banner_pattern guster_banner_pattern snout_banner_pattern decorated_pot disc_fragment_5
music_disc_5 dragon_egg dragon_head echo_shard elytra enchanted_book enchanted_golden_apple
gilded_blackstone heart_of_the_sea heavy_core large_fern music_disc_pigstep music_disc_otherside
music_disc_relic music_disc_creator music_disc_creator_music_box music_disc_precipice
copper_nautilus_armor iron_nautilus_armor golden_nautilus_armor diamond_nautilus_armor
netherite_nautilus_armor copper_horse_armor iron_horse_armor golden_horse_armor
diamond_horse_armor netherite_horse_armor angler_pottery_sherd archer_pottery_sherd
arms_up_pottery_sherd blade_pottery_sherd brewer_pottery_sherd burn_pottery_sherd
danger_pottery_sherd explorer_pottery_sherd flow_pottery_sherd friend_pottery_sherd
guster_pottery_sherd heart_pottery_sherd heartbreak_pottery_sherd howl_pottery_sherd
miner_pottery_sherd mourner_pottery_sherd plenty_pottery_sherd prize_pottery_sherd
scrape_pottery_sherd sheaf_pottery_sherd shelter_pottery_sherd skull_pottery_sherd
snort_pottery_sherd suspicious_gravel suspicious_sand tall_grass wet_sponge sponge
ancient_debris netherite_scrap netherite_ingot netherite_block netherite_helmet
netherite_chestplate netherite_leggings netherite_boots netherite_sword netherite_pickaxe
netherite_axe netherite_shovel netherite_hoe netherite_spear calcite cinnabar deepslate
dead_bush copper_ore deepslate_copper_ore iron_ore deepslate_iron_ore gold_ore
deepslate_gold_ore coal_ore deepslate_coal_ore lapis_ore deepslate_lapis_ore redstone_ore
deepslate_redstone_ore emerald_ore deepslate_emerald_ore diamond_ore deepslate_diamond_ore
nether_gold_ore nether_quartz_ore spore_blossom tuff coal copper_ingot music_disc_13
music_disc_cat ominous_banner ominous_bottle prismarine_crystals resin_clump sculk_catalyst
skeleton_skull spider_eye trial_key trident end_stone shulker_shell blaze_rod breeze_rod
dragon_breath ochre_froglight verdant_froglight pearlescent_froglight ghast_tear creeper_head
piglin_head wither_skeleton_skull zombie_head music_disc_blocks music_disc_chirp music_disc_far
music_disc_mall music_disc_mellohi music_disc_stal music_disc_strad music_disc_wait
music_disc_ward music_disc_11 music_disc_lava_chicken music_disc_tears ominous_trial_key
prismarine_shard totem_of_undying
"""

# Named by the renewable side too, so the "non-renewable" listing is difficulty-conditional,
# Bedrock-specific, or simply stale (vaults made several of these renewable).
CONTRADICTED_RAW = """
coal copper_ingot music_disc_13 music_disc_cat ominous_bottle prismarine_crystals resin_clump
sculk_catalyst skeleton_skull spider_eye trial_key trident end_stone shulker_shell blaze_rod
breeze_rod dragon_breath ochre_froglight verdant_froglight pearlescent_froglight ghast_tear
creeper_head piglin_head wither_skeleton_skull zombie_head music_disc_blocks music_disc_chirp
music_disc_far music_disc_mall music_disc_mellohi music_disc_stal music_disc_strad
music_disc_wait music_disc_ward music_disc_11 music_disc_lava_chicken music_disc_tears
ominous_trial_key prismarine_shard totem_of_undying enchanted_golden_apple heavy_core
flow_banner_pattern guster_banner_pattern music_disc_precipice music_disc_creator netherrack
dirt_path decorated_pot large_fern tall_grass tall_seagrass enchanted_book sponge music_disc_5
"""

# No survival source at all. Spawn eggs are summarised rather than listed.
NO_SURVIVAL_SOURCE_RAW = """
bedrock budding_amethyst end_portal_frame spawner trial_spawner vault reinforced_deepslate
barrier command_block repeating_command_block chain_command_block chain_command_block
debug_stick jigsaw light command_block_minecart petrified_oak_slab structure_block
structure_void knowledge_book test_block test_instance_block infested_stone infested_cobblestone
infested_stone_bricks infested_mossy_stone_bricks infested_cracked_stone_bricks
infested_chiseled_stone_bricks infested_deepslate
"""


def _s(raw):
    return {"minecraft:" + x for x in raw.split()}


NON_RENEWABLE = _s(NON_RENEWABLE_RAW)
CONTRADICTED = _s(CONTRADICTED_RAW)
FINITE = NON_RENEWABLE - CONTRADICTED
NO_SURVIVAL_SOURCE = _s(NO_SURVIVAL_SOURCE_RAW)

if __name__ == "__main__":
    print("non-renewable listed :", len(NON_RENEWABLE))
    print("contradicted (untag) :", len(CONTRADICTED & NON_RENEWABLE))
    print("tagged FINITE        :", len(FINITE))
    print("no survival source   :", len(NO_SURVIVAL_SOURCE))

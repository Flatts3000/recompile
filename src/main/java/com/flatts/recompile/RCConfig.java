package com.flatts.recompile;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * COMMON config. The mod's governing principle is "everything ships config-gated,
 * but defaults are the design" - config is for tuning, not for dodging decisions.
 * This starts minimal (Phase 0) and grows a section per system as they land.
 */
public final class RCConfig {

    public static final ModConfigSpec SPEC;

    /**
     * Master gate for the falling-block gravity shared by Blocks of Garbage (P0.3)
     * and mound-regrowth deorbit delivery (P1.6). Registered now so the config file
     * exists from first boot; consumed once those blocks land.
     */
    public static final ModConfigSpec.BooleanValue GARBAGE_GRAVITY_ENABLED;
    public static final ModConfigSpec.BooleanValue ROACHES_ENABLED;
    public static final ModConfigSpec.IntValue ROACH_CHANCE_DENOMINATOR;

    /**
     * Dimension lockout (P1.8): Nether and End access are disabled by default until
     * each themed dimension ships, so vanilla dimensions can't leak free resources
     * into the closed trash economy. Flip a flag on when its themed build lands.
     */
    public static final ModConfigSpec.BooleanValue NETHER_ENABLED;
    public static final ModConfigSpec.BooleanValue END_ENABLED;

    /**
     * Encroachment (P1.7-R): the junkyard fights back. Healed grass that borders unhealed
     * ground reverts to coarse dirt unless the reclamation ladder has stabilised it. The
     * defaults are the design; these exist so a pack can slow the fight down, not skip it.
     */
    public static final ModConfigSpec.BooleanValue ENCROACHMENT_ENABLED;
    public static final ModConfigSpec.IntValue ENCROACHMENT_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue ENCROACHMENT_ATTEMPTS_PER_PLAYER;
    public static final ModConfigSpec.IntValue ENCROACHMENT_RADIUS;
    public static final ModConfigSpec.IntValue TREE_ANCHOR_RADIUS;

    /**
     * Grass Spreader (P2.4-R3): rung 1 of the reclamation chain. A sprinkler that converts dead
     * ground to grass within a radius, consuming nothing. The radius is the load-bearing number -
     * it is exactly the land one machine can hold against encroachment.
     */
    public static final ModConfigSpec.BooleanValue GRASS_SPREADER_ENABLED;
    /**
     * Hard ceiling on the spreader's radius.
     *
     * <p>Shared with {@code GrassSpreaderCoreBlock}, which pre-builds its nearest-first offset table
     * out to exactly this distance. The two must agree: raising only the config bound would let a
     * player set a radius the offset table cannot reach, and the machine would silently stop at 64
     * with nothing anywhere reporting why.
     */
    public static final int GRASS_SPREADER_MAX_RADIUS = 64;

    public static final ModConfigSpec.IntValue GRASS_SPREADER_RADIUS;
    public static final ModConfigSpec.IntValue GRASS_SPREADER_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue GRASS_SPREADER_IDLE_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue GRASS_SPREADER_VERTICAL_TOLERANCE;

    // ---- Compost Heap (Mod Jam - the fertilizer tier) ----
    public static final ModConfigSpec.BooleanValue COMPOST_HEAP_ENABLED;
    public static final ModConfigSpec.IntValue COMPOST_LAYER_COST;
    public static final ModConfigSpec.IntValue COMPOST_LAYER_TICKS;
    public static final ModConfigSpec.IntValue COMPOST_VOLUNTEER_CHANCE;

    // ---- Vegetation tier (rung 2 - Fertilizer scatters plants) ----
    public static final ModConfigSpec.BooleanValue VEGETATION_ENABLED;
    public static final ModConfigSpec.BooleanValue FERTILIZER_GROWTH_ENABLED;
    public static final ModConfigSpec.IntValue FERTILIZER_ATTEMPTS;
    public static final ModConfigSpec.IntValue FERTILIZER_RIPPLE_TICKS;

    // ---- Farming tier (rung 3 - farmland from compost, not from a hoe) ----
    public static final ModConfigSpec.BooleanValue DISABLE_HOE_TILLING;

    // ---- Tree Nursery (rung 4 - saplings from water + Fertilizer + Unknown Seedling) ----
    public static final ModConfigSpec.BooleanValue TREE_NURSERY_ENABLED;
    public static final ModConfigSpec.IntValue TREE_NURSERY_COOK_TICKS;
    public static final ModConfigSpec.IntValue TREE_NURSERY_WATER_PER_SAPLING;
    public static final ModConfigSpec.IntValue TREE_NURSERY_TANK_CAPACITY;

    // ---- Animal bait (rung 5 - wildlife returns to healed grass) ----
    public static final ModConfigSpec.BooleanValue ANIMAL_BAIT_ENABLED;
    public static final ModConfigSpec.IntValue ANIMAL_BAIT_SETTLE_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue ANIMAL_BAIT_PLAYER_RADIUS;
    public static final ModConfigSpec.IntValue ANIMAL_BAIT_SPACING;

    /**
     * Scrap Bin (P2.9): how much one bin holds of its bound material. Large by design - the bin is
     * the tool the hoarding loop wants - and a first-pass number for the pre-beta balance pass.
     */
    public static final ModConfigSpec.IntValue SCRAP_BIN_CAPACITY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("world");
        GARBAGE_GRAVITY_ENABLED = builder
            .comment("Whether Blocks of Garbage obey gravity (slump when quarried, deorbit on regrowth).")
            .define("garbageGravityEnabled", true);
        ROACHES_ENABLED = builder
            .comment("Whether picking through garbage can disturb a roach (#78).")
            .define("roachesEnabled", true);
        ROACH_CHANCE_DENOMINATOR = builder
            .comment("One pull in N releases a roach instead of an item. Higher is rarer.",
                "This is a progression lever as well as a difficulty one: roaches are the earliest",
                "renewable food in the game, so the rate decides how much they feed you.",
                "",
                "NOTE: this is per PULL, not per block. A garbage block averages 2.5 pulls, so the",
                "rate a player actually experiences is one roach per N/2.5 blocks. 320 is the tuned",
                "target of roughly one roach per 128 blocks of garbage (owner, 2026-08-01).")
            .defineInRange("roachChanceDenominator", 320, 2, 10_000);
        builder.pop();

        builder.push("dimensions");
        NETHER_ENABLED = builder
            .comment("Allow travel to the Nether. Off until the themed Nether ships (P1.8).")
            .define("netherEnabled", false);
        END_ENABLED = builder
            .comment("Allow travel to the End. Off until the themed End ships (P1.8).")
            .define("endEnabled", false);
        builder.pop();

        builder.push("reclamation");
        ENCROACHMENT_ENABLED = builder
            .comment("Whether unhealed ground reclaims bordering healed grass (P1.7-R).")
            .define("encroachmentEnabled", true);
        ENCROACHMENT_INTERVAL_TICKS = builder
            .comment("Ticks between encroachment sweeps. Higher is slower.")
            .defineInRange("encroachmentIntervalTicks", 20, 1, 24000);
        ENCROACHMENT_ATTEMPTS_PER_PLAYER = builder
            .comment("Columns sampled per player per sweep. Most land on bare ground and do nothing.")
            .defineInRange("encroachmentAttemptsPerPlayer", 8, 0, 256);
        ENCROACHMENT_RADIUS = builder
            .comment("Horizontal radius around each player that the sweep samples, in blocks.")
            .defineInRange("encroachmentRadius", 48, 1, 128);
        TREE_ANCHOR_RADIUS = builder
            .comment("How far a log or leaf block holds the frontier permanently, in blocks.")
            .defineInRange("treeAnchorRadius", 4, 1, 16);

        GRASS_SPREADER_ENABLED = builder
            .comment("Whether the Grass Spreader converts ground (P2.4-R3, reclamation rung 1).")
            .define("grassSpreaderEnabled", true);
        GRASS_SPREADER_RADIUS = builder
            .comment("How far one spreader reaches, in blocks. This is exactly the land it can hold",
                     "against encroachment - beyond it, erosion wins.")
            .defineInRange("grassSpreaderRadius", 12, 1, GRASS_SPREADER_MAX_RADIUS);
        GRASS_SPREADER_INTERVAL_TICKS = builder
            .comment("Ticks between conversions while there is still ground to heal.")
            .defineInRange("grassSpreaderIntervalTicks", 40, 1, 24000);
        GRASS_SPREADER_IDLE_INTERVAL_TICKS = builder
            .comment("Ticks between re-scans once the radius is fully healed. Higher is cheaper;",
                     "it only has to notice ground the frontier has since taken back.")
            .defineInRange("grassSpreaderIdleIntervalTicks", 200, 1, 24000);
        GRASS_SPREADER_VERTICAL_TOLERANCE = builder
            .comment("How far above or below the machine a target surface may sit, in blocks,",
                     "so it cannot reach up cliffs or down pits.")
            .defineInRange("grassSpreaderVerticalTolerance", 3, 0, 32);

        COMPOST_HEAP_ENABLED = builder
            .comment("Whether the Compost Heap composts organics into Fertilizer.")
            .define("compostHeapEnabled", true);
        COMPOST_LAYER_COST = builder
            .comment("How many organics (muck and/or fiber) form one compost layer.")
            .defineInRange("compostLayerCost", 4, 1, 64);
        COMPOST_LAYER_TICKS = builder
            .comment("Ticks for one layer to finish composting into Fertilizer (1200 = 60s).")
            .defineInRange("compostLayerTicks", 1200, 1, 24000);
        COMPOST_VOLUNTEER_CHANCE = builder
            .comment("Chance (1 in N) that harvesting a compost layer also yields an Unknown Seedling -",
                     "a volunteer crop that sprouted in the pile. 8 = about one seedling per 8 layers.")
            .defineInRange("compostVolunteerChance", 8, 1, 1000);

        VEGETATION_ENABLED = builder
            .comment("Whether Fertilizer scatters plants (grass -> weeds/flowers, mycelium -> mushrooms).")
            .define("vegetationEnabled", true);
        FERTILIZER_GROWTH_ENABLED = builder
            .comment("Whether Fertilizer also accelerates planted crops and saplings, the way bone meal",
                "does. This world has no bone meal and cannot have any - it comes from skeletons, and",
                "the starting biome is deliberately creature-free - so without this nothing can hurry",
                "a crop or a tree along at all.")
            .define("fertilizerGrowthEnabled", true);
        FERTILIZER_ATTEMPTS = builder
            .comment("Scatter attempts per Fertilizer use (vanilla bonemeal uses 128).")
            .defineInRange("fertilizerAttempts", 128, 1, 512);
        FERTILIZER_RIPPLE_TICKS = builder
            .comment("Ticks over which the scatter ripples outward from the click (120 = 6s).")
            .defineInRange("fertilizerRippleTicks", 120, 0, 1200);

        DISABLE_HOE_TILLING = builder
            .comment("Block hoe-tilling of farmland, so farmland comes only from the compost recipe",
                     "(Fertilizer + dirt). The base mod has no hoe; this holds even in a pack that adds one.")
            .define("disableHoeTilling", true);

        TREE_NURSERY_ENABLED = builder
            .comment("Whether the Tree Nursery raises saplings (reclamation rung 4).")
            .define("treeNurseryEnabled", true);
        TREE_NURSERY_COOK_TICKS = builder
            .comment("Ticks to raise one sapling. Deliberately long - wood stays treasure (2400 = 120s).")
            .defineInRange("treeNurseryCookTicks", 2400, 1, 240000);
        TREE_NURSERY_WATER_PER_SAPLING = builder
            .comment("Water (mB) consumed per sapling. 250 = a quarter bucket.")
            .defineInRange("treeNurseryWaterPerSapling", 250, 0, 100000);
        TREE_NURSERY_TANK_CAPACITY = builder
            .comment("The nursery's internal water tank capacity, mB. 4000 = four buckets.")
            .defineInRange("treeNurseryTankCapacity", 4000, 1000, 1000000);

        ANIMAL_BAIT_ENABLED = builder
            .comment("Whether animal bait can draw wildlife back to healed grass (reclamation rung 5).")
            .define("animalBaitEnabled", true);
        ANIMAL_BAIT_SETTLE_INTERVAL_TICKS = builder
            .comment("Ticks per settle stage; a bait fires after 7 undisturbed stages. Deliberately slow -",
                     "you place it and leave. 300 = 15s/stage = about 1.75 min total.")
            .defineInRange("animalBaitSettleIntervalTicks", 300, 1, 24000);
        ANIMAL_BAIT_PLAYER_RADIUS = builder
            .comment("How near a player holds (and resets) a bait's settling, in blocks. Wildlife will not",
                     "come while watched, so you must step away.")
            .defineInRange("animalBaitPlayerRadius", 16.0, 1.0, 128.0);
        ANIMAL_BAIT_SPACING = builder
            .comment("Minimum distance between working baits, in blocks - they do not stack up a spot.")
            .defineInRange("animalBaitSpacing", 8, 1, 64);
        builder.pop();

        builder.push("storage");
        SCRAP_BIN_CAPACITY = builder
            .comment("How many of its bound material one Scrap Bin holds (P2.9). 4096 = 64 stacks.")
            .defineInRange("scrapBinCapacity", 4096, 64, 1_000_000);
        builder.pop();

        SPEC = builder.build();
    }

    private RCConfig() {
        // utility class
    }
}

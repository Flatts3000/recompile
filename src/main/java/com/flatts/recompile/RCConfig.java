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
    public static final ModConfigSpec.IntValue GRASS_SPREADER_RADIUS;
    public static final ModConfigSpec.IntValue GRASS_SPREADER_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue GRASS_SPREADER_IDLE_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue GRASS_SPREADER_VERTICAL_TOLERANCE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("world");
        GARBAGE_GRAVITY_ENABLED = builder
            .comment("Whether Blocks of Garbage obey gravity (slump when quarried, deorbit on regrowth).")
            .define("garbageGravityEnabled", true);
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
            .defineInRange("grassSpreaderRadius", 12, 1, 64);
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
        builder.pop();

        SPEC = builder.build();
    }

    private RCConfig() {
        // utility class
    }
}

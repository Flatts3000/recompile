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
        builder.pop();

        SPEC = builder.build();
    }

    private RCConfig() {
        // utility class
    }
}

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

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("world");
        GARBAGE_GRAVITY_ENABLED = builder
            .comment("Whether Blocks of Garbage obey gravity (slump when quarried, deorbit on regrowth).")
            .define("garbageGravityEnabled", true);
        builder.pop();

        SPEC = builder.build();
    }

    private RCConfig() {
        // utility class
    }
}

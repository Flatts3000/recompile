package com.flatts.recompile;

import com.flatts.recompile.gametest.RCGameTests;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCCreativeTabs;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Recompile mod entry point.
 *
 * <p>Teardown-as-knowledge: disassemble found items to recover not just their
 * materials but their recipes. Phase 0 wires the data spine (the public
 * {@code recompile:teardown} recipe type) and the config; gameplay systems
 * (worldgen, Blocks of Garbage, sorting, the workbench) land in later phases.
 */
@Mod(Recompile.MOD_ID)
public final class Recompile {

    public static final String MOD_ID = "recompile";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public Recompile(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Recompile initializing");

        // Blocks before Items (the garbage block-item references the block); block
        // entities after blocks; creative tab after items.
        RCBlocks.register(modEventBus);
        RCItems.register(modEventBus);
        RCBlockEntities.register(modEventBus);
        RCCreativeTabs.register(modEventBus);

        // The public data spine (P0.5). Registered from day one so the knowledge
        // axis (P1.4) is never retrofitted into a live schema.
        RCRecipeTypes.register(modEventBus);

        // In-world GameTests (CI gameTest job runs these).
        RCGameTests.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, RCConfig.SPEC);
    }
}

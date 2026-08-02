package com.flatts.recompile.event;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.Recompile;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Keeps water scarce by switching off vanilla's infinite water source (#101).
 *
 * <p><b>The problem, from playtest.</b> Two water buckets placed diagonally breed a third source
 * forever, so the Rain Collector - the machine the whole P1.10 water economy is built around - becomes
 * scenery the moment a player can make buckets. It is not obsolete on day one: a bucket is three iron
 * and iron lives behind the Cupola, so the collector has a real working life. Its life simply *ends* at
 * the demolition yard, and a machine you build and then never look at again is worse than one that was
 * never worth building.
 *
 * <p><b>The lever is vanilla's own.</b> {@code water_source_conversion} has existed as a game rule since
 * 1.19 and defaults to true. Vanilla already ships the lava equivalent defaulting to <b>false</b>, so an
 * asymmetric default is Mojang's choice rather than a law of the universe; this world just makes the
 * same call for water, because here water is the scarce thing.
 *
 * <p><b>Why the config is the override, not the game rule.</b> The rule is re-applied on every load
 * rather than set once at world creation. Setting it once would leave every world that already exists
 * with infinite water, which for a balance fix is the wrong half of the player base - the people already
 * playing are exactly the ones who reported it. A player or pack that wants infinite water back turns
 * <b>the config</b> off, and then the mod stops touching the rule at all. That keeps one switch instead
 * of two disagreeing ones, and it means a rule someone set by hand cannot silently fight the mod.
 *
 * <p><b>Only in garbage worlds.</b> Identified by the noise settings, which every version of the preset
 * has used, rather than by the biome source, which changed in 0.3.0. A vanilla world with this mod
 * installed keeps vanilla water.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID)
public final class RCWaterEconomy {

    /** The noise settings every garbage world has used, v0.1.0 onward. */
    private static final ResourceKey<NoiseGeneratorSettings> GARBAGE_SETTINGS = ResourceKey.create(
        Registries.NOISE_SETTINGS, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "garbage"));

    private RCWaterEconomy() {
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD
                || !RCConfig.DISABLE_INFINITE_WATER.get()
                || !isGarbageWorld(level.getChunkSource().getGenerator())) {
            return;
        }
        // Game rules are per-save, not per-dimension, so the overworld is the only place this needs
        // doing - and doing it in every dimension would just be the same write three times.
        if (level.getGameRules().get(GameRules.WATER_SOURCE_CONVERSION)) {
            level.getGameRules().set(GameRules.WATER_SOURCE_CONVERSION, false, level.getServer());
        }
    }

    /**
     * Is this one of the mod's worlds?
     *
     * <p>Keyed on the noise settings rather than the biome source deliberately: the biome source changed
     * in 0.3.0 when regions shipped, so a check against it would quietly skip every older save. The
     * noise settings have been {@code recompile:garbage} since v0.1.0.
     */
    public static boolean isGarbageWorld(ChunkGenerator generator) {
        return generator instanceof NoiseBasedChunkGenerator noise
            && noise.generatorSettings().is(GARBAGE_SETTINGS);
    }
}

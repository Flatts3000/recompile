package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.worldgen.RegionBiomeSource;
import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom biome sources. Home of {@code recompile:region} - the distance-gradient source that gives the
 * world its guaranteed safe household core and its noise-filled frontier (see
 * {@link RegionBiomeSource} and {@code docs/demolition_yard_spec.md}). Registered into the vanilla
 * {@link Registries#BIOME_SOURCE} registry (which holds the {@code MapCodec} per source type), the same
 * pattern {@link RCRecipeTypes} uses for recipe types.
 */
public final class RCBiomeSources {

    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
        DeferredRegister.create(Registries.BIOME_SOURCE, Recompile.MOD_ID);

    public static final Supplier<MapCodec<RegionBiomeSource>> REGION =
        BIOME_SOURCES.register("region", () -> RegionBiomeSource.CODEC);

    private RCBiomeSources() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        BIOME_SOURCES.register(modEventBus);
    }
}

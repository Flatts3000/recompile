package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.worldgen.MoundFeature;
import com.flatts.recompile.content.worldgen.MyceliumPatchFeature;
import com.flatts.recompile.content.worldgen.RubblePileFeature;
import com.flatts.recompile.content.worldgen.FallenGirdersFeature;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Worldgen feature registry. First entry is the garbage mound (design P0.2). */
public final class RCFeatures {

    public static final DeferredRegister<Feature<?>> FEATURES =
        DeferredRegister.create(Registries.FEATURE, Recompile.MOD_ID);

    public static final Supplier<Feature<NoneFeatureConfiguration>> GARBAGE_MOUND =
        FEATURES.register("garbage_mound", MoundFeature::new);

    /** Forageable mycelium patches with dump mushrooms (design P1.9). */
    public static final Supplier<Feature<NoneFeatureConfiguration>> MYCELIUM_PATCH =
        FEATURES.register("mycelium_patch", MyceliumPatchFeature::new);

    /** Low piles of Rubble in the demolition yard - the bare-hand stone-shard source. */
    public static final Supplier<Feature<NoneFeatureConfiguration>> RUBBLE_PILE =
        FEATURES.register("rubble_pile", RubblePileFeature::new);

    /** Girders down where a frame came apart: the survival source of beams AND reinforced concrete. */
    public static final Supplier<Feature<NoneFeatureConfiguration>> FALLEN_GIRDERS =
        FEATURES.register("fallen_girders", FallenGirdersFeature::new);

    private RCFeatures() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}

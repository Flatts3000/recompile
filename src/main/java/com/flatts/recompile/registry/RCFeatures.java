package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.worldgen.MoundFeature;
import com.flatts.recompile.content.worldgen.MyceliumPatchFeature;
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

    private RCFeatures() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}

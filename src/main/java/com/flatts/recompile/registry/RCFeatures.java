package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.worldgen.LeachatePoolFeature;
import com.flatts.recompile.content.worldgen.MechanicalWastePileFeature;
import com.flatts.recompile.content.worldgen.MoundFeature;
import com.flatts.recompile.content.worldgen.MyceliumPatchFeature;
import com.flatts.recompile.content.worldgen.RubblePileFeature;
import com.flatts.recompile.content.worldgen.TailingsHeapFeature;
import com.flatts.recompile.content.worldgen.BuildingHuskFeature;
import com.flatts.recompile.content.worldgen.SteelStackFeature;
import com.flatts.recompile.content.worldgen.TirePileFeature;
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

    /** The radioactive dump's scatter: a tailings heap, its stain, and sometimes a drum (#285). */
    public static final Supplier<Feature<NoneFeatureConfiguration>> TAILINGS_HEAP =
        FEATURES.register("tailings_heap", TailingsHeapFeature::new);

    /** Heaps of Mechanical Waste in the yard - the gem tier's found half (docs/gem_tier_spec.md). */
    public static final Supplier<Feature<NoneFeatureConfiguration>> MECHANICAL_WASTE_PILE =
        FEATURES.register("mechanical_waste_pile", MechanicalWastePileFeature::new);

    /** The demolition yard's landmark: a steel frame stripped to its skeleton (#49). */
    public static final Supplier<Feature<NoneFeatureConfiguration>> BUILDING_HUSK =
        FEATURES.register("building_husk", BuildingHuskFeature::new);

    /** Stacked salvage steel: the survival source of beams, reinforced concrete and copper pipe. */
    public static final Supplier<Feature<NoneFeatureConfiguration>> STEEL_STACK =
        FEATURES.register("steel_stack", SteelStackFeature::new);

    /** Clustered tire dumps in the household sprawl (#155) - the only rubber in the game. */
    public static final Supplier<Feature<NoneFeatureConfiguration>> TIRE_PILE =
        FEATURES.register("tire_pile", TirePileFeature::new);

    /** Sparse pools of leachate in the sprawl and the yard (#156) - the dump's own runoff. */
    public static final Supplier<Feature<NoneFeatureConfiguration>> LEACHATE_POOL =
        FEATURES.register("leachate_pool", LeachatePoolFeature::new);

    private RCFeatures() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}

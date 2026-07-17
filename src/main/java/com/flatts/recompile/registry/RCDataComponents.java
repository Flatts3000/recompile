package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item data components. Currently one: the water a broken Rain Collector carries on its dropped
 * item, so the tank survives break + replace instead of being emptied every time you move it.
 */
public final class RCDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Recompile.MOD_ID);

    /** Stored water in mB, carried on a broken Rain Collector item (see RainCollectorBlockEntity). */
    public static final Supplier<DataComponentType<Integer>> RAIN_WATER =
        DATA_COMPONENTS.register("rain_water",
            () -> DataComponentType.<Integer>builder()
                .persistent(Codec.INT)
                .networkSynchronized(ByteBufCodecs.VAR_INT)
                .build());

    private RCDataComponents() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}

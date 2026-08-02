package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.ScrapBinContents;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
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

    /**
     * The Cutting Torch's remaining charge, in cuts (see CuttingTorchItem). Absent means "never used",
     * which reads as one rag's worth, because the torch's recipe already spends a rag.
     */
    public static final Supplier<DataComponentType<Integer>> TORCH_FUEL =
        DATA_COMPONENTS.register("torch_fuel",
            () -> DataComponentType.<Integer>builder()
                .persistent(Codec.INT)
                .networkSynchronized(ByteBufCodecs.VAR_INT)
                .build());

    /** A filled Scrap Bin's {material, count}, carried on its dropped item (P2.9). */
    public static final Supplier<DataComponentType<ScrapBinContents>> SCRAP_BIN_CONTENTS =
        DATA_COMPONENTS.register("scrap_bin_contents",
            () -> DataComponentType.<ScrapBinContents>builder()
                .persistent(ScrapBinContents.CODEC)
                .networkSynchronized(ScrapBinContents.STREAM_CODEC)
                .build());

    /**
     * Which blueprint a Blueprint item is (#95, spec {@code docs/blueprints_spec.md}).
     *
     * <p><b>The item is the knowledge.</b> This component is the whole of it - there is no per-player
     * save data anywhere in this mod, and knowledge is not going to be the first. A blueprint can be
     * held, lost, dropped in lava, or handed to someone else, which is the Immersive Engineering model
     * and the reason it was picked.
     *
     * <p>An {@link Identifier} naming a blueprint <b>set</b>, not a recipe. One blueprint may unlock
     * several recipes; a recipe names the set it needs. A stack with no component, or one naming a set
     * nothing uses, is inert rather than broken - a datapack that removes a recipe must not turn every
     * blueprint a player is carrying into a crash.
     */
    public static final Supplier<DataComponentType<Identifier>> BLUEPRINT =
        DATA_COMPONENTS.register("blueprint",
            () -> DataComponentType.<Identifier>builder()
                .persistent(Identifier.CODEC)
                .networkSynchronized(Identifier.STREAM_CODEC)
                .build());

    private RCDataComponents() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}

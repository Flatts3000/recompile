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
 * Item data components: state that has to survive a block being broken and put back, since
 * {@code saveAdditional} covers save/load and nothing else.
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
     * Stored water in mB, carried on a broken Water Tank item (#229, see WaterTankBlockEntity).
     *
     * <p>Its own component rather than a second use of {@link #RAIN_WATER}, because the two mean
     * different things to a reader and to a datapack: rain water is what a collector caught from the
     * sky, and this is whatever somebody poured in. Sharing the id would make a tank's contents read
     * as rainfall in every tooltip and loot function that names it.
     */
    public static final Supplier<DataComponentType<Integer>> TANK_WATER =
        DATA_COMPONENTS.register("tank_water",
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
    /**
     * Which creature is trapped in a piece of Amber (#294), as an entity-type {@link Identifier}.
     *
     * <p><b>Stamped when the amber is found, not when it is read.</b> The alternative was the fridge's
     * component lottery - a teardown pool that draws the outcome and teaches whatever it drew - and it
     * is cheaper, because it is pure data. It was not taken: a stamped amber is a thing a player can
     * look at, sort, hoard and trade, and an unstamped one is a lottery ticket. Four fragments of the
     * SAME species make an egg, so knowing which ones you have is most of the mechanic.
     *
     * <p>An {@link Identifier} rather than a {@code Holder<EntityType<?>>} because a component has to
     * round-trip through a loot table, and a datapack naming an entity from a mod that is not
     * installed must not take the loot table down at parse. An id that resolves to nothing is handled
     * where it is read instead.
     */
    public static final Supplier<DataComponentType<Identifier>> SPECIES =
        DATA_COMPONENTS.register("species",
            () -> DataComponentType.<Identifier>builder()
                .persistent(Identifier.CODEC)
                .networkSynchronized(Identifier.STREAM_CODEC)
                .build());

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

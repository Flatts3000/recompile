package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.SortingTarpBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block-entity registry. The Sorting Tarp holds an inventory + sort progress (P1.3). */
public final class RCBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Recompile.MOD_ID);

    public static final Supplier<BlockEntityType<SortingTarpBlockEntity>> SORTING_TARP =
        BLOCK_ENTITIES.register(
            "sorting_tarp",
            () -> new BlockEntityType<>(SortingTarpBlockEntity::new, RCBlocks.SORTING_TARP.get())
        );

    private RCBlockEntities() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}

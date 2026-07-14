package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.GarbageBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block-entity registry. First entry backs the garbage block's sort progress (P0.4). */
public final class RCBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Recompile.MOD_ID);

    public static final Supplier<BlockEntityType<GarbageBlockEntity>> GARBAGE_BLOCK =
        BLOCK_ENTITIES.register(
            "garbage_block",
            () -> new BlockEntityType<>(GarbageBlockEntity::new, RCBlocks.GARBAGE_BLOCK.get())
        );

    private RCBlockEntities() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}

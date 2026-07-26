package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.CompostHeapBlockEntity;
import com.flatts.recompile.content.block.multiblock.MultiblockDummyBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Jade (server data): the Compost Heap's layer state lives in the core's BlockEntity and is not synced
 * (the cells only carry a coarse per-cell band count), so send the real totals to the client on hover
 * for {@link CompostHeapProvider} to render.
 *
 * <p>Resolves the core from whichever cell is hovered - hovering any cage face should read the heap,
 * not just the one core cell - via {@link MultiblockDummyBlock#findCore}.
 *
 * <p>Separate from the client component on purpose: since MC 1.21.6 one class may not be both an
 * {@code IComponentProvider} and an {@code IServerDataProvider}.
 */
public enum CompostHeapDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "compost_heap_data");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        CompostHeapBlockEntity heap = resolve(accessor);
        if (heap != null) {
            data.putInt("layers", heap.layers());
            data.putInt("max", heap.maxLayers());
            data.putInt("ready", heap.readyLayers());
        }
    }

    /** The core's BE, whether the core itself or one of its cage cells was hovered. */
    private static @Nullable CompostHeapBlockEntity resolve(BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof CompostHeapBlockEntity be) {
            return be;
        }
        BlockPos core = MultiblockDummyBlock.findCore(accessor.getLevel(), accessor.getPosition());
        if (core != null) {
            BlockEntity be = accessor.getLevel().getBlockEntity(core);
            if (be instanceof CompostHeapBlockEntity heap) {
                return heap;
            }
        }
        return null;
    }

    @Override
    public boolean shouldRequestData(BlockAccessor accessor) {
        return true;
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}

package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.TreeNurseryBlockEntity;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.content.block.multiblock.MultiblockDummyBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Jade (server data): the Tree Nursery's state lives in the core's BlockEntity and only partly syncs
 * (the ACTIVE blockstate), so send the real figures to the client on hover for {@link TreeNurseryProvider}
 * to render. Resolves the core from whichever cell is hovered (core or the clad tank), via
 * {@link MultiblockDummyBlock#findCore}.
 */
public enum TreeNurseryDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "tree_nursery_data");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        TreeNurseryBlockEntity nursery = resolve(accessor);
        if (nursery == null || !MultiblockCoreBlock.isFormed(nursery.getBlockState())) {
            return;
        }
        data.putInt("water", nursery.waterStored());
        data.putInt("waterCap", nursery.waterCapacity());
        data.putInt("cook", nursery.cookProgress());
        data.putInt("cookTotal", nursery.cookTotal());
        data.putInt("species", nursery.selectedSpecies());
        data.putInt("fert", nursery.getItem(TreeNurseryBlockEntity.SLOT_FERTILIZER).getCount());
        data.putInt("seed", nursery.getItem(TreeNurseryBlockEntity.SLOT_SEEDLING).getCount());
    }

    /** The core's BE, whether the core itself or its clad tank cell was hovered. */
    private static @Nullable TreeNurseryBlockEntity resolve(BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof TreeNurseryBlockEntity be) {
            return be;
        }
        BlockPos core = MultiblockDummyBlock.findCore(accessor.getLevel(), accessor.getPosition());
        if (core != null && accessor.getLevel().getBlockEntity(core) instanceof TreeNurseryBlockEntity be) {
            return be;
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

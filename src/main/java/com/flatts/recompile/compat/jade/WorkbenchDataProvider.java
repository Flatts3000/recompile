package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.RecompileWorkbenchBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Jade (server data): the racked tools' durability lives in the server-side BlockEntity and is not
 * otherwise synced, so send it to the client on hover for {@link WorkbenchHintProvider} to render.
 *
 * <p>Split out from the client component on purpose: since MC 1.21.6 Jade forbids one class being
 * both an {@code IComponentProvider} and an {@code IServerDataProvider}.
 */
public enum WorkbenchDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "workbench_data");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof RecompileWorkbenchBlockEntity workbench) {
            writeTool(data, "knife", workbench.getTool(RecompileWorkbenchBlockEntity.KNIFE_SLOT));
            writeTool(data, "prybar", workbench.getTool(RecompileWorkbenchBlockEntity.PRYBAR_SLOT));
        }
    }

    @Override
    public boolean shouldRequestData(BlockAccessor accessor) {
        return true;
    }

    private static void writeTool(CompoundTag data, String key, ItemStack tool) {
        if (!tool.isEmpty() && tool.isDamageableItem()) {
            data.putInt(key + "_rem", tool.getMaxDamage() - tool.getDamageValue());
            data.putInt(key + "_max", tool.getMaxDamage());
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}

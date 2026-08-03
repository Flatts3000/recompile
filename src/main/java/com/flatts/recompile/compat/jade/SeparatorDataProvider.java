package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.SeparatorBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Jade (server data) for the Separator ({@code docs/gem_tier_spec.md}).
 *
 * <p>The machine has <b>no screen at all</b> - its whole interaction is dropping things in the top - so
 * without this its power buffer and its progress are entirely invisible. The one blockstate it syncs
 * says "running", which is exactly the case a player does not need help with.
 *
 * <p>Separate class from the component provider because since MC 1.21.6 one class may not be both.
 */
public enum SeparatorDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "separator_data");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof SeparatorBlockEntity separator) {
            data.putInt("stored", separator.battery().getAmountAsInt());
            data.putInt("capacity", separator.battery().getCapacityAsInt());
            data.putInt("progress", separator.progress());
            data.putInt("goal", separator.goal());
            data.putInt("have", separator.feedHave());
            data.putInt("need", separator.feedNeed());
            data.putInt("queued", separator.queuedCount());
            data.putInt("slots",
                com.flatts.recompile.content.block.entity.SeparatorBlockEntity.QUEUE_SLOTS);
            int kinds = 0;
            for (net.minecraft.world.item.ItemStack stack : separator.queued()) {
                if (!stack.isEmpty()) {
                    kinds++;
                }
            }
            data.putInt("kinds", kinds);
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}

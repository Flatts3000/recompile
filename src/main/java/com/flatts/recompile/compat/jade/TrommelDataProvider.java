package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.TrommelBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Jade (server data) for the Trommel, on the Separator's terms (#188).
 *
 * <p>The two machines have the same shape of problem and should not answer it differently: no screen,
 * an internal queue nothing can open, and a power buffer with no other readout. A player who has built
 * both should not have to learn two ways of asking what a machine is doing.
 *
 * <p>Separate class from the component provider because since MC 1.21.6 one class may not be both.
 */
public enum TrommelDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "trommel_data");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof TrommelBlockEntity trommel) {
            data.putInt("stored", trommel.battery().getAmountAsInt());
            data.putInt("capacity", trommel.battery().getCapacityAsInt());
            data.putInt("progress", trommel.progress());
            data.putInt("goal", trommel.goal());
            data.putInt("queued", trommel.queuedCount());
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}

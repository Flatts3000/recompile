package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.PulverizerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Jade (server data) for the Pulverizer, on the Separator's terms (#189).
 *
 * <p>The two machines have the same shape of problem and must not answer it differently: no screen,
 * an internal queue nothing can open, and a power buffer with no other readout. A player who has built
 * both should not have to learn two ways of asking what a machine is doing.
 *
 * <p>Separate class from the component provider because since MC 1.21.6 one class may not be both.
 */
public enum PulverizerDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "pulverizer_data");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof PulverizerBlockEntity pulverizer) {
            data.putInt("stored", pulverizer.battery().getAmountAsInt());
            data.putInt("capacity", pulverizer.battery().getCapacityAsInt());
            data.putInt("progress", pulverizer.progress());
            data.putInt("goal", pulverizer.goal());
            data.putInt("queued", pulverizer.queuedCount());
            // The feed ratio, which the Separator also sends and the Trommel has no use for:
            // a pulverizing recipe may want several of its input, so "3 of 8" is a real answer
            // to "why is it not running".
            data.putInt("have", pulverizer.feedHave());
            data.putInt("need", pulverizer.feedNeed());
            data.putInt("draw", pulverizer.drawPerTick());
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}

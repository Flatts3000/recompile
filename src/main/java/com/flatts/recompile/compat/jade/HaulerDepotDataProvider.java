package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.HaulerDepotBlockEntity;
import com.flatts.recompile.content.item.ScrapHaulerItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Jade (server data) for the Hauler Depot (#376).
 *
 * <p>Writes the keys {@link GeneratorProvider} already renders - the buffer as a consumer, and the
 * docked Hauler's own gauge as {@code held_*}, the Charging Station's shape - plus what
 * {@link HaulerDepotProvider} needs to say where the Hauler is. Two classes because since 1.21.6 one
 * may not be both a data provider and a component provider.
 */
public enum HaulerDepotDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "hauler_depot_data");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof HaulerDepotBlockEntity depot)) {
            return;
        }
        data.putBoolean("consumer", true);
        data.putInt("stored", depot.stored());
        data.putInt("capacity", HaulerDepotBlockEntity.CAPACITY);
        data.putInt("rate", 0);
        ItemStack hauler = depot.hauler();
        boolean has = hauler.getItem() instanceof ScrapHaulerItem;
        data.putBoolean("docked", has && !depot.deployed());
        if (has) {
            data.putInt("held_stored", depot.deployed() ? depot.fieldCharge() : ScrapHaulerItem.charge(hauler));
            data.putInt("held_capacity", ScrapHaulerItem.CAPACITY);
        }
        data.putBoolean("has_hauler", has);
        data.putBoolean("deployed", depot.deployed());
        data.putInt("mode", depot.fieldMode());
        data.putInt("cargo", depot.fieldCargo());
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}

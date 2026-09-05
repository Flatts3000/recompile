package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.entity.ScrapHaulerEntity;
import com.flatts.recompile.content.item.ScrapHaulerItem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade for a deployed Scrap Hauler (#376): charge, cargo, and what it is doing. Everything it shows
 * is synced entity data, so no server data provider is needed - the painting's shape.
 */
public enum ScrapHaulerProvider implements IEntityComponentProvider {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "scrap_hauler");

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof ScrapHaulerEntity hauler)) {
            return;
        }
        tooltip.add(Component.translatable("jade.recompile.energy_stored",
            String.format("%,d", hauler.charge()), String.format("%,d", ScrapHaulerItem.CAPACITY)));
        tooltip.add(Component.translatable(modeKey(hauler.mode()),
            hauler.cargoCount(), ScrapHaulerEntity.CARGO_CAPACITY));
    }

    static String modeKey(ScrapHaulerEntity.Mode mode) {
        return switch (mode) {
            case SEEKING -> "jade.recompile.hauler.seeking";
            case RETURNING -> "jade.recompile.hauler.returning";
            case DUMPING, WAITING_DEPOT -> "jade.recompile.hauler.dumping";
            case PARKED_FLAT -> "jade.recompile.hauler.flat";
            case PARKED_IDLE -> "jade.recompile.hauler.idle";
        };
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}

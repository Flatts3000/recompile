package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.entity.ScrapHaulerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Jade for the Hauler Depot (#376): one line saying where its Hauler is and what it is doing. */
public enum HaulerDepotProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "hauler_depot");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null || !data.contains("has_hauler")) {
            return;
        }
        if (!data.getBooleanOr("has_hauler", false)) {
            tooltip.add(Component.translatable("jade.recompile.hauler.none"));
            return;
        }
        if (!data.getBooleanOr("deployed", false)) {
            tooltip.add(Component.translatable("jade.recompile.hauler.docked"));
            return;
        }
        tooltip.add(Component.translatable(ScrapHaulerProvider.modeKey(
            ScrapHaulerEntity.Mode.of(data.getIntOr("mode", 0))),
            data.getIntOr("cargo", 0), ScrapHaulerEntity.CARGO_CAPACITY));
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}

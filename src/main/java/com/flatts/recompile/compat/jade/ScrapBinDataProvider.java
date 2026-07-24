package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Jade (server data): a Scrap Bin's exact count and capacity live in the server-side BlockEntity and
 * are not synced (only the coarse {@code content} / {@code fill} blockstates are), so send them to the
 * client on hover for {@link ScrapBinProvider} to render as "N / capacity". The bound material id
 * rides along too, so the tooltip can name the exact item even for a modded (GENERIC) binding the
 * blockstate cannot identify.
 *
 * <p>Separate from the client component on purpose: since MC 1.21.6 one class may not be both an
 * {@code IComponentProvider} and an {@code IServerDataProvider}.
 */
public enum ScrapBinDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "scrap_bin_data");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ScrapBinBlockEntity bin) {
            data.putInt("amount", bin.amount());
            data.putInt("capacity", bin.capacityForDisplay());
            if (bin.boundMaterial() != null) {
                data.putString("material", BuiltInRegistries.ITEM.getKey(bin.boundMaterial()).toString());
            }
        }
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

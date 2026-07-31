package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.BurnerGeneratorBlockEntity;
import com.flatts.recompile.content.block.entity.SolarPanelBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Jade (server data) for the power tier (#72).
 *
 * <p>A generator's buffer lives entirely in the server-side BlockEntity and is not synced - the Burner's
 * {@code lit} blockstate is the only thing that crosses on its own, and that says "running", not "how
 * much". Without this, the two machines that produce the mod's only resource with no visible units would
 * be completely opaque.
 *
 * <p>The panel's <b>current rate</b> rides along rather than being recomputed client-side: it depends on
 * {@code getSkyDarken} and on sky exposure, and the client's view of both can lag the server's. Sending it
 * means the tooltip and the actual generation can never disagree.
 *
 * <p>Separate class from the component provider because since MC 1.21.6 one class may not be both.
 */
public enum GeneratorDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "generator_data");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof SolarPanelBlockEntity panel) {
            data.putInt("stored", panel.stored());
            data.putInt("capacity", SolarPanelBlockEntity.CAPACITY);
            data.putInt("rate", SolarPanelBlockEntity.outputAt(accessor.getLevel(), accessor.getPosition()));
        } else if (accessor.getBlockEntity() instanceof BurnerGeneratorBlockEntity generator) {
            data.putInt("stored", generator.stored());
            data.putInt("capacity", BurnerGeneratorBlockEntity.CAPACITY);
            data.putInt("rate", generator.isLit() ? BurnerGeneratorBlockEntity.FE_PER_TICK : 0);
            data.putInt("burn", generator.burnTime());
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

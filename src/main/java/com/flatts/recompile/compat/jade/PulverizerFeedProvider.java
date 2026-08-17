package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.PulverizerCoreBlock;
import com.flatts.recompile.content.block.PulverizerPartBlock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade: how to put something INTO a Pulverizer.
 *
 * <p>The machine has no screen and no slots on purpose, and nothing can push into it - so a player who
 * has just finished building one is looking at a sealed object with no visible way in. Formation is
 * already reported by {@link MachineStatusProvider}; "Running" on a machine you cannot feed is the
 * least useful true sentence available.
 *
 * <p>It says both routes because they serve different players: throwing scrap at the drum is what you
 * do the first time, and parking a container on it is what you do once you want it unattended. The
 * second is also the one nobody guesses, since every other automatable block in the game is fed by
 * pushing into it rather than by being drained.
 *
 * <p>Shown on the drum cells as well as the core. The drum is where the opening is and where a player
 * looks when they are wondering where the opening is; the core is the block they placed.
 */
public enum PulverizerFeedProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "pulverizer_feed");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getBlock() instanceof PulverizerCoreBlock
                && !MultiblockCoreBlock.isFormed(accessor.getBlockState())) {
            // Still a pile of parts. MachineStatusProvider is naming what is missing, and telling
            // someone how to feed a machine that does not exist yet is noise on top of the real answer.
            return;
        }
        if (accessor.getBlock() instanceof PulverizerCoreBlock
                || accessor.getBlock() instanceof PulverizerPartBlock) {
            tooltip.add(Component.translatable("jade.recompile.pulverizer_feed"));
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}

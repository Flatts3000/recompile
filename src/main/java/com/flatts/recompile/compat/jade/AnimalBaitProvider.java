package com.flatts.recompile.compat.jade;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.AnimalBaitBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade (client): every gate on an animal bait is an invisible failure mode ("I placed it and nothing
 * happened"), so name the exact blocker on hover - no grass, a player too near (settling is held), too
 * close to another bait, or the settle countdown - plus what the surrounding land is drawing. All of it
 * is read client-side from the blockstate and the world, so no server data provider is needed.
 */
public enum AnimalBaitProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "animal_bait");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockState state = accessor.getBlockState();
        if (!(state.getBlock() instanceof AnimalBaitBlock)) {
            return;
        }
        Level level = accessor.getLevel();
        BlockPos pos = accessor.getPosition();

        if (!AnimalBaitBlock.onGrass(level, pos)) {
            tooltip.add(Component.translatable("jade.recompile.bait_no_grass"));
            return;
        }
        if (AnimalBaitBlock.playerNear(level, pos)) {
            tooltip.add(Component.translatable("jade.recompile.bait_waiting"));
        } else if (AnimalBaitBlock.baitNear(level, pos)) {
            tooltip.add(Component.translatable("jade.recompile.bait_crowded"));
        } else {
            int settle = AnimalBaitBlock.settle(state);
            if (settle >= AnimalBaitBlock.SETTLE_MAX) {
                tooltip.add(Component.translatable("jade.recompile.bait_ready"));
            } else {
                int seconds = (AnimalBaitBlock.SETTLE_MAX - settle)
                    * RCConfig.ANIMAL_BAIT_SETTLE_INTERVAL_TICKS.get() / 20;
                tooltip.add(Component.translatable("jade.recompile.bait_settling", seconds));
            }
        }

        EntityType<?> likely = AnimalBaitBlock.mostLikely(level, pos, state.getValue(AnimalBaitBlock.DIET));
        if (likely != null) {
            tooltip.add(Component.translatable("jade.recompile.bait_expecting", likely.getDescription()));
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}

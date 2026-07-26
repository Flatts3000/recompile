package com.flatts.recompile.content.item;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.event.FertilizerScatter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fertilizer (Compost Heap output): a surface-aware "fancy bonemeal" (Vegetation tier, rung 2). Right-
 * click grass to scatter dump weeds and wildflowers, or mycelium to scatter mushrooms; the scatter
 * ripples outward over a few seconds ({@link FertilizerScatter}). No-op on any other surface, so it
 * cannot skip the Grass Spreader rung and it does not waste on the raw dump.
 */
public class FertilizerItem extends Item {

    public FertilizerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState clicked = level.getBlockState(pos);
        boolean grass = clicked.is(Blocks.GRASS_BLOCK);
        boolean mycelium = clicked.is(Blocks.MYCELIUM);
        if ((!grass && !mycelium) || !RCConfig.VEGETATION_ENABLED.get()) {
            return InteractionResult.PASS;   // no scatter, no consume
        }
        if (level instanceof ServerLevel server) {
            FertilizerScatter.schedule(server, pos, grass);
            level.levelEvent(1505, pos, 0);   // immediate bonemeal sparkle at the click
            context.getItemInHand().consume(1, context.getPlayer());
        }
        return InteractionResult.SUCCESS;
    }
}

package com.flatts.recompile.content.item;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.event.FertilizerScatter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BoneMealItem;
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
            return growLikeBoneMeal(context, level, pos);
        }
        if (level instanceof ServerLevel server) {
            FertilizerScatter.schedule(server, pos, grass);
            level.levelEvent(1505, pos, 0);   // immediate bonemeal sparkle at the click
            context.getItemInHand().consume(1, context.getPlayer());
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Everything that is not grass or mycelium falls through to vanilla's bone-meal path (#71).
     *
     * <p><b>This world has no bone meal and cannot have any.</b> Bone meal comes from skeletons and the
     * starting biome is deliberately creature-free (P1.9), so without this there is no way for a player
     * to hurry a crop or a tree along at all - everything grows on random ticks and you wait. Fertilizer
     * is the obvious stand-in: it already exists, it already comes from a machine you built and feed,
     * and it is already the item that means "growth" here.
     *
     * <p><b>Order is load-bearing.</b> Grass is itself a {@link net.minecraft.world.level.block.BonemealableBlock},
     * so this fallback has to sit AFTER the grass and mycelium branch. Reached first, it would quietly
     * replace the mod's rippling weeds-and-wildflowers scatter with vanilla's flower burst, and the
     * Vegetation tier would look like plain bone meal. Nothing about that would throw, which is why
     * {@code fertilizer_on_grass_still_uses_the_mod_scatter} exists.
     *
     * <p>{@code applyBonemeal} is vanilla's own, which means it fires NeoForge's bonemeal event, honours
     * {@code isValidBonemealTarget}, and shrinks the stack. Any modded crop that works with bone meal
     * works with this for free, and nothing here has to know what a crop is.
     *
     * <p>Strength is deliberately 1:1 with bone meal for now. Fertilizer is machine-made and scarcer
     * than bones ever are in vanilla, so the ratio is probably wrong - but that is a number, and numbers
     * belong to the pre-beta balance pass (#36), which is where the issue put it.
     */
    private InteractionResult growLikeBoneMeal(UseOnContext context, Level level, BlockPos pos) {
        if (!RCConfig.FERTILIZER_GROWTH_ENABLED.get()) {
            return InteractionResult.PASS;   // no growth, no consume
        }
        if (!BoneMealItem.applyBonemeal(context.getItemInHand(), level, pos, context.getPlayer())) {
            return InteractionResult.PASS;   // not a growable target: no sparkle, no consume
        }
        if (level instanceof ServerLevel server) {
            BoneMealItem.addGrowthParticles(server, pos, 0);
            server.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }
}

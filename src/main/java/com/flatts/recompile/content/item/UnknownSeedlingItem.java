package com.flatts.recompile.content.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The Unknown Seedling (Farming tier): a compost "volunteer" - a seed that came up in the pile from the
 * kitchen scraps you fed it. You plant it on farmland like any seed, but you don't know what it is: at
 * <b>plant time</b> it resolves to a random vanilla crop and grows as that crop. Wheat is left out of the
 * pool (grass already drops wheat seeds), so a volunteer is always a crop you can't otherwise get - the
 * bootstrap into farming, after which you replant that crop's own seed deterministically.
 */
public class UnknownSeedlingItem extends Item {

    /** What a volunteer can turn out to be. Carrots/potatoes plant as themselves; melon/pumpkin as stems. */
    private static final Block[] CROPS = {
        Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS, Blocks.MELON_STEM, Blocks.PUMPKIN_STEM
    };

    public UnknownSeedlingItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos plantPos = context.getClickedPos().relative(context.getClickedFace());
        // All crops need farmland below and an empty cell; validate once with a representative crop so the
        // random pick can happen server-only (picking on both sides would desync the placed crop).
        if (!level.getBlockState(plantPos).canBeReplaced()
                || !Blocks.WHEAT.defaultBlockState().canSurvive(level, plantPos)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            Block crop = CROPS[server.getRandom().nextInt(CROPS.length)];
            level.setBlock(plantPos, crop.defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, plantPos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
            context.getItemInHand().consume(1, context.getPlayer());
        }
        return InteractionResult.SUCCESS;
    }
}

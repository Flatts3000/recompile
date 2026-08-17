package com.flatts.recompile.content.block;

import com.flatts.recompile.registry.RCItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The way into a sewer (#90 phase 1): a scrap plate over a hole in the demolition yard.
 *
 * <p><b>It is the Bulky Waste loop exactly</b> - right-click with a prybar and it comes off, right-click
 * without one and it tells you what you need. That reuse is the point rather than a shortcut: a player
 * who has reached the yard already knows what a prybar is for, so the sewer needs no new verb to teach.
 * The prybar is the only thing that opens it - the block is <b>unbreakable</b>, because
 * {@code requiresCorrectToolForDrops} gates a drop and the reward here is the shaft rather than a
 * drop. With hardness alone, fifteen seconds of bare-handed mining achieved exactly what prying
 * achieves and the tool gate was decorative.
 *
 * <p><b>Why it is scrap steel and not a cast-iron cover.</b> A municipal manhole cover would be the
 * obvious art and the wrong object for this world - nothing here is municipal. It is a rusted, pitted,
 * bolted plate laid over a shaft, and it has its own generated texture, approved by the owner rather
 * than assumed: shipping an unapproved surface is how {@code mound_ground} reached a release with no
 * approval record.
 *
 * <p>Prying leaves <b>air</b>, not an "open manhole" block. The shaft below is already built by the
 * structure, so the cover's whole job is to be in the way until it is not; a second block state would be
 * two things to model, light and test for no gain.
 */
public class ManholeBlock extends Block {

    public static final MapCodec<ManholeBlock> CODEC = simpleCodec(ManholeBlock::new);

    public ManholeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends ManholeBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(RCItems.PRYBAR.get())) {
            if (level instanceof ServerLevel serverLevel) {
                pryOpen(serverLevel, pos);
            }
            return InteractionResult.SUCCESS;
        }
        // Wrong item in hand: fall through to the empty-hand path so it still nudges.
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.sendSystemMessage(
                Component.translatable("message.recompile.needs_tool",
                    Component.translatable(RCItems.PRYBAR.get().getDescriptionId())));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Take the cover off, leaving the shaft open. The single entry point interactions and GameTests
     * share, matching the {@code sortOnce} / {@code pryOpen} convention the rest of the mod uses so a
     * test never has to simulate a click.
     */
    public static void pryOpen(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockState(pos).getBlock() instanceof ManholeBlock)) {
            return;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN,
            net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 0.7F);
    }
}

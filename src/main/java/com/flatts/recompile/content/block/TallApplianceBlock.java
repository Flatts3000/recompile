package com.flatts.recompile.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * A discarded appliance that stands two blocks tall - the fridge, and whatever follows it.
 *
 * <p><b>Tall because the object is.</b> {@link FoundApplianceBlock} covers the box-shaped finds, and
 * a fridge crammed into one cube reads as a filing cabinet. The Dirty Mattress already proved a find
 * may occupy two blocks; this is the same idea stood on end.
 *
 * <p><b>The halves are kept together by state validation, not by breaking each other.</b> This is the
 * trap {@code MultiblockCoreBlock.disband} paid for: in 26.1 the removal hook fires on a plain
 * {@code setBlock}-to-AIR as well as on a real break, so a half that destroys its partner re-enters
 * the partner's hook and they ping-pong - and any drop in that path multiplies. So neither half ever
 * touches the other. Each simply declares itself unable to survive without its partner and returns
 * AIR from the neighbour-update hook, which is how vanilla doors and tall flowers do it: the game
 * removes the orphan itself, with no recursion and no second drop.
 *
 * <p><b>Only the lower half drops.</b> The loot table is gated on {@code half=lower}, so breaking
 * either block yields exactly one appliance rather than two.
 */
public class TallApplianceBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<TallApplianceBlock> CODEC = simpleCodec(TallApplianceBlock::new);

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public TallApplianceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends TallApplianceBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    /**
     * Front toward the player, and refuse the placement outright if the head has nowhere to go.
     *
     * <p>Returning null is what makes the item stay in hand rather than placing a lower half with no
     * upper - a half-appliance that would then delete itself on the next block update, which looks
     * exactly like the game eating the item.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (pos.getY() >= context.getLevel().getMaxY()
                || !context.getLevel().getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /** A half survives only while its partner is there. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos partner = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        BlockState other = level.getBlockState(partner);
        return other.getBlock() == this && other.getValue(HALF) != state.getValue(HALF);
    }

    /**
     * Become air when the partner goes, rather than breaking it.
     *
     * <p>The whole no-recursion argument lives here: this returns a state, it does not modify the
     * world, so there is no hook to re-enter.
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction direction, BlockPos neighbourPos, BlockState neighbourState,
            RandomSource random) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }
}

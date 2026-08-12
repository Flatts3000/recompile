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
import net.minecraft.world.entity.player.Player;
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
 * <p><b>Neither half ever breaks the other.</b> This is the trap {@code MultiblockCoreBlock.disband}
 * paid for: in 26.1 the removal hook fires on a plain {@code setBlock}-to-AIR as well as on a real
 * break, so a half that destroys its partner re-enters the partner's hook and they ping-pong - and
 * any drop in that path multiplies. Instead an orphaned half returns AIR from the neighbour-update
 * hook and the game removes it, with no recursion and no second drop. Vanilla doors and tall flowers
 * work the same way.
 *
 * <p><b>The orphan check lives in {@code updateShape} and NOT in {@code canSurvive}</b>, and that is
 * load-bearing rather than stylistic - putting it in {@code canSurvive} made the block impossible to
 * place at all. See the note on that method; it is the one thing to know before writing a second
 * tall block.
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

    /**
     * Creative-break the upper half without the lower one dropping a fridge.
     *
     * <p>Breaking a block in creative suppresses <i>its</i> drops, and only its own. The orphaned
     * partner is removed separately, through {@code updateShape} into {@code Block.updateOrDestroy},
     * which runs that block's loot table normally - and the loot sits on the LOWER half. So breaking
     * the head in creative handed over a free fridge. {@link MattressBlock} guards the mirror image
     * of this for the same reason; flag 35 is the setBlock that skips drops.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops()
                && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, below, Block.getId(lower));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Only the UPPER half answers for its partner, and that asymmetry is the whole bug fix.
     *
     * <p>The first version asked BOTH halves to prove their partner existed. That reads as the
     * honest rule and it made the block <b>impossible to place</b>: {@code BlockItem.canPlace}
     * consults {@code canSurvive} on the lower half <i>before</i> {@link #setPlacedBy} has built the
     * upper one, so the check could never pass and every placement was refused. Nothing logged, the
     * item simply never left the hand.
     *
     * <p>It hid well. {@link #getStateForPlacement} returned a perfectly good state throughout, so
     * the obvious test - the one the washing machine uses, calling that method directly - would have
     * passed. Only going through the item catches it, which is why
     * {@code a_fridge_places_both_halves_from_the_hand} holds a real {@code ItemStack}.
     *
     * <p>So the lower half survives on its own and is removed as an orphan by {@link #updateShape}
     * instead. Vanilla's tall flowers and doors are built exactly this way, for exactly this reason.
     */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return true;
        }
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    /**
     * Become air when the partner goes, rather than breaking it.
     *
     * <p>The whole no-recursion argument lives here: this returns a state, it does not modify the
     * world, so there is no hook to re-enter.
     *
     * <p>It deliberately does <b>not</b> delegate to {@link #canSurvive}. That would re-introduce
     * the placement bug from the other side - the lower half must be allowed to exist alone for the
     * one tick between the item placing it and {@link #setPlacedBy} adding the head - so the orphan
     * test is written out here against the specific neighbour that changed.
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction direction, BlockPos neighbourPos, BlockState neighbourState,
            RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean towardPartner = direction.getAxis() == Direction.Axis.Y
            && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP);
        if (towardPartner
                && (!neighbourState.is(this) || neighbourState.getValue(HALF) == half)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }
}

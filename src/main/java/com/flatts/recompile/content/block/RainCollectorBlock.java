package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.RainCollectorBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import org.jspecify.annotations.Nullable;

/**
 * The Rain Collector (design P1.10): a scrap frame with a tarp stretched over it that
 * fills from rain. Two cells - a solid base holding the tank ({@link RainCollectorBlockEntity},
 * the lower half) and a draped tarp above it (the upper half, the rain catcher). It borrows
 * the Sorting Tarp's tarp-on-a-frame look on purpose; both are the same real object.
 *
 * <p>Fills like a cauldron: {@link #handlePrecipitation} on the sky-exposed upper half rolls
 * the same slow chance vanilla uses and adds water to the base's tank. A glass bottle draws a
 * water bottle; a bucket (or a pipe) moves water through the standard fluid capability.
 *
 * <p><b>No water existed in this world at all</b> (sea level -64, {@code default_fluid: air});
 * only {@code has_precipitation} is true. The collector is the one source.
 */
public class RainCollectorBlock extends BaseEntityBlock {

    public static final MapCodec<RainCollectorBlock> CODEC = simpleCodec(RainCollectorBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    /** Slow fill, matching vanilla's cauldron rain chance. */
    private static final float RAIN_CHANCE = 0.05F;

    private static final VoxelShape BASE_SHAPE = Block.box(2, 0, 2, 14, 16, 14);
    private static final VoxelShape TARP_SHAPE = Shapes.or(
        Block.box(0, 0, 0, 16, 5, 16),   // tarp draped low over the frame
        Block.box(2, 5, 2, 14, 8, 14));  // a slight sagging peak

    public RainCollectorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends RainCollectorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    /** The tank lives on the base only; the tarp is just the catcher and the model. */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER
            ? new RainCollectorBlockEntity(pos, state) : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? BASE_SHAPE : TARP_SHAPE;
    }

    // ---------------- rain fill ----------------

    @Override
    public void handlePrecipitation(BlockState state, Level level, BlockPos pos, Biome.Precipitation precipitation) {
        // Only the sky-exposed tarp catches rain; it fills the base's tank below it.
        if (state.getValue(HALF) != DoubleBlockHalf.UPPER || precipitation != Biome.Precipitation.RAIN) {
            return;
        }
        if (level.getRandom().nextFloat() < RAIN_CHANCE
                && level.getBlockEntity(pos.below()) instanceof RainCollectorBlockEntity be) {
            be.catchRain();
        }
    }

    // ---------------- interaction: bucket (capability) + glass bottle -> water bottle ----------------

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos basePos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (!(level.getBlockEntity(basePos) instanceof RainCollectorBlockEntity be)) {
            return InteractionResult.PASS;
        }
        // Glass bottle -> water bottle (the specific case, handled before generic containers).
        if (stack.is(Items.GLASS_BOTTLE)) {
            if (!be.canFillBottle()) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide()) {
                be.drainBottle();
                stack.shrink(1);
                ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
                if (!player.getInventory().add(water)) {
                    player.drop(water, false);
                }
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        // Buckets and any other fluid container: the standard tank interaction. The server
        // does the real transfer for any container; the client optimistically succeeds for
        // the vanilla buckets so the arm swings.
        if (!level.isClientSide()) {
            return FluidUtil.interactWithFluidHandler(player, hand, basePos, be.fluidHandler())
                ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET)
            ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    // ---------------- two-cell placement / break (mirrors DoublePlantBlock) ----------------

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        return pos.getY() < level.getMaxY() && level.getBlockState(pos.above()).canBeReplaced(context)
            ? super.getStateForPlacement(context) : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (!level.isClientSide()) {
            level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) != DoubleBlockHalf.UPPER) {
            return super.canSurvive(state, level, pos);
        }
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (directionToNeighbour.getAxis() != Direction.Axis.Y
                || half == DoubleBlockHalf.LOWER != (directionToNeighbour == Direction.UP)
                || neighbourState.is(this) && neighbourState.getValue(HALF) != half) {
            return half == DoubleBlockHalf.LOWER && directionToNeighbour == Direction.DOWN
                    && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
        }
        return Blocks.AIR.defaultBlockState();
    }

    /** Creative-break one half without the other dropping a second collector. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops()) {
            DoubleBlockHalf half = state.getValue(HALF);
            BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(otherPos);
            if (otherState.is(this) && otherState.getValue(HALF) != half) {
                level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, otherPos, Block.getId(otherState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}

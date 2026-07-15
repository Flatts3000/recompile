package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.ScrapBarrelBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Scrap Barrel: the garbage world's storage, a welded drum of salvaged sheet metal.
 *
 * <p>This world has no trees, so it has no chest, no barrel, and therefore no hopper -
 * every vanilla container is wood-gated. Storage still has to exist, and it may as well
 * be the thing a dump is already full of.
 *
 * <p>Feature parity with {@code BarrelBlock}, which this mirrors: 27 slots, opens with a
 * block sitting on top (the reason a barrel and not a chest - you stack these), comparator
 * output, contents drop on break, and the open/close lid state and sounds.
 *
 * <p>The single deviation: <b>no {@code FACING}</b>. A vanilla barrel points wherever you
 * were looking; a drum stands on its end. So there is no {@code getStateForPlacement}
 * override - the default state is always top-up - and no {@code rotate}/{@code mirror},
 * which would have nothing to turn.
 */
public class ScrapBarrelBlock extends BaseEntityBlock {

    public static final MapCodec<ScrapBarrelBlock> CODEC = simpleCodec(ScrapBarrelBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public ScrapBarrelBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(OPEN, false));
    }

    @Override
    public MapCodec<ScrapBarrelBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new ScrapBarrelBlockEntity(worldPosition, blockState);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof ScrapBarrelBlockEntity barrel) {
            player.openMenu(barrel);
            player.awardStat(Stats.OPEN_BARREL);
            PiglinAi.angerNearbyPiglins(serverLevel, player, true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
            boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    /** Booked by {@code ContainerOpenersCounter} so the lid cannot stick open. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof ScrapBarrelBlockEntity barrel) {
            barrel.recheckOpen();
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }
}

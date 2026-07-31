package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.BurnerGeneratorBlockEntity;
import com.flatts.recompile.registry.RCBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Burner Generator (#72): the half of the power tier that works at night.
 *
 * <p><b>It has a fuel buffer and a screen</b> (owner call, 2026-07-31): if the Burn Barrel gets a UI for
 * holding fuel, so does this. It borrows vanilla's hopper screen - five slots is exactly a fuel buffer -
 * so the mod's "reuse a vanilla screen, never mint one" rule holds and {@code RCMenus} is untouched.
 *
 * <p>Its row in {@code docs/automation_policy_spec.md} is "fuel in, nothing out": every face takes fuel,
 * no face gives it back, since a pipe pulling fuel out of the generator it just filled is nobody's
 * intent.
 *
 * <p>{@link BlockStateProperties#LIT} drives the texture, so a running generator is readable from across a
 * base without a gauge - the same trick the Burn Barrel uses.
 */
public class BurnerGeneratorBlock extends Block implements EntityBlock {

    public static final MapCodec<BurnerGeneratorBlock> CODEC = simpleCodec(BurnerGeneratorBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** Which way the firebox faces. 26.1 has no DirectionProperty - horizontal facing is an EnumProperty. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BurnerGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(LIT, false));
    }

    /** Face the player, like every other machine with a front. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected MapCodec<? extends BurnerGeneratorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BurnerGeneratorBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || type != RCBlockEntities.BURNER_GENERATOR.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<BurnerGeneratorBlockEntity>)
            BurnerGeneratorBlockEntity::serverTick;
    }

    /**
     * Smoke from the top while it runs, the Burn Barrel's tell.
     *
     * <p>Client-only and purely cosmetic, but it is the only cue visible from a distance: the LIT texture
     * is on the front, so a generator seen from behind or above otherwise looks idle while it is working.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3;
        level.addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0.0, 0.02, 0.0);
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.04, 0.0);
            level.playLocalSound(x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS,
                0.4F, 1.0F, false);
        }
    }

    /**
     * Right-click opens the fuel buffer.
     *
     * <p>It reuses vanilla's hopper screen rather than minting one: five slots IS a fuel buffer, and the
     * mod's rule is that a bespoke machine screen is a design reversal. The energy level is not on this
     * screen because no vanilla screen has a bar for it - Jade carries that instead.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof BurnerGeneratorBlockEntity generator) {
            player.openMenu(generator);
        }
        return InteractionResult.SUCCESS;
    }
}

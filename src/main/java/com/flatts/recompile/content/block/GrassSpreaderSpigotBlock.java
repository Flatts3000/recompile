package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.multiblock.MultiblockDummyBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A drip spigot: what a Copper Pipe becomes on one of the four sides of a formed Grass Spreader, and
 * the part that visibly does the work.
 *
 * <p><b>Drip, not spray.</b> Water falls straight down from the spigot mouth rather than arcing
 * outward - this is a drip irrigator, not a lawn sprinkler, which suits a machine whose whole job is
 * patient, deliberate reclamation. It also costs nothing: dripping needs no {@code BlockEntity} and
 * no renderer, only {@code animateTick}, the client-side hook vanilla torches use. There is no
 * moving part anywhere in this machine, so it never needs 26.1's block-entity renderer.
 *
 * <p>Because a spigot only exists inside a formed spreader, <b>being formed gates the visual for
 * free</b> - there is no state to check, and a disbanded machine simply has no spigots to drip.
 */
public class GrassSpreaderSpigotBlock extends MultiblockDummyBlock {

    public static final MapCodec<GrassSpreaderSpigotBlock> CODEC = simpleCodec(GrassSpreaderSpigotBlock::new);

    /** Points at the manifold, so the elbow always plumbs inward. Set by {@code Multiblock.form}. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public GrassSpreaderSpigotBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends GrassSpreaderSpigotBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * Let a drop go from the spigot mouth. Deliberately sparse and with no initial velocity: a slow,
     * irregular drip reads as irrigation, where a steady stream would read as a burst pipe.
     *
     * <p>The mouth is at the <em>outer</em> end of the elbow, away from the manifold, so the drop
     * has to be offset opposite the facing - dripping from the block centre would put the water
     * inside the machine instead of under the spout.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (random.nextInt(3) != 0) {
            return;
        }
        Direction outward = state.getValue(FACING).getOpposite();
        double x = pos.getX() + 0.5 + outward.getStepX() * 0.28 + (random.nextDouble() - 0.5) * 0.2;
        double z = pos.getZ() + 0.5 + outward.getStepZ() * 0.28 + (random.nextDouble() - 0.5) * 0.2;
        level.addParticle(ParticleTypes.FALLING_WATER, x, pos.getY() + 0.05, z, 0.0, 0.0, 0.0);
    }
}

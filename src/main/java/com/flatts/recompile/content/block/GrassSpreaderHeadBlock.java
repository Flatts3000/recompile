package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.multiblock.MultiblockDummyBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Grass Spreader's sprinkler head: what a Motor becomes once the machine assembles, and the part
 * that visibly does the work.
 *
 * <p><b>The spray is particles, and that is most of the machine.</b> A tower that stands there
 * converting blocks reads as broken; the same tower throwing water reads as working. This needs no
 * BlockEntity and no renderer - {@code animateTick} is the client-side hook vanilla torches use.
 *
 * <p>Because this block only exists inside a formed spreader, <b>being formed gates the visual for
 * free</b> - there is no state to check. A disbanded machine has no head, so it cannot spray.
 *
 * <p>The head does not spin yet. Rotation needs a {@code BlockEntityRenderer}, and 26.1's is a
 * render-state / submit-node API that no 1.21-era reference ports to, so it is deliberately its own
 * task. A sprinkler with a convincing spray and a static head reads fine; the reverse does not.
 */
public class GrassSpreaderHeadBlock extends MultiblockDummyBlock {

    public static final MapCodec<GrassSpreaderHeadBlock> CODEC = simpleCodec(GrassSpreaderHeadBlock::new);

    /** Droplets per animate tick. Enough to read as a spray without hazing the screen. */
    private static final int JETS = 3;

    public GrassSpreaderHeadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GrassSpreaderHeadBlock> codec() {
        return CODEC;
    }

    /**
     * Throw water outward. Particles are launched on a horizontal heading with a slight upward
     * component so they arc out and fall, rather than dribbling straight down the machine - the arc
     * is what makes it read as a sprinkler rather than a leak.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        for (int i = 0; i < JETS; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double speed = 0.18 + random.nextDouble() * 0.12;
            double x = pos.getX() + 0.5 + Math.cos(angle) * 0.45;
            double y = pos.getY() + 0.55;
            double z = pos.getZ() + 0.5 + Math.sin(angle) * 0.45;
            level.addParticle(ParticleTypes.SPLASH, x, y, z,
                Math.cos(angle) * speed, 0.12, Math.sin(angle) * speed);
        }
        // a little fall-off underneath, so the tower looks wet rather than only ringed
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.FALLING_WATER,
                pos.getX() + random.nextDouble(), pos.getY() - 0.05, pos.getZ() + random.nextDouble(),
                0.0, 0.0, 0.0);
        }
    }
}

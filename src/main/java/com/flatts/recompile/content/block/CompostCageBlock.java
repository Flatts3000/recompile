package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.multiblock.MultiblockDummyBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A formed cell of the Compost Heap's 2x2x2 cage (Mod Jam - the fertilizer tier). A pure dummy: it
 * stores nothing and redirects use/break to the core (via {@link MultiblockDummyBlock}). Its one job of
 * its own is the visual - a top-of-the-heap cage cell <b>steams</b> while the core is cooking, the same
 * way the Grass Spreader's spigot sprays only when formed. Its loot drops a Machine Frame (the component
 * the disband returns).
 */
public class CompostCageBlock extends MultiblockDummyBlock {

    public static final MapCodec<CompostCageBlock> CODEC = simpleCodec(CompostCageBlock::new);

    public CompostCageBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends CompostCageBlock> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        // Only the open top of the heap vents, so steam rises off the pile rather than out of its sides.
        if (!level.getBlockState(pos.above()).isAir() || random.nextInt(3) != 0) {
            return;
        }
        BlockPos core = findCore(level, pos);
        if (core == null) {
            return;
        }
        BlockState coreState = level.getBlockState(core);
        if (!(coreState.getBlock() instanceof CompostHeapCoreBlock) || !coreState.getValue(CompostHeapCoreBlock.COOKING)) {
            return;
        }
        double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
        double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
        level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, pos.getY() + 0.95, z, 0.0, 0.02, 0.0);
    }
}

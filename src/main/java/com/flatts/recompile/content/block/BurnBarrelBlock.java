package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.BurnBarrelBlockEntity;
import com.flatts.recompile.registry.RCBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Burn Barrel (design P2.2): the garbage world's first smelter. A vanilla-furnace reskin (see
 * {@link BurnBarrelBlockEntity}) - same recipes, same speed, the vanilla furnace screen - that is
 * deliberately <b>manual-only</b> (the BlockEntity blocks all automation). Mirrors
 * {@code FurnaceBlock}: {@code AbstractFurnaceBlock} supplies FACING, the LIT state, placement, and
 * the open-on-use, so this class only wires the block entity, the ticker, and the lit particles.
 */
public class BurnBarrelBlock extends AbstractFurnaceBlock {

    public static final MapCodec<BurnBarrelBlock> CODEC = simpleCodec(BurnBarrelBlock::new);

    public BurnBarrelBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<BurnBarrelBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new BurnBarrelBlockEntity(worldPosition, blockState);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> blockEntityType) {
        return createFurnaceTicker(level, blockEntityType, RCBlockEntities.BURN_BARREL.get());
    }

    @Override
    protected void openContainer(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof BurnBarrelBlockEntity barrel) {
            player.openMenu(barrel);
            player.awardStat(Stats.INTERACT_WITH_FURNACE);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        // Smoke, the odd flame, off the open top of the drum.
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3;
        level.addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0.0, 0.02, 0.0);
        if (random.nextDouble() < 0.4) {
            level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.0, 0.0);
        }
    }
}

package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.CupolaFurnaceBlockEntity;
import com.flatts.recompile.registry.RCBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * The Cupola Furnace (#50) - see {@link CupolaFurnaceBlockEntity} for what a cupola is and why iron lives
 * behind it. {@code AbstractFurnaceBlock} supplies FACING, the LIT state, placement and open-on-use, so this
 * class only wires the block entity, the ticker and the lit particles.
 *
 * <p>Unlike the Burn Barrel there is no custom ticker: nothing to gate (blasting IS the gate) and nothing to
 * drain, because this machine automates through its faces the ordinary way.
 */
public class CupolaFurnaceBlock extends AbstractFurnaceBlock {

    public static final MapCodec<CupolaFurnaceBlock> CODEC = simpleCodec(CupolaFurnaceBlock::new);

    public CupolaFurnaceBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<CupolaFurnaceBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new CupolaFurnaceBlockEntity(worldPosition, blockState);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> blockEntityType) {
        return createFurnaceTicker(level, blockEntityType, RCBlockEntities.CUPOLA_FURNACE.get());
    }

    @Override
    protected void openContainer(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof CupolaFurnaceBlockEntity cupola) {
            player.openMenu(cupola);
            player.awardStat(Stats.INTERACT_WITH_BLAST_FURNACE);
        }
    }

    /** Coke smoke and a glow at the tap hole while it runs. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        if (random.nextDouble() < 0.1) {
            level.playLocalSound(x, y, z, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
        }
        level.addParticle(ParticleTypes.SMOKE, x + random.nextDouble() * 0.4 - 0.2, y + 1.0,
            z + random.nextDouble() * 0.4 - 0.2, 0.0, 0.0, 0.0);
    }
}

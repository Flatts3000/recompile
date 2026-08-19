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
        BlockEntityTicker<T> furnace =
            createFurnaceTicker(level, blockEntityType, RCBlockEntities.CUPOLA_FURNACE.get());
        if (furnace == null || level.isClientSide()) {
            return furnace;
        }
        // Smelt first, then push what came out into the network. Wrapping rather than replacing,
        // because AbstractFurnaceBlockEntity keeps its recipe lookup private behind a static tick -
        // the tick is the only seam this machine has, which is the same reason the Burn Barrel's
        // refuse gate lives here rather than on its slots.
        return (lvl, pos, st, be) -> {
            // NO HOLD WHEN THE SLAG SLOT IS FULL, and the first version of this was wrong to add one.
            //
            // Skipping the tick froze the machine mid-burn: AbstractFurnaceBlockEntity.serverTick is
            // the only code that clears the LIT blockstate, so a Cupola that stopped for slag stayed
            // lit forever - light 13, fire crackle and smoke on a machine doing nothing, which is the
            // worst possible signal since it looks exactly like a machine that is working.
            //
            // And it bought nothing. rakeSlag carries its remainder on the counter, so a full slot
            // loses no slag at all: the debt simply waits and pays out the moment there is room. The
            // metal keeps coming, which is what a player wants from the machine they are actually
            // watching.
            // COUNT THE RESULT SLOT ACROSS THE TICK, which is how the slag knows a smelt finished.
            //
            // Nothing else can grow that slot: vanilla's canPlaceItem refuses insertion into slot 2 and
            // FurnaceResultSlot.mayPlace refuses it in the GUI, so any increase is a completed smelt.
            // Exact, and it needs none of the private cook state AbstractFurnaceBlockEntity hides.
            //
            // Sampled BEFORE drainOutput, because that empties the slot into the network - read it after
            // and every smelt looks like nothing happened on any wired Cupola.
            int before = be instanceof com.flatts.recompile.content.block.entity.CupolaFurnaceBlockEntity c
                ? c.getItem(2).getCount() : 0;
            furnace.tick(lvl, pos, st, be);
            if (lvl instanceof net.minecraft.server.level.ServerLevel serverLevel
                    && be instanceof com.flatts.recompile.content.block.entity.CupolaFurnaceBlockEntity
                        cupola) {
                // CLAMPED TO ONE. The delta counts result ITEMS and rakeSlag wants SMELTS, and a
                // recipe may yield more than one - every recipe this mod ships yields exactly one, but
                // a datapack is supported here and one with "count": 3 would make slag three times as
                // fast, silently. A furnace completes at most one cook per tick, so any positive delta
                // is exactly one smelt.
                cupola.rakeSlag(serverLevel,
                    Math.min(1, Math.max(0, cupola.getItem(2).getCount() - before)));
                cupola.drainOutput(serverLevel);
            }
        };
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

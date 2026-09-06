package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.FreightTerminalBlockEntity;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.content.freight.FreightManifest;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Where the planet leaves (#387, spec {@code docs/freight_conversion_spec.md}).
 *
 * <p>The block half of the Freight Terminal. Everything interesting is in
 * {@link FreightTerminalBlockEntity}; this exists to hold it and tick it.
 *
 * <p><b>It does not drop its contents itself, and does not need to.</b>
 * {@code BlockEntity.preRemoveSideEffects} already runs {@code Containers.dropContents} for any
 * {@code Container}, which the terminal is - the same route the Hauler Depot relies on. What drops is
 * only whatever landed in the strip during the last tick before the block broke, because the ticker
 * drains it every tick. Voiding it instead would make breaking the block a silent way to lose goods,
 * which is exactly what the refuse-at-the-slot rule exists to prevent everywhere else.
 */
public class FreightTerminalBlock extends BaseEntityBlock {

    public static final MapCodec<FreightTerminalBlock> CODEC = simpleCodec(FreightTerminalBlock::new);

    public FreightTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FreightTerminalBlockEntity(pos, state);
    }

    /**
     * Open the manifest.
     *
     * <p>The two-argument {@code openMenu} carries the phase's requirements in the open buffer, which
     * is the Buy Terminal's pattern: the client screen lists exactly what the server resolved and no
     * second sync path exists to drift from it.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof FreightTerminalBlockEntity terminal) {
            player.openMenu(terminal,
                buffer -> FreightManifest.STREAM_CODEC.encode(buffer, terminal.manifest()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, RCBlockEntities.FREIGHT_TERMINAL.get(),
            FreightTerminalBlockEntity::serverTick);
    }
}

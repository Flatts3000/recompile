package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.SequencerBlockEntity;
import com.flatts.recompile.registry.RCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Sequencer (#294): a powered block that reads the creature out of a piece of Amber.
 *
 * <p><b>A single block with a screen, not a multiblock</b> (owner, 2026-08-28). The three conveyor
 * machines are things you feed and walk away from; this is one you put a single precious object into
 * and watch, which is the Burner Generator's shape rather than the Pulverizer's.
 *
 * <p><b>Its screen is the eighth in this mod, and it is the SAME recorded exception as the third.</b>
 * The rule is that containers reuse a vanilla screen and producers with a gauge vanilla has no shape
 * for cannot: a machine that burns FE needs an energy bar, and no vanilla screen has one. That is
 * exactly why the Burner Generator got one in #72, so this adds a case to a standing exception rather
 * than opening a new one.
 *
 * <p><b>No item capability and no {@code Container} exposure to the world</b>, matching the three
 * conveyor machines: a pipe cannot reach in. Unlike them it does not reach out either, because a
 * machine you watch does not need to - the whole interaction is the screen.
 */
public class SequencerBlock extends Block implements EntityBlock {

    public SequencerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SequencerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, RCBlockEntities.SEQUENCER.get(),
            SequencerBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof SequencerBlockEntity machine) {
            player.openMenu(machine);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Drop what is inside when the block goes.
     *
     * <p>Amber is a rare find and a half-read one is still a find; losing a piece to a misclick would
     * be the most annoying possible way to meet this machine.
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level,
            BlockPos pos, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof SequencerBlockEntity machine) {
            Containers.dropContents(level, pos, machine);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity, E extends BlockEntity> @Nullable BlockEntityTicker<T>
            createTickerHelper(BlockEntityType<T> given, BlockEntityType<E> expected,
            BlockEntityTicker<? super E> ticker) {
        return expected == given ? (BlockEntityTicker<T>) ticker : null;
    }
}

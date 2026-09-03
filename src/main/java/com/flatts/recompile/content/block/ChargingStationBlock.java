package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.ChargingStationBlockEntity;
import com.flatts.recompile.content.item.GarbageVacuumItem;
import com.flatts.recompile.registry.RCBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Charging Station (#336): a dock that takes FE from whatever generator is touching it and pushes it
 * into the Garbage Vacuum set on it.
 *
 * <p><b>No screen</b> (spec, {@code docs/garbage_vacuum_spec.md}). All eight custom machine screens are
 * recorded exceptions and this is not a ninth: the Display Pedestal's interaction, verbatim. Right-click
 * holding a vacuum to dock it (swapping out whatever was there, which comes back to your hand);
 * right-click empty-handed to take it back. Jade reports the charge.
 *
 * <p><b>Not in the Scrap Network</b>, and not because it was forgotten. That tag routes ITEMS between
 * bins and barrels, and nothing here is routable; power moves the way it does everywhere in this mod,
 * by adjacency - a generator pushes into the block it touches. The Solar Panel, the Burner and the
 * Sequencer are outside the tag for the same reason.
 *
 * <p>The dock exposes {@code Capabilities.Energy.BLOCK} insert-only (a consumer, like every other one)
 * and no item capability or {@code Container}: a hopper cannot pull the vacuum out, a pipe cannot
 * connect. Setting down and picking up is the whole interaction. Its row is in
 * {@code docs/automation_policy_spec.md}.
 */
public class ChargingStationBlock extends BaseEntityBlock {
    public static final MapCodec<ChargingStationBlock> CODEC = simpleCodec(ChargingStationBlock::new);

    public ChargingStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<ChargingStationBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChargingStationBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, RCBlockEntities.CHARGING_STATION.get(), ChargingStationBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof GarbageVacuumItem)
                || !(level.getBlockEntity(pos) instanceof ChargingStationBlockEntity dock)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            ItemStack previous = dock.dock(stack.copyWithCount(1));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            if (!previous.isEmpty()) {
                giveBack(player, previous);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ChargingStationBlockEntity dock) || dock.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            giveBack(player, dock.undock());
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    private static void giveBack(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // The docked vacuum drops from ChargingStationBlockEntity.preRemoveSideEffects on every removal
    // cause, the pedestal's pattern; the block itself drops via its loot table.
}

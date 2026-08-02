package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.HydroponicsBayBlockEntity;
import com.flatts.recompile.registry.RCBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import org.jspecify.annotations.Nullable;

/**
 * The Hydroponics Bay (#43): a single block that grows plants from water and power.
 *
 * <p><b>Deliberately not a multiblock.</b> The Rain Collector, Grass Spreader, Compost Heap and Tree
 * Nursery all use that framework, and this one does not - it arrives after the power tier, by which
 * point the player has built four of them, and a fifth assembly puzzle would be ceremony rather than
 * content.
 *
 * <p>{@link BlockStateProperties#LIT} tracks whether a batch is running, so the grow-light reads from
 * across a base without opening anything. Blockstate, not a BlockEntity field, for the same reason
 * {@code SortableBlock} keeps its sort count there: it is one bit the client already syncs.
 */
public class HydroponicsBayBlock extends BaseEntityBlock {

    public static final MapCodec<HydroponicsBayBlock> CODEC = simpleCodec(HydroponicsBayBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public HydroponicsBayBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // A baked model like every other block here. The pedestal's renderer is the one exception and
        // it is scoped to the pedestal.
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HydroponicsBayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
            : createTickerHelper(type, RCBlockEntities.HYDROPONICS_BAY.get(),
                HydroponicsBayBlockEntity::serverTick);
    }

    /**
     * A fluid container fills the tank; anything else opens the bay.
     *
     * <p><b>The bucket case has to come first.</b> Without it the only way to get water in is a pipe from
     * a Rain Collector, and a player holding a bucket of water gets a GUI with an empty tank gauge and no
     * way to act on it - the machine looks broken at exactly the moment they are trying to start it. This
     * is the same interaction {@code RainCollectorCoreBlock} offers, deliberately: one tank behaviour
     * across the mod, so learning it once is enough.
     *
     * <p>Opening is the fallback rather than the other way round, because a player holding a bucket over
     * a machine with a water gauge means to pour it, and a bucket has no reason to be in the bay's slots.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof HydroponicsBayBlockEntity bay)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            // The client cannot run the transfer, but it must agree that something happened or the arm
            // does not swing. Anything that is not a bucket falls through to opening the screen.
            return InteractionResult.SUCCESS;
        }
        if (FluidUtil.interactWithFluidHandler(player, hand, pos, bay.tank())) {
            return InteractionResult.SUCCESS;
        }
        player.openMenu(bay);
        return InteractionResult.SUCCESS;
    }

    /**
     * Spill the contents when broken.
     *
     * <p>Without this a player loses whatever was seeded and whatever had grown, and the plants in here
     * are the only ones in the world - a bay holding the last sugar cane in a save must not eat it.
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level,
            BlockPos pos, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof HydroponicsBayBlockEntity bay) {
            for (int slot = 0; slot < bay.getContainerSize(); slot++) {
                ItemStack stack = bay.getItem(slot);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                }
            }
            bay.clearContent();
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}

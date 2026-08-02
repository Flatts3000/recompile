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
     * Right-click opens the bay.
     *
     * <p>Without this the machine is reachable only by hopper or pipe, which makes the first one a
     * player builds appear broken.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof HydroponicsBayBlockEntity bay) {
            player.openMenu(bay);
        }
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

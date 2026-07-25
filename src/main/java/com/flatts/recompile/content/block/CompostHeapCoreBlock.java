package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.CompostHeapBlockEntity;
import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Compost Heap's core (Mod Jam - the fertilizer tier): the master of a 2x2x2 salvage cage that
 * composts organic dump waste into Fertilizer, the gate to the Vegetation and Farming tiers.
 *
 * <p>Reuses the multiblock framework (master + {@code MACHINE_FRAME} -> {@code COMPOST_CAGE} dummies,
 * the placement preview, disband) and carries a {@link CompostHeapBlockEntity} for the layer state -
 * the same "a machine may keep a BE for its own contents" line the Rain Collector's tank sits on.
 *
 * <p>Feed it muck or fiber (right-click), harvest a finished layer for one Fertilizer (right-click
 * empty-handed); the dummies redirect both to here so you can use any face of the cage. {@link #FILL}
 * (layer count) + {@link #COOKING} drive the model and the cage's steam.
 */
public class CompostHeapCoreBlock extends MultiblockCoreBlock implements EntityBlock {

    public static final MapCodec<CompostHeapCoreBlock> CODEC = simpleCodec(CompostHeapCoreBlock::new);

    /** How many layers are in the heap (0..MAX) - drives the compost model height. */
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, CompostHeapBlockEntity.MAX_LAYERS);
    /** Whether a layer is actively ripening - drives the steam. */
    public static final BooleanProperty COOKING = BooleanProperty.create("cooking");

    public CompostHeapCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends CompostHeapCoreBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);   // FORMED
        builder.add(FILL, COOKING);
    }

    @Override
    protected Multiblock createBlueprint() {
        // The core is the origin cell of the 2x2x2; the other seven are Machine Frames that form the
        // cage. (Machine Frame, orphaned by the workstation pivot, finally has a job.)
        List<Multiblock.Cell> cells = new ArrayList<>();
        for (Vec3i offset : List.of(
                new Vec3i(1, 0, 0), new Vec3i(0, 0, 1), new Vec3i(1, 0, 1),
                new Vec3i(0, 1, 0), new Vec3i(1, 1, 0), new Vec3i(0, 1, 1), new Vec3i(1, 1, 1))) {
            cells.add(new Multiblock.Cell(offset, RCBlocks.MACHINE_FRAME.get(), RCBlocks.COMPOST_CAGE.get()));
        }
        return new Multiblock(List.copyOf(cells));
    }

    // ---------------- the compost BlockEntity ----------------

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CompostHeapBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || !isFormed(state) || type != RCBlockEntities.COMPOST_HEAP.get()) {
            return null;   // an unformed cage does not compost; the client does not tick it
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<CompostHeapBlockEntity>)
            CompostHeapBlockEntity::serverTick;
    }

    // ---------------- interaction: feed muck/fiber, harvest fertilizer ----------------

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!isFormed(state)
                || !(stack.is(RCItems.ORGANIC_MUCK.get()) || stack.is(RCItems.FIBER_SCRAP.get()))
                || !(level.getBlockEntity(pos) instanceof CompostHeapBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (be.isFull()) {
            if (!level.isClientSide()) {
                player.sendSystemMessage(Component.translatable("message.recompile.compost_full"));
            }
            return InteractionResult.SUCCESS;   // consume the click, keep the item
        }
        if (!level.isClientSide()) {
            be.feed();
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.COMPOSTER_FILL_SUCCESS, SoundSource.BLOCKS, 0.7F, 0.9F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!isFormed(state) || !(level.getBlockEntity(pos) instanceof CompostHeapBlockEntity be)
                || !be.hasFinishedLayer()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack out = be.harvest();
            if (!out.isEmpty()) {
                if (!player.getInventory().add(out)) {
                    player.drop(out, false);
                }
                level.playSound(null, pos, SoundEvents.COMPOSTER_READY, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
        }
        return InteractionResult.SUCCESS;
    }
}

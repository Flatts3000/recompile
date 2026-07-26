package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.CompostHeapBlockEntity;
import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCTags;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.Rotation;
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
 * empty-handed); the dummies redirect both to here so you can use any face of the cage. {@link #LAYERS}
 * (compost bands, pushed onto every cell) + {@link #COOKING} drive the see-through model and the steam.
 */
public class CompostHeapCoreBlock extends MultiblockCoreBlock implements EntityBlock {

    public static final MapCodec<CompostHeapCoreBlock> CODEC = simpleCodec(CompostHeapCoreBlock::new);

    /**
     * The heap's total layer count (0..MAX), on the <b>core only</b>. The core draws one continuous
     * compost column spanning the whole 2x2 interior (a block model may extend across its neighbours),
     * so the compost reads as a single mass with no per-cell seams or gaps - the flaw of drawing it
     * per cell. The bands stay countable through the wire because the column is one piece.
     */
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, CompostHeapBlockEntity.MAX_LAYERS);
    /**
     * How many of the bottom layers have finished ripening (0..MAX, always {@code <= FILL}). Layers
     * ripen oldest-first, so the ripe ones are the bottom band prefix; those bands render with the
     * finished-compost texture, so a glance shows how much is ready to harvest.
     */
    public static final IntegerProperty RIPE = IntegerProperty.create("ripe", 0, CompostHeapBlockEntity.MAX_LAYERS);
    /**
     * Which of this cell's faces are the outer shell: {@code hi_*} true means this cell is on the high
     * side of that axis, so its high-side face is exposed (and its low-side face is internal). Every
     * cell of the fixed 2x2x2 is a corner, so exactly three are the exposed shell; the multipart applies
     * a wire panel only there, giving connected wire (no interior lattice) without touching neighbours.
     */
    public static final BooleanProperty HI_X = BooleanProperty.create("hi_x");
    public static final BooleanProperty HI_Y = BooleanProperty.create("hi_y");
    public static final BooleanProperty HI_Z = BooleanProperty.create("hi_z");
    /** Whether a layer is actively ripening - drives the steam. */
    public static final BooleanProperty COOKING = BooleanProperty.create("cooking");

    public CompostHeapCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends CompostHeapCoreBlock> codec() {
        return CODEC;
    }

    // The cage is mostly air (thin wire + inset compost), so it must not shade its own interior -
    // pass light through like glass (0 dampening, skylight straight down), or the recessed floor and
    // lower layers sit in a false shadow. (26.1 renamed getLightBlock -> getLightDampening.)
    @Override
    protected int getLightDampening(BlockState state) {
        return 0;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);   // FORMED
        builder.add(FILL, RIPE, HI_X, HI_Y, HI_Z, COOKING);
    }

    /**
     * Push the visual onto the cells: the whole heap's {@link #FILL} onto the core (which draws the one
     * compost column) and the outer-shell faces ({@code hi_*}, fixed by each cell's corner) onto every
     * cell. Only the core carries {@link #FILL} and {@link #COOKING} (the cage cells read cooking back
     * for steam). Writes {@code UPDATE_CLIENTS} only, and only the cells that changed, so a per-tick
     * call from the BE is a cheap no-op until the fill actually moves.
     */
    public void updateFillVisual(Level level, BlockPos corePos, int layerCount, int ripeLayers, boolean cooking) {
        Rotation rotation = rotationFor(level.getBlockState(corePos));
        setCellVisual(level, corePos, ORIGIN, layerCount, ripeLayers, cooking);
        for (Multiblock.Cell cell : blueprint().cells()) {
            setCellVisual(level, cell.at(corePos, rotation), cell.offset(), layerCount, ripeLayers, cooking);
        }
    }

    private static final Vec3i ORIGIN = new Vec3i(0, 0, 0);

    private static void setCellVisual(Level level, BlockPos pos, Vec3i offset, int globalLayers, int ripeLayers, boolean cooking) {
        BlockState state = level.getBlockState(pos);
        BlockState updated = state;
        if (state.hasProperty(FILL)) {   // only the core carries FILL/RIPE and draws the column
            updated = updated.setValue(FILL, globalLayers).setValue(RIPE, ripeLayers);
        }
        if (state.hasProperty(HI_X)) {
            updated = updated
                .setValue(HI_X, offset.getX() == 1)
                .setValue(HI_Y, offset.getY() == 1)
                .setValue(HI_Z, offset.getZ() == 1);
        }
        if (state.hasProperty(COOKING)) {   // only the core carries COOKING
            updated = updated.setValue(COOKING, cooking);
        }
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        }
    }

    /** On assembly, stamp the shell faces + any existing fill onto the cells so a formed cage is whole at once. */
    @Override
    protected void onFormed(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CompostHeapBlockEntity be) {
            be.refreshVisual();
        }
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
                || !stack.is(RCTags.COMPOSTABLE)
                || !(level.getBlockEntity(pos) instanceof CompostHeapBlockEntity be)) {
            // Not a feed: hand this to the empty-hand path (the harvest). Returning PASS instead would
            // stop the game ever calling useWithoutItem in 26.1, so an empty-handed right-click could
            // never pull Fertilizer - only TRY_WITH_EMPTY_HAND falls through.
            return InteractionResult.TRY_WITH_EMPTY_HAND;
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
                // Pop the produce out the face the player clicked, like the vanilla composter spits bonemeal
                // out its top - the compost is turned out of the bin, not teleported into a pocket.
                Direction face = hit.getDirection();
                Block.popResourceFromFace(level, pos, face, out);
                level.playSound(null, pos, SoundEvents.COMPOSTER_READY, SoundSource.BLOCKS, 0.8F, 1.0F);
                // A volunteer may have come up in the layer with the fertilizer; it turns out too.
                if (be.rollVolunteer()) {
                    Block.popResourceFromFace(level, pos, face,
                        new ItemStack(RCItems.UNKNOWN_SEEDLING.get()));
                }
            }
        }
        return InteractionResult.SUCCESS;
    }
}

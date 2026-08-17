package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.TrommelBlockEntity;
import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCBlocks;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The Trommel: a rotating screen that sorts garbage unattended (#188).
 *
 * <p><b>It exists because a shredder cannot sort.</b> The Separator had this job as a second mode, and
 * a shear shredder destroys distinctions - sorting needs a discrimination, and a real facility uses a
 * different machine for each one it makes. A trommel makes the SIZE cut: a long perforated drum,
 * tilted, turning, with fines falling through and oversize riding out the end. It is usually the first
 * sorting stage in a plant and often the machine that breaks the bags open on the way.
 *
 * <p><b>It yields exactly what the Sorting Tarp yields per block.</b> The reward for building one is
 * that it runs unattended, not that it produces more. That is structural rather than a promise: rolls
 * and pull table both come from {@link SortableBlock}, so the two cannot drift.
 *
 * <p><b>4 long x 1 deep x 2 tall</b>, eight blocks. Long and thin, so it is not the Separator's bulk
 * from any angle. The drum is the top row and the stand is the bottom; the Motor is the drive cell,
 * which is better justified here than on the Separator - a shredder's motor is an inference, a drum
 * that visibly turns without one would be the odd thing.
 *
 * <p>Demolition yard tier (owner, 2026-08-12). The consequence is recorded in #188 rather than
 * discovered later: the sorting ladder now has a long gap in the middle, and the tarp carries it.
 */
public class TrommelCoreBlock extends MultiblockCoreBlock implements EntityBlock {

    public static final MapCodec<TrommelCoreBlock> CODEC = simpleCodec(TrommelCoreBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** True while the drum is turning, which swaps the drum cells to their animated texture. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /** How many blocks long the machine runs, the drum included. */
    public static final int LENGTH = 4;

    public TrommelCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends TrommelCoreBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public Rotation rotationFor(BlockState state) {
        return switch (state.getValue(FACING)) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    @Override
    public Rotation placementRotation(Player player) {
        return rotationFor(defaultBlockState()
            .setValue(FACING, player.getDirection().getOpposite()));
    }

    /**
     * The blueprint. The core is the feed end, and everything runs away from it along +x.
     *
     * <p>The drum is four cells of Steel I-Beam, because a trommel screen is perforated steel plate and
     * the yard's own material ties the machine to the region it stands in - the same argument the
     * Separator's bay makes. The stand is Machine Frame, and its far cell forms into the discharge, so
     * one hopper under one opening catches everything the machine produces.
     */
    @Override
    protected Multiblock createBlueprint() {
        List<Multiblock.Cell> cells = new ArrayList<>();
        Block beam = RCBlocks.STEEL_I_BEAM.get();
        Block frame = RCBlocks.MACHINE_FRAME.get();

        for (int x = 0; x < LENGTH; x++) {
            cells.add(new Multiblock.Cell(new Vec3i(x, 1, 0), beam, RCBlocks.TROMMEL_DRUM.get()));
        }
        // THE MOTOR, immediately behind the feed end. A drum that turns needs something turning it, and
        // this is the component the machine is really gated on - you cannot forge a working motor out
        // of any quantity of scrap. It forms into ordinary stand housing rather than a bespoke block,
        // the same exception the Separator's motor cell already takes: an assembled machine does not
        // display its motor.
        cells.add(new Multiblock.Cell(new Vec3i(1, 0, 0), RCBlocks.MOTOR.get(),
            RCBlocks.TROMMEL_STAND.get()));
        cells.add(new Multiblock.Cell(new Vec3i(2, 0, 0), frame, RCBlocks.TROMMEL_STAND.get()));
        cells.add(new Multiblock.Cell(new Vec3i(LENGTH - 1, 0, 0), frame,
            RCBlocks.TROMMEL_CHUTE.get()));
        return new Multiblock(List.copyOf(cells));
    }

    /** Every drum cell of a formed machine, in run order. Shared by the block and its tests. */
    public static List<BlockPos> drumCells(Level level, BlockPos core) {
        List<BlockPos> out = new ArrayList<>();
        if (!(level.getBlockState(core).getBlock() instanceof TrommelCoreBlock block)) {
            return out;
        }
        Rotation rotation = block.rotationFor(level.getBlockState(core));
        for (int x = 0; x < LENGTH; x++) {
            out.add(core.offset(Multiblock.rotate(new Vec3i(x, 1, 0), rotation)));
        }
        return out;
    }

    /**
     * Stamp each drum cell with its place in the run, so the four read as one barrel rather than four
     * blocks wearing the same picture.
     */
    @Override
    protected void onFormed(Level level, BlockPos pos) {
        BlockState coreState = level.getBlockState(pos);
        Direction facing = coreState.getValue(FACING);
        List<BlockPos> drum = drumCells(level, pos);
        for (int i = 0; i < drum.size(); i++) {
            BlockState state = level.getBlockState(drum.get(i));
            if (state.is(RCBlocks.TROMMEL_DRUM.get())) {
                level.setBlock(drum.get(i), state
                    .setValue(TrommelDrumBlock.CELL, i)
                    .setValue(TrommelDrumBlock.FACING, facing), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * Where material is accepted: the volume above the drum, along its whole length.
     *
     * <p>A trommel is fed at the top and the drum is the mouth, so anything dropped along the run goes
     * in. Single source of truth, shared with the tests, so the machine and what proves it works cannot
     * disagree about where the opening is.
     */
    public static @Nullable AABB mouth(Level level, BlockPos core) {
        List<BlockPos> drum = drumCells(level, core);
        if (drum.isEmpty()) {
            return null;
        }
        AABB box = new AABB(drum.get(0)).expandTowards(0, 1, 0);
        for (BlockPos cell : drum) {
            box = box.minmax(new AABB(cell).expandTowards(0, 1, 0));
        }
        return box;
    }

    /**
     * Where sorted material leaves: <b>off the far END of the drum</b>, at drum height.
     *
     * <p>Which is what a trommel does. Material travels the length of the turning screen and falls out
     * the open end; it does not appear beside the machine. This used to be the block in front of the
     * chute at stand level, which put the output on the wrong axis entirely - beside the machine rather
     * than at the end of the run - and meant a hopper had to be parked somewhere the drum does not
     * point.
     */
    public static BlockPos outlet(Level level, BlockPos core) {
        if (!(level.getBlockState(core).getBlock() instanceof TrommelCoreBlock block)) {
            return core;
        }
        Rotation rotation = block.rotationFor(level.getBlockState(core));
        return core.offset(Multiblock.rotate(new Vec3i(LENGTH, 1, 0), rotation));
    }

    /** The direction material travels down the drum, so the discharge throws the way the run points. */
    public static Direction dischargeFacing(Level level, BlockPos core) {
        if (!(level.getBlockState(core).getBlock() instanceof TrommelCoreBlock block)) {
            return Direction.EAST;
        }
        Rotation rotation = block.rotationFor(level.getBlockState(core));
        return rotation.rotate(Direction.EAST);
    }

    // ---------------- the BlockEntity ----------------

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrommelBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return type != RCBlockEntities.TROMMEL.get() ? null
            : (lvl, pos, st, be) -> TrommelBlockEntity.serverTick(
                (net.minecraft.server.level.ServerLevel) lvl, pos, st, (TrommelBlockEntity) be);
    }
}

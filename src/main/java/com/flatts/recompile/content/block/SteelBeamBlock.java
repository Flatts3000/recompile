package com.flatts.recompile.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Steel I-Beam (demolition yard): the husk's structural steel, an auto-orienting member that reads as a real
 * beam in every arrangement - standing alone, in a column, mid-run, at a corner, or at a cross.
 *
 * <p><b>Why it is not a PipeBlock.</b> The obvious node-plus-arms scheme (chorus plant, vanilla pipes) draws
 * geometry only toward <i>connected</i> faces, so a lone beam is a stub and every run ends half a block short.
 * The scheme here instead asks "what run is this block part of?" and draws the whole 16-length member:
 *
 * <ul>
 *   <li>{@link #AXIS} is the beam's intended orientation, fixed at placement from the clicked face. Click a
 *       floor and you get a column; click a wall and you get a girder running out of it.</li>
 *   <li>{@link #X} / {@link #Z} mean "this block is part of a horizontal run on that axis". Neither set means
 *       a vertical pole - which is why a lone beam is a full column and never a stub.</li>
 *   <li>{@link #TOP} / {@link #BOTTOM} add a gusset where a horizontal run meets something above or below, so
 *       a beam-to-column junction is a joint rather than two shapes intersecting.</li>
 * </ul>
 *
 * <p>A beam keeps a run on its non-native axis only while that run is actually supported at one end (see
 * {@link #updateShape}), so dragging a girder out of a wall retracts it instead of leaving it floating.
 *
 * <p>Cut only with the Cutting Torch ({@code requiresCorrectToolForDrops} + {@code
 * #recompile:mineable/cutting_torch}); drops raw iron in bulk. {@code noOcclusion} (set on the block
 * properties) is load-bearing: without it this slim model would cull neighbouring faces and punch holes in the
 * world (the CLAUDE.md occlusion trap).
 *
 * <p><b>Attribution.</b> The connection state machine below (the X/Z/TOP/BOTTOM/AXIS scheme, the placement
 * rule, and the retract-when-unsupported rule) is ported from Create's {@code GirderBlock}, trimmed to this
 * mod's blocks and vanilla. Create's <i>code</i> is MIT:
 *
 * <pre>
 * Copyright (c) The Create Team / The Creators of Create
 * Licensed under the MIT License.
 * </pre>
 *
 * Create's <i>assets</i> are All Rights Reserved, so none of its models or textures are reproduced here - the
 * I-profile geometry in {@code models/block/steel_beam_*.json} is this mod's own, and the shapes below mirror
 * those models rather than Create's.
 */
public class SteelBeamBlock extends Block implements SimpleWaterloggedBlock {

    public static final MapCodec<SteelBeamBlock> CODEC = simpleCodec(SteelBeamBlock::new);

    /** Part of a horizontal run along X. */
    public static final BooleanProperty X = BooleanProperty.create("x");
    /** Part of a horizontal run along Z. */
    public static final BooleanProperty Z = BooleanProperty.create("z");
    /** A gusset joins this beam to something above. */
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    /** A gusset joins this beam to something below. */
    public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
    /** The orientation the beam was placed with; it always draws its own run on this axis. */
    public static final EnumProperty<Axis> AXIS = BlockStateProperties.AXIS;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    // These MUST mirror models/block/steel_beam_{pole,x,z,top,bottom,cross}.json 1:1. One I-profile, 8 wide:
    // two 2-thick flanges and a 2-wide web between them, swept the full 16 along whichever axis the member
    // runs. Java shapes and the JSON models are two hand-kept copies of one geometry, so DemolitionYardTests
    // asserts these bounds exactly - a retune is meant to fail the tests.
    //
    // The braces ENCLOSE the flanges (y0-8 and y8-16 against flanges at y3-5 and y11-13) rather than
    // abutting them, and that overlap is load-bearing: see the note on the cross case in buildShape.
    //
    // They also MEET at y8 rather than stopping short, so a junction with structure above and below is
    // one continuous plate over the full block height. Stopping at y6/y10 left the web showing through
    // between them, which read as two separate pads bolted either side of the beam.
    private static final VoxelShape POLE = Block.box(4, 0, 4, 12, 16, 12);
    private static final VoxelShape BEAM_X = Block.box(0, 3, 4, 16, 13, 12);
    private static final VoxelShape BEAM_Z = Block.box(4, 3, 0, 12, 13, 16);
    private static final VoxelShape BRACE_TOP = Block.box(3, 8, 3, 13, 16, 13);
    private static final VoxelShape BRACE_BOTTOM = Block.box(3, 0, 3, 13, 8, 13);

    /** Shapes for all 16 x/z/top/bottom combinations, indexed by {@link #shapeIndex}. */
    private static final VoxelShape[] SHAPES = buildShapes();

    public SteelBeamBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(X, false).setValue(Z, false)
            .setValue(TOP, false).setValue(BOTTOM, false)
            .setValue(AXIS, Axis.Y)
            .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(X, Z, TOP, BOTTOM, AXIS, WATERLOGGED);
    }

    // ------------------------------------------------------------------ shape

    private static VoxelShape[] buildShapes() {
        VoxelShape[] shapes = new VoxelShape[16];
        for (int i = 0; i < shapes.length; i++) {
            boolean x = (i & 1) != 0;
            boolean z = (i & 2) != 0;
            boolean top = (i & 4) != 0;
            boolean bottom = (i & 8) != 0;
            shapes[i] = buildShape(x, z, top, bottom);
        }
        return shapes;
    }

    /** Mirrors the blockstate's multipart cases exactly, so the hitbox is always what is drawn. */
    private static VoxelShape buildShape(boolean x, boolean z, boolean top, boolean bottom) {
        if (x && z) {
            // A cross ALWAYS gets both gussets, ignoring top/bottom, and that is not a style choice: the two
            // members' flanges land at identical heights over the central 8x8, so their outward faces are
            // coplanar AND same-facing there - textbook z-fighting. The braces enclose those faces and hide
            // them. Making these conditional would flicker on any cross with nothing above or below it.
            return Shapes.or(BEAM_X, BEAM_Z, BRACE_TOP, BRACE_BOTTOM);
        }
        if (!x && !z) {
            return POLE;   // a full-height column: the lone-beam case, and never a stub
        }
        VoxelShape shape = x ? BEAM_X : BEAM_Z;
        if (top) {
            shape = Shapes.or(shape, BRACE_TOP);
        }
        if (bottom) {
            shape = Shapes.or(shape, BRACE_BOTTOM);
        }
        return shape;
    }

    private static int shapeIndex(BlockState state) {
        return (state.getValue(X) ? 1 : 0)
            | (state.getValue(Z) ? 2 : 0)
            | (state.getValue(TOP) ? 4 : 0)
            | (state.getValue(BOTTOM) ? 8 : 0);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[shapeIndex(state)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return SHAPES[shapeIndex(state)];
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // ------------------------------------------------------------- connection

    public static boolean isBeam(BlockState state) {
        return state.getBlock() instanceof SteelBeamBlock;
    }

    /** The property a neighbour on this side controls: the run axis for horizontals, the gusset for verticals. */
    private static Property<Boolean> propertyFor(Direction direction) {
        Axis axis = direction.getAxis();
        if (axis == Axis.X) {
            return X;
        }
        if (axis == Axis.Z) {
            return Z;
        }
        return direction == Direction.UP ? TOP : BOTTOM;
    }

    private static Direction[] directionsInAxis(Axis axis) {
        return switch (axis) {
            case X -> new Direction[] { Direction.WEST, Direction.EAST };
            case Y -> new Direction[] { Direction.DOWN, Direction.UP };
            case Z -> new Direction[] { Direction.NORTH, Direction.SOUTH };
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        // The clicked face picks the orientation: a floor/ceiling click stands a column up, a wall click runs
        // a girder out of that wall.
        Axis axis = context.getClickedFace().getAxis();
        BlockState state = this.defaultBlockState()
            .setValue(X, axis == Axis.X)
            .setValue(Z, axis == Axis.Z)
            .setValue(AXIS, axis);

        for (Direction direction : Direction.values()) {
            state = updateState(level, pos, state, direction);
        }
        return state.setValue(WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTick,
            BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState,
            RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            scheduledTick.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        Axis axis = direction.getAxis();
        if (axis != Axis.Y) {
            // A run on an axis this beam was not placed along is only kept while it is actually supported at
            // one end - otherwise pulling a girder out of a wall would leave it hanging in the air.
            if (state.getValue(AXIS) != axis
                && !isConnected(level, pos, state, direction)
                && !isConnected(level, pos, state, direction.getOpposite())) {
                state = state.setValue(propertyFor(direction), false);
            }
        } else if (state.getValue(AXIS) != Axis.Y) {
            if (level.getBlockState(pos.above()).getBlockSupportShape(level, pos.above()).isEmpty()) {
                state = state.setValue(TOP, false);
            }
            if (level.getBlockState(pos.below()).getBlockSupportShape(level, pos.below()).isEmpty()) {
                state = state.setValue(BOTTOM, false);
            }
        }

        for (Direction d : directionsInAxis(axis)) {
            state = updateState(level, pos, state, d);
        }
        return state;
    }

    /** Recomputes the one property that the neighbour on {@code d} controls. */
    public static BlockState updateState(LevelReader level, BlockPos pos, BlockState state, Direction d) {
        Property<Boolean> property = propertyFor(d);
        BlockState sideState = level.getBlockState(pos.relative(d));

        if (d.getAxis().isVertical()) {
            return updateVerticalProperty(state, property, sideState, d);
        }
        // A beam always draws its own run, then picks up any run passing through it.
        if (state.getValue(AXIS) == d.getAxis()) {
            return state.setValue(property, true);
        }
        if (isBeam(sideState) && sideState.getValue(property)) {
            return state.setValue(property, true);
        }
        return state;
    }

    /**
     * A gusset is drawn where a horizontal beam meets something that reads as vertical structure - another
     * beam, a wall post, a hanging lantern, a chain, or anything mounted flat to the floor/ceiling.
     */
    private static BlockState updateVerticalProperty(BlockState state, Property<Boolean> property,
            BlockState sideState, Direction d) {
        boolean canAttach;
        if (state.getValue(AXIS) == Axis.Y || isBeam(sideState)) {
            canAttach = true;
        } else if (sideState.hasProperty(BlockStateProperties.UP) && sideState.getValue(BlockStateProperties.UP)) {
            canAttach = true;   // a wall post
        } else if (sideState.getBlock() instanceof LanternBlock
            && (d == Direction.DOWN) == sideState.getValue(BlockStateProperties.HANGING)) {
            canAttach = true;
        } else if (sideState.getBlock() instanceof ChainBlock
            && sideState.getValue(BlockStateProperties.AXIS) == Axis.Y) {
            canAttach = true;
        } else if (sideState.hasProperty(BlockStateProperties.ATTACH_FACE)) {
            AttachFace face = sideState.getValue(BlockStateProperties.ATTACH_FACE);
            canAttach = (face == AttachFace.CEILING && d == Direction.DOWN)
                || (face == AttachFace.FLOOR && d == Direction.UP);
        } else {
            canAttach = false;
        }
        return canAttach ? state.setValue(property, true) : state;
    }

    /** Whether the run on {@code side} is actually held up by something solid over there. */
    public static boolean isConnected(LevelReader level, BlockPos pos, BlockState state, Direction side) {
        Axis axis = side.getAxis();
        if (axis.isVertical()) {
            return false;
        }
        if (isBeam(state) && !state.getValue(axis == Axis.X ? X : Z)) {
            return false;
        }
        BlockPos relative = pos.relative(side);
        BlockState sideState = level.getBlockState(relative);
        if (sideState.isAir()) {
            return false;
        }
        VoxelShape shape = sideState.getShape(level, relative);
        if (shape.isEmpty()) {
            return false;
        }
        return Block.isFaceFull(shape, side.getOpposite()) && sideState.isSolid();
    }

    // ------------------------------------------------------------ misc vanilla

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            Axis axis = state.getValue(AXIS);
            return state
                .setValue(X, state.getValue(Z))
                .setValue(Z, state.getValue(X))
                .setValue(AXIS, axis == Axis.X ? Axis.Z : axis == Axis.Z ? Axis.X : axis);
        }
        return state;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state;   // the I-profile is symmetric on both horizontal axes
    }
}

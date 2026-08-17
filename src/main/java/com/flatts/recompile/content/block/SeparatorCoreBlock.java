package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.SeparatorBlockEntity;
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
import org.jspecify.annotations.Nullable;

/**
 * ONE MACHINE, ONE VERB (#187). It separates. The word "grinder" is deliberately absent - that is
 * the Pulverizer's job (#189), and a tree where one word names two machines is one where the
 * confusion recurs.
 *
 * The Separator's core: it takes a mixed feed in the top and hands back distinct materials out the
 * bottom ({@code docs/gem_tier_spec.md}, art {@code docs/separator_model_spec.md}).
 *
 * <p><b>It has no inventory, and that is the design rather than an omission.</b> The machine is not a
 * {@code Container} and exposes no item capability, so nothing can push into it and no pipe can even
 * connect. It automates by <b>reaching out</b> instead: it eats loose items in its mouth, and it drains
 * a container standing on the chamber. A hopper under the chute catches what falls.
 *
 * <p>The pull is what makes that liveable. A hopper pointed down at the chamber is the first thing
 * anyone tries and it can never work, because there is nothing there to insert into. Draining the
 * container instead costs none of the properties above and removes the dead end.
 *
 * <p><b>3 wide x 2 deep x 2 tall</b>, twelve cells. The grinding bay is a <b>2x2</b> square on top and
 * the third column is solid housing, which is what gives the machine its bulk: a machine that is all
 * mouth reads as a hole in the ground rather than a machine with a hopper in it.
 *
 * <p>The four bay cells read as <b>one</b> opening rather than four blocks. Each is stamped at assembly
 * with which quarter of the shredder it shows; the quadrant models draw a rim only on their outer edges
 * and the floor textures are quarters of a single image, so both the border and the teeth run
 * continuously across the seams.
 *
 * <p>Two component types only: auto-assemble is all-or-nothing, so every extra component is another way
 * for a player to stand in front of a core that will not form.
 */
public class SeparatorCoreBlock extends MultiblockCoreBlock implements EntityBlock {

    public static final MapCodec<SeparatorCoreBlock> CODEC = simpleCodec(SeparatorCoreBlock::new);

    /** The machine is 3x2 in plan, so it has a front: the face the player was looking at. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Running: drives the animated chamber texture, the particles and the grind. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public SeparatorCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FORMED, false)
            .setValue(ACTIVE, false)
            .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends SeparatorCoreBlock> codec() {
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
        return rotationFor(defaultBlockState().setValue(FACING, player.getDirection().getOpposite()));
    }

    /**
     * The blueprint. Offsets are given for {@link Rotation#NONE} (facing north) and rotated from the
     * core's facing everywhere else.
     *
     * <pre>
     *   y=1   hous bay   bay      (a solid column for bulk, then the 2x2 bay)
     *         hous bay   bay
     *   y=0   CORE chute hous     (front: ONE chute, where everything comes out)
     *         hous hous  hous     (back)
     * </pre>
     */
    @Override
    protected Multiblock createBlueprint() {
        List<Multiblock.Cell> cells = new ArrayList<>();
        Block beam = RCBlocks.STEEL_I_BEAM.get();
        Block frame = RCBlocks.MACHINE_FRAME.get();

        // The bay is a 2x2 square, not the whole top. An earlier build made the chamber one ROW with
        // housing behind it, which was a real trap: the back half looked like part of the same surface
        // and silently refused material. A square bay beside a full-height column does not have that
        // problem, because the column plainly is not the opening. Steel, because a shredder's cutters
        // are steel and the yard's own material ties the machine to the region it stands in.
        for (int x = BAY_X; x < BAY_X + 2; x++) {
            for (int z = 0; z < 2; z++) {
                cells.add(new Multiblock.Cell(new Vec3i(x, 1, z), beam, RCBlocks.SEPARATOR_CHAMBER.get()));
            }
        }
        // ONE chute, and everything leaves through it. A recipe may one day split a feed into several
        // outputs at once; they all still come out of the same opening, which is what a real machine
        // does and what makes a single hopper under the chute enough to catch the lot. Same component
        // as the housing, a different formed block - which the framework allows and the Grass Spreader
        // already does.
        cells.add(new Multiblock.Cell(new Vec3i(1, 0, 0), frame, RCBlocks.SEPARATOR_CHUTE.get()));
        cells.add(new Multiblock.Cell(new Vec3i(2, 0, 0), frame, RCBlocks.SEPARATOR_HOUSING.get()));
        // THE MOTOR, back centre (owner, 2026-08-06). One machine-frame cell of the back row becomes
        // the drive, which is what puts the Separator behind a component rather than behind bulk
        // material alone - you cannot forge a working motor out of any quantity of scrap.
        //
        // It forms into ordinary housing rather than a bespoke block. The framework's rule is that a
        // formed cell gets its own look, and this is the same exception the chute beside it already
        // takes: a component may map to a formed block that is not itself. The machine is unchanged
        // once assembled, which is correct - an assembled Separator does not display its motor.
        for (int x = 0; x < 3; x++) {
            Block component = x == 1 ? RCBlocks.MOTOR.get() : frame;
            cells.add(new Multiblock.Cell(new Vec3i(x, 0, 1), component,
                RCBlocks.SEPARATOR_HOUSING.get()));
        }
        // The column beside the bay, top: solid. This is the bulk.
        for (int z = 0; z < 2; z++) {
            cells.add(new Multiblock.Cell(new Vec3i(BAY_X == 0 ? 2 : 0, 1, z), frame,
                RCBlocks.SEPARATOR_HOUSING.get()));
        }
        return new Multiblock(List.copyOf(cells));
    }

    /**
     * Which column the 2x2 bay starts at, in unrotated space. <b>Single source of truth</b> for the
     * blueprint, the mouth and the quadrant stamp, so moving the bay across the machine is one number
     * and the three cannot end up describing different machines.
     */
    private static final int BAY_X = 1;

    /** The four cells of the grinding bay, in world space. Not the whole top - the other column is
     *  housing, and dropping material on it does nothing. */
    public static List<BlockPos> chamberCells(Level level, BlockPos core) {
        List<BlockPos> out = new ArrayList<>();
        if (!(level.getBlockState(core).getBlock() instanceof SeparatorCoreBlock block)) {
            return out;
        }
        Rotation rotation = block.rotationFor(level.getBlockState(core));
        for (int x = BAY_X; x < BAY_X + 2; x++) {
            for (int z = 0; z < 2; z++) {
                out.add(core.offset(Multiblock.rotate(new Vec3i(x, 1, z), rotation)));
            }
        }
        return out;
    }

    /**
     * Tell every formed cell which way the machine faces, and each bay cell which quarter it shows.
     *
     * <p>Facing and the machine-position skin are stamped for every machine by
     * {@code MultiblockCoreBlock.stampSkin} before this runs; only the bay quadrant is specific enough
     * to belong here.
     *
     * <p>The quadrant is what makes four blocks read as <b>one</b> opening: the textures are quarters of
     * a single image, so the teeth run continuously across the seams instead of the pattern restarting
     * at every block edge. It is taken from the <b>unrotated</b> offset and the facing is applied as a
     * model rotation, which is exactly equivalent to turning the whole image.
     */
    @Override
    protected void onFormed(Level level, BlockPos pos) {
        BlockState coreState = level.getBlockState(pos);
        Rotation rotation = rotationFor(coreState);
        Direction facing = coreState.getValue(FACING);

        for (int x = BAY_X; x < BAY_X + 2; x++) {
            for (int z = 0; z < 2; z++) {
                BlockPos cell = pos.offset(Multiblock.rotate(new Vec3i(x, 1, z), rotation));
                BlockState state = level.getBlockState(cell);
                if (state.is(RCBlocks.SEPARATOR_CHAMBER.get())) {
                    // Relative to the bay's own corner, not the machine's, so the quadrants stay 0..3
                    // wherever the bay sits.
                    level.setBlock(cell, state
                        .setValue(SeparatorChamberBlock.QUADRANT, (x - BAY_X) + z * 2)
                        .setValue(SeparatorChamberBlock.FACING, facing), Block.UPDATE_ALL);
                }
            }
        }
    }

    /**
     * The volume material is accepted in. <b>Single source of truth</b>, shared by the machine and its
     * tests, so the two cannot disagree about where the mouth is.
     *
     * <p>Spans the chamber cells themselves and the block above them. That matters and was wrong once:
     * the chamber's top face is <b>recessed</b>, so a dropped stack settles down inside the well rather
     * than on top of it, which puts it in the chamber's own block space. Scanning only the block above
     * meant a player could watch an item sit visibly in the mouth while the machine ignored it.
     */
    public static @Nullable AABB mouth(Level level, BlockPos core) {
        List<BlockPos> cells = chamberCells(level, core);
        if (cells.isEmpty()) {
            return null;
        }
        AABB box = new AABB(cells.get(0));
        for (BlockPos cell : cells) {
            box = box.minmax(new AABB(cell));
        }
        // Up through the block above (an item mid-fall, or resting on the rim), and a little outward so
        // a stack nudged against the lip still counts.
        return box.expandTowards(0, 1, 0).inflate(0.25, 0, 0.25);
    }

    /**
     * Where output is thrown: just outside the one chute's front face, so a hopper there catches it.
     *
     * <p><b>A single point on purpose.</b> A recipe may produce a result and several byproducts in one
     * operation, and every one of them leaves here - so catching a machine's whole output never takes
     * more than one hopper, whatever a pack later writes into the recipe.
     */
    public static BlockPos outlet(Level level, BlockPos core) {
        if (!(level.getBlockState(core).getBlock() instanceof SeparatorCoreBlock block)) {
            return core;
        }
        Rotation rotation = block.rotationFor(level.getBlockState(core));
        return core.offset(Multiblock.rotate(new Vec3i(1, 0, -1), rotation));
    }

    // ---------------- the BlockEntity ----------------

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SeparatorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.@Nullable BlockEntityTicker<T>
            getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTicker(type, RCBlockEntities.SEPARATOR.get(), SeparatorBlockEntity::serverTick);
    }

    @SuppressWarnings("unchecked")
    private static <A extends BlockEntity, E extends BlockEntity> @Nullable BlockEntityTicker<A> createTicker(
            BlockEntityType<A> given, BlockEntityType<E> expected, BlockEntityTicker<? super E> ticker) {
        return expected == given ? (BlockEntityTicker<A>) ticker : null;
    }
}

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
import org.jspecify.annotations.Nullable;

/**
 * The Separator's core: an industrial grinder that drops material in the top and raw materials out the
 * bottom ({@code docs/gem_tier_spec.md}, art {@code docs/separator_model_spec.md}).
 *
 * <p><b>It has no inventory, and that is the design rather than an omission.</b> Material arrives as
 * dropped item entities above the chamber and leaves as dropped item entities at the chute. So the
 * machine is not a {@code Container} and exposes no item capability, which means no hopper and no pipe
 * can reach into it - and yet it automates perfectly well, because a dropper can throw items in from
 * above and a hopper picks up what falls out below. Automation happens <b>through the world</b> rather
 * than through the block.
 *
 * <p><b>3 wide x 2 deep x 2 tall</b>, twelve cells, four kinds. Two component types only: auto-assemble
 * is all-or-nothing, so every extra component is another way for a player to stand in front of a core
 * that will not form.
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
     *   y=1   chamber chamber chamber      (front row: where material goes in)
     *         housing housing housing      (back row)
     *   y=0   CORE    chute   chute        (front row: where material comes out)
     *         housing housing housing      (back row)
     * </pre>
     */
    @Override
    protected Multiblock createBlueprint() {
        List<Multiblock.Cell> cells = new ArrayList<>();
        Block beam = RCBlocks.STEEL_I_BEAM.get();
        Block frame = RCBlocks.MACHINE_FRAME.get();

        for (int x = 0; x < 3; x++) {
            // Top front: the chamber. Steel, because a shredder's cutters are steel and the yard's
            // own material ties the machine to the region it stands in.
            cells.add(new Multiblock.Cell(new Vec3i(x, 1, 0), beam, RCBlocks.SEPARATOR_CHAMBER.get()));
            // Top back and bottom back: housing.
            cells.add(new Multiblock.Cell(new Vec3i(x, 1, 1), frame, RCBlocks.SEPARATOR_HOUSING.get()));
            cells.add(new Multiblock.Cell(new Vec3i(x, 0, 1), frame, RCBlocks.SEPARATOR_HOUSING.get()));
        }
        // Bottom front, beside the core: the chute. Same component as the housing, a different formed
        // block - which the framework allows and the Grass Spreader already does.
        cells.add(new Multiblock.Cell(new Vec3i(1, 0, 0), frame, RCBlocks.SEPARATOR_CHUTE.get()));
        cells.add(new Multiblock.Cell(new Vec3i(2, 0, 0), frame, RCBlocks.SEPARATOR_CHUTE.get()));
        return new Multiblock(List.copyOf(cells));
    }

    /** Where material is scanned for: the space directly above each chamber cell. */
    public static List<BlockPos> intakes(Level level, BlockPos core) {
        List<BlockPos> out = new ArrayList<>();
        if (!(level.getBlockState(core).getBlock() instanceof SeparatorCoreBlock block)) {
            return out;
        }
        Rotation rotation = block.rotationFor(level.getBlockState(core));
        for (int x = 0; x < 3; x++) {
            out.add(core.offset(Multiblock.rotate(new Vec3i(x, 2, 0), rotation)));
        }
        return out;
    }

    /** Where output is thrown: just outside the chute's front face, so a hopper there catches it. */
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

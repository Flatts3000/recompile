package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.PulverizerBlockEntity;
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
 * The Pulverizer: a hammer mill that reduces material to powder (#189).
 *
 * <p><b>The Separator divides; this reduces.</b> The test is whether an operation changes what the
 * material is or only how fine it is - Spent Abrasive to diamond changes what it is, E-Scrap to circuit
 * powder does not. Every real e-waste line runs shredder, then mill, then separation; the mod had the
 * shredder and no machine for fine comminution, which is the stage that liberates metal from board so a
 * smelter can recover it.
 *
 * <p><b>A hammer mill, specifically not a tumbling mill.</b> A closed housing with a rotor of swinging
 * hammers: impact, not tearing and not tumbling. The ball mill was the first proposal and is wrong for
 * a reason worth keeping - the Trommel is a rotating drum and so is a ball mill, and at 16px they would
 * collide. A boxy mill is also the more accurate machine for e-waste, so accuracy and legibility point
 * the same way for once. Three silhouettes that cannot be confused: the Separator's open-topped well,
 * the Trommel's long drum, this closed cube.
 *
 * <p><b>2x2x2, and the scale is the honest part</b> (owner, 2026-08-16). A real hammer mill is a
 * multi-tonne machine on its own foundation, comparable in bulk to the shredder - "compact" is only
 * true next to a trommel. A dense cube reads distinctly against the Separator's flat slab and the
 * Trommel's long line without pretending the machine is benchtop.
 *
 * <p>The Motor is the drive cell. A mill turns a rotor, and no quantity of scrap forges a working
 * motor - so the machine is gated on a found component rather than on bulk material, the same argument
 * the Separator and the Trommel make.
 */
public class PulverizerCoreBlock extends MultiblockCoreBlock implements EntityBlock {

    public static final MapCodec<PulverizerCoreBlock> CODEC = simpleCodec(PulverizerCoreBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** True while the rotor is turning. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public PulverizerCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends PulverizerCoreBlock> codec() {
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
     * The blueprint: a 2x2x2 cube, core at one bottom corner.
     *
     * <p>Steel above and frame below, which is the machine read as a section rather than as decoration.
     * The rotor and its breaker plates take the impact, so the upper half is Steel I-Beam - the yard's
     * own material, the same argument the Separator's cutters and the Trommel's screen make. The base
     * is Machine Frame, because it carries the thing rather than being hit by it.
     */
    @Override
    protected Multiblock createBlueprint() {
        List<Multiblock.Cell> cells = new ArrayList<>();
        Block beam = RCBlocks.STEEL_I_BEAM.get();
        Block frame = RCBlocks.MACHINE_FRAME.get();
        Block housing = RCBlocks.PULVERIZER_HOUSING.get();

        // THE MOTOR, beside the core on the base. One drive, and what the machine is really gated on.
        cells.add(new Multiblock.Cell(new Vec3i(1, 0, 0), RCBlocks.MOTOR.get(), housing));
        cells.add(new Multiblock.Cell(new Vec3i(0, 0, 1), frame, housing));
        cells.add(new Multiblock.Cell(new Vec3i(1, 0, 1), frame, housing));
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                cells.add(new Multiblock.Cell(new Vec3i(x, 1, z), beam, housing));
            }
        }
        return new Multiblock(List.copyOf(cells));
    }

    /**
     * Where material is accepted: the volume on the roof, across the whole top.
     *
     * <p>A hammer mill is fed from above by gravity. Single source of truth, shared with the tests, so
     * the machine and what proves it works cannot disagree about where the opening is.
     */
    public static @Nullable AABB mouth(Level level, BlockPos core) {
        if (!(level.getBlockState(core).getBlock() instanceof PulverizerCoreBlock block)) {
            return null;
        }
        Rotation rotation = block.rotationFor(level.getBlockState(core));
        AABB box = null;
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                BlockPos cell = core.offset(Multiblock.rotate(new Vec3i(x, 1, z), rotation));
                AABB above = new AABB(cell).move(0, 1, 0);
                box = box == null ? above : box.minmax(above);
            }
        }
        return box;
    }

    /** Where powder leaves: in front of the core at base level, under the machine's face. */
    public static BlockPos outlet(Level level, BlockPos core) {
        if (!(level.getBlockState(core).getBlock() instanceof PulverizerCoreBlock block)) {
            return core;
        }
        Rotation rotation = block.rotationFor(level.getBlockState(core));
        return core.offset(Multiblock.rotate(new Vec3i(0, 0, -1), rotation));
    }

    /** The direction the discharge throws, so powder leaves the machine rather than along it. */
    public static Direction dischargeFacing(Level level, BlockPos core) {
        if (!(level.getBlockState(core).getBlock() instanceof PulverizerCoreBlock block)) {
            return Direction.NORTH;
        }
        return block.rotationFor(level.getBlockState(core)).rotate(Direction.NORTH);
    }

    // ---------------- the BlockEntity ----------------

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PulverizerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return type != RCBlockEntities.PULVERIZER.get() ? null
            : (lvl, pos, st, be) -> PulverizerBlockEntity.serverTick(
                (net.minecraft.server.level.ServerLevel) lvl, pos, st, (PulverizerBlockEntity) be);
    }
}

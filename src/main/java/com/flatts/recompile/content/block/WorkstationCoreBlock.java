package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The Scrap Workstation's core (design P2.10): the controller that ties the scrap-interaction blocks
 * into one system. You place it and build the bench around it; while formed, the members share
 * storage.
 *
 * <p><b>Every cell is {@code formed == component}</b> - no block is replaced, no formed model exists.
 * The barrel stays a barrel, the bins stay bins; forming only wires them to the core (and, thanks to
 * the {@code form()} fix, leaves their state alone). That is the whole reason this is a template +
 * network rather than a machine that merges into one object.
 *
 * <p>The layout, with the core at the origin (back-left, a bin sits on it too - the core doubles as a
 * shelf leg): a front counter (crafting, workbench, sorting, burn barrel, barrel, and the junk bin) at
 * {@code z = -1}; a back row of five Machine Frames at {@code z = 0}; and six material bins on top of
 * the back row (including one on the core) at {@code y = 1}. 18 blocks, 6 wide x 2 deep x 2 tall.
 *
 * <p><b>Directional.</b> The blueprint is authored for a core facing {@link Direction#NORTH} (the
 * counter runs along {@code z = -1}) and rotated to the core's {@link #FACING} at validate/form/read
 * time. {@link #getStateForPlacement} sets the facing to the <em>opposite</em> of the player's look
 * direction (see {@link #facingForPlacement}), so the bench builds toward the player: they end up
 * standing at the counter with the shelf of bins behind it, not facing the back of the shelf.
 */
public class WorkstationCoreBlock extends MultiblockCoreBlock {

    public static final MapCodec<WorkstationCoreBlock> CODEC = simpleCodec(WorkstationCoreBlock::new);

    /** Which way the bench faces; the blueprint (authored for NORTH) is rotated to match. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public WorkstationCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends WorkstationCoreBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, facingForPlacement(context.getHorizontalDirection()));
    }

    /**
     * The core facing to store for a player looking in {@code look}. The <b>opposite</b> of the look
     * direction: the counter is authored at {@code z = -1} (behind a NORTH core), so facing the core
     * away from the player swings the counter around to the player's side. The single place the reverse
     * lives, so {@link #getStateForPlacement} and the held-item preview stay in lockstep.
     */
    public static Direction facingForPlacement(Direction look) {
        return look.getOpposite();
    }

    @Override
    protected Rotation rotationFor(BlockState state) {
        return rotationFromFacing(state.getValue(FACING));
    }

    /**
     * The rotation carrying the NORTH-authored blueprint to a core facing {@code facing}. Public so
     * the network can rotate a member offset back the same way the structure was built.
     */
    public static Rotation rotationFromFacing(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE; // NORTH (and the non-horizontal cases, which placement never sets)
        };
    }

    @Override
    protected Multiblock createBlueprint() {
        List<Multiblock.Cell> cells = new ArrayList<>();

        // Back row: five Machine Frames beside the core (the core is the sixth support).
        for (int x = 1; x <= 5; x++) {
            cells.add(cell(x, 0, 0, RCBlocks.MACHINE_FRAME.get()));
        }
        // Shelf: six material bins on top of the back row - one on the core, five on the frames.
        for (int x = 0; x <= 5; x++) {
            cells.add(cell(x, 1, 0, RCBlocks.SCRAP_BIN.get()));
        }
        // Front counter, hand level: the five workstations, then the junk bin at the right end.
        cells.add(cell(0, 0, -1, RCBlocks.SCRAP_CRAFTING_TABLE.get()));
        cells.add(cell(1, 0, -1, RCBlocks.RECOMPILE_WORKBENCH.get()));
        cells.add(cell(2, 0, -1, RCBlocks.SORTING_TARP.get()));
        cells.add(cell(3, 0, -1, RCBlocks.BURN_BARREL.get()));
        cells.add(cell(4, 0, -1, RCBlocks.SCRAP_BARREL.get()));
        cells.add(cell(5, 0, -1, RCBlocks.SCRAP_BIN.get()));   // junk bin, at hand level

        return new Multiblock(List.copyOf(cells));
    }

    /** A cell that stays as-is when formed: its formed block is its own component. */
    private static Multiblock.Cell cell(int x, int y, int z, Block block) {
        return new Multiblock.Cell(new Vec3i(x, y, z), block, block);
    }
}

package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Block;

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
 * <p>Fixed orientation for v1 - the blueprint is a fixed set of offsets and the framework does not yet
 * rotate a blueprint by a core's facing (that, and the placement outline, are framework enhancements
 * deferred with this).
 */
public class WorkstationCoreBlock extends MultiblockCoreBlock {

    public static final MapCodec<WorkstationCoreBlock> CODEC = simpleCodec(WorkstationCoreBlock::new);

    public WorkstationCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends WorkstationCoreBlock> codec() {
        return CODEC;
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

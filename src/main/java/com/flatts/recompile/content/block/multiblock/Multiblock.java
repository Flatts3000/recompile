package com.flatts.recompile.content.block.multiblock;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A multiblock blueprint: which block must sit at which offset from the core, and what each cell
 * becomes once the machine forms (design: {@code docs/multiblock_system_spec.md}).
 *
 * <p><b>One source of truth.</b> The same blueprint drives validation, the auto-assemble step that
 * builds the machine out of the player's inventory, and the GameTests. Define a machine's shape
 * once here and none of those three can drift from each other.
 *
 * <p>The pattern is Immersive Engineering's, trimmed hard: IE matches an NBT {@code
 * StructureTemplate} because its structures are large and irregular. Ours are short vertical
 * columns, so a list of offsets is the whole algorithm. What we keep from IE is the part that
 * matters - a <b>master</b> core plus <b>dummy</b> cells that redirect to it, so a formed machine
 * behaves as one object rather than a pile of blocks.
 */
public record Multiblock(List<Cell> cells) {

    /**
     * One cell of the blueprint.
     *
     * @param offset    where it sits relative to the core
     * @param component the loose block the player must place there
     * @param formed    what that cell becomes once the machine forms - the machine's <em>bespoke</em>
     *                  appearance, not the loose component's. A formed machine looks like a machine,
     *                  not like the parts it was built from.
     */
    public record Cell(Vec3i offset, Block component, Block formed) {

        public BlockPos at(BlockPos core) {
            return core.offset(offset);
        }
    }

    /** A single cell directly above the core - the shape both first machines use. */
    public static Multiblock stackedOn(Block component, Block formed) {
        return new Multiblock(List.of(new Cell(new Vec3i(0, 1, 0), component, formed)));
    }

    /** True when every cell already holds its loose component, ready to form. */
    public boolean matches(BlockGetter level, BlockPos core) {
        for (Cell cell : cells) {
            if (!level.getBlockState(cell.at(core)).is(cell.component())) {
                return false;
            }
        }
        return true;
    }

    /** True when every cell holds its formed block - i.e. this machine is currently assembled. */
    public boolean isFormed(BlockGetter level, BlockPos core) {
        for (Cell cell : cells) {
            if (!level.getBlockState(cell.at(core)).is(cell.formed())) {
                return false;
            }
        }
        return true;
    }

    /** Swap every loose component for its formed counterpart. Call only when {@link #matches}. */
    public void form(Level level, BlockPos core) {
        for (Cell cell : cells) {
            level.setBlock(cell.at(core), cell.formed().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Tear the machine apart, dropping each formed cell's loot (which is the loose component again)
     * and clearing it.
     *
     * <p>Clears with {@code setBlock(AIR)} after {@code dropResources} rather than
     * {@code destroyBlock}: destroying a cell would re-enter that cell's own removal handler, which
     * disbands the machine, which destroys the cell... Breaking that loop is the whole reason this
     * lives in one place.
     */
    public void disband(Level level, BlockPos core, boolean drop) {
        for (Cell cell : cells) {
            BlockPos pos = cell.at(core);
            BlockState state = level.getBlockState(pos);
            if (!state.is(cell.formed())) {
                continue;
            }
            if (drop) {
                Block.dropResources(state, level, pos);
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Every cell that is currently air or replaceable - what auto-assemble needs to fill. */
    public boolean roomToAssemble(BlockGetter level, BlockPos core) {
        for (Cell cell : cells) {
            if (!level.getBlockState(cell.at(core)).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }
}

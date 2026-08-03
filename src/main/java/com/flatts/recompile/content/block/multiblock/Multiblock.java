package com.flatts.recompile.content.block.multiblock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

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
     * <b>A machine may never take another machine's core as a component.</b>
     *
     * <p>Nesting cores looks appealing - "build the spreader around a rain collector" - and it does
     * not work: the inner core is live. It watches its own neighbours, and the moment one changes it
     * tries to assemble <em>itself</em>, placing its own components into cells the outer machine has
     * already claimed. Two machines then fight over the same blocks.
     *
     * <p>The fix is to use an inert component and put the other machine in its <em>recipe</em>, which
     * keeps the progression ("you need a collector first") without putting a second brain inside the
     * structure. Checked here so the rule fails loudly at the first blueprint build rather than as a
     * baffling in-world tug of war.
     */
    public Multiblock {
        for (Cell cell : cells) {
            if (cell.component() instanceof MultiblockCoreBlock) {
                throw new IllegalArgumentException(
                    "a multiblock component may not be another machine's core: " + cell.component()
                        + " - make it an inert block and consume the machine in its recipe instead");
            }
        }
    }

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
            return at(core, Rotation.NONE);
        }

        /** The cell's world position for a core with the given facing rotation. */
        public BlockPos at(BlockPos core, Rotation rotation) {
            return core.offset(rotate(offset, rotation));
        }
    }

    /**
     * The machine's bounding box in blueprint space, <b>including the core at the origin</b>.
     *
     * <p>Returned as {@code [minX, minY, minZ, width, height, depth]}. This is what lets a machine be
     * skinned as one object: the box is the canvas, and a cell's position within it is where on that
     * canvas the cell sits.
     */
    public int[] bounds() {
        int minX = 0;
        int minY = 0;
        int minZ = 0;
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;
        for (Cell cell : cells) {
            Vec3i offset = cell.offset();
            minX = Math.min(minX, offset.getX());
            minY = Math.min(minY, offset.getY());
            minZ = Math.min(minZ, offset.getZ());
            maxX = Math.max(maxX, offset.getX());
            maxY = Math.max(maxY, offset.getY());
            maxZ = Math.max(maxZ, offset.getZ());
        }
        return new int[] {minX, minY, minZ, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1};
    }

    /**
     * Every position the machine occupies, the core's origin included, in a canonical order.
     *
     * <p>Sorted bottom to top, then back to front, then left to right. Sorted rather than taken in
     * declaration order because {@link #cellIndex} is a blockstate value: declaration order would
     * silently renumber every cell the moment someone reorders {@code createBlueprint}, and the
     * renumbering shows up as the machine's skin scrambling, which nobody would connect to a
     * harmless-looking edit.
     */
    public List<Vec3i> skinOrder() {
        List<Vec3i> out = new ArrayList<>();
        out.add(Vec3i.ZERO);
        for (Cell cell : cells) {
            if (!out.contains(cell.offset())) {
                out.add(cell.offset());
            }
        }
        out.sort(Comparator.comparingInt(Vec3i::getY)
            .thenComparingInt(Vec3i::getZ)
            .thenComparingInt(Vec3i::getX));
        return out;
    }

    /**
     * Where a cell sits in the machine, as a single number.
     *
     * <p><b>A dense index over the cells the machine actually has</b>, not a position in its bounding
     * box. The difference is not academic: the Grass Spreader is a sparse cross, so its box is
     * thirty-six positions for about seven real cells, and indexing by box position pushed it past any
     * sane blockstate ceiling while most of the numbers addressed empty air. The index only has to
     * identify a cell; the tool that cuts the skin knows the layout and can map it back to a position.
     *
     * @return an index in {@code [0, cell count]}, or -1 if the offset is not part of this machine
     */
    public int cellIndex(Vec3i offset) {
        return skinOrder().indexOf(offset);
    }

    /**
     * Rotate an offset about the vertical axis (MC's {@code BlockPos.rotate} convention), so a
     * blueprint defined for one facing can be placed, validated and read at any of the four. Vertical
     * columns (the Grass Spreader, Rain Collector) are rotation-invariant and always pass
     * {@link Rotation#NONE}; the Workstation, a horizontal layout, uses its core's facing.
     */
    public static Vec3i rotate(Vec3i offset, Rotation rotation) {
        return switch (rotation) {
            case NONE -> offset;
            case CLOCKWISE_90 -> new Vec3i(-offset.getZ(), offset.getY(), offset.getX());
            case CLOCKWISE_180 -> new Vec3i(-offset.getX(), offset.getY(), -offset.getZ());
            case COUNTERCLOCKWISE_90 -> new Vec3i(offset.getZ(), offset.getY(), -offset.getX());
        };
    }

    /** A single cell directly above the core - the shape both first machines use. */
    public static Multiblock stackedOn(Block component, Block formed) {
        return new Multiblock(List.of(new Cell(new Vec3i(0, 1, 0), component, formed)));
    }

    /** True when every cell already holds its loose component, ready to form. */
    public boolean matches(BlockGetter level, BlockPos core) {
        return matches(level, core, Rotation.NONE);
    }

    public boolean matches(BlockGetter level, BlockPos core, Rotation rotation) {
        for (Cell cell : cells) {
            if (!level.getBlockState(cell.at(core, rotation)).is(cell.component())) {
                return false;
            }
        }
        return true;
    }

    /** True when every cell holds its formed block - i.e. this machine is currently assembled. */
    public boolean isFormed(BlockGetter level, BlockPos core) {
        return isFormed(level, core, Rotation.NONE);
    }

    public boolean isFormed(BlockGetter level, BlockPos core, Rotation rotation) {
        for (Cell cell : cells) {
            if (!level.getBlockState(cell.at(core, rotation)).is(cell.formed())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Swap every loose component for its formed counterpart. Call only when {@link #matches}.
     *
     * <p>A formed block that carries {@code HORIZONTAL_FACING} is turned to <b>face the core</b>.
     * Parts that plumb or bolt into the middle of a machine have to point at it, and a cell already
     * knows where the core is - so orienting here means no machine has to solve it again, and a
     * component placed on any side comes out pointing the right way.
     */
    public void form(Level level, BlockPos core) {
        form(level, core, Rotation.NONE);
    }

    public void form(Level level, BlockPos core, Rotation rotation) {
        for (Cell cell : cells) {
            BlockPos at = cell.at(core, rotation);
            // Preserve stateful components: when the cell's formed block IS its component (the
            // Workstation's every cell, the Grass Spreader's Solar Panel), the block is already in
            // place - re-setting it to a default state would wipe a barrel's contents, a bin's
            // binding, a furnace's smelt. Only touch the cell when the block actually changes.
            if (level.getBlockState(at).is(cell.formed())) {
                continue;
            }
            BlockState formed = cell.formed().defaultBlockState();
            Vec3i offset = rotate(cell.offset(), rotation);
            if (formed.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                    && (offset.getX() != 0 || offset.getZ() != 0)) {
                formed = formed.setValue(BlockStateProperties.HORIZONTAL_FACING,
                    Direction.getApproximateNearest(-offset.getX(), 0, -offset.getZ()));
            }
            level.setBlock(at, formed, Block.UPDATE_ALL);
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
        disband(level, core, Rotation.NONE, drop);
    }

    public void disband(Level level, BlockPos core, Rotation rotation, boolean drop) {
        for (Cell cell : cells) {
            BlockPos pos = cell.at(core, rotation);
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
        return roomToAssemble(level, core, Rotation.NONE);
    }

    public boolean roomToAssemble(BlockGetter level, BlockPos core, Rotation rotation) {
        for (Cell cell : cells) {
            if (!level.getBlockState(cell.at(core, rotation)).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }
}

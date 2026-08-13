package com.flatts.recompile.content.block.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A non-core cell of a formed multiblock - Immersive Engineering's "dummy" (design:
 * {@code docs/multiblock_system_spec.md}).
 *
 * <p>It stores nothing. Its whole job is to make a formed machine behave as <b>one object</b>:
 * interacting with it interacts with the core, and breaking it takes the machine down. That is the
 * piece worth copying from IE exactly - without it a formed machine is just a stack of blocks that
 * happen to touch.
 *
 * <p>A dummy is never crafted or given; it exists only inside a formed machine, which is why it has
 * no item. Its <em>appearance</em> is the machine's bespoke formed look and belongs to the subclass,
 * while the behaviour here is shared - the split the spec's rendering correction insists on.
 */
public abstract class MultiblockDummyBlock extends Block {

    /** How far below to look for the master. Generous enough for any stack we plan to build. */
    private static final int SEARCH_DEPTH = 4;

    /**
     * How far HORIZONTALLY a cell will look for its master.
     *
     * <p>This was 1, which quietly capped every machine at three blocks wide. A cell further out
     * simply never found its core, so breaking it did not disband the machine - it left a formed
     * machine with a hole in it, and the only symptom was a build that kept working while missing a
     * part. The Separator's far column is already inside that blind spot; the Trommel is four long
     * and was entirely outside it.
     *
     * <p>Widening is cheap because this runs on a break, not on a tick, and a wrong match is not a
     * risk: the loop below only accepts a candidate whose own blueprint claims this exact position.
     *
     * <p>{@code no_blueprint_reaches_past_the_core_search} fails the build if a machine ever grows
     * past this, so the next one to outgrow it finds out at build time rather than in a playtest.
     */
    public static final int SEARCH_RADIUS = 4;

    protected MultiblockDummyBlock(Properties properties) {
        super(properties);
    }

    /**
     * Find the core this cell belongs to: a {@link MultiblockCoreBlock} nearby whose blueprint actually
     * claims this position for this block. Checking the blueprint (not just "a core is near") means an
     * unrelated core cannot adopt us.
     *
     * <p>A core sits at or below its cells (cell offsets have {@code y >= 0}) and within one block
     * horizontally, so this small box covers every shape we build - the vertical towers (Grass Spreader,
     * Rain Collector) and the Compost Heap's 2x2x2 alike, where cells sit <em>beside</em> the core, not
     * only above it.
     */
    public static @Nullable BlockPos findCore(Level level, BlockPos pos) {
        for (int dy = 0; dy <= SEARCH_DEPTH; dy++) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    if (dx == 0 && dz == 0 && dy == 0) {
                        continue;   // the cell itself is never the core
                    }
                    BlockPos candidate = pos.offset(dx, -dy, dz);
                    BlockState candidateState = level.getBlockState(candidate);
                    if (!(candidateState.getBlock() instanceof MultiblockCoreBlock core)) {
                        continue;
                    }
                    Rotation rotation = core.rotationFor(candidateState);
                    for (Multiblock.Cell cell : core.blueprint().cells()) {
                        if (cell.at(candidate, rotation).equals(pos)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Right-clicking any part of the machine is right-clicking the machine. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos core = findCore(level, pos);
        if (core == null) {
            return InteractionResult.PASS;
        }
        BlockState coreState = level.getBlockState(core);
        return coreState.useItemOn(stack, level, player, hand,
            hit.withPosition(core));
    }

    /** And an empty-handed right-click on any part is one on the machine (the Compost Heap harvest). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        BlockPos core = findCore(level, pos);
        if (core == null) {
            return InteractionResult.PASS;
        }
        return level.getBlockState(core).useWithoutItem(level, player, hit.withPosition(core));
    }

    /**
     * Breaking a dummy disbands the whole machine. This cell's own loot has already dropped through
     * the normal break, so the core is torn down without re-dropping it here - {@code disband} skips
     * cells that are no longer their formed block, which this one is not by the time we run.
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        BlockPos core = MultiblockDummyBlock.findCore(level, pos);
        if (core == null) {
            return;
        }
        BlockState coreState = level.getBlockState(core);
        if (!MultiblockCoreBlock.isFormed(coreState)) {
            return;
        }
        // Drop the core's own contents, then clear it. dropResources + setBlock rather than
        // destroyBlock, so the core's removal handler cannot bounce back into this one.
        Block.dropResources(coreState, level, core, level.getBlockEntity(core));
        MultiblockCoreBlock.disband(level, core, true);
        level.removeBlock(core, false);
    }
}

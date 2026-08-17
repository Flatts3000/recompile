package com.flatts.recompile.content.block.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.gamerules.GameRules;
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
 * <p><b>Most</b> dummies are never crafted or given: a formed cell exists only inside an assembled
 * machine, which is why it has no item and why {@code MultiblockParts} hides it from JEI. Two
 * subclasses are deliberately both - the Water Tank and the Solar Panel are craftable blocks you place
 * by hand, and standalone they behave like ordinary blocks, since {@link #findCore} returns null and
 * every override here falls through. Their <em>appearance</em> is the machine's bespoke formed look and
 * belongs to the subclass, while the behaviour here is shared - the split the spec's rendering
 * correction insists on.
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
     * <p>A core sits at or below its cells (cell offsets have {@code y >= 0}) and within
     * {@link #SEARCH_RADIUS} blocks horizontally, so this box covers every shape we build - the vertical towers (Grass Spreader,
     * Rain Collector) and the Compost Heap's 2x2x2 alike, where cells sit <em>beside</em> the core, not
     * only above it.
     */
    public static @Nullable BlockPos findCore(Level level, BlockPos pos) {
        // NEAREST FIRST, and only a FORMED core whose cell actually matches this block.
        //
        // The search box was one block wide and is now four, which is 81 positions per layer instead
        // of 9 - and it returned the FIRST core that merely claimed this position. So an unformed core
        // dropped anywhere in that box could shadow the real one: its blueprint claims the same cells,
        // isFormed is false, and the break hook returns without disbanding, leaving exactly the
        // "formed machine with a hole in it" the widening was meant to prevent. Three guards close it:
        // scan outward so the true master is reached first, and ignore cores that are not formed.
        //
        // NOT a check that the block here matches the cell's formed block, which was tried and is
        // wrong: this runs from the removal hook, by which point the block is already AIR, so the
        // check can never pass and every machine stops disbanding. Eight tests said so at once.
        for (int ring = 1; ring <= Math.max(SEARCH_RADIUS, SEARCH_DEPTH); ring++) {
            for (int dy = 0; dy <= Math.min(ring, SEARCH_DEPTH); dy++) {
                int span = Math.min(ring, SEARCH_RADIUS);
                for (int dx = -span; dx <= span; dx++) {
                    for (int dz = -span; dz <= span; dz++) {
                        // only the shell of this ring, so closer candidates are never skipped past
                        if (Math.max(Math.max(Math.abs(dx), Math.abs(dz)), dy) != ring) {
                            continue;
                        }
                        BlockPos candidate = pos.offset(dx, -dy, dz);
                        BlockState candidateState = level.getBlockState(candidate);
                        if (!(candidateState.getBlock() instanceof MultiblockCoreBlock core)
                                || !MultiblockCoreBlock.isFormed(candidateState)) {
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
        }
        return null;
    }

    /**
     * A formed cell gives back <b>nothing of its own</b>. The machine decides what a cell returns.
     *
     * <p><b>Why this cannot be a loot table.</b> A formed block is not one component: the Separator's
     * Motor cell and its seven Machine Frame cells all form into {@code separator_housing}, and the
     * Trommel's Motor cell and its frame cell both form into {@code trommel_stand}. A per-block table
     * has no way to tell them apart, so whatever it names is wrong for one of them - and it was.
     * Breaking the Separator's motor cell by hand handed back a Machine Frame, silently converting the
     * rarest part in the machine into the commonest, which is verbatim the regression {@code
     * Multiblock.disband} was fixed for on 2026-08-07. Only the disband path was fixed; this one was
     * not, so the bug stayed reachable by simply hitting the block.
     *
     * <p>The Trommel's three cells were worse: they dropped <em>themselves</em> - items with no recipe,
     * hidden from JEI as unobtainable parts - while the Motor was destroyed.
     *
     * <p>So the blueprint is the single source of truth for what disassembly returns, on every path.
     * {@link #affectNeighborsAfterRemoval} pops the broken cell's own component and
     * {@code Multiblock.disband} handles the rest.
     */
    @Override
    protected java.util.List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return java.util.List.of();
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
        // THIS CELL'S COMPONENT, which nothing else will return. disband skips cells that are already
        // air, and this one is - the break took it out before the hook ran - so without this the part
        // the player broke is the one part they do not get back.
        if (level.getBlockState(core).getBlock() instanceof MultiblockCoreBlock owner) {
            Rotation rotation = owner.rotationFor(coreState);
            for (Multiblock.Cell cell : owner.blueprint().cells()) {
                if (cell.at(core, rotation).equals(pos)
                        && level.getGameRules().get(GameRules.BLOCK_DROPS)) {
                    Block.popResource(level, pos, new ItemStack(cell.component()));
                    break;
                }
            }
        }
        // Drop the core's own contents, then clear it. dropResources + setBlock rather than
        // destroyBlock, so the core's removal handler cannot bounce back into this one.
        Block.dropResources(coreState, level, core, level.getBlockEntity(core));
        MultiblockCoreBlock.disband(level, core, true);
        level.removeBlock(core, false);
    }
}

package com.flatts.recompile.content.block.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/**
 * The master block of a multiblock: the piece you place, and the only piece that knows anything
 * (design: {@code docs/multiblock_system_spec.md}).
 *
 * <p><b>The build flow.</b> Placing a core always succeeds, as an inert unformed block - it is
 * never refused for want of parts. If the player happens to be carrying the components, they are
 * placed and consumed in that same action and the machine forms immediately; otherwise the core
 * sits and waits, and stacking the components by hand forms it. One validation, two ways in.
 *
 * <p><b>No BlockEntity for the structure.</b> {@link #FORMED} is a blockstate and the cells are
 * read from the world, so nothing about the assembly is serialised and nothing can desync. A
 * subclass may still carry a BlockEntity for its own contents (the rain collector's tank does).
 */
public abstract class MultiblockCoreBlock extends Block {

    /** Whether the machine is assembled. Drives behaviour, and is worth surfacing in Jade. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    private volatile @Nullable Multiblock cachedBlueprint;

    protected MultiblockCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    /**
     * Build this machine's blueprint. Called <b>once</b>, lazily - not from the constructor, because
     * a blueprint names other blocks and those are not resolvable while blocks are still registering.
     */
    protected abstract Multiblock createBlueprint();

    /**
     * Called once, server-side, right after the machine assembles. Override to start work - a
     * machine that runs on scheduled ticks books its first one here, so an unformed core costs
     * nothing at all rather than polling to discover it is still unformed.
     */
    protected void onFormed(Level level, BlockPos pos) {
    }

    /** Called once, server-side, right after the machine comes apart. */
    protected void onDisbanded(Level level, BlockPos pos) {
    }

    /**
     * The shape this core assembles into, memoized.
     *
     * <p>Memoized rather than rebuilt because {@link #neighborChanged} calls this, and that fires on
     * every adjacent block update - a player mining beside a machine would otherwise churn a fresh
     * record, list and {@code Vec3i} per tick of noise. Caching here rather than in each subclass
     * means a future machine cannot forget to.
     */
    public final Multiblock blueprint() {
        Multiblock cached = cachedBlueprint;
        if (cached == null) {
            cached = createBlueprint();
            cachedBlueprint = cached;
        }
        return cached;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    public static boolean isFormed(BlockState state) {
        return state.hasProperty(FORMED) && state.getValue(FORMED);
    }

    // ---------------- formation ----------------

    /**
     * On placement, try to build the whole machine out of the placer's inventory. This is the
     * convenience path over hand-stacking, not a separate mechanism - it ends in the same
     * {@link #tryForm}.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level.isClientSide()) {
            return;
        }
        // Sneak-place suppresses auto-assembly, so a bare core can always be placed deliberately.
        // Without this a creative player could never place one at all - creative "has" every
        // component, so assembly would always fire and there would be no way to get the unformed
        // block (for a partial build, or just as decor).
        if (by instanceof Player player && !player.isShiftKeyDown()) {
            autoAssemble(level, pos, player);
        }
        tryForm(level, pos);
    }

    /**
     * Place each missing component from the player's inventory, consuming it. Does nothing unless
     * <em>every</em> cell can be filled - a half-built machine from a partial inventory would be a
     * worse outcome than leaving the core plainly unformed.
     */
    private void autoAssemble(Level level, BlockPos pos, Player player) {
        Multiblock blueprint = blueprint();
        if (!blueprint.roomToAssemble(level, pos)) {
            return;
        }
        for (Multiblock.Cell cell : blueprint.cells()) {
            if (!hasComponent(player, cell)) {
                return;   // can't complete it - leave the core unformed rather than half-built
            }
        }
        for (Multiblock.Cell cell : blueprint.cells()) {
            consumeComponent(player, cell);
            level.setBlock(cell.at(pos), cell.component().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static boolean hasComponent(Player player, Multiblock.Cell cell) {
        if (player.getAbilities().instabuild) {
            return true;   // creative: build it without taking anything
        }
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(cell.component().asItem()) && !stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void consumeComponent(Player player, Multiblock.Cell cell) {
        if (player.getAbilities().instabuild) {
            return;
        }
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(cell.component().asItem()) && !stack.isEmpty()) {
                stack.shrink(1);
                return;
            }
        }
    }

    /**
     * Form if the blueprint is satisfied. The static entry point the GameTests drive directly,
     * rather than simulating a placement - the convention {@code SortableBlock.sortOnce} set.
     *
     * @return true if the machine formed on this call
     */
    public static boolean tryForm(Level level, BlockPos pos) {
        if (!(level.getBlockState(pos).getBlock() instanceof MultiblockCoreBlock core)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        Multiblock blueprint = core.blueprint();
        if (isFormed(state) || !blueprint.matches(level, pos)) {
            return false;
        }
        blueprint.form(level, pos);
        level.setBlock(pos, state.setValue(FORMED, true), Block.UPDATE_ALL);
        core.onFormed(level, pos);
        return true;
    }

    /** Clear the machine's cells (dropping their loot) and mark the core unformed. */
    public static void disband(Level level, BlockPos pos, boolean drop) {
        if (!(level.getBlockState(pos).getBlock() instanceof MultiblockCoreBlock core)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        core.blueprint().disband(level, pos, drop);
        if (isFormed(state)) {
            level.setBlock(pos, state.setValue(FORMED, false), Block.UPDATE_ALL);
            core.onDisbanded(level, pos);
        }
    }

    // ---------------- keeping the state honest ----------------

    /**
     * A component stacked by hand forms the machine; a formed machine whose cells no longer match
     * falls apart. Driven by neighbour changes, so there is no polling.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (level.isClientSide()) {
            return;
        }
        if (isFormed(state)) {
            if (!blueprint().isFormed(level, pos)) {
                // a cell was taken out from under us - drop back to unformed, nothing left to drop
                level.setBlock(pos, state.setValue(FORMED, false), Block.UPDATE_ALL);
            }
        } else {
            tryForm(level, pos);
        }
    }

    /** Breaking the core takes the rest of the machine with it. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level,
            BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (isFormed(state)) {
            blueprint().disband(level, pos, true);
        }
    }
}

package com.flatts.recompile.content.block.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
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
     * How this core's blueprint is rotated for a given state - {@link Rotation#NONE} by default (a
     * vertical column is rotation-invariant). A directional machine overrides this from its facing so
     * the structure builds relative to the player.
     */
    protected Rotation rotationFor(BlockState state) {
        return Rotation.NONE;
    }

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
            AssembleResult result = autoAssemble(level, pos, player);
            // Legible failure: a large blueprint silently doing nothing reads as "it's broken". Say
            // why (interim, until the placement outline ships). NO_ROOM shows in creative too - it is
            // the likely creative failure (the fixed footprint hitting terrain); only MISSING_PARTS is
            // creative-irrelevant, since creative always has the parts.
            if (result == AssembleResult.NO_ROOM) {
                player.sendSystemMessage(Component.translatable("message.recompile.multiblock_no_room"));
            } else if (result == AssembleResult.MISSING_PARTS && !player.getAbilities().instabuild) {
                player.sendSystemMessage(Component.translatable("message.recompile.multiblock_missing_parts"));
            }
        }
        tryForm(level, pos);
    }

    /** What auto-assembly did, so the placer can be told why nothing appeared. */
    protected enum AssembleResult { ASSEMBLED, NO_ROOM, MISSING_PARTS }

    /**
     * Place the whole blueprint from the player's inventory, consuming it. All-or-nothing: a
     * half-built machine from a partial inventory would be a worse outcome than a plainly unformed
     * core. Quantity-correct - a component that appears in N cells needs N of the item, so a
     * blueprint with four copper pipes cannot be built from one.
     */
    private AssembleResult autoAssemble(Level level, BlockPos pos, Player player) {
        Multiblock blueprint = blueprint();
        Rotation rotation = rotationFor(level.getBlockState(pos));
        if (!blueprint.roomToAssemble(level, pos, rotation)) {
            return AssembleResult.NO_ROOM;
        }
        if (!player.getAbilities().instabuild) {
            java.util.Map<Item, Integer> needed = new java.util.HashMap<>();
            for (Multiblock.Cell cell : blueprint.cells()) {
                needed.merge(cell.component().asItem(), 1, Integer::sum);
            }
            for (java.util.Map.Entry<Item, Integer> entry : needed.entrySet()) {
                if (countIn(player, entry.getKey()) < entry.getValue()) {
                    return AssembleResult.MISSING_PARTS;
                }
            }
        }
        for (Multiblock.Cell cell : blueprint.cells()) {
            consumeOne(player, cell.component().asItem());
            level.setBlock(cell.at(pos, rotation), cell.component().defaultBlockState(), Block.UPDATE_ALL);
        }
        return AssembleResult.ASSEMBLED;
    }

    private static int countIn(Player player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void consumeOne(Player player, Item item) {
        if (player.getAbilities().instabuild) {
            return;
        }
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item) && !stack.isEmpty()) {
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
        Rotation rotation = core.rotationFor(state);
        if (isFormed(state) || !blueprint.matches(level, pos, rotation)) {
            return false;
        }
        blueprint.form(level, pos, rotation);
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
        core.blueprint().disband(level, pos, core.rotationFor(state), drop);
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
            if (!blueprint().isFormed(level, pos, rotationFor(state))) {
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
            blueprint().disband(level, pos, rotationFor(state), true);
        }
    }
}

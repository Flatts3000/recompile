package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.content.block.RecompileWorkbenchBlock;
import com.flatts.recompile.content.recipe.TeardownRecipe;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The Recompile Workbench's state: the two tools resting on the table (a scrap knife and a
 * prybar) plus the transient hold-progress of the breakdown in flight.
 *
 * <p>Deliberately <b>not</b> a {@link net.minecraft.world.Container}: the bench must never be
 * hopper-fed (design P1.4.1 - a powered automated disassembler is a tier-3+ upgrade), so it
 * exposes no item-handler capability. It just holds two tool stacks so their durability
 * survives, and the block's {@code has_knife}/{@code has_prybar} blockstate booleans mirror
 * their presence so a baked multipart model can draw them - no BlockEntityRenderer (P1.11.6).
 *
 * <p>The breakdown itself is teardown-for-materials only: {@link TeardownRecipe#results()} +
 * rolled {@link TeardownRecipe#extras()} pop into the world; {@link TeardownRecipe#teaches()}
 * is ignored (the knowledge/function axis is unresolved and layers on later).
 */
public class RecompileWorkbenchBlockEntity extends BlockEntity {

    public static final int KNIFE_SLOT = 0;
    public static final int PRYBAR_SLOT = 1;
    private static final int TOOL_SLOTS = 2;

    // Holding right-click refires useItemOn ~every 4 ticks. Accumulate the real elapsed
    // ticks so per-recipe `ticks` is honored exactly; a gap longer than the grace window
    // means the player let go, so progress restarts (the bench must be held continuously).
    private static final int GRACE_TICKS = 10;
    private static final int MAX_STEP = 8;

    private NonNullList<ItemStack> tools = NonNullList.withSize(TOOL_SLOTS, ItemStack.EMPTY);
    private int progress = 0;
    private long lastFireTick = Long.MIN_VALUE;

    public RecompileWorkbenchBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(RCBlockEntities.RECOMPILE_WORKBENCH.get(), worldPosition, blockState);
    }

    // ---- tool rack -------------------------------------------------------------------

    /** True if the stack is a tool this bench racks (checked on both sides - item identity only). */
    public static boolean isRackTool(ItemStack stack) {
        return slotFor(stack) >= 0;
    }

    private static int slotFor(ItemStack stack) {
        if (stack.is(RCItems.SCRAP_KNIFE.get())) {
            return KNIFE_SLOT;
        }
        if (stack.is(RCItems.PRYBAR.get())) {
            return PRYBAR_SLOT;
        }
        return -1;
    }

    /** Rack the held tool if its slot is free. Returns true if a tool was racked. */
    public boolean rackTool(ServerLevel level, Player player, ItemStack held) {
        int slot = slotFor(held);
        if (slot < 0 || !tools.get(slot).isEmpty()) {
            return false;
        }
        tools.set(slot, held.copyWithCount(1));
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        setChanged();
        syncPresence(level);
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);
        return true;
    }

    /** Return a racked tool to the player (prybar first, then knife). Returns true if one moved. */
    public boolean unrackOne(ServerLevel level, Player player) {
        for (int slot = tools.size() - 1; slot >= 0; slot--) {
            ItemStack tool = tools.get(slot);
            if (!tool.isEmpty()) {
                if (!player.getInventory().add(tool)) {
                    player.drop(tool, false);
                }
                tools.set(slot, ItemStack.EMPTY);
                setChanged();
                syncPresence(level);
                level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);
                return true;
            }
        }
        return false;
    }

    /** Drop every racked tool into the world (called when the block is broken). */
    public void dropTools(Level level) {
        for (int slot = 0; slot < tools.size(); slot++) {
            ItemStack tool = tools.get(slot);
            if (!tool.isEmpty()) {
                Block.popResource(level, worldPosition, tool);
                tools.set(slot, ItemStack.EMPTY);
            }
        }
    }

    /** Read-only presence check for Jade / gametests. */
    public boolean hasTool(int slot) {
        return slot >= 0 && slot < tools.size() && !tools.get(slot).isEmpty();
    }

    /** Read-only view of a racked tool (may be empty). For gametests. */
    public ItemStack getTool(int slot) {
        return slot >= 0 && slot < tools.size() ? tools.get(slot) : ItemStack.EMPTY;
    }

    // ---- breakdown -------------------------------------------------------------------

    /**
     * Advance a held-right-click breakdown by one interaction fire. Returns true when the held
     * item is a valid teardown input at this station - so the caller returns SUCCESS and holding
     * keeps refiring - <i>even if</i> the required tool is missing (progress just does not
     * accumulate; Jade tells the player what to rack).
     */
    public boolean advanceBreakdown(ServerLevel level, Player player, ItemStack held) {
        Optional<RecipeHolder<TeardownRecipe>> found = findRecipe(level, held);
        if (found.isEmpty()) {
            return false;
        }
        TeardownRecipe recipe = found.get().value();
        if (!hasRequiredTool(recipe)) {
            progress = 0;
            return true;
        }
        long now = level.getGameTime();
        long delta = now - lastFireTick;
        lastFireTick = now;
        if (delta < 0 || delta > GRACE_TICKS) {
            progress = 0;
        } else {
            progress += (int) Math.min(delta, MAX_STEP);
        }
        workingFeedback(level);
        if (progress >= recipe.ticks()) {
            complete(level, recipe, held, player);
            progress = 0;
        }
        return true;
    }

    /**
     * Run one full breakdown immediately (bypasses the hold-progress). The single entry point
     * for gametests, mirroring {@code SortingTarpBlock.siftInput}.
     */
    public static boolean breakdownNow(ServerLevel level, BlockPos pos, ItemStack held) {
        if (!(level.getBlockEntity(pos) instanceof RecompileWorkbenchBlockEntity workbench)) {
            return false;
        }
        Optional<RecipeHolder<TeardownRecipe>> found = findRecipe(level, held);
        if (found.isEmpty()) {
            return false;
        }
        TeardownRecipe recipe = found.get().value();
        if (!workbench.hasRequiredTool(recipe)) {
            return false;
        }
        workbench.complete(level, recipe, held, null);
        return true;
    }

    private void complete(ServerLevel level, TeardownRecipe recipe, ItemStack held, @Nullable Player player) {
        BlockPos above = worldPosition.above();
        for (TeardownRecipe.ItemResult result : recipe.results()) {
            Block.popResource(level, above, result.toStack());
        }
        RandomSource random = level.getRandom();
        for (TeardownRecipe.ChanceResult extra : recipe.extras()) {
            if (random.nextFloat() < extra.chance()) {
                Block.popResource(level, above, new ItemStack(extra.item()));
            }
        }
        recipe.tool().ifPresent(required -> damageRackedTool(level, required));
        if (player == null || !player.getAbilities().instabuild) {
            held.shrink(1);
        }
        SoundType sound = level.getBlockState(worldPosition).getSoundType();
        level.playSound(null, worldPosition, sound.getBreakSound(), SoundSource.BLOCKS, 0.8F, 0.9F);
    }

    private boolean hasRequiredTool(TeardownRecipe recipe) {
        if (recipe.tool().isEmpty()) {
            return true;
        }
        Ingredient required = recipe.tool().get();
        for (ItemStack tool : tools) {
            if (!tool.isEmpty() && required.test(tool)) {
                return true;
            }
        }
        return false;
    }

    /** Take one point of durability off the racked tool that matches; clears the slot if it breaks. */
    private void damageRackedTool(ServerLevel level, Ingredient required) {
        for (int slot = 0; slot < tools.size(); slot++) {
            ItemStack tool = tools.get(slot);
            if (!tool.isEmpty() && required.test(tool) && tool.isDamageableItem()) {
                tool.setDamageValue(tool.getDamageValue() + 1);
                if (tool.getDamageValue() >= tool.getMaxDamage()) {
                    tools.set(slot, ItemStack.EMPTY);
                    level.playSound(null, worldPosition, SoundEvents.ITEM_BREAK.value(),
                        SoundSource.BLOCKS, 0.7F, 1.1F);
                    syncPresence(level);
                }
                setChanged();
                return;
            }
        }
    }

    private static Optional<RecipeHolder<TeardownRecipe>> findRecipe(ServerLevel level, ItemStack held) {
        if (held.isEmpty()) {
            return Optional.empty();
        }
        return level.getServer().getRecipeManager()
            .getRecipeFor(RCRecipeTypes.TEARDOWN.get(), new SingleRecipeInput(held), level)
            .filter(holder -> holder.value().station().equals(TeardownRecipe.DEFAULT_STATION));
    }

    private void workingFeedback(ServerLevel level) {
        level.sendParticles(ParticleTypes.CRIT, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
            worldPosition.getZ() + 0.5, 2, 0.2, 0.05, 0.2, 0.0);
        SoundType sound = level.getBlockState(worldPosition).getSoundType();
        level.playSound(null, worldPosition, sound.getHitSound(), SoundSource.BLOCKS, 0.25F, 1.4F);
    }

    // ---- rendering-presence sync + persistence ---------------------------------------

    private void syncPresence(ServerLevel level) {
        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() instanceof RecompileWorkbenchBlock) {
            level.setBlock(worldPosition, state
                .setValue(RecompileWorkbenchBlock.HAS_KNIFE, !tools.get(KNIFE_SLOT).isEmpty())
                .setValue(RecompileWorkbenchBlock.HAS_PRYBAR, !tools.get(PRYBAR_SLOT).isEmpty()), 3);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.tools);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.tools = NonNullList.withSize(TOOL_SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.tools);
    }
}

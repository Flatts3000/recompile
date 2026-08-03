package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.content.block.SeparatorCoreBlock;
import com.flatts.recompile.content.recipe.SeparatingRecipe;
import com.flatts.recompile.content.recipe.TeardownRecipe;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCRecipeTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The Separator's brain ({@code docs/gem_tier_spec.md} Phase 2).
 *
 * <p><b>It holds no items.</b> Each tick it looks at the item entities floating above its chamber, and
 * if they carry enough of something a {@code recompile:separating} recipe accepts, it grinds them. That
 * is what keeps the machine off the {@code Container} path entirely, which is why it has no automation
 * surface to declare: no hopper can insert, no pipe can extract, and yet a dropper above and a hopper
 * below automate it perfectly.
 *
 * <p><b>Progress is banked against the material, not against the clock.</b> If the feed is taken away
 * mid-grind the run resets, because there is nothing inside the machine to have half-processed. If the
 * power runs out the material simply waits, the way a furnace with no fuel sits full and cold.
 */
public class SeparatorBlockEntity extends BlockEntity {

    /** Sized at one full default operation, so a solar gap does not stutter the machine. */
    private static final int BUFFER = SeparatingRecipe.DEFAULT_TICKS * SeparatingRecipe.DEFAULT_ENERGY;

    /**
     * Insert and extract are both open <b>on the handler</b>; it is the capability wrapper in
     * {@code RCBlockEntities} that makes the machine insert-only to the outside world. Building the
     * handler extract-disabled instead looks like the same thing and is not: the machine could no
     * longer draw its own power, so it sat fully charged and never ran.
     */
    private final SimpleEnergyHandler battery =
        new SimpleEnergyHandler(BUFFER, Integer.MAX_VALUE, Integer.MAX_VALUE);

    private int progress;
    /** The matched recipe's tick target, so Jade can show a percentage rather than a raw count. */
    private int goal;
    /**
     * The best partial match in the mouth: how much is there, and how much the recipe wants.
     *
     * <p>Exists because "nothing in the chamber" is a lie when seven of the sixteen it needs are
     * sitting there in plain sight. The count IS the mechanic of this tier, so a machine that will not
     * say which number it is waiting for has hidden the only thing the player has to act on.
     */
    private int feedHave;
    private int feedNeed;

    public SeparatorBlockEntity(BlockPos pos, BlockState state) {
        super(RCBlockEntities.SEPARATOR.get(), pos, state);
    }

    public SimpleEnergyHandler battery() {
        return battery;
    }

    public int progress() {
        return progress;
    }

    /** Ticks the current run needs, or 0 when nothing is being ground. */
    public int goal() {
        return goal;
    }

    /** How much of a partially-satisfied recipe's input is in the mouth. 0 when there is no partial. */
    public int feedHave() {
        return feedHave;
    }

    /** How much that recipe wants. 0 when nothing in the mouth matches anything. */
    public int feedNeed() {
        return feedNeed;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SeparatorBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (!com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock.isFormed(state)) {
            be.stall(level, pos, state);
            return;
        }

        List<ItemEntity> feed = new ArrayList<>();
        RecipeHolder<SeparatingRecipe> match = be.findFeed(server, pos, feed);
        if (match == null) {
            match = be.findContainerFeed(server, pos);
        }
        if (match == null) {
            be.progress = 0;
            be.goal = 0;
            be.stall(level, pos, state);
            return;
        }
        be.goal = match.value().ticks();

        int fe = match.value().energy();
        if (fe > 0) {
            try (Transaction tx = Transaction.openRoot()) {
                if (be.battery.extract(fe, tx) < fe) {
                    // Underpowered: hold progress and go dark. The material waits; nothing is lost.
                    be.stall(level, pos, state);
                    return;
                }
                tx.commit();
            }
        }

        be.setActive(level, pos, state, true);
        be.progress++;
        if (be.progress >= match.value().ticks()) {
            be.progress = 0;
            be.grind(server, pos, match.value(), feed);
        }
        be.setChanged();
    }

    /**
     * Find a recipe whose input is sitting above the chamber in sufficient quantity.
     *
     * <p>A scan rather than a collision handler: collision fires per entity and is fragile around
     * stacking, merging and despawn, while a bounded scan on the machine's own tick is one place to
     * look and one place to test.
     */
    private RecipeHolder<SeparatingRecipe> findFeed(ServerLevel level, BlockPos pos,
                                                    List<ItemEntity> collected) {
        AABB mouth = SeparatorCoreBlock.mouth(level, pos);
        if (mouth == null) {
            return null;
        }
        List<ItemEntity> above = level.getEntitiesOfClass(ItemEntity.class, mouth);
        if (above.isEmpty()) {
            return null;
        }
        feedHave = 0;
        feedNeed = 0;
        for (RecipeHolder<SeparatingRecipe> holder
                : level.recipeAccess().recipeMap().byType(RCRecipeTypes.SEPARATING.get())) {
            SeparatingRecipe recipe = holder.value();
            int have = 0;
            List<ItemEntity> matching = new ArrayList<>();
            for (ItemEntity entity : above) {
                ItemStack stack = entity.getItem();
                if (stack.isEmpty() || !recipe.matches(new SingleRecipeInput(stack), level)) {
                    continue;
                }
                matching.add(entity);
                have += stack.getCount();
            }
            if (have >= recipe.count()) {
                collected.addAll(matching);
                feedHave = 0;
                feedNeed = 0;
                return holder;
            }
            // Remember the closest near-miss, so the tooltip can name the number being waited on.
            if (have > 0 && (feedNeed == 0 || recipe.count() - have < feedNeed - feedHave)) {
                feedHave = have;
                feedNeed = recipe.count();
            }
        }
        return null;
    }

    /** Any recipe a container on the chamber can satisfy. Checked after the loose-item path. */
    private RecipeHolder<SeparatingRecipe> findContainerFeed(ServerLevel level, BlockPos pos) {
        for (RecipeHolder<SeparatingRecipe> holder
                : level.recipeAccess().recipeMap().byType(RCRecipeTypes.SEPARATING.get())) {
            if (feedContainer(level, pos, holder.value()) != null) {
                return holder;
            }
        }
        return null;
    }

    /**
     * A container sitting on the chamber, if it holds enough of the recipe's input.
     *
     * <p><b>The machine pulls; nothing pushes into it.</b> A hopper above is the first thing anyone
     * tries, and pointing one down at the chamber does nothing, because the chamber is not a
     * {@code Container} and never will be. Reaching out is how a hopper itself works, and it costs none
     * of the properties this machine is built on: it still exposes no item handler, so no pipe can
     * connect to it and nothing can insert into it or extract from it.
     */
    private @Nullable Container feedContainer(ServerLevel level, BlockPos pos, SeparatingRecipe recipe) {
        for (BlockPos cell : SeparatorCoreBlock.chamberCells(level, pos)) {
            Container container = HopperBlockEntity.getContainerAt(level, cell.above());
            if (container == null) {
                continue;
            }
            int have = 0;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty() && recipe.matches(new SingleRecipeInput(stack), level)) {
                    have += stack.getCount();
                }
            }
            if (have >= recipe.count()) {
                return container;
            }
        }
        return null;
    }

    private static void drain(Container container, SeparatingRecipe recipe, Level level, int wanted) {
        int remaining = wanted;
        for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || !recipe.matches(new SingleRecipeInput(stack), level)) {
                continue;
            }
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
            container.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
        container.setChanged();
    }

    /** Consume the feed and throw the results out of the chute. */
    private void grind(ServerLevel level, BlockPos pos, SeparatingRecipe recipe, List<ItemEntity> feed) {
        int remaining = recipe.count();
        if (feed.isEmpty()) {
            Container container = feedContainer(level, pos, recipe);
            if (container == null) {
                return;   // it went away mid-grind; nothing was banked, so nothing is lost
            }
            drain(container, recipe, level, remaining);
            remaining = 0;
        }
        for (ItemEntity entity : feed) {
            if (remaining <= 0) {
                break;
            }
            ItemStack stack = entity.getItem();
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
            if (stack.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(stack);
            }
        }

        BlockPos outlet = SeparatorCoreBlock.outlet(level, pos);
        for (TeardownRecipe.ItemResult result : recipe.results()) {
            Block.popResource(level, outlet, result.toStack());
        }
        for (TeardownRecipe.ItemResult byproduct : recipe.byproducts()) {
            Block.popResource(level, outlet, byproduct.toStack());
        }
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4F, 0.6F);
    }

    private void stall(Level level, BlockPos pos, BlockState state) {
        setActive(level, pos, state, false);
    }

    private void setActive(Level level, BlockPos pos, BlockState state, boolean active) {
        if (!state.hasProperty(SeparatorCoreBlock.ACTIVE)
                || state.getValue(SeparatorCoreBlock.ACTIVE) == active) {
            return;
        }
        level.setBlock(pos, state.setValue(SeparatorCoreBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        battery.serialize(output.child("energy"));
        output.putInt("progress", progress);
        output.putInt("goal", goal);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("energy").ifPresent(battery::deserialize);
        progress = input.getIntOr("progress", 0);
        goal = input.getIntOr("goal", 0);
    }
}

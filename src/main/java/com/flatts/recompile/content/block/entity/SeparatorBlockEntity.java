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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

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
        List<BlockPos> intakes = SeparatorCoreBlock.intakes(level, pos);
        if (intakes.isEmpty()) {
            return null;
        }
        // One box spanning every intake cell, inflated a little. Three tight per-block boxes miss an
        // item that has settled on a rim between two of them, and this is one entity query instead of
        // three. The inflation is deliberate: dropped items drift, and a machine that only eats
        // perfectly centred items reads as broken.
        AABB mouth = new AABB(intakes.get(0));
        for (BlockPos intake : intakes) {
            mouth = mouth.minmax(new AABB(intake));
        }
        List<ItemEntity> above = level.getEntitiesOfClass(ItemEntity.class, mouth.inflate(0.25));
        if (above.isEmpty()) {
            return null;
        }
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
                return holder;
            }
        }
        return null;
    }

    /** Consume the feed and throw the results out of the chute. */
    private void grind(ServerLevel level, BlockPos pos, SeparatingRecipe recipe, List<ItemEntity> feed) {
        int remaining = recipe.count();
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

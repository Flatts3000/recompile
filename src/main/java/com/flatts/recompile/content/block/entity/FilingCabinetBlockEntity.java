package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.content.item.BlueprintItem;
import com.flatts.recompile.registry.RCBlockEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Filing Cabinet's contents (#95, spec {@code docs/blueprints_spec.md}): where a base keeps what it
 * has worked out how to build.
 *
 * <p><b>Blueprints only.</b> It is not storage, it is a reference shelf, and letting it take cobblestone
 * would make it a worse chest with a nicer texture. The filter is on {@link #canPlaceItem} rather than
 * on the slot, because in 26.1 vanilla's {@code Slot.mayPlace} returns true unconditionally and
 * {@code ChestMenu} uses a plain {@code Slot} - the same trap the Burn Barrel's refuse-only rule hit,
 * where the check had to move into the ticker because the slot was never consulted. Here the menu is
 * built with a filtering slot for the GUI and this method covers hoppers and pipes.
 *
 * <p><b>It reuses a vanilla screen</b>, per the rule in CLAUDE.md: a grid of items is exactly what a
 * chest screen is for, and nothing here needs a gauge. Six rows, which is 54 blueprints. The spec asked
 * for unbounded and this is not that - it is the largest a borrowed screen goes, and going past it means
 * minting a fifth custom screen and recording another reversal. Worth doing when a save actually holds
 * 54 distinct blueprints; the mod currently ships one.
 */
public class FilingCabinetBlockEntity extends RandomizableContainerBlockEntity {

    /** Six rows, the biggest a vanilla chest screen goes. */
    public static final int SLOTS = 54;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    public FilingCabinetBlockEntity(BlockPos pos, BlockState state) {
        super(RCBlockEntities.FILING_CABINET.get(), pos, state);
    }

    /**
     * Every blueprint set filed here.
     *
     * <p>This is what the crafting table asks, so it returns sets rather than stacks: the table wants to
     * know whether the knowledge is reachable, not how it is stored.
     */
    public List<Identifier> filed() {
        List<Identifier> sets = new ArrayList<>();
        for (ItemStack stack : items) {
            Identifier set = BlueprintItem.blueprintOf(stack);
            if (set != null) {
                sets.add(set);
            }
        }
        return sets;
    }

    /** Whether this cabinet holds the named blueprint. */
    public boolean holds(Identifier set) {
        for (ItemStack stack : items) {
            if (set.equals(BlueprintItem.blueprintOf(stack))) {
                return true;
            }
        }
        return false;
    }

    /** Blueprints and nothing else, including from a hopper. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.isEmpty() || stack.getItem() instanceof BlueprintItem;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.recompile.filing_cabinet");
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> replacement) {
        items = replacement;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.sixRows(containerId, inventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!trySaveLootTable(output)) {
            net.minecraft.world.ContainerHelper.saveAllItems(output, items);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(input)) {
            net.minecraft.world.ContainerHelper.loadAllItems(input, items);
        }
    }
}

package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.registry.RCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Burn Barrel (design P2.2): the garbage world's first smelter - a drum you burn refuse in.
 * Functionally a vanilla furnace (same recipes on {@link RecipeType#SMELTING}, same cook time,
 * the vanilla furnace screen), with one deliberate downgrade: it is <b>not automatable</b>.
 *
 * <p>"Worse" here means manual-only. A vanilla furnace is a {@code WorldlyContainer} that exposes
 * its slots to hoppers (input on top, fuel on the sides, output on the bottom); this one exposes
 * <b>no</b> slots to any face, so hoppers, Create, and pipes cannot insert or extract - you load
 * and empty it by hand through the GUI. Automation is the reward for a later, better furnace.
 */
public class BurnBarrelBlockEntity extends AbstractFurnaceBlockEntity {

    private static final int[] NO_SLOTS = new int[0];

    public BurnBarrelBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(RCBlockEntities.BURN_BARREL.get(), worldPosition, blockState, RecipeType.SMELTING);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.recompile.burn_barrel");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new FurnaceMenu(containerId, inventory, this, this.dataAccess);
    }

    // No automation: expose no slots to any face, so nothing can pipe items in or out.
    @Override
    public int[] getSlotsForFace(Direction side) {
        return NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }
}

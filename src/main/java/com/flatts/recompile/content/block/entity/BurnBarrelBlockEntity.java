package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCTags;
import com.flatts.recompile.content.block.ScrapNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
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
 * It runs vanilla {@link RecipeType#SMELTING} recipes on the vanilla furnace screen, with two deliberate
 * downgrades: it is <b>not automatable</b>, and it only burns <b>refuse</b>.
 *
 * <p>"Not automatable" means manual-only. A vanilla furnace is a {@code WorldlyContainer} that exposes
 * its slots to hoppers (input on top, fuel on the sides, output on the bottom); this one exposes
 * <b>no</b> slots to any face, so hoppers, Create, and pipes cannot insert or extract - you load
 * and empty it by hand through the GUI. Automation is the reward for a later, better furnace.
 *
 * <p>"Refuse only" is {@link #burns}: a drum fire cooks food and reclaims scrap, and that is all. It will
 * not smelt ore, sand, stone or logs, so the barrel cannot quietly hand out the materials the economy gates
 * behind better machines. This is the smelter half of the rule material_economy.md has asserted since
 * 2026-07-17 - copper is the everyman metal and iron is the gated upgrade - which until now was written down
 * but enforced nowhere, because a plain {@code RecipeType.SMELTING} furnace runs every vanilla recipe there
 * is, {@code raw_iron -> iron_ingot} included.
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

    /**
     * Whether the barrel will burn this input at all. An allowlist, so it fails closed.
     *
     * <p>Food is matched by the {@code FOOD} data component rather than a list, so every vanilla and modded
     * edible works and nothing needs maintaining. {@link RCTags#BURN_BARREL_SMELTABLE} carries the rest: the
     * mod's scrap smelting, and inputs whose <i>product</i> is edible though they are not (kelp).
     *
     * <p><b>rebar -&gt; iron is deliberately allowed</b>, and it is the one metal that is. It is the only
     * iron source in the mod and the Cutting Torch needs an ingot, so gating it would soft-lock the
     * demolition yard - no iron, no torch, no cutting, and no materials for the Cupola Furnace (#50) that is
     * meant to supersede this one. It is the trickle bootstrap; bulk iron stays gated behind the Cupola.
     */
    public static boolean burns(ItemStack input) {
        return input.has(DataComponents.FOOD) || input.is(RCTags.BURN_BARREL_SMELTABLE);
    }

    /** Whether what is loaded right now will burn. SLOT_INPUT is protected, so the check lives here. */
    public boolean burnsCurrentInput() {
        return burns(getItem(SLOT_INPUT));
    }

    /**
     * Move any smelted output into the connected scrap-network storage (P2.10). The Burn Barrel is the
     * one time-based flow: it accrues output over ticks, and while wired to a network the result slot
     * drains to the connected bins / barrel each tick. Bypasses the no-automation face gate on purpose
     * - the network is the machine's own internal mover, not an external hopper. Standalone (no
     * connected storage), output stays put (you take it by hand through the GUI).
     */
    public void drainOutput(ServerLevel level) {
        ItemStack result = getItem(SLOT_RESULT);
        if (result.isEmpty()) {
            return;
        }
        ItemStack working = result.copy();
        ScrapNetwork.insertFromMember(level, worldPosition, working, false);
        if (working.getCount() != result.getCount()) {
            setItem(SLOT_RESULT, working.isEmpty() ? ItemStack.EMPTY : working);
            setChanged();
        }
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

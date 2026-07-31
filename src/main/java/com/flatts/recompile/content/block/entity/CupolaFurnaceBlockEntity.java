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
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Cupola Furnace (#50): the world's second smelter, and the machine that makes iron reachable.
 *
 * <p>A <b>cupola</b> is a real thing - a coke-fired shaft furnace whose entire job is remelting scrap iron
 * and steel. It is non-electric and predates the electric era; EAFs and induction furnaces are its modern
 * replacements, not the other way round. That matters here because this world's only power is solar panels,
 * so the ladder's old top rung ("induction recycler") named a machine the economy cannot run. It also means
 * this is deliberately <b>not</b> a blast furnace in the metallurgical sense: a blast furnace reduces iron
 * ORE into pig iron, which is virgin production. This world has no ore. It remelts what was already made.
 *
 * <p>Mechanically it is an <b>unrestricted</b> {@link RecipeType#SMELTING} furnace: everything the Burn
 * Barrel does and everything it refuses. That is what makes it a true upgrade rather than a second
 * appliance - you stop needing the barrel, instead of keeping both for different jobs.
 *
 * <p><b>The gate is the barrel's allowlist, not this block's recipe type.</b> Iron recipes (Steel Offcut ->
 * ingot, rebar -> nugget) are ordinary smelting; what makes them Cupola-only is that the Burn Barrel refuses
 * them and no other furnace exists. That last clause is load-bearing and fragile: a vanilla furnace needs
 * {@code #minecraft:stone_crafting_materials} (cobblestone, cobbled deepslate or blackstone), none of which
 * this world can produce - there is no cobblestone anywhere in the mod and no pickaxe to make cobbled
 * deepslate from the deepslate that shards build. <b>Adding any of those, or any pickaxe before iron, opens
 * the gate.</b> See {@code trashlands/docs/progression_gates.md}.
 *
 * <p><b>It automates, and the Burn Barrel does not.</b> The barrel exposes no slots to any face on purpose
 * (see {@link BurnBarrelBlockEntity}), with automation held back as the reward for a better machine. This is
 * that machine, so it inherits the vanilla furnace's face behaviour: hoppers, Create and pipes all work.
 * Upgrading is therefore two rewards in one - a metal tier and a machine tier.
 *
 * <p><b>One deliberate departure from furnace parity: automation cannot insert what cannot be smelted</b>
 * (owner call, 2026-07-31, spec {@code docs/automation_policy_spec.md}). Vanilla lets anything into the
 * input slot - verified, {@code cupola_refuses_unsmeltable_where_vanilla_accepts} asserts that vanilla
 * still does - which is harmless when a human is loading it and destructive when a pipe is. A pipe pushing a
 * non-smeltable fills the input slot and <b>bricks the machine</b> until someone empties it by hand;
 * found in playtest with the Cupola's own iron output looped back into its input.
 *
 * <p>The restriction is on {@link #canPlaceItemThroughFace} only, so <b>placing by hand is still exactly
 * vanilla</b>. The harm is automation-specific and so is the fix; a player who wants to park something in
 * the slot still can.
 */
public class CupolaFurnaceBlockEntity extends AbstractFurnaceBlockEntity {

    public CupolaFurnaceBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(RCBlockEntities.CUPOLA_FURNACE.get(), worldPosition, blockState, RecipeType.SMELTING);
    }

    /**
     * Automation may only insert into the input slot what a smelting recipe actually consumes.
     *
     * <p>Fuel (slot 1) and output (slot 2) keep vanilla's behaviour untouched - the jam only ever happens
     * on the input, and narrowing the other two would break legitimate hopper setups.
     *
     * <p>Recipe lookup rather than a hand-kept allowlist: a pack adding a smelting recipe should not have
     * to be added here too, and the Burn Barrel's allowlist exists for a different reason (gating what it
     * is WILLING to smelt, not protecting it from jams).
     */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (slot == 0 && this.level != null && this.level.getServer() != null) {
            boolean smeltable = this.level.getServer().getRecipeManager().recipeMap()
                .getRecipesFor(RecipeType.SMELTING, new SingleRecipeInput(stack), this.level)
                .findAny()
                .isPresent();
            if (!smeltable) {
                return false;
            }
        }
        return super.canPlaceItemThroughFace(slot, stack, side);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.recompile.cupola_furnace");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new FurnaceMenu(containerId, inventory, this, this.dataAccess);
    }
}

package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.registry.RCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
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
 * <p>Mechanically it is a {@link RecipeType#BLASTING} machine, not a smelting one, and <b>that is the
 * gate.</b> A vanilla furnace cannot run a blasting recipe at all, and a vanilla blast furnace costs five
 * iron ingots - circular, so unreachable before iron. Both iron recipes (Steel Offcut -> ingot, rebar ->
 * nugget) are {@code minecraft:blasting}, which makes this block the only thing in the world that can
 * produce iron. Nothing about the world's materials has to hold for that to be true.
 *
 * <p><b>This replaced a gate that had already failed silently</b> (#91). The rule used to be "iron recipes
 * are ordinary smelting, and they are Cupola-only because the Burn Barrel refuses them and no other furnace
 * exists". The second clause stopped being true when the Tree Nursery shipped: wood makes a wooden pickaxe,
 * a wooden pickaxe drops cobbled deepslate, and cobbled deepslate is in
 * {@code #minecraft:stone_crafting_materials}. Worse, {@code rebar} is a weight-40 entry in
 * {@code household_pulls}, so a player could stockpile it on day one and smelt iron at rung 4 with no
 * demolition yard, no Cutting Torch and no Cupola. The old comment here named that exact failure mode as a
 * risk and it happened anyway, because <b>the gate was an absence of materials rather than a property of
 * the machine.</b> A recipe type is a property of the machine.
 *
 * <p>Being blast-only means this does not cook food, which is deliberate: a cupola furnace melts metal. The
 * Burn Barrel keeps refuse and food, and it is still craftable on its own, so upgrading loses nothing. Scrap
 * Metal has a blasting twin ({@code copper_from_scrap_blasting}) precisely so copper survives the upgrade -
 * the same way vanilla gives every ore both a smelting and a blasting recipe. See
 * {@code trashlands/docs/progression_gates.md}.
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

    /** Vanilla's furnace layout: 0 input, 1 fuel, 2 result. */
    private static final int RESULT_SLOT = 2;

    public CupolaFurnaceBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(RCBlockEntities.CUPOLA_FURNACE.get(), worldPosition, blockState, RecipeType.BLASTING);
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
                .getRecipesFor(RecipeType.BLASTING, new SingleRecipeInput(stack), this.level)
                .findAny()
                .isPresent();
            if (!smeltable) {
                return false;
            }
        }
        return super.canPlaceItemThroughFace(slot, stack, side);
    }

    /**
     * Push finished metal into the connected Scrap Network.
     *
     * <p><b>The Cupola carries {@code #recompile:scrap_connectable} and did nothing with it.</b> Being
     * in the tag made it part of a cluster for everything ELSE routing through - it could be walked
     * across - while its own output sat in the result slot waiting to be collected by hand. A player
     * who has wired a barrel to it reasonably expects the iron to arrive there, and the Burn Barrel two
     * blocks away has done exactly that since P2.10.
     *
     * <p>Same shape as {@code BurnBarrelBlockEntity.drainOutput}, and for the same reason it bypasses
     * the face gate: the network is the machine's own internal mover, not an external hopper. With no
     * storage connected the output stays put and you take it through the GUI.
     */
    public void drainOutput(net.minecraft.server.level.ServerLevel level) {
        ItemStack result = getItem(RESULT_SLOT);
        if (result.isEmpty()) {
            return;
        }
        ItemStack working = result.copy();
        com.flatts.recompile.content.block.ScrapNetwork.insertFromMember(
            level, worldPosition, working, false);
        if (working.getCount() != result.getCount()) {
            setItem(RESULT_SLOT, working.isEmpty() ? ItemStack.EMPTY : working);
            setChanged();
        }
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.recompile.cupola_furnace");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new BlastFurnaceMenu(containerId, inventory, this, this.dataAccess);
    }
}

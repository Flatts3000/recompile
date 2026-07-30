package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.registry.RCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.item.crafting.RecipeType;
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
 * that machine, so it inherits the vanilla furnace's face behaviour untouched: hoppers, Create and pipes all
 * work. Upgrading is therefore two rewards in one - a metal tier and a machine tier.
 */
public class CupolaFurnaceBlockEntity extends AbstractFurnaceBlockEntity {

    public CupolaFurnaceBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(RCBlockEntities.CUPOLA_FURNACE.get(), worldPosition, blockState, RecipeType.SMELTING);
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

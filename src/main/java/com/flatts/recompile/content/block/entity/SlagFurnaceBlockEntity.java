package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.content.menu.SlagFurnaceMenu;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCRecipeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Slag Furnace's contents: vanilla's three furnace slots, running {@code recompile:vitrifying}.
 *
 * <p><b>Three slots, not four.</b> The Cupola needed a fourth because a remelt makes metal AND slag
 * whether you want it or not; vitrifying has one output because melting something to glass produces
 * one thing. Adding a slot here would be copying the Cupola's shape rather than its reason - and the
 * cost of that shape is on record: leaving vanilla's menu behind gave up the recipe book and JEI's
 * transfer button (#240), which this machine keeps precisely because it did not need to.
 *
 * <p>It automates on vanilla's terms - hoppers, pipes, and the Scrap Network drain - because it is the
 * far end of a chain the player already automated. The Cupola rakes slag into a slot; something has to
 * be able to carry it here.
 */
public class SlagFurnaceBlockEntity extends AbstractFurnaceBlockEntity {

    private static final int RESULT_SLOT = 2;

    public SlagFurnaceBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(RCBlockEntities.SLAG_FURNACE.get(), worldPosition, blockState,
            RCRecipeTypes.VITRIFYING.get());
    }

    /**
     * Push finished glass into a connected Scrap Network.
     *
     * <p>Same shape as {@code CupolaFurnaceBlockEntity.drainOutput}, and it bypasses the face gate for
     * the same reason: the network is the machine's own mover, not an external hopper. With nothing
     * wired the output stays put and you take it through the screen.
     */
    public void drainOutput(ServerLevel level) {
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
        return Component.translatable("container.recompile.slag_furnace");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new SlagFurnaceMenu(containerId, inventory, this, this.dataAccess);
    }
}

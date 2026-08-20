package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.content.menu.SinteringKilnMenu;
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
 * The Sintering Kiln's contents: vanilla's three furnace slots, running {@code recompile:sintering}.
 *
 * <p>Three slots, because sintering a compact produces one thing. The Cupola needed a fourth only
 * because a remelt makes metal AND slag whether you asked or not; copying that shape here would be
 * copying it without its reason, and the cost is on record - leaving vanilla's menu behind is what
 * made the Cupola reimplement its slots, {@code quickMoveStack} and its data sync by hand (#240).
 *
 * <p>It automates on vanilla's terms, and it should: this is the far end of a chain the player has
 * already automated. Blaze powder comes out of a Pulverizer, gets pressed at a bench, and something
 * has to be able to carry the briquettes here.
 */
public class SinteringKilnBlockEntity extends AbstractFurnaceBlockEntity {

    private static final int RESULT_SLOT = 2;

    public SinteringKilnBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(RCBlockEntities.SINTERING_KILN.get(), worldPosition, blockState,
            RCRecipeTypes.SINTERING.get());
    }

    /**
     * Push finished work into a connected Scrap Network.
     *
     * <p>Same shape as the Slag Furnace and the Cupola, and it bypasses the face gate for the same
     * reason: the network is the machine's own mover rather than an external hopper. With nothing wired
     * the output stays put and you take it through the screen.
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
        return Component.translatable("container.recompile.sintering_kiln");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new SinteringKilnMenu(containerId, inventory, this, this.dataAccess);
    }
}

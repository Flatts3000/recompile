package com.flatts.recompile.content.item;

import com.flatts.recompile.registry.RCItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Shared rules for "cut this open with the scrap knife".
 *
 * <p>The verb belongs to more than one item now - a sealed tin can, a mattress - so the
 * rule lives here rather than being copied per item, where the two copies would drift.
 * The knife is a tool: it is never consumed, and it only has to be <em>somewhere</em> in
 * the inventory, not in hand, because the hand is holding the thing being cut.
 */
public final class KnifeWork {

    private KnifeWork() {
    }

    /** True when the player is carrying a scrap knife in any slot. */
    public static boolean hasKnife(Player player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(RCItems.SCRAP_KNIFE.get())) {
                return true;
            }
        }
        return false;
    }

    /** Give the player a stack, dropping it at their feet if there is no room. */
    public static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}

package com.flatts.recompile.event;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.market.Market;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Put a thing's scrip value on its own tooltip (owner, 2026-09-04).
 *
 * <p><b>The price belongs where the player is already looking.</b> Before this the only way to learn
 * what something paid was to carry it to a Sell Terminal and drop it in the grid; the sell list is
 * nine entries and a pack may change it, so "is this worth anything" was a question you had to walk
 * across the base to answer. Same argument as the fuel line in {@link RCFuelTooltip}: Jade is a block
 * and entity HUD, so an item's own number belongs in its tooltip.
 *
 * <p><b>It reads {@link Market#isSellable}, which is the same predicate the terminal's slot uses</b>,
 * so the tooltip and the machine cannot disagree. An item in the tag with no price shows nothing and
 * is refused at the slot - the door fails closed in both places, and neither claims a value the other
 * would not honour. The value comes from the {@code recompile:scrip_value} data map, which is synced,
 * so a pack retuning a price moves this line with it and no number is baked in here.
 *
 * <p>Client-only, like every tooltip handler.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID, value = Dist.CLIENT)
public final class RCScripTooltip {

    private RCScripTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!Market.isSellable(stack)) {
            return;
        }
        int each = Market.priceOf(stack.getItem());
        int count = stack.getCount();
        // The per-item price is the one that is always true; the stack total is the one a player
        // holding 37 of something actually wants, and computing it in their head is the friction
        // this line exists to remove. One line either way, because a tooltip is not a screen.
        Component line = count > 1
            ? Component.translatable("tooltip.recompile.scrip_value_stack",
                String.format("%,d", each), String.format("%,d", (long) each * count))
            : Component.translatable("tooltip.recompile.scrip_value", String.format("%,d", each));
        event.getToolTip().add(line.copy().withStyle(ChatFormatting.GRAY));
    }
}

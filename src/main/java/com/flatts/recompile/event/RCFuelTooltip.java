package com.flatts.recompile.event;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Add a fuel line to the mod's fuels (Oily Rag, junk) in their item tooltip. Jade is a
 * block/entity HUD and does not cover items, so an item's fuel value belongs in its tooltip.
 * The burn time is read live from the level's fuel values (the {@code neoforge:furnace_fuels}
 * data map), so the shown number never drifts from the data. Client-only.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID, value = Dist.CLIENT)
public final class RCFuelTooltip {

    /** A furnace smelt is 200 ticks, so burn time / 200 = items smelted per unit of fuel. */
    private static final int TICKS_PER_SMELT = 200;

    private RCFuelTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(RCItems.OILY_RAG.get()) && !stack.is(RCItems.JUNK.get())) {
            return;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        int burn = level.fuelValues().burnDuration(stack);
        if (burn <= 0) {
            return;
        }
        event.getToolTip().add(Component.translatable("tooltip.recompile.fuel", burn / TICKS_PER_SMELT)
            .withStyle(ChatFormatting.GRAY));
    }
}

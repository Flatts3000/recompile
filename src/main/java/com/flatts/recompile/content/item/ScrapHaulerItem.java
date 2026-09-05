package com.flatts.recompile.content.item;

import com.flatts.recompile.registry.RCDataComponents;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The Scrap Hauler in the hand (#376, spec {@code docs/scrap_hauler_spec.md}): the item a Hauler Depot
 * holds and deploys. <b>It is never a block.</b> Dormant it is this; working it is
 * {@link com.flatts.recompile.content.entity.ScrapHaulerEntity}; the Depot is the only thing that
 * turns one into the other.
 *
 * <p><b>The second powered item in the mod</b>, on exactly the Garbage Vacuum's terms: charge is an
 * {@code Integer} component, and {@code Capabilities.Energy.ITEM} is answered over that component by a
 * stack-backed handler registered in {@code RCBlockEntities}. That is the door the Depot charges it
 * through, and any other mod's charger would use the same one. One number, two doors, no way for the
 * tooltip and the charger to disagree.
 *
 * <p>Capacity sits at the diamond vacuum's, and <b>under 32,767 on purpose</b>: the Depot's screen
 * syncs this figure through a menu data slot, which is a short on the wire (see
 * {@code content/menu/BalanceSync}), and a capacity that fits in one slot needs no split. A test pins
 * the ceiling so raising this cannot silently wrap the gauge.
 */
public class ScrapHaulerItem extends Item {

    /** The diamond vacuum's. Under the 16-bit wire ceiling deliberately; see the class javadoc. */
    public static final int CAPACITY = 16_000;

    public ScrapHaulerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static int charge(ItemStack stack) {
        return stack.getOrDefault(RCDataComponents.HAULER_CHARGE.get(), 0);
    }

    public static void setCharge(ItemStack stack, int charge) {
        stack.set(RCDataComponents.HAULER_CHARGE.get(), Math.max(0, Math.min(CAPACITY, charge)));
    }

    public static int capacityOf(ItemStack stack) {
        return stack.getItem() instanceof ScrapHaulerItem ? CAPACITY : 0;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * charge(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3FB8FF;   // the vacuum's blue, so one colour means "charge" across the powered items
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("tooltip.recompile.energy_stored",
            String.format("%,d", charge(stack)), String.format("%,d", CAPACITY)));
        lines.accept(Component.translatable("tooltip.recompile.scrap_hauler"));
    }
}

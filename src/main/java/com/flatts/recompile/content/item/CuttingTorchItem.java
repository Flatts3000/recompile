package com.flatts.recompile.content.item;

import com.flatts.recompile.registry.RCDataComponents;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The Cutting Torch: a tool you charge with Oily Rags before you cut, not one that drains your pack while
 * you work (spec {@code docs/steel_cutting_torch_spec.md}).
 *
 * <p>Right-click with the torch in hand to feed it a rag from your inventory. Each rag is worth
 * {@link #CUTS_PER_RAG} cuts and the torch holds {@link #CAPACITY}; cutting a block in
 * {@code #recompile:mineable/cutting_torch} spends one charge (see {@code RCTorchFuel}). Empty, it will not
 * cut at all.
 *
 * <p>Charging <b>refuses to overfill</b> rather than clamping. Topping up a nearly-full torch would silently
 * burn most of a rag, and a player has no way to see that happening - so a rag that would not fit is simply
 * not taken, and the torch says it is full.
 *
 * <p>A torch with no stored value reads as {@link #CUTS_PER_RAG}, not empty, because its recipe already
 * spends an Oily Rag - a freshly crafted torch arrives with that rag in it. A spent torch stores an explicit
 * 0, so this default only ever applies to one that has never been used.
 */
public class CuttingTorchItem extends Item {

    /** Cuts one Oily Rag is worth. First-pass; balance is #36. */
    public static final int CUTS_PER_RAG = 8;
    /** How many cuts the torch holds - eight rags. First-pass; balance is #36. */
    public static final int CAPACITY = 64;

    public CuttingTorchItem(Properties properties) {
        super(properties);
    }

    /** Charge remaining, in cuts. An unused torch carries its crafting rag. */
    public static int fuel(ItemStack stack) {
        return stack.getOrDefault(RCDataComponents.TORCH_FUEL.get(), CUTS_PER_RAG);
    }

    /** Spends one cut. False (and no change) when the torch is dry. */
    public static boolean spendCut(ItemStack stack) {
        int remaining = fuel(stack);
        if (remaining <= 0) {
            return false;
        }
        stack.set(RCDataComponents.TORCH_FUEL.get(), remaining - 1);
        return true;
    }

    /** Whether another whole rag would fit without any of it being wasted. */
    public static boolean hasRoomForRag(ItemStack stack) {
        return fuel(stack) <= CAPACITY - CUTS_PER_RAG;
    }

    /** Feeds the torch one rag's worth. Callers must check {@link #hasRoomForRag} first. */
    public static void addRag(ItemStack stack) {
        stack.set(RCDataComponents.TORCH_FUEL.get(), Math.min(CAPACITY, fuel(stack) + CUTS_PER_RAG));
    }

    private static boolean takeRagFrom(Player player) {
        if (player.getAbilities().instabuild) {
            return true;   // creative charges without spending, as the Sealed Can does
        }
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.is(RCItems.OILY_RAG.get())) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack torch = player.getItemInHand(hand);
        if (!hasRoomForRag(torch)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.recompile.torch_full"));
            }
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            if (!takeRagFrom(player)) {
                player.sendOverlayMessage(Component.translatable("message.recompile.torch_needs_rag"));
                return InteractionResult.PASS;
            }
            addRag(torch);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * The charge, in words as well as on the bar.
     *
     * <p>The bar alone says "some fuel left" but never how much, and it is easy to miss on a hotbar item.
     * The tooltip is where a player checks before walking out to a husk with 3 cuts in the tank.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            java.util.function.Consumer<Component> adder, TooltipFlag flag) {
        int charge = fuel(stack);
        adder.accept(Component.translatable("tooltip.recompile.torch_fuel", charge, CAPACITY)
            .withStyle(charge > 0 ? ChatFormatting.GOLD : ChatFormatting.RED));
        if (charge <= 0) {
            adder.accept(Component.translatable("tooltip.recompile.torch_charge_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // The fuel gauge. The torch is UNBREAKABLE, so this bar is free real estate - nothing else is using it,
    // and a charge you cannot see is a charge players will run out of mid-cut without warning.

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return fuel(stack) < CAPACITY;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * fuel(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xFF9A2B;   // flame orange, so it does not read as a vanilla durability bar
    }
}

package com.flatts.recompile.event;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCTags;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * The Cutting Torch burns an Oily Rag per cut (spec {@code docs/steel_cutting_torch_spec.md}).
 *
 * <p>This replaces the v1 durability-as-fuel-tank model. Under that model the torch was the consumable and
 * ran down whether or not you had any fuel, which made the rag a one-off crafting cost rather than an
 * ongoing one. Here the torch is {@code UNBREAKABLE} and the rag is the real sink, so cutting steel draws
 * continuously on the P1.4-A oily-rag line - and a torch with no rag simply will not cut.
 *
 * <p>Only blocks in {@code #recompile:mineable/cutting_torch} cost fuel, so breaking dirt while holding the
 * torch is free. Creative is exempt.
 *
 * <p>Refusing the cut cancels the break outright rather than dropping nothing. Silently eating the block is
 * the worse failure - a player who cannot see why their steel vanished has no way to learn the rule - so
 * the block stays put and says what is missing.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID)
public final class RCTorchFuel {

    private RCTorchFuel() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BreakBlockEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        if (!cutCostsFuel(player, event.getState())) {
            return;
        }
        if (!spendFuel(player)) {
            event.setCanceled(true);
            player.sendOverlayMessage(Component.translatable("message.recompile.torch_no_fuel"));
        }
    }

    /** Whether this break is a torch cut that should draw fuel. The static entry point the GameTests drive. */
    public static boolean cutCostsFuel(Player player, BlockState state) {
        return !player.getAbilities().instabuild
            && player.getMainHandItem().is(RCItems.CUTTING_TORCH.get())
            && state.is(RCTags.MINEABLE_WITH_CUTTING_TORCH);
    }

    /** Spends one Oily Rag, or reports that the player has none. */
    public static boolean spendFuel(Player player) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.is(RCItems.OILY_RAG.get())) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }
}

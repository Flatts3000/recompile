package com.flatts.recompile.event;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.item.CuttingTorchItem;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCTags;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * The Cutting Torch burns an Oily Rag per cut (spec {@code docs/steel_cutting_torch_spec.md}).
 *
 * <p>The torch carries its own charge, fed by rags ahead of time ({@link CuttingTorchItem}), so cutting
 * spends from the tool rather than reaching into the player's pack. Fuelling is a deliberate act you can
 * see on the item's gauge, and a torch you forgot to charge is empty when you find the steel - which is a
 * legible failure, where silently eating rags out of the pack mid-swing is not.
 *
 * <p>This replaces the v1 durability-as-fuel-tank model, where the torch was the consumable and ran down
 * whether or not you had fuel - making the rag a one-off crafting cost rather than an ongoing one. The torch
 * is now {@code UNBREAKABLE} and the rag line is the real sink.
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

    /** Spends one charge from the held torch, or reports that it is dry. */
    public static boolean spendFuel(Player player) {
        return CuttingTorchItem.spendCut(player.getMainHandItem());
    }
}

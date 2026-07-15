package com.flatts.recompile.content.item;

import com.flatts.recompile.registry.RCItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A sealed tin can (design P1.9): inedible until opened. Right-click with a scrap
 * knife anywhere in the inventory to open it into a {@code tin_can_open}; the knife
 * is a tool and is not consumed. No knife, no lunch.
 */
public class SealedCanItem extends Item {

    public SealedCanItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!hasKnife(player)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            stack.shrink(1);
            ItemStack opened = new ItemStack(RCItems.TIN_CAN_OPEN.get());
            if (!player.getInventory().add(opened)) {
                player.drop(opened, false);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.PLAYERS, 0.7F, 1.4F);
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean hasKnife(Player player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(RCItems.SCRAP_KNIFE.get())) {
                return true;
            }
        }
        return false;
    }
}

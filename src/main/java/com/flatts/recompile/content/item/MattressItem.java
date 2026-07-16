package com.flatts.recompile.content.item;

import com.flatts.recompile.registry.RCItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * The mattress: your bed, or your rope. Not both.
 *
 * <p>Place it on a block face and it is a bed. Right-click <em>air</em> with a scrap
 * knife in the inventory and you cut it open instead - the same verb, and the same code
 * path, as opening a tin can (see {@link KnifeWork}). Breaking a placed mattress always
 * returns the item, so a bed can be relocated; the knife is its only exit.
 *
 * <p>The cut is the mod's early source of {@code minecraft:string}: the starting biome
 * spawns nothing, so there are no spiders, no cobwebs and no fishing. Springs come out as
 * scrap metal rather than an item of their own - there is nothing yet for a spring to do,
 * and minting an item without a job is how a mod bloats.
 */
public class MattressItem extends BlockItem {

    private static final int STRING = 4;
    private static final int FIBER = 2;
    private static final int SPRINGS = 1;

    public MattressItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!KnifeWork.hasKnife(player)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            // Creative keeps its stack - same guard as the tin can.
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            KnifeWork.give(player, new ItemStack(Items.STRING, STRING));
            KnifeWork.give(player, new ItemStack(RCItems.FIBER_SCRAP.get(), FIBER));
            KnifeWork.give(player, new ItemStack(RCItems.SCRAP_METAL.get(), SPRINGS));
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.8F, 1.1F);
        }
        return InteractionResult.SUCCESS;
    }
}

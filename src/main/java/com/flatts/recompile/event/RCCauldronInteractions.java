package com.flatts.recompile.event;

import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.cauldron.CauldronInteractions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LayeredCauldronBlock;

/**
 * Hydrating a Dry Clay Body in a water cauldron, which is the last step of the clay chain (#115).
 *
 * <p><b>Why a cauldron and not a machine.</b> Grog plus bentonite plus water is three inputs, and a
 * {@link CauldronInteraction} is one held item against a cauldron state. The dry blend collapses that to
 * one solid plus water, which a cauldron can express - so no new block, no GUI, nothing stored. The
 * cauldron does only what a cauldron does, which is supply water. That is the Sorting Tarp idiom, and
 * the reason this feature adds no machine at all.
 *
 * <p><b>The chemistry is why the blend has to exist first.</b> Grog is crushed fired ceramic and firing
 * is irreversible - above roughly 550-600 C kaolinite dehydroxylates and the bound hydroxyls leave the
 * lattice permanently, so there is nothing left to rehydrate. Grinding a sherd and adding water cannot
 * make clay, which was the original proposal and is the one link in the chain that could not be made
 * honest. The plasticity comes from the bentonite instead.
 *
 * <p><b>It costs a level of water</b>, the way every other cauldron recipe does. Water is the P1.10
 * economy's scarce input and a chain that drained nothing would be quietly free.
 */
public final class RCCauldronInteractions {

    private RCCauldronInteractions() {
    }

    /**
     * Registered from the mod constructor, not from a data file.
     *
     * <p>{@code CauldronInteractions.WATER} is a plain map keyed by item, so this is a put rather than
     * anything reloadable - which also means a pack cannot add a hydration recipe without Java. That is
     * a real limit and an accepted one: the interaction is a behaviour, not a recipe, and the mod has
     * exactly one.
     */
    public static void register() {
        CauldronInteractions.WATER.put(RCItems.DRY_CLAY_BODY.get(), RCCauldronInteractions::hydrate);
    }

    private static InteractionResult hydrate(net.minecraft.world.level.block.state.BlockState state,
                                             net.minecraft.world.level.Level level,
                                             net.minecraft.core.BlockPos pos,
                                             net.minecraft.world.entity.player.Player player,
                                             net.minecraft.world.InteractionHand hand,
                                             ItemStack held) {
        if (level.isClientSide()) {
            // SUCCESS on the client so the arm swings; the server does the work. Returning the server's
            // answer here would swing on a failure too.
            return InteractionResult.SUCCESS;
        }
        // Give the clay BEFORE lowering the level, so a full inventory cannot lose the water as well as
        // the blend - addItem returning false is why this is not fire-and-forget.
        ItemStack clay = new ItemStack(Items.CLAY_BALL, 1);
        if (!player.getInventory().add(clay)) {
            player.drop(clay, false);
        }
        held.consume(1, player);
        player.awardStat(Stats.USE_CAULDRON);
        LayeredCauldronBlock.lowerFillLevel(state, level, pos);
        level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.4F, 1.4F);
        return InteractionResult.SUCCESS;
    }
}

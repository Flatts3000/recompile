package com.flatts.recompile.event;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.ScrapBinBlock;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Functional Storage's left-click extraction for the Scrap Bin: a left-click tap pulls one item, a
 * sneak-tap pulls a stack. There is no {@code Block.attack} hook in 26.1, so this rides the
 * {@code LeftClickBlock} event, on the initial press ({@code START}) only, so one tap = one extract.
 *
 * <p><b>The break is deliberately not cancelled.</b> A tap does not chip a strength-1.4 block, so it
 * only extracts; holding left-click still breaks the bin - which is how you pick up a full one, its
 * contents riding along on the dropped item. Cancelling would trap the contents until you emptied it
 * by hand, defeating the carry-through-break design.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID)
public final class RCScrapBinInteraction {

    private RCScrapBinInteraction() {
    }

    @SubscribeEvent
    static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (level.getBlockState(pos).is(RCBlocks.SCRAP_BIN.get())) {
            ScrapBinBlock.extract(level, pos, event.getEntity());
        }
    }
}

package com.flatts.recompile.event;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.MattressBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;

/**
 * A Dirty Mattress is destroyed by sleeping on it (#128).
 *
 * <p><b>Why this exists at all.</b> The bed is the mod's proof of concept - every wool-to-bed recipe is
 * deleted and the only route is a blueprint-gated Clean Mattress plus planks. But a Dirty Mattress is a
 * <em>found</em> item that already skips the night, so the chain it is meant to open was one nobody
 * needed to walk: the found version did the job and the crafted version added nothing a player could
 * feel. Playtest put it plainly - "making it pointless to craft anything better".
 *
 * <p>Single use fixes that without a punishment mechanic. A Dirty Mattress becomes what it always should
 * have been: <b>a way to survive a night, not a place to live.</b>
 *
 * <p><b>On waking, not on interact.</b> That distinction is the whole correctness of this class. A player
 * who right-clicks in daylight, or with a monster nearby, is refused the sleep - and destroying the block
 * at that point would take the mattress and give nothing back, for an action the game itself rejected.
 * {@code useWithoutItem} cannot tell the difference, because {@code startSleepInBed} reports the refusal
 * asynchronously through its own result. Waking is the only moment that proves a night was actually
 * spent.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID)
public final class RCMattressWear {

    private RCMattressWear() {
    }

    @SubscribeEvent
    public static void onWake(PlayerWakeUpEvent event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide()) {
            return;
        }
        // Read the position BEFORE anything else: this fires inside stopSleepInBed, and the sleeping
        // position is the only record of which block was slept in.
        player.getSleepingPos().ifPresent(pos -> consume(level, pos));
    }

    /**
     * Destroy both halves of the mattress at {@code pos}, if that is what was slept in.
     *
     * <p>Both halves, and with no drops: the point is that the mattress is spent, so returning the item
     * would be the same as not consuming it. A head left behind with no foot is also a half-block a
     * player cannot remove without breaking it, which is a worse state than either outcome.
     */
    private static void consume(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof MattressBlock)) {
            return;   // a real bed, or a mattress already gone - either way not ours to remove
        }
        BlockPos other = state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD
            ? pos.relative(state.getValue(MattressBlock.FACING).getOpposite())
            : pos.relative(state.getValue(MattressBlock.FACING));
        if (level.getBlockState(other).getBlock() instanceof MattressBlock) {
            level.removeBlock(other, false);
        }
        level.removeBlock(pos, false);
        level.levelEvent(2001, pos, Block.getId(state));   // the ordinary block-break puff and sound
    }
}

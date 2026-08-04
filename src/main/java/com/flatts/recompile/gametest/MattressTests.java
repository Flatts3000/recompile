package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.MattressBlock;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * The Dirty Mattress is destroyed by sleeping on it (#128).
 *
 * <p>The half that needs proving is not that it breaks - it is that it breaks <b>only</b> when a night
 * was actually spent. A player refused the sleep (daylight, monsters nearby) must keep their mattress,
 * and the natural mistake is to consume it on right-click, where the refusal is invisible.
 */
final class MattressTests {

    private static final BlockPos FOOT = new BlockPos(1, 2, 1);

    private MattressTests() {
    }

    static void register() {
        // Waking from it spends the mattress - both halves, no drops.
        //
        // Driven through startSleeping rather than startSleepInBed on purpose. The latter is vanilla's
        // gate (night, no monsters, in range) and this test is not about vanilla's gate; forcing the
        // sleeping position is what puts the wake handler in exactly the state a real night leaves it.
        RCGameTests.test("waking_on_a_dirty_mattress_destroys_it", 60, helper -> {
            BlockPos head = place(helper);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            // makeMockServerPlayerInLevel does NOT default to survival - its abilities carry
            // instabuild, and a creative player is exempt from half the rules this test is about.
            player.setGameMode(GameType.SURVIVAL);

            player.startSleeping(helper.absolutePos(head));
            helper.assertTrue(player.isSleeping(),
                "the mock player never got to sleep, so this test would pass without proving anything");

            player.stopSleepInBed(true, true);
            helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.AIR, head);
            helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.AIR, FOOT);
            helper.succeed();
        });

        // INTERACTING WITH IT NEVER DESTROYS IT. This is the whole reason RCMattressWear hangs off the
        // wake event rather than off useWithoutItem: a player refused the sleep - daylight, a monster
        // nearby - would otherwise lose the mattress for an action the game itself rejected, and
        // useWithoutItem cannot tell a refusal from a success because startSleepInBed reports it
        // through its own result.
        RCGameTests.test("interacting_with_a_dirty_mattress_never_destroys_it", 60, helper -> {
            BlockPos head = place(helper);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);

            // Whether this world happens to be day or night, and whether the sleep is taken or
            // refused, the block must still be there when the click returns. Only waking spends it.
            helper.getLevel().getBlockState(helper.absolutePos(head))
                .useWithoutItem(helper.getLevel(), player, new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(head)),
                    Direction.UP, helper.absolutePos(head), false));

            helper.assertBlockPresent(RCBlocks.MATTRESS.get(), head);
            helper.assertBlockPresent(RCBlocks.MATTRESS.get(), FOOT);
            helper.succeed();
        });
    }

    /** Lay a two-half mattress and return the HEAD position. */
    private static BlockPos place(net.minecraft.gametest.framework.GameTestHelper helper) {
        BlockPos head = FOOT.relative(Direction.NORTH);
        helper.setBlock(FOOT, RCBlocks.MATTRESS.get().defaultBlockState()
            .setValue(MattressBlock.FACING, Direction.NORTH)
            .setValue(MattressBlock.PART, BedPart.FOOT));
        helper.setBlock(head, RCBlocks.MATTRESS.get().defaultBlockState()
            .setValue(MattressBlock.FACING, Direction.NORTH)
            .setValue(MattressBlock.PART, BedPart.HEAD));
        return head;
    }
}

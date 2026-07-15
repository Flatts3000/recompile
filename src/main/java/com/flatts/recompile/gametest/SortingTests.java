package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.GarbageBlock;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * GameTests for the hand-sorting mechanic (design P0.4): pulling materials from a
 * garbage block and the block crumbling after 1-3 pulls.
 */
final class SortingTests {

    private SortingTests() {
    }

    static void register() {
        // Place a garbage block, pull the maximum number of times, and assert it
        // yielded material item entities and then crumbled to air.
        RCGameTests.test("garbage_block_sorts_then_crumbles", 60, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.GARBAGE_BLOCK.get());

            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(pos);

            boolean crumbled = false;
            for (int i = 0; i < 3 && !crumbled; i++) {
                crumbled = GarbageBlock.sortOnce(level, abs);
            }

            helper.assertTrue(crumbled, "garbage block should crumble within 3 pulls");
            helper.assertBlockPresent(Blocks.AIR, pos);
            helper.assertEntityPresent(EntityType.ITEM);
            helper.succeed();
        });

        // Regression: hand pulls were gated only by the client's 4-tick use delay, so
        // holding right-click tore blocks apart faster than digging them out with the
        // junk shovel - hands beat tools at clearing ground. A pull must put the
        // player's empty hand on cooldown, and a pull inside that window must be
        // refused. Asserted on the cooldown itself, not on `sorted`, because whether a
        // block survives a given pull is deliberately random.
        RCGameTests.test("hand_pulls_are_rate_limited", 20, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.GARBAGE_BLOCK.get());
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            Vec3 standing = helper.absoluteVec(pos.above().getCenter());
            player.snapTo(standing.x, standing.y, standing.z);

            helper.assertFalse(player.getCooldowns().isOnCooldown(ItemStack.EMPTY),
                "empty hand must start off cooldown");
            helper.useBlock(pos, player);
            helper.assertTrue(player.getCooldowns().isOnCooldown(ItemStack.EMPTY),
                "a bare-hand pull must put the hand on cooldown so holding right-click "
                    + "cannot out-clear the shovel");
            helper.succeed();
        });
    }
}

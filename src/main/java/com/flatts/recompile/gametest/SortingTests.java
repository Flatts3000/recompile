package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.GarbageBlock;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * GameTests for the hand-sorting mechanic (design P0.4): pulling materials from a
 * garbage block and the block crumbling after 2-3 pulls, plus the tool rules the
 * sorting economy leans on.
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

        // Every block has exactly one tool, and that tool must be the fast way to take it
        // apart - otherwise the block is stranded where it stands. The bale is the knife's
        // (it was the richest block but the slowest to cash in, because nothing mined it
        // faster than bare hands); garbage is the shovel's; the barrel is the prybar's -
        // it is welded steel, so an axe, the vanilla barrel's tool, has no business here.
        // Asserts the tags + TOOL components are really wired, which a compile cannot see,
        // and that no tool poaches another's block.
        RCGameTests.test("one_tool_per_block", 20, helper -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            BlockState garbage = RCBlocks.GARBAGE_BLOCK.get().defaultBlockState();
            BlockState bale = RCBlocks.COMPACTED_BALE.get().defaultBlockState();
            BlockState barrel = RCBlocks.SCRAP_BARREL.get().defaultBlockState();

            // Each tool against every block, so a tag pointed at the wrong one is caught.
            record Tool(String name, Item item, BlockState owns) {}
            List<Tool> tools = List.of(
                new Tool("junk shovel", RCItems.JUNK_SHOVEL.get(), garbage),
                new Tool("scrap knife", RCItems.SCRAP_KNIFE.get(), bale),
                new Tool("prybar", RCItems.PRYBAR.get(), barrel));
            List<BlockState> blocks = List.of(garbage, bale, barrel);

            for (Tool tool : tools) {
                for (BlockState block : blocks) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    float bare = player.getDestroySpeed(block);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool.item()));
                    float withTool = player.getDestroySpeed(block);

                    if (block == tool.owns()) {
                        helper.assertTrue(withTool > bare, tool.name()
                            + " must mine its own block faster than bare hands (got "
                            + withTool + " vs " + bare + ") - check its recompile:mineable/* tag");
                    } else {
                        helper.assertTrue(withTool == bare, tool.name()
                            + " must not poach another tool's block (got " + withTool
                            + " vs " + bare + " bare)");
                    }
                }
            }
            helper.succeed();
        });

        // Regression: minPulls was 1, so a third of garbage blocks (and half of all
        // bags) vanished on the very first pull. An instant break lets bare hands strip
        // ground faster than any tool, and no cooldown can fix it - the block is already
        // gone. The first pull must never destroy a sortable block. Run enough trials
        // that a reintroduced 1-in-3 would be caught every time.
        RCGameTests.test("first_pull_never_destroys_a_block", 60, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = new BlockPos(1, 1, 1);
            BlockPos abs = helper.absolutePos(pos);

            for (Block block : List.of(RCBlocks.GARBAGE_BLOCK.get(), RCBlocks.TRASH_BAG.get(),
                    RCBlocks.COMPACTED_BALE.get())) {
                for (int trial = 0; trial < 40; trial++) {
                    helper.setBlock(pos, block);
                    boolean crumbled = SortableBlock.sortOnce(level, abs);
                    helper.assertFalse(crumbled,
                        block + " must survive its first pull - an instant break lets hands "
                            + "out-clear tools (minPulls must stay >= 2)");
                }
            }
            helper.setBlock(pos, Blocks.AIR);
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

package com.flatts.recompile.gametest;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.content.block.GarbageBlock;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        //
        // Roaches are turned OFF for the duration, and that is the fix for a real flake (#94).
        // A released roach returns early from sortOnce and deliberately does NOT advance the sorted
        // count - "the block is not consumed by an encounter" - so an encounter eats one of the three
        // iterations. A garbage block is minPulls 2 / maxPulls 3, so it needs three EFFECTIVE pulls to
        // be certain to crumble, and roaches fired 1-in-40 when this was found:
        //
        //     P(at least one roach in 3 pulls) = 1 - (39/40)^3 = 7.3%
        //
        // which is how often this failed. The rate has since been tuned to 1-in-800, which lowers the
        // odds to 0.9% without removing them - the isolation below is what actually fixes it, and a
        // rarer flake is a worse flake because it survives longer before anyone believes it. It gates every merge, so roughly one merge in fourteen
        // stalled on a test that was not describing a defect - the exact rate at which people start
        // re-running CI without reading it. The crumble curve is what this test is about, so it
        // isolates the crumble curve; the roach branch has its own coverage in RoachTests.
        RCGameTests.test("garbage_block_sorts_then_crumbles", 60, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.GARBAGE_BLOCK.get());

            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(pos);

            boolean was = RCConfig.ROACHES_ENABLED.get();
            boolean crumbled = false;
            try {
                RCConfig.ROACHES_ENABLED.set(false);
                for (int i = 0; i < 3 && !crumbled; i++) {
                    crumbled = GarbageBlock.sortOnce(level, abs);
                }
            } finally {
                // Restore in finally: this config is global, so leaking it off would silently disable
                // roaches for every test that runs after this one.
                RCConfig.ROACHES_ENABLED.set(was);
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

            BlockState bulky = RCBlocks.BULKY_WASTE.get().defaultBlockState();

            // Each tool against every block, so a tag pointed at the wrong one is caught.
            // `owns` is a LIST: one block has one tool, but a tool may own several - the
            // prybar digs out both the barrel and bulky waste.
            record Tool(String name, Item item, List<BlockState> owns) {}
            List<Tool> tools = List.of(
                new Tool("junk shovel", RCItems.JUNK_SHOVEL.get(), List.of(garbage)),
                new Tool("scrap knife", RCItems.SCRAP_KNIFE.get(), List.of(bale)),
                new Tool("prybar", RCItems.PRYBAR.get(), List.of(barrel, bulky)));
            List<BlockState> blocks = List.of(garbage, bale, barrel, bulky);

            for (Tool tool : tools) {
                for (BlockState block : blocks) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    float bare = player.getDestroySpeed(block);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool.item()));
                    float withTool = player.getDestroySpeed(block);

                    if (tool.owns().contains(block)) {
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

        // Bare hands must not carry off a sortable pile - only the trash bag, which is loose surface
        // litter you can gather by the armful (owner, 2026-08-05).
        //
        // Both directions per block, because either half alone passes for the wrong reason: a block
        // that requires a tool NOBODY has is unharvestable rather than gated, and that fails silently -
        // it looks exactly like a gated block until you go to pick one up. So this asserts the gate is
        // on AND that the intended tool actually satisfies it. Stone Rubble and Mechanical Waste were
        // in no mineable tag at all when the gate went on, which is precisely that trap.
        //
        // SORTING is untouched by any of this and stays bare-hand: the harvest check does not run on
        // use, and the crumble path is destroyBlock(pos, false), which drops nothing.
        // Asserted through Player.hasCorrectToolForDrops, which is the predicate that actually decides
        // a drop: `!requiresCorrectToolForDrops() || selected.isCorrectToolForDrops(state)`. Asking
        // the ItemStack alone is the wrong question and gets a wrong answer on the exemption - an
        // empty hand is not "a correct tool" for a Trash Bag, the bag simply never asks.
        RCGameTests.test("bare_hands_carry_off_only_the_trash_bag", 20, helper -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            record Pile(String name, Block block, Item tool) {}
            List<Pile> gated = List.of(
                new Pile("Block of Garbage", RCBlocks.GARBAGE_BLOCK.get(), RCItems.JUNK_SHOVEL.get()),
                new Pile("Compacted Bale", RCBlocks.COMPACTED_BALE.get(), RCItems.SCRAP_KNIFE.get()),
                new Pile("Stone Rubble", RCBlocks.STONE_RUBBLE.get(), RCItems.JUNK_SHOVEL.get()),
                // Machinery, so a pickaxe rather than the shovel - and the WOODEN one on purpose, to
                // prove no tier gate crept in. This mod ships no pickaxe of its own, so the tool is
                // vanilla's and arrives with the Tree Nursery's wood, well before the yard.
                new Pile("Mechanical Waste", RCBlocks.MECHANICAL_WASTE.get(), Items.WOODEN_PICKAXE));

            for (Pile pile : gated) {
                BlockState state = pile.block().defaultBlockState();
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                helper.assertFalse(player.hasCorrectToolForDrops(state),
                    pile.name() + " must not drop for a bare hand - a mineable tag alone is only a "
                        + "speed bonus, it takes requiresCorrectToolForDrops to gate the drop");
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(pile.tool()));
                helper.assertTrue(player.hasCorrectToolForDrops(state),
                    pile.name() + " must drop for its own tool - if this fails the block is "
                        + "unharvestable by anything, which reads exactly like a working gate");
            }

            // The Junk Shovel is not special-cased: ANY shovel carries off a shovel pile (owner,
            // 2026-08-05). That falls out of tagging the blocks mineable/shovel with no needs_*_tool
            // tag beside them, so nothing enforces it and a tier tag added later would silently take
            // it away. The WOODEN shovel is the one worth asserting - it is the weakest in the game
            // and below the Junk Shovel's own stone tier, so it fails the moment a tier gate appears.
            for (Block pile : List.of(RCBlocks.GARBAGE_BLOCK.get(), RCBlocks.STONE_RUBBLE.get())) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SHOVEL));
                helper.assertTrue(player.hasCorrectToolForDrops(pile.defaultBlockState()),
                    "any shovel must carry off " + pile.getName().getString() + ", not just the Junk "
                        + "Shovel - check no needs_*_tool tag has been added");
            }

            // Mechanical Waste moved from the shovel to the pickaxe (owner, 2026-08-05), and asserting
            // only that a pickaxe works would not pin the move - leaving it in both tags would pass.
            // The shovel must now fail on it, which is what makes it the pickaxe's block.
            BlockState machinery = RCBlocks.MECHANICAL_WASTE.get().defaultBlockState();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RCItems.JUNK_SHOVEL.get()));
            helper.assertFalse(player.hasCorrectToolForDrops(machinery),
                "the Junk Shovel must NOT carry off Mechanical Waste - it is machinery on the pickaxe, "
                    + "and Stone Rubble beside it is the shovel's");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
            helper.assertFalse(player.hasCorrectToolForDrops(
                    RCBlocks.STONE_RUBBLE.get().defaultBlockState()),
                "a pickaxe must NOT carry off Stone Rubble - that one stays shovel work");

            // The one exemption, asserted so nobody "finishes the set" by gating it too.
            BlockState bag = RCBlocks.TRASH_BAG.get().defaultBlockState();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertFalse(bag.requiresCorrectToolForDrops(),
                "the Trash Bag is deliberately still bare-hand: it is loose surface litter, and it is "
                    + "the block a new player meets first");
            helper.assertTrue(player.hasCorrectToolForDrops(bag),
                "a bare hand must still pick up a Trash Bag");

            // A shovel helps on the bag without being required - it is in mineable/shovel for speed
            // only (owner, 2026-08-05). Asserted on destroy SPEED, because that is the whole of the
            // difference: hasCorrectToolForDrops answers true either way on an ungated block, so it
            // cannot tell the tag apart from no tag at all.
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            float bare = player.getDestroySpeed(bag);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RCItems.JUNK_SHOVEL.get()));
            helper.assertTrue(player.getDestroySpeed(bag) > bare,
                "a shovel must clear Trash Bags faster than bare hands - check trash_bag is in "
                    + "minecraft:mineable/shovel");
            helper.succeed();
        });

        // The refusal, which is the half that makes the gate humane. requiresCorrectToolForDrops
        // suppresses the DROP but not the mining, so without RCHarvestGate a bare-hand punch - the
        // universal Minecraft reflex, and a garbage block goes in about a second - would delete the
        // pile and say nothing. Cancelling instead is the same call RCTorchFuel already made.
        //
        // Asserted through the static entry point rather than by firing the event, the way
        // RCTorchFuel's tests drive cutCostsFuel.
        RCGameTests.test("digging_a_pile_without_its_tool_is_refused", 20, helper -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            BlockPos probe = new BlockPos(1, 1, 1);
            List<Block> gated = com.flatts.recompile.event.RCHarvestGate.gatedSortables();
            helper.assertTrue(gated.size() == 6,
                "expected 6 gated sortable piles (garbage, bale, rubble, mechanical waste, and the "
                    + "Nether's techno-organic waste and slag rubble), got " + gated.size()
                    + " - a new one is fine, but say so here");

            for (Block block : gated) {
                BlockState state = block.defaultBlockState();
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                helper.assertTrue(
                    com.flatts.recompile.event.RCHarvestGate.refusesDig(player, state, helper.absolutePos(probe)),
                    block.getName().getString() + " must refuse a bare-hand dig rather than break "
                        + "and drop nothing");
                // And the message must name a real tool, not fall through to the generic.
                helper.assertFalse(
                    com.flatts.recompile.event.RCHarvestGate.toolKey(state)
                        .equals("tool.recompile.right_tool"),
                    block.getName().getString() + " is in no tool tag, so the nudge cannot say what "
                        + "to bring - that also means nothing can harvest it");
            }

            // The nudge's own lang keys, which nothing else covers: GuidebookTests only walks the book
            // and RegistryCompletenessTests only walks item and block names. An unresolved key here
            // renders raw to the player at the exact moment the message exists to help them, and it is
            // invisible to a compile. Same idiom as every_guidebook_lang_key_resolves.
            for (Block block : gated) {
                String key = com.flatts.recompile.event.RCHarvestGate.toolKey(block.defaultBlockState());
                helper.assertFalse(Component.translatable(key).getString().equals(key),
                    "the nudge for " + block.getName().getString() + " names lang key " + key
                        + ", which has no translation - the player would be shown the raw key");
            }
            String message = "message.recompile.needs_dig_tool";
            helper.assertFalse(Component.translatable(message).getString().equals(message),
                message + " has no translation, so the nudge would render as its own key");

            // The bag is not gated, so it must never be refused.
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertFalse(
                com.flatts.recompile.event.RCHarvestGate.refusesDig(
                    player, RCBlocks.TRASH_BAG.get().defaultBlockState(), helper.absolutePos(probe)),
                "a Trash Bag must never refuse a bare hand");
            helper.succeed();
        });

        // And the same thing END TO END, because the test above only proves the predicate is right.
        // It would pass with @EventBusSubscriber missing, the wrong event subscribed, or setCanceled
        // never called - the feature dead and the rule perfectly stated. This breaks a real block with
        // a real server player and looks at whether it is still standing.
        RCGameTests.test("a_bare_hand_cannot_actually_break_a_garbage_block", 20, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.GARBAGE_BLOCK.get());
            // instabuild is set on a mock server player and skips the harvest check entirely, so
            // without this the test asserts the creative exemption instead of the rule.
            var player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            player.gameMode.destroyBlock(helper.absolutePos(pos));
            helper.assertTrue(helper.getBlockState(pos).is(RCBlocks.GARBAGE_BLOCK.get()),
                "a bare hand must not break a Block of Garbage at all - the break is cancelled, so "
                    + "the pile survives to be sorted, got " + helper.getBlockState(pos));

            // The other half: with the tool it really does come up. Without this the test passes on a
            // block that is simply unbreakable by anybody.
            player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(RCItems.JUNK_SHOVEL.get()));
            player.gameMode.destroyBlock(helper.absolutePos(pos));
            helper.assertBlockPresent(Blocks.AIR, pos);
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

            // Roaches off here too, for a subtler reason than the flake above (#94). This asserts
            // crumbled == false, and a roach release ALSO returns false - so a roach trial passes
            // while asserting nothing about the first-pull rule. It could never go red, it just
            // quietly stopped testing about one garbage trial in forty.
            boolean was = RCConfig.ROACHES_ENABLED.get();
            try {
                RCConfig.ROACHES_ENABLED.set(false);
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
            } finally {
                RCConfig.ROACHES_ENABLED.set(was);
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

package com.flatts.recompile.gametest;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.content.block.MoundGroundBlock;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.content.block.entity.ChargingStationBlockEntity;
import com.flatts.recompile.content.item.GarbageVacuumItem;
import com.flatts.recompile.content.item.GarbageVacuumItem.Intake;
import com.flatts.recompile.content.item.VacuumTier;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCEntities;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * GameTests for the Garbage Vacuum and its Charging Station (#336, spec {@code docs/garbage_vacuum_spec.md}).
 *
 * <p>Driven through the static entry points - {@link GarbageVacuumItem#intakeOnce} and
 * {@link ChargingStationBlockEntity#chargeOnce} - rather than by simulating a held right-click, the
 * {@code sortOnce} convention. The flight of a vacuumed block is the one thing that is allowed to run
 * on real ticks, because arriving is the contract and a test that delivers the item by hand would prove
 * nothing about the entity that carries it.
 *
 * <p>Players are made SURVIVAL first. {@code makeMockServerPlayerInLevel} has {@code instabuild} set,
 * and a creative player takes for free - so without that line every charge assertion here would pass
 * for the wrong reason.
 */
final class GarbageVacuumTests {

    private GarbageVacuumTests() {
    }

    /** Where the intake is aimed; the plot is 5x5x5 so a radius-2 volume around it stays inside. */
    private static final BlockPos AIM = new BlockPos(1, 2, 1);
    /** Three blocks from AIM along x: outside copper's reach (2), inside iron's (3). */
    private static final BlockPos FAR = new BlockPos(4, 2, 1);
    private static final BlockPos FOOT = new BlockPos(2, 1, 2);
    private static final BlockPos DOCK = new BlockPos(1, 1, 1);

    /**
     * The block the crosshair rests on in the hold tests, so the aim point cannot move mid-stream.
     *
     * <p>{@code onUseTick} recomputes {@link GarbageVacuumItem#aimPoint} every tick off
     * {@code player.pick}, so a test that aimed AT the pile it was about to take would re-centre its own
     * intake volume the instant that pile left the world, and a cadence measured against a moving cube
     * proves nothing. One stone block here pins it: the ray always stops on this face, and the piles sit
     * around it, inside copper's radius of 2.
     *
     * <p>The position is chosen so copper's radius-2 cube around it is EXACTLY the 5x5x5 plot. Plots are
     * batched a single block apart, so a cube that overhangs the wall can see a neighbouring test's
     * blocks - which would quietly falsify every assertion below of the form "nothing else was takeable".
     */
    private static final BlockPos SIGHT = new BlockPos(2, 2, 2);

    /**
     * A survival player STANDING IN THE PLOT. {@code makeMockServerPlayerInLevel} places its player at
     * world spawn, which on the gametest server is thousands of blocks from the structure; a flying
     * block aimed at that player leaves the loaded chunks and stops ticking, which is a real edge case
     * (the entity now delivers past {@code MAX_FLIGHT_DISTANCE}) but not the flight these tests mean
     * to exercise.
     */
    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        Vec3 standing = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
        player.setPos(standing.x, standing.y, standing.z);
        return player;
    }

    /**
     * A short delivery drop for the regrowth assertion, {@code MoundRegrowthTests}' own reason: the
     * shipped 30-block drop reaches out of the plot into whatever the test world has above it and
     * reports BLOCKED for reasons that have nothing to do with the rule under test.
     */
    private static void withShortDrop(Runnable body) {
        int was = RCConfig.MOUND_REGROWTH_DROP_HEIGHT.get();
        try {
            RCConfig.MOUND_REGROWTH_DROP_HEIGHT.set(2);
            body.run();
        } finally {
            RCConfig.MOUND_REGROWTH_DROP_HEIGHT.set(was);
        }
    }

    private static ItemStack vacuum(DeferredItem<GarbageVacuumItem> tier, int charge) {
        ItemStack stack = new ItemStack(tier.get());
        GarbageVacuumItem.setCharge(stack, charge);
        return stack;
    }

    private static Vec3 centreOf(GameTestHelper helper, BlockPos pos) {
        return Vec3.atCenterOf(helper.absolutePos(pos));
    }

    private static int garbageCost() {
        return VacuumTier.costFor(SortableBlock.sortRolls(RCBlocks.GARBAGE_BLOCK.get().asItem()));
    }

    /**
     * A survival player at the plot's edge looking due south at {@link #SIGHT}, with {@code stack} in
     * hand. Yaw 0 is +z, and the eye at y 2.62 sits inside the sight block's 2..3 band, which is the
     * same geometry {@code a_tap_takes_one_block_and_starts_the_stream} already relies on.
     */
    private static ServerPlayer aimedPlayer(GameTestHelper helper, ItemStack stack) {
        ServerPlayer player = survivalPlayer(helper);
        Vec3 edge = helper.absoluteVec(new Vec3(2.5, 1.0, 0.5));
        player.setPos(edge.x, edge.y, edge.z);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    /** The same player, already mid-hold, as if the click had happened and the trigger were down. */
    private static ServerPlayer holdingPlayer(GameTestHelper helper, ItemStack stack) {
        ServerPlayer player = aimedPlayer(helper, stack);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        return player;
    }

    /**
     * One tick of a hold, {@code elapsed} ticks after the click.
     *
     * <p>Driven through {@code ItemStack.onUseTick}, which is the exact call
     * {@code LivingEntity.updateUsingItem} makes, rather than by letting the server tick the player:
     * {@code ServerPlayer.tick} does not reach {@code LivingEntity.tick} (that is {@code doTick}, driven
     * by the packet listener), so a mock player never runs its own use loop and a real-tick test here
     * would sit there proving nothing. Same reason the rest of this file drives {@code intakeOnce}.
     */
    private static void holdTick(GameTestHelper helper, ServerPlayer player, ItemStack stack, int elapsed) {
        stack.onUseTick(helper.getLevel(), player, stack.getUseDuration(player) - elapsed);
    }

    /** How many of {@code piles} are still the given block. */
    private static int standing(GameTestHelper helper, List<BlockPos> piles, Block block) {
        int count = 0;
        for (BlockPos pos : piles) {
            if (helper.getBlockState(pos).is(block)) {
                count++;
            }
        }
        return count;
    }

    static void register() {
        // The type gate. Derived by class, so a pile is taken and a stone beside it is not, and the
        // block leaves the world without dropping itself on the floor - the entity carries it.
        RCGameTests.test("the_vacuum_takes_a_pile_and_leaves_the_stone_beside_it", 20, helper -> {
            helper.setBlock(AIM, RCBlocks.GARBAGE_BLOCK.get());
            helper.setBlock(AIM.east(), Blocks.STONE);
            ServerLevel level = helper.getLevel();
            ServerPlayer player = survivalPlayer(helper);
            ItemStack stack = vacuum(RCItems.COPPER_GARBAGE_VACUUM, 4_000);

            Intake first = GarbageVacuumItem.intakeOnce(level, player, stack, centreOf(helper, AIM));
            helper.assertTrue(first == Intake.TOOK, "a charged vacuum aimed at garbage must take it, got " + first);
            helper.assertBlockPresent(Blocks.AIR, AIM);
            helper.assertBlockPresent(Blocks.STONE, AIM.east());
            helper.assertEntityPresent(RCEntities.VACUUMED_BLOCK.get());
            helper.assertTrue(GarbageVacuumItem.charge(stack) == 4_000 - garbageCost(),
                "one Block of Garbage must cost exactly its rolls times FE_PER_ROLL, charge is "
                    + GarbageVacuumItem.charge(stack));

            Intake second = GarbageVacuumItem.intakeOnce(level, player, stack, centreOf(helper, AIM));
            helper.assertTrue(second == Intake.NOTHING_IN_RANGE,
                "with only stone left in range the vacuum must take nothing, got " + second);
            helper.succeed();
        });

        // A TAP takes a block. A quick click starts and releases the use inside one tick, so the use
        // loop never runs for it; the first intake is on the click itself. Found by driving the dev
        // client through devbridge, whose `use` verb is exactly a tap: three taps, nothing taken, a
        // tool that only worked when held. The mock player looks along +z from the plot's edge, so
        // the aim point five blocks out is in the air beside a pile within copper's reach.
        RCGameTests.test("a_tap_takes_one_block_and_starts_the_stream", 20, helper -> {
            BlockPos pile = new BlockPos(2, 2, 4);
            helper.setBlock(pile, RCBlocks.GARBAGE_BLOCK.get());
            ServerLevel level = helper.getLevel();
            ServerPlayer player = survivalPlayer(helper);
            Vec3 edge = helper.absoluteVec(new Vec3(2.5, 1.0, 0.5));
            player.setPos(edge.x, edge.y, edge.z);
            player.setYRot(0.0F);
            player.setXRot(0.0F);
            ItemStack stack = vacuum(RCItems.COPPER_GARBAGE_VACUUM, 4_000);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);

            InteractionResult result = stack.getItem().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(result.consumesAction(), "a click on a charged vacuum must be consumed, got " + result);
            helper.assertBlockPresent(Blocks.AIR, pile);
            helper.assertTrue(GarbageVacuumItem.charge(player.getMainHandItem()) == 4_000 - garbageCost(),
                "the click itself must take and pay for one block, charge is "
                    + GarbageVacuumItem.charge(player.getMainHandItem()));
            helper.assertTrue(player.isUsingItem(), "and the hold must be running so a held click keeps taking");
            helper.succeed();
        });

        // THE CADENCE OF A HOLD, which nothing drove before this. Every other test in this file reaches
        // intakeOnce directly, so they prove what ONE intake does and never how often the tool asks for
        // one - the use loop that turns a held trigger into five blocks a second was uncovered code.
        //
        // Delete the four-tick assertions and INTAKE_PERIOD_TICKS is free to drift to 1: twenty blocks
        // a second, a mound gone in the time it takes to look at it, and the FE capacity that separates
        // one tier from the next spent in a fifth of the time it was balanced for. Delete the elapsed-0
        // assertion and a tap takes TWO blocks for one block's charge, because the click already took
        // and paid for one before the loop ever ran.
        RCGameTests.test("a_held_vacuum_takes_one_block_every_four_ticks", 20, helper -> {
            helper.setBlock(SIGHT, Blocks.STONE);
            List<BlockPos> piles = List.of(
                new BlockPos(1, 2, 2), new BlockPos(3, 2, 2), new BlockPos(2, 3, 2));
            for (BlockPos pile : piles) {
                helper.setBlock(pile, RCBlocks.GARBAGE_BLOCK.get());
            }
            Block garbage = RCBlocks.GARBAGE_BLOCK.get();
            ItemStack stack = vacuum(RCItems.COPPER_GARBAGE_VACUUM, 4_000);
            ServerPlayer player = holdingPlayer(helper, stack);

            holdTick(helper, player, stack, 0);
            helper.assertTrue(standing(helper, piles, garbage) == 3,
                "tick zero of a hold IS the click, which use() has already taken and paid for - taking "
                    + "again here hands out two blocks for one block's charge");

            for (int elapsed = 1; elapsed <= 3; elapsed++) {
                holdTick(helper, player, stack, elapsed);
            }
            helper.assertTrue(standing(helper, piles, garbage) == 3,
                "nothing may be taken inside the four-tick window, or the vacuum runs faster than the "
                    + "rate every FE number was balanced against, got "
                    + (3 - standing(helper, piles, garbage)) + " taken");
            helper.assertTrue(GarbageVacuumItem.charge(stack) == 4_000,
                "and nothing may be paid for either, charge is " + GarbageVacuumItem.charge(stack));

            holdTick(helper, player, stack, 4);
            helper.assertTrue(standing(helper, piles, garbage) == 2,
                "the fourth tick must take exactly one block");
            for (int elapsed = 5; elapsed <= 7; elapsed++) {
                holdTick(helper, player, stack, elapsed);
            }
            helper.assertTrue(standing(helper, piles, garbage) == 2,
                "and then wait out another full window");
            holdTick(helper, player, stack, 8);
            helper.assertTrue(standing(helper, piles, garbage) == 1, "the eighth tick takes the second");
            for (int elapsed = 9; elapsed <= 11; elapsed++) {
                holdTick(helper, player, stack, elapsed);
            }
            // Tick 12 is both an intake tick and a sound tick. Stepping over it on purpose: the two
            // periods share one `elapsed`, and a sound period that also took a block would double the
            // rate every twelfth tick without ever looking wrong in a log.
            holdTick(helper, player, stack, 12);
            helper.assertTrue(standing(helper, piles, garbage) == 0,
                "the twelfth tick takes the third - and takes ONE, not one for the intake period and "
                    + "another for the sound period");
            helper.assertTrue(GarbageVacuumItem.charge(stack) == 4_000 - 3 * garbageCost(),
                "three blocks must cost exactly three blocks, charge is "
                    + GarbageVacuumItem.charge(stack));
            helper.assertTrue(player.isUsingItem(),
                "and an empty volume must not end the hold - a player sweeping between two mounds "
                    + "would have to re-click for every gap");
            helper.succeed();
        });

        // THE HOLD LETS GO WHEN THE CELL GOES FLAT. That intakeOnce reports FLAT was already pinned;
        // that the item then drops the trigger was not, and it is the half a player feels. Leave it
        // running and the tool keeps its use animation up, keeps playing its motor every twelve ticks,
        // and keeps scanning a cube of block states five times a second forever, while the trigger it
        // is holding can no longer do anything at all.
        //
        // Paired with its opposite in the same body, because "release on tick 8" passes just as well
        // when the item releases on every tick: with charge for two blocks the same eighth tick must
        // take the second pile and keep going.
        RCGameTests.test("a_hold_lets_go_when_the_vacuum_runs_flat", 20, helper -> {
            helper.setBlock(SIGHT, Blocks.STONE);
            List<BlockPos> piles = List.of(new BlockPos(1, 2, 2), new BlockPos(3, 2, 2));
            Block garbage = RCBlocks.GARBAGE_BLOCK.get();

            for (BlockPos pile : piles) {
                helper.setBlock(pile, garbage);
            }
            ItemStack ample = vacuum(RCItems.COPPER_GARBAGE_VACUUM, 2 * garbageCost());
            ServerPlayer player = holdingPlayer(helper, ample);
            holdTick(helper, player, ample, 4);
            holdTick(helper, player, ample, 8);
            helper.assertTrue(standing(helper, piles, garbage) == 0,
                "with charge for two, both must go");
            helper.assertTrue(player.isUsingItem(),
                "and a hold that is still taking blocks must not let go of the trigger");
            player.releaseUsingItem();

            // Now the same eighth tick with charge for one block only.
            for (BlockPos pile : piles) {
                helper.setBlock(pile, garbage);
            }
            ItemStack thin = vacuum(RCItems.COPPER_GARBAGE_VACUUM, garbageCost());
            player.setItemInHand(InteractionHand.MAIN_HAND, thin);
            player.startUsingItem(InteractionHand.MAIN_HAND);
            holdTick(helper, player, thin, 4);
            helper.assertTrue(GarbageVacuumItem.charge(thin) == 0, "setup: one block must empty it");
            helper.assertTrue(standing(helper, piles, garbage) == 1, "setup: one pile taken, one left");
            helper.assertTrue(player.isUsingItem(), "setup: and the hold is still running");

            holdTick(helper, player, thin, 8);
            helper.assertFalse(player.isUsingItem(),
                "a flat vacuum must release the trigger rather than idle at full rate on a cube it can "
                    + "never take anything out of");
            helper.assertTrue(standing(helper, piles, garbage) == 1,
                "and the pile it could not pay for must still be standing");
            helper.succeed();
        });

        // THE HOLD ALSO LETS GO ON A PILE IT IS NOT RATED FOR, and this is the branch that keeps the
        // ladder learnable. Sweeping a copper vacuum off household garbage and onto mill tailings has
        // to stop and name the pile; without the release the tool keeps humming over tailings forever,
        // which is exactly the "nothing happens, so the tool is broken" reading the message exists to
        // prevent - and this time with the motor still running to say it is working.
        //
        // The rated half is asserted straight after so this cannot pass by releasing on every tick.
        RCGameTests.test("a_hold_lets_go_when_it_meets_a_pile_it_cannot_take", 20, helper -> {
            helper.setBlock(SIGHT, Blocks.STONE);
            BlockPos soft = new BlockPos(1, 2, 2);
            BlockPos hard = new BlockPos(3, 2, 2);
            helper.setBlock(soft, RCBlocks.GARBAGE_BLOCK.get());
            helper.setBlock(hard, RCBlocks.MILL_TAILINGS.get());

            ItemStack copper = vacuum(RCItems.COPPER_GARBAGE_VACUUM, 4_000);
            ServerPlayer player = holdingPlayer(helper, copper);
            holdTick(helper, player, copper, 4);
            helper.assertBlockPresent(Blocks.AIR, soft);
            helper.assertTrue(player.isUsingItem(),
                "setup: taking the garbage must not end the hold");

            holdTick(helper, player, copper, 8);
            helper.assertFalse(player.isUsingItem(),
                "with only mill tailings left, a copper vacuum must let go and say so rather than run "
                    + "on over a pile it can never take");
            helper.assertBlockPresent(RCBlocks.MILL_TAILINGS.get(), hard);

            // Rated for the dump: the same tick, the same pile, and the hold survives it.
            ItemStack diamond = vacuum(RCItems.DIAMOND_GARBAGE_VACUUM, 16_000);
            player.setItemInHand(InteractionHand.MAIN_HAND, diamond);
            player.startUsingItem(InteractionHand.MAIN_HAND);
            holdTick(helper, player, diamond, 4);
            helper.assertBlockPresent(Blocks.AIR, hard);
            helper.assertTrue(player.isUsingItem(),
                "a diamond vacuum is rated for the dump, so tailings must not end its hold");
            helper.succeed();
        });

        // THE CROSSHAIR MUST NOT WIN. This is the shipped fix for the tool's first playtest report -
        // "the vacuum only works when aimed at nothing" - and it had nothing holding it.
        //
        // Vanilla resolves a right-click BLOCK-FIRST: with the crosshair on a pile the chain reaches
        // SortableBlock.useItemOn, and a TRY_WITH_EMPTY_HAND answer there goes straight to the
        // hand-sort. Returning PASS instead is the whole fix, and the failure it prevents is silent -
        // the pile gets picked through, the vacuum never fires, and the tool reads as broken in the one
        // place a player will always point it. Driven through helper.useBlock, which walks the real
        // block-then-item chain, because that ordering IS the bug.
        //
        // The bare hand is the other half: without it this passes just as well against a block that has
        // stopped hand-sorting for everybody.
        RCGameTests.test("a_click_aimed_at_a_pile_runs_the_vacuum_rather_than_hand_sorting_it", 20, helper -> {
            // The pile IS the crosshair target here, which is the whole point: this is the case where
            // the block gets asked about the click before the item does.
            BlockPos pile = new BlockPos(2, 2, 2);
            helper.setBlock(pile, RCBlocks.GARBAGE_BLOCK.get());
            ItemStack stack = vacuum(RCItems.COPPER_GARBAGE_VACUUM, 4_000);
            ServerPlayer player = aimedPlayer(helper, stack);

            helper.useBlock(pile, player);

            helper.assertBlockPresent(Blocks.AIR, pile);
            helper.assertEntityPresent(RCEntities.VACUUMED_BLOCK.get());
            helper.assertEntityNotPresent(EntityType.ITEM);
            helper.assertTrue(GarbageVacuumItem.charge(player.getMainHandItem()) == 4_000 - garbageCost(),
                "the click must be paid for once, charge is "
                    + GarbageVacuumItem.charge(player.getMainHandItem()));
            helper.assertTrue(player.isUsingItem(), "and the hold must have started");

            // Bare-handed, the very same click must still pick through the pile.
            player.stopUsingItem();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.setBlock(pile, RCBlocks.GARBAGE_BLOCK.get());
            // Roaches off, SortingTests' reason: a roach returns early and does NOT advance the sorted
            // count, so about one garbage trial in eight hundred would assert the pull never happened.
            boolean was = RCConfig.ROACHES_ENABLED.get();
            try {
                RCConfig.ROACHES_ENABLED.set(false);
                helper.useBlock(pile, player);
            } finally {
                RCConfig.ROACHES_ENABLED.set(was);
            }
            SortableBlock garbage = (SortableBlock) RCBlocks.GARBAGE_BLOCK.get();
            helper.assertBlockPresent(RCBlocks.GARBAGE_BLOCK.get(), pile);
            helper.assertTrue(garbage.sortedCount(helper.getBlockState(pile)) == 1,
                "a bare hand on the same pile must still hand-sort it - the vacuum's override is scoped "
                    + "to the vacuum, and widening it would silently kill the pick-through loop");
            helper.succeed();
        });

        // A CLICK WITH NO PLAYER BEHIND IT. useOn takes a UseOnContext, whose player is nullable - a
        // dispenser, a fake-player-less automation, any mod calling Item.useOn with a null player. The
        // guard is one line and the failure without it is a NullPointerException inside
        // beginVacuuming's getItemInHand, which does not throw in the caller's face, it kills the
        // server tick that was running the dispenser.
        RCGameTests.test("a_vacuum_click_with_no_player_behind_it_does_nothing", 20, helper -> {
            BlockPos pile = new BlockPos(1, 2, 1);
            helper.setBlock(pile, RCBlocks.GARBAGE_BLOCK.get());
            BlockPos abs = helper.absolutePos(pile);
            ItemStack stack = vacuum(RCItems.COPPER_GARBAGE_VACUUM, 4_000);

            InteractionResult result = stack.getItem().useOn(new UseOnContext(helper.getLevel(), null,
                InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));

            helper.assertTrue(result == InteractionResult.PASS,
                "a playerless click must PASS so the chain carries on, got " + result);
            helper.assertBlockPresent(RCBlocks.GARBAGE_BLOCK.get(), pile);
            helper.assertTrue(GarbageVacuumItem.charge(stack) == 4_000,
                "and nothing may be spent on it, charge is " + GarbageVacuumItem.charge(stack));
            helper.succeed();
        });

        // The charge gate. A vacuum that can pay for exactly one block takes one and then reports
        // FLAT with the second pile still standing - the item's own use loop reads FLAT to let go.
        RCGameTests.test("each_block_costs_charge_and_a_flat_vacuum_stops", 20, helper -> {
            helper.setBlock(AIM, RCBlocks.GARBAGE_BLOCK.get());
            helper.setBlock(AIM.above(), RCBlocks.GARBAGE_BLOCK.get());
            ServerLevel level = helper.getLevel();
            ServerPlayer player = survivalPlayer(helper);
            ItemStack stack = vacuum(RCItems.COPPER_GARBAGE_VACUUM, garbageCost());

            helper.assertTrue(GarbageVacuumItem.intakeOnce(level, player, stack, centreOf(helper, AIM)) == Intake.TOOK,
                "one block's worth of charge must take one block");
            helper.assertTrue(GarbageVacuumItem.charge(stack) == 0, "and leave the vacuum flat");
            Intake next = GarbageVacuumItem.intakeOnce(level, player, stack, centreOf(helper, AIM));
            helper.assertTrue(next == Intake.FLAT, "a flat vacuum facing a pile must report FLAT, got " + next);
            helper.assertBlockPresent(RCBlocks.GARBAGE_BLOCK.get(), AIM.above());
            helper.succeed();
        });

        // Creative takes for free. Asserted on purpose rather than left as an accident of instabuild,
        // because a creative player who cannot clear a mound would file it as a bug.
        RCGameTests.test("a_creative_player_vacuums_for_free", 20, helper -> {
            helper.setBlock(AIM, RCBlocks.GARBAGE_BLOCK.get());
            ServerPlayer creative = helper.makeMockServerPlayerInLevel();
            ItemStack stack = vacuum(RCItems.COPPER_GARBAGE_VACUUM, 0);
            Intake result = GarbageVacuumItem.intakeOnce(helper.getLevel(), creative, stack, centreOf(helper, AIM));
            helper.assertTrue(result == Intake.TOOK, "creative must take with no charge, got " + result);
            helper.assertTrue(GarbageVacuumItem.charge(stack) == 0, "and spend nothing");
            helper.succeed();
        });

        // Reach is the tier. Three blocks out is beyond copper and inside iron; both are asserted so
        // neither half can pass because the volume happened to be huge or empty.
        RCGameTests.test("reach_scales_with_the_tier", 20, helper -> {
            helper.setBlock(FAR, RCBlocks.GARBAGE_BLOCK.get());
            ServerLevel level = helper.getLevel();
            ServerPlayer player = survivalPlayer(helper);
            Intake copper = GarbageVacuumItem.intakeOnce(level, player,
                vacuum(RCItems.COPPER_GARBAGE_VACUUM, 4_000), centreOf(helper, AIM));
            helper.assertTrue(copper == Intake.NOTHING_IN_RANGE,
                "a block 3 out must be beyond copper's radius of " + VacuumTier.COPPER.radius() + ", got " + copper);
            helper.assertBlockPresent(RCBlocks.GARBAGE_BLOCK.get(), FAR);
            Intake iron = GarbageVacuumItem.intakeOnce(level, player,
                vacuum(RCItems.IRON_GARBAGE_VACUUM, 8_000), centreOf(helper, AIM));
            helper.assertTrue(iron == Intake.TOOK,
                "and inside iron's radius of " + VacuumTier.IRON.radius() + ", got " + iron);
            helper.succeed();
        });

        // The contract of the animation: the block ARRIVES. Real ticks, because the entity's flight,
        // timeout and delivery are what is under test; the item lands in the owner's inventory and
        // nothing is left flying.
        RCGameTests.test("a_vacuumed_block_arrives_in_the_owners_inventory", 140, helper -> {
            helper.setBlock(AIM, RCBlocks.GARBAGE_BLOCK.get());
            ServerPlayer player = survivalPlayer(helper);
            Item garbage = RCBlocks.GARBAGE_BLOCK.get().asItem();
            helper.assertTrue(player.getInventory().countItem(garbage) == 0, "the mock player starts empty-handed");
            Intake result = GarbageVacuumItem.intakeOnce(helper.getLevel(), player,
                vacuum(RCItems.COPPER_GARBAGE_VACUUM, 4_000), centreOf(helper, AIM));
            helper.assertTrue(result == Intake.TOOK, "setup: the block must be taken, got " + result);
            helper.succeedWhen(() -> {
                helper.assertTrue(player.getInventory().countItem(garbage) == 1,
                    "the flying block must land in the owner's inventory as one Block of Garbage");
                helper.assertEntityNotPresent(RCEntities.VACUUMED_BLOCK.get());
            });
        });

        // The owner's gravity ruling: take the foot of a stack and the rest falls. Asserted through
        // vanilla's own falling-block entity, which only exists if the neighbour update fired - a
        // removal that suppressed updates would leave the stack hanging and this red.
        RCGameTests.test("taking_the_foot_of_a_stack_lets_it_collapse", 40, helper -> {
            helper.setBlock(FOOT, RCBlocks.GARBAGE_BLOCK.get());
            helper.setBlock(FOOT.above(), RCBlocks.GARBAGE_BLOCK.get());
            helper.setBlock(FOOT.above(2), RCBlocks.GARBAGE_BLOCK.get());
            ServerPlayer player = survivalPlayer(helper);
            Intake result = GarbageVacuumItem.intakeOnce(helper.getLevel(), player,
                vacuum(RCItems.COPPER_GARBAGE_VACUUM, 4_000), centreOf(helper, FOOT));
            helper.assertTrue(result == Intake.TOOK, "setup: the foot must be taken, got " + result);
            helper.assertBlockPresent(Blocks.AIR, FOOT);
            helper.runAfterDelay(6, () -> {
                helper.assertEntityPresent(EntityType.FALLING_BLOCK);
                helper.succeed();
            });
        });

        // Phase 5's promise survives the tool: strip a mound to its ground and the ground still
        // remembers it. This is the assertion the issue asked for by name - a bulk tool is only
        // reasonable because mounds are renewable, and an "optimisation" that took the ground with
        // the garbage would turn it extractive in silence.
        RCGameTests.test("a_stripped_mound_still_regrows", 40, helper -> {
            helper.setBlock(FOOT, RCBlocks.MOUND_GROUND.get().defaultBlockState()
                .setValue(MoundGroundBlock.HEIGHT, 2));
            helper.setBlock(FOOT.above(), RCBlocks.GARBAGE_BLOCK.get());
            helper.setBlock(FOOT.above(2), RCBlocks.GARBAGE_BLOCK.get());
            ServerLevel level = helper.getLevel();
            ServerPlayer player = survivalPlayer(helper);
            ItemStack stack = vacuum(RCItems.COPPER_GARBAGE_VACUUM, 4_000);
            Vec3 aim = centreOf(helper, FOOT.above());
            helper.assertTrue(GarbageVacuumItem.intakeOnce(level, player, stack, aim) == Intake.TOOK, "setup: first block");
            helper.assertTrue(GarbageVacuumItem.intakeOnce(level, player, stack, aim) == Intake.TOOK, "setup: second block");
            helper.assertBlockPresent(RCBlocks.MOUND_GROUND.get(), FOOT);
            helper.assertBlockPresent(Blocks.AIR, FOOT.above());
            helper.assertBlockPresent(Blocks.AIR, FOOT.above(2));
            withShortDrop(() -> {
                MoundGroundBlock.Outcome outcome = MoundGroundBlock.regrowOnce(level, helper.absolutePos(FOOT));
                helper.assertTrue(outcome == MoundGroundBlock.Outcome.GREW,
                    "a mound stripped by the vacuum must regrow like one dug by hand, got " + outcome);
            });
            helper.succeed();
        });

        // The dock. Its buffer is filled directly (a generator is not under test), it pushes into the
        // docked vacuum at TRANSFER_PER_TICK through the ITEM capability, and its doors are what the
        // automation policy says: energy in only, no item capability, not a Container.
        RCGameTests.test("the_charging_station_charges_a_docked_vacuum_and_is_energy_in_only", 20, helper -> {
            helper.setBlock(DOCK, RCBlocks.CHARGING_STATION.get());
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(DOCK);
            if (!(level.getBlockEntity(abs) instanceof ChargingStationBlockEntity dock)) {
                helper.fail("the charging station has no BlockEntity");
                return;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                dock.battery().insert(2_000, transaction);
                transaction.commit();
            }
            helper.assertTrue(dock.dock(vacuum(RCItems.COPPER_GARBAGE_VACUUM, 0)).isEmpty(),
                "docking onto an empty station hands nothing back");
            for (int i = 0; i < 3; i++) {
                ChargingStationBlockEntity.chargeOnce(level, abs);
            }
            int expected = 3 * ChargingStationBlockEntity.TRANSFER_PER_TICK;
            helper.assertTrue(GarbageVacuumItem.charge(dock.docked()) == expected,
                "three ticks must move " + expected + " FE into the vacuum, it holds " + GarbageVacuumItem.charge(dock.docked()));
            helper.assertTrue(dock.stored() == 2_000 - expected,
                "and the buffer must drop by exactly that, it holds " + dock.stored());

            EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, abs, null);
            helper.assertTrue(handler != null, "the station must expose Capabilities.Energy.BLOCK or no generator can reach it");
            int accepted;
            int given;
            try (Transaction transaction = Transaction.openRoot()) {
                accepted = handler.insert(100, transaction);
                given = handler.extract(100, transaction);
            }
            helper.assertTrue(accepted == 100, "a generator must be able to push 100 FE in, got " + accepted);
            helper.assertTrue(given == 0, "and nothing may pull FE back out, got " + given);
            helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK, abs, null) == null,
                "no item capability: a pipe must not be able to lift the vacuum off the dock");
            helper.assertTrue(!(dock instanceof Container),
                "not a Container: a hopper must not be able to lift the vacuum off the dock either");
            helper.succeed();
        });

        // A full vacuum stops the dock, so a buffer is never spent into a tool that cannot hold it.
        RCGameTests.test("a_full_vacuum_takes_no_more_charge", 20, helper -> {
            helper.setBlock(DOCK, RCBlocks.CHARGING_STATION.get());
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(DOCK);
            if (!(level.getBlockEntity(abs) instanceof ChargingStationBlockEntity dock)) {
                helper.fail("the charging station has no BlockEntity");
                return;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                dock.battery().insert(2_000, transaction);
                transaction.commit();
            }
            dock.dock(vacuum(RCItems.COPPER_GARBAGE_VACUUM, VacuumTier.COPPER.capacity()));
            int moved = ChargingStationBlockEntity.chargeOnce(level, abs);
            helper.assertTrue(moved == 0, "a full vacuum must take nothing, got " + moved);
            helper.assertTrue(dock.stored() == 2_000, "and the buffer must be untouched, it holds " + dock.stored());
            helper.succeed();
        });

        // Breaking the dock hands back both the dock and what was on it. The vacuum is the expensive
        // half, and a machine that ate it on removal is the pattern this mod has shipped four times.
        RCGameTests.test("breaking_the_charging_station_drops_the_docked_vacuum", 20, helper -> {
            helper.setBlock(DOCK, RCBlocks.CHARGING_STATION.get());
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(DOCK);
            if (!(level.getBlockEntity(abs) instanceof ChargingStationBlockEntity dock)) {
                helper.fail("the charging station has no BlockEntity");
                return;
            }
            dock.dock(vacuum(RCItems.COPPER_GARBAGE_VACUUM, 1_234));
            // GameTestHelper.destroyBlock passes dropBlock=false; the level's own call runs the loot.
            level.destroyBlock(abs, true);
            helper.assertItemEntityPresent(RCItems.COPPER_GARBAGE_VACUUM.get(), DOCK, 2.0);
            helper.assertItemEntityPresent(RCItems.CHARGING_STATION.get(), DOCK, 2.0);
            helper.succeed();
        });

        // SETTING ONE DOWN IS THE WHOLE INTERACTION, and the tests above all reached past it - they
        // called dock() directly, so the block's own right-click was never driven at all. Three separate
        // things are pinned here because each fails differently and all three are silent.
        //
        // Driven through helper.useBlock, the real block-then-item chain, since the third assertion is
        // about what happens when the block DOESN'T take the click.
        RCGameTests.test("docking_a_vacuum_by_hand_takes_it_out_of_your_hand", 20, helper -> {
            helper.setBlock(DOCK, RCBlocks.CHARGING_STATION.get());
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(DOCK);
            if (!(level.getBlockEntity(abs) instanceof ChargingStationBlockEntity dock)) {
                helper.fail("the charging station has no BlockEntity");
                return;
            }
            ServerPlayer player = survivalPlayer(helper);
            player.setItemInHand(InteractionHand.MAIN_HAND,
                vacuum(RCItems.COPPER_GARBAGE_VACUUM, 1_500));

            helper.useBlock(DOCK, player);

            helper.assertTrue(dock.docked().is(RCItems.COPPER_GARBAGE_VACUUM.get()),
                "a right-click holding a vacuum must set it on the dock");
            helper.assertTrue(GarbageVacuumItem.charge(dock.docked()) == 1_500,
                "with the charge it already had - the dock stores a copy of the stack, and a copy that "
                    + "lost the component would silently reset a part-charged tool to empty, got "
                    + GarbageVacuumItem.charge(dock.docked()));
            helper.assertTrue(player.getMainHandItem().isEmpty(),
                "and a survival player must not keep one in hand as well - that is a duplication bug on "
                    + "the most expensive tool in the mod");

            // A click that the dock refuses must PASS all the way through to the held item, or the
            // block becomes a dead face you cannot build against. Asserted with a real placement rather
            // than on the InteractionResult, because SUCCESS on the empty-hand path looks identical in
            // a log and simply eats the click.
            dock.undock();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 4));
            helper.useBlock(DOCK, player);
            helper.assertTrue(dock.isEmpty(),
                "only a Garbage Vacuum may sit on the dock - anything else and the block is a one-slot "
                    + "chest nothing can get back out of");
            helper.assertBlockPresent(Blocks.STONE, DOCK.north());

            // Creative keeps its copy, the standard creative contract. The ability is SET rather than
            // relied on: makeMockServerPlayerInLevel does default to instabuild, so this worked either
            // way, but an assertion that is ABOUT that flag should not be one implicit-default change
            // away from silently testing the survival path instead. The mattress test in this same
            // pass failed for exactly that reason, one branch over.
            ServerPlayer creative = helper.makeMockServerPlayerInLevel();
            creative.getAbilities().instabuild = true;
            creative.onUpdateAbilities();
            creative.setItemInHand(InteractionHand.MAIN_HAND,
                vacuum(RCItems.COPPER_GARBAGE_VACUUM, 0));
            helper.useBlock(DOCK, creative);
            helper.assertFalse(dock.isEmpty(), "setup: the creative click must dock something");
            helper.assertTrue(creative.getMainHandItem().is(RCItems.COPPER_GARBAGE_VACUUM.get()),
                "a creative player must keep the vacuum in hand - taking it is how a builder loses the "
                    + "item they were placing from");
            helper.succeed();
        });

        // THE SWAP IS THE DATA-LOSS PATH. Click a dock that already holds a vacuum while holding
        // another one and the docked one is replaced; if the stack it hands back is dropped on the
        // floor of the method, a player who parked a full 4,000 FE tool and then absent-mindedly set a
        // spare on top has destroyed it - no drop, no message, no way to tell it ever existed. Nothing
        // drove useItemOn at all before this, so the whole swap arm was untested code on a path that
        // deletes the most expensive item in the mod.
        //
        // The charge is asserted, not just the item: handing back a BLANK vacuum is the same loss with
        // a decoy.
        RCGameTests.test("swapping_the_dock_hands_the_charged_vacuum_back", 20, helper -> {
            helper.setBlock(DOCK, RCBlocks.CHARGING_STATION.get());
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(DOCK);
            if (!(level.getBlockEntity(abs) instanceof ChargingStationBlockEntity dock)) {
                helper.fail("the charging station has no BlockEntity");
                return;
            }
            ServerPlayer player = survivalPlayer(helper);

            player.setItemInHand(InteractionHand.MAIN_HAND,
                vacuum(RCItems.COPPER_GARBAGE_VACUUM, 3_000));
            helper.useBlock(DOCK, player);
            helper.assertTrue(GarbageVacuumItem.charge(dock.docked()) == 3_000,
                "setup: the charged vacuum must be parked first");

            player.setItemInHand(InteractionHand.MAIN_HAND,
                vacuum(RCItems.COPPER_GARBAGE_VACUUM, 0));
            helper.useBlock(DOCK, player);

            helper.assertTrue(GarbageVacuumItem.charge(dock.docked()) == 0,
                "the flat vacuum must be the one left on the dock, charge is "
                    + GarbageVacuumItem.charge(dock.docked()));
            List<ItemStack> returned = new ArrayList<>();
            for (ItemStack slot : player.getInventory().getNonEquipmentItems()) {
                if (!slot.isEmpty() && slot.is(RCItems.COPPER_GARBAGE_VACUUM.get())) {
                    returned.add(slot);
                }
            }
            helper.assertTrue(returned.size() == 1,
                "exactly one vacuum must come back off the dock, got " + returned.size()
                    + " - zero means the swap ate it, two means it duplicated");
            helper.assertTrue(GarbageVacuumItem.charge(returned.get(0)) == 3_000,
                "and it must come back with all 3,000 FE still in it, got "
                    + GarbageVacuumItem.charge(returned.get(0)));
            helper.succeed();
        });

        // TAKING ONE BACK WITH NO ROOM FOR IT. giveBack falls through to a drop when the inventory is
        // full, and that fallback is the only thing between a full-bagged player and a deleted vacuum:
        // Inventory.add returns false and leaves the stack alone, so a giveBack that ignored the return
        // value would undock the tool and then simply forget about it.
        //
        // Both halves in one body, because a test that only looks at the floor passes just as well when
        // the vacuum is ALWAYS thrown on the ground rather than put in the bag.
        RCGameTests.test("taking_a_vacuum_back_with_a_full_bag_drops_it", 20, helper -> {
            helper.setBlock(DOCK, RCBlocks.CHARGING_STATION.get());
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(DOCK);
            if (!(level.getBlockEntity(abs) instanceof ChargingStationBlockEntity dock)) {
                helper.fail("the charging station has no BlockEntity");
                return;
            }
            ServerPlayer player = survivalPlayer(helper);

            // With room: an empty-handed click puts it in the bag and NOT on the floor.
            dock.dock(vacuum(RCItems.COPPER_GARBAGE_VACUUM, 2_000));
            helper.useBlock(DOCK, player);
            helper.assertTrue(dock.isEmpty(), "an empty-handed click must take the vacuum off the dock");
            helper.assertTrue(player.getInventory().countItem(RCItems.COPPER_GARBAGE_VACUUM.get()) == 1,
                "and it must land in the inventory, not on the ground at the player's feet");
            helper.assertItemEntityNotPresent(RCItems.COPPER_GARBAGE_VACUUM.get());

            // Now with every slot packed. The hand holds stone, so useItemOn refuses it and the click
            // still reaches the empty-hand retrieval.
            List<ItemStack> slots = player.getInventory().getNonEquipmentItems();
            for (int i = 0; i < slots.size(); i++) {
                slots.set(i, new ItemStack(Items.STONE, 64));
            }
            dock.dock(vacuum(RCItems.COPPER_GARBAGE_VACUUM, 2_000));
            helper.useBlock(DOCK, player);
            helper.assertTrue(dock.isEmpty(),
                "the dock must let go of the vacuum even when there is nowhere to put it");
            helper.assertItemEntityPresent(RCItems.COPPER_GARBAGE_VACUUM.get());
            helper.succeed();
        });

        // Every tier answers the item energy capability over its component, at its own capacity. This
        // is the door the station uses and the one any other mod's charger would; a tier missing from
        // the registration loop would dock and never fill, with nothing logged.
        RCGameTests.test("every_vacuum_tier_exposes_the_item_energy_capability", 20, helper -> {
            for (DeferredItem<GarbageVacuumItem> tier : RCItems.GARBAGE_VACUUMS) {
                ItemStack stack = new ItemStack(tier.get());
                EnergyHandler handler = ItemAccess.forStack(stack).getCapability(Capabilities.Energy.ITEM);
                helper.assertTrue(handler != null, tier.getId() + " must expose Capabilities.Energy.ITEM");
                helper.assertTrue(handler.getCapacityAsInt() == tier.get().tier().capacity(),
                    tier.getId() + " capacity through the capability must be its tier's, got " + handler.getCapacityAsInt());
                try (Transaction transaction = Transaction.openRoot()) {
                    handler.insert(100, transaction);
                    transaction.commit();
                }
                helper.assertTrue(GarbageVacuumItem.charge(stack) == 100,
                    tier.getId() + ": 100 FE in through the capability must read as 100 on the component, got "
                        + GarbageVacuumItem.charge(stack));
            }
            helper.succeed();
        });

        // THE LADDER, derived from the registry rather than from a list of names. Every sortable must
        // sit in some band: the gate fails CLOSED, so an untagged pile is one no vacuum can ever take,
        // and the symptom is a tool that silently ignores it forever. This is the guard that turns
        // that into a build failure the day the pile is registered.
        RCGameTests.test("every_sortable_block_is_in_a_vacuum_band", 20, helper -> {
            List<Block> sortables = BuiltInRegistries.BLOCK.stream()
                .filter(block -> block instanceof SortableBlock)
                .toList();
            helper.assertTrue(sortables.size() >= 9,
                "only " + sortables.size() + " sortable blocks found - discovery is broken, so this "
                    + "test would pass by checking nothing");

            List<String> unbanded = new ArrayList<>();
            for (Block block : sortables) {
                boolean banded = false;
                for (VacuumTier tier : VacuumTier.LADDER) {
                    if (block.defaultBlockState().is(RCTags.vacuumable(tier.name()))) {
                        banded = true;
                    }
                }
                if (!banded) {
                    unbanded.add(String.valueOf(BuiltInRegistries.BLOCK.getKey(block)));
                }
            }
            helper.assertTrue(unbanded.isEmpty(),
                "these piles are in no #recompile:vacuumable/<tier> band, so no vacuum of any tier can "
                    + "ever take them: " + unbanded);
            helper.succeed();
        });

        // A BANDED PILE MUST COST SOMETHING. VacuumTier.costFor returns 0 for anything sortRolls does
        // not name, and a cost of 0 can never fail the `charge < cost` test - so such a pile is
        // vacuumed for free, forever, by a flat vacuum, and no amount of clearing will ever report
        // FLAT. That is exactly the "reads as complete, fails silently" shape the band sweep above was
        // written against, and the band sweep does not catch it: a tenth sortable added to a tag and
        // forgotten in sortRolls passes there and is free here.
        RCGameTests.test("every_vacuumable_pile_costs_charge", 20, helper -> {
            List<String> free = new ArrayList<>();
            int checked = 0;
            for (Block block : BuiltInRegistries.BLOCK.stream()
                    .filter(b -> b instanceof SortableBlock).toList()) {
                boolean banded = false;
                for (VacuumTier tier : VacuumTier.LADDER) {
                    banded |= block.defaultBlockState().is(RCTags.vacuumable(tier.name()));
                }
                if (!banded) {
                    continue;   // every_sortable_block_is_in_a_vacuum_band owns that failure
                }
                checked++;
                if (VacuumTier.costFor(SortableBlock.sortRolls(block.asItem())) <= 0) {
                    free.add(String.valueOf(BuiltInRegistries.BLOCK.getKey(block)));
                }
            }
            helper.assertTrue(checked >= 9,
                "only " + checked + " banded piles found - discovery is broken, so this would pass by "
                    + "checking nothing");
            helper.assertTrue(free.isEmpty(),
                "these are vacuumable but cost 0 FE, so they are free and can never run a vacuum flat "
                    + "(missing from SortableBlock.sortRolls): " + free);
            helper.succeed();
        });

        // The bands are CUMULATIVE, and that is the property the tag files express by including the
        // band below rather than by restating it. Asserted over the registry so a pile added to copper
        // is proven to reach netherite too - the failure this replaces is a tag file that lists its own
        // region and quietly forgets the include.
        RCGameTests.test("a_higher_tier_takes_everything_a_lower_one_does", 20, helper -> {
            List<String> gaps = new ArrayList<>();
            for (Block block : BuiltInRegistries.BLOCK.stream()
                    .filter(b -> b instanceof SortableBlock).toList()) {
                boolean below = false;
                for (VacuumTier tier : VacuumTier.LADDER) {
                    boolean here = block.defaultBlockState().is(RCTags.vacuumable(tier.name()));
                    if (below && !here) {
                        gaps.add(BuiltInRegistries.BLOCK.getKey(block) + " drops out at " + tier.name());
                    }
                    below |= here;
                }
            }
            helper.assertTrue(gaps.isEmpty(),
                "a band must include the one below it, so these are ladder gaps: " + gaps);
            helper.succeed();
        });

        // The bands as the owner set them (2026-09-03), one representative pile per region. Named
        // rather than derived on purpose: the sweeps above prove the SHAPE of the ladder and would be
        // just as happy with every pile in copper. This is the test that says which region is which,
        // and it is the one that has to be edited deliberately when that changes.
        RCGameTests.test("each_tier_is_rated_for_its_region", 20, helper -> {
            record Case(String region, Block pile, int fromTier) {}
            List<Case> cases = List.of(
                new Case("household sprawl", RCBlocks.GARBAGE_BLOCK.get(), 0),
                new Case("demolition yard", RCBlocks.MECHANICAL_WASTE.get(), 1),
                new Case("radioactive dump", RCBlocks.MILL_TAILINGS.get(), 2),
                new Case("compacted depths", RCBlocks.SLAG_RUBBLE.get(), 3));

            List<String> wrong = new ArrayList<>();
            for (Case c : cases) {
                for (int i = 0; i < VacuumTier.LADDER.size(); i++) {
                    VacuumTier tier = VacuumTier.LADDER.get(i);
                    GarbageVacuumItem vacuum = RCItems.GARBAGE_VACUUMS.get(i).get();
                    boolean takes = vacuum.canTake(c.pile().defaultBlockState());
                    boolean should = i >= c.fromTier();
                    if (takes != should) {
                        wrong.add(tier.name() + (should ? " must take " : " must refuse ")
                            + BuiltInRegistries.BLOCK.getKey(c.pile()) + " (" + c.region() + ")");
                    }
                }
            }
            helper.assertTrue(wrong.isEmpty(), "the tier bands do not match the regions: " + wrong);
            helper.succeed();
        });

        // A refusal is REPORTED, not silent. The copper vacuum facing tailings must come back
        // TOO_TOUGH rather than NOTHING_IN_RANGE, because those two are the same to a player unless
        // the tool says which - and it is the message that teaches the ladder exists at all.
        RCGameTests.test("an_underrated_vacuum_says_so_instead_of_doing_nothing", 20, helper -> {
            helper.setBlock(AIM, RCBlocks.MILL_TAILINGS.get());
            ServerLevel level = helper.getLevel();
            ServerPlayer player = survivalPlayer(helper);

            Intake copper = GarbageVacuumItem.intakeOnce(level, player,
                vacuum(RCItems.COPPER_GARBAGE_VACUUM, 4_000), centreOf(helper, AIM));
            helper.assertTrue(copper == Intake.TOO_TOUGH,
                "copper on mill tailings must report TOO_TOUGH, got " + copper);
            helper.assertBlockPresent(RCBlocks.MILL_TAILINGS.get(), AIM);

            Intake diamond = GarbageVacuumItem.intakeOnce(level, player,
                vacuum(RCItems.DIAMOND_GARBAGE_VACUUM, 16_000), centreOf(helper, AIM));
            helper.assertTrue(diamond == Intake.TOOK,
                "diamond is rated for the dump and must take them, got " + diamond);

            // And an empty volume is still NOTHING_IN_RANGE - the two answers must not collapse.
            helper.assertTrue(GarbageVacuumItem.intakeOnce(level, player,
                    vacuum(RCItems.COPPER_GARBAGE_VACUUM, 4_000), centreOf(helper, AIM))
                    == Intake.NOTHING_IN_RANGE,
                "with the pile gone the same vacuum must report NOTHING_IN_RANGE, not TOO_TOUGH");
            helper.succeed();
        });

        // Copy is a silent failure: a missing key renders as itself and only shows in a client the
        // tests never run.
        RCGameTests.test("garbage_vacuum_lang_keys_resolve", 20, helper -> {
            List<String> missing = new ArrayList<>();
            for (String key : List.of(
                    "message.recompile.vacuum_flat", "tooltip.recompile.vacuum_radius",
                    "message.recompile.vacuum_too_tough",
                    "tooltip.recompile.vacuum_band.copper", "tooltip.recompile.vacuum_band.iron",
                    "tooltip.recompile.vacuum_band.diamond", "tooltip.recompile.vacuum_band.netherite",
                    // Jade names the entity under the crosshair mid-flight; the dev client showed
                    // the raw key there before this line existed.
                    "entity.recompile.vacuumed_block",
                    "jade.recompile.charging", "jade.recompile.dock_empty",
                    "book.recompile.guide.power.garbage_vacuum.name",
                    "book.recompile.guide.power.garbage_vacuum.intro.title",
                    "book.recompile.guide.power.garbage_vacuum.intro.text")) {
                if (Component.translatable(key).getString().equals(key)) {
                    missing.add(key);
                }
            }
            helper.assertTrue(missing.isEmpty(),
                "these keys render as their own name, so they are missing from en_us.json: " + missing);
            helper.succeed();
        });

        // A CLICK WITH TOO LITTLE CHARGE SAYS SO AND STOPS. #358: the zero-charge guard catches only
        // charge EXACTLY zero, so a cell holding less than one block's cost got Intake.FLAT back from
        // intakeOnce and the result was thrown away - no block, no message, and the hold started
        // anyway. On a tap that is completely silent, because onUseTick never runs for one. Charging
        // strands a remainder like this routinely: 200 FE/tick into a cell spent 60 at a time.
        //
        // Paired with the same click one FE richer, because "it took nothing" passes just as well on a
        // vacuum that can never take anything.
        RCGameTests.test("a_click_too_poor_for_the_pile_says_so_and_does_not_hold", 40, helper -> {
            helper.setBlock(SIGHT, RCBlocks.GARBAGE_BLOCK.get());
            int cost = garbageCost();
            helper.assertTrue(cost > 1, "setup: a garbage block must cost more than one FE");

            ItemStack poor = vacuum(RCItems.COPPER_GARBAGE_VACUUM, cost - 1);
            ServerPlayer player = aimedPlayer(helper, poor);
            player.gameMode.useItemOn(player, helper.getLevel(), poor, InteractionHand.MAIN_HAND,
                new BlockHitResult(centreOf(helper, SIGHT), Direction.NORTH,
                    helper.absolutePos(SIGHT), false));

            helper.assertBlockPresent(RCBlocks.GARBAGE_BLOCK.get(), SIGHT);
            helper.assertTrue(GarbageVacuumItem.charge(poor) == cost - 1,
                "a refused click must not spend charge, got " + GarbageVacuumItem.charge(poor));
            helper.assertFalse(player.isUsingItem(),
                "the hold must not start when the click could not take the pile it was aimed at - a "
                    + "running vacuum that takes nothing is the silent failure the message exists to "
                    + "prevent");

            // ...and one FE more takes it, so the assertions above are about the SHORTFALL rather than
            // about the vacuum being broken.
            ItemStack enough = vacuum(RCItems.COPPER_GARBAGE_VACUUM, cost);
            ServerPlayer richer = aimedPlayer(helper, enough);
            richer.gameMode.useItemOn(richer, helper.getLevel(), enough, InteractionHand.MAIN_HAND,
                new BlockHitResult(centreOf(helper, SIGHT), Direction.NORTH,
                    helper.absolutePos(SIGHT), false));
            helper.assertBlockNotPresent(RCBlocks.GARBAGE_BLOCK.get(), SIGHT);
            helper.succeed();
        });
    }
}

package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.MattressBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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
            helper.assertBlockPresent(Blocks.AIR, head);
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

        // SETTING SPAWN IS NOT SLEEPING, and only one of them wears the mattress out. Without this,
        // the only way to put a respawn point on a Dirty Mattress was to destroy it - so wanting a
        // spawn and wanting to keep the mattress were mutually exclusive.
        RCGameTests.test("shift_clicking_a_mattress_sets_spawn_without_spending_it", 60, helper -> {
            BlockPos head = place(helper);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.setShiftKeyDown(true);

            helper.getLevel().getBlockState(helper.absolutePos(head))
                .useWithoutItem(helper.getLevel(), player, new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(head)),
                    Direction.UP, helper.absolutePos(head), false));

            var config = player.getRespawnConfig();
            helper.assertTrue(config != null,
                "shift-clicking a mattress must set a respawn point - it is the only way to get one "
                    + "here without spending the block");
            helper.assertTrue(config.respawnData().pos().equals(helper.absolutePos(head)),
                "the respawn point should be this mattress, got " + config.respawnData().pos());

            helper.assertFalse(player.isSleeping(), "shift-clicking must not put the player to sleep");
            helper.assertBlockPresent(RCBlocks.MATTRESS.get(), head);
            helper.assertBlockPresent(RCBlocks.MATTRESS.get(), FOOT);
            helper.succeed();
        });

        registerPlacement();
    }

    /**
     * Laying one down and picking one up in creative - the two halves of a two-block bed that no test
     * reached.
     *
     * <p>Everything above sets both halves with {@code setBlock}, which is exactly the shape that let
     * the fridge ship unplaceable: {@code getStateForPlacement} handed back a perfectly good state the
     * whole time while a real hand could not put the thing down. This is the only bed in the game, and
     * this repo's own note on it is that a custom bed's overrides fail SILENTLY.
     */
    private static void registerPlacement() {
        // Placement lays BOTH halves from one click. setPlacedBy is what writes the head; skip it and
        // the player gets a lone foot that deletes itself on the next block update, which reads exactly
        // like the game eating the item. Nothing about that shows up in a compile.
        RCGameTests.test("a_mattress_lays_both_halves_from_the_hand", 40, helper -> {
            BlockPos floor = new BlockPos(1, 1, 1);
            helper.setBlock(floor, Blocks.STONE);
            BlockPos abs = helper.absolutePos(floor);

            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setYRot(0.0F);   // yaw 0 is due SOUTH, and a bed's head goes where the player looks
            ItemStack stack = new ItemStack(RCItems.MATTRESS.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);

            BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(abs).add(0.0, 0.5, 0.0), Direction.UP, abs, false);
            stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));

            BlockPos foot = floor.above();
            BlockPos head = foot.south();
            helper.assertBlockPresent(RCBlocks.MATTRESS.get(), foot);
            helper.assertBlockPresent(RCBlocks.MATTRESS.get(), head);

            BlockState footState = helper.getBlockState(foot);
            BlockState headState = helper.getBlockState(head);
            helper.assertTrue(footState.getValue(MattressBlock.PART) == BedPart.FOOT,
                "the clicked cell must be the foot, is " + footState.getValue(MattressBlock.PART));
            helper.assertTrue(headState.getValue(MattressBlock.PART) == BedPart.HEAD,
                "the cell the player is facing must be the head, is "
                    + headState.getValue(MattressBlock.PART));
            // FACING has to agree across the two, because it is what neighbourDirection reads to decide
            // which cell is the partner - disagree and each half thinks the other is a stranger and both
            // vanish on the next update.
            helper.assertTrue(footState.getValue(MattressBlock.FACING) == Direction.SOUTH
                    && headState.getValue(MattressBlock.FACING) == Direction.SOUTH,
                "both halves must face the player, got " + footState.getValue(MattressBlock.FACING)
                    + " / " + headState.getValue(MattressBlock.FACING));
            helper.succeed();
        });

        // The opposite, so neither can pass vacuously: with the head's cell occupied the placement is
        // refused OUTRIGHT and the item stays in hand. getStateForPlacement returning a state anyway
        // would spend the mattress to put down a foot with no head - and the mattress is a find, so
        // there is no second one to try with.
        RCGameTests.test("a_mattress_refuses_to_lay_with_no_room_for_its_head", 40, helper -> {
            BlockPos floor = new BlockPos(1, 1, 1);
            helper.setBlock(floor, Blocks.STONE);
            helper.setBlock(floor.above().south(), Blocks.STONE);   // the head's cell, taken
            BlockPos abs = helper.absolutePos(floor);

            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setYRot(0.0F);
            ItemStack stack = new ItemStack(RCItems.MATTRESS.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);

            BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(abs).add(0.0, 0.5, 0.0), Direction.UP, abs, false);
            stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));

            helper.assertBlockPresent(Blocks.AIR, floor.above());
            helper.assertTrue(!stack.isEmpty(), "a refused placement must leave the mattress in hand");
            helper.succeed();
        });

        // CREATIVE-BREAKING THE FOOT DROPS NOTHING. This is #357, and it failed for a fortnight of
        // nothing, because the survival case paid exactly one and looked right. The mechanism: a
        // creative break runs playerWillDestroy, which removes the HEAD; that neighbour update makes
        // the foot's updateShape return AIR, and updateOrDestroy rolls the foot's loot on the way out.
        // With the table conditioned on `part=foot` the foot was the dropping half, so it paid out and
        // the player kept their item too. Conditioned on `part=head`, as vanilla's beds are, the half
        // that self-destructs is the silent one.
        //
        // Driven through gameMode.destroyBlock rather than Level.destroyBlock because that is the only
        // path that calls playerWillDestroy at all; Level.destroyBlock drops unconditionally and would
        // assert nothing about creative.
        RCGameTests.test("creative_breaking_a_mattress_foot_drops_nothing", 60, helper -> {
            BlockPos foot = new BlockPos(1, 1, 1);
            BlockPos head = foot.south();
            layHalves(helper, foot, head);

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            // SET THE ABILITY, NOT THE GAME MODE. Player.preventsBlockDrops reads abilities.instabuild
            // and nothing else, and setGameMode re-derives abilities through updatePlayerAbilities,
            // which does not leave it set on a player with no registered connection.
            player.getAbilities().instabuild = true;
            player.onUpdateAbilities();
            player.gameMode.destroyBlock(helper.absolutePos(foot));

            helper.assertBlockPresent(Blocks.AIR, foot);
            helper.assertBlockPresent(Blocks.AIR, head);
            helper.succeedWhen(() ->
                helper.assertItemEntityCountIs(RCItems.MATTRESS.get(), foot, 3.0, 0));
        });

        // ...and its opposite, which is the half that makes the creative test mean something: a survival
        // break of the same cell hands back EXACTLY one. Two rolls fire on a break here - the half the
        // player broke and the orphan updateShape destroys - so anything other than one means the gate
        // between them moved. BulkyWasteTests proves this from the head; the foot is the other code path,
        // because playerWillDestroy only ever looks at the foot.
        RCGameTests.test("survival_breaking_a_mattress_foot_yields_exactly_one", 60, helper -> {
            BlockPos foot = new BlockPos(1, 1, 1);
            BlockPos head = foot.south();
            layHalves(helper, foot, head);

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.gameMode.destroyBlock(helper.absolutePos(foot));

            helper.assertBlockPresent(Blocks.AIR, foot);
            helper.assertBlockPresent(Blocks.AIR, head);
            helper.succeedWhen(() ->
                helper.assertItemEntityCountIs(RCItems.MATTRESS.get(), foot, 3.0, 1));
        });
    }

    /** Set both halves of a south-facing mattress directly, without going through placement. */
    private static void layHalves(net.minecraft.gametest.framework.GameTestHelper helper,
            BlockPos foot, BlockPos head) {
        helper.setBlock(foot, RCBlocks.MATTRESS.get().defaultBlockState()
            .setValue(MattressBlock.FACING, Direction.SOUTH)
            .setValue(MattressBlock.PART, BedPart.FOOT));
        helper.setBlock(head, RCBlocks.MATTRESS.get().defaultBlockState()
            .setValue(MattressBlock.FACING, Direction.SOUTH)
            .setValue(MattressBlock.PART, BedPart.HEAD));
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

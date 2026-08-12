package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.MattressBlock;
import com.flatts.recompile.content.block.FoundApplianceBlock;
import com.flatts.recompile.content.block.TallApplianceBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/** GameTests for Bulky Waste (design P1.11) and its first find, the mattress. */
final class BulkyWasteTests {

    private BulkyWasteTests() {
    }

    static void register() {
        // Bulky Waste's whole job is to give up a find when broken. The loot table is the
        // file that grows as finds are added, so this guards the wiring rather than the
        // contents: break it, something falls out.
        RCGameTests.test("bulky_waste_drops_a_find", 40, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.BULKY_WASTE.get());
            // NOT helper.destroyBlock: that passes dropBlock=false, so no loot table runs
            // and this would assert nothing. Drop for real.
            helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
            helper.assertBlockPresent(Blocks.AIR, pos);
            helper.succeedWhenEntityPresent(EntityType.ITEM, pos);
        });

        // Right-click with a prybar pops it open (mirrors the compacted bale's tool gate).
        // Fails against the pre-interaction block, where a right-click did nothing and the
        // block would still be standing.
        RCGameTests.test("bulky_waste_opens_with_a_prybar", 40, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.BULKY_WASTE.get());
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            Vec3 standing = helper.absoluteVec(pos.above().getCenter());
            player.snapTo(standing.x, standing.y, standing.z);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(RCItems.PRYBAR.get()));

            helper.useBlock(pos, player);
            helper.assertBlockPresent(Blocks.AIR, pos);
            helper.succeedWhenEntityPresent(EntityType.ITEM, pos);
        });

        // Bare hand must NOT open it - the prybar is the only way in, so the block stays
        // and the player is nudged instead (the message itself is server-only chat, so this
        // asserts the block survives rather than trying to read the text).
        RCGameTests.test("bulky_waste_needs_a_prybar_to_open", 20, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.BULKY_WASTE.get());
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            Vec3 standing = helper.absoluteVec(pos.above().getCenter());
            player.snapTo(standing.x, standing.y, standing.z);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            helper.useBlock(pos, player);
            helper.assertBlockPresent(RCBlocks.BULKY_WASTE.get(), pos);
            helper.succeed();
        });

        // The two overrides that fail SILENTLY. Without isBed, NeoForge's patched
        // LivingEntity.checkBedExists() ejects the sleeper on the next tick; without
        // getRespawnPosition, its default Optional.empty() is byte-for-byte vanilla's
        // "no respawn block available". Neither shows up in a compile, and neither would
        // be caught by placing the block and looking at it.
        RCGameTests.test("mattress_is_a_bed", 20, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.MATTRESS.get());
            BlockPos abs = helper.absolutePos(pos);
            BlockState state = helper.getBlockState(pos);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);

            helper.assertTrue(state.isBed(helper.getLevel(), abs, (LivingEntity) player),
                "the mattress must report isBed - without it the sleeper is ejected next tick");
            helper.assertTrue(
                state.getRespawnPosition(EntityType.PLAYER, helper.getLevel(), abs, 0.0F).isPresent(),
                "the mattress must supply a respawn position - the default is empty, which "
                    + "is exactly 'no respawn block available'");
            helper.succeed();
        });

        // A vanilla bed is two blocks and orphaned halves must vanish - that is updateShape's
        // job, not a removal hook, which is easy to get wrong.
        RCGameTests.test("mattress_places_and_breaks_as_two_halves", 20, helper -> {
            BlockPos foot = new BlockPos(1, 1, 1);
            BlockPos head = foot.north();

            helper.setBlock(foot, RCBlocks.MATTRESS.get().defaultBlockState()
                .setValue(MattressBlock.FACING, net.minecraft.core.Direction.NORTH)
                .setValue(MattressBlock.PART, BedPart.FOOT));
            helper.setBlock(head, RCBlocks.MATTRESS.get().defaultBlockState()
                .setValue(MattressBlock.FACING, net.minecraft.core.Direction.NORTH)
                .setValue(MattressBlock.PART, BedPart.HEAD));

            helper.assertBlockPresent(RCBlocks.MATTRESS.get(), foot);
            helper.assertBlockPresent(RCBlocks.MATTRESS.get(), head);

            // Removing one half must take the other with it.
            helper.setBlock(foot, Blocks.AIR);
            helper.assertBlockPresent(Blocks.AIR, head);
            helper.succeed();
        });

        // Breaking a mattress in survival must return EXACTLY ONE item. The loot gates on
        // part=foot, and breaking either half runs the loot table twice - once for the half
        // the player broke, once for the orphan that updateShape destroys - so the gate is
        // the only thing filtering two rolls down to one. A flipped gate or a stray
        // suppress-drops flag would silently yield zero or two, and neither shows in a
        // compile. setBlock(AIR) runs no loot at all (see mattress_places_and_breaks), so
        // this destroys the HEAD for real: the drop then comes from the orphaned FOOT.
        RCGameTests.test("mattress_broken_drops_exactly_one", 40, helper -> {
            BlockPos foot = new BlockPos(1, 1, 1);
            BlockPos head = foot.north();
            helper.setBlock(foot, RCBlocks.MATTRESS.get().defaultBlockState()
                .setValue(MattressBlock.FACING, net.minecraft.core.Direction.NORTH)
                .setValue(MattressBlock.PART, BedPart.FOOT));
            helper.setBlock(head, RCBlocks.MATTRESS.get().defaultBlockState()
                .setValue(MattressBlock.FACING, net.minecraft.core.Direction.NORTH)
                .setValue(MattressBlock.PART, BedPart.HEAD));

            helper.getLevel().destroyBlock(helper.absolutePos(head), true);
            helper.assertBlockPresent(Blocks.AIR, foot);
            helper.assertBlockPresent(Blocks.AIR, head);
            helper.succeedWhen(() ->
                helper.assertItemEntityCountIs(RCItems.MATTRESS.get(), foot, 3.0, 1));
        });
        // The washing machine must hand BACK a washing machine. This looks trivial and is not:
        // the water tank shipped with a loot table that dropped a rain collector, and the test
        // covering it asserted the wrong item and passed. A find whose loot table names the wrong
        // thing is a silent duplication bug, so the drop is asserted by identity.
        RCGameTests.test("washing_machine_drops_itself", 40, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.WASHING_MACHINE.get());

            helper.getLevel().destroyBlock(helper.absolutePos(pos), true);

            helper.assertBlockPresent(Blocks.AIR, pos);
            helper.succeedWhen(() ->
                helper.assertItemEntityCountIs(RCItems.WASHING_MACHINE.get(), pos, 3.0, 1));
        });

        // The door faces the player who placed it. Without this the block still places fine and
        // still works - it just shows a blank enamel side three times out of four, which stops it
        // reading as a washing machine at all and quietly wastes the one bespoke face it has.
        // Tests getStateForPlacement directly rather than simulating a click, so a broken override
        // cannot hide behind placement plumbing.
        RCGameTests.test("washing_machine_faces_the_player", 20, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            BlockPos abs = helper.absolutePos(pos);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setYRot(0.0F);       // yaw 0 is due SOUTH, so the door must come out NORTH
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(RCItems.WASHING_MACHINE.get()));

            BlockHitResult hit = new BlockHitResult(
                abs.getCenter(), net.minecraft.core.Direction.UP, abs, false);
            BlockState placed = RCBlocks.WASHING_MACHINE.get().getStateForPlacement(
                new BlockPlaceContext(new UseOnContext(
                    player, net.minecraft.world.InteractionHand.MAIN_HAND, hit)));

            helper.assertTrue(placed != null, "placement must produce a state");
            helper.assertTrue(
                placed.getValue(FoundApplianceBlock.FACING) == net.minecraft.core.Direction.NORTH,
                "the door must face the player, got " + placed.getValue(FoundApplianceBlock.FACING));
            helper.succeed();
        });

        // PLACEMENT GOES THROUGH THE ITEM, and that is the whole point of this test.
        //
        // The washing machine's facing test calls getStateForPlacement directly, deliberately, so a
        // broken override cannot hide behind placement plumbing. A two-tall block needs the exact
        // opposite: its first bug lived IN the plumbing. TallApplianceBlock.canSurvive demanded the
        // partner half already exist, BlockItem.canPlace consults canSurvive BEFORE setPlacedBy has
        // built the partner, so every placement was refused and the fridge could not be put down at
        // all. getStateForPlacement returned a perfectly good state the whole time - so a test at
        // that level passes while the block is unplaceable in a real hand.
        //
        // Reported from a playtest, one message after the art landed, which is the tell: 419 tests
        // passed and not one of them held the item.
        RCGameTests.test("a_fridge_places_both_halves_from_the_hand", 40, helper -> {
            BlockPos floor = new BlockPos(1, 1, 1);
            helper.setBlock(floor, Blocks.STONE);
            BlockPos abs = helper.absolutePos(floor);

            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setYRot(0.0F);   // yaw 0 is due SOUTH, so the door must come out NORTH
            ItemStack stack = new ItemStack(RCItems.FRIDGE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);

            BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(abs).add(0.0, 0.5, 0.0), Direction.UP, abs, false);
            stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));

            BlockPos lower = floor.above();
            BlockPos upper = floor.above(2);
            helper.assertBlockPresent(RCBlocks.FRIDGE.get(), lower);
            helper.assertBlockPresent(RCBlocks.FRIDGE.get(), upper);

            BlockState lowerState = helper.getBlockState(lower);
            BlockState upperState = helper.getBlockState(upper);
            helper.assertTrue(lowerState.getValue(TallApplianceBlock.HALF) == DoubleBlockHalf.LOWER,
                "the clicked cell must be the lower half");
            helper.assertTrue(upperState.getValue(TallApplianceBlock.HALF) == DoubleBlockHalf.UPPER,
                "the cell above must be the upper half");
            helper.assertTrue(
                lowerState.getValue(TallApplianceBlock.FACING) == Direction.NORTH
                    && upperState.getValue(TallApplianceBlock.FACING) == Direction.NORTH,
                "both halves must face the player, got "
                    + lowerState.getValue(TallApplianceBlock.FACING) + " / "
                    + upperState.getValue(TallApplianceBlock.FACING));
            helper.succeed();
        });

        // The opposite half, so neither can pass vacuously: with no headroom the placement is
        // refused OUTRIGHT rather than leaving a lone lower half that deletes itself on the next
        // block update - which looks to a player exactly like the game eating the item.
        RCGameTests.test("a_fridge_refuses_to_place_with_no_headroom", 40, helper -> {
            BlockPos floor = new BlockPos(1, 1, 1);
            helper.setBlock(floor, Blocks.STONE);
            helper.setBlock(floor.above(2), Blocks.STONE);   // ceiling: no room for the head
            BlockPos abs = helper.absolutePos(floor);

            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            ItemStack stack = new ItemStack(RCItems.FRIDGE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);

            BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(abs).add(0.0, 0.5, 0.0), Direction.UP, abs, false);
            stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));

            helper.assertBlockPresent(Blocks.AIR, floor.above());
            helper.assertTrue(!stack.isEmpty(), "a refused placement must leave the item in hand");
            helper.succeed();
        });

        // Breaking either half takes the other with it, and hands back exactly ONE fridge. Both
        // directions are separate code paths - the lower is where the loot table is gated and the
        // upper is where the orphan check runs - so one test cannot stand in for the other.
        RCGameTests.test("breaking_either_fridge_half_yields_one_fridge", 60, helper -> {
            BlockPos lower = new BlockPos(1, 1, 1);
            BlockState base = RCBlocks.FRIDGE.get().defaultBlockState();
            helper.setBlock(lower, base.setValue(TallApplianceBlock.HALF, DoubleBlockHalf.LOWER));
            helper.setBlock(lower.above(),
                base.setValue(TallApplianceBlock.HALF, DoubleBlockHalf.UPPER));

            // Break the UPPER: the half with no loot entry, so the drop has to come from the lower
            // being removed as an orphan - and must not double up with it.
            helper.getLevel().destroyBlock(helper.absolutePos(lower.above()), true);

            helper.assertBlockPresent(Blocks.AIR, lower.above());
            helper.assertBlockPresent(Blocks.AIR, lower);
            helper.succeedWhen(() ->
                helper.assertItemEntityCountIs(RCItems.FRIDGE.get(), lower, 3.0, 1));
        });

        // The mattress -> string exit moved to the Recompile Workbench (P1.4); its teardown
        // is covered by RecompileWorkbenchTests. The in-hand knife-cut was retired here.
    }
}

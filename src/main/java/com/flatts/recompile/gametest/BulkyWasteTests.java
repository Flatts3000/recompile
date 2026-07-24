package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.MattressBlock;
import com.flatts.recompile.content.block.WashingMachineBlock;
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
                placed.getValue(WashingMachineBlock.FACING) == net.minecraft.core.Direction.NORTH,
                "the door must face the player, got " + placed.getValue(WashingMachineBlock.FACING));
            helper.succeed();
        });

        // The mattress -> string exit moved to the Recompile Workbench (P1.4); its teardown
        // is covered by RecompileWorkbenchTests. The in-hand knife-cut was retired here.
    }
}

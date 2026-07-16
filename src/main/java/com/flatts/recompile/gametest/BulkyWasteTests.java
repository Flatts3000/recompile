package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.MattressBlock;
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

        // The mattress is your bed OR your rope. The knife is the only exit, reusing the
        // tin can's verb - and without a knife it must refuse rather than silently eat it.
        RCGameTests.test("mattress_cut_open_needs_a_knife", 20, helper -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(RCItems.MATTRESS.get()));

            var noKnife = RCItems.MATTRESS.get()
                .use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(noKnife == net.minecraft.world.InteractionResult.PASS,
                "without a knife the mattress must not cut open, got " + noKnife);

            player.getInventory().add(new ItemStack(RCItems.SCRAP_KNIFE.get()));
            RCItems.MATTRESS.get()
                .use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);

            boolean hasString = false;
            var inv = player.getInventory();
            for (int slot = 0; slot < inv.getContainerSize(); slot++) {
                if (inv.getItem(slot).is(net.minecraft.world.item.Items.STRING)) {
                    hasString = true;
                }
            }
            helper.assertTrue(hasString,
                "cutting a mattress with the knife must yield string - the only early source");
            helper.succeed();
        });
    }
}

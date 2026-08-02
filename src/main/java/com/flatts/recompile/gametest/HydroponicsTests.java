package com.flatts.recompile.gametest;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.content.block.HydroponicsBayBlock;
import com.flatts.recompile.content.block.entity.HydroponicsBayBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Hydroponics Bay (#43).
 *
 * <p>The claim worth defending is the <b>seedling swap</b>: an Unknown Seedling is a lottery ticket, and
 * what it yields then seeds itself forever. That is one mechanic doing the work of two, so if it breaks
 * apart the machine becomes either a slot machine you can never escape or a plant duplicator you can
 * never enter.
 *
 * <p>The other claim is scarcity: sugar cane, bamboo, cactus and sweet berries exist nowhere else in the
 * game, so a bay that quietly stops producing them removes a quarter of vanilla's plant life with
 * nothing to notice it.
 */
final class HydroponicsTests {

    private static final BlockPos BAY = new BlockPos(1, 1, 1);

    private HydroponicsTests() {
    }

    /** A bay with a full tank and a charged battery, ready to run. */
    private static HydroponicsBayBlockEntity placeFuelled(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, RCBlocks.HYDROPONICS_BAY.get());
        var be = (HydroponicsBayBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        try (Transaction tx = Transaction.openRoot()) {
            be.tank().insert(FluidResource.of(Fluids.WATER),
                RCConfig.HYDROPONICS_TANK_CAPACITY.get(), tx);
            be.battery().insert(Integer.MAX_VALUE, tx);
            tx.commit();
        }
        return be;
    }

    /** Tick the bay until it produces, or give up. Returns what landed in the output slot. */
    private static ItemStack runBatches(GameTestHelper helper, HydroponicsBayBlockEntity be, int batches) {
        int limit = RCConfig.HYDROPONICS_GROW_TICKS.get() * batches + 10;
        for (int i = 0; i < limit; i++) {
            HydroponicsBayBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(BAY),
                helper.getBlockState(BAY), be);
        }
        return be.getItem(HydroponicsBayBlockEntity.SLOT_OUTPUT);
    }

    static void register() {
        // THE SWAP, half one: a seedling is a lottery ticket and yields SOMETHING growable.
        RCGameTests.test("a_seedling_grows_into_some_plant", 60, helper -> {
            var be = placeFuelled(helper, BAY);
            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT,
                new ItemStack(RCItems.UNKNOWN_SEEDLING.get(), 4));

            ItemStack out = runBatches(helper, be, 1);
            helper.assertFalse(out.isEmpty(), "a seedling batch must produce a plant");
            helper.assertTrue(HydroponicsBayBlockEntity.isGrowable(out),
                "what a seedling yields must itself be growable, or the swap dead-ends and the player "
                    + "is stuck rolling the lottery forever - got " + out);
            helper.succeed();
        });

        // THE SWAP, half two: that plant then seeds itself, and at a PROFIT. A machine that returns
        // exactly what you feed it is an expensive way to stand still.
        RCGameTests.test("a_plant_seeds_itself_at_a_profit", 60, helper -> {
            var be = placeFuelled(helper, BAY);
            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT, new ItemStack(Items.SUGAR_CANE, 8));

            ItemStack out = runBatches(helper, be, 1);
            helper.assertTrue(out.is(Items.SUGAR_CANE),
                "a bay seeded with cane must produce cane, got " + out);
            helper.assertTrue(out.getCount() == RCConfig.HYDROPONICS_YIELD.get(),
                "one batch should yield " + RCConfig.HYDROPONICS_YIELD.get() + ", got " + out.getCount());
            helper.assertTrue(RCConfig.HYDROPONICS_YIELD.get() > 1,
                "the yield must exceed 1 or the machine consumes as much as it makes and is pointless");
            helper.assertTrue(be.getItem(HydroponicsBayBlockEntity.SLOT_INPUT).getCount() == 7,
                "a batch must consume exactly one input to seed itself");
            helper.succeed();
        });

        // The four that exist nowhere else. If the tag ever loses one, a quarter of vanilla's plant life
        // silently leaves the game and nothing else in the build would notice.
        RCGameTests.test("the_whole_plant_farmables_are_growable", 20, helper -> {
            for (var item : new net.minecraft.world.item.Item[] {
                    Items.SUGAR_CANE, Items.BAMBOO, Items.CACTUS, Items.SWEET_BERRIES}) {
                helper.assertTrue(HydroponicsBayBlockEntity.isGrowable(new ItemStack(item)),
                    item + " must be growable - the bay is its ONLY source in the entire game");
            }
            helper.succeed();
        });

        // Without power it does nothing. This is the first machine in the mod that spends FE, so "the
        // energy is actually wired to the outcome" is worth asserting rather than assuming.
        RCGameTests.test("no_power_means_no_growth", 60, helper -> {
            helper.setBlock(BAY, RCBlocks.HYDROPONICS_BAY.get());
            var be = (HydroponicsBayBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(BAY));
            try (Transaction tx = Transaction.openRoot()) {
                be.tank().insert(FluidResource.of(Fluids.WATER),
                    RCConfig.HYDROPONICS_TANK_CAPACITY.get(), tx);
                tx.commit();   // water, deliberately no charge
            }
            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT, new ItemStack(Items.SUGAR_CANE, 8));

            helper.assertTrue(runBatches(helper, be, 1).isEmpty(),
                "with an empty battery the bay must produce nothing");
            helper.assertTrue(be.getItem(HydroponicsBayBlockEntity.SLOT_INPUT).getCount() == 8,
                "and it must not eat its input while stalled");
            helper.succeed();
        });

        // Same for water. Two separate resources, two separate ways to stall, and a machine that runs
        // dry on one of them would be a silent half-feature.
        RCGameTests.test("no_water_means_no_growth", 60, helper -> {
            helper.setBlock(BAY, RCBlocks.HYDROPONICS_BAY.get());
            var be = (HydroponicsBayBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(BAY));
            try (Transaction tx = Transaction.openRoot()) {
                be.battery().insert(Integer.MAX_VALUE, tx);
                tx.commit();   // charge, deliberately no water
            }
            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT, new ItemStack(Items.SUGAR_CANE, 8));

            helper.assertTrue(runBatches(helper, be, 1).isEmpty(),
                "with an empty tank the bay must produce nothing");
            helper.succeed();
        });

        // The grow-light. Added because the LIT property, the lit blockstate variant and the pink
        // texture all existed while NOTHING ever set the property - so the light was unreachable and
        // every texture-and-model check still passed. A lit variant that never lights is invisible to
        // any test that only asks whether a texture exists.
        RCGameTests.test("the_bay_lights_up_while_it_is_working", 60, helper -> {
            var be = placeFuelled(helper, BAY);
            helper.assertFalse(helper.getBlockState(BAY).getValue(HydroponicsBayBlock.LIT),
                "an idle bay must not be lit");

            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT, new ItemStack(Items.SUGAR_CANE, 8));
            HydroponicsBayBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(BAY),
                helper.getBlockState(BAY), be);
            helper.assertTrue(helper.getBlockState(BAY).getValue(HydroponicsBayBlock.LIT),
                "a working bay must light up, or the lit texture and the light level are unreachable");

            // And it goes out again. A machine stuck permanently lit is the same bug wearing a hat.
            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT, ItemStack.EMPTY);
            HydroponicsBayBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(BAY),
                helper.getBlockState(BAY), be);
            helper.assertFalse(helper.getBlockState(BAY).getValue(HydroponicsBayBlock.LIT),
                "an emptied bay must go dark again");
            helper.succeed();
        });

        // Water in by hand. Without this the tank is reachable only by a pipe from a Rain Collector, so
        // a player holding a bucket gets a screen with an empty gauge and no way to act on it - the
        // machine looks broken at the exact moment they are trying to start it.
        RCGameTests.test("a_water_bucket_fills_the_tank", 40, helper -> {
            helper.setBlock(BAY, RCBlocks.HYDROPONICS_BAY.get());
            var be = (HydroponicsBayBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(BAY));
            helper.assertTrue(be.tank().getAmountAsInt(0) == 0, "a fresh bay starts dry");

            var player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(Items.WATER_BUCKET));
            helper.useBlock(BAY, player);

            helper.assertTrue(be.tank().getAmountAsInt(0) > 0,
                "right-clicking a water bucket on the bay must fill its tank");
            helper.assertTrue(player.getMainHandItem().is(Items.BUCKET),
                "and hand back the empty bucket, not keep the full one - a bucket that pours and stays "
                    + "full is infinite water, which #101 exists to prevent");
            helper.succeed();
        });

        // A BLOCKED OUTPUT MUST NOT TRANSMUTE THE CROP. The first version checked only that the output
        // stack had room, never that the item matched, so a bay growing cane over a slot of potatoes ran
        // to completion and merged the yield into the potatoes: cane in, potatoes out, cane destroyed.
        // A count-only guard looks like a guard, which is why this went unnoticed.
        RCGameTests.test("a_full_output_stops_the_batch_it_does_not_convert_it", 60, helper -> {
            var be = placeFuelled(helper, BAY);
            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT, new ItemStack(Items.SUGAR_CANE, 8));
            be.setItem(HydroponicsBayBlockEntity.SLOT_OUTPUT, new ItemStack(Items.POTATO, 5));

            runBatches(helper, be, 2);
            ItemStack out = be.getItem(HydroponicsBayBlockEntity.SLOT_OUTPUT);
            helper.assertTrue(out.is(Items.POTATO) && out.getCount() == 5,
                "an unrelated item in the output must be left exactly alone, got " + out);
            helper.assertTrue(be.getItem(HydroponicsBayBlockEntity.SLOT_INPUT).getCount() == 8,
                "and the crop must not be consumed into it - that is a duplication bug, not a stall");
            helper.assertFalse(helper.getBlockState(BAY).getValue(HydroponicsBayBlock.LIT),
                "a blocked bay must go dark, so the reason it stopped is visible from outside");
            helper.succeed();
        });

        // Same slot, matching item, no room. The count check still has to hold on its own.
        RCGameTests.test("a_maxed_output_stack_stops_the_batch", 60, helper -> {
            var be = placeFuelled(helper, BAY);
            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT, new ItemStack(Items.SUGAR_CANE, 8));
            be.setItem(HydroponicsBayBlockEntity.SLOT_OUTPUT, new ItemStack(Items.SUGAR_CANE, 64));

            runBatches(helper, be, 2);
            helper.assertTrue(be.getItem(HydroponicsBayBlockEntity.SLOT_OUTPUT).getCount() == 64,
                "a full stack cannot grow past its own maximum");
            helper.assertTrue(be.getItem(HydroponicsBayBlockEntity.SLOT_INPUT).getCount() == 8,
                "and the input must not be eaten while the output has nowhere to put it");
            helper.succeed();
        });

        // A seedling's result is not known until the batch ends, so it cannot be compared against
        // whatever is already sitting there. The bay waits for a clear slot rather than gambling.
        RCGameTests.test("a_seedling_batch_waits_for_a_clear_output", 60, helper -> {
            var be = placeFuelled(helper, BAY);
            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT,
                new ItemStack(RCItems.UNKNOWN_SEEDLING.get(), 4));
            be.setItem(HydroponicsBayBlockEntity.SLOT_OUTPUT, new ItemStack(Items.POTATO, 5));

            runBatches(helper, be, 2);
            ItemStack out = be.getItem(HydroponicsBayBlockEntity.SLOT_OUTPUT);
            helper.assertTrue(out.is(Items.POTATO) && out.getCount() == 5,
                "a lottery batch must not roll into an occupied slot, got " + out);
            helper.assertTrue(be.getItem(HydroponicsBayBlockEntity.SLOT_INPUT).getCount() == 4,
                "and it must not spend a seedling doing so - seedlings are not renewable");
            helper.succeed();
        });

        // Junk in, nothing out - and specifically it must not be ACCEPTED, or a hopper feeding a chest
        // of assorted salvage would jam the input slot and brick the machine. That exact failure is why
        // the Cupola gained an insert guard.
        RCGameTests.test("the_bay_refuses_what_it_cannot_grow", 20, helper -> {
            helper.assertFalse(HydroponicsBayBlockEntity.isGrowable(new ItemStack(Items.IRON_INGOT)),
                "an iron ingot is not a plant");
            helper.assertFalse(HydroponicsBayBlockEntity.isGrowable(new ItemStack(RCItems.JUNK.get())),
                "junk is not a plant - and a pipe pushing it in would brick the input slot");
            helper.succeed();
        });
    }
}

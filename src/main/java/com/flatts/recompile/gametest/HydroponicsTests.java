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
                new ItemStack(RCItems.UNKNOWN_SEEDLING.get()));

            ItemStack out = runBatches(helper, be, 1);
            helper.assertFalse(out.isEmpty(), "a seedling batch must produce a plant");
            helper.assertTrue(HydroponicsBayBlockEntity.isGrowable(out),
                "what a seedling yields must itself be growable, or the swap dead-ends and the player "
                    + "is stuck rolling the lottery forever - got " + out);
            helper.succeed();
        });

        // THE SWAP, half two: that plant is then the crop, and the crop is NEVER consumed. The bay is
        // the only source of four vanilla plants, so a machine that can eat its own last cactus is one
        // bad hopper away from taking a plant out of a save permanently.
        RCGameTests.test("a_seeded_plant_grows_forever_and_is_never_consumed", 90, helper -> {
            var be = placeFuelled(helper, BAY);
            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT, new ItemStack(Items.SUGAR_CANE));

            // Recharged between batches, because the battery is sized at exactly one batch - a bay is
            // meant to be running off a generator, and without the top-up this test would prove only
            // that the FIRST harvest leaves the crop alone.
            for (int batch = 0; batch < 3; batch++) {
                try (Transaction tx = Transaction.openRoot()) {
                    be.battery().insert(Integer.MAX_VALUE, tx);
                    tx.commit();
                }
                runBatches(helper, be, 1);
            }
            ItemStack out = be.getItem(HydroponicsBayBlockEntity.SLOT_OUTPUT);
            helper.assertTrue(out.is(Items.SUGAR_CANE),
                "a bay seeded with cane must produce cane, got " + out);
            helper.assertTrue(out.getCount() == RCConfig.HYDROPONICS_YIELD.get() * 3,
                "three batches should yield " + (RCConfig.HYDROPONICS_YIELD.get() * 3)
                    + ", got " + out.getCount());
            ItemStack crop = be.getItem(HydroponicsBayBlockEntity.SLOT_INPUT);
            helper.assertTrue(crop.is(Items.SUGAR_CANE) && crop.getCount() == 1,
                "the crop must still be sitting there untouched after every batch, got " + crop);
            helper.succeed();
        });

        // One crop, not a queue. A second copy in the slot would sit there doing nothing while looking
        // like it was lined up to be used, and a hopper would happily stack sixty-four of them.
        RCGameTests.test("the_crop_slot_takes_one_and_refuses_a_second", 20, helper -> {
            helper.setBlock(BAY, RCBlocks.HYDROPONICS_BAY.get());
            var be = (HydroponicsBayBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(BAY));
            helper.assertTrue(
                be.canPlaceItem(HydroponicsBayBlockEntity.SLOT_INPUT, new ItemStack(Items.SUGAR_CANE)),
                "an empty bay must accept a crop");

            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT, new ItemStack(Items.SUGAR_CANE));
            helper.assertFalse(
                be.canPlaceItem(HydroponicsBayBlockEntity.SLOT_INPUT, new ItemStack(Items.SUGAR_CANE)),
                "an occupied crop slot must refuse a second, from a player or a pipe alike");
            helper.assertFalse(
                be.canPlaceItemThroughFace(HydroponicsBayBlockEntity.SLOT_INPUT,
                    new ItemStack(Items.SUGAR_CANE), net.minecraft.core.Direction.UP),
                "and the automation face must agree, or a hopper routes round the rule");
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
            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT, new ItemStack(Items.SUGAR_CANE));

            helper.assertTrue(runBatches(helper, be, 1).isEmpty(),
                "with an empty battery the bay must produce nothing");
            helper.assertTrue(be.getItem(HydroponicsBayBlockEntity.SLOT_INPUT).is(Items.SUGAR_CANE),
                "and the crop must still be there when it stalls");
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
            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT, new ItemStack(Items.SUGAR_CANE));

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

            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT, new ItemStack(Items.SUGAR_CANE));
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
            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT, new ItemStack(Items.SUGAR_CANE));
            be.setItem(HydroponicsBayBlockEntity.SLOT_OUTPUT, new ItemStack(Items.POTATO, 5));

            runBatches(helper, be, 2);
            ItemStack out = be.getItem(HydroponicsBayBlockEntity.SLOT_OUTPUT);
            helper.assertTrue(out.is(Items.POTATO) && out.getCount() == 5,
                "an unrelated item in the output must be left exactly alone, got " + out);
            helper.assertTrue(be.getItem(HydroponicsBayBlockEntity.SLOT_INPUT).is(Items.SUGAR_CANE),
                "and the crop must not be consumed into it - that is a duplication bug, not a stall");
            helper.assertFalse(helper.getBlockState(BAY).getValue(HydroponicsBayBlock.LIT),
                "a blocked bay must go dark, so the reason it stopped is visible from outside");
            helper.succeed();
        });

        // Same slot, matching item, no room. The count check still has to hold on its own.
        RCGameTests.test("a_maxed_output_stack_stops_the_batch", 60, helper -> {
            var be = placeFuelled(helper, BAY);
            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT, new ItemStack(Items.SUGAR_CANE));
            be.setItem(HydroponicsBayBlockEntity.SLOT_OUTPUT, new ItemStack(Items.SUGAR_CANE, 64));

            runBatches(helper, be, 2);
            helper.assertTrue(be.getItem(HydroponicsBayBlockEntity.SLOT_OUTPUT).getCount() == 64,
                "a full stack cannot grow past its own maximum");
            helper.assertTrue(be.getItem(HydroponicsBayBlockEntity.SLOT_INPUT).is(Items.SUGAR_CANE),
                "and the crop must survive the output having nowhere to put its yield");
            helper.succeed();
        });

        // A seedling's result is not known until the batch ends, so it cannot be compared against
        // whatever is already sitting there. The bay waits for a clear slot rather than gambling.
        RCGameTests.test("a_seedling_batch_waits_for_a_clear_output", 60, helper -> {
            var be = placeFuelled(helper, BAY);
            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT,
                new ItemStack(RCItems.UNKNOWN_SEEDLING.get()));
            be.setItem(HydroponicsBayBlockEntity.SLOT_OUTPUT, new ItemStack(Items.POTATO, 5));

            runBatches(helper, be, 2);
            ItemStack out = be.getItem(HydroponicsBayBlockEntity.SLOT_OUTPUT);
            helper.assertTrue(out.is(Items.POTATO) && out.getCount() == 5,
                "a lottery batch must not roll into an occupied slot, got " + out);
            helper.assertTrue(be.getItem(HydroponicsBayBlockEntity.SLOT_INPUT)
                    .is(RCItems.UNKNOWN_SEEDLING.get()),
                "and it must not spend the seedling doing so - seedlings are not renewable");
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

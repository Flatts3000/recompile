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
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

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
        // THE NULL SIDE. The bay has said "automation may not take my seed" since #43 -
        // canTakeItemThroughFace is `slot != SLOT_INPUT` - and was not enforcing it against one
        // caller: WorldlyContainerWrapper.extract is guarded by `side != null &&`, so a non-sided
        // query skipped the check and could pull the seed back out of a bay it was feeding.
        //
        // Closed by handing a non-sided caller no handler at all, the Burner Generator's pattern. Both
        // halves are asserted, because refusing everything would "fix" this while breaking the machine.
        RCGameTests.test("a_non_sided_pipe_gets_no_handler_on_the_bay", 20, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.HYDROPONICS_BAY.get());
            BlockPos abs = helper.absolutePos(pos);

            helper.assertTrue(
                helper.getLevel().getCapability(Capabilities.Item.BLOCK, abs, null) == null,
                "a non-sided query must get NO handler - one would bypass canTakeItemThroughFace and "
                    + "let a pipe take the seed");
            helper.assertTrue(
                helper.getLevel().getCapability(Capabilities.Item.BLOCK, abs, Direction.UP) != null,
                "a sided query must still get a handler - this bay is the automation tier");
            helper.succeed();
        });

        // ...and the rule it was failing to keep, now asserted from a real face rather than inferred.
        RCGameTests.test("a_pipe_cannot_take_the_bays_seed", 20, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.HYDROPONICS_BAY.get());
            BlockPos abs = helper.absolutePos(pos);
            ServerLevel level = helper.getLevel();
            if (!(level.getBlockEntity(abs) instanceof HydroponicsBayBlockEntity bay)) {
                helper.fail("the hydroponics bay has no BlockEntity");
                return;
            }
            bay.setItem(HydroponicsBayBlockEntity.SLOT_INPUT,
                new ItemStack(RCItems.UNKNOWN_SEEDLING.get(), 3));

            ResourceHandler<ItemResource> top =
                level.getCapability(Capabilities.Item.BLOCK, abs, Direction.UP);
            helper.assertTrue(top != null, "the top face must expose a handler");
            try (Transaction tx = Transaction.openRoot()) {
                int stolen = top.extract(ItemResource.of(RCItems.UNKNOWN_SEEDLING.get()), 3, tx);
                helper.assertTrue(stolen == 0,
                    "a pipe must not be able to take the seed out of a bay it is feeding, took "
                        + stolen);
            }
            helper.succeed();
        });


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

        // SEED-BASED CROPS ARE PLANTED AS THEIR SEED, the same as in the ground. Wheat seeds in, wheat
        // out - and a wheat item is not a thing you can plant, here or anywhere else in Minecraft.
        RCGameTests.test("a_seed_crop_grows_from_its_seed_not_from_its_harvest", 60, helper -> {
            helper.assertTrue(HydroponicsBayBlockEntity.isGrowable(new ItemStack(Items.WHEAT_SEEDS)),
                "wheat seeds are what you plant, so they are what the bay takes");
            helper.assertFalse(HydroponicsBayBlockEntity.isGrowable(new ItemStack(Items.WHEAT)),
                "a wheat item is not plantable in vanilla and must not be here either");
            helper.assertTrue(HydroponicsBayBlockEntity.yieldOf(Items.WHEAT_SEEDS) == Items.WHEAT,
                "wheat seeds must yield wheat, not more of themselves");

            var be = placeFuelled(helper, BAY);
            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT, new ItemStack(Items.WHEAT_SEEDS));
            ItemStack out = runBatches(helper, be, 1);
            helper.assertTrue(out.is(Items.WHEAT), "and the machine must agree with the map, got " + out);
            helper.assertTrue(be.getItem(HydroponicsBayBlockEntity.SLOT_INPUT).is(Items.WHEAT_SEEDS),
                "the seed stays planted");
            helper.succeed();
        });

        // Potato and carrot are their own seed in vanilla, so they stay direct inputs. Without this the
        // seed rule would look like it applied to every crop, and a bay full of potatoes would be wrong.
        RCGameTests.test("a_tuber_is_its_own_seed", 60, helper -> {
            var be = placeFuelled(helper, BAY);
            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT, new ItemStack(Items.POTATO));
            helper.assertTrue(runBatches(helper, be, 1).is(Items.POTATO),
                "a potato plants a potato and harvests potatoes");
            helper.assertTrue(HydroponicsBayBlockEntity.yieldOf(Items.CARROT) == Items.CARROT,
                "and a carrot likewise - neither has a separate seed item");
            helper.succeed();
        });

        // THE BYPRODUCT SLOT, and why it is a slot rather than a second entry in the output.
        // A poisonous potato cannot merge into a potato stack, so with one output it would either be
        // binned silently or jam a potato farm on the 2% roll vanilla gives it.
        RCGameTests.test("a_byproduct_lands_in_its_own_slot", 60, helper -> {
            var by = HydroponicsBayBlockEntity.byproductOf(Items.POTATO);
            helper.assertFalse(by == null, "potatoes must carry the poisonous-potato roll");
            helper.assertTrue(by.item() == Items.POISONOUS_POTATO,
                "and it must be the poisonous one, got " + by.item());
            helper.assertTrue(by.chance() > 0.0f && by.chance() < 1.0f,
                "at a chance, not every batch - vanilla's is 2%");

            // Wheat seeds throw off seeds every batch at 50%, which is frequent enough to observe.
            var be = placeFuelled(helper, BAY);
            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT, new ItemStack(Items.WHEAT_SEEDS));
            boolean seen = false;
            for (int batch = 0; batch < 12 && !seen; batch++) {
                try (Transaction tx = Transaction.openRoot()) {
                    be.battery().insert(Integer.MAX_VALUE, tx);
                    tx.commit();
                }
                be.setItem(HydroponicsBayBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
                runBatches(helper, be, 1);
                seen = be.getItem(HydroponicsBayBlockEntity.SLOT_BYPRODUCT).is(Items.WHEAT_SEEDS);
            }
            helper.assertTrue(seen,
                "a 50% byproduct must show up inside twelve batches, and it must be in the byproduct "
                    + "slot rather than merged into the harvest");
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

        // The gauges must scale to the tank and battery the machine ACTUALLY has, and those numbers must
        // reach the client through the menu rather than being recomputed there.
        //
        // Two ways recomputing is wrong, and neither shows up in singleplayer at default settings, which
        // is why this is asserted rather than eyeballed. RCConfig is a COMMON config and NeoForge does
        // not sync those, so a client on a tuned server draws both bars against numbers the server never
        // agreed to. And the tank and battery are sized when the block entity is BUILT, so even offline a
        // retune leaves placed bays at their old size while a config-derived gauge scales to the new one -
        // a full tank reading half full.
        RCGameTests.test("the_gauges_report_the_capacity_the_machine_really_has", 20, helper -> {
            helper.setBlock(BAY, RCBlocks.HYDROPONICS_BAY.get());
            var be = (HydroponicsBayBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(BAY));

            helper.assertTrue(be.tankCapacity() == be.tank().getCapacityAsInt(0,
                    FluidResource.of(Fluids.WATER)),
                "the reported tank capacity must come off the tank itself");
            helper.assertTrue(be.energyCapacity() == be.battery().getCapacityAsInt(),
                "and the reported battery capacity off the battery itself");

            // Fill both and confirm the gauge would read exactly full - the arithmetic the screen does.
            try (Transaction tx = Transaction.openRoot()) {
                be.tank().insert(FluidResource.of(Fluids.WATER), Integer.MAX_VALUE, tx);
                be.battery().insert(Integer.MAX_VALUE, tx);
                tx.commit();
            }
            helper.assertTrue(be.tank().getAmountAsInt(0) == be.tankCapacity(),
                "a tank filled to the brim must read as exactly its own capacity, not over or under");
            helper.assertTrue(be.battery().getAmountAsInt() == be.energyCapacity(),
                "and so must a full battery");
            helper.succeed();
        });

        // EVERYTHING IN THE MACHINE MUST SURVIVE A RELOAD, and none of the other tests can reach this
        // because none of them serialize. A block entity with no saveAdditional looks completely healthy
        // for a whole session and empties itself the moment the chunk unloads.
        //
        // What that costs here is the whole point of the machine: the crop is the player's only cactus,
        // the tank is the water they carried across the map, and the battery is the power a generator
        // spent the night making.
        RCGameTests.test("a_bay_survives_a_reload_with_everything_in_it", 20, helper -> {
            var be = placeFuelled(helper, BAY);
            var registries = helper.getLevel().registryAccess();
            be.setItem(HydroponicsBayBlockEntity.SLOT_INPUT, new ItemStack(Items.CACTUS));
            be.setItem(HydroponicsBayBlockEntity.SLOT_OUTPUT, new ItemStack(Items.CACTUS, 7));
            be.setItem(HydroponicsBayBlockEntity.SLOT_BYPRODUCT, new ItemStack(Items.POISONOUS_POTATO, 3));
            int water = be.tank().getAmountAsInt(0);
            int power = be.battery().getAmountAsInt();
            helper.assertTrue(water > 0 && power > 0, "precondition: the bay was fuelled");

            net.minecraft.nbt.CompoundTag tag = be.saveCustomOnly(registries);
            var reloaded = new HydroponicsBayBlockEntity(be.getBlockPos(), be.getBlockState());
            reloaded.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, registries, tag));

            helper.assertTrue(reloaded.getItem(HydroponicsBayBlockEntity.SLOT_INPUT).is(Items.CACTUS),
                "the crop must survive - it may be the only cactus in the save");
            helper.assertTrue(reloaded.getItem(HydroponicsBayBlockEntity.SLOT_OUTPUT).getCount() == 7,
                "and the harvest waiting in the output");
            helper.assertTrue(
                reloaded.getItem(HydroponicsBayBlockEntity.SLOT_BYPRODUCT).getCount() == 3,
                "and the byproduct");
            helper.assertTrue(reloaded.tank().getAmountAsInt(0) == water,
                "the tank must survive; saved " + water + " mB, reloaded "
                    + reloaded.tank().getAmountAsInt(0) + " mB");
            helper.assertTrue(reloaded.battery().getAmountAsInt() == power,
                "and the battery; saved " + power + " FE, reloaded "
                    + reloaded.battery().getAmountAsInt() + " FE");
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

package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.entity.WaterTankBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCDataComponents;
import com.flatts.recompile.registry.RCFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Water Tank holds water (#229).
 *
 * <p><b>Owner ruling, 2026-08-18, from playtest: a water tank should hold water and work like a
 * tank.</b> A scoped reversal of P2.4-R item 6 for this block alone, so these tests are the record of
 * what "works like a tank" was taken to mean - it stores, it refuses what it is not for, it survives
 * being picked up, and a pipe can reach it.
 *
 * <p>The last of those is the one worth writing down. A tank that only ever fills through its own
 * BlockEntity passes every test a harness can write while being unreachable by anything in the world -
 * the exact failure the Trommel shipped with, where the tests powered the battery directly and no
 * generator in the game could connect. So the capability is asserted, not the field.
 */
final class WaterTankTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private WaterTankTests() {
    }

    static void register() {
        // IT HOLDS WATER, which is the whole ruling.
        RCGameTests.test("a_water_tank_holds_water", 20, helper -> {
            helper.setBlock(POS, RCBlocks.WATER_TANK.get());
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(POS))
                    instanceof WaterTankBlockEntity be)) {
                helper.fail("a placed Water Tank has no BlockEntity, so it can hold nothing");
                return;
            }
            helper.assertTrue(be.storedWater() == 0, "a fresh tank must be empty");
            be.fill(1000);
            helper.assertTrue(be.storedWater() == 1000,
                "the tank took no water; stored " + be.storedWater() + " mB of 1000");
            helper.assertTrue(be.storedWater() <= WaterTankBlockEntity.CAPACITY,
                "the tank holds more than its own capacity");
            helper.succeed();
        });

        // AND ONLY WATER. It is called a Water Tank and the ruling was that it should hold water; a
        // general fluid store is a different block with a different name. Leachate is the one other
        // fluid a player can carry in a bucket, so it is the one that would actually turn up.
        RCGameTests.test("a_water_tank_takes_only_water", 20, helper -> {
            helper.setBlock(POS, RCBlocks.WATER_TANK.get());
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(POS))
                    instanceof WaterTankBlockEntity be)) {
                helper.fail("a placed Water Tank has no BlockEntity");
                return;
            }
            int moved;
            try (Transaction transaction = Transaction.openRoot()) {
                moved = (int) be.fluidHandler()
                    .insert(FluidResource.of(RCFluids.LEACHATE.get()), 1000, transaction);
                transaction.commit();
            }
            helper.assertTrue(moved == 0 && be.storedWater() == 0,
                "the tank accepted " + moved + " mB of leachate - a block labelled Water Tank that "
                    + "fills with the hazard fluid is the same surprise this issue exists to remove");
            helper.succeed();
        });

        // A PIPE CAN REACH IT, asserted through the capability rather than the field.
        RCGameTests.test("a_water_tank_offers_its_fluid_capability", 20, helper -> {
            helper.setBlock(POS, RCBlocks.WATER_TANK.get());
            var handler = helper.getLevel().getCapability(
                Capabilities.Fluid.BLOCK, helper.absolutePos(POS), null);
            helper.assertTrue(handler != null,
                "the Water Tank exposes no fluid capability, so nothing in the world can fill it - "
                    + "which is a tank that works in this test file and nowhere else");
            int moved;
            try (Transaction transaction = Transaction.openRoot()) {
                moved = (int) handler.insert(FluidResource.of(Fluids.WATER), 500, transaction);
                transaction.commit();
            }
            helper.assertTrue(moved == 500,
                "the capability took " + moved + " mB of 500, so a pipe would see a tank that refuses "
                    + "water");
            helper.succeed();
        });

        // AND IT SURVIVES BEING PICKED UP. saveAdditional covers save/load and nothing else - breaking
        // the block destroys the BlockEntity - so without the item component a tank emptied itself
        // every time it was moved, which is a tank in name only. Same trap the Rain Collector
        // documents, and the reason its test exists in this shape.
        RCGameTests.test("a_water_tank_keeps_its_water_through_a_break", 20, helper -> {
            helper.setBlock(POS, RCBlocks.WATER_TANK.get());
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(POS))
                    instanceof WaterTankBlockEntity be)) {
                helper.fail("a placed Water Tank has no BlockEntity");
                return;
            }
            be.fill(750);
            int stored = be.storedWater();
            helper.assertTrue(stored == 750, "precondition: the tank was filled");

            var components = be.collectComponents();
            Integer carried = components.get(RCDataComponents.TANK_WATER.get());
            helper.assertTrue(carried != null && carried == stored,
                "a broken tank must carry its water on the dropped item, got " + carried);

            WaterTankBlockEntity replaced =
                new WaterTankBlockEntity(be.getBlockPos(), be.getBlockState());
            replaced.applyComponents(components, net.minecraft.core.component.DataComponentPatch.EMPTY);
            helper.assertTrue(replaced.storedWater() == stored,
                "a replaced tank must restore its water; expected " + stored + " got "
                    + replaced.storedWater());
            helper.succeed();
        });
    }
}

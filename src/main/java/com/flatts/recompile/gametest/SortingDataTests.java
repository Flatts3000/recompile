package com.flatts.recompile.gametest;

import com.flatts.recompile.compat.SortingData;
import com.flatts.recompile.registry.RCItems;
import java.util.List;

/**
 * GameTests for {@link SortingData} - the loot-table parsing the JEI Sorting/Prying
 * categories render. The categories themselves are client-only and cannot be tested here,
 * but this is the logic that would silently show the wrong odds, so it is the piece worth
 * pinning. Runs on the server; no world interaction needed.
 */
final class SortingDataTests {

    private SortingDataTests() {
    }

    static void register() {
        RCGameTests.test("sorting_data_reads_household", 10, helper -> {
            List<SortingData.Weighted> out = SortingData.outputs(SortingData.HOUSEHOLD);
            helper.assertTrue(!out.isEmpty(), "household pulls must parse to outputs");

            float sum = 0f;
            for (SortingData.Weighted w : out) {
                sum += w.chance();
            }
            helper.assertTrue(Math.abs(sum - 1.0f) < 0.01f,
                "one pool's chances should sum to ~1, got " + sum);

            SortingData.Weighted junk = out.stream()
                .filter(w -> w.stack().is(RCItems.JUNK.get())).findFirst().orElse(null);
            helper.assertTrue(junk != null && junk.chance() > 0.3f,
                "junk (weight 200) should dominate the household pull");
            SortingData.Weighted tin = out.stream()
                .filter(w -> w.stack().is(RCItems.TIN_CAN.get())).findFirst().orElse(null);
            helper.assertTrue(tin != null,
                "the tin can (a rare pull) should appear in the household stream");
            // Glass bottles are the found input for the Rain Collector (you can't craft one
            // in this world), dropped at half the tin can's weight.
            SortingData.Weighted bottle = out.stream()
                .filter(w -> w.stack().is(net.minecraft.world.item.Items.GLASS_BOTTLE))
                .findFirst().orElse(null);
            helper.assertTrue(bottle != null,
                "glass bottles should be a household pull - the collector's only source of them");
            helper.assertTrue(Math.abs(bottle.chance() - tin.chance() * 0.5f) < 0.001f,
                "glass bottles should be half as likely as tin cans");
            helper.succeed();
        });

        // Prying reads the block loot table, which now holds two weighted finds - the mattress
        // (weight 3) and the broken appliance the Motor comes out of (weight 2). This is the
        // "adding a find is a loot-table line" invariant working: a second find needed no code.
        RCGameTests.test("sorting_data_reads_bulky_finds", 10, helper -> {
            List<SortingData.Weighted> out = SortingData.outputs(SortingData.BULKY);
            // The furniture finds plus the six recovered paintings (#99), which live in their own 7%
            // pool. Counted rather than listed so a new find has to come here and be acknowledged: a
            // magic 8 that silently became 9 would mean nobody noticed the table changed.
            helper.assertTrue(out.size() == 10,
                "Bulky Waste should offer the four finds and the six paintings, got " + out.size());

            // The paintings' pool is gated on random_chance, and a reader that ignored that would show
            // each at 1/6 = 16.7% instead of 0.07/6 = 1.2%. JEI's whole job in these categories is the
            // odds, so an overstated rarity is a wrong answer, not a rounding error.
            SortingData.Weighted painting = out.stream()
                .filter(w -> w.stack().is(net.minecraft.world.item.Items.PAINTING))
                .findFirst().orElse(null);
            helper.assertTrue(painting != null, "a recovered painting must be a Bulky Waste find");
            helper.assertTrue(painting.chance() < 0.02F && painting.chance() > 0.008F,
                "a painting should read as roughly 1.2%, the 7% pool split six ways - got "
                    + (painting.chance() * 100) + "%");

            SortingData.Weighted mattress = out.stream()
                .filter(w -> w.stack().is(RCItems.MATTRESS.get())).findFirst().orElse(null);
            SortingData.Weighted appliance = out.stream()
                .filter(w -> w.stack().is(RCItems.WASHING_MACHINE.get())).findFirst().orElse(null);
            helper.assertTrue(mattress != null, "the mattress must still be a Bulky Waste find");
            SortingData.Weighted cabinet = out.stream()
                .filter(w -> w.stack().is(RCItems.FILING_CABINET.get())).findFirst().orElse(null);
            helper.assertTrue(cabinet != null,
                "the Filing Cabinet must be a Bulky Waste find - it is not craftable, so if it leaves "
                    + "this table there is no way to obtain one at all");
            helper.assertTrue(appliance != null,
                "the broken appliance must be a Bulky Waste find - it is the only source of Motors");

            // Every furniture find shares one pool, so their chances are a partition of it and must
            // sum to 1. Summing only the two named finds is what made this fail when a third arrived:
            // the assertion was really "these are ALL the finds", written as if it were about odds.
            SortingData.Weighted brokenBay = out.stream()
                .filter(w -> w.stack().is(RCItems.BROKEN_HYDROPONICS_BAY.get())).findFirst()
                .orElse(null);
            helper.assertTrue(brokenBay != null,
                "the Broken Hydroponics Bay must be a Bulky Waste find - it is the only thing that "
                    + "teaches the working bay, so if it leaves this table the machine is unbuildable");

            float sum = mattress.chance() + appliance.chance() + cabinet.chance()
                + brokenBay.chance();
            helper.assertTrue(Math.abs(sum - 1.0f) < 0.001f,
                "the furniture pool's chances should sum to ~1, got " + sum);
            helper.assertTrue(mattress.chance() > appliance.chance(),
                "the mattress stays the commonest find (weight 3 vs 2) - it is the teardown source the "
                    + "whole blueprint loop runs on");
            helper.succeed();
        });

        // The Steel I-Beam's drop feeds JEI's Cutting category, so the offcut has a visible SOURCE -
        // block drops are otherwise invisible to JEI, and an item you can only use is half an item.
        RCGameTests.test("sorting_data_reads_the_steel_beam", 10, helper -> {
            List<SortingData.Weighted> out = SortingData.outputs(SortingData.STEEL_BEAM);
            helper.assertTrue(!out.isEmpty(), "the steel beam's drop table must parse to outputs");
            helper.assertTrue(out.stream().allMatch(w -> w.stack().is(RCItems.STEEL_OFFCUT.get())),
                "a cut beam must show steel offcuts and nothing else, got " + out);
            helper.succeed();
        });
    }
}

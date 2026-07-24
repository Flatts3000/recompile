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
            helper.assertTrue(out.size() == 2,
                "Bulky Waste should offer both finds, got " + out.size());

            SortingData.Weighted mattress = out.stream()
                .filter(w -> w.stack().is(RCItems.MATTRESS.get())).findFirst().orElse(null);
            SortingData.Weighted appliance = out.stream()
                .filter(w -> w.stack().is(RCItems.WASHING_MACHINE.get())).findFirst().orElse(null);
            helper.assertTrue(mattress != null, "the mattress must still be a Bulky Waste find");
            helper.assertTrue(appliance != null,
                "the broken appliance must be a Bulky Waste find - it is the only source of Motors");

            float sum = mattress.chance() + appliance.chance();
            helper.assertTrue(Math.abs(sum - 1.0f) < 0.001f,
                "one pool's chances should sum to ~1, got " + sum);
            helper.assertTrue(mattress.chance() > appliance.chance(),
                "the mattress is the commoner find (weight 3 vs 2)");
            helper.succeed();
        });
    }
}

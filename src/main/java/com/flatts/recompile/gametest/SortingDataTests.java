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

        // Prying reads the block loot table; today it holds exactly a mattress.
        RCGameTests.test("sorting_data_reads_bulky_find", 10, helper -> {
            List<SortingData.Weighted> out = SortingData.outputs(SortingData.BULKY);
            helper.assertTrue(out.size() == 1
                    && out.get(0).stack().is(RCItems.MATTRESS.get())
                    && out.get(0).chance() == 1.0f,
                "Bulky Waste currently finds exactly a mattress at 100%");
            helper.succeed();
        });
    }
}

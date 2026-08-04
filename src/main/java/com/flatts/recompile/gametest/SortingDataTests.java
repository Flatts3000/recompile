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
            // Four spine finds, three windfall finds, six recovered paintings (#99). Counted rather
            // than listed so a new find has to come here and be acknowledged: a magic 10 that silently
            // became 11 would mean nobody noticed the table changed.
            //
            // Reaching 13 at all is the point of this number now. The spine and windfall tiers are
            // NESTED loot tables, and a reader that skipped minecraft:loot_table entries would return
            // six - a Prying category containing nothing but paintings, with every real find gone and
            // no error anywhere.
            helper.assertTrue(out.size() == 13,
                "Bulky Waste should offer four spine finds, three windfall finds and six paintings, "
                    + "got " + out.size());

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

            // A WINDFALL IS RARE, AND ITS RARITY IS THE WHOLE POINT. The jukebox and the bell need
            // diamond and gold, so finding one hands the player a capability this world cannot make;
            // if it turned up as often as a mattress the spine would be crowded out by novelties.
            SortingData.Weighted jukebox = out.stream()
                .filter(w -> w.stack().is(net.minecraft.world.item.Items.JUKEBOX))
                .findFirst().orElse(null);
            helper.assertTrue(jukebox != null, "the jukebox must be a windfall find");
            helper.assertTrue(jukebox.chance() < brokenBay.chance(),
                "a windfall (" + jukebox.chance() + ") must be rarer than the rarest spine find ("
                    + brokenBay.chance() + ") - the spine is what moves the player forward");

            // The two tiers partition the same pool, so everything that is not a painting sums to 1.
            // Summing only the spine is what made this fail when the tiers arrived: the assertion read
            // as if it were about odds and was really "these are ALL the finds".
            float sum = 0;
            for (SortingData.Weighted w : out) {
                if (!w.stack().is(net.minecraft.world.item.Items.PAINTING)) {
                    sum += w.chance();
                }
            }
            helper.assertTrue(Math.abs(sum - 1.0f) < 0.001f,
                "the spine and windfall tiers should partition the pool and sum to ~1, got " + sum);
            helper.assertTrue(mattress.chance() > appliance.chance(),
                "the mattress stays the commonest find (weight 3 vs 2) - it is the teardown source the "
                    + "whole blueprint loop runs on");
            helper.succeed();
        });

        // A TAG ENTRY IS SIXTEEN ITEMS, NOT ONE NAME. Bags carry wool and carpet as tag entries, which
        // is what keeps a new vanilla dye colour from silently changing how often WOOL comes up. A
        // reader that did not expand them would drop both from the Sorting category entirely - no
        // error, just two rows missing from a screen whose whole job is telling the player what is in
        // a bag.
        RCGameTests.test("sorting_data_expands_a_tag_entry", 10, helper -> {
            List<SortingData.Weighted> out = SortingData.outputs(SortingData.BAG);
            List<SortingData.Weighted> wools = out.stream()
                .filter(w -> w.stack().is(net.minecraft.tags.ItemTags.WOOL)).toList();
            helper.assertTrue(wools.size() > 8,
                "a wool tag entry should read as every colour in the tag, got " + wools.size());

            // Evenly, because that is what expand:false does - roll once, then pick a member at
            // random. Reporting the whole share against one colour would overstate it sixteenfold.
            float first = wools.get(0).chance();
            for (SortingData.Weighted w : wools) {
                helper.assertTrue(Math.abs(w.chance() - first) < 0.0001F,
                    "every colour in a tag entry shares its odds evenly - got " + w.chance()
                        + " against " + first);
            }

            float sum = 0;
            for (SortingData.Weighted w : out) {
                sum += w.chance();
            }
            helper.assertTrue(Math.abs(sum - 1.0F) < 0.01F,
                "expanding a tag must not create or destroy probability - the bag pool still sums to "
                    + "~1, got " + sum);
            helper.succeed();
        });

        // WHAT A VIEWER READS MUST BE DISCOVERED, NOT LISTED. TeardownData named its recipe paths in a
        // constant; when the Broken Hydroponics Bay teardown shipped, it was invisible to every viewer
        // - the block could be torn down in-world while JEI denied the recipe existed, and nothing
        // failed. This asserts the count matches the files on disk rather than a number written here.
        RCGameTests.test("every_bundled_teardown_reaches_the_viewers", 20, helper -> {
            int onDisk = com.flatts.recompile.compat.RecipeFiles.ofType("recompile:teardown").size();
            int surfaced = com.flatts.recompile.compat.TeardownData.all().size();
            helper.assertTrue(onDisk > 2,
                "only " + onDisk + " teardown files were discovered - the walk is broken, so this "
                    + "would pass against any recipe the viewers cannot see");
            helper.assertTrue(surfaced == onDisk,
                "every teardown recipe on disk must reach JEI; " + onDisk + " files, "
                    + surfaced + " surfaced");
            helper.succeed();
        });

        // Same for the blueprint recipes, which had the sharper version of this problem: they were
        // read from the synced recipe manager, and JEI builds its categories on its own schedule, so
        // the list could simply be empty. A player clicking a Clean Mattress then saw how to DYE one
        // and no way to make one at all.
        RCGameTests.test("every_blueprint_recipe_reaches_the_viewers", 20, helper -> {
            int onDisk = com.flatts.recompile.compat.RecipeFiles
                .ofType("recompile:blueprint_crafting").size();
            var surfaced = com.flatts.recompile.compat.BlueprintData.all();
            helper.assertTrue(onDisk > 0, "no blueprint recipe files were discovered");
            helper.assertTrue(surfaced.size() == onDisk,
                "every blueprint recipe must reach JEI; " + onDisk + " files, " + surfaced.size()
                    + " surfaced");
            for (var entry : surfaced) {
                helper.assertTrue(!entry.ingredients().isEmpty(),
                    entry.blueprint() + " surfaced with no ingredients, so the recipe would draw "
                        + "as an empty grid");
            }
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

package com.flatts.recompile.gametest;

import com.flatts.recompile.compat.JeiInfoPanels;
import com.flatts.recompile.compat.SortingData;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.tags.ItemTags;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * GameTests for {@link SortingData} - the loot-table parsing the JEI Sorting/Prying
 * categories render. The categories themselves are client-only and cannot be tested here,
 * but this is the logic that would silently show the wrong odds, so it is the piece worth
 * pinning. Runs on the server; no world interaction needed.
 */
final class SortingDataTests {

    private static final String INFO_PREFIX = "jei.recompile.info.";

    private SortingDataTests() {
    }

    static void register() {
        registerTagFormGuard();
        // JEI INFO PANELS AND LANG KEYS AGREE, BOTH WAYS.
        //
        // A declared-but-unregistered key is a translation nothing can ever ask for: it resolves
        // fine, it is simply never requested, and it is invisible at runtime and in every existing
        // test. RegistryCompletenessTests checks that items have translated NAMES; GuidebookTests
        // checks that guidebook keys EXIST. Neither knows which keys JEI actually asks for.
        //
        // It went wrong three times before this was written - leachate_bucket, then motor and bulb -
        // each caught only by a human reading a diff. Writing it immediately found two more that had
        // been dead for months: printer and broken_hydroponics_bay, the finds that gate the dye set
        // and the Hydroponics Bay blueprint, which are exactly the two a player would look up.
        //
        // The reverse is equally silent: a registration with no lang entry renders the raw key.
        RCGameTests.test("jei_info_panels_and_lang_keys_agree", 20, helper -> {
            Set<String> registered = new HashSet<>();
            for (JeiInfoPanels.Panel panel : JeiInfoPanels.all()) {
                helper.assertTrue(registered.add(panel.key()),
                    "duplicate info panel for key " + panel.key());
            }

            // Read the shipped lang off the classpath, the same way GuidebookTests does - a server
            // never loads assets/, but a dev run has them on the classpath.
            Set<String> declared = new HashSet<>();
            for (String key : RegistryCompletenessTests.langKeysStartingWith(INFO_PREFIX)) {
                declared.add(key.substring(INFO_PREFIX.length()));
            }
            helper.assertTrue(!declared.isEmpty(),
                "no jei.recompile.info.* keys were found at all - the lang file did not load, so "
                    + "this test would pass by comparing two empty sets");

            List<String> dead = new ArrayList<>(declared);
            dead.removeAll(registered);
            List<String> raw = new ArrayList<>(registered);
            raw.removeAll(declared);

            helper.assertTrue(dead.isEmpty(),
                "these info strings are written and never registered, so no player can ever see "
                    + "them: " + dead.stream().sorted().toList());
            helper.assertTrue(raw.isEmpty(),
                "these panels are registered with no lang entry, so JEI renders the raw key to the "
                    + "player: " + raw.stream().sorted().toList());
            helper.succeed();
        });

        // EVERY SORTABLE HAS A JEI-VISIBLE STREAM. Mechanical Waste was missing from the Sorting
        // category for its whole life, and the symptom was almost invisible: clicking Magnet Scrap
        // in JEI showed NOTHING. Its only source is that stream, block drops are invisible to JEI,
        // and no recipe produces it - so the panel was empty, which a player reads as a broken item
        // rather than a missing entry. Quartz Grit, Spent Abrasive and the Motor were all the same.
        //
        // The list JEI registers is now derived from the block registry, so this asserts the
        // derivation covers every sortable and that each stream actually has something to show.
        RCGameTests.test("every_sortable_block_is_a_jei_sorting_source", 20, helper -> {
            var sources = SortingData.sortingSources();
            helper.assertTrue(sources.size() >= 5,
                "expected every sortable (garbage, bag, bale, rubble, mechanical waste) to be a "
                    + "sorting source, got " + sources.size());

            List<String> empty = new ArrayList<>();
            for (var source : sources) {
                if (SortingData.visibleOutputs(source.path()).isEmpty()) {
                    empty.add(source.block() + " -> " + source.path());
                }
            }
            helper.assertTrue(empty.isEmpty(),
                "these sortables have nothing for JEI to show, so whatever they alone drop looks "
                    + "like a broken item: " + empty);

            boolean mechanical = sources.stream()
                .anyMatch(src -> src.path().contains("mechanical_pulls"));
            helper.assertTrue(mechanical,
                "Mechanical Waste must be a sorting source - it is the only place Magnet Scrap, "
                    + "Quartz Grit, Spent Abrasive and the Motor come from");
            helper.succeed();
        });

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

            // Music discs left the stream (owner, 2026-08-12) and the assertion that they were in it
            // went with them. What replaces it is the durable-goods pool itself: the point of that
            // pool is that a tool is rare without its weight having to be a fraction, so the check
            // worth keeping is that a rare finished good is still reachable and still rare.
            SortingData.Weighted bucket = out.stream()
                .filter(w -> w.stack().is(net.minecraft.world.item.Items.BUCKET))
                .findFirst().orElse(null);
            helper.assertTrue(bucket != null,
                "the bucket should be a household pull - it is the only way to move water in a "
                    + "standalone install, so the whole green tier hangs off this entry");
            helper.assertTrue(bucket.chance() < 0.001f,
                "a found tool should be rare - the bucket reads as " + bucket.chance()
                    + " per pull, and it shipped at one every two minutes once already");
            helper.succeed();
        });

        // Prying reads the block loot table, which now holds five weighted spine finds. This is the
        // "adding a find is a loot-table line" invariant working: the printer needed one JSON line to
        // appear here, and the only Java it touched was this count.
        // TREASURE IS HIDDEN FROM THE VIEWER, NOT FROM THE WORLD (owner, 2026-08-04). A JEI category
        // listing every collectible and all six recovered paintings spends the surprise before the
        // player has broken a block.
        //
        // Asserted in BOTH directions on purpose. "It is not in the category" alone would pass just as
        // happily if somebody deleted the entries from the loot tables, which is the opposite of what
        // was asked for - the drop must still be there, unchanged, and only the viewer blind to it.
        RCGameTests.test("a_viewer_does_not_spoil_the_collectibles", 20, helper -> {
            List<String> problems = new ArrayList<>();
            for (String table : List.of(SortingData.HOUSEHOLD, SortingData.BAG, SortingData.BULKY)) {
                var all = SortingData.outputs(table);
                var shown = SortingData.visibleOutputs(table);
                helper.assertTrue(!all.isEmpty(), table + " must parse to outputs");

                long hidden = all.stream()
                    .filter(w -> w.stack().is(com.flatts.recompile.registry.RCTags.UNDISCOVERABLE))
                    .count();
                if (hidden == 0) {
                    problems.add(table + " contains no tagged treasure at all - either the tag is empty "
                        + "or the loot tables stopped dropping it, and this test then proves nothing");
                }
                for (var weighted : shown) {
                    if (weighted.stack().is(com.flatts.recompile.registry.RCTags.UNDISCOVERABLE)) {
                        problems.add(table + " still shows " + weighted.stack().getItem());
                    }
                }
                if (shown.size() != all.size() - hidden) {
                    problems.add(table + " hid " + (all.size() - shown.size()) + " entries but only "
                        + hidden + " are tagged - the filter is removing something else");
                }
            }
            helper.assertTrue(problems.isEmpty(), "collectible spoiler check: " + problems);
            helper.succeed();
        });

        RCGameTests.test("sorting_data_reads_bulky_finds", 10, helper -> {
            List<SortingData.Weighted> out = SortingData.outputs(SortingData.BULKY);
            // Five spine finds (the fifth is the printer, #112), four windfall finds (the fourth is
            // the found spyglass, #113), six recovered paintings (#99). Counted rather than listed so a
            // new find has to come here and be acknowledged: a magic 14 that silently became 15 would
            // mean nobody noticed. It caught two when they were added (the Broken Fan and the Light
            // Fixture) and caught the number again when the Fridge replaced both - which is the job.
            //
            // Reaching 13 at all is the point of this number now. The spine and windfall tiers are
            // NESTED loot tables, and a reader that skipped minecraft:loot_table entries would return
            // six - a Prying category containing nothing but paintings, with every real find gone and
            // no error anywhere.
            // Six spine finds since 2026-08-12: the Broken Fan and the Broken Light Fixture were
            // replaced by the single Dead Fridge, which yields all three components between them.
            helper.assertTrue(out.size() == 16,
                "Bulky Waste should offer six spine finds, four windfall finds and six paintings, "
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

        /*
         * A TAG ENTRY IS SIXTEEN ITEMS, NOT ONE NAME, AND EACH ONE CARRIES THE ENTRY'S FULL ODDS. A
         * reader that did not expand one would drop the whole entry from the Sorting category - no
         * error, just a row missing from a screen whose only job is telling the player what is in a
         * bag - and one that expanded it while dividing the share understates every member instead.
         *
         * RUN AGAINST A FIXTURE, AND THAT IS THE POINT. This used to read bag_pulls, where wool was a
         * tag entry. Wool left the stream on 2026-08-11 (fiber scrap already makes string and string
         * already makes wool), carpets had gone the same day, and with them went the last tag entry in
         * the mod: SortingData.expandTag kept shipping with an unreachable call site while a test named
         * for it passed by finding nothing to expand. Coverage that depends on a piece of CONTENT still
         * existing disappears the moment that content is retuned, and it disappears silently - the same
         * shape as a pull hook that only ever covered hand sorting.
         *
         * data/recompile/gametest/ is not a loot-table directory, so the fixture is on the classpath
         * for SortingData (which reads raw paths) and invisible to the game.
         */
        RCGameTests.test("sorting_data_expands_a_tag_entry", 10, helper -> {
            List<SortingData.Weighted> out =
                SortingData.outputs("/data/recompile/gametest/tag_entry_fixture.json");
            helper.assertTrue(!out.isEmpty(),
                "the tag fixture did not parse at all, so everything below would pass vacuously");

            List<SortingData.Weighted> wools = out.stream()
                .filter(w -> w.stack().is(ItemTags.WOOL)).toList();
            helper.assertTrue(wools.size() > 8,
                "a wool tag entry should read as every colour in the tag, got " + wools.size());

            // Equally, and at the ENTRY'S OWN SHARE rather than a sixteenth of it.
            //
            // This assertion used to divide, and it was wrong for the same reason the code was: an
            // expand:false tag entry does NOT roll once and then pick a member. Vanilla's
            // TagEntry.createItemStack emits EVERY member of the tag when the entry wins:
            //
            //   BuiltInRegistries.ITEM.getTagOrEmpty(this.tag)
            //       .forEach(item -> output.accept(new ItemStack(item)));
            //
            // So all sixteen colours drop together in the 32 rolls per 100 that the entry wins, and
            // each colour's chance of appearing is 0.32 - not 0.02. The mod had measured this already
            // and written it down in chests/sump.json ("yields EVERY item in the tag at once rather
            // than picking one", 16 of 16 in #268); the code, this test and two javadocs all agreed
            // with each other and all disagreed with the measurement. Caught reviewing #279.
            float first = wools.get(0).chance();
            for (SortingData.Weighted w : wools) {
                helper.assertTrue(Math.abs(w.chance() - first) < 0.0001F,
                    "every colour in a tag entry shares its odds equally - got " + w.chance()
                        + " against " + first);
            }
            helper.assertTrue(Math.abs(first - 0.32F) < 0.01F,
                "each colour of an expand:false tag entry appears whenever the ENTRY wins, so each "
                    + "should read 0.32 (weight 32 of 100), got " + first + ". Dividing by the member "
                    + "count understates every member by sixteen times here.");

            // NOT a probability distribution, and that is the thing worth stating out loud: these
            // sixteen are not mutually exclusive, so their shares sum to an expected COUNT (5.12
            // wool per hundred rolls) rather than to the entry's 0.32. An assertion that they sum to
            // the entry's share is what encoded the wrong model for as long as it stood.
            float woolPerRoll = 0;
            for (SortingData.Weighted w : wools) {
                woolPerRoll += w.chance();
            }
            helper.assertTrue(Math.abs(woolPerRoll - 5.12F) < 0.05F,
                "sixteen colours at 0.32 each is an expected count of 5.12 per roll, got "
                    + woolPerRoll);

            // And the live stream still parses to a whole, tag entry or not. Catches a pool that
            // silently drops entries, which is the failure this file exists for.
            float sum = 0;
            for (SortingData.Weighted w : SortingData.outputs(SortingData.BAG)) {
                sum += w.chance();
            }
            helper.assertTrue(Math.abs(sum - 1.0F) < 0.01F,
                "parsing a pool must not create or destroy probability - the bag pool should still "
                    + "sum to ~1, got " + sum);
            helper.succeed();
        });

        // WHAT A VIEWER READS MUST BE DISCOVERED, NOT LISTED. TeardownData named its recipe paths in a
        // constant; when the Broken Hydroponics Bay teardown shipped, it was invisible to every viewer
        // - the block could be torn down in-world while JEI denied the recipe existed, and nothing
        // failed. This asserts the count matches the files on disk rather than a number written here.
        // A POOL'S QUANTITY MUST REACH THE VIEWER, not just its existence.
        //
        // The fridge's scrap pool rolls EIGHT times over metal/plastic/e-scrap. Expressed as a bare
        // per-item chance that saturates at 100%, a player reads "one metal, one plastic, maybe an
        // e-scrap" for a teardown that actually hands over eight items - the viewer would be off by
        // a factor of four on the mod's flagship recipe. Same family as #180: the mechanic works and
        // the viewer quietly describes a different game. Chance alone cannot carry "how many", so
        // the stack count has to.
        RCGameTests.test("a_viewer_reads_how_many_a_pool_gives", 20, helper -> {
            var fridge = com.flatts.recompile.compat.TeardownData.all().stream()
                .filter(e -> e.input().getItem() == RCItems.FRIDGE.get())
                .findFirst();
            helper.assertTrue(fridge.isPresent(), "the fridge teardown must reach the viewers");

            var metal = fridge.get().outputs().stream()
                .filter(w -> w.stack().getItem() == RCItems.SCRAP_METAL.get())
                .findFirst();
            helper.assertTrue(metal.isPresent(), "scrap metal must be listed as a fridge output");

            // 8 rolls, weight 5 of 10 -> four scrap metal per teardown on average.
            int shown = metal.get().stack().getCount();
            helper.assertTrue(shown == 4,
                "a viewer must show the ~4 scrap metal a fridge actually gives, showed " + shown);
            helper.succeed();
        });

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

    /**
     * No table this viewer reads may use an {@code expand: false} tag entry, because the share maths
     * for that form has never been exercised.
     *
     * <p><b>Why a guard rather than a test of the behaviour.</b> Review of #279 found
     * {@code SortingData.expandTag} dividing a tag entry's share by its member count, on the belief
     * that {@code expand: false} rolls once and picks one member. Vanilla's {@code TagEntry} emits
     * ALL members instead, so every member's chance is the entry's own - the code understated each by
     * a factor of N. Nothing caught it because nothing reaches it: the mod's single tag entry lives in
     * {@code chests/sump.json}, and this class reads pull streams rather than chest tables.
     *
     * <p>Correcting the maths without covering the path would leave the same silence behind. This
     * fails the build the day a stream starts using that form, so the coverage has to arrive with the
     * content. {@code expand: true} is unaffected and stays free to use - it is what the Grains of
     * Infinity entry uses, and the rate census measures it end to end.
     */
    private static void registerTagFormGuard() {
        RCGameTests.test("no_unexercised_tag_entry_reaches_the_viewer", 40, helper -> {
            var offenders = new java.util.TreeSet<String>();
            var paths = new java.util.ArrayList<>(java.util.List.of(
                SortingData.HOUSEHOLD, SortingData.BAG, SortingData.MECHANICAL, SortingData.RUBBLE,
                SortingData.BULKY, SortingData.STEEL_BEAM, SortingData.SEEDLING));
            for (var source : SortingData.sortingSources()) {
                paths.add(source.path());
            }

            int scanned = 0;
            for (String path : paths) {
                String body = readResource(path);
                if (body == null) {
                    continue;
                }
                scanned++;
                scanForTagEntries(com.google.gson.JsonParser.parseString(body), path, offenders);
            }

            helper.assertTrue(scanned >= 7,
                "scanned only " + scanned + " tables, so this guard would pass against a viewer that "
                    + "had stopped reading anything");
            helper.assertTrue(offenders.isEmpty(),
                "these use a minecraft:tag entry with expand:false, whose share maths in SortingData "
                    + "is not exercised by any test: " + offenders + ". Vanilla emits EVERY member of "
                    + "the tag when such an entry wins, so each member's chance is the entry's own - "
                    + "getting that wrong understates every member by the member count and the pool's "
                    + "reported odds stop summing to 1. Use expand:true, which the rate census "
                    + "measures, or add coverage for this form in the same change.");
            helper.succeed();
        });
    }

    /** Walk any JSON, collecting {@code minecraft:tag} entries that do not set {@code expand: true}. */
    private static void scanForTagEntries(com.google.gson.JsonElement node, String path,
            java.util.Set<String> offenders) {
        if (node.isJsonArray()) {
            for (var child : node.getAsJsonArray()) {
                scanForTagEntries(child, path, offenders);
            }
            return;
        }
        if (!node.isJsonObject()) {
            return;
        }
        var obj = node.getAsJsonObject();
        if (obj.has("type") && obj.get("type").isJsonPrimitive()
            && "minecraft:tag".equals(obj.get("type").getAsString())
            && !(obj.has("expand") && obj.get("expand").getAsBoolean())) {
            offenders.add(path + " -> " + (obj.has("name") ? obj.get("name").getAsString() : "?"));
        }
        for (var entry : obj.entrySet()) {
            scanForTagEntries(entry.getValue(), path, offenders);
        }
    }

    /** One bundled JSON as text, or null. */
    private static String readResource(String path) {
        try (java.io.InputStream in = SortingDataTests.class.getResourceAsStream(path)) {
            return in == null ? null
                : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException failed) {
            return null;
        }
    }
}
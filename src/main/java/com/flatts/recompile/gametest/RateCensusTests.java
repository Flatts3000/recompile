package com.flatts.recompile.gametest;

import com.flatts.recompile.compat.SortingData;
import com.flatts.recompile.content.block.SortableBlock;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Roll the real pull streams a quarter of a million times each, and check what the mod <b>claims</b>
 * against what the game <b>produces</b>.
 *
 * <p><b>Why this exists.</b> Every drop rate in this mod is quoted from a number nobody ever asked the
 * game for. Three separate pieces of code read the same loot JSON and work out odds from it -
 * {@link SortingData} (which is what JEI shows the player), {@code FindRateTest} (which is what the
 * balance targets are asserted against), and {@code tools/analyse_pulls.py} (which is what a playtest
 * gets compared to). All three parse weights out of the same files and none of them has ever been
 * checked against the loot system that actually rolls them. Three models, one source, zero
 * measurements.
 *
 * <p>They agree with each other today because the tables are simple: every pool is {@code "rolls": 1}
 * with no conditions, so weight-over-total is the whole story. <b>Nothing enforces that.</b> A pool
 * given {@code "rolls": 2}, or a {@code minecraft:random_chance} condition, or an entry shape
 * {@code collect} does not recognise, changes what the game produces and changes nothing any of the
 * three models compute. The failure is silent in the direction that matters: the mod would tell the
 * player one number in JEI, assert a second in CI, and hand them a third in the world.
 *
 * <p>So this measures. {@code ROLLS} pulls per stream through
 * {@code reloadableRegistries().getLootTable(...)} - the same call {@code SortableBlock.sort} makes -
 * tallied per item and compared to {@link SortingData}'s prediction.
 *
 * <p><b>What it can and cannot settle.</b> It settles the model: if predicted and observed agree
 * across the common items, the arithmetic is sound and the rare tail can be trusted to the same
 * arithmetic without needing to be sampled (a 1-in-480,000 collectible would need tens of millions of
 * rolls to measure directly, and would only confirm the division). It settles nothing about
 * <b>throughput</b> - how many pulls an hour a person actually makes is a fact about people, and the
 * only instrument for it is {@code RCAnalytics} plus somebody playing.
 *
 * <p>The census is also written to {@code logs/recompile-rates.tsv} so
 * {@code tools/analyse_pulls.py --rates} can turn it into minutes-of-play per find without anyone
 * having to play first.
 */
final class RateCensusTests {

    /**
     * Rolls per stream.
     *
     * <p>Sized so the assertion has teeth rather than by feel: an item at one pull in 2,500 (a bucket)
     * lands about 100 hits here, and 100 expected hits is where a doubled or halved rate stands more
     * than ten standard deviations clear of the tolerance below. Smaller samples cannot tell a real 2x
     * error from an unlucky afternoon.
     */
    private static final int ROLLS = 250_000;

    /**
     * Only assert on items the sample can actually carry an assertion about.
     *
     * <p>Below this the interval is wider than the errors worth catching, so an assertion would be
     * noise in both directions - flaky when it passes and meaningless when it fails. Rarer items are
     * still counted and still written to the census file; they are simply reported rather than judged.
     */
    private static final double MIN_EXPECTED_HITS = 100.0;

    /**
     * Tolerance, in standard deviations of the Poisson count.
     *
     * <p>Five, deliberately wide. This runs on every CI push and a test that fails once a month for
     * being unlucky teaches people to re-run it, which is worse than not having it. At five sigma a
     * false failure is a one-in-3.5-million event per item, while a 2x model error is still caught by
     * a mile.
     */
    private static final double SIGMA = 5.0;

    private RateCensusTests() {
    }

    static void register() {
        RCGameTests.test("pull_rates_match_what_the_mod_claims", 400, helper -> {
            ServerLevel level = helper.getLevel();
            Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));
            LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, origin)
                .create(LootContextParamSets.CHEST);

            List<String> problems = new ArrayList<>();
            List<String> census = new ArrayList<>();

            for (Map.Entry<ResourceKey<LootTable>, String> stream : streams().entrySet()) {
                LootTable table = level.getServer().reloadableRegistries().getLootTable(stream.getKey());
                helper.assertTrue(table != LootTable.EMPTY,
                    stream.getKey().identifier() + " did not load, so this measured an empty table and "
                        + "would have passed by finding exactly the nothing it predicted");

                Map<String, Integer> observed = roll(table, params);
                Map<String, Double> predicted = predict(stream.getValue(), level);

                check(stream.getKey().identifier(), observed, predicted, problems);
                report(stream.getKey().identifier(), observed, predicted, census);
            }

            write(level, census);

            helper.assertTrue(problems.isEmpty(),
                "the game does not produce what the mod says it produces, over " + ROLLS
                    + " rolls per stream:\n  " + String.join("\n  ", problems)
                    + "\n\nSortingData is the model JEI shows the player and the one FindRateTest's "
                    + "targets are measured in. A disagreement here means the odds in the guidebook, "
                    + "the odds in JEI and the odds in the world are three different numbers.");
            helper.succeed();
        });
    }

    /**
     * Every pull stream, derived from the block registry rather than listed.
     *
     * <p>The same reason {@link SortingData#sortingSources} derives its list: a hand-written one here
     * would measure four streams and quietly not measure the fifth somebody added, which is precisely
     * the shape of gap this class exists to close.
     */
    private static Map<ResourceKey<LootTable>, String> streams() {
        Map<ResourceKey<LootTable>, String> out = new LinkedHashMap<>();
        Set<ResourceKey<LootTable>> seen = new LinkedHashSet<>();
        for (SortingData.SortingSource source : SortingData.sortingSources()) {
            ResourceKey<LootTable> key = SortableBlock.pullTableOf(source.block());
            if (key != null && seen.add(key)) {
                out.put(key, source.path());
            }
        }
        return out;
    }

    /**
     * Roll the table and count STACKS, not items.
     *
     * <p>A stack is what one entry winning produces; how many items are in it is a {@code set_count}
     * function on top. Counting items instead makes scrap metal (which rolls 1-2) read as fifty
     * percent commoner than its weight, and that is a defect in the counter rather than a finding
     * about the table.
     */
    private static Map<String, Integer> roll(LootTable table, LootParams params) {
        Map<String, Integer> counts = new TreeMap<>();
        for (int i = 0; i < ROLLS; i++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                if (!stack.isEmpty()) {
                    counts.merge(id(stack.getItem()), 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    /** What {@link SortingData} says the same table should produce, per roll, per item. */
    private static Map<String, Double> predict(String path, ServerLevel level) {
        Map<String, Double> chances = new TreeMap<>();
        for (SortingData.Weighted weighted : SortingData.outputs(path, level.registryAccess())) {
            // Summed rather than assigned: an item can be an entry in more than one pool, and each
            // pool is an independent roll, so the expected number of stacks is the sum of the shares.
            chances.merge(id(weighted.stack().getItem()), (double) weighted.chance(), Double::sum);
        }
        return chances;
    }

    private static void check(Identifier stream, Map<String, Integer> observed,
            Map<String, Double> predicted, List<String> problems) {
        for (Map.Entry<String, Double> entry : predicted.entrySet()) {
            double expected = entry.getValue() * ROLLS;
            if (expected < MIN_EXPECTED_HITS) {
                continue;
            }
            int seen = observed.getOrDefault(entry.getKey(), 0);
            double tolerance = SIGMA * Math.sqrt(expected);
            if (Math.abs(seen - expected) > tolerance) {
                problems.add(String.format(
                    "%s: %s predicted 1 in %.0f (%.0f of %d rolls) but the game gave %d - %.1fx",
                    stream, entry.getKey(), 1.0 / entry.getValue(), expected, ROLLS, seen,
                    seen / expected));
            }
        }

        // The other direction, and the one a weights-only model cannot catch: something came out that
        // nothing predicted. That is what an unrecognised entry shape looks like from the outside -
        // SortingData skips what it does not understand, so the item is simply absent from JEI and
        // from every rate quoted anywhere, while the world hands it to players all day.
        for (String item : observed.keySet()) {
            if (!predicted.containsKey(item)) {
                problems.add(stream + ": the game produced " + item + " (" + observed.get(item)
                    + " times) and the model does not know it exists at all");
            }
        }
    }

    private static void report(Identifier stream, Map<String, Integer> observed,
            Map<String, Double> predicted, List<String> census) {
        Set<String> items = new TreeSet<>(observed.keySet());
        items.addAll(predicted.keySet());
        for (String item : items) {
            int seen = observed.getOrDefault(item, 0);
            double chance = predicted.getOrDefault(item, 0.0);
            census.add(stream + "\t" + item + "\t" + seen + "\t" + ROLLS + "\t"
                + String.format("%.9f", chance));
        }
    }

    /**
     * Write the census where the analysis tool looks.
     *
     * <p>Truncated rather than appended, unlike the pull log: this is a measurement of the tables as
     * they are right now, and two runs' worth interleaved would silently average a change with the
     * thing it changed.
     */
    private static void write(ServerLevel level, List<String> census) {
        try {
            Path logs = level.getServer().getServerDirectory().resolve("logs");
            Files.createDirectories(logs);
            List<String> lines = new ArrayList<>();
            lines.add("stream\titem\tseen\trolls\tpredicted_per_roll");
            lines.addAll(census);
            Files.write(logs.resolve("recompile-rates.tsv"), lines, StandardCharsets.UTF_8);
        } catch (IOException failed) {
            // The assertions above already ran. Losing the report is not worth failing a build over.
            com.flatts.recompile.Recompile.LOGGER.warn("could not write the rate census", failed);
        }
    }

    private static String id(Item item) {
        return String.valueOf(BuiltInRegistries.ITEM.getKey(item));
    }
}

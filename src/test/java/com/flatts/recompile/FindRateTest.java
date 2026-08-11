package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.recompile.content.worldgen.MoundFeature;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How often a player actually meets a thing, in the unit they meet it in: <b>playtime</b>.
 *
 * <p><b>Every drop rate in this mod was written as "one pull in N", and nobody ever converted that
 * into anything a person experiences.</b> It reads as rare. It was not. The 2026-08-11 playtest
 * reported nine buckets, three pairs of shears, nine leads and three collectibles in a
 * <b>fifteen-minute session</b>.
 *
 * <p><b>The unit is pulls, and the trap is that it is not mounds.</b> Clearing a mound takes a couple
 * of minutes because clearing is shovelling; <i>sorting</i> one is 712 right-clicks, since an average
 * mound is about 322 blocks, 88 percent of them Blocks of Garbage, at 2.5 pulls each. A player does
 * some of both, so how many mounds an hour they clear says nothing about how many pulls they make.
 * Rates key off pulls, so pulls is what the targets are stated against here.
 *
 * <p>What shipped, measured against those targets:
 *
 * <ul>
 *   <li>Buckets arrived about every two minutes, for a tool you need exactly one of.
 *   <li>Roaches were two and a half a mound - an interrupted pull every time you quarried a third of
 *       one.
 *   <li>Shears could not be fixed by lowering their weight at all: 1 is the floor, and in a pool
 *       totalling a few hundred that is still about one a mound. Hence the durable-goods pool.
 * </ul>
 *
 * <p>Pure arithmetic against the shipped constants and the shipped loot JSON. It <b>reads</b> both
 * rather than restating them, which is the only reason it cannot drift the way the comments did.
 */
class FindRateTest {

    /** Garbage is a 2-3 window, which {@code SortableCrumbleTest} pins at exactly 2.5 pulls. */
    private static final double PULLS_PER_GARBAGE_BLOCK = 2.5;

    /**
     * Pulls an hour, calibrated from a real session rather than guessed.
     *
     * <p>Derived rather than eyeballed, because an eyeballed one was wrong by two and a half times.
     * A held right-click repeats every 4 ticks, so sorting runs at <b>5 pulls a second</b>, and the
     * 2026-08-11 playtest put sorting at about a quarter of playtime. That is 4,500 pulls an hour.
     *
     * <p>The cross-check that says the model is sound: a junk shovel is stone-tier against a 0.6
     * hardness block, so 4.5 ticks a block, so a 322-block mound flattens in about 1.2 minutes - and
     * the owner measured two and called it slow. Note that flattening a mound is roughly HALF the time
     * of sorting one (712 pulls is 2.4 minutes), which is why mounds-per-hour is not the unit: it
     * measures the cheap half.
     */
    private static final double PULLS_PER_HOUR = 4500.0;

    /** Tools you need exactly one of. Owner target 2026-08-11: about one every half hour, each. */
    private static final Set<String> TOOLS = Set.of(
        "minecraft:bucket", "minecraft:shears", "minecraft:flint_and_steel", "minecraft:lead");

    private static final double TOOL_TARGET_MINUTES = 30.0;
    private static final double TOOL_TOLERANCE_MINUTES = 12.0;

    /**
     * Trophies. Owner target 2026-08-11: <b>all of them in about forty hours</b>.
     *
     * <p>"All of them" is the four whole objects plus a Puzzle Cube, and a cube is nine pieces - so the
     * piece is checked against the nine rather than against one.
     */
    private static final Set<String> COLLECTIBLES = Set.of(
        "recompile:avocado", "recompile:present", "recompile:gold_coin", "recompile:toy_car");

    private static final String CUBE_PIECE = "recompile:puzzle_cube_piece";
    private static final int PIECES_PER_CUBE = 9;

    /**
     * What v0.8.0 shipped: one pull in 4,000 for a whole object, one in 1,000 for a cube piece.
     *
     * <p>Kept because the owner's instruction was a <b>ratio against these</b> rather than a playtime,
     * so this is the number the target is actually relative to.
     */
    private static final double SHIPPED_OBJECT_PULLS = 4000.0;
    private static final double SHIPPED_PIECE_PULLS = 1000.0;

    /**
     * Owner instruction 2026-08-11: <b>collectibles should be 480 times rarer than they were</b>.
     *
     * <p><b>Settled at 120 after the arithmetic was put in front of the owner.</b> The first figure
     * given was 480, which works out at about 890 hours for the set of four and sat twenty-two times
     * away from the "all collectibles in about forty hours" target given minutes earlier. Shown both
     * numbers, the owner chose 120: roughly 107 hours for a whole object, 222 for the set, and 240 for
     * a Puzzle Cube. Still a long-tail trophy, and deliberately nowhere near forty - the forty-hour
     * figure is superseded, not approximated.
     */
    private static final double RARER_THAN_SHIPPED = 120.0;
    private static final double RATIO_TOLERANCE = 0.15;

    // ---------------- the mound, from the feature's own maths ----------------

    /**
     * Blocks of Garbage in an average mound.
     *
     * <p>Mirrors {@code MoundFeature.pickBlock}: the surface is a Trash Bag some of the time, and the
     * lower half of the core rolls Bulky Waste or a Compacted Bale. Only what is left is garbage, and
     * only garbage feeds {@code household_pulls} - assuming a mound is all garbage overstates it by
     * about twelve percent.
     */
    private static double averageGarbageBlocksPerMound() {
        double total = 0.0;
        int cases = 0;
        for (int width = MoundFeature.MIN_WIDTH; width <= MoundFeature.MAX_WIDTH; width++) {
            for (int height = MoundFeature.MIN_HEIGHT; height <= MoundFeature.MAX_HEIGHT; height++) {
                total += garbageIn(width, height);
                cases++;
            }
        }
        return total / cases;
    }

    private static double garbageIn(int width, int height) {
        double radius = width / 2.0;
        int r = (int) Math.floor(radius);
        double garbage = 0.0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.hypot(dx, dz);
                if (dist > radius) {
                    continue;
                }
                int column = (int) Math.round(height * (1.0 - dist / radius));
                boolean core = dist < radius * 0.4;
                for (int dy = 0; dy <= column; dy++) {
                    double notABag = 1.0 - (dy == column ? MoundFeature.SURFACE_BAG_CHANCE : 0.0);
                    garbage += (core && dy <= column * 0.5)
                        ? notABag * (1.0 - (MoundFeature.CORE_BULKY_WASTE_CHANCE
                            + MoundFeature.CORE_BALE_CHANCE))
                        : notABag;
                }
            }
        }
        return garbage;
    }

    private static double pullsPerMound() {
        return averageGarbageBlocksPerMound() * PULLS_PER_GARBAGE_BLOCK;
    }

    // ---------------- the loot table, from the shipped JSON ----------------

    /** Pulls needed on average for one of each item household sorting can yield. */
    private static Map<String, Double> pullsPerDrop() throws IOException {
        JsonObject table;
        try (InputStream in = FindRateTest.class.getResourceAsStream(
                "/data/recompile/loot_table/gameplay/household_pulls.json")) {
            assertTrue(in != null, "household_pulls.json is not on the classpath");
            table = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getAsJsonObject();
        }
        Map<String, Double> chancePerPull = new LinkedHashMap<>();
        for (JsonElement poolElement : table.getAsJsonArray("pools")) {
            JsonObject pool = poolElement.getAsJsonObject();
            double total = 0.0;
            for (JsonElement e : pool.getAsJsonArray("entries")) {
                total += weight(e.getAsJsonObject());
            }
            for (JsonElement e : pool.getAsJsonArray("entries")) {
                JsonObject entry = e.getAsJsonObject();
                if (!entry.has("name")) {
                    continue;   // the empty filler that makes a rare pool rare
                }
                chancePerPull.merge(entry.get("name").getAsString(),
                    weight(entry) / total, Double::sum);
            }
        }
        Map<String, Double> out = new LinkedHashMap<>();
        chancePerPull.forEach((item, chance) -> out.put(item, 1.0 / chance));
        return out;
    }

    private static double weight(JsonObject entry) {
        return entry.has("weight") ? entry.get("weight").getAsDouble() : 1.0;
    }

    private static double minutesFor(double pulls) {
        return pulls / PULLS_PER_HOUR * 60.0;
    }

    // ---------------- the assertions ----------------

    @Test
    @DisplayName("sorting a mound is about seven hundred pulls, however fast you can shovel one flat")
    void sortingAMoundIsSevenHundredPulls() {
        double pulls = pullsPerMound();
        assertTrue(pulls > 500 && pulls < 900,
            "sorting a mound should work out at roughly 700 pulls, got " + Math.round(pulls)
                + ". If the mound dimensions or its block mix changed, the rates below need "
                + "re-deriving - which is why this reads MoundFeature instead of a constant");
    }

    @Test
    @DisplayName("a tool you need one of turns up about once every half hour")
    void toolsArriveAboutHalfHourly() throws IOException {
        Map<String, Double> pulls = pullsPerDrop();
        List<String> wrong = new ArrayList<>();
        for (String item : TOOLS) {
            Double needed = pulls.get(item);
            assertTrue(needed != null, item + " has no source at all, which is worse than too many");
            double minutes = minutesFor(needed);
            if (Math.abs(minutes - TOOL_TARGET_MINUTES) > TOOL_TOLERANCE_MINUTES) {
                wrong.add(String.format("%s every %.0f min", item, minutes));
            }
        }
        assertTrue(wrong.isEmpty(),
            "tools should arrive about every " + (int) TOOL_TARGET_MINUTES + " minutes (owner, "
                + "2026-08-11): " + wrong + ". Too common and finding one stops meaning anything - "
                + "buckets shipped at one every two minutes. Too rare and the water tier stalls, "
                + "since a bucket is the only way to move water without a fluid mod");
    }

    @Test
    @DisplayName("collectibles are 120 times rarer than v0.8.0 shipped them")
    void collectiblesAre120TimesRarer() throws IOException {
        Map<String, Double> pulls = pullsPerDrop();
        List<String> wrong = new ArrayList<>();

        for (String item : COLLECTIBLES) {
            Double needed = pulls.get(item);
            assertTrue(needed != null, item + " has no source at all");
            double ratio = needed / SHIPPED_OBJECT_PULLS;
            if (Math.abs(ratio - RARER_THAN_SHIPPED) / RARER_THAN_SHIPPED > RATIO_TOLERANCE) {
                wrong.add(String.format("%s is %.0fx rarer, wanted %.0fx (that is %.0f h each)",
                    item, ratio, RARER_THAN_SHIPPED, needed / PULLS_PER_HOUR));
            }
        }

        Double piece = pulls.get(CUBE_PIECE);
        assertTrue(piece != null, CUBE_PIECE + " has no source at all");
        double pieceRatio = piece / SHIPPED_PIECE_PULLS;
        if (Math.abs(pieceRatio - RARER_THAN_SHIPPED) / RARER_THAN_SHIPPED > RATIO_TOLERANCE) {
            wrong.add(String.format("a cube piece is %.0fx rarer, wanted %.0fx (a whole cube is %.0f h)",
                pieceRatio, RARER_THAN_SHIPPED, piece * PIECES_PER_CUBE / PULLS_PER_HOUR));
        }

        assertTrue(wrong.isEmpty(),
            "collectibles should be " + (int) RARER_THAN_SHIPPED + "x rarer than v0.8.0 shipped them "
                + "(owner, 2026-08-11): " + wrong + ". The playtest that set this was getting three "
                + "in fifteen minutes");
    }

    @Test
    @DisplayName("bulk material is still bulk")
    void materialsStayCommon() throws IOException {
        Map<String, Double> pulls = pullsPerDrop();
        Double junk = pulls.get("recompile:junk");
        assertTrue(junk != null && junk < 4.0,
            "junk should come out of better than one pull in four - this file is about rare things "
                + "being rare, and it would be easy to fix that by making everything rare");
    }
}

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
                    // SURFACE_NON_GARBAGE, not SURFACE_BAG_CHANCE: the surface holds bags AND
                    // cardboard piles now, and reading only the bag share would count every
                    // cardboard cell as garbage and report every household find as commoner
                    // than it is. MoundFeature owns the sum so this cannot drift again.
                    double notLitter = 1.0
                        - (dy == column ? MoundFeature.SURFACE_NON_GARBAGE : 0.0);
                    garbage += (core && dy <= column * 0.5)
                        ? notLitter * (1.0 - (MoundFeature.CORE_BULKY_WASTE_CHANCE
                            + MoundFeature.CORE_BALE_CHANCE))
                        : notLitter;
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
        return pullsPerDrop("household_pulls");
    }

    private static Map<String, Double> pullsPerDrop(String stream) throws IOException {
        JsonObject table;
        try (InputStream in = FindRateTest.class.getResourceAsStream(
                "/data/recompile/loot_table/gameplay/" + stream + ".json")) {
            assertTrue(in != null, stream + ".json is not on the classpath");
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
    @DisplayName("both pull streams price a collectible the same")
    void bothStreamsAgreeOnCollectibles() throws IOException {
        // Collectibles are declared TWICE - once in household_pulls, once in bag_pulls - and a player
        // meets them through whichever they happen to be sorting. Because both carry the same
        // denominator the combined rate is that denominator regardless of the mix, which is the only
        // reason a single number can be quoted in hours at all. Two files that must agree is exactly
        // the shape that drifts, and every rate in this file is measured against the household one.
        Map<String, Double> household = pullsPerDrop("household_pulls");
        Map<String, Double> bag = pullsPerDrop("bag_pulls");

        List<String> disagree = new ArrayList<>();
        List<String> checked = new ArrayList<>(COLLECTIBLES);
        checked.add(CUBE_PIECE);
        for (String item : checked) {
            Double one = household.get(item);
            Double other = bag.get(item);
            assertTrue(one != null, item + " is missing from household_pulls");
            assertTrue(other != null, item + " is missing from bag_pulls");
            if (Math.abs(one - other) / one > 0.01) {
                disagree.add(String.format("%s is 1/%.0f in household but 1/%.0f in bags",
                    item, one, other));
            }
        }
        assertTrue(disagree.isEmpty(),
            "the two streams price the same collectible differently, so how often you find one "
                + "depends on which pile you happen to be picking through: " + disagree);
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

    @Test
    @DisplayName("a roach costs about one pull per mound, which is what the config claims it does")
    void roachesAreAboutOnePerMound() {
        // THE COMMENT ON THIS NUMBER PROMISED A TEST THAT DID NOT EXIST. RCConfig says "RoachRateTest
        // holds that arithmetic so a future retune cannot drift from its own stated intent the way
        // this one did" - and no RoachRateTest was ever written, in this or any other file. The drift
        // it was meant to prevent is exactly the drift that produced it: the rate was set as "one per
        // 128 blocks", which is two and a half roaches per mound, because nobody converted the unit.
        //
        // It belongs here rather than in a file of its own, because the conversion needs
        // pullsPerMound() and that is what this class already computes from MoundFeature.
        double perMound = pullsPerMound() / RCConfig.ROACH_CHANCE_DENOMINATOR.getDefault();
        assertTrue(perMound > 0.4 && perMound < 2.0,
            "the roach rate is meant to work out at about one per mound (owner, 2026-08-11), and this "
                + "denominator gives " + String.format("%.2f", perMound) + ". Both edges of that band "
                + "are a real failure: many per mound is an encounter every time you quarry a third "
                + "of one, each costing a pull, which is the 320 this was retuned away from; far "
                + "below one and roaches stop being the earliest renewable food, which is the job "
                + "they are actually doing");
    }

    /** Surface cells in one mound of this size - one per column, which is where litter goes. */
    private static double surfaceCellsIn(int width) {
        double radius = width / 2.0;
        int r = (int) Math.floor(radius);
        double cells = 0.0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (Math.hypot(dx, dz) <= radius) {
                    cells += 1.0;
                }
            }
        }
        return cells;
    }

    private static double averageCardboardPilesPerMound() {
        double total = 0.0;
        int cases = 0;
        for (int width = MoundFeature.MIN_WIDTH; width <= MoundFeature.MAX_WIDTH; width++) {
            // Height does not change how many COLUMNS a mound has, only how tall they are, and litter
            // sits on the top of each column - so the surface count is a function of width alone.
            total += surfaceCellsIn(width) * MoundFeature.SURFACE_CARDBOARD_CHANCE;
            cases++;
        }
        return total / cases;
    }

    @Test
    @DisplayName("cardboard is early because there is cardboard on every mound, not because of a recipe")
    void cardboardIsActuallyEarly() {
        // WHAT MAKES THE FAMILY WORK IS A NUMBER IN WORLDGEN, which is the whole reason this test
        // exists. #309's building family is meant to be the one a new player can use immediately.
        // Nothing in the recipes or the tags enforces that; it is enforced by there being piles of
        // boxes lying on the mound in front of them. Set SURFACE_CARDBOARD_CHANCE to 0.005 and every
        // other test in the repo still passes while the family quietly becomes late-game.
        double piles = averageCardboardPilesPerMound();
        assertTrue(piles > 3.0,
            "an average mound carries " + String.format("%.1f", piles) + " cardboard piles, which is "
                + "not enough for a player to meet cardboard before they have crafted anything - and "
                + "that is the only thing making this building family the early one");

        // AND IT MUST NOT EAT THE DUMP. Surface litter takes the cells garbage blocks would have had,
        // so every pile is a Block of Garbage that is not there, and every household rate this file
        // measures is quoted per pull of those. Same dial, other edge.
        assertTrue(MoundFeature.SURFACE_NON_GARBAGE < 0.5F,
            "surface litter is " + String.format("%.0f%%", MoundFeature.SURFACE_NON_GARBAGE * 100)
                + " of every mound's outer shell. Past about half these stop reading as heaps of "
                + "garbage with litter on them and start reading as heaps of litter, and every "
                + "household find rate in this file moves with it");

        // ONE PILE IS ABOUT ONE BLOCK, the rate that makes clearing the boxes off a mound feel like
        // it built you something: 2-3 pulls (2.5 average), about 77 percent of the stream cardboard,
        // 1-3 of it per pull, four to a Cardboard Block.
        double cardboardPerPile = 2.5 * (200.0 / 260.0) * 2.0;
        assertTrue(cardboardPerPile > 3.0 && cardboardPerPile < 6.0,
            "a pile yields " + String.format("%.1f", cardboardPerPile) + " cardboard against the four "
                + "a Cardboard Block costs. Far under and the piles are litter you walk past; far "
                + "over and one pile is a stack of walls");
    }
}

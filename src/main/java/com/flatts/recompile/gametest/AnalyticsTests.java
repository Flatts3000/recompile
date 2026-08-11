package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.event.RCAnalytics;
import com.flatts.recompile.registry.RCBlocks;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * The sorting log records what the analysis tool expects to read.
 *
 * <p>This is a pipe with two ends written days apart, and the failure mode is that it looks fine
 * from both: the mod writes a file nobody reads wrong, and the tool parses a shape the mod does not
 * produce. Neither end notices, and the first symptom is a rate computed from nothing.
 *
 * <p>So this drives real pulls through the real block and asserts the file that comes out is the
 * one {@code tools/analyse_pulls.py} parses - five tab-separated columns, a PULL per pull, and the
 * block id in the source column, since that is what the per-stream breakdown keys on.
 */
final class AnalyticsTests {

    private static final BlockPos SPOT = new BlockPos(1, 1, 1);

    private AnalyticsTests() {
    }

    static void register() {
        RCGameTests.test("sorting_is_recorded_in_the_shape_the_tool_reads", 40, helper -> {
            if (!RCAnalytics.recording()) {
                // Off by config, or the file could not be opened. Either way there is nothing to
                // assert, and failing here would turn a setting into a broken build.
                helper.succeed();
                return;
            }
            Path file = RCAnalytics.fileFor(helper.getLevel().getServer());
            long before = countLines(helper, file);

            helper.setBlock(SPOT, RCBlocks.GARBAGE_BLOCK.get());
            for (int i = 0; i < 3 && helper.getLevel()
                    .getBlockState(helper.absolutePos(SPOT)).getBlock()
                        instanceof SortableBlock; i++) {
                SortableBlock.sortOnce(helper.getLevel(), helper.absolutePos(SPOT));
            }
            RCAnalytics.flushForTest();

            // ONE read, not two. This used to call readLines twice in the same expression - once for
            // the list to slice and once for the end index - and the whole suite shares this file, so
            // another test appending between the two calls made the index outrun the list and threw
            // IndexOutOfBounds. Exactly the interleaving the comment below is about, in the code that
            // was written to cope with it.
            List<String> all = readLines(helper, file);
            helper.assertTrue(all.size() >= before,
                "the log shrank mid-test (" + before + " lines before, " + all.size() + " now), so "
                    + "something truncated a file this test only ever appends to");
            List<String> added = new ArrayList<>(all.subList((int) before, all.size()));
            helper.assertTrue(!added.isEmpty(),
                "sorting a garbage block wrote nothing to " + file + " - the log is a pipe with "
                    + "two ends, and this is the end that fills it");

            // Every line's SHAPE is checked, but only this block's lines are claimed as ours. The
            // whole suite shares one server and one file, so other tests' sorting interleaves here -
            // the first version of this assumed the log was its own and failed on a Mechanical Waste
            // line another test wrote a millisecond earlier.
            boolean sawOurPull = false;
            for (String line : added) {
                String[] cols = line.split("\t", -1);
                helper.assertTrue(cols.length == 5,
                    "analyse_pulls.py splits on tabs and expects 5 columns, got "
                        + cols.length + ": " + line);
                try {
                    Long.parseLong(cols[0]);
                    Integer.parseInt(cols[4]);
                } catch (NumberFormatException bad) {
                    helper.fail("column 1 must be epoch millis and column 5 a count: " + line);
                }
                if (cols[1].equals("PULL") && cols[2].equals("recompile:garbage_block")) {
                    sawOurPull = true;
                }
            }
            helper.assertTrue(sawOurPull,
                "three sorts of a garbage block produced no PULL line for it. The source column is "
                    + "what the tool keys its per-stream breakdown on, so a wrong id there is a "
                    + "silently empty report: " + added);
            helper.succeed();
        });
    }

    private static List<String> readLines(net.minecraft.gametest.framework.GameTestHelper helper,
            Path file) {
        try {
            return Files.exists(file) ? Files.readAllLines(file, StandardCharsets.UTF_8)
                                      : List.of();
        } catch (Exception failed) {
            helper.fail("could not read " + file + ": " + failed);
            return List.of();
        }
    }

    private static long countLines(net.minecraft.gametest.framework.GameTestHelper helper,
            Path file) {
        return readLines(helper, file).size();
    }
}

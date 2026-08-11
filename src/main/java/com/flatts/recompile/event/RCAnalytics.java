package com.flatts.recompile.event;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.Recompile;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * A local log of what sorting actually produces, so drop rates can be measured instead of argued.
 *
 * <p><b>Why this exists.</b> Every rate in this mod was tuned against an estimate of how many pulls an
 * hour a player makes, and that estimate was wrong twice on the day it was written - once by taking a
 * session's barrel contents as the whole story, once by confusing how fast a mound can be
 * <i>shovelled flat</i> with how long it takes to <i>pick through</i>. Both were reasonable and both
 * were out by more than a factor of two. A weight is only as good as the number of pulls you think it
 * is being rolled against, so that number should be counted rather than derived.
 *
 * <p><b>Local file, no network, ever.</b> This writes one tab-separated line per event into
 * {@code logs/recompile-pulls.tsv} in the game directory and does nothing else. There is no server, no
 * upload, no identifier; it is a text file the player can read, delete, or turn off. Saying so
 * explicitly because a class called "analytics" in a mod usually means the other thing.
 *
 * <p><b>The format is deliberately dumb.</b> Append-only TSV, one event per line, no header rewriting
 * and no in-place edits, so a crash costs at most the last unflushed handful of lines and the file is
 * still readable. {@code tools/analyse_pulls.py} turns it into rates.
 *
 * <p>The columns are {@code epochMillis}, {@code event}, {@code source}, {@code detail},
 * {@code count}. What {@code source} and {@code detail} mean depends on the event:
 *
 * <ul>
 *   <li>{@code PULL} - the sortable block picked through, and the item it yielded.
 *   <li>{@code ROACH} - the sortable that released one. A roach replaces a pull, so it is NOT
 *       also logged as a PULL; counting both would overstate throughput.
 *   <li>{@code CRUMBLE} - a sortable spent and destroyed by picking. Pulls divided by crumbles is the
 *       real average yield per block, which is the number the whole "per mound" conversion rests on.
 *   <li>{@code BREAK} - a sortable mined rather than sorted. The clearing-versus-sorting split, which
 *       is exactly what the two bad estimates disagreed about.
 *   <li>{@code SESSION} - the world opening and closing, so wall-clock time is on the same timeline
 *       as the events and pulls-per-hour needs no assumption at all.
 * </ul>
 */
@EventBusSubscriber(modid = Recompile.MOD_ID)
public final class RCAnalytics {

    private static final String FILE = "recompile-pulls.tsv";
    /** Flush after this many lines. Small enough to survive a crash, large enough not to thrash. */
    private static final int FLUSH_EVERY = 64;

    private static BufferedWriter writer;
    private static int sinceFlush;

    private RCAnalytics() {
    }

    private static boolean enabled() {
        return RCConfig.ANALYTICS_ENABLED.get();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!enabled()) {
            return;
        }
        try {
            Path logs = event.getServer().getServerDirectory().resolve("logs");
            Files.createDirectories(logs);
            Path file = logs.resolve(FILE);
            boolean fresh = !Files.exists(file);
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (fresh) {
                writer.write("epoch_millis\tevent\tsource\tdetail\tcount");
                writer.newLine();
            }
            record("SESSION", "-", "start", 1);
            Recompile.LOGGER.info("pull analytics writing to {}", file);
        } catch (IOException failed) {
            // Never let bookkeeping take the server down. A missing log is a missing measurement.
            Recompile.LOGGER.warn("pull analytics could not open its file; carrying on without it",
                failed);
            writer = null;
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        record("SESSION", "-", "stop", 1);
        close();
    }

    /**
     * A sortable mined rather than picked through.
     *
     * <p>Its own listener rather than a line inside the torch-fuel handler, because the two have
     * nothing to do with each other and a measurement that lives inside an unrelated feature is a
     * measurement that disappears when that feature is refactored.
     *
     * <p>This is the number the two bad estimates actually disagreed about: shovelling a mound flat
     * and picking through it are different activities at different speeds, and only one of them rolls
     * the loot table. Counting both is what lets the split be read off rather than assumed.
     */
    @SubscribeEvent
    public static void onBlockBreak(net.neoforged.neoforge.event.level.block.BreakBlockEvent event) {
        if (writer == null || !enabled()) {
            return;
        }
        if (event.getState().getBlock()
                instanceof com.flatts.recompile.content.block.SortableBlock sortable) {
            broke(sortable);
        }
    }

    /** One pull, and what it produced. Called from the single place a pull can happen. */
    public static void pull(ServerLevel level, Block source, List<ItemStack> yielded) {
        if (writer == null || !enabled()) {
            return;
        }
        // ONE LINE PER PULL, with the whole yield in the detail column as "item*n;item*n".
        //
        // The first version wrote a line per item stack and left the reader to collapse them by
        // timestamp. That is wrong at speed: sorting runs at five pulls a second and a gametest goes
        // far faster, so separate pulls share a millisecond and merge into one. It read 131 pulls out
        // of 380 lines and reported 14.6 pulls per block against a curve that says 2.5. A pull is the
        // unit every rate is measured in, so it gets its own line and is never inferred.
        StringBuilder detail = new StringBuilder();
        for (ItemStack stack : yielded) {
            if (stack.isEmpty()) {
                continue;
            }
            if (detail.length() > 0) {
                detail.append(';');
            }
            detail.append(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                .append('*').append(stack.getCount());
        }
        // A pull that rolled nothing is still a pull. Leaving it out would make the stream look
        // richer per click than it is.
        record("PULL", id(source), detail.length() == 0 ? "-" : detail.toString(), 1);
    }

    public static void roach(Block source) {
        record("ROACH", id(source), "-", 1);
    }

    public static void crumble(Block source) {
        record("CRUMBLE", id(source), "-", 1);
    }

    public static void broke(Block source) {
        record("BREAK", id(source), "-", 1);
    }

    private static String id(Block block) {
        return String.valueOf(BuiltInRegistries.BLOCK.getKey(block));
    }

    private static synchronized void record(String event, String source, String detail, int count) {
        if (writer == null || !enabled()) {
            return;
        }
        try {
            writer.write(System.currentTimeMillis() + "\t" + event + "\t" + source + "\t"
                + detail + "\t" + count);
            writer.newLine();
            if (++sinceFlush >= FLUSH_EVERY) {
                writer.flush();
                sinceFlush = 0;
            }
        } catch (IOException failed) {
            Recompile.LOGGER.warn("pull analytics write failed; disabling for this session", failed);
            close();
        }
    }

    private static synchronized void close() {
        if (writer == null) {
            return;
        }
        try {
            writer.flush();
            writer.close();
        } catch (IOException ignored) {
            // Shutting down; there is nowhere useful left to report this.
        }
        writer = null;
        sinceFlush = 0;
    }

    /** Test seam: force everything buffered to disk so a test can read the file back. */
    public static synchronized void flushForTest() {
        if (writer == null) {
            return;
        }
        try {
            writer.flush();
            sinceFlush = 0;
        } catch (IOException ignored) {
            // Same as above - the caller is a test and will simply see fewer lines.
        }
    }

    /** Test seam: whether a file is currently open, so a test can tell "off" from "broken". */
    public static synchronized boolean recording() {
        return writer != null;
    }

    /** Where the file lives, for a test and for the analysis tool's error message. */
    public static Path fileFor(MinecraftServer server) {
        return server.getServerDirectory().resolve("logs").resolve(FILE);
    }
}

package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.worldgen.RegionBiomeSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Proves the region system's load-bearing guarantee (demolition_yard_spec.md S1): <b>everything within
 * {@code coreRadius} of world origin is household_sprawl</b>, so standing at spawn the whole hostile
 * spawn range is inside the empty-spawner biome and the player is 100% protected. Also proves the
 * frontier is not merely reachable but <i>common</i> once you have travelled past it.
 *
 * <p>Drives {@link RegionBiomeSource#getNoiseBiome} directly with real biome holders from the server
 * registry - no world placement needed, so it is a pure-logic sweep run inside the gametest harness.
 *
 * <p><b>The parameters are read from the shipped world preset, not written here.</b> They used to be
 * hardcoded, and they had drifted: this file built the source with {@code falloff = 768} while
 * {@code world_preset/garbage.json} shipped {@code 256}. So the test was passing against a gradient no
 * player has ever generated. Reading the JSON means the test cannot disagree with the world again.
 *
 * <p>It also used to assert only that the frontier appeared <i>somewhere</i> in the sweep - a boolean,
 * satisfied by a single quart in a million. A player reported travelling 2000 blocks without finding the
 * demolition yard, which is exactly the failure a boolean cannot see. The share is now asserted per
 * distance band. (That report turned out to be an upgraded world keeping its pre-region biome source
 * from 0.2.0, not a generation bug - but the test could not have told anyone that.)
 */
final class RegionBiomeSourceTests {

    /** Where the real numbers live. Parsed rather than copied, so the two cannot drift apart. */
    private static final String PRESET =
        "/data/recompile/worldgen/world_preset/garbage.json";

    private RegionBiomeSourceTests() {
    }

    private static ResourceKey<Biome> biomeKey(String path) {
        return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, path));
    }

    /** Pull a numeric field out of the preset's biome_source block. */
    private static double presetValue(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*(-?[0-9.]+)").matcher(json);
        if (!m.find()) {
            throw new IllegalStateException("world preset has no '" + field + "' - the region biome "
                + "source config changed shape and this test is reading a world that no longer exists");
        }
        return Double.parseDouble(m.group(1));
    }

    private static String readPreset() {
        try (InputStream in = RegionBiomeSourceTests.class.getResourceAsStream(PRESET)) {
            if (in == null) {
                throw new IllegalStateException("world preset not on the classpath at " + PRESET);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("could not read " + PRESET, e);
        }
    }

    static void register() {
        RCGameTests.test("region_core_is_all_household_and_frontier_appears", 1, helper -> {
            String preset = readPreset();
            int coreRadius = (int) presetValue(preset, "core_radius");
            float falloff = (float) presetValue(preset, "falloff");
            float floor = (float) presetValue(preset, "household_floor");
            double noiseScale = presetValue(preset, "noise_scale");
            int onset = (int) presetValue(preset, "onset");

            var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
            Holder<Biome> household = biomes.getOrThrow(biomeKey("household_sprawl"));
            Holder<Biome> demolition = biomes.getOrThrow(biomeKey("demolition_yard"));

            RegionBiomeSource source = new RegionBiomeSource(household,
                List.of(new RegionBiomeSource.FrontierEntry(demolition, onset)),
                coreRadius, falloff, floor, noiseScale, 2611L);

            // The core guarantee: unconditional in code, asserted here so it stays that way.
            for (int bx = -coreRadius; bx <= coreRadius; bx += 32) {
                for (int bz = -coreRadius; bz <= coreRadius; bz += 32) {
                    double d = Math.sqrt((double) bx * bx + (double) bz * bz);
                    if (d >= coreRadius) {
                        continue;
                    }
                    helper.assertTrue(source.getNoiseBiome(bx >> 2, 0, bz >> 2, null) == household,
                        "inside the safe core (dist " + (int) d + " < " + coreRadius + ") the biome must "
                            + "be household, got frontier at block " + bx + "," + bz);
                }
            }

            // The reachability guarantee, as a share rather than a boolean. A player who has walked past
            // the falloff distance must be finding the yard constantly, not eventually.
            int share = frontierPercent(source, demolition, coreRadius + (int) falloff, 2000);
            helper.assertTrue(share >= 50,
                "past the falloff distance the demolition yard is only " + share + "% of sampled "
                    + "locations - a player travelling out there would struggle to find one, which is "
                    + "exactly the bug report this threshold exists to catch");
            helper.succeed();
        });
    }

    /** Percentage of sampled locations in an annulus that generate as the frontier biome. */
    private static int frontierPercent(RegionBiomeSource source, Holder<Biome> frontier, int lo, int hi) {
        int total = 0;
        int hits = 0;
        for (int bx = -hi; bx <= hi; bx += 16) {
            for (int bz = -hi; bz <= hi; bz += 16) {
                double d = Math.sqrt((double) bx * bx + (double) bz * bz);
                if (d < lo || d >= hi) {
                    continue;
                }
                total++;
                if (source.getNoiseBiome(bx >> 2, 0, bz >> 2, null) == frontier) {
                    hits++;
                }
            }
        }
        return total == 0 ? 0 : hits * 100 / total;
    }
}

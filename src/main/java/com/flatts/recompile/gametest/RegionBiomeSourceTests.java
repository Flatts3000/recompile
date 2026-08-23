package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.worldgen.RegionBiomeSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
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
 *
 * <p><b>And it builds the source from EVERY frontier entry, which is the second thing that had to be
 * paid for.</b> It used to call {@code presetValue(preset, "onset")} - a regex that stops at the first
 * match - and hand the source a one-element list holding the demolition yard. So when the radioactive
 * dump shipped as a second frontier region (#285) <b>the object under test never contained it</b>: this
 * file stayed green while measuring a world with one frontier region in it, and the dump's own tests
 * checked the preset JSON's onset ordering, which is true and says nothing about whether the source
 * ever returns the biome. Every entry is read now, and every entry is sampled from its own onset, so a
 * third region is covered the day it is added to the preset.
 *
 * <p><b>The gap was in the coverage, not in the world.</b> Worth stating plainly, because the first
 * draft of this javadoc claimed the biome "generated nowhere in a real world" on the strength of an
 * RCON probe that found zero hits from five origins. That probe ran against a save created before the
 * region existed, and a generator is baked in at world creation - so it measured a preset that predates
 * the code. A census of a FRESH world puts the dump 1041 blocks out with thousands of blocks of it.
 * The region system has never shipped broken; what shipped was a test that could not have noticed if it
 * had.
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

    /** The preset's biome_source block, parsed. */
    private static JsonObject biomeSource(String json) {
        return JsonParser.parseString(json).getAsJsonObject()
            .getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
            .getAsJsonObject("generator").getAsJsonObject("biome_source");
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

            var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
            Holder<Biome> household = biomes.getOrThrow(biomeKey("household_sprawl"));

            // EVERY frontier entry, in the preset's own order. The order is load-bearing: the pick
            // noise indexes into the eligible sub-list, so shuffling the array changes which region
            // a given coordinate generates.
            List<RegionBiomeSource.FrontierEntry> frontier = new ArrayList<>();
            for (var raw : biomeSource(preset).getAsJsonArray("frontier")) {
                JsonObject entry = raw.getAsJsonObject();
                Identifier id = Identifier.parse(entry.get("biome").getAsString());
                frontier.add(new RegionBiomeSource.FrontierEntry(
                    biomes.getOrThrow(ResourceKey.create(Registries.BIOME, id)),
                    entry.has("onset") ? entry.get("onset").getAsInt() : 0));
            }
            helper.assertTrue(!frontier.isEmpty(),
                "the world preset declares no frontier regions at all, so everything below is vacuous");

            RegionBiomeSource source = new RegionBiomeSource(household, frontier,
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

            // The reachability guarantee, as a share rather than a boolean, FOR EVERY REGION. A player
            // who has walked past a region's onset must be finding it constantly, not eventually.
            //
            // EACH REGION IS SAMPLED FROM ITS OWN ONSET, not from the furthest one. The first version
            // of this took `max(falloff, maxOnset)` as a single floor for every entry, which quietly
            // meant every region was measured over the identical annulus - and the band between the
            // falloff and the LAST region's onset, where the earlier regions are alone and should own
            // almost everything, was sampled by nothing at all. Push the demolition yard's onset out to
            // 1200 and a player would walk 400 blocks of pure household past the falloff, which is the
            // exact bug report the class javadoc cites, and that version stayed green.
            int base = coreRadius + (int) falloff;
            int span = Math.max(base, maxOnset(frontier)) + 1500;
            for (RegionBiomeSource.FrontierEntry entry : frontier) {
                int lo = Math.max(base, entry.onset());
                int eligible = 0;
                for (RegionBiomeSource.FrontierEntry other : frontier) {
                    if (other.onset() <= lo) {
                        eligible++;
                    }
                }

                // WHERE A REGION IS THE ONLY ONE ELIGIBLE IT MUST DOMINATE; ELSEWHERE IT NEED ONLY BE
                // PRESENT. Two thresholds rather than one, because a single "fair share" number
                // cannot be both meaningful and correct.
                //
                // The pick is `(int)(pick * eligible)` over a NormalNoise, which is roughly Gaussian
                // with a deviation near 1/3 - NOT uniform. Two regions split at the mean and come out
                // near 50/50, but three cut at about +-1 sigma (16/68/16) and four at +-1.5 sigma
                // (7/43/43/7). A "100/eligible, near enough" threshold would therefore go RED on a
                // fourth region for a source behaving exactly as built. That skew is a real defect -
                // a region's share depends on its POSITION in the preset array - and it is #290's to
                // fix, not this test's to hide.
                //
                // But in the band where a region is alone, no pick happens at all, so the share is
                // just the frontier probability and a strict threshold IS sound. That band is the one
                // the old bug report was about, and measuring each region from ITS OWN onset is what
                // makes it reachable: taking a single floor across every entry left everything below
                // the LAST region's onset sampled by nothing.
                int hi = span;
                for (RegionBiomeSource.FrontierEntry other : frontier) {
                    if (other.onset() > lo && other.onset() < hi) {
                        hi = other.onset();
                    }
                }
                int alone = frontierPercent(source, entry.biome(), lo, hi);
                int need = eligible == 1 ? 50 : 5;
                helper.assertTrue(alone >= need,
                    "between " + lo + " and " + hi + " blocks out - where " + eligible + " region(s) "
                        + "are eligible - the region "
                        + entry.biome().unwrapKey().map(k -> k.identifier().toString()).orElse("?")
                        + " is only " + alone + "% of sampled locations, against " + need + "% needed. "
                        + "A share of 0 means it is declared in the preset and generates NOWHERE, "
                        + "which is not something the preset JSON can tell you.");
            }

            // And the frontier as a whole still has to dominate out there, which is what the old
            // single-region threshold was really measuring. Asserted on the union so it stays true
            // however many regions there are and however the pick noise divides them between them.
            int frontierShare = 0;
            for (RegionBiomeSource.FrontierEntry entry : frontier) {
                frontierShare += frontierPercent(source, entry.biome(), base, span);
            }
            helper.assertTrue(frontierShare >= 50,
                "past the falloff the frontier regions together are only " + frontierShare + "% of "
                    + "sampled locations, so a player out there is mostly still walking through "
                    + "household sprawl - the bug report this threshold exists to catch.");
            helper.succeed();
        });
    }

    /** The furthest onset in the list, so the sweep starts where every region is eligible. */
    private static int maxOnset(List<RegionBiomeSource.FrontierEntry> frontier) {
        int max = 0;
        for (RegionBiomeSource.FrontierEntry entry : frontier) {
            max = Math.max(max, entry.onset());
        }
        return max;
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

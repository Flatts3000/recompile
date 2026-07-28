package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.worldgen.RegionBiomeSource;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Proves the region system's load-bearing guarantee (demolition_yard_spec.md S1): <b>everything within
 * {@code coreRadius} of world origin is household_sprawl</b>, so standing at spawn the whole hostile
 * spawn range is inside the empty-spawner biome and the player is 100% protected. Also confirms the
 * frontier actually appears further out (the gradient is not stuck on household).
 *
 * <p>Drives {@link RegionBiomeSource#getNoiseBiome} directly with real biome holders from the server
 * registry - no world placement needed, so it is a pure-logic sweep run inside the gametest harness.
 */
final class RegionBiomeSourceTests {

    private static final int CORE_RADIUS = 512;

    private RegionBiomeSourceTests() {
    }

    private static ResourceKey<Biome> biomeKey(String path) {
        return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, path));
    }

    static void register() {
        RCGameTests.test("region_core_is_all_household_and_frontier_appears", 1, helper -> {
            var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
            Holder<Biome> household = biomes.getOrThrow(biomeKey("household_sprawl"));
            Holder<Biome> demolition = biomes.getOrThrow(biomeKey("demolition_yard"));

            RegionBiomeSource source = new RegionBiomeSource(household,
                List.of(new RegionBiomeSource.FrontierEntry(demolition, CORE_RADIUS)),
                CORE_RADIUS, 2048.0F, 0.15F, 0.0025, 2611L);

            boolean sawFrontier = false;
            // Quart coords: 1 quart = 4 blocks. Sweep out to 1600 blocks (400 quart) so the frontier is
            // guaranteed to show, stepping 32 blocks (8 quart) - fine, the core guarantee is unconditional
            // in code, this just confirms it holds and the frontier is reachable.
            for (int qx = -400; qx <= 400; qx += 8) {
                for (int qz = -400; qz <= 400; qz += 8) {
                    int bx = qx << 2;
                    int bz = qz << 2;
                    double d = Math.sqrt((double) bx * bx + (double) bz * bz);
                    Holder<Biome> biome = source.getNoiseBiome(qx, 0, qz, null);
                    if (d < CORE_RADIUS) {
                        helper.assertTrue(biome == household,
                            "inside the safe core (dist " + (int) d + " < " + CORE_RADIUS
                                + ") the biome must be household, got frontier at block " + bx + "," + bz);
                    }
                    if (biome == demolition) {
                        sawFrontier = true;
                    }
                }
            }
            helper.assertTrue(sawFrontier, "demolition_yard must appear somewhere beyond the core");
            helper.succeed();
        });
    }
}

package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.recompile.content.recipe.TeardownRecipe;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Weighted teardown pools: pick-one draws, and knowledge that follows the draw.
 *
 * <p><b>Why pools exist at all.</b> {@code extras} rolls each bonus independently, so it cannot say
 * "one of motor, pump or bulb" - independent 1-in-3 chances give you none of them about 30% of the
 * time and two or more about 26%. A pool draws a fixed number of times from the weights, which is
 * how every other random table in this mod already behaves.
 *
 * <p>Pure logic, so it is a unit test rather than a GameTest: no world, no rendering, nothing to
 * place. The in-world half - that the bench drops what was drawn - is covered where the bench is.
 */
class TeardownPoolTest {

    private static TeardownRecipe.PoolEntry entry(Item item, int weight) {
        return new TeardownRecipe.PoolEntry(Optional.of(item), weight, 1);
    }

    private static TeardownRecipe.PoolEntry filler(int weight) {
        return new TeardownRecipe.PoolEntry(Optional.empty(), weight, 1);
    }

    @Test
    @DisplayName("a draw returns exactly one entry, never none and never two")
    void oneDrawIsOneEntry() {
        TeardownRecipe.Pool pool = new TeardownRecipe.Pool(1, false, 4, List.of(
            entry(Items.IRON_INGOT, 1), entry(Items.GOLD_INGOT, 1), entry(Items.DIAMOND, 1)));
        RandomSource random = RandomSource.create(1234L);
        for (int i = 0; i < 500; i++) {
            assertTrue(pool.draw(random).isPresent(),
                "a pool with no filler entry must always produce something - this is the whole "
                    + "difference from `extras`, which can roll nothing at all");
        }
    }

    @Test
    @DisplayName("weights are honoured, so a rare component stays rare")
    void weightsAreHonoured() {
        TeardownRecipe.Pool pool = new TeardownRecipe.Pool(1, false, 4, List.of(
            entry(Items.IRON_INGOT, 8), entry(Items.DIAMOND, 2)));
        RandomSource random = RandomSource.create(99L);
        Map<String, Integer> seen = new TreeMap<>();
        int draws = 20000;
        for (int i = 0; i < draws; i++) {
            pool.draw(random).flatMap(TeardownRecipe.PoolEntry::item).ifPresent(item ->
                seen.merge(String.valueOf(BuiltInRegistries.ITEM.getKey(item)), 1, Integer::sum));
        }
        double diamondShare = seen.getOrDefault("minecraft:diamond", 0) / (double) draws;
        assertTrue(Math.abs(diamondShare - 0.2) < 0.02,
            "weight 2 of 10 should come out at about 20%, got " + diamondShare
                + " - if this drifts the whole point of weighting a component pool is gone");
    }

    @Test
    @DisplayName("a filler entry is how a pool is allowed to give nothing")
    void fillerMeansNothing() {
        TeardownRecipe.Pool pool = new TeardownRecipe.Pool(1, false, 4, List.of(
            entry(Items.DIAMOND, 1), filler(9)));
        RandomSource random = RandomSource.create(7L);
        int hits = 0;
        for (int i = 0; i < 20000; i++) {
            if (pool.draw(random).isPresent()) {
                hits++;
            }
        }
        assertTrue(Math.abs(hits / 20000.0 - 0.1) < 0.02,
            "one in ten should survive the filler, got " + hits / 20000.0);
    }

    @Test
    @DisplayName("a teaching pool declares every recipe it could reveal")
    void teachingPoolIsVisibleDownstream() {
        // Fragment assembly, the guidebook checks and JEI all read teaches() to learn what a
        // teardown can reveal. A pool-taught recipe that never appeared there would produce
        // fragments that assemble into nothing - so the recipe synthesises an entry per pool item,
        // and only the BENCH knows that a draw grants just the one it pulled.
        TeardownRecipe recipe = new TeardownRecipe(
            net.minecraft.world.item.crafting.Ingredient.of(Items.IRON_INGOT),
            "recompile:workbench",
            List.of(),
            List.of(),
            List.of(new TeardownRecipe.Pool(1, true, 6, List.of(
                entry(Items.IRON_INGOT, 1), entry(Items.GOLD_INGOT, 1)))),
            List.of(),
            Optional.empty(),
            100);

        List<Identifier> taught = recipe.teaches().stream()
            .map(TeardownRecipe.TeachEntry::recipe).toList();
        assertEquals(2, taught.size(), "both pool items should be reported as teachable: " + taught);
        assertTrue(taught.contains(Identifier.parse("minecraft:iron_ingot")), "got " + taught);
        assertEquals(6, recipe.teaches().get(0).scrapsRequired(),
            "the pool's scraps_required must carry onto the entries it synthesises, or assembly "
                + "would use the default and the blueprint would cost the wrong number of fragments");

        assertTrue(recipe.declaredTeaches().isEmpty(),
            "declaredTeaches is what the bench grants unconditionally. If pool entries leaked into "
                + "it, tearing one thing down would teach every component at once.");
    }
}

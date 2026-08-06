package com.flatts.recompile.gametest;

import com.flatts.recompile.compat.SortingData;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * The component vocabulary (owner, 2026-08-06).
 *
 * <p>Machines are assembled from a shared set of parts rather than from bulk material, and the set
 * has <b>two kinds</b>, which is the distinction this expansion made explicit:
 *
 * <ul>
 *   <li><b>Placeable</b> - a block you stack into a multiblock. Pump, Machine Frame, Water Tank,
 *       Copper Pipe, Solar Panel, and now the <b>Motor</b>.
 *   <li><b>Crafting</b> - an ingredient you spend in a recipe. The <b>Bulb</b> is the first.
 * </ul>
 *
 * <p>Before this, "component" meant only the first kind, and the Pump was the whole vocabulary that
 * was actually gated - it appeared in <b>zero</b> crafting recipes and its only use was one
 * multiblock. The guidebook meanwhile claimed it gated the Rain Collector and the Hydroponics Bay,
 * and neither ever needed one.
 *
 * <p>So these tests are <b>reachability</b> tests. A component that exists, registers, renders and is
 * used nowhere passes every structural check the repo has while being exactly the dead weight this
 * work set out to remove.
 */
final class ComponentTests {

    private ComponentTests() {
    }

    static void register() {
        // THE BULB IS SPENT SOMEWHERE. It is a crafting component, so the test is that a recipe
        // consumes it - not that it exists.
        RCGameTests.test("the_bulb_is_a_real_crafting_ingredient", 20, helper -> {
            List<String> users = recipesUsing(helper, RCItems.BULB.get());
            helper.assertTrue(!users.isEmpty(),
                "nothing crafts with a Bulb, which is the exact state the Pump was in before this "
                    + "work: a component with no consumer is scenery");
            helper.succeed();
        });

        // AND IT IS THE HYDROPONICS BAY. Named rather than left to "something uses it", because a
        // bay grown under a mound has no other light and that is the reason the part exists.
        RCGameTests.test("the_hydroponics_bay_needs_a_bulb", 20, helper -> {
            List<String> users = recipesUsing(helper, RCItems.BULB.get());
            helper.assertTrue(users.stream().anyMatch(id -> id.contains("hydroponics_bay")),
                "the Hydroponics Bay must need a Bulb - it is the machine that grows plants with no "
                    + "sky over it. Recipes using one: " + users);
            helper.succeed();
        });

        // THE MOTOR IS IN THE MACHINE. Asserted against the blueprint the framework actually
        // validates against, so this cannot drift from what a player must build.
        RCGameTests.test("the_separator_is_built_around_a_motor", 20, helper -> {
            var blueprint = ((com.flatts.recompile.content.block.SeparatorCoreBlock)
                RCBlocks.SEPARATOR.get()).blueprint();
            boolean hasMotor = blueprint.cells().stream()
                .anyMatch(c -> c.component() == RCBlocks.MOTOR.get());
            helper.assertTrue(hasMotor,
                "the Separator must be built around a Motor - it is the machine that physically "
                    + "moves, and a motor is the thing no amount of scrap replaces");
            helper.succeed();
        });

        // BOTH ARE OBTAINABLE. A gated part with no source is a machine nobody can build, and
        // nothing about a recipe or a blueprint would say so. Reads the same parsed loot the JEI
        // Sorting category renders, so this also proves they show up there.
        RCGameTests.test("motor_and_bulb_can_actually_be_found", 20, helper -> {
            helper.assertTrue(inStream(SortingData.MECHANICAL, RCItems.MOTOR.get()),
                "Mechanical Waste must yield a Motor - it is the only source, and the Separator "
                    + "cannot be built without one");
            helper.assertTrue(inStream(SortingData.HOUSEHOLD, RCItems.BULB.get()),
                "household sorting must yield a Bulb, or the Hydroponics Bay is unbuildable");
            helper.succeed();
        });
    }

    private static boolean inStream(String table, Item item) {
        return SortingData.outputs(table).stream().anyMatch(w -> w.stack().is(item));
    }

    /**
     * Ids of every recipe that consumes this item as an ingredient.
     *
     * <p><b>Two sources, and missing the second one is silent.</b> Vanilla-shaped recipes expose
     * their ingredients through {@code placementInfo()}, but
     * {@link com.flatts.recompile.content.recipe.BlueprintCraftingRecipe} returns
     * {@code PlacementInfo.NOT_PLACEABLE} - it is not placeable in the recipe book by design - and
     * carries its ingredients on its own accessor instead. A check that reads only the first would
     * report "nothing uses this" for every blueprint-crafted machine in the mod, which is most of
     * the late ones. That is exactly what this test did on its first run.
     */
    private static List<String> recipesUsing(net.minecraft.gametest.framework.GameTestHelper helper,
            Item item) {
        var stack = new net.minecraft.world.item.ItemStack(item);
        return helper.getLevel().recipeAccess().recipeMap().values().stream()
            .filter(holder -> usesItem(holder.value(), stack))
            .map(RecipeHolder::id)
            .map(Object::toString)
            .toList();
    }

    private static boolean usesItem(net.minecraft.world.item.crafting.Recipe<?> recipe,
            net.minecraft.world.item.ItemStack stack) {
        if (recipe instanceof com.flatts.recompile.content.recipe.BlueprintCraftingRecipe blueprint) {
            return blueprint.ingredients().stream()
                .anyMatch(slot -> slot.isPresent() && slot.get().test(stack));
        }
        return recipe.placementInfo().ingredients().stream().anyMatch(ing -> ing.test(stack));
    }
}

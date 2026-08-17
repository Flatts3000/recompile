package com.flatts.recompile.gametest;

import com.flatts.recompile.compat.SortingData;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
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

        // AND IT IS THE INDOOR GROWERS. Named rather than left to "something uses it", because a
        // plant started under a mound has no other light and that is the reason the part exists.
        RCGameTests.test("the_indoor_growers_need_a_bulb", 20, helper -> {
            List<String> users = recipesUsing(helper, RCItems.BULB.get());
            // BOTH machines that grow something indoors, named individually. The generic test above
            // would still pass if either lost its bulb, because the other one would keep it - which
            // is exactly how a requirement quietly disappears.
            for (String machine : List.of("hydroponics_bay", "tree_nursery")) {
                helper.assertTrue(users.stream().anyMatch(id -> id.contains(machine)),
                    machine + " must need a Bulb - it grows a plant with no sky over it. Recipes "
                        + "using one: " + users);
            }
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

        // DISBAND GIVES THE MOTOR BACK, which it did not when this was first written.
        //
        // The Motor cell forms into ordinary Separator Housing, and disband used to run the FORMED
        // block's loot table - which drops a Machine Frame. So the machine silently converted the
        // rarest part in it into the commonest one every time it was taken apart, and no existing
        // test noticed, because every other formed block happened to map from exactly one component.
        //
        // Fixed by having disband read the component off the blueprint instead. This test is the
        // guard: it is the only thing standing between a scarce found component and a machine that
        // quietly eats it.
        // THE CASCADE, ON THE MACHINE MOST EXPOSED TO IT.
        //
        // The Separator has ELEVEN dummy cells - more than anything else in the mod - and in 26.1 the
        // removal hook fires on a plain setBlock-to-AIR as well as on a real break. So clearing one
        // cell re-enters its siblings' hooks, and while the core is still FORMED each re-entry
        // re-drops the core: eleven cores from one break. disband flips FORMED off BEFORE clearing
        // cells so every re-entry bails, and this is what says so.
        //
        // It goes through destroyBlock on a CELL, not through disband() directly. Its sibling below
        // calls disband and therefore never touches the removal hook at all - which is the whole path
        // the cascade lives in, and why that test could pass with this broken.
        //
        // Written because #191 claimed a tool-gated core cannot be counted here. It can; that issue
        // was wrong, and this machine had no core assertion on the break path because of it.
        RCGameTests.test("breaking_a_separator_cell_returns_exactly_one_core", 80, helper -> {
            BlockPos core = new BlockPos(1, 2, 1);
            helper.setBlock(core, RCBlocks.SEPARATOR.get());
            for (var cell : RCBlocks.SEPARATOR.get().blueprint().cells()) {
                helper.setBlock(cell.at(core, net.minecraft.world.level.block.Rotation.NONE),
                    cell.component());
            }
            helper.assertTrue(
                com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock.tryForm(
                    helper.getLevel(), helper.absolutePos(core)),
                "precondition: the Separator must form from its components");

            var cells = RCBlocks.SEPARATOR.get().blueprint().cells();
            BlockPos victim = cells.get(cells.size() - 1)
                .at(core, net.minecraft.world.level.block.Rotation.NONE);
            helper.getLevel().destroyBlock(helper.absolutePos(victim), true);

            helper.succeedWhen(() -> {
                helper.assertItemEntityCountIs(RCItems.SEPARATOR.get(), core, 8.0, 1);
                var state = helper.getLevel().getBlockState(helper.absolutePos(core));
                helper.assertTrue(
                    !com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock.isFormed(state),
                    "the core is still FORMED after one of its cells was broken");
            });
        });

        RCGameTests.test("disbanding_a_separator_returns_the_motor", 80, helper -> {
            BlockPos core = new BlockPos(1, 2, 1);
            helper.setBlock(core, RCBlocks.SEPARATOR.get());
            for (var cell : RCBlocks.SEPARATOR.get().blueprint().cells()) {
                helper.setBlock(cell.at(core, net.minecraft.world.level.block.Rotation.NONE),
                    cell.component());
            }
            helper.assertTrue(
                com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock.tryForm(
                    helper.getLevel(), helper.absolutePos(core)),
                "precondition: the Separator must form from its components, Motor included");

            com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock.disband(
                helper.getLevel(), helper.absolutePos(core), true);

            helper.succeedWhen(() -> helper.assertItemEntityCountIs(
                RCItems.MOTOR.get(), core, 6.0, 1));
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

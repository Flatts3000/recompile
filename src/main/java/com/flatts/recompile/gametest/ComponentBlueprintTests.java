package com.flatts.recompile.gametest;

import com.flatts.recompile.content.item.BlueprintItem;
import com.flatts.recompile.content.recipe.BlueprintCraftingRecipe;
import com.flatts.recompile.content.recipe.TeardownRecipe;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Components are craftable from teardown knowledge (#160).
 *
 * <p><b>The ruling this encodes</b> (owner, 2026-08-08): a component gets a blueprint route, and the
 * fragments for it come from teardown - so the gate moves from luck to effort without leaving the
 * teardown spine. It is a scoped reversal of <b>P2.4-R item 7</b>, which said the Pump is "never
 * crafted": the literal wording no longer holds, the reason behind it does, and four washing machines
 * is a harder gate than one lucky find.
 *
 * <p><b>Why these need their own tests rather than riding the existing sweep.</b>
 * {@code a_blueprint_result_has_no_other_route} asserts that nothing else makes a blueprint-gated item.
 * That is true of the Clean Mattress and the Hydroponics Bay and deliberately <b>false</b> of all three
 * components, which are still salvage. The sweep would have passed anyway, because it reads
 * {@code display()} and {@code TeardownRecipe} does not implement it - so it is structurally blind to
 * salvage routes. A green there says nothing about a component, and this file is what says something.
 *
 * <p><b>What this does NOT fix, stated because the issue implied otherwise.</b> #160 argued that a
 * found-only component is a hard gate whose failure mode is "if the stream never rolls one, the machine
 * is unbuildable and the player has no lever to pull". <b>None of the three was ever luck-gated.</b>
 * Each sits in its object's {@code results} rather than its {@code extras}, so a teardown yields one
 * <i>every time</i> - the only roll anywhere in the chain is whether Bulky Waste gives you the object
 * at all. Since a blueprint costs <b>four</b> of them, it cannot help a player who has found none; it
 * helps a player who has run out. That is a real problem and a smaller one, and
 * {@code a_component_from_a_teardown_is_never_a_dice_roll} pins the fact the argument got wrong so
 * nobody re-derives the original claim from a green suite.
 *
 * <p>The Motor and the Bulb keep their <b>sorting</b> sources as well (Mechanical Waste and household
 * pulls), so those two have three routes rather than two. Only the Pump depends on a single object.
 */
final class ComponentBlueprintTests {

    private ComponentBlueprintTests() {
    }

    /**
     * The three components that are salvage first and blueprint second.
     *
     * <p>All of #160's subjects, finished across three PRs: the Pump out of a Washing Machine, and
     * then the Motor and the Bulb once each was given a found object to be torn out of - a Broken Fan
     * and a Light Fixture (#170, #171). Before those existed neither could carry a {@code teaches}
     * entry at all, because the field lives on a teardown and they only came from sorting.
     *
     * <p><b>A method rather than a static field, and that is not style.</b> This class is initialised
     * from {@code RCGameTests.register}, which runs inside the mod constructor - so a static
     * {@code List.of(RCItems.PUMP.get(), ...)} resolves DeferredItems before registration has finished
     * and fails the whole mod to load with a bare {@code ExceptionInInitializerError}. The same trap
     * caught {@code MenuLayoutTests} holding layouts eagerly, in this same week; deferring the lookup
     * to call time is the fix in both places.
     */
    private static List<Item> gatedComponents() {
        return List.of(RCItems.PUMP.get(), RCItems.MOTOR.get(), RCItems.BULB.get());
    }

    static void register() {
        /*
         * The dual route, pinned from both ends. Either half alone is a different design: without the
         * teardown the Pump stops being a find and P2.4-R item 7 is gone rather than reworded; without
         * the blueprint the whole issue is unbuilt.
         */
        RCGameTests.test("every_gated_component_is_reachable_by_salvage_and_by_blueprint", 20, helper -> {
            List<String> broken = new ArrayList<>();
            for (Item component : gatedComponents()) {
                String name = String.valueOf(BuiltInRegistries.ITEM.getKey(component));

                boolean salvaged = false;
                for (RecipeHolder<TeardownRecipe> holder : helper.getLevel().recipeAccess()
                        .recipeMap().byType(RCRecipeTypes.TEARDOWN.get())) {
                    for (TeardownRecipe.ItemResult result : holder.value().results()) {
                        if (result.item() == component) {
                            salvaged = true;
                        }
                    }
                }
                if (!salvaged) {
                    broken.add(name + " comes out of no teardown. The blueprint route was added "
                        + "ALONGSIDE salvage, never instead of it - losing this turns a gated "
                        + "component into an ordinary crafting recipe, which is the half of P2.4-R "
                        + "item 7 that was never up for reversal");
                }

                boolean blueprinted = false;
                for (RecipeHolder<BlueprintCraftingRecipe> holder : helper.getLevel().recipeAccess()
                        .recipeMap().byType(RCRecipeTypes.BLUEPRINT_CRAFTING.get())) {
                    if (holder.value().result().item() == component) {
                        blueprinted = true;
                    }
                }
                if (!blueprinted) {
                    broken.add(name + " has no blueprint recipe");
                }
            }
            helper.assertTrue(broken.isEmpty(),
                "these components do not have both routes (" + broken.size() + "): " + broken);
            helper.succeed();
        });

        /*
         * The fact #160's motivation got wrong, pinned so it cannot be re-derived from a green suite.
         * A teardown yields its `results` every time and rolls only its `extras`; the Pump is a result.
         * So "the stream never rolls one" was never about the Pump - it is about finding a washing
         * machine, which the blueprint needs four of and therefore cannot rescue.
         */
        RCGameTests.test("a_component_from_a_teardown_is_never_a_dice_roll", 20, helper -> {
            List<String> rolled = new ArrayList<>();
            for (Item component : gatedComponents()) {
                boolean guaranteed = false;
                for (RecipeHolder<TeardownRecipe> holder : helper.getLevel().recipeAccess()
                        .recipeMap().byType(RCRecipeTypes.TEARDOWN.get())) {
                    for (TeardownRecipe.ItemResult result : holder.value().results()) {
                        if (result.item() == component) {
                            guaranteed = true;
                        }
                    }
                }
                if (!guaranteed) {
                    rolled.add(String.valueOf(BuiltInRegistries.ITEM.getKey(component)));
                }
            }
            helper.assertTrue(rolled.isEmpty(),
                "these components are not a guaranteed teardown result, so taking the object apart "
                    + "might give you nothing. The blueprint cannot rescue that - it costs four of the "
                    + "same teardown: " + rolled);
            helper.succeed();
        });

        /*
         * The knowledge half. chance and scraps_required are asserted the same way the mattress's are,
         * because both numbers have a design reason rather than being tuning: every teardown teaches
         * (a dice roll is not the thing that ends a grind), and a one-fragment blueprint IS the sheet.
         */
        RCGameTests.test("the_object_that_yields_a_component_is_the_one_that_teaches_it", 20, helper -> {
            List<String> broken = new ArrayList<>();
            for (Item component : gatedComponents()) {
                Identifier blueprint = BuiltInRegistries.ITEM.getKey(component);
                TeardownRecipe.TeachEntry lesson = null;
                for (RecipeHolder<TeardownRecipe> holder : helper.getLevel().recipeAccess()
                        .recipeMap().byType(RCRecipeTypes.TEARDOWN.get())) {
                    boolean yields = holder.value().results().stream()
                        .anyMatch(result -> result.item() == component);
                    if (!yields) {
                        continue;
                    }
                    for (TeardownRecipe.TeachEntry teach : holder.value().teaches()) {
                        if (teach.recipe().equals(blueprint)) {
                            lesson = teach;
                        }
                    }
                }
                if (lesson == null) {
                    broken.add(blueprint + ": the teardown that yields it does not teach it. Hanging "
                        + "the lesson on some other object breaks the tie between the thing you take "
                        + "apart and the thing you learn, which is the whole shape of #160");
                    continue;
                }
                if (lesson.chance() < 1.0f) {
                    broken.add(blueprint + ": teaches at chance " + lesson.chance()
                        + ". Every teardown teaches (owner, 2026-08-02) - below 1 the cost is a dice "
                        + "game, and what ends a grind is knowing the recipe, not getting lucky");
                }
                if (lesson.scrapsRequired() <= 1) {
                    broken.add(blueprint + ": scraps_required is " + lesson.scrapsRequired()
                        + " - at 1 the fragment IS the sheet, so fragments stop meaning anything");
                }
            }
            helper.assertTrue(broken.isEmpty(),
                "component teardowns are wrong (" + broken.size() + "): " + broken);
            helper.succeed();
        });

        /*
         * Every blueprint the mod ships must be nameable, buildable and learnable. Each half of this has
         * already failed here once: the schema's example recipe taught a blueprint that did not exist
         * (dangling teaches), and the JEI panels shipped lang keys nothing ever asked for. A blueprint
         * missing its lang key renders the raw key to the player, which is silent in exactly the same
         * way.
         */
        RCGameTests.test("every_shipped_blueprint_has_a_name_a_recipe_and_a_teacher", 20, helper -> {
            List<String> broken = new ArrayList<>();

            for (Identifier blueprint : BlueprintItem.shipped()) {
                String key = "blueprint." + blueprint.getNamespace() + "." + blueprint.getPath();
                if (Component.translatable(key).getString().equals(key)) {
                    broken.add(blueprint + " has no lang key, so its name renders as '" + key + "'");
                }

                boolean built = false;
                for (RecipeHolder<BlueprintCraftingRecipe> holder : helper.getLevel().recipeAccess()
                        .recipeMap().byType(RCRecipeTypes.BLUEPRINT_CRAFTING.get())) {
                    if (holder.value().blueprint().equals(blueprint)) {
                        built = true;
                    }
                }
                if (!built) {
                    broken.add(blueprint + " has no blueprint_crafting recipe, so holding the sheet "
                        + "does nothing");
                }

                boolean taught = false;
                for (RecipeHolder<TeardownRecipe> holder : helper.getLevel().recipeAccess()
                        .recipeMap().byType(RCRecipeTypes.TEARDOWN.get())) {
                    for (TeardownRecipe.TeachEntry teach : holder.value().teaches()) {
                        if (teach.recipe().equals(blueprint)) {
                            taught = true;
                        }
                    }
                }
                if (!taught) {
                    broken.add(blueprint + " is taught by no teardown, so it can only ever be a "
                        + "creative-tab item");
                }
            }

            helper.assertTrue(BlueprintItem.shipped().size() >= 3,
                "expected the mod's blueprints, found " + BlueprintItem.shipped().size());
            helper.assertTrue(broken.isEmpty(),
                "these blueprints are incomplete (" + broken.size() + "): " + broken);
            helper.succeed();
        });

        /*
         * The cost. Not a balance assertion - the numbers belong to #36 - but the shape does matter:
         * finding a Pump must stay better than making one, or the blueprint quietly retires the find
         * and takes rung 1's "and a find" clause with it. Salvage hands over a Pump AND its scrap for
         * one prybar action; the recipe has to cost real materials.
         */
        RCGameTests.test("making_a_pump_costs_more_than_finding_one", 20, helper -> {
            BlueprintCraftingRecipe recipe = null;
            for (RecipeHolder<BlueprintCraftingRecipe> holder : helper.getLevel().recipeAccess()
                    .recipeMap().byType(RCRecipeTypes.BLUEPRINT_CRAFTING.get())) {
                if (holder.value().result().item() == RCItems.PUMP.get()) {
                    recipe = holder.value();
                }
            }
            helper.assertTrue(recipe != null, "no Pump blueprint recipe");
            helper.assertTrue(recipe.result().count() == 1,
                "a Pump recipe that yields more than one makes the find worthless, got "
                    + recipe.result().count());

            long slots = recipe.pattern().ingredients().stream().filter(java.util.Optional::isPresent)
                .count();
            helper.assertTrue(slots >= 4,
                "the Pump recipe fills only " + slots + " slots. Salvage gives a Pump plus scrap for a "
                    + "single prybar action, so a cheap recipe retires the find rather than backing "
                    + "it up");
            helper.succeed();
        });
    }
}

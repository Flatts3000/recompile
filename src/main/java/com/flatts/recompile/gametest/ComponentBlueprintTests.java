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
 * <p><b>Why the Pump needs its own tests rather than riding the existing sweep.</b>
 * {@code a_blueprint_result_has_no_other_route} asserts that nothing else makes a blueprint-gated item.
 * That is true of the Clean Mattress and the Hydroponics Bay and deliberately <b>false</b> of the Pump,
 * which is still salvage. The sweep would have passed anyway, because it reads {@code display()} and
 * {@code TeardownRecipe} does not implement it - so it is structurally blind to salvage routes. A green
 * there says nothing about the Pump, and this file is what says something.
 *
 * <p><b>What this does NOT fix, stated because the issue implied otherwise.</b> #160 argued that a
 * found-only component is a hard gate whose failure mode is "if the stream never rolls one, the machine
 * is unbuildable and the player has no lever to pull". <b>The Pump was never luck-gated.</b> It sits in
 * the washing machine's {@code results} rather than its {@code extras}, so a teardown yields one
 * <i>every time</i> - the only roll anywhere in the chain is whether Bulky Waste gives you a washing
 * machine at all. Since the blueprint costs <b>four</b> washing machines, it cannot help a player who
 * has found none; it helps a player who has run out. That is a real problem and a smaller one, and
 * {@code a_pump_from_a_teardown_is_never_a_dice_roll} pins the fact the argument got wrong so nobody
 * re-derives the original claim from a green suite.
 */
final class ComponentBlueprintTests {

    private ComponentBlueprintTests() {
    }

    static void register() {
        /*
         * The dual route, pinned from both ends. Either half alone is a different design: without the
         * teardown the Pump stops being a find and P2.4-R item 7 is gone rather than reworded; without
         * the blueprint the whole issue is unbuilt.
         */
        RCGameTests.test("a_pump_is_reachable_by_salvage_and_by_blueprint", 20, helper -> {
            Item pump = RCItems.PUMP.get();

            boolean salvaged = false;
            for (RecipeHolder<TeardownRecipe> holder : helper.getLevel().recipeAccess()
                    .recipeMap().byType(RCRecipeTypes.TEARDOWN.get())) {
                for (TeardownRecipe.ItemResult result : holder.value().results()) {
                    if (result.item() == pump) {
                        salvaged = true;
                    }
                }
            }
            helper.assertTrue(salvaged,
                "no teardown yields a Pump any more. The blueprint route was added ALONGSIDE salvage, "
                    + "not instead of it - losing this makes rung 1 a crafting recipe, which is the "
                    + "half of P2.4-R item 7 that was never up for reversal");

            boolean blueprinted = false;
            for (RecipeHolder<BlueprintCraftingRecipe> holder : helper.getLevel().recipeAccess()
                    .recipeMap().byType(RCRecipeTypes.BLUEPRINT_CRAFTING.get())) {
                if (holder.value().result().item() == pump) {
                    blueprinted = true;
                }
            }
            helper.assertTrue(blueprinted,
                "no blueprint recipe makes a Pump, so #160 is not built");
            helper.succeed();
        });

        /*
         * The fact #160's motivation got wrong, pinned so it cannot be re-derived from a green suite.
         * A teardown yields its `results` every time and rolls only its `extras`; the Pump is a result.
         * So "the stream never rolls one" was never about the Pump - it is about finding a washing
         * machine, which the blueprint needs four of and therefore cannot rescue.
         */
        RCGameTests.test("a_pump_from_a_teardown_is_never_a_dice_roll", 20, helper -> {
            boolean guaranteed = false;
            for (RecipeHolder<TeardownRecipe> holder : helper.getLevel().recipeAccess()
                    .recipeMap().byType(RCRecipeTypes.TEARDOWN.get())) {
                for (TeardownRecipe.ItemResult result : holder.value().results()) {
                    if (result.item() == RCItems.PUMP.get()) {
                        guaranteed = true;
                    }
                }
            }
            helper.assertTrue(guaranteed,
                "the Pump must stay a guaranteed teardown result rather than a rolled extra. Moving it "
                    + "to extras would turn rung 1 into a dice game, and the blueprint could not "
                    + "rescue that either - it costs four of the same teardown");
            helper.succeed();
        });

        /*
         * The knowledge half. chance and scraps_required are asserted the same way the mattress's are,
         * because both numbers have a design reason rather than being tuning: every teardown teaches
         * (a dice roll is not the thing that ends a grind), and a one-fragment blueprint IS the sheet.
         */
        RCGameTests.test("tearing_down_a_washing_machine_teaches_the_pump", 20, helper -> {
            TeardownRecipe.TeachEntry toPump = null;
            for (RecipeHolder<TeardownRecipe> holder : helper.getLevel().recipeAccess()
                    .recipeMap().byType(RCRecipeTypes.TEARDOWN.get())) {
                boolean yieldsPump = holder.value().results().stream()
                    .anyMatch(result -> result.item() == RCItems.PUMP.get());
                if (!yieldsPump) {
                    continue;
                }
                for (TeardownRecipe.TeachEntry teach : holder.value().teaches()) {
                    if (teach.recipe().equals(BlueprintItem.PUMP)) {
                        toPump = teach;
                    }
                }
            }
            helper.assertTrue(toPump != null,
                "the teardown that yields a Pump must also teach the Pump blueprint - that is the "
                    + "whole shape of #160, and hanging the lesson on some other object would break "
                    + "the tie between the thing you take apart and the thing you learn");
            helper.assertTrue(toPump.chance() >= 1.0f,
                "every teardown teaches (owner, 2026-08-02); a chance below 1 turns the cost into a "
                    + "dice game - got " + toPump.chance());
            helper.assertTrue(toPump.scrapsRequired() > 1,
                "scraps_required is the whole reason fragments exist - at 1 the fragment is the sheet");
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

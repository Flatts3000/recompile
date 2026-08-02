package com.flatts.recompile.gametest;

import com.flatts.recompile.content.item.BlueprintItem;
import com.flatts.recompile.content.recipe.BlueprintCraftingRecipe;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * Blueprints, phase 2 (#95): the item exists, it names a set, and the thing it unlocks is reachable no
 * other way.
 *
 * <p>The claim that has to hold is <b>the gate</b>. A Clean Mattress that some ordinary recipe can also
 * produce makes the whole blueprint system decorative, and a gate proven only by a comment is exactly
 * how the iron gate stayed open for weeks - the reason
 * {@code no_smelting_recipe_turns_a_mod_item_into_iron} exists at all. So this sweeps the live recipe
 * manager rather than trusting the recipe files.
 */
final class BlueprintTests {

    private BlueprintTests() {
    }

    static void register() {
        // THE GATE. Nothing in the crafting recipe manager may produce a Clean Mattress: the blueprint
        // bench is the only route, and that is the entire proposition of the feature.
        RCGameTests.test("blueprint_crafting_is_the_only_route_to_a_clean_mattress", 20, helper -> {
            List<String> offenders = new ArrayList<>();
            int swept = 0;
            // Every recipe of every type, ours included, read through the display each one publishes
            // for the recipe book. Sweeping by INPUT the way the iron gate test does cannot work here:
            // the question is not "what does this item turn into" but "what turns into this item", and
            // the input space for that is every combination in a 3x3.
            for (RecipeHolder<?> holder : helper.getLevel().recipeAccess().recipeMap().values()) {
                swept++;
                if (holder.value().getType() == RCRecipeTypes.BLUEPRINT_CRAFTING.get()) {
                    continue;   // the sanctioned route
                }
                for (var display : holder.value().display()) {
                    if (produces(display.result(), RCItems.CLEAN_MATTRESS.get())) {
                        offenders.add(holder.id().toString());
                    }
                }
            }
            helper.assertTrue(swept > 100,
                "only " + swept + " recipes were swept - the sweep is broken, so this would pass "
                    + "against any recipe that made a Clean Mattress");
            helper.assertTrue(offenders.isEmpty(),
                "a Clean Mattress must come only from a blueprint: " + offenders);
            helper.succeed();
        });

        // The recipe type parses and its one recipe is present. Without this, a typo in the JSON is a
        // silent no-op: the recipe simply does not load and nothing anywhere reports it.
        RCGameTests.test("the_clean_mattress_blueprint_recipe_loads", 20, helper -> {
            var recipes = helper.getLevel().recipeAccess().recipeMap()
                .byType(RCRecipeTypes.BLUEPRINT_CRAFTING.get());
            List<BlueprintCraftingRecipe> found = new ArrayList<>();
            recipes.forEach(holder -> found.add(holder.value()));
            helper.assertTrue(found.size() == 1,
                "expected exactly one blueprint recipe, got " + found.size());

            BlueprintCraftingRecipe recipe = found.get(0);
            helper.assertTrue(recipe.blueprint().equals(BlueprintItem.CLEAN_MATTRESS),
                "it must name the Clean Mattress blueprint, got " + recipe.blueprint());
            helper.assertTrue(recipe.result().item() == RCItems.CLEAN_MATTRESS.get(),
                "and produce a Clean Mattress, got " + recipe.result().item());
            helper.succeed();
        });

        // Shapeless means shapeless. Matching by index would make the order a player happens to drop
        // items in load-bearing, which is the kind of bug that only shows up for one person.
        RCGameTests.test("a_blueprint_recipe_ignores_the_order_of_its_ingredients", 20, helper -> {
            var recipes = helper.getLevel().recipeAccess().recipeMap()
                .byType(RCRecipeTypes.BLUEPRINT_CRAFTING.get());
            List<BlueprintCraftingRecipe> found = new ArrayList<>();
            recipes.forEach(holder -> found.add(holder.value()));
            BlueprintCraftingRecipe recipe = found.get(0);

            ItemStack wool = new ItemStack(net.minecraft.world.item.Items.WHITE_WOOL);
            ItemStack string = new ItemStack(net.minecraft.world.item.Items.STRING);
            helper.assertTrue(
                recipe.matches(input(wool, wool, wool, string, string, string), helper.getLevel()),
                "the recipe must match its own ingredients");
            helper.assertTrue(
                recipe.matches(input(string, wool, string, wool, string, wool), helper.getLevel()),
                "and must still match them interleaved - shapeless means shapeless");
            helper.assertTrue(
                recipe.matches(input(new ItemStack(net.minecraft.world.item.Items.RED_WOOL),
                    wool, wool, string, string, string), helper.getLevel()),
                "any wool, since the ingredient is the tag - a player recolours the bed afterwards");
            helper.assertFalse(recipe.matches(input(wool, wool, string, string, string), helper.getLevel()),
                "but not with an ingredient missing");
            helper.assertFalse(
                recipe.matches(input(wool, wool, wool, wool, string, string, string), helper.getLevel()),
                "nor with a spare item in the grid - an extra ingredient is a different recipe");
            helper.succeed();
        });

        // A blueprint with no component is a real thing a player can hold: /give makes one, and so does
        // a pack removing a recipe out from under a save. It must be inert, not a crash.
        RCGameTests.test("a_blank_blueprint_is_inert_rather_than_broken", 20, helper -> {
            ItemStack blank = new ItemStack(RCItems.BLUEPRINT.get());
            helper.assertTrue(BlueprintItem.blueprintOf(blank) == null,
                "a blueprint with no component names nothing");
            helper.assertTrue(
                BlueprintItem.blueprintOf(new ItemStack(RCItems.CLEAN_MATTRESS.get())) == null,
                "and an item that is not a blueprint never names one either");

            ItemStack unknown = BlueprintItem.of(RCItems.BLUEPRINT.get(),
                Identifier.fromNamespaceAndPath("recompile", "no_such_blueprint"));
            helper.assertTrue(BlueprintItem.blueprintOf(unknown) != null,
                "a blueprint naming a set nothing uses still reads back - it is data, not a lookup");
            helper.succeed();
        });

        // Decided rather than defaulted, so assert it: knowledge does not stack. A second copy of what
        // you already know is worth nothing, and presence answers "do I know this" better than a count.
        RCGameTests.test("blueprints_do_not_stack", 20, helper -> {
            helper.assertTrue(new ItemStack(RCItems.BLUEPRINT.get()).getMaxStackSize() == 1,
                "a blueprint is a document, not a resource");

            ItemStack a = BlueprintItem.of(RCItems.BLUEPRINT.get(), BlueprintItem.CLEAN_MATTRESS);
            ItemStack b = BlueprintItem.of(RCItems.BLUEPRINT.get(),
                Identifier.fromNamespaceAndPath("recompile", "something_else"));
            helper.assertFalse(ItemStack.isSameItemSameComponents(a, b),
                "two different blueprints must be distinguishable, or one item cannot carry them all");
            helper.succeed();
        });
    }

    /** A crafting grid holding exactly these items, in this order. */
    private static CraftingInput input(ItemStack... stacks) {
        NonNullList<ItemStack> items = NonNullList.withSize(stacks.length, ItemStack.EMPTY);
        for (int i = 0; i < stacks.length; i++) {
            items.set(i, stacks[i]);
        }
        return CraftingInput.of(stacks.length, 1, items);
    }

    /**
     * Whether a recipe's result slot yields this item.
     *
     * <p>Reads the two concrete shapes a real result takes - a plain item and a stack template - and
     * recurses through the wrappers vanilla builds around them. It does <b>not</b> resolve through a
     * context map, which would need a live client-side resolution context that a headless GameTest has
     * no business constructing. A shape this does not recognise is reported as "not a match", which is
     * the safe direction for a gate test only in one sense: it could miss a leak rather than invent
     * one, so the sweep count assertion above is what stops it degrading into a test of nothing.
     */
    private static boolean produces(SlotDisplay display, Item item) {
        return switch (display) {
            case SlotDisplay.ItemSlotDisplay slot -> slot.item().value() == item;
            case SlotDisplay.ItemStackSlotDisplay slot -> slot.stack().item().value() == item;
            case SlotDisplay.Composite composite ->
                composite.contents().stream().anyMatch(inner -> produces(inner, item));
            case SlotDisplay.WithRemainder remainder -> produces(remainder.input(), item);
            case SlotDisplay.OnlyWithComponent only -> produces(only.source(), item);
            default -> false;
        };
    }
}

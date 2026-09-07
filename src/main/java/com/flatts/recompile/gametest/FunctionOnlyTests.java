package com.flatts.recompile.gametest;

import com.flatts.recompile.content.recipe.TeardownRecipe;
import com.flatts.recompile.registry.RCRecipeTypes;
import com.flatts.recompile.registry.RCTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code #recompile:function_only} enforced: found or nothing (#391, step 5 of #386).
 *
 * <p><b>The twin of {@code FoundNotCraftedTests}, and deliberately not the same test.</b> That one
 * governs FINISHED GOODS - buckets, bowls, bottles - the things a person would throw away. This one
 * governs the opposite end: the working PART inside the thing they threw away. Both say "you cannot
 * make this", and they say it about different halves of the economy for different reasons.
 *
 * <p><b>Three assertions, because one is not enough and the gaps are all silent.</b> Disabling a
 * recipe without a source does not make an item found, it makes it unobtainable, and the symptom is a
 * player who simply never sees one - that is the lesson {@code FoundNotCraftedTests} already carries
 * and it applies here verbatim. The market half is the one that check could not have: a shop counter
 * is not a recipe, so a recipe sweep is structurally blind to an offer selling the very thing, or the
 * knowledge to make it ({@code market_spec.md} section 14).
 *
 * <p><b>The renewability check is the load-bearing one</b> and it is the whole reason the #228
 * reversal is affordable. A find-only component sourced from a one-time structure is a wall; sourced
 * from a mound that regrows it is a rate limit, and a rate limit is what a progression gate is. So it
 * is not enough that some teardown yields the Motor - the INPUT to that teardown has to come from
 * somewhere that refills.
 */
public final class FunctionOnlyTests {

    private FunctionOnlyTests() {
    }

    private static List<Item> members() {
        List<Item> out = new ArrayList<>();
        BuiltInRegistries.ITEM.getTagOrEmpty(RCTags.FUNCTION_ONLY)
            .forEach(holder -> out.add(holder.value()));
        return out;
    }

    static void register() {

        RCGameTests.test("nothing_function_only_can_be_crafted", 40, helper -> {
            ServerLevel level = helper.getLevel();
            List<Item> members = members();
            helper.assertTrue(!members.isEmpty(),
                "#recompile:function_only is empty, so every check in this file passes by looking "
                    + "at nothing. The Motor is meant to be in it (#391)");

            List<String> broken = new ArrayList<>();
            int swept = 0;
            for (RecipeHolder<?> holder : level.getServer().getRecipeManager().recipeMap().values()) {
                swept++;
                // A BLUEPRINT RECIPE IS READ EXPLICITLY, and the first version of this test did not
                // do that and was VACUOUS because of it: BlueprintCraftingRecipe's placementInfo is
                // NOT_PLACEABLE, so display() is empty and the generic walk below sees no result at
                // all. Restoring recipe/motor.json - the very route this tag exists to close - left
                // the test green. MarketTests carries the same special case for the same reason;
                // this one was written after reading that comment and still walked into it.
                List<Item> results = new ArrayList<>();
                if (holder.value() instanceof com.flatts.recompile.content.recipe
                        .BlueprintCraftingRecipe blueprint) {
                    results.add(blueprint.result().item());
                } else {
                    for (var display : holder.value().display()) {
                        for (ItemStack stack : display.result().resolveForStacks(
                                net.minecraft.world.item.crafting.display.SlotDisplayContext
                                    .fromLevel(level))) {
                            results.add(stack.getItem());
                        }
                    }
                }
                for (Item result : results) {
                    if (members.contains(result)) {
                        broken.add(holder.id().identifier() + " crafts "
                            + BuiltInRegistries.ITEM.getKey(result)
                            + ", which is function_only and must be salvaged instead");
                    }
                }
            }
            helper.assertTrue(swept > 100,
                "only " + swept + " recipes swept - the sweep is broken and this proves nothing");
            helper.assertTrue(broken.isEmpty(), String.join("; ", broken));
            helper.succeed();
        });

        /*
         * The half a recipe sweep cannot reach. A market offer is not a recipe: `matches` returns
         * false by construction, it has no ingredients and no result slot, so the test above walks
         * straight past one selling a Motor outright or selling the sheet that makes it.
         */
        RCGameTests.test("nothing_function_only_is_sold_at_the_market", 40, helper -> {
            List<Item> members = members();
            List<String> broken = new ArrayList<>();
            for (RecipeHolder<com.flatts.recompile.content.recipe.MarketOfferRecipe> holder
                    : helper.getLevel().recipeAccess().recipeMap()
                        .byType(RCRecipeTypes.MARKET_OFFER.get())) {
                var offer = holder.value();
                offer.item().ifPresent(item -> {
                    if (members.contains(item)) {
                        broken.add(holder.id().identifier() + " sells "
                            + BuiltInRegistries.ITEM.getKey(item) + " outright");
                    }
                });
                // Selling the KNOWLEDGE is the same leak wearing a different hat: a sheet whose
                // recipe makes a function_only component reopens the manufacturing route the tag
                // exists to close.
                offer.blueprint().ifPresent(set -> {
                    for (Item member : members) {
                        Identifier id = BuiltInRegistries.ITEM.getKey(member);
                        if (set.equals(id)) {
                            broken.add(holder.id().identifier() + " sells the blueprint for "
                                + id + ", which is function_only");
                        }
                    }
                });
            }
            helper.assertTrue(broken.isEmpty(), String.join("; ", broken));
            helper.succeed();
        });

        RCGameTests.test("every_function_only_component_has_a_renewable_teardown_route", 60,
            helper -> {
                ServerLevel level = helper.getLevel();
                List<String> broken = new ArrayList<>();

                for (Item member : members()) {
                    String name = String.valueOf(BuiltInRegistries.ITEM.getKey(member));
                    List<Item> inputs = new ArrayList<>();
                    for (RecipeHolder<TeardownRecipe> holder : level.recipeAccess().recipeMap()
                            .byType(RCRecipeTypes.TEARDOWN.get())) {
                        if (holder.value().everyPossibleOutput().anyMatch(i -> i == member)) {
                            holder.value().input().items()
                                .forEach(h -> inputs.add(h.value()));
                        }
                    }
                    if (inputs.isEmpty()) {
                        broken.add(name + " comes out of no teardown at all. Disabling its recipe "
                            + "without a source does not make it found, it makes it unobtainable");
                        continue;
                    }

                    // THE RENEWABILITY HALF. Bulky Waste is placed by MoundFeature and mounds regrow
                    // (Phase 5, MoundGroundBlock), so a find that comes out of Bulky Waste refills.
                    // A find that came only from a hand-placed structure would not, and would be a
                    // wall rather than a rate limit - which is the one thing separating this from
                    // the #228 complaint.
                    boolean renewable = false;
                    for (Item input : inputs) {
                        if (yieldedByBulkyWaste(input)) {
                            renewable = true;
                        }
                    }
                    if (!renewable) {
                        broken.add(name + " is salvaged only from " + inputs.size() + " input(s) "
                            + inputs.stream()
                                .map(i -> String.valueOf(BuiltInRegistries.ITEM.getKey(i)))
                                .toList()
                            + ", none of which comes out of Bulky Waste. A find-only component "
                            + "whose source does not regrow is a wall, not a gate");
                    }
                }
                helper.assertTrue(broken.isEmpty(), String.join("; ", broken));
                helper.succeed();
            });
    }

    /**
     * True if breaking Bulky Waste can hand over this item.
     *
     * <p>Reads the BUNDLED loot JSON through {@code SortingData}, which is the same reader JEI and the
     * rate census use, and which evaluates {@code neoforge:conditions} and strip modifiers itself - so
     * a mod-gated find is not counted as a route in a default install.
     */
    private static boolean yieldedByBulkyWaste(Item find) {
        List<com.flatts.recompile.compat.SortingData.Weighted> drops =
            com.flatts.recompile.compat.SortingData.outputs(
                com.flatts.recompile.compat.SortingData.BULKY);
        // Fail LOUD on an empty read rather than quietly reporting "not renewable": a moved file
        // would otherwise read as a design violation and send the next reader after the data.
        if (drops.isEmpty()) {
            throw new IllegalStateException("bulky_waste read as empty, so this check would call "
                + "every find non-renewable");
        }
        return drops.stream().anyMatch(w -> w.stack().getItem() == find);
    }

}

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
            java.util.Set<String> unreadable = new java.util.TreeSet<>();
            int readable = 0;

            for (RecipeHolder<?> holder : level.getServer().getRecipeManager().recipeMap().values()) {
                List<Item> results = resultsOf(holder.value(), level);
                if (results == null) {
                    // The type's results cannot be read at all. Recorded rather than skipped - see
                    // the assertion below.
                    unreadable.add(String.valueOf(BuiltInRegistries.RECIPE_TYPE
                        .getKey(holder.value().getType())));
                    continue;
                }
                readable++;
                for (Item result : results) {
                    if (members.contains(result)) {
                        broken.add(holder.id().identifier() + " crafts "
                            + BuiltInRegistries.ITEM.getKey(result)
                            + ", which is function_only and must be salvaged instead");
                    }
                }
            }

            // COUNTS READABLE RESULTS, NOT RECIPES. Incrementing before resolving would keep this
            // number at ~1500 even if every type stopped resolving, which is the failure it claims
            // to detect - MarketTests counts after the same check for the same reason.
            helper.assertTrue(readable > 100,
                "only " + readable + " recipes had readable results - the sweep is blind and this "
                    + "test proves nothing");

            // THE BLINDNESS ITSELF FAILS, which is FoundNotCraftedTests' pattern and the reason this
            // file needed rewriting. The first version special-cased blueprint recipes only, so
            // `recompile:separating` and `recompile:pulverizing` - both of which return an empty
            // display() - were silently unexamined. Separating Mechanical Waste into a Motor is the
            // most plausible future recipe there is, since Mechanical Waste is already a Motor
            // source, and it would have passed.
            helper.assertTrue(unreadable.isEmpty(),
                "these recipe types do not expose their results to this sweep, so it is silently "
                    + "blind to them - read them in resultsOf() or add the type to "
                    + "RESULT_NOT_READABLE with a reason: " + unreadable);

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
                //
                // RESOLVED THROUGH THE RECIPE, not by comparing the set id to the item id. Those two
                // happen to share a path for the Motor - `recompile:motor` names both the set and
                // the item - so a name comparison passes here by coincidence and would miss a set
                // called anything else whose recipe yields a function_only part.
                offer.blueprint().ifPresent(set -> {
                    // TWO CHECKS, AND NEITHER ALONE IS ENOUGH. Resolving the set through its recipe
                    // catches a set named anything at all whose recipe yields a function_only part.
                    // But after this PR the Motor's recipe is DELETED, so that resolution finds
                    // nothing and an offer for `recompile:motor` sails through - which is exactly
                    // the state a regression would restore, and the negative control caught this
                    // file passing it. The id comparison covers that second case.
                    for (RecipeHolder<com.flatts.recompile.content.recipe.BlueprintCraftingRecipe> bp
                            : helper.getLevel().recipeAccess().recipeMap()
                                .byType(RCRecipeTypes.BLUEPRINT_CRAFTING.get())) {
                        if (!bp.value().blueprint().equals(set)) {
                            continue;
                        }
                        Item made = bp.value().result().item();
                        if (members.contains(made)) {
                            broken.add(holder.id().identifier() + " sells the blueprint " + set
                                + ", whose recipe makes " + BuiltInRegistries.ITEM.getKey(made)
                                + ", which is function_only");
                        }
                    }
                    for (Item member : members) {
                        if (set.equals(BuiltInRegistries.ITEM.getKey(member))) {
                            broken.add(holder.id().identifier() + " sells the blueprint " + set
                                + ", which names the function_only item "
                                + BuiltInRegistries.ITEM.getKey(member)
                                + ". Its recipe is gone, so the sheet would craft nothing anyway");
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
                    // A teardown is the headline route but not the only legitimate one: an item
                    // that drops straight out of a pull stream is just as found. What is fatal is
                    // having NEITHER, which is the "disabling a recipe without adding a source makes
                    // it unobtainable" failure FoundNotCraftedTests already names.
                    if (inputs.isEmpty() && !renewablySorted(member)) {
                        broken.add(name + " comes out of no teardown and no pull stream. Disabling "
                            + "its recipe without a source does not make it found, it makes it "
                            + "unobtainable");
                        continue;
                    }

                    // THE RENEWABILITY HALF. Everything checked here is placed by MoundFeature and
                    // mounds regrow (Phase 5, MoundGroundBlock), so any of these routes refills. A
                    // find that came only from a hand-placed structure would not, and would be a
                    // wall rather than a rate limit - which is the one thing separating this from
                    // the #228 complaint.
                    //
                    // A DIRECT SORTING ROUTE COUNTS, and the first version of this test did not let
                    // it: it demanded a teardown whose INPUT was in Bulky Waste, so the Motor's own
                    // other two sources - Mechanical Waste out in the demolition yard, and sewer
                    // crates - counted for nothing. A future member sourced the way the Motor
                    // actually is would have been failed as "a wall" while being renewable.
                    boolean renewable = renewablySorted(member);
                    for (Item input : inputs) {
                        if (renewablySorted(input)) {
                            renewable = true;
                        }
                    }
                    if (!renewable) {
                        broken.add(name + " has no renewable source: neither it nor any of its "
                            + "teardown inputs " + inputs.stream()
                                .map(i -> String.valueOf(BuiltInRegistries.ITEM.getKey(i)))
                                .toList()
                            + " comes out of a pull stream or Bulky Waste. A find-only component "
                            + "whose source does not regrow is a wall, not a gate");
                    }
                }
                helper.assertTrue(broken.isEmpty(), String.join("; ", broken));
                helper.succeed();
            });
    }

    /**
     * True if any renewable source can hand this item over.
     *
     * <p>All five are placed by {@code MoundFeature} or are {@code SortableBlock}s standing on mound
     * country, so all five refill. Reads the BUNDLED loot JSON through {@code SortingData}, the same
     * reader JEI and the rate census use, which evaluates {@code neoforge:conditions} and strip
     * modifiers itself - so a mod-gated find is not counted as a route in a default install.
     */
    private static boolean renewablySorted(Item find) {
        String[] streams = {
            com.flatts.recompile.compat.SortingData.BULKY,
            com.flatts.recompile.compat.SortingData.HOUSEHOLD,
            com.flatts.recompile.compat.SortingData.BAG,
            com.flatts.recompile.compat.SortingData.MECHANICAL,
            com.flatts.recompile.compat.SortingData.RUBBLE,
        };
        int seen = 0;
        for (String stream : streams) {
            List<com.flatts.recompile.compat.SortingData.Weighted> drops =
                com.flatts.recompile.compat.SortingData.outputs(stream);
            seen += drops.size();
            if (drops.stream().anyMatch(w -> w.stack().getItem() == find)) {
                return true;
            }
        }
        // Fail LOUD on an empty read rather than quietly reporting "not renewable": a moved file
        // would otherwise read as a design violation and send the next reader after the data.
        if (seen == 0) {
            throw new IllegalStateException("every pull stream read as empty, so this check would "
                + "call every find non-renewable");
        }
        return false;
    }

    /**
     * Types whose results genuinely cannot be read, each with the reason.
     *
     * <p>Same shape and same bar as {@code FoundNotCraftedTests.RESULT_NOT_READABLE}: a type belongs
     * here only when it has no fixed result BY CONSTRUCTION, never because reading it is awkward.
     * Adding one to dodge a failure is how this sweep goes quietly blind.
     */
    private static final java.util.Set<String> RESULT_NOT_READABLE = java.util.Set.of(
        // Takes an object apart. Its outputs are materials and are the very route this tag requires,
        // so a teardown producing a function_only item is the intended state rather than a leak.
        "recompile:teardown",
        // One line of shop stock, matched against nothing. Covered by its own test below, which is
        // the only place that can see it.
        "recompile:market_offer",
        // The result is read off a Blueprint sitting in the grid, so there is no fixed result to
        // compare. It only ever produces a spawn egg.
        "recompile:spawn_egg_crafting",
        // Fragments assemble into a Blueprint and nothing else.
        "recompile:fragment_assembly",
        // One rung of the freight ladder: a manifest of what a tier DEMANDS, matched against
        // nothing and producing nothing. Caught by this very assertion on its first run, which is
        // the argument for the assertion existing.
        "recompile:freight_phase");

    /**
     * Every item this recipe can produce, or {@code null} if this sweep cannot tell.
     *
     * <p><b>Three modded types return an empty {@code display()}</b> and so are invisible to the
     * ordinary route: {@code blueprint_crafting} (its placementInfo is NOT_PLACEABLE),
     * {@code separating} and {@code pulverizing} (both override {@code isSpecial()}). Each is read
     * through its own accessor here. Returning null rather than an empty list is the point: an empty
     * list reads as "makes nothing" and passes, null reads as "I could not look" and fails.
     */
    private static List<Item> resultsOf(net.minecraft.world.item.crafting.Recipe<?> recipe,
            ServerLevel level) {
        List<Item> out = new ArrayList<>();
        if (recipe instanceof com.flatts.recompile.content.recipe.BlueprintCraftingRecipe blueprint) {
            out.add(blueprint.result().item());
            return out;
        }
        if (recipe instanceof com.flatts.recompile.content.recipe.SeparatingRecipe separating) {
            separating.results().forEach(r -> out.add(r.item()));
            separating.byproducts().forEach(r -> out.add(r.item()));
            return out;
        }
        if (recipe instanceof com.flatts.recompile.content.recipe.PulverizingRecipe pulverizing) {
            out.add(pulverizing.result().item());
            return out;
        }
        for (var display : recipe.display()) {
            for (ItemStack stack : display.result().resolveForStacks(
                    net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(level))) {
                out.add(stack.getItem());
            }
        }
        if (out.isEmpty()) {
            String type = String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()));
            // A VANILLA special recipe (armour dyeing, map cloning, firework assembly) computes its
            // result and legitimately shows none. Those are minecraft: types and are not this mod's
            // to fix; a MODDED type reaching here is a real hole.
            if (RESULT_NOT_READABLE.contains(type) || type.startsWith("minecraft:")) {
                return out;
            }
            return null;
        }
        return out;
    }

}

package com.flatts.recompile.gametest;

import com.flatts.recompile.content.item.BlueprintItem;
import com.flatts.recompile.content.recipe.BlueprintCraftingRecipe;
import com.flatts.recompile.content.recipe.MarketOfferRecipe;
import com.flatts.recompile.content.recipe.TeardownRecipe;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
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
                    // everyPossibleOutput, not results: a component declared in a pool is still
                    // salvaged. Reading one field made the invariant a fact about JSON layout.
                    if (holder.value().everyPossibleOutput().anyMatch(i -> i == component)) {
                        salvaged = true;
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
         * REWRITTEN 2026-08-12, owner ruling: "our new rules override the old rules".
         *
         * This used to demand that each gated component be a GUARANTEED result of some teardown -
         * one object, one component, no roll. The Fridge is deliberately a lottery: it holds a
         * compressor motor, a refrigerant pump and an interior bulb, and which one you recover is
         * the draw. Under the old rule that is illegal, and the rule loses.
         *
         * What survives is the reason the rule existed. It was never "you must know in advance which
         * component"; it was "you must not take an object apart and come away with none", because the
         * blueprint route costs four of the same teardown and so cannot rescue an empty streak. A
         * component pool with no filler entry keeps that promise exactly - one component, every time.
         *
         * So the assertion moves from the component to the TEARDOWN: any pool that can produce a
         * gated component must be certain to produce one of them.
         */
        RCGameTests.test("a_component_teardown_always_yields_a_component", 20, helper -> {
            List<String> rolled = new ArrayList<>();
            for (RecipeHolder<TeardownRecipe> holder : helper.getLevel().recipeAccess()
                    .recipeMap().byType(RCRecipeTypes.TEARDOWN.get())) {
                for (TeardownRecipe.Pool pool : holder.value().pools()) {
                    boolean hasComponent = pool.entries().stream()
                        .anyMatch(e -> e.item().isPresent() && gatedComponents().contains(e.item().get()));
                    if (!hasComponent) {
                        continue;
                    }
                    boolean everyEntryIsAComponent = pool.entries().stream()
                        .allMatch(e -> e.item().isPresent() && gatedComponents().contains(e.item().get()));
                    if (!everyEntryIsAComponent || pool.rolls() < 1) {
                        rolled.add(holder.id() + " can draw a blank where a component should be");
                    }
                }
            }
            // extras is the other door into the same failure. A gated component written as an
            // extra rolls its own independent chance, so the object can come apart and give you
            // none - which is exactly what this test forbids, and the pool loop above would never
            // look at it. The old results-based test caught this by accident; say it out loud.
            for (RecipeHolder<TeardownRecipe> holder : helper.getLevel().recipeAccess()
                    .recipeMap().byType(RCRecipeTypes.TEARDOWN.get())) {
                for (TeardownRecipe.ChanceResult extra : holder.value().extras()) {
                    if (gatedComponents().contains(extra.item()) && extra.chance() < 1.0F) {
                        rolled.add(holder.id() + " offers "
                            + BuiltInRegistries.ITEM.getKey(extra.item())
                            + " as a chance extra, not a certainty");
                    }
                }
            }

            helper.assertTrue(rolled.isEmpty(),
                "a teardown that offers a gated component must be certain to hand one over - a "
                    + "filler slot beside a component, or a component written as a chance extra, "
                    + "means tearing the object apart can give you nothing, and four of the same "
                    + "teardown is what the blueprint costs: " + rolled);
            helper.succeed();
        });

        // A TEARDOWN MAY NOT HAND OUT A FREE WATER SOURCE UNLESS SOMEONE ARGUED FOR IT.
        //
        // minecraft:ice broken without silk touch on solid ground leaves a water SOURCE block, and
        // two sources are infinite water - a route to water that needs no Rain Collector and no
        // bucket. That is a real hole in the P1.10 economy and it must never be opened by accident.
        //
        // The Dead Fridge opens it DELIBERATELY. It shipped as packed ice for one commit on exactly
        // the reasoning above and the owner overruled it (2026-08-12): "I know it creates a water
        // source. Its fine gameplay." So the rule is not "never" - it is "never without a decision",
        // and the decision is recorded as an allowlist entry rather than by deleting the check.
        // Same shape as RegistryCompletenessTests' NO_LOOT_TABLE / NO_ITEM_FORM lists: add a
        // justified entry, never loosen the check.
        //
        // What the allowlist does NOT cover is scale, which is why leachate_is_not_water is
        // untouched. Water off a fridge costs a find, a prybar and a 1-in-4 draw; leachate answering
        // yes to being water would make every pool on the map a tap.
        //
        // Keyed on IceBlock, exactly the family whose playerDestroy creates water (ice and
        // frosted_ice); packed and blue ice are plain blocks and were never the problem.
        RCGameTests.test("no_teardown_hands_out_an_unsanctioned_water_source", 20, helper -> {
            // id -> the item it is allowed to give. Both halves are checked: a recipe on this list
            // that stops producing water, or produces a DIFFERENT water source, is a stale
            // exemption and must be re-argued rather than inherited.
            Map<String, Item> sanctioned = Map.of(
                "recompile:fridge", Items.ICE);

            List<String> taps = new ArrayList<>();
            for (RecipeHolder<TeardownRecipe> holder : helper.getLevel().recipeAccess()
                    .recipeMap().byType(RCRecipeTypes.TEARDOWN.get())) {
                String id = holder.id().identifier().toString();
                holder.value().everyPossibleOutput().forEach(item -> {
                    boolean isTap = (item instanceof BlockItem block
                            && block.getBlock() instanceof IceBlock)
                        || item == Items.WATER_BUCKET;
                    if (isTap && sanctioned.get(id) != item) {
                        taps.add(id + " -> " + BuiltInRegistries.ITEM.getKey(item));
                    }
                });
            }
            helper.assertTrue(taps.isEmpty(),
                "these teardown outputs are a free water source and are not on the sanctioned list "
                    + "- break the placed block without silk touch and you have a water source "
                    + "block, two of which are infinite water, with no Rain Collector and no "
                    + "bucket. Add a justified entry if that is intended: " + taps);

            // The list must not outlive what it exempts. A sanctioned recipe that no longer
            // produces its water source is an exemption nobody is checking any more.
            for (var entry : sanctioned.entrySet()) {
                boolean stillProduces = false;
                for (RecipeHolder<TeardownRecipe> holder : helper.getLevel().recipeAccess()
                        .recipeMap().byType(RCRecipeTypes.TEARDOWN.get())) {
                    if (holder.id().identifier().toString().equals(entry.getKey())
                            && holder.value().everyPossibleOutput().anyMatch(i -> i == entry.getValue())) {
                        stillProduces = true;
                    }
                }
                helper.assertTrue(stillProduces, entry.getKey() + " is allowlisted to give "
                    + BuiltInRegistries.ITEM.getKey(entry.getValue())
                    + " and no longer does - drop the stale exemption");
            }
            helper.succeed();
        });

        /*
         * The other half of that ruling: every gated component must still be REACHABLE by salvage.
         * Losing the lottery on a given fridge is fine; having no object in the world that can ever
         * produce a Bulb is not, and is what deleting the Light Fixture would have caused unnoticed.
         */
        RCGameTests.test("every_gated_component_is_drawable_from_something", 20, helper -> {
            List<String> orphaned = new ArrayList<>();
            for (Item component : gatedComponents()) {
                boolean drawable = false;
                for (RecipeHolder<TeardownRecipe> holder : helper.getLevel().recipeAccess()
                        .recipeMap().byType(RCRecipeTypes.TEARDOWN.get())) {
                    if (holder.value().everyPossibleOutput().anyMatch(i -> i == component)) {
                        drawable = true;
                    }
                }
                if (!drawable) {
                    orphaned.add(String.valueOf(BuiltInRegistries.ITEM.getKey(component)));
                }
            }
            helper.assertTrue(orphaned.isEmpty(),
                "no teardown in the world can produce these, so salvage cannot reach them at all "
                    + "and the blueprint has nothing to be taught by: " + orphaned);
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
                    boolean yields = holder.value().everyPossibleOutput()
                        .anyMatch(i -> i == component);
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
        RCGameTests.test("every_shipped_blueprint_has_a_name_a_recipe_and_a_route", 20, helper -> {
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
                // OR SOLD. Until the market every blueprint was earned by tearing down the object
                // it came from, so this asked for a teacher and that was the whole of it. A sheet
                // may now be BOUGHT instead, and one of them (the powder snow bucket) has no
                // teaching object because the thing it makes does not exist in this world to be
                // found. What still has to hold is that a shipped sheet has SOME route, or it is a
                // creative-tab item wearing a progression system's clothes.
                boolean sold = false;
                for (RecipeHolder<MarketOfferRecipe> holder : helper.getLevel().recipeAccess()
                        .recipeMap().byType(RCRecipeTypes.MARKET_OFFER.get())) {
                    if (holder.value().blueprint().filter(blueprint::equals).isPresent()) {
                        sold = true;
                    }
                }
                if (!taught && !sold) {
                    broken.add(blueprint + " is taught by no teardown and sold by no terminal, so "
                        + "it can only ever be a creative-tab item");
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

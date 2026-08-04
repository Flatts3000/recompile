package com.flatts.recompile.gametest;

import com.flatts.recompile.compat.SortingData;
import com.flatts.recompile.content.recipe.TeardownRecipe;
import com.flatts.recompile.registry.RCRecipeTypes;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/**
 * Found tools (#113): some tools are recovered from the dump rather than crafted, and a found one is
 * <b>used</b>.
 *
 * <p>Two of them - shears and flint and steel - are already craftable once the player has iron, so
 * finding them is an <b>earlier access</b> change. The spyglass is different in kind: it needs an
 * amethyst shard, and there is no amethyst in this world outside the Separator, so a found spyglass is
 * the only route to one. That is why it sits in the Bulky Waste windfall tier rather than in small
 * scrap - the tier that exists for capabilities this world cannot produce.
 */
final class FoundToolTests {

    /** The three named in #113. The pattern generalises; the list is deliberately not open-ended. */
    private static final List<Item> FOUND = List.of(
        Items.SHEARS, Items.FLINT_AND_STEEL, Items.SPYGLASS);

    private static final String HOUSEHOLD_JSON =
        "/data/recompile/loot_table/gameplay/household_pulls.json";
    private static final String WINDFALL_JSON =
        "/data/recompile/loot_table/gameplay/bulky_windfall.json";

    private FoundToolTests() {
    }

    static void register() {
        // Each is reachable, and in the stream it was placed in rather than whichever one happened to
        // be edited last. A tool that fell out of its table is a capability quietly removed from the
        // game - and for the spyglass, the only one there is.
        RCGameTests.test("every_found_tool_is_reachable", 20, helper -> {
            List<String> missing = new ArrayList<>();
            for (Item tool : List.of(Items.SHEARS, Items.FLINT_AND_STEEL)) {
                if (SortingData.outputs(SortingData.HOUSEHOLD).stream()
                        .noneMatch(w -> w.stack().is(tool))) {
                    missing.add(tool + " is not a household pull");
                }
            }
            if (SortingData.outputs(SortingData.BULKY).stream()
                    .noneMatch(w -> w.stack().is(Items.SPYGLASS))) {
                missing.add("the spyglass is not a Bulky Waste find, and nothing else grants one");
            }
            helper.assertTrue(missing.isEmpty(), "unreachable found tools: " + missing);
            helper.succeed();
        });

        // A FOUND TOOL IS USED. This is the dial #113 calls the difference between a real tier and a
        // teaser. A set_damage function dropped from a table would hand out pristine tools and nothing
        // else would ever complain, because a pristine tool works perfectly - it is only the economy
        // that quietly changes.
        RCGameTests.test("a_found_tool_arrives_used", 20, helper -> {
            List<String> pristine = new ArrayList<>();
            for (Item tool : List.of(Items.SHEARS, Items.FLINT_AND_STEEL)) {
                if (!hasSetDamage(HOUSEHOLD_JSON, tool)) {
                    pristine.add(tool.toString());
                }
            }
            if (!hasSetDamage(WINDFALL_JSON, Items.SPYGLASS)) {
                pristine.add(Items.SPYGLASS.toString());
            }
            helper.assertTrue(pristine.isEmpty(),
                "found tools that come out undamaged: " + pristine
                    + ". A found tool is a used tool; a pristine one is a crafted tool you did not pay "
                    + "for");
            helper.succeed();
        });

        // NO FOUND TOOL TEARS DOWN, and for the spyglass that is forced rather than chosen: it is two
        // copper and one AMETHYST SHARD, and amethyst exists in this world only downstream of the
        // Separator. A teardown recipe would hand a player the gem tier's output out of a Bulky Waste
        // find. Teardown is an allowlist, so the containment is simply never writing the recipe - and
        // this is what makes that a decision rather than an oversight nobody wrote down.
        RCGameTests.test("no_found_tool_can_be_torn_down", 20, helper -> {
            List<String> leaks = new ArrayList<>();
            int checked = 0;
            for (RecipeHolder<TeardownRecipe> holder : helper.getLevel().recipeAccess()
                    .recipeMap().byType(RCRecipeTypes.TEARDOWN.get())) {
                checked++;
                for (Item tool : FOUND) {
                    if (holder.value().matches(new SingleRecipeInput(new ItemStack(tool)),
                            helper.getLevel())) {
                        leaks.add(tool + " via " + holder.id());
                    }
                }
            }
            helper.assertTrue(checked > 0,
                "no teardown recipes were found at all - discovery is broken, so this would pass "
                    + "against a spyglass that tears into an amethyst shard");
            helper.assertTrue(leaks.isEmpty(),
                "found tools with a teardown recipe (" + leaks.size() + "): " + leaks
                    + ". The spyglass one would bypass the whole gem tier");
            helper.succeed();
        });
    }

    /**
     * Whether this item's entry in the bundled table carries a {@code set_damage} function.
     *
     * <p>Read off the JSON rather than by rolling the table: a roll only tells you what one pull
     * happened to be, and an undamaged result is indistinguishable from an unlucky one.
     */
    private static boolean hasSetDamage(String resourcePath, Item tool) {
        String id = BuiltInRegistries.ITEM.getKey(tool).toString();
        try (InputStream in = FoundToolTests.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return false;
            }
            JsonObject root = JsonParser.parseReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement poolEl : root.getAsJsonArray("pools")) {
                for (JsonElement entryEl : poolEl.getAsJsonObject().getAsJsonArray("entries")) {
                    JsonObject entry = entryEl.getAsJsonObject();
                    if (!entry.has("name") || !id.equals(entry.get("name").getAsString())
                            || !entry.has("functions")) {
                        continue;
                    }
                    for (JsonElement fn : entry.getAsJsonArray("functions")) {
                        JsonObject function = fn.getAsJsonObject();
                        if (function.has("function")
                                && "minecraft:set_damage".equals(
                                    function.get("function").getAsString())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }
}

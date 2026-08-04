package com.flatts.recompile.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Vanilla items reflavoured to fit the junkyard (#118): a lead is Rope, a bundle is Luggage.
 *
 * <p>This is the repo's <b>first {@code assets/minecraft/} override</b>. The mod has always overridden
 * vanilla <em>data</em> - the sixteen bed recipes, tags - but never vanilla assets, so the thing most
 * worth proving is simply that the override reaches the game at all. A lang file in the wrong namespace
 * loads without complaint and changes nothing.
 *
 * <p>It stays reversible by construction: mod resources sit <b>below</b> resource packs in load order,
 * so any pack or player wanting "Lead" back overrides it in one line.
 */
final class ReflavourTests {

    private static final List<String> DYES = List.of(
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    private ReflavourTests() {
    }

    static void register() {
        // The rename actually lands. NeoForge loads en_us server-side, so a translated name genuinely
        // resolves here - which is the only way to tell an override that works from one sitting in a
        // path nothing reads.
        RCGameTests.test("reflavoured_vanilla_items_are_renamed", 20, helper -> {
            String lead = new ItemStack(Items.LEAD).getHoverName().getString();
            helper.assertTrue("Rope".equals(lead),
                "a lead should read as Rope, got \"" + lead + "\". If this is still Lead the "
                    + "assets/minecraft/lang override is not being loaded at all");

            String bundle = new ItemStack(Items.BUNDLE).getHoverName().getString();
            helper.assertTrue("Luggage".equals(bundle),
                "a bundle should read as Luggage, got \"" + bundle + "\"");

            List<String> wrong = new ArrayList<>();
            for (String dye : DYES) {
                String key = "item.minecraft." + dye + "_bundle";
                String name = Component.translatable(key).getString();
                if (!name.endsWith("Luggage")) {
                    wrong.add(key + " -> \"" + name + "\"");
                }
            }
            helper.assertTrue(wrong.isEmpty(), "dyed bundles not renamed: " + wrong);
            helper.succeed();
        });

        // ALL SEVENTEEN SHARE THREE SPRITES, coloured by a constant tint in each client item
        // definition. That is the whole reason this is 3 PNGs rather than 51 - and it only works if
        // every definition carries a tint, because an untinted one renders the shared desaturated art
        // as-is and reads as a bleached bundle. A dyed Clean Mattress rendering white was diagnosed as
        // exactly this once before.
        RCGameTests.test("every_luggage_definition_carries_a_tint", 20, helper -> {
            List<String> problems = new ArrayList<>();
            List<String> all = new ArrayList<>(List.of("bundle"));
            DYES.forEach(dye -> all.add(dye + "_bundle"));
            for (String id : all) {
                JsonObject root = read("/assets/minecraft/items/" + id + ".json");
                if (root == null) {
                    problems.add(id + " has no client item definition");
                    continue;
                }
                String raw = root.toString();
                if (!raw.contains("minecraft:constant")) {
                    problems.add(id + " has no constant tint - it will render the shared grey art");
                }
                // The composite is what draws the bundle's contents when it is open. Flattening the
                // definition is the easy mistake and costs the feature silently.
                if (!raw.contains("minecraft:bundle/selected_item")) {
                    problems.add(id + " lost its selected_item composite - it will not show contents");
                }
            }
            helper.assertTrue(problems.isEmpty(), "broken luggage definitions: " + problems);
            helper.succeed();
        });
    }

    private static @org.jspecify.annotations.Nullable JsonObject read(String path) {
        try (InputStream in = ReflavourTests.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return JsonParser.parseReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }
}

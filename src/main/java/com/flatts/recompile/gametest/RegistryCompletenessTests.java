package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * The sweep that catches what registering a thing quietly leaves half-done.
 *
 * <p>Every other test here proves a behaviour. These prove <b>coverage</b>: that nothing was added
 * to a registry without the four files it needs beside it. That gap is the mod's most repeated bug,
 * it is always silent, and it never fails a compile - a missing lang key renders as
 * {@code item.recompile.scrap_torch}, a missing client item definition renders as the pink-and-black
 * missing texture, a missing blockstate renders as a purple cube, and a missing loot table means the
 * block deletes itself when broken. Every one of those has actually shipped in this repo.
 *
 * <p>Ported from productive-frogs' {@code ItemNameCompletenessTests} and widened: that one covers
 * item names, and the traps here are 26.1-specific asset wiring on top of them.
 *
 * <p><b>Why a GameTest can see resources at all.</b> A dedicated server never loads
 * {@code assets/}, but the files are still on the classpath in a dev run, so these read them as
 * classpath resources - the same trick {@code SortingData} and {@code TeardownData} use to parse
 * bundled loot and recipe JSON. Lang is different and better: NeoForge loads every mod's
 * {@code en_us} server-side, so a translatable component genuinely resolves here.
 */
final class RegistryCompletenessTests {

    /**
     * Blocks that legitimately ship no loot table of their own.
     *
     * <p>Keep this list short and justified. It exists so the sweep can be strict by default; an
     * entry added to silence a failure rather than to state a fact defeats the whole test.
     */
    private static final List<String> NO_LOOT_TABLE = List.of(
        // The wall form of the scrap torch is placed by the same item as the standing form and
        // drops through it, so a table here would be a second, competing drop.
        "wall_scrap_torch"
    );

    /**
     * Blocks that legitimately have no item form. Same rule as above: each entry is a decision.
     *
     * <p>Note {@code rain_collector_funnel} is deliberately NOT here. It is a dummy cell too, but it
     * is also a craftable component you place by hand, so it keeps its item - which is why this is
     * an explicit list rather than a blanket exemption for {@code MultiblockDummyBlock}.
     */
    private static final List<String> NO_ITEM_FORM = List.of(
        // Formed-only cells: what a Pump and a Copper Pipe BECOME inside an assembled Grass
        // Spreader. They are never held - disband returns the component you placed, not these.
        "grass_spreader_frame",
        "grass_spreader_spigot",
        // A formed cell of the Compost Heap's 2x2x2 cage - disband returns the Machine Frame, not this.
        "compost_cage",
        // The Tree Nursery's formed tank cell - disband returns the Water Tank you placed, not this.
        "tree_nursery_tank"
    );

    private RegistryCompletenessTests() {
    }

    static void register() {
        // The bug productive-frogs hit: a block item registered without the block description
        // prefix points at an item.* key while the lang file only carries block.*, and the item
        // renders as its raw key in every tooltip. Nothing about that fails a build.
        RCGameTests.test("every_item_has_a_translated_name", 20, helper -> {
            List<String> missing = new ArrayList<>();
            forEachModItem((id, item) -> {
                String rendered = new ItemStack(item).getHoverName().getString();
                if (rendered.equals(item.getDescriptionId()) || looksLikeARawKey(rendered)) {
                    missing.add(id + " -> \"" + rendered + "\"");
                }
            });
            report(helper, missing, "items with untranslated names");
        });

        // A block carries its own key, and a block whose item is named can still be nameless in
        // the world - Jade, the death message and the break subtitle all read the BLOCK key.
        RCGameTests.test("every_block_has_a_translated_name", 20, helper -> {
            List<String> missing = new ArrayList<>();
            forEachModBlock((id, block) -> {
                String rendered = block.getName().getString();
                if (rendered.equals(block.getDescriptionId()) || looksLikeARawKey(rendered)) {
                    missing.add(id + " -> \"" + rendered + "\"");
                }
            });
            report(helper, missing, "blocks with untranslated names");
        });

        // 26.1 needs assets/<ns>/items/<id>.json IN ADDITION TO models/item/<id>.json. Miss it and
        // the item is the missing texture - which reads as a texture problem, so the hunt starts in
        // the wrong place. This is the single most repeated omission in the repo.
        RCGameTests.test("every_item_has_a_client_definition", 20, helper -> {
            List<String> missing = new ArrayList<>();
            forEachModItem((id, item) -> {
                if (!resourceExists("/assets/" + id.getNamespace() + "/items/" + id.getPath() + ".json")) {
                    missing.add(id.toString());
                }
            });
            report(helper, missing, "items with no assets/<ns>/items/<id>.json");
        });

        RCGameTests.test("every_block_has_a_blockstate", 20, helper -> {
            List<String> missing = new ArrayList<>();
            forEachModBlock((id, block) -> {
                if (!resourceExists("/assets/" + id.getNamespace() + "/blockstates/" + id.getPath() + ".json")) {
                    missing.add(id.toString());
                }
            });
            report(helper, missing, "blocks with no blockstate JSON");
        });

        // A block with no loot table drops NOTHING - it deletes itself when broken. The failure is
        // total and it is silent, and the water tank already shipped a table naming the wrong item,
        // so this checks presence and the per-block tests check contents.
        RCGameTests.test("every_block_has_a_loot_table", 20, helper -> {
            List<String> missing = new ArrayList<>();
            forEachModBlock((id, block) -> {
                if (NO_LOOT_TABLE.contains(id.getPath())) {
                    return;
                }
                if (!resourceExists("/data/" + id.getNamespace()
                    + "/loot_table/blocks/" + id.getPath() + ".json")) {
                    missing.add(id.toString());
                }
            });
            report(helper, missing, "blocks with no loot table");
        });

        // A block with no item cannot be held, crafted into anything, or put in the creative tab.
        // Some legitimately should not be - but each of those is a decision, so they are named in
        // NO_ITEM_FORM rather than the test being loosened to accommodate them.
        // A model file existing is not the same as it RESOLVING. See checkModelParentsResolve.
        RCGameTests.test("every_item_model_parent_resolves", 20, helper -> {
            checkModelParentsResolve(helper);
            helper.succeed();
        });

        RCGameTests.test("every_client_item_model_resolves", 20, helper -> {
            checkClientItemModelsResolve(helper);
            helper.succeed();
        });

        RCGameTests.test("every_block_has_an_item", 20, helper -> {
            List<String> missing = new ArrayList<>();
            forEachModBlock((id, block) -> {
                if (NO_ITEM_FORM.contains(id.getPath())) {
                    return;
                }
                if (block.asItem() == net.minecraft.world.item.Items.AIR) {
                    missing.add(id.toString());
                }
            });
            report(helper, missing, "blocks with no item form");
        });
    }

    /** True if the string is a raw translation key rather than a display name. */
    /**
     * Every model's {@code parent} in this mod's namespace must exist.
     *
     * <p>The other checks confirm a model FILE is present; none of them followed the parent chain, so a
     * model could point at a parent that had been renamed or deleted and every test still passed. The block
     * kept rendering (its blockstate names a different model) while the ITEM silently became the
     * pink-and-black missing texture - visible in inventories, JEI and Jade, and nowhere in a test.
     *
     * <p>Caught exactly that when Rubble and Reinforced Concrete moved to numbered variants: their
     * {@code models/block/<id>.json} was replaced by {@code <id>_0..2}, and the item models still parented
     * to the old path.
     */
    private static void checkModelParentsResolve(GameTestHelper helper) {
        List<String> broken = new ArrayList<>();
        forEachModItem((id, item) -> {
            String json = readResource("/assets/" + id.getNamespace() + "/models/item/"
                + id.getPath() + ".json");
            if (json == null) {
                return;   // absence is already reported by the item-form check
            }
            Matcher m = PARENT.matcher(json);
            while (m.find()) {
                String parent = m.group(1);
                if (!parent.startsWith(Recompile.MOD_ID + ":")) {
                    continue;   // vanilla parents are not ours to verify
                }
                String path = parent.substring(parent.indexOf(':') + 1);
                if (!resourceExists("/assets/" + Recompile.MOD_ID + "/models/" + path + ".json")) {
                    broken.add(id.getPath() + " -> " + parent);
                }
            }
        });
        report(helper, broken, "item models whose parent does not exist");
    }

    /**
     * Every model an {@code assets/<ns>/items/<id>.json} client definition names must exist.
     *
     * <p>The sibling parent check walks {@code models/item/<id>.json} upward. Nothing walked the step
     * BEFORE it: the client item definition is what points at that model in the first place, so a
     * definition naming a model that had been renamed away left an item rendering as the pink-and-black
     * missing texture with the model file itself perfectly intact and its parents all resolving.
     *
     * <p>Caught in playtest, not here: the Rubble to Stone Rubble rename (#61) moved
     * {@code models/item/rubble.json} to {@code stone_rubble.json} and left the definition pointing at
     * {@code recompile:item/rubble}. The block was fine - blockstates name their models directly - so it
     * only showed on the dropped item and in inventories.
     */
    private static void checkClientItemModelsResolve(GameTestHelper helper) {
        List<String> broken = new ArrayList<>();
        forEachModItem((id, item) -> {
            String json = readResource("/assets/" + id.getNamespace() + "/items/" + id.getPath() + ".json");
            if (json == null) {
                return;   // absence is already reported by the client-definition check
            }
            Matcher m = MODEL_REF.matcher(json);
            while (m.find()) {
                String model = m.group(1);
                if (!model.startsWith(Recompile.MOD_ID + ":")) {
                    continue;   // vanilla models are not ours to verify
                }
                String path = model.substring(model.indexOf(':') + 1);
                if (!resourceExists("/assets/" + Recompile.MOD_ID + "/models/" + path + ".json")) {
                    broken.add(id.getPath() + " -> " + model);
                }
            }
        });
        report(helper, broken, "client item definitions naming a model that does not exist");
    }

    private static final Pattern PARENT = Pattern.compile("\"parent\"\s*:\s*\"([^\"]+)\"");

    /**
     * A string-valued {@code "model"} field. Deliberately matches every occurrence rather than one: a
     * definition may name several models (ranged, composite, condition), and each must resolve. The
     * {@code "type": "minecraft:model"} sibling key does not match - only the value of {@code model} does.
     */
    private static final Pattern MODEL_REF = Pattern.compile("\"model\"\s*:\s*\"([^\"]+)\"");

    /** A classpath resource's text, or null when it is absent. */
    private static String readResource(String path) {
        try (java.io.InputStream in = RegistryCompletenessTests.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private static boolean looksLikeARawKey(String rendered) {
        return (rendered.startsWith("item.") || rendered.startsWith("block."))
            && rendered.contains(".")
            && !rendered.contains(" ");
    }

    private static boolean resourceExists(String path) {
        return RegistryCompletenessTests.class.getResource(path) != null;
    }

    private interface ItemCheck {
        void accept(Identifier id, Item item);
    }

    private interface BlockCheck {
        void accept(Identifier id, Block block);
    }

    private static void forEachModItem(ItemCheck check) {
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (Recompile.MOD_ID.equals(id.getNamespace())) {
                check.accept(id, item);
            }
        }
    }

    private static void forEachModBlock(BlockCheck check) {
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (Recompile.MOD_ID.equals(id.getNamespace())) {
                check.accept(id, block);
            }
        }
    }

    /** Fail with the whole list, capped - a sweep that names one offender wastes the sweep. */
    private static void report(GameTestHelper helper, List<String> missing, String what) {
        if (!missing.isEmpty()) {
            helper.fail(what + " (" + missing.size() + "): "
                + String.join(", ", missing.subList(0, Math.min(15, missing.size()))));
            return;
        }
        helper.succeed();
    }
}

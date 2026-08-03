package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.registry.RCCreativeTabs;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
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

    /**
     * Vanilla model parents this mod is allowed to inherit from, each one confirmed present in the
     * 26.1.2 client jar.
     *
     * <p><b>Why a list and not an existence check.</b> Vanilla assets are not on the classpath of either
     * test layer - not the GameTest server (a dedicated server ships no {@code assets/}) and not the JUnit
     * run. Both were probed rather than assumed. So a test cannot ask whether a vanilla model exists, and
     * the honest substitute is to require that every vanilla parent be one somebody has checked against
     * the jar for this MC version. Adding a new one means opening the jar; that is the point.
     *
     * <p>This exists because 26.1 <b>deleted</b> {@code minecraft:item/template_spawn_egg} - spawn eggs
     * stopped being a tinted two-layer template and became ordinary {@code item/generated} with a PNG
     * each. The Roach's egg still parented to it and rendered as the missing model. The old check skipped
     * every parent outside this mod's namespace on the reasoning that vanilla is not ours to verify;
     * vanilla is stable within a version, and this mod moves between them.
     */
    /**
     * Items deliberately absent from the creative tab, with the reason.
     *
     * <p>Add a justified entry, never loosen the check - the same rule the loot and item-form lists in
     * this class already follow.
     */
    private static final Set<String> NOT_IN_TAB = Set.of(
        // A blank Blueprint and a blank Idea Fragment are inert by design; the tab offers the real
        // ones, already carrying their component, so a creative pull is a working sheet.
        "blueprint",
        "idea_fragment");

    private static final Set<String> VANILLA_PARENTS = Set.of(
        "block/block",
        "block/cross",
        "block/cube",
        "block/cube_all",
        "block/cube_bottom_top",
        "block/inner_stairs",
        "block/orientable",
        "block/orientable_with_bottom",
        "block/outer_stairs",
        "block/slab",
        "block/slab_top",
        "block/stairs",
        "block/template_daylight_detector",
        "block/template_glass_pane_noside",
        "block/template_glass_pane_noside_alt",
        "block/template_glass_pane_post",
        "block/template_glass_pane_side",
        "block/template_glass_pane_side_alt",
        "block/template_wall_post",
        "block/template_wall_side",
        "block/template_wall_side_tall",
        "block/torch",
        "block/wall_inventory",
        "block/wall_torch",
        "item/generated",
        "item/handheld"
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

        // EVERY ITEM MUST BE IN THE CREATIVE TAB, ONCE.
        //
        // The tab's order is what JEI and EMI show in their ingredient panel, so it is a product
        // surface rather than a convenience - and it is the one list nothing else checks. An item left
        // out is invisible in creative and in JEI while working perfectly in every other test here; an
        // item added twice is a duplicate in the panel that reads as two different things.
        //
        // Deliberate exceptions live in NOT_IN_TAB below, each with a reason.
        RCGameTests.test("every_mod_item_is_in_the_creative_tab", 20, helper -> {
            List<Item> shown = new ArrayList<>();
            var parameters = new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(
                net.minecraft.world.flag.FeatureFlags.REGISTRY.allFlags(), true,
                helper.getLevel().registryAccess());
            RCCreativeTabs.RECOMPILE_TAB.get().buildContents(parameters);
            for (ItemStack stack : RCCreativeTabs.RECOMPILE_TAB.get().getDisplayItems()) {
                shown.add(stack.getItem());
            }
            helper.assertTrue(shown.size() > 50,
                "only " + shown.size() + " items were built - the tab did not populate, so this would "
                    + "pass against an empty one");

            List<String> missing = new ArrayList<>();
            forEachModItem((id, item) -> {
                if (!shown.contains(item) && !NOT_IN_TAB.contains(id.getPath())) {
                    missing.add(id.toString());
                }
            });
            report(helper, missing, "mod items absent from the creative tab");
        });

        // There is deliberately NO duplicate test to go with this. One was written and dropped after it
        // was driven RED and did not fail: vanilla's tab builder collects into a set with ItemStack
        // equality, so adding the same item twice collapses to one entry before anything can see it.
        // A test that cannot fail is worse than no test, because it reads as coverage.

        // BIOMES ARE A DATAPACK REGISTRY, which is exactly why they were missed. This sweep walks items
        // and blocks off BuiltInRegistries; biomes are not there, so both of the mod's shipped biomes
        // went the whole project with no translation key and nothing said a word.
        //
        // It surfaced only when FTB Chunks was added to the Trashlands pack and its minimap started
        // printing "Biome: biome.recompile.household_sprawl" under the compass (#107). Nothing in the
        // mod alone renders a biome name - not the F3 screen a developer rarely reads, not any GUI here
        // - so the gap was invisible for as long as nobody looked at it through another mod.
        //
        // Read through the level's registry access rather than a built-in one, because a datapack
        // registry only exists once a world is loaded.
        RCGameTests.test("every_biome_has_a_translated_name", 20, helper -> {
            var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
            List<String> missing = new ArrayList<>();
            int checked = 0;
            for (var holder : biomes.listElements().toList()) {
                Identifier id = holder.key().identifier();
                if (!Recompile.MOD_ID.equals(id.getNamespace())) {
                    continue;
                }
                checked++;
                String key = "biome." + id.getNamespace() + "." + id.getPath();
                String rendered = Component.translatable(key).getString();
                if (rendered.equals(key)) {
                    missing.add(id + " -> \"" + rendered + "\"");
                }
            }
            helper.assertTrue(checked > 0,
                "no mod biomes were found - discovery is broken, so this would pass against a biome "
                    + "with no name at all");
            report(helper, missing, "biomes with untranslated names");
        });

        // JADE'S CONFIG TRANSLATIONS ARE NOT COSMETIC - a missing one is an AssertionError thrown at
        // the client on the title screen, and Minecraft's recovery is "Caught error loading
        // resourcepacks, removing all selected resourcepacks". So the visible symptom is the player's
        // resource packs silently turning themselves off, which points nowhere near a lang file. It
        // shipped that way with the Separator (2026-08-03) and only surfaced by reading a boot log.
        //
        // Same family as the biome gap above: a key this mod alone never renders, so nothing in the
        // GameTest or JUnit layers had any reason to touch it.
        //
        // Discovery walks the compiled package rather than a hand-written list, because a list is what
        // let a third teardown recipe go unseen by every viewer. The UID is derived from the class name
        // by the convention every provider follows; a provider that breaks the convention fails here,
        // which is the correct outcome rather than a silent miss. Only display providers get a config
        // entry - an IServerDataProvider has no toggle - which is what the *DataProvider filter means.
        RCGameTests.test("every_jade_provider_has_a_config_translation", 20, helper -> {
            List<String> providers = jadeDisplayProviders();
            helper.assertTrue(!providers.isEmpty(),
                "no Jade providers were discovered - discovery is broken, so this would pass against a "
                    + "provider with no config translation at all");

            String prefix = "config.jade.plugin_" + Recompile.MOD_ID + ".";
            List<String> missing = new ArrayList<>();
            Set<String> expected = new java.util.HashSet<>();
            for (String provider : providers) {
                String uid = snakeCase(provider.substring(0, provider.length() - "Provider".length()));
                expected.add(uid);
                if (Component.translatable(prefix + uid).getString().equals(prefix + uid)) {
                    missing.add(provider + " -> " + prefix + uid);
                }
            }

            // And the other direction, so a deleted provider cannot leave a key behind that makes the
            // forward check look healthier than it is.
            for (String key : langKeysStartingWith(prefix)) {
                String uid = key.substring(prefix.length());
                if (!expected.contains(uid)) {
                    missing.add(key + " -> no such provider");
                }
            }
            report(helper, missing, "Jade config translation mismatches");
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

        // A model file existing is not the same as it RESOLVING. See checkModelParentsResolve.
        RCGameTests.test("every_model_parent_resolves", 20, helper -> {
            checkModelParentsResolve(helper);
            helper.succeed();
        });

        // The Cupola shipped advertising automation that no pipe mod could reach, because nothing
        // asserted that a block which HOLDS things exposes the capability to move them. Hoppers use a
        // different path and worked, so the gap was invisible. docs/automation_policy_spec.md already
        // states the intended answer per block; this makes that document executable.
        RCGameTests.test("every_container_block_declares_its_automation", 20, helper -> {
            checkContainersDeclareCapabilities(helper);
            helper.succeed();
        });

        RCGameTests.test("every_texture_a_model_names_exists", 20, helper -> {
            checkModelTexturesExist(helper);
            helper.succeed();
        });

        RCGameTests.test("every_client_item_model_resolves", 20, helper -> {
            checkClientItemModelsResolve(helper);
            helper.succeed();
        });

        // A block with no item cannot be held, crafted into anything, or put in the creative tab.
        // Some legitimately should not be - but each of those is a decision, so they are named in
        // NO_ITEM_FORM rather than the test being loosened to accommodate them.
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
     *
     * <p><b>Vanilla parents are checked too, and that is a correction.</b> This used to skip anything
     * outside the mod's namespace on the reasoning that vanilla is not ours to verify. Vanilla is stable
     * <i>within</i> a version and this mod moves between them: 26.1 deleted
     * {@code minecraft:item/template_spawn_egg} (spawn eggs stopped being a tinted two-layer template and
     * became ordinary {@code item/generated} with a PNG each). The Roach's spawn egg still parented to it,
     * rendered as the missing model, and every test here stayed green. A parent that does not exist is
     * broken regardless of who owns it.
     *
     * <p>It walks <b>every</b> model the game can reach, blocks included, rather than only
     * {@code models/item/<id>.json}. Restricting it to items would have left the allowlist half enforced -
     * two thirds of its entries are {@code block/} parents.
     */
    private static void checkModelParentsResolve(GameTestHelper helper) {
        Set<String> models = discoverModels();
        helper.assertTrue(models.size() > 20,
            "only " + models.size() + " models were reached - discovery is broken, so this test would "
                + "pass against any broken parent");

        List<String> broken = new ArrayList<>();
        for (String model : models) {
            String json = readResource("/assets/" + Recompile.MOD_ID + "/models/" + model + ".json");
            if (json == null) {
                continue;   // a model named but absent is reported by the resolve checks
            }
            Matcher m = PARENT.matcher(json);
            while (m.find()) {
                String parent = m.group(1);
                if (!modelExists(parent)) {
                    broken.add(model + " -> " + parent);
                }
            }
        }
        report(helper, broken, "models whose parent does not exist");
    }

    /**
     * Every model of this mod's that the game can actually reach, found the way the game finds them:
     * from every blockstate and every client item definition, then following {@code parent} up the chain.
     *
     * <p>Walking the parent chain also reaches models nothing else names directly - the bin's per-material
     * labels, the burner's lit variant - which is why both the texture check and the parent check share
     * this rather than each enumerating what they think exists.
     */
    private static Set<String> discoverModels() {
        Set<String> models = new java.util.LinkedHashSet<>();
        forEachModBlock((id, block) -> collectModels(
            readResource("/assets/" + id.getNamespace() + "/blockstates/" + id.getPath() + ".json"),
            models));
        forEachModItem((id, item) -> {
            collectModels(readResource("/assets/" + id.getNamespace() + "/items/"
                + id.getPath() + ".json"), models);
            if (resourceExists("/assets/" + id.getNamespace() + "/models/item/" + id.getPath() + ".json")) {
                models.add("item/" + id.getPath());
            }
        });

        List<String> queue = new ArrayList<>(models);
        for (int i = 0; i < queue.size(); i++) {
            String json = readResource("/assets/" + Recompile.MOD_ID + "/models/" + queue.get(i) + ".json");
            if (json == null) {
                continue;
            }
            Matcher parents = PARENT.matcher(json);
            while (parents.find()) {
                String parent = parents.group(1);
                if (parent.startsWith(Recompile.MOD_ID + ":")) {
                    String path = parent.substring(parent.indexOf(':') + 1);
                    if (models.add(path)) {
                        queue.add(path);
                    }
                }
            }
        }
        return models;
    }

    /**
     * Does a model reference resolve?
     *
     * <p>Two different questions behind one name, because a model file cannot tell you which it is. Ours
     * is answered by reading the classpath. Vanilla's cannot be - see {@link #VANILLA_PARENTS} - so it is
     * answered by the allowlist instead. A third-party namespace is treated as present: another mod's
     * assets are not guaranteed to be on this classpath, and failing there would report a missing optional
     * dependency as a broken model.
     */
    private static boolean modelExists(String ref) {
        int colon = ref.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : ref.substring(0, colon);
        String path = ref.substring(colon + 1);
        if (namespace.equals(Recompile.MOD_ID)) {
            return resourceExists("/assets/" + namespace + "/models/" + path + ".json");
        }
        return !namespace.equals("minecraft") || VANILLA_PARENTS.contains(path);
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

    /**
     * Every texture a shipped model names must exist on disk.
     *
     * <p>The gap this closes is the loudest-in-game, quietest-in-CI failure the mod has: a missing PNG is
     * a pink-and-black block for the player and a green build for everyone else. Nothing compiles a model,
     * so a renamed or never-promoted texture is invisible until somebody looks at it - which is exactly
     * how the Stone Rubble item shipped broken, and how a texture that is generated but not yet promoted
     * would ship broken again.
     *
     * <p><b>Reached through the classpath, not the source tree.</b> The first version of this walked
     * {@code src/main/resources} with a relative path, and the gametest server does not run from the
     * project root - so it scanned nothing and passed against a deliberately broken reference. It is
     * called out here because that is the failure this whole file exists to prevent, and it took a RED
     * check to notice rather than a reading.
     *
     * <p>Models are discovered the way the game finds them: from every blockstate and every client item
     * definition, then following {@code parent} up the chain. That also reaches models nothing else
     * names directly - the bin's per-material labels, the burner's lit variant.
     */
    private static void checkModelTexturesExist(GameTestHelper helper) {
        Set<String> models = discoverModels();

        // Non-vacuous by construction: if discovery finds nothing, that is the bug, not a pass.
        helper.assertTrue(models.size() > 20,
            "only " + models.size() + " models were reached - discovery is broken, so this test would "
                + "pass against any missing texture");

        List<String> missing = new ArrayList<>();
        for (String model : models) {
            String json = readResource("/assets/" + Recompile.MOD_ID + "/models/" + model + ".json");
            if (json == null) {
                continue;   // a model named but absent is reported by the resolve checks
            }
            Matcher m = TEXTURE_REF.matcher(json);
            while (m.find()) {
                if (!"parent".equals(m.group(1))) {
                    String ref = m.group(2);
                    if (ref.startsWith(Recompile.MOD_ID + ":")
                            && !resourceExists("/assets/" + Recompile.MOD_ID + "/textures/"
                                + ref.substring(ref.indexOf(':') + 1) + ".png")) {
                        missing.add(model + " -> " + ref);
                    }
                }
            }
        }
        report(helper, missing, "textures named by a model but absent from the jar");
    }

    /**
     * Blocks that hold items but deliberately expose no item capability, each with its reason.
     *
     * <p>This is the {@code automation_policy_spec.md} table in executable form. An entry here is a
     * design decision; adding one to silence a failure rather than to state a fact defeats the test.
     */
    private static final List<String> NO_ITEM_CAPABILITY = List.of(
        // Manual-only by design, and the reason the Cupola is worth building. Exposing ANY handler -
        // even one that refuses - makes pipes visually connect, so it exposes none at all.
        "burn_barrel",
        // Items stay manual; only its water tank is automatable.
        "tree_nursery",
        // Holds one displayed item and is never hopper-fed - placing and taking is the interaction.
        "display_pedestal"
    );

    /**
     * Every block with a container BlockEntity either exposes {@code Capabilities.Item.BLOCK} or is
     * named above.
     *
     * <p>Catches the Cupola bug: a machine that advertises automation while no capability-based pipe
     * can reach it. Hoppers travel the vanilla {@code Container} path and never consult the capability,
     * so "it works with a hopper" proves nothing about pipes.
     */
    private static void checkContainersDeclareCapabilities(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        List<String> undeclared = new ArrayList<>();
        int containers = 0;
        for (var type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            Identifier typeId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
            if (typeId == null || !Recompile.MOD_ID.equals(typeId.getNamespace())) {
                continue;
            }
            for (Block block : type.getValidBlocks()) {
                Identifier id = BuiltInRegistries.BLOCK.getKey(block);
                BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
                level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
                // try/finally, not a clear at the end of the body: every early exit below has to put the
                // plot back. Leaving a block behind is not cosmetic here - gametest plots sit close
                // together, and debris from one test is the next test's starting world.
                try {
                    if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.Container)) {
                        continue;   // holds no items, so there is nothing to declare
                    }
                    containers++;
                    if (NO_ITEM_CAPABILITY.contains(id.getPath())) {
                        continue;
                    }
                    boolean exposed = false;
                    for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                        if (level.getCapability(
                                net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                                pos, side) != null) {
                            exposed = true;
                            break;
                        }
                    }
                    if (!exposed) {
                        undeclared.add(id.getPath());
                    }
                } finally {
                    level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL);
                }
            }
        }
        helper.assertTrue(containers > 2,
            "only " + containers + " container blocks were found - discovery is broken, so this test "
                + "would pass against any missing capability");
        report(helper, undeclared,
            "blocks that hold items but expose no item capability, so no pipe can reach them");
    }

    /** Every {@code "model": "recompile:..."} in a blockstate or client item definition. */
    private static void collectModels(@Nullable String json, Set<String> into) {
        if (json == null) {
            return;
        }
        Matcher m = MODEL_REF.matcher(json);
        while (m.find()) {
            String ref = m.group(1);
            if (ref.startsWith(Recompile.MOD_ID + ":")) {
                into.add(ref.substring(ref.indexOf(':') + 1));
            }
        }
    }

    /** A {@code "name": "value"} pair inside a model's textures block. */
    private static final Pattern TEXTURE_REF =
        Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]+)\"");

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

    /**
     * Simple class names of every Jade <b>display</b> provider, read off the compiled package.
     *
     * <p>{@code *DataProvider} is excluded: since MC 1.21.6 one class may not be both an
     * {@code IServerDataProvider} and an {@code IBlockComponentProvider}, so the data half of each pair
     * is a separate class - and only the display half gets a config toggle to translate.
     *
     * <p>Reads the directory rather than loading the classes, so this needs nothing from Jade on the
     * classpath and cannot be broken by a provider that fails to link.
     */
    private static List<String> jadeDisplayProviders() {
        List<String> out = new ArrayList<>();
        // Anchor on a CLASS FILE and take its parent, not on the package directory. Asking a
        // classloader for a directory is unreliable - it returned null here under NeoForge's union
        // filesystem, which made discovery silently empty. A class file resource always resolves.
        java.net.URL anchor = RegistryCompletenessTests.class
            .getResource("/com/flatts/recompile/compat/jade/ToolHintProvider.class");
        if (anchor == null) {
            return out;   // the caller fails on empty, which is what an unreadable package should do
        }
        // Only an IBlockComponentProvider gets a config toggle, so only it needs a translation. The
        // filter was a NAME check first and swept in the item-storage view providers, which have no
        // config entry at all - a test demanding a key for something Jade never asks about is a test
        // that will be silenced by adding a dead string. Asking the class what it implements cannot
        // drift; the cost is loading Jade, which is on the runtime classpath here.
        try (var entries = java.nio.file.Files.list(java.nio.file.Path.of(anchor.toURI()).getParent())) {
            // BLOCK and ENTITY component providers both get a config toggle. Only the block one was
            // listed at first, and the reverse check immediately named PaintingNameProvider - which is
            // an entity provider, because a painting is an entity.
            List<Class<?>> component = List.of(
                Class.forName("snownee.jade.api.IBlockComponentProvider"),
                Class.forName("snownee.jade.api.IEntityComponentProvider"));
            for (java.nio.file.Path entry : entries.toList()) {
                String name = entry.getFileName().toString();
                if (!name.endsWith(".class") || name.contains("$")) {
                    continue;
                }
                String simple = name.substring(0, name.length() - ".class".length());
                Class<?> type = Class.forName("com.flatts.recompile.compat.jade." + simple);
                if (component.stream().anyMatch(c -> c.isAssignableFrom(type))) {
                    out.add(simple);
                }
            }
        } catch (Exception e) {
            return List.of();
        }
        return out;
    }

    /** Every key in the bundled {@code en_us} under the given prefix. */
    private static List<String> langKeysStartingWith(String prefix) {
        List<String> out = new ArrayList<>();
        try (var in = RegistryCompletenessTests.class
                .getResourceAsStream("/assets/" + Recompile.MOD_ID + "/lang/en_us.json")) {
            if (in == null) {
                return out;
            }
            var json = com.google.gson.JsonParser.parseReader(
                new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            for (String key : json.getAsJsonObject().keySet()) {
                if (key.startsWith(prefix)) {
                    out.add(key);
                }
            }
        } catch (Exception e) {
            return List.of();
        }
        return out;
    }

    /** {@code SortProgress} -> {@code sort_progress}. The convention every provider UID follows. */
    private static String snakeCase(String camel) {
        return camel.replaceAll("(?<=[a-z0-9])(?=[A-Z])", "_")
            .toLowerCase(java.util.Locale.ROOT);
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

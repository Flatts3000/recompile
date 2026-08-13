package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The Modonomicon guidebook's references.
 *
 * <p><b>Both failures this catches are silent.</b> A page whose {@code text} names a lang key that does
 * not exist renders the raw key to the player - {@code book.recompile.guide.demolition.iron.intro.text}
 * in place of a paragraph - and an entry whose icon names an item that does not exist renders the
 * pink-and-black missing texture on the category map. Neither throws, neither logs, and neither shows up
 * in a build. The spec calls the book "client-render-only" and leaves all of it to a manual
 * {@code runClient} pass; these two checks do not need a client, so they should not wait for one.
 *
 * <p><b>What this cannot do is notice a missing entry.</b> Whether a shipped feature deserves a page is a
 * judgement call, and an audit found twelve of them absent at once - the demolition yard, the power tier,
 * the Hydroponics Bay, the Tree Nursery, recovered paintings, Animal Bait, sleeping, and the rule that
 * water does not spread. Nothing mechanical would have flagged those. This guards the references only,
 * which is the half a test can actually hold.
 *
 * <p>Discovery walks the resource directory rather than a manifest, because Modonomicon itself finds
 * books by scanning {@code modonomicon/books} and there is no index to read. If the walk comes back empty
 * the test fails rather than passing vacuously.
 */
final class GuidebookTests {

    private static final String BOOK_ROOT =
        "/data/" + Recompile.MOD_ID + "/modonomicon/books/guide";

    /** Any of this mod's guidebook lang keys, wherever it appears in the JSON. */
    private static final Pattern LANG_KEY =
        Pattern.compile("\"(book\\." + Recompile.MOD_ID + "\\.[A-Za-z0-9_.]+)\"");
    /** A {@code "entry": "<id>"} inside an entry's parents list. */
    private static final Pattern PARENT_ENTRY =
        Pattern.compile("\"entry\"\\s*:\\s*\"([a-z0-9_.-]+:[a-z0-9_/.-]+)\"");
    /** An {@code "id": "<namespace>:<path>"} pair, which in this book is always an item icon. */
    private static final Pattern ICON_ID =
        Pattern.compile("\"id\"\\s*:\\s*\"([a-z0-9_.-]+:[a-z0-9_/.-]+)\"");

    private GuidebookTests() {
    }

    static void register() {
        // A LIST in prose goes stale in the one way that does not read as wrong: it reads as complete.
        // Both of these had already rotted before anyone noticed - the tarp entry never learned that
        // Mechanical Waste became sortable, and the network entry named six members when the tag held
        // twelve, while the Filing Cabinet's own entry told the player to connect one.
        //
        // Nothing else here can catch that. every_guidebook_lang_key_resolves proves a sentence
        // EXISTS; no test layer knows whether it is true. This is the narrow slice that is checkable:
        // a prose list that enumerates a set the code also defines.
        //
        // It works because the prose quotes the game's own display names, which is why those entries
        // say "Block of Garbage" rather than "garbage". That is the whole mechanism - a friendlier
        // wording would need a keyword mapping here, and the mapping would be the next thing to drift.
        RCGameTests.test("prose_lists_name_every_member_of_the_set_they_describe", 20, helper -> {
            List<String> problems = new ArrayList<>();

            // Every block the tarp and the Separator will sift, wherever a page enumerates them.
            List<net.minecraft.world.level.block.Block> sortables = BuiltInRegistries.BLOCK.stream()
                .filter(b -> com.flatts.recompile.content.block.SortableBlock
                    .sortRolls(b.asItem()) > 0)
                .toList();
            helper.assertTrue(sortables.size() >= 5,
                "only " + sortables.size() + " sortable blocks found - discovery is broken, so this "
                    + "test would pass by checking nothing");
            for (String key : List.of(
                    "book.recompile.guide.workstations.sorting_tarp.intro.text",
                    "book.recompile.guide.demolition.separator.sorting.text")) {
                checkNames(problems, key, sortables);
            }

            // Every Scrap Network member, minus the cells that only exist once a machine is assembled -
            // a player never places a Separator Chamber, so the prose has no business naming one.
            Set<net.minecraft.world.level.block.Block> formedOnly =
                com.flatts.recompile.compat.MultiblockParts.formedOnly();
            List<net.minecraft.world.level.block.Block> members = new ArrayList<>();
            for (var holder : BuiltInRegistries.BLOCK
                    .getTagOrEmpty(com.flatts.recompile.registry.RCTags.SCRAP_CONNECTABLE)) {
                if (!formedOnly.contains(holder.value())) {
                    members.add(holder.value());
                }
            }
            helper.assertTrue(members.size() >= 7,
                "only " + members.size() + " placeable network members found - discovery is broken");
            checkNames(problems, "book.recompile.guide.workstations.scrap_network.intro.text", members);

            report(helper, problems, "guidebook lists missing a member the code defines");
        });

        // EVERY FIND A PLAYER CAN PRY OPEN MUST HAVE AN ENTRY.
        //
        // The Dead Fridge shipped in v0.9.0 with no page at all. The Broken Fan and the Light Fixture
        // entries were deleted alongside those blocks and nothing replaced them, so the category
        // simply had one fewer page - which renders perfectly. That is why nothing caught it: a
        // missing entry is not a broken reference, and every other check here is a reference check.
        //
        // It matters most for exactly the find that went missing. The fridge is the only teardown in
        // the mod where WHICH component you get is a draw, and the only source of ice or snow, and a
        // player who is not told either will tear one down expecting the part they wanted.
        RCGameTests.test("every_bulky_waste_find_has_a_guidebook_entry", 20, helper -> {
            Set<String> icons = new LinkedHashSet<>();
            for (String json : categoryFiles(helper, "bulky_waste")) {
                Matcher m = ICON_ID.matcher(json);
                while (m.find()) {
                    if (!m.group(1).contains("/")) {
                        icons.add(m.group(1));
                    }
                }
            }
            helper.assertTrue(icons.size() >= 5,
                "only " + icons.size() + " icons found in the bulky_waste category - discovery is "
                    + "broken, so this test would pass by checking nothing");

            List<String> unwritten = new ArrayList<>();
            int finds = 0;
            for (var drop : com.flatts.recompile.compat.SortingData.outputs(SPINE)) {
                finds++;
                String id = String.valueOf(
                    BuiltInRegistries.ITEM.getKey(drop.stack().getItem()));
                if (!icons.contains(id)) {
                    unwritten.add(id);
                }
            }
            helper.assertTrue(finds >= 5,
                "only " + finds + " finds read from the spine - discovery is broken");
            helper.assertTrue(unwritten.isEmpty(),
                "these Bulky Waste finds have no guidebook entry, so the book quietly says they do "
                    + "not exist: " + unwritten);
            helper.succeed();
        });

        // A key that does not resolve renders as itself. It is the most visible possible bug and the
        // least visible possible failure: the book still opens, the page still turns.
        RCGameTests.test("every_guidebook_lang_key_resolves", 20, helper -> {
            List<String> files = bookFiles(helper);
            Set<String> keys = new LinkedHashSet<>();
            for (String json : files) {
                Matcher m = LANG_KEY.matcher(json);
                while (m.find()) {
                    keys.add(m.group(1));
                }
            }
            helper.assertTrue(keys.size() > 50,
                "only " + keys.size() + " guidebook lang keys were found - discovery is broken, so this "
                    + "test would pass against a book with none of its text written");

            List<String> unresolved = new ArrayList<>();
            for (String key : keys) {
                if (Component.translatable(key).getString().equals(key)) {
                    unresolved.add(key);
                }
            }
            report(helper, unresolved, "guidebook lang keys with no translation");
        });

        // An icon naming an item that does not exist draws the missing texture on the category map, next
        // to a perfectly good entry. Nothing else in the build looks at these ids.
        RCGameTests.test("every_guidebook_icon_is_a_real_item", 20, helper -> {
            List<String> files = bookFiles(helper);
            Set<String> ids = new LinkedHashSet<>();
            for (String json : files) {
                Matcher m = ICON_ID.matcher(json);
                while (m.find()) {
                    String id = m.group(1);
                    // Entry and category ids share the "id" field name; those are book paths, not items.
                    if (!id.contains("/")) {
                        ids.add(id);
                    }
                }
            }
            helper.assertTrue(ids.size() > 20,
                "only " + ids.size() + " guidebook icons were found - discovery is broken");

            List<String> unknown = new ArrayList<>();
            for (String id : ids) {
                Identifier parsed = Identifier.tryParse(id);
                if (parsed == null || !BuiltInRegistries.ITEM.containsKey(parsed)) {
                    unknown.add(id);
                }
            }
            report(helper, unknown, "guidebook icons naming an item that does not exist");
        });

        // THE BOOK'S INTERNAL LINKS. An entry filed under a category that does not exist simply never
        // appears, and a parent naming an entry that does not exist draws a line to nowhere - both
        // silent, both exactly the mistake a restructure makes. The Bulky Waste chapter moved seven
        // entries at once (2026-08-04) and nothing mechanical would have caught a typo in any of them.
        RCGameTests.test("every_guidebook_entry_is_wired_up", 20, helper -> {
            Path root = bookRoot(helper);
            if (root == null) {
                return;
            }
            Set<String> categories = new LinkedHashSet<>();
            Set<String> entries = new LinkedHashSet<>();
            List<Path> entryFiles = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path path : walk.filter(Files::isRegularFile).toList()) {
                    String name = path.getFileName().toString();
                    if (!name.endsWith(".json")) {
                        continue;
                    }
                    Path parent = path.getParent();
                    if (parent.getFileName().toString().equals("categories")) {
                        categories.add(Recompile.MOD_ID + ":" + name.substring(0, name.length() - 5));
                    } else if (parent.getParent() != null
                        && parent.getParent().getFileName().toString().equals("entries")) {
                        entryFiles.add(path);
                        entries.add(Recompile.MOD_ID + ":" + parent.getFileName() + "/"
                            + name.substring(0, name.length() - 5));
                    }
                }
            } catch (IOException e) {
                helper.fail("could not walk the guidebook: " + e);
                return;
            }
            helper.assertTrue(categories.size() > 5 && entries.size() > 20,
                "found " + categories.size() + " categories and " + entries.size() + " entries - "
                    + "discovery is broken, so this would pass against an empty book");

            List<String> problems = new ArrayList<>();
            for (Path file : entryFiles) {
                String json;
                try {
                    json = Files.readString(file, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    problems.add(file + " unreadable: " + e);
                    continue;
                }
                String id = field(json, "id");
                String category = field(json, "category");
                if (category != null && !categories.contains(category)) {
                    problems.add(id + " is filed under " + category + ", which has no category file");
                }
                Matcher m = PARENT_ENTRY.matcher(json);
                while (m.find()) {
                    if (!entries.contains(m.group(1))) {
                        problems.add(id + " has a parent " + m.group(1) + " that does not exist");
                    }
                }
                // An entry does not list its pages - the directory is scanned - so an entry with no
                // pages directory is a node that opens onto nothing.
                String name = file.getFileName().toString();
                Path pages = file.getParent().resolve(name.substring(0, name.length() - 5))
                    .resolve("pages");
                boolean hasPage = false;
                if (Files.isDirectory(pages)) {
                    try (Stream<Path> inside = Files.list(pages)) {
                        hasPage = inside.anyMatch(p -> p.toString().endsWith(".json"));
                    } catch (IOException ignored) {
                        hasPage = false;
                    }
                }
                if (!hasPage) {
                    problems.add(id + " has no pages, so it opens blank");
                }
            }
            report(helper, problems, "guidebook entries that are not wired up");
        });
    }

    /** {@code "<key>": "<value>"} at the top level of an entry file. */
    private static @org.jspecify.annotations.Nullable String field(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static @org.jspecify.annotations.Nullable Path bookRoot(GameTestHelper helper) {
        URL anchor = GuidebookTests.class.getResource(BOOK_ROOT + "/book.json");
        if (anchor == null) {
            helper.fail("the guidebook is not on the classpath at " + BOOK_ROOT + "/book.json");
            return null;
        }
        try {
            return Path.of(anchor.toURI()).getParent();
        } catch (java.net.URISyntaxException e) {
            helper.fail("could not resolve the guidebook root: " + e);
            return null;
        }
    }

    /**
     * Every JSON file under the book, read off the classpath.
     *
     * <p>Anchored on {@code book.json} rather than on the directory: asking the classloader for a
     * directory does not reliably return a URL, and it did not here. A file always does, and its parent
     * is the folder we want.
     */
    /** The Bulky Waste find table, read as the source of truth for what a player can pry open. */
    private static final String SPINE = "/data/recompile/loot_table/gameplay/bulky_spine.json";

    /** Every JSON under one category's entry directory, contents only. */
    static List<String> categoryFiles(GameTestHelper helper, String category) {
        List<String> out = new ArrayList<>();
        URL anchor = GuidebookTests.class.getResource(BOOK_ROOT + "/book.json");
        if (anchor == null) {
            helper.fail("the guidebook is not on the classpath at " + BOOK_ROOT + "/book.json");
            return out;
        }
        try {
            Path dir = Path.of(anchor.toURI()).getParent().resolve("entries").resolve(category);
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path path : walk.filter(Files::isRegularFile).toList()) {
                    if (path.toString().endsWith(".json")) {
                        out.add(Files.readString(path, StandardCharsets.UTF_8));
                    }
                }
            }
        } catch (IOException | java.net.URISyntaxException e) {
            helper.fail("could not walk the " + category + " category: " + e);
        }
        return out;
    }

    static List<String> bookFiles(GameTestHelper helper) {
        List<String> out = new ArrayList<>();
        URL anchor = GuidebookTests.class.getResource(BOOK_ROOT + "/book.json");
        if (anchor == null) {
            helper.fail("the guidebook is not on the classpath at " + BOOK_ROOT + "/book.json");
            return out;
        }
        try (Stream<Path> walk = Files.walk(Path.of(anchor.toURI()).getParent())) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                if (path.toString().endsWith(".json")) {
                    out.add(Files.readString(path, StandardCharsets.UTF_8));
                }
            }
        } catch (IOException | java.net.URISyntaxException e) {
            helper.fail("could not walk the guidebook: " + e);
        }
        return out;
    }

    /**
     * Record every block whose display name is missing from the entry's rendered text.
     *
     * <p>Singular names, so an entry may still write "Scrap Bins" and read naturally; the singular is
     * a substring of the plural. Matching is on the rendered string rather than the raw key, so this
     * fails the same way a player would see it.
     */
    private static void checkNames(List<String> problems, String key,
            List<net.minecraft.world.level.block.Block> expected) {
        String prose = Component.translatable(key).getString();
        String entry = key.substring("book.recompile.guide.".length());
        for (net.minecraft.world.level.block.Block block : expected) {
            String name = Component.translatable(block.getDescriptionId()).getString();
            if (!prose.contains(name)) {
                problems.add(entry + " never names " + name);
            }
        }
    }

    private static void report(GameTestHelper helper, List<String> problems, String label) {
        helper.assertTrue(problems.isEmpty(), label + " (" + problems.size() + "): " + problems);
        helper.succeed();
    }
}

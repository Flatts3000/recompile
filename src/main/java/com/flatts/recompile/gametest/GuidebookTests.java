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
    /** An {@code "id": "<namespace>:<path>"} pair, which in this book is always an item icon. */
    private static final Pattern ICON_ID =
        Pattern.compile("\"id\"\\s*:\\s*\"([a-z0-9_.-]+:[a-z0-9_/.-]+)\"");

    private GuidebookTests() {
    }

    static void register() {
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
    }

    /**
     * Every JSON file under the book, read off the classpath.
     *
     * <p>Anchored on {@code book.json} rather than on the directory: asking the classloader for a
     * directory does not reliably return a URL, and it did not here. A file always does, and its parent
     * is the folder we want.
     */
    private static List<String> bookFiles(GameTestHelper helper) {
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

    private static void report(GameTestHelper helper, List<String> problems, String label) {
        helper.assertTrue(problems.isEmpty(), label + " (" + problems.size() + "): " + problems);
        helper.succeed();
    }
}

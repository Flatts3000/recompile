package com.flatts.recompile.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import java.util.HashMap;
import java.util.TreeSet;

/**
 * What this mod's loot tables are capable of dropping, read from the tables themselves.
 *
 * <p><b>Statically, not by rolling.</b> Rolling a weighted table until a rare entry appears is a test
 * that fails on an unlucky seed, and a one-in-four-thousand collectible would need a great many rolls
 * before absence meant anything. Reading the declaration answers "can this ever drop" exactly, which is
 * the question {@code everything_found_only_is_actually_findable} is actually asking.
 *
 * <p>Tables are read as <b>classpath resources</b> rather than through the loot registry, because a
 * {@code LootTable} exposes no way to enumerate what its pools contain - only to roll them. The files
 * are on the classpath in a dev run, which is the same trick {@code SortingData} and
 * {@code RegistryCompletenessTests} already use. The registry still supplies the list of table ids, so
 * nothing here has to know a directory listing.
 *
 * <p>Entries of type {@code minecraft:tag} are expanded through the live tag registry, so a table that
 * offers {@code #minecraft:wool_carpets} counts as a source for all sixteen.
 */
final class LootSearch {

    private LootSearch() {
    }

    /** Every item any of this mod's loot tables can produce. Built once, then cached. */
    private static Set<Item> droppable;

    /** Which tables can produce each item, for the times "is there a source" is not the question. */
    private static Map<Item, Set<String>> sources;

    static boolean anyTableCanDrop(ServerLevel level, Item item) {
        if (droppable == null) {
            collect(level);
        }
        return droppable.contains(item);
    }

    /**
     * Which of this mod's loot tables can produce an item, by id.
     *
     * <p>Exists for the exclusivity question rather than the existence one: an item that must come from
     * exactly one place needs the list, not the boolean. The obvious way to write that check - walk the
     * data directory and grep - does not work here at all: NeoForge's dev classpath resolves individual
     * resources but hands back null for a directory URL, so the walk finds zero files and the emptiness
     * assertion built on it passes against anything. That was written, driven with a deliberate second
     * source, and quietly stayed green.
     *
     * <p><b>Pair it with {@link #tablesNotRead}.</b> This reads JSON off the classpath, so a table that is
     * in the registry but not on the classpath - a datapack's, or a pack overriding one of ours - is
     * skipped in silence. For "is there a source at all" that is harmless. For "is there a SECOND
     * source" it is the whole question, because the second source is exactly what would be hiding there.
     */
    static Set<String> tablesThatCanDrop(ServerLevel level, Item item) {
        if (sources == null) {
            collect(level);
        }
        return sources.getOrDefault(item, Set.of());
    }

    /** Registry-listed tables whose JSON was not on the classpath, so nothing here has seen them. */
    private static Set<String> skipped;

    /**
     * Which of this mod's loot tables were listed but could not be read.
     *
     * <p>An exclusivity claim has to assert this is empty. The alternative is a sweep that reports a
     * clean result both when it found no second source and when it could not look.
     */
    static Set<String> tablesNotRead(ServerLevel level) {
        if (skipped == null) {
            collect(level);
        }
        return skipped;
    }

    private static void collect(ServerLevel level) {
        // BUILD LOCALS, PUBLISH AT THE END. Assigning the statics up front and filling them in place
        // looks equivalent and is not: anything thrown out of walk() or expandTag() part-way - a
        // malformed name reaching Identifier.parse, say - would leave a non-null, half-filled cache that
        // every later caller reads as complete. A loud failure becomes an exclusivity sweep that quietly
        // under-reports, which is the exact failure this class is used to rule out.
        Set<Item> foundAll = new HashSet<>();
        Map<Item, Set<String>> bySource = new HashMap<>();
        Set<String> unread = new TreeSet<>();
        for (var key : level.getServer().reloadableRegistries().lookup()
                .lookupOrThrow(Registries.LOOT_TABLE).listElementIds().toList()) {
            Identifier id = key.identifier();
            if (!id.getNamespace().equals("recompile")) {
                continue;
            }
            JsonElement root = read("/data/" + id.getNamespace() + "/loot_table/" + id.getPath() + ".json");
            if (root == null) {
                unread.add(id.toString());
                continue;
            }
            Set<Item> here = new HashSet<>();
            walk(level, root, here);
            foundAll.addAll(here);
            for (Item item : here) {
                bySource.computeIfAbsent(item, ignored -> new HashSet<>()).add(id.toString());
            }
        }
        droppable = foundAll;
        sources = bySource;
        skipped = unread;
    }

    /**
     * Recursive because loot JSON nests: pools hold entries, an {@code alternatives} or {@code group}
     * entry holds children, and a function can carry a whole table of its own. Anything that names an
     * item anywhere counts, which is deliberately generous - this answers "is there a source at all",
     * and a false yes would only ever come from a table that names an item it cannot reach.
     */
    private static void walk(ServerLevel level, JsonElement element, Set<Item> found) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                walk(level, child, found);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        String type = object.has("type") && object.get("type").isJsonPrimitive()
            ? object.get("type").getAsString() : "";
        if (object.has("name") && object.get("name").isJsonPrimitive()) {
            String name = object.get("name").getAsString();
            if (type.equals("minecraft:tag")) {
                expandTag(level, name, found);
            } else if (type.equals("minecraft:item")) {
                Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getValue(Identifier.parse(name));
                found.add(item);
            }
        }
        for (Map.Entry<String, JsonElement> child : object.entrySet()) {
            walk(level, child.getValue(), found);
        }
    }

    private static void expandTag(ServerLevel level, String name, Set<Item> found) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(name));
        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(tag)
            .ifPresent(holders -> holders.forEach(holder -> found.add(holder.value())));
    }

    private static JsonElement read(String path) {
        try (InputStream in = LootSearch.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception failed) {
            return null;
        }
    }
}

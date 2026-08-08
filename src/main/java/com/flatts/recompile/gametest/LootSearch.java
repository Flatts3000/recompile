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

    static boolean anyTableCanDrop(ServerLevel level, Item item) {
        if (droppable == null) {
            droppable = collect(level);
        }
        return droppable.contains(item);
    }

    private static Set<Item> collect(ServerLevel level) {
        Set<Item> found = new HashSet<>();
        for (var key : level.getServer().reloadableRegistries().lookup()
                .lookupOrThrow(Registries.LOOT_TABLE).listElementIds().toList()) {
            Identifier id = key.identifier();
            if (!id.getNamespace().equals("recompile")) {
                continue;
            }
            JsonElement root = read("/data/" + id.getNamespace() + "/loot_table/" + id.getPath() + ".json");
            if (root != null) {
                walk(level, root, found);
            }
        }
        return found;
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

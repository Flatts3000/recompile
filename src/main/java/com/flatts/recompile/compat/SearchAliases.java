package com.flatts.recompile.compat;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Search aliases for the items this mod renames (#118): a lead is Rope, a bundle is Luggage.
 *
 * <p><b>A rename breaks search, and that is the cost nobody sees coming.</b> Everything a player knows
 * about a lead is filed under the word "lead" - the wiki, the recipe they half remember, every video.
 * Renaming it to Rope makes the item impossible to find by the only name they have for it, which is a
 * worse outcome than the flavour is worth.
 *
 * <p>The alias is registered with JEI as a <b>translation key</b>, not literal text, so a pack that
 * translates this book gets a translated alias too and the mechanism keeps working in every language.
 *
 * <p>Plain data with no JEI types on purpose, exactly like {@link SortingData}. The plugin that consumes
 * it only loads when JEI is installed, so anything expressed there is invisible to the test layers; here
 * it is ordinary code a GameTest can hold to account.
 */
public final class SearchAliases {

    /** What a lead was called, so searching for it still finds the Rope. */
    public static final String LEAD = "recompile.alias.lead";

    /** What every bundle was called, so searching for it still finds the Luggage. */
    public static final String BUNDLE = "recompile.alias.bundle";

    private SearchAliases() {
    }

    /**
     * Every renamed item and the old name to file it under.
     *
     * <p>The bundles are found by <b>suffix over the item registry</b> rather than listed. Seventeen
     * literal ids is seventeen chances to miss one, and the miss is silent - the item simply stops
     * answering to its own name in search. It also means a colour vanilla adds later is covered on the
     * day it exists.
     */
    public static Map<Item, String> all() {
        Map<Item, String> aliases = new LinkedHashMap<>();
        aliases.put(Items.LEAD, LEAD);
        for (Item item : BuiltInRegistries.ITEM) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (id.getNamespace().equals("minecraft") && id.getPath().endsWith("bundle")) {
                aliases.put(item, BUNDLE);
            }
        }
        return aliases;
    }
}

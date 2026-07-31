package com.flatts.recompile.client;

import com.flatts.recompile.Recompile;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import org.jspecify.annotations.Nullable;

/**
 * Caches the server's recipes as the client receives them.
 *
 * <p>Needed because 26.1's client-side {@code RecipeAccess} exposes only property sets and stonecutter
 * recipes - there is no way to enumerate, say, every smelting recipe through it. The full set does arrive,
 * just via {@link RecipesReceivedEvent}, which is how JEI's own vanilla plugin gets them.
 *
 * <p>Used by the JEI plugin to build the Burn Barrel's category from the smelting recipes it actually
 * accepts, rather than claiming all of them. Client-only, and null until a world is joined.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID, value = Dist.CLIENT)
public final class RCSyncedRecipes {

    private static @Nullable RecipeMap recipes;

    private RCSyncedRecipes() {
    }

    @SubscribeEvent
    public static void onRecipesReceived(RecipesReceivedEvent event) {
        recipes = event.getRecipeMap();
    }

    /** The synced recipes, or null before a world has been joined. */
    public static @Nullable RecipeMap get() {
        return recipes;
    }
}

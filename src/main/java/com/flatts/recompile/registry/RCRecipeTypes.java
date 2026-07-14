package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.recipe.TeardownRecipe;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom recipe types. The founding one is {@code recompile:teardown} - the public
 * data spine (design P0.5, see {@link TeardownRecipe}). Kept as the home for any
 * future Recompile recipe type.
 */
public final class RCRecipeTypes {

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
        DeferredRegister.create(Registries.RECIPE_TYPE, Recompile.MOD_ID);

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, Recompile.MOD_ID);

    public static final Supplier<RecipeType<TeardownRecipe>> TEARDOWN =
        RECIPE_TYPES.register("teardown", () -> RecipeType.simple(
            Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "teardown")));

    public static final Supplier<RecipeSerializer<TeardownRecipe>> TEARDOWN_SERIALIZER =
        RECIPE_SERIALIZERS.register("teardown",
            () -> new RecipeSerializer<>(TeardownRecipe.CODEC, TeardownRecipe.STREAM_CODEC));

    private RCRecipeTypes() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}

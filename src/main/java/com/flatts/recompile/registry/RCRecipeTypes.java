package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.recipe.BlueprintCraftingRecipe;
import com.flatts.recompile.content.recipe.FragmentAssemblyRecipe;
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

    /**
     * {@code recompile:blueprint_crafting} (#95): a recipe that only runs while the player holds the
     * blueprint it names. Registered before anything reads it, exactly as {@code teardown} was - a
     * public schema that arrives after the packs is a breaking change; one that arrives first is an
     * extension point.
     */
    public static final Supplier<RecipeType<BlueprintCraftingRecipe>> BLUEPRINT_CRAFTING =
        RECIPE_TYPES.register("blueprint_crafting", () -> RecipeType.simple(
            Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "blueprint_crafting")));

    public static final Supplier<RecipeSerializer<BlueprintCraftingRecipe>> BLUEPRINT_CRAFTING_SERIALIZER =
        RECIPE_SERIALIZERS.register("blueprint_crafting",
            () -> new RecipeSerializer<>(BlueprintCraftingRecipe.CODEC,
                BlueprintCraftingRecipe.STREAM_CODEC));

    /**
     * Fragments into a sheet (#95). A SPECIAL crafting recipe, not a type of its own: it has to be
     * findable through {@code RecipeType.CRAFTING} so it works in any 3x3 the player can reach, and its
     * ingredients are distinguished by a data component rather than by item id, which an ordinary
     * shapeless recipe cannot match on.
     */
    public static final Supplier<RecipeSerializer<FragmentAssemblyRecipe>> FRAGMENT_ASSEMBLY_SERIALIZER =
        RECIPE_SERIALIZERS.register("fragment_assembly",
            () -> new RecipeSerializer<>(FragmentAssemblyRecipe.CODEC,
                FragmentAssemblyRecipe.STREAM_CODEC));

    private RCRecipeTypes() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}

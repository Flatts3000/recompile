package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.recipe.BlueprintCraftingRecipe;
import com.flatts.recompile.content.recipe.FragmentAssemblyRecipe;
import com.flatts.recompile.content.recipe.PulverizingRecipe;
import com.flatts.recompile.content.recipe.VitrifyingRecipe;
import com.flatts.recompile.content.recipe.SeparatingRecipe;
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

    /**
     * {@code recompile:separating} (docs/gem_tier_spec.md): many inputs into one raw material plus
     * recovered scrap. A type of its own rather than a teardown with a different station, because
     * teardown is one-in and public API - see {@link SeparatingRecipe} for the full argument.
     */
    public static final Supplier<RecipeType<SeparatingRecipe>> SEPARATING =
        RECIPE_TYPES.register("separating", () -> RecipeType.simple(
            Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "separating")));

    public static final Supplier<RecipeSerializer<SeparatingRecipe>> SEPARATING_SERIALIZER =
        RECIPE_SERIALIZERS.register("separating",
            () -> new RecipeSerializer<>(SeparatingRecipe.CODEC, SeparatingRecipe.STREAM_CODEC));

    /**
     * {@code recompile:pulverizing} (#189) - one input, one finer output.
     *
     * <p>Its own type rather than a {@code separating} recipe with a single result, because the
     * Separator divides and the Pulverizer reduces. Overloading separating - which is public API packs
     * extend - with a shape it was not designed for would redefine every recipe already written
     * against it.
     */
    public static final Supplier<RecipeType<PulverizingRecipe>> PULVERIZING =
        RECIPE_TYPES.register("pulverizing", () -> RecipeType.simple(
            Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "pulverizing")));

    public static final Supplier<RecipeSerializer<PulverizingRecipe>> PULVERIZING_SERIALIZER =
        RECIPE_SERIALIZERS.register("pulverizing",
            () -> new RecipeSerializer<>(PulverizingRecipe.CODEC, PulverizingRecipe.STREAM_CODEC));

    /**
     * {@code recompile:vitrifying} - the Slag Furnace's verb (#236).
     *
     * <p><b>Its own type IS the gate.</b> Obsidian is made only, and the portal gate rides on that, so
     * the operation must be one nothing else in the game can do. Measured: {@code minecraft:smelting}
     * would hand it to a vanilla furnace and {@code minecraft:blasting} to a vanilla blast furnace,
     * which is craftable here because iron is reachable. A recipe type is a property of a machine; an
     * absent material is not, and that distinction cost the iron gate two designs (#91).
     */
    public static final Supplier<RecipeType<VitrifyingRecipe>> VITRIFYING =
        RECIPE_TYPES.register("vitrifying", () -> RecipeType.simple(
            Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "vitrifying")));

    public static final Supplier<RecipeSerializer<VitrifyingRecipe>> VITRIFYING_SERIALIZER =
        RECIPE_SERIALIZERS.register("vitrifying",
            () -> new RecipeSerializer<>(VitrifyingRecipe.CODEC, VitrifyingRecipe.STREAM_CODEC));

    private RCRecipeTypes() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}

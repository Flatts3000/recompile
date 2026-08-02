package com.flatts.recompile.content.recipe;

import com.flatts.recompile.registry.RCRecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

/**
 * The public {@code recompile:blueprint_crafting} recipe type (#95, spec
 * {@code docs/blueprints_spec.md}): something you can only make while holding the sheet that says how.
 *
 * <p>JSON shape ({@code data/<ns>/recipe/<name>.json}):
 * <pre>{@code
 * {
 *   "type": "recompile:blueprint_crafting",
 *   "blueprint": "recompile:clean_mattress",
 *   "key": { "W": "#minecraft:wool", "S": "minecraft:string" },
 *   "pattern": [ "WWW", "SSS" ],
 *   "result": { "item": "recompile:clean_mattress", "count": 1 }
 * }
 * }</pre>
 *
 * <p><b>Shaped, using vanilla's own {@link ShapedRecipePattern}.</b> It was shapeless first, on the
 * argument that the blueprint is already the puzzle - but a blueprint that does not say how the thing
 * is laid out is not much of a blueprint, and a pile of ingredients in no arrangement reads as a
 * lesser recipe than the vanilla ones beside it. Reusing vanilla's pattern rather than hand-rolling
 * one also inherits its mirroring, its size limits and its JSON shape, so a pack author writing one of
 * these writes exactly what they write for any shaped recipe.
 *
 * <p><b>{@code blueprint} names a set, not a recipe.</b> Several recipes may name the same set, so one
 * sheet can unlock a small family - IE's model, and what stops a twenty-recipe tier needing twenty
 * items. {@link #matches} takes the ingredients only; whether the player holds the right sheet is the
 * bench's question, because a recipe type has no way to see an item that is not part of its input.
 *
 * <p><b>It is registered now and consumed in phase 4</b>, the same reason {@code recompile:teardown}
 * was registered in Phase 0 with nothing reading it: a public schema that arrives after the packs do is
 * a breaking change, and one that arrives first is an extension point.
 */
public class BlueprintCraftingRecipe implements Recipe<CraftingInput> {

    /** The result: an item and a count. */
    public record Result(Item item, int count) {
        public static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Result::item),
            Codec.intRange(1, 99).optionalFieldOf("count", 1).forGetter(Result::count)
        ).apply(i, Result::new));

        public ItemStack toStack() {
            return new ItemStack(item, count);
        }
    }

    public static final MapCodec<BlueprintCraftingRecipe> CODEC =
        RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("blueprint").forGetter(BlueprintCraftingRecipe::blueprint),
            ShapedRecipePattern.MAP_CODEC.forGetter(BlueprintCraftingRecipe::pattern),
            Result.CODEC.fieldOf("result").forGetter(BlueprintCraftingRecipe::result)
        ).apply(instance, BlueprintCraftingRecipe::new));

    // Bridged from the map codec rather than composed field by field, matching TeardownRecipe: recipes
    // sync once on join, and this cannot hit a component-arity ceiling as the schema grows.
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintCraftingRecipe> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    private final Identifier blueprint;
    private final ShapedRecipePattern pattern;
    private final Result result;

    public BlueprintCraftingRecipe(Identifier blueprint, ShapedRecipePattern pattern, Result result) {
        this.blueprint = blueprint;
        this.pattern = pattern;
        this.result = result;
    }

    public ShapedRecipePattern pattern() {
        return pattern;
    }

    /** The blueprint set a player must be holding for this recipe to run. */
    public Identifier blueprint() {
        return blueprint;
    }

    /** The ingredients in grid order, empty slots included. */
    public List<java.util.Optional<Ingredient>> ingredients() {
        return pattern.ingredients();
    }

    public Result result() {
        return result;
    }

    /**
     * Whether the grid matches, position and all.
     *
     * <p>Deliberately does <b>not</b> check the blueprint. A recipe only sees its own input, so it
     * cannot know what a player is carrying or what block is next door - the table asks that question
     * (see {@code BlueprintAccess}). Splitting it this way keeps the recipe honest about what it can
     * actually know.
     */
    @Override
    public boolean matches(CraftingInput input, Level level) {
        return pattern.matches(input);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return result.toStack();
    }

    @Override
    public boolean isSpecial() {
        // Keeps it out of the vanilla recipe book, which cannot express "and you must be holding this".
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public RecipeSerializer<BlueprintCraftingRecipe> getSerializer() {
        return RCRecipeTypes.BLUEPRINT_CRAFTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<BlueprintCraftingRecipe> getType() {
        return RCRecipeTypes.BLUEPRINT_CRAFTING.get();
    }
}

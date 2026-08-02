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
import net.minecraft.util.ExtraCodecs;
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
 *   "ingredients": [ "recompile:dirty_mattress", "#c:fibers", "#c:fibers" ],
 *   "result": { "item": "recompile:clean_mattress", "count": 1 }
 * }
 * }</pre>
 *
 * <p><b>Shapeless, deliberately.</b> The blueprint is the puzzle; making the player also work out a
 * grid arrangement is a second lock on one door, and the recipe is going to be read off the blueprint's
 * own screen rather than discovered. {@code ingredients} is a flat list because the bench that runs
 * these is a list of slots, not a 3x3.
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
            ExtraCodecs.nonEmptyList(Ingredient.CODEC.listOf()).fieldOf("ingredients")
                .forGetter(BlueprintCraftingRecipe::ingredients),
            Result.CODEC.fieldOf("result").forGetter(BlueprintCraftingRecipe::result)
        ).apply(instance, BlueprintCraftingRecipe::new));

    // Bridged from the map codec rather than composed field by field, matching TeardownRecipe: recipes
    // sync once on join, and this cannot hit a component-arity ceiling as the schema grows.
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintCraftingRecipe> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    private final Identifier blueprint;
    private final List<Ingredient> ingredients;
    private final Result result;

    public BlueprintCraftingRecipe(Identifier blueprint, List<Ingredient> ingredients, Result result) {
        this.blueprint = blueprint;
        this.ingredients = List.copyOf(ingredients);
        this.result = result;
    }

    /** The blueprint set a player must be holding for this recipe to run. */
    public Identifier blueprint() {
        return blueprint;
    }

    public List<Ingredient> ingredients() {
        return ingredients;
    }

    public Result result() {
        return result;
    }

    /**
     * Whether the supplied items satisfy this recipe, ignoring order.
     *
     * <p>Deliberately does <b>not</b> check the blueprint. A recipe only sees its own input, and the
     * sheet is held rather than consumed - so the bench asks that question in phase 4. Splitting it this
     * way keeps the recipe honest about what it can actually know.
     */
    @Override
    public boolean matches(CraftingInput input, Level level) {
        List<ItemStack> supplied = new java.util.ArrayList<>();
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (!stack.isEmpty()) {
                supplied.add(stack);
            }
        }
        if (supplied.size() != ingredients.size()) {
            return false;
        }
        // Greedy match against a shrinking pool. The lists are a handful of entries long, so the cost
        // of being exact here is nothing and the alternative - matching by index - would make a
        // shapeless recipe secretly order-dependent.
        List<ItemStack> pool = new java.util.ArrayList<>(supplied);
        for (Ingredient ingredient : ingredients) {
            int found = -1;
            for (int i = 0; i < pool.size(); i++) {
                if (ingredient.test(pool.get(i))) {
                    found = i;
                    break;
                }
            }
            if (found < 0) {
                return false;
            }
            pool.remove(found);
        }
        return true;
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

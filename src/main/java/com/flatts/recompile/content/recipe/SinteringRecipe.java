package com.flatts.recompile.content.recipe;

import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * {@code recompile:sintering} - fuse a pressed powder into a solid without melting it (#248).
 *
 * <p><b>The fifth verb, and the first that goes the other way.</b> Every machine this mod had ran one
 * direction: the Trommel cuts a block into its drops, the Separator divides a mixed feed, the
 * Pulverizer reduces fineness, and the Slag Furnace changes a material's state. All four take
 * something apart. The Pulverizer alone ships seven recipes and every one of them produces a POWDER -
 * so the mod had a machine dedicated to making powder and nothing at all that turned powder back into
 * a solid. That is not a missing recipe, it is a missing direction, and it is why blaze rods had been
 * stuck since the compacted depths shipped.
 *
 * <p><b>Sintering is the honest word.</b> You cannot melt blaze powder - it is already fire - so the
 * real operation is consolidating particles under heat and pressure <i>below</i> melting until they
 * fuse. That is exactly how powder metallurgy makes rod stock, and it is a different thing from
 * vitrifying: vitrifying takes a solid to a glass, this takes a loose powder to a solid.
 *
 * <p><b>Why the input is a briquette and not the powder itself.</b> A cooking recipe consumes exactly
 * one item, so {@code blaze powder -> blaze rod} would be one-for-one - and vanilla crafts one rod
 * into TWO powder, which makes that an infinite rod loop rather than a recipe. The fix is not a
 * workaround, it is the rest of the real process: powder metallurgy compacts a green body first and
 * sinters it second, and a green compact is famously fragile until it is fired. So four blaze powder
 * press into a Blaze Briquette at a bench, and the kiln fires the briquette. A rod therefore costs
 * four powder and melts back down to two, which is a loss in both directions and cannot loop.
 *
 * <p>Vanilla's cooking shape, like {@link VitrifyingRecipe}: one input, one output, a cook time and
 * experience. A pack writes a sintering recipe the way it writes any smelting one.
 */
public class SinteringRecipe extends AbstractCookingRecipe {

    /**
     * Slower than smelting, faster than vitrifying. Fusing a compact is a long soak rather than a
     * melt, but it is not the hours that turning rock to glass stands in for.
     */
    public static final int DEFAULT_COOKING_TIME = 250;

    public static final MapCodec<SinteringRecipe> CODEC =
        cookingMapCodec(SinteringRecipe::new, DEFAULT_COOKING_TIME);
    public static final StreamCodec<RegistryFriendlyByteBuf, SinteringRecipe> STREAM_CODEC =
        cookingStreamCodec(SinteringRecipe::new);

    public SinteringRecipe(Recipe.CommonInfo commonInfo, CookingBookInfo bookInfo,
            Ingredient ingredient, ItemStackTemplate result, float experience, int cookingTime) {
        super(commonInfo, bookInfo, ingredient, result, experience, cookingTime);
    }

    /** What JEI draws beside the recipe. */
    @Override
    protected Item furnaceIcon() {
        return RCItems.SINTERING_KILN.get();
    }

    /**
     * <b>Never in a recipe book</b>, for the reason {@link VitrifyingRecipe#isSpecial()} sets out at
     * length and the owner made standing on 2026-08-19: no recipe-book buttons in this mod's machines.
     * Every {@code RecipeBookCategory} that exists belongs to a vanilla screen's tab list, so filing
     * there offers the recipe inside a machine that cannot run it.
     */
    @Override
    public boolean isSpecial() {
        return true;
    }

    /**
     * Unused - {@link #isSpecial()} keeps this out of every book - but the interface requires a
     * category, so it names one.
     */
    @Override
    public net.minecraft.world.item.crafting.RecipeBookCategory recipeBookCategory() {
        return switch (this.category()) {
            case BLOCKS -> net.minecraft.world.item.crafting.RecipeBookCategories.FURNACE_BLOCKS;
            case FOOD, MISC -> net.minecraft.world.item.crafting.RecipeBookCategories.FURNACE_MISC;
        };
    }

    @Override
    public RecipeSerializer<SinteringRecipe> getSerializer() {
        return RCRecipeTypes.SINTERING_SERIALIZER.get();
    }

    @Override
    public RecipeType<SinteringRecipe> getType() {
        return RCRecipeTypes.SINTERING.get();
    }
}

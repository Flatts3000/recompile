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
 * {@code recompile:vitrifying} - melt something and let it freeze as glass (#236).
 *
 * <p><b>The fourth verb, and it had to be its own.</b> The Trommel makes a size cut, the Separator
 * divides a mixed feed, the Pulverizer reduces fineness - all three change what a material <em>is</em>
 * or how fine it is. Vitrifying changes its <b>state</b>: the same silicates go in and come out as an
 * amorphous glass. Nothing else in the mod expresses that.
 *
 * <p><b>And it is the gate.</b> Obsidian is "made only" by {@code material_economy.md}, and the portal
 * gate rides on that - so the operation has to be one no other machine in the game can perform.
 * Measured rather than assumed: {@code minecraft:smelting} would let a vanilla furnace do it, and
 * {@code minecraft:blasting} would let a vanilla blast furnace do it, <b>which is craftable in this
 * world</b> because iron is reachable through the Cupola. A distinct recipe type is a property of the
 * machine rather than an absence of materials, which is the same lesson the iron gate cost two designs
 * to learn (#91).
 *
 * <p>Vanilla's cooking shape rather than the Separator's, because this genuinely is a furnace: one
 * input, one output, a cook time and experience. {@code cookingMapCodec} supplies the whole schema, so
 * this class is the same size vanilla's own {@link net.minecraft.world.item.crafting.BlastingRecipe}
 * is - and a pack writes a vitrifying recipe the way it writes any smelting one.
 */
public class VitrifyingRecipe extends AbstractCookingRecipe {

    /** Slower than blasting and slower than smelting: melting rock to glass is the long job here. */
    public static final int DEFAULT_COOKING_TIME = 300;

    public static final MapCodec<VitrifyingRecipe> CODEC =
        cookingMapCodec(VitrifyingRecipe::new, DEFAULT_COOKING_TIME);
    public static final StreamCodec<RegistryFriendlyByteBuf, VitrifyingRecipe> STREAM_CODEC =
        cookingStreamCodec(VitrifyingRecipe::new);

    public VitrifyingRecipe(Recipe.CommonInfo commonInfo, CookingBookInfo bookInfo,
            Ingredient ingredient, ItemStackTemplate result, float experience, int cookingTime) {
        super(commonInfo, bookInfo, ingredient, result, experience, cookingTime);
    }

    /** What JEI and the recipe book draw beside the recipe. */
    @Override
    protected Item furnaceIcon() {
        return RCItems.SLAG_FURNACE.get();
    }

    /**
     * <b>Never in a recipe book.</b>
     *
     * <p>{@code RecipeBook.add} skips a special recipe, so it is never marked known and never files
     * into any book at all. That is the point: every {@code RecipeBookCategory} that exists belongs to
     * a VANILLA screen's tab list, and filing there leaks the recipe into a machine that cannot run it.
     * This shipped reusing {@code BLAST_FURNACE_BLOCKS}, which meant a player who pulled obsidian out
     * of a Slag Furnace then opened a vanilla blast furnace was offered Obsidian in its book - clicking
     * it loaded their slag into a machine where it would sit forever, and it visibly contradicted the
     * "nothing else can vitrify" gate this whole recipe type exists to enforce.
     *
     * <p>Minting our own category is not an escape either: a category is only ever drawn by the screen
     * whose tab list names it, and this machine's screen has no recipe book. With one recipe in the
     * type there is nothing for a book to be useful about, so the honest answer is not to be in one.
     *
     * <p>It does not affect JEI, which reads the recipe map and not the book.
     */
    @Override
    public boolean isSpecial() {
        return true;
    }

    /**
     * Unused in practice - {@link #isSpecial()} keeps this recipe out of every book - but a
     * {@code RecipeBookCategory} is not optional on the interface, so it has to name something.
     */
    @Override
    public net.minecraft.world.item.crafting.RecipeBookCategory recipeBookCategory() {
        return switch (this.category()) {
            case BLOCKS -> net.minecraft.world.item.crafting.RecipeBookCategories.BLAST_FURNACE_BLOCKS;
            case FOOD, MISC -> net.minecraft.world.item.crafting.RecipeBookCategories.BLAST_FURNACE_MISC;
        };
    }

    @Override
    public RecipeSerializer<VitrifyingRecipe> getSerializer() {
        return RCRecipeTypes.VITRIFYING_SERIALIZER.get();
    }

    @Override
    public RecipeType<VitrifyingRecipe> getType() {
        return RCRecipeTypes.VITRIFYING.get();
    }
}

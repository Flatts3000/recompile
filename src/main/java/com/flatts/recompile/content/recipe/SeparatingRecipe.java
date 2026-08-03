package com.flatts.recompile.content.recipe;

import com.flatts.recompile.registry.RCRecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;

/**
 * {@code recompile:separating} - the gem tier's recipe type ({@code docs/gem_tier_spec.md}).
 *
 * <p><b>Why this is not a teardown with a different station.</b> The teardown schema already carries a
 * {@code station} field, so reusing it looks free. It is not, for three reasons. Teardown consumes a
 * <b>single item</b> and this tier's entire point is that many inputs become a little output. Teardown
 * is <b>public API</b> - packs extend it without a mod release - so bolting a count field onto it would
 * silently redefine every recipe anyone has already written as count-1. And the guard test has to tell
 * the two apart cleanly, which two types give for free and a station discriminator does not.
 *
 * <p><b>{@code count} is the concentration dial.</b> One circuit board is worth nothing; that is the
 * real economics of urban mining and it is the gate this tier is built on. Because the gate is
 * arithmetic rather than the absence of a material, it cannot die the way the first iron gate did
 * (#91): another mod flooding the player with scrap just means they reach the gem using that mod's
 * scrap, which is the correct outcome rather than a leak.
 *
 * <p><b>Byproducts are deterministic, not weighted.</b> A separator splits a feed into streams; it does
 * not roll for a bonus. Determinism is also what keeps the machine tunable, because the luck in this
 * tier already lives in {@code mechanical_pulls} where it can be balanced in one place.
 */
public class SeparatingRecipe implements Recipe<SingleRecipeInput> {

    /** Ticks per operation when a recipe does not say. 200 = 10 seconds. */
    public static final int DEFAULT_TICKS = 200;
    /** FE per tick while running when a recipe does not say. */
    public static final int DEFAULT_ENERGY = 16;

    public static final MapCodec<SeparatingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Ingredient.CODEC.fieldOf("input").forGetter(SeparatingRecipe::input),
        Codec.intRange(1, 999).fieldOf("count").forGetter(SeparatingRecipe::count),
        ExtraCodecs.nonEmptyList(TeardownRecipe.ItemResult.CODEC.listOf())
            .fieldOf("results").forGetter(SeparatingRecipe::results),
        TeardownRecipe.ItemResult.CODEC.listOf()
            .optionalFieldOf("byproducts", List.of()).forGetter(SeparatingRecipe::byproducts),
        Codec.intRange(1, 72000).optionalFieldOf("ticks", DEFAULT_TICKS).forGetter(SeparatingRecipe::ticks),
        Codec.intRange(0, 100000).optionalFieldOf("energy", DEFAULT_ENERGY).forGetter(SeparatingRecipe::energy)
    ).apply(instance, SeparatingRecipe::new));

    // Bridged from the map codec rather than composed field by field, the way TeardownRecipe is: recipes
    // sync once on join and this has no component-arity ceiling as the schema grows.
    public static final StreamCodec<RegistryFriendlyByteBuf, SeparatingRecipe> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    private final Ingredient input;
    private final int count;
    private final List<TeardownRecipe.ItemResult> results;
    private final List<TeardownRecipe.ItemResult> byproducts;
    private final int ticks;
    private final int energy;

    public SeparatingRecipe(Ingredient input, int count, List<TeardownRecipe.ItemResult> results,
                            List<TeardownRecipe.ItemResult> byproducts, int ticks, int energy) {
        this.input = input;
        this.count = count;
        this.results = List.copyOf(results);
        this.byproducts = List.copyOf(byproducts);
        this.ticks = ticks;
        this.energy = energy;
    }

    public Ingredient input() {
        return input;
    }

    /** How many of the input one operation consumes. The concentration dial. */
    public int count() {
        return count;
    }

    /** The raw material this separates out. */
    public List<TeardownRecipe.ItemResult> results() {
        return results;
    }

    /** Ordinary scrap recovered alongside it. Always produced, never rolled. */
    public List<TeardownRecipe.ItemResult> byproducts() {
        return byproducts;
    }

    public int ticks() {
        return ticks;
    }

    /** FE per tick while this recipe is running. Per tick, not per operation, so an underpowered
     *  machine visibly stalls rather than silently refusing. */
    public int energy() {
        return energy;
    }

    /**
     * Whether this recipe accepts the given stack, <b>ignoring how many there are</b>. The count is
     * checked by the machine, which is the only thing that knows how much material is in front of it.
     */
    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return this.input.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return results.isEmpty() ? ItemStack.EMPTY : results.get(0).toStack();
    }

    @Override
    public boolean isSpecial() {
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
    public List<RecipeDisplay> display() {
        return List.of();
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return net.minecraft.world.item.crafting.RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public RecipeSerializer<SeparatingRecipe> getSerializer() {
        return RCRecipeTypes.SEPARATING_SERIALIZER.get();
    }

    @Override
    public RecipeType<SeparatingRecipe> getType() {
        return RCRecipeTypes.SEPARATING.get();
    }
}

package com.flatts.recompile.content.recipe;

import com.flatts.recompile.registry.RCRecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
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
 * {@code recompile:pulverizing} - one input, one finer output (#189).
 *
 * <p><b>The Separator divides; the Pulverizer reduces.</b> That is the whole reason this is its own
 * type rather than a {@code separating} recipe with one result. The test is whether the operation
 * changes <em>what</em> the material is or only <em>how fine</em> it is: Spent Abrasive to diamond
 * changes what it is, E-Scrap to circuit powder does not. Separating is one input yielding several
 * distinct outputs plus byproducts; pulverizing is one input yielding one finer output, and a schema
 * that expresses both expresses neither.
 *
 * <p>{@code separating} is also <b>public API</b> that packs extend without a mod release, so
 * overloading it with a shape it was not designed for would redefine what an existing recipe means for
 * everyone who has already written one.
 *
 * <p><b>{@code count} is the ratio dial</b>, and it is the reason it exists here when the Separator's
 * ships at 1. Gold gets tuned by how much E-Scrap a nugget costs, and doing it here rather than in
 * E-Scrap's drop weight leaves the Motor and the Solar Panel - which are fed by the same drop -
 * untouched. Two dials for two jobs.
 *
 * <p><b>No byproducts, deliberately.</b> A mill does not sort; everything that goes in comes out as
 * the same powder. A recipe that wanted a second distinct output would be describing separation, and
 * the machine for that already exists.
 */
public class PulverizingRecipe implements Recipe<SingleRecipeInput> {

    /** Ticks per operation when a recipe does not say. Slower than a grind: impact takes longer. */
    public static final int DEFAULT_TICKS = 60;

    /** FE per tick while running when a recipe does not say. */
    public static final int DEFAULT_ENERGY = 24;

    public static final MapCodec<PulverizingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Ingredient.CODEC.fieldOf("input").forGetter(PulverizingRecipe::input),
        Codec.intRange(1, 999).optionalFieldOf("count", 1).forGetter(PulverizingRecipe::count),
        TeardownRecipe.ItemResult.CODEC.fieldOf("result").forGetter(PulverizingRecipe::result),
        Codec.intRange(1, 72000).optionalFieldOf("ticks", DEFAULT_TICKS).forGetter(PulverizingRecipe::ticks),
        Codec.intRange(0, 100000).optionalFieldOf("energy", DEFAULT_ENERGY).forGetter(PulverizingRecipe::energy)
    ).apply(instance, PulverizingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PulverizingRecipe> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    private final Ingredient input;
    private final int count;
    private final TeardownRecipe.ItemResult result;
    private final int ticks;
    private final int energy;

    public PulverizingRecipe(Ingredient input, int count, TeardownRecipe.ItemResult result,
                             int ticks, int energy) {
        this.input = input;
        this.count = count;
        this.result = result;
        this.ticks = ticks;
        this.energy = energy;
    }

    public Ingredient input() {
        return input;
    }

    /** How many of the input one operation consumes - the ratio dial. */
    public int count() {
        return count;
    }

    /** The single finer thing this makes. */
    public TeardownRecipe.ItemResult result() {
        return result;
    }

    public int ticks() {
        return ticks;
    }

    /** FE per tick while running, not per operation, so an underpowered mill visibly stalls. */
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
        return result.toStack();
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
    public RecipeSerializer<PulverizingRecipe> getSerializer() {
        return RCRecipeTypes.PULVERIZING_SERIALIZER.get();
    }

    @Override
    public RecipeType<PulverizingRecipe> getType() {
        return RCRecipeTypes.PULVERIZING.get();
    }
}

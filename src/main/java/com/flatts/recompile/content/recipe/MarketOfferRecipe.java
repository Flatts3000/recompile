package com.flatts.recompile.content.recipe;

import com.flatts.recompile.content.market.Market;
import com.flatts.recompile.registry.RCRecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * The public {@code recompile:market_offer} recipe type: one line of the Buy Terminal's stock.
 *
 * <p>JSON shape ({@code data/<ns>/recipe/<name>.json}):
 * <pre>{@code
 * {
 *   "type": "recompile:market_offer",
 *   "blueprint": "recompile:battery",
 *   "price": 240
 * }
 * }</pre>
 *
 * <p><b>A recipe type rather than a data map</b>, because a Blueprint set is not a registry entry - it
 * is an {@link Identifier} on an item component - so there is no registry to key a data map on. A
 * recipe is the other thing a pack already writes by dropping in a file, it reloads with the world,
 * and the terminal reads the loaded set off the recipe manager when it opens. It is never matched
 * against anything: {@link #matches} is false by construction, and {@link #isSpecial} keeps it out of
 * the recipe book, where a recipe with no ingredients and no fixed result has nothing to show.
 *
 * <p><b>Priced in the balance alone</b> (owner, 2026-08-30). There is no fragment field and there is
 * not going to be one: money replaces the grind rather than discounting it.
 */
public class MarketOfferRecipe implements Recipe<RecipeInput> {

    public static final MapCodec<MarketOfferRecipe> CODEC =
        RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("blueprint").forGetter(MarketOfferRecipe::blueprint),
            Codec.intRange(1, Market.MAX_BALANCE).fieldOf("price").forGetter(MarketOfferRecipe::price)
        ).apply(instance, MarketOfferRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketOfferRecipe> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    private final Identifier blueprint;
    private final int price;

    public MarketOfferRecipe(Identifier blueprint, int price) {
        this.blueprint = blueprint;
        this.price = price;
    }

    /** The Blueprint set this line sells. */
    public Identifier blueprint() {
        return blueprint;
    }

    /** What it costs, in scrip. */
    public int price() {
        return price;
    }

    public Market.Offer offer() {
        return new Market.Offer(blueprint, price);
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput input) {
        return ItemStack.EMPTY;
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
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public RecipeSerializer<MarketOfferRecipe> getSerializer() {
        return RCRecipeTypes.MARKET_OFFER_SERIALIZER.get();
    }

    @Override
    public RecipeType<MarketOfferRecipe> getType() {
        return RCRecipeTypes.MARKET_OFFER.get();
    }
}

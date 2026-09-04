package com.flatts.recompile.content.recipe;

import com.flatts.recompile.content.item.BlueprintItem;
import com.flatts.recompile.content.market.Market;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
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
 * <p>JSON shape ({@code data/<ns>/recipe/<name>.json}), one of two forms:
 * <pre>{@code
 * { "type": "recompile:market_offer", "blueprint": "recompile:battery",        "price": 240 }
 * { "type": "recompile:market_offer", "item": "minecraft:totem_of_undying",    "price": 2500 }
 * { "type": "recompile:market_offer", "item": "recompile:rebar", "count": 8,   "price": 60 }
 * }</pre>
 *
 * <p><b>Exactly one of {@code blueprint} and {@code item}</b>, and the split is the whole design of
 * the type. A {@code blueprint} line sells KNOWLEDGE - the sheet the fragment loop also produces,
 * so the buyer still needs every material and the bench. An {@code item} line sells the THING, and
 * that is a genuinely different promise: it is the only route in this mod by which an object can
 * enter the world without being found, grown, or built. See {@code docs/market_spec.md} section 14
 * for what that opens and what guards it.
 *
 * <p><b>A recipe type rather than a data map</b>, because a Blueprint set is not a registry entry -
 * it is an {@link Identifier} on an item component - so there is no registry to key a data map on.
 * A recipe is the other thing a pack already writes by dropping in a file, it reloads with the
 * world, and the terminal reads the loaded set off the recipe manager when it opens. It is never
 * matched against anything: {@link #matches} is false by construction, and {@link #isSpecial} keeps
 * it out of the recipe book, where a recipe with no ingredients has nothing to show.
 *
 * <p><b>Priced in the balance alone</b> (owner, 2026-08-30). There is no fragment field and there is
 * not going to be one: money replaces the grind rather than discounting it.
 */
public class MarketOfferRecipe implements Recipe<RecipeInput> {

    public static final MapCodec<MarketOfferRecipe> CODEC =
        RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("blueprint").forGetter(MarketOfferRecipe::blueprint),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("item").forGetter(MarketOfferRecipe::item),
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(MarketOfferRecipe::count),
            Codec.intRange(1, Market.MAX_BALANCE).fieldOf("price").forGetter(MarketOfferRecipe::price)
        ).apply(instance, MarketOfferRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketOfferRecipe> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    private final Optional<Identifier> blueprint;
    private final Optional<Item> item;
    private final int count;
    private final int price;

    public MarketOfferRecipe(Optional<Identifier> blueprint, Optional<Item> item, int count,
            int price) {
        // AN IllegalArgumentException SPECIFICALLY, and that is load-bearing rather than a default.
        // SimpleJsonResourceReloadListener.scanDirectory wraps each file in
        // `catch (IllegalArgumentException | IOException | JsonParseException)`, so throwing one
        // here logs "Couldn't parse data file" against the offending file and skips it. Any other
        // unchecked type escapes that catch and takes the whole reload down, which would turn one
        // bad line in a pack into a broken world rather than one missing row.
        //
        // Neither field, or both, is an authoring mistake rather than a shape with a sensible
        // reading: a line that sells nothing is a row a player can click for silence.
        if (blueprint.isPresent() == item.isPresent()) {
            throw new IllegalArgumentException(
                "a market_offer needs exactly one of 'blueprint' or 'item', got "
                    + (blueprint.isPresent() ? "both" : "neither"));
        }
        // A sheet does not stack (BlueprintItem is stacksTo(1)), so a count on a blueprint line is
        // a request the terminal cannot honour. Refused rather than silently handing over one.
        if (blueprint.isPresent() && count != 1) {
            throw new IllegalArgumentException(
                "a market_offer selling a blueprint cannot set 'count' (got " + count
                    + "); a Blueprint does not stack");
        }
        // NO ItemStack IS BUILT HERE, AND IT CANNOT BE. Recipes are decoded before item components
        // are bound, so `new ItemStack(item)` in a recipe constructor throws "Components not bound
        // yet" and takes the whole reload down - which is how the count-against-max-stack-size check
        // that used to live here was written and immediately reverted. The clamp lives in offer()
        // instead, which runs when a screen opens and so is long past that boundary.
        this.blueprint = blueprint;
        this.item = item;
        this.count = count;
        this.price = price;
    }

    /** The Blueprint set this line sells, if it sells knowledge. */
    public Optional<Identifier> blueprint() {
        return blueprint;
    }

    /** The item this line sells, if it sells a thing. */
    public Optional<Item> item() {
        return item;
    }

    /** How many per purchase. Only meaningful on an {@code item} line; a sheet is always one. */
    public int count() {
        return count;
    }

    /** What it costs, in scrip. */
    public int price() {
        return price;
    }

    /**
     * What the buyer receives, as the stack the terminal will hand over.
     *
     * <p>Built here rather than stored, so a blueprint line resolves {@code RCItems.BLUEPRINT} at
     * datapack-load time rather than at class-init - the "Components not bound yet" trap.
     */
    public Market.Offer offer() {
        ItemStack stack = blueprint
            .map(set -> BlueprintItem.of(RCItems.BLUEPRINT.get(), set))
            .orElseGet(() -> new ItemStack(item.orElseThrow()));
        // Clamped rather than refused at parse, because deciding it needs the item's components and
        // a recipe is decoded before those are bound - see the constructor. `count` is already
        // bounded to 1..64 by the codec, so this only bites an item that stacks to less.
        stack.setCount(Math.min(count, stack.getMaxStackSize()));
        return new Market.Offer(stack, price);
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

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
 * The public {@code recompile:freight_phase} recipe type: one rung of the freight ladder (#387,
 * spec {@code docs/freight_conversion_spec.md}).
 *
 * <p>JSON shape ({@code data/<ns>/recipe/<name>.json}):
 * <pre>{@code
 * {
 *   "type": "recompile:freight_phase",
 *   "tier": 3,
 *   "name": "freight.recompile.phase.3",
 *   "requires": [
 *     { "item": "recompile:reinforced_concrete", "count": 200 },
 *     { "item": "recompile:steel_offcut",        "count": 50  }
 *   ]
 * }
 * }</pre>
 *
 * <p><b>A recipe type rather than a data map, for the reason {@code market_offer} is one</b> (#370):
 * a phase is keyed by its own id rather than by a registry entry, so there is no registry to hang a
 * data map off, and a recipe file is the other thing a pack already extends by dropping one in. It
 * reloads with the world, and the terminal reads the loaded set off the recipe manager.
 *
 * <p><b>It is never matched against anything.</b> {@link #matches} is false by construction and
 * {@link #isSpecial} keeps it out of the recipe book, where a recipe with no ingredients has nothing
 * to show. This type is data wearing a recipe's clothes.
 *
 * <p><b>Tiers are unique and dense, and that is checked in the terminal rather than here.</b> A
 * codec sees one file at a time and cannot know that two phases claim tier 3, or that the set jumps
 * from 2 to 4. {@code FreightPhases} validates the loaded set; this class only guarantees that a
 * single file is well formed.
 *
 * <p><b>The exception type is load-bearing.</b> {@code SimpleJsonResourceReloadListener.scanDirectory}
 * catches {@code IllegalArgumentException | IOException | JsonParseException} per file, so validation
 * that throws one of those costs a pack a missing rung and anything else costs it the world. Same
 * reasoning as {@code MarketOfferRecipe}'s constructor, and the same choice.
 */
public class FreightPhaseRecipe implements Recipe<RecipeInput> {

    /** One line of a phase's manifest. */
    public record Requirement(Item item, int count) {
        public static final Codec<Requirement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Requirement::item),
            // Bounded rather than open. The upper end is a pack's problem to justify and the lower
            // end stops a zero-count line, which would be a requirement that is already met.
            Codec.intRange(1, 100_000).fieldOf("count").forGetter(Requirement::count)
        ).apply(instance, Requirement::new));
    }

    public static final MapCodec<FreightPhaseRecipe> CODEC =
        RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.intRange(1, 64).fieldOf("tier").forGetter(FreightPhaseRecipe::tier),
            Codec.STRING.fieldOf("name").forGetter(FreightPhaseRecipe::name),
            Requirement.CODEC.listOf().fieldOf("requires").forGetter(FreightPhaseRecipe::requires)
        ).apply(instance, FreightPhaseRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FreightPhaseRecipe> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    private final int tier;
    private final String name;
    private final List<Requirement> requires;

    public FreightPhaseRecipe(int tier, String name, List<Requirement> requires) {
        if (requires.isEmpty()) {
            throw new IllegalArgumentException(
                "freight phase for tier " + tier + " requires nothing, so it is already complete");
        }
        // Two lines naming the same item is an authoring mistake with no sensible reading: the
        // terminal would count against whichever it hit first and the other would never fall. Caught
        // here because a codec CAN see this one - it is within a single file.
        long distinct = requires.stream().map(Requirement::item).distinct().count();
        if (distinct != requires.size()) {
            throw new IllegalArgumentException(
                "freight phase for tier " + tier + " names the same item twice");
        }
        this.tier = tier;
        this.name = name;
        this.requires = List.copyOf(requires);
    }

    public int tier() {
        return tier;
    }

    /** A translation key. Player-facing text is lang, never a literal in data. */
    public String name() {
        return name;
    }

    public List<Requirement> requires() {
        return requires;
    }

    /** How many of {@code item} this phase wants, or 0 if it does not want it at all. */
    public int required(Item item) {
        for (Requirement requirement : requires) {
            if (requirement.item() == item) {
                return requirement.count();
            }
        }
        return 0;
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
    public RecipeSerializer<FreightPhaseRecipe> getSerializer() {
        return RCRecipeTypes.FREIGHT_PHASE_SERIALIZER.get();
    }

    @Override
    public RecipeType<FreightPhaseRecipe> getType() {
        return RCRecipeTypes.FREIGHT_PHASE.get();
    }
}

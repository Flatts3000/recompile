package com.flatts.recompile.content.recipe;

import com.flatts.recompile.content.item.BlueprintItem;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
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
 * The public {@code recompile:spawn_egg_crafting} recipe type (#294): a vessel built around a
 * Blueprint, which decides what comes out of it.
 *
 * <p>JSON shape ({@code data/<ns>/recipe/<name>.json}) - a shaped pattern like any other, plus the one
 * character that must be the sheet:
 * <pre>{@code
 * {
 *   "type": "recompile:spawn_egg_crafting",
 *   "key": { "B": "recompile:blueprint", "G": "recompile:glass_shards",
 *            "R": "recompile:rendered_organics" },
 *   "pattern": [ " G ", "GBG", " R " ]
 * }
 * }</pre>
 *
 * <p><b>Why this is not a {@code blueprint_crafting} recipe, which is where it started.</b> A spawn
 * egg needs one blueprint SET PER SPECIES - that is what makes "four fragments of one species" mean
 * anything, and {@code idea_fragments_are_specific_to_their_blueprint} enforces it. Writing that as
 * one {@code blueprint_crafting} recipe per species gives 29 recipes sharing one 3x3 arrangement, and
 * the bench resolves a blueprint recipe by looping until it finds one whose sheet is reachable: a
 * player holding a cow sheet and a pig sheet would get whichever iterated first. Non-deterministic,
 * and {@code every_crafting_recipe_is_reachable_at_a_bench} fails it as the collision it is. So the
 * species has to be named by something IN the grid, which is the sheet itself.
 *
 * <p><b>The sheet is an input here and nowhere else, and it is never consumed.</b> Every other
 * blueprint recipe reads the sheet out of your pocket or a filing cabinet, because knowledge is not a
 * material. That still holds, but NOT from here: 26.1's {@code ResultSlot.getRemainingItems} is
 * private and resolves {@code RecipeType.CRAFTING} only, so a custom type cannot supply a remainder.
 * The Scrap Crafting Table's own result slot puts the sheet back instead, which is why laying it in
 * the grid is pointing at it rather than spending it. It is in the grid at all only because there is
 * no other way for a player to say WHICH egg they want.
 *
 * <p><b>One recipe, every creature.</b> The result is computed from the sheet's set, so a mob this mod
 * has never heard of works the day something adds it, with no per-species file anywhere. That is also
 * why the type exists rather than a hundred data files: the mapping from species to spawn egg is
 * mechanical, and a schema that made someone write it out by hand would be a worse schema.
 */
public class SpawnEggCraftingRecipe implements Recipe<CraftingInput> {

    public static final MapCodec<SpawnEggCraftingRecipe> CODEC =
        RecordCodecBuilder.mapCodec(instance -> instance.group(
            ShapedRecipePattern.MAP_CODEC.forGetter(SpawnEggCraftingRecipe::pattern)
        ).apply(instance, SpawnEggCraftingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnEggCraftingRecipe> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    private final ShapedRecipePattern pattern;

    public SpawnEggCraftingRecipe(ShapedRecipePattern pattern) {
        this.pattern = pattern;
    }

    public ShapedRecipePattern pattern() {
        return pattern;
    }

    /** The ingredients in grid order, empty slots included. */
    public List<Optional<Ingredient>> ingredients() {
        return pattern.ingredients();
    }

    /**
     * The grid is right AND the sheet in it names a creature that has a spawn egg.
     *
     * <p>Both halves are needed. The arrangement alone would let a BLANK blueprint, or one for the
     * Clean Mattress, sit in the middle and produce nothing - and a result slot that is empty for a
     * reason the player cannot see is the worst outcome, so the recipe simply does not match and the
     * table stays quiet rather than teasing.
     */
    @Override
    public boolean matches(CraftingInput input, Level level) {
        return pattern.matches(input) && !eggFor(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return eggFor(input);
    }

    /**
     * The spawn egg the sheet in this grid asks for, or nothing.
     *
     * <p>Nothing when: no Blueprint is present, its set is not a spawn-egg set, the species names an
     * entity that is not installed, or that entity has no spawn egg item. The last one is real rather
     * than defensive - plenty of entity types have none ({@code minecraft:item},
     * {@code minecraft:arrow}), so a datapack that stamps amber with one must fail here rather than
     * hand back air.
     */
    private static ItemStack eggFor(CraftingInput input) {
        Identifier species = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.is(RCItems.BLUEPRINT.get())) {
                continue;
            }
            Identifier found = speciesOf(stack);
            if (found == null) {
                return ItemStack.EMPTY;   // a sheet, but not one of these
            }
            if (species != null) {
                // Two sheets is an ambiguous request, and guessing which one the player meant is
                // exactly the non-determinism this recipe type exists to avoid.
                return ItemStack.EMPTY;
            }
            species = found;
        }
        if (species == null) {
            return ItemStack.EMPTY;
        }
        Item egg = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(
            species.getNamespace(), species.getPath() + "_spawn_egg"));
        // An id that resolves to nothing comes back as AIR rather than null, so this is the check that
        // stops a species with no egg producing an empty stack the table would show as a blank result.
        return egg == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(egg);
    }

    /** The species a Blueprint stack names, or null if it is not a spawn-egg sheet at all. */
    public static Identifier speciesOf(ItemStack blueprint) {
        Identifier set = BlueprintItem.blueprintOf(blueprint);
        if (set == null || !set.getPath().startsWith(BlueprintItem.SPAWN_EGG_PREFIX)) {
            return null;
        }
        String rest = set.getPath().substring(BlueprintItem.SPAWN_EGG_PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) {
            return null;
        }
        return Identifier.tryParse(rest.substring(0, slash) + ":" + rest.substring(slash + 1));
    }

    @Override
    public boolean isSpecial() {
        // Its result depends on a component of one of its inputs, which no recipe book can draw.
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
    public RecipeSerializer<SpawnEggCraftingRecipe> getSerializer() {
        return RCRecipeTypes.SPAWN_EGG_CRAFTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<SpawnEggCraftingRecipe> getType() {
        return RCRecipeTypes.SPAWN_EGG_CRAFTING.get();
    }
}

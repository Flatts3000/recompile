package com.flatts.recompile.content.recipe;

import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A Clean Mattress and three planks make a bed, in whatever colour the mattress was dyed (#95).
 *
 * <p><b>This is the only bed recipe in the game.</b> Phase 1 deleted all sixteen wool-to-bed recipes,
 * so if this breaks the world has no beds at all - which is why it is asserted rather than assumed.
 *
 * <p><b>A special recipe, because the colour lives in a component.</b> Dyeing the mattress is an
 * ordinary crafting-table recipe that sets {@code minecraft:dyed_color}; a plain shaped recipe cannot
 * read that back, so sixteen bed recipes would each need a component-matching ingredient. One recipe
 * that reads the colour is smaller and cannot get out of step with the dye recipes.
 *
 * <p><b>An undyed mattress makes a white bed</b>, which is also what vanilla wool does. The colour map
 * is built from {@link DyeColor}'s own values, so it is exactly the sixteen a dye can produce and there
 * is no table to keep in sync.
 */
public class BedFromMattressRecipe extends CustomRecipe {

    // No fields; the JSON is {"type": "recompile:bed_from_mattress"} and nothing else.
    public static final com.mojang.serialization.MapCodec<BedFromMattressRecipe> CODEC =
        com.mojang.serialization.MapCodec.unit(BedFromMattressRecipe::new);
    public static final net.minecraft.network.codec.StreamCodec<
            net.minecraft.network.RegistryFriendlyByteBuf, BedFromMattressRecipe> STREAM_CODEC =
        net.minecraft.network.codec.StreamCodec.unit(new BedFromMattressRecipe());

    private static final int PLANKS_NEEDED = 3;

    /** Dye colour to the bed of that colour, derived from DyeColor rather than hand-listed. */
    private static final Map<Integer, Item> BEDS = new HashMap<>();

    static {
        for (DyeColor colour : DyeColor.values()) {
            BuiltInRegistries.ITEM
                .getOptional(Identifier.withDefaultNamespace(colour.getName() + "_bed"))
                .ifPresent(bed -> BEDS.put(colour.getTextureDiffuseColor(), bed));
        }
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !bedFor(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return bedFor(input);
    }

    /**
     * The bed these ingredients make, or empty.
     *
     * <p>Exactly one mattress and exactly three planks, and nothing else in the grid. Counting rather
     * than pattern-matching means the player can lay it out however they like, which matters more here
     * than shape does - a bed is not a shape puzzle.
     */
    private ItemStack bedFor(CraftingInput input) {
        ItemStack mattress = null;
        int planks = 0;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(RCItems.CLEAN_MATTRESS.get())) {
                if (mattress != null) {
                    return ItemStack.EMPTY;   // two mattresses is not one bed
                }
                mattress = stack;
            } else if (stack.is(ItemTags.PLANKS)) {
                planks += stack.getCount();
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (mattress == null || planks != PLANKS_NEEDED) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(bedColour(mattress));
    }

    /** The bed matching this mattress's dye, white if it was never dyed. */
    private static Item bedColour(ItemStack mattress) {
        @Nullable DyedItemColor dyed = mattress.get(DataComponents.DYED_COLOR);
        if (dyed == null) {
            return Items.WHITE_BED;
        }
        return BEDS.getOrDefault(dyed.rgb(), Items.WHITE_BED);
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return RCRecipeTypes.BED_FROM_MATTRESS_SERIALIZER.get();
    }
}

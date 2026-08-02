package com.flatts.recompile.content.recipe;

import com.flatts.recompile.content.item.BlueprintItem;
import com.flatts.recompile.content.item.IdeaFragmentItem;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Enough Idea Fragments about one thing become the Blueprint for it (#95, spec
 * {@code docs/blueprints_spec.md}).
 *
 * <p><b>A special recipe, because the ingredients are not a fixed list.</b> Every blueprint the mod or
 * a pack ever adds assembles the same way, and its fragments are distinguished by a data component
 * rather than by item id - so an ordinary shapeless recipe would need one file per blueprint and could
 * not match on the component anyway. This is the same reason vanilla makes map cloning and firework
 * assembly special recipes.
 *
 * <p><b>Every fragment in the grid must be for the same blueprint.</b> Mixing them is not a partial
 * match, it is a mistake, and returning nothing is how the grid says so. Without this rule a player
 * could pool unrelated ideas into whichever blueprint they liked, and the whole point of a fragment
 * naming its target is that you have to earn each one separately.
 *
 * <p>How many is {@code scraps_required} on the teardown that teaches it, read here off the recipe
 * that taught it rather than hardcoded, so a pack retunes the cost in the same file it sets the odds.
 */
public class FragmentAssemblyRecipe extends CustomRecipe {

    /** The fallback when no teardown declares a threshold for this blueprint. */
    public static final int DEFAULT_REQUIRED = 4;

    // No fields, so the JSON is {"type": "recompile:fragment_assembly"} and nothing else, and there
    // is nothing to put on the wire either.
    //
    // NOT StreamCodec.unit. That looks like the obvious fit for a value-less codec and is a trap: its
    // encoder ASSERTS the value equals the instance baked into it, and a recipe loaded from JSON is a
    // different object. Every client join died on "Can't encode ... expected ..." with the server
    // still perfectly healthy, so it read as a networking fault rather than a recipe one.
    public static final com.mojang.serialization.MapCodec<FragmentAssemblyRecipe> CODEC =
        com.mojang.serialization.MapCodec.unit(FragmentAssemblyRecipe::new);
    public static final net.minecraft.network.codec.StreamCodec<
            net.minecraft.network.RegistryFriendlyByteBuf, FragmentAssemblyRecipe> STREAM_CODEC =
        net.minecraft.network.codec.StreamCodec.of((buf, value) -> { }, buf -> new FragmentAssemblyRecipe());

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return resolve(input, level) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        Identifier set = resolveSet(input);
        return set == null
            ? ItemStack.EMPTY
            : BlueprintItem.of(RCItems.BLUEPRINT.get(), set);
    }

    /** The blueprint these fragments assemble into, or null if they do not. */
    private @Nullable Identifier resolve(CraftingInput input, Level level) {
        Identifier set = resolveSet(input);
        if (set == null) {
            return null;
        }
        return count(input) >= required(level, set) ? set : null;
    }

    /**
     * The one blueprint every non-empty slot points at, or null if the grid is empty, holds anything
     * that is not a fragment, or mixes fragments for different blueprints.
     */
    private @Nullable Identifier resolveSet(CraftingInput input) {
        Identifier set = null;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Identifier towards = IdeaFragmentItem.towards(stack);
            if (towards == null) {
                return null;   // something in the grid is not a fragment at all
            }
            if (set == null) {
                set = towards;
            } else if (!set.equals(towards)) {
                return null;   // two different ideas; not a partial match, a mistake
            }
        }
        return set;
    }

    /** Total fragments in the grid, counting stack sizes rather than occupied slots. */
    private int count(CraftingInput input) {
        int total = 0;
        for (int slot = 0; slot < input.size(); slot++) {
            total += input.getItem(slot).getCount();
        }
        return total;
    }

    /**
     * How many fragments this blueprint costs, taken from whichever teardown teaches it.
     *
     * <p>Read from the recipe manager rather than stored here, so {@code scraps_required} means one
     * thing in one place: a pack that retunes the odds of learning something retunes its cost in the
     * same file, and the two cannot drift into disagreeing.
     */
    private int required(Level level, Identifier set) {
        if (level.getServer() == null) {
            return DEFAULT_REQUIRED;
        }
        for (var holder : level.getServer().getRecipeManager().recipeMap()
                .byType(RCRecipeTypes.TEARDOWN.get())) {
            for (TeardownRecipe.TeachEntry entry : holder.value().teaches()) {
                if (entry.recipe().equals(set)) {
                    return entry.scrapsRequired();
                }
            }
        }
        return DEFAULT_REQUIRED;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return RCRecipeTypes.FRAGMENT_ASSEMBLY_SERIALIZER.get();
    }
}

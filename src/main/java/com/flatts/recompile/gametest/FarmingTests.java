package com.flatts.recompile.gametest;

import com.flatts.recompile.registry.RCItems;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * GameTests for the Farming tier (rung 3). Farmland is gated behind the compost economy: 26.1 gives
 * farmland a real item, so a plain crafting recipe outputs it directly (no custom block/item). Hoe-
 * tilling is cancelled via {@code RCFarming} so the recipe is the only path - untested here for want of
 * a hoe in the mod, but the recipe itself is the load-bearing part.
 */
final class FarmingTests {

    private FarmingTests() {
    }

    static void register() {
        // Fertilizer + dirt crafts vanilla farmland - the survival path to farmland in a world with no hoe.
        RCGameTests.test("fertilizer_and_dirt_craft_farmland", 20, helper -> {
            ServerLevel level = helper.getLevel();
            CraftingInput input = CraftingInput.of(2, 1,
                List.of(new ItemStack(RCItems.FERTILIZER.get()), new ItemStack(Items.DIRT)));
            Optional<RecipeHolder<CraftingRecipe>> recipe = level.getServer()
                .getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level, (RecipeHolder<CraftingRecipe>) null);
            helper.assertTrue(recipe.isPresent(), "fertilizer + dirt must have a crafting recipe");
            ItemStack result = recipe.get().value().assemble(input);
            helper.assertTrue(result.is(Items.FARMLAND),
                "the compost recipe must yield vanilla farmland, got " + result);
            helper.succeed();
        });
    }
}

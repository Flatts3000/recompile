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
        // Two survival paths to farmland in a world with no hoe: cheap on real dirt (1 fertilizer),
        // expensive on the raw dump surface (coarse dirt + 4 fertilizer).
        RCGameTests.test("compost_recipes_craft_farmland", 20, helper -> {
            ItemStack fert = new ItemStack(RCItems.FERTILIZER.get());
            assertCraftsFarmland(helper, 2, 1,
                List.of(fert.copy(), new ItemStack(Items.DIRT)), "dirt + 1 fertilizer");
            assertCraftsFarmland(helper, 3, 2,
                List.of(new ItemStack(Items.COARSE_DIRT), fert.copy(), fert.copy(), fert.copy(), fert.copy(),
                    ItemStack.EMPTY), "coarse dirt + 4 fertilizer");
            helper.succeed();
        });
    }

    private static void assertCraftsFarmland(net.minecraft.gametest.framework.GameTestHelper helper,
            int w, int h, List<ItemStack> items, String label) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(w, h, items);
        Optional<RecipeHolder<CraftingRecipe>> recipe = level.getServer()
            .getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level, (RecipeHolder<CraftingRecipe>) null);
        helper.assertTrue(recipe.isPresent(), label + " must have a crafting recipe");
        ItemStack result = recipe.get().value().assemble(input);
        helper.assertTrue(result.is(Items.FARMLAND), label + " must yield farmland, got " + result);
    }
}

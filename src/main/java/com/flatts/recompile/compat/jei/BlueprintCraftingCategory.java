package com.flatts.recompile.compat.jei;

import com.flatts.recompile.content.item.BlueprintItem;
import com.flatts.recompile.content.recipe.BlueprintCraftingRecipe;
import com.flatts.recompile.registry.RCItems;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Blueprint recipes drawn as recipes: the ingredients in a grid, the sheet you must be holding, and
 * what comes out.
 *
 * <p><b>This replaces a category that showed only the blueprint and the result.</b> That answered
 * "what does this sheet make" and left the actual question - how do I make a Clean Mattress - with no
 * answer anywhere in the game. A player clicking an item in JEI is asking for its recipe, and getting
 * a picture of a different item instead reads as the recipe being missing.
 *
 * <p><b>Ingredients go in through {@code addIngredients}, not as fixed stacks</b>, so a tag ingredient
 * cycles through everything it accepts. The Clean Mattress takes {@code #minecraft:wool}; showing only
 * white wool would state a restriction that does not exist.
 *
 * <p>The blueprint sits in its own slot away from the grid because it is <b>not consumed</b> and not
 * placed in the table - it only has to be in reach. A slot is the clearest way to say "you need this
 * too" without implying it goes in the grid, and the ingredient panel carries the rest.
 */
public class BlueprintCraftingCategory implements IRecipeCategory<BlueprintCraftingRecipe> {

    private static final int SLOT = 18;
    private static final int PAD = 4;
    private static final int GRID = 3;
    private static final int GAP = 8;

    private static final int GRID_W = GRID * SLOT;
    private static final int SHEET_X = PAD + GRID_W + GAP;
    private static final int RESULT_X = SHEET_X + SLOT + GAP + SLOT;

    private final RecipeType<BlueprintCraftingRecipe> type;
    private final Component title;
    private final IDrawable icon;

    public BlueprintCraftingCategory(RecipeType<BlueprintCraftingRecipe> type, Component title,
            IDrawable icon) {
        this.type = type;
        this.title = title;
        this.icon = icon;
    }

    @Override
    public IRecipeType<BlueprintCraftingRecipe> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return RESULT_X + SLOT + PAD;
    }

    @Override
    public int getHeight() {
        return PAD + GRID * SLOT + PAD;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BlueprintCraftingRecipe recipe,
            IFocusGroup focuses) {
        List<Ingredient> ingredients = recipe.ingredients();
        for (int i = 0; i < ingredients.size() && i < GRID * GRID; i++) {
            builder.addInputSlot(PAD + (i % GRID) * SLOT, PAD + (i / GRID) * SLOT)
                .addIngredients(ingredients.get(i));
        }
        int middle = PAD + SLOT;
        builder.addInputSlot(SHEET_X, middle)
            .addItemStack(BlueprintItem.of(RCItems.BLUEPRINT.get(), recipe.blueprint()));
        builder.addOutputSlot(RESULT_X, middle)
            .addItemStack(new ItemStack(recipe.result().item(), recipe.result().count()));
    }
}

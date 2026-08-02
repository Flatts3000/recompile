package com.flatts.recompile.compat.jei;

import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * A category for this mod's <b>special</b> crafting recipes (#95): fragments into a blueprint, and a
 * dyed mattress into a bed.
 *
 * <p><b>They need their own chapter or they are invisible.</b> Both are {@code CustomRecipe}s, so
 * {@code isSpecial()} hides them from JEI outright. The first fix put worked examples into vanilla's
 * Crafting category, which technically listed them and practically buried them: seventeen entries
 * scattered across six pages of every crafting recipe in the game, with nothing marking them as the
 * two steps the whole blueprint mechanic turns on.
 *
 * <p>Inputs on the left, result on the right - the layout of a recipe rather than of a loot table,
 * which is why this does not reuse {@link SalvageCategory}.
 */
public class AssemblyCategory implements IRecipeCategory<AssemblyRecipe> {

    private static final int SLOT = 18;
    private static final int PAD = 4;
    private static final int COLS = 4;
    private static final int GAP = 22;   // room for the arrow between inputs and result

    private final RecipeType<AssemblyRecipe> type;
    private final Component title;
    private final IDrawable icon;
    private final int rows;

    public AssemblyCategory(RecipeType<AssemblyRecipe> type, Component title, IDrawable icon,
            int maxInputs) {
        this.type = type;
        this.title = title;
        this.icon = icon;
        this.rows = Math.max(1, (Math.max(maxInputs, 1) + COLS - 1) / COLS);
    }

    @Override
    public IRecipeType<AssemblyRecipe> getRecipeType() {
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
        return PAD + COLS * SLOT + GAP + SLOT + PAD;
    }

    @Override
    public int getHeight() {
        return PAD + rows * SLOT + PAD;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AssemblyRecipe recipe, IFocusGroup focuses) {
        List<ItemStack> inputs = recipe.inputs();
        for (int i = 0; i < inputs.size(); i++) {
            builder.addInputSlot(PAD + (i % COLS) * SLOT, PAD + (i / COLS) * SLOT)
                .addItemStack(inputs.get(i));
        }
        builder.addOutputSlot(PAD + COLS * SLOT + GAP, (getHeight() - SLOT) / 2)
            .addItemStack(recipe.output());
    }
}

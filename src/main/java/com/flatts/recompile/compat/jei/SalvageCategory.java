package com.flatts.recompile.compat.jei;

import com.flatts.recompile.compat.SortingData;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;

/**
 * One reusable category for all three salvage actions - input on the left, its outputs in
 * a grid on the right, with the drop odds on each output's tooltip when they are not a
 * certainty. Three instances are registered (Sorting / Cutting / Prying); they share this
 * layout and differ only in {@link RecipeType}, title, icon, and {@code showChance}.
 */
public class SalvageCategory implements IRecipeCategory<SalvageRecipe> {

    private static final int SLOT = 18;
    private static final int PAD = 4;
    private static final int COLS = 6;
    private static final int GAP = 12; // between the input and the output grid

    private final RecipeType<SalvageRecipe> type;
    private final Component title;
    private final IDrawable icon;
    private final boolean showChance;

    public SalvageCategory(RecipeType<SalvageRecipe> type, Component title, IDrawable icon, boolean showChance) {
        this.type = type;
        this.title = title;
        this.icon = icon;
        this.showChance = showChance;
    }

    @Override
    public IRecipeType<SalvageRecipe> getRecipeType() {
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
        return PAD + SLOT + GAP + COLS * SLOT + PAD;
    }

    @Override
    public int getHeight() {
        return PAD + 2 * SLOT + PAD; // up to two rows of outputs (household has 9)
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SalvageRecipe recipe, IFocusGroup focuses) {
        int inputY = (getHeight() - SLOT) / 2;
        builder.addInputSlot(PAD, inputY).addItemStack(recipe.input());

        int startX = PAD + SLOT + GAP;
        List<SortingData.Weighted> outputs = recipe.outputs();
        for (int i = 0; i < outputs.size(); i++) {
            SortingData.Weighted out = outputs.get(i);
            int x = startX + (i % COLS) * SLOT;
            int y = PAD + (i / COLS) * SLOT;
            IRecipeSlotBuilder slot = builder.addOutputSlot(x, y).addItemStack(out.stack());
            if (showChance && out.chance() < 1.0f) {
                float chance = out.chance();
                slot.addRichTooltipCallback((view, tooltip) ->
                    tooltip.add(Component.translatable("jei.recompile.chance",
                        String.format("%.1f", chance * 100.0f))));
            }
        }
    }
}

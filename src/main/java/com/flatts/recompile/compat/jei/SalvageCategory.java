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

    /** Never shorter than this, so a one-output category is not a letterbox. */
    private static final int MIN_ROWS = 2;

    private final RecipeType<SalvageRecipe> type;
    private final Component title;
    private final IDrawable icon;
    private final boolean showChance;
    private final int rows;

    /**
     * @param maxOutputs the largest output count any recipe in this category will have. <b>Pass the real
     *     number, measured from the same data the recipes are built from.</b> The height used to be
     *     hardcoded at two rows with a comment noting the biggest table had nine outputs - true when it
     *     was written, and silently wrong the moment a table grew past twelve. The Hydroponics seedling
     *     lottery reached fourteen and its third row drew straight through the bottom of the panel.
     */
    public SalvageCategory(RecipeType<SalvageRecipe> type, Component title, IDrawable icon,
            boolean showChance, int maxOutputs) {
        this.type = type;
        this.title = title;
        this.icon = icon;
        this.showChance = showChance;
        this.rows = Math.max(MIN_ROWS, (Math.max(maxOutputs, 1) + COLS - 1) / COLS);
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
        return PAD + rows * SLOT + PAD;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SalvageRecipe recipe, IFocusGroup focuses) {
        int inputY = (getHeight() - SLOT) / 2;
        // addItemStacks, not addItemStack: a tag input has several accepted items and JEI cycles
        // them in place. A single-item recipe is a one-element list and draws exactly as before.
        builder.addInputSlot(PAD, inputY).addItemStacks(recipe.inputs());

        int startX = PAD + SLOT + GAP;
        List<SortingData.Weighted> outputs = recipe.outputs();
        for (int i = 0; i < outputs.size(); i++) {
            SortingData.Weighted out = outputs.get(i);
            int x = startX + (i % COLS) * SLOT;
            int y = PAD + (i / COLS) * SLOT;
            // addItemStacks for the same reason the input slot above uses it: a collapsed
            // entry (the 29 stamped ambers) is one slot that cycles its variants. An ordinary
            // output is a one-element list and draws exactly as before.
            IRecipeSlotBuilder slot = builder.addOutputSlot(x, y).addItemStacks(out.variants());
            if (showChance && out.chance() < 1.0f) {
                float chance = out.chance();
                slot.addRichTooltipCallback((view, tooltip) ->
                    tooltip.add(Component.translatable("jei.recompile.chance",
                        String.format("%.1f", chance * 100.0f))));
            }
        }
    }
}

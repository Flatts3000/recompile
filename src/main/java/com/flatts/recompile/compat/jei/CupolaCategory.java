package com.flatts.recompile.compat.jei;

import com.flatts.recompile.compat.CupolaData;
import com.flatts.recompile.registry.RCItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The Cupola Furnace: a blasting recipe, plus the slag it rakes off whether you asked for it or not
 * (#243).
 *
 * <p><b>Not a {@link SalvageCategory} instance</b>, and the reason is one number. That category draws
 * a chance column, and the slag is not a chance - it is one lump every N completed smelts, counted.
 * Rendering it as "12.5%" would describe a machine that can give you nothing for sixteen smelts
 * running, which is not this one. The note under the slag says "1 every N smelts" instead, with N read
 * from config so a retuned pack is not contradicted by its own viewer.
 *
 * <p><b>The recipes still appear under vanilla Blasting, but the Cupola is no longer listed there</b>
 * (owner, 2026-08-19). It shipped in both for a day and the duplication was the problem: each recipe
 * showed twice, once as "scrap to copper nugget" and once as "scrap to copper nugget and slag", with
 * the Cupola named in both. The second is a superset of the first, so the pair carried no extra
 * information. Nothing is hidden by the removal, because this category holds every recipe the machine
 * runs - a player looking up Scrap Metal still sees both machines, from two entries that now differ.
 */
public class CupolaCategory implements IRecipeCategory<CupolaData.Entry> {

    private static final int SLOT = 18;
    private static final int PAD = 4;
    private static final int ARROW = 24;   // room for the arrow between input and output

    private final RecipeType<CupolaData.Entry> type;
    private final Component title;
    private final IDrawable icon;

    public CupolaCategory(RecipeType<CupolaData.Entry> type, Component title, IDrawable icon) {
        this.type = type;
        this.title = title;
        this.icon = icon;
    }

    @Override
    public IRecipeType<CupolaData.Entry> getRecipeType() {
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
        return PAD + SLOT + ARROW + SLOT + ARROW + SLOT + PAD;
    }

    @Override
    public int getHeight() {
        return PAD + SLOT + PAD;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CupolaData.Entry recipe, IFocusGroup focuses) {
        int y = PAD;
        // addItemStacks, not addItemStack: an ingredient may be a tag, and JEI cycles the members in
        // place rather than the category picking one arbitrarily.
        builder.addInputSlot(PAD, y).addItemStacks(recipe.inputs());
        builder.addOutputSlot(PAD + SLOT + ARROW, y).addItemStack(recipe.output());

        int per = CupolaData.smeltsPerSlag();
        builder.addOutputSlot(PAD + SLOT + ARROW + SLOT + ARROW, y)
            .addItemStack(new ItemStack(RCItems.SLAG.get()))
            .addRichTooltipCallback((view, tooltip) -> {
                tooltip.add(Component.translatable("jei.recompile.cupola.slag_rate", per));
                tooltip.add(Component.translatable("jei.recompile.cupola.slag_any"));
            });
    }
}

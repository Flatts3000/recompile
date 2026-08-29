package com.flatts.recompile.compat.jei;

import com.flatts.recompile.content.item.BlueprintItem;
import com.flatts.recompile.content.recipe.SpawnEggCraftingRecipe;
import com.flatts.recompile.registry.RCItems;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * One spawn egg, drawn as the vessel and the sheet that names it (#294).
 *
 * <p><b>One JEI entry per species, from one recipe.</b> The mod ships a single
 * {@code spawn_egg_crafting} recipe whose result is computed from the sheet in the grid, which is right
 * for the game and useless as a JEI page: a player clicking a cow spawn egg is asking "how do I get
 * THIS", and a page showing a generic blueprint and a generic egg answers a question nobody asked. So a
 * {@link Entry} pairs the recipe with a species and the plugin registers one per creature the pull
 * streams can stamp.
 *
 * <p><b>The sheet is an INPUT here, unlike every other blueprint page.</b> {@code
 * BlueprintCraftingCategory} draws its sheet as a CRAFTING_STATION because it must only be within
 * reach; this one really does go in the grid, and drawing it anywhere else would teach the wrong thing
 * to the one recipe where it matters. It is not consumed, which the tooltip says rather than the
 * layout, because JEI has no role for "consumed by position but handed straight back".
 */
public class SpawnEggCategory implements IRecipeCategory<SpawnEggCategory.Entry> {

    /** One species' page: the shipped recipe, plus which creature this page is about. */
    public record Entry(SpawnEggCraftingRecipe recipe, Identifier species, ItemStack egg) {}

    private static final int SLOT = 18;
    private static final int PAD = 4;
    private static final int GRID = 3;
    private static final int GAP = 8;
    private static final int RESULT_X = PAD + GRID * SLOT + GAP + SLOT;

    private final RecipeType<Entry> type;
    private final Component title;
    private final IDrawable icon;

    public SpawnEggCategory(RecipeType<Entry> type, Component title, IDrawable icon) {
        this.type = type;
        this.title = title;
        this.icon = icon;
    }

    @Override
    public IRecipeType<Entry> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, Entry entry, IFocusGroup focuses) {
        var pattern = entry.recipe().pattern();
        List<Optional<Ingredient>> ingredients = pattern.ingredients();
        ItemStack sheet = BlueprintItem.of(RCItems.BLUEPRINT.get(),
            Identifier.fromNamespaceAndPath("recompile",
                BlueprintItem.SPAWN_EGG_PREFIX + entry.species().getNamespace() + "/"
                    + entry.species().getPath()));

        // Every cell gets a slot, empty ones included, so the slot index IS the grid position - the
        // same reason BlueprintCraftingCategory does it, and the same drift if it does not.
        for (int row = 0; row < GRID; row++) {
            for (int col = 0; col < GRID; col++) {
                var slot = builder.addInputSlot(PAD + col * SLOT, PAD + row * SLOT);
                if (row >= pattern.height() || col >= pattern.width()) {
                    continue;
                }
                Optional<Ingredient> ingredient = ingredients.get(row * pattern.width() + col);
                if (ingredient.isEmpty()) {
                    continue;
                }
                // The blueprint cell is drawn as THIS species' sheet rather than as the bare item the
                // pattern names, because a page that shows a blank blueprint is telling the player any
                // sheet will do, which is the one thing that is not true here.
                if (ingredient.get().test(new ItemStack(RCItems.BLUEPRINT.get()))) {
                    slot.addItemStack(sheet)
                        .addRichTooltipCallback((view, tip) ->
                            tip.add(Component.translatable("jei.recompile.spawn_egg.sheet_kept")));
                } else {
                    slot.addIngredients(ingredient.get());
                }
            }
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, RESULT_X, PAD + SLOT)
            .addItemStack(entry.egg());
    }
}

package com.flatts.recompile.compat.jei;

import com.flatts.recompile.compat.SortingData;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * One row in a salvage category: an input (a garbage block, a sealed can, a mattress,
 * Bulky Waste) and the weighted outputs it can give up. Shared by all three categories
 * (Sorting / Cutting / Prying) - they differ only in title, icon, and whether the odds
 * are worth showing (Cutting outputs are guaranteed).
 */
public record SalvageRecipe(ItemStack input, List<SortingData.Weighted> outputs) {
}

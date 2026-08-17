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
public record SalvageRecipe(List<ItemStack> inputs, List<SortingData.Weighted> outputs) {

    /**
     * A single accepted input, which is what six of the seven categories have.
     *
     * <p>The list exists for the seventh: an ingredient may be a TAG. The sherd recipe accepts all 23
     * pottery sherds, and JEI cycles a multi-item slot natively - so the row shows what the machine
     * really takes instead of one arbitrary member, or (as it did until this) nothing at all.
     */
    public SalvageRecipe(ItemStack input, List<SortingData.Weighted> outputs) {
        this(List.of(input), outputs);
    }

    /** The first accepted item, for callers that only need something to draw. */
    public ItemStack input() {
        return inputs.isEmpty() ? ItemStack.EMPTY : inputs.get(0);
    }
}

package com.flatts.recompile.compat.jei;

import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * One worked example of a special crafting recipe: some things in, one thing out.
 *
 * <p>Separate from {@link SalvageRecipe} because that one is a single input against weighted outputs -
 * the shape of a loot table - and this is the opposite: several definite inputs against one definite
 * result. Forcing them into one record would mean a chance field that is always 1 and an input list
 * that is always length 1, half of it lying in each direction.
 */
public record AssemblyRecipe(List<ItemStack> inputs, ItemStack output) {
}

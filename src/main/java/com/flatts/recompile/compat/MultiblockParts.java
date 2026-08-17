package com.flatts.recompile.compat;

import com.flatts.recompile.content.block.multiblock.Multiblock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * The blocks a multiblock turns INTO, which a player can never hold.
 *
 * <p><b>The rule (owner, 2026-08-03): a viewer must not list an uncraftable multiblock part.</b> A
 * formed cell exists only as the result of assembling a machine - the Separator Chamber, the Compost
 * Cage, the Tree Nursery Tank. It has no recipe and no loot route, so offering it in JEI is a dead end:
 * the player clicks it, JEI says nothing makes it, and the only thing they have learned is that the mod
 * has a block they cannot have.
 *
 * <p><b>Derived structurally, not listed.</b> A cell whose formed block differs from the component you
 * place is a transformation, and the formed half is unobtainable. A cell where the two are the SAME
 * block is a part you craft and place by hand - the Rain Collector Funnel and the Solar Panel both work
 * that way - and it stays visible, because it is real and craftable. Nothing here names a block, so a
 * new machine is covered the day it is written and a hand-maintained list cannot rot. That mistake has
 * been made in this package before: {@code TeardownData} named its recipes, a third shipped, and every
 * viewer denied it existed.
 */
public final class MultiblockParts {

    private MultiblockParts() {
    }

    /**
     * Every formed-only cell block across every multiblock in the game.
     *
     * <p>The derivation moved to {@link Multiblock#formedOnly()} so it sits beside its inverse,
     * {@link Multiblock#isHandPlaced}. They are one question asked from both ends, and keeping them
     * apart is how the game came to show these blocks' craftable siblings in JEI while deleting them
     * on break. This stays as the viewer-facing name because that is what the rule is written about.
     */
    public static Set<Block> formedOnly() {
        return Multiblock.formedOnly();
    }

    /** The same set as item stacks, skipping any formed block with no item form at all. */
    public static List<ItemStack> hiddenStacks() {
        List<ItemStack> out = new ArrayList<>();
        for (Block block : formedOnly()) {
            Item item = block.asItem();
            if (item != Items.AIR) {
                out.add(new ItemStack(item));
            }
        }
        return out;
    }
}

package com.flatts.recompile.compat;

import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
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

    /** Every formed-only cell block across every multiblock in the game. */
    public static Set<Block> formedOnly() {
        Set<Block> out = new LinkedHashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof MultiblockCoreBlock core)) {
                continue;
            }
            for (Multiblock.Cell cell : core.blueprint().cells()) {
                if (cell.formed() != cell.component()) {
                    out.add(cell.formed());
                }
            }
        }
        return out;
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

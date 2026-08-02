package com.flatts.recompile.content.recipe;

import com.flatts.recompile.content.block.ScrapNetwork;
import com.flatts.recompile.content.block.entity.FilingCabinetBlockEntity;
import com.flatts.recompile.content.item.BlueprintItem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Whether a blueprint is <b>reachable</b> from a crafting table (#95, spec
 * {@code docs/blueprints_spec.md}).
 *
 * <p>Two places count, and they are deliberately different in character:
 *
 * <ul>
 *   <li><b>The player's inventory.</b> Carrying the sheet is enough. A player who has just earned
 *       their first blueprint should be able to use it immediately, without first having found a
 *       cabinet - the cabinet is where a collection lives, not a licence to craft.</li>
 *   <li><b>A Filing Cabinet in the same scrap cluster.</b> Placement, not wiring: the Scrap Network is
 *       adjacency and has no core and no saved state, so a cabinet touching the table is read from it
 *       and one across the room is not.</li>
 * </ul>
 *
 * <p><b>The sheet is never consumed.</b> Knowledge does not wear out, and a blueprint that burned on
 * use would turn a one-off discovery into a resource to hoard.
 *
 * <p><b>This is checked at the table rather than in the recipe.</b> A {@code Recipe} only sees its own
 * input, so it cannot know what a player is carrying or what block is next door - which is why
 * {@link BlueprintCraftingRecipe#matches} tests the ingredients and nothing else. Splitting it here
 * keeps the recipe honest about what it can actually know.
 */
public final class BlueprintAccess {

    private BlueprintAccess() {
    }

    /**
     * Whether this player, crafting at this block, can reach the named blueprint.
     *
     * <p>Null-tolerant on both the player and the position, because the menu exists client-side and
     * during construction before either is meaningful. Unreachable is the safe answer: the worst case
     * is a recipe that briefly shows no result, never one that crafts something it should not.
     */
    public static boolean reachable(@Nullable Level level, @Nullable Player player,
            @Nullable BlockPos table, Identifier set) {
        return heldBy(player, set) || filedNear(level, table, set);
    }

    /** Whether the player is carrying the sheet, anywhere in their inventory. */
    public static boolean heldBy(@Nullable Player player, Identifier set) {
        if (player == null) {
            return false;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (set.equals(BlueprintItem.blueprintOf(stack))) {
                return true;
            }
        }
        return false;
    }

    /** Whether any Filing Cabinet in the table's scrap cluster has it filed. */
    public static boolean filedNear(@Nullable Level level, @Nullable BlockPos table, Identifier set) {
        if (level == null || table == null) {
            return false;
        }
        for (BlockPos pos : ScrapNetwork.collect(level, table)) {
            if (level.getBlockEntity(pos) instanceof FilingCabinetBlockEntity cabinet
                    && cabinet.holds(set)) {
                return true;
            }
        }
        return false;
    }
}

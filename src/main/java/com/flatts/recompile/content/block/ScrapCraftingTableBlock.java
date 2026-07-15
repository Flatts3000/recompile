package com.flatts.recompile.content.block;

import com.flatts.recompile.content.menu.ScrapCraftingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Scrap crafting table: a tier-zero 3x3 crafting station for the garbage world,
 * which has no wood for a vanilla crafting table. Craftable in the player's 2x2 grid
 * from scrap so it's the bootstrap that unlocks every 3x3 recipe (tools, tarp).
 * Full parity with the vanilla crafting table - it opens the vanilla crafting menu,
 * via {@link ScrapCraftingMenu}, which only re-points vanilla's hard-coded
 * {@code stillValid} block check at this block so the menu stays open.
 */
public class ScrapCraftingTableBlock extends Block {

    private static final Component TITLE = Component.translatable("container.crafting");

    public ScrapCraftingTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        player.openMenu(new SimpleMenuProvider(
            (id, inventory, opener) -> new ScrapCraftingMenu(id, inventory, ContainerLevelAccess.create(level, pos)),
            TITLE));
        player.awardStat(Stats.INTERACT_WITH_CRAFTING_TABLE);
        return InteractionResult.CONSUME;
    }
}

package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.ScrapCraftingTableBlockEntity;
import com.flatts.recompile.content.menu.ScrapCraftingStationMenu;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Scrap crafting table: a tier-zero 3x3 crafting station for the garbage world,
 * which has no wood for a vanilla crafting table. Craftable in the player's 2x2 grid
 * from scrap so it's the bootstrap that unlocks every 3x3 recipe (tools, tarp).
 * It opens {@link ScrapCraftingStationMenu} - vanilla 3x3 crafting revalidated against this block,
 * plus craft-from-storage: shift-clicking the result restocks the grid from the connected scrap
 * network, so a whole run crafts straight out of the bins (design P2.10 flow 4).
 *
 * <p>It carries a {@link ScrapCraftingTableBlockEntity} solely to keep an in-progress grid across
 * closing the screen (the Tinkers' Crafting Station QoL); the crafting itself is still menu-driven.
 */
public class ScrapCraftingTableBlock extends BaseEntityBlock {

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        // The shared workstation bench. Was a full cube, so standing on one put you three pixels
        // above the top face you can see.
        return WorkstationTable.SHAPE;
    }


    public static final MapCodec<ScrapCraftingTableBlock> CODEC = simpleCodec(ScrapCraftingTableBlock::new);

    private static final Component TITLE = Component.translatable("container.crafting");

    public ScrapCraftingTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<ScrapCraftingTableBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScrapCraftingTableBlockEntity(pos, state);
    }

    /** Render the normal block model - a BaseEntityBlock must say so, or it draws nothing. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        player.openMenu(new SimpleMenuProvider(
            (id, inventory, opener) -> new ScrapCraftingStationMenu(id, inventory, level, pos),
            TITLE), buffer -> buffer.writeBlockPos(pos));
        player.awardStat(Stats.INTERACT_WITH_CRAFTING_TABLE);
        return InteractionResult.CONSUME;
    }
}

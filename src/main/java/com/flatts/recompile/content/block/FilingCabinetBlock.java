package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.FilingCabinetBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Filing Cabinet (#95, spec {@code docs/blueprints_spec.md}): where a workshop keeps what it has
 * worked out how to build.
 *
 * <p><b>It is found, not crafted.</b> One more thing pried out of Bulky Waste, which is the same beat as
 * the mattress and the washing machine - the dump hands you the furniture. That also means it arrives on
 * the dump's schedule rather than a tech tree's, so a player can be filing blueprints long before they
 * could build a machine to do it.
 *
 * <p><b>It joins the Scrap Network by placement.</b> Carrying {@code #recompile:scrap_connectable} means
 * a cabinet touching the Scrap Crafting Table is readable from it, with no wiring, no core and no saved
 * state - the same rule every other scrap block already follows. The table asks the cluster whether the
 * blueprint is filed; the cabinet does not push anything anywhere.
 *
 * <p>Horizontal facing, because a cabinet has a front. Contents drop on break, because losing a
 * collection to a misplaced pickaxe would be the single most annoying thing in the mod.
 */
public class FilingCabinetBlock extends BaseEntityBlock {

    public static final MapCodec<FilingCabinetBlock> CODEC = simpleCodec(FilingCabinetBlock::new);
    /** 26.1 has no DirectionProperty; horizontal facing is an EnumProperty now. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public FilingCabinetBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FilingCabinetBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state,
            Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand,
            BlockHitResult hit) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof FilingCabinetBlockEntity cabinet) {
            player.openMenu(cabinet);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Spill the drawers when broken.
     *
     * <p>A blueprint is a lot of teardowns. Losing a cabinet's worth to one mistaken swing would be the
     * most expensive accident in the mod, so the contents come out rather than going with the block.
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
            boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof FilingCabinetBlockEntity cabinet) {
            Containers.dropContents(level, pos, cabinet);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}

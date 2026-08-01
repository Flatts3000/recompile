package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.TreeNurseryBlockEntity;
import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCBlocks;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import org.jspecify.annotations.Nullable;

/**
 * The Tree Nursery's core (reclamation rung 4, spec {@code docs/tree_nursery_spec.md}): the master of a
 * 2x2x1 wall - bottom row the core plus an inert Water Tank, top row two Solar Panels - that turns
 * water + Fertilizer + an Unknown Seedling into a sapling of the player's chosen species. The saplings
 * it makes are the only ones in the game (the loot strip keeps them un-findable, spec P2.4-R2b).
 *
 * <p>Reuses the multiblock framework (master + inert dummy cells, the placement preview, disband) and
 * the Grass Spreader's exact inert parts, so there is no new component art and no Machine Frame. Carries
 * a {@link TreeNurseryBlockEntity} for its contents - the sanctioned "a machine may keep a BE for its
 * own contents" line the Rain Collector's tank sits on.
 *
 * <p>Right-click with a water bucket to fill the tank (the Rain Collector's fill path); right-click with
 * anything else - or an empty hand - to open the GUI. The dummy cells redirect both to here, so any face
 * of the wall works.
 */
public class TreeNurseryCoreBlock extends MultiblockCoreBlock implements EntityBlock {

    public static final MapCodec<TreeNurseryCoreBlock> CODEC = simpleCodec(TreeNurseryCoreBlock::new);

    /** The wall is flat, so it has a front - the face the player placed it looking at. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Running: a sapling is cooking. Swaps the front to the lit grow-light texture and emits light. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public TreeNurseryCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
            .setValue(FORMED, false)
            .setValue(FACING, Direction.NORTH)
            .setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends TreeNurseryCoreBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);   // FORMED
        builder.add(FACING, ACTIVE);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** The blueprint is authored for {@code FACING=NORTH}; rotate it to the placed facing. */
    @Override
    public Rotation rotationFor(BlockState state) {
        return switch (state.getValue(FACING)) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;   // NORTH (and any non-horizontal, which cannot occur)
        };
    }

    /** Preview footprint: the facing this core would take is the same one {@link #getStateForPlacement} sets. */
    @Override
    public Rotation placementRotation(Player player) {
        return rotationFor(defaultBlockState().setValue(FACING, player.getDirection().getOpposite()));
    }

    @Override
    protected Multiblock createBlueprint() {
        // A 2x2x1 wall: core at the origin, the inert Water Tank beside it, two Solar Panels on top.
        // Water Tank and Solar Panel are reused unchanged (component == formed), so the cells never
        // transform - the shared inert parts the Grass Spreader already uses.
        return new Multiblock(List.of(
            new Multiblock.Cell(new Vec3i(1, 0, 0), RCBlocks.WATER_TANK.get(), RCBlocks.TREE_NURSERY_TANK.get()),
            new Multiblock.Cell(new Vec3i(0, 1, 0), RCBlocks.SOLAR_PANEL.get(), RCBlocks.SOLAR_PANEL.get()),
            new Multiblock.Cell(new Vec3i(1, 1, 0), RCBlocks.SOLAR_PANEL.get(), RCBlocks.SOLAR_PANEL.get())
        ));
    }

    // ---------------- the nursery BlockEntity ----------------

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TreeNurseryBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || !isFormed(state) || type != RCBlockEntities.TREE_NURSERY.get()) {
            return null;   // an unformed wall does not produce; the client does not tick it
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<TreeNurseryBlockEntity>)
            TreeNurseryBlockEntity::serverTick;
    }

    // ---------------- interaction: fill water, open GUI ----------------

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!isFormed(state) || !(level.getBlockEntity(pos) instanceof TreeNurseryBlockEntity be)) {
            return InteractionResult.PASS;   // unformed: not a machine yet
        }
        // A bucket fills the tank - the same server-authoritative path the Rain Collector uses.
        if (stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET)) {
            if (!level.isClientSide()) {
                return FluidUtil.interactWithFluidHandler(player, hand, pos, be.fluidHandler())
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            return InteractionResult.SUCCESS;   // client optimistically swings the arm
        }
        // Anything else opens the GUI.
        return openMenu(level, pos, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!isFormed(state)) {
            return InteractionResult.PASS;
        }
        return openMenu(level, pos, player);
    }

    private static InteractionResult openMenu(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TreeNurseryBlockEntity be) {
            player.openMenu(be, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }
}

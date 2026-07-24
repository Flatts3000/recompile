package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Scrap Bin (design P2.9): bulk storage for one salvage type, with a screen-free UX.
 *
 * <p>Right-click with matching salvage to deposit (sneak to dump every matching stack from your
 * inventory); right-click empty-handed to withdraw a stack (sneak for one). No menu - the interaction
 * is entirely in the world, the Sorting Tarp's stateless philosophy applied to storage.
 *
 * <p>Two blockstates carry the look: {@link #CONTENT} is what the bin is bound to (a block color
 * handler tints the body by it - {@code tintindex}, not a renderer), and {@link #FILL} is how full it
 * is (a composter-style level). The count itself lives on the {@link ScrapBinBlockEntity}; Jade reads
 * it for the exact number.
 */
public class ScrapBinBlock extends BaseEntityBlock {

    public static final MapCodec<ScrapBinBlock> CODEC = simpleCodec(ScrapBinBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<ScrapBinContent> CONTENT =
        EnumProperty.create("content", ScrapBinContent.class);
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 4);

    public ScrapBinBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(CONTENT, ScrapBinContent.EMPTY)
            .setValue(FILL, 0));
    }

    @Override
    public MapCodec<ScrapBinBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONTENT, FILL);
    }

    /** Front (the chute face) toward the player who placed it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new ScrapBinBlockEntity(worldPosition, blockState);
    }

    /** A restored (component-carrying) bin must show its content and fill the instant it is placed. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof ScrapBinBlockEntity bin) {
            bin.refreshStateAfterPlacement();
        }
    }

    /** Deposit. A matching item binds an empty bin; sneak dumps every matching stack you carry. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ScrapBinBlockEntity bin)) {
            return InteractionResult.PASS;
        }
        if (!bin.accepts(stack)) {
            // Not binnable, or the bin is bound to something else - let the held item do its own thing.
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            depositEveryMatchingStack(bin, player);
        } else {
            bin.deposit(stack);
        }
        return InteractionResult.SUCCESS;
    }

    /** Withdraw. Empty-handed pulls a stack; sneak pulls a single item. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ScrapBinBlockEntity bin)) {
            return InteractionResult.PASS;
        }
        if (bin.isEmpty()) {
            return InteractionResult.CONSUME;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ItemStack out = bin.withdraw(player.isSecondaryUseActive());
        if (!out.isEmpty()) {
            player.getInventory().placeItemBackInInventory(out);
        }
        return InteractionResult.SUCCESS;
    }

    /** Sweep the player's inventory, depositing every stack the bin will take until it is full. */
    private static void depositEveryMatchingStack(ScrapBinBlockEntity bin, Player player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (bin.accepts(stack)) {
                bin.deposit(stack);
            }
        }
    }
}

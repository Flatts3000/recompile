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

    /**
     * Deposit (Functional Storage's insert): a right-click puts the held stack in and binds an empty
     * bin; a quick <b>double</b> right-click dumps every matching stack from the inventory. Extraction
     * is left-click - see {@link #extract} - so right-click never takes anything out.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ScrapBinBlockEntity bin)) {
            return InteractionResult.PASS;
        }
        boolean matches = bin.accepts(stack);
        // Meaningful only if the held item matches, or the bin is already bound - the latter catches
        // the second click of a double after the first click emptied the hand (the bulk-dump case).
        // Otherwise the held item just does its own thing.
        if (!matches && bin.boundMaterial() == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (bin.rightClickIsDouble(player.getUUID(), level.getGameTime())) {
            depositEveryMatchingStack(bin, player);
        } else if (matches) {
            bin.deposit(stack);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Left-click extraction (Functional Storage's take-out), driven by the {@code LeftClickBlock}
     * event: a plain click pulls a single item, sneak pulls a full stack. A static entry point so the
     * event handler and the GameTests share one path. Returns true if it took anything - the event
     * uses that to know a full bin should extract rather than start breaking on a tap.
     */
    public static boolean extract(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof ScrapBinBlockEntity bin) || bin.isEmpty()) {
            return false;
        }
        if (!level.isClientSide()) {
            // Plain click = one item; sneak = a stack (mirrors FS, which is the reverse of deposit).
            ItemStack out = bin.withdraw(!player.isSecondaryUseActive());
            if (!out.isEmpty()) {
                player.getInventory().placeItemBackInInventory(out);
            }
        }
        return true;
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

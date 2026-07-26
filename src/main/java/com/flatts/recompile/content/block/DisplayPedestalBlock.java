package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.DisplayPedestalBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Display Pedestal (Collectibles, design I-2): a ProjectE-style stand that shows off any one item -
 * a finished collectible trophy is the star use, but it holds anything (the WALL-E curio shelf).
 * Right-click with an item on an empty pedestal to set it out; right-click a filled pedestal empty-handed
 * to take it back. No GUI - the interaction is the whole interface, in keeping with the no-machine-screen
 * line.
 *
 * <p>The trophy is drawn live by a {@link com.flatts.recompile.client.DisplayPedestalRenderer} - the
 * one place the mod runs a BlockEntityRenderer (a scoped, recorded reversal of P1.11.6; see the block
 * entity). A finished collectible is an arbitrary item, so its display cannot be baked into the
 * pedestal's model the way the workbench bakes its racked tools.
 */
public class DisplayPedestalBlock extends BaseEntityBlock {

    public static final MapCodec<DisplayPedestalBlock> CODEC = simpleCodec(DisplayPedestalBlock::new);

    // A tiered plinth (ProjectE-pedestal shape): stepped base, slim column, stepped cap plate. The
    // trophy floats above the cap, drawn by the BER.
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(2, 0, 2, 14, 4, 14),
        Block.box(5, 4, 5, 11, 11, 11),
        Block.box(2, 11, 2, 14, 14, 14));

    public DisplayPedestalBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<DisplayPedestalBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayPedestalBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;   // the plinth is a baked model; only the trophy on top is a BER
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
            BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        // Any item in hand, on an empty stand, gets set out (ProjectE-pedestal style - a general
        // display, collectibles being the star use). An empty hand falls through to the take-back path.
        if (level.getBlockEntity(pos) instanceof DisplayPedestalBlockEntity pedestal
                && pedestal.isEmpty() && !stack.isEmpty()) {
            if (!level.isClientSide()) {
                pedestal.setDisplayed(stack.copyWithCount(1));
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof DisplayPedestalBlockEntity pedestal && !pedestal.isEmpty()) {
            if (!level.isClientSide()) {
                ItemStack out = pedestal.removeDisplayed();
                if (!player.getInventory().add(out)) {
                    player.drop(out, false);
                }
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    // The displayed trophy drops from DisplayPedestalBlockEntity.preRemoveSideEffects on every removal
    // cause - no playerWillDestroy override needed, and the pedestal block itself drops via its loot table.
}

package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.RecompileWorkbenchBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Recompile Workbench (design P1.4): a hand-cranked disassembly table - the teardown
 * exit the whole found economy was waiting on (the P1.11.5 invariant, "finds in, materials
 * out"). Built <b>materials-only</b>: it reads a {@code recompile:teardown} recipe's
 * {@code results}/{@code extras} and ignores {@code teaches} entirely. No knowledge, no
 * gate; the knowledge/function axis is a later, separate decision.
 *
 * <p>Stateless-feeling and GUI-free, in keeping with the mod's "no machine screen" identity:
 * <ul>
 *   <li>Right-click with a scrap knife or prybar in hand to <b>rack</b> it - it appears
 *       resting on the table (a {@code has_knife}/{@code has_prybar} blockstate boolean drives
 *       a baked multipart model; no BlockEntityRenderer, per P1.11.6).
 *   <li><b>Sneak</b> + right-click with an empty hand to pull a racked tool back.
 *   <li><b>Hold</b> right-click with a found item in hand to run the breakdown - it takes the
 *       recipe's {@code ticks} to complete (default 80), then the materials fly into the world
 *       and the required racked tool loses one durability.
 * </ul>
 *
 * <p>It ticks only while a player is actively holding it (the interaction refires ~every 4
 * ticks) and exposes no item-handler capability, so it is never hopper-automated - a powered
 * disassembler stays a tier-3+ upgrade.
 */
public class RecompileWorkbenchBlock extends BaseEntityBlock {

    public static final MapCodec<RecompileWorkbenchBlock> CODEC = simpleCodec(RecompileWorkbenchBlock::new);

    public static final BooleanProperty HAS_KNIFE = BooleanProperty.create("has_knife");
    public static final BooleanProperty HAS_PRYBAR = BooleanProperty.create("has_prybar");

    public RecompileWorkbenchBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(HAS_KNIFE, false)
            .setValue(HAS_PRYBAR, false));
    }

    @Override
    public MapCodec<RecompileWorkbenchBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HAS_KNIFE, HAS_PRYBAR);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new RecompileWorkbenchBlockEntity(worldPosition, blockState);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        // A tool in hand racks it (item identity is safe on both sides).
        if (RecompileWorkbenchBlockEntity.isRackTool(stack)) {
            if (level instanceof ServerLevel serverLevel
                    && level.getBlockEntity(pos) instanceof RecompileWorkbenchBlockEntity workbench) {
                workbench.rackTool(serverLevel, player, stack);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        // Teardown lookup is server-only in 26.1; the client is optimistic so holding refires.
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof RecompileWorkbenchBlockEntity workbench
                && workbench.advanceBreakdown(serverLevel, player, stack)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND; // no salvage value
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        // Sneak + empty hand pulls a racked tool back off the table. The empty-hand check matters:
        // this also runs when useItemOn returns TRY_WITH_EMPTY_HAND for a held non-input item, and
        // without it a sneak-click while holding junk would yank a tool off unexpectedly.
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            if (level instanceof ServerLevel serverLevel
                    && level.getBlockEntity(pos) instanceof RecompileWorkbenchBlockEntity workbench) {
                workbench.unrackOne(serverLevel, player);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    // Racked tools drop from RecompileWorkbenchBlockEntity.preRemoveSideEffects, which fires on
    // every removal cause (player break, explosion, piston, /setblock) - no playerWillDestroy needed.
}

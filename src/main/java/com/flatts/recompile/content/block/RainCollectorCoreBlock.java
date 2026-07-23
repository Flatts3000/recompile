package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.RainCollectorBlockEntity;
import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import org.jspecify.annotations.Nullable;

/**
 * The Rain Collector (design P1.10, rebuilt as a multiblock): a caged IBC tote that holds the tank,
 * completed by a Machine Frame on top which becomes the tarp funnel that actually catches the rain.
 *
 * <p>This is the core - the master of the two-cell machine. It carries everything the old two-half
 * block did (the tank {@link RainCollectorBlockEntity}, the fluid capability, the glass-bottle draw,
 * and the water that survives break + replace), so nothing about P1.10 regresses; only the structure
 * around it changed.
 *
 * <p><b>Collection is gated on being formed.</b> An unformed core is a tote with nothing over it, so
 * it catches nothing - the funnel is visibly the part that does the work, and the unformed state
 * means something rather than merely looking unfinished.
 *
 * <p><b>No water existed in this world at all</b> (sea level -64, {@code default_fluid: air}); only
 * {@code has_precipitation} is true. The collector is still the one source.
 */
public class RainCollectorCoreBlock extends MultiblockCoreBlock implements EntityBlock {

    public static final MapCodec<RainCollectorCoreBlock> CODEC = simpleCodec(RainCollectorCoreBlock::new);

    /** Near-full width so it reads as a tote in its cage, not a post. */
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 16, 16);

    public RainCollectorCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends RainCollectorCoreBlock> codec() {
        return CODEC;
    }

    @Override
    public Multiblock blueprint() {
        return Multiblock.stackedOn(RCBlocks.MACHINE_FRAME.get(), RCBlocks.RAIN_COLLECTOR_FUNNEL.get());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ---------------- the tank ----------------

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RainCollectorBlockEntity(pos, state);
    }

    /** Server-side only, and only while assembled - no funnel, no catch. */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || !isFormed(state) || type != RCBlockEntities.RAIN_COLLECTOR.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<RainCollectorBlockEntity>)
            RainCollectorBlockEntity::serverTick;
    }

    // ---------------- interaction: glass bottle + any fluid container ----------------

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RainCollectorBlockEntity be)) {
            return InteractionResult.PASS;
        }
        // Glass bottle -> water bottle (the specific case, handled before generic containers).
        if (stack.is(Items.GLASS_BOTTLE)) {
            if (!be.canFillBottle()) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide()) {
                be.drainBottle();
                stack.shrink(1);
                ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
                if (!player.getInventory().add(water)) {
                    player.drop(water, false);
                }
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        // Buckets and any other fluid container: the standard tank interaction. The server does the
        // real transfer; the client optimistically succeeds for vanilla buckets so the arm swings.
        if (!level.isClientSide()) {
            return FluidUtil.interactWithFluidHandler(player, hand, pos, be.fluidHandler())
                ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET)
            ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}

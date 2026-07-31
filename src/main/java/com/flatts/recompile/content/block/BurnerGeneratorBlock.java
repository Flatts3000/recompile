package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.BurnerGeneratorBlockEntity;
import com.flatts.recompile.registry.RCBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Burner Generator (#72): the half of the power tier that works at night.
 *
 * <p><b>No screen, no inventory, no menu.</b> Right-click it holding fuel and it takes one, exactly as the
 * Cutting Torch takes rags. That is the mod's standing rule - machine GUIs are the exception, not the
 * default - and it is why this block ships without touching {@code RCMenus} at all.
 *
 * <p>Automation feeds it through the item capability instead, which is the door pipes and hoppers use and
 * needs no screen to exist. Its row in {@code docs/automation_policy_spec.md} is "fuel in, energy out,
 * nothing else".
 *
 * <p>{@link BlockStateProperties#LIT} drives the texture, so a running generator is readable from across a
 * base without a gauge - the same trick the Burn Barrel uses.
 */
public class BurnerGeneratorBlock extends Block implements EntityBlock {

    public static final MapCodec<BurnerGeneratorBlock> CODEC = simpleCodec(BurnerGeneratorBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public BurnerGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BurnerGeneratorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BurnerGeneratorBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || type != RCBlockEntities.BURNER_GENERATOR.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<BurnerGeneratorBlockEntity>)
            BurnerGeneratorBlockEntity::serverTick;
    }

    /**
     * Feed it by hand.
     *
     * <p>Both refusals are messaged rather than silent. A generator that eats a rag and shows nothing is
     * indistinguishable from one that is broken, and "it is already burning" is the case a player will
     * otherwise hit repeatedly while wondering why their fuel is vanishing.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BurnerGeneratorBlockEntity generator)) {
            return InteractionResult.PASS;
        }
        if (level.fuelValues().burnDuration(stack) <= 0) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.recompile.burner_needs_fuel"));
            }
            return InteractionResult.PASS;
        }
        if (generator.isLit()) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.recompile.burner_still_burning"));
            }
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && generator.addFuel(level, stack)) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6F, 1.4F);
        }
        return InteractionResult.SUCCESS;
    }
}

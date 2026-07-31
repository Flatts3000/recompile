package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.BurnerGeneratorBlockEntity;
import com.flatts.recompile.registry.RCBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
 * <p><b>It has a fuel buffer and a screen</b> (owner call, 2026-07-31): if the Burn Barrel gets a UI for
 * holding fuel, so does this. It borrows vanilla's hopper screen - five slots is exactly a fuel buffer -
 * so the mod's "reuse a vanilla screen, never mint one" rule holds and {@code RCMenus} is untouched.
 *
 * <p>Its row in {@code docs/automation_policy_spec.md} is "fuel in, nothing out": every face takes fuel,
 * no face gives it back, since a pipe pulling fuel out of the generator it just filled is nobody's
 * intent.
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
     * Right-click opens the fuel buffer.
     *
     * <p>It reuses vanilla's hopper screen rather than minting one: five slots IS a fuel buffer, and the
     * mod's rule is that a bespoke machine screen is a design reversal. The energy level is not on this
     * screen because no vanilla screen has a bar for it - Jade carries that instead.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof BurnerGeneratorBlockEntity generator) {
            player.openMenu(generator);
        }
        return InteractionResult.SUCCESS;
    }
}

package com.flatts.recompile.content.block;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.registry.RCItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Appliance (design P1.1): deterministic guts and the ideal teardown input - the
 * on-ramp to the knowledge system (P1.4). Pry it open with a prybar to pop the
 * appliance item, which you carry to the Recompile Workbench to tear down (that
 * bench arrives in Phase 3; until then the item is simply collected). Mining also
 * drops the appliance (self-drop loot table), so it can be relocated whole.
 */
public class ApplianceBlock extends FallingBlock {

    public static final MapCodec<ApplianceBlock> CODEC = simpleCodec(ApplianceBlock::new);

    public ApplianceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends ApplianceBlock> codec() {
        return CODEC;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (RCConfig.GARBAGE_GRAVITY_ENABLED.get()) {
            super.tick(state, level, pos, random);
        }
    }

    @Override
    public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getMapColor(level, pos).col;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(RCItems.PRYBAR.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel serverLevel) {
            SoundType sound = state.getSoundType();
            Block.popResource(serverLevel, pos, new ItemStack(RCItems.APPLIANCE.get()));
            serverLevel.destroyBlock(pos, false); // pried open, not mined - suppress the self-drop
            serverLevel.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, 0.9F, 0.8F);
            stack.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
        return InteractionResult.SUCCESS;
    }
}

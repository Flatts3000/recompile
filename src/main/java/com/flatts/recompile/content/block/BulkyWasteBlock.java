package com.flatts.recompile.content.block;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.registry.RCItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * Bulky Waste (design P1.11): something big is buried here. Pry it open to find out what.
 *
 * <p>The real term - municipalities run "bulky waste collection" for the stuff too big
 * for the bag, which is exactly what this holds. It replaces the appliance, which was a
 * placeholder for a fridge or a washer rather than any actual object.
 *
 * <p><b>It is a tell, and that is the whole point.</b> A Block of Garbage is an opaque
 * cube that reveals nothing until a random number decides for you; this one you can
 * <em>see</em> and choose to dig for. See {@code ../trashlands/docs/concept.md} ("Why
 * sifting garbage is fun") - the player has to be the filter, not the RNG. Its texture
 * must never hint at the contents or it spoils its own reveal.
 *
 * <p><b>You pry it open with a right-click, mirroring the compacted bale.</b> Right-click
 * holding a {@link RCItems#PRYBAR} and it pops open, dropping its find; right-click
 * without one and it just nudges you toward the prybar. The prybar is the <em>only</em>
 * way in: the block is {@code requiresCorrectToolForDrops}, so knocking it apart by hand
 * yields nothing, the same way a bale gives up nothing to bare hands. That keeps the
 * "you need a Prybar" message honest rather than a suggestion you can ignore.
 *
 * <p>There are still no per-find models, entities, or structure templates: worldgen
 * places only <em>this</em> block, and a find becomes a specific object (a mattress) only
 * as an item in your hand. Adding a find later is a line in
 * {@code loot_table/blocks/bulky_waste.json}, not code.
 *
 * <p>It obeys gravity like the rest of the garbage (P0.3), so mounds still slump when
 * quarried around it.
 */
public class BulkyWasteBlock extends FallingBlock {

    public static final MapCodec<BulkyWasteBlock> CODEC = simpleCodec(BulkyWasteBlock::new);

    public BulkyWasteBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BulkyWasteBlock> codec() {
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

    // ---------------- pry open (mirrors CompactedBaleBlock's tool gate) ----------------

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(RCItems.PRYBAR.get())) {
            if (level instanceof ServerLevel serverLevel) {
                pryOpen(serverLevel, pos);
            }
            return InteractionResult.SUCCESS;
        }
        // Wrong item in hand: fall through to the empty-hand path so it still nudges.
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        // Needs the prybar: nudge the player, don't consume the block. Same message the
        // bale uses when you reach for it without a knife.
        if (!level.isClientSide()) {
            player.sendSystemMessage(
                Component.translatable("message.recompile.needs_tool",
                    Component.translatable(RCItems.PRYBAR.get().getDescriptionId())));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Pry the block apart: drop its find and break it. The single entry point interactions
     * and gametests share. {@code destroyBlock(pos, true)} rolls the block loot table with
     * no tool context, so the find drops even though {@code requiresCorrectToolForDrops}
     * would gate a bare-hand <em>mine</em> - the prybar check already happened at the
     * interaction.
     */
    public static void pryOpen(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BulkyWasteBlock)) {
            return;
        }
        SoundType sound = state.getSoundType();
        level.destroyBlock(pos, true);
        level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, 0.9F, 0.8F);
    }
}

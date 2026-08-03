package com.flatts.recompile.content.block;

import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCTags;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Sorting Tarp (design P1.3, revised 2026-07-14): a jury-rigged manual sorting
 * table. No GUI, no inventory. Right-click it holding a garbage block / bag / bale to
 * sift one batch - it rolls the region pull table and drops the sorted materials into
 * the world above the table (bales batch more). Hold right-click to keep sifting.
 *
 * <p>Stateless by identity: there is no input slot and no output buffer, so the sorting
 * <i>action</i> is the manual gate - hopper-proof by construction (collecting the drops
 * is the player's logistics problem). No BlockEntity.
 */
public class SortingTarpBlock extends Block {

    // Gate holding right-click so a held stack sifts at a steady cadence instead of
    // flooding the world with item entities (a bale is 12 rolls per sift).
    private static final int SIFT_COOLDOWN_TICKS = 8;

    // A waist-height tarp-draped table: a draped mass from y3 up, on four leg feet.
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(1, 3, 1, 15, 13, 15),    // tarp-draped table mass
        Block.box(2, 0, 2, 5, 3, 5),       // leg feet
        Block.box(11, 0, 2, 14, 3, 5),
        Block.box(2, 0, 11, 5, 3, 14),
        Block.box(11, 0, 11, 14, 3, 14));

    public SortingTarpBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        // Shift-right-click a networked tarp files your whole scrap haul into the connected bins (P2.10).
        if (player.isSecondaryUseActive()) {
            return fileAllIntoNetwork(level, pos, player);
        }
        if (SortableBlock.sortRolls(stack.getItem()) <= 0) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        // Holding right-click auto-repeats; the cooldown paces it (both sides track it,
        // set before shrink so it keys on the input item, not an emptied stack).
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.SUCCESS;
        }
        player.getCooldowns().addCooldown(stack, SIFT_COOLDOWN_TICKS);
        if (level instanceof ServerLevel serverLevel) {
            sift(serverLevel, pos, stack.getItem());
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Roll the input's pull table {@code rolls} times and drop the results onto the table. */
    private void sift(ServerLevel level, BlockPos pos, Item input) {
        int rolls = SortableBlock.sortRolls(input);
        LootTable table = level.getServer().reloadableRegistries()
            .getLootTable(SortableBlock.pullTableFor(input));
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .create(LootContextParamSets.CHEST);
        for (int i = 0; i < rolls; i++) {
            List<ItemStack> pulled = table.getRandomItems(params);
            for (ItemStack drop : pulled) {
                if (drop.isEmpty()) {
                    continue;
                }
                // Wired to a scrap network, the sorted materials flow into the connected bins/barrel;
                // standalone (or when storage is full) they drop on the table, the tarp's usual behavior.
                ItemStack remainder = ScrapNetwork.insertFromMember(level, pos, drop, false);
                if (!remainder.isEmpty()) {
                    Block.popResource(level, pos.above(), remainder);
                }
            }
        }
        SoundType sound = level.getBlockState(pos).getSoundType();
        level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, 0.6F, 0.9F);
    }

    /**
     * File-all (P2.10): every {@code #binnable} stack in the player's inventory into the connected
     * bins, auto-binding empties, overflow to the barrel. Only when the tarp is wired to a network
     * with storage; otherwise a shift-right-click does nothing here (the held item, if any, does its
     * own thing).
     */
    private static InteractionResult fileAllIntoNetwork(Level level, BlockPos pos, Player player) {
        if (!ScrapNetwork.reachesStorage(level, pos)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            int filed = 0;
            var inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && stack.is(RCTags.BINNABLE)) {
                    int before = stack.getCount();
                    ScrapNetwork.insertFromMember(serverLevel, pos, stack, true);
                    filed += before - stack.getCount();
                }
            }
            if (filed > 0) {
                SoundType sound = level.getBlockState(pos).getSoundType();
                level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, 0.7F, 1.1F);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Empty-handed shift-right-click also files (the common case: you have no scrap in hand). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive()) {
            return fileAllIntoNetwork(level, pos, player);
        }
        return InteractionResult.PASS;
    }

    /** Single entry point for interactions and gametests: sift one {@code input} at the tarp. */
    public static void siftInput(ServerLevel level, BlockPos pos, Item input) {
        if (level.getBlockState(pos).getBlock() instanceof SortingTarpBlock tarp
                && SortableBlock.sortRolls(input) > 0) {
            tarp.sift(level, pos, input);
        }
    }

}

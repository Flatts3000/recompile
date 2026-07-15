package com.flatts.recompile.content.block;

import com.flatts.recompile.registry.RCItems;
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
        if (outputRolls(stack.getItem()) <= 0) {
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
        int rolls = outputRolls(input);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(pullTableFor(input));
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .create(LootContextParamSets.CHEST);
        for (int i = 0; i < rolls; i++) {
            List<ItemStack> pulled = table.getRandomItems(params);
            for (ItemStack drop : pulled) {
                if (!drop.isEmpty()) {
                    Block.popResource(level, pos.above(), drop);
                }
            }
        }
        SoundType sound = level.getBlockState(pos).getSoundType();
        level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, 0.6F, 0.9F);
    }

    /** Single entry point for interactions and gametests: sift one {@code input} at the tarp. */
    public static void siftInput(ServerLevel level, BlockPos pos, Item input) {
        if (level.getBlockState(pos).getBlock() instanceof SortingTarpBlock tarp && outputRolls(input) > 0) {
            tarp.sift(level, pos, input);
        }
    }

    /** How many material rolls one of this input yields (0 = not a valid sorting input). */
    private static int outputRolls(Item item) {
        if (item == RCItems.GARBAGE_BLOCK.get().asItem()) {
            return 5;
        }
        if (item == RCItems.TRASH_BAG.get().asItem()) {
            return 2;
        }
        if (item == RCItems.COMPACTED_BALE.get().asItem()) {
            return 12;
        }
        return 0;
    }

    private static ResourceKey<LootTable> pullTableFor(Item item) {
        if (item == RCItems.TRASH_BAG.get().asItem()) {
            return TrashBagBlock.BAG_PULLS;
        }
        return GarbageBlock.HOUSEHOLD_PULLS; // garbage block + dense bale
    }
}

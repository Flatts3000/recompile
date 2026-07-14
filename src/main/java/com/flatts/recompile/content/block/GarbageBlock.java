package com.flatts.recompile.content.block;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.GarbageBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The household Block of Garbage (design P0.3 + P0.4). Carry it, stack it, pick
 * through it: right-click with an empty hand to pull one drop from the region pull
 * table; after 4-6 pulls the block crumbles away. The same "sort verb" reappears
 * at higher speeds later (Sorting Tarp, machines).
 *
 * <p>Backed by a {@link GarbageBlockEntity} for pull progress. Gravity (P0.3) is
 * deferred to the mounds increment - it is not needed for the pick-through slice.
 */
public class GarbageBlock extends Block implements EntityBlock {

    /** The household region's pull table (design: region-weighted pulls, JSON per region). */
    public static final ResourceKey<LootTable> HOUSEHOLD_PULLS = ResourceKey.create(
        Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "gameplay/household_pulls"));

    public GarbageBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GarbageBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel) {
            sortOnce(serverLevel, pos);
        }
        // SUCCESS on both sides: swing the arm client-side, do the work server-side.
        return InteractionResult.SUCCESS;
    }

    /**
     * Pull once from this garbage block: roll the household pull table, drop the
     * results, advance sort progress, and crumble the block if it is spent. Returns
     * true if this pull crumbled the block. Server-side; the block interaction and
     * the gametest both route through here so the mechanic has one implementation.
     */
    public static boolean sortOnce(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof GarbageBlockEntity be)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        SoundType sound = state.getSoundType();

        LootTable table = level.getServer().reloadableRegistries().getLootTable(HOUSEHOLD_PULLS);
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .create(LootContextParamSets.CHEST);
        List<ItemStack> pulled = table.getRandomItems(params);
        for (ItemStack stack : pulled) {
            if (!stack.isEmpty()) {
                Block.popResource(level, pos, stack);
            }
        }
        level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, 0.6F, 0.9F);

        boolean crumbled = be.recordPullAndCheckCrumble(level.getRandom());
        if (crumbled) {
            level.destroyBlock(pos, false); // spent: remove without dropping itself
            level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, 0.8F, 0.9F);
        }
        return crumbled;
    }
}

package com.flatts.recompile.content.block;

import com.flatts.recompile.Recompile;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The household Block of Garbage (design P0.3 + P0.4). Carry it, stack it, pick
 * through it: right-click with an empty hand to pull one drop from the region pull
 * table; after 4-6 pulls the block crumbles away.
 *
 * <p>Sort progress lives in the {@link #SORTED} blockstate, not a BlockEntity - the
 * garbage block is the mod's bulk block (mounds are made of thousands), so a
 * per-instance BlockEntity would be a large memory/chunk-save cost. Blockstates are
 * shared flyweights already stored in the section palette, so tracking progress this
 * way is effectively free. Gravity (P0.3) is deferred to a later pass.
 */
public class GarbageBlock extends Block {

    private static final int MIN_PULLS = 4;
    private static final int MAX_PULLS = 6;

    /** Pulls taken from this block so far. Reaches at most MAX_PULLS-1 before crumbling. */
    public static final IntegerProperty SORTED = IntegerProperty.create("sorted", 0, MAX_PULLS - 1);

    /** The household region's pull table (design: region-weighted pulls, JSON per region). */
    public static final ResourceKey<LootTable> HOUSEHOLD_PULLS = ResourceKey.create(
        Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "gameplay/household_pulls"));

    public GarbageBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(SORTED, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SORTED);
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
     * results, advance sort progress in the blockstate, and crumble the block if it
     * is spent. Returns true if this pull crumbled the block. Server-side; the block
     * interaction and the gametest both route through here so the mechanic has one
     * implementation.
     */
    public static boolean sortOnce(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof GarbageBlock)) {
            return false;
        }
        SoundType sound = state.getSoundType();

        LootTable table = level.getServer().reloadableRegistries().getLootTable(HOUSEHOLD_PULLS);
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .create(LootContextParamSets.CHEST);
        for (ItemStack stack : table.getRandomItems(params)) {
            if (!stack.isEmpty()) {
                Block.popResource(level, pos, stack);
            }
        }
        level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, 0.6F, 0.9F);

        int pulls = state.getValue(SORTED) + 1;
        if (shouldCrumble(pulls, level.getRandom())) {
            level.destroyBlock(pos, false); // spent: remove without dropping itself
            level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, 0.8F, 0.9F);
            return true;
        }
        level.setBlock(pos, state.setValue(SORTED, pulls), Block.UPDATE_ALL);
        return false;
    }

    /**
     * Crumble decision spread across pulls {@value #MIN_PULLS}-{@value #MAX_PULLS}:
     * certain at MAX_PULLS, a rising chance below it, never before MIN_PULLS.
     */
    private static boolean shouldCrumble(int pulls, RandomSource random) {
        if (pulls >= MAX_PULLS) {
            return true;
        }
        if (pulls < MIN_PULLS) {
            return false;
        }
        float chance = (float) (pulls - (MIN_PULLS - 1)) / (MAX_PULLS - (MIN_PULLS - 1));
        return random.nextFloat() < chance;
    }
}

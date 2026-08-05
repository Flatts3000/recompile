package com.flatts.recompile.content.block;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Mound Ground: coarse dirt that remembers a mound stood on it, and grows it back (design P1.6,
 * Phase 5).
 *
 * <p><b>It is coarse dirt with a different name and a darker face</b>, and the rest follows from
 * that. Same hardness, same sound, same shovel, no tool gate. Grass will not spread onto it by
 * itself, exactly as it will not spread onto coarse dirt, so the Grass Spreader is the only thing
 * that greens it - and greening it is what retires the mound. It is deliberately kept OUT of
 * {@code #minecraft:dirt}: membership would drag it into {@code #encroachable} through
 * {@code substrate_overworld}, and the junkyard eating its own memory is not a fight, it is a bug.
 *
 * <p><b>The memory is a blockstate, not saved data.</b> {@link com.flatts.recompile.content.worldgen.MoundFeature}
 * writes one under every footprint cell carrying that column's height, so the exact footprint and
 * profile survive with no save file, no worldgen-thread concurrency and no region tracking - the same
 * palette-flyweight idiom as {@link SortableBlock}'s {@code sorted}.
 *
 * <p><b>{@code HEIGHT} counts the blocks that belong above it, and 0 means inert.</b> That is what
 * makes a hand-placed one inert rather than the seed of a new mound: the feature always writes at
 * least 1, so 0 can only mean nobody remembers a mound here. Counting rather than storing a top
 * offset also removes an off-by-one that is invisible until measured - the feature fills
 * {@code dy = 0..column} <i>inclusive</i>, so a rim cell of column 0 still carries one block.
 *
 * <p><b>Delivery is a falling block from above</b>, so replenishing mounds are visible across the
 * plain and the lore lands: the void-dumped garbage is still coming home. The design said "from the
 * top of the world"; it drops from {@link RCConfig#MOUND_REGROWTH_DROP_HEIGHT} instead, because this
 * world's build limit is 320 over a surface near -60, and a 380-block fall is nine seconds of entity
 * per block of garbage for a beat that reads identically from thirty. The flight path is checked
 * clear first, so a roof over a mound stops regrowth instead of collecting it.
 */
public class MoundGroundBlock extends Block {

    /**
     * How many mound blocks belong on this column. 0 is inert; 16 covers MoundFeature's tallest
     * column (MAX_HEIGHT 15, filled inclusively, so 16 blocks).
     */
    public static final IntegerProperty HEIGHT = IntegerProperty.create("height", 0, 16);

    /** Why a regrowth attempt did nothing, so a test can tell these apart instead of guessing. */
    public enum Outcome {
        DISABLED,
        INERT,
        FULL,
        BLOCKED,
        GREW
    }

    public MoundGroundBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HEIGHT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HEIGHT);
    }

    /**
     * Regrowth rides the random tick, deliberate on two counts: the block is already the memory, so
     * it costs no extra bookkeeping to make it the ticker too; and random ticks only fire in chunks
     * near a player, so mounds grow where somebody can watch and an unattended world does not refill
     * behind your back - the same "it stops while you are away" rule encroachment follows.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(Math.max(1, RCConfig.MOUND_REGROWTH_RARITY.get())) != 0) {
            return;
        }
        regrowOnce(level, pos);
    }

    /**
     * Try to put one block back on this column. The static entry point the GameTests drive, the same
     * way {@code SortableBlock.sortOnce} is - a test should not have to wait on a random tick.
     */
    public static Outcome regrowOnce(ServerLevel level, BlockPos pos) {
        if (!RCConfig.MOUND_REGROWTH_ENABLED.get()) {
            return Outcome.DISABLED;
        }
        BlockState ground = level.getBlockState(pos);
        if (!(ground.getBlock() instanceof MoundGroundBlock)) {
            return Outcome.INERT;
        }
        int height = ground.getValue(HEIGHT);
        if (height == 0) {
            // Placed by hand, or left by something that forgot to record a height. A block the player
            // puts down must not seed a mound that was never there.
            return Outcome.INERT;
        }

        // Walk up through what is already mound. The first cell that is not mound is the target.
        int filled = 0;
        while (filled < height && isMound(level.getBlockState(pos.above(filled + 1)))) {
            filled++;
        }
        if (filled >= height) {
            return Outcome.FULL;
        }

        BlockPos target = pos.above(filled + 1);
        BlockState at = level.getBlockState(target);
        // "Regrowth only fills exposed ground within the original bounds. Grass and any built or
        // placed blocks stop it." Anything that is not free space belongs to somebody.
        if (!at.isAir() && !at.canBeReplaced()) {
            return Outcome.BLOCKED;
        }

        int drop = RCConfig.MOUND_REGROWTH_DROP_HEIGHT.get();
        if (target.getY() + drop >= level.getMaxY()) {
            return Outcome.BLOCKED;
        }
        // Check the flight path before spawning anything. Without this a roof, a walkway or a floating
        // build over the mound catches the block, and the mound quietly rebuilds itself on top of the
        // player's structure instead of inside its own footprint.
        for (int dy = 1; dy <= drop; dy++) {
            BlockState overhead = level.getBlockState(target.above(dy));
            if (!overhead.isAir() && !overhead.canBeReplaced()) {
                return Outcome.BLOCKED;
            }
        }

        FallingBlockEntity.fall(level, target.above(drop),
            RCBlocks.GARBAGE_BLOCK.get().defaultBlockState());
        return Outcome.GREW;
    }

    /**
     * Whether this block counts as part of the mound when measuring the column.
     *
     * <p>Every block the feature places, so a bale, a bag or an unopened Bulky Waste in the stack is
     * not read as a gap and buried under fresh garbage. Derived from {@link SortableBlock} rather than
     * listed, so a new pile variant is covered the day it is registered; Bulky Waste is named because
     * it is the one mound block that is not sortable.
     */
    private static boolean isMound(BlockState state) {
        return state.getBlock() instanceof SortableBlock
            || state.getBlock() instanceof BulkyWasteBlock;
    }
}

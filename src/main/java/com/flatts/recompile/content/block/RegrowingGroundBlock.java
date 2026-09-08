package com.flatts.recompile.content.block;

import com.flatts.recompile.RCConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Ground that remembers a pile stood on it, and grows it back (design P1.6 + P1.6-R, Phase 5).
 *
 * <p>Three regions have one of these and each grows its own material back: {@link MoundGroundBlock}
 * under a garbage mound, {@link RubbleGroundBlock} under a demolition-yard rubble pile, and
 * {@link StainedGroundBlock} under a radioactive-dump tailings impoundment. Everything below is
 * shared by all three; a subclass supplies {@link #regrown()} and nothing else.
 *
 * <p><b>The memory is a blockstate, not saved data.</b> The pile's feature writes one of these under
 * every footprint cell carrying that column's height, so the exact footprint and profile survive with
 * no save file, no worldgen-thread concurrency and no region tracking - the same palette-flyweight
 * idiom as {@link SortableBlock}'s {@code sorted}.
 *
 * <p><b>{@code HEIGHT} counts the blocks that belong above it, and 0 means inert.</b> That is what
 * makes a hand-placed one inert rather than the seed of a pile that was never there: a feature always
 * writes at least 1, so 0 can only mean nobody remembers a pile here. Counting rather than storing a
 * top offset also removes an off-by-one that is invisible until measured - a feature fills
 * {@code dy = 0..column} <i>inclusive</i>, so a rim cell of column 0 still carries one block.
 *
 * <p><b>Why a class per region rather than one block with a kind property.</b> A block's NAME and its
 * FACE are both per-block rather than per-state, and these three differ in both: the dump's memory is
 * Stained Ground, which already existed, is already painted across the whole impoundment footprint,
 * and already looks like contamination. Folding all three into one block would have put "Mound
 * Ground" under a rubble pile and replaced the dump's own ground with the sprawl's. Two of the three
 * grounds were already in the world before regrowth reached them, which is most of the argument.
 *
 * <p><b>Delivery is a falling block from above, for all three</b> (owner, 2026-09-08), so
 * replenishing piles are visible across the plain and the lore lands: the void-dumped garbage is
 * still coming home. Per-material delivery was considered and rejected - debris is tipped and
 * tailings are pumped as slurry, but the beat's job is to be seen from a distance and that job does
 * not change with the material. The design said "from the top of the world"; it drops from
 * {@link RCConfig#MOUND_REGROWTH_DROP_HEIGHT} instead, because this world's build limit is 320 over a
 * surface near -60, and a 380-block fall is nine seconds of entity per block for a beat that reads
 * identically from thirty. The flight path is checked clear first, so a roof over a pile stops
 * regrowth instead of collecting it.
 *
 * <p><b>Retirement is the block being gone, and only two of the three can be retired.</b> Greening a
 * footprint takes the memory with it, so a mound and a rubble pile both retire when the Grass
 * Spreader reaches them - both grounds are in {@code #recompile:spreadable}. Stained Ground
 * deliberately is not: <i>contamination that scrubs clean is not contamination</i>. So a tailings
 * impoundment never retires and the radioactive dump is permanently non-reclaimable, which is the
 * owner's 2026-09-08 ruling rather than a gap. Nothing here enforces it - it falls out of the tag,
 * which is the right place for it.
 */
public abstract class RegrowingGroundBlock extends Block {

    /**
     * How many pile blocks belong on this column. 0 is inert; 16 covers the tallest column any
     * feature writes (MoundFeature's MAX_HEIGHT is 15, filled inclusively, so 16 blocks).
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

    protected RegrowingGroundBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HEIGHT, 0));
    }

    /** The block this ground puts back, one at a time, until the column is its remembered height. */
    protected abstract Block regrown();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HEIGHT);
    }

    /**
     * Regrowth rides the random tick, deliberate on two counts: the block is already the memory, so
     * it costs no extra bookkeeping to make it the ticker too; and random ticks only fire in chunks
     * near a player, so piles grow where somebody can watch and an unattended world does not refill
     * behind your back - the same "it stops while you are away" rule encroachment follows.
     *
     * <p>A subclass must ask for {@code randomTicks()} in its Properties or this never runs, and
     * nothing about the class can enforce that. {@code every_regrowing_ground_random_ticks} does it
     * instead - Stained Ground shipped without the flag for a fortnight before it needed one.
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
     * way {@code SortableBlock.sortOnce} is - a test should not have to wait on a random tick. What
     * grows is read off the ground block itself, so one entry point serves all three regions.
     */
    public static Outcome regrowOnce(ServerLevel level, BlockPos pos) {
        if (!RCConfig.MOUND_REGROWTH_ENABLED.get()) {
            return Outcome.DISABLED;
        }
        BlockState ground = level.getBlockState(pos);
        if (!(ground.getBlock() instanceof RegrowingGroundBlock memory)) {
            return Outcome.INERT;
        }
        int height = ground.getValue(HEIGHT);
        if (height == 0) {
            // Placed by hand, or left by something that forgot to record a height. A block the player
            // puts down must not seed a pile that was never there.
            return Outcome.INERT;
        }

        // Walk up through what is already pile. The first cell that is not pile is the target.
        int filled = 0;
        while (filled < height && isPile(level.getBlockState(pos.above(filled + 1)))) {
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

        BlockState product = memory.regrown().defaultBlockState();

        // Gravity off means no deorbit: the block is simply put there. GARBAGE_GRAVITY_ENABLED's own
        // comment has read "slump when quarried, deorbit on regrowth" since Phase 0, so this delivery
        // was always meant to answer to it - but it governs the FALL, not the feature. Turning gravity
        // off should change how a pile comes back, not stop it coming back, which is why regrowth
        // keeps its own switch. With nothing in flight there is also no flight path to keep clear, so
        // a roofed pile still fills in from underneath.
        if (!RCConfig.GARBAGE_GRAVITY_ENABLED.get()) {
            level.setBlockAndUpdate(target, product);
            return Outcome.GREW;
        }

        int drop = RCConfig.MOUND_REGROWTH_DROP_HEIGHT.get();
        if (target.getY() + drop >= level.getMaxY()) {
            return Outcome.BLOCKED;
        }
        // Check the flight path before spawning anything. Without this a roof, a walkway or a floating
        // build over the pile catches the block, and the pile quietly rebuilds itself on top of the
        // player's structure instead of inside its own footprint.
        for (int dy = 1; dy <= drop; dy++) {
            BlockState overhead = level.getBlockState(target.above(dy));
            if (!overhead.isAir() && !overhead.canBeReplaced()) {
                return Outcome.BLOCKED;
            }
        }

        FallingBlockEntity.fall(level, target.above(drop), product);
        return Outcome.GREW;
    }

    /**
     * Whether this block counts as part of the pile when measuring the column.
     *
     * <p>Every block a pile feature places, so a bale, a bag or an unopened Bulky Waste in the stack
     * is not read as a gap and buried under fresh garbage. Mostly derived from {@link SortableBlock}
     * rather than listed, so a new pile variant is covered the day it is registered; Bulky Waste and
     * the cardboard pile are named because they are the two mound blocks that are not sortable.
     *
     * <p><b>This is the single definition, and {@code MoundFeature.isMoundContent} defers to it.</b>
     * They were two copies of one idea and had already drifted once: #309 added the cardboard pile to
     * the feature's copy and not to this one, so a column topped with cardboard reported BLOCKED
     * where it should report FULL. Bounded, because cardboard only ever lands on the surface cell -
     * but two methods answering the same question is how it happened, and one of them is covered by
     * {@code every_block_a_mound_places_is_recognised_as_mound_content} while the other was not.
     *
     * <p><b>It is deliberately blind to WHICH pile block it finds, and the kind-aware version is
     * strictly worse.</b> The obvious worry once three regions regrow is that a column reads a
     * neighbouring pile's block as its own fill and stops short. It does, and that is correct: a
     * feature only ever writes into AIR, so a cell holding somebody else's block was never this
     * column's to fill. Checking the kind instead would target that cell, find it occupied by
     * something not replaceable, and return {@link Outcome#BLOCKED} on every tick forever - the column
     * would never grow PAST the foreign block rather than merely stopping one short of full. And when
     * the player mines that block out, the blind version reclaims the cell on the next tick, because
     * air is not pile.
     */
    public static boolean isPile(BlockState state) {
        return state.getBlock() instanceof SortableBlock
            || state.getBlock() instanceof BulkyWasteBlock
            || state.getBlock() instanceof CardboardPileBlock;
    }
}

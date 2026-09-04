package com.flatts.recompile.content.worldgen;

import com.flatts.recompile.content.block.BulkyWasteBlock;
import com.flatts.recompile.content.block.CardboardPileBlock;
import com.flatts.recompile.content.block.MoundGroundBlock;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A pile of garbage blocks (design P0.2): the mounds that crowd the coarse-dirt
 * plain and make the world read as an endless dump. Each mound is a rounded dome
 * of random radius and height, so the field varies instead of tiling. Density is
 * controlled by the placed-feature count (config-tunable later).
 *
 * <p>The block mix (P1.1) follows the mound shape: trash bags and cardboard piles scatter
 * on the outer surface (easy litter, sharing one budget), compacted bales concentrate in
 * the core (the mound shape does the depth-reward work), and Bulky Waste is the uncommon
 * pocket find inside.
 * Per-column heightmap sampling can refine the skirt later if needed.
 */
public class MoundFeature extends Feature<NoneFeatureConfiguration> {

    // Height and width are drawn independently and uniformly, so the field mixes
    // tall spires, low wide heaps, and everything between. Width is a diameter.
    //
    // Public because how big a mound is, is the unit other tuning has to be expressed in. The roach
    // rate was set at "one per 128 blocks" and shipped at two and a half per mound, because nobody
    // converted one into the other; FindRateTest now reads these rather than restating them.
    public static final int MIN_HEIGHT = 3;
    public static final int MAX_HEIGHT = 15;
    public static final int MIN_WIDTH = 4;
    public static final int MAX_WIDTH = 15;

    // Public alongside the dimensions, and for the same reason: what fraction of a mound is actually
    // GARBAGE decides how many pulls a mound is worth, which is the unit every drop rate has to be
    // read in. FindRateTest computes that fraction from these rather than assuming a mound is all
    // garbage - it is 88 percent of it.
    public static final float SURFACE_BAG_CHANCE = 0.22F;

    /**
     * Cardboard piles per surface cell (#309, owner 2026-08-31): cardboard is a thing you SEE in the
     * world, not a weighted entry you occasionally get out of something else.
     *
     * <p><b>It shares the bag's roll, so the two compete for one surface budget.</b> That is the same
     * idiom the core uses for bulky waste and bales, and it is the honest shape: a mound has one
     * surface, and every cell given to boxes is a cell not given to bags or garbage. Rolling
     * independently would let the surface quietly fill up as materials are added, with each addition
     * looking free on its own.
     *
     * <p><b>0.05, against the bag's 0.22, and it was cut from 0.10 on sight</b> (owner, 2026-08-31:
     * "that's way too much cardboard"). At 0.10 an average mound carried about 8 piles and a big one
     * nearly 18 - a visible fraction of the surface, which is more presence than a background
     * material has any business having. 0.05 gives about 4 on an average mound, under a quarter of
     * the bags there, which is enough that a player meets cardboard on the first mound and not so
     * much that mounds start reading as heaps of boxes.
     *
     * <p>One pile is worth roughly one Cardboard Block, so an average mound is about four blocks of
     * cardboard. That is the number to think in when retuning this: it is walls per mound, not piles.
     */
    public static final float SURFACE_CARDBOARD_CHANCE = 0.05F;

    /**
     * What fraction of a surface cell is NOT a garbage block.
     *
     * <p><b>Here so that exactly one place knows it.</b> {@code FindRateTest} computes the garbage
     * fraction of a mound and every household drop rate in the game is read against that number, so
     * it has to agree with what this class actually places. It agreed by having the same arithmetic
     * typed out twice, which survives one surface variant and not two: adding cardboard without
     * touching the test would have left it counting cardboard cells as garbage and reporting every
     * find as commoner than it is, with nothing red anywhere.
     */
    public static final float SURFACE_NON_GARBAGE = SURFACE_BAG_CHANCE + SURFACE_CARDBOARD_CHANCE;
    public static final float CORE_BALE_CHANCE = 0.35F;
    /**
     * Bulky Waste per core cell (P1.11). Inherited unchanged from the appliance it
     * replaced, because it is already playtested: measured 2026-07-15 at ~2.41 per mound
     * and ~12 per chunk against ~48.5 core-eligible cells, so most mounds hold a couple
     * and tearing into one pays off. That is the *beat* - how often "something big is
     * buried here" fires. Which find it turns out to be is the loot table's job, not this
     * number's, so a new find never needs worldgen retuned.
     */
    public static final float CORE_BULKY_WASTE_CHANCE = 0.05F;

    public MoundFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        // The Municipal Aquarium claims its footprint before any feature runs; nothing the yard or
        // the sprawl scatters may stand in it (owner, 2026-09-03: mounds neither). See BuildingHuskFeature.
        if (com.flatts.recompile.content.worldgen.aquarium.AquariumStructure.claims(level, origin)) {
            return false;
        }
        RandomSource random = context.random();

        int height = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
        int width = MIN_WIDTH + random.nextInt(MAX_WIDTH - MIN_WIDTH + 1);
        double radius = width / 2.0;
        int r = (int) Math.floor(radius);

        boolean placedAny = false;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (dist > radius) {
                    continue;
                }
                // Dome profile: tallest at the center, tapering to a 1-block rim.
                int column = (int) Math.round(height * (1.0 - dist / radius));
                boolean core = dist < radius * 0.4;
                for (int dy = 0; dy <= column; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (level.getBlockState(pos).isAir()) {
                        boolean surface = dy == column;
                        level.setBlock(pos, pickBlock(random, core, dy, column, surface), 2);
                        placedAny = true;
                    }
                }
                writeBed(level, origin.offset(dx, -1, dz), column);
            }
        }
        return placedAny;
    }

    /**
     * Remember this column's original height under its footprint, so Phase 5 can grow it back.
     *
     * <p><b>Takes the taller of the two when mounds overlap.</b> The loop above only writes into air,
     * so a later mound interleaves with an earlier one rather than replacing it - and its rim, which
     * is a column of 0, would otherwise overwrite a tall neighbour's memory and permanently flatten
     * what regrows there. Silent, and invisible until somebody quarries that mound and watches it come
     * back wrong.
     *
     * <p>Only ever replaces ground. Writing into another mound's garbage would punch a hole in a
     * stack nobody has touched yet.
     *
     * <p>Stores the block COUNT, not the top offset: this loop fills {@code dy = 0..column}
     * inclusive, so a rim cell of column 0 still carries one block. Storing the offset would build
     * every mound one block short, and would leave 0 meaning both "a one-block rim" and "nothing
     * here" - which is the value a hand-placed block has, and it must stay inert.
     */
    private void writeBed(WorldGenLevel level, BlockPos pos, int column) {
        BlockState existing = level.getBlockState(pos);
        if (existing.getBlock() instanceof MoundGroundBlock) {
            if (existing.getValue(MoundGroundBlock.HEIGHT) >= column + 1) {
                return;
            }
        } else if (!existing.isSolidRender() || isMoundContent(existing)) {
            return;
        }
        level.setBlock(pos, RCBlocks.MOUND_GROUND.get().defaultBlockState()
            .setValue(MoundGroundBlock.HEIGHT, Math.min(column + 1, 16)), 2);
    }

    /**
     * Whether this block is something a mound is MADE of, as opposed to ground a mound can sit on.
     *
     * <p><b>Extracted because the list went stale the first time it was extended, and silently.</b>
     * It used to be two {@code instanceof} checks inline in {@link #writeBed} - SortableBlock and
     * BulkyWasteBlock - which was complete only for as long as every mound block was one of those
     * two things. The Cardboard Pile (#309) is neither: it is a plain {@link FallingBlock}, and it
     * is a full opaque cube, so it passed {@code isSolidRender()} and fell straight through the
     * guard. Mounds overlap by design, so a later mound's bed pass would have replaced a
     * neighbour's cardboard pile with Mound Ground - destroying the pile AND planting a regrowth
     * bed partway up a stack, which is precisely what {@code writeBed}'s own javadoc says it exists
     * to prevent. Nothing would have been logged and nothing would have looked wrong until someone
     * quarried that mound and watched it regrow from the middle.
     *
     * <p>{@code every_block_a_mound_places_is_recognised_as_mound_content} sweeps
     * {@link #pickBlock}'s outputs against this, so the next variant fails the build instead.
     */
    public static boolean isMoundContent(BlockState state) {
        return state.getBlock() instanceof SortableBlock
            || state.getBlock() instanceof BulkyWasteBlock
            || state.getBlock() instanceof CardboardPileBlock;
    }

    /**
     * Every block {@link #pickBlock} can return, for the test that keeps
     * {@link #isMoundContent} honest. Public only because the GameTests live in another package.
     */
    public static java.util.List<BlockState> everyMoundBlock() {
        return java.util.List.of(
            RCBlocks.TRASH_BAG.get().defaultBlockState(),
            RCBlocks.CARDBOARD_PILE.get().defaultBlockState(),
            RCBlocks.BULKY_WASTE.get().defaultBlockState(),
            RCBlocks.COMPACTED_BALE.get().defaultBlockState(),
            RCBlocks.GARBAGE_BLOCK.get().defaultBlockState());
    }

    /** Pick the block for a mound cell: bags on the surface, bales/bulky waste in the core. */
    private BlockState pickBlock(RandomSource random, boolean core, int dy, int column, boolean surface) {
        if (surface) {
            // ONE ROLL FOR BOTH, like the core's bulky/bale pair: the cardboard band sits above the
            // bag band, so changing either moves the other and neither can be tuned into the other's
            // share by accident.
            float litter = random.nextFloat();
            if (litter < SURFACE_BAG_CHANCE) {
                return RCBlocks.TRASH_BAG.get().defaultBlockState();
            }
            if (litter < SURFACE_NON_GARBAGE) {
                return RCBlocks.CARDBOARD_PILE.get().defaultBlockState();
            }
        }
        if (core && dy <= column * 0.5) {
            // One roll shared by both: bulky waste takes the bottom band, bales the next.
            // So the bale chance is offset by the bulky one - change either and both move.
            float roll = random.nextFloat();
            if (roll < CORE_BULKY_WASTE_CHANCE) {
                return RCBlocks.BULKY_WASTE.get().defaultBlockState();
            }
            if (roll < CORE_BULKY_WASTE_CHANCE + CORE_BALE_CHANCE) {
                return RCBlocks.COMPACTED_BALE.get().defaultBlockState();
            }
        }
        return RCBlocks.GARBAGE_BLOCK.get().defaultBlockState();
    }
}

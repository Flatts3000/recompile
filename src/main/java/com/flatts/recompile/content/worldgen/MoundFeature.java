package com.flatts.recompile.content.worldgen;

import com.flatts.recompile.content.block.BulkyWasteBlock;
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
     * <p>0.10 against the bag's 0.22, which is a handful of piles on a small mound and a dozen or
     * more on a big one - common enough that a new player trips over cardboard before they have
     * crafted anything, which is the entire point of the family.
     */
    public static final float SURFACE_CARDBOARD_CHANCE = 0.10F;

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
        } else if (!existing.isSolidRender() || existing.getBlock() instanceof SortableBlock
                || existing.getBlock() instanceof BulkyWasteBlock) {
            return;
        }
        level.setBlock(pos, RCBlocks.MOUND_GROUND.get().defaultBlockState()
            .setValue(MoundGroundBlock.HEIGHT, Math.min(column + 1, 16)), 2);
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

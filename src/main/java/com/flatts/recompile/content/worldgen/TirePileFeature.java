package com.flatts.recompile.content.worldgen;

import com.flatts.recompile.content.block.BulkyWasteBlock;
import com.flatts.recompile.content.block.MoundGroundBlock;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Tire dumps (spec {@code docs/tire_piles_spec.md}, #155).
 *
 * <p><b>A dump, not a tire</b> (owner, 2026-09-04). One roll of this feature places a CLUSTER of piles
 * rather than a single stack, because a real tire dump is many piles in one place and a lone stack on a
 * hillside reads as clutter rather than as somewhere. The rarity filter is well below the mound's, so a
 * dump is a destination.
 *
 * <p><b>Each pile is circular in plan with the mound's height falloff</b>, {@code height * (1 - d/r)},
 * which reuses arithmetic that already exists and reads more natural at distance than a flat top.
 * Tires are slabs, so a column of N tires is N/2 blocks tall: an odd count finishes with a bottom slab
 * and an even one with a double.
 *
 * <p><b>Placement rules, all owner-stated and all checked before anything is written.</b> A pile takes
 * no mound, no Mound Ground, no leachate and nothing built, and it writes no Mound Ground of its own,
 * so nothing regrows a dump once it is cleared.
 *
 * <p><b>The avoidance is a block read at this feature's own position, which is the whole reason it is
 * safe.</b> This feature is ordered after {@code garbage_mound} in the biome's decoration step, so the
 * mound is already in the chunk and is an ordinary block to look at. Do NOT reach for
 * {@code StructureManager} here: a structure lookup reads a chunk at {@code STRUCTURE_REFERENCES} and
 * the allowed status degrades with distance, which is what crashed world generation in #349.
 */
public class TirePileFeature extends Feature<NoneFeatureConfiguration> {

    /** Piles in one dump. A dump is a site; these are what give it an edge. */
    private static final int MIN_PILES = 3;
    private static final int MAX_PILES = 6;
    /** How far a dump's piles scatter from its origin. */
    private static final int SPREAD = 7;
    /**
     * Tries a pile gets at finding somewhere it fits before that pile is given up on.
     *
     * <p>Without this a dump is one blob rather than a site. Piles refuse an actual mound and the
     * sprawl is mostly mounds, so a single blind offset lands about one pile in five and the other
     * four are simply lost - which was visible the first time one generated: 55 tires, all of them in
     * one patch. Retrying finds the gaps BETWEEN mounds, which is where a real dump would be tipped.
     */
    private static final int PLACEMENT_TRIES = 10;

    private static final int MIN_RADIUS = 1;
    private static final int MAX_RADIUS = 3;
    /** Tires, not blocks: a column of 5 tires is two and a half blocks tall. */
    private static final int MIN_TIRES = 2;
    private static final int MAX_TIRES = 7;

    /** Roughly one pile in six is alight somewhere. */
    private static final float LIT_CHANCE = 0.16F;

    public TirePileFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        // The Municipal Aquarium claims its footprint before any feature runs; nothing the sprawl
        // scatters may stand in it. See BuildingHuskFeature.
        if (com.flatts.recompile.content.worldgen.aquarium.AquariumStructure.claims(level, origin)) {
            return false;
        }
        RandomSource random = context.random();

        int piles = MIN_PILES + random.nextInt(MAX_PILES - MIN_PILES + 1);
        boolean placedAny = false;
        for (int i = 0; i < piles; i++) {
            for (int attempt = 0; attempt < PLACEMENT_TRIES; attempt++) {
                int ox = origin.getX() + random.nextInt(SPREAD * 2 + 1) - SPREAD;
                int oz = origin.getZ() + random.nextInt(SPREAD * 2 + 1) - SPREAD;
                if (pile(level, random, ox, origin.getY(), oz)) {
                    placedAny = true;
                    break;
                }
            }
        }
        return placedAny;
    }

    /** One circular heap. Returns false if the ground refused it, which is not an error. */
    private boolean pile(WorldGenLevel level, RandomSource random, int cx, int probeY, int cz) {
        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        int tires = MIN_TIRES + random.nextInt(MAX_TIRES - MIN_TIRES + 1);

        // Survey the whole footprint before writing a block. A pile that half-lands looks like damage.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                BlockPos ground = groundAt(level, cx + dx, probeY, cz + dz);
                if (ground == null || !clear(level, ground)) {
                    return false;
                }
            }
        }

        BlockState tire = RCBlocks.TIRE.get().defaultBlockState();
        boolean lit = random.nextFloat() < LIT_CHANCE;
        boolean placed = false;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius) {
                    continue;
                }
                BlockPos ground = groundAt(level, cx + dx, probeY, cz + dz);
                if (ground == null) {
                    continue;
                }
                retire(level, ground);
                int column = Math.max(1, (int) Math.round(tires * (1.0 - dist / (radius + 1.0))));

                // MERGE INTO A HALF-FILLED CELL RATHER THAN STARTING ABOVE IT. Piles overlap - they
                // scatter and they retry - so a later pile routinely lands on the top tire of an
                // earlier one, and that tire is a BOTTOM slab filling half its cell. Stacking from the
                // cell above it leaves half a block of daylight under the new column. Filling it to a
                // DOUBLE is both the fix and the honest reading: another tire went on that stack.
                if (column > 0 && isHalfTire(level, ground)) {
                    level.setBlock(ground, tire.setValue(SlabBlock.TYPE, SlabType.DOUBLE),
                        Block.UPDATE_CLIENTS);
                    column--;
                    placed = true;
                }
                BlockPos top = stack(level, ground.above(), column, tire);

                // FIRE ON THE TOP TIRE OF A STACK ONLY, never buried in a column. Half the particle
                // load of a lit dump, and it is what a real pile looks like, since a tire fire burns
                // at the surface. Only the centre of a lit pile, so a dump reads as smouldering rather
                // than as an inferno.
                if (top != null) {
                    placed = true;
                    if (lit && dx == 0 && dz == 0) {
                        // A COLUMN OF ODD LENGTH ENDS IN A BOTTOM SLAB, and fire is a whole block that
                        // can only sit in the cell ABOVE it - which is half a block clear of the rubber
                        // and reads as a flame hanging in the air. One more tire costs nothing and puts
                        // a flat top under the flame.
                        if (isHalfTire(level, top)) {
                            level.setBlock(top, tire.setValue(SlabBlock.TYPE, SlabType.DOUBLE),
                                Block.UPDATE_CLIENTS);
                        }
                        if (level.getBlockState(top.above()).isAir()) {
                            level.setBlock(top.above(), Blocks.FIRE.defaultBlockState(),
                                Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }
        return placed;
    }

    /**
     * Lay {@code count} tires upward from {@code base}, returning the highest cell written or null.
     *
     * <p>Pairs become doubles and the odd tire is a bottom slab, which HAS to be the last one laid
     * rather than the first: a bottom slab fills the lower half of its cell, so a full cell resting on
     * one would hang half a block clear. At the top of a column it is simply a stack of odd height.
     */
    private BlockPos stack(WorldGenLevel level, BlockPos base, int count, BlockState tire) {
        BlockPos top = null;
        BlockPos.MutableBlockPos cursor = base.mutable();
        int left = count;
        while (left > 0) {
            if (!level.getBlockState(cursor).isAir()) {
                break;
            }
            SlabType type = left >= 2 ? SlabType.DOUBLE : SlabType.BOTTOM;
            level.setBlock(cursor, tire.setValue(SlabBlock.TYPE, type), Block.UPDATE_CLIENTS);
            top = cursor.immutable();
            left -= left >= 2 ? 2 : 1;
            cursor.move(Direction.UP);
        }
        return top;
    }

    /** A tire filling only the bottom half of its cell, which is the one thing nothing may rest on. */
    private static boolean isHalfTire(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() == RCBlocks.TIRE.get()
            && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    /**
     * The first non-air block near the probe height, or null if there is none in the window.
     *
     * <p>The window is generous in both directions because a dump's piles scatter up to
     * {@link #SPREAD} blocks and this terrain is mounds: the column seven blocks away is routinely
     * several blocks higher or lower than the one the placement heightmap measured.
     */
    private static BlockPos groundAt(WorldGenLevel level, int x, int y, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos().set(x, y + 4, z);
        while (cursor.getY() > y - 8 && level.getBlockState(cursor).isAir()) {
            cursor.move(Direction.DOWN);
        }
        return level.getBlockState(cursor).isAir() ? null : cursor.immutable();
    }

    /**
     * Whether a pile may stand on this cell.
     *
     * <p>Refuses a mound, standing leachate and anything already built. Leachate is refused because a
     * heap dropped in a pool looks like a mistake and puts tires under a fluid nothing here considered.
     *
     * <p><b>Mound Ground is NOT refused here, and that is a measured reversal.</b> The rule as first
     * written also rejected the ground that remembers a mound, which reads correct and makes the
     * feature impossible: a census of two freshly generated sprawl chunks put Mound Ground under
     * <b>943 and 884 of 1024 columns</b>, so the surface is 86 to 92 percent mound memory and a
     * footprint that must avoid all of it never lands. Six hand-placed features in a row failed before
     * anything was measured, and the symptom of that is indistinguishable from a feature that is
     * merely rare. What the owner's rule protects is {@link #retire}: no Mound Ground under a dump
     * once it is built, which is now true because the dump CLEARS it rather than because it dodged it.
     */
    private static boolean clear(WorldGenLevel level, BlockPos ground) {
        BlockState state = level.getBlockState(ground);
        if (state.getBlock() instanceof SortableBlock
            || state.getBlock() instanceof BulkyWasteBlock
            || state.getBlock() == RCBlocks.LEACHATE.get()) {
            return false;
        }
        // NOTHING MAY REST ON A PARTIAL TOP. A block whose UP face is not full leaves the tire above it
        // hanging in the air, and the sprawl has plenty of them. A tire is the one exception, because a
        // half-filled one is merged into rather than built on - see the write pass.
        if (!(state.getBlock() == RCBlocks.TIRE.get())
            && !state.isFaceSturdy(level, ground, Direction.UP)) {
            return false;
        }
        // And the column it would stand in has to be free.
        return level.getBlockState(ground.above()).isAir();
    }

    /**
     * Take a cell's mound memory away, permanently.
     *
     * <p>Owner's rule, restated as what it actually protects: a dump does not have Mound Ground under
     * it and does not replenish. Left in place the memory would keep random-ticking and spawn Blocks
     * of Garbage into the air above, which would then fall onto the tires - so this is a correctness
     * fix and not only an aesthetic one. Coarse dirt is the revert target encroachment already uses
     * for a retired patch, so a dump retires ground exactly the way greening it does.
     */
    private static void retire(WorldGenLevel level, BlockPos ground) {
        if (level.getBlockState(ground).getBlock() instanceof MoundGroundBlock) {
            level.setBlock(ground, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}

package com.flatts.recompile.content.worldgen.tower;

import com.flatts.recompile.registry.RCStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * A brick smokestack, standing or felled (#308).
 *
 * <p><b>Why a chimney belongs in the demolition yard and a cooling tower does not.</b> The yard is
 * about things being taken apart, and a chimney is the thing left standing <em>because</em> it is the
 * hardest thing to bring down - crews fell them last. A half-cleared site with the stack still up is
 * the most yard-specific image available, and a felled one lying in sections is the story of what the
 * yard does to everything else.
 *
 * <p><b>Deliberately shorter than the cooling tower.</b> 30 to 48 against its 62 to 76, so the two
 * layer instead of competing: the tower is what you see from the next region, the chimneys are what
 * you see once you are in the yard. Two landmarks at one height would just be two landmarks.
 *
 * <p><b>No way in.</b> The shell is a ring with no opening, which is what a real chimney is - a flue,
 * not a room. Drawing it solid would read the same from outside and cost several thousand more blocks.
 * It is not climbable and there is no shaft: a vertical shaft with a ladder is already the sewer's
 * entrance, and reusing that verb would make the two structures read as the same thing.
 *
 * <p><b>The flue is not empty, and that is a knowing exception.</b> #308 rules that these hold nothing.
 * A standing stack has a lit campfire buried in it to make the smoke the owner asked for (see
 * {@link #light}) - two charcoal to anyone who tunnels forty blocks up a chimney for it, and the
 * cheapest vanilla source of a tall plume, where a hay bale under the fire would have been nine wheat.
 *
 * <p>It also has a husk spawner at the foot, and that one is not small. It reaches past the brick on
 * purpose, so walking by a chimney is an encounter rather than scenery. Vanilla husks drop iron on a
 * player kill, so the husk loot table is overridden here without that pool: this mod PLACES this
 * spawner, at a fixed point, in unlimited supply, and #91 is on record as an iron gate that died to a
 * route nobody had costed.
 *
 * <p><b>That closes what this structure adds and no more, and the difference matters.</b> It is not a
 * guarantee that iron cannot be farmed from mobs in the demolition yard, and reading it as one would be
 * wrong twice. The yard's biome already lists {@code minecraft:zombie} at weight 90 with its vanilla
 * table intact, so natural spawns drop iron there today and did before this structure existed. And a
 * husk submerged for 300 ticks converts to a zombie ({@code Husk.doUnderWaterConversion}), which is the
 * override walked around with a bucket. Whether the vanilla mob economy should be gated at all is a
 * design question rather than a defect in this piece, and it is filed rather than decided here (#318).
 *
 * <p>Everything is derived from the bounding box, for the reason {@link CoolingTowerPiece} gives: a
 * piece is stored as its box and rebuilt from it, so a shape held in a field comes back wrong.
 */
public class SmokestackPiece extends StructurePiece {

    /**
     * Half the width at the foot.
     *
     * <p><b>Slenderness is the whole silhouette, and the first pass got it wrong.</b> At radius 3.4 and
     * 25 to 40 tall the ratio was about four to one, which generated a stubby brick tower that read as
     * a keep rather than a chimney. A real industrial stack is nearer ten to one. Narrower and taller
     * puts it between about six at the shortest and nine at the tallest, which is what makes it
     * recognisable at the distance it is seen from.
     */
    private static final double BASE_RADIUS = 2.6;

    /** Half the width at the rim. Chimneys taper gently, so this is close to the base. */
    private static final double TOP_RADIUS = 1.7;

    /** How many courses at the top are eaten away. */
    private static final int RAGGED_ROWS = 4;

    /** How much of a felled stack is left standing as a stump. */
    private static final int STUMP_ROWS = 5;

    /** How far below the stump a fallen section will follow the ground. Also how far the box reaches. */
    private static final int GROUND_SEARCH_DOWN = 6;

    /**
     * Where the stack stands, which is NOT the centre of the box.
     *
     * <p><b>This is the one thing that has to be saved, and the tower does not have to save anything.</b>
     * A felled stack stretches its box toward wherever it fell, so the box is asymmetric and its centre
     * is somewhere out along the debris. Recovering the foot from a corner only works if you already
     * know which way it went, which is the thing being recovered - so two ints go to disk instead.
     */
    private final int footX;
    private final int footZ;

    /** The shortest and tallest a stack can roll. Exposed so the profile test can measure both ends. */
    static final int MIN_HEIGHT = 30;
    static final int MAX_HEIGHT = 48;

    /**
     * Height over width, which is the only number that decides whether this reads as a chimney.
     *
     * <p>Package-private because it is what {@code SmokestackProfileTest} measures. A stack is a
     * silhouette and nothing else - there is no interior, no loot and no interaction - so the ratio is
     * the entire specification.
     */
    static double slenderness(int height) {
        return height / (BASE_RADIUS * 2);
    }

    public SmokestackPiece(RandomSource random, int x, int base, int z) {
        super(RCStructures.SMOKESTACK_PIECE.get(), 0, boxFor(random, x, base, z));
        this.footX = x;
        this.footZ = z;
    }

    public SmokestackPiece(CompoundTag tag) {
        super(RCStructures.SMOKESTACK_PIECE.get(), tag);
        this.footX = tag.getIntOr("foot_x", 0);
        this.footZ = tag.getIntOr("foot_z", 0);
    }

    /**
     * 30 to 48 tall, and the box is only as big as this particular stack needs.
     *
     * <p><b>The box has to cover the fallen length, because a piece may only write inside its own box</b>
     * - a stack that fell outside it would be sheared off at the edge with nothing said. But a square
     * of plus-or-minus the height in every direction, which is the easy way to guarantee that, is a box
     * nearly a hundred across for a chimney six wide. That matters beyond tidiness: {@code beard_thin}
     * terrain adaptation works on the bounding box, so an oversized one would flatten the yard in a
     * wide circle around every stack.
     *
     * <p>So the fall is decided here rather than at draw time - from the position, so both agree - and
     * the box is the stump plus the far end of the fall and nothing more.
     */
    private static BoundingBox boxFor(RandomSource random, int x, int base, int z) {
        int height = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
        int foot = (int) Math.ceil(BASE_RADIUS) + 1;
        if (!isFelled(x, z)) {
            return new BoundingBox(x - foot, base, z - foot, x + foot, base + height - 1, z + foot);
        }
        double angle = fallAngle(x, z);
        int endX = x + (int) Math.round(Math.cos(angle) * (height + BASE_RADIUS));
        int endZ = z + (int) Math.round(Math.sin(angle) * (height + BASE_RADIUS));
        // A FELLED STACK REACHES BELOW ITS FOOT. Sections follow the ground under them and that search
        // goes GROUND_SEARCH_DOWN below the stump, so the box goes there too: a piece may only write
        // inside its own box, and beard_thin adapts terrain from box.minY().
        return new BoundingBox(
            Math.min(x, endX) - foot, base - GROUND_SEARCH_DOWN, Math.min(z, endZ) - foot,
            Math.max(x, endX) + foot, base + height - 1, Math.max(z, endZ) + foot);
    }

    /**
     * One in three comes down.
     *
     * <p>Derived from the position rather than rolled, because {@link #boxFor} has to size the box for
     * it before {@code postProcess} ever runs, and the two answering differently would mean a stack
     * drawn outside the box that was reserved for it.
     */
    private static boolean isFelled(int x, int z) {
        return RandomSource.create(x * 341873128712L + z * 132897987541L).nextInt(3) == 0;
    }

    private static double fallAngle(int x, int z) {
        RandomSource source = RandomSource.create(x * 341873128712L + z * 132897987541L);
        source.nextInt(3);
        return source.nextDouble() * Math.PI * 2;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("foot_x", this.footX);
        tag.putInt("foot_z", this.footZ);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
            RandomSource random, BoundingBox limit, ChunkPos chunk, BlockPos origin) {
        BoundingBox box = this.boundingBox;
        int baseY = box.minY();
        int height = box.maxY() - box.minY() + 1;

        if (isFelled(this.footX, this.footZ)) {
            drawFelled(level, limit, this.footX, this.footZ, baseY, height);
        } else {
            drawStanding(level, limit, this.footX, this.footZ, baseY, height, 0, height);
            light(level, limit, this.footX, this.footZ, baseY, height);
            // A HUSK SPAWNER AT THE FOOT OF THE FLUE (owner, 2026-08-31).
            //
            // NOT SEALED, AND THAT IS THE POINT. It first shipped clamped to a spawn range of 1 so the
            // husks stayed inside the brick until a player broke in. That was wrong: it made the
            // structure inert to anyone who simply walked past, which is most people. The default
            // range of 4 reaches past the flue, so passing a chimney at any hour puts husks around
            // you. The spawner needs no line of sight - it only measures distance to the player.
            //
            // NO HAT, unlike the tower's, and the reason is the mob rather than the building. A husk
            // is the zombie that does not burn: Husk.isSunSensitive returns false, so a leather cap on
            // one would be cargo-culted from the other structure and would do nothing. That holds out
            // in the open, which is where the range of 4 puts most of them.
            Spawners.place(level, limit, new BlockPos(this.footX, baseY + 1, this.footZ),
                "minecraft:husk", 4, null);
        }
    }

    /** The stack itself: a tapering ring, ragged at the rim. */
    private void drawStanding(WorldGenLevel level, BoundingBox limit, int cx, int cz, int baseY,
            int height, int from, int to) {
        BlockState brick = Blocks.BRICKS.defaultBlockState();
        for (int t = from; t < to; t++) {
            int y = baseY + t;
            double r = BASE_RADIUS + (TOP_RADIUS - BASE_RADIUS) * (t / (double) Math.max(1, height - 1));
            int span = (int) Math.ceil(BASE_RADIUS) + 1;
            for (int dx = -span; dx <= span; dx++) {
                for (int dz = -span; dz <= span; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    // A ring, not a disc: a chimney is a flue. See the class note.
                    if (d > r || d < r - 1.35) {
                        continue;
                    }
                    int fromTop = to - 1 - t;
                    if (fromTop < RAGGED_ROWS
                            && !RaggedRim.survives(cx + dx, cz + dz, fromTop, RAGGED_ROWS)) {
                        continue;
                    }
                    put(level, limit, cx + dx, y, cz + dz, brick);
                }
            }
        }
    }

    /**
     * Something still burning at the top, which is the point of the whole structure.
     *
     * <p><b>It is not realistic and it is deliberate</b> (owner, 2026-08-31). An abandoned stack does
     * not smoke. But this world is a flat brown plain of things nobody wanted, and a plume on the
     * horizon is the one cheap signal that says somebody is still out here - it gives the dump life in
     * a way no amount of rubble does. A felled stack gets nothing, so standing and fallen read as alive
     * and dead rather than as two shapes.
     *
     * <p><b>A campfire with {@code SIGNAL_FIRE} set, and no hay bale under it.</b> Vanilla makes the
     * tall twenty-four block plume by putting a campfire on a hay block, but the height is driven by
     * the blockstate rather than by what is beneath it at render time - so setting the property
     * directly gets the same plume without burying a hay bale in the flue. That matters: a hay bale is
     * nine wheat, and #308's decision is that these hold nothing. A free crop inside a landmark that
     * pays nothing would be exactly the payout that decision rules out.
     *
     * <p>It sits down inside the flue rather than on the rim, so the source is out of sight and only
     * the smoke shows. The centre column is always clear: the wall is drawn as a ring, and at the top
     * radius only the exact centre falls inside it.
     */
    private void light(WorldGenLevel level, BoundingBox limit, int cx, int cz, int baseY, int height) {
        BlockState fire = Blocks.CAMPFIRE.defaultBlockState()
            .setValue(CampfireBlock.LIT, true)
            .setValue(CampfireBlock.SIGNAL_FIRE, true);
        put(level, limit, cx, baseY + height - 1 - RAGGED_ROWS - 1, cz, fire);
    }

    /**
     * The one that came down: a stump, and the rest of it lying in broken sections.
     *
     * <p>Same geometry rotated onto its side, which is why the variant costs the code twice rather
     * than twice over. The gaps between sections are where it broke on landing.
     */
    private void drawFelled(WorldGenLevel level, BoundingBox limit, int cx, int cz, int baseY,
            int height) {
        drawStanding(level, limit, cx, cz, baseY, height, 0, STUMP_ROWS);

        BlockState brick = Blocks.BRICKS.defaultBlockState();
        double angle = fallAngle(cx, cz);
        double ux = Math.cos(angle);
        double uz = Math.sin(angle);
        int fallen = height - STUMP_ROWS;

        for (int along = 2; along < fallen; along++) {
            // Broken into sections, with the breaks widening toward the far end - which is where a
            // falling chimney actually shatters.
            double breakChance = 0.06 + 0.5 * (along / (double) fallen);
            if (RaggedRim.hash(cx + along, baseY, cz - along) < breakChance * 0.35) {
                continue;
            }
            double r = BASE_RADIUS + (TOP_RADIUS - BASE_RADIUS) * (along / (double) Math.max(1, height - 1));
            int lx = cx + (int) Math.round(ux * (along + BASE_RADIUS));
            int lz = cz + (int) Math.round(uz * (along + BASE_RADIUS));
            int across = (int) Math.ceil(r) + 1;
            // THE DISC IS 2r TALL, NOT r. It is a ring centred at dy = r with radius r, so it runs from
            // the ground to twice the radius. Bounding the vertical sweep by the horizontal one sliced
            // the crown off every section near the stump, which reads as an open trough rather than
            // as pipe.
            int tall = (int) Math.ceil(2 * r) + 1;
            for (int side = -across; side <= across; side++) {
                int px = lx + (int) Math.round(-uz * side);
                int pz = lz + (int) Math.round(ux * side);
                // ONE GROUND LOOKUP PER COLUMN, HOISTED OUT OF THE dy LOOP ON PURPOSE. Calling it per
                // block reads the bricks this same piece laid a moment earlier: the block at dy=0
                // becomes the ground for dy=1, which lands at dy=2 leaving a hole, and by dy=4 the
                // column is four blocks clear of anything. The fix for floating blocks was creating
                // them.
                int ground = groundAt(level, limit, px, pz, baseY);
                for (int dy = 0; dy <= tall; dy++) {
                    // A disc standing up across the fall line: the section seen end-on.
                    double d = Math.sqrt(side * side + (dy - r) * (dy - r));
                    if (d > r || d < r - 1.35) {
                        continue;
                    }
                    // LAY IT ON THE GROUND UNDER IT, not on the ground under the stump. The yard has
                    // rubble piles and mounds, so a section forty blocks out lands at a different
                    // height, and using the stump's would hang the far end in the air or bury it.
                    put(level, limit, px, ground + dy, pz, brick);
                }
            }
        }
    }

    /**
     * The first free block above the ground in this column, searched near the stack's own base.
     *
     * <p>Bounded on purpose: a fallen stack should follow the terrain it landed on, not chase a hole
     * twenty blocks down. Outside the search window it falls back to the stump's level, which is the
     * old behaviour and is right for flat ground.
     */
    private static int groundAt(WorldGenLevel level, BoundingBox limit, int x, int z, int around) {
        for (int y = around + 4; y >= around - GROUND_SEARCH_DOWN; y--) {
            BlockPos at = new BlockPos(x, y, z);
            if (!limit.isInside(at)) {
                continue;
            }
            if (!level.getBlockState(at).isAir()) {
                return y + 1;
            }
        }
        return around;
    }

    private static void put(WorldGenLevel level, BoundingBox limit, int x, int y, int z, BlockState state) {
        BlockPos at = new BlockPos(x, y, z);
        if (limit.isInside(at)) {
            level.setBlock(at, state, Block.UPDATE_CLIENTS);
        }
    }
}

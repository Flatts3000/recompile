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
 * <p><b>Deliberately shorter than the cooling tower.</b> 25 to 40 against its 62 to 76, so the two
 * layer instead of competing: the tower is what you see from the next region, the chimneys are what
 * you see once you are in the yard. Two landmarks at one height would just be two landmarks.
 *
 * <p><b>Nothing inside, and no way in.</b> The shell is a ring with no opening, which is what a real
 * chimney is - a flue, not a room. Drawing it solid would read the same from outside and cost several
 * thousand more blocks, so it is a wall around sealed air. It is not climbable and there is no shaft:
 * a vertical shaft with a ladder is already the sewer's entrance, and reusing that verb would make the
 * two structures read as the same thing.
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
     * puts this around eight, which is what makes it recognisable at the distance it is seen from.
     */
    private static final double BASE_RADIUS = 2.6;

    /** Half the width at the rim. Chimneys taper gently, so this is close to the base. */
    private static final double TOP_RADIUS = 1.7;

    /** How many courses at the top are eaten away. */
    private static final int RAGGED_ROWS = 4;

    /** How much of a felled stack is left standing as a stump. */
    private static final int STUMP_ROWS = 5;

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
     * 25 to 40 tall, and the box is only as big as this particular stack needs.
     *
     * <p><b>The box has to cover the fallen length, because a piece may only write inside its own box</b>
     * - a stack that fell outside it would be sheared off at the edge with nothing said. But a square
     * of plus-or-minus the height in every direction, which is the easy way to guarantee that, is a
     * ninety block box for a nine block chimney. That matters beyond tidiness: {@code beard_thin}
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
        return new BoundingBox(
            Math.min(x, endX) - foot, base, Math.min(z, endZ) - foot,
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
                    if (fromTop < RAGGED_ROWS) {
                        double bite = (RAGGED_ROWS - fromTop) / (double) (RAGGED_ROWS + 1);
                        if (hash(cx + dx, y, cz + dz) <= bite) {
                            continue;
                        }
                    }
                    put(level, limit, cx + dx, y, cz + dz, brick);
                }
            }
        }
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
            if (hash(cx + along, baseY, cz - along) < breakChance * 0.35) {
                continue;
            }
            double r = BASE_RADIUS + (TOP_RADIUS - BASE_RADIUS) * (along / (double) Math.max(1, height - 1));
            int lx = cx + (int) Math.round(ux * (along + BASE_RADIUS));
            int lz = cz + (int) Math.round(uz * (along + BASE_RADIUS));
            int span = (int) Math.ceil(r) + 1;
            for (int dy = 0; dy <= span; dy++) {
                for (int side = -span; side <= span; side++) {
                    // A disc standing up across the fall line: the section seen end-on.
                    double d = Math.sqrt(side * side + (dy - r) * (dy - r));
                    if (d > r || d < r - 1.35) {
                        continue;
                    }
                    int px = lx + (int) Math.round(-uz * side);
                    int pz = lz + (int) Math.round(ux * side);
                    put(level, limit, px, baseY + dy, pz, brick);
                }
            }
        }
    }

    private static double hash(int x, int y, int z) {
        long h = x * 3129871L ^ z * 116129781L ^ y * 7919L;
        h = h * h * 42317861L + h * 11L;
        return ((h >> 16) & 0xFFFF) / 65536.0;
    }

    private static void put(WorldGenLevel level, BoundingBox limit, int x, int y, int z, BlockState state) {
        BlockPos at = new BlockPos(x, y, z);
        if (limit.isInside(at)) {
            level.setBlock(at, state, Block.UPDATE_CLIENTS);
        }
    }
}

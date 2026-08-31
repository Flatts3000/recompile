package com.flatts.recompile.content.worldgen.tower;

import com.flatts.recompile.registry.RCBlocks;
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
 * The shell itself: a hyperboloid of one sheet, one block thick, standing on legs.
 *
 * <p><b>Every number is derived from the bounding box, and nothing else is saved.</b> A piece is
 * written to disk as its box and re-read later, so anything held in a field has to be serialised or it
 * comes back wrong - and a structure that regenerates differently after a reload is the quietest bug
 * this feature could have. Deriving the profile and the damage from the box means the piece cannot
 * disagree with itself: the same box always draws the same tower.
 *
 * <p><b>The profile.</b> A real cooling tower is a hyperboloid because that shape is a stack of
 * straight lines, so it can be built out of straight rods, and it narrows at a throat about three
 * quarters of the way up. Here that is {@code r = throat * sqrt(1 + (dy/c)^2)}, with {@code c} solved
 * so the curve passes through the base radius at the ground. Solving for the constant rather than
 * picking one means the tower is the same shape whatever height it rolled.
 *
 * <p><b>The tolerance is not half a block.</b> A ring drawn as "within half a block of the radius"
 * leaves holes wherever the wall is steep, because the radius moves more than a block between one
 * layer and the next. The test widens with the local slope, which closes the seam without thickening
 * the wall where it is nearly vertical.
 */
public class CoolingTowerPiece extends StructurePiece {

    /**
     * Where the wall is narrowest, as a fraction of total height.
     *
     * <p><b>0.60, not the 0.75 a real tower uses, and that is deliberate.</b> The flare above the
     * throat is what says cooling tower rather than chimney, and its size depends on how much height
     * is left above the throat: at 0.72 the top came out only a tenth wider than the waist, which at
     * block resolution is invisible, and the first one generated read as a rook. Dropping the throat
     * leaves room for the top to open out to about a third wider than the waist, which is legible from
     * the distance this structure exists to be seen from.
     */
    private static final double THROAT_FRACTION = 0.60;

    /** Throat radius as a fraction of the base radius. */
    private static final double THROAT_RATIO = 0.62;

    /** How tall the open colonnade at the foot is. This is also the way in. */
    private static final int LEG_HEIGHT = 6;

    /** How many rows at the top are eaten away. */
    private static final int RAGGED_ROWS = 6;

    /** How far up the interior is cleared. Mounds are a few blocks tall; above that it is already air. */
    private static final int CLEAR_HEIGHT = 10;

    public CoolingTowerPiece(RandomSource random, int x, int base, int z) {
        super(RCStructures.COOLING_TOWER_SHELL.get(), 0, boxFor(random, x, base, z));
    }

    public CoolingTowerPiece(CompoundTag tag) {
        super(RCStructures.COOLING_TOWER_SHELL.get(), tag);
    }

    /**
     * 62 to 76 tall, with the base radius a fixed share of the height.
     *
     * <p>The height range is the decision from #307: tall enough to clear the horizon from the next
     * region, since this world's terrain sits flat at about y 66. The radius follows the height rather
     * than rolling separately, because a hyperboloid that is short and fat stops reading as a cooling
     * tower, and the silhouette is the entire point of the structure.
     */
    private static BoundingBox boxFor(RandomSource random, int x, int base, int z) {
        int height = 62 + random.nextInt(15);
        int radius = (int) Math.round(height * 0.24);
        return new BoundingBox(x - radius, base, z - radius,
                               x + radius, base + height - 1, z + radius);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        // Nothing. The box is the whole state - see the class note.
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
            RandomSource random, BoundingBox limit, ChunkPos chunk, BlockPos origin) {
        BoundingBox box = this.boundingBox;
        int cx = (box.minX() + box.maxX()) / 2;
        int cz = (box.minZ() + box.maxZ()) / 2;
        int baseY = box.minY();
        int height = box.maxY() - box.minY() + 1;
        double baseRadius = (box.maxX() - box.minX()) / 2.0;
        BlockState shell = RCBlocks.REINFORCED_CONCRETE.get().defaultBlockState();

        // Deterministic per tower, and derived from where it stands rather than from the generation
        // RandomSource: that source is not the same one on a reload, and damage that moved would show
        // up as the wall changing shape when a chunk came back.
        RandomSource shape = RandomSource.create(cx * 341873128712L + cz * 132897987541L);
        double tearAngle = shape.nextDouble() * Math.PI * 2;
        double tearSpan = 0.45 + shape.nextDouble() * 0.25;
        int tearBottom = (int) (height * (0.22 + shape.nextDouble() * 0.12));
        int tearTop = tearBottom + 6 + shape.nextInt(6);

        for (int t = 0; t < height; t++) {
            int y = baseY + t;
            double r = radiusAt(t, height, baseRadius);
            double slope = Math.abs(radiusAt(t + 1, height, baseRadius) - r);
            double tolerance = 0.5 + slope / 2.0;
            int span = (int) Math.ceil(baseRadius) + 1;

            for (int dx = -span; dx <= span; dx++) {
                for (int dz = -span; dz <= span; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (Math.abs(d - r) > tolerance) {
                        continue;
                    }
                    if (!standsHere(t, height, dx, dz, tearAngle, tearSpan, tearBottom, tearTop,
                            cx + dx, y, cz + dz)) {
                        continue;
                    }
                    put(level, limit, cx + dx, y, cz + dz, shell);
                }
            }
        }

        clearInterior(level, limit, cx, cz, baseY, height, baseRadius);
        silt(level, limit, cx, cz, baseY, baseRadius);
    }

    /**
     * {@code r = throat * sqrt(1 + (dy/c)^2)}, with c solved so r(0) is the base radius.
     *
     * <p>Package-private so {@code CoolingTowerProfileTest} can measure the silhouette. The shape is
     * the entire point of this structure and it is pure arithmetic, so it belongs in a unit test
     * rather than in a screenshot.
     */
    static double radiusAt(int t, int height, double baseRadius) {
        double throat = baseRadius * THROAT_RATIO;
        double throatY = THROAT_FRACTION * (height - 1);
        double c = throatY / Math.sqrt(Math.pow(baseRadius / throat, 2) - 1);
        double dy = t - throatY;
        return throat * Math.sqrt(1 + (dy / c) * (dy / c));
    }

    /** Whether the shell stands at this point, once the legs, the tear and the ragged top are applied. */
    private static boolean standsHere(int t, int height, int dx, int dz, double tearAngle, double tearSpan,
            int tearBottom, int tearTop, int worldX, int worldY, int worldZ) {
        double angle = Math.atan2(dz, dx);

        // THE LEGS. A real tower stands on an open colonnade, and here that is also the door, which is
        // why the tear is decoration rather than the way in. Twelve piers with gaps between them.
        if (t < LEG_HEIGHT) {
            double turns = (angle + Math.PI) / (Math.PI * 2);
            return ((int) (turns * 24)) % 2 == 0;
        }

        // THE TEAR. One vertical rip, so the inside is visible from outside, which is what makes
        // somebody walk to it rather than past it.
        if (t >= tearBottom && t <= tearTop) {
            double delta = Math.abs(Math.atan2(Math.sin(angle - tearAngle), Math.cos(angle - tearAngle)));
            if (delta < tearSpan) {
                return false;
            }
        }

        // THE RAGGED TOP. Weathering eats the rim, and more of it the higher you go.
        int fromTop = height - 1 - t;
        if (fromTop < RAGGED_ROWS) {
            double bite = (RAGGED_ROWS - fromTop) / (double) (RAGGED_ROWS + 1);
            return hash(worldX, worldY, worldZ) > bite;
        }
        return true;
    }

    /**
     * Clear whatever the terrain left standing inside the footprint.
     *
     * <p>Only the first {@link #CLEAR_HEIGHT} blocks. A mound is a few blocks tall and everything above
     * that is already air, so clearing the full column would write tens of thousands of blocks to no
     * effect.
     */
    private void clearInterior(WorldGenLevel level, BoundingBox limit, int cx, int cz, int baseY,
            int height, double baseRadius) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int t = 1; t <= Math.min(CLEAR_HEIGHT, height - 1); t++) {
            double r = radiusAt(t, height, baseRadius) - 1;
            int span = (int) Math.ceil(r);
            for (int dx = -span; dx <= span; dx++) {
                for (int dz = -span; dz <= span; dz++) {
                    if (Math.sqrt(dx * dx + dz * dz) <= r) {
                        put(level, limit, cx + dx, baseY + t, cz + dz, air);
                    }
                }
            }
        }
    }

    /**
     * The basin floor: silt and debris, and deliberately nothing worth taking.
     *
     * <p><b>Gravel and sand, never Mill Tailings.</b> Tailings are a {@code SortableBlock} with a pull
     * stream behind them, so a floor of those would be a payout, and the decision on #307 is that a
     * landmark pays nothing. It cannot be water either - the Rain Collector is the only water source in
     * this world - and it cannot be leachate, which is sprawl-only.
     */
    private void silt(WorldGenLevel level, BoundingBox limit, int cx, int cz, int baseY,
            double baseRadius) {
        int span = (int) Math.ceil(baseRadius);
        for (int dx = -span; dx <= span; dx++) {
            for (int dz = -span; dz <= span; dz++) {
                if (Math.sqrt(dx * dx + dz * dz) > baseRadius - 1) {
                    continue;
                }
                double roll = hash(cx + dx, baseY, cz + dz);
                BlockState floor = roll < 0.45 ? Blocks.GRAVEL.defaultBlockState()
                                 : roll < 0.85 ? Blocks.SAND.defaultBlockState()
                                               : Blocks.COARSE_DIRT.defaultBlockState();
                put(level, limit, cx + dx, baseY, cz + dz, floor);
            }
        }
    }

    /** A stable 0..1 from a position. Same block, same answer, on every regeneration. */
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

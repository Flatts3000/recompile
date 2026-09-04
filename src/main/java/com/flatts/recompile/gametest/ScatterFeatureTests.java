package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.worldgen.RegionBiomeSource;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCFeatures;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The features that scatter piles across a region: the tailings impoundment, the mycelium patch, the
 * rubble pile and the mechanical-waste heap, plus the one rule every scatter feature in this mod has to
 * obey.
 *
 * <p>The tailings impoundment's pure geometry is proven with no world by {@code TailingsImpoundmentTest}
 * and is deliberately not repeated here. What these cover is the half arithmetic cannot hold: that the
 * loop consuming those numbers actually reaches the world, that the guards fire on the ground they were
 * written for, and that nothing lands where the engine would silently reject it.
 *
 * <p><b>Every test that scatters wider than a plot gets its own Y band, and the bands are listed here so
 * the next one does not land on an occupied one.</b> The harness sites plots about a dozen blocks apart
 * and nothing written outside a plot is cleaned up between tests, so two wide builds at a common height
 * write through each other's neighbours. Spoken for elsewhere: 0/40/80/120/160/240 by the aquarium
 * builds (38 blocks wide, clearing 28 above) and 40/80/120 by the tire dumps (14 blocks out). This file
 * takes <b>200, 216 and 232</b> for the three tailings tests, whose footprint is 35 blocks across.
 * <b>Sixteen apart rather than eight, and the gap is set by the MEASUREMENT window rather than by the
 * build.</b> A pile is about six tall, so eight looked ample; the drums test scans ten blocks to catch
 * a drum sitting on the skirt, and at eight it was reading the next band's drums as its own. It passed
 * on registration order alone.
 *
 * <p>The mycelium and yard-pile tests take no band at all, on purpose: they reach at most three blocks
 * from their origin and plots are a dozen apart, so no number of them can touch a neighbour. Lifting
 * those would be cargo cult rather than hygiene.
 */
final class ScatterFeatureTests {

    private ScatterFeatureTests() {
    }

    /**
     * The class the aquarium's guard lives in, in the internal form a constant pool stores it as. A
     * class that calls {@code AquariumStructure.claims} carries this string in its own constant pool;
     * one that does not, cannot.
     */
    private static final String AQUARIUM_CLASS =
        "com/flatts/recompile/content/worldgen/aquarium/AquariumStructure";

    /** The guard's method name, which lands in the constant pool beside the class it is called on. */
    private static final String CLAIMS_METHOD = "claims";

    /** How far the narrow tests reach: three blocks of pile plus a block of margin. */
    private static final int NEAR = 4;

    /**
     * A class's own bytes. Asked for twice - relative to the class, then absolutely off its loader -
     * because NeoForge loads mod classes through a transforming loader and only one of the two routes
     * is guaranteed to serve a resource from a module path. Null means neither worked, which the sweep
     * treats as a failure rather than as a pass: a reader that cannot read proves nothing.
     */
    private static byte[] bytecodeOf(Class<?> type) {
        String absolute = type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            if (in != null) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            // fall through to the loader
        }
        ClassLoader loader = type.getClassLoader();
        if (loader == null) {
            return null;
        }
        try (InputStream in = loader.getResourceAsStream(absolute)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Whether a class file mentions a name. ISO_8859_1 maps every byte to one char, so a constant-pool
     * UTF8 entry holding ASCII survives the decode intact and a plain substring search finds it.
     */
    private static boolean mentions(byte[] bytecode, String name) {
        return new String(bytecode, StandardCharsets.ISO_8859_1).contains(name);
    }

    private static String idOf(Block block) {
        return String.valueOf(BuiltInRegistries.BLOCK.getKey(block));
    }

    private static boolean place(ServerLevel level, Supplier<Feature<NoneFeatureConfiguration>> feature,
            BlockPos origin, long seed) {
        return feature.get().place(new FeaturePlaceContext<>(
            Optional.empty(), level, level.getChunkSource().getGenerator(),
            RandomSource.create(seed), origin, NoneFeatureConfiguration.INSTANCE));
    }

    /** Lay a flat field of one block one course below the origin, out to {@code reach} in each direction. */
    private static void layField(ServerLevel level, BlockPos origin, Block ground, int reach) {
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                level.setBlock(origin.offset(dx, -1, dz), ground.defaultBlockState(), 2);
            }
        }
    }

    /** The tallest offset a pile reached and how far out it reached; -1 for each if it placed none. */
    private record Pile(int top, int radius) {
    }

    /**
     * Clear a box, run one of the yard's two pile features into it, and measure what came out. Both
     * features write into air only and neither looks for ground, so an empty box is all they need.
     */
    private static Pile layPile(ServerLevel level, Supplier<Feature<NoneFeatureConfiguration>> feature,
            BlockPos origin, Block made, long seed) {
        for (int dx = -NEAR; dx <= NEAR; dx++) {
            for (int dz = -NEAR; dz <= NEAR; dz++) {
                for (int dy = 0; dy <= 8; dy++) {
                    level.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        place(level, feature, origin, seed);

        int top = -1;
        int radius = -1;
        for (int dx = -NEAR; dx <= NEAR; dx++) {
            for (int dz = -NEAR; dz <= NEAR; dz++) {
                for (int dy = 0; dy <= 8; dy++) {
                    if (level.getBlockState(origin.offset(dx, dy, dz)).is(made)) {
                        top = Math.max(top, dy);
                        radius = Math.max(radius, Math.max(Math.abs(dx), Math.abs(dz)));
                    }
                }
            }
        }
        return new Pile(top, radius);
    }

    static void register() {

        // THE AQUARIUM'S CLAIM, SWEPT OVER THE REGISTRY.
        //
        // Delete this and the next scatter feature somebody writes generates INSIDE the Municipal
        // Aquarium: a tailings heap through the guardian tank, a mound filling the lobby, a tire dump in
        // the filtration hall. The building is still there and still walkable, so nothing errors and
        // nothing crashes - it just has garbage growing through it, in the one structure the region was
        // built around.
        //
        // The guard's true branch cannot be reached from this harness at all: claims early-returns false
        // unless the level is a WorldGenRegion, and a GameTest hands a feature a ServerLevel. So this
        // asserts the CALL rather than its result, by reading each feature's own class file and looking
        // for the guard in its constant pool. Derived from the registry, never from a list of names,
        // because a list of names is the exact failure this repo keeps recording: it reads as complete
        // and the tenth entry is simply absent from it.
        //
        // If a feature is ever added that genuinely cannot generate near the yard - a Nether-only one,
        // say - it needs an exemption written down here with its reason, not a loosened check. And if a
        // feature ever reaches the guard through a shared helper instead of calling it directly, this
        // fails loudly and wants updating rather than deleting.
        RCGameTests.test("every_scatter_feature_asks_the_aquarium_first", 20, helper -> {
            List<Feature<?>> mine = BuiltInRegistries.FEATURE.stream()
                .filter(f -> {
                    var id = BuiltInRegistries.FEATURE.getKey(f);
                    return id != null && Recompile.MOD_ID.equals(id.getNamespace());
                })
                .toList();
            helper.assertTrue(mine.size() >= 9,
                "only " + mine.size() + " recompile features found - discovery is broken, so this test "
                    + "would pass by checking nothing");

            var unguarded = new TreeSet<String>();
            var unreadable = new TreeSet<String>();
            for (Feature<?> feature : mine) {
                String id = String.valueOf(BuiltInRegistries.FEATURE.getKey(feature));
                byte[] bytecode = bytecodeOf(feature.getClass());
                if (bytecode == null) {
                    unreadable.add(id);
                } else if (!mentions(bytecode, AQUARIUM_CLASS) || !mentions(bytecode, CLAIMS_METHOD)) {
                    unguarded.add(id);
                }
            }
            helper.assertTrue(unreadable.isEmpty(),
                "could not read the class bytes for " + unreadable + ", so nothing was proven about "
                    + "them. Fix the reader rather than dropping the check.");
            helper.assertTrue(unguarded.isEmpty(),
                "these features never call AquariumStructure.claims, so they will scatter blocks through "
                    + "the Municipal Aquarium: " + unguarded);

            // AND THE DETECTOR CAN SAY NO. Without this the sweep passes on a reader that finds every
            // string it is asked for, which is what a broken decode looks like - it would report nine
            // guarded features on a mod with none. RegionBiomeSource is the control: it is worldgen, it
            // lives in the same package tree, and it has no business asking about a building.
            byte[] control = bytecodeOf(RegionBiomeSource.class);
            helper.assertTrue(control != null, "could not read RegionBiomeSource, so the control is void");
            helper.assertTrue(!mentions(control, AQUARIUM_CLASS),
                "the control class reports calling the aquarium guard, so the reader matches anything "
                    + "and the sweep above means nothing");
            helper.succeed();
        });

        // THE IMPOUNDMENT ACTUALLY REACHES THE WORLD - plateau, decant pond, stain and all.
        //
        // Every number in this feature is proven with no world by TailingsImpoundmentTest, and every one
        // of them was right on the day it shipped a field of cupcakes. Arithmetic cannot see whether the
        // loop consuming it ever writes a block. Delete this and the radioactive dump can come out as
        // bare coarse dirt - no tailings, no water, no contamination anywhere in it - with the whole
        // JUnit layer still green, and Mill Tailings is the only uranium in the game.
        //
        // Band 200. See the class note: the footprint is 35 blocks across, far wider than a plot.
        RCGameTests.test("a_tailings_impoundment_builds_a_plateau_a_pond_and_a_stain", 100, helper -> {
            ServerLevel level = helper.getLevel();
            final int floor = 200;
            BlockPos origin = helper.absolutePos(new BlockPos(2, floor + 1, 2));
            // One ring wider than the feature's own loop, so the escape check below has ground to read.
            layField(level, origin, Blocks.COARSE_DIRT, 17);

            boolean placed = place(level, RCFeatures.TAILINGS_HEAP, origin, 5L);
            helper.assertTrue(placed,
                "the impoundment refused a flat field of coarse dirt, which is the region's own surface "
                    + "- so it refuses everywhere");
            helper.assertTrue(level.getBlockState(origin).is(RCBlocks.MILL_TAILINGS.get()),
                "the feature reported success and wrote nothing at its own origin, so whatever it built "
                    + "is somewhere nobody asked for");

            int tallest = -1;
            int rims = 0;
            int stains = 0;
            int stainsPastTheToe = 0;
            List<Integer> pondLevels = new ArrayList<>();
            for (int dx = -16; dx <= 16; dx++) {
                for (int dz = -16; dz <= 16; dz++) {
                    int top = -1;
                    for (int dy = 0; dy <= 6; dy++) {
                        BlockState state = level.getBlockState(origin.offset(dx, dy, dz));
                        if (state.is(RCBlocks.MILL_TAILINGS.get())) {
                            top = dy;
                        } else if (state.is(Blocks.WATER)) {
                            pondLevels.add(dy);
                        }
                    }
                    tallest = Math.max(tallest, top);
                    if (top == 0) {
                        rims++;
                    }
                    if (level.getBlockState(origin.offset(dx, -1, dz)).is(RCBlocks.STAINED_GROUND.get())) {
                        stains++;
                        if (top < 0) {
                            stainsPastTheToe++;
                        }
                    }
                }
            }

            // A PLATEAU WITH A SKIRT. A pile that came out one course thick everywhere would still be
            // the right material in the right place and would read as a smear rather than a landform;
            // one with no rim would be a cylinder with a cliff for a side.
            helper.assertTrue(tallest >= 2,
                "the tallest column is " + tallest + " blocks up, so this is a smear rather than an "
                    + "impoundment");
            helper.assertTrue(rims > 0,
                "no column came out one block tall, so the skirt never ramped down and the pile ends in "
                    + "a cliff");

            // THE DECANT POND, AND IT HAS TO BE FLAT. Every radius and height this feature can draw
            // leaves plateau enough for one, so a dry pile is a defect rather than an unlucky roll -
            // that is the exact shape of the bug a census of a real world found (correct tailings,
            // correct stain, correct drums, zero water). And level, because these are source blocks: a
            // pond cut at two heights pours off the lower rim the first time it ticks.
            helper.assertTrue(!pondLevels.isEmpty(),
                "the impoundment came out dry, and no combination of its own radius and height can "
                    + "produce that");
            helper.assertTrue(pondLevels.stream().distinct().count() == 1,
                "the pond sits at more than one height "
                    + pondLevels.stream().distinct().sorted().toList()
                    + ", so it is stepped and drains off the low side");

            // THE STAIN, AND SPECIFICALLY THE RING PAST THE TOE. Contamination stopping exactly where
            // the blocks stand would give the pile a tidy edge, which is the one thing a dump is not.
            helper.assertTrue(stains > 0,
                "nothing was stained, so the impoundment is sitting on clean ground");
            helper.assertTrue(stainsPastTheToe > 0,
                "every stained cell has tailings standing on it, so the stain never reached past the toe");

            // AND NOTHING WAS WRITTEN PAST THE WINDOW. Past 16 blocks the engine REJECTS the write and
            // logs an ERROR per block through Util.logAndPauseIfInIde, which also pauses under a
            // debugger; in a real world the pile comes out sheared flat along a chunk boundary. The
            // arithmetic behind the limit is pinned by TailingsImpoundmentTest; this proves the loop
            // that consumes it stops where the arithmetic says.
            List<String> escaped = new ArrayList<>();
            for (int dx = -17; dx <= 17; dx++) {
                for (int dz = -17; dz <= 17; dz++) {
                    if (Math.abs(dx) != 17 && Math.abs(dz) != 17) {
                        continue;
                    }
                    // MEASURE THE FEATURE'S OWN BLOCKS, not a pristine cell. The contract is that
                    // THIS feature writes nothing past MAX_REACH; "nothing else touched this cell" is
                    // a different and much stronger claim, and a false one - a 35-wide field reaches
                    // well into the neighbouring test plots, which are about a dozen blocks apart and
                    // are never cleaned up between runs. The first version asserted the strong form
                    // and failed on an arc of thirteen cells in one quadrant, which is what a
                    // neighbour looks like and not what an escaped write looks like.
                    for (int dy = -1; dy <= 6; dy++) {
                        BlockState out = level.getBlockState(origin.offset(dx, dy, dz));
                        if (out.is(RCBlocks.MILL_TAILINGS.get())
                                || out.is(RCBlocks.STAINED_GROUND.get())
                                || out.is(RCBlocks.WASTE_DRUM.get())) {
                            escaped.add(dx + "," + dz + " (" + out.getBlock() + ")");
                        }
                    }
                }
            }
            helper.assertTrue(escaped.isEmpty(),
                "the impoundment wrote 17 blocks out, past the window ChunkStatus.FEATURES allows: "
                    + escaped);
            helper.succeed();
        });

        // DRUMS LAND AT THE TOE AND STAND ON SOMETHING.
        //
        // Delete this and the drums can go back to where the first version of this feature put them: one
        // perched on the centre spire of every pile, which is the single tell that made the whole region
        // read as decorated cake. A drum in the wrong place fails nothing, logs nothing, and comes out
        // identical in a block census - it can only be seen.
        //
        // Band 216. See the class note.
        RCGameTests.test("tailings_drums_land_at_the_toe_and_never_on_the_summit", 100, helper -> {
            ServerLevel level = helper.getLevel();
            // BAND 216, NOT 208. Eight blocks of separation is not enough for THIS test: it scans
            // dy -1..8, a ten-block window, and the neighbouring stain test drops Waste Drums one
            // above its own field. At 208 this test's window reached 217 and the stain test's drums
            // sat at 217, horizontally inside this sweep because plots are about ten blocks apart.
            // It passed only because the three run in registration order inside one batch tick;
            // adding a fourth tailings test or letting the batch split would have broken it.
            final int floor = 216;
            BlockPos origin = helper.absolutePos(new BlockPos(2, floor + 1, 2));
            layField(level, origin, Blocks.COARSE_DIRT, 17);

            helper.assertTrue(place(level, RCFeatures.TAILINGS_HEAP, origin, 12L),
                "the impoundment refused the field, so there are no drums to judge");

            int drums = 0;
            List<String> onTheSummit = new ArrayList<>();
            List<String> floating = new ArrayList<>();
            List<String> escaped = new ArrayList<>();
            for (int dx = -17; dx <= 17; dx++) {
                for (int dz = -17; dz <= 17; dz++) {
                    for (int dy = -1; dy <= 8; dy++) {
                        BlockPos at = origin.offset(dx, dy, dz);
                        if (!level.getBlockState(at).is(RCBlocks.WASTE_DRUM.get())) {
                            continue;
                        }
                        drums++;
                        double dist = Math.sqrt((double) dx * dx + (double) dz * dz);
                        // The narrowest outline this feature can draw is radius 9 cut to about 7.1 by
                        // the lobes, and drums are thrown to a band straddling that toe, so the closest
                        // one arithmetic allows is about 5.4 out. Anything inside 5 is on the pile.
                        if (dist < 5.0) {
                            onTheSummit.add(at.toShortString() + " at " + Math.round(dist));
                        }
                        if (Math.abs(dx) > 16 || Math.abs(dz) > 16) {
                            escaped.add(at.toShortString());
                        }
                        if (level.getBlockState(at.below()).isAir()) {
                            floating.add(at.toShortString());
                        }
                    }
                }
            }

            helper.assertTrue(drums >= 1,
                "no drums at all. The feature always draws at least one cluster of at least two, and "
                    + "every position it picks is inside the writable window, so none means scatterDrums "
                    + "never ran.");
            helper.assertTrue(onTheSummit.isEmpty(),
                "these drums are on the pile rather than at its toe, which is the cupcake-and-candle "
                    + "silhouette the reshape existed to kill: " + onTheSummit);
            helper.assertTrue(escaped.isEmpty(),
                "these drums were thrown past the 16-block write window, so in a real world they are "
                    + "rejected and logged at ERROR: " + escaped);
            helper.assertTrue(floating.isEmpty(),
                "these drums have air under them. A drum is placed on the first air above the local "
                    + "surface, so one hanging means the surface hunt missed: " + floating);
            helper.succeed();
        });

        // THE STAIN NEVER EATS A NEIGHBOUR'S TAILINGS.
        //
        // Piles are placed several to a region and land on one another; when one does, its origin is
        // pushed up onto the earlier pile and its whole stain disc covers that neighbour. Without the
        // ground check the disc converts Mill Tailings into Stained Ground - and Stained Ground has no
        // pull stream, so the only uranium in the game turns into dressing that yields nothing, one disc
        // at a time. Caught in review of #286 rather than in play, because a stained pile looks like a
        // stained pile.
        //
        // Paired with the landform test above, which proves the stain DOES paint coarse dirt. Alone this
        // one would pass just as happily against a feature that had stopped staining anything at all.
        //
        // Band 216. See the class note.
        RCGameTests.test("a_tailings_stain_never_eats_a_neighbours_tailings", 100, helper -> {
            ServerLevel level = helper.getLevel();
            // BAND 232. The drums test below it now measures up to 225, so eight is no longer
            // clear of it; sixteen is.
            final int floor = 232;
            BlockPos origin = helper.absolutePos(new BlockPos(2, floor + 1, 2));
            // The field IS the neighbour: a flat sheet of the block an earlier pile would have left.
            layField(level, origin, RCBlocks.MILL_TAILINGS.get(), 17);

            place(level, RCFeatures.TAILINGS_HEAP, origin, 21L);

            List<String> eaten = new ArrayList<>();
            for (int dx = -16; dx <= 16; dx++) {
                for (int dz = -16; dz <= 16; dz++) {
                    if (level.getBlockState(origin.offset(dx, -1, dz)).is(RCBlocks.STAINED_GROUND.get())) {
                        eaten.add(dx + "," + dz);
                    }
                }
            }
            helper.assertTrue(eaten.isEmpty(),
                "the stain converted a neighbour's Mill Tailings into Stained Ground at " + eaten
                    + ", which deletes the region's only uranium source wherever two piles overlap");
            helper.succeed();
        });

        // MYCELIUM GREENS THE GROUND IT IS MEANT TO AND NOTHING ELSE.
        //
        // Three grounds in one test, because the interesting claim is the difference between them. The
        // two failures are opposite and both silent: a patch that converts garbage hollows the side out
        // of a mound, and a patch that converts MOUND GROUND deletes that mound's regrowth memory - the
        // count of blocks belonging on the column lives in the blockstate, so overwriting it retires the
        // mound permanently and the plain around it quietly stops replenishing. Mound Ground is kept out
        // of #minecraft:dirt for exactly this reason, and a tag is a thing somebody edits.
        //
        // No band: the patch reaches three blocks and plots are a dozen apart. See the class note.
        RCGameTests.test("a_mycelium_patch_greens_dirt_and_refuses_a_mound", 40, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
            final int cells = (2 * 3 + 1) * (2 * 3 + 1);

            record Ground(Block block, boolean converts, String why) {
            }
            List<Ground> grounds = List.of(
                new Ground(Blocks.COARSE_DIRT, true,
                    "coarse dirt is the world's surface, so a patch that refuses it never appears anywhere"),
                new Ground(RCBlocks.GARBAGE_BLOCK.get(), false,
                    "a patch that converts garbage hollows the side out of a mound"),
                new Ground(RCBlocks.MOUND_GROUND.get(), false,
                    "a patch that converts Mound Ground deletes the column count that mound regrows "
                        + "from, and it never comes back"));

            for (Ground ground : grounds) {
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        level.setBlock(origin.offset(dx, -1, dz), ground.block().defaultBlockState(), 2);
                        level.setBlock(origin.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 2);
                        level.setBlock(origin.offset(dx, 1, dz), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                boolean placed = place(level, RCFeatures.MYCELIUM_PATCH, origin, 3L);

                int mycelium = 0;
                int survived = 0;
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        BlockState state = level.getBlockState(origin.offset(dx, -1, dz));
                        if (state.is(Blocks.MYCELIUM)) {
                            mycelium++;
                        } else if (state.is(ground.block())) {
                            survived++;
                        }
                    }
                }

                if (ground.converts()) {
                    helper.assertTrue(placed && mycelium > 0,
                        "no mycelium on " + idOf(ground.block()) + ": " + ground.why());
                } else {
                    helper.assertTrue(!placed && mycelium == 0,
                        "mycelium replaced " + idOf(ground.block()) + " in " + mycelium + " cells: "
                            + ground.why());
                    helper.assertTrue(survived == cells,
                        "only " + survived + " of " + cells + " cells of " + idOf(ground.block())
                            + " survived the patch: " + ground.why());
                }
            }

            // AND THE MUSHROOMS GET PLANTED. The mycelium is only a substrate; the dump mushroom on top
            // of it is the P1.9 forage economy, and household_sprawl has every spawner list empty on
            // purpose, so this is where the first food in the game comes from. A patch that laid
            // mycelium and no mushroom looks correct from ten blocks away and starves the opening hours.
            //
            // Twelve patches rather than one because the per-cell chance is 0.15 and the radius is
            // rolled; the seeds are fixed, so this is deterministic rather than flaky.
            int mushrooms = 0;
            for (int seed = 0; seed < 12 && mushrooms == 0; seed++) {
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        level.setBlock(origin.offset(dx, -1, dz), Blocks.COARSE_DIRT.defaultBlockState(), 2);
                        level.setBlock(origin.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                place(level, RCFeatures.MYCELIUM_PATCH, origin, 100L + seed);
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        BlockPos above = origin.offset(dx, 0, dz);
                        if (!level.getBlockState(above).is(RCBlocks.DUMP_MUSHROOM.get())) {
                            continue;
                        }
                        mushrooms++;
                        helper.assertTrue(level.getBlockState(above.below()).is(Blocks.MYCELIUM),
                            "a dump mushroom at " + above.toShortString() + " is standing on "
                                + idOf(level.getBlockState(above.below()).getBlock())
                                + " rather than on mycelium, so it pops the first time it is ticked");
                    }
                }
            }
            helper.assertTrue(mushrooms > 0,
                "twelve patches in a row grew no dump mushroom. The starting biome spawns no creatures, "
                    + "so this is the only food in the opening hours.");
            helper.succeed();
        });

        // AND IT REFUSES GROUND THAT IS COVERED.
        //
        // Mycelium dies in the dark and reverts to dirt. Under a mound overhang that means a patch is
        // laid, is never seen, and quietly turns back - so the feature spends one of its per-chunk
        // placements on nothing and the surface ends up with fewer patches than the count says, which is
        // a shortage of the only food source with nothing anywhere to point at.
        //
        // Paired with its own opposite below: take the cover away and the identical call must convert,
        // or this would pass against a feature that had stopped placing anything at all.
        //
        // No band: reaches three blocks. See the class note.
        RCGameTests.test("a_mycelium_patch_never_greens_covered_ground", 40, helper -> {
            ServerLevel level = helper.getLevel();
            // The origin sits two below the ground on purpose. The patch hunts DOWN from two above its
            // origin for the first non-air block, so an origin level with the ground would stop ON the
            // cover and be refused for not being dirt - a different guard, and not the one this is
            // about. From here the hunt lands on the dirt itself with the cover still overhead, which is
            // the branch that decides whether a roofed patch is skipped.
            BlockPos ground = helper.absolutePos(new BlockPos(2, 3, 2));
            BlockPos origin = ground.below(2);

            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    level.setBlock(ground.offset(dx, 0, dz), Blocks.COARSE_DIRT.defaultBlockState(), 2);
                    level.setBlock(ground.offset(dx, 1, dz), Blocks.STONE.defaultBlockState(), 2);
                }
            }
            boolean covered = place(level, RCFeatures.MYCELIUM_PATCH, origin, 8L);
            int under = 0;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (level.getBlockState(ground.offset(dx, 0, dz)).is(Blocks.MYCELIUM)) {
                        under++;
                    }
                }
            }
            helper.assertTrue(!covered && under == 0,
                "the patch greened " + under + " cells of roofed-over ground. Mycelium reverts to dirt "
                    + "in the dark, so those placements are spent and then silently undone.");

            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    level.setBlock(ground.offset(dx, 1, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
            helper.assertTrue(place(level, RCFeatures.MYCELIUM_PATCH, origin, 8L),
                "with the roof taken off, the identical call still placed nothing - so the refusal above "
                    + "was about something other than the cover and this test proves nothing");
            helper.succeed();
        });

        // THE YARD'S TWO PILES HAVE TO LOOK DIFFERENT FROM TEN BLOCKS AWAY.
        //
        // Rubble and Mechanical Waste generate in the same biome step, and one of them is the gem tier's
        // only found source while the other is the workaday stone. If they converge on one silhouette
        // the valuable one stops being findable: a player crosses the yard breaking rubble and never
        // learns there was a second kind of heap. Nothing fails - both features still place, both still
        // drop what they should - the region just goes flat.
        //
        // Measured over 24 seeds each because the two shapes are drawn from overlapping ranges, so one
        // pile of each proves nothing. The seeds are fixed, so this is deterministic rather than flaky.
        //
        // No band: neither pile reaches past three blocks. See the class note.
        RCGameTests.test("a_rubble_pile_spreads_and_a_waste_heap_stacks", 60, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));

            int rubbleTop = -1;
            int rubbleRadius = -1;
            int wasteTop = -1;
            int wasteRadius = -1;
            for (long seed = 0; seed < 24; seed++) {
                Pile rubble = layPile(level, RCFeatures.RUBBLE_PILE, origin,
                    RCBlocks.STONE_RUBBLE.get(), seed);
                rubbleTop = Math.max(rubbleTop, rubble.top());
                rubbleRadius = Math.max(rubbleRadius, rubble.radius());

                Pile waste = layPile(level, RCFeatures.MECHANICAL_WASTE_PILE, origin,
                    RCBlocks.MECHANICAL_WASTE.get(), seed);
                wasteTop = Math.max(wasteTop, waste.top());
                wasteRadius = Math.max(wasteRadius, waste.radius());
            }

            helper.assertTrue(rubbleTop >= 1,
                "twenty-four rubble piles produced a tallest column of " + rubbleTop + " - the yard's "
                    + "only bare-hand stone source is not generating");
            helper.assertTrue(wasteTop >= 1,
                "twenty-four waste heaps produced a tallest column of " + wasteTop + " - the gem tier "
                    + "has no found half");

            helper.assertTrue(rubbleRadius > wasteRadius,
                "rubble reached " + rubbleRadius + " blocks out against the waste heap's " + wasteRadius
                    + ". Rubble is debris SPREAD by demolition and has to be the wider of the two, or "
                    + "the two read as one kind of pile.");
            helper.assertTrue(wasteTop > rubbleTop,
                "the waste heap stacked " + wasteTop + " blocks against rubble's " + rubbleTop
                    + ". It is machinery that collapsed where it was stacked and has to be the taller of "
                    + "the two, which is what makes the valuable one visible across the yard.");
            helper.succeed();
        });

        // NEITHER YARD PILE EVER REPLACES WHAT IT LANDS ON.
        //
        // Both write into air only. Drop that and a pile whose origin lands on a mound overwrites Blocks
        // of Garbage with rubble - which is not a cosmetic swap, it deletes pulls out of a mound a
        // player may be part-way through sorting, during worldgen, where nobody is watching.
        //
        // Paired with its opposite in the same loop, because "placed nothing" is also what a feature
        // that has stopped working looks like.
        //
        // No band: reaches three blocks. See the class note.
        RCGameTests.test("neither_yard_pile_replaces_the_block_it_lands_on", 60, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));

            record Case(String name, Supplier<Feature<NoneFeatureConfiguration>> feature, Block made) {
            }
            List<Case> cases = List.of(
                new Case("rubble pile", RCFeatures.RUBBLE_PILE, RCBlocks.STONE_RUBBLE.get()),
                new Case("waste heap", RCFeatures.MECHANICAL_WASTE_PILE, RCBlocks.MECHANICAL_WASTE.get()));

            for (Case pile : cases) {
                // A solid block of garbage exactly where the pile wants to be.
                for (int dx = -NEAR; dx <= NEAR; dx++) {
                    for (int dz = -NEAR; dz <= NEAR; dz++) {
                        for (int dy = 0; dy <= 8; dy++) {
                            level.setBlock(origin.offset(dx, dy, dz),
                                RCBlocks.GARBAGE_BLOCK.get().defaultBlockState(), 2);
                        }
                    }
                }
                boolean placed = place(level, pile.feature(), origin, 6L);

                int eaten = 0;
                for (int dx = -NEAR; dx <= NEAR; dx++) {
                    for (int dz = -NEAR; dz <= NEAR; dz++) {
                        for (int dy = 0; dy <= 8; dy++) {
                            if (level.getBlockState(origin.offset(dx, dy, dz)).is(pile.made())) {
                                eaten++;
                            }
                        }
                    }
                }
                helper.assertTrue(!placed && eaten == 0,
                    "the " + pile.name() + " overwrote " + eaten + " Blocks of Garbage. A pile landing "
                        + "on a mound would delete pulls out of it during worldgen.");

                // The opposite, so the assertion above cannot pass on a feature that places nothing: the
                // identical call into clear air must build.
                Pile clear = layPile(level, pile.feature(), origin, pile.made(), 6L);
                helper.assertTrue(clear.top() >= 0,
                    "the " + pile.name() + " placed nothing into clear air either, so its refusal above "
                        + "was not about the ground being taken");
            }
            helper.succeed();
        });
    }
}

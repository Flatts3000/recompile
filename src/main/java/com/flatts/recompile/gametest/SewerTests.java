package com.flatts.recompile.gametest;

import com.flatts.recompile.content.worldgen.sewer.SewerPalette;
import com.flatts.recompile.content.worldgen.sewer.SewerPieces;
import com.flatts.recompile.content.worldgen.sewer.SewerStructure;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

/**
 * The sewer's shape and its palette (#90, phase 2).
 *
 * <p><b>The piece graph needs no world</b>, which is what makes it testable at all. Building the tree is
 * pure arithmetic over bounding boxes - {@code StructurePiecesBuilder} is an ordinary object and
 * {@code addChildren} touches nothing but it - so branching, descent and boundedness can all be asserted
 * here rather than by generating a world and going to look. What genuinely needs a world is what the
 * blocks look like, and that is a {@code runClient} job.
 *
 * <p>Every shape test runs over <b>many seeds</b>. One seed proves one sewer, and the interesting
 * failures are the rare ones: the sewer that happens to run in a straight line, or the one whose
 * stairwell chain descends further than anybody expected.
 */
final class SewerTests {

    /** Enough seeds that a one-in-a-hundred layout shows up rather than hiding until someone plays. */
    private static final int SEEDS = 200;

    private SewerTests() {
    }

    /** Build one sewer's piece tree, exactly as {@code SewerStructure} does, and hand back the pieces. */
    private static List<StructurePiece> layout(long seed) {
        RandomSource random = RandomSource.create(seed);
        StructurePiecesBuilder builder = new StructurePiecesBuilder();
        SewerPieces.SewerRoom room = new SewerPieces.SewerRoom(0, random, 0, 0);
        builder.addPiece(room);
        room.addChildren(room, builder, random);
        List<StructurePiece> pieces = new ArrayList<>();
        builder.build().pieces().forEach(pieces::add);
        return pieces;
    }

    static void register() {
        // THE PALETTE OPENS NO GATE. Phase 2's acceptance criterion, and it says explicitly that it must
        // be asserted by walking every block the structure can place rather than by reading the palette -
        // which is why SewerPalette.ALL exists as a list at all.
        //
        // The gate: anything in #minecraft:stone_crafting_materials crafts a vanilla furnace, and a
        // vanilla furnace skips the Cupola. Brick was checked by hand when the spec was written; this
        // checks it on every build, and covers the block somebody adds to the palette next year.
        RCGameTests.test("the_sewer_palette_opens_no_gate", 20, helper -> {
            List<String> offenders = new ArrayList<>();
            for (BlockState state : SewerPalette.ALL) {
                ItemStack item = new ItemStack(state.getBlock());
                if (!item.isEmpty() && item.is(ItemTags.STONE_CRAFTING_MATERIALS)) {
                    offenders.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                }
            }
            helper.assertTrue(SewerPalette.ALL.size() >= 4,
                "the sewer palette has only " + SewerPalette.ALL.size() + " entries - discovery is "
                    + "broken, so this would pass by checking almost nothing");
            helper.assertTrue(offenders.isEmpty(),
                "these sewer blocks craft a vanilla furnace, which skips the Cupola and opens the iron "
                    + "gate: " + offenders);
            helper.succeed();
        });

        // IT BRANCHES AND IT DESCENDS, which is the difference between a sewer and a corridor.
        //
        // Asserted as "most seeds", not "every seed": a layout whose first roll collides on all four
        // exits is legitimately a stub, and demanding every single one branch would make this flake on
        // whichever seed happens to be unlucky. The failure worth catching is systemic - a graph that
        // never branches, or never changes level - and a majority test catches that just as hard.
        RCGameTests.test("a_sewer_branches_and_descends", 20, helper -> {
            int branched = 0;
            int multiLevel = 0;
            for (long seed = 0; seed < SEEDS; seed++) {
                List<StructurePiece> pieces = layout(seed);
                if (pieces.size() > 3) {
                    branched++;
                }
                Set<Integer> floors = new HashSet<>();
                for (StructurePiece piece : pieces) {
                    floors.add(piece.getBoundingBox().minY());
                }
                if (floors.size() > 1) {
                    multiLevel++;
                }
            }
            helper.assertTrue(branched > SEEDS / 2,
                "only " + branched + " of " + SEEDS + " sewers grew past three pieces - the graph is "
                    + "not branching, so this is a corridor rather than the sprawl the structure is for");
            helper.assertTrue(multiLevel > SEEDS / 2,
                "only " + multiLevel + " of " + SEEDS + " sewers reached more than one level - without "
                    + "the vertical drift and the stairs a sewer is flat, and phase 2 asks for levels");
            helper.succeed();
        });

        // IT NEVER OPENS INTO THE VOID OR THE SURFACE, which are the same bug seen from two sides and
        // neither of which throws anything - one looks like a broken generator, the other like a broken
        // structure. The arithmetic is pure, so it is asserted here rather than by going to look.
        //
        // The case that mattered: clamping only the floor pushed a too-deep tree bodily upward until it
        // broke daylight. A tree spanning 0..57 under a surface at 65 came out at 12..69 - ten blocks of
        // corridor in the open air, in a structure whose whole premise is that you go down to it.
        RCGameTests.test("a_sewer_never_breaks_the_surface_or_the_void", 20, helper -> {
            List<String> bad = new ArrayList<>();
            // The real world: rock from about y=5 to a surface between 63 and 69, measured by
            // the_world_has_rock_enough_to_hold_a_sewer.
            for (int surface = 63; surface <= 69; surface++) {
                for (int treeMin = -40; treeMin <= 50; treeMin += 2) {
                    for (int height = 8; height <= 90; height += 2) {
                        int treeMax = treeMin + height;
                        var shift = SewerStructure.sink(surface, treeMin, treeMax);
                        if (shift.isEmpty()) {
                            continue;   // refusing to place is always a correct answer
                        }
                        int low = treeMin + shift.getAsInt();
                        int high = treeMax + shift.getAsInt();
                        if (high > surface - 6) {
                            bad.add("surface " + surface + " tree " + treeMin + ".." + treeMax
                                + " placed its roof at " + high + ", in the open air");
                        }
                        if (low < 12) {
                            bad.add("surface " + surface + " tree " + treeMin + ".." + treeMax
                                + " placed its floor at " + low + ", in the void");
                        }
                    }
                }
            }
            helper.assertTrue(bad.isEmpty(),
                "these sewers would generate outside the rock: "
                    + bad.subList(0, Math.min(4, bad.size())) + " (" + bad.size() + " total)");
            // And it must still say yes to the ordinary case, or the check above passes by refusing
            // everything - which would ship a structure that never generates at all.
            helper.assertTrue(SewerStructure.sink(65, 10, 57).isPresent(),
                "a sewer that fits comfortably in the rock was refused, so the guard above is passing "
                    + "by placing nothing anywhere");
            helper.succeed();
        });

        // THE GEOMETRY IS ACTUALLY BUILT, and this is the test the graph tests could not be.
        //
        // Every shape assertion above runs over StructurePiecesBuilder, which is bounding boxes and
        // nothing else - so all of them passed while postProcess was placing blocks in the wrong place.
        // Three bugs lived there at once and none of them threw: an east-west piece was carved
        // transposed (local X is world Z on that axis), the un-oriented room built itself at world
        // origin (a null orientation makes getWorldX return the LOCAL value as an absolute), and every
        // piece was shelled on all six faces so the sewer was a chain of sealed boxes you could not
        // walk between. An in-world look does not catch them either: it found brick at a plausible
        // depth and that was taken for a working sewer.
        //
        // EAST on purpose. North-south is the case where local and world axes agree, so it is the one
        // orientation that proves nothing.
        RCGameTests.test("a_corridor_is_carved_along_its_own_length", 40, helper -> {
            var level = helper.getLevel();
            BlockPos base = helper.absolutePos(new BlockPos(0, 24, 0));
            Direction facing = Direction.EAST;
            BoundingBox box = SewerPieces.SewerPiece.box(
                base.getX(), base.getY(), base.getZ(), facing, 5, 5, 7);
            var corridor = new SewerPieces.SewerCorridor(1, box, facing);
            BoundingBox limit = new BoundingBox(base.getX() - 32, base.getY() - 16, base.getZ() - 32,
                base.getX() + 48, base.getY() + 32, base.getZ() + 48);
            corridor.postProcess(level, level.structureManager(),
                level.getChunkSource().getGenerator(), RandomSource.create(7L),
                limit, new net.minecraft.world.level.ChunkPos(base.getX() >> 4, base.getZ() >> 4), base);

            // Seven long on X and five wide on Z is what makeBoundingBox produced; the carve must agree.
            helper.assertTrue(box.maxX() - box.minX() == 6 && box.maxZ() - box.minZ() == 4,
                "the corridor box is not 7 by 5, so this test cannot tell a transposed carve from a "
                    + "correct one: " + box);
            List<String> wrong = new ArrayList<>();
            // The interior must be open along the WHOLE seven-block run, at head height.
            for (int x = box.minX(); x <= box.maxX(); x++) {
                BlockPos at = new BlockPos(x, box.minY() + 2, box.minZ() + 2);
                if (!level.getBlockState(at).isAir()) {
                    wrong.add("solid at " + at + ", so the tunnel does not run its own length");
                }
            }
            // BOTH ENDS OPEN. This is what lets one piece connect to the next.
            for (int x : new int[]{box.minX(), box.maxX()}) {
                BlockPos at = new BlockPos(x, box.minY() + 2, box.minZ() + 2);
                if (!level.getBlockState(at).isAir()) {
                    wrong.add("end face sealed at " + at);
                }
            }
            // And it must NOT have spilled outside its own box, which a transposed carve does.
            for (int z = box.maxZ() + 1; z <= box.maxZ() + 3; z++) {
                BlockPos at = new BlockPos(box.minX() + 2, box.minY() + 2, z);
                if (level.getBlockState(at).is(net.minecraft.world.level.block.Blocks.BRICKS)) {
                    wrong.add("brick outside the box at " + at + ", so the carve is transposed");
                }
            }
            helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
            helper.succeed();
        });

        // AND THE ROOM LANDS WHERE ITS BOX IS, not at world origin.
        RCGameTests.test("the_root_room_is_built_at_its_own_bounding_box", 40, helper -> {
            var level = helper.getLevel();
            BlockPos base = helper.absolutePos(new BlockPos(0, 40, 0));
            var room = new SewerPieces.SewerRoom(0, RandomSource.create(3L), base.getX(), base.getZ());
            BoundingBox box = room.getBoundingBox();
            // The room builds at a fixed y=50 and is moved by the structure; shift it here the same way.
            int shift = base.getY() - box.minY();
            room.move(0, shift, 0);
            box = room.getBoundingBox();
            BoundingBox limit = new BoundingBox(box.minX() - 32, box.minY() - 16, box.minZ() - 32,
                box.maxX() + 32, box.maxY() + 32, box.maxZ() + 32);
            room.postProcess(level, level.structureManager(),
                level.getChunkSource().getGenerator(), RandomSource.create(3L),
                limit, new net.minecraft.world.level.ChunkPos(base.getX() >> 4, base.getZ() >> 4), base);

            BlockPos floor = new BlockPos(box.minX() + 1, box.minY(), box.minZ() + 1);
            helper.assertTrue(!level.getBlockState(floor).isAir(),
                "the room floor at " + floor + " is air - the chamber was not built inside its own box, "
                    + "which is what a null orientation does when it is handed local coordinates");
            BlockPos inside = new BlockPos(box.minX() + 2, box.minY() + 2, box.minZ() + 2);
            helper.assertTrue(level.getBlockState(inside).isAir(),
                "the room interior at " + inside + " is solid, so nothing was hollowed out");
            helper.succeed();
        });

        // IT GROWS ALL FOUR WAYS, which sounds obvious and was not true.
        //
        // Boxes were briefly built with vanilla's makeBoundingBox, which always extends in +x/+z from
        // the anchor because it exists for ROOT pieces. Chained north and west branches therefore
        // produced boxes running back INTO their parent, findCollisionPiece rejected them, and they
        // were dropped with no error - every sewer confined to one quadrant with half of every branch
        // roll wasted. Nothing caught it: a quadrant is smaller than the bound, not larger, so the
        // boundedness test was happy, and the branch count only asked for "more than three pieces".
        RCGameTests.test("a_sewer_grows_in_all_four_directions", 20, helper -> {
            boolean north = false;
            boolean south = false;
            boolean west = false;
            boolean east = false;
            for (long seed = 0; seed < SEEDS; seed++) {
                List<StructurePiece> pieces = layout(seed);
                BoundingBox root = pieces.get(0).getBoundingBox();
                for (StructurePiece piece : pieces) {
                    BoundingBox b = piece.getBoundingBox();
                    north |= b.minZ() < root.minZ();
                    south |= b.maxZ() > root.maxZ();
                    west |= b.minX() < root.minX();
                    east |= b.maxX() > root.maxX();
                }
            }
            helper.assertTrue(north && south && west && east,
                "over " + SEEDS + " sewers the branches only reached north=" + north + " south=" + south
                    + " west=" + west + " east=" + east + " - a sewer that can only grow into one "
                    + "quadrant is half a sewer, and every roll toward the missing side is discarded");
            helper.succeed();
        });

        // AND A TURN IS ACTUALLY CONNECTED TO WHAT IT TURNED OFF.
        //
        // Each piece walls its own two sides for its full length, and a child is anchored one block
        // past its parent - so a left or right branch began on the far side of a solid brick layer and
        // was reachable only by mining. Straight-ahead children were fine, which is exactly why the
        // corridor test missed it: that one probes a single piece end to end.
        RCGameTests.test("a_turn_opens_into_its_parent", 40, helper -> {
            var level = helper.getLevel();
            BlockPos base = helper.absolutePos(new BlockPos(0, 56, 0));
            var gen = level.getChunkSource().getGenerator();
            var mgr = level.structureManager();
            BoundingBox limit = new BoundingBox(base.getX() - 64, base.getY() - 32, base.getZ() - 64,
                base.getX() + 64, base.getY() + 32, base.getZ() + 64);
            var chunk = new net.minecraft.world.level.ChunkPos(base.getX() >> 4, base.getZ() >> 4);

            // A parent running SOUTH, then a child turning EAST off its side - the case that was sealed.
            BoundingBox parentBox = SewerPieces.SewerPiece.box(
                base.getX(), base.getY(), base.getZ(), Direction.SOUTH, 5, 5, 7);
            var parent = new SewerPieces.SewerCorridor(1, parentBox, Direction.SOUTH);
            BoundingBox childBox = SewerPieces.SewerPiece.box(
                parentBox.maxX() + 1, parentBox.minY(), parentBox.minZ(), Direction.EAST, 5, 5, 7);
            var child = new SewerPieces.SewerCorridor(2, childBox, Direction.EAST);

            // Parent first, exactly as the builder orders them - the child has the last word.
            parent.postProcess(level, mgr, gen, RandomSource.create(11L), limit, chunk, base);
            child.postProcess(level, mgr, gen, RandomSource.create(12L), limit, chunk, base);

            BlockPos doorway = new BlockPos(parentBox.maxX(), parentBox.minY() + 2, parentBox.minZ() + 2);
            helper.assertTrue(level.getBlockState(doorway).isAir(),
                "the wall between a corridor and the branch off its side is still solid at " + doorway
                    + ", so the branch is only reachable by mining - which is not a sewer, it is two "
                    + "tunnels that happen to touch");
            helper.succeed();
        });

        // THE SEWER IS OCCUPIED, and both halves of that needed a mechanism rather than a wish.
        //
        // spawn_overrides picks WHICH mobs a structure offers; it does not bypass SpawnPlacements, whose
        // per-type predicate still runs. Measured against 26.1: drowned are registered IN_WATER, which
        // tests FluidTags.WATER, and leachate is deliberately outside it - so natural spawning yields
        // none, ever. Turtles are worse: they want y < seaLevel + 4, and sea level here is -64.
        //
        // Drowned get a spawner, because checkDrownedSpawnRules has an explicit isSpawner branch that
        // skips the water test. Turtles get placed as entities, because their predicate has no such
        // branch - and that also makes them finite, which is what the sewer wants.
        RCGameTests.test("the_room_is_occupied_by_a_spawner_and_turtles", 40, helper -> {
            var level = helper.getLevel();
            BlockPos base = helper.absolutePos(new BlockPos(0, 44, 0));
            var room = new SewerPieces.SewerRoom(0, RandomSource.create(5L), base.getX(), base.getZ());
            room.move(0, base.getY() - room.getBoundingBox().minY(), 0);
            BoundingBox box = room.getBoundingBox();
            BoundingBox limit = new BoundingBox(box.minX() - 32, box.minY() - 16, box.minZ() - 32,
                box.maxX() + 32, box.maxY() + 32, box.maxZ() + 32);
            room.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(),
                RandomSource.create(5L), limit,
                new net.minecraft.world.level.ChunkPos(base.getX() >> 4, base.getZ() >> 4), base);

            BlockPos centre = new BlockPos(box.getCenter().getX(), box.minY() + 1, box.getCenter().getZ());
            helper.assertTrue(level.getBlockState(centre).is(net.minecraft.world.level.block.Blocks.SPAWNER),
                "no spawner in the root chamber at " + centre + " - without one the sewer has no "
                    + "drowned at all, because IN_WATER can never be satisfied by leachate");
            var be = level.getBlockEntity(centre);
            helper.assertTrue(be instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity,
                "the spawner block has no BlockEntity, so it holds nothing and spawns nothing");

            long turtles = level.getEntitiesOfClass(net.minecraft.world.entity.animal.turtle.Turtle.class,
                new net.minecraft.world.phys.AABB(
                    box.minX(), box.minY() - 2, box.minZ(),
                    box.maxX() + 1, box.maxY() + 2, box.maxZ() + 1)).size();
            helper.assertTrue(turtles >= 2,
                "found " + turtles + " turtles in the chamber - they cannot spawn in this world at all "
                    + "(sea level is -64 and there is no sand), so if the structure does not place them "
                    + "there are none anywhere");
            helper.succeed();
        });

        // IT IS BOUNDED. Two sewers must not merge and one must not run for a thousand blocks, so the
        // extent is checked on every seed rather than on average - this is the assertion where a single
        // outlier IS the bug.
        RCGameTests.test("a_sewer_is_bounded", 20, helper -> {
            List<String> runaway = new ArrayList<>();
            int worst = 0;
            for (long seed = 0; seed < SEEDS; seed++) {
                BoundingBox extent = null;
                for (StructurePiece piece : layout(seed)) {
                    extent = extent == null ? piece.getBoundingBox()
                        : BoundingBox.encapsulatingBoxes(List.of(extent, piece.getBoundingBox()))
                            .orElse(extent);
                }
                if (extent == null) {
                    continue;
                }
                int reach = Math.max(extent.maxX() - extent.minX(), extent.maxZ() - extent.minZ());
                worst = Math.max(worst, reach);
                // The cap is applied to each new opening, and a piece placed at the cap still has its
                // own length, so the true bound is the cap plus a piece. Generous room above that and
                // it is still an order of magnitude short of "a thousand blocks".
                if (reach > SewerPieces.RADIUS_CAP * 2 + 32) {
                    runaway.add("seed " + seed + " reaches " + reach);
                }
            }
            helper.assertTrue(runaway.isEmpty(),
                "these sewers ran past the bound, so two of them can meet and one can wander out of the "
                    + "region it belongs to: " + runaway);
            helper.assertTrue(worst > 16,
                "the widest of " + SEEDS + " sewers was only " + worst + " blocks across - that is not a "
                    + "bound holding, it is the graph failing to generate, and the check above would "
                    + "pass against nothing");
            helper.succeed();
        });
    }
}

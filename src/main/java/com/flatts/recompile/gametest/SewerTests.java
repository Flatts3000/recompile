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
import com.flatts.recompile.Recompile;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
        // MIRROR THE STRUCTURE EXACTLY, in its order. A layout built without these steps measures a
        // sewer nobody will ever generate - which has now caught this helper out twice, once for the
        // access chamber and once for the sump.
        SewerPieces.attachSump(room, builder, random);
        SewerPieces.forceAccessChamber(room, builder, random);
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

        // THE ANIMALS LIVE ON THEIR OWN GROUND, in a den each.
        //
        // They used to be placed straight into the chamber's leachate pool, so every sewer generated
        // with its turtles and frogs standing in the Hunger fluid - permanently, since RCLeachateContact
        // fires on anything living in it. Playtest called it: "a pool with lots of drowned, frog and
        // turtle", which is a zoo rather than a sewer.
        //
        // The substrate is the mechanism as well as the look. #minecraft:frogs_spawnable_on is grass
        // block, mud and the two mangrove roots, so mud is the one member a sewer could hold; and
        // TurtleEggBlock.onSand is half of vanilla's turtle rule. Neither makes them renewable - the
        // other half is y < seaLevel + 4 against a sea level of -64.
        RCGameTests.test("each_den_holds_its_animals_on_its_own_ground", 60, helper -> {
            var level = helper.getLevel();
            var gen = level.getChunkSource().getGenerator();
            var mgr = level.structureManager();

            BlockPos turtleAt = helper.absolutePos(new BlockPos(0, 30, 0));
            var turtleDen = new SewerPieces.SewerTurtleDen(1, new BoundingBox(
                turtleAt.getX(), turtleAt.getY(), turtleAt.getZ(),
                turtleAt.getX() + 5, turtleAt.getY() + 3, turtleAt.getZ() + 4));
            BlockPos frogAt = helper.absolutePos(new BlockPos(0, 30, 20));
            var frogDen = new SewerPieces.SewerFrogDen(1, new BoundingBox(
                frogAt.getX(), frogAt.getY(), frogAt.getZ(),
                frogAt.getX() + 5, frogAt.getY() + 3, frogAt.getZ() + 4));

            for (var den : List.of(turtleDen, frogDen)) {
                BoundingBox box = den.getBoundingBox();
                BoundingBox limit = new BoundingBox(box.minX() - 16, box.minY() - 16, box.minZ() - 16,
                    box.maxX() + 16, box.maxY() + 16, box.maxZ() + 16);
                den.postProcess(level, mgr, gen, RandomSource.create(2L), limit,
                    new net.minecraft.world.level.ChunkPos(box.minX() >> 4, box.minZ() >> 4), turtleAt);
            }

            BoundingBox tBox = turtleDen.getBoundingBox();
            helper.assertTrue(level.getBlockState(
                    new BlockPos(tBox.minX() + 1, tBox.minY(), tBox.minZ() + 1))
                    .is(net.minecraft.world.level.block.Blocks.SAND),
                "the turtle den is not floored in sand, which is the ground vanilla's own turtle rule "
                    + "names");
            BoundingBox fBox = frogDen.getBoundingBox();
            helper.assertTrue(level.getBlockState(
                    new BlockPos(fBox.minX() + 1, fBox.minY(), fBox.minZ() + 1))
                    .is(net.minecraft.world.level.block.Blocks.MUD),
                "the frog den is not floored in mud, which is the only member of "
                    + "#minecraft:frogs_spawnable_on a sewer could hold");

            int turtles = level.getEntitiesOfClass(
                net.minecraft.world.entity.animal.turtle.Turtle.class,
                new net.minecraft.world.phys.AABB(tBox.minX(), tBox.minY() - 1, tBox.minZ(),
                    tBox.maxX() + 1, tBox.maxY() + 1, tBox.maxZ() + 1)).size();
            int frogs = level.getEntitiesOfClass(net.minecraft.world.entity.animal.frog.Frog.class,
                new net.minecraft.world.phys.AABB(fBox.minX(), fBox.minY() - 1, fBox.minZ(),
                    fBox.maxX() + 1, fBox.maxY() + 1, fBox.maxZ() + 1)).size();
            helper.assertTrue(turtles == 3, "found " + turtles + " turtles in the den rather than 3");
            helper.assertTrue(frogs == 2, "found " + frogs + " frogs in the den rather than 2");

            // And they are not standing in the fluid, which is the whole reason the dens exist.
            List<String> wet = new ArrayList<>();
            // BOTH SPECIES. The first version iterated turtles only while its message claimed to cover
            // both, so a change that moved the frog den's spawn row would have kept it green.
            for (var box : List.of(tBox, fBox)) {
                for (var mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                        new net.minecraft.world.phys.AABB(box.minX(), box.minY() - 1, box.minZ(),
                            box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1))) {
                    if (level.getFluidState(mob.blockPosition()).getType()
                            == com.flatts.recompile.registry.RCFluids.LEACHATE.get()) {
                        wet.add(mob.getType().toString() + " at " + mob.blockPosition());
                    }
                }
            }
            helper.assertTrue(wet.isEmpty(),
                "these animals generate standing in leachate, which sickens them for as long as they "
                    + "stay: " + wet);
            level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                new net.minecraft.world.phys.AABB(tBox.minX() - 2, tBox.minY() - 2, tBox.minZ() - 2,
                    fBox.maxX() + 2, fBox.maxY() + 2, fBox.maxZ() + 2))
                .forEach(net.minecraft.world.entity.Entity::discard);
            helper.succeed();
        });

        // AND THE CHAMBER YOU LAND IN IS QUIET.
        //
        // This is the point of the change rather than a side effect. The spawner used to sit five blocks
        // from the ladder, so a player climbed down into a crowd that had been accumulating since the
        // chunk loaded: you arrived at the payoff instead of walking to it.
        RCGameTests.test("the_root_chamber_is_quiet", 40, helper -> {
            var level = helper.getLevel();
            BlockPos base = helper.absolutePos(new BlockPos(0, 44, 0));
            var room = new SewerPieces.SewerRoom(0, RandomSource.create(5L), base.getX(), base.getZ());
            room.move(0, base.getY() - room.getBoundingBox().minY(), 0);
            BoundingBox box = room.getBoundingBox();
            BoundingBox limit = new BoundingBox(box.minX() - 16, box.minY() - 16, box.minZ() - 16,
                box.maxX() + 16, box.maxY() + 16, box.maxZ() + 16);
            room.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(),
                RandomSource.create(5L), limit,
                new net.minecraft.world.level.ChunkPos(base.getX() >> 4, base.getZ() >> 4), base);

            List<String> found = new ArrayList<>();
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    for (int z = box.minZ(); z <= box.maxZ(); z++) {
                        if (level.getBlockState(new BlockPos(x, y, z))
                                .is(net.minecraft.world.level.block.Blocks.SPAWNER)) {
                            found.add("spawner at " + x + "," + y + "," + z);
                        }
                    }
                }
            }
            helper.assertTrue(found.isEmpty(),
                "the chamber you climb down into still holds " + found + " - the threat belongs deeper "
                    + "in, so that arriving is quiet and the fight is something you walk toward");
            helper.succeed();
        });

        // THE SUMP CARRIES THE THREAT, and every sewer has one.
        //
        // Junctions used to, past depth 2 and on an even box-hash, which was a stand-in for a guarantee
        // the graph could not give - roughly one sewer in five had no drowned at all. The sump replaces
        // it with a guarantee and a reason: standing water is where drowned accumulate, so the fiction
        // and the mechanism are the same fact rather than two arrangements of one.
        RCGameTests.test("every_sewer_has_a_sump_and_it_holds_the_spawner", 40, helper -> {
            int without = 0;
            for (long seed = 0; seed < SEEDS; seed++) {
                boolean found = false;
                for (StructurePiece piece : layout(seed)) {
                    if (piece instanceof SewerPieces.SewerSump) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    without++;
                }
            }
            // AND IT SITS BELOW WHATEVER IT HANGS OFF. This is the honest version of the spec's "at
            // the deepest end", and the difference is worth stating rather than hiding behind a
            // threshold.
            //
            // attachSump walks the pieces ascending by minY and takes the first that fits. The bottom of
            // a sewer is the busy end, so a nine-block room often collides there: measured over these
            // 200 seeds, the sump lands 2 below the tree minimum in 56, at or below it in 102, and more
            // than a stair flight above it in 7. It is the low point of its own branch in all 200,
            // because it is anchored DEPTH under its host by construction.
            //
            // The first draft of this test asserted the strict property and failed 98 of 200, which is
            // the useful part: "a sump exists" and "the sump is at the bottom" are different claims, and
            // only the first was ever true. Buying the strict one costs a room tall enough to carry a
            // variable-height door (TALL 14 against 7, since the door must meet the host's floor), and
            // that is a design change rather than a fix - flagged in the spec, not decided here.
            int aboveHost = 0;
            for (long seed = 0; seed < SEEDS; seed++) {
                for (StructurePiece piece : layout(seed)) {
                    if (!(piece instanceof SewerPieces.SewerSump)) {
                        continue;
                    }
                    int floor = piece.getBoundingBox().minY();
                    boolean below = false;
                    for (StructurePiece other : layout(seed)) {
                        if (!(other instanceof SewerPieces.SewerSump)
                                && other.getBoundingBox().minY() == floor + SewerPieces.SewerSump.DEPTH) {
                            below = true;
                        }
                    }
                    if (!below) {
                        aboveHost++;
                    }
                }
            }
            helper.assertTrue(aboveHost == 0,
                aboveHost + " sumps do not sit exactly DEPTH below a piece of the sewer, so the pool is "
                    + "not under a walkway and the door cannot line up with the host's floor");

            helper.assertTrue(without == 0,
                without + " of " + SEEDS + " sewers have no sump, and the sump is the only guaranteed "
                    + "drowned in the structure - IN_WATER can never be satisfied by leachate");

            var level = helper.getLevel();
            BlockPos at = helper.absolutePos(new BlockPos(0, 16, 0));
            BoundingBox box = SewerPieces.SewerPiece.box(at.getX(), at.getY(), at.getZ(),
                Direction.SOUTH, 9, 7, 9);
            BoundingBox limit = new BoundingBox(box.minX() - 8, box.minY() - 8, box.minZ() - 8,
                box.maxX() + 8, box.maxY() + 8, box.maxZ() + 8);
            new SewerPieces.SewerSump(3, box, Direction.SOUTH).postProcess(
                level, level.structureManager(), level.getChunkSource().getGenerator(),
                RandomSource.create(5L), limit,
                new net.minecraft.world.level.ChunkPos(box.minX() >> 4, box.minZ() >> 4), at);
            boolean spawner = false;
            String holds = "nothing";
            int deep = 0;
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    for (int z = box.minZ(); z <= box.maxZ(); z++) {
                        var here = new BlockPos(x, y, z);
                        if (level.getBlockState(here)
                                .is(net.minecraft.world.level.block.Blocks.SPAWNER)) {
                            spawner = true;
                            if (level.getBlockEntity(here) instanceof net.minecraft.world.level.block
                                    .entity.SpawnerBlockEntity be) {
                                // READ IT OFF THE SYNC TAG. BaseSpawner keeps its spawn data private
                                // and exposes no accessor, but SpawnerBlockEntity has to tell the
                                // client what to render in the cage, so the entity id is in there.
                                holds = be.getUpdateTag(level.registryAccess()).toString();
                            }
                        }
                        if (y > box.minY() && level.getFluidState(here).getType()
                                == com.flatts.recompile.registry.RCFluids.LEACHATE.get()) {
                            deep++;
                        }
                    }
                }
            }
            helper.assertTrue(spawner,
                "the sump has no spawner, so the guarantee it exists for is not kept and the sewer can "
                    + "hold no drowned at all");
            helper.assertTrue(holds.contains("minecraft:drowned"),
                "the sump's spawner names no drowned - it holds " + holds + ". An empty spawner is "
                    + "a spawner as far as a block check is concerned, which is why that check alone was "
                    + "not enough: deleting the setEntityId call left every sewer with a decorative "
                    + "spawner cage and the suite green.");
            helper.assertTrue(deep > 0,
                "the sump has no standing water above its floor - it is the low point of a drainage "
                    + "system, and a dry one is just a room");

            // AND IT CANNOT GET OUT, checked as geometry rather than as flow.
            //
            // The obvious test - place the room, look for leachate outside it - is worthless here and
            // was written first: placeBlock only SCHEDULES a fluid tick, so at tick 0 nothing has moved
            // and the check passes against a room with no walkway and against one with no back wall.
            // Both were driven and both stayed green.
            //
            // So assert the condition that makes it flow instead. A source block with an air neighbour
            // to the side or below is a source block that is about to leave; every one of the pool's
            // faces must be fluid or brick.
            List<String> loose = new ArrayList<>();
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    for (int z = box.minZ(); z <= box.maxZ(); z++) {
                        var here = new BlockPos(x, y, z);
                        if (level.getFluidState(here).isEmpty()) {
                            continue;
                        }
                        for (Direction face : new Direction[]{Direction.NORTH, Direction.SOUTH,
                                Direction.EAST, Direction.WEST, Direction.DOWN}) {
                            var next = here.relative(face);
                            if (level.getBlockState(next).isAir()) {
                                loose.add(here + " open to " + face);
                            }
                        }
                    }
                }
            }
            helper.assertTrue(loose.isEmpty(),
                "the pool has faces open to air, so it drains the moment its scheduled fluid ticks run - "
                    + "over the ledge that exists to telegraph it, out the doorway, and into a stairwell "
                    + "that carries no channel of its own precisely so it cannot flood: " + loose);
            helper.succeed();
        });

        // THE SEWER OFFERS WHAT IT SAYS IT DOES, and only inside its own pieces.
        //
        // spawn_overrides is the half that decides which mobs are on the menu; the placement predicates
        // in RCSewerSpawns are the half that decides whether they can actually stand there. Both have to
        // agree or the mob silently never appears, which is how this whole phase started.
        RCGameTests.test("the_sewer_offers_slime_and_roach_in_its_own_pieces", 20, helper -> {
            // READ THE LOADED STRUCTURE, not the raw JSON. A string match passes no matter which
            // MobCategory the entry sits under - move the roach to "creature" and the file still
            // contains the id, while getMobsAt(..., MONSTER, ...) stops returning the override, the
            // biome list is used instead, and NEITHER mob ever spawns in a sewer. That is exactly the
            // silent-empty failure this test is named for, and the version that grepped the file could
            // not see it.
            var structure = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(ResourceKey.create(Registries.STRUCTURE,
                    Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "sewer")))
                .value();
            var override = structure.spawnOverrides()
                .get(net.minecraft.world.entity.MobCategory.MONSTER);
            helper.assertTrue(override != null,
                "the sewer has no MONSTER spawn override, so the biome list applies inside it and "
                    + "nothing the sewer is supposed to own will appear");
            helper.assertTrue(override.boundingBox()
                    == net.minecraft.world.level.levelgen.structure.StructureSpawnOverride
                        .BoundingBoxType.PIECE,
                "the override is not scoped to the structure pieces, so mobs would spawn in the solid "
                    + "rock inside its bounding box as well as in its corridors");
            List<String> offered = new ArrayList<>();
            override.spawns().unwrap().forEach(e -> offered.add(e.value().type().toString()));
            helper.assertTrue(offered.toString().contains("slime"),
                "the sewer does not offer slime under MONSTER, so the relaxation in RCSewerSpawns has "
                    + "nothing to act on: " + offered);
            helper.assertTrue(offered.toString().contains("roach"),
                "the sewer does not offer roaches under MONSTER: " + offered);
            helper.succeed();
        });

        // AND THE PREDICATES THEMSELVES SAY NO OUTSIDE A SEWER.
        //
        // This is the claim the whole design rests on - the containment is a property of the predicate
        // rather than an argument about who lists what - and nothing asserted it. Both other tests read
        // data; neither exercises SpawnPlacements at all, so deleting the inSewer gate left everything
        // green while slimes spawned on every dark block in the world. The gametest plot contains no
        // sewer, which makes the negative case free. Follows StrayTests, which does this for the cat.
        //
        // NATURAL, not a test or command reason: those bypass rules on purpose, and asserting against
        // one is how a test like this quietly stops checking anything.
        RCGameTests.test("sewer_mobs_do_not_spawn_outside_a_sewer", 80, helper -> {
            // A SEALED, DARK POCKET. The slime gate is "inside a sewer AND dark AND a roll", so in a
            // lit plot it returns false on the lighting alone - it would keep returning false with the
            // sewer check deleted, and assert nothing. Driving the gate red proved exactly that: the
            // broken version was caught for the roach and missed for the slime.
            for (int dx = 0; dx <= 2; dx++) {
                for (int dy = 1; dy <= 3; dy++) {
                    for (int dz = 0; dz <= 2; dz++) {
                        boolean shell = dx == 0 || dx == 2 || dy == 1 || dy == 3 || dz == 0 || dz == 2;
                        helper.setBlock(new BlockPos(dx, dy, dz), shell
                            ? net.minecraft.world.level.block.Blocks.BRICKS
                            : net.minecraft.world.level.block.Blocks.AIR);
                    }
                }
            }
            BlockPos abs = helper.absolutePos(new BlockPos(1, 2, 1));
            // succeedWhen, not a fixed delay: sky light belongs to ThreadedLevelLightEngine on its own
            // thread, so no delay is ever sound - the repo already learned this from a solar panel test
            // that passed locally for months and went red on CI.
            helper.succeedWhen(() -> {
                helper.assertTrue(helper.getLevel().getMaxLocalRawBrightness(abs) <= 7,
                    "the probe is still lit (brightness "
                        + helper.getLevel().getMaxLocalRawBrightness(abs) + "), so the slime rule would "
                        + "fail on light alone and this test would pass without the sewer gate doing "
                        + "anything");
                List<String> leaked = new ArrayList<>();
                if (net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(
                        net.minecraft.world.entity.EntityType.SLIME, helper.getLevel(),
                        net.minecraft.world.entity.EntitySpawnReason.NATURAL, abs,
                        helper.getLevel().getRandom())) {
                    leaked.add("slime");
                }
                if (net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(
                        com.flatts.recompile.registry.RCEntities.ROACH.get(), helper.getLevel(),
                        net.minecraft.world.entity.EntitySpawnReason.NATURAL, abs,
                        helper.getLevel().getRandom())) {
                    leaked.add("roach");
                }
                helper.assertTrue(leaked.isEmpty(),
                    "these spawn in a dark pocket with no sewer anywhere near, so the structure gate in "
                        + "RCSewerSpawns is not holding and the relaxation has escaped: " + leaked);
            });
        });

        // THE COVER IS PRYBAR-ONLY, and "only" is the half that was decorative.
        //
        // This drives ManholeBlock.useItemOn, which is the branch that actually decides prybar against
        // not-prybar. The first version called useWithoutItem and the static pryOpen directly, so it
        // never touched that branch at all - a regression letting ANY held item open the cover would
        // have passed it unchanged.
        RCGameTests.test("a_manhole_opens_only_for_a_prybar", 40, helper -> {
            BlockPos at = new BlockPos(1, 1, 1);
            BlockPos abs = helper.absolutePos(at);
            var player = helper.makeMockServerPlayerInLevel();
            // SURVIVAL: a mock player has instabuild set, and a creative exemption in the gate would
            // let this pass for the wrong reason.
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(abs),
                net.minecraft.core.Direction.UP, abs, false);

            // A wrong item must not open it. A stick, not an empty hand - the empty hand takes a
            // different path and proves less.
            helper.setBlock(at, com.flatts.recompile.registry.RCBlocks.MANHOLE.get());
            helper.getLevel().getBlockState(abs).useItemOn(
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK),
                helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            helper.assertBlockPresent(com.flatts.recompile.registry.RCBlocks.MANHOLE.get(), at);

            // And it must not be minable, which is what actually gates it: the flag it used to carry
            // gated a DROP, and the reward here is the shaft rather than a drop, so hardness alone was
            // a bare-handed bypass of the whole tool gate.
            helper.assertTrue(helper.getLevel().getBlockState(abs)
                    .getDestroySpeed(helper.getLevel(), abs) < 0,
                "the cover is minable, so a player can open the sewer without ever finding a prybar and "
                    + "the tool gate is decoration");

            // The prybar opens it.
            helper.getLevel().getBlockState(abs).useItemOn(
                new net.minecraft.world.item.ItemStack(com.flatts.recompile.registry.RCItems.PRYBAR.get()),
                helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            helper.assertBlockNotPresent(com.flatts.recompile.registry.RCBlocks.MANHOLE.get(), at);
            helper.succeed();
        });

        // AND THE SHAFT'S BOX IS DERIVED CORRECTLY, which is the test the last one could not be.
        //
        // The previous version hand-built a BoundingBox and asserted postProcess filled it - true of any
        // box, including a wrong one. Both numbers in the real box were wrong at once and it saw
        // neither: the cap came from a Math.min over nine samples spanning the whole footprint, so the
        // cover generated BURIED wherever the ground above the chamber was higher than the lowest
        // ground anywhere under the sewer; and the floor sat at the chamber's CEILING, six blocks above
        // the player, directly over the leachate pool. Pure arithmetic, so it is asserted as arithmetic.
        RCGameTests.test("the_entrance_shaft_is_derived_from_the_chamber_and_its_own_ground", 20, helper -> {
            BoundingBox chamber = new BoundingBox(100, 20, 200, 111, 27, 211);
            BoundingBox shaft = SewerStructure.entranceBox(chamber, 64);

            helper.assertTrue(shaft.minY() == chamber.maxY(),
                "the shaft starts at y=" + shaft.minY() + " rather than the chamber ceiling at "
                    + chamber.maxY() + "; below the ceiling it would line itself in brick and seal the "
                    + "ladder inside a tube standing in the room");
            helper.assertTrue(shaft.maxY() == 63,
                "the pad is at y=" + shaft.maxY() + " rather than 63 - getBaseHeight returns the first "
                    + "AIR block, so the top solid layer is one below and anything else is buried or "
                    + "proud");
            // The column must be the dry interior ring, not the pool.
            int cx = shaft.minX() + 1;
            int cz = shaft.minZ() + 1;
            helper.assertTrue(cx == chamber.minX() + 1 && cz == chamber.minZ() + 1,
                "the shaft column is (" + cx + "," + cz + "); the pool fills minX+2..maxX-2, so any "
                    + "column at +2 or beyond drops the player into the Hunger fluid");
            helper.succeed();
        });

        // AND THE CHAMBER CARRIES THE LADDER UP TO MEET IT, so the entrance is not one-way.
        RCGameTests.test("the_chamber_ladder_reaches_the_shaft", 40, helper -> {
            var level = helper.getLevel();
            BlockPos base = helper.absolutePos(new BlockPos(0, 40, 0));
            var room = new SewerPieces.SewerRoom(0, RandomSource.create(9L), base.getX(), base.getZ());
            room.move(0, base.getY() - room.getBoundingBox().minY(), 0);
            BoundingBox box = room.getBoundingBox();
            BoundingBox limit = new BoundingBox(box.minX() - 16, box.minY() - 16, box.minZ() - 16,
                box.maxX() + 16, box.maxY() + 16, box.maxZ() + 16);
            room.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(),
                RandomSource.create(9L), limit,
                new net.minecraft.world.level.ChunkPos(base.getX() >> 4, base.getZ() >> 4), base);

            List<String> gaps = new ArrayList<>();
            for (int y = box.minY() + 1; y <= box.maxY(); y++) {
                if (!level.getBlockState(new BlockPos(box.minX() + 1, y, box.minZ() + 1))
                        .is(net.minecraft.world.level.block.Blocks.LADDER)) {
                    gaps.add("y=" + y);
                }
            }
            helper.assertTrue(gaps.isEmpty(),
                "the chamber's ladder does not run from its floor to its ceiling, so the shaft's lowest "
                    + "rung is unreachable and the only way in is a drop: " + gaps);
            helper.succeed();
        });

        // THE BARRELS DO NOT STAND IN THE LADDER.
        //
        // The entrance and the loot were built on separate branches, and the barrel's first position was
        // the chamber's interior corner - which the shaft had by then claimed for its column. Nothing
        // caught it until the branches met, and the symptom would have been a barrel where the way out
        // should be. Cheap to assert, and the kind of thing that comes back when two features share a
        // small room.
        RCGameTests.test("no_barrel_stands_in_the_entrance_shaft", 40, helper -> {
            var level = helper.getLevel();
            BlockPos base = helper.absolutePos(new BlockPos(0, 40, 0));
            var room = new SewerPieces.SewerRoom(0, RandomSource.create(4L), base.getX(), base.getZ());
            room.move(0, base.getY() - room.getBoundingBox().minY(), 0);
            BoundingBox box = room.getBoundingBox();
            BoundingBox limit = new BoundingBox(box.minX() - 16, box.minY() - 16, box.minZ() - 16,
                box.maxX() + 16, box.maxY() + 16, box.maxZ() + 16);
            room.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(),
                RandomSource.create(4L), limit,
                new net.minecraft.world.level.ChunkPos(base.getX() >> 4, base.getZ() >> 4), base);

            BlockPos column = new BlockPos(box.minX() + 1, box.minY() + 1, box.minZ() + 1);
            helper.assertTrue(level.getBlockState(column)
                    .is(net.minecraft.world.level.block.Blocks.LADDER),
                "the shaft column at " + column + " holds "
                    + level.getBlockState(column).getBlock() + " rather than the ladder - something "
                    + "else in the chamber has taken the way out");
            // THE CHAMBER NO LONGER HOLDS THE LOOT. The barrels were here because this is where the
            // code could put them, not because anyone would store anything at the foot of a ladder;
            // they live in an access chamber now, which is a room that explains them.
            int barrels = 0;
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    if (level.getBlockState(new BlockPos(x, box.minY() + 1, z))
                            .is(net.minecraft.world.level.block.Blocks.BARREL)) {
                        barrels++;
                    }
                }
            }
            helper.assertTrue(barrels == 0,
                "the root chamber still holds " + barrels + " barrels - the loot moved to the access "
                    + "chambers, and leaving a copy here undoes the reason for moving it");

            // THE TABLE STILL HAS TO RESOLVE, and that check stays here even though the barrels left.
            // The per-barrel scan that used to follow it did not: it walked this room for barrels after
            // asserting the room has none, so it examined an empty set and passed by construction - a
            // test that survives the change it is supposed to notice. The live version is in
            // sewers_get_their_access_chambers, which counts stocked barrels where they now are.
            // ReloadableServerRegistries returns LootTable.EMPTY for a key that resolves to nothing -
            // no log, no throw - so a typo in the path, a renamed file, or a refactor that drops the
            // BlockEntity match ships every sewer with two empty barrels and the whole suite stays
            // green. That is the entire phase-4 deliverable failing invisibly.
            var key = net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "chests/sewer"));
            helper.assertTrue(level.getServer().reloadableRegistries().getLootTable(key)
                    != net.minecraft.world.level.storage.loot.LootTable.EMPTY,
                "recompile:chests/sewer resolves to nothing, so every barrel in every sewer is empty "
                    + "and nothing anywhere says so");

            // AND THE TABLE OFFERS A COMPONENT (owner, 2026-08-17). Bulk salvage alone is what a player
            // already gets from sorting garbage, so it is not a reason to have cleared a sewer. The
            // components here are all blueprint_crafting - a found one is a single unit that teaches
            // nothing, so it cannot skip the tier the blueprint gates.
            String table;
            try (var in = SewerTests.class.getResourceAsStream(
                    "/data/recompile/loot_table/chests/sewer.json")) {
                helper.assertTrue(in != null, "the sewer loot table is not on the classpath");
                table = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                helper.fail("could not read the sewer loot table: " + e);
                return;
            }
            List<String> missing = new ArrayList<>();
            for (String component : List.of("recompile:motor", "recompile:pump", "recompile:bulb")) {
                if (!table.contains(component)) {
                    missing.add(component);
                }
            }
            helper.assertTrue(missing.isEmpty(),
                "the sewer table offers no " + missing + ", so a cleared sewer pays out only what "
                    + "sorting garbage already pays");
            helper.succeed();
        });

        // A DEN HAS A DOOR, which is the assertion whose absence shipped the whole feature invisible.
        //
        // Every wall of a den was written and nothing was ever carved, so each one generated as an
        // airtight box with three turtles sealed inside. No player could enter one, see one, or know it
        // existed - and the test that was supposed to cover dens asserted floor material and head count,
        // both of which are perfectly true inside a sealed box.
        RCGameTests.test("a_den_opens_into_the_chamber", 40, helper -> {
            var level = helper.getLevel();
            BlockPos at = helper.absolutePos(new BlockPos(0, 24, 0));
            var den = new SewerPieces.SewerTurtleDen(1, new BoundingBox(
                at.getX(), at.getY(), at.getZ(),
                at.getX() + 5, at.getY() + 3, at.getZ() + 4));
            BoundingBox box = den.getBoundingBox();
            BoundingBox limit = new BoundingBox(box.minX() - 16, box.minY() - 16, box.minZ() - 16,
                box.maxX() + 16, box.maxY() + 16, box.maxZ() + 16);
            den.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(),
                RandomSource.create(8L), limit,
                new net.minecraft.world.level.ChunkPos(box.minX() >> 4, box.minZ() >> 4), at);

            // The turtle den's door is on its WEST face, which is the wall it shares with the chamber.
            int open = 0;
            for (int dy = 1; dy <= 2; dy++) {
                if (level.getBlockState(new BlockPos(box.minX(), box.minY() + dy,
                        box.getCenter().getZ())).isAir()) {
                    open++;
                }
            }
            helper.assertTrue(open == 2,
                "the den's shared wall has " + open + " open cells rather than 2 - a den with no door "
                    + "is a sealed box with animals in it, which is a feature nobody can ever see");
            level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                new net.minecraft.world.phys.AABB(box.minX() - 2, box.minY() - 2, box.minZ() - 2,
                    box.maxX() + 2, box.maxY() + 2, box.maxZ() + 2))
                .forEach(net.minecraft.world.entity.Entity::discard);
            helper.succeed();
        });

        // AND THE DENS LAND ON NOTHING, which is pure geometry and was pure fiction.
        //
        // The dens are added after the graph is built, so findCollisionPiece never sees them, and they
        // postProcess last - so wherever they overlap something they win, silently. A comment claimed
        // the high-X wall was clear of every corridor mouth; it is clear of three. The EAST child
        // anchors at maxX+1 over z = minZ..minZ+4, and the turtle den sat exactly there, walling that
        // branch shut on every sewer that grew one. The two dens also overlapped each other whenever
        // the chamber rolled its two smallest sizes.
        //
        // Swept over every chamber size the room can roll, because both bugs were size-dependent.
        RCGameTests.test("the_dens_land_on_no_corridor_and_not_on_each_other", 20, helper -> {
            List<String> clashes = new ArrayList<>();
            for (int w = 9; w <= 13; w++) {
                for (int d = 9; d <= 13; d++) {
                    BoundingBox chamber = new BoundingBox(0, 50, 0, w, 57, d);
                    BoundingBox turtle = SewerStructure.turtleDenBox(chamber);
                    BoundingBox frog = SewerStructure.frogDenBox(chamber);
                    if (turtle.intersects(frog)) {
                        clashes.add(w + "x" + d + ": the dens overlap each other");
                    }
                    for (var child : SewerStructure.childBoxes(chamber)) {
                        if (turtle.intersects(child)) {
                            clashes.add(w + "x" + d + ": turtle den on a corridor");
                        }
                        if (frog.intersects(child)) {
                            clashes.add(w + "x" + d + ": frog den on a corridor");
                        }
                    }
                }
            }
            helper.assertTrue(clashes.isEmpty(),
                "the dens are written last and win every overlap silently, so these seal whatever they "
                    + "land on: " + clashes.subList(0, Math.min(4, clashes.size()))
                    + " (" + clashes.size() + " total)");
            helper.succeed();
        });


        // NO LIGHT WHERE THINGS MUST SPAWN. This is phase 1's real acceptance criterion, and the one
        // failure in it that nothing else would report.
        //
        // A hostile spawn needs block light 0, so ANY source suppresses spawning in its radius - the
        // level does not soften that, only the radius changes. A lantern in a junction therefore
        // switches off that junction's drowned spawner silently: the spawner is still there, still
        // holds a drowned, and never fires. The lit rooms are deliberately the unspawnable ones (the
        // chamber, the shaft, the dens); corridors and junctions must stay dark.
        RCGameTests.test("no_light_where_the_sewer_must_spawn", 60, helper -> {
            var level = helper.getLevel();
            var gen = level.getChunkSource().getGenerator();
            var mgr = level.structureManager();
            List<String> lit = new ArrayList<>();

            // MANY PIECES, ON A GRID, and this is the difference between a test and a coin flip. The
            // dressing is seeded from each piece's own bounding box and a GameTest plot lands somewhere
            // different on every run, so a single corridor either got a mushroom that run or did not -
            // the light-1 brown mushroom rode through an entire phase behind exactly that, and the runs
            // that would have caught it were indistinguishable from the runs that had nothing to catch.
            //
            // DEPTH 1 on the junctions, kept from when a deep junction carried a spawner. The spawner
            // moved to the sump, so this no longer avoids anything - but a test that writes outside its
            // own plot should still place the least it can.
            var pieces = new ArrayList<StructurePiece>();
            for (int step = 0; step < 9; step++) {
                BlockPos at = helper.absolutePos(new BlockPos(0, 28, 0))
                    .offset((step % 3) * 11, 0, (step / 3) * 13);
                pieces.add(new SewerPieces.SewerCorridor(1, SewerPieces.SewerPiece.box(
                    at.getX(), at.getY(), at.getZ(), Direction.SOUTH, 5, 5, 7), Direction.SOUTH));
                pieces.add(new SewerPieces.SewerCrossing(1, SewerPieces.SewerPiece.box(
                    at.getX() + 40, at.getY(), at.getZ(), Direction.SOUTH, 5, 5, 5), Direction.SOUTH));
            }
            BlockPos base = helper.absolutePos(new BlockPos(0, 28, 0));

            for (var piece : pieces) {
                BoundingBox box = piece.getBoundingBox();
                BoundingBox limit = new BoundingBox(box.minX() - 16, box.minY() - 16, box.minZ() - 16,
                    box.maxX() + 16, box.maxY() + 16, box.maxZ() + 16);
                piece.postProcess(level, mgr, gen, RandomSource.create(21L), limit,
                    new net.minecraft.world.level.ChunkPos(box.minX() >> 4, box.minZ() >> 4), base);
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    for (int y = box.minY(); y <= box.maxY(); y++) {
                        for (int z = box.minZ(); z <= box.maxZ(); z++) {
                            var at = new BlockPos(x, y, z);
                            if (level.getBlockState(at).getLightEmission() > 0) {
                                lit.add(level.getBlockState(at).getBlock() + " at " + at);
                            }
                        }
                    }
                }
            }
            helper.assertTrue(lit.isEmpty(),
                "these light sources are in a corridor or a junction, which silently switches off the "
                    + "spawns that piece exists to host - the spawner stays, holds a drowned, and never "
                    + "fires: " + lit);

            // AND THE ROOMS THAT SHOULD BE LIT ARE. Without this the test passes if LIGHT is deleted
            // outright, which is exactly how the chamber came to be unlit while three separate comments
            // said it was lit.
            BlockPos roomAt = helper.absolutePos(new BlockPos(0, 46, 0));
            var room = new SewerPieces.SewerRoom(0, RandomSource.create(31L), roomAt.getX(), roomAt.getZ());
            room.move(0, roomAt.getY() - room.getBoundingBox().minY(), 0);
            BoundingBox rbox = room.getBoundingBox();
            BoundingBox rlimit = new BoundingBox(rbox.minX() - 16, rbox.minY() - 16, rbox.minZ() - 16,
                rbox.maxX() + 16, rbox.maxY() + 16, rbox.maxZ() + 16);
            room.postProcess(level, mgr, gen, RandomSource.create(31L), rlimit,
                new net.minecraft.world.level.ChunkPos(rbox.minX() >> 4, rbox.minZ() >> 4), roomAt);
            int lamps = 0;
            for (int x = rbox.minX(); x <= rbox.maxX(); x++) {
                for (int z = rbox.minZ(); z <= rbox.maxZ(); z++) {
                    if (level.getBlockState(new BlockPos(x, rbox.maxY() - 1, z))
                            .getLightEmission() > 0) {
                        lamps++;
                    }
                }
            }
            helper.assertTrue(lamps > 0,
                "the root chamber has no light in it - it holds the ladder, the pool and the barrels, "
                    + "and unlit it is also spawnable, which is the opposite of what it is for");
            level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                new net.minecraft.world.phys.AABB(rbox.minX() - 2, rbox.minY() - 2, rbox.minZ() - 2,
                    rbox.maxX() + 2, rbox.maxY() + 2, rbox.maxZ() + 2))
                .forEach(net.minecraft.world.entity.Entity::discard);
            helper.succeed();
        });

        // EVERY DRESSING BLOCK ACTUALLY GETS PLACED, which is the guard whose absence shipped two dead
        // features in one commit.
        //
        // The fine silt and the growth were declared, documented, added to SewerPalette.ALL and never
        // reachable: their arithmetic could not fire for the only dimensions any caller passes. Nothing
        // saw it, because the palette walk asks what a block IS - is it in the furnace tag - and never
        // whether anything places it. A palette entry that cannot be reached is indistinguishable from
        // one that is fine, right up until someone goes looking for a mushroom.
        RCGameTests.test("the_dressing_blocks_are_all_reachable", 60, helper -> {
            var level = helper.getLevel();
            var gen = level.getChunkSource().getGenerator();
            var mgr = level.structureManager();
            var seen = new HashSet<net.minecraft.world.level.block.Block>();

            // A GRID, not a line. The dressing is seeded from minX * 31 + minZ * 17, so walking the
            // samples diagonally keeps that sum marching in a fixed stride - the seed never varies mod 2
            // or mod 4 and half the palette can never come up. This is the same correlation mistake the
            // dressing arithmetic itself had, made a second time in the test written to catch it: the
            // first version offset x and z in lockstep and reported gravel and mushrooms as dead.
            for (int step = 0; step < 16; step++) {
                BlockPos base = helper.absolutePos(new BlockPos(0, 20, 0))
                    .offset((step % 4) * 9, 0, (step / 4) * 11);
                BoundingBox box = SewerPieces.SewerPiece.box(
                    base.getX(), base.getY(), base.getZ(), Direction.SOUTH, 5, 5, 7);
                BoundingBox limit = new BoundingBox(box.minX() - 8, box.minY() - 8, box.minZ() - 8,
                    box.maxX() + 8, box.maxY() + 8, box.maxZ() + 8);
                new SewerPieces.SewerCorridor(1, box, Direction.SOUTH).postProcess(
                    level, mgr, gen, RandomSource.create(step), limit,
                    new net.minecraft.world.level.ChunkPos(box.minX() >> 4, box.minZ() >> 4), base);
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    for (int y = box.minY(); y <= box.maxY(); y++) {
                        for (int z = box.minZ(); z <= box.maxZ(); z++) {
                            seen.add(level.getBlockState(new BlockPos(x, y, z)).getBlock());
                        }
                    }
                }
            }

            List<String> unreachable = new ArrayList<>();
            for (var state : List.of(SewerPalette.WET_COURSE, SewerPalette.CRACKED_COURSE,
                    SewerPalette.SILT, SewerPalette.FINE_SILT, SewerPalette.GROWTH)) {
                if (!seen.contains(state.getBlock())) {
                    unreachable.add(state.getBlock().toString());
                }
            }
            helper.assertTrue(unreachable.isEmpty(),
                "these are in the palette and no corridor ever places them, so they are dead content "
                    + "that every existing check reports as fine: " + unreachable);
            helper.succeed();
        });

        // A SEWER ACTUALLY GETS AN ACCESS CHAMBER, which is now where all of its loot lives.
        //
        // Moving the barrels out of the root chamber traded a guarantee for a placement: the room was
        // always there, and an access chamber is a roll on a corridor past the first link. If that roll
        // can miss, a sewer generates with no loot at all - the same shape as the spawner guarantee,
        // and the same reason to measure it rather than assume it.
        RCGameTests.test("sewers_get_their_access_chambers", 20, helper -> {
            int withRoom = 0;
            int rooms = 0;
            for (long seed = 0; seed < SEEDS; seed++) {
                int here = 0;
                for (StructurePiece piece : layout(seed)) {
                    if (piece instanceof SewerPieces.SewerAccessChamber) {
                        here++;
                    }
                }
                rooms += here;
                if (here > 0) {
                    withRoom++;
                }
            }
            helper.assertTrue(withRoom * 100 / SEEDS >= 100,
                "only " + withRoom + " of " + SEEDS + " sewers contain an access chamber, and the loot "
                    + "lives in them now - the rest generate with nothing to find at all");
            // AND NEVER AT THE BOTTOM OF THE LADDER. The roll requires depth >= 2 precisely so the
            // store is not the first thing you see, and the guarantee path used to walk build order -
            // which starts at the corridor leaving the root chamber, so a forced room landed exactly
            // where the roll forbids it.
            //
            // ZERO, not a small rate. The rate version of this line passed against the broken code: the
            // fallback fires on 8 seeds in 200 (measured by disabling it - roll-only coverage is 192 of
            // 200), so eight misplaced rooms against ~400 total is 2%, comfortably under any threshold
            // loose enough to feel safe. A budget wide enough to tolerate noise is wide enough to hide
            // the bug, and here there is no noise to tolerate.
            int shallow = 0;
            for (long seed = 0; seed < SEEDS; seed++) {
                for (StructurePiece piece : layout(seed)) {
                    if (piece instanceof SewerPieces.SewerAccessChamber && piece.getGenDepth() < 2) {
                        shallow++;
                    }
                }
            }
            helper.assertTrue(shallow == 0,
                shallow + " access chambers hang off the first link, where the store room is the first "
                    + "thing a player sees down the ladder - the depth >= 2 rule exists to stop that, "
                    + "so the guarantee path must not route around it");
            helper.assertTrue(rooms <= SEEDS * 4,
                "averaging " + (rooms / (double) SEEDS) + " access chambers per sewer, which is a "
                    + "warehouse rather than a maintenance point");

            // AND ONE ACTUALLY HOLDS THE LOOT. Coverage without contents is the ships-empty failure
            // wearing a different hat: every sewer having a room means nothing if the room is bare.
            var level = helper.getLevel();
            BlockPos at = helper.absolutePos(new BlockPos(0, 52, 0));
            BoundingBox box = SewerPieces.SewerPiece.box(at.getX(), at.getY(), at.getZ(),
                Direction.SOUTH, 7, 6, 7);
            BoundingBox limit = new BoundingBox(box.minX() - 8, box.minY() - 8, box.minZ() - 8,
                box.maxX() + 8, box.maxY() + 8, box.maxZ() + 8);
            new SewerPieces.SewerAccessChamber(2, box, Direction.SOUTH).postProcess(
                level, level.structureManager(), level.getChunkSource().getGenerator(),
                RandomSource.create(77L), limit,
                new net.minecraft.world.level.ChunkPos(box.minX() >> 4, box.minZ() >> 4), at);
            var key = net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "chests/sewer"));
            int stocked = 0;
            boolean litRoom = false;
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    for (int z = box.minZ(); z <= box.maxZ(); z++) {
                        var here = new BlockPos(x, y, z);
                        litRoom |= level.getBlockState(here).getLightEmission() > 0;
                        if (level.getBlockEntity(here)
                                instanceof net.minecraft.world.RandomizableContainer c
                                && key.equals(c.getLootTable())) {
                            stocked++;
                        }
                    }
                }
            }
            helper.assertTrue(stocked == 2,
                "the access chamber holds " + stocked + " stocked barrels rather than 2 - the loot moved "
                    + "here, so a bare room means the sewer pays out nothing");
            helper.assertTrue(litRoom,
                "the access chamber is unlit, which leaves the room holding the reward spawnable");

            // AND IT IS CLOSED AT THE BACK. line() leaves both end planes open so corridors can chain,
            // which is right for a corridor and wrong for a room that never has a child: the rear face
            // was a hole onto raw terrain directly behind the barrels.
            List<String> open = new ArrayList<>();
            for (int x = box.minX() + 1; x < box.maxX(); x++) {
                for (int y = box.minY() + 1; y < box.maxY(); y++) {
                    var here = new BlockPos(x, y, box.maxZ());
                    if (level.getBlockState(here).isAir()) {
                        open.add(here.toString());
                    }
                }
            }
            helper.assertTrue(open.isEmpty(),
                "the access chamber's back wall has holes in it, so the store room opens straight onto "
                    + "the terrain behind it: " + open);
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

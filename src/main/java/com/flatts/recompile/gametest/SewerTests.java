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
            var chunk = new net.minecraft.world.level.ChunkPos(base.getX() >> 4, base.getZ() >> 4);

            // ONE postProcess PER CHUNK, which is what the real caller does. A single limit covering the
            // whole room is the one shape that cannot reproduce the bug this test exists for: the room
            // is 10-14 blocks across, so it straddles several chunks, and each pass gets its own limit
            // and its own RandomSource. Rolling positions per pass made the turtle count a random sum
            // that could be zero; a test with one big limit would never have noticed.
            for (int cx = (box.minX() >> 4); cx <= (box.maxX() >> 4); cx++) {
                for (int cz = (box.minZ() >> 4); cz <= (box.maxZ() >> 4); cz++) {
                    BoundingBox slice = new BoundingBox(cx << 4, level.getMinY(), cz << 4,
                        (cx << 4) + 15, level.getMaxY(), (cz << 4) + 15);
                    // A DIFFERENT RandomSource PER PASS, as the real caller gives. Reusing one seed
                    // makes every pass roll identically, which hides a per-chunk re-roll completely -
                    // the version of this test that did that could not see the bug it was written for.
                    room.postProcess(level, level.structureManager(),
                        level.getChunkSource().getGenerator(),
                        RandomSource.create(cx * 7919L + cz), slice, chunk, base);
                }
            }

            BlockPos seat = new BlockPos(box.minX() + 1, box.minY() + 1, box.getCenter().getZ());
            helper.assertTrue(level.getBlockState(seat).is(net.minecraft.world.level.block.Blocks.SPAWNER),
                "no spawner in the root chamber at " + seat + " - without one the sewer has no drowned "
                    + "at all, because IN_WATER can never be satisfied by leachate");
            // AND IT HOLDS A DROWNED. Asserting the BlockEntity exists is a tautology - SpawnerBlock is
            // an EntityBlock, so it always has one - and a spawner with no entity id spawns nothing at
            // all, which is the failure actually worth catching. Deleting setEntityId used to leave this
            // test green.
            helper.assertTrue(
                level.getBlockEntity(seat) instanceof net.minecraft.world.level.block.entity
                    .SpawnerBlockEntity be
                    && be.saveWithoutMetadata(level.registryAccess()).toString().contains("drowned"),
                "the spawner at " + seat + " holds no drowned - an empty spawner is a block that looks "
                    + "right and spawns nothing");

            var bounds = new net.minecraft.world.phys.AABB(
                box.minX() - 1, box.minY() - 2, box.minZ() - 1,
                box.maxX() + 2, box.maxY() + 2, box.maxZ() + 2);
            var turtles = level.getEntitiesOfClass(
                net.minecraft.world.entity.animal.turtle.Turtle.class, bounds);
            helper.assertTrue(turtles.size() == 4,
                "found " + turtles.size() + " turtles rather than 4 - they cannot spawn in this world "
                    + "at all (sea level is -64 and there is no sand), and the count must not depend on "
                    + "how many chunks the room happens to straddle");
            // NOT ASSERTED, and stated rather than skipped: a turtle's homePos is private with a setter
            // and no getter, so the test cannot read it back. A turtle added without finalizeSpawn keeps
            // homePos = (0,0,0), and TurtleGoHomeGoal then fires on anything further than 64 blocks -
            // always true for a sewer hundreds of blocks out - so the turtles walk off toward world
            // origin and save the wrong home on the way. The placement calls setHomePos explicitly for
            // that reason, rather than relying on a side effect of finalizeSpawn that nothing here can
            // check.
            var frogs = level.getEntitiesOfClass(
                net.minecraft.world.entity.animal.frog.Frog.class, bounds);
            helper.assertTrue(frogs.size() == 2,
                "found " + frogs.size() + " frogs rather than 2 - a frog wants "
                    + "#minecraft:frogs_spawnable_on (grass block, mud, mangrove roots) plus a brightness check, so a "
                    + "brick sewer gives it nowhere to stand and it never arrives on its own");
            frogs.forEach(net.minecraft.world.entity.Entity::discard);
            // They are persistent and mostly land outside the plot, which GameTest cleanup does not
            // reach - so they are removed here rather than left to wander through neighbouring tests.
            turtles.forEach(net.minecraft.world.entity.Entity::discard);
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

        // THE COVER IS PRYBAR-ONLY, which is the Bulky Waste gate reused rather than a new verb.
        //
        // Both halves matter and they fail differently: a cover that opens bare-handed makes the prybar
        // pointless, and a cover that never opens makes the sewer unreachable. The second is the worse
        // one and leaves no trace - the structure generates perfectly and simply cannot be entered.
        RCGameTests.test("a_manhole_opens_only_for_a_prybar", 40, helper -> {
            BlockPos at = new BlockPos(1, 1, 1);
            helper.setBlock(at, com.flatts.recompile.registry.RCBlocks.MANHOLE.get());
            var player = helper.makeMockServerPlayerInLevel();
            // SURVIVAL: a mock player has instabuild set, and a creative exemption anywhere in the tool
            // gate would let this pass for the wrong reason.
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);

            // Bare hands first - it must still be there afterwards.
            BlockPos abs = helper.absolutePos(at);
            helper.getLevel().getBlockState(abs).useWithoutItem(helper.getLevel(), player,
                new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atCenterOf(abs),
                    net.minecraft.core.Direction.UP, abs, false));
            helper.assertBlockPresent(com.flatts.recompile.registry.RCBlocks.MANHOLE.get(), at);

            // Now the prybar, through the same static entry point the interaction uses.
            com.flatts.recompile.content.block.ManholeBlock.pryOpen(helper.getLevel(), abs);
            helper.assertBlockNotPresent(com.flatts.recompile.registry.RCBlocks.MANHOLE.get(), at);
            helper.succeed();
        });

        // AND THE SHAFT REACHES BOTH ENDS.
        //
        // A manhole that opens onto solid rock, or a chamber with no shaft above it, are both silent:
        // the structure generates, /locate finds it, and the player has no way in. This builds the
        // entrance the structure builds and walks the whole column.
        RCGameTests.test("the_entrance_shaft_runs_from_the_chamber_to_the_surface", 40, helper -> {
            var level = helper.getLevel();
            BlockPos base = helper.absolutePos(new BlockPos(0, 30, 0));
            int top = base.getY() + 20;
            BoundingBox box = new BoundingBox(base.getX(), base.getY(), base.getZ(),
                base.getX() + 2, top, base.getZ() + 2);
            var shaft = new SewerPieces.SewerEntrance(1, box);
            BoundingBox limit = new BoundingBox(box.minX() - 16, box.minY() - 16, box.minZ() - 16,
                box.maxX() + 16, box.maxY() + 16, box.maxZ() + 16);
            shaft.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(),
                RandomSource.create(1L), limit,
                new net.minecraft.world.level.ChunkPos(base.getX() >> 4, base.getZ() >> 4), base);

            List<String> broken = new ArrayList<>();
            int x = box.minX() + 1;
            int z = box.minZ() + 1;
            for (int y = box.minY(); y < top; y++) {
                if (!level.getBlockState(new BlockPos(x, y, z))
                        .is(net.minecraft.world.level.block.Blocks.LADDER)) {
                    broken.add("no ladder at y=" + y);
                }
            }
            helper.assertTrue(broken.isEmpty(),
                "the shaft is not climbable for its whole height, so it is a hole you fall down rather "
                    + "than a way in and out: " + broken.subList(0, Math.min(3, broken.size())));
            helper.assertTrue(level.getBlockState(new BlockPos(x, top, z))
                    .is(com.flatts.recompile.registry.RCBlocks.MANHOLE.get()),
                "no manhole cover at the top of the shaft, so the sewer is either sealed or standing "
                    + "open - and which one it is depends on what the terrain happened to put there");
            helper.assertTrue(level.getBlockState(new BlockPos(x + 1, top, z))
                    .is(com.flatts.recompile.registry.RCBlocks.REINFORCED_CONCRETE.get()),
                "no concrete pad around the cover, which is the only thing that makes a one-block hole "
                    + "findable in a biome this size");
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

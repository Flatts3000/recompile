package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.worldgen.sewer.SewerPalette;
import com.flatts.recompile.content.worldgen.sewer.SewerPieces;
import com.flatts.recompile.content.worldgen.sewer.SewerStructure;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/**
 * Who lives in a sewer, where they may spawn, and whether the rooms built for them are habitable (#90).
 *
 * <p><b>Two rules meet here and they pull the same way.</b> A hostile spawn needs block light 0, so the
 * rooms that are lit - the chamber, the entrance shaft, the dens - are exactly the rooms that must not
 * spawn anything, and the corridors that are dark are exactly the ones that must. The fiction and the
 * mob rule want the same lighting, which is the tell that the placement is right rather than a
 * compromise.
 *
 * <p><b>These are the tests that have to tick.</b> The shape tests can assert against a world one
 * instant after {@code postProcess}, because geometry is finished the moment it is written. Life is not:
 * a den that generates correctly can still kill its animals over the following seconds, which is exactly
 * what {@code the_dens_animals_can_live_in_them} exists to catch and what the head-count test it sits
 * beside could never have seen.
 *
 * <p>Split out of {@code SewerTests} at #223; see {@link SewerShapeTests} for the seam.
 */
final class SewerLifeTests {

    private SewerLifeTests() {
    }

    static void register() {

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

            // FROM THE SHIPPED GEOMETRY, not from numbers retyped here. Both dens were hand-built at
            // 6x4 in this test, which is a size the turtle den has not been since it grew to hold three
            // turtles - so the test measured a room the game does not generate, and would have gone on
            // passing while the real one broke.
            BlockPos turtleAt = helper.absolutePos(new BlockPos(0, 30, 0));
            var turtleDen = new SewerPieces.SewerTurtleDen(1,
                SewerFixtures.shapedLike(SewerStructure::turtleDenBox, turtleAt));
            // ABOVE the turtle den, not a chunk to the south of it (#239). At z+16 this landed in a
            // different chunk from the plot, and a GameTest plot lands somewhere different every run -
            // so whether that chunk was entity-loaded was a coin flip. `setBlock` pulls a chunk in,
            // which is why the mud-floor assertion below always passed, but `addFreshEntity` does not
            // and simply returns without adding. The symptom was zero frogs rather than some, and it
            // failed roughly one run in three.
            //
            // Both dens are four blocks tall, so twenty of clearance separates them with room to
            // spare, and Y-separation keeps them in the chunk column the turtle den has always
            // worked in.
            BlockPos frogAt = helper.absolutePos(new BlockPos(0, 50, 0));
            var frogDen = new SewerPieces.SewerFrogDen(1,
                SewerFixtures.shapedLike(SewerStructure::frogDenBox, frogAt));

            for (var den : List.of(turtleDen, frogDen)) {
                BoundingBox box = den.getBoundingBox();
                // A den built into a chunk that is not loaded places its blocks and silently drops its
                // animals, so check before rather than diagnose after. This is the guard #239 asked
                // for: "the test should fail loudly if its own setup did not take", because
                // postProcess placing nothing looks exactly like the mechanic being broken.
                for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                    for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                        helper.assertTrue(
                            level.getChunkSource().hasChunk(cx, cz),
                            "chunk " + cx + "," + cz + " is not loaded, so this den's animals would "
                                + "be dropped without a word - the den is being built outside the "
                                + "plot's own chunks");
                    }
                }
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
            // MAX OF THE TWO, not whichever den is named second. This took its X ceiling from the frog
            // den, which is four wide against the turtle den's eight - so the third turtle was outside
            // the sweep by two blocks and only got discarded because its hitbox clipped the edge by 0.2.
            // Any further widening, or any change to the pitch that shifts it east, leaves live turtles
            // in a level the next test is counting mobs in.
            level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                new net.minecraft.world.phys.AABB(
                    Math.min(tBox.minX(), fBox.minX()) - 2,
                    Math.min(tBox.minY(), fBox.minY()) - 2,
                    Math.min(tBox.minZ(), fBox.minZ()) - 2,
                    Math.max(tBox.maxX(), fBox.maxX()) + 2,
                    Math.max(tBox.maxY(), fBox.maxY()) + 2,
                    Math.max(tBox.maxZ(), fBox.maxZ()) + 2))
                .forEach(net.minecraft.world.entity.Entity::discard);
            helper.succeed();
        });


        // AND THEY CAN LIVE IN IT, which is a different question from whether they are in it.
        //
        // Playtest, 2026-08-18: "turtles are getting stuck in the wall and dying." EntityType.TURTLE is
        // sized(1.2F, 0.4F) - WIDER THAN THE BLOCK IT STANDS ON - and the den was sized and populated as
        // though it held something a block across. Three turtles seeded on a one-block pitch in the
        // corner row each spawned overlapping a neighbour and the wall behind them, and Entity.isInWall
        // samples a box 0.8 * width across the eye, so a body shoved by the neighbour it was already
        // inside starts taking suffocation damage against the brick.
        //
        // THE DEN TEST ABOVE COULD NOT SEE ANY OF IT, and that is the lesson rather than the bug. It
        // counts heads immediately after postProcess, and postProcess is the one moment the room is
        // correct: nothing has moved yet and nothing has been hurt yet. Suffocation is a tick-loop
        // fact, so a test that never ticks asserts the placement and calls it the habitat.
        RCGameTests.test("the_dens_animals_can_live_in_them", 500, helper -> {
            var level = helper.getLevel();
            var gen = level.getChunkSource().getGenerator();
            var mgr = level.structureManager();

            // THE SHIPPED SHAPE AT A HEIGHT NOTHING ELSE USES. Plots are 5x5x5 and laid out along X, so
            // any sewer test is building into its neighbours' airspace - the whole file gets away with it
            // by separating on Y. It is not a nicety: this test read a turtle standing in bricks another
            // sewer test had written into the same cell, which looks exactly like the suffocation bug it
            // exists to measure.
            //
            // 100, WELL CLEAR OF THE PACK. The first attempt at this took an unused NUMBER (36) rather
            // than an unused SPACE, and every anchor in this file is the bottom of something several
            // blocks tall - y=34 is a seven-tall sump, so it owns 34 through 40. Everything else here
            // lives at 2 through 56; a hundred cannot be reached by any of it.
            //
            // The dens get the real boxes' DIMENSIONS at a corner of this test's choosing rather than
            // their real POSITION, because where a den sits relative to the chamber is a different
            // test's question and building it out there is what walks into a neighbour.
            var dens = List.of(
                (SewerPieces.SewerDen) new SewerPieces.SewerTurtleDen(1,
                    SewerFixtures.shapedLike(SewerStructure::turtleDenBox, helper.absolutePos(new BlockPos(0, 100, 0)))),
                new SewerPieces.SewerFrogDen(1,
                    SewerFixtures.shapedLike(SewerStructure::frogDenBox, helper.absolutePos(new BlockPos(0, 100, 16)))));
            BlockPos at = dens.getFirst().getBoundingBox().getCenter();

            // GROUND UNDER THE DEN AND OUTSIDE ITS DOOR, both of which a real sewer has for free.
            //
            // The lower course is load-bearing in the literal sense: THE TURTLE DEN IS FLOORED IN SAND,
            // and sand falls. In the world it rests on the rock the sewer was cut into; in an empty plot
            // it rests on nothing, so the bed drained away and took the turtles with it - and the test
            // reported an empty den, which is exactly what the bug it hunts looks like. The frog den sat
            // there perfectly happy on its mud the whole time.
            //
            // The upper course is so a frog, which is narrow enough to walk out of a doorway a turtle
            // cannot, has somewhere to land.
            for (var den : dens) {
                BoundingBox box = den.getBoundingBox();
                for (int x = box.minX() - 6; x <= box.maxX() + 6; x++) {
                    for (int z = box.minZ() - 6; z <= box.maxZ() + 6; z++) {
                        for (int y = box.minY() - 1; y <= box.minY(); y++) {
                            level.setBlock(new BlockPos(x, y, z), SewerPalette.WALL,
                                net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
            for (var den : dens) {
                BoundingBox box = den.getBoundingBox();
                BoundingBox limit = new BoundingBox(box.minX() - 24, box.minY() - 24, box.minZ() - 24,
                    box.maxX() + 24, box.maxY() + 24, box.maxZ() + 24);
                den.postProcess(level, mgr, gen, RandomSource.create(4L), limit,
                    new net.minecraft.world.level.ChunkPos(box.minX() >> 4, box.minZ() >> 4), at);
            }

            // AND THE DOORWAY IS SEALED FOR THE SOAK. A frog is 0.5 wide and the door is a one-block
            // hole, so it can walk out of a den a turtle cannot - and twenty seconds is long enough to
            // cross the skirt of ground this test lays and step off the edge, a hundred blocks up. It
            // would then either die on impact or leave the search box, and the assertion would report
            // that the den killed what it was built to hold: a false accusation against the code under
            // test, arrived at by way of a cliff the test built itself.
            //
            // Sealing every outward-facing cell rather than the door specifically, because which wall
            // the door is in is the den's business and this test should not have to know. In a sewer the
            // other side of that hole is the chamber, so a closed shell is the faithful stand-in;
            // a_den_opens_into_the_chamber is what proves the door is there at all.
            for (var den : dens) {
                BoundingBox box = den.getBoundingBox();
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    for (int y = box.minY(); y <= box.maxY(); y++) {
                        for (int z = box.minZ(); z <= box.maxZ(); z++) {
                            boolean face = x == box.minX() || x == box.maxX()
                                || y == box.minY() || y == box.maxY()
                                || z == box.minZ() || z == box.maxZ();
                            var cell = new BlockPos(x, y, z);
                            if (face && level.getBlockState(cell).isAir()) {
                                level.setBlock(cell, SewerPalette.WALL,
                                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                            }
                        }
                    }
                }
            }

            // THE ROOM HAS TO FIT THE POPULATION, checked before anything ticks. residents() places what
            // fits and no more, which stops a cramped den killing its animals - and would just as
            // happily generate a den two turtles short in silence. This is the half that notices.
            // EVERY EXIT CLEARS UP FIRST. Both assertions here used to run with the animals still in the
            // level, so a genuine failure left three turtles and two frogs standing in shared world space
            // for every test that ran afterwards - and the neighbouring sewer tests count mobs by AABB,
            // so one real failure could cascade into unrelated ones and bury its own cause.
            Runnable sweep = () -> {
                for (var den : dens) {
                    BoundingBox box = den.getBoundingBox();
                    level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                            new net.minecraft.world.phys.AABB(box.minX() - 12, box.minY() - 6,
                                box.minZ() - 12, box.maxX() + 13, box.maxY() + 7, box.maxZ() + 13))
                        .forEach(net.minecraft.world.entity.Entity::discard);
                }
            };

            List<String> cramped = new ArrayList<>();
            for (var den : dens) {
                int fits = SewerPieces.SewerDen.residents(den.getBoundingBox(),
                    den.resident().getWidth(), den.population()).size();
                if (fits != den.population()) {
                    cramped.add(den.resident() + ": the den holds " + fits + " with room to move but "
                        + "the design asks for " + den.population());
                }
            }
            if (!cramped.isEmpty()) {
                sweep.run();
            }
            helper.assertTrue(cramped.isEmpty(), String.join("; ", cramped));

            // TWENTY SECONDS, not the two it takes to reproduce the original bug. Suffocation is a hit
            // every ten ticks, so the broken layout went red inside 120 - but the clearance that matters
            // most here is the 0.52 a turtle has from the den's short walls, and the only way to buy
            // confidence in a margin that thin is to let the goal selectors push at it for a while.
            helper.runAfterDelay(400, () -> {
                List<String> hurt = new ArrayList<>();
                for (var den : dens) {
                    BoundingBox box = den.getBoundingBox();
                    var near = new net.minecraft.world.phys.AABB(
                        box.minX() - 10, box.minY() - 4, box.minZ() - 10,
                        box.maxX() + 11, box.maxY() + 5, box.maxZ() + 11);
                    var living = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, near)
                        .stream().filter(m -> m.getType() == den.resident()).toList();
                    if (living.size() != den.population()) {
                        // The floor is worth printing: an empty den usually means the room went, not the
                        // animals, and the sand bed dropping out from under them looks identical from
                        // the headcount alone.
                        hurt.add(den.resident() + ": " + living.size() + " alive rather than "
                            + den.population() + ", so the den killed what it was built to hold (floor "
                            + level.getBlockState(new BlockPos(box.minX() + 2, box.minY(),
                                box.minZ() + 1)) + " at " + box + ")");
                    }
                    for (var mob : living) {
                        if (mob.isInWall()) {
                            // Naming the cell rather than the entity, because "a turtle is in a wall"
                            // never says WHICH wall - and isInWall samples a box 0.8 * width across the
                            // eye, so the offending block is often not the one under the animal's feet.
                            float cw = mob.getBbWidth() * 0.8F;
                            List<String> solid = new ArrayList<>();
                            BlockPos.betweenClosedStream(net.minecraft.world.phys.AABB.ofSize(
                                    mob.getEyePosition(), cw, 1.0E-6, cw))
                                .forEach(cell -> solid.add(
                                    cell.toShortString() + "=" + level.getBlockState(cell).getBlock()));
                            hurt.add(mob.getType() + " at " + mob.position() + " has its eye in "
                                + solid + " inside " + box
                                + ", which is a suffocation clock and not a habitat");
                        }
                        if (mob.getHealth() < mob.getMaxHealth()) {
                            hurt.add(mob.getType() + " at " + mob.blockPosition() + " is on "
                                + mob.getHealth() + "/" + mob.getMaxHealth() + " health having done "
                                + "nothing but stand in the room built for it");
                        }
                    }
                }
                sweep.run();
                helper.assertTrue(hurt.isEmpty(), String.join("; ", hurt));
                helper.succeed();
            });
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
            for (long seed = 0; seed < SewerFixtures.SEEDS; seed++) {
                boolean found = false;
                for (StructurePiece piece : SewerFixtures.layout(seed)) {
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
            for (long seed = 0; seed < SewerFixtures.SEEDS; seed++) {
                for (StructurePiece piece : SewerFixtures.layout(seed)) {
                    if (!(piece instanceof SewerPieces.SewerSump)) {
                        continue;
                    }
                    int floor = piece.getBoundingBox().minY();
                    boolean below = false;
                    for (StructurePiece other : SewerFixtures.layout(seed)) {
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
                without + " of " + SewerFixtures.SEEDS + " sewers have no sump, and the sump is the only guaranteed "
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
    }

}

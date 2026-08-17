package com.flatts.recompile.gametest;

import com.flatts.recompile.content.worldgen.sewer.SewerPalette;
import com.flatts.recompile.content.worldgen.sewer.SewerPieces;
import com.flatts.recompile.content.worldgen.sewer.SewerStructure;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

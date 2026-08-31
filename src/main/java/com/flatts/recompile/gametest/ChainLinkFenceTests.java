package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.ChainLinkFenceBlock;
import com.flatts.recompile.content.worldgen.FencedCompoundFeature;
import com.flatts.recompile.registry.RCBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The mod's first climbable block, and its first boundary (#310).
 *
 * <p><b>Climbing is the reason this block exists</b>, and it is a NeoForge extension point rather
 * than anything vanilla surfaces: {@code IBlockExtension#isLadder}. Nothing else in this mod is
 * climbable - the sewer shafts use vanilla ladders - so there was no existing coverage of it, and a
 * fence that has quietly stopped being climbable looks exactly like a fence.
 *
 * <p>The pair of assertions is the point rather than either one alone. A block that always answers
 * true is climbable and has no barbed variant; one that always answers false is an iron bar with a
 * new texture. The difference between the two states is the entire feature.
 */
final class ChainLinkFenceTests {

    private ChainLinkFenceTests() {
    }

    static void register() {

        // ONE BLOCK, TWO BEHAVIOURS. #310 decided against two blocks, so the boolean has to carry
        // the difference - and it has to carry it through isLadder, which is the extension point,
        // not through anything a blockstate comparison would notice.
        RCGameTests.test("chain_link_is_climbable_until_it_is_wired", 20, helper -> {
            var level = helper.getLevel();
            BlockPos plain = new BlockPos(1, 1, 1);
            BlockPos wired = new BlockPos(3, 1, 1);
            helper.setBlock(plain, RCBlocks.CHAIN_LINK_FENCE.get().defaultBlockState()
                .setValue(ChainLinkFenceBlock.BARBED, false));
            helper.setBlock(wired, RCBlocks.CHAIN_LINK_FENCE.get().defaultBlockState()
                .setValue(ChainLinkFenceBlock.BARBED, true));

            helper.assertTrue(
                level.getBlockState(helper.absolutePos(plain))
                    .isLadder(level, helper.absolutePos(plain), null),
                "a plain chain-link fence is not climbable. See-through AND climbable is the one "
                    + "shape vanilla has no block for, and it is the whole reason this is not just a "
                    + "Corrugated Metal Wall with a new texture");

            helper.assertTrue(
                !level.getBlockState(helper.absolutePos(wired))
                    .isLadder(level, helper.absolutePos(wired), null),
                "a barbed chain-link fence is still climbable, so the wire is decoration. Plain "
                    + "fence is an inconvenience and wired fence is a barrier, and that difference "
                    + "is the only thing the variant exists to express");
            helper.succeed();
        });

        // A COMPOUND, NOT A FIELD OF POSTS.
        //
        // A pane placed by worldgen gets its connections from the chunk's post-processing pass, which
        // WorldGenRegion.setBlock queues whenever UPDATE_KNOWN_SHAPE (16) is clear. Set that bit and
        // every panel keeps the four falses it was given: the perimeter generates as unconnected
        // posts, which still passes a "did any fence generate" check, still passes every registry
        // sweep, and is visibly wrong from ten blocks away.
        //
        // THAT IS NOT HYPOTHETICAL AND IS WHY THIS TEST EARNS ITS KEEP. The feature originally
        // carried a hand-rolled pass to resolve the connections, written on the strength of this
        // repo's Steel I-Beam note about flag 2 skipping neighbour updates - which is about neighbour
        // NOTIFICATIONS, not shape updates, and did not apply. Disabling that pass failed nothing,
        // which is how it was found to be dead code; setting bit 16 reports 0 of 54 panels connected,
        // which is how this assertion was shown to be live rather than decorative.
        RCGameTests.test("a_fenced_compound_is_connected_and_not_sealed", 100, helper -> {
            var level = helper.getLevel();
            BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
            // CLEARED HIGH, not just enough for a two-block fence. Plots sit beside each other and
            // WORLD_SURFACE_WG reads whatever a neighbouring test left standing; at four blocks of
            // clearance the footprint measured a spread of five and the flatness gate refused it,
            // correctly. The pad has to actually be flat before it can stand in for flat ground.
            for (int dx = -9; dx <= 9; dx++) {
                for (int dz = -9; dz <= 9; dz++) {
                    for (int dy = 0; dy <= 12; dy++) {
                        level.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                    }
                    level.setBlock(origin.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2);
                }
            }

            var feature = new FencedCompoundFeature();
            boolean built = false;
            for (int seed = 0; seed < 12 && !built; seed++) {
                built = feature.place(NoneFeatureConfiguration.INSTANCE, level,
                    level.getChunkSource().getGenerator(),
                    RandomSource.create(500L + seed), origin);
            }
            helper.assertTrue(built,
                "twelve attempts placed no compound on flat stone, so either the ground check, the "
                    + "flatness gate or the size floor rejects everything and fences never generate");

            List<BlockPos> panels = new ArrayList<>();
            int connected = 0;
            for (int dx = -9; dx <= 9; dx++) {
                for (int dy = 0; dy <= 3; dy++) {
                    for (int dz = -9; dz <= 9; dz++) {
                        BlockPos at = origin.offset(dx, dy, dz);
                        var state = level.getBlockState(at);
                        if (!state.is(RCBlocks.CHAIN_LINK_FENCE.get())) {
                            continue;
                        }
                        panels.add(at);
                        if (state.getValue(BlockStateProperties.NORTH)
                                || state.getValue(BlockStateProperties.EAST)
                                || state.getValue(BlockStateProperties.SOUTH)
                                || state.getValue(BlockStateProperties.WEST)) {
                            connected++;
                        }
                    }
                }
            }

            helper.assertTrue(panels.size() > 20,
                "the compound is only " + panels.size() + " panels, which is not a perimeter");

            // Nearly all of them, not all: the missing-panel roll leaves genuine orphans, and the
            // corners of the gap have a neighbour on one side only. A floor of four fifths is well
            // clear of both and nowhere near the zero a missing resolve pass produces.
            helper.assertTrue(connected * 5 >= panels.size() * 4,
                "only " + connected + " of " + panels.size() + " panels face a neighbour, so the "
                    + "compound generated as a field of unconnected posts. A pane is connected by the "
                    + "chunk's post-processing pass, which WorldGenRegion.setBlock queues only while "
                    + "UPDATE_KNOWN_SHAPE is clear - check the flag on the placement");

            // AND IT IS NOT SEALED. A perimeter you cannot get through is a trap rather than a
            // place, and this feature has two ways of opening one: the cut panel, which is
            // deliberate, and the missing-panel roll, which is weathering.
            //
            // ASSERTED AS "not closed" RATHER THAN "has a cut", deliberately, and the test was
            // renamed to match. Both mechanisms leave the same evidence - a hole in the ground
            // course - so no assertion here can tell them apart, and one claiming to would be
            // reading a stronger guarantee out of a weaker check. What is actually guaranteed is
            // that you can walk in, and that is what this says.
            int groundCourse = 0;
            for (BlockPos at : panels) {
                if (at.getY() == origin.getY()) {
                    groundCourse++;
                }
            }
            helper.assertTrue(groundCourse > 0, "no ground course at all, so nothing enclosed anything");
            helper.assertTrue(groundCourse < panels.size(),
                "every panel is on the ground course, so the fence is one block tall - it should be "
                    + "two, and a one-block fence is a kerb");
            helper.succeed();
        });

        // AND IT IS IN EVERY OVERWORLD BIOME, swept off the shipped biome JSON rather than trusted.
        //
        // The owner's instruction was "every biome" (2026-08-31), which overruled #310's own
        // structures-only decision. That is three files, each edited by hand, and a fence missing
        // from one of them is invisible: the biome still generates, and you would have to stand in
        // it and notice an absence.
        RCGameTests.test("every_overworld_biome_has_fenced_compounds", 20, helper -> {
            var biomes = helper.getLevel().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
            List<String> missing = new ArrayList<>();
            for (var entry : biomes.listElements().toList()) {
                var id = entry.key().identifier();
                if (!"recompile".equals(id.getNamespace())
                        // The compacted depths is solid floor to ceiling, so a fence has nowhere to
                        // stand. Named rather than derived, because "the Nether one" is the only way
                        // to say it and a second dimension would want its own decision anyway.
                        || "compacted_depths".equals(id.getPath())) {
                    continue;
                }
                boolean has = entry.value().getGenerationSettings().features().stream()
                    .flatMap(step -> step.stream())
                    .anyMatch(holder -> holder.unwrapKey()
                        .map(key -> key.identifier().getPath().equals("fenced_compound"))
                        .orElse(false));
                if (!has) {
                    missing.add(id.toString());
                }
            }
            helper.assertTrue(missing.isEmpty(),
                missing + " generate no fenced compounds. The owner asked for fences in every biome, "
                    + "and a biome quietly left out of that list looks exactly like a biome that "
                    + "happened not to roll one");
            helper.succeed();
        });

        // AND IT REFUSES A HILL, which is the difference between a fence you can see and one you
        // cannot.
        //
        // The first version placed on WORLD_SURFACE_WG wherever it landed, which in household
        // sprawl means over and between mounds: measured in a generated world at 10 of 45 ground
        // panels with a mound block sitting directly on top, and the rest visible only in fragments.
        // It generated, every test passed, and you could stand next to a compound without seeing
        // one. That is the failure this guards, and it is invisible to every other check here
        // because the feature still returns true and still writes fence.
        RCGameTests.test("a_compound_will_not_generate_on_a_hill", 100, helper -> {
            var level = helper.getLevel();
            BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
            for (int dx = -9; dx <= 9; dx++) {
                for (int dz = -9; dz <= 9; dz++) {
                    for (int dy = -1; dy <= 12; dy++) {
                        level.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                    }
                    level.setBlock(origin.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2);
                }
            }
            // A mound in the middle of the footprint, taller than MAX_RELIEF allows.
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = 0; dy <= 7; dy++) {
                        level.setBlock(origin.offset(dx, dy, dz),
                            RCBlocks.GARBAGE_BLOCK.get().defaultBlockState(), 2);
                    }
                }
            }

            var feature = new FencedCompoundFeature();
            int built = 0;
            for (int seed = 0; seed < 12; seed++) {
                if (feature.place(NoneFeatureConfiguration.INSTANCE, level,
                        level.getChunkSource().getGenerator(),
                        RandomSource.create(900L + seed), origin)) {
                    built++;
                }
            }
            helper.assertTrue(built == 0,
                built + " of 12 compounds generated around a mound eight blocks tall. A perimeter "
                    + "laid over that is buried in it, and a fence you cannot see is not a boundary");
            helper.succeed();
        });
    }
}

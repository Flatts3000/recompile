package com.flatts.recompile.gametest;

import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * GameTests for the building-block tier (design P1.12). These are ordinary blocks, so
 * the risk is not behaviour but wiring - a block that does not drop itself, or a slab
 * whose double form does not yield two. One representative check per concern; the full
 * family set is validated by {@code runGameTestServer} parsing every loot table and
 * recipe on boot.
 */
final class BuildingBlockTests {

    private BuildingBlockTests() {
    }

    static void register() {
        // Building blocks must drop themselves - you reclaim your own walls by hand, with
        // no tool gate. Break for real (destroyBlock passes dropBlock=false).
        RCGameTests.test("building_block_drops_itself", 40, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.PRESSED_JUNK_BLOCK.get());
            helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
            helper.assertBlockPresent(Blocks.AIR, pos);
            helper.succeedWhenEntityPresent(EntityType.ITEM, pos);
        });

        // A double slab must give back two slabs, not one - the vanilla-derived loot
        // table carries a set_count that a bad substitution would silently drop.
        RCGameTests.test("double_slab_drops_two", 40, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.PRESSED_JUNK_SLAB.get().defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.DOUBLE));
            helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
            helper.succeedWhen(() -> helper.assertItemEntityCountIs(
                RCBlocks.PRESSED_JUNK_SLAB.get().asItem(), pos, 2.0, 2));
        });

        // Cullet Glass drops itself (not shards, and not nothing-without-silk-touch like
        // vanilla glass) - building must be reversible. The pane is an IronBarsBlock, the
        // family's most distinct class, so it is the one worth asserting.
        RCGameTests.test("glass_pane_drops_itself", 40, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.CULLET_GLASS_PANE.get());
            helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
            helper.assertBlockPresent(Blocks.AIR, pos);
            helper.succeedWhenEntityPresent(EntityType.ITEM, pos);
        });

        // CARDBOARD IS THE UNGATED ONE, and that is the entire reason it exists (#309).
        //
        // Every other route to a wall in this world asks for something first - the ladder is gated,
        // the machines are gated, the frontier is gated - so the fifth family's job is to ask for
        // nothing: pick through a mound, get cardboard, build. The failure mode is not that it stops
        // working, it is that it quietly stops being EARLY. A blueprint requirement, a Scrap Crafting
        // Table requirement, or a component slipped into the recipe would each leave a family that
        // still crafts, still drops, still passes every other test in this file, and no longer does
        // the one thing it was added for.
        //
        // So this asks the live recipe manager what the shipped 2x2 of cardboard makes. A vanilla
        // `minecraft:crafting` recipe is by definition reachable at any bench; a blueprint recipe is
        // `recompile:blueprint_crafting` and would simply not be found here.
        RCGameTests.test("cardboard_builds_with_nothing_but_cardboard", 40, helper -> {
            var level = helper.getLevel();
            var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 2, java.util.List.of(
                new net.minecraft.world.item.ItemStack(RCItems.CARDBOARD.get()),
                new net.minecraft.world.item.ItemStack(RCItems.CARDBOARD.get()),
                new net.minecraft.world.item.ItemStack(RCItems.CARDBOARD.get()),
                new net.minecraft.world.item.ItemStack(RCItems.CARDBOARD.get())));
            var matches = level.recipeAccess().recipeMap().getRecipesFor(
                net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level);
            boolean makesTheBlock = matches.anyMatch(holder ->
                holder.value().assemble(input)
                    .is(RCBlocks.CARDBOARD_BLOCK.get().asItem()));
            helper.assertTrue(makesTheBlock,
                "four cardboard in a 2x2 does not make a Cardboard Block at an ordinary bench, so the "
                    + "one building family that is meant to need nothing now needs something");

            helper.succeed();
        });

        // BREAKING A PILE BY HAND IS THE WHOLE SOURCE OF CARDBOARD, so it is worth a test of its own
        // rather than a clause in the one above.
        //
        // The block was a SortableBlock for an afternoon and a teardown recipe for less than that.
        // Both are gone, and what replaced them has no code behind it at all: a plain FallingBlock
        // and a loot table. That is a good design and a quiet one - the loot table could be pointed
        // at the pile itself, or at junk, or lose its set_count, and nothing else in this repo would
        // notice. RegistryCompletenessTests only asks that a loot table EXISTS.
        //
        // destroyBlock with dropBlock=true, because GameTestHelper.destroyBlock passes false and
        // runs no loot table at all - a version of this test using the helper asserts nothing.
        RCGameTests.test("a_cardboard_pile_breaks_into_cardboard", 40, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.CARDBOARD_PILE.get());
            helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
            helper.assertBlockPresent(Blocks.AIR, pos);
            // COUNTED BY HAND, because the drop is a 3-5 range and GameTestHelper only offers an
            // exact-count assertion. At least three, since a pile is meant to be worth about one
            // Cardboard Block (four) and a single sheet would make the piles litter you walk past.
            helper.succeedWhen(() -> {
                net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3
                    .atCenterOf(helper.absolutePos(pos));
                int cardboard = 0;
                for (var entity : helper.getLevel().getEntitiesOfClass(
                        net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(at, at).inflate(3.0))) {
                    if (entity.getItem().is(RCItems.CARDBOARD.get())) {
                        cardboard += entity.getItem().getCount();
                    }
                }
                helper.assertTrue(cardboard >= 3,
                    "breaking a Cardboard Pile by hand dropped " + cardboard + " cardboard. It is "
                        + "meant to be worth about one Cardboard Block, which is four - and this is "
                        + "the only source of the material in the game, so a table pointing anywhere "
                        + "else leaves the whole building family unreachable");
            });
        });

        // AND WORLDGEN ACTUALLY PLACES THEM, which is the half no amount of recipe checking reaches.
        //
        // FindRateTest proves the ARITHMETIC - that SURFACE_CARDBOARD_CHANCE works out at several
        // piles a mound - and it would go on proving it if pickBlock never returned a pile at all.
        // The two halves fail independently: a constant set to zero is caught there, a branch that
        // never fires is caught here, and neither test sees the other's failure.
        //
        // This runs MoundFeature itself rather than reimplementing the placement rule, for the same
        // reason ShellHasNoFloatersTest calls the tower's own flood fill: a test that copies the
        // algorithm it is checking passes just as happily when the real one is wrong.
        RCGameTests.test("mounds_generate_cardboard_piles", 100, helper -> {
            var level = helper.getLevel();
            var origin = helper.absolutePos(new BlockPos(0, 1, 0));

            // A floor to build on. The feature only writes into air, so bare test-plot ground would
            // let a mound land wherever and make the count meaningless.
            for (int dx = -20; dx <= 20; dx++) {
                for (int dz = -20; dz <= 20; dz++) {
                    level.setBlock(origin.offset(dx, -1, dz),
                        net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
                }
            }

            var feature = com.flatts.recompile.registry.RCFeatures.GARBAGE_MOUND.get();
            int piles = 0;
            int bags = 0;
            int garbage = 0;
            // ENOUGH MOUNDS TO BE A MEASUREMENT RATHER THAN A COIN FLIP. One mound at 10 percent of
            // its surface could plausibly roll none; a dozen could not.
            for (int seed = 0; seed < 12; seed++) {
                for (int dx = -20; dx <= 20; dx++) {
                    for (int dy = 0; dy <= 18; dy++) {
                        for (int dz = -20; dz <= 20; dz++) {
                            level.setBlock(origin.offset(dx, dy, dz),
                                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
                feature.place(
                    net.minecraft.world.level.levelgen.feature.configurations
                        .NoneFeatureConfiguration.INSTANCE,
                    level, level.getChunkSource().getGenerator(),
                    net.minecraft.util.RandomSource.create(1000L + seed), origin);
                for (int dx = -20; dx <= 20; dx++) {
                    for (int dy = 0; dy <= 18; dy++) {
                        for (int dz = -20; dz <= 20; dz++) {
                            var state = level.getBlockState(origin.offset(dx, dy, dz));
                            if (state.is(RCBlocks.CARDBOARD_PILE.get())) {
                                piles++;
                            } else if (state.is(RCBlocks.TRASH_BAG.get())) {
                                bags++;
                            } else if (state.is(RCBlocks.GARBAGE_BLOCK.get())) {
                                garbage++;
                            }
                        }
                    }
                }
            }

            helper.assertTrue(garbage > 0,
                "no mound generated at all, so this measured nothing - the placement below would "
                    + "have 'passed' against a feature that writes no blocks");
            helper.assertTrue(piles > 0,
                "twelve mounds generated " + garbage + " garbage blocks and " + bags + " trash bags "
                    + "and not one cardboard pile, so cardboard has no source in the world however "
                    + "well its recipes and its rate arithmetic check out");

            // THE BAG IS THE YARDSTICK, not a fixed number. Both come out of one roll at 0.22 and
            // 0.10, so piles should be roughly half the bags whatever the surface budget is retuned
            // to; a bare count would need editing every time either dial moved. Wide band because
            // twelve mounds is a small sample and this is guarding a branch, not a balance point.
            helper.assertTrue(piles < bags,
                "cardboard piles (" + piles + ") outnumber trash bags (" + bags + "), but they share "
                    + "one roll with cardboard taking the smaller band - so either the bands are the "
                    + "wrong way round or the roll is not shared any more");
            helper.succeed();
        });
    }
}

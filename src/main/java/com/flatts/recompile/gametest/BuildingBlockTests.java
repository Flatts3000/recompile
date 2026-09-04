package com.flatts.recompile.gametest;

import com.flatts.recompile.content.worldgen.MoundFeature;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import com.flatts.recompile.Recompile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

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

        // WALLS MUST BE IN #minecraft:walls, AND THIS IS NOT BOOKKEEPING - it is the only thing that
        // makes one wall connect to the next. WallBlock.connectsTo reads
        //   state.is(BlockTags.WALLS) || !isExceptionForConnection(state) && faceSolid || ...
        // and a wall's side face is NOT sturdy, so the tag is the sole route for wall-to-wall. All
        // five shipped without it and stood as separate posts; reported from playtest by a player
        // ("the walls aren't connecting") rather than by anything here, because every other check
        // passes - the block registers, models, drops and places perfectly well as an island.
        //
        // Derived from the REGISTRY, so a sixth wall is covered the day it is registered rather than
        // the day somebody remembers this file. Stairs and slabs ride along: nothing vanilla needs
        // those tags for them to work, which is exactly why they would go missing unnoticed.
        RCGameTests.test("every_building_family_block_is_in_its_vanilla_tag", 20, helper -> {
            // BOTH HALVES, and the item half is the one that would drift. Vanilla ships a block
            // tag and an item tag for each of these families; only the block half changes anything
            // in play, so a sixth wall added to the block tag turns this green while the item tag
            // stays at five with nothing to say so - which is the same "nothing needs it, so it goes
            // missing unnoticed" failure that produced the bug this test exists for.
            record Family(String name, Class<?> type, TagKey<Block> blockTag, TagKey<Item> itemTag) {}
            List<Family> families = List.of(
                new Family("wall", WallBlock.class, BlockTags.WALLS, ItemTags.WALLS),
                new Family("stairs", StairBlock.class, BlockTags.STAIRS, ItemTags.STAIRS),
                new Family("slab", SlabBlock.class, BlockTags.SLABS, ItemTags.SLABS));

            // THE ONE EXEMPTION, and it is a justified entry rather than a loosened check. A tire
            // (#155) is a SlabBlock because that is the shape and the stacking behaviour it wants, and
            // it is not a building family member: it is a found object out of a dump, it has no block,
            // stairs or wall siblings, and nothing should be able to stonecut or craft with it as if it
            // were masonry. Putting it in #minecraft:slabs would tell every mod reading that family
            // that a tire is a building slab, which is the opposite of true.
            //
            // Add here only for a block that reuses a family CLASS without joining the family. Anything
            // that is actually a building block belongs in the tag, which is what this sweep is for.
            List<String> notABuildingFamily = List.of("recompile:tire");

            List<String> missing = new ArrayList<>();
            int checked = 0;
            for (Block block : BuiltInRegistries.BLOCK) {
                Identifier id = BuiltInRegistries.BLOCK.getKey(block);
                if (id == null || !Recompile.MOD_ID.equals(id.getNamespace())) {
                    continue;
                }
                if (notABuildingFamily.contains(id.toString())) {
                    continue;
                }
                for (Family family : families) {
                    if (!family.type().isInstance(block)) {
                        continue;
                    }
                    checked++;
                    if (!block.defaultBlockState().is(family.blockTag())) {
                        missing.add(id + " is a " + family.name() + " outside "
                            + family.blockTag().location());
                    }
                    // A block with no item form has nothing to tag; RegistryCompletenessTests owns
                    // that case, and asking here would report it twice in different words.
                    Item item = block.asItem();
                    if (item != Items.AIR && !item.getDefaultInstance().is(family.itemTag())) {
                        missing.add(id + " is a " + family.name() + " whose ITEM is outside "
                            + family.itemTag().location());
                    }
                }
            }
            helper.assertTrue(checked >= 15,
                "only " + checked + " family blocks found - discovery is broken, so this would pass "
                    + "by checking nothing");
            helper.assertTrue(missing.isEmpty(),
                "these are outside their vanilla family tag; for a wall's BLOCK tag that means it "
                    + "will not connect to its neighbours, and for an item tag it means anything "
                    + "reading the family will not see it: " + missing);
            helper.succeed();
        });

        // And the behaviour the tag exists for, asserted directly rather than inferred from the tag.
        // A tag present but somehow unread would pass the sweep above and still leave a row of posts,
        // so this asks the block what it actually does with a neighbour.
        RCGameTests.test("two_walls_side_by_side_connect", 20, helper -> {
            BlockPos west = new BlockPos(1, 1, 1);
            BlockPos east = new BlockPos(2, 1, 1);
            helper.setBlock(west, RCBlocks.SCRAP_PLATING_WALL.get());
            helper.setBlock(east, RCBlocks.SCRAP_PLATING_WALL.get());

            // setBlock writes the default state without running placement or neighbour logic, so
            // recompute the way the game does when a block is placed beside another.
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(west);
            BlockState connected = Block.updateFromNeighbourShapes(level.getBlockState(abs), level, abs);

            helper.assertTrue(connected.getValue(WallBlock.EAST) != WallSide.NONE,
                "a wall with another wall to its east must connect toward it, got "
                    + connected.getValue(WallBlock.EAST)
                    + " - NONE is what the playtest saw as a row of separate posts");
            helper.succeed();
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
            //
            // SIZED TO WHAT A MOUND CAN ACTUALLY OCCUPY, not generously. MAX_WIDTH is 15 and
            // MAX_HEIGHT is 15, so a mound centred here never reaches past 8 in any direction. The
            // first version used 20 and 400,000 setBlock calls, which spills well outside a
            // 5x5x5 plot into whatever test GameTest laid out next door - a failure that would
            // reproduce only under one plot layout and look like a flake.
            final int r = 8;
            final int h = MoundFeature.MAX_HEIGHT + 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
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
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = 0; dy <= h; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
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
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = 0; dy <= h; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
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

            // THE BAG IS THE YARDSTICK, not a fixed number. Both come out of one roll, cardboard
            // taking the smaller band (0.05 against 0.22 today), so piles stay the minority however
            // the surface budget is retuned; a bare count would need editing every time either dial
            // moved. Deliberately only an inequality: twelve mounds is a small sample, and this is
            // guarding a branch rather than a balance point - FindRateTest owns the numbers.
            helper.assertTrue(piles < bags,
                "cardboard piles (" + piles + ") outnumber trash bags (" + bags + "), but they share "
                    + "one roll with cardboard taking the smaller band - so either the bands are the "
                    + "wrong way round or the roll is not shared any more");

            // PUT THE PLOT BACK. GameTest restores only the structure bounds, and this wrote well
            // outside them; leaving a dozen mounds' worth of garbage standing there is how a later
            // batch inherits a dirty floor.
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -1; dy <= h; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        level.setBlock(origin.offset(dx, dy, dz),
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            helper.succeed();
        });

        // EVERY BLOCK A MOUND PLACES MUST BE RECOGNISED AS MOUND CONTENT.
        //
        // MoundFeature.writeBed lays the regrowth bed UNDER a mound, and bails when the cell it is
        // about to write already holds part of a mound - because mounds overlap, and overwriting a
        // neighbour's stack punches a hole in it. That guard was two instanceof checks, complete
        // only while every mound block was a SortableBlock or Bulky Waste. The Cardboard Pile is
        // neither: a plain FallingBlock, and a full opaque cube, so it passed isSolidRender() and
        // fell through - a later mound would have replaced a pile with Mound Ground, destroying it
        // and planting a regrowth bed partway up a stack. Nothing logged, and invisible until
        // somebody quarried that mound and watched it come back from the middle.
        //
        // So the list is swept rather than trusted. A new mound variant that forgets this fails
        // here instead of shipping.
        RCGameTests.test("every_block_a_mound_places_is_recognised_as_mound_content", 20, helper -> {
            java.util.List<String> unguarded = new java.util.ArrayList<>();
            for (var state : MoundFeature.everyMoundBlock()) {
                if (!MoundFeature.isMoundContent(state)) {
                    unguarded.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(state.getBlock()).toString());
                }
            }
            helper.assertTrue(unguarded.isEmpty(),
                unguarded + " are placed into mounds but writeBed does not recognise them as mound "
                    + "content, so an overlapping mound will overwrite them with Mound Ground - "
                    + "destroying the block and seeding regrowth at the wrong height, silently");
            helper.succeed();
        });
    }
}

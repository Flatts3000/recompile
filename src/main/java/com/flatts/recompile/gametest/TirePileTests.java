package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.content.recipe.TeardownRecipe;
import com.flatts.recompile.content.block.MoundGroundBlock;
import com.flatts.recompile.content.block.TireBlock;
import com.flatts.recompile.content.worldgen.TirePileFeature;
import net.minecraft.core.Direction;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Tire piles (spec {@code docs/tire_piles_spec.md}, #155).
 *
 * <p>The tests here are for the claims a player would notice breaking and nothing else would: what a
 * tire drops depends on the tool, the fire never goes out, and the fire never eats the tire.
 */
public final class TirePileTests {

    private TirePileTests() {
    }

    private static ResourceKey<LootTable> tireLoot() {
        return ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "blocks/tire"));
    }

    /** Roll the tire's block table with a given tool in hand. */
    private static List<ItemStack> dropsWith(GameTestHelper helper, BlockState state, ItemStack tool) {
        var level = helper.getLevel();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(tireLoot());
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.BLOCK_STATE, state)
            .withParameter(LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(BlockPos.ZERO))
            .withParameter(LootContextParams.TOOL, tool)
            .create(LootContextParamSets.BLOCK);
        return table.getRandomItems(params);
    }

    static void register() {

        // THE TOOL GATE, which is the whole harvest design and lives entirely in one loot file. A hand
        // gets the tire, a knife gets rubber. Rolled through the real table rather than asserted off
        // the JSON, because the ORDER of the alternatives children is what makes it work: with the tire
        // entry first a knife would yield a tire and the gate would silently not exist.
        RCGameTests.test("a_tire_gives_rubber_to_a_knife_and_a_tire_to_a_hand", 20, helper -> {
            BlockState bottom = RCBlocks.TIRE.get().defaultBlockState();

            List<ItemStack> byHand = dropsWith(helper, bottom, ItemStack.EMPTY);
            helper.assertTrue(byHand.stream().anyMatch(s -> s.is(RCItems.TIRE.get())),
                "breaking a tire by hand must give the tire, got " + byHand);
            helper.assertTrue(byHand.stream().noneMatch(s -> s.is(RCItems.RUBBER_SCRAP.get())),
                "a bare hand must not strip rubber, got " + byHand);

            List<ItemStack> byKnife = dropsWith(helper, bottom, new ItemStack(RCItems.SCRAP_KNIFE.get()));
            helper.assertTrue(byKnife.stream().anyMatch(s -> s.is(RCItems.RUBBER_SCRAP.get())),
                "breaking a tire with a Scrap Knife must give rubber, got " + byKnife);
            helper.assertTrue(byKnife.stream().noneMatch(s -> s.is(RCItems.TIRE.get())),
                "a knife strips the tire rather than recovering it, got " + byKnife);

            // A shovel is neither: it must behave as a hand rather than as a knife.
            List<ItemStack> byShovel = dropsWith(helper, bottom, new ItemStack(net.minecraft.world.item.Items.IRON_SHOVEL));
            helper.assertTrue(byShovel.stream().anyMatch(s -> s.is(RCItems.TIRE.get())),
                "only the knife is the gate; any other tool gives the tire, got " + byShovel);
            helper.succeed();
        });

        // A DOUBLE SLAB IS TWO TIRES and must pay out as two, or half a stack vanishes on every break.
        RCGameTests.test("a_double_tire_pays_out_twice", 20, helper -> {
            BlockState doubled = RCBlocks.TIRE.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                    net.minecraft.world.level.block.state.properties.SlabType.DOUBLE);
            int tires = dropsWith(helper, doubled, ItemStack.EMPTY).stream()
                .filter(s -> s.is(RCItems.TIRE.get())).mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(tires == 2, "a double tire must give two tires, got " + tires);
            int rubber = dropsWith(helper, doubled, new ItemStack(RCItems.SCRAP_KNIFE.get())).stream()
                .filter(s -> s.is(RCItems.RUBBER_SCRAP.get())).mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(rubber == 3, "a double tire knifed must give three rubber, got " + rubber);
            helper.succeed();
        });

        // NETHERRACK SEMANTICS, HALF ONE: the fire does not go out. Asserted through the block's own
        // answer rather than by waiting on a fire tick, because a test that waits would pass on a
        // broken implementation for as long as the fire happened to survive.
        RCGameTests.test("fire_on_a_tire_never_goes_out", 100, helper -> {
            BlockPos at = new BlockPos(2, 1, 2);
            helper.setBlock(at, RCBlocks.TIRE.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                    net.minecraft.world.level.block.state.properties.SlabType.DOUBLE));
            helper.setBlock(at.above(), Blocks.FIRE.defaultBlockState());

            BlockPos abs = helper.absolutePos(at);
            helper.assertTrue(helper.getLevel().getBlockState(abs)
                    .isFireSource(helper.getLevel(), abs, net.minecraft.core.Direction.UP),
                "a tire must answer isFireSource, or FireBlock burns the fire out and extinguishes it "
                    + "in rain - and this world rains");

            // And it is still burning after enough ticks for an ordinary fire to have died.
            helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.FIRE, at.above()));
        });

        // NETHERRACK SEMANTICS, HALF TWO: the fire does not eat the tire. If it did, every dump would
        // be a slow leak of the only rubber in the game.
        RCGameTests.test("fire_does_not_consume_the_tire", 100, helper -> {
            BlockPos at = new BlockPos(2, 1, 2);
            helper.setBlock(at, RCBlocks.TIRE.get().defaultBlockState());
            helper.setBlock(at.above(), Blocks.FIRE.defaultBlockState());
            BlockPos abs = helper.absolutePos(at);
            helper.assertTrue(helper.getLevel().getBlockState(abs)
                    .getFlammability(helper.getLevel(), abs, net.minecraft.core.Direction.UP) == 0,
                "a tire must not be flammable, or the pile burns itself away");
            helper.succeedWhen(() -> helper.assertBlockPresent(RCBlocks.TIRE.get(), at));
        });

        // THE TIRE IS NOT A SortableBlock, which decides three separate things: no gravity (that class
        // extends FallingBlock), no pull stream, and MoundGroundBlock.isMound never counting a tire as
        // part of a mound. All three would regress silently if somebody "tidied" the hierarchy.
        RCGameTests.test("a_tire_is_a_plain_block_not_a_sortable_one", 20, helper -> {
            // isAssignableFrom rather than instanceof: the compiler already knows a TireBlock is
            // neither, and rejects the instanceof outright. Asking the classes keeps the claim written
            // down where a future hierarchy change would trip over it.
            Class<?> tire = RCBlocks.TIRE.get().getClass();
            helper.assertTrue(!SortableBlock.class.isAssignableFrom(tire),
                "a tire must not be a SortableBlock: it would inherit FallingBlock's gravity and would "
                    + "be counted as mound by MoundGroundBlock.isMound");
            helper.assertTrue(!net.minecraft.world.level.block.FallingBlock.class.isAssignableFrom(tire),
                "and it must not fall - a tipped stack stays where it was tipped");
            helper.succeed();
        });

        // BOTH PROCESSING ROUTES EXIST, and the hand route is the only one that recovers the belts.
        // That asymmetry is what stops the Pulverizer making the knife pointless, so it is asserted.
        RCGameTests.test("the_bench_recovers_steel_belts_and_the_mill_does_not", 20, helper -> {
            RecipeHolder<TeardownRecipe> teardown = null;
            for (RecipeHolder<TeardownRecipe> holder : helper.getLevel().recipeAccess()
                    .recipeMap().byType(RCRecipeTypes.TEARDOWN.get())) {
                if (holder.value().input().test(new ItemStack(RCItems.TIRE.get()))) {
                    teardown = holder;
                    break;
                }
            }
            helper.assertTrue(teardown != null, "no teardown accepts a tire");
            List<net.minecraft.world.item.Item> out = teardown.value().everyPossibleOutput().toList();
            helper.assertTrue(out.contains(RCItems.RUBBER_SCRAP.get()),
                "the tire teardown must yield rubber, got " + out);
            helper.assertTrue(out.contains(RCItems.SCRAP_METAL.get()),
                "the tire teardown is the only route to the steel belts, got " + out);
            helper.succeed();
        });

        // RUBBER HAS A CONSUMER THAT DOES NOT NEED CREATE, which is the one thing #155 said must not be
        // skipped. Without a mod-side use, rubber is a dead end in every install lacking that mod.
        RCGameTests.test("rubber_is_spent_by_something_this_mod_ships", 20, helper -> {
            List<String> uses = new ArrayList<>();
            for (var entry : com.flatts.recompile.compat.BlueprintData.all()) {
                boolean spendsRubber = entry.ingredients().stream()
                    .anyMatch(i -> i.isPresent()
                        && i.get().test(new ItemStack(RCItems.RUBBER_SCRAP.get())));
                if (spendsRubber) {
                    uses.add(entry.blueprint().toString());
                }
            }
            helper.assertTrue(!uses.isEmpty(),
                "nothing this mod ships spends rubber scrap, so it is a dead end in every install "
                    + "without Create - which #155 named as the one thing that must not happen");
            helper.succeed();
        });

        // AND IT IS NOT A FUEL, either as a tire or as rubber (owner, 2026-09-04). Piles do not
        // replenish, so a furnace entry would pair a finite material with an infinite sink and empty
        // the world quietly.
        RCGameTests.test("neither_tires_nor_rubber_burn_as_fuel", 20, helper -> {
            var fuels = helper.getLevel().fuelValues();
            helper.assertTrue(fuels.burnDuration(new ItemStack(RCItems.TIRE.get())) == 0,
                "a tire must not be furnace fuel: dumps do not replenish and a furnace is bottomless");
            helper.assertTrue(fuels.burnDuration(new ItemStack(RCItems.RUBBER_SCRAP.get())) == 0,
                "rubber must not be furnace fuel either, for the same reason");
            helper.succeed();
        });

        // THE FEATURE REFUSES A MOUND. Owner rule, and the one that would corrupt the look of a mound
        // field if it regressed. Placed by hand rather than by generating a chunk, so the test is about
        // the rule rather than about worldgen luck.
        //
        // THE FIELD MUST BE WIDER THAN THE SCATTER, and the first version was not. It laid garbage
        // across the 5x5 plot while a dump throws piles up to SPREAD blocks out, so most piles landed
        // on bare plot floor beyond the garbage and the test passed on the ground probe's old reach
        // rather than on the mound rule at all. Widening that probe made it fail, which is the only
        // reason anybody looked.
        RCGameTests.test("a_tire_dump_never_stands_in_a_mound", 60, helper -> {
            var level = helper.getLevel();
            // ITS OWN Y BAND. These three tests each lay a field WIDER than the shared plot, and the
            // harness sets plots about a dozen blocks apart, so at a common height their footprints
            // overlap each other's neighbours - and nothing outside a plot is cleaned up between runs.
            // A leftover tire under this one would be found by groundAt, permitted by clear(), and
            // would flip the assertion below. Bands of 40 keep them apart, the way the aquarium's
            // builds already do.
            final int floor = 40;
            for (int x = -12; x <= 14; x++) {
                for (int z = -12; z <= 14; z++) {
                    level.setBlock(helper.absolutePos(new BlockPos(x, floor, z)),
                        RCBlocks.GARBAGE_BLOCK.get().defaultBlockState(), 2);
                }
            }
            BlockPos origin = helper.absolutePos(new BlockPos(2, floor + 1, 2));
            boolean placed = new TirePileFeature().place(
                new net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<>(
                    java.util.Optional.empty(), level, level.getChunkSource().getGenerator(),
                    RandomSource.create(9L), origin, NoneFeatureConfiguration.INSTANCE));
            helper.assertTrue(!placed,
                "a tire dump placed itself on a field of garbage blocks; piles must refuse a mound");
            helper.succeed();
        });

        // A DUMP LANDS ON MOUND GROUND AND TAKES THE MEMORY WITH IT. This is the regression guard for
        // the defect that shipped in the first draft of the feature and could not be seen from inside
        // the game: the survey also refused Mound Ground, which reads like the owner's rule and is
        // instead a total ban, because a census of freshly generated sprawl put Mound Ground under 943
        // and 884 of 1024 columns. Nothing failed. Six hand-placed features in a row simply returned
        // false, which is exactly what a rare feature looks like.
        //
        // Built forty blocks up because a dump scatters its piles up to SPREAD from the origin, which
        // is wider than a harness plot; at that height the neighbouring plots are open air.
        RCGameTests.test("a_tire_dump_lands_on_mound_ground_and_retires_it", 100, helper -> {
            var level = helper.getLevel();
            final int lift = 80;   // its own band - see a_tire_dump_never_stands_in_a_mound
            for (int x = -10; x <= 12; x++) {
                for (int z = -10; z <= 12; z++) {
                    level.setBlock(helper.absolutePos(new BlockPos(x, lift, z)),
                        RCBlocks.MOUND_GROUND.get().defaultBlockState(), 2);
                }
            }
            BlockPos origin = helper.absolutePos(new BlockPos(2, lift + 1, 2));
            boolean placed = new TirePileFeature().place(
                new net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<>(
                    java.util.Optional.empty(), level, level.getChunkSource().getGenerator(),
                    RandomSource.create(4L), origin, NoneFeatureConfiguration.INSTANCE));
            helper.assertTrue(placed,
                "a tire dump refused a plain field of Mound Ground. That is 86 to 92 percent of the "
                    + "sprawl surface, so refusing it is not rarity, it is never.");

            int tires = 0;
            for (int x = -10; x <= 12; x++) {
                for (int z = -10; z <= 12; z++) {
                    BlockPos ground = helper.absolutePos(new BlockPos(x, lift, z));
                    if (!(level.getBlockState(ground.above()).getBlock() instanceof TireBlock)) {
                        continue;
                    }
                    tires++;
                    helper.assertTrue(
                        !(level.getBlockState(ground).getBlock() instanceof MoundGroundBlock),
                        "Mound Ground survived under a tire at " + ground + ". It would keep ticking "
                            + "and drop Blocks of Garbage onto the pile from above.");
                }
            }
            helper.assertTrue(tires > 0, "the dump reported success and wrote no tires");
            helper.succeed();
        });

        // NOTHING FLOATS. Owner, 2026-09-04, from a screenshot - and it took two separate defects to
        // produce, which is why an earlier "is any tire over air" check came back clean on 1,070 tires
        // and the picture still showed a gap. Both are half-block problems that whole-block arithmetic
        // cannot see:
        //
        //   1. A column of odd height ends in a BOTTOM slab, filling the lower half of its cell. Piles
        //      overlap, so a later pile would start in the cell ABOVE that tire and hang half a block
        //      clear of it. The block below was a tire either way, so "is there air under it" passed.
        //   2. Fire is a whole block and can only sit in the cell above the stack. On a column ending
        //      in a BOTTOM slab that is half a block clear of the rubber, and it reads as a flame
        //      hanging in the air.
        //
        // So this measures SUPPORT rather than occupancy: every tire wants a full top under it, and
        // every fire wants one too.
        RCGameTests.test("nothing_in_a_tire_dump_floats", 100, helper -> {
            var level = helper.getLevel();
            final int lift = 120;  // its own band - see a_tire_dump_never_stands_in_a_mound
            for (int x = -10; x <= 12; x++) {
                for (int z = -10; z <= 12; z++) {
                    level.setBlock(helper.absolutePos(new BlockPos(x, lift, z)),
                        Blocks.COARSE_DIRT.defaultBlockState(), 2);
                }
            }
            // Several dumps over one another, because overlap is what produced the defect. One dump
            // on clean ground never lands on its own half-filled cell.
            for (long seed : new long[] {3L, 11L, 19L, 27L}) {
                new TirePileFeature().place(
                    new net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<>(
                        java.util.Optional.empty(), level, level.getChunkSource().getGenerator(),
                        RandomSource.create(seed), helper.absolutePos(new BlockPos(2, lift + 1, 2)),
                        NoneFeatureConfiguration.INSTANCE));
            }

            int tires = 0;
            int fires = 0;
            for (int x = -10; x <= 12; x++) {
                for (int z = -10; z <= 12; z++) {
                    for (int y = lift + 1; y <= lift + 24; y++) {
                        BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                        BlockState state = level.getBlockState(pos);
                        boolean isTire = state.getBlock() instanceof TireBlock;
                        boolean isFire = state.getBlock() == Blocks.FIRE;
                        if (!isTire && !isFire) {
                            continue;
                        }
                        BlockState below = level.getBlockState(pos.below());
                        boolean flush = below.isFaceSturdy(level, pos.below(), Direction.UP);
                        if (isTire) {
                            tires++;
                            helper.assertTrue(flush,
                                "a tire at " + pos + " rests on " + below.getBlock()
                                    + ", whose top face is not full. It hangs in the air.");
                        } else {
                            fires++;
                            helper.assertTrue(flush,
                                "fire at " + pos + " sits over " + below.getBlock()
                                    + ", whose top face is not full. The flame floats.");
                        }
                    }
                }
            }
            helper.assertTrue(tires > 0, "four dumps in a row wrote no tires, so this proved nothing");
            helper.succeed();
        });
    }
}

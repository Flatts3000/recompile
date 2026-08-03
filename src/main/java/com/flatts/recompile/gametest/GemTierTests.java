package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.MechanicalWasteBlock;
import com.flatts.recompile.content.block.SeparatorCoreBlock;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.content.recipe.SeparatingRecipe;
import com.flatts.recompile.content.recipe.TeardownRecipe;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The gem tier: Mechanical Waste, the Separator, and the gate that keeps them honest
 * ({@code docs/gem_tier_spec.md}).
 */
final class GemTierTests {

    /** Everything past the iron gate. Nothing may reach these except through the Separator. */
    private static final Set<Item> GATED = Set.of(
        Items.DIAMOND, Items.EMERALD, Items.LAPIS_LAZULI, Items.REDSTONE, Items.GOLD_INGOT,
        Items.GOLD_NUGGET, Items.AMETHYST_SHARD);

    private GemTierTests() {
    }

    static void register() {
        // The tier's whole shape: the pile is the found half, the Separator is the refined half. A pile
        // that handed out a diamond directly would be a rarer loot table rather than a tier.
        RCGameTests.test("mechanical_waste_never_drops_a_gem", 40, helper -> {
            BlockPos pos = new BlockPos(1, 2, 1);
            helper.setBlock(pos, RCBlocks.MECHANICAL_WASTE.get());
            List<Item> seen = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                helper.setBlock(pos, RCBlocks.MECHANICAL_WASTE.get());
                SortableBlock.sortOnce(helper.getLevel(), helper.absolutePos(pos));
                for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class, AABB.ofSize(helper.absolutePos(pos).getCenter(), 6, 6, 6))) {
                    seen.add(entity.getItem().getItem());
                    entity.discard();
                }
            }
            helper.assertTrue(seen.size() > 50,
                "only " + seen.size() + " pulls came out - the pile is not yielding, so this test would "
                    + "pass against a table that drops diamonds");
            for (Item item : seen) {
                helper.assertFalse(GATED.contains(item),
                    "Mechanical Waste dropped " + item + " directly. Gems are separated OUT of the "
                        + "scrap, never found in it");
            }
            helper.succeed();
        });

        // The #91 lesson, generalised. That gate died because it was built from the absence of a
        // material; this asserts the absence of a ROUTE, which is a thing a test can actually hold.
        RCGameTests.test("no_teardown_recipe_yields_a_gated_material", 20, helper -> {
            List<String> leaks = new ArrayList<>();
            int checked = 0;
            for (RecipeHolder<TeardownRecipe> holder : helper.getLevel().recipeAccess()
                    .recipeMap().byType(RCRecipeTypes.TEARDOWN.get())) {
                checked++;
                List<TeardownRecipe.ItemResult> all = new ArrayList<>(holder.value().results());
                for (var extra : holder.value().extras()) {
                    if (GATED.contains(extra.item())) {
                        leaks.add(holder.id() + " rolls " + extra.item());
                    }
                }
                for (TeardownRecipe.ItemResult result : all) {
                    if (GATED.contains(result.item())) {
                        leaks.add(holder.id() + " yields " + result.item());
                    }
                }
            }
            helper.assertTrue(checked > 0,
                "no teardown recipes were found at all - discovery is broken, so this would pass "
                    + "against a mod that tears a jukebox down into its diamond");
            helper.assertTrue(leaks.isEmpty(),
                "teardown recipes that open the gem tier (" + leaks.size() + "): " + leaks
                    + ". The Separator is the sanctioned route; teardown is not");
            helper.succeed();
        });

        // Every separating recipe must consume more than one item, or the tier is not a tier.
        RCGameTests.test("every_separating_recipe_concentrates", 20, helper -> {
            List<String> problems = new ArrayList<>();
            int checked = 0;
            for (RecipeHolder<SeparatingRecipe> holder : helper.getLevel().recipeAccess()
                    .recipeMap().byType(RCRecipeTypes.SEPARATING.get())) {
                checked++;
                if (holder.value().count() < 2) {
                    problems.add(holder.id() + " consumes " + holder.value().count());
                }
                if (holder.value().results().isEmpty()) {
                    problems.add(holder.id() + " produces nothing");
                }
            }
            helper.assertTrue(checked >= 3,
                "only " + checked + " separating recipes - the tier ships three");
            helper.assertTrue(problems.isEmpty(), "separating recipes that do not concentrate: " + problems);
            helper.succeed();
        });

        // The viewers must describe the machine the game actually runs. SeparatingData is the only
        // JEI logic a GameTest can reach, and it is the half that has been wrong before: TeardownData
        // named its recipes in a constant, a third shipped, and every viewer denied it existed.
        RCGameTests.test("jei_sees_every_separating_recipe_with_its_real_count", 20, helper -> {
            var rows = com.flatts.recompile.compat.SeparatingData.all();
            int inGame = 0;
            for (RecipeHolder<SeparatingRecipe> holder : helper.getLevel().recipeAccess()
                    .recipeMap().byType(RCRecipeTypes.SEPARATING.get())) {
                inGame++;
                boolean matched = false;
                for (var row : rows) {
                    if (holder.value().matches(new net.minecraft.world.item.crafting.SingleRecipeInput(
                            row.input()), helper.getLevel())
                            && row.input().getCount() == holder.value().count()) {
                        matched = true;
                    }
                }
                helper.assertTrue(matched,
                    "JEI does not show " + holder.id() + " at its real input count of "
                        + holder.value().count() + ". A row showing one item describes a different "
                        + "machine, because the count IS the tier");
            }
            helper.assertTrue(inGame > 0 && rows.size() == inGame,
                "the game runs " + inGame + " separating recipes and JEI reads " + rows.size());
            helper.succeed();
        });

        // The machine, end to end: form it, power it, feed it, and check the chute.
        RCGameTests.test("separator_grinds_a_feed_into_its_raw_material", 260, helper -> {
            BlockPos core = new BlockPos(1, 2, 1);
            helper.setBlock(core, RCBlocks.SEPARATOR.get());
            buildAround(helper, core);
            helper.assertTrue(MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core)),
                "the Separator did not form from its components");

            var be = (com.flatts.recompile.content.block.entity.SeparatorBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(core));
            helper.assertTrue(be != null, "no Separator BlockEntity");
            try (Transaction tx = Transaction.openRoot()) {
                be.battery().insert(1_000_000, tx);
                tx.commit();
            }

            BlockPos intake = SeparatorCoreBlock.chamberCells(
                helper.getLevel(), helper.absolutePos(core)).get(0).above();
            ItemEntity feed = new ItemEntity(helper.getLevel(), intake.getX() + 0.5, intake.getY() + 0.5,
                intake.getZ() + 0.5, new ItemStack(RCItems.QUARTZ_GRIT.get(), 12));
            // ItemEntity's constructor gives it a random shove. In the world a dropped stack settles;
            // here it would drift out of the machine's mouth mid-test.
            feed.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            feed.setNoGravity(true);
            helper.getLevel().addFreshEntity(feed);

            // Checked separately so a failure says WHICH half broke. "no amethyst reached the chute"
            // is a useless message when the machine never started.
            helper.runAfterDelay(20, () -> {
                BlockState now = helper.getLevel().getBlockState(helper.absolutePos(core));
                helper.assertTrue(now.getValue(SeparatorCoreBlock.ACTIVE),
                    "the Separator never started: formed=" + now.getValue(MultiblockCoreBlock.FORMED)
                        + ", stored FE=" + be.battery().getAmountAsInt()
                        + ", feed entities above intake="
                        + helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                            SeparatorCoreBlock.mouth(
                                helper.getLevel(), helper.absolutePos(core))).size());
            });

            helper.runAfterDelay(230, () -> {
                boolean found = false;
                for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        AABB.ofSize(helper.absolutePos(core).getCenter(), 12, 12, 12))) {
                    if (entity.getItem().is(Items.AMETHYST_SHARD)) {
                        found = true;
                    }
                }
                helper.assertTrue(found,
                    "the Separator ran but no amethyst reached the chute");
                helper.succeed();
            });
        });

        // A hopper on the chamber is the first thing anyone reaches for, and pointing one down at the
        // machine does nothing, because the chamber is not a Container. The machine drains it instead.
        RCGameTests.test("separator_drains_a_container_on_its_chamber", 260, helper -> {
            BlockPos core = new BlockPos(1, 2, 1);
            helper.setBlock(core, RCBlocks.SEPARATOR.get());
            buildAround(helper, core);
            MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core));
            var be = (com.flatts.recompile.content.block.entity.SeparatorBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(core));
            try (Transaction tx = Transaction.openRoot()) {
                be.battery().insert(1_000_000, tx);
                tx.commit();
            }

            BlockPos above = SeparatorCoreBlock.chamberCells(
                helper.getLevel(), helper.absolutePos(core)).get(0).above();
            helper.getLevel().setBlockAndUpdate(above, Blocks.HOPPER.defaultBlockState());
            var hopper = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(above);
            helper.assertTrue(hopper != null, "no hopper container above the chamber");
            hopper.setItem(0, new ItemStack(RCItems.QUARTZ_GRIT.get(), 12));

            helper.runAfterDelay(230, () -> {
                boolean found = false;
                for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        AABB.ofSize(helper.absolutePos(core).getCenter(), 12, 12, 12))) {
                    if (entity.getItem().is(Items.AMETHYST_SHARD)) {
                        found = true;
                    }
                }
                helper.assertTrue(found,
                    "a hopper of Quartz Grit sat on the chamber and nothing came out. The machine has "
                        + "to pull, because nothing can push into it");
                helper.succeed();
            });
        });

        // No Container, no item capability, on every face AND on the null side - the
        // WorldlyContainerWrapper trap the automation policy records.
        RCGameTests.test("the_separator_is_unreachable_by_pipe_and_hopper", 40, helper -> {
            BlockPos core = new BlockPos(1, 2, 1);
            helper.setBlock(core, RCBlocks.SEPARATOR.get());
            buildAround(helper, core);
            MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core));

            List<String> reachable = new ArrayList<>();
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                if (helper.getLevel().getCapability(
                        net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                        helper.absolutePos(core), side) != null) {
                    reachable.add(side.getName());
                }
            }
            if (helper.getLevel().getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                    helper.absolutePos(core), null) != null) {
                reachable.add("null side");
            }
            helper.assertTrue(reachable.isEmpty(),
                "the Separator exposes an item handler on " + reachable + ". It must expose none at "
                    + "all: a pipe decides whether to CONNECT on whether a handler exists, so one that "
                    + "always refuses reads as a broken machine rather than a manual one");

            helper.assertTrue(helper.getLevel().getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK,
                    helper.absolutePos(core), null) != null,
                "the Separator must expose Capabilities.Energy.BLOCK, or no generator can reach it");
            helper.succeed();
        });
    }

    /** Place the eleven components a north-facing Separator needs, leaving the core alone. */
    private static void buildAround(GameTestHelper helper, BlockPos core) {
        for (int x = 0; x < 3; x++) {
            helper.setBlock(core.offset(x, 1, 0), RCBlocks.STEEL_I_BEAM.get());
            helper.setBlock(core.offset(x, 1, 1), RCBlocks.MACHINE_FRAME.get());
            helper.setBlock(core.offset(x, 0, 1), RCBlocks.MACHINE_FRAME.get());
        }
        helper.setBlock(core.offset(1, 0, 0), RCBlocks.MACHINE_FRAME.get());
        helper.setBlock(core.offset(2, 0, 0), RCBlocks.MACHINE_FRAME.get());
    }
}

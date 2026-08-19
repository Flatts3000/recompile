package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.MechanicalWasteBlock;
import com.flatts.recompile.content.block.SeparatorCoreBlock;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.content.recipe.SeparatingRecipe;
import com.flatts.recompile.content.recipe.TeardownRecipe;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Vec3i;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The gem tier: Mechanical Waste, the Separator, and the gate that keeps them honest
 * ({@code docs/gem_tier_spec.md}).
 */
final class GemTierTests {

    /** Everything past the iron gate. None of it may be found lying in a pile. */
    private static final Set<Item> GATED = Set.of(
        Items.DIAMOND, Items.EMERALD, Items.LAPIS_LAZULI, Items.REDSTONE, Items.GOLD_INGOT,
        Items.GOLD_NUGGET, Items.AMETHYST_SHARD);

    /**
     * The same set minus lapis, for the teardown guard only.
     *
     * <p><b>Lapis moved to the Printer (owner, 2026-08-02, #112)</b> and that reverses this spec's
     * placement of it. The reasoning is worth keeping, because it also says where lapis must not go:
     * lapis is a <em>pigment</em> - ultramarine is ground lapis, and cyan toner is phthalocyanine blue -
     * so it belongs in a printer and does not belong in machinery, which contains no lapis at all.
     * Vanilla puts it at {@code needs_stone_tool}, beside iron and copper rather than beside diamond, so
     * arriving before the demolition yard is where vanilla already has it. And it gates nothing on its
     * own: its only real job is enchanting, which also needs obsidian and diamond, both still yard-gated.
     *
     * <p>Lapis stays in {@link #GATED} because the <em>pile</em> rule is unchanged - Mechanical Waste
     * must still never drop it. Only the teardown route opened.
     */
    private static final Set<Item> TEARDOWN_GATED = GATED.stream()
        .filter(item -> item != Items.LAPIS_LAZULI)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());

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
                // Pool entries count as outputs here too; see TeardownRecipe.everyPossibleOutput.
                holder.value().pools().stream().flatMap(pool -> pool.entries().stream())
                    .forEach(e -> e.item().ifPresent(i ->
                        all.add(new TeardownRecipe.ItemResult(i, e.count()))));
                for (var extra : holder.value().extras()) {
                    if (TEARDOWN_GATED.contains(extra.item())) {
                        leaks.add(holder.id() + " rolls " + extra.item());
                    }
                }
                for (TeardownRecipe.ItemResult result : all) {
                    if (TEARDOWN_GATED.contains(result.item())) {
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

        // The difficulty lives in the loot table now, not in the recipe (owner, 2026-08-03), so this
        // asserts the thing that actually keeps the tier honest: every scrap the Separator eats has to
        // be findable, and nothing it eats may be a vanilla gem. A recipe whose input has no source is
        // a dead machine that every test would otherwise call healthy.
        RCGameTests.test("every_separating_input_is_findable_scrap", 20, helper -> {
            List<String> problems = new ArrayList<>();
            int checked = 0;
            for (RecipeHolder<SeparatingRecipe> holder : helper.getLevel().recipeAccess()
                    .recipeMap().byType(RCRecipeTypes.SEPARATING.get())) {
                checked++;
                if (holder.value().results().isEmpty()) {
                    problems.add(holder.id() + " produces nothing");
                }
                boolean findable = false;
                // MACHINE-MADE FEEDS COUNT TOO, and each one is listed with its reason.
                //
                // The rule this test enforces is the comment above it: an input with no source is a
                // dead machine. It was IMPLEMENTED as "the input is industrial scrap" because for three
                // recipes those were the same sentence - every separating input came out of Mechanical
                // Waste. Slag (#236) is the first that does not: the Cupola rakes it off every eighth
                // smelt, which is a source, just not a loot table. A hardcoded list is honest here for
                // the reason RegistryCompletenessTests keeps two of them - a justified entry beats a
                // loosened check, because the next input with no source at all must still fail.
                for (var made : List.of(RCItems.SLAG)) {
                    if (holder.value().matches(
                            new net.minecraft.world.item.crafting.SingleRecipeInput(
                                new ItemStack(made.get())), helper.getLevel())) {
                        findable = true;
                    }
                }
                for (var scrap : RCItems.INDUSTRIAL_SCRAP) {
                    if (holder.value().matches(
                            new net.minecraft.world.item.crafting.SingleRecipeInput(
                                new ItemStack(scrap.get())), helper.getLevel())) {
                        findable = true;
                    }
                }
                if (!findable) {
                    problems.add(holder.id() + " eats something with no source - neither a Mechanical Waste drop nor a machine output");
                }
            }
            helper.assertTrue(checked >= 3,
                "only " + checked + " separating recipes - the tier ships three");
            helper.assertTrue(problems.isEmpty(), "broken separating recipes: " + problems);
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
        RCGameTests.test("separator_grinds_a_feed_into_its_raw_material", 120, helper -> {
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
                intake.getZ() + 0.5, new ItemStack(RCItems.QUARTZ_GRIT.get(), 4));
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

            helper.runAfterDelay(80, () -> {
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
        RCGameTests.test("separator_drains_a_container_on_its_chamber", 120, helper -> {
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
            hopper.setItem(0, new ItemStack(RCItems.QUARTZ_GRIT.get(), 4));

            helper.runAfterDelay(80, () -> {
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

        // The bay has to read as ONE opening. Four cells all showing quadrant 0 would tile the same
        // quarter four times, which is exactly the look this stamping exists to avoid, and nothing
        // else in the build would notice.
        RCGameTests.test("the_grinding_bay_stamps_four_distinct_quadrants", 40, helper -> {
            BlockPos core = new BlockPos(1, 2, 1);
            helper.setBlock(core, RCBlocks.SEPARATOR.get());
            buildAround(helper, core);
            helper.assertTrue(MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core)),
                "the Separator did not form");
            java.util.Set<Integer> seen = new java.util.HashSet<>();
            for (BlockPos cell : SeparatorCoreBlock.chamberCells(
                    helper.getLevel(), helper.absolutePos(core))) {
                BlockState state = helper.getLevel().getBlockState(cell);
                helper.assertTrue(state.is(RCBlocks.SEPARATOR_CHAMBER.get()),
                    "a bay cell did not form into a chamber");
                seen.add(state.getValue(
                    com.flatts.recompile.content.block.SeparatorChamberBlock.QUADRANT));
            }
            helper.assertTrue(seen.size() == 4,
                "the bay stamped " + seen.size() + " distinct quadrants, not 4: " + seen
                    + ". Repeating a quarter makes four blocks read as four grinders");
            helper.succeed();
        });

        // MULTIPLE TYPES AT ONCE, all queued (owner, 2026-08-03). The machine used to grind whatever
        // single recipe the bay happened to satisfy, so a mixed pile was a coin toss over which one it
        // saw. Feeding it three kinds at once has to leave three kinds queued and every one of them
        // eventually ground - that is the whole point of an internal queue over a bay scan.
        RCGameTests.test("the_separator_queues_several_kinds_of_scrap_at_once", 200, helper -> {
            BlockPos core = new BlockPos(1, 2, 1);
            helper.setBlock(core, RCBlocks.SEPARATOR.get());
            buildAround(helper, core);
            helper.assertTrue(MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core)),
                "the Separator did not form");
            var be = (com.flatts.recompile.content.block.entity.SeparatorBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(core));
            try (Transaction tx = Transaction.openRoot()) {
                be.battery().insert(1_000_000, tx);
                tx.commit();
            }

            // The three the tier actually ships a recipe for. E-Scrap deliberately is NOT one of them,
            // and an earlier draft of this test used it - the machine correctly refused it and the
            // test read as a queue bug.
            drop(helper, core, new ItemStack(RCItems.QUARTZ_GRIT.get(), 1));
            drop(helper, core, new ItemStack(RCItems.SPENT_ABRASIVE.get(), 1));
            drop(helper, core, new ItemStack(RCItems.MAGNET_SCRAP.get(), 1));

            helper.runAfterDelay(5, () -> {
                int kinds = 0;
                for (ItemStack stack : be.queued()) {
                    if (!stack.isEmpty()) {
                        kinds++;
                    }
                }
                helper.assertTrue(kinds == 3,
                    "three kinds went into the bay and " + kinds + " ended up queued. A queue that "
                        + "holds one kind at a time is the bay scan this replaced");
            });

            // And all three actually get processed, not just whichever reached the head first.
            helper.runAfterDelay(160, () -> {
                java.util.Set<net.minecraft.world.item.Item> out = new java.util.HashSet<>();
                for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        AABB.ofSize(helper.absolutePos(core).getCenter(), 12, 12, 12))) {
                    out.add(entity.getItem().getItem());
                }
                helper.assertTrue(out.contains(Items.AMETHYST_SHARD)
                        && out.contains(Items.REDSTONE) && out.contains(Items.DIAMOND),
                    "the queue did not work through every kind it swallowed. Out of the chute: " + out);
                helper.succeed();
            });
        });

        // The queue is BOUNDED and only takes what it can grind. Both halves matter: unbounded makes
        // the machine a storage block, and swallowing junk means a player loses an item to a machine
        // that will never give it back, since nothing can extract from this block.
        RCGameTests.test("the_separator_queue_is_bounded_and_refuses_what_it_cannot_grind", 60, helper -> {
            BlockPos core = new BlockPos(1, 2, 1);
            helper.setBlock(core, RCBlocks.SEPARATOR.get());
            buildAround(helper, core);
            helper.assertTrue(MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core)),
                "the Separator did not form");
            var be = (com.flatts.recompile.content.block.entity.SeparatorBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(core));
            // No power on purpose: intake must not depend on the machine being able to run.

            drop(helper, core, new ItemStack(Items.DIAMOND, 16));   // a gem, not scrap: no recipe
            int slots = com.flatts.recompile.content.block.entity.SeparatorBlockEntity.QUEUE_SLOTS;
            for (int i = 0; i < slots + 4; i++) {
                drop(helper, core, new ItemStack(RCItems.QUARTZ_GRIT.get(), 64));
            }

            helper.runAfterDelay(10, () -> {
                for (ItemStack stack : be.queued()) {
                    helper.assertTrue(!stack.is(Items.DIAMOND),
                        "the machine swallowed a Diamond, which it has no recipe for. Nothing can "
                            + "extract from this block, so anything it takes and cannot grind is gone");
                }
                helper.assertTrue(be.queued().size() == slots,
                    "the queue grew past its " + slots + " slots to " + be.queued().size());
                helper.assertTrue(be.queuedCount() <= slots * 64,
                    "the queue holds " + be.queuedCount() + " items, past its bound");
                boolean diamondStillThere = false;
                for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        SeparatorCoreBlock.mouth(helper.getLevel(), helper.absolutePos(core)))) {
                    if (entity.getItem().is(Items.DIAMOND)) {
                        diamondStillThere = true;
                    }
                }
                helper.assertTrue(diamondStillThere,
                    "what the machine refuses has to stay lying in the bay where the player can pick "
                        + "it back up");
                helper.succeed();
            });
        });

        // Breaking the machine hands the queue back. It has no item capability and no Container, so a
        // queue that did not drop would be an item sink with no way in and no way out.
        RCGameTests.test("breaking_the_separator_drops_its_queue", 60, helper -> {
            BlockPos core = new BlockPos(1, 2, 1);
            helper.setBlock(core, RCBlocks.SEPARATOR.get());
            buildAround(helper, core);
            helper.assertTrue(MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core)),
                "the Separator did not form");
            var be = (com.flatts.recompile.content.block.entity.SeparatorBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(core));
            drop(helper, core, new ItemStack(RCItems.QUARTZ_GRIT.get(), 7));

            helper.runAfterDelay(5, () -> {
                helper.assertTrue(be.queuedCount() == 7,
                    "expected 7 queued before the break, got " + be.queuedCount());
                helper.getLevel().destroyBlock(helper.absolutePos(core), true);

                int found = 0;
                for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        AABB.ofSize(helper.absolutePos(core).getCenter(), 12, 12, 12))) {
                    if (entity.getItem().is(RCItems.QUARTZ_GRIT.get())) {
                        found += entity.getItem().getCount();
                    }
                }
                helper.assertTrue(found == 7,
                    "7 Quartz Grit were queued and " + found + " came back out on break");
                helper.succeed();
            });
        });

        // A SAVE/LOAD ROUND TRIP, which is the one thing every other test here misses. All of them run
        // inside a single session, so the machine's serialization was never exercised at all - and the
        // first version of the queue threw on load, which aborted loadAdditional and took the stored
        // ENERGY down with it. The machine came back from a world reload empty and cold, and the only
        // sign anywhere was one line in a log nobody reads unless something else is already wrong.
        //
        // saveCustomOnly/loadCustomOnly is exactly the pair a chunk save uses, so this fails for the
        // same reason a real reload would.
        RCGameTests.test("a_separator_survives_being_saved_and_reloaded", 40, helper -> {
            BlockPos core = new BlockPos(1, 2, 1);
            helper.setBlock(core, RCBlocks.SEPARATOR.get());
            buildAround(helper, core);
            helper.assertTrue(MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core)),
                "the Separator did not form");
            var be = (com.flatts.recompile.content.block.entity.SeparatorBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(core));
            try (Transaction tx = Transaction.openRoot()) {
                be.battery().insert(2_500, tx);
                tx.commit();
            }
            drop(helper, core, new ItemStack(RCItems.QUARTZ_GRIT.get(), 5));

            helper.runAfterDelay(5, () -> {
                int energy = be.battery().getAmountAsInt();
                int queued = be.queuedCount();
                helper.assertTrue(queued == 5, "expected 5 queued before the save, got " + queued);
                helper.assertTrue(energy > 0, "expected stored energy before the save");

                var registries = helper.getLevel().registryAccess();
                var tag = be.saveCustomOnly(registries);
                var reloaded = new com.flatts.recompile.content.block.entity.SeparatorBlockEntity(
                    helper.absolutePos(core),
                    helper.getLevel().getBlockState(helper.absolutePos(core)));
                reloaded.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(
                    net.minecraft.util.ProblemReporter.DISCARDING, registries, tag));

                helper.assertTrue(reloaded.queuedCount() == queued,
                    "the queue did not survive a save: " + queued + " went in, "
                        + reloaded.queuedCount() + " came back");
                helper.assertTrue(reloaded.battery().getAmountAsInt() == energy,
                    "the stored energy did not survive a save: " + energy + " went in, "
                        + reloaded.battery().getAmountAsInt() + " came back. A throw anywhere in "
                        + "loadAdditional abandons everything after it, so this is how a queue bug "
                        + "eats the battery");
                helper.succeed();
            });
        });

        // The chute FILLS a container standing in front of it (owner, 2026-08-03). The machine was
        // piling its output on the lid of an obviously-correct barrel, which reads as broken rather
        // than as deliberate. Pushing out costs none of the closed-door properties - the machine still
        // exposes no handler, so nothing can reach IN - and it is the same reach-out the intake
        // already does at the other end.
        RCGameTests.test("the_chute_fills_a_container_in_front_of_it", 120, helper -> {
            BlockPos core = new BlockPos(1, 2, 1);
            helper.setBlock(core, RCBlocks.SEPARATOR.get());
            buildAround(helper, core);
            helper.assertTrue(MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core)),
                "the Separator did not form");
            var be = (com.flatts.recompile.content.block.entity.SeparatorBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(core));
            try (Transaction tx = Transaction.openRoot()) {
                be.battery().insert(1_000_000, tx);
                tx.commit();
            }

            BlockPos outlet = SeparatorCoreBlock.outlet(helper.getLevel(), helper.absolutePos(core));
            helper.getLevel().setBlockAndUpdate(outlet, Blocks.CHEST.defaultBlockState());
            var chest = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(outlet);
            helper.assertTrue(chest != null, "no chest at the outlet");
            drop(helper, core, new ItemStack(RCItems.QUARTZ_GRIT.get(), 1));

            helper.runAfterDelay(80, () -> {
                boolean inChest = false;
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    if (chest.getItem(slot).is(Items.AMETHYST_SHARD)) {
                        inChest = true;
                    }
                }
                helper.assertTrue(inChest,
                    "the chute did not fill the chest in front of it. Output on the floor beside a "
                        + "container the player deliberately placed reads as the machine being broken");

                // And nothing was duplicated on the way in - what went to the chest must not ALSO be
                // lying on the ground, which is the obvious way an insert-then-drop path goes wrong.
                for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        AABB.ofSize(helper.absolutePos(core).getCenter(), 12, 12, 12))) {
                    helper.assertFalse(entity.getItem().is(Items.AMETHYST_SHARD),
                        "an amethyst reached the chest AND the floor - the output was duplicated");
                }
                helper.succeed();
            });
        });

        // The chute RESPECTS a receiver that refuses items. The first version hand-rolled a slot loop
        // and would have walked straight past canPlaceItem and WorldlyContainer face rules - posting
        // an amethyst into a furnace's fuel slot, and worse, into the Burn Barrel, which returns NO
        // slots on any face precisely to keep automation out of its smelt slots. Using vanilla's own
        // addItem is what makes that hold, and this is the test that says so.
        RCGameTests.test("the_chute_cannot_force_items_into_a_closed_container", 120, helper -> {
            BlockPos core = new BlockPos(1, 2, 1);
            helper.setBlock(core, RCBlocks.SEPARATOR.get());
            buildAround(helper, core);
            helper.assertTrue(MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core)),
                "the Separator did not form");
            var be = (com.flatts.recompile.content.block.entity.SeparatorBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(core));
            try (Transaction tx = Transaction.openRoot()) {
                be.battery().insert(1_000_000, tx);
                tx.commit();
            }

            BlockPos outlet = SeparatorCoreBlock.outlet(helper.getLevel(), helper.absolutePos(core));
            helper.getLevel().setBlockAndUpdate(outlet, RCBlocks.BURN_BARREL.get().defaultBlockState());
            var barrel = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(outlet);
            helper.assertTrue(barrel != null, "no Burn Barrel at the outlet");
            drop(helper, core, new ItemStack(RCItems.QUARTZ_GRIT.get(), 1));

            helper.runAfterDelay(80, () -> {
                for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                    helper.assertFalse(barrel.getItem(slot).is(Items.AMETHYST_SHARD),
                        "the chute forced an amethyst into the Burn Barrel, which closes every face to "
                            + "automation. Pushing out must not be a way around a receiver's own rules");
                }
                boolean onFloor = false;
                for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        AABB.ofSize(helper.absolutePos(core).getCenter(), 12, 12, 12))) {
                    if (entity.getItem().is(Items.AMETHYST_SHARD)) {
                        onFloor = true;
                    }
                }
                helper.assertTrue(onFloor,
                    "what a receiver refuses has to fall on the floor - the machine must never destroy "
                        + "what it made just because nothing would take it");
                helper.succeed();
            });
        });

        // JOINING THE NETWORK MUST NOT OPEN THE DOOR. The Separator is a SOURCE, and the routing code
        // only ever lands in a Scrap Bin or the Scrap Barrel by block id - but that is a property worth
        // asserting rather than trusting, because "it is in the tag now" is exactly the reasoning that
        // would let something try to route INTO a machine with no container at all.
        RCGameTests.test("a_scrap_route_never_lands_in_the_separator", 40, helper -> {
            BlockPos core = new BlockPos(1, 2, 1);
            helper.setBlock(core, RCBlocks.SEPARATOR.get());
            buildAround(helper, core);
            helper.assertTrue(MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core)),
                "the Separator did not form");
            BlockPos tarpPos = new BlockPos(2, 2, 3);   // beside the machine, not inside it
            helper.setBlock(tarpPos, RCBlocks.SORTING_TARP.get());

            ItemStack junk = new ItemStack(RCItems.SCRAP_METAL.get(), 8);
            ItemStack left = com.flatts.recompile.content.block.ScrapNetwork.insertFromMember(
                helper.getLevel(), helper.absolutePos(tarpPos), junk, false);
            helper.assertTrue(left.getCount() == 8,
                "a route found somewhere to put scrap in a cluster whose only members are a tarp and "
                    + "the Separator. The machine has no container and must never be a sink");

            var be = (com.flatts.recompile.content.block.entity.SeparatorBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(core));
            helper.assertTrue(be.queuedCount() == 0,
                "the route put " + be.queuedCount() + " items into the Separator's queue. The queue is "
                    + "fed by the machine reaching out, never by anything reaching in");
            helper.succeed();
        });

        // THE CHUTE POINTS THE WAY THE MACHINE DOES. Its mouth is cut into one side of its model, and
        // for a while the formed cell carried no facing at all - so it opened north whatever direction
        // the machine was built in, correct by accident on a north-facing Separator and wrong on the
        // other three. That was found by eye and fixed by hand; this is the regression test it never
        // got. Built facing EAST on purpose, because NORTH is the orientation the bug hid behind.
        RCGameTests.test("every_formed_part_carries_the_machines_facing", 40, helper -> {
            BlockPos core = new BlockPos(2, 2, 2);
            var rotation = net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
            helper.setBlock(core, RCBlocks.SEPARATOR.get().defaultBlockState()
                .setValue(SeparatorCoreBlock.FACING, net.minecraft.core.Direction.EAST));
            buildAround(helper, core, rotation);
            helper.assertTrue(MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core)),
                "the east-facing Separator did not form");

            List<String> wrong = new ArrayList<>();
            for (Multiblock.Cell cell : RCBlocks.SEPARATOR.get().blueprint().cells()) {
                BlockPos at = helper.absolutePos(cell.at(core, rotation));
                BlockState state = helper.getLevel().getBlockState(at);
                var facing = state.hasProperty(
                        com.flatts.recompile.content.block.multiblock.MultiblockSkinnedBlock.FACING)
                    ? state.getValue(
                        com.flatts.recompile.content.block.multiblock.MultiblockSkinnedBlock.FACING)
                    : null;
                if (facing != null && facing != net.minecraft.core.Direction.EAST) {
                    wrong.add(cell.offset() + " faces " + facing);
                }
            }
            helper.assertTrue(wrong.isEmpty(),
                "formed parts of an EAST-facing machine that do not face east: " + wrong
                    + ". The chute's mouth is geometry, so a cell that does not know the machine's "
                    + "direction opens the wrong way");
            helper.succeed();
        });

        // THE BAY MIRRORS THE CORE'S RUNNING STATE. ACTIVE lived only on the core once, so the
        // blockstate had no variant that could ever select the running models - they shipped as dead
        // files and the grinder never appeared to turn. Nothing in the build noticed, because the
        // machine worked perfectly; it just looked stopped.
        RCGameTests.test("the_bay_animates_while_the_machine_runs", 120, helper -> {
            BlockPos core = new BlockPos(1, 2, 1);
            helper.setBlock(core, RCBlocks.SEPARATOR.get());
            buildAround(helper, core);
            helper.assertTrue(MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core)),
                "the Separator did not form");
            var be = (com.flatts.recompile.content.block.entity.SeparatorBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(core));

            // Idle first, so a bay stuck permanently ON would fail here rather than pass the next check.
            for (BlockPos cell : SeparatorCoreBlock.chamberCells(
                    helper.getLevel(), helper.absolutePos(core))) {
                helper.assertFalse(helper.getLevel().getBlockState(cell).getValue(
                        com.flatts.recompile.content.block.SeparatorChamberBlock.ACTIVE),
                    "a bay cell was animating before the machine had anything to do");
            }

            try (Transaction tx = Transaction.openRoot()) {
                be.battery().insert(1_000_000, tx);
                tx.commit();
            }
            drop(helper, core, new ItemStack(RCItems.QUARTZ_GRIT.get(), 1));

            helper.runAfterDelay(20, () -> {
                helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(core))
                        .getValue(SeparatorCoreBlock.ACTIVE),
                    "the core never went active");
                for (BlockPos cell : SeparatorCoreBlock.chamberCells(
                        helper.getLevel(), helper.absolutePos(core))) {
                    helper.assertTrue(helper.getLevel().getBlockState(cell).getValue(
                            com.flatts.recompile.content.block.SeparatorChamberBlock.ACTIVE),
                        "the core is running and " + cell + " is not - the bay mirrors ACTIVE, and "
                            + "without it the animated models are unreachable");
                }
                helper.succeed();
            });
        });

        // The machine-position stamp actually reaching the world. The skin index arithmetic is checked
        // in GuidebookMultiblockTests, but arithmetic being right and stampSkin running are different
        // claims - and a machine whose cells all kept CELL 0 would wear one tile six times, which reads
        // as bad art rather than as a hook that never fired.
        RCGameTests.test("forming_stamps_every_cell_with_its_place_in_the_machine", 40, helper -> {
            BlockPos core = new BlockPos(1, 2, 1);
            helper.setBlock(core, RCBlocks.SEPARATOR.get());
            buildAround(helper, core);
            helper.assertTrue(MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core)),
                "the Separator did not form");

            Multiblock blueprint = RCBlocks.SEPARATOR.get().blueprint();
            java.util.Set<Integer> seen = new java.util.HashSet<>();
            List<String> wrong = new ArrayList<>();
            for (Multiblock.Cell cell : blueprint.cells()) {
                BlockState state = helper.getLevel().getBlockState(helper.absolutePos(cell.at(core)));
                if (!state.hasProperty(
                        com.flatts.recompile.content.block.multiblock.MultiblockSkinnedBlock.CELL)) {
                    continue;
                }
                int stamped = state.getValue(
                    com.flatts.recompile.content.block.multiblock.MultiblockSkinnedBlock.CELL);
                int expected = blueprint.cellIndex(cell.offset());
                if (stamped != expected) {
                    wrong.add(cell.offset() + " stamped " + stamped + ", expected " + expected);
                }
                seen.add(stamped);
            }
            helper.assertTrue(wrong.isEmpty(), "cells stamped with the wrong place: " + wrong);
            helper.assertTrue(seen.size() > 1,
                "every skinned cell carries the same index (" + seen + "), so the machine would wear "
                    + "one tile everywhere - stampSkin is not running");
            helper.succeed();
        });

        // NO POWER MEANS THE MATERIAL WAITS. It must not be consumed, destroyed, or spat back out - the
        // furnace-with-no-fuel behaviour the spec promises. The half of this that matters is the second
        // one: that it picks straight up once power arrives, so a solar gap costs time and nothing else.
        RCGameTests.test("an_unpowered_separator_holds_its_material_and_resumes", 200, helper -> {
            BlockPos core = new BlockPos(1, 2, 1);
            helper.setBlock(core, RCBlocks.SEPARATOR.get());
            buildAround(helper, core);
            helper.assertTrue(MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core)),
                "the Separator did not form");
            var be = (com.flatts.recompile.content.block.entity.SeparatorBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(core));
            drop(helper, core, new ItemStack(RCItems.QUARTZ_GRIT.get(), 1));

            helper.runAfterDelay(60, () -> {
                helper.assertTrue(be.queuedCount() == 1,
                    "an unpowered machine did not hold its material: " + be.queuedCount() + " queued");
                helper.assertFalse(helper.getLevel().getBlockState(helper.absolutePos(core))
                        .getValue(SeparatorCoreBlock.ACTIVE),
                    "an unpowered machine was showing as running");
                for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        AABB.ofSize(helper.absolutePos(core).getCenter(), 12, 12, 12))) {
                    helper.assertFalse(entity.getItem().is(Items.AMETHYST_SHARD),
                        "an unpowered machine produced output");
                }
                try (Transaction tx = Transaction.openRoot()) {
                    be.battery().insert(1_000_000, tx);
                    tx.commit();
                }
            });

            helper.runAfterDelay(160, () -> {
                boolean found = false;
                for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        AABB.ofSize(helper.absolutePos(core).getCenter(), 12, 12, 12))) {
                    if (entity.getItem().is(Items.AMETHYST_SHARD)) {
                        found = true;
                    }
                }
                helper.assertTrue(found,
                    "the machine never resumed once power arrived - waiting has to be a pause, not a "
                        + "state it gets stuck in");
                helper.succeed();
            });
        });

        // Energy is INSERT-only from outside: it is a consumer, and a generator next door must not be
        // able to pull its own power back out. The handler itself is open both ways so the machine can
        // draw on its own battery; it is the capability wrapper that limits the outside world, and that
        // distinction cost a playtest once - built extract-disabled, the machine sat fully charged and
        // never ran.
        RCGameTests.test("the_separators_battery_cannot_be_drained_from_outside", 40, helper -> {
            BlockPos core = new BlockPos(1, 2, 1);
            helper.setBlock(core, RCBlocks.SEPARATOR.get());
            buildAround(helper, core);
            helper.assertTrue(MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core)),
                "the Separator did not form");
            var handler = helper.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK,
                helper.absolutePos(core), null);
            helper.assertTrue(handler != null, "the Separator must expose an energy capability");

            try (Transaction tx = Transaction.openRoot()) {
                int taken = handler.insert(1_000, tx);
                tx.commit();
                helper.assertTrue(taken > 0, "the Separator refused energy from outside");
            }
            try (Transaction tx = Transaction.openRoot()) {
                int pulled = handler.extract(1_000, tx);
                tx.commit();
                helper.assertTrue(pulled == 0,
                    "something drained " + pulled + " FE out of the Separator. It is a consumer; a "
                        + "generator that can pull its own energy back hands it back and forth forever");
            }
            helper.succeed();
        });

        // ONE chute, and everything leaves through it (owner, 2026-08-03). A recipe that produces a
        // result plus several byproducts is exactly the moment someone would reach for a second
        // opening, and the whole point is that catching a machine's output never needs more than one
        // hopper. Asserts the count AND that the outlet is in front of that chute, because a chute the
        // machine does not actually throw through is decoration.
        RCGameTests.test("the_separator_has_exactly_one_chute", 40, helper -> {
            BlockPos core = new BlockPos(1, 2, 1);
            helper.setBlock(core, RCBlocks.SEPARATOR.get());
            buildAround(helper, core);
            helper.assertTrue(MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core)),
                "the Separator did not form");

            List<Multiblock.Cell> chutes = RCBlocks.SEPARATOR.get().blueprint().cells().stream()
                .filter(cell -> cell.formed() == RCBlocks.SEPARATOR_CHUTE.get())
                .toList();
            helper.assertTrue(chutes.size() == 1,
                "the Separator has " + chutes.size() + " chutes, not 1. Everything the machine makes "
                    + "leaves through one opening, so one hopper catches all of it");

            BlockPos outlet = SeparatorCoreBlock.outlet(helper.getLevel(), helper.absolutePos(core));
            Vec3i offset = chutes.get(0).offset();
            BlockPos chute = helper.absolutePos(core).offset(offset.getX(), offset.getY(), offset.getZ());
            helper.assertTrue(helper.getLevel().getBlockState(chute).is(RCBlocks.SEPARATOR_CHUTE.get()),
                "the blueprint's chute cell did not form into a chute");
            helper.assertTrue(outlet.closerThan(chute, 1.5),
                "output is thrown at " + outlet + " but the only chute is at " + chute
                    + " - a chute the machine does not throw through is decoration");
            helper.succeed();
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

    /**
     * Drop a stack into the middle of the machine's bay, held still.
     *
     * <p>{@code ItemEntity}'s constructor gives it a random shove. In the world a dropped stack settles;
     * in a test it would drift out of the mouth and the test would fail for a reason that has nothing to
     * do with what it is checking.
     */
    private static void drop(GameTestHelper helper, BlockPos core, ItemStack stack) {
        BlockPos into = SeparatorCoreBlock.chamberCells(
            helper.getLevel(), helper.absolutePos(core)).get(0).above();
        ItemEntity entity = new ItemEntity(helper.getLevel(), into.getX() + 0.5, into.getY() + 0.5,
            into.getZ() + 0.5, stack);
        entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        entity.setNoGravity(true);
        helper.getLevel().addFreshEntity(entity);
    }

    /**
     * Place every component a north-facing Separator needs, leaving the core alone.
     *
     * <p>Read off the blueprint rather than written out by hand. These tests are about what the machine
     * <b>does</b> - it grinds, it drains a container, it stamps its bay - and none of them is about its
     * shape, so hardcoding the shape only meant every one of them broke on a reshape for no reason.
     * {@code GuidebookMultiblockTests} is where the shape is actually pinned.
     */
    private static void buildAround(GameTestHelper helper, BlockPos core) {
        buildAround(helper, core, net.minecraft.world.level.block.Rotation.NONE);
    }

    /** The same, for a machine built facing somewhere other than north. */
    private static void buildAround(GameTestHelper helper, BlockPos core,
                                    net.minecraft.world.level.block.Rotation rotation) {
        for (Multiblock.Cell cell : RCBlocks.SEPARATOR.get().blueprint().cells()) {
            helper.setBlock(cell.at(core, rotation), cell.component());
        }
    }
}

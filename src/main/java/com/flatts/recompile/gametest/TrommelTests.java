package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.content.block.TrommelCoreBlock;
import com.flatts.recompile.content.block.TrommelDrumBlock;
import com.flatts.recompile.content.block.entity.TrommelBlockEntity;
import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** GameTests for the Trommel (#188): the machine that takes automated sorting off the Separator. */
final class TrommelTests {

    private TrommelTests() {
    }

    /** Place every component the blueprint asks for, in world space, around a placed core. */
    private static void buildAround(GameTestHelper helper, BlockPos core) {
        Multiblock blueprint = ((MultiblockCoreBlock) RCBlocks.TROMMEL.get()).blueprint();
        for (Multiblock.Cell cell : blueprint.cells()) {
            helper.setBlock(core.offset(cell.offset()), cell.component());
        }
    }

    private static TrommelBlockEntity formAndPower(GameTestHelper helper, BlockPos core) {
        helper.setBlock(core, RCBlocks.TROMMEL.get());
        buildAround(helper, core);
        helper.assertTrue(MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core)),
            "the Trommel did not form from its components");
        var be = (TrommelBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(core));
        helper.assertTrue(be != null, "no Trommel BlockEntity");

        // POWERED THROUGH THE CAPABILITY, never by writing to be.battery() directly. That shortcut is
        // how the machine shipped its first pass with no Capabilities.Energy.BLOCK registration at all:
        // every test passed, and no generator or pipe in the game could reach the block. A machine that
        // runs in the harness and is dead in the world is the exact failure this indirection prevents,
        // so every Trommel test now takes its power the way a Solar Panel would deliver it.
        var energy = helper.getLevel().getCapability(
            Capabilities.Energy.BLOCK, helper.absolutePos(core), null);
        helper.assertTrue(energy != null,
            "the Trommel exposes no Capabilities.Energy.BLOCK, so no generator can reach it");
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(energy.insert(1_000_000, tx) > 0,
                "the Trommel refused energy through its capability");
            tx.commit();
        }
        return be;
    }

    static void register() {
        // It assembles, and every drum cell knows its place in the run. Without the stamp the four
        // cells wear the same picture and read as four blocks rather than one barrel - and the ACTIVE
        // mirror has nowhere to land, which is how the Separator once shipped running models that
        // nothing referenced.
        RCGameTests.test("a_trommel_forms_and_stamps_its_drum", 40, helper -> {
            BlockPos core = new BlockPos(1, 1, 1);
            formAndPower(helper, core);

            var drum = TrommelCoreBlock.drumCells(helper.getLevel(), helper.absolutePos(core));
            helper.assertTrue(drum.size() == TrommelCoreBlock.LENGTH,
                "expected " + TrommelCoreBlock.LENGTH + " drum cells, got " + drum.size());
            for (int i = 0; i < drum.size(); i++) {
                var state = helper.getLevel().getBlockState(drum.get(i));
                helper.assertTrue(state.is(RCBlocks.TROMMEL_DRUM.get()),
                    "drum cell " + i + " did not form into a drum");
                helper.assertTrue(state.getValue(TrommelDrumBlock.CELL) == i,
                    "drum cell " + i + " is stamped " + state.getValue(TrommelDrumBlock.CELL));
            }
            helper.succeed();
        });

        // THE WHOLE POINT: it sorts, and it yields exactly what the Sorting Tarp yields per block.
        //
        // Asserted as a COUNT rather than as "both call the same function", because the second is
        // tautological - they do call the same function, which is the design, and a test that says so
        // proves nothing. Counting what actually lands proves the machine uses the rolls it declares.
        RCGameTests.test("a_trommel_sorts_a_block_at_the_tarps_rate", 200, helper -> {
            // AT x = 0, not x = 1. The machine discharges off the END of the drum, one block past its
            // own footprint, and a four-long machine started at x = 1 puts that outside a 5x5x5 plot -
            // where the output lands off the test's own bounds and is counted as nothing. The failure
            // reads as "the machine produced nothing", which is a much more alarming sentence than
            // "the test cannot see where it went".
            BlockPos core = new BlockPos(0, 1, 1);
            TrommelBlockEntity be = formAndPower(helper, core);

            int rolls = SortableBlock.sortRolls(RCItems.GARBAGE_BLOCK.get());
            helper.assertTrue(rolls > 0, "a garbage block is not sortable - the test is measuring air");

            BlockPos feed = TrommelCoreBlock.drumCells(
                helper.getLevel(), helper.absolutePos(core)).get(0).above();
            helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(),
                feed.getX() + 0.5, feed.getY() + 0.5, feed.getZ() + 0.5,
                new ItemStack(RCItems.GARBAGE_BLOCK.get(), 1)));

            helper.succeedWhen(() -> {
                helper.assertTrue(be.queuedCount() == 0,
                    "the Trommel still holds " + be.queuedCount() + " - it has not finished");
                long dropped = helper.getLevel()
                    .getEntitiesOfClass(ItemEntity.class, helper.getBounds())
                    .stream()
                    .filter(e -> !e.getItem().is(RCItems.GARBAGE_BLOCK.get()))
                    .mapToLong(e -> e.getItem().getCount())
                    .sum();
                helper.assertTrue(dropped >= rolls,
                    "one garbage block should yield " + rolls + " pulls, the tarp's rate; got "
                        + dropped);
            });
        });

        // A machine that eats what it was holding is worse than one that never accepted it.
        RCGameTests.test("breaking_a_trommel_hands_back_its_queue", 60, helper -> {
            BlockPos core = new BlockPos(1, 1, 1);
            TrommelBlockEntity be = formAndPower(helper, core);
            BlockPos feed = TrommelCoreBlock.drumCells(
                helper.getLevel(), helper.absolutePos(core)).get(0).above();
            helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(),
                feed.getX() + 0.5, feed.getY() + 0.5, feed.getZ() + 0.5,
                new ItemStack(RCItems.TRASH_BAG.get(), 3)));

            helper.runAfterDelay(20, () -> {
                helper.assertTrue(be.queuedCount() > 0, "the Trommel swallowed nothing to hand back");
                helper.getLevel().destroyBlock(helper.absolutePos(core), true);
                helper.succeedWhen(() ->
                    helper.assertItemEntityCountIs(RCItems.TRASH_BAG.get(), core, 4.0, 3));
            });
        });

        // HOW YOU GET SOMETHING INTO IT, which is the question a sealed machine with no screen and no
        // slots leaves a player holding. Two routes, and this covers the one nobody guesses: a
        // container parked on the drum is DRAINED. Every other automatable block in the game is fed by
        // pushing into it, so being drained is the opposite of the habit - and it is the route that
        // makes the machine unattended, which is the entire reward for building one.
        //
        // Note the direction. Nothing pushes into the Trommel; the machine reaches out and takes. That
        // is what keeps the closed door shut while still letting a hopper feed the chest that feeds it.
        RCGameTests.test("a_container_parked_on_the_trommel_is_drained", 200, helper -> {
            BlockPos core = new BlockPos(1, 1, 1);
            TrommelBlockEntity be = formAndPower(helper, core);

            BlockPos chest = TrommelCoreBlock.drumCells(
                helper.getLevel(), helper.absolutePos(core)).get(0).above();
            helper.getLevel().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            var container = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(chest);
            helper.assertTrue(container != null, "no chest to drain");
            container.setItem(0, new ItemStack(RCItems.GARBAGE_BLOCK.get(), 4));

            helper.succeedWhen(() -> {
                var left = container.getItem(0);
                helper.assertTrue(left.getCount() < 4,
                    "the Trommel did not take anything out of the chest standing on its drum");
                helper.assertTrue(be.queuedCount() > 0 || left.getCount() < 4,
                    "nothing moved from the chest into the machine");
            });
        });

        // IT DISCHARGES OFF THE END OF THE DRUM, INTO WHATEVER IS PARKED THERE.
        //
        // A trommel delivers along its own axis: material travels the length of the screen and leaves
        // by the open end. The output used to appear beside the machine, in front of the chute, which
        // is the wrong axis entirely - a hopper had to be put somewhere the drum does not point.
        //
        // Container FIRST, thrown only as a fallback. A machine that spits onto the floor while a chest
        // sits in its discharge is not automatable, and gathering the floor by hand is worse than not
        // having built it.
        RCGameTests.test("the_trommel_discharges_into_a_container_at_the_drum_end", 200, helper -> {
            BlockPos core = new BlockPos(0, 1, 1);
            TrommelBlockEntity be = formAndPower(helper, core);

            // ABSOLUTE, not relative. A GameTest plot is placed rotated, so the machine's +x run maps
            // to -x in plot space and relativePos put the discharge four blocks OUTSIDE the structure.
            // The chest went somewhere else entirely and the test reported "the machine produced
            // nothing" while 32 items were lying on the ground next to it.
            BlockPos outlet = TrommelCoreBlock.outlet(helper.getLevel(), helper.absolutePos(core));
            helper.getLevel().setBlockAndUpdate(outlet, Blocks.CHEST.defaultBlockState());
            var chest = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(outlet);
            helper.assertTrue(chest != null, "no chest at the discharge");

            BlockPos feed = TrommelCoreBlock.drumCells(
                helper.getLevel(), helper.absolutePos(core)).get(0).above();
            helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(),
                feed.getX() + 0.5, feed.getY() + 0.5, feed.getZ() + 0.5,
                new ItemStack(RCItems.GARBAGE_BLOCK.get(), 1)));

            helper.succeedWhen(() -> {
                helper.assertTrue(be.queuedCount() == 0, "the Trommel has not finished sorting");
                int inChest = 0;
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    inChest += chest.getItem(slot).getCount();
                }
                helper.assertTrue(inChest > 0,
                    "the chest at the drum's end got nothing - the discharge missed it");
            });
        });

        // THE DISBAND DUPLICATION TRAP, which a two-cell machine cannot catch. In 26.1 the removal
        // hook fires on a plain setBlock-to-AIR as well as on a real break, so clearing one cell
        // re-enters its siblings' hooks - and while the core is still FORMED each of those re-drops
        // the core. A machine with N dummies hands back N cores from one break. The Trommel has SEVEN
        // dummy cells, so it is exactly the shape that exposes it.
        //
        // WHAT THIS CANNOT SEE, stated rather than glossed: the core ITEM. MultiblockDummyBlock drops
        // the core with Block.dropResources and no tool, so a core declaring
        // requiresCorrectToolForDrops - the Trommel and the Separator, both of them - drops nothing
        // through that path at all. That is a real framework defect (#191) and it is pre-existing;
        // it also means a duplicated core would duplicate ZERO items, so the count is unobservable
        // here until it is fixed. What is observable is that the machine comes apart exactly once and
        // returns its components exactly once, which is the same cascade seen from the other side.
        RCGameTests.test("breaking_a_trommel_cell_disbands_it_exactly_once", 80, helper -> {
            BlockPos core = new BlockPos(1, 1, 1);
            formAndPower(helper, core);

            BlockPos cell = TrommelCoreBlock.drumCells(
                helper.getLevel(), helper.absolutePos(core)).get(2);
            helper.getLevel().destroyBlock(cell, true);

            helper.succeedWhen(() -> {
                helper.assertBlockPresent(Blocks.AIR, helper.relativePos(cell));
                var state = helper.getLevel().getBlockState(helper.absolutePos(core));
                helper.assertTrue(!MultiblockCoreBlock.isFormed(state),
                    "the core is still FORMED after a cell was broken");
                // THREE beams, not four, and the missing one is the point rather than an error: the
                // broken cell dropped its own loot through the normal break BEFORE disband ran, so it
                // came back as a Trommel Drum. disband then returns the component the blueprint names
                // for every cell it still recognises. A cascade through the siblings' hooks would
                // return more of all of these.
                helper.assertItemEntityCountIs(RCItems.STEEL_I_BEAM.get(), core, 6.0, 3);
                helper.assertItemEntityCountIs(RCItems.TROMMEL_DRUM.get(), core, 6.0, 1);
                helper.assertItemEntityCountIs(RCItems.MOTOR.get(), core, 6.0, 1);
            });
        });

        // NO BLUEPRINT MAY REACH PAST THE CORE SEARCH.
        //
        // A dummy finds its master by looking around itself, and a cell further out than
        // SEARCH_RADIUS simply never finds one - so breaking it does not disband the machine and you
        // are left with a formed machine with a hole in it, still running, missing a part. There is
        // no error and nothing to see; the build just keeps working.
        //
        // The radius was 1 until the Trommel, which capped every machine at three blocks wide and had
        // already swallowed the Separator's far column. This is the guard that stops the next machine
        // rediscovering it in a playtest instead of at build time.
        RCGameTests.test("no_blueprint_reaches_past_the_core_search", 20, helper -> {
            java.util.List<String> tooBig = new java.util.ArrayList<>();
            int checked = 0;
            for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                if (!(block instanceof MultiblockCoreBlock core)) {
                    continue;
                }
                checked++;
                for (Multiblock.Cell cell : core.blueprint().cells()) {
                    int dx = Math.abs(cell.offset().getX());
                    int dz = Math.abs(cell.offset().getZ());
                    if (Math.max(dx, dz) > com.flatts.recompile.content.block.multiblock
                            .MultiblockDummyBlock.SEARCH_RADIUS) {
                        tooBig.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block)
                            + " cell " + cell.offset());
                    }
                }
            }
            helper.assertTrue(checked >= 4,
                "only " + checked + " multiblock cores found - discovery is broken, so this would "
                    + "pass by checking nothing");
            helper.assertTrue(tooBig.isEmpty(),
                "these cells sit outside MultiblockDummyBlock.SEARCH_RADIUS, so breaking one would "
                    + "leave the machine formed with a hole in it: " + tooBig);
            helper.succeed();
        });

        // The closed door, inherited from the Separator's design: no Container, no item capability, so
        // nothing can push into it and no pipe can connect. It automates by reaching out instead.
        RCGameTests.test("the_trommel_is_unreachable_by_pipe_and_hopper", 40, helper -> {
            BlockPos core = new BlockPos(1, 1, 1);
            formAndPower(helper, core);
            BlockPos abs = helper.absolutePos(core);

            helper.assertTrue(
                helper.getLevel().getCapability(
                    Capabilities.Item.BLOCK, abs, null) == null,
                "the Trommel exposes an item capability - a pipe could fill it");
            helper.assertTrue(
                !(helper.getLevel().getBlockEntity(abs) instanceof net.minecraft.world.Container),
                "the Trommel is a Container - a hopper could push into it");

            // Power is the ONE door that is open, and it opens one way. A consumer that also hands
            // energy back is not a harmless extra: every generator pushes to its neighbours each tick,
            // so a machine that gives energy back trades the same charge with its own supply forever.
            var energy = helper.getLevel().getCapability(
                Capabilities.Energy.BLOCK, abs, null);
            helper.assertTrue(energy != null, "the Trommel takes no energy - it cannot be powered");
            try (Transaction tx = Transaction.openRoot()) {
                helper.assertTrue(energy.extract(1000, tx) == 0,
                    "the Trommel gives energy back - it would trade charge with its own generator");
            }
            helper.succeed();
        });
    }
}

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

        // MECHANICAL WASTE SORTS HERE NOW. Moved from the Separator with #187, not deleted: the
        // yard's own pile is a sortable, and losing the assertion along with the machine that used to
        // hold it would quietly drop the coverage rather than relocate it.
        RCGameTests.test("a_trommel_sorts_mechanical_waste", 200, helper -> {
            BlockPos core = new BlockPos(0, 1, 1);
            TrommelBlockEntity be = formAndPower(helper, core);

            int rolls = SortableBlock.sortRolls(RCItems.MECHANICAL_WASTE.get());
            helper.assertTrue(rolls > 0,
                "Mechanical Waste is not sortable - this test would be measuring air");

            BlockPos outlet = TrommelCoreBlock.outlet(helper.getLevel(), helper.absolutePos(core));
            helper.getLevel().setBlockAndUpdate(outlet, Blocks.CHEST.defaultBlockState());
            var chest = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(outlet);
            helper.assertTrue(chest != null, "no chest at the discharge");

            BlockPos feed = TrommelCoreBlock.drumCells(
                helper.getLevel(), helper.absolutePos(core)).get(0).above();
            helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(),
                feed.getX() + 0.5, feed.getY() + 0.5, feed.getZ() + 0.5,
                new ItemStack(RCItems.MECHANICAL_WASTE.get(), 1)));

            helper.succeedWhen(() -> {
                helper.assertTrue(be.queuedCount() == 0, "the Trommel has not finished");
                int out = 0;
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    out += chest.getItem(slot).getCount();
                }
                helper.assertTrue(out > 0, "sorting Mechanical Waste produced nothing");
            });
        });

        // ITS OUTPUT FILES ITSELF INTO A CONNECTED BIN, which is the assertion that makes the SOURCE
        // role in ScrapNetworkTests a fact rather than a promise. That table lists this machine as a
        // SOURCE; the push test beside it hand-lists its subjects and does not reach here.
        //
        // Moved from the Separator with #187. It also guards the defect review found on #188: the
        // Trommel was calling into the network while absent from #recompile:scrap_connectable, so
        // every routed item silently fell through to the chute instead - with the guidebook promising
        // the opposite.
        RCGameTests.test("trommel_output_files_itself_into_a_connected_bin", 200, helper -> {
            BlockPos core = new BlockPos(0, 1, 1);
            TrommelBlockEntity be = formAndPower(helper, core);

            // Against the core, so the cluster is reached from the acting block itself.
            BlockPos binPos = helper.absolutePos(core).below();
            helper.getLevel().setBlockAndUpdate(binPos, RCBlocks.SCRAP_BARREL.get().defaultBlockState());
            var barrel = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(binPos);
            helper.assertTrue(barrel != null, "no barrel to file into");

            BlockPos feed = TrommelCoreBlock.drumCells(
                helper.getLevel(), helper.absolutePos(core)).get(0).above();
            helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(),
                feed.getX() + 0.5, feed.getY() + 0.5, feed.getZ() + 0.5,
                new ItemStack(RCItems.GARBAGE_BLOCK.get(), 1)));

            helper.succeedWhen(() -> {
                helper.assertTrue(be.queuedCount() == 0, "the Trommel has not finished sorting");
                int filed = 0;
                for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                    filed += barrel.getItem(slot).getCount();
                }
                helper.assertTrue(filed > 0,
                    "nothing reached the connected barrel - the machine is in the tag but its output "
                        + "is not routing, so the Scrap Network membership is decoration");
            });
        });

        // THE DISBAND DUPLICATION TRAP, which a two-cell machine cannot catch. In 26.1 the removal
        // hook fires on a plain setBlock-to-AIR as well as on a real break, so clearing one cell
        // re-enters its siblings' hooks - and while the core is still FORMED each of those re-drops
        // the core. A machine with N dummies hands back N cores from one break. The Trommel has SEVEN
        // dummy cells, so it is exactly the shape that exposes it.
        //
        // IT COUNTS THE CORE, because that is the assertion that actually guards the cascade: N
        // dummy cells re-entering the hook would hand back N cores.
        //
        // This used to count only components, on my claim that a tool-gated core drops nothing
        // through dropResources-with-no-tool and so could not be counted. THAT CLAIM WAS FALSE and
        // was never measured - #191 was filed on it. requiresCorrectToolForDrops is enforced in the
        // PLAYER's break path, not in the loot table, and neither core's table carries a tool
        // condition at all, so dropResources called directly runs the table and the core drops
        // normally. Probed before this was written: exactly one core, every time.
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
                // FOUR beams, one per drum cell, and that number is itself the fix. It was three,
                // and the comment here explained the missing one as correct: the broken cell dropped
                // its own loot before disband ran, so it came back as a Trommel Drum - an item with
                // no recipe, hidden from JEI, useless for rebuilding the machine. A formed cell now
                // drops nothing of its own and the blueprint decides on every path, so the cell you
                // broke returns the component you put in.
                //
                // A cascade through the siblings' hooks would return more of all of these.
                helper.assertItemEntityCountIs(RCItems.TROMMEL.get(), core, 6.0, 1);
                helper.assertItemEntityCountIs(RCItems.STEEL_I_BEAM.get(), core, 6.0, 4);
                helper.assertItemEntityCountIs(RCItems.TROMMEL_DRUM.get(), core, 6.0, 0);
                helper.assertItemEntityCountIs(RCItems.MOTOR.get(), core, 6.0, 1);
            });
        });

        // ...and the same on the Trommel, which failed it differently: its formed cells dropped
        // THEMSELVES. Breaking the motor cell handed back a trommel_stand - no recipe, hidden from JEI
        // as unobtainable, and useless for rebuilding the machine it came out of - while the Motor,
        // the part this machine is deliberately gated on, was destroyed.
        RCGameTests.test("breaking_the_trommel_motor_cell_returns_the_motor", 80, helper -> {
            BlockPos core = new BlockPos(0, 1, 1);
            formAndPower(helper, core);
            var blueprint = ((MultiblockCoreBlock) RCBlocks.TROMMEL.get()).blueprint();
            var motorCell = blueprint.cells().stream()
                .filter(c -> c.component() == RCBlocks.MOTOR.get())
                .findFirst().orElse(null);
            helper.assertTrue(motorCell != null, "the Trommel blueprint has no Motor cell");
            BlockPos motor = helper.absolutePos(core).offset(
                Multiblock.rotate(motorCell.offset(),
                    ((MultiblockCoreBlock) RCBlocks.TROMMEL.get())
                        .rotationFor(helper.getLevel().getBlockState(helper.absolutePos(core)))));
            helper.getLevel().destroyBlock(motor, true);

            helper.succeedWhen(() -> {
                helper.assertItemEntityCountIs(RCItems.MOTOR.get(), core, 8.0, 1);
                helper.assertItemEntityCountIs(RCItems.TROMMEL.get(), core, 8.0, 1);
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

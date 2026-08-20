package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.PulverizerCoreBlock;
import com.flatts.recompile.content.block.entity.PulverizerBlockEntity;
import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** GameTests for the Pulverizer (#189): the machine that reduces rather than divides. */
final class PulverizerTests {

    private PulverizerTests() {
    }

    private static void buildAround(GameTestHelper helper, BlockPos core) {
        Multiblock blueprint = ((MultiblockCoreBlock) RCBlocks.PULVERIZER.get()).blueprint();
        for (Multiblock.Cell cell : blueprint.cells()) {
            helper.setBlock(core.offset(cell.offset()), cell.component());
        }
    }

    private static PulverizerBlockEntity formAndPower(GameTestHelper helper, BlockPos core) {
        helper.setBlock(core, RCBlocks.PULVERIZER.get());
        buildAround(helper, core);
        helper.assertTrue(MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core)),
            "the Pulverizer did not form from its components");
        var be = (PulverizerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(core));
        helper.assertTrue(be != null, "no Pulverizer BlockEntity");

        // POWERED THROUGH THE CAPABILITY, never by writing to be.battery() directly. The Trommel
        // shipped its first pass with no energy capability registered at all, and every test passed
        // because they took that shortcut - a machine that runs in the harness and is dead in the
        // world. Taking power the way a Solar Panel delivers it is what makes that impossible here.
        var energy = helper.getLevel().getCapability(
            Capabilities.Energy.BLOCK, helper.absolutePos(core), null);
        helper.assertTrue(energy != null,
            "the Pulverizer exposes no Capabilities.Energy.BLOCK, so no generator can reach it");
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(energy.insert(1_000_000, tx) > 0,
                "the Pulverizer refused energy through its capability");
            tx.commit();
        }
        return be;
    }

    /** The block above the machine's roof, where material is fed in. */
    private static BlockPos feedPoint(GameTestHelper helper, BlockPos core) {
        return helper.absolutePos(core).above(2);
    }

    static void register() {
        // It assembles from a Motor, two frames and four beams, and the Motor is the part that gates it.
        RCGameTests.test("a_pulverizer_forms_from_its_components", 40, helper -> {
            BlockPos core = new BlockPos(1, 1, 1);
            formAndPower(helper, core);
            var state = helper.getLevel().getBlockState(helper.absolutePos(core));
            helper.assertTrue(MultiblockCoreBlock.isFormed(state), "the core is not FORMED");
            Multiblock blueprint = ((MultiblockCoreBlock) RCBlocks.PULVERIZER.get()).blueprint();
            helper.assertTrue(blueprint.cells().size() == 7,
                "a 2x2x2 machine is a core plus seven cells, got " + blueprint.cells().size());
            helper.assertTrue(
                blueprint.cells().stream().anyMatch(c -> c.component() == RCBlocks.MOTOR.get()),
                "the Pulverizer must be gated on a Motor - no quantity of scrap forges one");
            helper.succeed();
        });

        // THE WHOLE POINT: it reduces. Bone in, bone meal out, and the output has to arrive somewhere a
        // player can collect it rather than on the floor.
        RCGameTests.test("a_pulverizer_mills_bone_into_bone_meal", 300, helper -> {
            BlockPos core = new BlockPos(1, 1, 1);
            PulverizerBlockEntity be = formAndPower(helper, core);

            BlockPos outlet = PulverizerCoreBlock.outlet(helper.getLevel(), helper.absolutePos(core));
            helper.getLevel().setBlockAndUpdate(outlet, Blocks.CHEST.defaultBlockState());
            var chest = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(outlet);
            helper.assertTrue(chest != null, "no chest at the discharge");

            BlockPos feed = feedPoint(helper, core);
            helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(),
                feed.getX() + 0.5, feed.getY() + 0.5, feed.getZ() + 0.5,
                new ItemStack(Items.BONE, 1)));

            helper.succeedWhen(() -> {
                helper.assertTrue(be.queuedCount() == 0, "the Pulverizer has not finished milling");
                int meal = 0;
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    ItemStack stack = chest.getItem(slot);
                    if (stack.is(Items.BONE_MEAL)) {
                        meal += stack.getCount();
                    }
                }
                helper.assertTrue(meal == 4,
                    "one bone must mill into exactly 4 bone meal, got " + meal);
            });
        });

        // It refuses what it has no recipe for, rather than swallowing it. A machine that eats an item
        // it cannot process and offers no way to get it back is worse than one that never took it.
        RCGameTests.test("a_pulverizer_leaves_alone_what_it_cannot_mill", 60, helper -> {
            BlockPos core = new BlockPos(1, 1, 1);
            PulverizerBlockEntity be = formAndPower(helper, core);
            BlockPos feed = feedPoint(helper, core);
            helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(),
                feed.getX() + 0.5, feed.getY() + 0.5, feed.getZ() + 0.5,
                new ItemStack(Items.DIAMOND, 3)));

            helper.runAfterDelay(30, () -> {
                helper.assertTrue(be.queuedCount() == 0,
                    "the Pulverizer swallowed a diamond it has no recipe for, and nothing can extract "
                        + "from this machine - so the player has no way to get it back");
                helper.succeed();
            });
        });

        // A container parked on the roof is drained. This is the route that runs without the player,
        // and it is the one nobody guesses, because every other automatable block is fed by pushing.
        RCGameTests.test("a_container_on_the_pulverizer_roof_is_drained", 200, helper -> {
            BlockPos core = new BlockPos(1, 1, 1);
            PulverizerBlockEntity be = formAndPower(helper, core);

            BlockPos hopper = helper.absolutePos(core).above(2);
            helper.getLevel().setBlockAndUpdate(hopper, Blocks.CHEST.defaultBlockState());
            var chest = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(hopper);
            helper.assertTrue(chest != null, "no chest on the roof");
            chest.setItem(0, new ItemStack(Items.BONE, 6));

            helper.succeedWhen(() -> helper.assertTrue(chest.getItem(0).getCount() < 6,
                "the Pulverizer did not take anything out of the chest standing on its roof"));
        });

        // The closed door, inherited from the Separator: no Container, no item capability, so nothing
        // can push in and no pipe can connect. It automates by reaching out instead.
        RCGameTests.test("the_pulverizer_is_unreachable_by_pipe_and_hopper", 40, helper -> {
            BlockPos core = new BlockPos(1, 1, 1);
            formAndPower(helper, core);
            BlockPos abs = helper.absolutePos(core);

            helper.assertTrue(
                helper.getLevel().getCapability(Capabilities.Item.BLOCK, abs, null) == null,
                "the Pulverizer exposes an item capability - a pipe could fill it");
            helper.assertTrue(
                !(helper.getLevel().getBlockEntity(abs) instanceof net.minecraft.world.Container),
                "the Pulverizer is a Container - a hopper could push into it");

            // Power is the one open door, and it opens one way. A consumer that hands energy back
            // trades the same charge with its own generator forever.
            var energy = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, abs, null);
            helper.assertTrue(energy != null, "the Pulverizer takes no energy - it cannot be powered");
            try (Transaction tx = Transaction.openRoot()) {
                helper.assertTrue(energy.extract(1000, tx) == 0,
                    "the Pulverizer gives energy back - it would trade charge with its own generator");
            }
            helper.succeed();
        });

        // THE GOLD CHAIN, END TO END (#120).
        //
        // Two stages, and the test asserts they CONNECT rather than that each exists: grinding boards
        // is what liberates the metal from the resin and glass holding it, and the smelter is what
        // recovers it. A chain whose halves are both present and do not meet is the failure mode a
        // per-recipe test cannot see - one stage's output has to be the next stage's input, by id.
        //
        // Stage two is minecraft:blasting on purpose, which is also the gate: a vanilla furnace cannot
        // run a blasting recipe at all and a vanilla blast furnace costs five iron ingots, so gold
        // cannot be short-circuited with a furnace made of stone.
        RCGameTests.test("the_gold_chain_runs_from_e_scrap_to_a_nugget", 300, helper -> {
            BlockPos core = new BlockPos(1, 1, 1);
            PulverizerBlockEntity be = formAndPower(helper, core);

            BlockPos outlet = PulverizerCoreBlock.outlet(helper.getLevel(), helper.absolutePos(core));
            helper.getLevel().setBlockAndUpdate(outlet, Blocks.CHEST.defaultBlockState());
            var chest = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(outlet);
            helper.assertTrue(chest != null, "no chest at the discharge");

            // STAGE ONE: enough E-Scrap for exactly one operation, which is ONE since the owner's
            // 2026-08-19 ruling that a GUI-less machine cannot take N > 1 inputs. It fed 4 while the
            // recipe wanted 4, "so the count is exercised too"; there is no count to exercise now, and
            // feeding 4 here just makes four separate runs and times the test out.
            int need = 1;
            BlockPos feed = feedPoint(helper, core);
            helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(),
                feed.getX() + 0.5, feed.getY() + 0.5, feed.getZ() + 0.5,
                new ItemStack(RCItems.E_SCRAP.get(), need)));

            helper.succeedWhen(() -> {
                helper.assertTrue(be.queuedCount() == 0, "the mill has not finished");
                ItemStack powder = ItemStack.EMPTY;
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    if (chest.getItem(slot).is(RCItems.CIRCUIT_POWDER.get())) {
                        powder = chest.getItem(slot);
                    }
                }
                helper.assertTrue(!powder.isEmpty(),
                    "grinding " + need + " E-Scrap must yield Circuit Powder - stage one of the gold "
                        + "chain produced nothing");

                // STAGE TWO: the smelter must actually accept what stage one made.
                var blasting = helper.getLevel().recipeAccess().getRecipeFor(
                    net.minecraft.world.item.crafting.RecipeType.BLASTING,
                    new net.minecraft.world.item.crafting.SingleRecipeInput(powder),
                    helper.getLevel());
                helper.assertTrue(blasting.isPresent(),
                    "nothing blasts Circuit Powder, so the chain stops halfway and the powder is a "
                        + "dead end the player cannot use");
                ItemStack out = blasting.get().value().assemble(
                    new net.minecraft.world.item.crafting.SingleRecipeInput(powder));
                helper.assertTrue(out.is(net.minecraft.world.item.Items.GOLD_NUGGET),
                    "the chain must end in a gold nugget, got " + out);
            });
        });

        // THE DISBAND CASCADE. Seven dummy cells, so seven chances to re-enter the removal hook and
        // re-drop the core. It also proves the blueprint is what decides what a cell gives back: the
        // Motor cell forms into ordinary housing, so a per-block loot table would hand back the wrong
        // part - which is exactly what it did on the Separator until #196.
        RCGameTests.test("breaking_a_pulverizer_cell_returns_the_motor_and_one_core", 80, helper -> {
            BlockPos core = new BlockPos(1, 1, 1);
            formAndPower(helper, core);

            var motorCell = ((MultiblockCoreBlock) RCBlocks.PULVERIZER.get()).blueprint().cells()
                .stream().filter(c -> c.component() == RCBlocks.MOTOR.get()).findFirst().orElse(null);
            helper.assertTrue(motorCell != null, "the Pulverizer blueprint has no Motor cell");
            BlockPos victim = helper.absolutePos(core).offset(motorCell.offset());
            helper.getLevel().destroyBlock(victim, true);

            helper.succeedWhen(() -> {
                helper.assertItemEntityCountIs(RCItems.PULVERIZER.get(), core, 8.0, 1);
                helper.assertItemEntityCountIs(RCItems.MOTOR.get(), core, 8.0, 1);
                helper.assertItemEntityCountIs(RCItems.PULVERIZER_HOUSING.get(), core, 8.0, 0);
                var state = helper.getLevel().getBlockState(helper.absolutePos(core));
                helper.assertTrue(!MultiblockCoreBlock.isFormed(state)
                        || helper.getLevel().getBlockState(helper.absolutePos(core)).isAir(),
                    "the core is still FORMED after a cell was broken");
            });
        });
    }
}

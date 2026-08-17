package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.registry.RCTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * The three reach-out machines must behave the same way, and this is what stops them drifting apart.
 *
 * <p><b>Why a whole class for it.</b> The Separator, the Trommel and the Pulverizer share one contract -
 * powered, GUI-less, no inventory anything can reach into, fed by reaching out, output routed to the
 * Scrap Network first - and each was built at a different time by copying the last one. Copying is how
 * they came to agree and also how they come apart: the Pulverizer shipped with <b>zero</b> Jade
 * providers against the Separator's four, so a machine holding nine slots of a player's material had no
 * way to show any of it. An audit found that. A test means the next one cannot repeat it.
 *
 * <p><b>Every rule here is derived from the world, not from a list of machine names.</b> A hand-list is
 * the thing that goes stale - it is the exact failure {@code GuidebookMultiblockTests} guards its own
 * list against - so these ask the registry which blocks are powered machines and then ask what those
 * machines have.
 */
final class MachineParityTests {

    /** Where a probe block is placed. Cleared between machines so nothing leaks between checks. */
    private static final BlockPos PROBE = new BlockPos(1, 1, 1);

    private MachineParityTests() {
    }

    /**
     * Every multiblock core that accepts power, which is the definition of "a machine" for these rules.
     *
     * <p>Measured by querying the capability at a real position rather than by reading the registration
     * code, because that is the thing a generator actually does. A machine whose capability is not
     * registered is invisible to this - and it is also invisible to every generator in the game, which
     * is precisely the defect the Trommel shipped with.
     */
    private static List<Block> poweredMachines(net.minecraft.gametest.framework.GameTestHelper helper) {
        List<Block> out = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof MultiblockCoreBlock)) {
                continue;
            }
            helper.setBlock(PROBE, Blocks.AIR);
            helper.setBlock(PROBE, block);
            if (helper.getLevel().getCapability(
                    Capabilities.Energy.BLOCK, helper.absolutePos(PROBE), null) != null) {
                out.add(block);
            }
        }
        helper.setBlock(PROBE, Blocks.AIR);
        return out;
    }

    private static String idOf(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).getPath();
    }

    private static boolean jadeClassExists(String simple) {
        try {
            Class.forName("com.flatts.recompile.compat.jade." + simple);
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    static void register() {
        // A MACHINE THAT HOLDS YOUR MATERIAL MUST BE ABLE TO SHOW IT.
        //
        // None of these has a screen and none is a Container, so Jade is the only surface that can say
        // what is inside, how much power it has, and why it has stopped. Without it the machine is a box
        // that swallowed nine stacks and will not discuss it.
        //
        // Checked by class name because that is the convention every one of them already follows, and
        // because the alternative - reading Jade's registry - needs Jade loaded and linked, which a
        // headless test cannot rely on. The names are asserted against machines DISCOVERED from the
        // registry, so a new machine is covered the day it is registered rather than the day someone
        // remembers to add it here.
        RCGameTests.test("every_powered_machine_has_jade_coverage", 60, helper -> {
            List<Block> machines = poweredMachines(helper);
            helper.assertTrue(machines.size() >= 3,
                "only " + machines.size() + " powered machine cores found - discovery is broken, so "
                    + "this test would pass by checking nothing");

            List<String> gaps = new ArrayList<>();
            for (Block machine : machines) {
                String name = idOf(machine);
                String camel = camel(name);
                for (String kind : List.of("Provider", "DataProvider", "StorageProvider",
                        "StorageClientProvider")) {
                    if (!jadeClassExists(camel + kind)) {
                        gaps.add(name + " has no " + camel + kind);
                    }
                }
            }
            helper.assertTrue(gaps.isEmpty(),
                "these powered machines cannot show a player what they are holding or why they have "
                    + "stopped, and nothing else in the game can: " + gaps);
            helper.succeed();
        });

        // AND IT MUST BE IN THE SCRAP NETWORK, because all three produce.
        //
        // A machine whose output only ever lands in its own chute makes the player empty it by hand,
        // which is a worse version of the station it replaced. The Trommel was in the code for the
        // network and absent from the TAG, so every routed item silently fell through - a whole feature
        // that existed in one file and nowhere else.
        RCGameTests.test("every_powered_machine_joins_the_scrap_network", 60, helper -> {
            List<Block> machines = poweredMachines(helper);
            helper.assertTrue(machines.size() >= 3,
                "only " + machines.size() + " powered machine cores found - discovery is broken");

            List<String> missing = new ArrayList<>();
            for (Block machine : machines) {
                if (!machine.defaultBlockState().is(RCTags.SCRAP_CONNECTABLE)) {
                    missing.add(idOf(machine));
                }
            }
            helper.assertTrue(missing.isEmpty(),
                "these machines produce material but are in no cluster, so nothing they make can be "
                    + "routed and the player empties them by hand: " + missing);
            helper.succeed();
        });

        // THE CLOSED DOOR, on all of them at once.
        //
        // Each machine has its own version of this test; this is the one that catches the machine that
        // forgot to have one. Exposing an item capability is how a machine accidentally becomes
        // pipe-fillable, and the whole automation policy rests on the distinction between reaching out
        // and being reached into.
        RCGameTests.test("no_powered_machine_can_be_reached_into", 60, helper -> {
            List<Block> machines = poweredMachines(helper);
            helper.assertTrue(machines.size() >= 3,
                "only " + machines.size() + " powered machine cores found - discovery is broken");

            List<String> open = new ArrayList<>();
            for (Block machine : machines) {
                helper.setBlock(PROBE, Blocks.AIR);
                helper.setBlock(PROBE, machine);
                BlockPos abs = helper.absolutePos(PROBE);
                if (helper.getLevel().getCapability(Capabilities.Item.BLOCK, abs, null) != null) {
                    open.add(idOf(machine) + " exposes an item capability - a pipe could fill it");
                }
                if (helper.getLevel().getBlockEntity(abs) instanceof net.minecraft.world.Container) {
                    open.add(idOf(machine) + " is a Container - a hopper could push into it");
                }
                // A consumer that hands energy back trades the same charge with its own generator.
                var energy = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, abs, null);
                if (energy != null) {
                    try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                        if (energy.extract(1000, tx) != 0) {
                            open.add(idOf(machine) + " gives energy back - it would trade charge with "
                                + "its own generator forever");
                        }
                    }
                }
            }
            helper.setBlock(PROBE, Blocks.AIR);
            helper.assertTrue(open.isEmpty(), "the closed door is open on: " + open);
            helper.succeed();
        });

        // AND A PLAYER MUST BE ABLE TO FIND THE WAY IN.
        //
        // None of these can be pushed into, which is unlike every other automatable block in the game,
        // so "how do I feed this" is a real question with a non-obvious answer. The Pulverizer needs it
        // most - a shredder's throat and a perforated drum are visibly openings, a sealed box is not -
        // but all three are equally unpushable, so all three owe the player a sentence.
        RCGameTests.test("every_powered_machine_says_how_to_feed_it", 60, helper -> {
            List<Block> machines = poweredMachines(helper);
            List<String> silent = new ArrayList<>();
            for (Block machine : machines) {
                String key = "jade." + Recompile.MOD_ID + "." + idOf(machine) + "_feed";
                if (!net.minecraft.locale.Language.getInstance().has(key)) {
                    silent.add(idOf(machine) + " has no " + key);
                }
            }
            helper.assertTrue(machines.size() >= 3, "discovery is broken");
            helper.assertTrue(silent.isEmpty(),
                "nothing can push into these machines, so a player has to be told how to feed them and "
                    + "these do not say: " + silent);
            helper.succeed();
        });
    }

    /** {@code trommel_core} style ids are not used here; every machine id is one or two words. */
    private static String camel(String path) {
        StringBuilder out = new StringBuilder();
        for (String part : path.split("_")) {
            if (!part.isEmpty()) {
                out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return out.toString();
    }
}

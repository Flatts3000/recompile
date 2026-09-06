package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.entity.FreightTerminalBlockEntity;
import com.flatts.recompile.content.block.ScrapNetwork;
import net.minecraft.world.item.Items;
import com.flatts.recompile.content.freight.FreightPhases;
import com.flatts.recompile.content.freight.FreightState;
import com.flatts.recompile.content.recipe.FreightPhaseRecipe;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCTags;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Freight Terminal (#387, spec {@code docs/freight_conversion_spec.md}).
 *
 * <p><b>Every test here resets the world's tier first.</b> {@link FreightState} is per-world and the
 * whole suite shares one, so a test that assumed it started at tier 0 would pass or fail depending on
 * which tests ran before it. That is exactly the kind of order dependence that makes a suite flaky in
 * CI and green locally, so the reset is not optional politeness.
 *
 * <p><b>What is proved here and not in JUnit:</b> the container refusal, the drain, the
 * double-advance guard and persistence all need a real level and a real block entity. The ladder's
 * arithmetic is pure and lives in {@code FreightLadderTest}.
 */
public final class FreightTerminalTests {

    private FreightTerminalTests() {
    }

    /** Terminals are placed well apart: the state is shared, so proximity is not the risk, order is. */
    private static BlockPos at(int index) {
        return new BlockPos(1 + index, 2, 1);
    }

    /** A terminal at a fresh position, with the world's ladder wound back to the start. */
    private static FreightTerminalBlockEntity terminal(GameTestHelper helper, int index) {
        ServerLevel level = helper.getLevel();
        FreightState.of(level).setTier(0);
        helper.setBlock(at(index), RCBlocks.FREIGHT_TERMINAL.get().defaultBlockState());
        return (FreightTerminalBlockEntity) level.getBlockEntity(helper.absolutePos(at(index)));
    }

    /** The first rung of whatever ladder the datapack shipped. */
    private static FreightPhaseRecipe firstPhase(GameTestHelper helper) {
        Optional<FreightPhaseRecipe> phase = FreightPhases.current(helper.getLevel(), 0);
        if (phase.isEmpty()) {
            throw new IllegalStateException("no freight phases loaded; the shipped ladder is empty");
        }
        return phase.get();
    }

    public static void register() {
        RCGameTests.test("the_freight_terminal_is_in_the_scrap_network", 10, helper -> {
            // Without this the Hauler, the Depot and every machine's output have nowhere to end up,
            // which is the whole point of the block. A tag membership nobody asserts is one that
            // silently falls out - this repo has lost the same tag entry five separate times.
            helper.assertTrue(
                RCBlocks.FREIGHT_TERMINAL.get().defaultBlockState().is(RCTags.SCRAP_CONNECTABLE),
                "the Freight Terminal is not in #recompile:scrap_connectable, so nothing can route to it");
            helper.succeed();
        });

        RCGameTests.test("the_network_routes_wanted_goods_into_the_terminal", 40, helper -> {
            // The third sink (#393). A Depot pushing into a cluster that contains a terminal must
            // actually reach it, which is the claim #387 made, had corrected on review because it was
            // false, and now finally implements.
            ServerLevel level = helper.getLevel();
            FreightTerminalBlockEntity terminal = terminal(helper, 6);
            FreightPhaseRecipe phase = firstPhase(helper);
            Item wanted = phase.requires().get(0).item();

            ItemStack routed = new ItemStack(wanted, 8);
            ItemStack left = ScrapNetwork.insertFromMember(level, helper.absolutePos(at(6)), routed, false);
            helper.assertTrue(left.isEmpty(),
                "the network did not route " + wanted + " into the terminal; " + left.getCount()
                    + " came back");
            // DISARM IT. The block ticks for real, so goods left on the strip would drain into the
            // shared per-world FreightState on some later tick, outside any test's control - and
            // a_delivery_is_counted_and_the_stack_is_spent asserts an exact delivered count. This
            // file's own header names that hazard; leaving a live terminal loaded would have been
            // creating it.
            terminal.clearContent();
            FreightState.of(level).setTier(0);
            helper.succeed();
        });

        RCGameTests.test("the_network_does_not_route_what_the_phase_does_not_want", 40, helper -> {
            // The conditional half, and the whole reason a third sink was acceptable. Without it the
            // terminal would swallow a player's sorted materials, which is why every FIXED priority
            // against the bins was rejected.
            ServerLevel level = helper.getLevel();
            terminal(helper, 7);
            FreightPhaseRecipe phase = firstPhase(helper);

            // THE ITEM IS FOUND, NOT ASSUMED. The first version guarded on
            // `if (phase.required(DIAMOND) == 0)` and then succeeded unconditionally, so a pack - or
            // one edit to phase one - that added a diamond line would have made this assert nothing
            // and still pass green. That is the exact vacuity the PR claimed to have checked for, and
            // checking the current data is not the same as checking the structure.
            Item unwantedItem = null;
            for (Item candidate : new Item[] {Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT,
                    Items.NETHER_STAR, Items.FEATHER}) {
                if (phase.required(candidate) == 0) {
                    unwantedItem = candidate;
                    break;
                }
            }
            helper.assertTrue(unwantedItem != null,
                "no candidate item is outside phase one's manifest, so this test cannot assert "
                    + "anything; pick a different candidate rather than letting it pass green");

            ItemStack unwanted = new ItemStack(unwantedItem, 8);
            ItemStack left = ScrapNetwork.insertFromMember(
                level, helper.absolutePos(at(7)), unwanted, false);
            helper.assertTrue(left.getCount() == 8,
                "the terminal absorbed " + (8 - left.getCount()) + " of "
                    + unwantedItem + ", which no phase line asks for");
            helper.succeed();
        });

        RCGameTests.test("routing_takes_only_what_the_rung_is_short_of", 40, helper -> {
            // THE BUG THIS PINS. canPlaceItem is a boolean - "is any more wanted" - which is right
            // for a hopper, because a hopper moves one item per transfer. The network moves a whole
            // stack once the gate says yes, so a 64 stack routed at a phase wanting 32 put all 64 on
            // the strip and stranded the excess in a block whose faces hand nothing back. That is the
            // precise "swallow a player's sorted materials" failure the conditional sink exists to
            // prevent, so the sink was not conditional at all until this.
            ServerLevel level = helper.getLevel();
            FreightTerminalBlockEntity terminal = terminal(helper, 9);
            FreightPhaseRecipe phase = firstPhase(helper);
            // THE LINE MUST NOT BE A WHOLE NUMBER OF STACKS, and the first version of this test did
            // not check that. Phase one's first line wants 64 pressed junk and a stack IS 64, so the
            // old whole-stack path coincidentally moved exactly the right amount and the test passed
            // against the very bug it was written for. Verified by reintroducing the bug: it stayed
            // green. Pick a line that can actually discriminate, and say so if none can.
            FreightPhaseRecipe.Requirement line = null;
            for (FreightPhaseRecipe.Requirement candidate : phase.requires()) {
                int max = new ItemStack(candidate.item()).getMaxStackSize();
                if (candidate.count() % max != 0) {
                    line = candidate;
                    break;
                }
            }
            helper.assertTrue(line != null,
                "every line of phase one wants a whole number of stacks, so this test cannot tell "
                    + "exact-amount routing from whole-stack routing; give a phase line a count that "
                    + "is not a multiple of its stack size");

            int want = line.count();
            int offered = want + 40;
            ItemStack routed = new ItemStack(line.item(), offered);
            ItemStack left = ScrapNetwork.insertFromMember(
                level, helper.absolutePos(at(9)), routed, false);

            helper.assertTrue(left.getCount() == offered - want,
                "routing should have taken exactly the " + want + " outstanding and returned "
                    + (offered - want) + "; it returned " + left.getCount());

            int onStrip = 0;
            for (int slot = 0; slot < FreightTerminalBlockEntity.SLOT_COUNT; slot++) {
                onStrip += terminal.getItem(slot).getCount();
            }
            helper.assertTrue(onStrip == want,
                "the strip holds " + onStrip + " but the rung was only short of " + want
                    + "; the excess would strand");

            terminal.clearContent();
            FreightState.of(level).setTier(0);
            helper.succeed();
        });

        RCGameTests.test("a_terminal_is_not_storage_even_though_it_is_a_sink", 20, helper -> {
            // reachesStorage answers "is there anywhere to KEEP this", and a terminal spends rather
            // than keeps. A machine told yes because a terminal was adjacent would be telling its
            // user their output is safe while it is being consumed.
            ServerLevel level = helper.getLevel();
            terminal(helper, 8);
            helper.assertTrue(!ScrapNetwork.reachesStorage(level, helper.absolutePos(at(8))),
                "a lone Freight Terminal reported itself as storage");
            helper.succeed();
        });

        RCGameTests.test("the_shipped_ladder_is_dense_and_starts_at_one", 10, helper -> {
            // Guards the data rather than the code. A gap or a duplicate truncates the ladder
            // silently, so the mod could ship four reachable phases while claiming eight.
            List<FreightPhaseRecipe> phases = FreightPhases.sorted(helper.getLevel());
            helper.assertTrue(!phases.isEmpty(), "no freight phases loaded at all");
            for (int i = 0; i < phases.size(); i++) {
                int tier = phases.get(i).tier();
                helper.assertTrue(tier == i + 1,
                    "the ladder is not dense: position " + i + " is tier " + tier);
            }
            helper.succeed();
        });

        RCGameTests.test("the_terminal_takes_what_the_phase_wants", 20, helper -> {
            FreightTerminalBlockEntity terminal = terminal(helper, 0);
            FreightPhaseRecipe phase = firstPhase(helper);
            Item wanted = phase.requires().get(0).item();

            helper.assertTrue(terminal.canPlaceItem(0, new ItemStack(wanted)),
                "the terminal refused an item its own current phase asks for");
            helper.assertTrue(
                terminal.canPlaceItemThroughFace(0, new ItemStack(wanted), Direction.UP),
                "a pipe could not insert what the phase wants");
            helper.succeed();
        });

        RCGameTests.test("the_terminal_refuses_what_the_phase_does_not_want", 20, helper -> {
            // The safety is refusal, not a buffer. A hopper must back up visibly rather than the
            // terminal eating a stack of something the player wanted.
            FreightTerminalBlockEntity terminal = terminal(helper, 1);
            FreightPhaseRecipe phase = firstPhase(helper);

            ItemStack unwanted = new ItemStack(net.minecraft.world.item.Items.DIAMOND);
            if (phase.required(unwanted.getItem()) == 0) {
                helper.assertTrue(!terminal.canPlaceItem(0, unwanted),
                    "the terminal accepted an item no phase line asks for");
                helper.assertTrue(!terminal.canPlaceItemThroughFace(0, unwanted, Direction.UP),
                    "a pipe could insert an item the phase does not want");
            }
            helper.succeed();
        });

        RCGameTests.test("nothing_can_be_pulled_back_out_of_the_terminal", 20, helper -> {
            // Otherwise a hopper under it is a way to launder progress out of a phase mid-drain.
            FreightTerminalBlockEntity terminal = terminal(helper, 2);
            for (int slot = 0; slot < FreightTerminalBlockEntity.SLOT_COUNT; slot++) {
                helper.assertTrue(
                    !terminal.canTakeItemThroughFace(slot, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT), Direction.DOWN),
                    "slot " + slot + " could be drained through a face");
            }
            helper.succeed();
        });

        RCGameTests.test("a_delivery_is_counted_and_the_stack_is_spent", 20, helper -> {
            ServerLevel level = helper.getLevel();
            FreightTerminalBlockEntity terminal = terminal(helper, 3);
            FreightPhaseRecipe phase = firstPhase(helper);
            FreightPhaseRecipe.Requirement line = phase.requires().get(0);

            terminal.setItem(0, new ItemStack(line.item(), 4));
            BlockPos world = helper.absolutePos(at(3));
            FreightTerminalBlockEntity.serverTick(level, world, level.getBlockState(world), terminal);

            helper.assertTrue(FreightState.of(level).delivered(line.item()) == 4,
                "expected 4 delivered, got " + FreightState.of(level).delivered(line.item()));
            helper.assertTrue(terminal.getItem(0).isEmpty(),
                "the delivered stack was not consumed");
            helper.succeed();
        });

        RCGameTests.test("completing_a_phase_advances_the_tier_exactly_once", 60, helper -> {
            // The double-advance guard. Two full slots draining on one tick would otherwise each see
            // a satisfied phase; FreightState.completePhase re-reads the tier rather than trusting
            // the one the caller captured.
            ServerLevel level = helper.getLevel();
            FreightTerminalBlockEntity terminal = terminal(helper, 4);
            FreightPhaseRecipe phase = firstPhase(helper);

            // Fill every line to its full count, spread across slots.
            int slot = 0;
            for (FreightPhaseRecipe.Requirement line : phase.requires()) {
                int left = line.count();
                while (left > 0 && slot < FreightTerminalBlockEntity.SLOT_COUNT) {
                    int put = Math.min(left, line.item().getDefaultMaxStackSize());
                    terminal.setItem(slot++, new ItemStack(line.item(), put));
                    left -= put;
                }
            }
            // Tick until the phase falls, or give up loudly.
            BlockPos world = helper.absolutePos(at(4));
            for (int i = 0; i < 20 && FreightState.of(level).tier() == 0; i++) {
                FreightTerminalBlockEntity.serverTick(level, world, level.getBlockState(world), terminal);
            }
            helper.assertTrue(FreightState.of(level).tier() == 1,
                "expected tier 1 after satisfying phase 1, got " + FreightState.of(level).tier());

            // Tick again with the strip empty: nothing should move.
            FreightTerminalBlockEntity.serverTick(level, world, level.getBlockState(world), terminal);
            helper.assertTrue(FreightState.of(level).tier() == 1,
                "the tier advanced twice; the completion guard did not hold");
            helper.succeed();
        });

        RCGameTests.test("the_manifest_follows_the_tier_rather_than_going_stale", 60, helper -> {
            // The phase is CACHED on the block entity, because resolving it scans and sorts the
            // recipe map and that ran on every hopper insert and thirteen times a tick per open
            // screen. A cache that did not follow a completed phase would leave the strip accepting
            // the old phase's goods and the screen drawing the old manifest.
            ServerLevel level = helper.getLevel();
            FreightTerminalBlockEntity terminal = terminal(helper, 5);
            helper.assertTrue(terminal.manifest().tier() == 0,
                "a fresh terminal did not report tier 0");

            FreightState.of(level).setTier(1);
            BlockPos world = helper.absolutePos(at(5));
            FreightTerminalBlockEntity.serverTick(level, world, level.getBlockState(world), terminal);
            helper.assertTrue(terminal.manifest().tier() == 1,
                "the manifest still reports tier " + terminal.manifest().tier()
                    + " after the world moved to tier 1");
            FreightState.of(level).setTier(0);
            helper.succeed();
        });

        RCGameTests.test("progress_resets_when_a_phase_completes", 60, helper -> {
            // Otherwise leftover progress on a shared item would part-fill the next phase for free.
            ServerLevel level = helper.getLevel();
            FreightState state = FreightState.of(level);
            state.setTier(0);
            FreightPhaseRecipe phase = firstPhase(helper);
            FreightPhaseRecipe.Requirement line = phase.requires().get(0);

            state.deliver(line.item(), 3);
            helper.assertTrue(state.delivered(line.item()) == 3, "delivery did not count");
            helper.assertTrue(state.completePhase(0), "completePhase refused from the tier it was given");
            helper.assertTrue(state.delivered(line.item()) == 0,
                "progress survived a phase completion");
            helper.succeed();
        });

        RCGameTests.test("a_stale_tier_cannot_complete_a_phase", 20, helper -> {
            ServerLevel level = helper.getLevel();
            FreightState state = FreightState.of(level);
            state.setTier(2);
            helper.assertTrue(!state.completePhase(0),
                "a caller holding tier 0 advanced a world already on tier 2");
            helper.assertTrue(state.tier() == 2, "the tier moved on a refused completion");
            state.setTier(0);
            helper.succeed();
        });
    }
}

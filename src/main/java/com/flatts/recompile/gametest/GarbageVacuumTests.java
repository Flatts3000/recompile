package com.flatts.recompile.gametest;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.content.block.MoundGroundBlock;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.content.block.entity.ChargingStationBlockEntity;
import com.flatts.recompile.content.item.GarbageVacuumItem;
import com.flatts.recompile.content.item.GarbageVacuumItem.Intake;
import com.flatts.recompile.content.item.VacuumTier;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCEntities;
import com.flatts.recompile.registry.RCItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * GameTests for the Garbage Vacuum and its Charging Station (#336, spec {@code docs/garbage_vacuum_spec.md}).
 *
 * <p>Driven through the static entry points - {@link GarbageVacuumItem#intakeOnce} and
 * {@link ChargingStationBlockEntity#chargeOnce} - rather than by simulating a held right-click, the
 * {@code sortOnce} convention. The flight of a vacuumed block is the one thing that is allowed to run
 * on real ticks, because arriving is the contract and a test that delivers the item by hand would prove
 * nothing about the entity that carries it.
 *
 * <p>Players are made SURVIVAL first. {@code makeMockServerPlayerInLevel} has {@code instabuild} set,
 * and a creative player takes for free - so without that line every charge assertion here would pass
 * for the wrong reason.
 */
final class GarbageVacuumTests {

    private GarbageVacuumTests() {
    }

    /** Where the intake is aimed; the plot is 5x5x5 so a radius-2 volume around it stays inside. */
    private static final BlockPos AIM = new BlockPos(1, 2, 1);
    /** Three blocks from AIM along x: outside copper's reach (2), inside iron's (3). */
    private static final BlockPos FAR = new BlockPos(4, 2, 1);
    private static final BlockPos FOOT = new BlockPos(2, 1, 2);
    private static final BlockPos DOCK = new BlockPos(1, 1, 1);

    /**
     * A survival player STANDING IN THE PLOT. {@code makeMockServerPlayerInLevel} places its player at
     * world spawn, which on the gametest server is thousands of blocks from the structure; a flying
     * block aimed at that player leaves the loaded chunks and stops ticking, which is a real edge case
     * (the entity now delivers past {@code MAX_FLIGHT_DISTANCE}) but not the flight these tests mean
     * to exercise.
     */
    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        Vec3 standing = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
        player.setPos(standing.x, standing.y, standing.z);
        return player;
    }

    /**
     * A short delivery drop for the regrowth assertion, {@code MoundRegrowthTests}' own reason: the
     * shipped 30-block drop reaches out of the plot into whatever the test world has above it and
     * reports BLOCKED for reasons that have nothing to do with the rule under test.
     */
    private static void withShortDrop(Runnable body) {
        int was = RCConfig.MOUND_REGROWTH_DROP_HEIGHT.get();
        try {
            RCConfig.MOUND_REGROWTH_DROP_HEIGHT.set(2);
            body.run();
        } finally {
            RCConfig.MOUND_REGROWTH_DROP_HEIGHT.set(was);
        }
    }

    private static ItemStack vacuum(DeferredItem<GarbageVacuumItem> tier, int charge) {
        ItemStack stack = new ItemStack(tier.get());
        GarbageVacuumItem.setCharge(stack, charge);
        return stack;
    }

    private static Vec3 centreOf(GameTestHelper helper, BlockPos pos) {
        return Vec3.atCenterOf(helper.absolutePos(pos));
    }

    private static int garbageCost() {
        return VacuumTier.costFor(SortableBlock.sortRolls(RCBlocks.GARBAGE_BLOCK.get().asItem()));
    }

    static void register() {
        // The type gate. Derived by class, so a pile is taken and a stone beside it is not, and the
        // block leaves the world without dropping itself on the floor - the entity carries it.
        RCGameTests.test("the_vacuum_takes_a_pile_and_leaves_the_stone_beside_it", 20, helper -> {
            helper.setBlock(AIM, RCBlocks.GARBAGE_BLOCK.get());
            helper.setBlock(AIM.east(), Blocks.STONE);
            ServerLevel level = helper.getLevel();
            ServerPlayer player = survivalPlayer(helper);
            ItemStack stack = vacuum(RCItems.COPPER_GARBAGE_VACUUM, 4_000);

            Intake first = GarbageVacuumItem.intakeOnce(level, player, stack, centreOf(helper, AIM));
            helper.assertTrue(first == Intake.TOOK, "a charged vacuum aimed at garbage must take it, got " + first);
            helper.assertBlockPresent(Blocks.AIR, AIM);
            helper.assertBlockPresent(Blocks.STONE, AIM.east());
            helper.assertEntityPresent(RCEntities.VACUUMED_BLOCK.get());
            helper.assertTrue(GarbageVacuumItem.charge(stack) == 4_000 - garbageCost(),
                "one Block of Garbage must cost exactly its rolls times FE_PER_ROLL, charge is "
                    + GarbageVacuumItem.charge(stack));

            Intake second = GarbageVacuumItem.intakeOnce(level, player, stack, centreOf(helper, AIM));
            helper.assertTrue(second == Intake.NOTHING_IN_RANGE,
                "with only stone left in range the vacuum must take nothing, got " + second);
            helper.succeed();
        });

        // A TAP takes a block. A quick click starts and releases the use inside one tick, so the use
        // loop never runs for it; the first intake is on the click itself. Found by driving the dev
        // client through devbridge, whose `use` verb is exactly a tap: three taps, nothing taken, a
        // tool that only worked when held. The mock player looks along +z from the plot's edge, so
        // the aim point five blocks out is in the air beside a pile within copper's reach.
        RCGameTests.test("a_tap_takes_one_block_and_starts_the_stream", 20, helper -> {
            BlockPos pile = new BlockPos(2, 2, 4);
            helper.setBlock(pile, RCBlocks.GARBAGE_BLOCK.get());
            ServerLevel level = helper.getLevel();
            ServerPlayer player = survivalPlayer(helper);
            Vec3 edge = helper.absoluteVec(new Vec3(2.5, 1.0, 0.5));
            player.setPos(edge.x, edge.y, edge.z);
            player.setYRot(0.0F);
            player.setXRot(0.0F);
            ItemStack stack = vacuum(RCItems.COPPER_GARBAGE_VACUUM, 4_000);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);

            InteractionResult result = stack.getItem().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(result.consumesAction(), "a click on a charged vacuum must be consumed, got " + result);
            helper.assertBlockPresent(Blocks.AIR, pile);
            helper.assertTrue(GarbageVacuumItem.charge(player.getMainHandItem()) == 4_000 - garbageCost(),
                "the click itself must take and pay for one block, charge is "
                    + GarbageVacuumItem.charge(player.getMainHandItem()));
            helper.assertTrue(player.isUsingItem(), "and the hold must be running so a held click keeps taking");
            helper.succeed();
        });

        // The charge gate. A vacuum that can pay for exactly one block takes one and then reports
        // FLAT with the second pile still standing - the item's own use loop reads FLAT to let go.
        RCGameTests.test("each_block_costs_charge_and_a_flat_vacuum_stops", 20, helper -> {
            helper.setBlock(AIM, RCBlocks.GARBAGE_BLOCK.get());
            helper.setBlock(AIM.above(), RCBlocks.GARBAGE_BLOCK.get());
            ServerLevel level = helper.getLevel();
            ServerPlayer player = survivalPlayer(helper);
            ItemStack stack = vacuum(RCItems.COPPER_GARBAGE_VACUUM, garbageCost());

            helper.assertTrue(GarbageVacuumItem.intakeOnce(level, player, stack, centreOf(helper, AIM)) == Intake.TOOK,
                "one block's worth of charge must take one block");
            helper.assertTrue(GarbageVacuumItem.charge(stack) == 0, "and leave the vacuum flat");
            Intake next = GarbageVacuumItem.intakeOnce(level, player, stack, centreOf(helper, AIM));
            helper.assertTrue(next == Intake.FLAT, "a flat vacuum facing a pile must report FLAT, got " + next);
            helper.assertBlockPresent(RCBlocks.GARBAGE_BLOCK.get(), AIM.above());
            helper.succeed();
        });

        // Creative takes for free. Asserted on purpose rather than left as an accident of instabuild,
        // because a creative player who cannot clear a mound would file it as a bug.
        RCGameTests.test("a_creative_player_vacuums_for_free", 20, helper -> {
            helper.setBlock(AIM, RCBlocks.GARBAGE_BLOCK.get());
            ServerPlayer creative = helper.makeMockServerPlayerInLevel();
            ItemStack stack = vacuum(RCItems.COPPER_GARBAGE_VACUUM, 0);
            Intake result = GarbageVacuumItem.intakeOnce(helper.getLevel(), creative, stack, centreOf(helper, AIM));
            helper.assertTrue(result == Intake.TOOK, "creative must take with no charge, got " + result);
            helper.assertTrue(GarbageVacuumItem.charge(stack) == 0, "and spend nothing");
            helper.succeed();
        });

        // Reach is the tier. Three blocks out is beyond copper and inside iron; both are asserted so
        // neither half can pass because the volume happened to be huge or empty.
        RCGameTests.test("reach_scales_with_the_tier", 20, helper -> {
            helper.setBlock(FAR, RCBlocks.GARBAGE_BLOCK.get());
            ServerLevel level = helper.getLevel();
            ServerPlayer player = survivalPlayer(helper);
            Intake copper = GarbageVacuumItem.intakeOnce(level, player,
                vacuum(RCItems.COPPER_GARBAGE_VACUUM, 4_000), centreOf(helper, AIM));
            helper.assertTrue(copper == Intake.NOTHING_IN_RANGE,
                "a block 3 out must be beyond copper's radius of " + VacuumTier.COPPER.radius() + ", got " + copper);
            helper.assertBlockPresent(RCBlocks.GARBAGE_BLOCK.get(), FAR);
            Intake iron = GarbageVacuumItem.intakeOnce(level, player,
                vacuum(RCItems.IRON_GARBAGE_VACUUM, 8_000), centreOf(helper, AIM));
            helper.assertTrue(iron == Intake.TOOK,
                "and inside iron's radius of " + VacuumTier.IRON.radius() + ", got " + iron);
            helper.succeed();
        });

        // The contract of the animation: the block ARRIVES. Real ticks, because the entity's flight,
        // timeout and delivery are what is under test; the item lands in the owner's inventory and
        // nothing is left flying.
        RCGameTests.test("a_vacuumed_block_arrives_in_the_owners_inventory", 140, helper -> {
            helper.setBlock(AIM, RCBlocks.GARBAGE_BLOCK.get());
            ServerPlayer player = survivalPlayer(helper);
            Item garbage = RCBlocks.GARBAGE_BLOCK.get().asItem();
            helper.assertTrue(player.getInventory().countItem(garbage) == 0, "the mock player starts empty-handed");
            Intake result = GarbageVacuumItem.intakeOnce(helper.getLevel(), player,
                vacuum(RCItems.COPPER_GARBAGE_VACUUM, 4_000), centreOf(helper, AIM));
            helper.assertTrue(result == Intake.TOOK, "setup: the block must be taken, got " + result);
            helper.succeedWhen(() -> {
                helper.assertTrue(player.getInventory().countItem(garbage) == 1,
                    "the flying block must land in the owner's inventory as one Block of Garbage");
                helper.assertEntityNotPresent(RCEntities.VACUUMED_BLOCK.get());
            });
        });

        // The owner's gravity ruling: take the foot of a stack and the rest falls. Asserted through
        // vanilla's own falling-block entity, which only exists if the neighbour update fired - a
        // removal that suppressed updates would leave the stack hanging and this red.
        RCGameTests.test("taking_the_foot_of_a_stack_lets_it_collapse", 40, helper -> {
            helper.setBlock(FOOT, RCBlocks.GARBAGE_BLOCK.get());
            helper.setBlock(FOOT.above(), RCBlocks.GARBAGE_BLOCK.get());
            helper.setBlock(FOOT.above(2), RCBlocks.GARBAGE_BLOCK.get());
            ServerPlayer player = survivalPlayer(helper);
            Intake result = GarbageVacuumItem.intakeOnce(helper.getLevel(), player,
                vacuum(RCItems.COPPER_GARBAGE_VACUUM, 4_000), centreOf(helper, FOOT));
            helper.assertTrue(result == Intake.TOOK, "setup: the foot must be taken, got " + result);
            helper.assertBlockPresent(Blocks.AIR, FOOT);
            helper.runAfterDelay(6, () -> {
                helper.assertEntityPresent(EntityType.FALLING_BLOCK);
                helper.succeed();
            });
        });

        // Phase 5's promise survives the tool: strip a mound to its ground and the ground still
        // remembers it. This is the assertion the issue asked for by name - a bulk tool is only
        // reasonable because mounds are renewable, and an "optimisation" that took the ground with
        // the garbage would turn it extractive in silence.
        RCGameTests.test("a_stripped_mound_still_regrows", 40, helper -> {
            helper.setBlock(FOOT, RCBlocks.MOUND_GROUND.get().defaultBlockState()
                .setValue(MoundGroundBlock.HEIGHT, 2));
            helper.setBlock(FOOT.above(), RCBlocks.GARBAGE_BLOCK.get());
            helper.setBlock(FOOT.above(2), RCBlocks.GARBAGE_BLOCK.get());
            ServerLevel level = helper.getLevel();
            ServerPlayer player = survivalPlayer(helper);
            ItemStack stack = vacuum(RCItems.COPPER_GARBAGE_VACUUM, 4_000);
            Vec3 aim = centreOf(helper, FOOT.above());
            helper.assertTrue(GarbageVacuumItem.intakeOnce(level, player, stack, aim) == Intake.TOOK, "setup: first block");
            helper.assertTrue(GarbageVacuumItem.intakeOnce(level, player, stack, aim) == Intake.TOOK, "setup: second block");
            helper.assertBlockPresent(RCBlocks.MOUND_GROUND.get(), FOOT);
            helper.assertBlockPresent(Blocks.AIR, FOOT.above());
            helper.assertBlockPresent(Blocks.AIR, FOOT.above(2));
            withShortDrop(() -> {
                MoundGroundBlock.Outcome outcome = MoundGroundBlock.regrowOnce(level, helper.absolutePos(FOOT));
                helper.assertTrue(outcome == MoundGroundBlock.Outcome.GREW,
                    "a mound stripped by the vacuum must regrow like one dug by hand, got " + outcome);
            });
            helper.succeed();
        });

        // The dock. Its buffer is filled directly (a generator is not under test), it pushes into the
        // docked vacuum at TRANSFER_PER_TICK through the ITEM capability, and its doors are what the
        // automation policy says: energy in only, no item capability, not a Container.
        RCGameTests.test("the_charging_station_charges_a_docked_vacuum_and_is_energy_in_only", 20, helper -> {
            helper.setBlock(DOCK, RCBlocks.CHARGING_STATION.get());
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(DOCK);
            if (!(level.getBlockEntity(abs) instanceof ChargingStationBlockEntity dock)) {
                helper.fail("the charging station has no BlockEntity");
                return;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                dock.battery().insert(2_000, transaction);
                transaction.commit();
            }
            helper.assertTrue(dock.dock(vacuum(RCItems.COPPER_GARBAGE_VACUUM, 0)).isEmpty(),
                "docking onto an empty station hands nothing back");
            for (int i = 0; i < 3; i++) {
                ChargingStationBlockEntity.chargeOnce(level, abs);
            }
            int expected = 3 * ChargingStationBlockEntity.TRANSFER_PER_TICK;
            helper.assertTrue(GarbageVacuumItem.charge(dock.docked()) == expected,
                "three ticks must move " + expected + " FE into the vacuum, it holds " + GarbageVacuumItem.charge(dock.docked()));
            helper.assertTrue(dock.stored() == 2_000 - expected,
                "and the buffer must drop by exactly that, it holds " + dock.stored());

            EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, abs, null);
            helper.assertTrue(handler != null, "the station must expose Capabilities.Energy.BLOCK or no generator can reach it");
            int accepted;
            int given;
            try (Transaction transaction = Transaction.openRoot()) {
                accepted = handler.insert(100, transaction);
                given = handler.extract(100, transaction);
            }
            helper.assertTrue(accepted == 100, "a generator must be able to push 100 FE in, got " + accepted);
            helper.assertTrue(given == 0, "and nothing may pull FE back out, got " + given);
            helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK, abs, null) == null,
                "no item capability: a pipe must not be able to lift the vacuum off the dock");
            helper.assertTrue(!(dock instanceof Container),
                "not a Container: a hopper must not be able to lift the vacuum off the dock either");
            helper.succeed();
        });

        // A full vacuum stops the dock, so a buffer is never spent into a tool that cannot hold it.
        RCGameTests.test("a_full_vacuum_takes_no_more_charge", 20, helper -> {
            helper.setBlock(DOCK, RCBlocks.CHARGING_STATION.get());
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(DOCK);
            if (!(level.getBlockEntity(abs) instanceof ChargingStationBlockEntity dock)) {
                helper.fail("the charging station has no BlockEntity");
                return;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                dock.battery().insert(2_000, transaction);
                transaction.commit();
            }
            dock.dock(vacuum(RCItems.COPPER_GARBAGE_VACUUM, VacuumTier.COPPER.capacity()));
            int moved = ChargingStationBlockEntity.chargeOnce(level, abs);
            helper.assertTrue(moved == 0, "a full vacuum must take nothing, got " + moved);
            helper.assertTrue(dock.stored() == 2_000, "and the buffer must be untouched, it holds " + dock.stored());
            helper.succeed();
        });

        // Breaking the dock hands back both the dock and what was on it. The vacuum is the expensive
        // half, and a machine that ate it on removal is the pattern this mod has shipped four times.
        RCGameTests.test("breaking_the_charging_station_drops_the_docked_vacuum", 20, helper -> {
            helper.setBlock(DOCK, RCBlocks.CHARGING_STATION.get());
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(DOCK);
            if (!(level.getBlockEntity(abs) instanceof ChargingStationBlockEntity dock)) {
                helper.fail("the charging station has no BlockEntity");
                return;
            }
            dock.dock(vacuum(RCItems.COPPER_GARBAGE_VACUUM, 1_234));
            // GameTestHelper.destroyBlock passes dropBlock=false; the level's own call runs the loot.
            level.destroyBlock(abs, true);
            helper.assertItemEntityPresent(RCItems.COPPER_GARBAGE_VACUUM.get(), DOCK, 2.0);
            helper.assertItemEntityPresent(RCItems.CHARGING_STATION.get(), DOCK, 2.0);
            helper.succeed();
        });

        // Every tier answers the item energy capability over its component, at its own capacity. This
        // is the door the station uses and the one any other mod's charger would; a tier missing from
        // the registration loop would dock and never fill, with nothing logged.
        RCGameTests.test("every_vacuum_tier_exposes_the_item_energy_capability", 20, helper -> {
            for (DeferredItem<GarbageVacuumItem> tier : RCItems.GARBAGE_VACUUMS) {
                ItemStack stack = new ItemStack(tier.get());
                EnergyHandler handler = ItemAccess.forStack(stack).getCapability(Capabilities.Energy.ITEM);
                helper.assertTrue(handler != null, tier.getId() + " must expose Capabilities.Energy.ITEM");
                helper.assertTrue(handler.getCapacityAsInt() == tier.get().tier().capacity(),
                    tier.getId() + " capacity through the capability must be its tier's, got " + handler.getCapacityAsInt());
                try (Transaction transaction = Transaction.openRoot()) {
                    handler.insert(100, transaction);
                    transaction.commit();
                }
                helper.assertTrue(GarbageVacuumItem.charge(stack) == 100,
                    tier.getId() + ": 100 FE in through the capability must read as 100 on the component, got "
                        + GarbageVacuumItem.charge(stack));
            }
            helper.succeed();
        });

        // Copy is a silent failure: a missing key renders as itself and only shows in a client the
        // tests never run.
        RCGameTests.test("garbage_vacuum_lang_keys_resolve", 20, helper -> {
            List<String> missing = new ArrayList<>();
            for (String key : List.of(
                    "message.recompile.vacuum_flat", "tooltip.recompile.vacuum_radius",
                    // Jade names the entity under the crosshair mid-flight; the dev client showed
                    // the raw key there before this line existed.
                    "entity.recompile.vacuumed_block",
                    "jade.recompile.charging", "jade.recompile.dock_empty",
                    "book.recompile.guide.power.garbage_vacuum.name",
                    "book.recompile.guide.power.garbage_vacuum.intro.title",
                    "book.recompile.guide.power.garbage_vacuum.intro.text")) {
                if (Component.translatable(key).getString().equals(key)) {
                    missing.add(key);
                }
            }
            helper.assertTrue(missing.isEmpty(),
                "these keys render as their own name, so they are missing from en_us.json: " + missing);
            helper.succeed();
        });
    }
}

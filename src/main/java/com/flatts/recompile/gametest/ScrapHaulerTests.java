package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.LeachateBlock;
import com.flatts.recompile.content.block.entity.HaulerDepotBlockEntity;
import com.flatts.recompile.content.entity.ScrapHaulerEntity;
import com.flatts.recompile.content.entity.ScrapHaulerGoal;
import com.flatts.recompile.content.item.ScrapHaulerItem;
import com.flatts.recompile.content.menu.HaulerDepotMenu;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCTags;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * The Scrap Hauler and its Depot (#376, spec {@code docs/scrap_hauler_spec.md}).
 *
 * <p><b>The conservation invariant gets a test per code path</b>, because the paths are separate and
 * this repo has already paid for covering only one: the GUI slot, the shift-click, the hopper face,
 * the break-while-deployed, and the duplicated Deploy. Then the loop itself, the biology opt-outs, and
 * the two things that have to be true of the block for the feature to exist at all - that it is in the
 * Scrap Network tag, and that it pushes into the network.
 *
 * <p>The plot is five blocks across and the Hauler's reach is 2.6, so the near-target loop completes
 * without navigation and the far-target one needs a step. That is deliberate: the state machine is
 * proved here, and the navigation across real mound terrain is proved in a client, which is the only
 * place it can be.
 */
public final class ScrapHaulerTests {

    private static final BlockPos DEPOT = new BlockPos(2, 1, 2);
    private static final BlockPos NEAR_PILE = new BlockPos(2, 1, 4);
    private static final BlockPos FAR_PILE = new BlockPos(0, 1, 4);

    private ScrapHaulerTests() {
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static HaulerDepotBlockEntity depot(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(DEPOT, RCBlocks.HAULER_DEPOT.get());
        var be = helper.getLevel().getBlockEntity(helper.absolutePos(DEPOT));
        helper.assertTrue(be instanceof HaulerDepotBlockEntity, "no Depot block entity");
        return (HaulerDepotBlockEntity) be;
    }

    private static ItemStack chargedHauler(int charge) {
        ItemStack stack = new ItemStack(RCItems.SCRAP_HAULER.get());
        ScrapHaulerItem.setCharge(stack, charge);
        return stack;
    }

    private static HaulerDepotBlockEntity docked(GameTestHelper helper, int charge) {
        HaulerDepotBlockEntity depot = depot(helper);
        depot.setItem(HaulerDepotBlockEntity.HAULER_SLOT, chargedHauler(charge));
        return depot;
    }

    private static List<ScrapHaulerEntity> haulers(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ScrapHaulerEntity.class,
            AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(-8, -8, -8)),
                helper.absolutePos(new BlockPos(13, 13, 13))));
    }

    private static int holdCount(HaulerDepotBlockEntity depot) {
        int n = 0;
        for (int i = HaulerDepotBlockEntity.CARGO_START; i < HaulerDepotBlockEntity.SLOT_COUNT; i++) {
            n += depot.getItem(i).getCount();
        }
        return n;
    }

    static void register() {

        // ---- the invariant, one path at a time ------------------------------------------------

        RCGameTests.test("deploying_locks_the_slot_and_the_item_stays_in_it", 20, helper -> {
            ServerLevel level = helper.getLevel();
            HaulerDepotBlockEntity depot = docked(helper, ScrapHaulerItem.CAPACITY);
            helper.assertTrue(depot.deploy(level), "the Depot refused to deploy a docked Hauler");
            helper.assertTrue(depot.deployed(), "deploy did not mark the Depot deployed");
            helper.assertTrue(haulers(helper).size() == 1, "expected exactly one Hauler, found " + haulers(helper).size());
            helper.assertTrue(depot.hauler().getItem() instanceof ScrapHaulerItem,
                "the item left the slot on deploy - it must stay, locked, or the invariant has no item half");
            helper.assertTrue(!depot.canPlaceItem(HaulerDepotBlockEntity.HAULER_SLOT, chargedHauler(0)),
                "a second Hauler could be placed while one is out");
            helper.succeed();
        });

        RCGameTests.test("a_second_deploy_does_not_make_a_second_hauler", 20, helper -> {
            ServerLevel level = helper.getLevel();
            HaulerDepotBlockEntity depot = docked(helper, ScrapHaulerItem.CAPACITY);
            helper.assertTrue(depot.deploy(level), "first deploy refused");
            helper.assertTrue(!depot.deploy(level), "a duplicated Deploy was accepted");
            helper.assertTrue(haulers(helper).size() == 1,
                "two Deploys put " + haulers(helper).size() + " Haulers in the world");
            helper.succeed();
        });

        RCGameTests.test("the_hauler_cannot_be_shift_clicked_out_while_deployed", 20, helper -> {
            ServerLevel level = helper.getLevel();
            HaulerDepotBlockEntity depot = docked(helper, ScrapHaulerItem.CAPACITY);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            HaulerDepotMenu menu = new HaulerDepotMenu(0, player.getInventory(), depot, depot.data());
            helper.assertTrue(depot.deploy(level), "deploy refused");
            ItemStack moved = menu.quickMoveStack(player, 0);
            helper.assertTrue(moved.isEmpty(), "shift-click moved the deployed Hauler out: " + moved);
            helper.assertTrue(depot.hauler().getItem() instanceof ScrapHaulerItem, "the slot lost its Hauler");
            helper.assertTrue(!menu.slots.get(0).mayPickup(player), "the slot allows pickup while deployed");
            // And after recall, the same slot lets go.
            helper.assertTrue(depot.recall(level), "recall refused");
            helper.assertTrue(menu.slots.get(0).mayPickup(player), "the slot still refuses pickup after recall");
            helper.succeed();
        });

        RCGameTests.test("a_hopper_can_drain_the_hold_but_never_the_hauler", 20, helper -> {
            HaulerDepotBlockEntity depot = docked(helper, 0);
            for (Direction side : Direction.values()) {
                for (int slot : depot.getSlotsForFace(side)) {
                    helper.assertTrue(slot != HaulerDepotBlockEntity.HAULER_SLOT,
                        "getSlotsForFace(" + side + ") names the Hauler slot");
                }
                helper.assertTrue(!depot.canTakeItemThroughFace(HaulerDepotBlockEntity.HAULER_SLOT, depot.hauler(), side),
                    "a hopper on " + side + " may take the Hauler");
                helper.assertTrue(!depot.canPlaceItemThroughFace(HaulerDepotBlockEntity.HAULER_SLOT, chargedHauler(0), side),
                    "a hopper on " + side + " may insert a Hauler");
                helper.assertTrue(depot.canPlaceItemThroughFace(HaulerDepotBlockEntity.CARGO_START,
                        new ItemStack(RCBlocks.GARBAGE_BLOCK.get()), side),
                    "a hopper on " + side + " cannot stock the hold");
            }
            helper.succeed();
        });

        RCGameTests.test("breaking_the_depot_recalls_the_hauler_and_drops_exactly_one", 40, helper -> {
            ServerLevel level = helper.getLevel();
            HaulerDepotBlockEntity depot = docked(helper, 5_000);
            helper.assertTrue(depot.deploy(level), "deploy refused");
            helper.assertTrue(haulers(helper).size() == 1, "no Hauler out");
            level.destroyBlock(helper.absolutePos(DEPOT), true);
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(haulers(helper).isEmpty(),
                    "the Hauler survived its Depot being broken: " + haulers(helper).size() + " left");
                int items = 0;
                for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class,
                        AABB.ofSize(helper.absolutePos(DEPOT).getCenter(), 8, 8, 8))) {
                    if (entity.getItem().getItem() instanceof ScrapHaulerItem) {
                        items += entity.getItem().getCount();
                        helper.assertTrue(ScrapHaulerItem.charge(entity.getItem()) == 5_000,
                            "the dropped Hauler lost its charge: " + ScrapHaulerItem.charge(entity.getItem()));
                    }
                }
                helper.assertTrue(items == 1, "expected exactly one Scrap Hauler item on the ground, found " + items);
                helper.succeed();
            });
        });

        RCGameTests.test("a_killed_hauler_unlocks_the_depot_without_an_extra_item", 40, helper -> {
            ServerLevel level = helper.getLevel();
            HaulerDepotBlockEntity depot = docked(helper, 1_000);
            helper.assertTrue(depot.deploy(level), "deploy refused");
            ScrapHaulerEntity hauler = haulers(helper).get(0);
            // /kill: the one thing vanilla lets past an invulnerable entity.
            hauler.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(haulers(helper).isEmpty(), "genericKill did not remove the Hauler");
                helper.assertTrue(!depot.deployed(), "the Depot still thinks its Hauler is out");
                helper.assertTrue(depot.hauler().getItem() instanceof ScrapHaulerItem, "the slot item is gone");
                int dropped = level.getEntitiesOfClass(ItemEntity.class,
                    AABB.ofSize(helper.absolutePos(DEPOT).getCenter(), 8, 8, 8)).size();
                helper.assertTrue(dropped == 0, "a killed Hauler dropped items: " + dropped);
                helper.succeed();
            });
        });

        // ---- the loop --------------------------------------------------------------------------

        RCGameTests.test("recall_brings_the_charge_home_into_the_item", 20, helper -> {
            ServerLevel level = helper.getLevel();
            HaulerDepotBlockEntity depot = docked(helper, 8_000);
            helper.assertTrue(depot.deploy(level), "deploy refused");
            ScrapHaulerEntity hauler = haulers(helper).get(0);
            helper.assertTrue(hauler.charge() == 8_000, "the entity did not take the item's charge: " + hauler.charge());
            hauler.setCharge(3_210);
            helper.assertTrue(depot.recall(level), "recall refused");
            helper.assertTrue(haulers(helper).isEmpty(), "the Hauler is still out after recall");
            helper.assertTrue(!depot.deployed(), "the Depot still reads deployed");
            helper.assertTrue(ScrapHaulerItem.charge(depot.hauler()) == 3_210,
                "the item did not get the field charge back: " + ScrapHaulerItem.charge(depot.hauler()));
            helper.succeed();
        });

        RCGameTests.test("a_hauler_takes_a_pile_and_dumps_it_into_the_depot", 200, helper -> {
            ServerLevel level = helper.getLevel();
            HaulerDepotBlockEntity depot = docked(helper, ScrapHaulerItem.CAPACITY);
            helper.setBlock(NEAR_PILE, RCBlocks.GARBAGE_BLOCK.get());
            helper.assertTrue(depot.deploy(level), "deploy refused");
            helper.succeedWhen(() -> {
                helper.assertTrue(level.getBlockState(helper.absolutePos(NEAR_PILE)).isAir(),
                    "the pile is still there");
                helper.assertTrue(holdCount(depot) >= 1,
                    "the pile went but nothing reached the Depot's hold (hold=" + holdCount(depot)
                        + ", entity cargo=" + (haulers(helper).isEmpty() ? -1 : haulers(helper).get(0).cargoCount()) + ")");
            });
        });

        RCGameTests.test("a_hauler_walks_to_a_pile_it_cannot_reach_from_the_depot", 400, helper -> {
            ServerLevel level = helper.getLevel();
            HaulerDepotBlockEntity depot = docked(helper, ScrapHaulerItem.CAPACITY);
            helper.setBlock(FAR_PILE, RCBlocks.GARBAGE_BLOCK.get());
            helper.assertTrue(depot.deploy(level), "deploy refused");
            helper.succeedWhen(() -> helper.assertTrue(
                level.getBlockState(helper.absolutePos(FAR_PILE)).isAir(), "the far pile is still there"));
        });

        RCGameTests.test("taking_a_pile_costs_the_vacuum_price", 200, helper -> {
            ServerLevel level = helper.getLevel();
            int cost = ScrapHaulerEntity.costOf(RCBlocks.GARBAGE_BLOCK.get().defaultBlockState());
            helper.assertTrue(cost > 0, "a garbage block costs nothing to take");
            HaulerDepotBlockEntity depot = docked(helper, ScrapHaulerItem.CAPACITY);
            helper.setBlock(NEAR_PILE, RCBlocks.GARBAGE_BLOCK.get());
            helper.assertTrue(depot.deploy(level), "deploy refused");
            helper.succeedWhen(() -> {
                helper.assertTrue(level.getBlockState(helper.absolutePos(NEAR_PILE)).isAir(), "not taken yet");
                ScrapHaulerEntity hauler = haulers(helper).get(0);
                // Solar may have trickled a little back; the point is that the take was charged.
                helper.assertTrue(hauler.charge() <= ScrapHaulerItem.CAPACITY - cost + 40,
                    "the take was free: charge " + hauler.charge() + " against a cost of " + cost);
            });
        });

        RCGameTests.test("a_flat_hauler_parks_rather_than_stranding_itself", 60, helper -> {
            ServerLevel level = helper.getLevel();
            HaulerDepotBlockEntity depot = docked(helper, 0);
            helper.setBlock(NEAR_PILE, RCBlocks.GARBAGE_BLOCK.get());
            helper.assertTrue(depot.deploy(level), "deploy refused");
            helper.succeedWhen(() -> {
                ScrapHaulerEntity hauler = haulers(helper).get(0);
                helper.assertTrue(hauler.mode() == ScrapHaulerEntity.Mode.PARKED_FLAT,
                    "a Hauler with no charge is " + hauler.mode() + " rather than parked");
                helper.assertTrue(!level.getBlockState(helper.absolutePos(NEAR_PILE)).isAir(),
                    "a flat Hauler took a pile anyway");
            });
        });

        RCGameTests.test("the_hauler_wakes_itself_when_garbage_returns", 200, helper -> {
            ServerLevel level = helper.getLevel();
            HaulerDepotBlockEntity depot = docked(helper, ScrapHaulerItem.CAPACITY);
            helper.assertTrue(depot.deploy(level), "deploy refused");
            // Nothing to do: it should park idle. Then the field regrows.
            helper.runAfterDelay(ScrapHaulerEntity.IDLE_SCAN_TICKS + 5, () -> {
                ScrapHaulerEntity hauler = haulers(helper).get(0);
                helper.assertTrue(hauler.mode() == ScrapHaulerEntity.Mode.PARKED_IDLE,
                    "with no work the Hauler is " + hauler.mode() + " rather than parked idle");
                helper.setBlock(NEAR_PILE, RCBlocks.GARBAGE_BLOCK.get());
            });
            helper.succeedWhen(() -> helper.assertTrue(
                level.getBlockState(helper.absolutePos(NEAR_PILE)).isAir(),
                "the Hauler did not wake for the new pile"));
        });

        // ---- the biology, switched off ---------------------------------------------------------

        RCGameTests.test("the_hauler_is_not_hurt_and_not_sickened", 20, helper -> {
            ServerLevel level = helper.getLevel();
            HaulerDepotBlockEntity depot = docked(helper, 1_000);
            helper.assertTrue(depot.deploy(level), "deploy refused");
            ScrapHaulerEntity hauler = haulers(helper).get(0);
            float before = hauler.getHealth();
            hauler.hurtServer(level, level.damageSources().explosion(null, null), 50.0F);
            hauler.hurtServer(level, level.damageSources().fall(), 50.0F);
            hauler.hurtServer(level, level.damageSources().inFire(), 50.0F);
            hauler.hurtServer(level, level.damageSources().drown(), 50.0F);
            helper.assertTrue(hauler.getHealth() == before && hauler.isAlive(),
                "the Hauler took damage: " + before + " -> " + hauler.getHealth());
            helper.assertTrue(!LeachateBlock.sicken(level, hauler),
                "leachate sickened the Hauler - a mob effect ignores invulnerability, so this needs its exemption");
            helper.assertTrue(!hauler.hasEffect(MobEffects.HUNGER), "the Hauler carries the leachate effect");
            helper.assertTrue(!hauler.canBeLeashed(), "the Hauler can be leashed");
            helper.assertTrue(!hauler.removeWhenFarAway(1_000_000), "the Hauler would despawn");
            helper.succeed();
        });

        RCGameTests.test("the_hauler_takes_only_from_the_top_vacuum_band", 20, helper -> {
            ServerLevel level = helper.getLevel();
            floor(helper);
            helper.setBlock(NEAR_PILE, RCBlocks.GARBAGE_BLOCK.get());
            helper.setBlock(FAR_PILE, Blocks.STONE);
            helper.assertTrue(ScrapHaulerGoal.takeable(level, helper.absolutePos(NEAR_PILE)),
                "a garbage block is not takeable");
            helper.assertTrue(!ScrapHaulerGoal.takeable(level, helper.absolutePos(FAR_PILE)),
                "stone is takeable - the band tag is not being consulted");
            helper.assertTrue(level.getBlockState(helper.absolutePos(NEAR_PILE)).is(RCTags.vacuumable("netherite")),
                "the top band does not contain a garbage block, so the 'every pile' claim is false");
            helper.succeed();
        });

        RCGameTests.test("the_hauler_refuses_a_pile_beside_fire", 20, helper -> {
            ServerLevel level = helper.getLevel();
            floor(helper);
            helper.setBlock(NEAR_PILE, RCBlocks.GARBAGE_BLOCK.get());
            helper.setBlock(NEAR_PILE.above(), Blocks.FIRE);
            helper.assertTrue(!ScrapHaulerGoal.takeable(level, helper.absolutePos(NEAR_PILE)),
                "a pile with fire on it is a target, which puts the machine in the fire");
            helper.succeed();
        });

        // ---- the network -----------------------------------------------------------------------

        RCGameTests.test("the_depot_is_in_the_scrap_network_tag", 20, helper -> {
            helper.assertTrue(RCBlocks.HAULER_DEPOT.get().defaultBlockState().is(RCTags.SCRAP_CONNECTABLE),
                "hauler_depot is not in #recompile:scrap_connectable - ScrapNetwork.collect will return "
                    + "nothing and the push is a silent no-op, the Slag Furnace's bug");
            helper.succeed();
        });

        RCGameTests.test("the_depot_pushes_its_hold_into_a_connected_barrel", 100, helper -> {
            ServerLevel level = helper.getLevel();
            HaulerDepotBlockEntity depot = depot(helper);
            BlockPos barrelPos = DEPOT.east();
            level.setBlockAndUpdate(helper.absolutePos(barrelPos), RCBlocks.SCRAP_BARREL.get().defaultBlockState());
            var barrel = (Container) level.getBlockEntity(helper.absolutePos(barrelPos));
            helper.assertTrue(barrel != null, "no barrel");
            depot.setItem(HaulerDepotBlockEntity.CARGO_START, new ItemStack(RCBlocks.GARBAGE_BLOCK.get(), 5));
            helper.succeedWhen(() -> {
                int filed = 0;
                for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                    filed += barrel.getItem(slot).getCount();
                }
                helper.assertTrue(filed == 5, "the barrel holds " + filed + " of 5; the hold is not pushing");
                helper.assertTrue(holdCount(depot) == 0, "the hold still holds " + holdCount(depot));
            });
        });

        RCGameTests.test("the_hold_refuses_what_is_not_garbage", 20, helper -> {
            HaulerDepotBlockEntity depot = depot(helper);
            helper.assertTrue(depot.canPlaceItem(HaulerDepotBlockEntity.CARGO_START, new ItemStack(RCBlocks.GARBAGE_BLOCK.get())),
                "the hold refuses a garbage block");
            helper.assertTrue(!depot.canPlaceItem(HaulerDepotBlockEntity.CARGO_START, new ItemStack(Blocks.STONE)),
                "the hold accepts stone");
            helper.assertTrue(!depot.canPlaceItem(HaulerDepotBlockEntity.CARGO_START, chargedHauler(0)),
                "the hold accepts a Hauler, which is a second slot it could hide in");
            helper.succeed();
        });
    }
}

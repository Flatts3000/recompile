package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.entity.BurnBarrelBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * GameTests for the Burn Barrel (design P2.2): a vanilla-furnace reskin that smelts scrap into
 * copper (the gating choice) but is deliberately manual-only - no hopper / Create automation.
 */
final class BurnBarrelTests {

    private BurnBarrelTests() {
    }

    static void register() {
        // It is a furnace: load scrap + fuel, it smelts to copper. (Slots 0=input, 1=fuel, 2=out.)
        RCGameTests.test("burn_barrel_smelts_scrap_to_copper", 250, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.BURN_BARREL.get());
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                    instanceof BurnBarrelBlockEntity barrel)) {
                helper.fail("the burn barrel has no BlockEntity");
                return;
            }
            barrel.setItem(0, new ItemStack(RCItems.SCRAP_METAL.get()));
            barrel.setItem(1, new ItemStack(RCItems.OILY_RAG.get()));
            helper.succeedWhen(() ->
                helper.assertTrue(barrel.getItem(2).is(Items.COPPER_NUGGET),
                    "the burn barrel must smelt scrap metal into copper, output was " + barrel.getItem(2)));
        });

        // Refuse only. The barrel handles food and scrap; it will not smelt ore, sand, stone or logs,
        // so it cannot quietly hand out what the economy gates behind better machines.
        RCGameTests.test("burn_barrel_burns_refuse_only", 20, helper -> {
            // Food matches on the FOOD component, so every vanilla and modded edible works unlisted.
            helper.assertTrue(BurnBarrelBlockEntity.burns(new ItemStack(Items.BEEF)),
                "the barrel must cook food");
            helper.assertTrue(BurnBarrelBlockEntity.burns(new ItemStack(Items.POTATO)),
                "the barrel must cook food");
            // The tag carries the rest: this mod's scrap, and inputs whose product is edible (kelp).
            helper.assertTrue(BurnBarrelBlockEntity.burns(new ItemStack(RCItems.SCRAP_METAL.get())),
                "the barrel must reclaim scrap metal");
            helper.assertTrue(BurnBarrelBlockEntity.burns(new ItemStack(Items.KELP)),
                "the barrel must dry kelp");

            // Everything else fails closed. raw_iron and rebar are the iron path and belong to the
            // Cupola Furnace (#50); sand, cobble and logs are ordinary furnace work this world does not get.
            helper.assertFalse(BurnBarrelBlockEntity.burns(new ItemStack(Items.RAW_IRON)),
                "raw iron must be gated behind the Cupola Furnace, not the barrel");
            helper.assertFalse(BurnBarrelBlockEntity.burns(new ItemStack(RCItems.REBAR.get())),
                "rebar must smelt only in the Cupola Furnace");
            helper.assertFalse(BurnBarrelBlockEntity.burns(new ItemStack(Items.SAND)),
                "the barrel must not make glass");
            helper.assertFalse(BurnBarrelBlockEntity.burns(new ItemStack(Items.COBBLESTONE)),
                "the barrel must not make stone");
            helper.assertFalse(BurnBarrelBlockEntity.burns(new ItemStack(Items.OAK_LOG)),
                "the barrel must not make charcoal");
            helper.succeed();
        });

        // ...and the gate is actually WIRED, not just a predicate sitting unused. A blocked input must
        // never light the barrel: no progress and, critically, no fuel burned. Asserted through the real
        // ticker, because a correct predicate nobody calls would pass the test above and change nothing.
        RCGameTests.test("burn_barrel_will_not_light_for_ore", 40, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.BURN_BARREL.get());
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                    instanceof BurnBarrelBlockEntity barrel)) {
                helper.fail("the burn barrel has no BlockEntity");
                return;
            }
            barrel.setItem(0, new ItemStack(Items.RAW_IRON));
            barrel.setItem(1, new ItemStack(RCItems.OILY_RAG.get(), 8));
            helper.runAfterDelay(20, () -> {
                helper.assertBlockProperty(pos, BlockStateProperties.LIT, false);
                helper.assertTrue(barrel.getItem(2).isEmpty(),
                    "ore must produce nothing in the barrel, got " + barrel.getItem(2));
                helper.assertTrue(barrel.getItem(1).getCount() == 8,
                    "a refused input must not burn fuel, got " + barrel.getItem(1).getCount() + " of 8");
                helper.succeed();
            });
        });

        // The whole point of "worse": no automation. A hopper below must NOT pull the output -
        // getSlotsForFace is empty on every face, so the copper stays put. Verified to FAIL if
        // the barrel exposed its slots like a normal furnace.
        RCGameTests.test("burn_barrel_blocks_a_hopper", 30, helper -> {
            BlockPos pos = new BlockPos(1, 2, 1);
            helper.setBlock(pos, RCBlocks.BURN_BARREL.get());
            helper.setBlock(pos.below(), Blocks.HOPPER);
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                    instanceof BurnBarrelBlockEntity barrel)) {
                helper.fail("the burn barrel has no BlockEntity");
                return;
            }
            barrel.setItem(2, new ItemStack(Items.COPPER_NUGGET));
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(barrel.getItem(2).is(Items.COPPER_NUGGET),
                    "a hopper must not pull from the burn barrel - it is manual-only");
                helper.succeed();
            });
        });

        // The automation lockout, tested through the capability rather than through a hopper.
        // getSlotsForFace returns an empty int[], and this is what actually reads it - the same door
        // Pipez and every other pipe mod come through. Load-bearing for progression, not just tidiness:
        // "unlike the barrel it takes hoppers" is the Cupola's stated reason to be built, so a pipe that
        // can feed a barrel deletes the upgrade. Every face is checked because one open side is a hole.
        RCGameTests.test("burn_barrel_refuses_pipe_insertion", 20, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.BURN_BARREL.get());
            BlockPos abs = helper.absolutePos(pos);

            for (Direction side : Direction.values()) {
                ResourceHandler<ItemResource> handler = helper.getLevel()
                    .getCapability(Capabilities.Item.BLOCK, abs, side);
                // The capability is registered on purpose (RCBlockEntities), so a missing handler means
                // that registration was dropped - and this test would silently stop proving anything,
                // which is exactly what it did before the wrapper was wired up.
                helper.assertTrue(handler != null,
                    "the barrel must expose an item handler on " + side + " for this test to mean anything");
                int accepted;
                try (Transaction tx = Transaction.openRoot()) {
                    accepted = handler.insert(ItemResource.of(RCItems.SCRAP_METAL.get()), 8, tx);
                    tx.commit();
                }
                helper.assertTrue(accepted == 0,
                    "the barrel must refuse automation on " + side + ", took " + accepted);
            }
            helper.succeed();
        });
    }
}

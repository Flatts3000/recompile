package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.ScrapNetwork;
import com.flatts.recompile.content.block.entity.BurnBarrelBlockEntity;
import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * GameTests for the Scrap Network (design P2.10): scrap blocks placed touching each other form one
 * connected cluster and junk routes between them, with no controller and no saved state. Adjacency
 * finally fits the {@code empty_5x5x5} plot (the old fixed 6-wide bench never did), so the routing and
 * the flood-fill are both proven in-world here.
 *
 * <p>Routing is driven through {@link ScrapNetwork#insertFromMember} directly - the deterministic
 * engine every producer shares (the sorter's sift, the workbench's teardown output, the burn barrel's
 * drain). The sift's own loot roll is random and covered by {@code SortingTarpTests}; here we pin the
 * wiring the producers depend on.
 */
final class ScrapNetworkTests {

    private static final int RESULT_SLOT = 2; // burn barrel result slot (vanilla furnace layout)

    private ScrapNetworkTests() {
    }

    static void register() {
        // A drop routes into a bin already bound to its material.
        RCGameTests.test("scrap_network_routes_into_bound_bin", 20, helper -> {
            helper.setBlock(new BlockPos(1, 1, 1), RCBlocks.SORTING_TARP.get());
            ScrapBinBlockEntity bin = placeBin(helper, new BlockPos(2, 1, 1));
            bin.deposit(new ItemStack(RCItems.SCRAP_METAL.get(), 1)); // bind to scrap metal

            ItemStack remainder = ScrapNetwork.insertFromMember(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)),
                new ItemStack(RCItems.SCRAP_METAL.get(), 10), false);

            helper.assertTrue(remainder.isEmpty(), "the whole stack should route into the bound bin");
            helper.assertTrue(bin.amount() == 11, "bin should hold 1 + 10, got " + bin.amount());
            helper.succeed();
        });

        // No matching bin -> overflow into the barrel. A bin bound to a different material is skipped.
        RCGameTests.test("scrap_network_overflows_to_barrel", 20, helper -> {
            helper.setBlock(new BlockPos(1, 1, 1), RCBlocks.SORTING_TARP.get());
            ScrapBinBlockEntity bin = placeBin(helper, new BlockPos(2, 1, 1));
            bin.deposit(new ItemStack(RCItems.PLASTIC_SCRAP.get(), 1)); // bound to plastic, not metal
            Container barrel = placeBarrel(helper, new BlockPos(3, 1, 1));

            ItemStack remainder = ScrapNetwork.insertFromMember(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)),
                new ItemStack(RCItems.SCRAP_METAL.get(), 8), false);

            helper.assertTrue(remainder.isEmpty(), "metal should land in the barrel");
            helper.assertTrue(bin.amount() == 1, "the plastic-bound bin must be untouched, has " + bin.amount());
            helper.assertTrue(countIn(barrel, RCItems.SCRAP_METAL.get()) == 8,
                "the barrel should hold 8 scrap metal");
            helper.succeed();
        });

        // File-all's autoBind: an empty bin binds to and takes the item.
        RCGameTests.test("scrap_network_autobind_fills_an_empty_bin", 20, helper -> {
            helper.setBlock(new BlockPos(1, 1, 1), RCBlocks.SORTING_TARP.get());
            ScrapBinBlockEntity bin = placeBin(helper, new BlockPos(2, 1, 1));

            ItemStack remainder = ScrapNetwork.insertFromMember(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)),
                new ItemStack(RCItems.SCRAP_METAL.get(), 5), true);

            helper.assertTrue(remainder.isEmpty(), "autoBind should file into the empty bin");
            helper.assertTrue(bin.boundMaterial() == RCItems.SCRAP_METAL.get(), "the empty bin should bind to metal");
            helper.assertTrue(bin.amount() == 5, "bin should hold 5, got " + bin.amount());
            helper.succeed();
        });

        // Negative control: without autoBind (a sift / teardown / drain), an empty bin is never hijacked.
        RCGameTests.test("scrap_network_no_autobind_leaves_empty_bin_alone", 20, helper -> {
            helper.setBlock(new BlockPos(1, 1, 1), RCBlocks.SORTING_TARP.get());
            ScrapBinBlockEntity bin = placeBin(helper, new BlockPos(2, 1, 1));

            ItemStack remainder = ScrapNetwork.insertFromMember(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)),
                new ItemStack(RCItems.SCRAP_METAL.get(), 5), false);

            helper.assertTrue(remainder.getCount() == 5, "nothing should route into an unbound bin without autoBind");
            helper.assertTrue(bin.boundMaterial() == null, "the empty bin must stay unbound");
            helper.succeed();
        });

        // The Burn Barrel conducts but is never a routing sink - a route past it lands nowhere.
        RCGameTests.test("scrap_network_never_routes_into_the_burn_barrel", 20, helper -> {
            helper.setBlock(new BlockPos(1, 1, 1), RCBlocks.SORTING_TARP.get());
            helper.setBlock(new BlockPos(2, 1, 1), RCBlocks.BURN_BARREL.get());
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(2, 1, 1)))
                    instanceof BurnBarrelBlockEntity burn)) {
                helper.fail("the burn barrel has no BlockEntity");
                return;
            }

            ItemStack remainder = ScrapNetwork.insertFromMember(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)),
                new ItemStack(RCItems.SCRAP_METAL.get(), 4), true);

            helper.assertTrue(remainder.getCount() == 4, "with no bin/barrel there is nowhere to route");
            helper.assertTrue(burn.getItem(0).isEmpty() && burn.getItem(1).isEmpty() && burn.getItem(RESULT_SLOT).isEmpty(),
                "routing must never fill the burn barrel's smelt slots");
            helper.succeed();
        });

        // Multi-hop: a bin reachable only THROUGH an intermediate member (tarp - table - bin) is found.
        RCGameTests.test("scrap_network_routes_through_an_intermediate_member", 20, helper -> {
            helper.setBlock(new BlockPos(1, 1, 1), RCBlocks.SORTING_TARP.get());
            helper.setBlock(new BlockPos(2, 1, 1), RCBlocks.SCRAP_CRAFTING_TABLE.get());
            ScrapBinBlockEntity bin = placeBin(helper, new BlockPos(3, 1, 1)); // not adjacent to the tarp
            bin.deposit(new ItemStack(RCItems.SCRAP_METAL.get(), 1));

            ItemStack remainder = ScrapNetwork.insertFromMember(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)),
                new ItemStack(RCItems.SCRAP_METAL.get(), 6), false);

            helper.assertTrue(remainder.isEmpty(), "the flood should reach the bin two hops away");
            helper.assertTrue(bin.amount() == 7, "bin should hold 1 + 6, got " + bin.amount());
            helper.succeed();
        });

        // Negative control for connectivity: a bin one block away (not touching) is NOT reached.
        RCGameTests.test("scrap_network_does_not_reach_across_a_gap", 20, helper -> {
            helper.setBlock(new BlockPos(1, 1, 1), RCBlocks.SORTING_TARP.get());
            // (2,1,1) left as air - the gap.
            ScrapBinBlockEntity bin = placeBin(helper, new BlockPos(3, 1, 1));
            bin.deposit(new ItemStack(RCItems.SCRAP_METAL.get(), 1));

            ItemStack remainder = ScrapNetwork.insertFromMember(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)),
                new ItemStack(RCItems.SCRAP_METAL.get(), 6), false);

            helper.assertTrue(remainder.getCount() == 6, "a disconnected bin must not receive anything");
            helper.assertTrue(bin.amount() == 1, "the gapped bin must be untouched, has " + bin.amount());
            helper.succeed();
        });

        // reachesStorage gates the file-all: true with a connected sink, false with only conductors.
        RCGameTests.test("scrap_network_reaches_storage_only_with_a_sink", 20, helper -> {
            helper.setBlock(new BlockPos(1, 1, 1), RCBlocks.SORTING_TARP.get());
            helper.setBlock(new BlockPos(2, 1, 1), RCBlocks.SCRAP_CRAFTING_TABLE.get());
            ServerLevel level = helper.getLevel();
            BlockPos tarp = helper.absolutePos(new BlockPos(1, 1, 1));
            helper.assertFalse(ScrapNetwork.reachesStorage(level, tarp),
                "a tarp + table cluster has no storage sink");

            placeBin(helper, new BlockPos(3, 1, 1));
            helper.assertTrue(ScrapNetwork.reachesStorage(level, tarp),
                "adding a bin two hops away must make storage reachable");
            helper.succeed();
        });

        // Integration: the Burn Barrel's drain moves its result into a connected bin (it as producer).
        RCGameTests.test("scrap_network_burn_barrel_drains_into_a_bin", 20, helper -> {
            helper.setBlock(new BlockPos(1, 1, 1), RCBlocks.BURN_BARREL.get());
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)))
                    instanceof BurnBarrelBlockEntity burn)) {
                helper.fail("the burn barrel has no BlockEntity");
                return;
            }
            ScrapBinBlockEntity bin = placeBin(helper, new BlockPos(2, 1, 1));
            bin.deposit(new ItemStack(RCItems.SCRAP_METAL.get(), 1)); // bind to the smelt output's item

            burn.setItem(RESULT_SLOT, new ItemStack(RCItems.SCRAP_METAL.get(), 3));
            burn.drainOutput(helper.getLevel());

            helper.assertTrue(burn.getItem(RESULT_SLOT).isEmpty(), "the drained result slot should be empty");
            helper.assertTrue(bin.amount() == 4, "the bin should hold 1 + 3 drained, got " + bin.amount());
            helper.succeed();
        });
    }

    private static ScrapBinBlockEntity placeBin(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, RCBlocks.SCRAP_BIN.get());
        return (ScrapBinBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }

    private static Container placeBarrel(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, RCBlocks.SCRAP_BARREL.get());
        return (Container) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }

    private static int countIn(Container container, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}

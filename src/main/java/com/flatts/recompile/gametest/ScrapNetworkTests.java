package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.ScrapNetwork;
import com.flatts.recompile.content.block.entity.BurnBarrelBlockEntity;
import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Block;
import com.flatts.recompile.registry.RCTags;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.Map;
import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
        // EVERY MEMBER OF THE TAG MUST HAVE A JOB, AND THE TAG IS NOT THE JOB.
        //
        // This is the test the Cupola Furnace needed and did not have. It carried
        // #recompile:scrap_connectable from the day it shipped, which made it a stepping stone that
        // other members' routes could cross - so every test here passed - while its own iron sat in
        // its result slot forever. Being IN the network and PARTICIPATING in it are different things,
        // and nothing anywhere checked the second one.
        //
        // The roles below are the contract. A block that joins the tag has to be named here and given
        // one, which is a two-line change that forces the question "and what does it actually do?" to
        // be answered rather than assumed.
        RCGameTests.test("every_scrap_network_member_has_a_declared_role", 20, helper -> {
            // SINK   - a route can end here (only two, deliberately; see ScrapNetwork)
            // SOURCE - it pushes its own output into the network
            // READER - it reads the cluster rather than moving anything
            // RELAY  - it is a member only so a cluster can span it
            Map<Block, String> roles = new LinkedHashMap<>();
            roles.put(RCBlocks.SCRAP_BIN.get(), "SINK");
            roles.put(RCBlocks.SCRAP_BARREL.get(), "SINK");
            roles.put(RCBlocks.SORTING_TARP.get(), "SOURCE");
            roles.put(RCBlocks.RECOMPILE_WORKBENCH.get(), "SOURCE");
            roles.put(RCBlocks.BURN_BARREL.get(), "SOURCE");
            roles.put(RCBlocks.CUPOLA_FURNACE.get(), "SOURCE");
            roles.put(RCBlocks.SCRAP_CRAFTING_TABLE.get(), "READER");
            roles.put(RCBlocks.FILING_CABINET.get(), "READER");
            // The Separator pushes what it separates straight into the cluster. It sorted garbage
            // until #187; the role is unchanged, the reason given for it no longer exists.
            roles.put(RCBlocks.SEPARATOR.get(), "SOURCE");
            // Its formed cells are RELAYs and nothing more. They are in the tag so a bin parked against
            // any face of the machine joins the cluster - a formed machine should behave as one object
            // here as it does everywhere else, rather than connecting only at the core's corner.
            roles.put(RCBlocks.SEPARATOR_CHAMBER.get(), "RELAY");
            roles.put(RCBlocks.SEPARATOR_CHUTE.get(), "RELAY");
            roles.put(RCBlocks.SEPARATOR_HOUSING.get(), "RELAY");

            // The Trommel is a SOURCE on exactly the Separator's terms: it pushes what it sorts into
            // the network and can never be routed INTO, having no Container and no item handler.
            roles.put(RCBlocks.TROMMEL.get(), "SOURCE");
            // ...and its formed cells are RELAYs, so a bin parked against any face of the assembled
            // machine is in the same cluster as the core. A formed machine behaves as one object here
            // as everywhere else.
            roles.put(RCBlocks.TROMMEL_DRUM.get(), "RELAY");
            roles.put(RCBlocks.TROMMEL_STAND.get(), "RELAY");
            roles.put(RCBlocks.TROMMEL_CHUTE.get(), "RELAY");

            List<String> undeclared = new ArrayList<>();
            int members = 0;
            for (var holder : BuiltInRegistries.BLOCK.getTagOrEmpty(RCTags.SCRAP_CONNECTABLE)) {
                members++;
                if (!roles.containsKey(holder.value())) {
                    undeclared.add(BuiltInRegistries.BLOCK.getKey(holder.value()).toString());
                }
            }
            helper.assertTrue(members >= roles.size(),
                "only " + members + " members were found in the tag - discovery is broken, so this "
                    + "would pass against a block that joined it and did nothing");
            helper.assertTrue(undeclared.isEmpty(),
                "these blocks are in #recompile:scrap_connectable with no declared role. Being in the "
                    + "tag is not a job: decide whether each is a SINK, a SOURCE, a READER or a RELAY, "
                    + "add it here, and make sure the thing you decided actually happens - "
                    + undeclared);

            // And nothing claims a role it cannot have, which is the other direction of the same drift.
            List<String> missing = new ArrayList<>();
            for (Block block : roles.keySet()) {
                if (!block.defaultBlockState().is(RCTags.SCRAP_CONNECTABLE)) {
                    missing.add(BuiltInRegistries.BLOCK.getKey(block).toString());
                }
            }
            helper.assertTrue(missing.isEmpty(),
                "these have a role here but are not in the tag, so they are in no cluster at all: "
                    + missing);
            helper.succeed();
        });

        // Every SOURCE must actually push. The role table above is a promise; this is the part that
        // makes it one - each of these is a machine whose output would otherwise sit in a slot while
        // the player waited for a barrel that was never going to fill.
        RCGameTests.test("every_source_member_pushes_into_connected_storage", 60, helper -> {
            BlockPos barrelPos = new BlockPos(0, 1, 0);
            helper.setBlock(barrelPos, RCBlocks.SCRAP_BARREL.get());
            var barrel = (net.minecraft.world.Container)
                helper.getLevel().getBlockEntity(helper.absolutePos(barrelPos));

            // The Burn Barrel and the Cupola both expose a drain; both are wired to their tickers.
            BlockPos burnPos = new BlockPos(1, 1, 0);
            helper.setBlock(burnPos, RCBlocks.BURN_BARREL.get());
            var burn = (com.flatts.recompile.content.block.entity.BurnBarrelBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(burnPos));
            burn.setItem(2, new ItemStack(Items.IRON_NUGGET, 2));
            burn.drainOutput(helper.getLevel());
            helper.assertTrue(burn.getItem(2).isEmpty(),
                "the Burn Barrel must push its finished item into the connected barrel");

            BlockPos cupolaPos = new BlockPos(2, 1, 0);
            helper.setBlock(cupolaPos, RCBlocks.CUPOLA_FURNACE.get());
            var cupola = (com.flatts.recompile.content.block.entity.CupolaFurnaceBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(cupolaPos));
            cupola.setItem(2, new ItemStack(Items.IRON_INGOT, 2));
            cupola.drainOutput(helper.getLevel());
            helper.assertTrue(cupola.getItem(2).isEmpty(),
                "and so must the Cupola - it went weeks in the tag without doing this");

            int stored = 0;
            for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                stored += barrel.getItem(slot).getCount();
            }
            helper.assertTrue(stored == 4,
                "everything both sources pushed must be in the barrel; found " + stored);
            helper.succeed();
        });

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

        // #68: the Cupola is a cluster member, so upgrading a Burn Barrel does not sever the cluster it
        // was linking. The tarp and the barrel touch nothing but the Cupola here, so the stack can only
        // arrive by flooding THROUGH it - tag membership is the whole mechanism being tested.
        RCGameTests.test("scrap_network_reaches_through_a_cupola", 20, helper -> {
            helper.setBlock(new BlockPos(1, 1, 1), RCBlocks.SORTING_TARP.get());
            helper.setBlock(new BlockPos(2, 1, 1), RCBlocks.CUPOLA_FURNACE.get());
            Container barrel = placeBarrel(helper, new BlockPos(3, 1, 1));

            ItemStack remainder = ScrapNetwork.insertFromMember(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)),
                new ItemStack(RCItems.SCRAP_METAL.get(), 6), false);

            helper.assertTrue(remainder.isEmpty(), "the stack must cross the Cupola to reach the barrel");
            helper.assertTrue(countIn(barrel, RCItems.SCRAP_METAL.get()) == 6,
                "the barrel should hold 6 scrap metal, has " + countIn(barrel, RCItems.SCRAP_METAL.get()));
            helper.succeed();
        });

        // ...and the Cupola is a member, NOT a sink. It is an unrestricted furnace that accepts
        // automation, so a route landing in its smelt slots would feed it whatever passed by - the exact
        // trap the Burn Barrel is excluded from routing to avoid. Only bins and the barrel are sinks.
        RCGameTests.test("scrap_network_never_routes_into_a_cupola", 20, helper -> {
            helper.setBlock(new BlockPos(1, 1, 1), RCBlocks.SORTING_TARP.get());
            helper.setBlock(new BlockPos(2, 1, 1), RCBlocks.CUPOLA_FURNACE.get());

            ItemStack stack = new ItemStack(RCItems.SCRAP_METAL.get(), 4);
            ItemStack remainder = ScrapNetwork.insertFromMember(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), stack, true);

            helper.assertTrue(remainder.getCount() == 4,
                "with no bin or barrel the stack must come back whole, got " + remainder.getCount());
            // Asserted, not guarded by an `if`: a furnace BE that stopped being a Container would make
            // a conditional check vanish silently, and this test would keep passing while covering
            // nothing. If the type changes, this should fail loudly and be rewritten.
            helper.assertTrue(
                helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(2, 1, 1)))
                    instanceof Container,
                "the Cupola must expose a Container for this test to mean anything");
            Container cupola = (Container) helper.getLevel()
                .getBlockEntity(helper.absolutePos(new BlockPos(2, 1, 1)));
            helper.assertTrue(countIn(cupola, RCItems.SCRAP_METAL.get()) == 0,
                "nothing may be routed into the Cupola's slots");
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

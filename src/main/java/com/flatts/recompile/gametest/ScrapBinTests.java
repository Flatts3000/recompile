package com.flatts.recompile.gametest;

import com.flatts.recompile.content.ScrapBinContents;
import com.flatts.recompile.content.block.ScrapBinBlock;
import com.flatts.recompile.content.block.ScrapBinContent;
import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCDataComponents;
import com.flatts.recompile.registry.RCItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * GameTests for the Scrap Bin (design P2.9). These prove the server-side mechanic: binding,
 * deposit/withdraw, capacity, the sticky-binding lifecycle, break-survives, and hopper-in /
 * no-automation-out. The <b>color</b> is deliberately absent here - it is a client render tint keyed
 * on the {@code content} blockstate, not GameTest-able. What is tested is that {@code content} is set
 * correctly, which is the load-bearing half.
 */
final class ScrapBinTests {

    private static final BlockPos BIN = new BlockPos(1, 1, 1);

    private ScrapBinTests() {
    }

    private static ScrapBinBlockEntity placeBin(GameTestHelper helper) {
        helper.setBlock(BIN, RCBlocks.SCRAP_BIN.get());
        return (ScrapBinBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(BIN));
    }

    static void register() {
        // The first binnable item bound the bin and is counted. content follows the material.
        RCGameTests.test("scrap_bin_binds_on_first_deposit", 20, helper -> {
            ScrapBinBlockEntity bin = placeBin(helper);
            int accepted = bin.deposit(new ItemStack(RCItems.SCRAP_METAL.get(), 30));

            helper.assertTrue(accepted == 30, "all 30 should be accepted, got " + accepted);
            helper.assertTrue(bin.boundMaterial() == RCItems.SCRAP_METAL.get(), "must bind to scrap metal");
            helper.assertTrue(bin.amount() == 30, "must hold 30, got " + bin.amount());
            helper.assertTrue(
                helper.getBlockState(BIN).getValue(ScrapBinBlock.CONTENT) == ScrapBinContent.SCRAP_METAL,
                "content blockstate must follow the bound material");
            helper.succeed();
        });

        // A non-binnable item is refused outright - the bin stays empty and unbound.
        RCGameTests.test("scrap_bin_refuses_non_binnable", 20, helper -> {
            ScrapBinBlockEntity bin = placeBin(helper);
            ItemStack ingot = new ItemStack(Items.IRON_INGOT, 10);
            int accepted = bin.deposit(ingot);

            helper.assertTrue(accepted == 0, "an iron ingot is not #binnable and must be refused");
            helper.assertTrue(bin.boundMaterial() == null, "a refused item must not bind the bin");
            helper.assertTrue(ingot.getCount() == 10, "the refused stack must be untouched");
            helper.succeed();
        });

        // #68: the yard's base material stores like the household ones. A shard is what sifting rubble
        // yields, exactly as scrap metal is what sifting garbage yields, so the bin takes it and binds.
        RCGameTests.test("scrap_bin_accepts_stone_shards", 20, helper -> {
            ScrapBinBlockEntity bin = placeBin(helper);
            int accepted = bin.deposit(new ItemStack(RCItems.STONE_SHARD.get(), 12));

            helper.assertTrue(accepted == 12, "a stone shard is #binnable and must be taken, got " + accepted);
            helper.assertTrue(bin.boundMaterial() == RCItems.STONE_SHARD.get(),
                "the bin must bind to the shard it took");
            helper.succeed();
        });

        // The Steel Offcut is deliberately NOT binnable: #binnable is base materials out of a pull
        // stream, and the offcut is a cut product that remelts to iron. Pinned so widening the tag to
        // cover the yard does not quietly sweep in the yard's intermediates too.
        RCGameTests.test("scrap_bin_still_refuses_steel_offcut", 20, helper -> {
            ScrapBinBlockEntity bin = placeBin(helper);
            ItemStack offcut = new ItemStack(RCItems.STEEL_OFFCUT.get(), 8);
            int accepted = bin.deposit(offcut);

            helper.assertTrue(accepted == 0, "the Steel Offcut is a cut product, not a base material");
            helper.assertTrue(bin.boundMaterial() == null, "a refused item must not bind the bin");
            helper.succeed();
        });

        // Once bound, a different binnable material is refused too (the binding gates it).
        RCGameTests.test("scrap_bin_refuses_a_second_material", 20, helper -> {
            ScrapBinBlockEntity bin = placeBin(helper);
            bin.deposit(new ItemStack(RCItems.SCRAP_METAL.get(), 5));
            int accepted = bin.deposit(new ItemStack(RCItems.PLASTIC_SCRAP.get(), 5));

            helper.assertTrue(accepted == 0, "a bin bound to metal must refuse plastic");
            helper.assertTrue(bin.amount() == 5, "the refusal must not change the count");
            helper.succeed();
        });

        // Deposit adds, withdraw removes, counts are exact. A stack withdraw returns up to a stack.
        RCGameTests.test("scrap_bin_deposit_and_withdraw_counts", 20, helper -> {
            ScrapBinBlockEntity bin = placeBin(helper);
            bin.deposit(new ItemStack(RCItems.JUNK.get(), 64));
            bin.deposit(new ItemStack(RCItems.JUNK.get(), 64));
            helper.assertTrue(bin.amount() == 128, "two stacks in should be 128, got " + bin.amount());

            ItemStack out = bin.withdraw(false);
            helper.assertTrue(out.getCount() == 64, "a stack withdraw returns 64, got " + out.getCount());
            helper.assertTrue(bin.amount() == 64, "64 should remain, got " + bin.amount());

            ItemStack one = bin.withdraw(true);
            helper.assertTrue(one.getCount() == 1, "a single withdraw returns 1");
            helper.assertTrue(bin.amount() == 63, "63 should remain, got " + bin.amount());
            helper.succeed();
        });

        // fill blockstate tracks the amount: 0 empty, at least 1 once anything is in, 4 near full.
        RCGameTests.test("scrap_bin_fill_tracks_amount", 20, helper -> {
            ScrapBinBlockEntity bin = placeBin(helper);
            helper.assertTrue(helper.getBlockState(BIN).getValue(ScrapBinBlock.FILL) == 0, "empty is fill 0");

            bin.deposit(new ItemStack(RCItems.GLASS_SHARDS.get(), 1));
            helper.assertTrue(helper.getBlockState(BIN).getValue(ScrapBinBlock.FILL) == 1,
                "any positive amount shows at least fill 1");

            bin.deposit(new ItemStack(RCItems.GLASS_SHARDS.get(), 64));
            bin.deposit(new ItemStack(RCItems.GLASS_SHARDS.get(), 64));
            // capacity default is 4096; ~129 is well under a quarter, still fill 1.
            helper.assertTrue(helper.getBlockState(BIN).getValue(ScrapBinBlock.FILL) >= 1, "still shows fill");
            helper.succeed();
        });

        // Capacity is a hard cap - a deposit past it leaves the remainder in hand.
        RCGameTests.test("scrap_bin_capacity_is_a_hard_cap", 40, helper -> {
            ScrapBinBlockEntity bin = placeBin(helper);
            int cap = bin.capacityForDisplay();
            // Fill to the brim in stack-sized deposits.
            int deposited = 0;
            while (deposited < cap) {
                int chunk = Math.min(64, cap - deposited + 64);
                ItemStack stack = new ItemStack(RCItems.FIBER_SCRAP.get(), 64);
                bin.deposit(stack);
                deposited += 64 - stack.getCount();
                if (64 - (64 - stack.getCount()) == 64) {
                    break; // nothing accepted -> full
                }
            }
            helper.assertTrue(bin.amount() == cap, "must fill exactly to capacity, got " + bin.amount());

            ItemStack overflow = new ItemStack(RCItems.FIBER_SCRAP.get(), 64);
            int accepted = bin.deposit(overflow);
            helper.assertTrue(accepted == 0, "a full bin accepts nothing more");
            helper.assertTrue(overflow.getCount() == 64, "the overflow stays in hand");
            helper.succeed();
        });

        // Emptied by withdrawal WHILE PLACED, the bin stays bound - refill without re-binding.
        RCGameTests.test("scrap_bin_stays_bound_when_emptied_in_place", 20, helper -> {
            ScrapBinBlockEntity bin = placeBin(helper);
            bin.deposit(new ItemStack(RCItems.E_SCRAP.get(), 5));
            bin.withdraw(false);   // take the whole (sub-stack) lot
            bin.withdraw(false);   // and again to be sure it is empty

            helper.assertTrue(bin.isEmpty(), "the bin should be empty now");
            helper.assertTrue(bin.boundMaterial() == RCItems.E_SCRAP.get(),
                "an emptied-in-place bin must stay bound to e-scrap");
            helper.assertTrue(
                helper.getBlockState(BIN).getValue(ScrapBinBlock.CONTENT) == ScrapBinContent.E_SCRAP,
                "content blockstate must still read e-scrap while placed and empty");
            helper.succeed();
        });

        // Broken while empty -> the dropped item is a blank, unbound bin (no contents component).
        RCGameTests.test("scrap_bin_broken_empty_drops_blank", 40, helper -> {
            placeBin(helper);
            helper.getLevel().destroyBlock(helper.absolutePos(BIN), true);

            ItemStack dropped = droppedBin(helper);
            helper.assertTrue(!dropped.isEmpty(), "a scrap bin item must drop");
            helper.assertTrue(dropped.get(RCDataComponents.SCRAP_BIN_CONTENTS.get()) == null,
                "an empty bin must drop with no contents component");
            helper.succeed();
        });

        // Broken WITH contents -> the dropped item carries {material, count}. The load-bearing test,
        // and the exact failure the water tank hit: a drop that names the wrong thing (or nothing).
        RCGameTests.test("scrap_bin_broken_full_carries_contents", 40, helper -> {
            ScrapBinBlockEntity bin = placeBin(helper);
            bin.deposit(new ItemStack(RCItems.ORGANIC_MUCK.get(), 200));
            helper.getLevel().destroyBlock(helper.absolutePos(BIN), true);

            ItemStack dropped = droppedBin(helper);
            ScrapBinContents contents = dropped.get(RCDataComponents.SCRAP_BIN_CONTENTS.get());
            helper.assertTrue(contents != null, "a loaded bin must carry a contents component");
            helper.assertTrue(contents.material() == RCItems.ORGANIC_MUCK.get(),
                "the component must name the bound material");
            helper.assertTrue(contents.count() == 200,
                "the component must carry the exact count, got " + contents.count());
            helper.succeed();
        });

        // The round trip: place a component-carrying bin item and the new bin restores its contents.
        RCGameTests.test("scrap_bin_placed_from_loaded_item_restores", 40, helper -> {
            BlockPos floor = new BlockPos(2, 1, 2);
            helper.setBlock(floor, Blocks.STONE);
            ItemStack loaded = new ItemStack(RCItems.SCRAP_BIN.get());
            loaded.set(RCDataComponents.SCRAP_BIN_CONTENTS.get(),
                new ScrapBinContents(RCItems.PLASTIC_SCRAP.get(), 321));

            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, loaded);
            BlockPos abs = helper.absolutePos(floor);
            loaded.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(abs.getCenter(), Direction.UP, abs, false)));

            BlockPos placed = floor.above();
            helper.assertTrue(helper.getBlockState(placed).is(RCBlocks.SCRAP_BIN.get()),
                "the bin must place on top of the stone");
            if (helper.getLevel().getBlockEntity(helper.absolutePos(placed)) instanceof ScrapBinBlockEntity bin) {
                helper.assertTrue(bin.boundMaterial() == RCItems.PLASTIC_SCRAP.get(),
                    "the placed bin must restore its binding");
                helper.assertTrue(bin.amount() == 321,
                    "the placed bin must restore its count, got " + bin.amount());
            } else {
                helper.fail("no scrap bin BE where the bin was placed");
            }
            helper.succeed();
        });

        // Hopper in: the item capability accepts a matching insert (what a hopper actually uses).
        RCGameTests.test("scrap_bin_capability_accepts_insert", 20, helper -> {
            ScrapBinBlockEntity bin = placeBin(helper);
            ResourceHandler<ItemResource> handler = helper.getLevel()
                .getCapability(Capabilities.Item.BLOCK, helper.absolutePos(BIN), null);
            helper.assertTrue(handler != null, "the bin must expose an item handler");

            int accepted;
            try (Transaction tx = Transaction.openRoot()) {
                accepted = handler.insert(ItemResource.of(RCItems.SCRAP_METAL.get()), 40, tx);
                tx.commit();
            }
            helper.assertTrue(accepted == 40, "the handler must accept 40, got " + accepted);
            helper.assertTrue(bin.amount() == 40, "the insert must land in the bin");
            helper.succeed();
        });

        // No automation out: the capability never extracts, so a hopper below pulls nothing.
        RCGameTests.test("scrap_bin_capability_never_extracts", 20, helper -> {
            ScrapBinBlockEntity bin = placeBin(helper);
            bin.deposit(new ItemStack(RCItems.SCRAP_METAL.get(), 64));
            ResourceHandler<ItemResource> handler = helper.getLevel()
                .getCapability(Capabilities.Item.BLOCK, helper.absolutePos(BIN), null);

            int extracted;
            try (Transaction tx = Transaction.openRoot()) {
                extracted = handler.extract(ItemResource.of(RCItems.SCRAP_METAL.get()), 64, tx);
                tx.commit();
            }
            helper.assertTrue(extracted == 0, "the bin must never give items to automation");
            helper.assertTrue(bin.amount() == 64, "and the count must be unchanged, got " + bin.amount());
            helper.succeed();
        });

        // A binnable-but-uncolored (e.g. modded) material maps to the neutral GENERIC content, so it
        // is held and named but uncolored - the finite-enum fallback. Tested on the mapping directly,
        // since no in-repo binnable item is uncolored.
        RCGameTests.test("scrap_bin_unknown_material_is_generic", 20, helper -> {
            helper.assertTrue(ScrapBinContent.forItem(Items.IRON_INGOT) == ScrapBinContent.GENERIC,
                "an unmapped item must fall back to GENERIC");
            helper.assertTrue(ScrapBinContent.forItem(RCItems.SCRAP_METAL.get()) == ScrapBinContent.SCRAP_METAL,
                "a known material must map to its own colored value");
            helper.succeed();
        });

        // ---- Functional Storage interaction ----

        // The double-click detector: a second click within the window is a double, and the window is
        // consumed so a third fast click is not another double. Drives deposit-all.
        RCGameTests.test("scrap_bin_double_click_window", 20, helper -> {
            ScrapBinBlockEntity bin = placeBin(helper);
            UUID p = java.util.UUID.randomUUID();
            helper.assertTrue(!bin.rightClickIsDouble(p, 100), "the first click is never a double");
            helper.assertTrue(bin.rightClickIsDouble(p, 105), "a click 5 ticks later is a double");
            helper.assertTrue(!bin.rightClickIsDouble(p, 106), "the window is consumed - not a triple");
            helper.assertTrue(!bin.rightClickIsDouble(p, 999), "a click long after is not a double");
            helper.succeed();
        });

        // Double right-click deposits every matching stack, including ones the first click could not
        // reach after it emptied the hand. Driven through the real block interaction (useBlock).
        RCGameTests.test("scrap_bin_double_right_click_deposits_all", 20, helper -> {
            ScrapBinBlockEntity bin = placeBin(helper);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RCItems.SCRAP_METAL.get(), 64));
            player.getInventory().add(new ItemStack(RCItems.SCRAP_METAL.get(), 64));
            player.getInventory().add(new ItemStack(RCItems.SCRAP_METAL.get(), 64));

            helper.useBlock(BIN, player);   // deposits the held stack, arms the double-click
            helper.useBlock(BIN, player);   // same tick -> double -> deposits the remaining two

            helper.assertTrue(bin.amount() == 192, "all three stacks should land, got " + bin.amount());
            helper.assertTrue(!player.getInventory().contains(new ItemStack(RCItems.SCRAP_METAL.get())),
                "no scrap metal should remain in the inventory");
            helper.succeed();
        });

        // Left-click extraction (the LeftClickBlock path, via the shared static entry): a plain click
        // takes one, a sneak click takes a stack. FS's granularity, the reverse of deposit.
        RCGameTests.test("scrap_bin_left_click_extracts", 20, helper -> {
            ScrapBinBlockEntity bin = placeBin(helper);
            bin.deposit(new ItemStack(RCItems.SCRAP_METAL.get(), 100));
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);

            boolean took = ScrapBinBlock.extract(helper.getLevel(), helper.absolutePos(BIN), player);
            helper.assertTrue(took, "a full bin must extract on a left-click");
            helper.assertTrue(bin.amount() == 99, "a plain click takes one, got " + (100 - bin.amount()));

            player.setShiftKeyDown(true);
            ScrapBinBlock.extract(helper.getLevel(), helper.absolutePos(BIN), player);
            helper.assertTrue(bin.amount() == 35, "a sneak click takes a stack (64), got " + bin.amount());

            helper.assertTrue(!ScrapBinBlock.extract(
                    helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3)), player),
                "left-clicking where there is no bin extracts nothing");
            helper.succeed();
        });
    }

    /** The scrap-bin ItemStack that fell in the plot, or empty if none. */
    private static ItemStack droppedBin(GameTestHelper helper) {
        List<ItemEntity> items = helper.getLevel()
            .getEntitiesOfClass(ItemEntity.class, helper.getBounds());
        for (ItemEntity entity : items) {
            if (entity.getItem().is(RCItems.SCRAP_BIN.get())) {
                return entity.getItem();
            }
        }
        return ItemStack.EMPTY;
    }
}

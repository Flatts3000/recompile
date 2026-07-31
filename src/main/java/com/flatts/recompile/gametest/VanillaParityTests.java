package com.flatts.recompile.gametest;

import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Automation parity against the vanilla block each machine is a reskin of (owner call, 2026-07-31).
 *
 * <p>Rather than assert hand-picked numbers, these run the SAME operations against our block and its
 * vanilla counterpart and require the answers to match. That is what "parity" means operationally, and
 * it is the only version that stays true when Mojang changes a furnace's slot mapping - a hardcoded
 * expectation would silently become wrong, while a comparison just keeps tracking.
 *
 * <p>Every face is compared, plus the non-sided ({@code null}) query, which is where the Burn Barrel's
 * lockout leaked: {@code WorldlyContainerWrapper} skips {@code getSlotsForFace} entirely when the side
 * is null, so six matching faces prove nothing about the seventh case.
 */
final class VanillaParityTests {

    private VanillaParityTests() {
    }

    private static final BlockPos OURS = new BlockPos(1, 1, 1);
    private static final BlockPos VANILLA = new BlockPos(3, 1, 1);

    /** The six faces plus the non-sided query. */
    private static List<Direction> faces() {
        List<Direction> all = new ArrayList<>(Arrays.asList(Direction.values()));
        all.add(null);
        return all;
    }

    private static ResourceHandler<ItemResource> handler(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(pos), side);
    }

    /** Insert into a handler and report how much it took. -1 means "no handler exposed at all". */
    private static int probeInsert(ResourceHandler<ItemResource> handler, ItemResource resource, int count) {
        if (handler == null) {
            return -1;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int n = handler.insert(resource, count, tx);
            tx.commit();
            return n;
        }
    }

    private static int probeExtract(ResourceHandler<ItemResource> handler, ItemResource resource, int count) {
        if (handler == null) {
            return -1;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int n = handler.extract(resource, count, tx);
            tx.commit();
            return n;
        }
    }

    /**
     * Both blocks get the same insert on every face; the amount accepted must match. -1 (no handler) has
     * to match too - a block that exposes nothing where vanilla exposes something cannot be piped at all,
     * which is exactly the Scrap Barrel's bug.
     */
    private static void assertInsertParity(GameTestHelper helper, Block ours, Block vanilla,
            ItemResource resource, String label) {
        helper.setBlock(OURS, ours);
        helper.setBlock(VANILLA, vanilla);
        // Prove the comparison is worth making before making it. If the vanilla reference exposed nothing
        // anywhere, every face would compare -1 to -1 and this whole test would pass while asserting that
        // two blocks are equally unreachable. That is the exact false-pass shape this file exists to avoid.
        int vanillaBest = -1;
        for (Direction side : faces()) {
            vanillaBest = Math.max(vanillaBest, probeInsert(handler(helper, VANILLA, side), resource, 4));
        }
        helper.assertTrue(vanillaBest > 0, label + ": the vanilla reference must itself accept items on"
            + " some face, or this parity check is vacuous (best was " + vanillaBest + ")");
        helper.setBlock(VANILLA, Blocks.AIR);
        helper.setBlock(VANILLA, vanilla);   // fresh, so the probe above does not skew the comparison
        for (Direction side : faces()) {
            int mine = probeInsert(handler(helper, OURS, side), resource, 4);
            int theirs = probeInsert(handler(helper, VANILLA, side), resource, 4);
            helper.assertTrue(mine == theirs, label + ": insert on " + side
                + " must match vanilla - ours " + mine + ", vanilla " + theirs
                + " (-1 = no handler exposed)");
        }
    }

    static void register() {
        // The Scrap Barrel is a barrel. Anything a hopper or pipe can do to a vanilla barrel it must be
        // able to do here - this is storage, and there is no reason for it to be the odd one out.
        RCGameTests.test("scrap_barrel_matches_vanilla_barrel", 20, helper -> {
            assertInsertParity(helper, RCBlocks.SCRAP_BARREL.get(), Blocks.BARREL,
                ItemResource.of(RCItems.SCRAP_METAL.get()), "scrap barrel");

            // ...and out again, which is half of what a barrel is for.
            for (Direction side : faces()) {
                ResourceHandler<ItemResource> mine = handler(helper, OURS, side);
                ResourceHandler<ItemResource> theirs = handler(helper, VANILLA, side);
                int a = probeExtract(mine, ItemResource.of(RCItems.SCRAP_METAL.get()), 2);
                int b = probeExtract(theirs, ItemResource.of(RCItems.SCRAP_METAL.get()), 2);
                helper.assertTrue(a == b, "scrap barrel: extract on " + side
                    + " must match vanilla - ours " + a + ", vanilla " + b);
            }
            helper.succeed();
        });

        // The Cupola is a furnace. Its whole selling point over the Burn Barrel is that it automates, so
        // it has to automate the way a furnace does - same faces, same slots, same answers.
        RCGameTests.test("cupola_matches_vanilla_furnace", 20, helper -> {
            assertInsertParity(helper, RCBlocks.CUPOLA_FURNACE.get(), Blocks.FURNACE,
                ItemResource.of(RCItems.STEEL_OFFCUT.get()), "cupola");
            helper.succeed();
        });

        // Fuel goes in the side slot on a furnace; the Cupola must take it the same way or it cannot be
        // fed by automation at all, only stocked by hand - which is the barrel's behaviour, not its own.
        RCGameTests.test("cupola_matches_vanilla_furnace_for_fuel", 20, helper -> {
            assertInsertParity(helper, RCBlocks.CUPOLA_FURNACE.get(), Blocks.FURNACE,
                ItemResource.of(RCItems.OILY_RAG.get()), "cupola fuel");
            helper.succeed();
        });
    }
}

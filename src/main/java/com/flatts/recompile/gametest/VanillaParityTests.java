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
    /**
     * Extraction parity on every face, the null one included.
     *
     * <p>Assumes {@code assertInsertParity} has already placed both blocks - it is called straight
     * after it, and re-placing them would discard the contents that make an extract worth probing.
     *
     * <p><b>The null side is the point.</b> {@code WorldlyContainerWrapper.extract} is guarded by
     * {@code side != null &&}, so a non-sided caller skips {@code canTakeItemThroughFace} entirely.
     * That is exactly the behaviour a parity block must KEEP and a bespoke machine must refuse, and it
     * is only visible if the comparison includes null - which {@code faces()} does and
     * {@code Direction.values()} does not.
     */
    private static void assertExtractParity(GameTestHelper helper, ItemResource resource, String label) {
        // SAME GUARD AS THE INSERT HALF, for the same reason. If the vanilla reference could not hand
        // the item back on any face, every comparison would be 0 == 0 and this would pass while
        // asserting that two blocks are equally unextractable. Proven before it is used.
        int vanillaBest = 0;
        for (Direction side : faces()) {
            vanillaBest = Math.max(vanillaBest, probeExtract(handler(helper, VANILLA, side), resource, 1));
        }
        helper.assertTrue(vanillaBest > 0,
            label + ": the vanilla reference gave nothing back on any face, so an extract comparison "
                + "would be 0 == 0 everywhere and prove nothing");

        for (Direction side : faces()) {
            ResourceHandler<ItemResource> mine = handler(helper, OURS, side);
            ResourceHandler<ItemResource> theirs = handler(helper, VANILLA, side);
            int a = probeExtract(mine, resource, 1);
            int b = probeExtract(theirs, resource, 1);
            helper.assertTrue(a == b, label + ": extract on " + side
                + " must match vanilla - ours " + a + ", vanilla " + b);
        }
    }

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

        // THE OTHER TWO FURNACES (#341). Neither was covered here, and the gap only surfaced because
        // a comment elsewhere justified leaving them unguarded on the null side by claiming they were
        // "held to vanilla parity" - a claim this file was the whole evidence for, and did not support.
        //
        // Both are plain AbstractFurnaceBlockEntity subclasses that override none of the automation
        // methods, so they should be vanilla's behaviour exactly. That is worth ASSERTING rather than
        // reading off the class declaration: a future override, or a NeoForge change to the base
        // class, would be invisible until a hopper stopped working in somebody's base.
        //
        // Insert AND extract, unlike the Cupola's test above which checks insert only. The claim being
        // pinned is specifically about every face PLUS the null one on the EXTRACT path, since that is
        // where WorldlyContainerWrapper's `side != null &&` guard lives.
        RCGameTests.test("slag_furnace_matches_vanilla_furnace", 20, helper -> {
            assertInsertParity(helper, RCBlocks.SLAG_FURNACE.get(), Blocks.FURNACE,
                ItemResource.of(RCItems.SLAG.get()), "slag furnace");
            assertExtractParity(helper, ItemResource.of(RCItems.SLAG.get()), "slag furnace");
            helper.succeed();
        });

        RCGameTests.test("sintering_kiln_matches_vanilla_furnace", 20, helper -> {
            assertInsertParity(helper, RCBlocks.SINTERING_KILN.get(), Blocks.FURNACE,
                ItemResource.of(RCItems.BLAZE_BRIQUETTE.get()), "sintering kiln");
            assertExtractParity(helper, ItemResource.of(RCItems.BLAZE_BRIQUETTE.get()),
                "sintering kiln");
            helper.succeed();
        });

        // The ONE documented departure from furnace parity (owner call, 2026-07-31): automation cannot
        // insert what cannot be smelted. Asserted as a difference on purpose - vanilla accepting the jam
        // is what makes the departure necessary, so this test proves both halves and fails loudly if
        // either the exception or vanilla's behaviour changes underneath it.
        RCGameTests.test("cupola_refuses_unsmeltable_where_vanilla_accepts", 20, helper -> {
            helper.setBlock(OURS, RCBlocks.CUPOLA_FURNACE.get());
            helper.setBlock(VANILLA, Blocks.FURNACE);
            // Both jams seen in playtest: an Iron Ingot (the machine's own output, looped back) and an
            // Oily Rag piped at the top face, which vanilla files into the SMELT slot rather than the
            // fuel slot. Neither is a smelting input, so neither may enter slot 0 by automation.
            for (ItemResource jam : List.of(ItemResource.of(net.minecraft.world.item.Items.IRON_INGOT),
                    ItemResource.of(RCItems.OILY_RAG.get()))) {
                helper.setBlock(OURS, Blocks.AIR);
                helper.setBlock(OURS, RCBlocks.CUPOLA_FURNACE.get());
                int oursSlotZero = probeInsert(handler(helper, OURS, Direction.UP), jam, 4);
                helper.assertTrue(oursSlotZero <= 0,
                    "the Cupola must refuse " + jam.getItem() + " into the input slot from automation,"
                        + " took " + oursSlotZero);
            }
            // ...and vanilla must still take it, or the departure has nothing to depart from.
            int vanillaSlotZero = probeInsert(handler(helper, VANILLA, Direction.UP),
                ItemResource.of(net.minecraft.world.item.Items.IRON_INGOT), 4);
            helper.assertTrue(vanillaSlotZero > 0,
                "vanilla must still accept the jam, or this exception has no reason to exist (got "
                    + vanillaSlotZero + ")");
            helper.succeed();
        });

        // Fuel must still be automatable, or the Cupola can only be stocked by hand - which is the
        // barrel's behaviour, not its own. Asserted as "reaches the fuel slot from some face" rather than
        // face-by-face equality: the input-slot filter deliberately refuses fuel on the face that maps to
        // slot 0, and pinning which face that is would hardcode vanilla's mapping into the test.
        RCGameTests.test("cupola_still_accepts_fuel_from_automation", 20, helper -> {
            helper.setBlock(OURS, RCBlocks.CUPOLA_FURNACE.get());
            helper.setBlock(VANILLA, Blocks.FURNACE);
            ItemResource rag = ItemResource.of(RCItems.OILY_RAG.get());

            int ours = -1;
            int vanilla = -1;
            for (Direction side : faces()) {
                ours = Math.max(ours, probeInsert(handler(helper, OURS, side), rag, 4));
                vanilla = Math.max(vanilla, probeInsert(handler(helper, VANILLA, side), rag, 4));
            }
            helper.assertTrue(vanilla > 0, "vanilla must take fuel from automation somewhere");
            helper.assertTrue(ours == vanilla,
                "the Cupola must take just as much fuel as vanilla on its best face - ours " + ours
                    + ", vanilla " + vanilla);
            helper.succeed();
        });
    }
}

package com.flatts.recompile.gametest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import com.flatts.recompile.content.block.ScrapBarrelBlock;
import com.flatts.recompile.content.block.entity.ScrapBarrelBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** GameTests for the Scrap Barrel: storage in a world with no wood. */
final class ScrapBarrelTests {

    private ScrapBarrelTests() {
    }

    static void register() {
        // The barrel is only worth having if it actually holds things and hands them back
        // when broken. Contents-dropping is inherited from BlockEntity.preRemoveSideEffects
        // (any Container drops on removal), so this guards that the block entity really is
        // a Container wired to the block - a mismatch would silently void the inventory.
        RCGameTests.test("scrap_barrel_holds_items_and_drops_them", 40, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.SCRAP_BARREL.get());

            // Throws if the block entity is missing or the wrong type - so this also
            // proves the BlockEntityType is bound to the block.
            ScrapBarrelBlockEntity barrel = helper.getBlockEntity(pos, ScrapBarrelBlockEntity.class);

            helper.assertTrue(barrel.getContainerSize() == 27,
                "barrel must have 27 slots like a vanilla barrel, got " + barrel.getContainerSize());
            barrel.setItem(0, new ItemStack(RCItems.SCRAP_METAL.get(), 5));
            helper.assertTrue(barrel.getItem(0).is(RCItems.SCRAP_METAL.get()),
                "the barrel must hold what was put in it");

            // Breaking it must hand the contents back, not void them.
            helper.destroyBlock(pos);
            helper.assertBlockPresent(Blocks.AIR, pos);
            helper.succeedWhenEntityPresent(EntityType.ITEM, pos);
        });

        // Parity check with vanilla barrels, and the one deliberate deviation. A vanilla
        // barrel carries FACING and points wherever you looked; a drum stands on its end,
        // so this one is always top-up and OPEN is its only state.
        RCGameTests.test("scrap_barrel_is_always_top_up", 20, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.SCRAP_BARREL.get());
            var state = helper.getBlockState(pos);

            helper.assertTrue(state.hasProperty(ScrapBarrelBlock.OPEN),
                "the barrel needs its OPEN lid state for vanilla parity");
            helper.assertFalse(state.getValue(ScrapBarrelBlock.OPEN),
                "a freshly placed barrel must be closed");
            helper.assertTrue(state.getProperties().size() == 1,
                "OPEN must be the barrel's only state - no FACING, it is always top-up. Got: "
                    + state.getProperties());
            helper.succeed();
        });
        // The Scrap Barrel is the network's overflow sink, so it is the one member that should be
        // freely automatable - both ways. It is a plain Container, so hoppers always worked through the
        // vanilla path; a pipe needs the item capability, and without it a pipe would not even connect.
        RCGameTests.test("scrap_barrel_capability_moves_both_ways", 20, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.SCRAP_BARREL.get());
            ResourceHandler<ItemResource> handler = helper.getLevel()
                .getCapability(Capabilities.Item.BLOCK, helper.absolutePos(pos), null);
            helper.assertTrue(handler != null, "the barrel must expose an item handler, or pipes cannot connect");

            int accepted;
            try (Transaction tx = Transaction.openRoot()) {
                accepted = handler.insert(ItemResource.of(RCItems.SCRAP_METAL.get()), 32, tx);
                tx.commit();
            }
            helper.assertTrue(accepted == 32, "a pipe must fill the barrel, got " + accepted);

            int extracted;
            try (Transaction tx = Transaction.openRoot()) {
                extracted = handler.extract(ItemResource.of(RCItems.SCRAP_METAL.get()), 12, tx);
                tx.commit();
            }
            helper.assertTrue(extracted == 12, "a pipe must pull from the barrel, got " + extracted);
            helper.succeed();
        });


        // A BARREL SURVIVES A SAVE AND LOAD. The three existing barrel tests put items in and take them
        // out live, which never serialises anything: a wrong ValueOutput/ValueInput pairing silently
        // empties the mod's bulk store on the next world load and no live test can see it. This drives
        // the real round trip, saveCustomOnly into loadCustomOnly, the way a chunk save does.
        //
        // IT IS A GAMETEST AND NOT A UNIT TEST, deliberately. Written first in src/test/java beside
        // ScrapBinContentsCodecTest, which is where a pure codec round trip belongs, it threw
        // "Components not bound yet" on every case that held a real ItemStack while the empty case
        // passed - the JUnit layer's documented static-init fragility. Data components are bound by
        // the server, so the round trip has to run on one.
        RCGameTests.test("a_scrap_barrel_survives_a_save_and_load", 40, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.SCRAP_BARREL.get());
            BlockPos abs = helper.absolutePos(pos);
            var level = helper.getLevel();
            if (!(level.getBlockEntity(abs) instanceof ScrapBarrelBlockEntity barrel)) {
                helper.fail("the scrap barrel has no BlockEntity");
                return;
            }

            // Slot 0, the LAST slot, and a stack carrying a component. The last slot is here because a
            // size mismatch between save and load truncates from the end and nothing else would notice.
            ItemStack knife = new ItemStack(RCItems.SCRAP_KNIFE.get());
            knife.setDamageValue(7);
            barrel.setItem(0, new ItemStack(RCItems.SCRAP_METAL.get(), 42));
            barrel.setItem(barrel.getContainerSize() - 1, new ItemStack(RCItems.REBAR.get(), 3));
            barrel.setItem(5, knife);

            CompoundTag saved = barrel.saveCustomOnly(level.registryAccess());

            // A SECOND barrel, so this proves a load rather than the first object still holding state.
            BlockPos other = new BlockPos(3, 1, 1);
            helper.setBlock(other, RCBlocks.SCRAP_BARREL.get());
            if (!(level.getBlockEntity(helper.absolutePos(other)) instanceof ScrapBarrelBlockEntity fresh)) {
                helper.fail("the second scrap barrel has no BlockEntity");
                return;
            }
            // Something in every slot first, so a load that MERGES instead of replacing is caught. The
            // rebuild line in loadAdditional is the only thing standing between us and ghost stacks.
            for (int i = 0; i < fresh.getContainerSize(); i++) {
                fresh.setItem(i, new ItemStack(RCItems.PLASTIC_SCRAP.get(), 1));
            }
            fresh.loadCustomOnly(TagValueInput.create(
                ProblemReporter.DISCARDING, level.registryAccess(), saved));

            helper.assertTrue(fresh.getItem(0).is(RCItems.SCRAP_METAL.get())
                    && fresh.getItem(0).getCount() == 42,
                "slot 0 came back as " + fresh.getItem(0) + " rather than 42 scrap metal");
            ItemStack last = fresh.getItem(fresh.getContainerSize() - 1);
            helper.assertTrue(last.is(RCItems.REBAR.get()) && last.getCount() == 3,
                "the last slot came back as " + last + ", so the save truncated from the end");
            helper.assertTrue(fresh.getItem(5).is(RCItems.SCRAP_KNIFE.get())
                    && fresh.getItem(5).getDamageValue() == 7,
                "the knife came back as " + fresh.getItem(5) + " rather than damaged 7, so stack "
                    + "components did not ride along");
            for (int i = 0; i < fresh.getContainerSize(); i++) {
                if (i == 0 || i == 5 || i == fresh.getContainerSize() - 1) {
                    continue;
                }
                helper.assertTrue(fresh.getItem(i).isEmpty(),
                    "slot " + i + " still holds " + fresh.getItem(i) + ", so the load merged into the "
                        + "old contents instead of replacing them");
            }
            helper.succeed();
        });
    }
}

package com.flatts.recompile.gametest;

import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * GameTests for the building-block tier (design P1.12). These are ordinary blocks, so
 * the risk is not behaviour but wiring - a block that does not drop itself, or a slab
 * whose double form does not yield two. One representative check per concern; the full
 * family set is validated by {@code runGameTestServer} parsing every loot table and
 * recipe on boot.
 */
final class BuildingBlockTests {

    private BuildingBlockTests() {
    }

    static void register() {
        // Building blocks must drop themselves - you reclaim your own walls by hand, with
        // no tool gate. Break for real (destroyBlock passes dropBlock=false).
        RCGameTests.test("building_block_drops_itself", 40, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.PRESSED_JUNK_BLOCK.get());
            helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
            helper.assertBlockPresent(Blocks.AIR, pos);
            helper.succeedWhenEntityPresent(EntityType.ITEM, pos);
        });

        // A double slab must give back two slabs, not one - the vanilla-derived loot
        // table carries a set_count that a bad substitution would silently drop.
        RCGameTests.test("double_slab_drops_two", 40, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.PRESSED_JUNK_SLAB.get().defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.DOUBLE));
            helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
            helper.succeedWhen(() -> helper.assertItemEntityCountIs(
                RCBlocks.PRESSED_JUNK_SLAB.get().asItem(), pos, 2.0, 2));
        });
    }
}

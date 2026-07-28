package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * GameTests for the demolition yard's stone path (demolition_yard_spec.md S4.1): Rubble is a
 * pick-through block whose pull stream is stone shards. Driven through the shared {@link
 * SortableBlock#sortOnce} entry point, the {@code sortOnce} convention.
 */
final class DemolitionYardTests {

    private static final BlockPos RUBBLE = new BlockPos(2, 2, 2);

    private static final TagKey<Item> STONE_SHARDS = TagKey.create(
        Registries.ITEM, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "stone_shards"));

    private DemolitionYardTests() {
    }

    static void register() {
        // Sifting rubble bare-hand drops stone shards, then the pile crumbles - the stone entry path.
        RCGameTests.test("rubble_sift_yields_stone_shards", 40, helper -> {
            helper.setBlock(RUBBLE.below(), Blocks.STONE);
            helper.setBlock(RUBBLE, RCBlocks.RUBBLE.get().defaultBlockState());
            BlockPos abs = helper.absolutePos(RUBBLE);
            ServerLevel level = helper.getLevel();

            for (int i = 0; i < 8; i++) {
                if (SortableBlock.sortOnce(level, abs)) {
                    break; // crumbled
                }
            }

            int shards = 0;
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(4))) {
                if (entity.getItem().is(STONE_SHARDS)) {
                    shards++;
                }
            }
            helper.assertTrue(shards > 0, "sifting rubble must drop at least one stone shard, got " + shards);
            helper.succeed();
        });
    }
}

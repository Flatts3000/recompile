package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

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

        // Reinforced Concrete drops only to the Sledgehammer - you crush concrete, bare hands do nothing.
        RCGameTests.test("reinforced_concrete_needs_sledgehammer", 20, helper -> {
            BlockState state = RCBlocks.REINFORCED_CONCRETE.get().defaultBlockState();
            helper.assertTrue(state.requiresCorrectToolForDrops(),
                "reinforced concrete must require the correct tool for drops");
            ItemStack hammer = new ItemStack(RCItems.COPPER_SLEDGEHAMMER.get());
            helper.assertTrue(hammer.isCorrectToolForDrops(state),
                "the copper sledgehammer must be the correct tool for reinforced concrete");
            helper.assertFalse(ItemStack.EMPTY.isCorrectToolForDrops(state),
                "a bare hand must not be the correct tool for reinforced concrete");
            helper.succeed();
        });

        // Steel I-Beam drops only to the Cutting Torch - you cut steel, and the sledgehammer (which crushes
        // concrete) explicitly cannot. Two verbs, two tools.
        RCGameTests.test("steel_i_beam_needs_cutting_torch", 20, helper -> {
            BlockState state = RCBlocks.STEEL_I_BEAM.get().defaultBlockState();
            helper.assertTrue(state.requiresCorrectToolForDrops(),
                "steel i-beam must require the correct tool for drops");
            ItemStack torch = new ItemStack(RCItems.CUTTING_TORCH.get());
            helper.assertTrue(torch.isCorrectToolForDrops(state),
                "the cutting torch must be the correct tool for steel");
            ItemStack hammer = new ItemStack(RCItems.COPPER_SLEDGEHAMMER.get());
            helper.assertFalse(hammer.isCorrectToolForDrops(state),
                "the sledgehammer must NOT cut steel (you crush concrete, you cut steel)");
            helper.assertFalse(ItemStack.EMPTY.isCorrectToolForDrops(state),
                "a bare hand must not cut steel");
            helper.succeed();
        });

        // Steel I-Beams auto-connect on EVERY face (all 6 - up/down included, not just n/e/s/w) and
        // disconnect when the neighbour goes. Test-driven so connection is provably correct per direction.
        RCGameTests.test("steel_beam_connects_and_disconnects_all_faces", 40, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos center = helper.absolutePos(new BlockPos(3, 3, 3));
            level.setBlock(center, RCBlocks.STEEL_I_BEAM.get().defaultBlockState(), 3);
            for (Direction dir : Direction.values()) {
                BlockPos n = center.relative(dir);
                level.setBlock(n, RCBlocks.STEEL_I_BEAM.get().defaultBlockState(), 3);
                helper.assertTrue(level.getBlockState(center).getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir)),
                    "center beam must connect " + dir + " when a beam is placed on that face");
                level.setBlock(n, Blocks.AIR.defaultBlockState(), 3);
                helper.assertFalse(level.getBlockState(center).getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir)),
                    "center beam must disconnect " + dir + " when that beam is removed");
            }
            helper.succeed();
        });

        // Only beams connect - a stone neighbour does not.
        RCGameTests.test("steel_beam_ignores_non_beams", 20, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos center = helper.absolutePos(new BlockPos(3, 3, 3));
            level.setBlock(center, RCBlocks.STEEL_I_BEAM.get().defaultBlockState(), 3);
            level.setBlock(center.east(), Blocks.STONE.defaultBlockState(), 3);
            helper.assertFalse(level.getBlockState(center).getValue(PipeBlock.EAST),
                "a steel beam must not connect to a non-beam neighbour");
            helper.succeed();
        });

        // The hitbox tracks connections: an isolated beam is just the core; a connected face adds an arm
        // reaching it, so beams are clickable/solid (the runClient bug that started this).
        RCGameTests.test("steel_beam_shape_tracks_connections", 20, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos center = helper.absolutePos(new BlockPos(3, 3, 3));
            level.setBlock(center, RCBlocks.STEEL_I_BEAM.get().defaultBlockState(), 3);
            VoxelShape core = level.getBlockState(center).getShape(level, center);
            helper.assertFalse(core.isEmpty(), "an isolated beam must still have a hitbox (the core)");
            helper.assertTrue(core.max(Direction.Axis.Y) <= 0.7,
                "an isolated beam's hitbox is just the core, got maxY " + core.max(Direction.Axis.Y));
            level.setBlock(center.above(), RCBlocks.STEEL_I_BEAM.get().defaultBlockState(), 3);
            VoxelShape up = level.getBlockState(center).getShape(level, center);
            helper.assertTrue(up.max(Direction.Axis.Y) >= 0.99,
                "a beam connected up must have an arm reaching the top, got maxY " + up.max(Direction.Axis.Y));
            helper.succeed();
        });
    }
}

package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.content.block.SteelBeamBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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

    /** Asserts a shape's extent on one axis to within a voxel-rounding epsilon. */
    private static void assertBounds(GameTestHelper helper, VoxelShape shape, Direction.Axis axis,
            double expectedMin, double expectedMax, String what) {
        double min = shape.min(axis);
        double max = shape.max(axis);
        helper.assertTrue(Math.abs(min - expectedMin) < 1.0E-6 && Math.abs(max - expectedMax) < 1.0E-6,
            what + " " + axis + " must span " + expectedMin + ".." + expectedMax + ", got " + min + ".." + max
                + " (the Java VoxelShape and the block model have drifted apart)");
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

        // The clicked face picks the orientation: a floor click stands a column up, a wall click runs a
        // girder out of that wall. This is the beam's headline behaviour and the ONLY rule that reads
        // BlockPlaceContext, so every other test in this file - which all set states directly - would keep
        // passing if getStateForPlacement broke. Hence driving the real placement path here.
        RCGameTests.test("steel_beam_placement_takes_axis_from_clicked_face", 20, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos anchor = helper.absolutePos(new BlockPos(3, 3, 3));
            level.setBlock(anchor, Blocks.STONE.defaultBlockState(), 3);
            ItemStack beam = new ItemStack(RCItems.STEEL_I_BEAM.get());

            for (Direction face : new Direction[] { Direction.UP, Direction.EAST, Direction.NORTH }) {
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(anchor), face, anchor, false);
                BlockState placed = RCBlocks.STEEL_I_BEAM.get().getStateForPlacement(
                    new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, beam, hit));

                helper.assertTrue(placed.getValue(SteelBeamBlock.AXIS) == face.getAxis(),
                    "clicking the " + face + " face must orient the beam on that axis, got "
                        + placed.getValue(SteelBeamBlock.AXIS));
                helper.assertTrue(placed.getValue(SteelBeamBlock.X) == (face.getAxis() == Direction.Axis.X),
                    "X must be set only for a run along X, clicked " + face);
                helper.assertTrue(placed.getValue(SteelBeamBlock.Z) == (face.getAxis() == Direction.Axis.Z),
                    "Z must be set only for a run along Z, clicked " + face);
            }
            helper.succeed();
        });

        // A lone beam is a FULL-HEIGHT COLUMN, not a stub. This is the whole point of the X/Z/AXIS scheme
        // over a node-plus-arms one: geometry is drawn for the run the block belongs to, not only toward
        // connected faces, so a single placed beam looks like the item you placed.
        RCGameTests.test("steel_beam_alone_is_a_full_column", 20, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos center = helper.absolutePos(new BlockPos(3, 3, 3));
            level.setBlock(center, RCBlocks.STEEL_I_BEAM.get().defaultBlockState(), 3);
            BlockState state = level.getBlockState(center);
            helper.assertFalse(state.getValue(SteelBeamBlock.X), "a lone beam is not part of an X run");
            helper.assertFalse(state.getValue(SteelBeamBlock.Z), "a lone beam is not part of a Z run");
            VoxelShape pole = state.getShape(level, center);
            assertBounds(helper, pole, Direction.Axis.Y, 0.0, 1.0, "lone beam (pole model)");
            assertBounds(helper, pole, Direction.Axis.X, 4 / 16.0, 12 / 16.0, "lone beam (I profile)");
            assertBounds(helper, pole, Direction.Axis.Z, 4 / 16.0, 12 / 16.0, "lone beam (I profile)");
            helper.succeed();
        });

        // A horizontal member spans its block face to face, so a run has no seam and no short end.
        //
        // Bounds are asserted EXACTLY against models/block/steel_beam_{pole,x,cross}.json. The Java
        // VoxelShapes and the JSON models are two hand-kept copies of one geometry, and a loose bound lets
        // them drift apart silently - which is how a 6px wireframe once ended up drawn around a 2px nub.
        // If you retune the models, this test is meant to fail.
        RCGameTests.test("steel_beam_horizontal_run_spans_the_block", 20, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos center = helper.absolutePos(new BlockPos(3, 3, 3));
            level.setBlock(center, RCBlocks.STEEL_I_BEAM.get().defaultBlockState()
                .setValue(SteelBeamBlock.AXIS, Direction.Axis.X)
                .setValue(SteelBeamBlock.X, true), 3);
            VoxelShape beam = level.getBlockState(center).getShape(level, center);
            assertBounds(helper, beam, Direction.Axis.X, 0.0, 1.0, "X beam spans the block");
            assertBounds(helper, beam, Direction.Axis.Y, 3 / 16.0, 13 / 16.0, "X beam (I profile)");
            helper.succeed();
        });

        // A cross junction gets both gussets, so a beam crossing a beam reads as a joint.
        RCGameTests.test("steel_beam_cross_gets_both_gussets", 20, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos center = helper.absolutePos(new BlockPos(3, 3, 3));
            level.setBlock(center, RCBlocks.STEEL_I_BEAM.get().defaultBlockState()
                .setValue(SteelBeamBlock.AXIS, Direction.Axis.X)
                .setValue(SteelBeamBlock.X, true)
                .setValue(SteelBeamBlock.Z, true), 3);
            VoxelShape cross = level.getBlockState(center).getShape(level, center);
            assertBounds(helper, cross, Direction.Axis.X, 0.0, 1.0, "cross spans X");
            assertBounds(helper, cross, Direction.Axis.Z, 0.0, 1.0, "cross spans Z");
            assertBounds(helper, cross, Direction.Axis.Y, 0.0, 1.0, "cross gussets reach both faces");
            helper.succeed();
        });

        // A column picks up a horizontal run passing through it, and drops it again when the run is pulled
        // out - so a girder cannot be left hanging in the air with nothing holding it up.
        RCGameTests.test("steel_beam_column_joins_and_leaves_a_run", 40, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos center = helper.absolutePos(new BlockPos(3, 3, 3));
            level.setBlock(center, RCBlocks.STEEL_I_BEAM.get().defaultBlockState(), 3);
            helper.assertFalse(level.getBlockState(center).getValue(SteelBeamBlock.X),
                "a lone column is not part of an X run yet");

            level.setBlock(center.east(), RCBlocks.STEEL_I_BEAM.get().defaultBlockState()
                .setValue(SteelBeamBlock.AXIS, Direction.Axis.X)
                .setValue(SteelBeamBlock.X, true), 3);
            helper.assertTrue(level.getBlockState(center).getValue(SteelBeamBlock.X),
                "a column must join a horizontal run that reaches it");

            level.setBlock(center.east(), Blocks.AIR.defaultBlockState(), 3);
            helper.assertFalse(level.getBlockState(center).getValue(SteelBeamBlock.X),
                "the run must retract when it is no longer supported");
            helper.succeed();
        });

        // Where a horizontal run meets a column, a gusset joins them instead of the two shapes just
        // intersecting. A stone neighbour is NOT structure and gets no gusset.
        RCGameTests.test("steel_beam_gussets_a_beam_above_only", 40, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos center = helper.absolutePos(new BlockPos(3, 3, 3));
            BlockState horizontal = RCBlocks.STEEL_I_BEAM.get().defaultBlockState()
                .setValue(SteelBeamBlock.AXIS, Direction.Axis.X)
                .setValue(SteelBeamBlock.X, true);

            level.setBlock(center, horizontal, 3);
            level.setBlock(center.above(), Blocks.STONE.defaultBlockState(), 3);
            helper.assertFalse(level.getBlockState(center).getValue(SteelBeamBlock.TOP),
                "plain stone above a beam is not structure and must not raise a gusset");

            level.setBlock(center.above(), RCBlocks.STEEL_I_BEAM.get().defaultBlockState(), 3);
            helper.assertTrue(level.getBlockState(center).getValue(SteelBeamBlock.TOP),
                "a column above a horizontal run must raise a gusset");
            VoxelShape joined = level.getBlockState(center).getShape(level, center);
            assertBounds(helper, joined, Direction.Axis.Y, 3 / 16.0, 1.0,
                "gusset must reach the top face (block/steel_beam_top.json)");
            helper.succeed();
        });
    }
}

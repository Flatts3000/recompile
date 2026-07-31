package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.content.block.SteelBeamBlock;
import com.flatts.recompile.content.item.CuttingTorchItem;
import com.flatts.recompile.event.RCTorchFuel;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
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

    /** How many of an item a player is carrying. */
    private static int countIn(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    static void register() {
        // Sifting rubble bare-hand drops stone shards, then the pile crumbles - the stone entry path.
        RCGameTests.test("rubble_sift_yields_stone_shards", 40, helper -> {
            helper.setBlock(RUBBLE.below(), Blocks.STONE);
            helper.setBlock(RUBBLE, RCBlocks.STONE_RUBBLE.get().defaultBlockState());
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

        // The torch is CHARGED with rags ahead of time and cuts off its own stored fuel - it never reaches
        // into the pack mid-cut. Driven through the same entry points the use() and break hooks call.
        RCGameTests.test("cutting_torch_charges_from_rags", 40, helper -> {
            ItemStack torch = new ItemStack(RCItems.CUTTING_TORCH.get());

            // A freshly crafted torch carries the rag its recipe already spent - it is not born empty.
            helper.assertTrue(CuttingTorchItem.fuel(torch) == CuttingTorchItem.CUTS_PER_RAG,
                "a new torch must hold one rag's worth, got " + CuttingTorchItem.fuel(torch));

            CuttingTorchItem.addRag(torch);
            helper.assertTrue(CuttingTorchItem.fuel(torch) == CuttingTorchItem.CUTS_PER_RAG * 2,
                "charging must add a whole rag, got " + CuttingTorchItem.fuel(torch));

            // Fill to capacity, then confirm it refuses a rag that would be partly wasted rather than
            // clamping - a clamped top-up silently burns most of a rag with nothing on screen to show it.
            while (CuttingTorchItem.hasRoomForRag(torch)) {
                CuttingTorchItem.addRag(torch);
            }
            helper.assertTrue(CuttingTorchItem.fuel(torch) == CuttingTorchItem.CAPACITY,
                "topping up must reach exactly capacity, got " + CuttingTorchItem.fuel(torch));
            helper.assertFalse(CuttingTorchItem.hasRoomForRag(torch),
                "a full torch must not accept another rag");
            helper.succeed();
        });

        // Charging through the REAL interaction, not the helper. The tests above call addRag directly, so
        // use() - the only thing a player actually touches - would keep them all green if it broke.
        RCGameTests.test("cutting_torch_use_charges_from_the_pack", 40, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            ItemStack torch = new ItemStack(RCItems.CUTTING_TORCH.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, torch);

            // No rags: refused, and nothing is added.
            helper.assertFalse(CuttingTorchItem.hasRag(player), "the test player must start with no rags");
            RCItems.CUTTING_TORCH.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(CuttingTorchItem.fuel(player.getMainHandItem()) == CuttingTorchItem.CUTS_PER_RAG,
                "charging with an empty pack must not add fuel, got "
                    + CuttingTorchItem.fuel(player.getMainHandItem()));

            // With a rag: the rag is spent and the charge rises by exactly one rag's worth.
            player.getInventory().add(new ItemStack(RCItems.OILY_RAG.get(), 1));
            RCItems.CUTTING_TORCH.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(CuttingTorchItem.fuel(player.getMainHandItem()) == CuttingTorchItem.CUTS_PER_RAG * 2,
                "use() must add one rag's worth, got " + CuttingTorchItem.fuel(player.getMainHandItem()));
            helper.assertTrue(countIn(player, RCItems.OILY_RAG.get()) == 0,
                "use() must spend the rag, got " + countIn(player, RCItems.OILY_RAG.get()) + " left");

            player.discard();
            helper.succeed();
        });

        // Cutting draws down the stored charge, and an empty torch refuses outright.
        RCGameTests.test("cutting_torch_spends_its_charge", 40, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            // Survival explicitly: creative is exempt from fuel by design, and the mock player does not
            // default to survival, so without this the test would assert the exemption rather than the rule.
            player.setGameMode(GameType.SURVIVAL);
            ItemStack torch = new ItemStack(RCItems.CUTTING_TORCH.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, torch);
            BlockState beam = RCBlocks.STEEL_I_BEAM.get().defaultBlockState();

            helper.assertFalse(player.getAbilities().instabuild, "the test player must be in survival");
            helper.assertTrue(RCTorchFuel.cutCostsFuel(player, beam),
                "cutting steel with the torch must cost fuel");
            helper.assertFalse(RCTorchFuel.cutCostsFuel(player, Blocks.DIRT.defaultBlockState()),
                "breaking a block the torch does not cut must be free");

            int charge = CuttingTorchItem.fuel(torch);
            for (int cut = 0; cut < charge; cut++) {
                helper.assertTrue(RCTorchFuel.spendFuel(player), "cut " + cut + " must draw on the charge");
            }
            helper.assertTrue(CuttingTorchItem.fuel(player.getMainHandItem()) == 0,
                "the charge must be spent exactly, got " + CuttingTorchItem.fuel(player.getMainHandItem()));
            helper.assertFalse(RCTorchFuel.spendFuel(player), "a dry torch must refuse the cut");

            player.discard();
            helper.succeed();
        });

        // The torch is the tool, not the consumable: the rag is the sink, so durability must not double-tax
        // the same cut. A damageable torch would silently reintroduce the v1 fuel model alongside this one.
        RCGameTests.test("cutting_torch_never_wears_out", 20, helper -> {
            ItemStack torch = new ItemStack(RCItems.CUTTING_TORCH.get());
            helper.assertFalse(torch.isDamageableItem(),
                "the torch must be unbreakable - its fuel is the rag, not its own durability");
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

        // Cutting a beam yields STEEL OFFCUTS, not ore. Recycled structural steel is already-reduced
        // metal - it becomes graded scrap and is remelted, never returned to ore - so raw_iron was
        // backwards, quite apart from handing the gated metal straight to a basic furnace.
        RCGameTests.test("steel_beam_drops_offcuts_not_ore", 20, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = helper.absolutePos(new BlockPos(3, 3, 3));
            BlockState beam = RCBlocks.STEEL_I_BEAM.get().defaultBlockState();
            level.setBlock(pos, beam, 3);

            // Rolled with the torch in hand, since the beam requires the correct tool for drops.
            List<ItemStack> drops = Block.getDrops(beam, level, pos, null, null,
                new ItemStack(RCItems.CUTTING_TORCH.get()));

            helper.assertFalse(drops.isEmpty(), "cutting a beam with the torch must drop something");
            for (ItemStack drop : drops) {
                helper.assertTrue(drop.is(RCItems.STEEL_OFFCUT.get()),
                    "a beam must yield steel offcuts, got " + drop);
                helper.assertFalse(drop.is(Items.RAW_IRON), "a beam must not vend vanilla ore");
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

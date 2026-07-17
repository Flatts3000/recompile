package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.RecompileWorkbenchBlock;
import com.flatts.recompile.content.block.entity.RecompileWorkbenchBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

/**
 * GameTests for the Recompile Workbench (design P1.4): the materials-only teardown bench.
 * The bench is the found economy's teardown exit (P1.11.5), so its behaviour - rack a tool,
 * hold to break a find into materials, spend the tool's durability - is the load-bearing part.
 */
final class RecompileWorkbenchTests {

    private RecompileWorkbenchTests() {
    }

    static void register() {
        // Right-click with a tool racks it (blockstate + BE), and breaking the bench returns it.
        RCGameTests.test("workbench_racks_and_drops_a_tool", 40, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.RECOMPILE_WORKBENCH.get());

            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            Vec3 standing = helper.absoluteVec(pos.above().getCenter());
            player.snapTo(standing.x, standing.y, standing.z);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RCItems.SCRAP_KNIFE.get()));

            helper.useBlock(pos, player);

            helper.assertTrue(helper.getBlockState(pos).getValue(RecompileWorkbenchBlock.HAS_KNIFE),
                "racking a knife must set the has_knife blockstate (drives the baked model)");
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                    instanceof RecompileWorkbenchBlockEntity workbench)) {
                helper.fail("the workbench has no BlockEntity");
                return;
            }
            helper.assertTrue(workbench.hasTool(RecompileWorkbenchBlockEntity.KNIFE_SLOT),
                "the knife must be racked in the BlockEntity (rack's syncPresence must not drop it)");

            // Any removal must return the racked tool - driven by preRemoveSideEffects, since the
            // BE is not a Container. destroyBlock takes the real removal path.
            helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
            helper.assertBlockPresent(Blocks.AIR, pos);
            helper.succeedWhen(() ->
                helper.assertItemEntityCountIs(RCItems.SCRAP_KNIFE.get(), pos, 2.0, 1));
        });

        // The hold-progress path (advanceBreakdown across ticks) - which breakdownNow bypasses.
        // Fire it every 4 ticks like a held right-click; near 80 ticks it completes and drops.
        RCGameTests.test("workbench_hold_completes_a_breakdown", 140, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.RECOMPILE_WORKBENCH.get());
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(pos);
            if (!(level.getBlockEntity(abs) instanceof RecompileWorkbenchBlockEntity workbench)) {
                helper.fail("the workbench has no BlockEntity");
                return;
            }
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            workbench.rackTool(level, player, new ItemStack(RCItems.SCRAP_KNIFE.get()));
            ItemStack mattress = new ItemStack(RCItems.MATTRESS.get());

            for (int tick = 4; tick <= 100; tick += 4) {
                helper.runAfterDelay(tick, () -> workbench.advanceBreakdown(level, player, mattress));
            }
            helper.runAfterDelay(112, () -> {
                helper.assertItemEntityCountIs(Items.STRING, pos, 3.0, 4);
                helper.succeed();
            });
        });

        // The flagship: a mattress + a racked knife -> string/fiber/scrap, and the knife wears.
        RCGameTests.test("workbench_breaks_down_mattress_with_knife", 40, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.RECOMPILE_WORKBENCH.get());
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(pos);
            if (!(level.getBlockEntity(abs) instanceof RecompileWorkbenchBlockEntity workbench)) {
                helper.fail("the workbench has no BlockEntity");
                return;
            }
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            workbench.rackTool(level, player, new ItemStack(RCItems.SCRAP_KNIFE.get()));

            boolean broke = RecompileWorkbenchBlockEntity.breakdownNow(level, abs,
                new ItemStack(RCItems.MATTRESS.get()));
            helper.assertTrue(broke, "a mattress with a knife racked must break down");
            helper.assertTrue(
                workbench.getTool(RecompileWorkbenchBlockEntity.KNIFE_SLOT).getDamageValue() == 1,
                "the breakdown must spend one durability on the racked knife");

            helper.succeedWhen(() -> {
                helper.assertItemEntityCountIs(Items.STRING, pos, 3.0, 4);
                helper.assertItemEntityCountIs(RCItems.FIBER_SCRAP.get(), pos, 3.0, 2);
                helper.assertItemEntityCountIs(RCItems.SCRAP_METAL.get(), pos, 3.0, 1);
            });
        });

        // The tool gate: no knife racked, the mattress must be refused whole (nothing dropped,
        // input intact). Verified to FAIL against a version that skips hasRequiredTool.
        RCGameTests.test("workbench_refuses_without_the_tool", 20, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.RECOMPILE_WORKBENCH.get());
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(pos);

            ItemStack mattress = new ItemStack(RCItems.MATTRESS.get());
            boolean broke = RecompileWorkbenchBlockEntity.breakdownNow(level, abs, mattress);

            helper.assertFalse(broke, "no knife racked -> the bench must refuse the mattress");
            helper.assertTrue(mattress.getCount() == 1,
                "a refused breakdown must not consume the input, count was " + mattress.getCount());
            helper.assertItemEntityCountIs(Items.STRING, pos, 3.0, 0);
            helper.succeed();
        });

        // Racked tools (and their durability) must survive a save/load - the one behaviour the
        // interaction tests can't reach, and a wrong ValueOutput/ValueInput pairing would drop
        // the tools silently on world reload.
        RCGameTests.test("workbench_tools_survive_reload", 20, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.RECOMPILE_WORKBENCH.get());
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(pos);
            if (!(level.getBlockEntity(abs) instanceof RecompileWorkbenchBlockEntity workbench)) {
                helper.fail("the workbench has no BlockEntity");
                return;
            }
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            workbench.rackTool(level, player, new ItemStack(RCItems.SCRAP_KNIFE.get()));
            RecompileWorkbenchBlockEntity.breakdownNow(level, abs, new ItemStack(RCItems.MATTRESS.get()));
            helper.assertTrue(
                workbench.getTool(RecompileWorkbenchBlockEntity.KNIFE_SLOT).getDamageValue() == 1,
                "precondition: the racked knife took durability");

            var registries = level.registryAccess();
            CompoundTag tag = workbench.saveCustomOnly(registries);
            RecompileWorkbenchBlockEntity reloaded =
                new RecompileWorkbenchBlockEntity(workbench.getBlockPos(), workbench.getBlockState());
            reloaded.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

            helper.assertTrue(reloaded.hasTool(RecompileWorkbenchBlockEntity.KNIFE_SLOT),
                "the racked knife must survive save/load");
            helper.assertTrue(
                reloaded.getTool(RecompileWorkbenchBlockEntity.KNIFE_SLOT).getDamageValue() == 1,
                "the knife's durability must survive save/load");
            helper.succeed();
        });
    }
}

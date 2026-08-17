package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.ScrapBinBlock;
import com.flatts.recompile.content.block.ScrapBinContent;
import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import com.flatts.recompile.content.block.TreeNurseryCoreBlock;
import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.content.block.multiblock.MultiblockDummyBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Framework-level GameTests for the multiblock {@code form()} step (design P2.10, the Workstation).
 *
 * <p>The Workstation reuses the multiblock core with <b>{@code formed == component} for every cell</b>
 * so no block is replaced - the barrel, bins and burn barrel keep functioning. That only works if
 * {@code form()} <b>leaves a cell alone when its block already matches</b>. MC keeps a BlockEntity
 * across a same-block {@code setBlock}, so the BE <em>contents</em> survive either way; what the old
 * form reset is the derived <b>blockstate</b> - a bound bin's {@code content}/{@code fill} snapping
 * back to default (rendering empty while its BE still holds the scrap), plus a wave of redundant block
 * updates. These two tests pin both sides: the blockstate survives when formed == component, and a
 * differing cell is still replaced.
 */
final class MultiblockTests {

    private MultiblockTests() {
    }

    static void register() {
        // The Workstation case: a formed==component cell that carries state must survive forming.
        RCGameTests.test("multiblock_form_preserves_stateful_component", 20, helper -> {
            BlockPos coreRel = new BlockPos(1, 1, 1);
            BlockPos binRel = coreRel.above();
            helper.setBlock(binRel, RCBlocks.SCRAP_BIN.get());
            ScrapBinBlockEntity bin = (ScrapBinBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(binRel));
            bin.deposit(new ItemStack(RCItems.SCRAP_METAL.get(), 42));
            // Depositing bound the bin, which synced its content blockstate - the thing form() must
            // not reset. Precondition both the blockstate and the BE.
            helper.assertTrue(
                helper.getBlockState(binRel).getValue(ScrapBinBlock.CONTENT) == ScrapBinContent.SCRAP_METAL,
                "precondition: the bin's content blockstate reads scrap metal");
            helper.assertTrue(bin.amount() == 42, "precondition: the bin holds 42");

            // A blueprint whose one cell is the bin, formed == component.
            Multiblock blueprint = new Multiblock(List.of(
                new Multiblock.Cell(new Vec3i(0, 1, 0),
                    RCBlocks.SCRAP_BIN.get(), RCBlocks.SCRAP_BIN.get())));
            blueprint.form(helper.getLevel(), helper.absolutePos(coreRel));

            // The load-bearing assertion: the content blockstate must NOT have snapped to EMPTY.
            helper.assertTrue(
                helper.getBlockState(binRel).getValue(ScrapBinBlock.CONTENT) == ScrapBinContent.SCRAP_METAL,
                "form() must not reset a formed==component block's blockstate - content stayed bound");
            ScrapBinBlockEntity after = (ScrapBinBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(binRel));
            helper.assertTrue(after != null && after.amount() == 42,
                "and the BE contents are intact (42)");
            helper.succeed();
        });

        // The machine case is unchanged: when the formed block differs from the component, the cell is
        // still replaced (the Grass Spreader's copper-pipe -> spigot). Guards against the fix
        // over-reaching and skipping real replacements.
        // Rotation (#82, reported by Spagles). MultiblockDummyBlock.findCore walked the blueprint with no
        // rotation, so a dummy cell of a machine facing anything but NORTH could not find its own core.
        // Everything downstream of findCore broke silently with it: right-clicking a cell did nothing,
        // Jade showed no tooltip, and breaking a cell did not disband the machine.
        //
        // The Tree Nursery is the only directional multiblock in the mod (it is the only core that
        // overrides rotationFor), and its Water Tank cell is at offset (1,0,0) - a pure X offset, so it
        // lands somewhere different under every rotation. That is what makes it the case that catches this.
        RCGameTests.test("dummy_cell_finds_its_core_on_a_rotated_machine", 20, helper -> {
            for (Direction facing : new Direction[] {
                    Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                BlockPos core = new BlockPos(2, 1, 2);
                // Clear the whole footprint between cases: a stale block from the previous facing would
                // sit where this one expects air and quietly satisfy the search.
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dy = 0; dy <= 1; dy++) {
                            helper.setBlock(core.offset(dx, dy, dz), Blocks.AIR);
                        }
                    }
                }
                // FORMED, because a machine with dummy cells standing in the world always is - the
                // cells only exist once tryForm ran. Placing the core unformed and calling form()
                // straight on the blueprint builds a state the game never produces, and findCore now
                // ignores unformed cores so that an unformed one dropped nearby cannot shadow the real
                // master of a built machine.
                BlockState coreState = RCBlocks.TREE_NURSERY.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, facing);
                helper.setBlock(core, coreState);

                TreeNurseryCoreBlock block = (TreeNurseryCoreBlock) coreState.getBlock();
                Rotation rotation = block.rotationFor(coreState);
                block.blueprint().form(helper.getLevel(), helper.absolutePos(core), rotation);
                // FORMED, and it has to be set AFTER the cells exist. A machine with dummy cells
                // standing in the world always carries it - they only exist once tryForm ran - and
                // findCore now ignores unformed cores, so that an unformed one dropped nearby cannot
                // shadow the real master. Setting it before form() does not stick: placing the core
                // fires neighborChanged, the blueprint does not match yet, and the core clears its own
                // flag again.
                helper.setBlock(core, coreState.setValue(MultiblockCoreBlock.FORMED, true));

                BlockPos tank = block.blueprint().cells().get(0).at(core, rotation);
                helper.assertTrue(
                    helper.getBlockState(tank).is(RCBlocks.TREE_NURSERY_TANK.get()),
                    "facing " + facing + ": the tank cell should be formed at " + tank
                        + ", found " + helper.getBlockState(tank));

                BlockPos found = MultiblockDummyBlock.findCore(
                    helper.getLevel(), helper.absolutePos(tank));
                helper.assertTrue(found != null && found.equals(helper.absolutePos(core)),
                    "facing " + facing + ": the tank cell at " + tank + " must find its core at " + core
                        + ", got " + (found == null ? "null" : helper.relativePos(found).toString()));
            }
            helper.succeed();
        });

        // A MACHINE COMES BACK HOWEVER YOU BREAK IT (owner, 2026-08-16, #195).
        //
        // Breaking a multiblock - core, cell, any tool or none - returns every part including the
        // core. A machine is assembled and disassembled, not quarried, and Multiblock.disband already
        // hands components back with popResource and no tool check at all.
        //
        // WHAT WAS WRONG. The Separator and the Trommel declared requiresCorrectToolForDrops, which
        // is enforced in the PLAYER's break path. So breaking the core bare-handed destroyed it, while
        // breaking any CELL bare-handed returned it - the removal hook calls dropResources with no
        // tool context and the loot table has no tool condition to fail. The most expensive machines
        // in the mod had an opt-out gate: hit the drum instead of the core.
        //
        // The precedent was already written down for the Cupola: "a machine you cannot pick up is a
        // machine you lose forever by placing it in the wrong spot", and requiresCorrectToolForDrops
        // "reads as the obvious call for a stone machine and is exactly the trap here". Same trap,
        // now closed for the whole framework rather than one block at a time.
        //
        // Asserted on the PROPERTY rather than through a break, deliberately: destroyBlock drops
        // through the loot table and never consults the harvest layer, so a break-based test returns
        // the core either way and proves the opposite of what it looks like it proves. That is the
        // same reasoning DemolitionYardTests records for the beam, in the other direction.
        RCGameTests.test("every_multiblock_core_comes_back_however_it_is_broken", 20, helper -> {
            List<String> gated = new ArrayList<>();
            int cores = 0;
            for (Block block : BuiltInRegistries.BLOCK) {
                if (!(block instanceof MultiblockCoreBlock)) {
                    continue;
                }
                cores++;
                BlockState state = block.defaultBlockState();
                // requiresCorrectToolForDrops ALONE. Pairing it with
                // ItemStack.EMPTY.isCorrectToolForDrops was wrong and failed all six cores at once:
                // vanilla's harvest check is "does not require a correct tool OR this is one", so a
                // bare hand reports false for plenty of blocks that drop perfectly well. The flag is
                // the whole question.
                if (state.requiresCorrectToolForDrops()) {
                    gated.add(BuiltInRegistries.BLOCK.getKey(block).toString());
                }
            }
            // EXACT, not "more than zero". A greater-than guard catches total discovery failure and
            // misses the likelier one: a machine moved off MultiblockCoreBlock, so the sweep quietly
            // covers less and still passes. Bump this when a machine is added - that is the point.
            helper.assertTrue(cores == 6,
                "expected 6 multiblock cores, found " + cores + " - if a machine was added, raise this "
                    + "number; if one vanished, the sweep is covering less than it claims");
            helper.assertTrue(gated.isEmpty(),
                "these machine cores are destroyed rather than returned when broken without the right "
                    + "tool, while breaking any of their cells hands the core back - so the gate is "
                    + "opt-out and the machine is lost to a wrong swing: " + gated);
            helper.succeed();
        });


        RCGameTests.test("multiblock_form_still_replaces_differing_cells", 20, helper -> {
            BlockPos coreRel = new BlockPos(1, 1, 1);
            BlockPos cellRel = coreRel.above();
            helper.setBlock(cellRel, RCBlocks.COPPER_PIPE.get());

            Multiblock blueprint = new Multiblock(List.of(
                new Multiblock.Cell(new Vec3i(0, 1, 0),
                    RCBlocks.COPPER_PIPE.get(), RCBlocks.GRASS_SPREADER_SPIGOT.get())));
            blueprint.form(helper.getLevel(), helper.absolutePos(coreRel));

            helper.assertBlockPresent(RCBlocks.GRASS_SPREADER_SPIGOT.get(), cellRel);
            helper.succeed();
        });
    }
}

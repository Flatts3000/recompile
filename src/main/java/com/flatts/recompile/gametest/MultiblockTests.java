package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.ScrapBinBlock;
import com.flatts.recompile.content.block.ScrapBinContent;
import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.ItemStack;

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

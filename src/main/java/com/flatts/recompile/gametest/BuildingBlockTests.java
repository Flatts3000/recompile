package com.flatts.recompile.gametest;

import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
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

        // Cullet Glass drops itself (not shards, and not nothing-without-silk-touch like
        // vanilla glass) - building must be reversible. The pane is an IronBarsBlock, the
        // family's most distinct class, so it is the one worth asserting.
        RCGameTests.test("glass_pane_drops_itself", 40, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.CULLET_GLASS_PANE.get());
            helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
            helper.assertBlockPresent(Blocks.AIR, pos);
            helper.succeedWhenEntityPresent(EntityType.ITEM, pos);
        });

        // CARDBOARD IS THE UNGATED ONE, and that is the entire reason it exists (#309).
        //
        // Every other route to a wall in this world asks for something first - the ladder is gated,
        // the machines are gated, the frontier is gated - so the fifth family's job is to ask for
        // nothing: pick through a mound, get cardboard, build. The failure mode is not that it stops
        // working, it is that it quietly stops being EARLY. A blueprint requirement, a Scrap Crafting
        // Table requirement, or a component slipped into the recipe would each leave a family that
        // still crafts, still drops, still passes every other test in this file, and no longer does
        // the one thing it was added for.
        //
        // So this asks the live recipe manager what the shipped 2x2 of cardboard makes. A vanilla
        // `minecraft:crafting` recipe is by definition reachable at any bench; a blueprint recipe is
        // `recompile:blueprint_crafting` and would simply not be found here.
        RCGameTests.test("cardboard_builds_with_nothing_but_cardboard", 40, helper -> {
            var level = helper.getLevel();
            var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 2, java.util.List.of(
                new net.minecraft.world.item.ItemStack(RCItems.CARDBOARD.get()),
                new net.minecraft.world.item.ItemStack(RCItems.CARDBOARD.get()),
                new net.minecraft.world.item.ItemStack(RCItems.CARDBOARD.get()),
                new net.minecraft.world.item.ItemStack(RCItems.CARDBOARD.get())));
            var matches = level.recipeAccess().recipeMap().getRecipesFor(
                net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level);
            boolean makesTheBlock = matches.anyMatch(holder ->
                holder.value().assemble(input)
                    .is(RCBlocks.CARDBOARD_BLOCK.get().asItem()));
            helper.assertTrue(makesTheBlock,
                "four cardboard in a 2x2 does not make a Cardboard Block at an ordinary bench, so the "
                    + "one building family that is meant to need nothing now needs something");

            // AND IT COMES OUT OF THE GROUND. A recipe a player cannot reach the ingredient for is
            // gated just as hard as one behind a blueprint, and the ingredient here is a pull-stream
            // entry - data, so it can be moved or dropped without touching a line of Java.
            boolean inTheStream = com.flatts.recompile.compat.SortingData
                .outputs(com.flatts.recompile.compat.SortingData.HOUSEHOLD).stream()
                .anyMatch(w -> w.stack().is(RCItems.CARDBOARD.get()));
            helper.assertTrue(inTheStream,
                "cardboard is not in the household pull stream, so the first thing a player can build "
                    + "with is not actually available from the first mound");
            helper.succeed();
        });
    }
}

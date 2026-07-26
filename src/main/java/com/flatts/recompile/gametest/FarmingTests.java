package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.entity.CompostHeapBlockEntity;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * GameTests for the Farming tier (rung 3): the compost -> farmland recipes, the Compost Heap's volunteer
 * seedlings, and the Unknown Seedling that plants as a random vanilla crop.
 */
final class FarmingTests {

    private FarmingTests() {
    }

    static void register() {
        // Both compost paths to farmland (dirt + 1 fertilizer; coarse dirt + 4). No hoe in this world.
        RCGameTests.test("compost_recipes_craft_farmland", 20, helper -> {
            ItemStack fert = new ItemStack(RCItems.FERTILIZER.get());
            assertCraftsFarmland(helper, 2, 1,
                List.of(fert.copy(), new ItemStack(Items.DIRT)), "dirt + 1 fertilizer");
            assertCraftsFarmland(helper, 3, 2,
                List.of(new ItemStack(Items.COARSE_DIRT), fert.copy(), fert.copy(), fert.copy(), fert.copy(),
                    ItemStack.EMPTY), "coarse dirt + 4 fertilizer");
            helper.succeed();
        });

        // The Unknown Seedling plants a random vanilla crop on farmland (resolved at plant time).
        RCGameTests.test("unknown_seedling_plants_a_random_crop_on_farmland", 20, helper -> {
            BlockPos farmland = new BlockPos(1, 1, 1);
            helper.setBlock(farmland, Blocks.FARMLAND);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RCItems.UNKNOWN_SEEDLING.get()));
            BlockPos fAbs = helper.absolutePos(farmland);
            RCItems.UNKNOWN_SEEDLING.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(fAbs), Direction.UP, fAbs, false)));
            BlockState planted = helper.getBlockState(farmland.above());
            boolean isCrop = planted.is(Blocks.CARROTS) || planted.is(Blocks.POTATOES)
                || planted.is(Blocks.BEETROOTS) || planted.is(Blocks.MELON_STEM) || planted.is(Blocks.PUMPKIN_STEM);
            helper.assertTrue(isCrop, "the seedling must plant one of the vanilla crops, got " + planted);
            helper.succeed();
        });

        // Off farmland it is a no-op and is not consumed.
        RCGameTests.test("unknown_seedling_only_plants_on_farmland", 20, helper -> {
            BlockPos stone = new BlockPos(1, 1, 1);
            helper.setBlock(stone, Blocks.STONE);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ItemStack seedling = new ItemStack(RCItems.UNKNOWN_SEEDLING.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, seedling);
            BlockPos sAbs = helper.absolutePos(stone);
            InteractionResult result = RCItems.UNKNOWN_SEEDLING.get().useOn(new UseOnContext(player,
                InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(sAbs), Direction.UP, sAbs, false)));
            helper.assertTrue(result == InteractionResult.PASS, "seedling off farmland must PASS, got " + result);
            helper.assertTrue(seedling.getCount() == 1, "seedling off farmland must not be consumed");
            helper.assertTrue(helper.getBlockState(stone.above()).isAir(), "nothing should be planted on stone");
            helper.succeed();
        });

        // Compost volunteers: the roll can come up, and is not guaranteed every layer.
        RCGameTests.test("compost_can_yield_volunteer_seedlings", 20, helper -> {
            BlockPos heap = new BlockPos(1, 1, 1);
            helper.setBlock(heap, RCBlocks.COMPOST_HEAP.get().defaultBlockState()
                .setValue(MultiblockCoreBlock.FORMED, true));
            CompostHeapBlockEntity be =
                (CompostHeapBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(heap));
            boolean sawVolunteer = false;
            boolean sawNone = false;
            for (int i = 0; i < 200 && !(sawVolunteer && sawNone); i++) {
                if (be.rollVolunteer()) {
                    sawVolunteer = true;
                } else {
                    sawNone = true;
                }
            }
            helper.assertTrue(sawVolunteer, "a compost volunteer must be possible");
            helper.assertTrue(sawNone, "volunteers must not come up on every single layer");
            helper.succeed();
        });
    }

    private static void assertCraftsFarmland(net.minecraft.gametest.framework.GameTestHelper helper,
            int w, int h, List<ItemStack> items, String label) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(w, h, items);
        Optional<RecipeHolder<CraftingRecipe>> recipe = level.getServer()
            .getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level, (RecipeHolder<CraftingRecipe>) null);
        helper.assertTrue(recipe.isPresent(), label + " must have a crafting recipe");
        ItemStack result = recipe.get().value().assemble(input);
        helper.assertTrue(result.is(Items.FARMLAND), label + " must yield farmland, got " + result);
    }
}

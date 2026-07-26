package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.entity.DisplayPedestalBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import java.util.ArrayList;
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
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * GameTests for Collectibles (design I-2): the Puzzle Cube's 9-piece recipe, and the Display
 * Pedestal that shows a finished trophy. The renderer is a client concern verified in runClient; the
 * server-side state (holds one collectible, returns it, drops it on break, rejects non-collectibles)
 * is what these cover.
 */
final class CollectiblesTests {

    private static final BlockPos PEDESTAL = new BlockPos(1, 1, 1);

    private CollectiblesTests() {
    }

    static void register() {
        // Nine Puzzle Cube Pieces fill the 3x3 grid and craft the trophy - the grid is the cube's face.
        RCGameTests.test("puzzle_cube_crafts_from_nine_pieces", 20, helper -> {
            ServerLevel level = helper.getLevel();
            List<ItemStack> grid = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                grid.add(new ItemStack(RCItems.PUZZLE_CUBE_PIECE.get()));
            }
            CraftingInput input = CraftingInput.of(3, 3, grid);
            Optional<RecipeHolder<CraftingRecipe>> recipe = level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level, (RecipeHolder<CraftingRecipe>) null);
            helper.assertTrue(recipe.isPresent(), "nine pieces must have a crafting recipe");
            ItemStack result = recipe.get().value().assemble(input);
            helper.assertTrue(result.is(RCItems.PUZZLE_CUBE.get()),
                "the 3x3 of pieces must yield a Puzzle Cube, got " + result);
            helper.succeed();
        });

        // Eight pieces (one short of the full grid) do NOT craft the cube.
        RCGameTests.test("puzzle_cube_needs_the_full_grid", 20, helper -> {
            ServerLevel level = helper.getLevel();
            List<ItemStack> grid = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                grid.add(new ItemStack(RCItems.PUZZLE_CUBE_PIECE.get()));
            }
            grid.add(ItemStack.EMPTY);   // one hole in the 3x3
            CraftingInput input = CraftingInput.of(3, 3, grid);
            Optional<RecipeHolder<CraftingRecipe>> recipe = level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level, (RecipeHolder<CraftingRecipe>) null);
            helper.assertFalse(recipe.isPresent(), "a gap in the grid must not craft the cube");
            helper.succeed();
        });

        // Crafting the cube with itself swaps solved <-> scrambled (a shapeless one-in one-out recipe).
        RCGameTests.test("puzzle_cube_toggles_solved_and_scrambled", 20, helper -> {
            ServerLevel level = helper.getLevel();
            CraftingInput solved = CraftingInput.of(1, 1, List.of(new ItemStack(RCItems.PUZZLE_CUBE.get())));
            Optional<RecipeHolder<CraftingRecipe>> toScrambled = level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, solved, level, (RecipeHolder<CraftingRecipe>) null);
            helper.assertTrue(toScrambled.isPresent()
                    && toScrambled.get().value().assemble(solved).is(RCItems.PUZZLE_CUBE_SCRAMBLED.get()),
                "a solved cube must craft into a scrambled one");

            CraftingInput scrambled = CraftingInput.of(1, 1,
                List.of(new ItemStack(RCItems.PUZZLE_CUBE_SCRAMBLED.get())));
            Optional<RecipeHolder<CraftingRecipe>> toSolved = level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, scrambled, level, (RecipeHolder<CraftingRecipe>) null);
            helper.assertTrue(toSolved.isPresent()
                    && toSolved.get().value().assemble(scrambled).is(RCItems.PUZZLE_CUBE.get()),
                "a scrambled cube must craft back into a solved one");
            helper.succeed();
        });

        // The pedestal takes a collectible in hand, holds it, and hands it back on an empty-hand click.
        RCGameTests.test("display_pedestal_holds_and_returns_a_collectible", 20, helper -> {
            helper.setBlock(PEDESTAL, RCBlocks.DISPLAY_PEDESTAL.get());
            DisplayPedestalBlockEntity pedestal =
                (DisplayPedestalBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(PEDESTAL));
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            BlockPos abs = helper.absolutePos(PEDESTAL);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);

            ItemStack cube = new ItemStack(RCItems.PUZZLE_CUBE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, cube);
            helper.getLevel().getBlockState(abs)
                .useItemOn(cube, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            helper.assertFalse(pedestal.isEmpty(), "the pedestal must hold the collectible after placing");
            helper.assertTrue(pedestal.getDisplayed().is(RCItems.PUZZLE_CUBE.get()),
                "the pedestal must display the Puzzle Cube, got " + pedestal.getDisplayed());

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.getLevel().getBlockState(abs).useWithoutItem(helper.getLevel(), player, hit);
            helper.assertTrue(pedestal.isEmpty(), "an empty-hand click must take the trophy back off");
            helper.succeed();
        });

        // It is a general display (ProjectE-style): any item goes on the stand, not only collectibles.
        RCGameTests.test("display_pedestal_holds_any_item", 20, helper -> {
            helper.setBlock(PEDESTAL, RCBlocks.DISPLAY_PEDESTAL.get());
            DisplayPedestalBlockEntity pedestal =
                (DisplayPedestalBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(PEDESTAL));
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            BlockPos abs = helper.absolutePos(PEDESTAL);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);

            ItemStack ordinary = new ItemStack(Items.DIAMOND);   // not a collectible
            player.setItemInHand(InteractionHand.MAIN_HAND, ordinary);
            helper.getLevel().getBlockState(abs)
                .useItemOn(ordinary, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            helper.assertFalse(pedestal.isEmpty(), "the pedestal must display any item, not only collectibles");
            helper.assertTrue(pedestal.getDisplayed().is(Items.DIAMOND),
                "the displayed item must be what was placed, got " + pedestal.getDisplayed());

            // Empty-handed on a separate, never-filled pedestal is a no-op (nothing to take back).
            BlockPos empty = new BlockPos(3, 1, 1);
            helper.setBlock(empty, RCBlocks.DISPLAY_PEDESTAL.get());
            BlockPos emptyAbs = helper.absolutePos(empty);
            BlockHitResult emptyHit = new BlockHitResult(Vec3.atCenterOf(emptyAbs), Direction.UP, emptyAbs, false);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            InteractionResult result = helper.getLevel().getBlockState(emptyAbs)
                .useWithoutItem(helper.getLevel(), player, emptyHit);
            helper.assertTrue(result == InteractionResult.PASS,
                "empty-hand on an empty pedestal must be a no-op, got " + result);
            helper.succeed();
        });

        // Breaking a filled pedestal drops the trophy, not just the block - the trophy is never lost.
        RCGameTests.test("display_pedestal_drops_its_trophy_on_break", 20, helper -> {
            helper.setBlock(PEDESTAL, RCBlocks.DISPLAY_PEDESTAL.get());
            DisplayPedestalBlockEntity pedestal =
                (DisplayPedestalBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(PEDESTAL));
            pedestal.setDisplayed(new ItemStack(RCItems.PUZZLE_CUBE.get()));

            helper.getLevel().destroyBlock(helper.absolutePos(PEDESTAL), true);
            helper.assertItemEntityPresent(RCItems.PUZZLE_CUBE.get(), PEDESTAL, 2.0);
            helper.succeed();
        });
    }
}

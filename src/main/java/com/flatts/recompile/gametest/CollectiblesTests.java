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
import net.minecraft.world.level.GameType;
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
        //
        // BOTH ENDS OF THE HAND-OFF ARE ASSERTED, and neither used to be. This test placed a cube with a
        // creative player and then only looked at the pedestal, so it could not fail: a survival player
        // who keeps the cube in hand AND sees it on the plinth has duplicated it, and a take-back that
        // empties the pedestal without giving anything back has deleted it. From the pedestal's side the
        // two look exactly like success.
        RCGameTests.test("display_pedestal_holds_and_returns_a_collectible", 20, helper -> {
            helper.setBlock(PEDESTAL, RCBlocks.DISPLAY_PEDESTAL.get());
            DisplayPedestalBlockEntity pedestal =
                (DisplayPedestalBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(PEDESTAL));
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            // makeMockServerPlayerInLevel does NOT default to survival - its abilities carry instabuild,
            // and DisplayPedestalBlock skips the shrink entirely for a creative player. Without this line
            // the hand-off assertion below is testing the creative branch and can never go red.
            player.setGameMode(GameType.SURVIVAL);
            BlockPos abs = helper.absolutePos(PEDESTAL);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);

            ItemStack cube = new ItemStack(RCItems.PUZZLE_CUBE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, cube);
            helper.getLevel().getBlockState(abs)
                .useItemOn(cube, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            helper.assertFalse(pedestal.isEmpty(), "the pedestal must hold the collectible after placing");
            helper.assertTrue(pedestal.getDisplayed().is(RCItems.PUZZLE_CUBE.get()),
                "the pedestal must display the Puzzle Cube, got " + pedestal.getDisplayed());
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                "the cube must leave the hand of a survival player who sets it out - keeping it while the "
                    + "pedestal also holds one is a duplication bug, holds "
                    + player.getItemInHand(InteractionHand.MAIN_HAND));

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.getLevel().getBlockState(abs).useWithoutItem(helper.getLevel(), player, hit);
            helper.assertTrue(pedestal.isEmpty(), "an empty-hand click must take the trophy back off");
            helper.assertTrue(countIn(player, RCItems.PUZZLE_CUBE.get()) == 1,
                "taking the trophy back must put it in the player's inventory - a pedestal that empties "
                    + "and hands back nothing has destroyed the collectible, and the collectible is the "
                    + "one thing here that cannot be re-made");
            helper.succeed();
        });

        // The creative half of the same rule, so neither can pass vacuously: a creative player is not
        // charged for what they set out. Deleting this and the survival assertion above together is how
        // the shrink branch stops being covered at all.
        RCGameTests.test("display_pedestal_does_not_charge_a_creative_player", 20, helper -> {
            helper.setBlock(PEDESTAL, RCBlocks.DISPLAY_PEDESTAL.get());
            DisplayPedestalBlockEntity pedestal =
                (DisplayPedestalBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(PEDESTAL));
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.CREATIVE);
            BlockPos abs = helper.absolutePos(PEDESTAL);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);

            ItemStack cube = new ItemStack(RCItems.PUZZLE_CUBE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, cube);
            helper.getLevel().getBlockState(abs)
                .useItemOn(cube, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);

            helper.assertFalse(pedestal.isEmpty(), "the pedestal must still take the item in creative");
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1,
                "a creative player keeps what they set out, has "
                    + player.getItemInHand(InteractionHand.MAIN_HAND).getCount());
            helper.succeed();
        });

        // It is a general display (ProjectE-style): any item goes on the stand, not only collectibles.
        //
        // ONE of a stack goes up, not the stack. The pedestal copies with count 1 and shrinks by 1, and
        // those are two separate numbers - a shrink of the whole stack silently eats 63 diamonds, and a
        // copy of the whole stack puts 64 on a plinth that hands 64 back for the price of one.
        RCGameTests.test("display_pedestal_holds_any_item", 20, helper -> {
            helper.setBlock(PEDESTAL, RCBlocks.DISPLAY_PEDESTAL.get());
            DisplayPedestalBlockEntity pedestal =
                (DisplayPedestalBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(PEDESTAL));
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            BlockPos abs = helper.absolutePos(PEDESTAL);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);

            ItemStack ordinary = new ItemStack(Items.DIAMOND, 3);   // not a collectible
            player.setItemInHand(InteractionHand.MAIN_HAND, ordinary);
            helper.getLevel().getBlockState(abs)
                .useItemOn(ordinary, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            helper.assertFalse(pedestal.isEmpty(), "the pedestal must display any item, not only collectibles");
            helper.assertTrue(pedestal.getDisplayed().is(Items.DIAMOND),
                "the displayed item must be what was placed, got " + pedestal.getDisplayed());
            helper.assertTrue(pedestal.getDisplayed().getCount() == 1,
                "exactly one of the stack goes on the plinth, got " + pedestal.getDisplayed().getCount());
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 2,
                "setting one diamond out of three must cost exactly one, hand holds "
                    + player.getItemInHand(InteractionHand.MAIN_HAND).getCount());

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

    /** How many of {@code item} the player is carrying, across the whole inventory. */
    private static int countIn(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}

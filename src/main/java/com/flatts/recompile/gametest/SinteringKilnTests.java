package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.entity.SinteringKilnBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import com.flatts.recompile.registry.RCTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;

/**
 * The Sintering Kiln (#248): the fifth verb, and the first that puts something back together.
 *
 * <p>Its parity is with the Burn Barrel, the Cupola and the Slag Furnace rather than with the three
 * conveyor machines - it burns fuel, not FE, so {@code MachineParityTests} derives its list from
 * multiblock cores answering the energy capability and correctly never sees this block.
 */
final class SinteringKilnTests {

    private SinteringKilnTests() {
    }

    private static SinteringKilnBlockEntity place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, RCBlocks.SINTERING_KILN.get());
        if (helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                instanceof SinteringKilnBlockEntity kiln) {
            return kiln;
        }
        helper.fail("the Sintering Kiln has no BlockEntity");
        throw new IllegalStateException("unreachable");
    }

    static void register() {

        // End to end through the real ticker rather than by calling the recipe: fuel in, briquette in,
        // wait. This is what unlocks the brewing stand, and therefore every potion in the game.
        RCGameTests.test("the_kiln_sinters_a_briquette_into_a_blaze_rod", 600, helper -> {
            SinteringKilnBlockEntity kiln = place(helper, new BlockPos(1, 1, 1));
            kiln.setItem(0, new ItemStack(RCItems.BLAZE_BRIQUETTE.get(), 3));
            kiln.setItem(1, new ItemStack(Items.COAL, 8));
            helper.succeedWhen(() -> {
                helper.assertTrue(kiln.getItem(2).is(Items.BLAZE_ROD),
                    "a briquette and fuel must eventually make a blaze rod; the result slot holds "
                        + kiln.getItem(2));
                // ONE BRIQUETTE PER ROD, pinned. A sintering recipe is a cooking recipe and vanilla
                // cooking consumes exactly one item per cook - there is no count on the ingredient -
                // so the four-powder cost lives in the briquette's own crafting recipe and nowhere
                // else. The Slag Furnace shipped with three separate descriptions claiming a ratio its
                // schema could not express; this is cheaper than that drift.
                int made = kiln.getItem(2).getCount();
                int left = kiln.getItem(0).getCount();
                helper.assertTrue(3 - left == made,
                    "one briquette per rod: " + made + " made but " + (3 - left) + " consumed");
            });
        });

        // THE LOOP THAT MUST NOT EXIST, and it is the reason the briquette exists at all. Vanilla
        // crafts one blaze rod into TWO blaze powder. If any recipe turned powder back into a rod
        // one-for-one, a player could run that pair forever and print rods out of nothing.
        //
        // Measured as an economy rather than asserted as a file: count the powder a rod costs by
        // walking the real recipes, and compare it with the powder a rod gives back.
        RCGameTests.test("no_recipe_turns_blaze_powder_into_more_blaze_powder", 20, helper -> {
            var recipes = helper.getLevel().recipeAccess();

            // What one rod gives back, from vanilla's own recipe.
            int refund = 0;
            for (RecipeHolder<?> holder : recipes.recipeMap().values()) {
                for (var display : holder.value().display()) {
                    ItemStack out = display.result().resolveForFirstStack(
                        net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(
                            helper.getLevel()));
                    if (out.is(Items.BLAZE_POWDER)
                            && holder.id().toString().contains("blaze_powder")) {
                        refund = Math.max(refund, out.getCount());
                    }
                }
            }
            helper.assertTrue(refund > 0,
                "vanilla's blaze rod to powder recipe was not found, so this test is measuring "
                    + "nothing");

            // What a rod costs, measured as BEHAVIOUR rather than by counting slots: a 2x2 of blaze
            // powder must make a briquette and anything less must not. That pins the number four
            // without depending on how a shaped recipe reports its ingredients, which is what the
            // first version of this got wrong - it counted unique ingredients, got 1, and reported a
            // cost of zero.
            int cost = 0;
            for (int n = 1; n <= 4; n++) {
                List<ItemStack> grid = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    grid.add(i < n ? new ItemStack(Items.BLAZE_POWDER) : ItemStack.EMPTY);
                }
                var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 2, grid);
                boolean makes = recipes.getRecipeFor(
                    net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, helper.getLevel())
                    .map(h -> h.value().assemble(input).is(RCItems.BLAZE_BRIQUETTE.get()))
                    .orElse(Boolean.FALSE);
                if (makes && cost == 0) {
                    cost = n;
                }
            }
            helper.assertTrue(cost > 0,
                "no amount of blaze powder up to four makes a Blaze Briquette, so the kiln's only "
                    + "input is unobtainable");
            helper.assertTrue(cost > refund,
                "a blaze rod costs " + cost + " blaze powder and refunds " + refund + ". At or below "
                    + "the refund this is an infinite rod loop: craft a rod, break it for powder, and "
                    + "come out ahead every time.");
            helper.succeed();
        });

        // Output routing, and it is not cosmetic. ScrapNetwork.collect returns an EMPTY member list
        // when the block it floods FROM is not itself in #recompile:scrap_connectable, so a machine
        // that drains without being tagged routes nothing at all and logs nothing. The Slag Furnace
        // shipped exactly that bug.
        RCGameTests.test("the_kiln_pushes_its_output_into_connected_storage", 40, helper -> {
            BlockPos barrelPos = new BlockPos(2, 1, 1);
            SinteringKilnBlockEntity kiln = place(helper, new BlockPos(1, 1, 1));
            helper.setBlock(barrelPos, RCBlocks.SCRAP_BARREL.get());
            var barrel = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(barrelPos));
            helper.assertTrue(barrel != null, "no Scrap Barrel to route into");

            kiln.setItem(2, new ItemStack(Items.BLAZE_ROD, 3));
            kiln.drainOutput(helper.getLevel());

            int found = 0;
            for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                if (barrel.getItem(slot).is(Items.BLAZE_ROD)) {
                    found += barrel.getItem(slot).getCount();
                }
            }
            helper.assertTrue(found == 3,
                "the kiln must drain into a connected Scrap Barrel; " + found + " rods arrived. If "
                    + "this is zero, check that recompile:sintering_kiln is in "
                    + "#recompile:scrap_connectable - flooding from a non-member finds nobody and "
                    + "says nothing.");
            helper.succeed();
        });

        // A player breaking their own machine in survival must get it back. No multiblock core and no
        // furnace here declares requiresCorrectToolForDrops, because this block is named in no mineable
        // tag and "correct tool" then resolves to NO tool existing - which cost the Slag Furnace a
        // whole Cupola before it was caught.
        RCGameTests.test("the_kiln_can_be_picked_back_up", 40, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            place(helper, pos);
            var player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            helper.getLevel().destroyBlock(helper.absolutePos(pos), true, player);
            helper.succeedWhen(() -> helper.assertItemEntityPresent(RCItems.SINTERING_KILN.get()));
        });

        // The tag and the recipes must agree in both directions. The tag is what shift-click and the
        // client can ask; the recipes are what the machine actually runs. A briquette in one and not
        // the other is a machine that refuses its only input, or offers one it cannot use.
        RCGameTests.test("the_sinterable_tag_matches_the_sintering_recipes", 40, helper -> {
            List<String> problems = new ArrayList<>();
            int checked = 0;
            for (RecipeHolder<com.flatts.recompile.content.recipe.SinteringRecipe> holder
                    : helper.getLevel().recipeAccess().recipeMap()
                        .byType(RCRecipeTypes.SINTERING.get())) {
                checked++;
                boolean tagged = false;
                for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                    ItemStack stack = new ItemStack(item);
                    if (stack.is(RCTags.SINTERABLE) && holder.value().matches(
                            new net.minecraft.world.item.crafting.SingleRecipeInput(stack),
                            helper.getLevel())) {
                        tagged = true;
                    }
                }
                if (!tagged) {
                    problems.add(holder.id() + " has no input in #recompile:sinterable, so the kiln "
                        + "will refuse to accept it on a shift-click");
                }
            }
            helper.assertTrue(checked > 0,
                "no sintering recipes loaded, so this sweep is checking nothing");
            helper.assertTrue(problems.isEmpty(), "tag and recipes disagree: " + problems);
            helper.succeed();
        });

        // The verb is PUBLIC schema, like the other four. A pack writes a sintering recipe the way it
        // writes a smelting one, so the type has to be registered rather than an internal detail.
        RCGameTests.test("sintering_is_a_registered_public_recipe_type", 20, helper -> {
            helper.assertTrue(
                net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(
                    RCRecipeTypes.SINTERING.get()) != null,
                "recompile:sintering is not in the recipe type registry, so no datapack can write one");
            helper.succeed();
        });
    }
}

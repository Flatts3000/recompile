package com.flatts.recompile.gametest;

import com.flatts.recompile.compat.SortingData;
import com.flatts.recompile.content.recipe.TeardownRecipe;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;

/**
 * The Printer (#112): the find that closes the dye set.
 *
 * <p><b>Black dye has exactly two vanilla sources - an ink sac, or a wither rose</b> - and the wither
 * rose is behind the dimension lockout. So this world had no black dye, which took grey dye with it
 * (white plus black) and left a {@code gray_bed} that shipped in v0.5.0 and could not be made. The
 * printer is the ink.
 *
 * <p>That makes the interesting assertions <b>reachability</b> ones rather than block behaviour. A
 * printer that places, faces and drops correctly but yields no ink would satisfy every structural
 * check the repo already has and still leave the gap exactly where it was.
 */
final class PrinterTests {

    private PrinterTests() {
    }

    static void register() {
        // INK EXISTS AT ALL. The single fact this whole issue is about: somewhere in the shipped
        // recipes, tearing something down hands the player an ink sac. Without it, vanilla's
        // ink_sac -> black_dye recipe is a dead edge and two of the sixteen mattresses are uncraftable.
        RCGameTests.test("something_in_this_world_yields_ink", 20, helper -> {
            List<String> sources = new ArrayList<>();
            int checked = 0;
            for (RecipeHolder<TeardownRecipe> holder : helper.getLevel().recipeAccess()
                    .recipeMap().byType(RCRecipeTypes.TEARDOWN.get())) {
                checked++;
                if (yields(holder.value(), Items.INK_SAC)) {
                    sources.add(holder.id().toString());
                }
            }
            helper.assertTrue(checked > 0,
                "no teardown recipes were found at all - discovery is broken, so this would pass "
                    + "against a mod with no recipes whatsoever");
            helper.assertTrue(!sources.isEmpty(),
                "nothing yields an ink sac, so black dye is unreachable and grey dye with it. "
                    + "The gray and black Clean Mattresses have no route");
            helper.succeed();
        });

        // AND IT COMES FROM THE PRINTER. Deliberately separate from the test above: "ink is reachable"
        // and "ink is reachable HERE" fail for different reasons, and a second find quietly growing an
        // ink output would make the printer's whole reason for existing redundant without failing a
        // single test.
        RCGameTests.test("the_printer_is_the_ink", 20, helper -> {
            TeardownRecipe printer = teardownFor(helper, RCItems.PRINTER.get());
            helper.assertTrue(printer != null,
                "the printer must have a teardown recipe - a find with no exit is just clutter");
            helper.assertTrue(yields(printer, Items.INK_SAC),
                "the printer's teardown must yield an ink sac");
            helper.assertTrue(yields(printer, Items.PAPER),
                "a printer is full of paper");

            List<String> others = new ArrayList<>();
            for (RecipeHolder<TeardownRecipe> holder : helper.getLevel().recipeAccess()
                    .recipeMap().byType(RCRecipeTypes.TEARDOWN.get())) {
                if (yields(holder.value(), Items.INK_SAC)
                    && !holder.value().input().test(new net.minecraft.world.item.ItemStack(
                        RCItems.PRINTER.get()))) {
                    others.add(holder.id().toString());
                }
            }
            helper.assertTrue(others.isEmpty(),
                "ink is the printer's reason to exist, and these also yield it: " + others
                    + ". If that is intended, say so here rather than leaving the printer pointless");
            helper.succeed();
        });

        // LAPIS, AND THE GATE IT MOVED THROUGH (owner, 2026-08-02). Lapis is a pigment - ultramarine is
        // ground lapis - so it comes out of a printer and NOT out of machinery, which contains none.
        // This asserts the placement rather than only the presence, because the gem tier's guard
        // deliberately stopped covering lapis for this and nothing else would notice it drifting into
        // Mechanical Waste later.
        RCGameTests.test("lapis_comes_out_of_a_printer", 20, helper -> {
            TeardownRecipe printer = teardownFor(helper, RCItems.PRINTER.get());
            helper.assertTrue(printer != null, "the printer must have a teardown recipe");
            boolean lapis = printer.extras().stream().anyMatch(e -> e.item() == Items.LAPIS_LAZULI)
                || printer.results().stream().anyMatch(r -> r.item() == Items.LAPIS_LAZULI);
            helper.assertTrue(lapis,
                "the printer is this world's lapis (cyan toner is phthalocyanine blue). Without it "
                    + "lapis has no source at all - the gem tier spec claims to deliver it and no "
                    + "separating recipe produces it");
            helper.succeed();
        });

        // IT IS ACTUALLY IN THE PILE. A find with a perfect teardown that no Bulky Waste ever hands out
        // is unreachable, and nothing about the recipe would say so. Reads the same parsed loot the JEI
        // Prying category renders, so this also proves the printer shows up there.
        RCGameTests.test("bulky_waste_can_hand_out_a_printer", 20, helper -> {
            List<SortingData.Weighted> finds = SortingData.outputs(SortingData.BULKY);
            helper.assertTrue(!finds.isEmpty(), "the Bulky Waste table must parse to finds");
            SortingData.Weighted printer = finds.stream()
                .filter(w -> w.stack().is(RCItems.PRINTER.get())).findFirst().orElse(null);
            helper.assertTrue(printer != null,
                "Bulky Waste never hands out a printer, so its teardown can never run: "
                    + finds.stream().map(w -> w.stack().getItem().toString()).toList());
            helper.assertTrue(printer.chance() > 0.05f,
                "a printer is the only ink in the world, so it must not be a curiosity - got "
                    + printer.chance());
            helper.succeed();
        });

        // The drop is asserted by identity because a find whose loot table names the wrong thing is a
        // silent duplication bug - the water tank shipped a table that dropped a rain collector, and the
        // test covering it asserted the wrong item and passed.
        RCGameTests.test("printer_drops_itself", 40, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.PRINTER.get());

            helper.getLevel().destroyBlock(helper.absolutePos(pos), true);

            helper.assertBlockPresent(Blocks.AIR, pos);
            helper.succeedWhen(() ->
                helper.assertItemEntityCountIs(RCItems.PRINTER.get(), pos, 3.0, 1));
        });
    }

    private static boolean yields(TeardownRecipe recipe, Item item) {
        return recipe.results().stream().anyMatch(r -> r.item() == item)
            || recipe.extras().stream().anyMatch(e -> e.item() == item);
    }

    private static @org.jspecify.annotations.Nullable TeardownRecipe teardownFor(
            net.minecraft.gametest.framework.GameTestHelper helper, Item input) {
        for (RecipeHolder<TeardownRecipe> holder : helper.getLevel().recipeAccess()
                .recipeMap().byType(RCRecipeTypes.TEARDOWN.get())) {
            if (holder.value().input().test(new net.minecraft.world.item.ItemStack(input))) {
                return holder.value();
            }
        }
        return null;
    }
}

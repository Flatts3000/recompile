package com.flatts.recompile.gametest;

import com.flatts.recompile.compat.SortingData;
import com.flatts.recompile.content.recipe.TeardownRecipe;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
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
 *
 * <p><b>It now carries the whole set, not only the ink</b> (owner, 2026-08-05): toner is the one thing
 * in a junkyard that is pigment by design. Blue and black stay routed through their pigment - lapis
 * and an ink sac - because vanilla already turns each into its dye, so shipping the dye item too would
 * orphan a vanilla recipe and hand over a strictly worse item (an ink sac is also bait and a
 * book-and-quill; black dye is neither).
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

        // THE WHOLE DYE SET, WHICH IS THE PRINTER'S SECOND JOB (owner, 2026-08-05). Toner is the one
        // thing in a junkyard that is pigment by design, so the printer carries every colour.
        //
        // The reason this is a test and not a comment is that the gap it closes is invisible from any
        // single dye. White dye's only vanilla routes are bone meal and lily of the valley; there is no
        // lily in the fertilizer scatter and bone meal needs a composter, which needs wood - so white
        // was a rung-4 unlock, and it took gray, pink and light blue with it (all are white plus
        // something) and magenta after those (it needs pink). Five colours behind the tree rung, and
        // every one of them would read as individually fine.
        RCGameTests.test("a_printer_covers_every_dye_colour", 20, helper -> {
            TeardownRecipe printer = teardownFor(helper, RCItems.PRINTER.get());
            helper.assertTrue(printer != null, "the printer must have a teardown recipe");

            List<String> missing = new ArrayList<>();
            for (DyeColor colour : DyeColor.values()) {
                Item wanted = upstreamOf(colour);
                // An id that resolves to nothing comes back as AIR, not null, and AIR is in no
                // teardown - so a typo here would report every colour missing rather than pass
                // vacuously. Named anyway, because the failure would otherwise read as a content bug.
                helper.assertTrue(wanted != Items.AIR,
                    "no item resolved for " + colour.getName() + " - the test's own id mapping is "
                        + "broken, not the printer");
                if (!yields(printer, wanted)) {
                    missing.add(colour.getName());
                }
            }
            helper.assertTrue(missing.isEmpty(),
                "the printer must be able to yield every dye colour, and these have no route out of "
                    + "it: " + missing);
            helper.succeed();
        });

        // BLUE AND BLACK ARE THE PIGMENT, NOT THE DYE (owner, 2026-08-05). Vanilla already grinds lapis
        // into blue dye and an ink sac into black, so shipping the dye items too would hand the player
        // a strictly better version of an item they can already make and quietly orphan two vanilla
        // recipes. It also matters downstream: an ink sac is fishing bait and a book-and-quill, and a
        // black dye is neither, so the substitution is not free either way.
        //
        // Asserted as an exclusion because the failure is silent - the set test above passes whether
        // blue arrives as lapis or as blue_dye, so nothing else here would notice the drift.
        RCGameTests.test("blue_and_black_arrive_as_lapis_and_ink", 20, helper -> {
            TeardownRecipe printer = teardownFor(helper, RCItems.PRINTER.get());
            helper.assertTrue(printer != null, "the printer must have a teardown recipe");

            helper.assertTrue(!yields(printer, Items.BLUE_DYE),
                "blue comes out of a printer as lapis lazuli, not as blue dye - lapis grinds into "
                    + "blue dye and is also the only lapis in this world");
            helper.assertTrue(!yields(printer, Items.BLACK_DYE),
                "black comes out of a printer as an ink sac, not as black dye - the sac makes the "
                    + "dye, and it is also the only bait and the only book-and-quill here");
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

    /**
     * The item the printer is expected to hand over for a colour: the dye itself, except for the two
     * the owner routed through their pigment instead (blue as lapis, black as an ink sac).
     *
     * <p>Resolved by id rather than through {@code DyeItem}, because <b>26.1 severed the link</b>: a
     * {@code DyeItem} no longer takes a {@link DyeColor} in its constructor, no longer exposes
     * {@code getDyeColor()}, and the static {@code DyeItem.byColor(DyeColor)} that every 1.21-era
     * snippet uses is gone. The colour lives in item data now, so the registry id is the mapping.
     */
    private static Item upstreamOf(DyeColor colour) {
        return switch (colour) {
            case BLUE -> Items.LAPIS_LAZULI;
            case BLACK -> Items.INK_SAC;
            default -> BuiltInRegistries.ITEM.getValue(
                Identifier.withDefaultNamespace(colour.getName() + "_dye"));
        };
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

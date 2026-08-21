package com.flatts.recompile.gametest;

import com.flatts.recompile.compat.PulverizingData;
import com.flatts.recompile.content.menu.CupolaFurnaceMenu;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Shift-clicking, and the viewer data for the machine nobody had tested.
 *
 * <p><b>Why this file exists.</b> A post-v0.14.0 coverage pass found `content/menu` at 30% branch and
 * every custom menu's {@code quickMoveStack} at ZERO branches covered, while the same methods carry
 * javadoc describing real bugs already found and fixed in them. A fix nothing pins is a fix waiting to
 * be undone, and this one is not cosmetic: shift-clicking is how players move items, so a regression
 * either strands junk in a machine slot or moves an item somewhere it cannot come back from.
 */
final class MenuTransferTests {

    private MenuTransferTests() {
    }

    static void register() {

        // THE TWO REGRESSIONS THE CUPOLA'S OWN JAVADOC DESCRIBES, neither of which had a test.
        //
        // <p>The first version of that method tried fuel BEFORE smeltable and let a failed move fall
        // through to the next branch. With a full fuel slot, a second stack of Oily Rags then went into
        // the INPUT - "where nothing can smelt it and the machine simply stops", which is a bricked
        // machine with no message. Any non-smeltable junk went the same way, because 26.1's
        // Slot.mayPlace returns true unconditionally and the recipe filter on canPlaceItemThroughFace
        // only guards automation, so the GUI had nothing stopping it.
        //
        // <p>Both halves are asserted here: the ORDER (smeltable wins) and the DEAD END (junk is
        // refused rather than falling into the input).
        RCGameTests.test("cupola_shift_click_puts_things_where_they_belong", 40, helper -> {
            var player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            int slots = com.flatts.recompile.content.block.entity.CupolaFurnaceBlockEntity.SLOTS;

            record Case(String what, ItemStack stack, int expect, String why) {
            }
            // expect: the container slot it must land in, or -1 for "must stay out of the machine".
            List<Case> cases = List.of(
                new Case("a smeltable", new ItemStack(RCItems.SCRAP_METAL.get()), 0,
                    "scrap metal blasts into a copper nugget, so it belongs in the input"),
                new Case("a fuel", new ItemStack(RCItems.OILY_RAG.get()), 1,
                    "an Oily Rag is in the furnace_fuels data map, so it belongs in the fuel slot"),
                new Case("junk that is neither", new ItemStack(Items.COBBLESTONE), -1,
                    "cobblestone cannot be blasted and does not burn. The first version of "
                        + "quickMoveStack put exactly this in the INPUT, where it cannot smelt and the "
                        + "machine stops"));

            List<String> problems = new ArrayList<>();
            for (Case c : cases) {
                var menu = new CupolaFurnaceMenu(0, player.getInventory());
                int from = slots;  // the first player-inventory slot in this menu
                menu.slots.get(from).set(c.stack.copy());
                menu.quickMoveStack(player, from);

                int landed = -1;
                for (int i = 0; i < slots; i++) {
                    if (!menu.slots.get(i).getItem().isEmpty()) {
                        landed = i;
                    }
                }
                if (landed != c.expect()) {
                    problems.add(c.what() + " landed in "
                        + (landed < 0 ? "no machine slot" : "slot " + landed)
                        + " rather than "
                        + (c.expect() < 0 ? "staying out of the machine" : "slot " + c.expect())
                        + " - " + c.why());
                }
            }
            helper.assertTrue(problems.isEmpty(), String.join("; ", problems));

            // AND NOTHING MAY BE SHIFTED INTO EITHER OUTPUT. Slot 3 is the slag slot, which exists only
            // because the machine hands back a byproduct; a player putting something there would be
            // feeding a slot the ticker only ever writes to.
            var menu = new CupolaFurnaceMenu(0, player.getInventory());
            menu.slots.get(2).set(new ItemStack(Items.COPPER_INGOT, 3));
            menu.quickMoveStack(player, 2);
            helper.assertTrue(menu.slots.get(2).getItem().isEmpty(),
                "shift-clicking the output slot did not empty it, so a finished smelt cannot be taken "
                    + "out the way every furnace in the game takes it out");
            helper.succeed();
        });

        // THE VIEWER MUST SHOW EVERY PULVERIZING RECIPE THAT EXISTS, and nothing had ever checked.
        //
        // <p>PulverizingData was at 0% coverage with zero callers in any test, while its siblings
        // SortingData, TeardownData, CupolaData and SeparatingData all had some. It reads the bundled
        // JSON rather than the recipe manager, because recipes are not client-synced in 26.1 and the
        // manager is reliably empty when JEI asks - which is exactly what makes it able to drift from
        // the recipes that actually load, with nothing to say so.
        //
        // <p><b>This is not hypothetical.</b> That class's own javadoc records TeardownData making the
        // mistake already: it named its recipes from a list, a third one shipped, and the viewers
        // denied it existed. The fix was to walk the folder. This asserts the result of that fix by
        // comparing the viewer's list against the LIVE REGISTRY, which is the only thing that can catch
        // the two halves drifting.
        RCGameTests.test("every_pulverizing_recipe_reaches_the_viewer", 40, helper -> {
            int live = 0;
            for (var holder : helper.getLevel().recipeAccess().recipeMap()
                    .byType(RCRecipeTypes.PULVERIZING.get())) {
                live++;
            }
            List<PulverizingData.Entry> shown = PulverizingData.all();

            helper.assertTrue(live > 0,
                "no recipes of type recompile:pulverizing loaded at all, so this test would pass by "
                    + "comparing two empty lists");
            helper.assertTrue(shown.size() == live,
                "the viewer shows " + shown.size() + " pulverizing recipes but " + live + " are "
                    + "loaded. PulverizingData reads the bundled JSON and the registry reads the same "
                    + "files, so a mismatch means one of them is skipping a recipe silently - which is "
                    + "the TeardownData bug its own javadoc describes.");

            List<String> bad = new ArrayList<>();
            for (PulverizingData.Entry e : shown) {
                if (e.inputs().isEmpty()) {
                    bad.add("an entry has no input");
                } else if (e.inputs().get(0).getCount() < 1) {
                    bad.add("an entry's input carries count " + e.inputs().get(0).getCount()
                        + "; count is this type's ratio dial and a category drawing zero of something "
                        + "is describing a different machine");
                }
                if (e.outputs().isEmpty()) {
                    bad.add("an entry has no output");
                }
                if (e.ticks() <= 0) {
                    bad.add("an entry takes " + e.ticks() + " ticks");
                }
            }
            helper.assertTrue(bad.isEmpty(), "malformed viewer rows: " + bad);
            helper.succeed();
        });
    }
}

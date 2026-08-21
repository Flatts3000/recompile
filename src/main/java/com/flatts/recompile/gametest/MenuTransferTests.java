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
        // <p><b>What is asserted, and what deliberately is not.</b> Review of #274 mutation-tested the
        // first version of this: swapping the canSmelt and isFuel branches in production left all 547
        // tests green, because the three fixtures were mutually exclusive - scrap metal smelts and does
        // not burn, an Oily Rag burns and does not smelt, cobblestone does neither, so no permutation
        // of the branches routes any of them differently. The comment claimed to pin the ORDER and did
        // not.
        //
        // <p>It cannot: **no Cupola blasting input is also a fuel** (checked against the four blasting
        // recipes and the furnace_fuels data map), so branch order has no observable consequence and a
        // test pretending otherwise would be theatre. What IS the documented bug is the DEAD END - each
        // branch returning on failure instead of falling through - and the scenario the javadoc names
        // for it is a FULL FUEL SLOT, which the first version never set up. That case is below and it
        // is the one that goes red when the returns come out.
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

            // THE SCENARIO THE JAVADOC ACTUALLY NAMES: "with a full fuel slot a second stack of Oily
            // Rags went into the INPUT - where nothing can smelt it and the machine simply stops."
            //
            // The fuel branch must RETURN when the fuel slot cannot take more, not fall through to a
            // branch that can. This is the only case in this test that distinguishes the fixed code
            // from the broken code, so it is the one carrying the finding.
            var full = new CupolaFurnaceMenu(0, player.getInventory());
            full.slots.get(1).set(new ItemStack(RCItems.OILY_RAG.get(), 64));
            full.slots.get(slots).set(new ItemStack(RCItems.OILY_RAG.get(), 16));
            full.quickMoveStack(player, slots);
            helper.assertTrue(full.slots.get(0).getItem().isEmpty(),
                "with the fuel slot full, a second stack of Oily Rags went into the INPUT. Nothing can "
                    + "blast an Oily Rag, so the machine stops with no message - the exact regression "
                    + "the fuel branch's early return exists to prevent");

            // AND NOTHING MAY BE SHIFTED INTO EITHER OUTPUT. Slot 3 is the slag slot, which exists only
            // because the machine hands back a byproduct; a player putting something there would be
            // feeding a slot the ticker only ever writes to.
            //
            // BOTH of them, and they are different classes on purpose: slot 2 is a FurnaceResultSlot,
            // slot 3 is a plain Slot. That distinction is deliberate and load-bearing - a
            // FurnaceResultSlot pops the furnace's banked smelting XP and fires PlayerSmeltedEvent when
            // taken from, so making the slag slot one would drain the experience owed for the METAL and
            // report slag to every listener as something that was smelted. Testing only slot 2 leaves
            // that unpinned, which review of #274 caught.
            for (int out : new int[]{2, 3}) {
                var menu = new CupolaFurnaceMenu(0, player.getInventory());
                menu.slots.get(out).set(new ItemStack(Items.COPPER_INGOT, 3));
                menu.quickMoveStack(player, out);
                helper.assertTrue(menu.slots.get(out).getItem().isEmpty(),
                    "shift-clicking slot " + out + " did not empty it, so what the machine hands back "
                        + "cannot be taken out the way every furnace in the game takes it out");
            }
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
            // BY IDENTITY, NOT BY CARDINALITY. Counting alone passes when the viewer resolves the
            // wrong item, misreads count or picks up the wrong result - the totals still match. Review
            // of #274 called that out, and it is the same "a covered line is not a tested one" trap
            // docs/test_coverage.md already warns about one layer up.
            java.util.Set<String> liveInputs = new java.util.TreeSet<>();
            for (var holder : helper.getLevel().recipeAccess().recipeMap()
                    .byType(RCRecipeTypes.PULVERIZING.get())) {
                holder.value().input().items().forEach(item ->
                    liveInputs.add(BuiltInRegistries.ITEM.getKey(new ItemStack(item).getItem())
                        .toString()));
            }
            List<PulverizingData.Entry> shown = PulverizingData.all();
            java.util.Set<String> shownInputs = new java.util.TreeSet<>();
            for (PulverizingData.Entry e : shown) {
                for (ItemStack st : e.inputs()) {
                    shownInputs.add(BuiltInRegistries.ITEM.getKey(st.getItem()).toString());
                }
            }
            int live = liveInputs.size();

            helper.assertTrue(live > 0,
                "no recipes of type recompile:pulverizing loaded at all, so this test would pass by "
                    + "comparing two empty lists");
            helper.assertTrue(shownInputs.size() == live,
                "the viewer accepts " + shownInputs.size() + " distinct items but " + live + " are "
                    + "loaded. PulverizingData reads the bundled JSON and the registry reads the same "
                    + "files, so a mismatch means one of them is skipping a recipe silently - which is "
                    + "the TeardownData bug its own javadoc describes.");

            java.util.Set<String> onlyLive = new java.util.TreeSet<>(liveInputs);
            onlyLive.removeAll(shownInputs);
            java.util.Set<String> onlyShown = new java.util.TreeSet<>(shownInputs);
            onlyShown.removeAll(liveInputs);
            helper.assertTrue(onlyLive.isEmpty() && onlyShown.isEmpty(),
                "the viewer and the registry disagree about WHICH items pulverize. Loaded but not "
                    + "shown: " + onlyLive + "; shown but not loaded: " + onlyShown + ". Counting rows "
                    + "alone would have missed this.");

            helper.assertTrue(!shown.isEmpty(),
                "PulverizingData returned no rows at all, so every set comparison above compared two "
                    + "empty sets");

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

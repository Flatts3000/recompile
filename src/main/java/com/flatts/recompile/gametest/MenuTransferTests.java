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
        // SHIFT-CLICK NEVER CREATES OR DESTROYS AN ITEM, on every menu that reimplements the move.
        //
        // <p>This file's own header says a coverage pass found "every custom menu's quickMoveStack at
        // ZERO branches covered". It then tested ONE of them.
        //
        // <p><b>Conservation is the assertion because it is the failure that hurts.</b> Routing an item
        // to the wrong slot is visible and annoying; losing it is not recoverable, and vanilla's own
        // contract makes the mistake easy - quickMoveStack must return EMPTY once it can move no more,
        // or the caller loops forever, and the usual bug is clearing the source slot when only part of
        // the stack actually moved.
        //
        // <p><b>Every case asserts it MOVED something, and that is the load-bearing half.</b> Two
        // earlier versions of this test were vacuous in two different ways and both passed. The first
        // never created a remainder, so the branch deciding the remainder's fate never ran. The second
        // fed each machine an item it does not accept - a bare UNSTAMPED Amber to the Sequencer, none
        // of the sixteen hydroponic crops to the Bay - so the player-to-machine half returned EMPTY on
        // the first call and before == after held because nothing happened at all. A conservation check
        // over a no-op is always green. So each menu now names an item its own predicate really takes,
        // and the test fails if that item does not land in a machine slot.
        RCGameTests.test("shift_clicking_never_creates_or_destroys_an_item", 80, helper -> {
            var player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);

            record Menu(String name, java.util.function.Function<net.minecraft.world.entity.player.Inventory,
                net.minecraft.world.inventory.AbstractContainerMenu> make, ItemStack accepted) {
            }
            ItemStack stampedAmber = new ItemStack(RCItems.AMBER.get(), 16);
            stampedAmber.set(com.flatts.recompile.registry.RCDataComponents.SPECIES.get(),
                net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "cow"));

            // `accepted` is an item the machine's own predicate really takes, read off that predicate
            // rather than guessed: the Bay wants an Unknown Seedling or a #recompile:hydroponic crop,
            // the Sequencer wants amber that is STAMPED, the Burner wants something in the
            // furnace_fuels map, the nursery routes Fertilizer to a slot of its own.
            List<Menu> menus = List.of(
                new Menu("burner_generator", inv ->
                    new com.flatts.recompile.content.menu.BurnerGeneratorMenu(0, inv),
                    new ItemStack(RCItems.OILY_RAG.get(), 64)),
                new Menu("hydroponics_bay", inv ->
                    new com.flatts.recompile.content.menu.HydroponicsBayMenu(0, inv),
                    new ItemStack(RCItems.UNKNOWN_SEEDLING.get(), 16)),
                new Menu("sequencer", inv ->
                    new com.flatts.recompile.content.menu.SequencerMenu(0, inv), stampedAmber),
                new Menu("tree_nursery", inv ->
                    new com.flatts.recompile.content.menu.TreeNurseryMenu(0, inv),
                    new ItemStack(RCItems.FERTILIZER.get(), 32)),
                new Menu("cupola_furnace", inv ->
                    new CupolaFurnaceMenu(0, inv),
                    new ItemStack(RCItems.SCRAP_METAL.get(), 64)));

            List<String> problems = new ArrayList<>();
            for (Menu m : menus) {
                // 1. INVENTORY -> MACHINE, room everywhere, using something it accepts.
                //
                // The inventory is cleared FIRST. It is the mock player's real one and scenario 2 packs
                // all 36 slots, so without this every menu after the first ran against a full backpack:
                // the fallback shuffle had nowhere to go, quickMoveStack returned EMPTY at once, and
                // the conservation check passed over a no-op again.
                player.getInventory().clearContent();
                var menu = m.make().apply(player.getInventory());
                int from = firstPlayerSlot(menu, player);
                if (from < 0) {
                    problems.add(m.name() + " exposes no player-inventory slot");
                    continue;
                }
                menu.slots.get(from).set(m.accepted().copy());
                problems.addAll(drain(menu, player, from, m.name() + " in", m.accepted()));
                if (machineHeld(menu, player) == 0) {
                    problems.add(m.name() + " took none of the " + m.accepted().getItem()
                        + " it is supposed to accept, so its player-to-machine branch never ran and "
                        + "the conservation check measured a no-op");
                }

                // 2. MACHINE -> INVENTORY WITH ONLY PARTIAL ROOM, which is the case that matters: with
                // room for everything a move always completes, so the branch deciding the REMAINDER's
                // fate never runs and clearing the source slot unconditionally is indistinguishable.
                for (ItemStack fixture : List.of(m.accepted(), new ItemStack(Items.COBBLESTONE, 64))) {
                    player.getInventory().clearContent();
                    var partial = m.make().apply(player.getInventory());
                    int firstInv = firstPlayerSlot(partial, player);
                    if (firstInv < 0 || partial.slots.get(0).container == player.getInventory()) {
                        problems.add(m.name() + " has no machine slot at index 0, so this case would "
                            + "silently be a player-to-player move under a machine's name");
                        continue;
                    }
                    int max = fixture.getMaxStackSize();
                    if (max < 2) {
                        problems.add(m.name() + " fixture " + fixture.getItem() + " does not stack, so "
                            + "no remainder can exist and this case would be vacuous");
                        continue;
                    }
                    for (int i = firstInv; i < partial.slots.size(); i++) {
                        partial.slots.get(i).set(new ItemStack(fixture.getItem(), max));
                    }
                    partial.slots.get(firstInv).set(new ItemStack(fixture.getItem(), max / 2));
                    partial.slots.get(0).set(new ItemStack(fixture.getItem(), max));
                    problems.addAll(drain(partial, player, 0, m.name() + " out(partial)", fixture));
                    if (partial.slots.get(firstInv).getItem().getCount() != max) {
                        problems.add(m.name() + " filled none of the gap left for it with "
                            + fixture.getItem() + ", so no remainder was ever produced and the case "
                            + "this scenario exists for did not run");
                    }
                }
            }
            helper.assertTrue(problems.isEmpty(),
                "shift-clicking must move items, never mint or eat them: " + problems);
            player.discard();
            helper.succeed();
        });

        // AND THE LIST ABOVE IS COMPLETE, which is checked rather than claimed.
        //
        // <p>The sweep is a hand-written list because these menus cannot all be built from a MenuType
        // alone - the Scrap Crafting Table's factory reads a BlockPos off the open packet. An earlier
        // version of this file said the list was "derived from the REGISTRY", which was simply untrue,
        // and a hand list that reads as complete is this repo's most repeated failure: the
        // scrap_connectable tag went stale five separate times, each caught by review rather than by
        // the person editing it.
        //
        // <p>So it is checked. Every menu class that declares its OWN quickMoveStack must be in the
        // sweep or named here with a reason, and a seventh bespoke menu fails this the day it is
        // written. The Sintering Kiln and Slag Furnace inherit vanilla's and are skipped automatically
        // rather than by being listed, which is the difference between a guard and a second hand-list.
        RCGameTests.test("every_bespoke_menu_transfer_is_covered", 20, helper -> {
            java.util.Set<String> covered = java.util.Set.of(
                "BurnerGeneratorMenu", "HydroponicsBayMenu", "SequencerMenu", "TreeNurseryMenu",
                "CupolaFurnaceMenu");
            java.util.Map<String, String> excused = java.util.Map.of(
                "ScrapCraftingStationMenu",
                "its result path calls player.drop, so it deliberately moves items OUT of the menu's "
                    + "slot set and a conservation check over slots is the wrong instrument for it. "
                    + "Its result-slot behaviour is pinned by CraftingTableTests."
                    + "one_shift_click_crafts_one_batch_not_the_whole_network");

            List<String> uncovered = new ArrayList<>();
            List<Class<?>> found = menuClasses();
            helper.assertTrue(found.size() >= 6,
                "only " + found.size() + " menu classes were found - discovery is broken, so this "
                    + "would pass against any menu that was never tested");
            for (Class<?> c : found) {
                boolean own = false;
                for (var method : c.getDeclaredMethods()) {
                    if (method.getName().equals("quickMoveStack")) {
                        own = true;
                        break;
                    }
                }
                if (!own) {
                    continue;   // inherits vanilla's, so there is nothing of ours to pin
                }
                String name = c.getSimpleName();
                if (!covered.contains(name) && !excused.containsKey(name)) {
                    uncovered.add(name);
                }
            }
            helper.assertTrue(uncovered.isEmpty(),
                "these menus reimplement quickMoveStack and nothing pins it: " + uncovered);
            helper.succeed();
        });

    }

    /** Every item in a menu, machine slots and player inventory together. */
    private static int total(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        int n = 0;
        for (var slot : menu.slots) {
            n += slot.getItem().getCount();
        }
        return n;
    }


    /**
     * Shift-click one slot until the menu says there is nothing left to move, and report any item the
     * menu minted or ate along the way.
     *
     * <p>Loops the way vanilla's own caller does, because a {@code quickMoveStack} that never returns
     * EMPTY hangs the game rather than failing - a bounded loop turns that into a failure with a name.
     */
    private static List<String> drain(net.minecraft.world.inventory.AbstractContainerMenu menu,
            net.minecraft.world.entity.player.Player player, int from, String what, ItemStack fixture) {
        List<String> problems = new ArrayList<>();
        int before = total(menu);
        int guard = 0;
        while (!menu.quickMoveStack(player, from).isEmpty()) {
            if (++guard > 64) {
                problems.add(what + " never returned EMPTY for " + fixture
                    + ", so vanilla's caller would loop forever");
                break;
            }
        }
        int after = total(menu);
        if (before != after) {
            problems.add(what + " held " + before + " items before the shift-click and " + after
                + " after (" + fixture.getItem() + ")");
        }
        return problems;
    }

    /** The first slot in a menu that belongs to the player rather than the machine. */
    private static int firstPlayerSlot(net.minecraft.world.inventory.AbstractContainerMenu menu,
            net.minecraft.world.entity.player.Player player) {
        for (int i = 0; i < menu.slots.size(); i++) {
            if (menu.slots.get(i).container == player.getInventory()) {
                return i;
            }
        }
        return -1;
    }


    /** How many items a menu's MACHINE slots hold, ignoring the player's own. */
    private static int machineHeld(net.minecraft.world.inventory.AbstractContainerMenu menu,
            net.minecraft.world.entity.player.Player player) {
        int n = 0;
        for (var slot : menu.slots) {
            if (slot.container != player.getInventory()) {
                n += slot.getItem().getCount();
            }
        }
        return n;
    }

    /**
     * Every menu class this mod ships, read off {@code RCMenus}' own field types.
     *
     * <p><b>Derived, not listed.</b> A second hand-written list here would have exactly the fault this
     * test exists to catch. {@code RCMenus} declares each menu as
     * {@code DeferredHolder<MenuType<?>, MenuType<XMenu>>} and registering there is not optional - a
     * menu that is not in that class does not exist - so the concrete type is readable off the field's
     * generic signature and nothing can be added without appearing here.
     *
     * <p>Generic type arguments survive in the class file for FIELDS (they are erased from values, not
     * from declarations), which is what makes this work at all; the same trick on a local variable
     * would return nothing.
     */
    private static List<Class<?>> menuClasses() {
        List<Class<?>> classes = new ArrayList<>();
        for (var field : com.flatts.recompile.registry.RCMenus.class.getDeclaredFields()) {
            java.lang.reflect.Type generic = field.getGenericType();
            if (!(generic instanceof java.lang.reflect.ParameterizedType holder)) {
                continue;
            }
            java.lang.reflect.Type[] args = holder.getActualTypeArguments();
            if (args.length < 2
                    || !(args[1] instanceof java.lang.reflect.ParameterizedType menuType)) {
                continue;
            }
            java.lang.reflect.Type[] inner = menuType.getActualTypeArguments();
            if (inner.length == 1 && inner[0] instanceof Class<?> c) {
                classes.add(c);
            }
        }
        return classes;
    }

}

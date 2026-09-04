package com.flatts.recompile.gametest;

import com.flatts.recompile.content.menu.BurnerGeneratorMenu;
import com.flatts.recompile.content.menu.CupolaFurnaceMenu;
import com.flatts.recompile.content.menu.SlagFurnaceMenu;
import com.flatts.recompile.content.menu.HydroponicsBayMenu;
import com.flatts.recompile.content.menu.ScrapCraftingStationMenu;
import com.flatts.recompile.content.menu.TreeNurseryMenu;
import com.flatts.recompile.gui.GuiTheme;
import com.flatts.recompile.gui.Rect;
import com.flatts.recompile.gui.ScreenLayout;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.GameType;

/**
 * Screen geometry for every custom menu in the mod.
 *
 * <p><b>A screen cannot be rendered in a GameTest, but its geometry can be measured</b> - it lives on the
 * {@link ScreenLayout}, which is common code. That is the whole trick here, and it turns "looks broken in
 * a screenshot" into a failing build.
 *
 * <p>Written because the Burner Generator's screen shipped with its readout drawn through the fuel row
 * and its inventory label on top of the slots. Nothing could have caught it: the geometry lived in a
 * client-only class.
 *
 * <p><b>The framework is what made this general.</b> Before it, only slots could be swept, and the one
 * screen whose non-slot elements were checked - the Hydroponics Bay - had them listed by hand in this
 * file, so every new gauge, arrow and readout was uncovered until somebody remembered to add it. Now
 * every element of every screen is declared in one place, so the sweep reads the declaration and a new
 * machine is covered on the day it is written.
 *
 * <p>What this still does <b>not</b> cover: whether the drawing is correct. A gauge could be filled from
 * the wrong end and every assertion here would pass. That is client-render-only and a {@code runClient}
 * pass is the only proof, the same rule the guidebook pages live under.
 */
final class MenuLayoutTests {

    private MenuLayoutTests() {
    }

    /**
     * A screen under test.
     *
     * <p><b>The layout is a supplier, and that is not tidiness.</b> This class is initialised from
     * {@code RCGameTests.register}, which runs inside the mod constructor - so anything it touches
     * eagerly is class-loaded before the game has bound its registries. Naming
     * {@code TreeNurseryMenu.LAYOUT} directly here loads {@code TreeNurseryMenu}, whose layout sizes its
     * species picker from {@code TreeNurseryBlockEntity.SPECIES}, whose own static holds a
     * {@code FluidResource.of(Fluids.WATER)} - and that throws <i>"Components not bound yet"</i> and
     * fails the whole mod to load. A lambda defers the load to when the test actually runs, which is what
     * the previous version of this list was accidentally doing by holding only factories.
     */
    private record Screen(String name, Supplier<ScreenLayout> layoutSource,
            Function<Inventory, AbstractContainerMenu> factory) {

        ScreenLayout layout() {
            return layoutSource.get();
        }
    }

    /**
     * <b>Hand-maintained, and that is the standing risk.</b> A menu added to the mod and not to this
     * list is a screen with no geometry checks at all - no overlap sweep, no panel-bounds sweep, no
     * grid centring - while this class's own javadoc promises "a new machine is covered on the day it
     * is written". The Cupola shipped that way for exactly one review cycle (#236).
     *
     * <p>{@code every_menu_type_is_swept} below fails the build if the registry holds a menu this list
     * does not, so the next one cannot be forgotten quietly.
     */
    private static final List<Screen> SCREENS = List.of(
        new Screen("sequencer", () -> com.flatts.recompile.content.menu.SequencerMenu.LAYOUT,
            inv -> new com.flatts.recompile.content.menu.SequencerMenu(0, inv)),
        new Screen("burner_generator", () -> BurnerGeneratorMenu.LAYOUT,
            inv -> new BurnerGeneratorMenu(0, inv)),
        new Screen("hydroponics_bay", () -> HydroponicsBayMenu.LAYOUT,
            inv -> new HydroponicsBayMenu(0, inv)),
        new Screen("tree_nursery", () -> TreeNurseryMenu.LAYOUT,
            inv -> new TreeNurseryMenu(0, inv)),
        new Screen("scrap_crafting_station", () -> ScrapCraftingStationMenu.LAYOUT,
            inv -> new ScrapCraftingStationMenu(0, inv, BlockPos.ZERO)),
        new Screen("cupola_furnace", () -> CupolaFurnaceMenu.LAYOUT,
            inv -> new CupolaFurnaceMenu(0, inv)),
        new Screen("slag_furnace", () -> SlagFurnaceMenu.LAYOUT,
            inv -> new com.flatts.recompile.content.menu.SlagFurnaceMenu(0, inv)),
        new Screen("sintering_kiln",
            () -> com.flatts.recompile.content.menu.SinteringKilnMenu.LAYOUT,
            inv -> new com.flatts.recompile.content.menu.SinteringKilnMenu(0, inv)),
        new Screen("sell_terminal",
            () -> com.flatts.recompile.content.menu.SellTerminalMenu.LAYOUT,
            inv -> new com.flatts.recompile.content.menu.SellTerminalMenu(0, inv)),
        new Screen("buy_terminal",
            () -> com.flatts.recompile.content.menu.BuyTerminalMenu.LAYOUT,
            inv -> new com.flatts.recompile.content.menu.BuyTerminalMenu(0, inv)));

    /**
     * The machines that hand JEI raw slot indices for its transfer button, and the ranges they hand it.
     *
     * <p>Hand-written, and {@code every_transfer_menu_is_swept} below fails the build if a menu
     * declares transfer constants without appearing here - the same guard {@code SCREENS} gets from
     * {@code every_menu_type_is_swept}, and for the same reason. A list nothing checks is a list that
     * silently stops covering things.
     */
    private record Transfer(String name, Class<?> menuClass, int recipeStart, int recipeCount,
                            int invStart, int invCount,
                            java.util.function.Function<net.minecraft.world.entity.player.Inventory,
                                net.minecraft.world.inventory.AbstractContainerMenu> build) {
    }

    private static final List<Transfer> TRANSFERS = List.of(
        new Transfer("cupola_furnace", CupolaFurnaceMenu.class,
            CupolaFurnaceMenu.TRANSFER_RECIPE_START, CupolaFurnaceMenu.TRANSFER_RECIPE_COUNT,
            CupolaFurnaceMenu.TRANSFER_INV_START, CupolaFurnaceMenu.TRANSFER_INV_COUNT,
            inv -> new CupolaFurnaceMenu(0, inv)),
        new Transfer("slag_furnace", SlagFurnaceMenu.class,
            SlagFurnaceMenu.TRANSFER_RECIPE_START, SlagFurnaceMenu.TRANSFER_RECIPE_COUNT,
            SlagFurnaceMenu.TRANSFER_INV_START, SlagFurnaceMenu.TRANSFER_INV_COUNT,
            inv -> new SlagFurnaceMenu(0, inv)),
        new Transfer("sintering_kiln",
            com.flatts.recompile.content.menu.SinteringKilnMenu.class,
            com.flatts.recompile.content.menu.SinteringKilnMenu.TRANSFER_RECIPE_START,
            com.flatts.recompile.content.menu.SinteringKilnMenu.TRANSFER_RECIPE_COUNT,
            com.flatts.recompile.content.menu.SinteringKilnMenu.TRANSFER_INV_START,
            com.flatts.recompile.content.menu.SinteringKilnMenu.TRANSFER_INV_COUNT,
            inv -> new com.flatts.recompile.content.menu.SinteringKilnMenu(0, inv)));

    static void register() {
        // JEI'S TRANSFER RANGES ARE RAW SLOT INDICES, AND GETTING ONE WRONG IS SILENT (#240).
        //
        // Both smelters own a bespoke MenuType, so JEI's built-in furnace handler - which recognises
        // vanilla's own menu classes and nothing else - does not cover them, and each registers the
        // basic overload with an inventory start and count. Hand that overload an index one off and the
        // "+" button still appears and still moves items, into the wrong slots. Nothing throws.
        //
        // The plugin is client-only, so nothing server-side can read a number written there; the menus
        // declare the ranges instead and this measures them against the menus they describe.
        //
        // Note what this does and does not catch, because an earlier version of this comment claimed
        // more than it delivered. A fifth slot ADDED to the Cupola does not fail here and should not:
        // TRANSFER_INV_START derives from SLOTS, so the range moves with it and stays correct. What
        // would go wrong is the input being REORDERED - slot 0 becoming an output - and that is the
        // case the recipe-slot assertions below exist for.
        RCGameTests.test("menu_transfer_ranges_match_the_real_slots", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            for (Transfer transfer : TRANSFERS) {
                var menu = transfer.build().apply(player.getInventory());
                int total = menu.slots.size();

                // THE RECIPE SLOT, which is the half that fails invisibly. JEI writes the chosen
                // ingredient into these indices, and validateTransferInfo only rejects a FAKE slot -
                // a FurnaceResultSlot is a real one - so pointing this at an output would quietly load
                // the ingredient into the machine's results rather than refusing.
                helper.assertTrue(transfer.recipeCount() == 1,
                    transfer.name() + " declares " + transfer.recipeCount() + " recipe slots; both "
                        + "smelters take exactly one input and the categories draw one input slot");
                var recipeSlot = menu.slots.get(transfer.recipeStart());
                helper.assertTrue(recipeSlot.container != player.getInventory(),
                    transfer.name() + " points JEI's recipe slot at the player's inventory");
                // mayPLACE, not mayPickup. Both are true on a FurnaceResultSlot, so only placement
                // separates an input from an output: 26.1's base Slot.mayPlace is unconditionally
                // true and ResultSlot overrides it to false.
                helper.assertTrue(recipeSlot.mayPlace(new ItemStack(Items.STONE)),
                    transfer.name() + " recipe slot " + transfer.recipeStart() + " refuses items, so "
                        + "it is an output rather than an input - JEI would write the chosen "
                        + "ingredient into the machine's results and not complain");
                helper.assertTrue(transfer.invStart() + transfer.invCount() == total,
                    transfer.name() + " tells JEI the inventory runs " + transfer.invStart() + ".."
                        + (transfer.invStart() + transfer.invCount() - 1) + " but the menu has "
                        + total + " slots - a transfer would land outside it");
                // ...and the slot it names as the first inventory one must really belong to the player,
                // which is the half a count check alone cannot see.
                helper.assertTrue(
                    menu.slots.get(transfer.invStart()).container == player.getInventory(),
                    transfer.name() + " slot " + transfer.invStart() + " is not a player inventory "
                        + "slot, so JEI would draw the transfer from the machine's own contents");
                helper.assertTrue(
                    menu.slots.get(transfer.invStart() - 1).container != player.getInventory(),
                    transfer.name() + " slot " + (transfer.invStart() - 1) + " is already a player "
                        + "slot, so the range starts one too late and the first one is unreachable");
            }
            helper.succeed();
        });

        // THE LIST ABOVE MUST COVER EVERY MENU THAT DECLARES TRANSFER CONSTANTS.
        //
        // Same asymmetry SCREENS already fixed: a hand-written list with nothing tying it to the code
        // stops covering things without saying so. A third machine given a transfer handler would
        // otherwise be entirely unmeasured. Declaring TRANSFER_RECIPE_START is the marker, because a
        // menu only declares it in order to be handed to JEI.
        RCGameTests.test("every_transfer_menu_is_swept", 20, helper -> {
            List<String> missing = new ArrayList<>();
            int declared = 0;
            // THE CANDIDATE LIST IS ITSELF A HAND-LIST, which is why this guard did not fire when
            // the Sintering Kiln shipped declaring transfer constants and missing from TRANSFERS: the
            // kiln was in neither list, so `declared` never counted it and the sweep passed by not
            // looking. A guard whose coverage is a literal has the same failure mode as the thing it
            // guards. The size assertion below is what makes that visible - it cannot name the class
            // somebody forgot, but it can refuse to believe six candidates cover eight menus.
            List<Class<?>> candidates = List.of(
                    CupolaFurnaceMenu.class, SlagFurnaceMenu.class, BurnerGeneratorMenu.class,
                    HydroponicsBayMenu.class, TreeNurseryMenu.class, ScrapCraftingStationMenu.class,
                    com.flatts.recompile.content.menu.SinteringKilnMenu.class,
                    com.flatts.recompile.content.menu.SequencerMenu.class,
                    com.flatts.recompile.content.menu.SellTerminalMenu.class,
                    com.flatts.recompile.content.menu.BuyTerminalMenu.class);
            int registered = com.flatts.recompile.registry.RCMenus.MENUS.getEntries().size();
            helper.assertTrue(candidates.size() >= registered,
                "this sweep considers " + candidates.size() + " menu classes while " + registered
                    + " menu types are registered, so at least one menu is checked by nothing. Add it "
                    + "to the candidate list - a guard that only looks at what someone remembered to "
                    + "list is not a guard.");
            for (Class<?> candidate : candidates) {
                boolean declaresTransfer;
                try {
                    candidate.getField("TRANSFER_RECIPE_START");
                    declaresTransfer = true;
                } catch (NoSuchFieldException absent) {
                    declaresTransfer = false;
                }
                if (!declaresTransfer) {
                    continue;
                }
                declared++;
                if (TRANSFERS.stream().noneMatch(t -> t.menuClass() == candidate)) {
                    missing.add(candidate.getSimpleName());
                }
            }
            helper.assertTrue(declared == TRANSFERS.size(),
                declared + " menus declare transfer constants but TRANSFERS holds "
                    + TRANSFERS.size());
            helper.assertTrue(missing.isEmpty(),
                "these menus hand JEI slot ranges but are not swept here, so nothing checks them: "
                    + missing);
            helper.succeed();
        });

        // THE LIST ABOVE MUST COVER THE REGISTRY, or every sweep in this file is measuring a subset and
        // reporting a clean result. This is how the Cupola's screen shipped unswept: it was registered,
        // it worked, and nothing here knew it existed. Derived from the registry so the next menu
        // cannot be forgotten the same way.
        RCGameTests.test("every_menu_type_is_swept", 20, helper -> {
            java.util.Set<String> swept = new java.util.HashSet<>();
            for (Screen screen : SCREENS) {
                swept.add(screen.name());
            }
            List<String> missed = new ArrayList<>();
            for (var entry : com.flatts.recompile.registry.RCMenus.MENUS.getEntries()) {
                String name = entry.getId().getPath();
                if (!swept.contains(name)) {
                    missed.add(name);
                }
            }
            helper.assertTrue(swept.size() >= 4,
                "only " + swept.size() + " screens listed - discovery is broken, so this passes by "
                    + "checking nothing");
            helper.assertTrue(missed.isEmpty(),
                "these menus are registered but absent from SCREENS, so their geometry is swept by "
                    + "nothing in this file: " + missed);
            helper.succeed();
        });

        /*
         * The structural guarantee, and the one that makes every other assertion here worth having:
         * a menu's slots ARE its layout's slots. Before the framework a menu placed slots from its own
         * numbers and a screen drew chrome from a second set, so the two could disagree silently - the
         * Tree Nursery's screen declared FERT_X = 44 while its menu independently passed 44 to a Slot.
         * With this passing, "no hardcoded slot coordinate" is enforced rather than asked for: a menu
         * that types a number is a menu whose slots no longer match what the screen will draw under them.
         */
        RCGameTests.test("every_menu_slot_comes_from_its_layout", 20, helper -> {
            List<String> wrong = new ArrayList<>();
            forEachScreen(helper, (screen, menu) -> {
                Set<String> declared = new HashSet<>();
                for (ScreenLayout.Group group : screen.layout().groups()) {
                    if (group.kind() == ScreenLayout.Kind.SLOT) {
                        for (Rect rect : group.cells()) {
                            declared.add(rect.x() + "," + rect.y());
                        }
                    }
                }
                Set<String> placed = new HashSet<>();
                for (Slot slot : menu.slots) {
                    placed.add(slot.x + "," + slot.y);
                }
                Set<String> undeclared = new HashSet<>(placed);
                undeclared.removeAll(declared);
                Set<String> undrawn = new HashSet<>(declared);
                undrawn.removeAll(placed);
                if (!undeclared.isEmpty()) {
                    wrong.add(screen.name() + " places slots the layout never declared: " + undeclared);
                }
                if (!undrawn.isEmpty()) {
                    wrong.add(screen.name() + " declares slots the menu never places: " + undrawn);
                }
            });
            report(helper, wrong, "menus whose slots disagree with their layout");
        });

        // Overlapping slots are unclickable or ambiguous, and neither is visible in a diff.
        RCGameTests.test("no_menu_has_overlapping_slots", 20, helper -> {
            List<String> clashes = new ArrayList<>();
            forEachScreen(helper, (screen, menu) -> {
                for (int a = 0; a < menu.slots.size(); a++) {
                    for (int b = a + 1; b < menu.slots.size(); b++) {
                        Slot first = menu.slots.get(a);
                        Slot second = menu.slots.get(b);
                        if (box(first).overlaps(box(second))) {
                            clashes.add(screen.name() + ": slot " + a + " at " + first.x + ","
                                + first.y + " overlaps slot " + b + " at " + second.x + "," + second.y);
                        }
                    }
                }
            });
            report(helper, clashes, "menus with overlapping slots");
        });

        /*
         * Everything the screen draws, against everything else it draws. A backdrop is excluded because
         * having things on top of it is what a backdrop is; everything else - gauges, arrows, readouts,
         * pickers, and both text labels - has to keep out of the others' way.
         */
        RCGameTests.test("no_screen_element_overlaps_another", 20, helper -> {
            List<String> clashes = new ArrayList<>();
            for (Screen screen : SCREENS) {
                List<Map.Entry<ScreenLayout.Group, Rect>> drawn = new ArrayList<>();
                for (Map.Entry<ScreenLayout.Group, Rect> entry : screen.layout().everything()) {
                    ScreenLayout.Kind kind = entry.getKey().kind();
                    if (kind != ScreenLayout.Kind.PANEL && kind != ScreenLayout.Kind.BACKDROP) {
                        drawn.add(entry);
                    }
                }
                for (int a = 0; a < drawn.size(); a++) {
                    for (int b = a + 1; b < drawn.size(); b++) {
                        ScreenLayout.Group groupA = drawn.get(a).getKey();
                        ScreenLayout.Group groupB = drawn.get(b).getKey();
                        // Cells of one group are compared too. The first version skipped them on the
                        // grounds that a grid is laid out by pitch and so cannot collide with itself -
                        // which is only true while every group's pitch is at least its cell size, and
                        // nothing makes that so. A row declared at a pitch narrower than its cells is a
                        // real mistake and there is no reason for the sweep to be blind to it.
                        if (drawn.get(a).getValue().overlaps(drawn.get(b).getValue())) {
                            clashes.add(screen.name() + ": " + groupA.name() + " cell at "
                                + drawn.get(a).getValue() + " overlaps " + groupB.name() + " cell at "
                                + drawn.get(b).getValue());
                        }
                    }
                }
            }
            report(helper, clashes, "screen elements drawn on top of each other");
        });

        // A GRID OF CELLS SITS IN THE MIDDLE OF ITS PANEL.
        //
        // Not a law about layout in general - a well is anchored to the left edge on purpose, and a
        // countdown to the right. It is a law about GRIDS, because a grid is a block of interchangeable
        // cells with no reason to favour a side, and because widening one is the one edit that moves
        // its right edge and leaves its left where it was.
        //
        // Which is exactly what happened (#230): the Tree Nursery's species picker went from four
        // columns to five to fit a ninth species, the origin stayed at x=52, and the picker ended up
        // eight pixels - half a slot - right of centre, no longer under the Fertilizer slot above it.
        // Every sweep above stayed green, because 52..140 neither collides with anything nor leaves the
        // panel. Nothing in this file could see it and nothing was going to until somebody opened the
        // screen.
        RCGameTests.test("a_grid_of_cells_is_centred_in_its_panel", 20, helper -> {
            List<String> crooked = new ArrayList<>();
            for (Screen screen : SCREENS) {
                Map<String, Rect> spans = new java.util.LinkedHashMap<>();
                for (Map.Entry<ScreenLayout.Group, Rect> entry : screen.layout().everything()) {
                    ScreenLayout.Group group = entry.getKey();
                    if (group.kind() != ScreenLayout.Kind.CELL || group.count() <= 1) {
                        continue;
                    }
                    Rect cell = entry.getValue();
                    spans.merge(group.name(), cell, (a, b) -> new Rect(
                        Math.min(a.x(), b.x()), Math.min(a.y(), b.y()),
                        Math.max(a.right(), b.right()) - Math.min(a.x(), b.x()),
                        Math.max(a.bottom(), b.bottom()) - Math.min(a.y(), b.y())));
                }
                for (Map.Entry<String, Rect> span : spans.entrySet()) {
                    int left = span.getValue().x();
                    int right = screen.layout().width() - span.getValue().right();
                    // A pixel of slack: an odd leftover cannot be split evenly and either side is fine.
                    if (Math.abs(left - right) > 1) {
                        crooked.add(screen.name() + ": the " + span.getKey() + " grid spans "
                            + span.getValue() + " in a " + screen.layout().width()
                            + "-wide panel - " + left + "px of margin on the left against " + right
                            + " on the right");
                    }
                }
            }
            report(helper, crooked, "cell grids sitting off the centre of their panel");
        });

        // Anything outside the panel is drawn over the world: clickable at some resolutions, invisible at
        // others, and always wrong.
        RCGameTests.test("no_screen_element_leaves_the_panel", 20, helper -> {
            List<String> escaped = new ArrayList<>();
            for (Screen screen : SCREENS) {
                for (Map.Entry<ScreenLayout.Group, Rect> entry : screen.layout().everything()) {
                    Rect rect = entry.getValue();
                    if (rect.x() < 0 || rect.y() < 0
                            || rect.right() > screen.layout().width()
                            || rect.bottom() > screen.layout().height()) {
                        escaped.add(screen.name() + ": " + entry.getKey().name() + " at " + rect
                            + " leaves a " + screen.layout().width() + "x"
                            + screen.layout().height() + " panel");
                    }
                }
            }
            report(helper, escaped, "screen elements outside their panel");
        });

        // Every menu must carry the player's 36 inventory slots. Forgetting a row is a classic
        // hand-rolled-menu bug: the screen looks fine and a third of the backpack is unreachable.
        RCGameTests.test("every_menu_includes_the_player_inventory", 20, helper -> {
            List<String> wrong = new ArrayList<>();
            forEachScreen(helper, (screen, menu) -> {
                long playerSlots = menu.slots.stream()
                    .filter(slot -> slot.container instanceof Inventory)
                    .count();
                if (playerSlots != 36) {
                    wrong.add(screen.name() + " exposes " + playerSlots + " player slots, expected 36");
                }
            });
            report(helper, wrong, "menus with an incomplete player inventory");
        });

        /*
         * The hotbar is the bottom row and the backpack is above it, in vanilla's order. Getting this
         * wrong is invisible on screen and disastrous in play: the slots draw in the right places and
         * point at the wrong items, so picking up your pickaxe hands you whatever is in the backpack.
         */
        RCGameTests.test("player_inventory_indices_follow_vanilla", 20, helper -> {
            List<String> wrong = new ArrayList<>();
            forEachScreen(helper, (screen, menu) -> {
                List<Slot> player = menu.slots.stream()
                    .filter(slot -> slot.container instanceof Inventory)
                    .toList();
                if (player.size() != 36) {
                    return;   // already reported by the sweep above
                }
                int hotbarY = player.stream().mapToInt(slot -> slot.y).max().orElse(0);
                for (Slot slot : player) {
                    boolean onHotbarRow = slot.y == hotbarY;
                    boolean isHotbarIndex = slot.getContainerSlot() < 9;
                    if (onHotbarRow != isHotbarIndex) {
                        wrong.add(screen.name() + ": inventory slot " + slot.getContainerSlot()
                            + " at y=" + slot.y + " is on the "
                            + (onHotbarRow ? "hotbar row but is a backpack index"
                                           : "backpack but is a hotbar index"));
                    }
                }
            });
            report(helper, wrong, "menus whose player slots are in the wrong order");
        });
    }

    private static Rect box(Slot slot) {
        return new Rect(slot.x, slot.y, GuiTheme.SLOT_SIZE, GuiTheme.SLOT_SIZE);
    }

    /**
     * Build each menu against a real player inventory.
     *
     * <p>Survival explicitly: {@code makeMockServerPlayerInLevel} hands back a creative player, and a
     * menu that behaves differently for creative would be measured in the wrong mode.
     */
    private static void forEachScreen(GameTestHelper helper,
            java.util.function.BiConsumer<Screen, AbstractContainerMenu> body) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        for (Screen screen : SCREENS) {
            body.accept(screen, screen.factory().apply(player.getInventory()));
        }
        player.discard();
    }

    private static void report(GameTestHelper helper, List<String> problems, String label) {
        helper.assertTrue(problems.isEmpty(), label + " (" + problems.size() + "): " + problems);
        helper.succeed();
    }
}

package com.flatts.recompile.gametest;

import com.flatts.recompile.content.menu.BurnerGeneratorMenu;
import com.flatts.recompile.content.menu.HydroponicsBayMenu;
import com.flatts.recompile.content.menu.ScrapCraftingStationMenu;
import com.flatts.recompile.content.menu.TreeNurseryMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.GameType;

/**
 * Slot geometry for every custom menu in the mod.
 *
 * <p><b>A screen cannot be rendered in a GameTest, but its slots can be measured</b> - they live on the
 * menu, which is server-side. That is the whole trick here, and it turns "looks broken in a screenshot"
 * into a failing build.
 *
 * <p>Written because the Burner Generator's screen shipped with its readout drawn through the fuel row
 * and its inventory label on top of the slots. Nothing could have caught it: the geometry lived in a
 * client-only class. It now lives on the menu, and these three menus are swept together so the next one
 * is covered on the day it is written.
 *
 * <p>What this does <b>not</b> cover: anything drawn that is not a slot - gauges, arrows, labels, text.
 * Those are per-screen and only the Burner Generator asserts them today
 * ({@code burner_generator_screen_layout_does_not_overlap}). Stated rather than implied, so the sweep is
 * not mistaken for a guarantee that a screen looks right.
 */
final class MenuLayoutTests {

    /** Vanilla's container width, and the tallest panel any of these use. */
    private static final int PANEL_W = 176;
    private static final int PANEL_H = 184;
    private static final int SLOT = 16;

    private MenuLayoutTests() {
    }

    private record Menu(String name, Function<Inventory, AbstractContainerMenu> factory) { }

    private static final List<Menu> MENUS = List.of(
        new Menu("burner_generator", inv -> new BurnerGeneratorMenu(0, inv)),
        new Menu("hydroponics_bay", inv -> new HydroponicsBayMenu(0, inv)),
        new Menu("tree_nursery", inv -> new TreeNurseryMenu(0, inv)),
        new Menu("scrap_crafting_station",
            inv -> new ScrapCraftingStationMenu(0, inv, BlockPos.ZERO)));

    static void register() {
        // Overlapping slots are unclickable or ambiguous, and neither is visible in a diff.
        RCGameTests.test("no_menu_has_overlapping_slots", 20, helper -> {
            List<String> clashes = new ArrayList<>();
            forEachMenu(helper, (name, menu) -> {
                for (int a = 0; a < menu.slots.size(); a++) {
                    for (int b = a + 1; b < menu.slots.size(); b++) {
                        Slot sa = menu.slots.get(a);
                        Slot sb = menu.slots.get(b);
                        if (sa.x < sb.x + SLOT && sb.x < sa.x + SLOT
                                && sa.y < sb.y + SLOT && sb.y < sa.y + SLOT) {
                            clashes.add(name + ": slot " + a + " at " + sa.x + "," + sa.y
                                + " overlaps slot " + b + " at " + sb.x + "," + sb.y);
                        }
                    }
                }
            });
            report(helper, clashes, "menus with overlapping slots");
        });

        // A slot off the panel is drawn outside the window - clickable in some resolutions, invisible in
        // others, and always wrong.
        RCGameTests.test("no_menu_slot_leaves_the_panel", 20, helper -> {
            List<String> escaped = new ArrayList<>();
            forEachMenu(helper, (name, menu) -> {
                for (Slot slot : menu.slots) {
                    if (slot.x < 0 || slot.y < 0
                            || slot.x + SLOT > PANEL_W || slot.y + SLOT > PANEL_H) {
                        escaped.add(name + ": slot at " + slot.x + "," + slot.y);
                    }
                }
            });
            report(helper, escaped, "menu slots outside the panel");
        });

        // Every menu must carry the player's 36 inventory slots. Forgetting a row is a classic
        // hand-rolled-menu bug: the screen looks fine and a third of the backpack is unreachable.
        RCGameTests.test("every_menu_includes_the_player_inventory", 20, helper -> {
            List<String> wrong = new ArrayList<>();
            forEachMenu(helper, (name, menu) -> {
                long playerSlots = menu.slots.stream()
                    .filter(slot -> slot.container instanceof Inventory)
                    .count();
                if (playerSlots != 36) {
                    wrong.add(name + " exposes " + playerSlots + " player slots, expected 36");
                }
            });
            report(helper, wrong, "menus with an incomplete player inventory");
        });
    }

    /**
     * Build each menu against a real player inventory.
     *
     * <p>Survival explicitly: {@code makeMockServerPlayerInLevel} hands back a creative player, and a
     * menu that behaves differently for creative would be measured in the wrong mode.
     */
    private static void forEachMenu(GameTestHelper helper,
            java.util.function.BiConsumer<String, AbstractContainerMenu> body) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        for (Menu menu : MENUS) {
            body.accept(menu.name(), menu.factory().apply(player.getInventory()));
        }
        player.discard();
    }

    private static void report(GameTestHelper helper, List<String> problems, String label) {
        helper.assertTrue(problems.isEmpty(), label + " (" + problems.size() + "): " + problems);
        helper.succeed();
    }
}

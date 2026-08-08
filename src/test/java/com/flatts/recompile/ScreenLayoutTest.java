package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.recompile.gui.GuiTheme;
import com.flatts.recompile.gui.Rect;
import com.flatts.recompile.gui.ScreenLayout;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The layout algebra, which is pure logic and therefore belongs here rather than in a GameTest.
 *
 * <p>No world, no rendering and no server, so a GameTest would be the wrong instrument and a much slower
 * one. What a GameTest still owns is whether the four real screens agree with their four real menus -
 * that needs the menus constructed against a player inventory, and it lives in {@code MenuLayoutTests}.
 */
class ScreenLayoutTest {

    private static ScreenLayout.Builder panel() {
        return ScreenLayout.builder(GuiTheme.PANEL_W, GuiTheme.PANEL_H);
    }

    @Test
    void a_row_steps_by_vanillas_slot_pitch() {
        ScreenLayout layout = panel().slotRow("fuel", 5, 43, 30).build();
        List<Rect> cells = layout.group("fuel").cells();
        assertEquals(5, cells.size());
        assertEquals(new Rect(43, 30, 16, 16), cells.get(0));
        assertEquals(new Rect(43 + 4 * GuiTheme.SLOT_PITCH, 30, 16, 16), cells.get(4));
    }

    @Test
    void a_grid_fills_left_to_right_then_top_to_bottom() {
        // Vanilla's crafting grid, whose reading order is the same as its slot indices - getting this
        // backwards would place every recipe transposed and no test of the recipe itself would notice.
        ScreenLayout layout = panel().slotGrid("crafting", 3, 3, 30, 17).build();
        List<Rect> cells = layout.group("crafting").cells();
        assertEquals(9, cells.size());
        assertEquals(new Rect(30, 17, 16, 16), cells.get(0));
        assertEquals(new Rect(30 + 2 * 18, 17, 16, 16), cells.get(2));
        assertEquals(new Rect(30, 17 + 18, 16, 16), cells.get(3));
        assertEquals(new Rect(30 + 2 * 18, 17 + 2 * 18, 16, 16), cells.get(8));
    }

    @Test
    void the_player_inventory_follows_vanillas_index_order() {
        ScreenLayout layout = panel().playerInventory(84).build();
        List<int[]> placed = new ArrayList<>();
        layout.forEachPlayerSlot((index, x, y) -> placed.add(new int[] {index, x, y}));

        assertEquals(36, placed.size());
        // The backpack comes first and carries inventory indices 9 through 35...
        assertEquals(9, placed.get(0)[0]);
        assertEquals(8, placed.get(0)[1]);
        assertEquals(84, placed.get(0)[2]);
        assertEquals(35, placed.get(26)[0]);
        // ...and the hotbar is 0 through 8, on its own row four pixels below the backpack. Writing these
        // out in visual order instead is the classic hand-rolled-menu bug: the slots draw in the right
        // places and point at the wrong items.
        assertEquals(0, placed.get(27)[0]);
        assertEquals(84 + 3 * GuiTheme.SLOT_PITCH + GuiTheme.HOTBAR_GAP, placed.get(27)[2]);
        assertEquals(8, placed.get(35)[0]);
    }

    @Test
    void the_inventory_label_rises_with_the_inventory() {
        // A taller panel moves its label without anybody remembering to, which is the point of deriving
        // it: three of the four screens used to inherit a vanilla default that happened to agree.
        assertEquals(72, panel().playerInventory(84).build().inventoryLabelY());
        assertEquals(90, ScreenLayout.builder(GuiTheme.PANEL_W, 184)
            .playerInventory(102).build().inventoryLabelY());
    }

    @Test
    void a_vertical_run_answers_for_the_row_after_its_last() {
        // The connected-storage shelf places its tail line ("+6 more") directly under however many rows
        // it actually drew, which can be any number up to the reserve. A list genuinely has a next row.
        ScreenLayout layout = panel().rows("shelf", 5, 182, 32, 80, 16, 20).build();
        assertEquals(new Rect(182, 32, 80, 16), layout.rect("shelf", 0));
        assertEquals(new Rect(182, 32 + 4 * 20, 80, 16), layout.rect("shelf", 4));
        assertEquals(new Rect(182, 32 + 5 * 20, 80, 16), layout.rect("shelf", 5));
    }

    @Test
    void nothing_else_answers_past_its_own_count() {
        // The extrapolation above is a property of vertical runs, not a hole in bounds checking. A fuel
        // row that answered for a sixth slot would silently place it on a second row that does not exist.
        ScreenLayout layout = panel().slotRow("fuel", 5, 43, 30).slot("meter", 8, 17).build();
        assertThrows(IndexOutOfBoundsException.class, () -> layout.rect("fuel", 5));
        assertThrows(IndexOutOfBoundsException.class, () -> layout.rect("meter", 1));
        assertThrows(IndexOutOfBoundsException.class, () -> layout.rect("fuel", -1));
    }

    @Test
    void a_duplicate_or_missing_name_fails_loudly() {
        assertThrows(IllegalArgumentException.class,
            () -> panel().slot("input", 1, 1).slot("input", 2, 2).build());
        ScreenLayout layout = panel().slot("input", 1, 1).build();
        assertThrows(IllegalArgumentException.class, () -> layout.rect("inupt"));
        assertFalse(layout.has("inupt"));
        assertTrue(layout.has("input"));
    }

    @Test
    void asking_a_multi_cell_group_for_its_only_rect_fails() {
        ScreenLayout layout = panel().slotRow("fuel", 5, 43, 30).build();
        assertThrows(IllegalStateException.class, () -> layout.rect("fuel"));
    }

    @Test
    void suppressing_chrome_covers_both_halves_of_the_player_inventory() {
        // playerInventory adds two groups. Suppressing only the hotbar would draw twenty-seven slot
        // sprites over a background that already had them and leave nine that matched - a half-applied
        // fix that looks deliberate.
        ScreenLayout layout = panel().playerInventory(84).noChrome().build();
        for (ScreenLayout.Group group : layout.groups()) {
            if (group.kind() == ScreenLayout.Kind.SLOT) {
                assertFalse(group.hasChrome(), group.name() + " still draws chrome");
            }
        }
    }

    @Test
    void both_text_labels_are_declared_so_the_overlap_sweep_can_see_them() {
        // Vanilla draws these from fields rather than from anything a layout can see, so it is easy to
        // forget they occupy space - and the Burner Generator's original defect was a piece of text drawn
        // through something else.
        ScreenLayout layout = panel().playerInventory(84).build();
        assertTrue(layout.has("title"));
        assertTrue(layout.has("inventory_label"));
        assertEquals(new Rect(GuiTheme.TITLE_X, GuiTheme.TITLE_Y, GuiTheme.LABEL_W, 9),
            layout.rect("title"));
        assertEquals(72, layout.rect("inventory_label").y());
    }

    @Test
    void a_layout_with_no_player_inventory_declares_no_inventory_label() {
        // Not every layout has to carry one, and declaring a label for an inventory that is not there
        // would put a phantom rectangle into the overlap sweep.
        ScreenLayout layout = panel().slot("input", 44, 35).build();
        assertFalse(layout.has("inventory_label"));
        assertTrue(layout.has("title"));
    }

    @Test
    void everything_reports_one_entry_per_cell() {
        ScreenLayout layout = panel().slotRow("fuel", 5, 43, 30).slot("meter", 8, 17).build();
        // 5 fuel + 1 meter + the title, which build() adds.
        assertEquals(7, layout.everything().size());
    }
}

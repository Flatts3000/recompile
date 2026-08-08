package com.flatts.recompile.client;

import com.flatts.recompile.client.gui.GuiPainter;
import com.flatts.recompile.client.gui.LayoutScreen;
import com.flatts.recompile.content.menu.ScrapCraftingStationMenu;
import com.flatts.recompile.content.menu.ScrapPanelInteraction;
import com.flatts.recompile.gui.GuiTheme;
import com.flatts.recompile.network.ScrapNetworkContentsPayload;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Scrap Crafting Table's screen (design P2.10 flow 4): the vanilla 3x3 crafting layout plus a
 * <b>connected-storage panel</b> down the right side - the Tinkers' Crafting Station affordance, so
 * opening the table shows what the whole network holds (bins <em>and</em> barrel), and that it is
 * connected.
 *
 * <p>The panel renders {@link ScrapCraftingStationMenu#contents()} verbatim - a snapshot the
 * <b>server</b> computes from the real bins + barrel and pushes each tick. The screen never inspects
 * block entities itself, so the panel cannot disagree with the world: no per-block client sync to drift,
 * no empty-bin / hidden-barrel gaps. <b>Click a material to withdraw</b> it (the row lights up on hover),
 * or <b>click the panel holding a stack to store it</b> into the network.
 *
 * <p>The shelf <b>scrolls</b> and a click takes <b>one</b> item, not a stack (shift for a stack, right
 * for half). Both were playtest bugs, both are issue #86: a barrel holds 27 stacks against five visible
 * rows, so a fixed list left most of a player's storage behind a "+6 more" label with no way to reach it,
 * and left-click handing over 64 Rebar when three were wanted meant walking the rest back. The quantity
 * and window arithmetic is in {@link ScrapPanelInteraction}, which is where its tests are.
 */
public class ScrapCraftingStationScreen extends LayoutScreen<ScrapCraftingStationMenu> {

    private static final Identifier CRAFTING_BG =
        Identifier.withDefaultNamespace("textures/gui/container/crafting_table.png");

    /**
     * First visible material row.
     *
     * <p>Client-only, and deliberately not on the menu: the server withdraws by item id rather than by
     * row, so where the view happens to be sitting is nobody else's business. The arithmetic that keeps
     * it in range lives in {@link ScrapPanelInteraction} where a unit test can reach it.
     */
    private int scroll;

    public ScrapCraftingStationScreen(ScrapCraftingStationMenu menu, Inventory inventory,
            Component title) {
        super(menu, inventory, title, ScrapCraftingStationMenu.LAYOUT);
    }

    @Override
    protected void paint(GuiPainter painter, int mouseX, int mouseY) {
        painter.background("crafting_bg", CRAFTING_BG);
        painter.slab("shelf", GuiTheme.SIDE_PANEL_BODY);

        // Why the result slot is empty, when the reason is one the player can act on.
        if (this.menu.needsBlueprint()) {
            painter.wrapped("needs_blueprint",
                Component.translatable("container.recompile.needs_blueprint"), GuiTheme.TEXT_WARN);
        }

        painter.wrapped("shelf_title", Component.translatable("container.recompile.connected"),
            GuiTheme.TEXT_BRIGHT);

        ScrapNetworkContentsPayload contents = this.menu.contents();
        if (contents.binCount() == 0 && !contents.hasBarrel()) {
            painter.wrapped("shelf_summary",
                Component.translatable("container.recompile.not_connected"), GuiTheme.TEXT_MUTED);
            return;
        }

        // Summary: what is wired in (bins + barrel), independent of whether it holds anything.
        String summary = "";
        if (contents.binCount() > 0) {
            summary = Component.translatable("container.recompile.bin_count",
                contents.binCount()).getString();
        }
        if (contents.hasBarrel()) {
            summary += (summary.isEmpty() ? "" : " ")
                + Component.translatable("container.recompile.plus_barrel").getString();
        }
        painter.wrapped("shelf_summary", Component.literal(summary), GuiTheme.TEXT_DIM);

        // The material shelf: every item available across the network, merged by item.
        var materials = contents.materials();
        // Re-clamp every frame: the list is a server snapshot that changes under us, so an offset that
        // was valid last tick can be past the end of this one.
        this.scroll = ScrapPanelInteraction.clampScroll(this.scroll, materials.size(), rows());
        int shown = shown(materials.size());
        for (int i = 0; i < shown; i++) {
            ScrapNetworkContentsPayload.Material material = materials.get(this.scroll + i);
            if (painter.isOver("shelf_rows", i, mouseX, mouseY)) {
                painter.tintPadded("shelf_rows", i, 2, GuiTheme.HOVER_ROW);
            }
            painter.item("shelf_rows", i, new ItemStack(material.item()));
            painter.textIn("shelf_rows", i, 20, 4, Integer.toString(material.count()),
                GuiTheme.BEVEL_LIGHT);
        }

        // The tail line, directly under however many rows were actually drawn - which is what a vertical
        // run of rows can answer for even one step past its own count.
        if (materials.isEmpty()) {
            painter.wrapped("shelf_rows", shown,
                Component.translatable("container.recompile.bins_empty"), GuiTheme.TEXT_MUTED);
        } else {
            // How many are still BELOW the window, not how many the window omits. Counting the latter
            // would keep saying "+20 more" after you had scrolled to the last row, pointing down at
            // nothing - which is the same defect as the old dead arrow, just further along.
            int below = materials.size() - (this.scroll + shown);
            if (below > 0) {
                painter.wrapped("shelf_rows", shown,
                    Component.translatable("container.recompile.more_scroll", below),
                    GuiTheme.TEXT_MUTED);
            }
        }

        if (!this.menu.getCarried().isEmpty()) {
            painter.wrapped("store_hint",
                Component.translatable("container.recompile.store_hint"), GuiTheme.TEXT_GOOD);
        }
    }

    /**
     * Row hover: what it is, how much there is, and what each click takes.
     *
     * <p>The controls have to be said somewhere, because this panel deliberately does not follow
     * vanilla's left-takes-a-stack and a player who assumes vanilla is wrong with no way to find out.
     * They are <b>here</b> rather than printed on the panel because the panel is 92px wide - about
     * thirteen characters of usable width - and "Click 1, shift a stack, right-click half" is three
     * times that. Text that does not fit its box is how the Burner Generator's readout shipped drawn
     * through its own fuel row.
     */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (!this.menu.getCarried().isEmpty()) {
            return;   // holding a stack, the panel deposits rather than withdraws
        }
        var materials = this.menu.contents().materials();
        int row = overIndex("shelf_rows", shown(materials.size()), mouseX, mouseY);
        if (row < 0) {
            return;
        }
        ScrapNetworkContentsPayload.Material material = materials.get(this.scroll + row);
        graphics.setTooltipForNextFrame(this.font, List.of(
            new ItemStack(material.item()).getHoverName().copy().append(" x" + material.count()),
            Component.translatable("container.recompile.take_hint")
                .withStyle(ChatFormatting.GRAY)), Optional.empty(), mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isOver("shelf", mouseX, mouseY)) {
            this.scroll = ScrapPanelInteraction.clampScroll(
                this.scroll - (int) Math.signum(scrollY),
                this.menu.contents().materials().size(), rows());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean left = event.button() == 0;
        boolean right = event.button() == 1;
        if ((left || right) && this.minecraft != null && this.minecraft.gameMode != null
                && isOver("shelf", event.x(), event.y())) {
            // Holding a stack over the panel deposits it into the network (and stops vanilla from
            // dropping the cursor into the world, which a panel click would otherwise do).
            if (left && !this.menu.getCarried().isEmpty()) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                    ScrapCraftingStationMenu.DEPOSIT_BUTTON);
                return true;
            }
            // Empty cursor: a material row withdraws. The button id carries the item's registry id (so
            // the server withdraws that exact item, with no index-drift race if the list is a tick
            // stale) packed with how much this click asked for.
            ScrapPanelInteraction.Mode mode = right
                ? ScrapPanelInteraction.Mode.HALF
                // 26.1: modifiers ride on the input event (MouseButtonEvent implements
                // InputWithModifiers). The old static Screen.hasShiftDown() is gone, and reading global
                // key state would have been the wrong question anyway - what matters is the modifiers
                // held for THIS click.
                : (event.hasShiftDown() ? ScrapPanelInteraction.Mode.STACK
                                        : ScrapPanelInteraction.Mode.ONE);
            var materials = this.menu.contents().materials();
            int row = overIndex("shelf_rows", shown(materials.size()), event.x(), event.y());
            if (row >= 0) {
                int itemId = BuiltInRegistries.ITEM.getId(materials.get(this.scroll + row).item());
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                    ScrapPanelInteraction.encode(itemId, mode));
            }
            return true;   // consume other panel clicks so an empty cursor click does nothing here
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** How many rows the shelf reserved - the layout's arithmetic, not a second copy of it. */
    private int rows() {
        return layout().group("shelf_rows").count();
    }

    /** How many are actually on screen, which is fewer than {@link #rows()} on a short list. */
    private int shown(int total) {
        return Math.max(0, Math.min(total - this.scroll, rows()));
    }
}

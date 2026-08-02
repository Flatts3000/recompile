package com.flatts.recompile.client;

import com.flatts.recompile.content.menu.ScrapCraftingStationMenu;
import com.flatts.recompile.content.menu.ScrapPanelInteraction;
import com.flatts.recompile.network.ScrapNetworkContentsPayload;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
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
 * <b>server</b> computes from the real bins + barrel and pushes each tick (see
 * {@code ScrapNetworkContentsPayload}). The screen never inspects block entities itself, so the panel
 * cannot disagree with the world: no per-block client sync to drift, no empty-bin / hidden-barrel gaps.
 * <b>Click a material to withdraw</b> it (the row lights up on hover), or <b>click the panel holding a
 * stack to store it</b> into the network - the two halves of Tinkers-style interaction.
 *
 * <p>The shelf <b>scrolls</b> and a click takes <b>one</b> item, not a stack (shift for a stack, right
 * for half). Both were playtest bugs, both are issue #86: a barrel holds 27 stacks against seven visible
 * rows, so the old fixed list left most of a player's storage behind a "+6 more" label with no way to
 * reach it, and left-click handing over 64 Rebar when three were wanted meant walking the rest back. The
 * quantity and window arithmetic is in {@link ScrapPanelInteraction}, which is where its tests are.
 *
 * <p>26.1 renders through the retained-mode "extract" model, so the drawing lives in
 * {@link #extractBackground} via {@link GuiGraphicsExtractor}, not a {@code renderBg(GuiGraphics)}.
 */
public class ScrapCraftingStationScreen extends AbstractContainerScreen<ScrapCraftingStationMenu> {

    private static final Identifier CRAFTING_BG =
        Identifier.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private static final int CRAFT_W = 176;
    private static final int CRAFT_H = 166;
    private static final int PANEL_W = 92;
    private static final int PANEL_PAD = 6;
    private static final int ROW_H = 20;
    /** Y offset (from the panel top) where the material shelf begins - below the title + summary. */
    private static final int SHELF_TOP = PANEL_PAD + 26;
    private static final int ICON = 16;

    /**
     * First visible material row.
     *
     * <p>Client-only, and deliberately not on the menu: the server withdraws by item id rather than by
     * row, so where the view happens to be sitting is nobody else's business. The arithmetic that keeps
     * it in range lives in {@link ScrapPanelInteraction} where a unit test can reach it.
     */
    private int scroll;

    public ScrapCraftingStationScreen(ScrapCraftingStationMenu menu, Inventory inventory, Component title) {
        // imageWidth/imageHeight are final in 26.1; the extra panel width is set via the super ctor.
        super(menu, inventory, title, CRAFT_W + PANEL_W, CRAFT_H);
    }

    @Override
    protected void init() {
        super.init();
        // Match the vanilla crafting table's label placement (its title sits over the grid).
        this.titleLabelX = 29;
        this.inventoryLabelX = 8;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int left = this.leftPos;
        int top = this.topPos;
        // The vanilla crafting table GUI on the left.
        graphics.blit(RenderPipelines.GUI_TEXTURED, CRAFTING_BG, left, top, 0.0F, 0.0F, CRAFT_W, CRAFT_H, 256, 256);

        // The connected-storage panel on the right.
        int panelX = left + CRAFT_W;
        graphics.fill(panelX, top, panelX + PANEL_W, top + CRAFT_H, 0xFF3A3A3A);               // body
        graphics.fill(panelX, top, panelX + PANEL_W, top + 1, 0xFF202020);                     // top edge
        graphics.fill(panelX, top + CRAFT_H - 1, panelX + PANEL_W, top + CRAFT_H, 0xFF202020);  // bottom edge
        wrapped(graphics, Component.translatable("container.recompile.connected"),
            panelX + PANEL_PAD, top + PANEL_PAD, 0xFFD0D0D0);

        ScrapNetworkContentsPayload contents = this.menu.contents();
        if (contents.binCount() == 0 && !contents.hasBarrel()) {
            wrapped(graphics, Component.translatable("container.recompile.not_connected"),
                panelX + PANEL_PAD, top + PANEL_PAD + 12, 0xFF808080);
            return;
        }

        // Summary: what is wired in (bins + barrel), independent of whether it holds anything.
        String summary = "";
        if (contents.binCount() > 0) {
            summary = Component.translatable("container.recompile.bin_count", contents.binCount()).getString();
        }
        if (contents.hasBarrel()) {
            summary += (summary.isEmpty() ? "" : " ")
                + Component.translatable("container.recompile.plus_barrel").getString();
        }
        wrapped(graphics, Component.literal(summary),
            panelX + PANEL_PAD, top + PANEL_PAD + 12, 0xFF9AA0A6);

        // The material shelf: every item available across the network (bins + barrel), merged by item.
        // Click a row to withdraw (handled in mouseClicked); the hovered row lights up.
        var materials = contents.materials();
        int rows = maxRows();
        // Re-clamp every frame: the list is a server snapshot that changes under us, so an offset that
        // was valid last tick can be past the end of this one.
        this.scroll = ScrapPanelInteraction.clampScroll(this.scroll, materials.size(), rows);
        int shown = Math.min(materials.size() - this.scroll, rows);
        for (int i = 0; i < shown; i++) {
            ScrapNetworkContentsPayload.Material material = materials.get(this.scroll + i);
            int rowY = top + SHELF_TOP + i * ROW_H;
            if (overRow(panelX, top, i, mouseX, mouseY)) {
                graphics.fill(panelX + PANEL_PAD - 2, rowY - 2,
                    panelX + PANEL_W - PANEL_PAD + 2, rowY + ICON + 1, 0x40FFFFFF);
            }
            graphics.item(new ItemStack(material.item()), panelX + PANEL_PAD, rowY);
            graphics.text(this.font, Integer.toString(material.count()),
                panelX + PANEL_PAD + 20, rowY + 4, 0xFFFFFFFF, false);
        }
        int tailY = top + SHELF_TOP + shown * ROW_H;
        if (materials.isEmpty()) {
            wrapped(graphics, Component.translatable("container.recompile.bins_empty"),
                panelX + PANEL_PAD, tailY, 0xFF808080);
        } else {
            // How many are still BELOW the window, not how many the window omits. Counting the latter
            // would keep saying "+20 more" after you had scrolled to the last row, pointing down at
            // nothing - which is the same defect as the old dead arrow, just further along.
            int below = materials.size() - (this.scroll + shown);
            if (below > 0) {
                wrapped(graphics, Component.translatable("container.recompile.more_scroll", below),
                    panelX + PANEL_PAD, tailY, 0xFF808080);
            }
        }

        if (!this.menu.getCarried().isEmpty()) {
            graphics.text(this.font, Component.translatable("container.recompile.store_hint"),
                panelX + PANEL_PAD, top + CRAFT_H - PANEL_PAD - 8, 0xFF7FD07F);
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
        int panelX = this.leftPos + CRAFT_W;
        int shown = Math.min(materials.size() - this.scroll, maxRows());
        for (int i = 0; i < shown; i++) {
            if (!overRow(panelX, this.topPos, i, mouseX, mouseY)) {
                continue;
            }
            ScrapNetworkContentsPayload.Material material = materials.get(this.scroll + i);
            graphics.setTooltipForNextFrame(this.font, List.of(
                new ItemStack(material.item()).getHoverName().copy()
                    .append(" x" + material.count()),
                Component.translatable("container.recompile.take_hint")
                    .withStyle(ChatFormatting.GRAY)), Optional.empty(), mouseX, mouseY);
            return;
        }
    }

    private int maxRows() {
        return (CRAFT_H - SHELF_TOP - PANEL_PAD) / ROW_H;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (overPanel(mouseX, mouseY)) {
            int total = this.menu.contents().materials().size();
            this.scroll = ScrapPanelInteraction.clampScroll(
                this.scroll - (int) Math.signum(scrollY), total, maxRows());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Whether the mouse is anywhere over the connected-storage panel. */
    private boolean overPanel(double mouseX, double mouseY) {
        int panelX = this.leftPos + CRAFT_W;
        return mouseX >= panelX && mouseX < panelX + PANEL_W
            && mouseY >= this.topPos && mouseY < this.topPos + CRAFT_H;
    }

    /** Whether the mouse is over material row {@code i} in the panel (the clickable strip). */
    private boolean overRow(int panelX, int top, int i, double mouseX, double mouseY) {
        int rowY = top + SHELF_TOP + i * ROW_H;
        return mouseX >= panelX + PANEL_PAD && mouseX < panelX + PANEL_W - PANEL_PAD
            && mouseY >= rowY && mouseY < rowY + ICON;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean left = event.button() == 0;
        boolean right = event.button() == 1;
        if ((left || right) && this.minecraft != null && this.minecraft.gameMode != null
                && overPanel(event.x(), event.y())) {
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
            int panelX = this.leftPos + CRAFT_W;
            int shown = Math.min(materials.size() - this.scroll, maxRows());
            for (int i = 0; i < shown; i++) {
                if (overRow(panelX, this.topPos, i, event.x(), event.y())) {
                    int itemId = BuiltInRegistries.ITEM.getId(materials.get(this.scroll + i).item());
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                        ScrapPanelInteraction.encode(itemId, mode));
                    return true;
                }
            }
            return true;   // consume other panel clicks so an empty cursor click does nothing here
        }
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * Draw panel prose inside the panel, wrapping rather than running off the edge.
     *
     * <p>The panel is {@value #PANEL_W} wide and every string here is written by hand or comes from a
     * lang file, so none of them can be assumed short: "No storage connected" is nearly twice the
     * usable width and was drawn straight through the panel's right edge and across the world behind
     * it. A translation into a longer language would do the same to any of the others, which is why
     * this wraps everything in the panel rather than only the one that was reported.
     */
    private void wrapped(GuiGraphicsExtractor graphics, Component text, int x, int y, int colour) {
        int width = PANEL_W - PANEL_PAD * 2;
        int line = y;
        for (net.minecraft.util.FormattedCharSequence part : this.font.split(text, width)) {
            graphics.text(this.font, part, x, line, colour, false);
            line += this.font.lineHeight;
        }
    }

}

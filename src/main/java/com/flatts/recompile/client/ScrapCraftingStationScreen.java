package com.flatts.recompile.client;

import com.flatts.recompile.content.menu.ScrapCraftingStationMenu;
import com.flatts.recompile.network.ScrapNetworkContentsPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * Read-only in v1 (deposit/withdraw stay on the file-all and hopper-in).
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
        graphics.text(this.font, Component.translatable("container.recompile.connected"),
            panelX + PANEL_PAD, top + PANEL_PAD, 0xFFD0D0D0);

        ScrapNetworkContentsPayload contents = this.menu.contents();
        if (contents.binCount() == 0 && !contents.hasBarrel()) {
            graphics.text(this.font, Component.translatable("container.recompile.not_connected"),
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
        graphics.text(this.font, summary, panelX + PANEL_PAD, top + PANEL_PAD + 12, 0xFF9AA0A6, false);

        // The material shelf: every item available across the network (bins + barrel), merged by item.
        var materials = contents.materials();
        int rowY = top + PANEL_PAD + 26;
        int maxRows = (CRAFT_H - (PANEL_PAD + 26) - PANEL_PAD) / ROW_H;
        int shown = Math.min(materials.size(), maxRows);
        for (int i = 0; i < shown; i++) {
            ScrapNetworkContentsPayload.Material material = materials.get(i);
            graphics.item(new ItemStack(material.item()), panelX + PANEL_PAD, rowY);
            graphics.text(this.font, Integer.toString(material.count()),
                panelX + PANEL_PAD + 20, rowY + 4, 0xFFFFFFFF, false);
            rowY += ROW_H;
        }
        if (materials.isEmpty()) {
            graphics.text(this.font, Component.translatable("container.recompile.bins_empty"),
                panelX + PANEL_PAD, rowY, 0xFF808080);
        } else if (materials.size() > shown) {
            graphics.text(this.font,
                Component.translatable("container.recompile.more", materials.size() - shown).getString(),
                panelX + PANEL_PAD, rowY, 0xFF808080, false);
        }
    }
}

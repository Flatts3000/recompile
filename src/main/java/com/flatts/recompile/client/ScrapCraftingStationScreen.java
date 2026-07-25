package com.flatts.recompile.client;

import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import com.flatts.recompile.content.menu.ScrapCraftingStationMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Scrap Crafting Table's screen (design P2.10 flow 4): the vanilla 3x3 crafting layout plus a
 * <b>connected-storage panel</b> down the right side, listing the bins wired into the scrap network
 * with their material and count - the Tinkers' Crafting Station affordance, so opening the table shows
 * what the network holds (and, implicitly, that it is connected).
 *
 * <p>The panel is read-only in v1 (a material shelf you can see; deposit/withdraw stay on the file-all
 * and hopper-in). It reads {@link ScrapCraftingStationMenu#connectedBins()} live - the bins sync their
 * material + amount to the client - so it updates as a shift-craft drains them.
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

    /** The connected bins + barrel, refreshed once per tick (not per render frame) - see containerTick. */
    private List<ScrapBinBlockEntity> bins = List.of();
    private boolean hasBarrel = false;

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
        refreshNetwork();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Re-flood the network once per tick rather than every render frame - the panel stays live
        // (a shift-craft's drain shows up within a tick) without a per-frame BFS.
        refreshNetwork();
    }

    private void refreshNetwork() {
        this.bins = this.menu.connectedBins();
        this.hasBarrel = this.menu.hasConnectedBarrel();
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

        int rowY = top + PANEL_PAD + 12;
        int shown = 0;
        int maxRows = (CRAFT_H - (PANEL_PAD + 12) - PANEL_PAD) / ROW_H;
        for (ScrapBinBlockEntity bin : this.bins) {
            if (shown >= maxRows) {
                break;
            }
            if (bin.boundMaterial() == null || bin.amount() <= 0) {
                continue;   // an empty bin has nothing to show on the shelf
            }
            graphics.item(new ItemStack(bin.boundMaterial()), panelX + PANEL_PAD, rowY);
            graphics.text(this.font, Integer.toString(bin.amount()),
                panelX + PANEL_PAD + 20, rowY + 4, 0xFFFFFFFF, false);
            rowY += ROW_H;
            shown++;
        }
        if (shown == 0) {
            graphics.text(this.font, Component.translatable("container.recompile.not_connected"),
                panelX + PANEL_PAD, rowY, 0xFF808080);
        } else if (this.hasBarrel) {
            graphics.text(this.font, Component.translatable("container.recompile.plus_barrel"),
                panelX + PANEL_PAD, top + CRAFT_H - PANEL_PAD - 8, 0xFF808080);
        }
    }
}

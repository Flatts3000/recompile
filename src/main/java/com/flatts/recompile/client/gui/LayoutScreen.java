package com.flatts.recompile.client.gui;

import com.flatts.recompile.gui.ScreenLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * A container screen driven by a {@link ScreenLayout}, which is the only thing a subclass has to supply.
 *
 * <p>This absorbs the three costs that repeated on every screen this mod built by hand:
 *
 * <ul>
 *   <li><b>The 26.1 render model, learned once.</b> Drawing goes through {@code GuiGraphicsExtractor} in
 *       {@code extractBackground} rather than a {@code renderBg(GuiGraphics)}; {@code blit} takes an
 *       explicit {@code RenderPipelines} pipeline and explicit atlas dimensions; {@code imageWidth} and
 *       {@code imageHeight} are final and pass through a five-argument {@code super(...)}. None of it is
 *       guessable, all of it was identical per screen, and no subclass sees any of it now.
 *   <li><b>Label placement.</b> Every screen set some subset of the four label fields in {@code init},
 *       and the ones it did not set inherited a vanilla default that happened to agree. The layout owns
 *       all four, so a taller panel moves its "Inventory" label without anybody remembering to.
 *   <li><b>The slot-drawing loop.</b> Gone entirely - see {@link GuiPainter#drawChrome()}.
 *   </ul>
 *
 * <p>What a subclass writes is {@link #paint}: the dynamic half, addressed by name.
 */
public abstract class LayoutScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {

    private final ScreenLayout layout;

    protected LayoutScreen(M menu, Inventory inventory, Component title, ScreenLayout layout) {
        // imageWidth/imageHeight are final in 26.1, so the layout has to be able to answer for its own
        // size before this object exists. That is why a layout is a pure declaration built into a static.
        super(menu, inventory, title, layout.width(), layout.height());
        this.layout = layout;
    }

    protected final ScreenLayout layout() {
        return this.layout;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = this.layout.titleX();
        this.titleLabelY = this.layout.titleY();
        this.inventoryLabelX = this.layout.inventoryLabelX();
        this.inventoryLabelY = this.layout.inventoryLabelY();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float alpha) {
        super.extractBackground(graphics, mouseX, mouseY, alpha);
        GuiPainter painter = painter(graphics);
        painter.drawChrome();
        paint(painter, mouseX, mouseY);
    }

    /**
     * Everything this screen draws that the layout cannot know: gauge fills, progress, live text, items.
     *
     * <p>Called with the chrome already drawn, so a subclass never places a slot or a panel.
     */
    protected abstract void paint(GuiPainter painter, int mouseX, int mouseY);

    /**
     * A painter bound to this screen's current origin.
     *
     * <p>Needed separately for the tooltip pass, which vanilla runs through its own method with its own
     * graphics object. Cheap enough to build per call - it holds four ints and three references.
     */
    protected final GuiPainter painter(GuiGraphicsExtractor graphics) {
        return new GuiPainter(graphics, this.layout, this.font, this.leftPos, this.topPos);
    }

    /** Whether the mouse is over a named region, for the hit tests a subclass runs outside of painting. */
    protected final boolean isOver(String group, double mouseX, double mouseY) {
        return this.layout.rect(group).offset(this.leftPos, this.topPos).contains(mouseX, mouseY);
    }

    /** Which cell of a group the mouse is over, or {@code -1}. */
    protected final int overIndex(String group, int limit, double mouseX, double mouseY) {
        for (int i = 0; i < limit; i++) {
            if (this.layout.rect(group, i).offset(this.leftPos, this.topPos).contains(mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }
}

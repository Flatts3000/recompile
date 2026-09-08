package com.flatts.recompile.client.gui;

import com.flatts.recompile.gui.ScreenLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
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

    // The hit tests a subclass runs outside of painting - a tooltip pass, a click. Both delegate to the
    // layout rather than repeating the arithmetic, for the same reason the layout exists at all.

    /** Whether the mouse is over a named region. */
    protected final boolean isOver(String group, double mouseX, double mouseY) {
        return this.layout.contains(group, this.leftPos, this.topPos, mouseX, mouseY);
    }

    /** Which cell of a group the mouse is over, or {@code -1}. */
    protected final int overIndex(String group, int limit, double mouseX, double mouseY) {
        return this.layout.indexAt(group, limit, this.leftPos, this.topPos, mouseX, mouseY);
    }

    /**
     * Press a control: click sound, then the button id to the server.
     *
     * <p><b>Every clickable thing in this mod was silent, and that is a framework gap rather than five
     * oversights.</b> Vanilla plays {@code UI_BUTTON_CLICK} from {@code AbstractWidget.playDownSound},
     * so every button in the game answers a click - but these screens do not use widgets. They hit-test
     * a named layout region and call {@code handleInventoryButtonClick} by hand, which is the right
     * shape (a region is not a widget, and the id travels as vanilla's Stonecutter/Loom VAR_INT) and
     * silently skips the half of a button press that is feedback. Five screens wrote that same call and
     * all five omitted the same line.
     *
     * <p>It matters most where the click is the whole point and the result is off-screen: Deploy on a
     * Depot puts a machine into the world behind the GUI, and Sell empties a grid whose numbers were
     * already zero. With no sound and nothing visibly moving, a press that worked and a press that
     * missed the region by a pixel look identical.
     *
     * <p>The sound plays client-side on the press rather than on the server's reply, which is what
     * vanilla does and is deliberate: a widget clicks under latency because the feedback belongs to the
     * input, not to the outcome. A refused press should not reach here at all - the caller checks first,
     * the way {@code BuyTerminalScreen} refuses a locked row before sending it.
     */
    protected final void press(int buttonId) {
        if (this.minecraft == null || this.minecraft.gameMode == null) {
            return;
        }
        this.minecraft.getSoundManager()
            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
    }
}

package com.flatts.recompile.content.menu;

import com.flatts.recompile.content.block.entity.FreightTerminalBlockEntity;
import com.flatts.recompile.content.freight.FreightManifest;
import com.flatts.recompile.gui.GuiTheme;
import com.flatts.recompile.gui.ScreenLayout;
import com.flatts.recompile.registry.RCMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Freight Terminal's menu: a manifest and a landing strip (#387).
 *
 * <p><b>The twelfth custom screen, and a recorded exception like the eleven before it.</b> No vanilla
 * screen shows a delivery manifest - a chest screen would show the goods and hide the only numbers
 * that matter, which is the same argument that earned the market its two terminals.
 *
 * <p><b>The requirements arrive in the open buffer; only progress is synced.</b> Requirements do not
 * change while the screen is open, so putting them in data slots would pay every tick for a constant
 * AND put a value that may reach 100,000 into a 16-bit channel. The delivered counts do move, and
 * they go as low/high pairs through {@link WideSync} for exactly that reason - the ceiling this repo
 * has already been bitten by twice.
 */
public class FreightTerminalMenu extends AbstractContainerMenu {

    /** One low/high pair per manifest line, plus the tier. */
    public static final int DATA_SIZE = FreightManifest.MAX_LINES * 2 + 1;

    public static final int TIER_INDEX = FreightManifest.MAX_LINES * 2;

    /** Manifest lines on screen at once; the rest scroll. See the note on the layout. */
    public static final int VISIBLE_LINES = 4;

    public static final ScreenLayout LAYOUT = ScreenLayout.builder(GuiTheme.PANEL_W, 230)
        .panel()
        // Which rung this is, above the lines. Without it the screen is a list of goods with no
        // indication that a ladder exists at all.
        .region("phase", 8, 17, 160, 10)
        // 16 HIGH ON AN 18 PITCH, FOUR OF THEM, AND EVERY ONE OF THOSE NUMBERS IS PINNED.
        //
        // An item icon is 16 pixels and this was 12 high on a 13 pitch, so consecutive rows
        // overlapped by three and the manifest read as one smudged column (owner, 2026-09-07).
        // 16 high on an 18 pitch is the Buy Terminal's shelf, and 16 is the floor.
        //
        // THE CEILING IS THE WINDOW, which the first fix walked into: six rows at 18 put the panel
        // at 258. Minecraft's auto GUI scale guarantees only 320x240 LOGICAL pixels,
        // AbstractContainerScreen centres with topPos = (240 - height) / 2 and never clamps, so a
        // 258 panel renders its title nine pixels above the top of the window and loses the bottom
        // of the hotbar. MenuLayoutTests cannot see it - it measures against the panel's own height.
        //
        // Six rows do not fit at ANY usable pitch. The budget is forced - 82 for the inventory
        // block, 34 to the label, 20 for the strip, 28 of header - which leaves the list about 108
        // pixels, and 108/6 is 18 only if the rest is free. It is not. So the screen shows FOUR and
        // scrolls, which is the Buy Terminal's answer to the same arithmetic. Every shipped phase
        // asks for two goods, so nothing scrolls today; MAX_LINES stays 6 so a pack can still ask
        // for six, and a hint under the last row says how many are hidden.
        //
        // THE PANEL IS 230 RATHER THAN 220 SO THAT HINT HAS SOMEWHERE TO GO. Four rows at 18 from
        // y=28 end at 98 and the strip began at 104, which left six pixels - less than the font's
        // nine - so the hint was crammed INSIDE the last row at dy=12 and drew across that row's
        // own name and the bottom of its item icon. Invisible in the shipped game, because every
        // phase asks for two goods and the hint never appears; a datapack with six lines shows it
        // immediately, which is how this was found. The extra ten pixels buy the hint its own band
        // at y=100 and are still twenty short of the 240 the window guarantees, which
        // no_panel_is_bigger_than_the_smallest_promised_window now pins.
        .rows("manifest", VISIBLE_LINES, 8, 28, 160, 16, 18)
        // ONE ROW rather than a 3x3. The strip is a landing pad the ticker empties, not storage, so
        // giving it the footprint of a chest would suggest it holds things. Nine wide at
        // INVENTORY_X so it really does line up with the hotbar under it - it was at x=7 for one
        // review cycle, one pixel off the inventory it claimed to match, which no sweep catches
        // because the centring check only covers CELL groups.
        .slotRow("strip", FreightTerminalBlockEntity.SLOT_COUNT, GuiTheme.INVENTORY_X, 114)
        .playerInventory(148)
        .build();

    private static final int STRIP_END = FreightTerminalBlockEntity.SLOT_COUNT;

    private final Container container;
    private final ContainerData data;
    private final FreightManifest manifest;

    /** Dummy factory for the layout sweep and for a menu opened with no manifest to send. */
    public FreightTerminalMenu(int containerId, Inventory inventory) {
        this(containerId, inventory,
            new SimpleContainer(FreightTerminalBlockEntity.SLOT_COUNT),
            new net.minecraft.world.inventory.SimpleContainerData(DATA_SIZE),
            FreightManifest.NONE);
    }

    /** Client factory: the manifest arrives in the open buffer, the numbers in the data slots. */
    public FreightTerminalMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory,
            new SimpleContainer(FreightTerminalBlockEntity.SLOT_COUNT),
            new net.minecraft.world.inventory.SimpleContainerData(DATA_SIZE),
            FreightManifest.STREAM_CODEC.decode(buffer));
    }

    public FreightTerminalMenu(int containerId, Inventory inventory, Container container,
            ContainerData data, FreightManifest manifest) {
        super(RCMenus.FREIGHT_TERMINAL.get(), containerId);
        checkContainerSize(container, FreightTerminalBlockEntity.SLOT_COUNT);
        // Both counts, not just the container's. The Tree Nursery shipped a client-side data size as
        // a literal, the number drifted, and the mismatch surfaced as an IndexOutOfBoundsException on
        // the render thread rather than as a message naming both numbers.
        checkContainerDataCount(data, DATA_SIZE);
        this.container = container;
        this.data = data;
        this.manifest = manifest;

        LAYOUT.forEachSlot("strip", (index, x, y) ->
            this.addSlot(new Slot(container, index, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    // Defers to the container, so a player and a hopper cannot disagree about what
                    // the phase wants.
                    return container.canPlaceItem(index, stack);
                }
            }));
        LAYOUT.forEachPlayerSlot((index, x, y) -> this.addSlot(new Slot(inventory, index, x, y)));
        this.addDataSlots(data);
    }

    public FreightManifest manifest() {
        return manifest;
    }

    /** How much of manifest line {@code line} has been delivered. */
    public int delivered(int line) {
        if (line < 0 || line >= FreightManifest.MAX_LINES) {
            return 0;
        }
        return WideSync.combine(data.get(line * 2), data.get(line * 2 + 1));
    }

    /** Completed phases. Tier 0 means the first phase is in progress. */
    public int tier() {
        return data.get(TIER_INDEX);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < STRIP_END) {
            // Out of the strip and back to the player. Possible only in the tick before the drain
            // takes it, which is the same window the block's break-drop covers.
            if (!this.moveItemStackTo(stack, STRIP_END, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, STRIP_END, false)) {
            // Refused because the phase does not want it. Silent, but the strip visibly does not take
            // it, which is the same feedback a backed-up hopper gives.
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }
}

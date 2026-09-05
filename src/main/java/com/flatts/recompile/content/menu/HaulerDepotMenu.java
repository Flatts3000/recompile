package com.flatts.recompile.content.menu;

import com.flatts.recompile.content.block.entity.HaulerDepotBlockEntity;
import com.flatts.recompile.content.entity.ScrapHaulerEntity;
import com.flatts.recompile.content.item.ScrapHaulerItem;
import com.flatts.recompile.gui.GuiTheme;
import com.flatts.recompile.gui.ScreenLayout;
import com.flatts.recompile.registry.RCMenus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * The Hauler Depot's menu (#376): the Hauler slot, the hold, the FE gauge and the button.
 *
 * <p><b>The layout is declared once, here, in common code</b>, which is the GUI framework's point:
 * a server-side test measures it, and the screen draws from it rather than from numbers of its own.
 *
 * <p><b>Three of the conservation table's rows are this class.</b> The Hauler slot's {@code mayPickup}
 * is false while deployed; {@link #quickMoveStack} checks that guard explicitly, because vanilla's
 * shift-click path never consults a slot's pickup rule and is the classic bypass; and
 * {@link #clickMenuButton} hands the request to the block entity, which re-derives the state before
 * acting rather than trusting an integer that arrived on the wire.
 */
public class HaulerDepotMenu extends AbstractContainerMenu {

    public static final int DEPLOY_BUTTON = 0;
    public static final int RECALL_BUTTON = 1;

    /**
     * Taller than a furnace: a header for the Hauler slot, the button, a status line and the gauge,
     * then the hold as a chest's 9x3, then the player. The gauge is vertical because the painter's
     * gauge fills bottom-up, and a 9-wide grid leaves no room beside it, so the gauge lives in the
     * header band instead.
     */
    public static final ScreenLayout LAYOUT = ScreenLayout.builder(GuiTheme.PANEL_W, 206)
        .panel()
        .slot("hauler", 8, 26)
        .backdrop("deploy", 30, 17, 60, 18)
        .region("deploy_label", 36, 22, 48, 9)
        .region("status", 30, 40, 120, 9)
        .well("power", 154, 17, 14, 36)
        .slotGrid("cargo", 9, 3, 8, 56)
        .playerInventory(124)
        .build();

    private static final int HAULER = 0;
    private static final int CARGO_START = 1;
    private static final int CARGO_END = CARGO_START + HaulerDepotBlockEntity.CARGO_SLOTS;
    private static final int INV_START = CARGO_END;
    private static final int INV_MAIN_END = INV_START + 27;
    private static final int INV_END = INV_START + 36;

    private final Container container;
    private final ContainerData data;

    /** Client factory: dummy container + data, filled by the sync. */
    public HaulerDepotMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(HaulerDepotBlockEntity.SLOT_COUNT),
            new SimpleContainerData(HaulerDepotBlockEntity.DATA_SIZE));
    }

    public HaulerDepotMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
        super(RCMenus.HAULER_DEPOT.get(), containerId);
        checkContainerSize(container, HaulerDepotBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, HaulerDepotBlockEntity.DATA_SIZE);
        this.container = container;
        this.data = data;

        var hauler = LAYOUT.rect("hauler");
        this.addSlot(new Slot(container, HaulerDepotBlockEntity.HAULER_SLOT, hauler.x(), hauler.y()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return container.canPlaceItem(HaulerDepotBlockEntity.HAULER_SLOT, stack);
            }

            /** Ruling 13. The first conservation row. */
            @Override
            public boolean mayPickup(Player player) {
                return !deployed();
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        LAYOUT.forEachSlot("cargo", (index, x, y) ->
            this.addSlot(new Slot(container, HaulerDepotBlockEntity.CARGO_START + index, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return container.canPlaceItem(HaulerDepotBlockEntity.CARGO_START + index, stack);
                }
            }));
        LAYOUT.forEachPlayerSlot((index, x, y) -> this.addSlot(new Slot(inventory, index, x, y)));
        this.addDataSlots(data);
    }

    // ---- what the screen reads ----------------------------------------------------------------

    public int energy() {
        return data.get(HaulerDepotBlockEntity.DATA_ENERGY);
    }

    public int energyCapacity() {
        return HaulerDepotBlockEntity.CAPACITY;
    }

    public boolean deployed() {
        return data.get(HaulerDepotBlockEntity.DATA_DEPLOYED) != 0;
    }

    public boolean hasHauler() {
        return this.slots.get(HAULER).getItem().getItem() instanceof ScrapHaulerItem;
    }

    public int haulerCharge() {
        return data.get(HaulerDepotBlockEntity.DATA_HAULER_CHARGE);
    }

    public int haulerCapacity() {
        return ScrapHaulerItem.CAPACITY;
    }

    public int haulerCargo() {
        return data.get(HaulerDepotBlockEntity.DATA_CARGO);
    }

    /** The deployed Hauler's mode, or null when it is docked. */
    public ScrapHaulerEntity.@org.jspecify.annotations.Nullable Mode haulerMode() {
        int raw = data.get(HaulerDepotBlockEntity.DATA_MODE);
        return raw < 0 ? null : ScrapHaulerEntity.Mode.of(raw);
    }

    // ---- the button ----------------------------------------------------------------------------

    /**
     * Deploy or Recall, as ASKED, and only if the block's real state admits it.
     *
     * <p>Two ids rather than a toggle, deliberately. A toggle would make a laggy double-click deploy
     * and then immediately recall; with intent on the wire, a duplicated Deploy against a Hauler that
     * is already out is a no-op, and a stale Recall against one that is already home is too. The
     * block entity re-derives its preconditions either way - the sixth row of the conservation table.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(container instanceof HaulerDepotBlockEntity depot) || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        return switch (id) {
            case DEPLOY_BUTTON -> depot.deploy(level);
            case RECALL_BUTTON -> depot.recall(level);
            default -> false;
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    /**
     * Shift-click. The second conservation row: the Hauler slot is checked against its own pickup
     * guard here, because {@code doClick}'s QUICK_MOVE path never does.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < INV_START) {
            if (!this.moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof ScrapHaulerItem
                && container.canPlaceItem(HaulerDepotBlockEntity.HAULER_SLOT, stack)) {
            if (!this.moveItemStackTo(stack, HAULER, HAULER + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (container.canPlaceItem(HaulerDepotBlockEntity.CARGO_START, stack)) {
            if (!this.moveItemStackTo(stack, CARGO_START, CARGO_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < INV_MAIN_END) {
            if (!this.moveItemStackTo(stack, INV_MAIN_END, INV_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, INV_START, INV_MAIN_END, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}

package com.flatts.recompile.content.menu;

import com.flatts.recompile.content.block.entity.TreeNurseryBlockEntity;
import com.flatts.recompile.gui.GuiTheme;
import com.flatts.recompile.gui.Rect;
import com.flatts.recompile.gui.ScreenLayout;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Tree Nursery's menu (spec {@code docs/tree_nursery_spec.md}): the mod's <b>second</b> bespoke
 * screen (a recorded reversal), earned because species selection has no vanilla-screen analog and
 * cannot be an inserted-item template (saplings cannot be held as an input - that is the whole loot
 * strip). Three slots - Fertilizer in, Unknown Seedling in, sapling out - plus a species picker driven
 * through {@link #clickMenuButton} (the vanilla Stonecutter/Loom no-custom-packet path) and a
 * {@link ContainerData} feeding the water gauge and progress arrow.
 */
public class TreeNurseryMenu extends AbstractContainerMenu {

    public static final int SLOT_FERTILIZER = 0;
    public static final int SLOT_SEEDLING = 1;
    public static final int SLOT_OUTPUT = 2;
    private static final int MACHINE_SLOTS = 3;

    /**
     * Where everything on this screen is.
     *
     * <p>The panel is taller than vanilla's because the species picker sits between the machine row and
     * the player's inventory. The picker is a grid of {@code CELL}s rather than slots: it is drawn as
     * slots and clicked like slots, but nothing can be put in one - a species is chosen through
     * {@link #clickMenuButton}, and saplings cannot be held at all, which is the whole point of the loot
     * strip.
     *
     * <p>This layout is why the Tree Nursery is worth converting first: its screen declared
     * {@code FERT_X = 44} while this menu independently passed {@code 44} to a {@code Slot}, and the two
     * numbers had no connection beyond someone having typed them the same on one afternoon.
     */
    public static final ScreenLayout LAYOUT = ScreenLayout.builder(GuiTheme.PANEL_W, 184)
        .panel()
        .well("water", 8, 18, 8, 56)
        .slot("fertilizer", 44, 24)
        .slot("seedling", 62, 24)
        .slot("output", 116, 24)
        .arrow("cook", 84, 24)
        .region("countdown", 138, 30, 30, 9)
        // FIVE COLUMNS, NOT FOUR, so nine species still fit in two rows. Four columns held the eight
        // this picker shipped with exactly; the ninth started a third row at y=82 and landed on the
        // Inventory label at y=90. no_screen_element_overlaps_another is what said so - the geometry
        // is declared here and drawn elsewhere, and before the GUI framework nothing connected the two.
        //
        // AND x MOVES WITH IT, 52 -> 44. Widening a grid moves its right edge and nothing else, so
        // leaving the origin alone shoved the picker 8px - half a slot - off the panel's centre. No
        // test could see it: the geometry sweeps assert collisions and the panel bounds, and 52..140
        // satisfies both. 44 centres 88 pixels of grid in a 176 panel AND puts the first column under
        // the Fertilizer slot, which is where the eye expects it.
        .cellGrid("species", 5, TreeNurseryBlockEntity.SPECIES.length, 44, 46)
        .playerInventory(102)
        .build();
    private static final int INV_START = MACHINE_SLOTS;
    private static final int INV_MAIN_END = INV_START + 27;   // 3 rows of the backpack
    private static final int INV_END = INV_START + 36;        // + the hotbar

    private final Container container;
    private final ContainerData data;

    /** Client factory: a dummy container + data, filled by the sync (see {@link RCMenus}). */
    public TreeNurseryMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(MACHINE_SLOTS),
            new SimpleContainerData(TreeNurseryBlockEntity.DATA_SIZE));
    }

    public TreeNurseryMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
        super(RCMenus.TREE_NURSERY.get(), containerId);
        checkContainerSize(container, MACHINE_SLOTS);
        this.container = container;
        this.data = data;

        // Fertilizer + Unknown Seedling inputs, and the take-only sapling output.
        Rect fertilizer = LAYOUT.rect("fertilizer");
        this.addSlot(new Slot(container, SLOT_FERTILIZER, fertilizer.x(), fertilizer.y()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(RCItems.FERTILIZER.get());
            }
        });
        Rect seedling = LAYOUT.rect("seedling");
        this.addSlot(new Slot(container, SLOT_SEEDLING, seedling.x(), seedling.y()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(RCItems.UNKNOWN_SEEDLING.get());
            }
        });
        Rect output = LAYOUT.rect("output");
        this.addSlot(new Slot(container, SLOT_OUTPUT, output.x(), output.y()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // Player inventory - below the two-row species picker, which is why this panel is 184 tall.
        LAYOUT.forEachPlayerSlot((index, x, y) -> this.addSlot(new Slot(inventory, index, x, y)));

        this.addDataSlots(data);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    /**
     * The species picker: a button id in {@code [0, SPECIES.length)} selects that sapling. Server-side
     * the container IS the nursery BE, so set it there; the id travels as a VAR_INT, no custom packet.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0 && id < TreeNurseryBlockEntity.SPECIES.length
                && this.container instanceof TreeNurseryBlockEntity nursery) {
            nursery.setSelectedSpecies(id);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return moved;
        }
        ItemStack inSlot = slot.getItem();
        moved = inSlot.copy();
        if (index == SLOT_OUTPUT) {
            // Sapling out to the inventory; onQuickCraft so a full run empties in one shift-click.
            if (!this.moveItemStackTo(inSlot, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(inSlot, moved);
        } else if (index == SLOT_FERTILIZER || index == SLOT_SEEDLING) {
            if (!this.moveItemStackTo(inSlot, INV_START, INV_END, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // From the player inventory: route to the matching input, else shuffle backpack <-> hotbar.
            if (inSlot.is(RCItems.FERTILIZER.get())) {
                if (!this.moveItemStackTo(inSlot, SLOT_FERTILIZER, SLOT_FERTILIZER + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (inSlot.is(RCItems.UNKNOWN_SEEDLING.get())) {
                if (!this.moveItemStackTo(inSlot, SLOT_SEEDLING, SLOT_SEEDLING + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < INV_MAIN_END) {
                if (!this.moveItemStackTo(inSlot, INV_MAIN_END, INV_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(inSlot, INV_START, INV_MAIN_END, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (inSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (inSlot.getCount() == moved.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, inSlot);
        return moved;
    }

    // ---------------- screen readouts ----------------

    /** Thousandths of the current sapling, already scaled by the server. */
    public int cookProgress() {
        return this.data.get(TreeNurseryBlockEntity.DATA_COOK_PERMILLE);
    }

    /** Whole seconds left, computed server-side; see {@code TreeNurseryBlockEntity.secondsLeft}. */
    public int secondsLeft() {
        return this.data.get(TreeNurseryBlockEntity.DATA_COOK_SECONDS_LEFT);
    }

    /** What {@link #cookProgress()} is out of: a constant, because progress arrives pre-scaled. */
    public int cookTotal() {
        return WideSync.PERMILLE;
    }

    public int water() {
        return WideSync.combine(this.data.get(TreeNurseryBlockEntity.DATA_WATER_LOW),
            this.data.get(TreeNurseryBlockEntity.DATA_WATER_HIGH));
    }

    public int waterCapacity() {
        return WideSync.combine(this.data.get(TreeNurseryBlockEntity.DATA_WATER_CAP_LOW),
            this.data.get(TreeNurseryBlockEntity.DATA_WATER_CAP_HIGH));
    }

    public int selectedSpecies() {
        return this.data.get(TreeNurseryBlockEntity.DATA_SPECIES);
    }
}

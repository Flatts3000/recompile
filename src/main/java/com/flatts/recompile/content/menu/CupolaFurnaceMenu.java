package com.flatts.recompile.content.menu;

import com.flatts.recompile.content.block.entity.CupolaFurnaceBlockEntity;
import com.flatts.recompile.gui.GuiTheme;
import com.flatts.recompile.gui.ScreenLayout;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Cupola Furnace's menu: a furnace with a second output, for the slag (#236).
 *
 * <p><b>Vanilla's furnace menu cannot be subclassed into this.</b> {@code AbstractFurnaceMenu}'s
 * constructor calls {@code checkContainerSize(container, 3)} and then hard-codes three
 * {@code addSlot} calls at vanilla's coordinates, so a fourth slot is not a parameter it takes - it
 * throws on construction. This is the same wall {@code ScrapCraftingStationMenu} hit, where
 * {@code CraftingMenu} hard-locks itself to {@code MenuType.CRAFTING}, and the answer is the same:
 * reimplement the small amount of menu this machine needs over {@link AbstractContainerMenu}.
 *
 * <p><b>This is the mod's fifth custom screen and a recorded exception</b>, per the rule that every one
 * of them is a deliberate call written down rather than a habit. The reason it earns one: the byproduct
 * slot has no vanilla equivalent to borrow. Every vanilla cooking screen has exactly one output because
 * every vanilla cooking recipe has exactly one result - a furnace that hands back two things is outside
 * what the vanilla screens were built to show. The alternative was leaving slag to pop onto the floor,
 * which is what shipped first and what the owner rejected (2026-08-18): a machine that litters is not a
 * machine with an output.
 *
 * <p>The data is vanilla's own furnace {@code ContainerData} - lit time, lit duration, cooking progress,
 * cooking total - so the flame and the arrow behave exactly as a player expects. Nothing here reinvents
 * how a furnace looks; the only new thing on the screen is the extra slot.
 */
public class CupolaFurnaceMenu extends AbstractContainerMenu {

    /** Vanilla's furnace layout plus one: 0 input, 1 fuel, 2 result, 3 slag. */
    public static final int SLOTS = CupolaFurnaceBlockEntity.SLOTS;
    /** Vanilla's four furnace values: lit, lit total, cooking, cooking total. */
    public static final int DATA_SIZE = 4;

    /**
     * The screen's layout, declared once and read by both halves.
     *
     * <p>Vanilla's furnace geometry for the three slots a player already knows - input at (56,17), fuel
     * at (56,53), result at (116,35) - so the machine reads as a furnace at a glance and only the new
     * thing is new. The slag sits beside the result rather than under it: a byproduct is a second
     * output, not a lesser one, and stacking them would have put it where vanilla's screens put
     * nothing at all.
     */
    public static final ScreenLayout LAYOUT = ScreenLayout.builder(GuiTheme.PANEL_W, GuiTheme.PANEL_H)
        .panel()
        .slot("input", 56, 17)
        .slot("fuel", 56, 53)
        // A REGION, NOT A WELL. A well is bevelled exactly like a slot - same face, same size - so
        // declaring one between the input and the fuel drew a fourth slot as far as any player was
        // concerned, and playtest asked what it was for. Nothing draws a region; the screen paints
        // vanilla's flame into it.
        .region("flame", 56, 36, GuiTheme.FLAME_W, GuiTheme.FLAME_H)
        .arrow("cook", 79, 34)
        .slot("result", 116, 35)
        .slot("slag", 140, 35)
        .playerInventory(84)
        .build();


    /**
     * Where the player's inventory starts in this menu, for JEI's transfer handler (#240).
     *
     * <p><b>A constant rather than a literal in the plugin, because getting it wrong is silent.</b>
     * JEI's basic transfer overload takes raw slot indices; hand it an index one off and the "+" button
     * still appears and still moves items, into the wrong slots. Nothing throws, and the plugin is
     * client-only so no server-side test can read a number written there. Declared here, where
     * {@code menu_transfer_ranges_match_the_real_slots} can measure it against the menu it is built
     * from.
     */
    public static final int TRANSFER_INV_START = SLOTS;

    /** The 27 main inventory slots plus the 9 hotbar ones, which is what a transfer may draw from. */
    public static final int TRANSFER_INV_COUNT = 36;

    private static final int INV_START = SLOTS;
    private static final int INV_MAIN_END = INV_START + 27;
    private static final int INV_END = INV_START + 36;

    private final Container container;
    private final ContainerData data;

    /** Client factory: a dummy container and data, filled by the sync (see {@code RCMenus}). */
    public CupolaFurnaceMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(SLOTS), new SimpleContainerData(DATA_SIZE));
    }

    public CupolaFurnaceMenu(int containerId, Inventory inventory, Container container,
            ContainerData data) {
        super(RCMenus.CUPOLA_FURNACE.get(), containerId);
        checkContainerSize(container, SLOTS);
        checkContainerDataCount(data, DATA_SIZE);
        this.container = container;
        this.data = data;

        // Placed FROM the layout rather than from numbers of this class's own, which is the whole
        // point of the framework: the drawing and the hit boxes cannot disagree if there is only one of
        // them to read.
        LAYOUT.forEachSlot("input", (i, x, y) -> this.addSlot(new Slot(container, 0, x, y)));
        // A plain Slot, because vanilla's FurnaceFuelSlot takes an AbstractFurnaceMenu and this
        // deliberately is not one - but it copies what that class actually does, which the first
        // version did not.
        //
        // NOT container.canPlaceItem. That is the same client/server split isFuel exists to avoid: on
        // the client the container is this class's own SimpleContainer, which accepts everything, and
        // menu clicks are predicted locally before the server answers - so cobblestone would visibly
        // land in the fuel slot and snap back. I fixed that in quickMoveStack and left it standing
        // here, two methods from the javadoc explaining it.
        //
        // The bucket clause is vanilla's too: an empty bucket is allowed in so lava buckets leave one
        // behind, and it is capped at one so a stack of sixteen cannot be parked in the slot.
        LAYOUT.forEachSlot("fuel", (i, x, y) -> this.addSlot(new Slot(container, 1, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isFuel(inventory.player, stack) || stack.is(net.minecraft.world.item.Items.BUCKET);
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return stack.is(net.minecraft.world.item.Items.BUCKET) ? 1 : super.getMaxStackSize(stack);
            }
        }));
        LAYOUT.forEachSlot("result", (i, x, y) ->
            this.addSlot(new FurnaceResultSlot(inventory.player, container, 2, x, y)));
        // OUTPUT-ONLY, but NOT a FurnaceResultSlot. That class pops the furnace's banked smelting XP
        // and fires PlayerSmeltedEvent whenever it is taken from - so collecting slag would drain the
        // experience owed for the metal and report slag to every listener as something that was smelted.
        // A plain slot that refuses insertion gets the behaviour wanted and none of the side effects.
        LAYOUT.forEachSlot("slag", (i, x, y) -> this.addSlot(new Slot(container, 3, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        }));
        LAYOUT.forEachPlayerSlot((index, x, y) -> this.addSlot(new Slot(inventory, index, x, y)));
        this.addDataSlots(data);
    }

    /** Fraction of the current cook that is done, for the arrow. */
    public float cookProgress() {
        int total = this.data.get(3);
        int done = this.data.get(2);
        return total == 0 || done == 0 ? 0.0F : (float) done / total;
    }

    /** Fraction of the current fuel that is left, for the flame. */
    public float burnProgress() {
        int total = this.data.get(1);
        return total == 0 ? 0.0F : (float) this.data.get(0) / total;
    }

    public boolean isLit() {
        return this.data.get(0) > 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    /**
     * Shift-click routing, and the slag slot is the reason it is written out rather than borrowed.
     *
     * <p>Both outputs move OUT to the player and nothing may be shifted INTO either. Fuel goes to the
     * fuel slot, and anything else goes to the input - which is vanilla's behaviour with one extra
     * output to account for.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 2 || index == 3) {
            if (!this.moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
        } else if (index >= INV_START) {
            // VANILLA'S ORDER, AND VANILLA'S DEAD ENDS. Smeltable first, then fuel, and each branch
            // RETURNS on failure rather than falling through to the next.
            //
            // The first version tried fuel first and let it fall through, which meant that with a full
            // fuel slot a second stack of Oily Rags went into the INPUT - where nothing can smelt it and
            // the machine simply stops. Slot.mayPlace returns true unconditionally in 26.1, and the
            // recipe filter on canPlaceItemThroughFace only guards automation, so the GUI had nothing
            // stopping it. Any non-smeltable junk went the same way.
            if (canSmelt(player, stack)) {
                if (!this.moveItemStackTo(stack, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (isFuel(player, stack)) {
                if (!this.moveItemStackTo(stack, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < INV_MAIN_END) {
                if (!this.moveItemStackTo(stack, INV_MAIN_END, INV_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, INV_START, INV_MAIN_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, INV_START, INV_END, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    /**
     * Both tests answer the same on the client and the server, which is the point of taking them from
     * the level rather than from the container.
     *
     * <p>{@code container.canPlaceItem} looked right and is unusable here: on the client the container
     * is the {@code SimpleContainer} from the menu's own factory, whose {@code canPlaceItem} returns
     * true for everything. Shift-click is predicted locally before the server answers, so every stack
     * would be predicted into the fuel slot and then corrected - visible flicker on every click.
     * {@code fuelValues()} and the synced recipe property set are both present on both sides.
     */
    private static boolean isFuel(Player player, ItemStack stack) {
        return stack.getBurnTime(net.minecraft.world.item.crafting.RecipeType.BLASTING,
            player.level().fuelValues()) > 0;
    }

    private static boolean canSmelt(Player player, ItemStack stack) {
        return player.level().recipeAccess()
            .propertySet(net.minecraft.world.item.crafting.RecipePropertySet.BLAST_FURNACE_INPUT)
            .test(stack);
    }

    /** Only useful to a test: which block this menu belongs to. */
    public static net.minecraft.world.level.block.Block block() {
        return RCBlocks.CUPOLA_FURNACE.get();
    }
}

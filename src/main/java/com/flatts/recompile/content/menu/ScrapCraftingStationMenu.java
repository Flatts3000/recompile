package com.flatts.recompile.content.menu;

import com.flatts.recompile.content.block.ScrapNetwork;
import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import com.flatts.recompile.content.block.entity.ScrapCraftingTableBlockEntity;
import com.flatts.recompile.network.ScrapNetworkContentsPayload;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCMenus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The Scrap Crafting Table's menu: a 3x3 crafting station wired to the connected scrap network (design
 * P2.10 flow 4, the Tinkers' Crafting Station pattern). Two additions over a plain crafting table:
 *
 * <ul>
 *   <li><b>Craft from storage:</b> shift-clicking the result restocks the grid from the connected
 *       network (bins -> barrel -> inventory) between crafts, so one action crafts a whole run.</li>
 *   <li><b>A connected-storage panel:</b> the server computes what the network holds (bins + barrel,
 *       merged by item) in {@link #broadcastChanges()} and pushes it to the viewer as a
 *       {@code ScrapNetworkContentsPayload}; the screen renders {@link #contents()} verbatim, so the
 *       panel is a single server-owned source of truth and cannot drift from the world. That panel
 *       requires a custom screen, hence a custom {@code MenuType} - which is why this reimplements the
 *       crafting menu over {@link AbstractContainerMenu} rather than extending vanilla
 *       {@code CraftingMenu} (whose constructor hard-locks itself to {@code MenuType.CRAFTING}, so a
 *       subclass can never carry a custom screen).</li>
 * </ul>
 *
 * <p>The crafting logic (grid, result, recompute, quick-move) is vanilla's, copied from
 * {@code CraftingMenu}. The recipe-book button is dropped for v1 (it would need
 * {@code RecipeBookMenu} + its screen); every crafting recipe still works by hand.
 */
public class ScrapCraftingStationMenu extends AbstractContainerMenu {

    public static final int RESULT_SLOT = 0;
    private static final int GRID_START = 1;
    private static final int GRID_SIZE = 9;
    private static final int GRID_END = 10;
    private static final int INV_END = 46;

    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    private final ContainerLevelAccess access;
    private final Player player;
    private final Level level;
    private final BlockPos pos;
    /** Whether this menu checked out the table's persistent grid and so must save it back on close. */
    private boolean ownsTableGrid;

    /** Client factory: the block pos is streamed in the open buffer (see {@link RCMenus}). */
    public ScrapCraftingStationMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, inventory.player.level(), pos);
    }

    public ScrapCraftingStationMenu(int containerId, Inventory inventory, Level level, BlockPos pos) {
        super(RCMenus.SCRAP_CRAFTING_STATION.get(), containerId);
        this.access = ContainerLevelAccess.create(level, pos);
        this.player = inventory.player;
        this.level = level;
        this.pos = pos;

        this.addSlot(new ResultSlot(inventory.player, this.craftSlots, this.resultSlots, 0, 124, 35));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new Slot(this.craftSlots, col + row * 3, 30 + col * 18, 17 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }

        // Restore a grid left in the table - but only the first opener owns it (checks it out). A
        // concurrent second opener gets a plain transient grid and never persists, so it cannot wipe
        // the owner's grid. Server-side; the loaded grid syncs to the client with the rest of the menu.
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof ScrapCraftingTableBlockEntity table
                && table.tryCheckOut()) {
            table.loadInto(this.craftSlots);
            this.ownsTableGrid = true;
        }
    }

    // ---------------- crafting (copied from vanilla CraftingMenu) ----------------

    private static void slotChangedCraftingGrid(AbstractContainerMenu menu, Level level, Player player,
            CraftingContainer craftSlots, ResultContainer resultSlots, RecipeHolder<CraftingRecipe> last) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        CraftingInput input = craftSlots.asCraftInput();
        ItemStack result = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> recipe = level.getServer()
            .getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level, last);
        if (recipe.isPresent()) {
            RecipeHolder<CraftingRecipe> holder = recipe.get();
            resultSlots.setRecipeUsed(holder);
            ItemStack assembled = holder.value().assemble(input);
            if (assembled.isItemEnabled(level.enabledFeatures())) {
                result = assembled;
            }
        }
        resultSlots.setItem(0, result);
        menu.setRemoteSlot(0, result);
        serverPlayer.connection.send(
            new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), 0, result));
    }

    @Override
    public void slotsChanged(Container container) {
        this.access.execute((lvl, blockPos) ->
            slotChangedCraftingGrid(this, lvl, this.player, this.craftSlots, this.resultSlots, null));
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // SERVER ONLY. AbstractContainerScreen.removed() calls this on the CLIENT as well, every time the
        // screen is swapped - which is what JEI does to show a recipe. On the client ownsTableGrid is
        // always false, because it is only ever set in the server-side load path in the constructor, so
        // the else branch below would clearContainer and empty the CLIENT's copy of the grid. The items
        // vanish on screen while the server still holds them.
        //
        // The load path already guards this way; this is its missing counterpart. Container persistence
        // is server work, and any menu that mutates state in removed() needs the same guard.
        if (player.level().isClientSide()) {
            return;
        }
        this.access.execute((lvl, blockPos) -> {
            // Only the owner persists back into the table (and releases the check-out). A non-owner, or
            // an owner whose table is gone (broken while open), falls back to vanilla's give-back-or-drop
            // so nothing is lost and nothing is overwritten.
            if (this.ownsTableGrid
                    && lvl.getBlockEntity(blockPos) instanceof ScrapCraftingTableBlockEntity table) {
                table.saveFrom(this.craftSlots);
            } else {
                this.clearContainer(player, this.craftSlots);
            }
        });
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, RCBlocks.SCRAP_CRAFTING_TABLE.get());
    }

    /** Button id (never a valid item registry id, which are non-negative) meaning "deposit my cursor". */
    public static final int DEPOSIT_BUTTON = -1;

    /**
     * Panel interaction, via menu-button clicks so no custom packet is needed (the id travels as a
     * VAR_INT, so any item id fits). Two actions, both server-authoritative:
     *
     * <ul>
     *   <li><b>{@link #DEPOSIT_BUTTON}</b> - store the cursor stack into the network (matching bin, then
     *       an empty bin that binds, then the barrel), the same routing the file-all uses.</li>
     *   <li><b>an item's registry id</b> - withdraw a stack of that item out of the network.</li>
     * </ul>
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.level.isClientSide()) {
            return false;
        }
        if (id == DEPOSIT_BUTTON) {
            return depositCarried();
        }
        // The id comes off the wire (the panel sends an item's registry id); a malformed client could
        // send anything, so treat an unknown/air id as a no-op rather than trusting it.
        Item item = BuiltInRegistries.ITEM.byId(id);
        if (item == null || item == Items.AIR) {
            return false;
        }
        ItemStack pulled = withdrawStack(item);
        if (pulled.isEmpty()) {
            return false;
        }
        if (!player.getInventory().add(pulled)) {
            player.drop(pulled, false);
        }
        this.level.playSound(null, this.pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 1.2F);
        return true;
    }

    /** Store the cursor stack into the connected network (auto-binding an empty bin). Withdraw's mirror. */
    private boolean depositCarried() {
        ItemStack carried = this.getCarried().copy();
        if (carried.isEmpty()) {
            return false;
        }
        int before = carried.getCount();
        ScrapNetwork.insertFromMember(this.level, this.pos, carried, true);
        if (carried.getCount() == before) {
            return false;   // nothing accepted (no matching/empty bin and full/absent barrel)
        }
        this.setCarried(carried);
        this.level.playSound(null, this.pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 0.8F);
        return true;
    }

    /** Pull up to a stack of {@code item} from the network - the first matching bin, else a barrel slot. */
    private ItemStack withdrawStack(Item item) {
        List<BlockPos> members = ScrapNetwork.collect(level, pos);
        for (ScrapBinBlockEntity bin : ScrapNetwork.bins(level, members)) {
            if (bin.boundMaterial() == item && bin.amount() > 0) {
                return bin.withdraw(false);   // up to a stack
            }
        }
        int max = new ItemStack(item).getMaxStackSize();
        for (Container barrel : ScrapNetwork.barrels(level, members)) {
            for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                ItemStack stack = barrel.getItem(slot);
                if (stack.is(item)) {
                    ItemStack out = stack.split(Math.min(stack.getCount(), max));
                    barrel.setChanged();
                    return out;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    /**
     * Vanilla crafting quick-move, plus craft-from-storage: shift-clicking the result crafts, then
     * refills the emptied grid slots from the connected network. The quick-move loop keeps calling this
     * while the result slot still holds the same item, so a restocked grid crafts the whole run.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return moved;
        }
        ItemStack inSlot = slot.getItem();
        moved = inSlot.copy();
        // For a craft, snapshot the grid BEFORE the craft consumes it, so the refill (below, after
        // onTake has decremented the grid) knows what each emptied slot held.
        Item[] pattern = index == RESULT_SLOT ? capturePattern() : null;
        if (index == RESULT_SLOT) {
            inSlot.getItem().onCraftedBy(inSlot, player);
            if (!this.moveItemStackTo(inSlot, GRID_END, INV_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(inSlot, moved);
        } else if (index >= GRID_END && index < INV_END) {
            if (!this.moveItemStackTo(inSlot, GRID_START, GRID_END, false)) {
                if (index < 37) {
                    if (!this.moveItemStackTo(inSlot, 37, INV_END, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(inSlot, GRID_END, 37, false)) {
                    return ItemStack.EMPTY;
                }
            }
        } else if (!this.moveItemStackTo(inSlot, GRID_END, INV_END, false)) {
            return ItemStack.EMPTY;
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
        if (index == RESULT_SLOT) {
            player.drop(inSlot, false);
            // Now that onTake has emptied the crafted slots, restock them from the network - server
            // only, so the client never mutates its predicted bins. The result recomputes via the
            // grid's container callback, so the quick-move loop crafts the whole run in one click.
            if (!this.level.isClientSide() && pattern != null) {
                refillGrid(player, pattern);
            }
        }
        return moved;
    }

    // ---------------- craft-from-storage refill ----------------

    /** The item in each grid slot, so an emptied slot can be restocked with the same one. */
    private Item[] capturePattern() {
        Item[] pattern = new Item[GRID_SIZE];
        for (int i = 0; i < GRID_SIZE; i++) {
            ItemStack stack = this.slots.get(GRID_START + i).getItem();
            pattern[i] = stack.isEmpty() ? Items.AIR : stack.getItem();
        }
        return pattern;
    }

    /** Restock each emptied grid slot with its pattern item, from bins -> barrel -> inventory. */
    private void refillGrid(Player player, Item[] pattern) {
        List<BlockPos> members = ScrapNetwork.collect(level, pos);
        List<ScrapBinBlockEntity> bins = ScrapNetwork.bins(level, members);
        List<Container> barrels = ScrapNetwork.barrels(level, members);
        for (int i = 0; i < GRID_SIZE; i++) {
            Slot slot = this.slots.get(GRID_START + i);
            Item want = pattern[i];
            if (want == Items.AIR || !slot.getItem().isEmpty()) {
                continue;
            }
            ItemStack pulled = pullOne(want, bins, barrels, player);
            if (!pulled.isEmpty()) {
                slot.set(pulled);   // notifies TransientCraftingContainer -> result recomputes
            }
        }
    }

    private ItemStack pullOne(Item want, List<ScrapBinBlockEntity> bins, List<Container> barrels, Player player) {
        for (ScrapBinBlockEntity bin : bins) {
            if (bin.boundMaterial() == want && bin.amount() > 0) {
                ItemStack out = bin.withdraw(true);
                if (!out.isEmpty()) {
                    return out;
                }
            }
        }
        for (Container barrel : barrels) {
            for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                ItemStack stack = barrel.getItem(slot);
                if (stack.is(want)) {
                    ItemStack out = stack.split(1);
                    barrel.setChanged();
                    return out;
                }
            }
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(want)) {
                return stack.split(1);
            }
        }
        return ItemStack.EMPTY;
    }

    // ---------------- the connected-storage panel (server computes, client renders) ----------------

    /** Cap on distinct materials synced to the panel - a sane bound, and the panel shows a few rows. */
    private static final int MATERIAL_CAP = 18;

    /** Client-side: the last contents the server sent, rendered by the screen. */
    private ScrapNetworkContentsPayload contents = ScrapNetworkContentsPayload.EMPTY;
    /** Server-side: the last contents sent, so an unchanged network sends nothing. */
    @Nullable
    private ScrapNetworkContentsPayload lastSent;

    /** The contents the screen renders (client side). */
    public ScrapNetworkContentsPayload contents() {
        return this.contents;
    }

    /** Set by the payload handler when the server pushes a fresh snapshot. */
    public void setContents(ScrapNetworkContentsPayload payload) {
        this.contents = payload;
    }

    /**
     * Each tick the menu is open, recompute what the connected network holds and push it to the viewer
     * if it changed. The server owns the real bins + barrel, so this is the single source of truth for
     * the panel - the client renders it verbatim and can never disagree with the world.
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (this.level.isClientSide() || !(this.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ScrapNetworkContentsPayload now = computeContents();
        if (!now.equals(this.lastSent)) {
            this.lastSent = now;
            PacketDistributor.sendToPlayer(serverPlayer, now);
        }
    }

    /** Aggregate every item available across the connected bins and barrel, merged by item. */
    private ScrapNetworkContentsPayload computeContents() {
        List<BlockPos> members = ScrapNetwork.collect(level, pos);
        List<ScrapBinBlockEntity> bins = ScrapNetwork.bins(level, members);
        List<Container> barrels = ScrapNetwork.barrels(level, members);

        Map<Item, Integer> totals = new LinkedHashMap<>();
        for (ScrapBinBlockEntity bin : bins) {
            if (bin.boundMaterial() != null && bin.amount() > 0) {
                totals.merge(bin.boundMaterial(), bin.amount(), Integer::sum);
            }
        }
        for (Container barrel : barrels) {
            for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                ItemStack stack = barrel.getItem(slot);
                if (!stack.isEmpty()) {
                    totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
                }
            }
        }

        List<ScrapNetworkContentsPayload.Material> materials = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : totals.entrySet()) {
            if (materials.size() >= MATERIAL_CAP) {
                break;
            }
            materials.add(new ScrapNetworkContentsPayload.Material(entry.getKey(), entry.getValue()));
        }
        return new ScrapNetworkContentsPayload(bins.size(), !barrels.isEmpty(), materials);
    }

    // ---------------- test seams ----------------

    /** Test seam: the current grid pattern. */
    public Item[] capturePatternForTest() {
        return capturePattern();
    }

    /** Test seam: refill emptied grid slots from the network, as after a craft. */
    public void refillGridForTest(Player player, Item[] pattern) {
        refillGrid(player, pattern);
    }
}

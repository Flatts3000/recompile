package com.flatts.recompile.content.menu;

import com.flatts.recompile.content.block.ScrapNetwork;
import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import com.flatts.recompile.content.block.entity.ScrapCraftingTableBlockEntity;
import com.flatts.recompile.gui.GuiTheme;
import com.flatts.recompile.gui.Rect;
import com.flatts.recompile.gui.ScreenLayout;
import com.flatts.recompile.network.ScrapNetworkContentsPayload;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCMenus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.flatts.recompile.content.recipe.BlueprintAccess;
import com.flatts.recompile.content.recipe.BlueprintCraftingRecipe;
import com.flatts.recompile.content.recipe.SpawnEggCraftingRecipe;
import com.flatts.recompile.registry.RCRecipeTypes;
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
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import com.flatts.recompile.network.FillGridPayload;
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

    /**
     * Where everything on this screen is: vanilla's crafting table on the left, the connected-storage
     * shelf on the right.
     *
     * <p><b>This is the layout that proves the framework.</b> It is the odd one of the four - wider than
     * a vanilla panel, built on a reused vanilla background rather than a nine-slice, and carrying a
     * scrolling list. If it needed an escape hatch the API would not be ready to extract (issue #164,
     * acceptance criterion 4). It needs two verbs the simpler screens do not: {@code noChrome()}, because
     * {@code crafting_table.png} already draws its own slot wells, and {@code backdrop}, because a
     * surface other things sit on top of is not an overlap bug.
     *
     * <p>The shelf reserves {@code TAIL_H} below its last row for the tail line ("+6 more", or "(empty)"),
     * and two lines' worth of it, because that text wraps: the panel is 92 wide and "+6 more (scroll)"
     * does not fit on one. Without the reserve the shelf filled every row to the bottom edge and the tail
     * drew underneath the panel, over the world. The row count is derived from that arithmetic rather
     * than typed, so widening the panel cannot leave a stale number behind.
     */
    private static final int SHELF_W = 92;
    private static final int SHELF_PAD = 6;
    private static final int SHELF_TOP = SHELF_PAD + 26;
    private static final int ROW_PITCH = 20;
    private static final int TAIL_H = 20;
    private static final int SHELF_ROWS =
        (GuiTheme.PANEL_H - SHELF_TOP - SHELF_PAD - TAIL_H) / ROW_PITCH;
    private static final int SHELF_X = GuiTheme.PANEL_W + SHELF_PAD;
    private static final int SHELF_TEXT_W = SHELF_W - SHELF_PAD * 2;

    public static final ScreenLayout LAYOUT =
        ScreenLayout.builder(GuiTheme.PANEL_W + SHELF_W, GuiTheme.PANEL_H)
            .backdrop("crafting_bg", 0, 0, GuiTheme.PANEL_W, GuiTheme.PANEL_H)
            .backdrop("shelf", GuiTheme.PANEL_W, 0, SHELF_W, GuiTheme.PANEL_H)
            .slotGrid("crafting", 3, 3, 30, 17).noChrome()
            .slot("result", 124, 35).noChrome()
            .playerInventory(84).noChrome()
            // Beside the result rather than in a corner, because that is where the player is looking
            // when they are wondering why the result slot is empty.
            .region("needs_blueprint", 98, 52, 74, 27)
            .region("shelf_title", SHELF_X, SHELF_PAD, SHELF_TEXT_W, 9)
            .region("shelf_summary", SHELF_X, SHELF_PAD + 12, SHELF_TEXT_W, 9)
            .rows("shelf_rows", SHELF_ROWS, SHELF_X, SHELF_TOP, SHELF_TEXT_W,
                GuiTheme.SLOT_SIZE, ROW_PITCH)
            .region("store_hint", SHELF_X, GuiTheme.PANEL_H - SHELF_PAD - 8, SHELF_TEXT_W, 8)
            // The title sits over the grid, matching the vanilla crafting table it is drawn on.
            .title(29)
            .build();

    public static final int RESULT_SLOT = 0;
    private static final int GRID_START = 1;
    private static final int GRID_SIZE = 9;
    private static final int GRID_END = 10;
    private static final int INV_END = 46;

    /**
     * 1 when the grid matches a blueprint recipe the player cannot run, 0 otherwise.
     *
     * <p><b>The table used to say nothing at all.</b> An unreachable blueprint and a wrong arrangement
     * produced the same empty result slot, so a player who had laid out a recipe correctly and left the
     * sheet in a cabinet across the room had no way to tell which of the two had happened - and the
     * transfer button will now happily fill that grid for them, making it likelier.
     *
     * <p>A DataSlot rather than a payload: it is one bit, and vanilla already syncs these on every menu
     * change for free.
     */
    private final net.minecraft.world.inventory.DataSlot needsBlueprint =
        net.minecraft.world.inventory.DataSlot.standalone();

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

        this.addDataSlot(this.needsBlueprint);
        Rect result = LAYOUT.rect("result");
        this.addSlot(new SheetPreservingResultSlot(inventory.player, this.craftSlots, this.resultSlots,
            result.x(), result.y()));
        LAYOUT.forEachSlot("crafting",
            (index, x, y) -> this.addSlot(new Slot(this.craftSlots, index, x, y)));
        LAYOUT.forEachPlayerSlot((index, x, y) -> this.addSlot(new Slot(inventory, index, x, y)));

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
            CraftingContainer craftSlots, ResultContainer resultSlots, RecipeHolder<CraftingRecipe> last,
            net.minecraft.core.BlockPos tablePos) {
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
            castMenu(menu).needsBlueprint.set(0);
            castMenu(menu).spawnEggResult = false;
        } else {
            // Blueprint recipes (#95), looked up only when nothing ordinary matched. This is the second
            // half of the gate and the half a Recipe cannot do itself: a recipe sees its own input and
            // nothing else, so whether the player can reach the sheet is a question only the table is
            // in a position to ask.
            //
            // The lookup lives HERE and nowhere else, which is what stops the system being bypassed.
            // A vanilla crafting table resolves RecipeType.CRAFTING and blueprint recipes are not of
            // that type, so it cannot see them at all - the gate needs no code on the vanilla side.
            result = blueprintResult(level, player, input, tablePos, castMenu(menu));
        }
        resultSlots.setItem(0, result);
        menu.setRemoteSlot(0, result);
        serverPlayer.connection.send(
            new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), 0, result));
    }

    /**
     * What a blueprint recipe would make here, or nothing.
     *
     * <p>Nothing is returned both when no blueprint recipe matches the grid AND when one matches but
     * the sheet is out of reach - deliberately the same outcome, because a result slot that showed a
     * ghost item you could not take would be worse than one that stays empty. The guidebook is where
     * "you need the blueprint" gets said; the table just does not offer it.
     */
    private static ItemStack blueprintResult(Level level, Player player, CraftingInput input,
            net.minecraft.core.BlockPos tablePos, ScrapCraftingStationMenu menu) {
        // THE SPAWN EGG IS ASKED FIRST, and it is the one recipe here whose sheet is IN the grid.
        //
        // <p>It cannot be a blueprint_crafting recipe: a spawn egg needs one blueprint set per species,
        // and 29 recipes sharing one arrangement would resolve to whichever iterated first (see
        // SpawnEggCraftingRecipe). So the player names the species by laying the sheet in the grid, and
        // there is nothing for BlueprintAccess to look up - holding the sheet is not the question when
        // the sheet is right there. It is checked before the loop below because its pattern contains a
        // Blueprint item, which no other recipe's does, so it can never shadow one of them.
        for (RecipeHolder<SpawnEggCraftingRecipe> holder : level.getServer().getRecipeManager()
                .recipeMap().byType(RCRecipeTypes.SPAWN_EGG_CRAFTING.get())) {
            if (holder.value().matches(input, level)) {
                menu.needsBlueprint.set(0);
                menu.spawnEggResult = true;
                return holder.value().assemble(input);
            }
        }

        menu.spawnEggResult = false;
        boolean matchedButLocked = false;
        for (RecipeHolder<BlueprintCraftingRecipe> holder : level.getServer().getRecipeManager()
                .recipeMap().byType(RCRecipeTypes.BLUEPRINT_CRAFTING.get())) {
            BlueprintCraftingRecipe blueprint = holder.value();
            if (!blueprint.matches(input, level)) {
                continue;
            }
            if (!BlueprintAccess.reachable(level, player, tablePos, blueprint.blueprint())) {
                matchedButLocked = true;   // right arrangement, missing knowledge
                continue;
            }
            menu.needsBlueprint.set(0);
            return blueprint.assemble(input);
        }
        menu.needsBlueprint.set(matchedButLocked ? 1 : 0);
        return ItemStack.EMPTY;
    }

    /**
     * The result slot, plus the one rule that keeps a Blueprint knowledge rather than a material.
     *
     * <p>{@code spawn_egg_crafting} is the only recipe in the mod that puts a Blueprint in the grid,
     * because it is the only one where the player has to name WHICH of many sets they mean (see
     * {@link SpawnEggCraftingRecipe}). Vanilla's {@link ResultSlot} decrements every occupied grid slot
     * by one on take, so without this the sheet would be eaten by the egg it made, and four ambers of
     * one species would buy exactly one.
     *
     * <p><b>Recipe-level remainders are not available here.</b> In 26.1 {@code
     * ResultSlot.getRemainingItems} is private and resolves {@code RecipeType.CRAFTING} only, so a
     * custom type cannot supply one. The item-level {@code craftRemainder} is worse than useless for
     * this: it hands back {@code new ItemStack(item)}, which would return a BLANK blueprint and quietly
     * destroy the set the player earned.
     *
     * <p>Restoring is gated on a spawn-egg recipe having matched, not on "a blueprint was in the grid".
     * Nothing else consumes one today, but a rule of "blueprints are never consumed here" would become
     * an item duplicator the day a recipe legitimately spends one.
     */
    private final class SheetPreservingResultSlot extends ResultSlot {
        private final CraftingContainer grid;

        private SheetPreservingResultSlot(Player player, CraftingContainer grid,
                net.minecraft.world.Container out, int x, int y) {
            super(player, grid, out, 0, x, y);
            this.grid = grid;
        }

        /** The menu that owns this slot, so the flag set when the result was SHOWN is the one read. */
        private ScrapCraftingStationMenu menu() {
            return ScrapCraftingStationMenu.this;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            java.util.Map<Integer, ItemStack> sheets = new java.util.HashMap<>();
            if (menu().spawnEggResult) {
                for (int i = 0; i < grid.getContainerSize(); i++) {
                    ItemStack held = grid.getItem(i);
                    if (held.is(RCItems.BLUEPRINT.get())) {
                        sheets.put(i, held.copy());
                    }
                }
            }
            super.onTake(player, stack);
            // Blueprints do not stack, so a consumed one leaves the slot empty. Only refill an empty
            // slot: anything else means the take did not go the way this assumed, and putting a second
            // sheet on top of a surviving one is how a preservation rule becomes a dupe.
            sheets.forEach((index, sheet) -> {
                if (grid.getItem(index).isEmpty()) {
                    grid.setItem(index, sheet);
                }
            });
        }

    }

    /**
     * Whether the result currently SHOWN came from a spawn-egg recipe.
     *
     * <p>The sheet-preserving slot used to re-ask "would a spawn-egg recipe match this grid" at take
     * time, which is a different question and one step short of the gate. {@code
     * slotChangedCraftingGrid} resolves {@code RecipeType.CRAFTING} FIRST and only falls through to
     * the blueprint path, so the two can disagree: a vanilla shapeless recipe consuming a Blueprint
     * and the same vessel ingredients would win the result while the slot still handed the sheet back
     * free. Nothing in the mod triggers it - only {@code spawn_egg.json} names a Blueprint as an
     * ingredient - but that is a fact about today's content, and the gate is supposed to be a fact
     * about the code.
     *
     * <p>Set where the result is decided and read where it is taken, so the two cannot come apart.
     * Server-side only; it is never synced because only the take path reads it.
     */
    private boolean spawnEggResult;

    /** Whether the grid matches a blueprint recipe the player cannot currently run. */
    public boolean needsBlueprint() {
        return needsBlueprint.get() != 0;
    }

    /** The menu is always this type here; the static helper is copied from vanilla and takes the base. */
    private static ScrapCraftingStationMenu castMenu(AbstractContainerMenu menu) {
        return (ScrapCraftingStationMenu) menu;
    }

    @Override
    public void slotsChanged(Container container) {
        this.access.execute((lvl, blockPos) ->
            slotChangedCraftingGrid(this, lvl, this.player, this.craftSlots, this.resultSlots, null,
                blockPos));
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
     *   <li><b>an item's registry id packed with a click mode</b> - withdraw that item out of the
     *       network, one / a stack / half depending on the mode. See {@link ScrapPanelInteraction} for
     *       the packing and for why left-click takes one rather than vanilla's whole stack.</li>
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
        // Everything below comes off the wire, so a malformed client must land on a no-op rather than on
        // a default: an unknown mode is not "assume ONE", it is nothing.
        ScrapPanelInteraction.Mode mode = ScrapPanelInteraction.modeOf(id);
        if (mode == null) {
            return false;
        }
        Item item = BuiltInRegistries.ITEM.byId(ScrapPanelInteraction.itemIdOf(id));
        if (item == null || item == Items.AIR) {
            return false;
        }
        ItemStack pulled = withdrawStack(item, mode);
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

    /**
     * Pull {@code item} from the network - the first matching bin, else a barrel slot - in the quantity
     * the click asked for.
     *
     * <p>The amount is decided against what that <i>source</i> holds rather than against the panel's
     * merged total, so "half" means half of the stack you are actually taking from. Deciding it against
     * the network-wide count would make a right-click on a 300-item spread try to pull 150 out of a
     * single bin and quietly return whatever it had.
     */
    /**
     * Put one of each named item into the crafting grid, sourced from anywhere this table can reach.
     *
     * <p>Server side of JEI's transfer button (#95). The client decided the placements; this finds each
     * item and moves it, preferring the <b>player's own inventory</b> before the network - taking from
     * a shared barrel when the crafter is already carrying the item would quietly redistribute other
     * people's storage on a server.
     *
     * <p><b>Anything already in the grid goes back first</b>, or a second transfer stacks a new recipe
     * on top of the last one and produces a grid matching neither.
     *
     * <p>Best effort by design: a slot whose item cannot be found is left empty rather than the whole
     * transfer being refused. The client checks availability before sending, so a gap here means the
     * network changed underneath it, and half a grid the player can see and finish is better than an
     * empty one with no explanation.
     */
    public void fillGrid(List<Integer> itemIds) {
        if (level == null || level.isClientSide() || itemIds.size() != FillGridPayload.SLOTS) {
            return;
        }
        // The payload handler proves the sender has THIS menu open; this proves the menu is still
        // legitimate. A player who walked away, or whose table was broken, has a menu vanilla has not
        // closed yet - and withdrawStack reaches into blocks in the world, so the window matters.
        if (!stillValid(player)) {
            return;
        }
        clearGridToPlayer();
        for (int slot = 0; slot < FillGridPayload.SLOTS; slot++) {
            int id = itemIds.get(slot);
            if (id == FillGridPayload.EMPTY) {
                continue;
            }
            Item item = Item.byId(id);
            if (item == Items.AIR) {
                continue;
            }
            ItemStack one = takeOne(item);
            if (!one.isEmpty()) {
                craftSlots.setItem(slot, one);
            }
        }
        slotsChanged(craftSlots);
    }

    /** Return whatever is in the grid to the player, dropping what will not fit. */
    private void clearGridToPlayer() {
        for (int slot = 0; slot < craftSlots.getContainerSize(); slot++) {
            ItemStack stack = craftSlots.removeItemNoUpdate(slot);
            if (!stack.isEmpty() && !player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    /** One of this item from the player's inventory, else from the connected network. */
    private ItemStack takeOne(Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                return stack.split(1);
            }
        }
        ItemStack pulled = withdrawStack(item, ScrapPanelInteraction.Mode.ONE);
        return pulled.isEmpty() ? ItemStack.EMPTY : pulled.split(1);
    }

    private ItemStack withdrawStack(Item item, ScrapPanelInteraction.Mode mode) {
        int stackMax = new ItemStack(item).getMaxStackSize();
        List<BlockPos> members = ScrapNetwork.collect(level, pos);
        for (ScrapBinBlockEntity bin : ScrapNetwork.bins(level, members)) {
            if (bin.boundMaterial() == item && bin.amount() > 0) {
                return bin.withdraw(ScrapPanelInteraction.amountFor(mode, bin.amount(), stackMax));
            }
        }
        for (Container barrel : ScrapNetwork.barrels(level, members)) {
            for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                ItemStack stack = barrel.getItem(slot);
                if (stack.is(item)) {
                    int take = ScrapPanelInteraction.amountFor(mode, stack.getCount(), stackMax);
                    ItemStack out = stack.split(take);
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
     * Vanilla crafting quick-move.
     *
     * <p><b>The refill deliberately does NOT happen here</b>, and that is the whole of #127. Vanilla's
     * quick-move loop calls this method over and over while the result slot still holds the same item,
     * so restocking the grid from the network inside it meant the loop could never end on its own: one
     * shift-click crafted until the network ran dry or the player's inventory filled. Two individually
     * reasonable features - craft-from-storage and vanilla shift-crafting - are jointly unbounded, and
     * nothing in this mod un-crafts, so a single keypress could spend a whole sorted wall.
     *
     * <p>The restock moved to {@link #clicked}, which runs once per click rather than once per craft.
     * Shift-click is therefore bounded by what is in the grid, exactly like a vanilla table, and the
     * grid is full again by the time the player looks at it.
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
        }
        return moved;
    }

    /**
     * Restock the grid from the network once the click is finished.
     *
     * <p>Once per CLICK, which is the difference that matters. A shift-click runs vanilla's quick-move
     * loop to completion inside {@code super.clicked}; refilling after it returns means the run is
     * bounded by the grid the player could see, and the grid is stocked again for the next one.
     *
     * <p>The pattern is captured BEFORE the click, because by the time it is over the grid is empty and
     * there is nothing left to say what each slot held.
     */
    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        boolean crafted = slotId == RESULT_SLOT
            && (input == ContainerInput.QUICK_MOVE || input == ContainerInput.PICKUP);
        Item[] pattern = crafted ? capturePattern() : null;
        super.clicked(slotId, button, input, player);
        if (pattern != null && !this.level.isClientSide()) {
            refillGrid(player, pattern);
        }
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

    // THERE IS DELIBERATELY NO CAP ON WHAT THE NETWORK REPORTS.
    //
    // There was one, of 18, chosen because "the panel shows a few rows" - and a number picked for a
    // layout ended up deciding gameplay. A Scrap Barrel alone holds 27 stacks and a cluster may hold
    // any number of barrels, so everything past the eighteenth distinct item did not exist as far as
    // the client was concerned. Two failures, one cause:
    //
    //   * The shelf SCROLLS (#86), so the cap truncated content the player was meant to scroll to.
    //     "+6 more" meant "+6 of the 18 I happen to know about".
    //   * ScrapTableTransfer reads this snapshot to decide whether a recipe's ingredients are
    //     reachable, so a barrel holding 19 Rebar reported "Not in your inventory or any connected
    //     storage" because Rebar was the 25th distinct item (playtest, 2026-08-11).
    //
    // A bound was never needed for the wire either: a Material is an item id and a count, so even a
    // hoarder's cluster is a couple of kilobytes, and it is only sent when it changes.

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
            materials.add(new ScrapNetworkContentsPayload.Material(entry.getKey(), entry.getValue()));
        }
        return new ScrapNetworkContentsPayload(bins.size(), !barrels.isEmpty(), materials);
    }

    // ---------------- test seams ----------------

    /** Test seam: what the server would send the viewer, cap and all. */
    public ScrapNetworkContentsPayload contentsForTest() {
        return computeContents();
    }

    /** Test seam: the current grid pattern. */
    public Item[] capturePatternForTest() {
        return capturePattern();
    }

    /** Test seam: refill emptied grid slots from the network, as after a craft. */
    public void refillGridForTest(Player player, Item[] pattern) {
        refillGrid(player, pattern);
    }
}

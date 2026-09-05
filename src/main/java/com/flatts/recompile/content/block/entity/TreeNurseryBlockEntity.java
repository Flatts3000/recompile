package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.content.block.TreeNurseryCoreBlock;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

import com.flatts.recompile.content.menu.TreeNurseryMenu;
import com.flatts.recompile.content.menu.WideSync;

/**
 * The Tree Nursery's contents (reclamation rung 4, spec {@code docs/tree_nursery_spec.md}): the
 * producer that turns <b>water + Fertilizer + an Unknown Seedling</b> into a sapling of the player's
 * chosen species. Furnace-shaped - inputs in, one sapling out over a long cook - and deliberately
 * slow, so wood stays treasure-grade.
 *
 * <p>A BlockEntity because it must hold state: the water tank, three item slots, the selected species,
 * and the cook progress. It is the sanctioned "storage is the honest exception" line the Rain
 * Collector's tank and the Scrap Barrel already sit on.
 *
 * <p><b>Automatable since 2026-09-03</b> (owner reversal - it shipped manual-only, and a playtester
 * asked why a hopper would not feed it). As a {@link WorldlyContainer} it exposes its inputs to the
 * sides and its output to the bottom, so hoppers and pipes can run it; loading it by hand through the
 * GUI still works exactly as before. See {@link #getSlotsForFace} for why the top is not one of those
 * faces on an assembled machine. The water was always automatable and is unchanged: the fluid tank is
 * exposed as a capability, so a pipe or pump from a Rain Collector fills it (and a bucket fills it
 * through the block's use handler).
 */
public class TreeNurseryBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {

    public static final int SLOT_FERTILIZER = 0;
    public static final int SLOT_SEEDLING = 1;
    public static final int SLOT_OUTPUT = 2;
    private static final int SLOTS = 3;

    // ContainerData indices - what the GUI reads (water gauge, progress arrow, selected species).
    /**
     * The screen's slots, every one of them shaped for a 16-BIT wire rather than for the field behind
     * it (#369). {@code treeNurseryCookTicks} allows 240,000 and {@code treeNurseryTankCapacity} a
     * million; a data slot carries a signed short, so both used to arrive wrapped with nothing logged.
     *
     * <p>What each one carries follows from what the screen DISPLAYS. The arrow draws a proportion, so
     * a proportion travels and one slot always suffices. The countdown prints seconds, so seconds
     * travel - at most 12,000 of them, which fits. The water tooltip prints millibuckets, so those go
     * as two halves. See {@link WideSync}.
     */
    public static final int DATA_COOK_PERMILLE = 0;
    public static final int DATA_COOK_SECONDS_LEFT = 1;
    public static final int DATA_WATER_LOW = 2;
    public static final int DATA_WATER_HIGH = 3;
    public static final int DATA_WATER_CAP_LOW = 4;
    public static final int DATA_WATER_CAP_HIGH = 5;
    public static final int DATA_SPECIES = 6;
    /**
     * Public because the MENU needs it. Its client-side constructor builds its own
     * {@code SimpleContainerData}, and that was written as a literal {@code 5}: when #369 widened this
     * to 7 the number drifted, the screen opened, and reading slot 5 threw
     * {@code IndexOutOfBoundsException} on the render thread. Neither test layer saw it - the layout
     * tests check geometry and never read a slot - and it took opening the screen in a client.
     */
    public static final int DATA_SIZE = 7;

    /**
     * The saplings the nursery can raise, in picker order. <b>Vanilla saplings only</b> - the global
     * loot strip ({@code StripSaplingsModifier}) keeps them un-findable, so this machine is their sole
     * source (spec P2.4-R2b). Mangrove uses its propagule, the vanilla plantable equivalent.
     */
    public static final Item[] SPECIES = {
        Items.OAK_SAPLING, Items.BIRCH_SAPLING, Items.SPRUCE_SAPLING, Items.JUNGLE_SAPLING,
        Items.ACACIA_SAPLING, Items.DARK_OAK_SAPLING, Items.CHERRY_SAPLING, Items.MANGROVE_PROPAGULE,
        Items.PALE_OAK_SAPLING
    };

    /** Everything a hopper may push in, and the one thing it may pull out. */
    private static final int[] INPUT_FACES = new int[] {SLOT_FERTILIZER, SLOT_SEEDLING};
    private static final int[] OUTPUT_FACE = new int[] {SLOT_OUTPUT};
    private static final FluidResource WATER = FluidResource.of(Fluids.WATER);

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private int selectedSpecies = 0;
    private int cookProgress = 0;

    private final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, tankCapacity()) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.value() == Fluids.WATER;   // water only, like the Rain Collector's tank
        }
    };

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_COOK_PERMILLE -> WideSync.permille(cookProgress, cookTicks());
                case DATA_COOK_SECONDS_LEFT -> secondsLeft();
                case DATA_WATER_LOW -> WideSync.low(tank.getAmountAsInt(0));
                case DATA_WATER_HIGH -> WideSync.high(tank.getAmountAsInt(0));
                case DATA_WATER_CAP_LOW -> WideSync.low(tankCapacity());
                case DATA_WATER_CAP_HIGH -> WideSync.high(tankCapacity());
                case DATA_SPECIES -> selectedSpecies;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                // Only the species is written back. Cook progress used to be, and the client then read
                // its TOTAL straight off the config - which NeoForge does not sync for a COMMON spec, so
                // a client with a different config file drew the arrow against a number the server never
                // agreed to. The proportion and the countdown are both computed server-side now, so
                // there is nothing left for the client to get wrong.
                case DATA_SPECIES -> selectedSpecies = value;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return DATA_SIZE;
        }
    };

    public TreeNurseryBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(RCBlockEntities.TREE_NURSERY.get(), worldPosition, blockState);
    }

    private static int tankCapacity() {
        return RCConfig.TREE_NURSERY_TANK_CAPACITY.get();
    }

    private static int waterPerSapling() {
        return RCConfig.TREE_NURSERY_WATER_PER_SAPLING.get();
    }

    private static int cookTicks() {
        return RCConfig.TREE_NURSERY_COOK_TICKS.get();
    }

    /**
     * Whole seconds until this sapling is done, rounded up, computed HERE so the client never has to
     * know the goal. Twenty ticks a second against a 240,000-tick ceiling is 12,000, which fits a slot
     * with room to spare; the ticks themselves do not.
     */
    private int secondsLeft() {
        int total = Math.max(1, cookTicks());
        int done = Math.max(0, Math.min(cookProgress, total));
        return (total - done + 19) / 20;
    }

    /** The capability handed to pipes, pumps, and bucket interactions. */
    public ResourceHandler<FluidResource> fluidHandler() {
        return tank;
    }

    public ContainerData dataAccess() {
        return data;
    }

    // ---------------- production ----------------

    /** Server ticker (only runs while formed - {@code getTicker} gates it). */
    public static void serverTick(Level level, BlockPos pos, BlockState state, TreeNurseryBlockEntity be) {
        if (!RCConfig.TREE_NURSERY_ENABLED.get()) {
            return;
        }
        boolean changed = be.produceTick();
        // Mirror "is cooking" onto the ACTIVE blockstate so the front lights up + the machine glows.
        boolean active = be.cookProgress > 0;
        if (state.hasProperty(TreeNurseryCoreBlock.ACTIVE)
                && state.getValue(TreeNurseryCoreBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(TreeNurseryCoreBlock.ACTIVE, active), Block.UPDATE_ALL);
        }
        if (changed) {
            be.setChanged();
        }
    }

    /** Advance one tick of the cook; on completion, consume the inputs and emit a sapling. */
    boolean produceTick() {
        if (!canProduce()) {
            if (cookProgress != 0) {
                cookProgress = 0;
                return true;   // inputs pulled mid-cook: reset, do not bank progress
            }
            return false;
        }
        cookProgress++;
        if (cookProgress >= cookTicks()) {
            finishSapling();
            cookProgress = 0;
        }
        return true;
    }

    /** Whether every input is present and there is room for the chosen sapling in the output. */
    boolean canProduce() {
        if (getItem(SLOT_FERTILIZER).isEmpty() || getItem(SLOT_SEEDLING).isEmpty()) {
            return false;
        }
        if (tank.getAmountAsInt(0) < waterPerSapling()) {
            return false;
        }
        ItemStack output = getItem(SLOT_OUTPUT);
        if (output.isEmpty()) {
            return true;
        }
        ItemStack sapling = new ItemStack(currentSpecies());
        return ItemStack.isSameItemSameComponents(output, sapling)
            && output.getCount() < output.getMaxStackSize();
    }

    private void finishSapling() {
        try (Transaction transaction = Transaction.openRoot()) {
            tank.extract(WATER, waterPerSapling(), transaction);
            transaction.commit();
        }
        getItem(SLOT_FERTILIZER).shrink(1);
        getItem(SLOT_SEEDLING).shrink(1);
        ItemStack output = getItem(SLOT_OUTPUT);
        if (output.isEmpty()) {
            setItem(SLOT_OUTPUT, new ItemStack(currentSpecies()));
        } else {
            output.grow(1);
        }
    }

    private Item currentSpecies() {
        return SPECIES[Math.floorMod(selectedSpecies, SPECIES.length)];
    }

    /** Set by the GUI's species picker (via the menu button). */
    public void setSelectedSpecies(int index) {
        selectedSpecies = Math.floorMod(index, SPECIES.length);
        setChanged();
    }

    // ---------------- Container ----------------

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_FERTILIZER -> stack.is(RCItems.FERTILIZER.get());
            case SLOT_SEEDLING -> stack.is(RCItems.UNKNOWN_SEEDLING.get());
            default -> false;   // the output slot is take-only
        };
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.level == null || this.level.getBlockEntity(this.worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
            worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    /**
     * Automation faces: inputs from the top and sides, saplings out of the bottom.
     *
     * <p><b>A recorded reversal</b> (owner, 2026-09-03). This block shipped manual-only on both doors,
     * and {@code docs/automation_policy_spec.md} carried the row saying so. A playtester asked whether
     * hoppers or pipes could feed it, which was the question the old row answered with "no"; the answer
     * is now yes, in full - fertilizer and seedlings in, saplings out, so a nursery can actually run a
     * tree farm rather than being a block you stand at.
     *
     * <p>The furnace convention, matching the Hydroponics Bay: a player should not have to learn a
     * second layout for the second automatable machine.
     *
     * <p><b>On an ASSEMBLED nursery the top face is not reachable, and that is geometry rather than
     * policy.</b> This is a multiblock: {@code TreeNurseryCoreBlock.createBlueprint} puts a Solar Panel
     * at {@code (0, 1, 0)}, directly above the core, and {@code Multiblock.rotate} only turns about Y,
     * so that cell is above the core in every orientation. A dummy cell forwards no item capability, so
     * a hopper on the panel reaches nothing. What a player can actually use is the three free
     * horizontal faces - the fourth is the Water Tank at {@code (1, 0, 0)} - and the bottom for output.
     * The mapping still gives inputs to {@code UP} because an unformed core can be fed from above and
     * costing nothing to keep; the guidebook says "the sides", because that is what is true of a
     * machine you have actually built.
     *
     * <p>Shared arrays rather than fresh ones - a hopper calls this several times a second per adjacent
     * hopper, and vanilla's containers hand back constants for exactly that reason.
     */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? OUTPUT_FACE : INPUT_FACES;
    }

    /**
     * {@code canPlaceItem} already refuses the output slot and routes each input by item, so a pipe
     * cannot jam this machine with the wrong thing the way the Cupola could before #240 restricted it.
     */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    /**
     * Only the finished sapling leaves. Automation may never pull the fertilizer or the seedling back
     * out - a hopper that could would drain the machine it is supposed to be feeding.
     *
     * <p><b>One hole, and it is NeoForge's rather than ours:</b>
     * {@code WorldlyContainerWrapper.extract} is guarded by {@code side != null &&}, so a capability
     * query with a NULL side skips this check entirely and can take any slot. The Hydroponics Bay has
     * the same gap for the same reason. Sided queries - which is what a pipe attached to a face
     * actually makes - are covered.
     */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_OUTPUT;
    }

    // ---------------- MenuProvider ----------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.recompile.tree_nursery");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new TreeNurseryMenu(containerId, inventory, this, this.data);
    }

    // ---------------- persistence ----------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        tank.serialize(output.child("tank"));
        output.putInt("species", selectedSpecies);
        output.putInt("cook", cookProgress);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        input.child("tank").ifPresent(tank::deserialize);
        selectedSpecies = Math.floorMod(input.getIntOr("species", 0), SPECIES.length);
        cookProgress = input.getIntOr("cook", 0);
    }

    // ---------------- test seams ----------------

    /** Test seam: add water directly (mirrors a bucket fill). */
    public void addWaterForTest(int millibuckets) {
        try (Transaction transaction = Transaction.openRoot()) {
            tank.insert(WATER, millibuckets, transaction);
            transaction.commit();
        }
    }

    /** Current stored water, mB. */
    public int waterStored() {
        return tank.getAmountAsInt(0);
    }

    /** The water tank's capacity, mB (for the GUI gauge and Jade). */
    public int waterCapacity() {
        return tankCapacity();
    }

    /** Current cook progress, ticks (0 = idle). */
    public int cookProgress() {
        return cookProgress;
    }

    /** Ticks for a full cook (one sapling). */
    public int cookTotal() {
        return cookTicks();
    }

    /** The selected species index. */
    public int selectedSpecies() {
        return selectedSpecies;
    }

    /** Test seam: current cook progress, ticks. */
    public int cookProgressForTest() {
        return cookProgress;
    }

    /** Test seam: the selected species index. */
    public int selectedSpeciesForTest() {
        return selectedSpecies;
    }

    /** Test seam: run one production tick, returning whether state changed. */
    public boolean produceTickForTest() {
        return produceTick();
    }
}

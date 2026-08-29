package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.content.item.AmberItem;
import com.flatts.recompile.content.menu.SequencerMenu;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCDataComponents;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the creature out of a piece of Amber (#294).
 *
 * <p><b>It is not a teardown, and that is the point of it being a machine at all.</b> The Recompile
 * Workbench takes an object apart with hand tools; this reads information out of one and hands the
 * amber's contents back as an Idea Fragment. Owner call: a bench cannot sequence DNA.
 *
 * <p><b>It also does not change the material, which is why it is a sixth verb rather than a sixth
 * machine of an existing kind.</b> The five-machine table's test is what an operation does to what it
 * is given - the Trommel cuts size, the Separator divides, the Pulverizer reduces fineness, the Slag
 * Furnace changes state, the Kiln consolidates. Sequencing changes nothing at all.
 *
 * <p><b>A powered SINGLE block with a screen</b> (owner, 2026-08-28), not a multiblock: this is a
 * machine you put one precious thing into and watch, not a conveyor you feed and walk away from. It
 * consumes FE, so it sits above the fuel-burning tier and below the three conveyor multiblocks, and it
 * is therefore outside {@code MachineParityTests} - that sweep derives its list from multiblock cores.
 * Its parity is with the Burner Generator and the Hydroponics Bay, which are the other two powered
 * blocks that own a screen.
 */
public class SequencerBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int SLOT_COUNT = 2;

    /** Big enough that a solar panel can fill it between reads rather than gating every single one. */
    public static final int CAPACITY = 20_000;
    public static final int ENERGY_PER_TICK = 24;

    /** Long enough to feel like work, short enough that a stack is not an afternoon. */
    public static final int TICKS_PER_READ = 200;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final SimpleEnergyHandler battery = new SimpleEnergyHandler(CAPACITY, CAPACITY, CAPACITY);
    private int progress;

    public SequencerBlockEntity(BlockPos pos, BlockState state) {
        super(RCBlockEntities.SEQUENCER.get(), pos, state);
    }

    public SimpleEnergyHandler battery() {
        return this.battery;
    }

    /** How far through the current read, in ticks. Read by Jade; the screen uses the ContainerData. */
    public int progress() {
        return this.progress;
    }

    /**
     * Whether it is actually reading right now.
     *
     * <p>Not "has an amber in it" and not "has power": both can be true while the output slot is full,
     * and a tooltip that says a stalled machine is running is worse than one that says nothing. Same
     * predicate the ticker uses, so the two cannot disagree.
     */
    public boolean isReading() {
        ItemStack input = this.items.get(INPUT_SLOT);
        return canSequence(input) && canAcceptOutput(input)
            && this.battery.getAmountAsInt() >= ENERGY_PER_TICK;
    }

    /**
     * Whether a stack is something this machine can read.
     *
     * <p><b>Stamped amber only.</b> An unstamped piece names no creature, so there is nothing to
     * learn from it - and refusing it at the slot is the only place a player finds that out before
     * spending two hundred ticks of power discovering the output is empty.
     */
    public static boolean canSequence(ItemStack stack) {
        return stack.is(RCItems.AMBER.get()) && AmberItem.isStamped(stack);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            SequencerBlockEntity machine) {
        ItemStack input = machine.items.get(INPUT_SLOT);
        if (!canSequence(input) || !machine.canAcceptOutput(input)) {
            // Reset rather than hold. A half-read amber that was taken back out has taught nothing,
            // and carrying its progress into the NEXT piece would silently sequence one amber with
            // another's machine time.
            if (machine.progress != 0) {
                machine.progress = 0;
                machine.setChanged();
            }
            return;
        }
        if (machine.battery.getAmountAsInt() < ENERGY_PER_TICK) {
            return;
        }
        try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            machine.battery.extract(ENERGY_PER_TICK, transaction);
            transaction.commit();
        }
        machine.progress++;
        if (machine.progress >= TICKS_PER_READ) {
            machine.progress = 0;
            machine.finish(input);
        }
        machine.setChanged();
    }

    /** The fragment this amber would produce, carrying the blueprint set for its species' egg. */
    public static ItemStack fragmentFor(ItemStack amber) {
        Identifier species = amber.get(RCDataComponents.SPECIES.get());
        if (species == null) {
            return ItemStack.EMPTY;
        }
        ItemStack fragment = new ItemStack(RCItems.IDEA_FRAGMENT.get());
        // The blueprint set is named for the species, so every vanilla mob is a set without anything
        // being registered per-species in Java. A pack adds a creature by adding a loot entry and a
        // blueprint recipe, and nothing here changes.
        fragment.set(RCDataComponents.BLUEPRINT.get(),
            Identifier.fromNamespaceAndPath("recompile",
                com.flatts.recompile.content.item.BlueprintItem.SPAWN_EGG_PREFIX
                    + species.getNamespace() + "/" + species.getPath()));
        return fragment;
    }

    private boolean canAcceptOutput(ItemStack input) {
        ItemStack made = fragmentFor(input);
        if (made.isEmpty()) {
            return false;
        }
        ItemStack out = this.items.get(OUTPUT_SLOT);
        if (out.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(out, made) && out.getCount() < out.getMaxStackSize();
    }

    private void finish(ItemStack input) {
        ItemStack made = fragmentFor(input);
        ItemStack out = this.items.get(OUTPUT_SLOT);
        if (out.isEmpty()) {
            this.items.set(OUTPUT_SLOT, made);
        } else {
            out.grow(1);
        }
        input.shrink(1);
    }

    public ContainerData data() {
        return this.data;
    }

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> SequencerBlockEntity.this.battery.getAmountAsInt();
                case 1 -> SequencerBlockEntity.this.progress;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 1) {
                SequencerBlockEntity.this.progress = value;
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.recompile.sequencer");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SequencerMenu(containerId, inventory, this, this.data);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.putInt("progress", this.progress);
        this.battery.serialize(output.child("battery"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items.clear();
        ContainerHelper.loadAllItems(input, this.items);
        this.progress = input.getIntOr("progress", 0);
        input.child("battery").ifPresent(this.battery::deserialize);
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return this.items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return ContainerHelper.removeItem(this.items, slot, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        this.setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT_SLOT && canSequence(stack);
    }

    // WORLDLYCONTAINER, AND THE EMPTY FACE ARRAY IS THE WHOLE REASON.
    //
    // <p>A hopper does NOT travel the capability path - {@code HopperBlockEntity.getContainerAt} finds
    // any {@code Container} block entity directly, which is a thing this mod relies on elsewhere (the
    // Trommel and the Separator both drain a container parked on them that way). So declaring no item
    // capability, as this machine does, closes the door to pipes and leaves it wide open to a hopper:
    // one placed underneath would pull the amber straight out of the input slot mid-read, resetting
    // progress and losing the FE already spent on it.
    //
    // <p>It shipped that way and was caught in review. Insertion was never the hole - {@code
    // canPlaceItem} gates that - extraction was, which is why "I tried putting things in and it
    // refused" is not evidence here. Same fix and same reason as {@code TreeNurseryBlockEntity}, the
    // other manual-only machine with a screen.
    private static final int[] NO_SLOTS = new int[0];

    @Override
    public int[] getSlotsForFace(Direction side) {
        return NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }
}

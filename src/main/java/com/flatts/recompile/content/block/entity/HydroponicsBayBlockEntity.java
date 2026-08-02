package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.HydroponicsBayBlock;
import net.minecraft.world.level.block.Block;
import com.flatts.recompile.content.menu.HydroponicsBayMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCDataMaps;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCTags;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.minecraft.world.level.material.Fluids;

/**
 * The Hydroponics Bay (#43, spec {@code docs/hydroponics_spec.md}): water plus power grows plants that
 * this world otherwise has no way to reach.
 *
 * <p><b>It is the only door to a quarter of vanilla's plant life.</b> Sugar cane, bamboo, cactus and
 * sweet berries have zero loot-table and zero recipe references anywhere in the mod, deliberately - they
 * were held back for this machine rather than given a found source in the meantime. It also grows the
 * six seed-crops, because it is the automation tier <em>above</em> hand farming, not a parallel to it.
 *
 * <h2>The seedling swap</h2>
 *
 * Put an <b>Unknown Seedling</b> in and the first batch is a lottery, exactly as planting one in dirt is
 * today. What comes out then <b>becomes the input</b> and seeds itself from there.
 *
 * <p>That single rule collapses what looks like two features. "Unlock the cutting" and "grow it forever"
 * are the same mechanism before and after the swap, so there is no dual-mode machine and no second output
 * type. And because the output is the <b>vanilla item</b>, whether a player replants it outdoors or
 * leaves it in the bay is their choice - which defuses the terrain problem entirely, since cane needs
 * water-adjacent sand and cactus needs sand, and sand only exists in the demolition yard.
 *
 * <h2>Why a tag and a loot table rather than a recipe type</h2>
 *
 * The mechanic is always "this item makes more of itself", so a recipe file per plant would be ten copies
 * of one sentence. {@link RCTags#HYDROPONIC} is the set it can grow, and a pack extends it by adding an
 * item. The seedling lottery is a <b>gameplay loot table</b> rolled programmatically, the same machinery
 * the pull streams use - so its odds are tunable in JSON and #36 can retune them without touching Java.
 *
 * <h2>Power</h2>
 *
 * This is the <b>first thing in the mod that spends FE</b>. The generators shipped in #72 with nothing to
 * feed, which is why their rates are explicitly unbalanced in #36: there was no load to tune against.
 * There is now.
 */
public class HydroponicsBayBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    /**
     * The byproduct slot: what a crop throws off besides itself.
     *
     * <p><b>It exists so a rare byproduct cannot jam the harvest.</b> Vanilla potatoes carry a 2% chance
     * of a poisonous one, and seeds come off wheat, beetroot, melon and pumpkin every time. With a single
     * output those would either have to be dropped on the floor or merged into the yield stack, and the
     * yield stack is type-locked - so one poisonous potato in fifty would stall a potato farm outright.
     */
    public static final int SLOT_BYPRODUCT = 2;
    private static final int SLOT_COUNT = 3;

    /** The seedling lottery. Data, so the odds are a datapack question rather than a code one. */
    public static final ResourceKey<LootTable> SEEDLING_TABLE = ResourceKey.create(
        Registries.LOOT_TABLE,
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "gameplay/hydroponics_seedling"));

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int progress;

    /** Water-only, like every other tank in the mod - a bay full of lava grows nothing. */
    private final FluidStacksResourceHandler tank =
        new FluidStacksResourceHandler(1, RCConfig.HYDROPONICS_TANK_CAPACITY.get()) {
            @Override
            public boolean isValid(int index, FluidResource resource) {
                return resource.is(Fluids.WATER);
            }
        };

    /**
     * The energy buffer.
     *
     * <p>Sized at one full batch so the bay can ride out a generator's gaps rather than stuttering: a
     * Solar Panel makes nothing at night, and a machine that stalls every dusk would read as broken.
     */
    private final SimpleEnergyHandler battery = new SimpleEnergyHandler(
        RCConfig.HYDROPONICS_GROW_TICKS.get() * RCConfig.HYDROPONICS_FE_PER_TICK.get(),
        Integer.MAX_VALUE, Integer.MAX_VALUE);

    public HydroponicsBayBlockEntity(BlockPos pos, BlockState state) {
        super(RCBlockEntities.HYDROPONICS_BAY.get(), pos, state);
    }

    /** What the screen reads: progress, its goal, water and stored power. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case HydroponicsBayMenu.DATA_PROGRESS -> progress;
                case HydroponicsBayMenu.DATA_GOAL -> RCConfig.HYDROPONICS_GROW_TICKS.get();
                case HydroponicsBayMenu.DATA_WATER -> tank.getAmountAsInt(0);
                case HydroponicsBayMenu.DATA_ENERGY -> battery.getAmountAsInt();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Read-only: the server owns all four, and a client write would only desync the gauges.
        }

        @Override
        public int getCount() {
            return HydroponicsBayMenu.DATA_SIZE;
        }
    };

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.recompile.hydroponics_bay");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new HydroponicsBayMenu(containerId, inventory, this, data,
            ContainerLevelAccess.create(level, worldPosition));
    }

    /** Capacities the gauges scale against. Read from config so a retune moves the bars with it. */
    public int tankCapacity() {
        return RCConfig.HYDROPONICS_TANK_CAPACITY.get();
    }

    public int energyCapacity() {
        return RCConfig.HYDROPONICS_GROW_TICKS.get() * RCConfig.HYDROPONICS_FE_PER_TICK.get();
    }

    public FluidStacksResourceHandler tank() {
        return tank;
    }

    public SimpleEnergyHandler battery() {
        return battery;
    }

    public int progress() {
        return progress;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            HydroponicsBayBlockEntity be) {
        if (!RCConfig.HYDROPONICS_ENABLED.get() || !(level instanceof ServerLevel server)) {
            return;
        }
        if (!be.hasInputAndWater()) {
            // Reset rather than pause. A half-grown batch that survives the input being pulled out
            // would let a player bank progress with an empty machine and cash it in later.
            if (be.progress != 0) {
                be.progress = 0;
                be.setChanged();
            }
            setLit(level, pos, state, false);
            return;
        }
        if (!be.outputAccepts()) {
            // Blocked, not idle: hold progress where it is and go dark. Nothing can be banked here -
            // the input and the water are both still sitting in the machine - so the reset above does
            // not apply, and vanilla's furnace stalls the same way rather than winding back.
            setLit(level, pos, state, false);
            return;
        }
        int fe = RCConfig.HYDROPONICS_FE_PER_TICK.get();
        if (fe > 0) {
            try (Transaction tx = Transaction.openRoot()) {
                if (be.battery.extract(fe, tx) < fe) {
                    return;   // not enough power this tick; hold, do not lose progress
                }
                tx.commit();
            }
        }
        setLit(level, pos, state, true);
        be.progress++;
        if (be.progress >= RCConfig.HYDROPONICS_GROW_TICKS.get()) {
            be.progress = 0;
            be.grow(server);
        }
        be.setChanged();
    }

    /**
     * Drive the grow-light blockstate.
     *
     * <p>The whole point of the lit texture is reading "this is working" from across a base without
     * opening anything, the same beat as the Tree Nursery glowing and the Burn Barrel's fire. The
     * property and the model both existed before this did, so the pink lights were unreachable and
     * nothing failed - a lit variant that never lights is invisible to every test that checks a texture
     * merely exists.
     *
     * <p>Only writes on a change. setBlock every tick would resend the block to every nearby client
     * twenty times a second for no reason.
     */
    private static void setLit(Level level, BlockPos pos, BlockState state, boolean lit) {
        if (state.hasProperty(HydroponicsBayBlock.LIT) && state.getValue(HydroponicsBayBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(HydroponicsBayBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    /** The two inputs a batch consumes: something growable in the slot, and water in the tank. */
    private boolean hasInputAndWater() {
        ItemStack input = items.get(SLOT_INPUT);
        return !input.isEmpty() && isGrowable(input)
            && tank.getAmountAsInt(0) >= RCConfig.HYDROPONICS_WATER_PER_GROW.get();
    }

    /**
     * Whether whatever this batch is about to make can actually land in the output slot.
     *
     * <p><b>This must match the item, not merely count the room.</b> The first version checked only that
     * the stack had space, so a bay growing sugar cane over a slot holding potatoes ran to completion and
     * merged the yield into the potatoes - the cane was consumed and came back out as potatoes. A machine
     * that silently transmutes one item into another is a duplication bug wearing a farm's clothes, and
     * the count check made it look guarded.
     *
     * <p><b>A seedling batch needs the slot empty.</b> Its result is a lottery not rolled until the batch
     * finishes, so there is nothing to compare against beforehand; anything already in the output could
     * turn out to be the wrong plant.
     */
    private boolean outputAccepts() {
        ItemStack input = items.get(SLOT_INPUT);
        if (input.is(RCItems.UNKNOWN_SEEDLING.get())) {
            // A lottery result cannot be compared against anything in advance, so it needs a clear slot.
            return items.get(SLOT_OUTPUT).isEmpty();
        }
        if (!fits(SLOT_OUTPUT, yieldOf(input.getItem()), RCConfig.HYDROPONICS_YIELD.get())) {
            return false;
        }
        // The byproduct slot is checked whether or not this batch rolls one. Gating on the roll would
        // make an identical machine sometimes run and sometimes not for reasons a player cannot see, and
        // the alternative - producing the yield and binning the byproduct - loses items silently.
        var by = byproductOf(input.getItem());
        return by == null || fits(SLOT_BYPRODUCT, by.item(), by.count());
    }

    /** Whether {@code count} more of {@code item} would land in that slot. */
    private boolean fits(int slot, Item item, int count) {
        ItemStack existing = items.get(slot);
        return existing.isEmpty()
            || (existing.is(item) && existing.getCount() + count <= existing.getMaxStackSize());
    }

    /**
     * What one plantable grows into - itself unless the data map says otherwise.
     *
     * <p>The exception is the seed-based crops, which are planted as their seed exactly as they are in
     * the ground: wheat seeds in, wheat out. A wheat item is not a thing you can plant, so it is not an
     * input here either.
     */
    public static Item yieldOf(Item plantable) {
        var crop = BuiltInRegistries.ITEM.getData(RCDataMaps.HYDROPONIC_CROP,
            BuiltInRegistries.ITEM.getResourceKey(plantable).orElse(null));
        return crop == null ? plantable : crop.yields().orElse(plantable);
    }

    /** What else comes off this plantable, or null for the plants that throw off nothing. */
    public static RCDataMaps.@Nullable Byproduct byproductOf(Item plantable) {
        var crop = BuiltInRegistries.ITEM.getData(RCDataMaps.HYDROPONIC_CROP,
            BuiltInRegistries.ITEM.getResourceKey(plantable).orElse(null));
        return crop == null ? null : crop.byproduct().orElse(null);
    }

    /** An Unknown Seedling, or anything in the growable tag. */
    public static boolean isGrowable(ItemStack stack) {
        return stack.is(RCItems.UNKNOWN_SEEDLING.get()) || stack.is(RCTags.HYDROPONIC);
    }

    /**
     * Run one batch.
     *
     * <p><b>A seeded plant is a crop, not an ingredient - the bay replants itself.</b> The input stack is
     * never consumed; a batch spends water and power and yields {@code hydroponicsYield} to the output,
     * and the plant that produced it stays put. Three reasons, in order of how much they matter:
     *
     * <ul>
     *   <li>The bay is the ONLY source of sugar cane, bamboo, cactus and sweet berries in the game.
     *       Consuming the crop means one bad hopper, or one broken block, can take a plant out of a save
     *       permanently. A machine holding the last cactus in the world must not be able to eat it.</li>
     *   <li>Consuming made it worse than the thing it replaces. One sugar cane planted in vanilla is
     *       infinite cane forever; a late-game machine that instead charges you a plant per harvest is
     *       backwards, and the yield multiplier only papered over it.</li>
     *   <li>It removes the shuttling. Nothing has to move output back to input, by hand or by hopper.</li>
     * </ul>
     *
     * <p><b>A seedling is still consumed</b>, because that is the whole of what it is: a lottery ticket.
     * It yields ONE plantable to the output, which the player then seeds the bay with - and from that
     * point the machine never asks for another. That is the swap, and it survives the change intact.
     */
    private void grow(ServerLevel server) {
        ItemStack input = items.get(SLOT_INPUT);
        boolean seedling = input.is(RCItems.UNKNOWN_SEEDLING.get());
        ItemStack produced = seedling ? rollSeedling(server)
            : new ItemStack(yieldOf(input.getItem()), RCConfig.HYDROPONICS_YIELD.get());
        if (produced.isEmpty()) {
            return;
        }
        // The last word on whether the yield fits, checked against the rolled item rather than a
        // prediction of it. Bail before anything is consumed: no water spent, no input eaten, and the
        // roll is simply discarded. Only reachable for a seedling, whose result outputAccepts() cannot
        // know in advance.
        ItemStack existing = items.get(SLOT_OUTPUT);
        if (!existing.isEmpty()
                && (!ItemStack.isSameItemSameComponents(existing, produced)
                    || existing.getCount() + produced.getCount() > existing.getMaxStackSize())) {
            return;
        }
        int water = RCConfig.HYDROPONICS_WATER_PER_GROW.get();
        if (water > 0) {
            try (Transaction tx = Transaction.openRoot()) {
                tank.extract(0, FluidResource.of(Fluids.WATER), water, tx);
                tx.commit();
            }
        }
        if (seedling) {
            input.shrink(1);   // the ticket is spent; a seeded plant is not
        }
        if (existing.isEmpty()) {
            items.set(SLOT_OUTPUT, produced);
        } else {
            existing.grow(produced.getCount());
        }
        if (!seedling) {
            rollByproduct(server, input.getItem());
        }
    }

    /**
     * The byproduct, if this crop has one and the roll comes up.
     *
     * <p>Its own slot because the yield stack is type-locked: a poisonous potato merged into a potato
     * harvest is impossible, and one landing in the output would stall a potato farm on the 2% roll that
     * vanilla gives it. Seeds off wheat and beetroot are the same shape of problem arriving every batch.
     *
     * <p>Room was already proven by {@code outputAccepts} before the batch started, so this cannot lose
     * an item; the guard is here because the roll is separate from the check.
     */
    private void rollByproduct(ServerLevel server, Item plantable) {
        RCDataMaps.Byproduct by = byproductOf(plantable);
        if (by == null || server.getRandom().nextFloat() >= by.chance()) {
            return;
        }
        ItemStack slot = items.get(SLOT_BYPRODUCT);
        if (slot.isEmpty()) {
            items.set(SLOT_BYPRODUCT, new ItemStack(by.item(), by.count()));
        } else if (slot.is(by.item()) && slot.getCount() + by.count() <= slot.getMaxStackSize()) {
            slot.grow(by.count());
        }
    }

    /** One plant from the seedling table, or empty if the table is missing or rolled nothing. */
    private ItemStack rollSeedling(ServerLevel server) {
        LootTable table = server.getServer().reloadableRegistries().getLootTable(SEEDLING_TABLE);
        List<ItemStack> rolled = table.getRandomItems(
            new LootParams.Builder(server).create(LootContextParamSets.EMPTY));
        return rolled.isEmpty() ? ItemStack.EMPTY : rolled.get(0);
    }

    // ---------------- WorldlyContainer ----------------

    /**
     * Automation faces: input from the top and sides, output from the bottom.
     *
     * <p>The furnace convention, so a hopper stack behaves the way a player already expects rather than
     * needing to be learned.
     */
    @Override
    public int[] getSlotsForFace(Direction side) {
        // Both harvest slots pull from below, or a hopper under a potato farm drains the potatoes and
        // leaves the poisonous ones to fill up and stall it - the exact jam the byproduct slot exists to
        // prevent, moved one block down.
        return side == Direction.DOWN
            ? new int[] {SLOT_OUTPUT, SLOT_BYPRODUCT}
            : new int[] {SLOT_INPUT};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot != SLOT_INPUT;
    }

    /**
     * <b>The crop slot holds exactly one, and only while it is empty.</b>
     *
     * <p>The bay plants one thing and grows it forever, so a second copy in the slot would sit there
     * doing nothing while looking like it was queued to be used. Enforced here rather than only on the
     * menu slot, so a hopper cannot stack the crop up either.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_INPUT && isGrowable(stack) && items.get(SLOT_INPUT).isEmpty();
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack out = ContainerHelper.removeItem(items, slot, amount);
        if (!out.isEmpty()) {
            setChanged();
        }
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return net.minecraft.world.Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }
}

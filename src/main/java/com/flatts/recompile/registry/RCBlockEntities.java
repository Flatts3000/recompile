package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.CupolaFurnaceBlockEntity;
import com.flatts.recompile.content.block.entity.BurnBarrelBlockEntity;
import com.flatts.recompile.content.block.entity.CompostHeapBlockEntity;
import com.flatts.recompile.content.block.entity.DisplayPedestalBlockEntity;
import com.flatts.recompile.content.block.entity.RainCollectorBlockEntity;
import com.flatts.recompile.content.block.entity.RecompileWorkbenchBlockEntity;
import com.flatts.recompile.content.block.entity.ScrapBarrelBlockEntity;
import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import com.flatts.recompile.content.block.entity.ScrapCraftingTableBlockEntity;
import com.flatts.recompile.content.block.entity.TreeNurseryBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block-entity registry.
 *
 * <p>The mod ran with none of these on purpose for a long while - the Sorting Tarp is
 * stateless so it can never become an automation surface. Storage is the honest exception:
 * a container has to hold items. That is not a reversal of the tarp's design, but it is the
 * line where "no block stores anything" stops being true, so keep it to blocks whose whole
 * job is holding items.
 */
public final class RCBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Recompile.MOD_ID);

    /** The Display Pedestal's single displayed trophy (Collectibles, design I-2). Renders via a BER. */
    public static final Supplier<BlockEntityType<DisplayPedestalBlockEntity>> DISPLAY_PEDESTAL =
        BLOCK_ENTITIES.register(
            "display_pedestal",
            () -> new BlockEntityType<>(DisplayPedestalBlockEntity::new, RCBlocks.DISPLAY_PEDESTAL.get()));

    /** The Scrap Barrel's 27-slot inventory (design: storage without wood). */
    public static final Supplier<BlockEntityType<ScrapBarrelBlockEntity>> SCRAP_BARREL =
        BLOCK_ENTITIES.register(
            "scrap_barrel",
            () -> new BlockEntityType<>(ScrapBarrelBlockEntity::new, RCBlocks.SCRAP_BARREL.get()));

    /** The Scrap Crafting Table's 3x3 grid, kept across close (design P2.10 follow-up). */
    public static final Supplier<BlockEntityType<ScrapCraftingTableBlockEntity>> SCRAP_CRAFTING_TABLE =
        BLOCK_ENTITIES.register(
            "scrap_crafting_table",
            () -> new BlockEntityType<>(ScrapCraftingTableBlockEntity::new, RCBlocks.SCRAP_CRAFTING_TABLE.get()));

    /** The Rain Collector's water tank (design P1.10) - the second holding block. */
    public static final Supplier<BlockEntityType<RainCollectorBlockEntity>> RAIN_COLLECTOR =
        BLOCK_ENTITIES.register(
            "rain_collector",
            () -> new BlockEntityType<>(RainCollectorBlockEntity::new, RCBlocks.RAIN_COLLECTOR.get()));

    /**
     * The Recompile Workbench's racked tools (design P1.4). Holds two tool stacks so their
     * durability survives; exposes no capability, so it is never hopper-fed.
     */
    public static final Supplier<BlockEntityType<RecompileWorkbenchBlockEntity>> RECOMPILE_WORKBENCH =
        BLOCK_ENTITIES.register(
            "recompile_workbench",
            () -> new BlockEntityType<>(RecompileWorkbenchBlockEntity::new, RCBlocks.RECOMPILE_WORKBENCH.get()));

    /** The Compost Heap's composting layers (Mod Jam - the fertilizer tier). */
    public static final Supplier<BlockEntityType<CompostHeapBlockEntity>> COMPOST_HEAP =
        BLOCK_ENTITIES.register(
            "compost_heap",
            () -> new BlockEntityType<>(CompostHeapBlockEntity::new, RCBlocks.COMPOST_HEAP.get()));

    /** The Burn Barrel's furnace inventory (design P2.2) - a manual-only smelter. */
    /** The Cupola Furnace's contents (#50) - a blasting furnace, and the iron gate. */
    public static final Supplier<BlockEntityType<CupolaFurnaceBlockEntity>> CUPOLA_FURNACE =
        BLOCK_ENTITIES.register(
            "cupola_furnace",
            () -> new BlockEntityType<>(CupolaFurnaceBlockEntity::new, RCBlocks.CUPOLA_FURNACE.get()));

    public static final Supplier<BlockEntityType<BurnBarrelBlockEntity>> BURN_BARREL =
        BLOCK_ENTITIES.register(
            "burn_barrel",
            () -> new BlockEntityType<>(BurnBarrelBlockEntity::new, RCBlocks.BURN_BARREL.get()));

    /** The Scrap Bin's contents (design P2.9) - one salvage type, bulk. */
    public static final Supplier<BlockEntityType<ScrapBinBlockEntity>> SCRAP_BIN =
        BLOCK_ENTITIES.register(
            "scrap_bin",
            () -> new BlockEntityType<>(ScrapBinBlockEntity::new, RCBlocks.SCRAP_BIN.get()));

    /** The Tree Nursery's water tank + slots + species + cook progress (reclamation rung 4). */
    public static final Supplier<BlockEntityType<TreeNurseryBlockEntity>> TREE_NURSERY =
        BLOCK_ENTITIES.register(
            "tree_nursery",
            () -> new BlockEntityType<>(TreeNurseryBlockEntity::new, RCBlocks.TREE_NURSERY.get()));

    private RCBlockEntities() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
        modEventBus.addListener(RCBlockEntities::onRegisterCapabilities);
    }

    /** Expose the collector's water tank so pipes, pumps, and buckets see it as any tank. */
    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.Fluid.BLOCK,
            RAIN_COLLECTOR.get(),
            (be, side) -> be.fluidHandler());
        // The Scrap Bin's item handler: in and out (owner call, 2026-07-31, reversing P2.9's
        // "hopper in, no out"). Extraction keeps the binding, so draining a bin does not un-type it.
        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            SCRAP_BIN.get(),
            (be, side) -> be.storageHandler());
        // The Tree Nursery's water tank: a pipe or pump from a Rain Collector fills it (items stay
        // manual - the BE exposes no item capability, so hoppers cannot touch the slots).
        event.registerBlockEntity(
            Capabilities.Fluid.BLOCK,
            TREE_NURSERY.get(),
            (be, side) -> be.fluidHandler());
        // Both furnaces expose their sided container to the item capability, which is the door pipe
        // mods come through (hoppers use the vanilla Container path instead and never consult this).
        //
        // Registering the BARREL here looks backwards for a manual-only machine, and is the point: its
        // getSlotsForFace returns an empty int[], so the wrapper refuses every face and the SAME rule
        // now locks out hoppers and pipes alike. Before this, pipes were blocked only because no
        // capability existed - an accident that would have quietly become a hole the first time anyone
        // added one, and that made the lockout test pass against a null handler while asserting nothing.
        //
        // The Cupola is the half that was actually broken: it advertises "unlike the barrel it takes
        // hoppers", and hoppers did work, but no capability-based pipe could reach it at all.
        // The null side is NOT redundant. WorldlyContainerWrapper.size() short-circuits on a null side and
        // returns getContainerSize() - it never consults getSlotsForFace - so a non-sided query hands out
        // the whole furnace and walks straight past the lockout. Found in playtest: a Pipez pipe fed the
        // barrel while a six-direction test insisted it could not. There is no non-sided access to a
        // manual-only machine, so say exactly that and return no handler at all.
        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            BURN_BARREL.get(),
            (be, side) -> side == null ? null : new WorldlyContainerWrapper(be, side));
        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            CUPOLA_FURNACE.get(),
            (be, side) -> new WorldlyContainerWrapper(be, side));
    }
}

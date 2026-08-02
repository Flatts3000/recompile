package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.CupolaFurnaceBlockEntity;
import com.flatts.recompile.content.block.entity.BurnBarrelBlockEntity;
import com.flatts.recompile.content.block.entity.CompostHeapBlockEntity;
import com.flatts.recompile.content.block.entity.DisplayPedestalBlockEntity;
import com.flatts.recompile.content.block.entity.SolarPanelBlockEntity;
import com.flatts.recompile.content.block.entity.BurnerGeneratorBlockEntity;
import com.flatts.recompile.content.block.entity.RainCollectorBlockEntity;
import com.flatts.recompile.content.block.entity.RecompileWorkbenchBlockEntity;
import com.flatts.recompile.content.block.entity.ScrapBarrelBlockEntity;
import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import com.flatts.recompile.content.block.entity.ScrapCraftingTableBlockEntity;
import com.flatts.recompile.content.block.entity.HydroponicsBayBlockEntity;
import com.flatts.recompile.content.block.entity.TreeNurseryBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.LimitingEnergyHandler;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

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

    /**
     * The Solar Panel's energy buffer (#72). Every placed panel has one, including the panels inside a
     * formed Grass Spreader or Tree Nursery - they generate, those machines just do not consume.
     */
    public static final Supplier<BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL =
        BLOCK_ENTITIES.register(
            "solar_panel",
            () -> new BlockEntityType<>(SolarPanelBlockEntity::new, RCBlocks.SOLAR_PANEL.get()));

    /** The Burner Generator's burn timer and buffer (#72). No inventory - it is fed by right-click. */
    public static final Supplier<BlockEntityType<BurnerGeneratorBlockEntity>> BURNER_GENERATOR =
        BLOCK_ENTITIES.register(
            "burner_generator",
            () -> new BlockEntityType<>(BurnerGeneratorBlockEntity::new, RCBlocks.BURNER_GENERATOR.get()));

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

    /** The Hydroponics Bay's water, power, slots and grow progress (#43). */
    public static final Supplier<BlockEntityType<HydroponicsBayBlockEntity>> HYDROPONICS_BAY =
        BLOCK_ENTITIES.register(
            "hydroponics_bay",
            () -> new BlockEntityType<>(HydroponicsBayBlockEntity::new, RCBlocks.HYDROPONICS_BAY.get()));

    private RCBlockEntities() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
        modEventBus.addListener(RCBlockEntities::onRegisterCapabilities);
    }

    /** Expose the collector's water tank so pipes, pumps, and buckets see it as any tank. */
    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        // The Hydroponics Bay (#43) exposes all three, because it is the automation tier and a machine
        // that cannot be plumbed is not one. Caught by every_container_block_declares_its_automation on
        // its first run, which is the test written after the Cupola shipped advertising automation no
        // pipe could reach.
        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            HYDROPONICS_BAY.get(),
            (be, side) -> new WorldlyContainerWrapper(be, side));
        event.registerBlockEntity(
            Capabilities.Fluid.BLOCK,
            HYDROPONICS_BAY.get(),
            (be, side) -> be.tank());
        // Energy INSERT-only: it is a consumer. The generators are the mirror image, extract-only, and
        // for the same reason - two machines that could both push and pull would hand energy back and
        // forth forever, which is exactly what two adjacent Solar Panels did before #72 limited them.
        event.registerBlockEntity(
            Capabilities.Energy.BLOCK,
            HYDROPONICS_BAY.get(),
            (be, side) -> new LimitingEnergyHandler(be.battery(), Integer.MAX_VALUE, 0));

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
        // The BURN BARREL deliberately registers NOTHING here, and that absence is the feature.
        //
        // Two rounds of playtest got this wrong. Exposing a WorldlyContainerWrapper looked right - the
        // barrel's getSlotsForFace returns an empty int[], so the wrapper refuses every insert - but a
        // pipe decides whether to CONNECT by whether a handler exists at all, not by whether it accepts.
        // So the barrel refused items while pipes visibly hooked up to it, which reads as a broken
        // machine rather than a manual one. (The null side leaked outright: WorldlyContainerWrapper.size()
        // short-circuits on null and returns getContainerSize() without ever calling getSlotsForFace.)
        //
        // No capability means no connection, no insert, and no ambiguity. Hoppers are unaffected - they
        // use the vanilla Container path, where the empty getSlotsForFace already stops them.
        // burn_barrel_refuses_pipe_insertion asserts the absence on all six faces and on null.
        //
        // The CUPOLA is the opposite case, and the half that was actually broken: it advertises "unlike
        // the barrel it takes hoppers", and hoppers did work, but no capability-based pipe could reach
        // it at all. Sided wrapper, so it automates exactly like the vanilla furnace it reskins - minus
        // one documented departure, the input-slot filter on its BlockEntity.

        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            CUPOLA_FURNACE.get(),
            (be, side) -> new WorldlyContainerWrapper(be, side));
        // The Scrap Barrel is bulk overflow storage - the thing the network dumps into - so it is the
        // one member that should be freely automatable. It is a plain Container (chest-shaped), not a
        // WorldlyContainer, so hoppers already worked through the vanilla path while pipes could not
        // even connect: nothing exposed the item capability they look for.
        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            SCRAP_BARREL.get(),
            (be, side) -> VanillaContainerWrapper.of(be));
        // The power tier (#72). Energy only - neither generator holds items, so neither exposes an item
        // capability, and the Burner is fed by right-click rather than through a slot.
        //
        // Both are exposed OUTPUT-ONLY (maxInsert 0). A generator that accepts energy is not a harmless
        // extra: every generator also pushes to its neighbours each tick, so two of them side by side
        // trade the same energy back and forth forever. That is not hypothetical - the Tree Nursery
        // places two Solar Panels in adjacent cells, so every nursery in the world would do it.
        //
        // Generation writes to the battery directly through energyHandler(); only what leaves the block
        // goes through this wrapper.
        event.registerBlockEntity(
            Capabilities.Energy.BLOCK,
            SOLAR_PANEL.get(),
            (be, side) -> new LimitingEnergyHandler(be.energyHandler(), 0, SolarPanelBlockEntity.CAPACITY));
        event.registerBlockEntity(
            Capabilities.Energy.BLOCK,
            BURNER_GENERATOR.get(),
            (be, side) -> new LimitingEnergyHandler(
                be.energyHandler(), 0, BurnerGeneratorBlockEntity.CAPACITY));
        // ...and its fuel buffer, so a pipe can stock it and not only a hopper. Its getSlotsForFace
        // opens every face to fuel and canTakeItemThroughFace refuses all of them, so the wrapper is
        // "fuel in, nothing out" without needing a second rule here.
        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            BURNER_GENERATOR.get(),
            (be, side) -> side == null ? null : new WorldlyContainerWrapper(be, side));
    }
}

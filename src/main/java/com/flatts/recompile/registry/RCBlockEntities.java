package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.SequencerBlockEntity;
import com.flatts.recompile.content.block.entity.FilingCabinetBlockEntity;
import com.flatts.recompile.content.block.entity.CupolaFurnaceBlockEntity;
import com.flatts.recompile.content.block.entity.BurnBarrelBlockEntity;
import com.flatts.recompile.content.block.entity.CompostHeapBlockEntity;
import com.flatts.recompile.content.block.entity.DisplayPedestalBlockEntity;
import com.flatts.recompile.content.block.entity.SeparatorBlockEntity;
import com.flatts.recompile.content.block.entity.PulverizerBlockEntity;
import com.flatts.recompile.content.block.entity.TrommelBlockEntity;
import com.flatts.recompile.content.block.entity.SolarPanelBlockEntity;
import com.flatts.recompile.content.block.entity.BurnerGeneratorBlockEntity;
import com.flatts.recompile.content.block.entity.ChargingStationBlockEntity;
import com.flatts.recompile.content.block.entity.HaulerDepotBlockEntity;
import com.flatts.recompile.content.item.ScrapHaulerItem;
import com.flatts.recompile.content.block.entity.RainCollectorBlockEntity;
import com.flatts.recompile.content.block.entity.SinteringKilnBlockEntity;
import com.flatts.recompile.content.block.entity.SlagFurnaceBlockEntity;
import com.flatts.recompile.content.block.entity.WaterTankBlockEntity;
import com.flatts.recompile.content.block.entity.RecompileWorkbenchBlockEntity;
import com.flatts.recompile.content.block.entity.ScrapBarrelBlockEntity;
import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import com.flatts.recompile.content.block.entity.ScrapCraftingTableBlockEntity;
import com.flatts.recompile.content.block.entity.HydroponicsBayBlockEntity;
import com.flatts.recompile.content.block.entity.TreeNurseryBlockEntity;
import com.flatts.recompile.content.item.GarbageVacuumItem;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;
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

    /** The Charging Station's docked vacuum and buffer (#336). Not a Container; see the block. */
    public static final Supplier<BlockEntityType<ChargingStationBlockEntity>> CHARGING_STATION =
        BLOCK_ENTITIES.register(
            "charging_station",
            () -> new BlockEntityType<>(ChargingStationBlockEntity::new, RCBlocks.CHARGING_STATION.get()));

    /** The Hauler Depot (#376): a Hauler slot, a hold, and a buffer the generators fill. */
    public static final Supplier<BlockEntityType<HaulerDepotBlockEntity>> HAULER_DEPOT =
        BLOCK_ENTITIES.register(
            "hauler_depot",
            () -> new BlockEntityType<>(HaulerDepotBlockEntity::new, RCBlocks.HAULER_DEPOT.get()));

    /** The Filing Cabinet's blueprint shelf (#95). */
    public static final Supplier<BlockEntityType<FilingCabinetBlockEntity>> FILING_CABINET =
        BLOCK_ENTITIES.register(
            "filing_cabinet",
            () -> new BlockEntityType<>(FilingCabinetBlockEntity::new, RCBlocks.FILING_CABINET.get()));

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

    /** The Sequencer's amber slot, fragment slot and power buffer (#294). */
    public static final Supplier<BlockEntityType<SequencerBlockEntity>> SEQUENCER =
        BLOCK_ENTITIES.register(
            "sequencer",
            () -> new BlockEntityType<>(SequencerBlockEntity::new, RCBlocks.SEQUENCER.get()));

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

    /** The Slag Furnace's three slots (#236). Vanilla's furnace shape, running a modded recipe type. */
    public static final Supplier<BlockEntityType<SlagFurnaceBlockEntity>> SLAG_FURNACE =
        BLOCK_ENTITIES.register(
            "slag_furnace",
            () -> new BlockEntityType<>(SlagFurnaceBlockEntity::new, RCBlocks.SLAG_FURNACE.get()));

    /**
     * The Water Tank's contents (#229). The one shared component that is not inert - see
     * {@link com.flatts.recompile.content.block.entity.WaterTankBlockEntity} for the ruling.
     */
    public static final Supplier<BlockEntityType<WaterTankBlockEntity>> WATER_TANK =
        BLOCK_ENTITIES.register(
            "water_tank",
            () -> new BlockEntityType<>(WaterTankBlockEntity::new, RCBlocks.WATER_TANK.get()));

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

    /** The Separator's power buffer and grind progress (docs/gem_tier_spec.md). No item storage. */
    public static final Supplier<BlockEntityType<SeparatorBlockEntity>> SEPARATOR =
        BLOCK_ENTITIES.register(
            "separator",
            () -> new BlockEntityType<>(SeparatorBlockEntity::new, RCBlocks.SEPARATOR.get()));

    public static final Supplier<BlockEntityType<PulverizerBlockEntity>> PULVERIZER =
        BLOCK_ENTITIES.register("pulverizer",
            () -> new BlockEntityType<>(PulverizerBlockEntity::new, RCBlocks.PULVERIZER.get()));

    public static final Supplier<BlockEntityType<TrommelBlockEntity>> TROMMEL =
        BLOCK_ENTITIES.register("trommel",
            () -> new BlockEntityType<>(TrommelBlockEntity::new, RCBlocks.TROMMEL.get()));


    /** The Sintering Kiln's three slots (#248), running {@code recompile:sintering}. */
    public static final Supplier<BlockEntityType<SinteringKilnBlockEntity>> SINTERING_KILN =
        BLOCK_ENTITIES.register(
            "sintering_kiln",
            () -> new BlockEntityType<>(SinteringKilnBlockEntity::new, RCBlocks.SINTERING_KILN.get()));

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
        //
        // NULL SIDE GETS NOTHING (2026-09-03). Its canTakeItemThroughFace says `slot != SLOT_INPUT`,
        // and WorldlyContainerWrapper.extract is guarded by `side != null &&` - so a non-sided caller
        // skipped that check entirely and could pull the seed straight back out of a bay it was
        // supposed to be feeding. The machine had declared the rule since #43 and not been enforcing
        // it against that one caller. Same fix and same shape as the Tree Nursery and the Burner
        // Generator.
        //
        // IT COSTS NULL-SIDE INSERTION TOO, which is the honest price of this shape. `insert` has no
        // `side != null` guard, so a non-sided caller could legitimately FEED the bay under its own
        // rules and now cannot reach it at all. Accepted: it keeps this machine identical to the
        // Nursery and the Burner rather than minting a bespoke insert-only wrapper for one block, and
        // a pipe attached to a face - which is what almost all of them are - is unaffected.
        //
        // The CUPOLA below keeps the plain wrapper on purpose: it is held to vanilla furnace parity,
        // a vanilla furnace does answer a non-sided query, and VanillaParityTests compares every face
        // PLUS the null one against Blocks.FURNACE. Guarding it would break the parity it exists to
        // keep. The Slag Furnace and the Sintering Kiln are pinned too since #341, with insert AND
        // extract parity on every face including the null one - stronger coverage than the Cupola's,
        // which checks insert only.
        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            HYDROPONICS_BAY.get(),
            (be, side) -> side == null ? null : new WorldlyContainerWrapper(be, side));
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

        // The Sequencer takes power and nothing else, INSERT-only like every other consumer here.
        // No item capability and no Container exposure on purpose: this is a machine you stand at, so
        // there is nothing for a pipe to be reaching into.
        event.registerBlockEntity(
            Capabilities.Energy.BLOCK,
            SEQUENCER.get(),
            (be, side) -> new LimitingEnergyHandler(be.battery(), Integer.MAX_VALUE, 0));

        // The Charging Station (#336): power in, nothing else - INSERT-only, a consumer. No item
        // capability and no Container, the Display Pedestal's terms: the docked vacuum is set down and
        // picked up by hand, and a pipe that could pull it out would make the dock a chest.
        event.registerBlockEntity(
            Capabilities.Energy.BLOCK,
            CHARGING_STATION.get(),
            (be, side) -> new LimitingEnergyHandler(be.battery(), Integer.MAX_VALUE, 0));

        // THE FIRST ITEM CAPABILITY IN THE MOD. Every vacuum tier answers Capabilities.Energy.ITEM
        // over its VACUUM_CHARGE component, which is how the station charges it and how any other
        // mod's charger would. The handler is stack-backed (ItemAccessEnergyHandler, NeoForge's own),
        // so there is no second copy of the number anywhere to fall out of step with the tooltip.
        for (DeferredItem<GarbageVacuumItem> vacuum : RCItems.GARBAGE_VACUUMS) {
            event.registerItem(
                Capabilities.Energy.ITEM,
                (stack, access) -> new ItemAccessEnergyHandler(
                    access, RCDataComponents.VACUUM_CHARGE.get(), vacuum.get().tier().capacity()),
                vacuum.get());
        }

        // The Separator takes power and nothing else. INSERT-only, like the Bay: it is a consumer.
        // Deliberately NO item capability and no Container, so no pipe can connect and no hopper can
        // reach in. It still automates, because the machine REACHES OUT at both ends: it swallows what
        // lands in its bay and drains a container standing on it, and it pushes finished material into
        // whatever is parked at the chute. Reaching out and being reached into are different doors, and
        // only the second one is shut.
        event.registerBlockEntity(
            Capabilities.Energy.BLOCK,
            SEPARATOR.get(),
            (be, side) -> new LimitingEnergyHandler(be.battery(), Integer.MAX_VALUE, 0));

        // The Trommel, on exactly the Separator's terms: power in, nothing else. INSERT-only, because
        // it is a consumer.
        //
        // This was MISSED on the machine's first pass, and how it was missed is the point. The
        // BlockEntity has a battery and its tests power it by writing to that battery directly, so
        // every test passed while no generator in the game could reach the block - a machine that
        // works in the harness and is dead in the world. What catches it is asserting the CAPABILITY
        // is there, not that the machine runs once something has handed it energy.
        event.registerBlockEntity(
            Capabilities.Energy.BLOCK,
            TROMMEL.get(),
            (be, side) -> new LimitingEnergyHandler(be.battery(), Integer.MAX_VALUE, 0));

        // The Pulverizer, on the same terms: power in, nothing else. INSERT-only, it is a consumer.
        event.registerBlockEntity(
            Capabilities.Energy.BLOCK,
            PULVERIZER.get(),
            (be, side) -> new LimitingEnergyHandler(be.battery(), Integer.MAX_VALUE, 0));

        event.registerBlockEntity(
            Capabilities.Fluid.BLOCK,
            RAIN_COLLECTOR.get(),
            (be, side) -> be.fluidHandler());
        // The Water Tank, on the same terms (#229): a real tank, so a pipe or pump moves water through
        // it as it would any other. Both directions - it is a store rather than a source or a sink.
        event.registerBlockEntity(
            Capabilities.Fluid.BLOCK,
            WATER_TANK.get(),
            (be, side) -> be.fluidHandler());
        // The Scrap Bin's item handler: in and out (owner call, 2026-07-31, reversing P2.9's
        // "hopper in, no out"). Extraction keeps the binding, so draining a bin does not un-type it.
        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            SCRAP_BIN.get(),
            (be, side) -> be.storageHandler());
        // The Tree Nursery's ITEMS, opened 2026-09-03 by owner reversal - it was manual-only and a
        // playtester asked why a hopper would not feed it. Sided: inputs from the sides, saplings out
        // of the bottom.
        //
        // NULL SIDE GETS NOTHING, which is the Burner Generator's pattern below and is load-bearing
        // rather than tidy. WorldlyContainerWrapper.extract is guarded by `side != null &&`, so a
        // non-sided caller SKIPS canTakeItemThroughFace entirely and could pull the fertilizer and
        // seedling straight back out - the exact invariant this machine states. Handing a non-sided
        // caller no handler at all is one expression and closes it; the alternative was documenting a
        // hole. (The Hydroponics Bay had the same one and was fixed the same day; see its registration.)
        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            TREE_NURSERY.get(),
            (be, side) -> side == null ? null : new WorldlyContainerWrapper(be, side));
        // The Tree Nursery's water tank: a pipe or pump from a Rain Collector fills it. Older than the
        // item door above by a year and unaffected by it.
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
        // The SLAG FURNACE on the Cupola's terms, and for a reason beyond consistency: it is the far
        // end of a chain the player has already automated. The Cupola rakes slag into a slot and
        // something has to carry it here, so a machine that no pipe could reach would break the chain
        // at its last link. every_container_block_declares_its_automation caught the omission on the
        // first run, which is what that test is for.
        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            SLAG_FURNACE.get(),
            (be, side) -> new WorldlyContainerWrapper(be, side));
        // The SINTERING KILN on the same terms, and for the same reason: it is the far end of a chain
        // the player already automated. Blaze powder comes out of a Pulverizer and gets pressed at a
        // bench; a kiln no pipe could reach would break that chain at its last link.
        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            SINTERING_KILN.get(),
            (be, side) -> new WorldlyContainerWrapper(be, side));
        // The Scrap Barrel is bulk overflow storage - the thing the network dumps into - so it is the
        // one member that should be freely automatable. It is a plain Container (chest-shaped), not a
        // WorldlyContainer, so hoppers already worked through the vanilla path while pipes could not
        // even connect: nothing exposed the item capability they look for.
        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            SCRAP_BARREL.get(),
            (be, side) -> VanillaContainerWrapper.of(be));
        // The Filing Cabinet takes blueprints from a pipe as readily as from a hand; canPlaceItem is
        // what keeps everything else out, and it is on the container so both paths obey it.
        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            FILING_CABINET.get(),
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

        // The Hauler Depot (#376). Three doors, each on a precedent's terms:
        //  - power IN only, the Charging Station's consumer shape, so a Solar Panel or Burner against
        //    any face fills the buffer and nothing pulls it back out;
        //  - items through the sided wrapper, so a hopper or pipe can stock the hold and drain it,
        //    while getSlotsForFace never names the Hauler slot and canTakeItemThroughFace refuses it
        //    besides - the third row of the conservation table, held on the container itself;
        //  - and the Hauler ITEM answers Energy.ITEM over HAULER_CHARGE exactly as the vacuums do,
        //    which is the door the Depot charges it through.
        event.registerBlockEntity(
            Capabilities.Energy.BLOCK,
            HAULER_DEPOT.get(),
            (be, side) -> new LimitingEnergyHandler(be.battery(), Integer.MAX_VALUE, 0));
        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            HAULER_DEPOT.get(),
            (be, side) -> side == null ? null : new WorldlyContainerWrapper(be, side));
        event.registerItem(
            Capabilities.Energy.ITEM,
            (stack, access) -> new ItemAccessEnergyHandler(
                access, RCDataComponents.HAULER_CHARGE.get(), ScrapHaulerItem.CAPACITY),
            RCItems.SCRAP_HAULER.get());
    }
}

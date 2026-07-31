package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Creative-mode tab. One dedicated Recompile tab aggregating the mod's items in
 * category order (raw garbage, tools, materials, stations, machines, the
 * reclamation ladder, plants, food, light, shelter, collectibles). The accept
 * order here is the mod's public item ordering (JEI/EMI read it too).
 */
public final class RCCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Recompile.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RECOMPILE_TAB =
        CREATIVE_MODE_TABS.register(
            "recompile",
            () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.recompile"))
                .icon(() -> RCItems.GARBAGE_BLOCK.get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    // Ordered by category so the tab (and JEI/EMI, which read this order) reads as a
                    // coherent list rather than a phase-by-phase accretion. Groups run roughly in play
                    // order: raw garbage -> the tools + materials it yields -> the stations and machines
                    // that process it -> the reclamation ladder -> forage/food -> light -> shelter ->
                    // collectibles. Items within a group are sorted by their own progression.

                    // --- Raw garbage: the source blocks you pick through ---
                    RCItems.GARBAGE_BLOCKS.forEach(block -> output.accept(block.get()));
                    output.accept(RCItems.STONE_RUBBLE.get());
                    output.accept(RCItems.REINFORCED_CONCRETE.get());
                    output.accept(RCItems.STEEL_I_BEAM.get());

                    // --- Tools: the starter trio + the demolition sledgehammer ladder ---
                    RCItems.TRASH_TOOLS.forEach(tool -> output.accept(tool.get()));
                    RCItems.SLEDGEHAMMERS.forEach(hammer -> output.accept(hammer.get()));
                    output.accept(RCItems.CUTTING_TORCH.get());

                    // --- Base materials + the universal component ---
                    RCItems.BASE_MATERIALS.forEach(material -> output.accept(material.get()));
                    output.accept(RCItems.REBAR.get());
                    output.accept(RCItems.STEEL_OFFCUT.get());
                    RCItems.STONE_SHARDS.forEach(shard -> output.accept(shard.get()));

                    // --- Workstations: place these to sort, craft, store, and smelt scrap ---
                    output.accept(RCItems.SCRAP_CRAFTING_TABLE.get());
                    output.accept(RCItems.SORTING_TARP.get());
                    output.accept(RCItems.RECOMPILE_WORKBENCH.get());
                    output.accept(RCItems.SCRAP_BARREL.get());
                    output.accept(RCItems.SCRAP_BIN.get());
                    output.accept(RCItems.BURN_BARREL.get());
                    output.accept(RCItems.CUPOLA_FURNACE.get());

                    // --- Machines + multiblock parts: water, power, and the reclamation machines ---
                    output.accept(RCItems.MACHINE_FRAME.get());
                    output.accept(RCItems.RAIN_COLLECTOR.get());
                    output.accept(RCItems.RAIN_COLLECTOR_FUNNEL.get());
                    output.accept(RCItems.WATER_TANK.get());
                    output.accept(RCItems.PUMP.get());
                    output.accept(RCItems.COPPER_PIPE.get());
                    output.accept(RCItems.SOLAR_PANEL.get());
                    output.accept(RCItems.GRASS_SPREADER.get());
                    output.accept(RCItems.COMPOST_HEAP.get());
                    output.accept(RCItems.TREE_NURSERY.get());
                    output.accept(RCItems.WASHING_MACHINE.get());

                    // --- Reclamation ladder: the consumables the machines make and take ---
                    output.accept(RCItems.FERTILIZER.get());
                    output.accept(RCItems.UNKNOWN_SEEDLING.get());
                    output.accept(RCItems.HERBIVORE_BAIT.get());
                    output.accept(RCItems.CARNIVORE_BAIT.get());
                    output.accept(RCItems.OMNIVORE_BAIT.get());
                    output.accept(RCItems.RICH_HERBIVORE_BAIT.get());
                    output.accept(RCItems.RICH_CARNIVORE_BAIT.get());
                    output.accept(RCItems.RICH_OMNIVORE_BAIT.get());

                    // --- Plants: the pioneer weeds ---
                    output.accept(RCItems.WEEDGRASS.get());
                    output.accept(RCItems.FIREWEED.get());

                    // --- Food: scavenged and foraged ---
                    RCItems.FOOD.forEach(food -> output.accept(food.get()));

                    // --- Light + fuel ---
                    output.accept(RCItems.OILY_RAG.get());
                    output.accept(RCItems.SCRAP_TORCH.get());

                    // --- Shelter: the bed and the deliberate building tier ---
                    output.accept(RCItems.MATTRESS.get());
                    RCItems.BUILDING_BLOCKS.forEach(block -> output.accept(block.get()));

                    // --- Collectibles + display ---
                    output.accept(RCItems.DISPLAY_PEDESTAL.get());
                    RCItems.COLLECTIBLES.forEach(collectible -> output.accept(collectible.get()));
                    output.accept(RCItems.PUZZLE_CUBE.get());
                    output.accept(RCItems.PUZZLE_CUBE_SCRAMBLED.get());
                    output.accept(RCItems.AVOCADO.get());
                    output.accept(RCItems.PRESENT.get());
                    output.accept(RCItems.GOLD_COIN.get());
                    output.accept(RCItems.TOY_CAR.get());
                })
                .build()
        );

    private RCCreativeTabs() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}

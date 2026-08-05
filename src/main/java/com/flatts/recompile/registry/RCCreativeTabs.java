package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponents;
import java.util.List;
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

    /**
     * The recovered painting variants, in the order they should read on a shelf.
     *
     * <p>Hardcoded rather than derived from the {@code placeable} tag, because the tag is the *rule*
     * about what can be hung and this is a *display* order. A pack that adds a seventh placeable variant
     * should not silently gain a creative entry the mod never authored art for.
     */
    private static final List<String> RECOVERED_PAINTINGS = List.of(
        "mona_lisa", "the_scream", "starry_night", "great_wave", "pearl_earring", "la_grande_jatte");


    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Recompile.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RECOMPILE_TAB =
        CREATIVE_MODE_TABS.register(
            "recompile",
            () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.recompile"))
                .icon(() -> RCItems.GARBAGE_BLOCK.get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    // ORDER IS THE PRODUCT HERE. This list is what JEI and EMI show a player in their
                    // ingredient panel, and it had drifted into a record of the order things were
                    // built: roaches filed under Workstations, stone shards under Blueprints. Groups
                    // run in play order, and items run in progression order inside each.
                    //
                    // every_mod_item_is_in_the_creative_tab keeps this honest. Nothing else would: an
                    // item left out of the tab is invisible in creative and in JEI's panel while
                    // working perfectly in every test.

                    // --- 1. Raw garbage: what you pick through ---
                    RCItems.GARBAGE_BLOCKS.forEach(block -> output.accept(block.get()));
                    output.accept(RCItems.STONE_RUBBLE.get());
                    output.accept(RCItems.REINFORCED_CONCRETE.get());
                    output.accept(RCItems.STEEL_I_BEAM.get());
                    output.accept(RCItems.MECHANICAL_WASTE.get());
                    // The only way to hold leachate, and the only way to place it in creative.
                    output.accept(RCItems.LEACHATE_BUCKET.get());

                    // --- 2. Bulky Waste finds: the furniture the dump hands you ---
                    output.accept(RCItems.BULKY_WASTE.get());
                    output.accept(RCItems.MATTRESS.get());
                    output.accept(RCItems.WASHING_MACHINE.get());
                    output.accept(RCItems.FILING_CABINET.get());
                    output.accept(RCItems.PRINTER.get());
                    output.accept(RCItems.BROKEN_HYDROPONICS_BAY.get());

                    // --- 3. Tools ---
                    RCItems.TRASH_TOOLS.forEach(tool -> output.accept(tool.get()));
                    RCItems.SLEDGEHAMMERS.forEach(hammer -> output.accept(hammer.get()));
                    output.accept(RCItems.CUTTING_TORCH.get());

                    // --- 4. Base materials, then the salvaged metals and stone they sit beside ---
                    RCItems.BASE_MATERIALS.forEach(material -> output.accept(material.get()));
                    output.accept(RCItems.REBAR.get());
                    output.accept(RCItems.STEEL_OFFCUT.get());
                    RCItems.STONE_SHARDS.forEach(shard -> output.accept(shard.get()));
                    RCItems.INDUSTRIAL_SCRAP.forEach(scrap -> output.accept(scrap.get()));

                    // --- 5. Machine parts: what every multiblock is assembled from ---
                    output.accept(RCItems.MACHINE_FRAME.get());
                    output.accept(RCItems.COPPER_PIPE.get());
                    output.accept(RCItems.PUMP.get());
                    output.accept(RCItems.WATER_TANK.get());
                    output.accept(RCItems.SOLAR_PANEL.get());

                    // --- 6. Workstations: sort, craft, store, smelt ---
                    output.accept(RCItems.SCRAP_CRAFTING_TABLE.get());
                    output.accept(RCItems.SORTING_TARP.get());
                    output.accept(RCItems.RECOMPILE_WORKBENCH.get());
                    output.accept(RCItems.SCRAP_BARREL.get());
                    output.accept(RCItems.SCRAP_BIN.get());
                    output.accept(RCItems.BURN_BARREL.get());
                    output.accept(RCItems.CUPOLA_FURNACE.get());

                    // --- 7. Knowledge (#95): fragments, the sheets they become, what they unlock ---
                    com.flatts.recompile.content.item.BlueprintItem.shipped().forEach(set ->
                        output.accept(com.flatts.recompile.content.item.IdeaFragmentItem.of(
                            RCItems.IDEA_FRAGMENT.get(), set, 1)));
                    com.flatts.recompile.content.item.BlueprintItem.shipped().forEach(set ->
                        output.accept(com.flatts.recompile.content.item.BlueprintItem.of(
                            RCItems.BLUEPRINT.get(), set)));
                    RCItems.CLEAN_MATTRESSES.forEach(m -> output.accept(m.get()));

                    // --- 8. Power ---
                    output.accept(RCItems.BURNER_GENERATOR.get());

                    // --- 9. Machines, in the order a base gets them ---
                    output.accept(RCItems.RAIN_COLLECTOR.get());
                    output.accept(RCItems.RAIN_COLLECTOR_FUNNEL.get());
                    output.accept(RCItems.GRASS_SPREADER.get());
                    output.accept(RCItems.COMPOST_HEAP.get());
                    output.accept(RCItems.TREE_NURSERY.get());
                    output.accept(RCItems.HYDROPONICS_BAY.get());
                    output.accept(RCItems.SEPARATOR.get());
                    output.accept(RCItems.SEPARATOR_CHAMBER.get());
                    output.accept(RCItems.SEPARATOR_HOUSING.get());
                    output.accept(RCItems.SEPARATOR_CHUTE.get());

                    // --- 10. Reclamation consumables, rung by rung ---
                    output.accept(RCItems.FERTILIZER.get());
                    output.accept(RCItems.UNKNOWN_SEEDLING.get());
                    output.accept(RCItems.HERBIVORE_BAIT.get());
                    output.accept(RCItems.CARNIVORE_BAIT.get());
                    output.accept(RCItems.OMNIVORE_BAIT.get());
                    output.accept(RCItems.RICH_HERBIVORE_BAIT.get());
                    output.accept(RCItems.RICH_CARNIVORE_BAIT.get());
                    output.accept(RCItems.RICH_OMNIVORE_BAIT.get());

                    // --- 11. Plants ---
                    output.accept(RCItems.WEEDGRASS.get());
                    output.accept(RCItems.FIREWEED.get());

                    // --- 12. Food, scavenged and foraged. Roaches belong here, not under
                    // Workstations, where they sat because that is where the code happened to go. ---
                    RCItems.FOOD.forEach(food -> output.accept(food.get()));
                    output.accept(RCItems.RAW_ROACH.get());
                    output.accept(RCItems.COOKED_ROACH.get());

                    // --- 13. Light and fuel ---
                    output.accept(RCItems.OILY_RAG.get());
                    output.accept(RCItems.SCRAP_TORCH.get());

                    // --- 14. Building blocks ---
                    RCItems.BUILDING_BLOCKS.forEach(block -> output.accept(block.get()));

                    // --- 15. Collectibles and their stand ---
                    output.accept(RCItems.DISPLAY_PEDESTAL.get());
                    RCItems.COLLECTIBLES.forEach(collectible -> output.accept(collectible.get()));
                    output.accept(RCItems.PUZZLE_CUBE.get());
                    output.accept(RCItems.PUZZLE_CUBE_SCRAMBLED.get());
                    output.accept(RCItems.AVOCADO.get());
                    output.accept(RCItems.PRESENT.get());
                    output.accept(RCItems.GOLD_COIN.get());
                    output.accept(RCItems.TOY_CAR.get());

                    // Recovered paintings (#99). Vanilla already puts one stack per placeable variant in
                    // Functional Blocks, but it sets only the variant - so all six show as "Painting",
                    // are indistinguishable in a row, and searching JEI for "Mona Lisa" finds nothing.
                    // These carry item_name as well, which is what the loot drop does and what the
                    // acceptance criteria ask for: the item in your hand says Mona Lisa.
                    RECOVERED_PAINTINGS.forEach(id -> output.accept(paintingStack(parameters, id)));

                    // --- 16. Spawn eggs last, the way vanilla keeps them out of the way ---
                    output.accept(RCItems.ROACH_SPAWN_EGG.get());
                    output.accept(RCItems.PIGEON_SPAWN_EGG.get());
                })
                .build()
        );

    private RCCreativeTabs() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }


    /** A painting item that IS the given work: right image, right name, right tooltip. */
    private static ItemStack paintingStack(CreativeModeTab.ItemDisplayParameters parameters, String id) {
        ItemStack stack = new ItemStack(Items.PAINTING);
        parameters.holders().lookup(Registries.PAINTING_VARIANT)
            .flatMap(lookup -> lookup.get(ResourceKey.create(Registries.PAINTING_VARIANT,
                Identifier.fromNamespaceAndPath(Recompile.MOD_ID, id))))
            .ifPresent(variant -> {
                stack.set(DataComponents.PAINTING_VARIANT, variant);
                variant.value().title().ifPresent(
                    title -> stack.set(DataComponents.ITEM_NAME, title));
            });
        return stack;
    }
}

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
 * gameplay order (blocks first, then the base material vocabulary). The accept
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
                    RCItems.GARBAGE_BLOCKS.forEach(block -> output.accept(block.get()));
                    output.accept(RCItems.SCRAP_CRAFTING_TABLE.get());
                    output.accept(RCItems.SORTING_TARP.get());
                    output.accept(RCItems.SCRAP_BARREL.get());
                    output.accept(RCItems.MATTRESS.get());
                    RCItems.TRASH_TOOLS.forEach(tool -> output.accept(tool.get()));
                    output.accept(RCItems.REBAR.get());
                    RCItems.BUILDING_BLOCKS.forEach(block -> output.accept(block.get()));
                    RCItems.BASE_MATERIALS.forEach(material -> output.accept(material.get()));
                    RCItems.FOOD.forEach(food -> output.accept(food.get()));
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

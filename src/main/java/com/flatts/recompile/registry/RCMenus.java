package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.menu.ScrapCraftingStationMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Menu-type registry. The mod reuses vanilla menus wherever it can (the Scrap Barrel is a
 * {@code ChestMenu}, the Burn Barrel a {@code FurnaceMenu}), so the only entry here is the one that
 * genuinely needs a custom screen: the Scrap Crafting Table's station menu, which draws a
 * connected-storage panel (design P2.10 flow 4). Its type is custom precisely because vanilla
 * {@code CraftingMenu} hard-locks itself to {@code MenuType.CRAFTING} and so can never carry a bespoke
 * screen.
 */
public final class RCMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, Recompile.MOD_ID);

    /** The Scrap Crafting Table's menu. The client factory reads the table's pos from the open buffer. */
    public static final DeferredHolder<MenuType<?>, MenuType<ScrapCraftingStationMenu>> SCRAP_CRAFTING_STATION =
        MENUS.register("scrap_crafting_station", () -> IMenuTypeExtension.create(
            (id, inventory, buffer) -> new ScrapCraftingStationMenu(id, inventory, buffer.readBlockPos())));

    private RCMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}

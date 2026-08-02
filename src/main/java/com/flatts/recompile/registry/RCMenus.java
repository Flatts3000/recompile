package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.menu.ScrapCraftingStationMenu;
import com.flatts.recompile.content.menu.BurnerGeneratorMenu;
import com.flatts.recompile.content.menu.HydroponicsBayMenu;
import com.flatts.recompile.content.menu.TreeNurseryMenu;
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

    /** The Tree Nursery's menu (reclamation rung 4): the mod's second bespoke screen, for the species picker. */
    /** The Burner Generator's fuel row + power meter (#72). No vanilla screen has an energy bar. */
    public static final DeferredHolder<MenuType<?>, MenuType<BurnerGeneratorMenu>> BURNER_GENERATOR =
        MENUS.register("burner_generator", () -> IMenuTypeExtension.create(
            (id, inventory, buffer) -> new BurnerGeneratorMenu(id, inventory)));

    /** The Hydroponics Bay (#43): two slots plus water, power and grow progress. */
    public static final DeferredHolder<MenuType<?>, MenuType<HydroponicsBayMenu>> HYDROPONICS_BAY =
        MENUS.register("hydroponics_bay", () -> IMenuTypeExtension.create(
            (containerId, inventory, buf) -> new HydroponicsBayMenu(containerId, inventory)));

    public static final DeferredHolder<MenuType<?>, MenuType<TreeNurseryMenu>> TREE_NURSERY =
        MENUS.register("tree_nursery", () -> IMenuTypeExtension.create(
            (id, inventory, buffer) -> new TreeNurseryMenu(id, inventory)));

    private RCMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}

package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.menu.SequencerMenu;
import com.flatts.recompile.content.menu.ScrapCraftingStationMenu;
import com.flatts.recompile.content.menu.BurnerGeneratorMenu;
import com.flatts.recompile.content.menu.CupolaFurnaceMenu;
import com.flatts.recompile.content.menu.SinteringKilnMenu;
import com.flatts.recompile.content.menu.SlagFurnaceMenu;
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
    /**
     * The Sequencer's two slots and power meter (#294). The eighth custom screen, and the SAME
     * exception as the Burner Generator's below: a machine that burns FE needs an energy bar, and no
     * vanilla screen has one.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<SequencerMenu>> SEQUENCER =
        MENUS.register("sequencer", () -> IMenuTypeExtension.create(
            (id, inventory, buffer) -> new SequencerMenu(id, inventory)));

    /** The Burner Generator's fuel row + power meter (#72). No vanilla screen has an energy bar. */
    public static final DeferredHolder<MenuType<?>, MenuType<BurnerGeneratorMenu>> BURNER_GENERATOR =
        MENUS.register("burner_generator", () -> IMenuTypeExtension.create(
            (id, inventory, buffer) -> new BurnerGeneratorMenu(id, inventory)));

    /**
     * The Slag Furnace (#236): vanilla's furnace menu with one method changed.
     *
     * <p>Unlike the Cupola's, this one <b>subclasses</b> {@code AbstractFurnaceMenu} - three slots, so
     * {@code checkContainerSize(container, 3)} is satisfied - which hands it the slots,
     * {@code quickMoveStack}, the progress data sync and the container plumbing for free, all of which
     * the Cupola had to reimplement over a bare {@code AbstractContainerMenu}. It needs its own MenuType
     * only because a MenuType is what binds a screen to a menu.
     *
     * <p><b>It does NOT inherit the recipe book or JEI's transfer button</b>, and this javadoc claimed
     * both until review checked. The book widget is built by the SCREEN - vanilla's furnace screens
     * construct their own recipe-book component - and this mod's screen extends
     * {@code AbstractContainerScreen}; JEI's furnace transfer handler keys on vanilla's own menu
     * classes rather than on any subclass. Subclassing saves reimplementing a menu, which is worth it
     * on its own and is the whole claim.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<SlagFurnaceMenu>> SLAG_FURNACE =
        MENUS.register("slag_furnace", () -> IMenuTypeExtension.create(
            (id, inventory, buffer) -> new SlagFurnaceMenu(id, inventory)));

    /**
     * The Cupola Furnace (#236): vanilla's furnace plus a slag slot.
     *
     * <p>The mod's fifth bespoke menu, and the reason is the same shape as the crafting table's:
     * {@code AbstractFurnaceMenu} calls {@code checkContainerSize(container, 3)} in its constructor, so
     * a fourth slot is not something it can be asked for.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<CupolaFurnaceMenu>> CUPOLA_FURNACE =
        MENUS.register("cupola_furnace", () -> IMenuTypeExtension.create(
            (id, inventory, buffer) -> new CupolaFurnaceMenu(id, inventory)));

    /** The Hydroponics Bay (#43): two slots plus water, power and grow progress. */
    public static final DeferredHolder<MenuType<?>, MenuType<HydroponicsBayMenu>> HYDROPONICS_BAY =
        MENUS.register("hydroponics_bay", () -> IMenuTypeExtension.create(
            (containerId, inventory, buf) -> new HydroponicsBayMenu(containerId, inventory)));

    public static final DeferredHolder<MenuType<?>, MenuType<TreeNurseryMenu>> TREE_NURSERY =
        MENUS.register("tree_nursery", () -> IMenuTypeExtension.create(
            (id, inventory, buffer) -> new TreeNurseryMenu(id, inventory)));

    /**
     * The Sintering Kiln (#248): vanilla's three furnace slots, running {@code recompile:sintering}.
     *
     * <p>A {@code MenuType} exists at all only because it is what binds a screen to a menu - vanilla's
     * {@code FurnaceScreen} is typed to {@code FurnaceMenu}, so a machine with its own screen needs its
     * own type even when the menu is otherwise vanilla's.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<SinteringKilnMenu>> SINTERING_KILN =
        MENUS.register("sintering_kiln", () -> IMenuTypeExtension.create(
            (id, inventory, buffer) -> new SinteringKilnMenu(id, inventory)));

    private RCMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}

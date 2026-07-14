package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.menu.SortingTarpMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Container-menu registry. First entry is the Sorting Tarp (P1.3). */
public final class RCMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(Registries.MENU, Recompile.MOD_ID);

    public static final Supplier<MenuType<SortingTarpMenu>> SORTING_TARP =
        MENU_TYPES.register("sorting_tarp", () -> IMenuTypeExtension.create(SortingTarpMenu::new));

    private RCMenuTypes() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}

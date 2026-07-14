package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item registry. Phase 1 adds the base material vocabulary (design P0.4) - the
 * seven sorted-material items every machine tier speaks: scrap metal, plastic
 * scrap, glass shards, organic muck, fiber scrap, e-scrap (rare), and junk (the
 * filler majority; fuel later, not worthless).
 *
 * <p>Uses the factory form ({@code registerItem(name, factory)}) because MC 26.1
 * sets the {@code ResourceKey} on Properties before the item constructor runs.
 */
public final class RCItems {

    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(Recompile.MOD_ID);

    // ---------------- Base material vocabulary (P0.4) ----------------
    public static final DeferredItem<Item> SCRAP_METAL = ITEMS.registerItem("scrap_metal", Item::new);
    public static final DeferredItem<Item> PLASTIC_SCRAP = ITEMS.registerItem("plastic_scrap", Item::new);
    public static final DeferredItem<Item> GLASS_SHARDS = ITEMS.registerItem("glass_shards", Item::new);
    public static final DeferredItem<Item> ORGANIC_MUCK = ITEMS.registerItem("organic_muck", Item::new);
    public static final DeferredItem<Item> FIBER_SCRAP = ITEMS.registerItem("fiber_scrap", Item::new);
    public static final DeferredItem<Item> E_SCRAP = ITEMS.registerItem("e_scrap", Item::new);
    public static final DeferredItem<Item> JUNK = ITEMS.registerItem("junk", Item::new);

    /** The seven base materials in canonical order (creative tab + docs use this). */
    public static final List<DeferredItem<Item>> BASE_MATERIALS = List.of(
        SCRAP_METAL, PLASTIC_SCRAP, GLASS_SHARDS, ORGANIC_MUCK, FIBER_SCRAP, E_SCRAP, JUNK);

    // ---------------- Blocks-as-items ----------------
    public static final DeferredItem<BlockItem> GARBAGE_BLOCK =
        ITEMS.registerSimpleBlockItem("garbage_block", RCBlocks.GARBAGE_BLOCK);

    private RCItems() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}

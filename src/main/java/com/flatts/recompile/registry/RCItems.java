package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
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

    // ---------------- Crafting components + trash-tier tools (P1.2) ----------------
    // Rebar is the universal handle (the analog of vanilla sticks) - drops from the
    // scrap-metal pull stream. Tools are tier-zero (stone-class); no pickaxe, on
    // purpose (nothing to mine). The knife opens bales, the prybar opens appliances
    // (and is a weak weapon), the junk shovel digs garbage fast.
    public static final DeferredItem<Item> REBAR = ITEMS.registerItem("rebar", Item::new);
    public static final DeferredItem<Item> JUNK_SHOVEL = ITEMS.registerItem(
        "junk_shovel", props -> new Item(props.shovel(ToolMaterial.STONE, 1.5F, -3.0F)));
    public static final DeferredItem<Item> SCRAP_KNIFE = ITEMS.registerItem(
        "scrap_knife", props -> new Item(props.sword(ToolMaterial.STONE, 1.0F, -2.0F)));
    public static final DeferredItem<Item> PRYBAR = ITEMS.registerItem(
        "prybar", props -> new Item(props.sword(ToolMaterial.STONE, 2.0F, -2.6F)));

    /** The starter tool trio (creative tab ordering). */
    public static final List<DeferredItem<Item>> TRASH_TOOLS = List.of(
        SCRAP_KNIFE, PRYBAR, JUNK_SHOVEL);

    // ---------------- Blocks-as-items ----------------
    public static final DeferredItem<BlockItem> GARBAGE_BLOCK =
        ITEMS.registerSimpleBlockItem("garbage_block", RCBlocks.GARBAGE_BLOCK);
    public static final DeferredItem<BlockItem> TRASH_BAG =
        ITEMS.registerSimpleBlockItem("trash_bag", RCBlocks.TRASH_BAG);
    public static final DeferredItem<BlockItem> COMPACTED_BALE =
        ITEMS.registerSimpleBlockItem("compacted_bale", RCBlocks.COMPACTED_BALE);
    /** The appliance block-item is also the teardown input carried to the workbench (P1.4). */
    public static final DeferredItem<BlockItem> APPLIANCE =
        ITEMS.registerSimpleBlockItem("appliance", RCBlocks.APPLIANCE);

    /** The garbage-block family in creative-tab order. */
    public static final List<DeferredItem<BlockItem>> GARBAGE_BLOCKS = List.of(
        GARBAGE_BLOCK, TRASH_BAG, COMPACTED_BALE, APPLIANCE);

    private RCItems() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}

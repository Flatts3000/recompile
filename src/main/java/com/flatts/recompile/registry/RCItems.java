package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.item.MattressItem;
import com.flatts.recompile.content.item.OpenedCanItem;
import com.flatts.recompile.content.item.SealedCanItem;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Weapon;
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
    // purpose (nothing to mine). The knife cuts bales, the prybar digs out Bulky Waste
    // (and is a weak weapon), the junk shovel digs garbage fast.
    public static final DeferredItem<Item> REBAR = ITEMS.registerItem("rebar", Item::new);
    public static final DeferredItem<Item> JUNK_SHOVEL = ITEMS.registerItem(
        "junk_shovel", props -> new Item(props.shovel(ToolMaterial.STONE, 1.5F, -3.0F)));
    // The knife is a cutting tool, not a sword: it mines its own tag
    // (recompile:mineable/knife = bales) the way a shovel mines mineable/shovel, so the
    // one tool that opens a bale is also the one that cuts it loose for the tarp.
    //
    // tool() rather than sword() is a deliberate trade, not an oversight - the two
    // builders hand out different components, and the knife wants the tool side of each:
    //   - 1 durability per block broken, where a sword costs 2. This is the knife's day
    //     job (a bale per cut), so its real cost halved.
    //   - 2 durability per melee hit, where a sword costs 1. The knife is not the weapon,
    //     so it can eat that; the prybar below is, and pins the cost back to Weapon(1).
    //   - it can break blocks in creative, which swords refuse - correct for a tool.
    // Dropped with sword(): fast cobweb mining and the SWORD_INSTANTLY_MINES /
    // SWORD_EFFICIENT overrides. Nothing in a garbage world has cobwebs or leaves.
    public static final DeferredItem<Item> SCRAP_KNIFE = ITEMS.registerItem(
        "scrap_knife",
        props -> new Item(props.tool(ToolMaterial.STONE, RCTags.MINEABLE_WITH_KNIFE, 1.0F, -2.0F, 0.0F)));

    // The prybar digs out Bulky Waste and levers the Scrap Barrel apart - a vanilla
    // barrel answers to an axe, but this one is welded steel and axes have no place in a
    // world with no trees. Same tool()-over-a-tag treatment as the knife.
    //
    // It is also the trio's weak weapon, and tool() would quietly double its melee cost:
    // sword() ships Weapon(1), tool() ships Weapon(2). The knife can eat that trade
    // because it is not the weapon - this one cannot, so the melee profile is pinned back
    // to Weapon(1) explicitly. The override is chained AFTER tool() on purpose: components
    // are last-write-wins, so the order is what makes the pin hold. What it keeps from
    // tool(): the mining rule, and 1 durability per block broken rather than a sword's 2.
    //
    // It does still give up the sword-only combat rules - fast cobweb cutting and the
    // SWORD_INSTANTLY_MINES / SWORD_EFFICIENT overrides - and that is accepted rather than
    // overlooked. Those rules need cobwebs or foliage to matter; this world has no trees,
    // its starting biome spawns nothing, no vanilla structure generates in it (its biome is
    // in no vanilla biome tag), and the Nether and End are locked. There is nothing here
    // for them to bite on. Revisit if a themed dimension ever ships webs.
    public static final DeferredItem<Item> PRYBAR = ITEMS.registerItem(
        "prybar",
        props -> new Item(props.tool(ToolMaterial.STONE, RCTags.MINEABLE_WITH_PRYBAR, 2.0F, -2.6F, 0.0F)
            .component(DataComponents.WEAPON, new Weapon(1))));

    /** The starter tool trio (creative tab ordering). */
    public static final List<DeferredItem<Item>> TRASH_TOOLS = List.of(
        SCRAP_KNIFE, PRYBAR, JUNK_SHOVEL);

    // ---------------- Food (P1.9) ----------------
    // Scavenged tin cans: a sealed can opens with a scrap knife into an opened can
    // that eats like Suspicious Stew (a random effect - the risk staple). The dump
    // mushroom is the humble forage staple, foraged off garbage mycelium.
    public static final DeferredItem<Item> TIN_CAN = ITEMS.registerItem("tin_can", SealedCanItem::new);
    public static final DeferredItem<Item> TIN_CAN_OPEN = ITEMS.registerItem(
        "tin_can_open",
        props -> new OpenedCanItem(props.food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.3F).build())));
    public static final DeferredItem<Item> DUMP_MUSHROOM = ITEMS.registerItem(
        "dump_mushroom",
        props -> new Item(props.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.1F).build())));

    /** Food items in creative-tab order. */
    public static final List<DeferredItem<Item>> FOOD = List.of(TIN_CAN, TIN_CAN_OPEN, DUMP_MUSHROOM);

    // ---------------- Blocks-as-items ----------------
    public static final DeferredItem<BlockItem> GARBAGE_BLOCK =
        ITEMS.registerSimpleBlockItem("garbage_block", RCBlocks.GARBAGE_BLOCK);
    public static final DeferredItem<BlockItem> TRASH_BAG =
        ITEMS.registerSimpleBlockItem("trash_bag", RCBlocks.TRASH_BAG);
    public static final DeferredItem<BlockItem> COMPACTED_BALE =
        ITEMS.registerSimpleBlockItem("compacted_bale", RCBlocks.COMPACTED_BALE);
    /**
     * Bulky Waste (P1.11). Registered so creative can place it; it is unobtainable in
     * survival, because breaking the block yields the <em>find</em> rather than itself,
     * so there is nothing to dupe.
     */
    public static final DeferredItem<BlockItem> BULKY_WASTE =
        ITEMS.registerSimpleBlockItem("bulky_waste", RCBlocks.BULKY_WASTE);
    /**
     * The mattress: place it and it is a bed, or cut it open with the knife for string.
     * Never craftable - the dump gives you the bed (P1.11).
     */
    public static final DeferredItem<MattressItem> MATTRESS = ITEMS.registerItem(
        "mattress", props -> new MattressItem(RCBlocks.MATTRESS.get(), props));
    public static final DeferredItem<BlockItem> SCRAP_CRAFTING_TABLE =
        ITEMS.registerSimpleBlockItem("scrap_crafting_table", RCBlocks.SCRAP_CRAFTING_TABLE);
    public static final DeferredItem<BlockItem> SORTING_TARP =
        ITEMS.registerSimpleBlockItem("sorting_tarp", RCBlocks.SORTING_TARP);
    public static final DeferredItem<BlockItem> SCRAP_BARREL =
        ITEMS.registerSimpleBlockItem("scrap_barrel", RCBlocks.SCRAP_BARREL);

    /** The garbage-block family in creative-tab order. */
    public static final List<DeferredItem<BlockItem>> GARBAGE_BLOCKS = List.of(
        GARBAGE_BLOCK, TRASH_BAG, COMPACTED_BALE, BULKY_WASTE);

    // ---------------- Building blocks (P1.12): the deliberate shelter tier ----------------
    // Refined from scrap into blocks you would choose to build a home from. Full kit per
    // material (base + slab + stairs + wall); also the material sink for bulk scrap.
    public static final DeferredItem<BlockItem> PRESSED_JUNK_BLOCK =
        ITEMS.registerSimpleBlockItem("pressed_junk_block", RCBlocks.PRESSED_JUNK_BLOCK);
    public static final DeferredItem<BlockItem> PRESSED_JUNK_SLAB =
        ITEMS.registerSimpleBlockItem("pressed_junk_slab", RCBlocks.PRESSED_JUNK_SLAB);
    public static final DeferredItem<BlockItem> PRESSED_JUNK_STAIRS =
        ITEMS.registerSimpleBlockItem("pressed_junk_stairs", RCBlocks.PRESSED_JUNK_STAIRS);
    public static final DeferredItem<BlockItem> PRESSED_JUNK_WALL =
        ITEMS.registerSimpleBlockItem("pressed_junk_wall", RCBlocks.PRESSED_JUNK_WALL);

    /** Building blocks in creative-tab order (grouped by family: base, slab, stairs, wall). */
    public static final List<DeferredItem<BlockItem>> BUILDING_BLOCKS = List.of(
        PRESSED_JUNK_BLOCK, PRESSED_JUNK_SLAB, PRESSED_JUNK_STAIRS, PRESSED_JUNK_WALL);

    private RCItems() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}

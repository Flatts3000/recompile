package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.BulkyWasteBlock;
import com.flatts.recompile.content.block.MattressBlock;
import com.flatts.recompile.content.block.CompactedBaleBlock;
import com.flatts.recompile.content.block.DumpMushroomBlock;
import com.flatts.recompile.content.block.GarbageBlock;
import com.flatts.recompile.content.block.ScrapBarrelBlock;
import com.flatts.recompile.content.block.ScrapCraftingTableBlock;
import com.flatts.recompile.content.block.SortingTarpBlock;
import com.flatts.recompile.content.block.TrashBagBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block registry. Phase 1 adds the household Block of Garbage (design P0.3): the
 * unit of mixed trash you carry, stack, and pick through. Hand-breakable but slow,
 * shovel-class fast; drops itself.
 *
 * <p>Uses the factory form ({@code registerBlock(name, factory, propsSupplier)})
 * because MC 26.1 sets the {@code ResourceKey} on Properties before the block
 * constructor runs.
 */
public final class RCBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(Recompile.MOD_ID);

    /**
     * The household Block of Garbage. Strength 0.6 (dirt-ish): hand-breakable but
     * slow, fast with a shovel (via the {@code mineable/shovel} tag). No
     * {@code requiresCorrectToolForDrops} - always drops itself. Randomized visual
     * variants come from the blockstate JSON, not code.
     */
    public static final DeferredBlock<GarbageBlock> GARBAGE_BLOCK = BLOCKS.registerBlock(
        "garbage_block",
        GarbageBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.DIRT)
            .strength(0.6F)
            .sound(SoundType.GRAVEL)
    );

    /** Trash bag (P1.1): soft surface litter. Instant hand-break, quiet. */
    public static final DeferredBlock<TrashBagBlock> TRASH_BAG = BLOCKS.registerBlock(
        "trash_bag",
        TrashBagBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.TERRACOTTA_WHITE)
            .strength(0.2F)
            .sound(SoundType.WOOL)
    );

    /** Compacted bale (P1.1): dense, strapped trash. Sturdier; opened with a scrap knife. */
    public static final DeferredBlock<CompactedBaleBlock> COMPACTED_BALE = BLOCKS.registerBlock(
        "compacted_bale",
        CompactedBaleBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.DIRT)
            .strength(0.9F)
            .sound(SoundType.GRASS)
    );

    /**
     * Bulky Waste (P1.11): something big is buried here - pry it open to find out what.
     * Inherits the appliance's slot and feel. {@code requiresCorrectToolForDrops} so the
     * prybar (via {@code recompile:mineable/prybar}) is the <em>only</em> way in - bare
     * hands get nothing, matching the compacted bale, which keeps its "you need a Prybar"
     * nudge honest. The find itself lives in the loot table, which is the file that grows.
     */
    public static final DeferredBlock<BulkyWasteBlock> BULKY_WASTE = BLOCKS.registerBlock(
        "bulky_waste",
        BulkyWasteBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.4F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
    );

    /** Scrap crafting table: the tier-zero 3x3 crafting station (no wood in this world). */
    public static final DeferredBlock<ScrapCraftingTableBlock> SCRAP_CRAFTING_TABLE = BLOCKS.registerBlock(
        "scrap_crafting_table",
        ScrapCraftingTableBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.0F)
            .sound(SoundType.METAL)
    );

    /**
     * Sorting Tarp (P1.3): the manual batch-sorting table. Right-click holding a
     * garbage block / bag / bale to sift into the world; no GUI, no hoppers, no
     * BlockEntity. {@code noOcclusion} because the model is a table, not a full cube.
     */
    public static final DeferredBlock<SortingTarpBlock> SORTING_TARP = BLOCKS.registerBlock(
        "sorting_tarp",
        SortingTarpBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .strength(0.8F)
            .sound(SoundType.WOOL)
            .noOcclusion()
    );

    /**
     * Scrap Barrel: the garbage world's storage. Every vanilla container is wood-gated
     * (chest and barrel want planks, hopper wants a chest), and this world has no trees,
     * so without this there is nowhere to put anything - which the Sorting Tarp makes
     * acute, since it sifts onto the ground. Metal, so it sounds and mines like one.
     */
    public static final DeferredBlock<ScrapBarrelBlock> SCRAP_BARREL = BLOCKS.registerBlock(
        "scrap_barrel",
        ScrapBarrelBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.6F)
            .sound(SoundType.METAL)
    );

    /**
     * Mattress (P1.11): the first find in the Bulky Waste table, and this world's bed -
     * a vanilla bed needs planks, and there are no trees. Two blocks like a bed, soft and
     * quiet, {@code noOcclusion} because it is 5 pixels tall rather than a cube.
     */
    public static final DeferredBlock<MattressBlock> MATTRESS = BLOCKS.registerBlock(
        "mattress",
        MattressBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOL)
            .strength(0.2F)
            .sound(SoundType.WOOL)
            .noOcclusion()
    );

    /**
     * Dump mushroom (P1.9): the forageable plant. Grows on vanilla mycelium in any
     * light; breaking it drops the edible {@code dump_mushroom} item. No block-item -
     * it is worldgen-placed and foraged, not planted (farming is a later tier).
     */
    public static final DeferredBlock<DumpMushroomBlock> DUMP_MUSHROOM = BLOCKS.registerBlock(
        "dump_mushroom",
        DumpMushroomBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GRAY)
            .noCollision()
            .instabreak()
            .sound(SoundType.GRASS)
            .pushReaction(PushReaction.DESTROY)
    );

    // ---------------- Building blocks (P1.12): the deliberate shelter tier ----------------
    // Crafted from scrap at the Scrap Crafting Table; tier-0 and ungated (survival/shelter is
    // free, tech is locked). Hand-breakable and drop themselves - there is no pickaxe in this
    // world and reclaiming your own walls must not be punishing; the prybar is only the
    // *faster* tool on the metal ones (via the mineable/prybar tag), never required. Full kit
    // per material: base + slab + stairs + wall. The base block is declared immediately above
    // its stairs so the StairBlock factory can read its default state during registration.

    public static final DeferredBlock<Block> PRESSED_JUNK_BLOCK = BLOCKS.registerBlock(
        "pressed_junk_block", Block::new, RCBlocks::pressedJunkProps);
    public static final DeferredBlock<SlabBlock> PRESSED_JUNK_SLAB = BLOCKS.registerBlock(
        "pressed_junk_slab", SlabBlock::new, RCBlocks::pressedJunkProps);
    public static final DeferredBlock<StairBlock> PRESSED_JUNK_STAIRS = BLOCKS.registerBlock(
        "pressed_junk_stairs",
        props -> new StairBlock(PRESSED_JUNK_BLOCK.get().defaultBlockState(), props),
        RCBlocks::pressedJunkProps);
    public static final DeferredBlock<WallBlock> PRESSED_JUNK_WALL = BLOCKS.registerBlock(
        "pressed_junk_wall", WallBlock::new, RCBlocks::pressedJunkProps);

    public static final DeferredBlock<Block> SCRAP_PLATING = BLOCKS.registerBlock(
        "scrap_plating", Block::new, RCBlocks::metalBuildProps);
    public static final DeferredBlock<SlabBlock> SCRAP_PLATING_SLAB = BLOCKS.registerBlock(
        "scrap_plating_slab", SlabBlock::new, RCBlocks::metalBuildProps);
    public static final DeferredBlock<StairBlock> SCRAP_PLATING_STAIRS = BLOCKS.registerBlock(
        "scrap_plating_stairs",
        props -> new StairBlock(SCRAP_PLATING.get().defaultBlockState(), props),
        RCBlocks::metalBuildProps);
    public static final DeferredBlock<WallBlock> SCRAP_PLATING_WALL = BLOCKS.registerBlock(
        "scrap_plating_wall", WallBlock::new, RCBlocks::metalBuildProps);

    public static final DeferredBlock<Block> CORRUGATED_METAL = BLOCKS.registerBlock(
        "corrugated_metal", Block::new, RCBlocks::metalBuildProps);
    public static final DeferredBlock<SlabBlock> CORRUGATED_METAL_SLAB = BLOCKS.registerBlock(
        "corrugated_metal_slab", SlabBlock::new, RCBlocks::metalBuildProps);
    public static final DeferredBlock<StairBlock> CORRUGATED_METAL_STAIRS = BLOCKS.registerBlock(
        "corrugated_metal_stairs",
        props -> new StairBlock(CORRUGATED_METAL.get().defaultBlockState(), props),
        RCBlocks::metalBuildProps);
    public static final DeferredBlock<WallBlock> CORRUGATED_METAL_WALL = BLOCKS.registerBlock(
        "corrugated_metal_wall", WallBlock::new, RCBlocks::metalBuildProps);

    public static final DeferredBlock<Block> PLASTIC_PANEL = BLOCKS.registerBlock(
        "plastic_panel", Block::new, RCBlocks::plasticBuildProps);
    public static final DeferredBlock<SlabBlock> PLASTIC_PANEL_SLAB = BLOCKS.registerBlock(
        "plastic_panel_slab", SlabBlock::new, RCBlocks::plasticBuildProps);
    public static final DeferredBlock<StairBlock> PLASTIC_PANEL_STAIRS = BLOCKS.registerBlock(
        "plastic_panel_stairs",
        props -> new StairBlock(PLASTIC_PANEL.get().defaultBlockState(), props),
        RCBlocks::plasticBuildProps);
    public static final DeferredBlock<WallBlock> PLASTIC_PANEL_WALL = BLOCKS.registerBlock(
        "plastic_panel_wall", WallBlock::new, RCBlocks::plasticBuildProps);

    // Cullet Glass: just the block and its pane. Glass has no honest slab or stairs form
    // (vanilla ships neither), so the family is block + pane (an IronBarsBlock).
    public static final DeferredBlock<TransparentBlock> CULLET_GLASS = BLOCKS.registerBlock(
        "cullet_glass", TransparentBlock::new, RCBlocks::glassBuildProps);
    public static final DeferredBlock<IronBarsBlock> CULLET_GLASS_PANE = BLOCKS.registerBlock(
        "cullet_glass_pane", IronBarsBlock::new, RCBlocks::glassBuildProps);

    /** Compacted mixed trash - the WALL-E cube. Soft, cheap, the bulk junk sink. */
    private static BlockBehaviour.Properties pressedJunkProps() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.DIRT)
            .strength(1.2F)
            .sound(SoundType.GRAVEL);
    }

    /** Salvaged sheet metal - sturdy, so slow by hand; the prybar is the faster tool. */
    private static BlockBehaviour.Properties metalBuildProps() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.0F)
            .sound(SoundType.METAL);
    }

    /** Salvaged plastic sheeting - light and quick to work. */
    private static BlockBehaviour.Properties plasticBuildProps() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.TERRACOTTA_WHITE)
            .strength(1.0F)
            .sound(SoundType.WOOL);
    }

    /** Salvaged glass - fragile, near-instant to break, {@code noOcclusion} for transparency. */
    private static BlockBehaviour.Properties glassBuildProps() {
        return BlockBehaviour.Properties.of()
            .strength(0.4F)
            .sound(SoundType.GLASS)
            .noOcclusion();
    }

    private RCBlocks() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}

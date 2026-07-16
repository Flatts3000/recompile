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
import net.minecraft.world.level.block.SoundType;
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
     * Bulky Waste (P1.11): something big is buried here - break it to find out what.
     * Inherits the appliance's slot and feel. No {@code requiresCorrectToolForDrops}: the
     * prybar is fast (via {@code recompile:mineable/prybar}), but it always gives up its
     * find. The find itself lives in the loot table, which is the file that grows.
     */
    public static final DeferredBlock<BulkyWasteBlock> BULKY_WASTE = BLOCKS.registerBlock(
        "bulky_waste",
        BulkyWasteBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.4F)
            .sound(SoundType.METAL)
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

    private RCBlocks() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}

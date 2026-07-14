package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.ApplianceBlock;
import com.flatts.recompile.content.block.CompactedBaleBlock;
import com.flatts.recompile.content.block.GarbageBlock;
import com.flatts.recompile.content.block.SortingTarpBlock;
import com.flatts.recompile.content.block.TrashBagBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
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

    /** Appliance (P1.1): the teardown on-ramp. Pried open with a prybar. Metal feel. */
    public static final DeferredBlock<ApplianceBlock> APPLIANCE = BLOCKS.registerBlock(
        "appliance",
        ApplianceBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.4F)
            .sound(SoundType.METAL)
    );

    /** Sorting Tarp (P1.3): the batch-sorting station. GUI machine; manual, no hoppers. */
    public static final DeferredBlock<SortingTarpBlock> SORTING_TARP = BLOCKS.registerBlock(
        "sorting_tarp",
        SortingTarpBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .strength(0.8F)
            .sound(SoundType.WOOL)
    );

    private RCBlocks() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}

package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.content.block.CompostHeapCoreBlock;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCItems;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Compost Heap's contents (Mod Jam - the fertilizer tier): a stack of independently-composting
 * <b>layers</b>. This is the deliberate fix for the vanilla composter's batch model - you never have to
 * fill it to get an output, and it is never really "empty and useless":
 *
 * <ul>
 *   <li>Feed <b>muck or fiber</b> (either alone is fine); every {@code COMPOST_LAYER_COST} organics
 *       forms one new layer on top.</li>
 *   <li>Each layer ripens on <b>its own timer</b> ({@code COMPOST_LAYER_TICKS}); the oldest (bottom)
 *       finishes first.</li>
 *   <li>A finished layer is harvested for <b>one Fertilizer</b>; a single layer is enough, and the rest
 *       keep their progress.</li>
 *   <li>Full at {@link #MAX_LAYERS} (refuses input until harvested); empty means dormant.</li>
 * </ul>
 *
 * <p>A BlockEntity because it must hold state over time - the same "storage is the honest exception"
 * line the Rain Collector's tank and the Scrap Barrel sit on. No renderer; the {@code FILL}/{@code
 * COOKING} blockstate on the core drives the model + steam.
 */
public class CompostHeapBlockEntity extends BlockEntity {

    /** Fixed so the {@code FILL} blockstate (0..MAX) is a compile-time range; config may cap lower. */
    public static final int MAX_LAYERS = 8;

    /** Per-layer ripen progress in ticks, index 0 = oldest/bottom. Only [0, layerCount) are active. */
    private final int[] layers = new int[MAX_LAYERS];
    private int layerCount;
    /** Organics fed toward the next layer, not yet enough to form one. */
    private int inputAccumulator;

    public CompostHeapBlockEntity(BlockPos pos, BlockState state) {
        super(RCBlockEntities.COMPOST_HEAP.get(), pos, state);
    }

    private static int layerCost() {
        return RCConfig.COMPOST_LAYER_COST.get();
    }

    private static int layerTicks() {
        return RCConfig.COMPOST_LAYER_TICKS.get();
    }

    public boolean isFull() {
        return layerCount >= MAX_LAYERS;
    }

    /** Whether the oldest layer is finished and ready to pull as fertilizer. */
    public boolean hasFinishedLayer() {
        return layerCount > 0 && layers[0] >= layerTicks();
    }

    /**
     * Feed one organic item. Returns true if accepted (false only when the heap is full). Forms new
     * layers as the accumulator crosses the per-layer cost, up to the cap.
     */
    public boolean feed() {
        if (isFull()) {
            return false;
        }
        inputAccumulator++;
        boolean formed = false;
        while (inputAccumulator >= layerCost() && layerCount < MAX_LAYERS) {
            inputAccumulator -= layerCost();
            layers[layerCount++] = 0;
            formed = true;
        }
        setChanged();
        if (formed) {
            syncState();
        }
        return true;
    }

    /** Pull the oldest finished layer as one Fertilizer, or empty if none has finished. */
    public ItemStack harvest() {
        if (!hasFinishedLayer()) {
            return ItemStack.EMPTY;
        }
        for (int i = 1; i < layerCount; i++) {
            layers[i - 1] = layers[i];
        }
        layers[--layerCount] = 0;
        setChanged();
        syncState();
        return new ItemStack(RCItems.FERTILIZER.get());
    }

    /**
     * A compost volunteer: after a layer is harvested, roll the config chance that a seed came up in the
     * pile (real compost sprouts volunteers from kitchen scraps). Rolled per harvested layer, so the
     * default {@code 1 in 8} averages one Unknown Seedling per eight layers.
     */
    public boolean rollVolunteer() {
        return level != null
            && level.getRandom().nextInt(Math.max(1, RCConfig.COMPOST_VOLUNTEER_CHANCE.get())) == 0;
    }

    /** Advance each unfinished layer by one tick; the blockstate follows when fill/cooking changes. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, CompostHeapBlockEntity be) {
        int ticks = layerTicks();
        boolean advanced = false;
        for (int i = 0; i < be.layerCount; i++) {
            if (be.layers[i] < ticks) {
                be.layers[i]++;
                advanced = true;
            }
        }
        if (advanced) {
            be.setChanged();
        }
        // Sync every tick, not only when a layer advanced: a heap loaded from save with finished
        // (no-longer-ripening) layers must still push its LOAD onto the cells, and updateFillVisual is
        // a no-op that rewrites nothing when the fill and cooking are already right.
        be.syncState();
    }

    /**
     * Push the fill level onto every cell of the 2x2x2 (compost rises up the whole cage) and whether
     * anything is ripening (COOKING, the steam) onto the core. Delegated to the core because only it
     * knows the blueprint; cheap to call per tick (it rewrites only the cells that actually changed).
     */
    private void syncState() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (!(getBlockState().getBlock() instanceof CompostHeapCoreBlock core)) {
            return;
        }
        boolean cooking = false;
        int ticks = layerTicks();
        for (int i = 0; i < layerCount; i++) {
            if (layers[i] < ticks) {
                cooking = true;
                break;
            }
        }
        core.updateFillVisual(level, worldPosition, layerCount, readyLayers(), cooking);
    }

    // ---------------- persistence ----------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        List<Integer> active = new ArrayList<>(layerCount);
        for (int i = 0; i < layerCount; i++) {
            active.add(layers[i]);
        }
        output.store("layers", Codec.INT.listOf(), active);
        output.putInt("input", inputAccumulator);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        List<Integer> active = input.read("layers", Codec.INT.listOf()).orElse(List.of());
        layerCount = Math.min(active.size(), MAX_LAYERS);
        for (int i = 0; i < MAX_LAYERS; i++) {
            layers[i] = i < layerCount ? active.get(i) : 0;
        }
        inputAccumulator = input.getIntOr("input", 0);
    }

    /** Re-push the cage visual (bands + shell faces) - called on assembly so a formed cage is whole at once. */
    public void refreshVisual() {
        syncState();
    }

    // ---------------- Jade readouts ----------------

    /** How many layers are in the heap right now (0..MAX). */
    public int layers() {
        return layerCount;
    }

    /** The cap a heap fills to. */
    public int maxLayers() {
        return MAX_LAYERS;
    }

    /** How many layers have finished ripening and can be harvested. */
    public int readyLayers() {
        int ticks = layerTicks();
        int ready = 0;
        for (int i = 0; i < layerCount; i++) {
            if (layers[i] >= ticks) {
                ready++;
            }
        }
        return ready;
    }

    // ---------------- test seams ----------------

    /** Test seam: number of active layers. */
    public int layerCountForTest() {
        return layerCount;
    }

    /** Test seam: force the oldest layer to finished, so a harvest can be driven without waiting. */
    public void ripenOldestForTest() {
        if (layerCount > 0) {
            layers[0] = layerTicks();
            setChanged();
            syncState();
        }
    }
}

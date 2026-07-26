package com.flatts.recompile.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/**
 * Per-frame render state for the Display Pedestal (Collectibles, design I-2): the resolved model of
 * the trophy on top, plus its current spin. Reused across frames by the retained-mode pipeline; the
 * renderer refreshes it each {@code extractRenderState}.
 */
public class DisplayPedestalRenderState extends BlockEntityRenderState {

    /** The displayed trophy's resolved model. Empty when the pedestal holds nothing. */
    public final ItemStackRenderState item = new ItemStackRenderState();

    /** Accumulated spin in degrees, so the trophy turns slowly on its stand. */
    public float spin;
}

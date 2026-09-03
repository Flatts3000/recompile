package com.flatts.recompile.client;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Extracted state for a {@link com.flatts.recompile.content.entity.VacuumedBlockEntity}: the block, how big, how turned. */
public class VacuumedBlockRenderState extends EntityRenderState {
    public final MovingBlockRenderState block = new MovingBlockRenderState();
    public float scale = 1.0F;
    public float spin;
}

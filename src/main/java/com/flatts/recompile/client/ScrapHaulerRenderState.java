package com.flatts.recompile.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

/**
 * What the renderer copies off a {@code ScrapHaulerEntity} each frame for the model to animate from.
 *
 * <p>26.1's retained-mode entity rendering: the model never sees the entity, only this. The three
 * animation states are the entity's own objects, copied by reference, which is the vanilla idiom
 * (the Creaking and the Breeze do the same) - an {@code AnimationState} is a start tick and a flag,
 * and the model reads the elapsed time off it.
 */
public class ScrapHaulerRenderState extends LivingEntityRenderState {
    public AnimationState idle = new AnimationState();
    public AnimationState drive = new AnimationState();
    public AnimationState pickup = new AnimationState();
}

package com.flatts.recompile.client;

import com.flatts.recompile.content.entity.VacuumedBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;

/**
 * Draws a {@link VacuumedBlockEntity} as the block it was, shrinking and turning as it flies into the
 * nozzle. Vanilla's {@code FallingBlockRenderer} with two additions - a scale about the block's centre
 * and a spin - and the same {@code submitMovingBlock} path, so the block is lit and tinted exactly as
 * it was in the ground.
 *
 * <p>No shadow: a shrinking block with a full-size shadow reads as floating, and by the time it is
 * small it is at head height anyway.
 */
public class VacuumedBlockRenderer extends EntityRenderer<VacuumedBlockEntity, VacuumedBlockRenderState> {

    private static final float SPIN_DEGREES_PER_TICK = 9.0F;

    public VacuumedBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public VacuumedBlockRenderState createRenderState() {
        return new VacuumedBlockRenderState();
    }

    @Override
    public void extractRenderState(VacuumedBlockEntity entity, VacuumedBlockRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        BlockPos pos = BlockPos.containing(entity.getX(), entity.getY(), entity.getZ());
        state.block.randomSeedPos = pos;
        state.block.blockPos = pos;
        state.block.blockState = entity.getBlockState();
        if (entity.level() instanceof ClientLevel clientLevel) {
            state.block.biome = clientLevel.getBiome(pos);
            state.block.cardinalLighting = clientLevel.cardinalLighting();
            state.block.lightEngine = clientLevel.getLightEngine();
        }
        state.scale = entity.renderScale(partialTicks);
        state.spin = (entity.tickCount + partialTicks) * SPIN_DEGREES_PER_TICK;
    }

    @Override
    public void submit(VacuumedBlockRenderState state, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.block.blockState.getRenderShape() != RenderShape.MODEL) {
            return;
        }
        poseStack.pushPose();
        // The entity's position is the block's centre, so scale and turn about the origin and then
        // step back half a block to draw the model's 0..1 cube around it.
        poseStack.mulPose(Axis.YP.rotationDegrees(state.spin));
        poseStack.scale(state.scale, state.scale, state.scale);
        poseStack.translate(-0.5, -0.5, -0.5);
        submitNodeCollector.submitMovingBlock(poseStack, state.block);
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }
}

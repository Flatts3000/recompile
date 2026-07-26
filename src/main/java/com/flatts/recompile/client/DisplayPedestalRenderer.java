package com.flatts.recompile.client;

import com.flatts.recompile.content.block.entity.DisplayPedestalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The mod's one BlockEntityRenderer - a scoped, recorded reversal of P1.11.6, whose no-BER rule was
 * written for dump-scale finds (thousands in view). A Display Pedestal shows an <em>arbitrary</em>
 * collectible, so its look cannot be a baked model; the held item's model is resolved and drawn live,
 * turning slowly above the plinth. A handful of trophy stands is nowhere near dump scale, so the rule's
 * reason does not apply. Every other block in the mod still bakes its model.
 *
 * <p>Structure mirrors vanilla {@code CampfireRenderer} (which renders its cooking items the same way):
 * resolve the item into an {@link net.minecraft.client.renderer.item.ItemStackRenderState} in
 * {@code extractRenderState}, then position and submit it in {@code submit}.
 */
public class DisplayPedestalRenderer
        implements BlockEntityRenderer<DisplayPedestalBlockEntity, DisplayPedestalRenderState> {

    private static final float SPIN_DEGREES_PER_TICK = 1.5F;

    private final ItemModelResolver itemModelResolver;

    public DisplayPedestalRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public DisplayPedestalRenderState createRenderState() {
        return new DisplayPedestalRenderState();
    }

    @Override
    public void extractRenderState(DisplayPedestalBlockEntity blockEntity, DisplayPedestalRenderState state,
            float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        int seed = (int) blockEntity.getBlockPos().asLong();
        this.itemModelResolver.updateForTopItem(state.item, blockEntity.getDisplayed(),
            ItemDisplayContext.FIXED, blockEntity.getLevel(), null, seed);
        long time = blockEntity.getLevel() != null ? blockEntity.getLevel().getGameTime() : 0L;
        state.spin = (time + partialTicks) * SPIN_DEGREES_PER_TICK;
    }

    @Override
    public void submit(DisplayPedestalRenderState state, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.item.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5F, 1.15F, 0.5F);   // hover above the cap plate (cap top at 0.875)
        poseStack.mulPose(Axis.YP.rotationDegrees(state.spin));
        poseStack.scale(0.6F, 0.6F, 0.6F);
        state.item.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}

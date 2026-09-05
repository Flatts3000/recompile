package com.flatts.recompile.client;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.entity.ScrapHaulerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Draws the Scrap Hauler (#376). An ENTITY renderer, so it costs no rule: the one-BlockEntityRenderer
 * exception stays scoped to the Display Pedestal, and this is the same shape as the Roach's and the
 * Pigeon's. The difference is that those two borrow vanilla geometry and this one has its own.
 */
public class ScrapHaulerRenderer extends MobRenderer<ScrapHaulerEntity, ScrapHaulerRenderState, ScrapHaulerModel> {

    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "textures/entity/scrap_hauler_skin.png");

    public ScrapHaulerRenderer(EntityRendererProvider.Context context) {
        super(context, new ScrapHaulerModel(context.bakeLayer(ScrapHaulerModel.LAYER)), 0.5F);
    }

    @Override
    public ScrapHaulerRenderState createRenderState() {
        return new ScrapHaulerRenderState();
    }

    @Override
    public void extractRenderState(ScrapHaulerEntity entity, ScrapHaulerRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.idle = entity.idleAnimation;
        state.drive = entity.driveAnimation;
        state.pickup = entity.pickupAnimation;
    }

    @Override
    public Identifier getTextureLocation(ScrapHaulerRenderState state) {
        return TEXTURE;
    }
}

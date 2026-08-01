package com.flatts.recompile.client;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.entity.RoachEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.silverfish.SilverfishModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Renders the Roach with vanilla's silverfish model and our own skin.
 *
 * <p>The entity is bespoke so it can leave out the summoning behaviour, but there is no reason for the
 * geometry to be: a roach and a silverfish are the same shape and the same gait. Reusing
 * {@link ModelLayers#SILVERFISH} means the whole art budget for the mod's first entity is one texture.
 */
public class RoachRenderer extends MobRenderer<RoachEntity, LivingEntityRenderState, SilverfishModel> {

    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "textures/entity/roach.png");

    public RoachRenderer(EntityRendererProvider.Context context) {
        super(context, new SilverfishModel(context.bakeLayer(ModelLayers.SILVERFISH)), 0.3F);
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}

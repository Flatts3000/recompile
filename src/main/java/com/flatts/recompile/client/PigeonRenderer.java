package com.flatts.recompile.client;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.entity.PigeonEntity;
import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.parrot.Parrot;

/**
 * Renders the Pigeon with vanilla's parrot model and our own skin.
 *
 * <p>Same trade as the Roach and the silverfish: the entity is bespoke so it can leave out everything a
 * parrot does - the shoulder-perching, the dancing, the mob mimicry - while the geometry costs nothing,
 * because a pigeon and a parrot are the same bird-shaped thing. The whole art budget is one texture.
 *
 * <p>{@link ParrotRenderState} carries a {@code variant} that vanilla's renderer uses to choose between
 * five parrot skins. We always return our own texture, so the field is unread here - but it must still
 * be non-null, because the model reads the state before anything gets a chance to look at the texture.
 */
public class PigeonRenderer extends MobRenderer<PigeonEntity, ParrotRenderState, ParrotModel> {

    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "textures/entity/pigeon.png");

    public PigeonRenderer(EntityRendererProvider.Context context) {
        super(context, new ParrotModel(context.bakeLayer(ModelLayers.PARROT)), 0.3F);
    }

    @Override
    public ParrotRenderState createRenderState() {
        ParrotRenderState state = new ParrotRenderState();
        state.variant = Parrot.Variant.RED_BLUE;
        state.pose = ParrotModel.Pose.STANDING;
        return state;
    }

    @Override
    public void extractRenderState(PigeonEntity entity, ParrotRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.variant = Parrot.Variant.RED_BLUE;
        // Wings beat while airborne and settle on the ground. The value MUST stay bounded: the parrot
        // model spends it as a y offset on every part, and y is down, so an unbounded one walks the bird
        // into the ground away from its own hitbox. See PigeonEntity.flapAngle.
        state.flapAngle = PigeonEntity.flapAngle(
            entity.tickCount, partialTick, entity.onGround(), entity.getId());
        state.pose = entity.onGround() ? ParrotModel.Pose.STANDING : ParrotModel.Pose.FLYING;
    }

    @Override
    public Identifier getTextureLocation(ParrotRenderState state) {
        return TEXTURE;
    }
}

package com.flatts.recompile.client;

import com.flatts.recompile.Recompile;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

/**
 * The Scrap Hauler's geometry and motion (#376): <b>the first bespoke entity model in this mod</b>.
 * Both existing entities reuse vanilla geometry; there is no vanilla model for a small tracked robot.
 *
 * <p><b>Authored as code, deliberately</b> (owner, 2026-09-05). In 26.1 a model is boxes with
 * dimensions, offsets and rotations, and an animation is keyframes on channels - arithmetic, not
 * draughtsmanship - so both are diffable, reviewable, and need no GeckoLib and no Blockbench round
 * trip. That is the same argument that made the Puzzle Cube's faces procedural.
 *
 * <p><b>The mesh is generation-friendly on purpose.</b> Few boxes, faces on a predictable grid,
 * generous texels: an entity texture is UV-mapped, and this is the first entity here whose UV layout
 * was not fixed by a vanilla model, so the skin is painted procedurally against exactly these boxes
 * (texgen's {@code hauler_skin} style). The UV origins below are the contract that painter reads.
 *
 * <p>Layout, in model space (y down, 24 = the ground):
 * <ul>
 *   <li><b>body</b> - the hull, 12 wide, 8 tall, 14 long, sitting on the treads.</li>
 *   <li><b>tread_l / tread_r</b> - two rails, 3 wide, 4 tall, 14 long.</li>
 *   <li><b>head</b> - a squat sensor block on the front of the hull, and the part that looks about.</li>
 *   <li><b>arm</b> - a short boom out the front, and the part that dips on a pickup.</li>
 * </ul>
 */
public class ScrapHaulerModel extends EntityModel<ScrapHaulerRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "scrap_hauler"), "main");

    /** The atlas the skin painter targets. */
    public static final int TEXTURE_WIDTH = 64;
    public static final int TEXTURE_HEIGHT = 64;

    private final ModelPart head;
    /** The three motions, baked against this mesh once. 26.1: a definition is data until it is baked. */
    private final KeyframeAnimation idle;
    private final KeyframeAnimation drive;
    private final KeyframeAnimation pickup;

    public ScrapHaulerModel(ModelPart root) {
        super(root);
        ModelPart body = root.getChild("body");
        this.head = body.getChild("head");
        this.idle = IDLE.bake(root);
        this.drive = DRIVE.bake(root);
        this.pickup = PICKUP.bake(root);
    }

    /**
     * The shipped silhouette (owner, 2026-09-05: 0, the one every screenshot has shown).
     *
     * <p><b>This was the texgen candidate directory, for geometry.</b> The owner asked for options on
     * the model rather than one take, and a model cannot go on the texture review page; what it can do
     * is be photographed in a dev client three times. It was selected at launch with
     * {@code -Drecompile.hauler.silhouette=N} while that review was open. <b>The knob is gone now the
     * pick is made</b> - a system property that changes a shipped model is a thing a player can set by
     * accident, and a review instrument has no business surviving the review.
     *
     * <p>{@link #createBodyLayer(int)} keeps all three, because every silhouette holds the SAME box
     * dimensions and differs only in where the parts sit: one skin fits all of them, the UV contract in
     * the class javadoc holds for every one, and the two that lost are the record of what was
     * considered rather than dead weight.
     */
    static final int SILHOUETTE = 0;

    public static LayerDefinition createBodyLayer() {
        return createBodyLayer(SILHOUETTE);
    }

    /**
     * Three candidates, same boxes, different stances:
     * <ul>
     *   <li><b>0, compact</b> - hull low on the treads, head tucked at the front. The one that ships.</li>
     *   <li><b>1, periscope</b> - hull raised, head up on a taller neck looking over the piles,
     *       treads spread wider.</li>
     *   <li><b>2, low-rider</b> - hull dropped between the treads, head forward and low, arm reaching
     *       further out front.</li>
     * </ul>
     */
    public static LayerDefinition createBodyLayer(int silhouette) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        float hullY = switch (silhouette) { case 1 -> 18.0F; case 2 -> 21.0F; default -> 20.0F; };
        float headY = switch (silhouette) { case 1 -> -11.0F; case 2 -> -6.0F; default -> -8.0F; };
        float headZ = switch (silhouette) { case 1 -> -1.0F; case 2 -> -5.0F; default -> -3.0F; };
        float armZ = switch (silhouette) { case 2 -> -9.0F; default -> -7.0F; };
        float treadX = switch (silhouette) { case 1 -> 8.5F; case 2 -> 7.0F; default -> 7.5F; };

        // Hull: UV origin (0, 0). A 12x8x14 box unwraps to 52 wide by 22 tall.
        PartDefinition body = root.addOrReplaceChild("body",
            CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -8.0F, -7.0F, 12.0F, 8.0F, 14.0F),
            PartPose.offset(0.0F, hullY, 0.0F));

        // Head: UV origin (0, 22). An 8x5x6 box unwraps to 28 wide by 11 tall.
        body.addOrReplaceChild("head",
            CubeListBuilder.create().texOffs(0, 22).addBox(-4.0F, -5.0F, -3.0F, 8.0F, 5.0F, 6.0F),
            PartPose.offset(0.0F, headY, headZ));

        // Arm: UV origin (28, 22). A 2x2x6 box unwraps to 16 wide by 8 tall.
        body.addOrReplaceChild("arm",
            CubeListBuilder.create().texOffs(28, 22).addBox(-1.0F, -1.0F, -6.0F, 2.0F, 2.0F, 6.0F),
            PartPose.offset(0.0F, -2.0F, armZ));

        // Treads: UV origin (0, 33). A 3x4x14 box unwraps to 34 wide by 18 tall; a second strip at
        // (0, 51) would run to 69 and off the 64-row atlas, so the right tread mirrors the left's UV.
        root.addOrReplaceChild("tread_l",
            CubeListBuilder.create().texOffs(0, 33).addBox(-1.5F, -4.0F, -7.0F, 3.0F, 4.0F, 14.0F),
            PartPose.offset(-treadX, 24.0F, 0.0F));
        root.addOrReplaceChild("tread_r",
            CubeListBuilder.create().texOffs(0, 33).mirror().addBox(-1.5F, -4.0F, -7.0F, 3.0F, 4.0F, 14.0F),
            PartPose.offset(treadX, 24.0F, 0.0F));

        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    // ---- motion --------------------------------------------------------------------------------

    /** Idle: the head looks slowly about, the way a machine waiting for work would. Two-second loop. */
    public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(2.0F).looping()
        .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, -12.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.0F, KeyframeAnimations.degreeVec(0.0F, 12.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, -12.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)))
        .build();

    /** Driving: the hull rocks on its treads. Half-second loop, small, so it reads as motion not damage. */
    public static final AnimationDefinition DRIVE = AnimationDefinition.Builder.withLength(0.5F).looping()
        .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-2.0F, 0.0F, 1.5F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.25F, KeyframeAnimations.degreeVec(2.0F, 0.0F, -1.5F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.5F, KeyframeAnimations.degreeVec(-2.0F, 0.0F, 1.5F), AnimationChannel.Interpolations.CATMULLROM)))
        .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
            new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, 0.5F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)))
        .build();

    /** Pickup: the arm dips and comes back. Not looped; it is restarted by the entity on each take. */
    public static final AnimationDefinition PICKUP = AnimationDefinition.Builder.withLength(0.6F)
        .addAnimation("arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.3F, KeyframeAnimations.degreeVec(35.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.6F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)))
        .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.3F, KeyframeAnimations.degreeVec(18.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.6F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)))
        .build();

    @Override
    public void setupAnim(ScrapHaulerRenderState state) {
        super.setupAnim(state);
        this.idle.apply(state.idle, state.ageInTicks);
        this.drive.apply(state.drive, state.ageInTicks);
        this.pickup.apply(state.pickup, state.ageInTicks);
        // The head follows the look direction on top of whatever the animation left it at.
        this.head.yRot += state.yRot * ((float) Math.PI / 180F) * 0.4F;
        this.head.xRot += state.xRot * ((float) Math.PI / 180F) * 0.4F;
    }
}

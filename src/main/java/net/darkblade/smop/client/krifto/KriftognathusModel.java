package net.darkblade.smop.client.krifto;

import net.darkblade.deluxelib.client.render.DeluxeEntityRenderState;
import net.darkblade.deluxelib.client.rig.AnimContext;
import net.darkblade.deluxelib.client.rig.Rig;
import net.darkblade.smop.SMOP;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import org.jetbrains.annotations.NotNull;

/**
 * Adult Kriftognathus. Geometry is the untouched Blockbench export; what changed in the port is that
 * {@code setupAnim} no longer hand-drives a dozen {@code AnimationState}s — the {@link Rig} resets
 * the pose, blends whatever the {@code MobAnimator} says is playing, and layers head tracking on top.
 *
 * <p>The look-at goes on the neck rather than the head: this is a long-necked animal, and turning
 * only the skull reads as the head swivelling independently of the body.
 */
public class KriftognathusModel extends EntityModel<DeluxeEntityRenderState> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(SMOP.id("kriftognathus"), "main");

    private static final Rig<KriftognathusModel> RIG =
            Rig.<KriftognathusModel>builder()
                    .resetPoses()
                    .keyframeBlend(220L, 0)
                    // Layer 1: bite_flight (gLower_jaw only). Applied after layer 0 so the jaw snap
                    // composites additively on top of whatever locomotion/flight clip is current
                    // instead of replacing it — see KriftoAnimations#bite_flight.
                    .keyframeBlend(80L, 1)
                    .lookAt(m -> m.neck, 35.0F, 30.0F)
                    .build();

    public final ModelPart neck;

    public KriftognathusModel(ModelPart root) {
        super(root);
        this.neck = root.getChild("gPiglug").getChild("gBody_parts").getChild("gNeck");
    }

    @Override
    public void setupAnim(@NotNull DeluxeEntityRenderState state) {
        RIG.apply(state, this, AnimContext.from(state));
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition gPiglug = partdefinition.addOrReplaceChild("gPiglug", CubeListBuilder.create(), PartPose.offset(0.0F, 18.0086F, 4.1305F));

        PartDefinition gBody_parts = gPiglug.addOrReplaceChild("gBody_parts", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition body_r1 = gBody_parts.addOrReplaceChild("body_r1", CubeListBuilder.create().texOffs(0, 35).addBox(-2.5F, -7.0F, -5.0F, 0.0F, 3.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(-5.0F, -4.0F, -7.0F, 5.0F, 5.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, 0.0F, 0.0F, -0.1309F, 0.0F, 0.0F));

        PartDefinition gNeck = gBody_parts.addOrReplaceChild("gNeck", CubeListBuilder.create().texOffs(14, 30).addBox(-1.5F, -9.0F, -2.5F, 3.0F, 9.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(48, 28).addBox(0.0F, -4.0F, -4.5F, 0.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(14, 20).addBox(-2.5F, -6.0F, -3.0F, 5.0F, 4.0F, 6.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, -1.0086F, -6.6305F));

        PartDefinition gHGead = gNeck.addOrReplaceChild("gHGead", CubeListBuilder.create().texOffs(38, 44).addBox(-2.0F, -3.0F, 0.0F, 4.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(26, 0).addBox(-2.0F, -3.0F, -5.0F, 4.0F, 3.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(36, 20).addBox(-1.5F, -2.0F, -10.0F, 3.0F, 3.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(30, 37).addBox(-1.5F, 1.0F, -10.0F, 3.0F, 2.0F, 5.0F, new CubeDeformation(0.01F))
                .texOffs(26, 44).addBox(-2.0F, -6.0F, -5.0F, 1.0F, 3.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(50, 38).addBox(0.0F, -5.0F, -4.0F, 0.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(26, 44).mirror().addBox(1.0F, -6.0F, -5.0F, 1.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, -6.5F, -2.5F));

        PartDefinition head_r1 = gHGead.addOrReplaceChild("head_r1", CubeListBuilder.create().texOffs(56, 34).mirror().addBox(0.0F, -3.0F, 0.0F, 0.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-2.0F, -1.0F, 2.0F, 0.0F, -0.7854F, 0.0F));

        PartDefinition head_r2 = gHGead.addOrReplaceChild("head_r2", CubeListBuilder.create().texOffs(56, 34).addBox(0.0F, -3.0F, 0.0F, 0.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, -1.0F, 2.0F, 0.0F, 0.7854F, 0.0F));

        PartDefinition gEyes = gHGead.addOrReplaceChild("gEyes", CubeListBuilder.create().texOffs(26, 8).addBox(-2.0F, -1.0F, -1.5F, 4.0F, 2.0F, 3.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, -1.0F, -3.5F));

        PartDefinition gRight_pupil = gEyes.addOrReplaceChild("gRight_pupil", CubeListBuilder.create().texOffs(36, 28).addBox(0.025F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.02F)), PartPose.offset(-2.025F, -0.5F, -1.0F));

        PartDefinition gLeft_pupil = gEyes.addOrReplaceChild("gLeft_pupil", CubeListBuilder.create().texOffs(36, 28).mirror().addBox(-1.025F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.02F)).mirror(false), PartPose.offset(2.025F, -0.5F, -1.0F));

        PartDefinition gLower_jaw = gHGead.addOrReplaceChild("gLower_jaw", CubeListBuilder.create().texOffs(30, 30).addBox(-2.0F, 0.0F, -5.0F, 4.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(0, 44).addBox(-1.5F, 1.0F, -10.0F, 3.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(42, 8).addBox(-1.5F, -1.0F, -10.0F, 3.0F, 2.0F, 5.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition gGoiter = gLower_jaw.addOrReplaceChild("gGoiter", CubeListBuilder.create().texOffs(44, 0).addBox(-1.5F, -1.0F, -2.0F, 3.0F, 3.0F, 4.0F, new CubeDeformation(-0.01F)), PartPose.offset(0.0F, 2.0F, -2.0F));

        PartDefinition gTail = gBody_parts.addOrReplaceChild("gTail", CubeListBuilder.create(), PartPose.offset(0.0F, -3.8353F, 1.5135F));

        PartDefinition tail_r1 = gTail.addOrReplaceChild("tail_r1", CubeListBuilder.create().texOffs(14, 13).addBox(-3.5F, 0.0F, 0.0F, 7.0F, 0.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.1309F, 0.0F, 0.0F));

        PartDefinition gLegs = gPiglug.addOrReplaceChild("gLegs", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition gFront_legs = gLegs.addOrReplaceChild("gFront_legs", CubeListBuilder.create(), PartPose.offset(0.0F, -2.0086F, -4.6305F));

        PartDefinition gRight_leg1 = gFront_legs.addOrReplaceChild("gRight_leg1", CubeListBuilder.create().texOffs(16, 44).addBox(-1.0F, 0.0F, -1.5F, 2.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(10, 50).addBox(-1.0F, 0.0F, -3.5F, 0.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(12, 35).addBox(-1.0F, 1.0F, 1.5F, 0.0F, 7.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.5F, 0.0F, 0.0F));

        PartDefinition gRight_claws1 = gRight_leg1.addOrReplaceChild("gRight_claws1", CubeListBuilder.create().texOffs(42, 15).addBox(-1.5F, 0.0F, 0.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 7.0F, 1.0F));

        PartDefinition gRight_wing = gRight_leg1.addOrReplaceChild("gRight_wing", CubeListBuilder.create(), PartPose.offset(-1.0F, 8.0F, 0.0F));

        PartDefinition right_wing_r1 = gRight_wing.addOrReplaceChild("right_wing_r1", CubeListBuilder.create().texOffs(0, 13).mirror().addBox(0.0F, -15.0F, -2.5F, 0.0F, 15.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, -0.1745F));

        PartDefinition gLeft_leg1 = gFront_legs.addOrReplaceChild("gLeft_leg1", CubeListBuilder.create().texOffs(16, 44).mirror().addBox(-1.0F, 0.0F, -1.5F, 2.0F, 8.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(10, 50).mirror().addBox(1.0F, 0.0F, -3.5F, 0.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(12, 35).mirror().addBox(1.0F, 1.0F, 1.5F, 0.0F, 7.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(2.5F, 0.0F, 0.0F));

        PartDefinition gLeft_claws1 = gLeft_leg1.addOrReplaceChild("gLeft_claws1", CubeListBuilder.create().texOffs(42, 15).mirror().addBox(-1.5F, 0.0F, 0.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 7.0F, 1.0F));

        PartDefinition gLeft_wing = gLeft_leg1.addOrReplaceChild("gLeft_wing", CubeListBuilder.create(), PartPose.offset(1.0F, 8.0F, 0.0F));

        PartDefinition left_wing_r1 = gLeft_wing.addOrReplaceChild("left_wing_r1", CubeListBuilder.create().texOffs(0, 13).addBox(0.0F, -15.0F, -2.5F, 0.0F, 15.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 0.1745F));

        PartDefinition gBack_legs = gLegs.addOrReplaceChild("gBack_legs", CubeListBuilder.create(), PartPose.offset(0.0F, -0.5086F, 0.3695F));

        PartDefinition gRight_leg2 = gBack_legs.addOrReplaceChild("gRight_leg2", CubeListBuilder.create().texOffs(0, 50).addBox(-1.0F, -0.5F, -1.5F, 2.0F, 4.0F, 3.0F, new CubeDeformation(0.01F)), PartPose.offset(-2.0F, 0.0F, 0.0F));

        PartDefinition gRight_calf = gRight_leg2.addOrReplaceChild("gRight_calf", CubeListBuilder.create().texOffs(50, 45).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 1.5F, 1.5F));

        PartDefinition gRight_claws2 = gRight_calf.addOrReplaceChild("gRight_claws2", CubeListBuilder.create().texOffs(38, 51).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.0F, -0.5F));

        PartDefinition gLeft_leg2 = gBack_legs.addOrReplaceChild("gLeft_leg2", CubeListBuilder.create().texOffs(0, 50).mirror().addBox(-1.0F, -0.5F, -1.5F, 2.0F, 4.0F, 3.0F, new CubeDeformation(0.01F)).mirror(false), PartPose.offset(2.0F, 0.0F, 0.0F));

        PartDefinition gLeft_calf = gLeft_leg2.addOrReplaceChild("gLeft_calf", CubeListBuilder.create().texOffs(50, 45).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 1.5F, 1.5F));

        PartDefinition gLeft_claws2 = gLeft_calf.addOrReplaceChild("gLeft_claws2", CubeListBuilder.create().texOffs(38, 51).mirror().addBox(-1.5F, 0.0F, -2.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 4.0F, -0.5F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }
}

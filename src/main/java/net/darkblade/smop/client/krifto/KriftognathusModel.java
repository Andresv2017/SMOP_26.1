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
 *
 * <p><b>Bone naming.</b> This export dropped the {@code g} prefix the previous one carried
 * ({@code gPiglug} → {@code piglug}), and renamed the misspelled {@code gHGead} to {@code head}. Every
 * clip in {@code KriftoAnimations} addresses bones by name at bake time and throws
 * {@code IllegalArgumentException} on a name this mesh does not define, so the two files have to agree
 * exactly — there is no partial match and no fallback.
 */
public class KriftognathusModel extends EntityModel<DeluxeEntityRenderState> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(SMOP.id("kriftognathus"), "main");

    private static final Rig<KriftognathusModel> RIG =
            Rig.<KriftognathusModel>builder()
                    .resetPoses()
                    .keyframeBlend(220L, 0)
                    // Layer 1: the jaw overlay, bite_flight (gLower_jaw only). Applied after layer 0
                    // so it composites additively on top of whatever locomotion/flight clip is
                    // current instead of replacing it, which would drop every bone it does not
                    // author back to the bind pose.
                    .keyframeBlend(80L, 1)
                    .lookAt(m -> m.neck, 35.0F, 30.0F)
                    .build();

    public final ModelPart neck;
    /**
     * The chain a talon-attached render layer has to walk by hand to land in the right spot — see
     * {@link StolenItemLayer}. {@code legs} sits at a permanent zero offset today (nothing ever
     * animates it), but it is exposed and walked anyway rather than skipped: skipping it silently
     * relies on that staying true forever.
     */
    public final ModelPart piglug;
    public final ModelPart legs;
    public final ModelPart backLegs;

    public KriftognathusModel(ModelPart root) {
        super(root);
        this.piglug = root.getChild("piglug");
        this.neck = this.piglug.getChild("body_parts").getChild("neck");
        this.legs = this.piglug.getChild("legs");
        this.backLegs = this.legs.getChild("back_legs");
    }

    @Override
    public void setupAnim(@NotNull DeluxeEntityRenderState state) {
        RIG.apply(state, this, AnimContext.from(state));
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition piglug = partdefinition.addOrReplaceChild("piglug", CubeListBuilder.create(), PartPose.offset(0.0F, 18.0086F, 4.1305F));

        PartDefinition body_parts = piglug.addOrReplaceChild("body_parts", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition body_r1 = body_parts.addOrReplaceChild("body_r1", CubeListBuilder.create().texOffs(0, 35).addBox(-2.5F, -7.0F, -5.0F, 0.0F, 3.0F, 6.0F, new CubeDeformation(0.0F))
        .texOffs(0, 0).addBox(-5.0F, -4.0F, -7.0F, 5.0F, 5.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, 0.0F, 0.0F, -0.1309F, 0.0F, 0.0F));

        PartDefinition neck = body_parts.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(14, 30).addBox(-1.5F, -9.0F, -2.5F, 3.0F, 9.0F, 5.0F, new CubeDeformation(-0.01F))
        .texOffs(48, 28).addBox(0.0F, -4.0F, -4.5F, 0.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
        .texOffs(14, 20).addBox(-2.5F, -6.0F, -3.0F, 5.0F, 4.0F, 6.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, -1.0086F, -6.6305F));

        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(38, 44).addBox(-2.0F, -3.0F, 0.0F, 4.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(26, 0).addBox(-2.0F, -3.0F, -5.0F, 4.0F, 3.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(36, 20).addBox(-1.5F, -2.0F, -10.0F, 3.0F, 3.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(30, 37).addBox(-1.5F, 1.0F, -10.0F, 3.0F, 2.0F, 5.0F, new CubeDeformation(0.01F))
        .texOffs(26, 44).addBox(-2.0F, -6.0F, -5.0F, 1.0F, 3.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(50, 38).addBox(0.0F, -5.0F, -4.0F, 0.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(26, 44).mirror().addBox(1.0F, -6.0F, -5.0F, 1.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false)
        .texOffs(0, 13).addBox(-1.5F, 0.0F, -3.0F, 3.0F, 2.0F, 3.0F, new CubeDeformation(-0.01F)), PartPose.offset(0.0F, -6.5F, -2.5F));

        PartDefinition head_r1 = head.addOrReplaceChild("head_r1", CubeListBuilder.create().texOffs(56, 34).mirror().addBox(0.0F, -3.0F, 0.0F, 0.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-2.0F, -1.0F, 2.0F, 0.0F, -0.7854F, 0.0F));

        PartDefinition head_r2 = head.addOrReplaceChild("head_r2", CubeListBuilder.create().texOffs(56, 34).addBox(0.0F, -3.0F, 0.0F, 0.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, -1.0F, 2.0F, 0.0F, 0.7854F, 0.0F));

        PartDefinition eyes = head.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(26, 8).addBox(-2.0F, -1.0F, -1.5F, 4.0F, 2.0F, 3.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, -1.0F, -3.5F));

        PartDefinition right_pupil = eyes.addOrReplaceChild("right_pupil", CubeListBuilder.create().texOffs(36, 28).addBox(0.025F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.02F)), PartPose.offset(-2.025F, -0.5F, -1.0F));

        PartDefinition left_pupil = eyes.addOrReplaceChild("left_pupil", CubeListBuilder.create().texOffs(36, 28).mirror().addBox(-1.025F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.02F)).mirror(false), PartPose.offset(2.025F, -0.5F, -1.0F));

        PartDefinition lower_jaw = head.addOrReplaceChild("lower_jaw", CubeListBuilder.create().texOffs(30, 30).addBox(-2.0F, 0.0F, -5.0F, 4.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(0, 44).addBox(-1.5F, 1.0F, -10.0F, 3.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(42, 8).addBox(-1.5F, -1.0F, -10.0F, 3.0F, 2.0F, 5.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition goiter = lower_jaw.addOrReplaceChild("goiter", CubeListBuilder.create().texOffs(44, 0).addBox(-1.5F, -1.0F, -2.0F, 3.0F, 3.0F, 4.0F, new CubeDeformation(-0.01F)), PartPose.offset(0.0F, 2.0F, -2.0F));

        PartDefinition tail = body_parts.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offset(0.0F, -3.8353F, 1.5135F));

        PartDefinition tail_r1 = tail.addOrReplaceChild("tail_r1", CubeListBuilder.create().texOffs(14, 13).addBox(-3.5F, 0.0F, 0.0F, 7.0F, 0.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.1309F, 0.0F, 0.0F));

        PartDefinition legs = piglug.addOrReplaceChild("legs", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition front_legs = legs.addOrReplaceChild("front_legs", CubeListBuilder.create(), PartPose.offset(0.0F, -2.0086F, -4.6305F));

        PartDefinition right_leg1 = front_legs.addOrReplaceChild("right_leg1", CubeListBuilder.create().texOffs(16, 44).addBox(-1.0F, 0.0F, -1.5F, 2.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(10, 50).addBox(-1.0F, 0.0F, -3.5F, 0.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(12, 35).addBox(-1.0F, 1.0F, 1.5F, 0.0F, 7.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.5F, 0.0F, 0.0F));

        PartDefinition right_claws1 = right_leg1.addOrReplaceChild("right_claws1", CubeListBuilder.create().texOffs(42, 15).addBox(-1.5F, 0.0F, 0.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 7.0F, 1.0F));

        PartDefinition right_wing = right_leg1.addOrReplaceChild("right_wing", CubeListBuilder.create(), PartPose.offset(-1.0F, 8.0F, 0.0F));

        PartDefinition right_wing_r1 = right_wing.addOrReplaceChild("right_wing_r1", CubeListBuilder.create().texOffs(0, 13).mirror().addBox(0.0F, -15.0F, -2.5F, 0.0F, 15.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, -0.1745F));

        PartDefinition left_leg1 = front_legs.addOrReplaceChild("left_leg1", CubeListBuilder.create().texOffs(16, 44).mirror().addBox(-1.0F, 0.0F, -1.5F, 2.0F, 8.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false)
        .texOffs(10, 50).mirror().addBox(1.0F, 0.0F, -3.5F, 0.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
        .texOffs(12, 35).mirror().addBox(1.0F, 1.0F, 1.5F, 0.0F, 7.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(2.5F, 0.0F, 0.0F));

        PartDefinition left_claws1 = left_leg1.addOrReplaceChild("left_claws1", CubeListBuilder.create().texOffs(42, 15).mirror().addBox(-1.5F, 0.0F, 0.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 7.0F, 1.0F));

        PartDefinition left_wing = left_leg1.addOrReplaceChild("left_wing", CubeListBuilder.create(), PartPose.offset(1.0F, 8.0F, 0.0F));

        PartDefinition left_wing_r1 = left_wing.addOrReplaceChild("left_wing_r1", CubeListBuilder.create().texOffs(0, 13).addBox(0.0F, -15.0F, -2.5F, 0.0F, 15.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 0.1745F));

        PartDefinition back_legs = legs.addOrReplaceChild("back_legs", CubeListBuilder.create(), PartPose.offset(0.0F, -0.5086F, 0.3695F));

        PartDefinition right_leg2 = back_legs.addOrReplaceChild("right_leg2", CubeListBuilder.create().texOffs(0, 50).addBox(-1.0F, -0.5F, -1.5F, 2.0F, 4.0F, 3.0F, new CubeDeformation(0.01F)), PartPose.offset(-2.0F, 0.0F, 0.0F));

        PartDefinition right_calf = right_leg2.addOrReplaceChild("right_calf", CubeListBuilder.create().texOffs(50, 45).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 1.5F, 1.5F));

        PartDefinition right_claws2 = right_calf.addOrReplaceChild("right_claws2", CubeListBuilder.create().texOffs(38, 51).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.0F, -0.5F));

        PartDefinition left_leg2 = back_legs.addOrReplaceChild("left_leg2", CubeListBuilder.create().texOffs(0, 50).mirror().addBox(-1.0F, -0.5F, -1.5F, 2.0F, 4.0F, 3.0F, new CubeDeformation(0.01F)).mirror(false), PartPose.offset(2.0F, 0.0F, 0.0F));

        PartDefinition left_calf = left_leg2.addOrReplaceChild("left_calf", CubeListBuilder.create().texOffs(50, 45).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 1.5F, 1.5F));

        PartDefinition left_claws2 = left_calf.addOrReplaceChild("left_claws2", CubeListBuilder.create().texOffs(38, 51).mirror().addBox(-1.5F, 0.0F, -2.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 4.0F, -0.5F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }
}

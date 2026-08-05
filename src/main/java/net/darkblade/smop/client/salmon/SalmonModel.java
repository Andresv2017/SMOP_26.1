package net.darkblade.smop.client.salmon;

import net.darkblade.deluxelib.client.render.DeluxeEntityRenderState;
import net.darkblade.deluxelib.client.rig.AnimContext;
import net.darkblade.deluxelib.client.rig.Rig;
import net.darkblade.deluxelib.client.rig.component.LookAtAdditive;
import net.darkblade.smop.SMOP;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import org.jetbrains.annotations.NotNull;

/**
 * The salmon. Geometry is the untouched Blockbench export.
 *
 * <p><b>Port note.</b> 1.20.1's {@code setupAnim} both drove ten {@code AnimationState}s by hand and
 * then layered a block of manual trigonometry on top — head/body/tail yaw and pitch derived from
 * {@code netHeadYaw}, {@code headPitch} and the entity's own {@code currentRoll} field, guarded by a
 * hand-written "not sleeping and not attacking" check. The {@link Rig} covers both halves: the
 * keyframe blend plays whatever the {@code MobAnimator} says is current, and a look-at chain
 * distributes the turn down head → body → tail, which is what the manual maths was approximating.
 *
 * <p>Distributing it as a chain rather than dumping it on the head is what makes a fish read as a
 * fish: the body leads into the turn and the tail trails it. The old code did this with three
 * hand-tuned coefficients (0.35 / 0.20 / -0.45); here they are the chain's shares, and the tail's
 * share is negative for the same reason it was there — it sweeps opposite to the head.
 */
public class SalmonModel extends EntityModel<DeluxeEntityRenderState> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(SMOP.id("salmon"), "main");

    private static final Rig<SalmonModel> RIG =
            Rig.<SalmonModel>builder()
                    .resetPoses()
                    .keyframeBlend(200L, 0)
                    .lookAtChain(35.0F, 25.0F,
                            LookAtAdditive.link(m -> m.head, 0.55F),
                            LookAtAdditive.link(m -> m.bodyParts, 0.30F),
                            LookAtAdditive.link(m -> m.tail1, -0.45F))
                    .build();

    public final ModelPart bodyParts;
    public final ModelPart head;
    public final ModelPart tail1;

    public SalmonModel(ModelPart root) {
        super(root);
        this.bodyParts = root.getChild("MVNF").getChild("body_parts");
        this.head = this.bodyParts.getChild("head");
        this.tail1 = this.bodyParts.getChild("tail1");
    }

    @Override
    public void setupAnim(@NotNull DeluxeEntityRenderState state) {
        RIG.apply(state, this, AnimContext.from(state));
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition MVNF = partdefinition.addOrReplaceChild("MVNF", CubeListBuilder.create(), PartPose.offset(0.0F, 18.96F, 2.88F));

        PartDefinition body_parts = MVNF.addOrReplaceChild("body_parts", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -9.46F, -9.88F, 6.0F, 15.0F, 12.0F, new CubeDeformation(0.0F))
                .texOffs(0, 49).addBox(0.0F, -14.46F, -0.88F, 0.0F, 11.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition head = body_parts.addOrReplaceChild("head", CubeListBuilder.create().texOffs(60, 46).addBox(-2.0F, -6.68F, -2.8F, 4.0F, 11.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(34, 46).addBox(-2.0F, -6.68F, -11.8F, 4.0F, 6.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(0, 33).addBox(-2.0F, -0.68F, -9.8F, 4.0F, 3.0F, 7.0F, new CubeDeformation(-0.01F)), PartPose.offset(0.0F, 0.72F, -10.08F));

        head.addOrReplaceChild("pupils", CubeListBuilder.create().texOffs(33, 53).addBox(-2.0F, -0.5F, -0.5F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, -2.18F, -4.3F));

        PartDefinition upper_whiskers = head.addOrReplaceChild("upper_whiskers", CubeListBuilder.create(), PartPose.offset(0.0F, -3.6F, -9.0F));

        PartDefinition upper_right_whisker = upper_whiskers.addOrReplaceChild("upper_right_whisker", CubeListBuilder.create(), PartPose.offset(-2.16F, 0.0F, 0.0F));
        upper_right_whisker.addOrReplaceChild("upper_right_whisker_r1", CubeListBuilder.create().texOffs(64, 63).addBox(0.0F, -6.0F, -2.5F, 0.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.16F, -0.08F, -0.3F, 0.1745F, 0.0F, -0.1745F));

        PartDefinition upper_left_whisker = upper_whiskers.addOrReplaceChild("upper_left_whisker", CubeListBuilder.create(), PartPose.offset(2.16F, 0.0F, 0.0F));
        upper_left_whisker.addOrReplaceChild("upper_left_whisker_r1", CubeListBuilder.create().texOffs(64, 63).mirror().addBox(0.0F, -6.0F, -2.5F, 0.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-0.16F, -0.08F, -0.3F, 0.1745F, 0.0F, 0.1745F));

        PartDefinition lower_whiskers = head.addOrReplaceChild("lower_whiskers", CubeListBuilder.create(), PartPose.offset(0.0F, -1.44F, -8.28F));

        PartDefinition lower_right_whisker = lower_whiskers.addOrReplaceChild("lower_right_whisker", CubeListBuilder.create(), PartPose.offset(-2.16F, 0.0F, 0.0F));
        lower_right_whisker.addOrReplaceChild("lower_right_whisker_r1", CubeListBuilder.create().texOffs(58, 63).addBox(0.0F, 0.01F, -2.52F, 0.0F, 8.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.16F, -0.25F, 0.0F, 0.1745F, 0.0F, 0.1745F));

        PartDefinition lower_left_whisker = lower_whiskers.addOrReplaceChild("lower_left_whisker", CubeListBuilder.create(), PartPose.offset(2.16F, 0.0F, 0.0F));
        lower_left_whisker.addOrReplaceChild("lower_left_whisker_r1", CubeListBuilder.create().texOffs(58, 63).mirror().addBox(0.0F, 0.01F, -2.52F, 0.0F, 8.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-0.16F, -0.25F, 0.0F, 0.1745F, 0.0F, -0.1745F));

        head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(32, 61).addBox(-2.0F, -5.96F, -1.84F, 4.0F, 12.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(0, 69).addBox(-2.0F, 0.04F, -2.84F, 4.0F, 9.0F, 6.0F, new CubeDeformation(-0.01F)), PartPose.offset(0.0F, -0.72F, -12.96F));

        PartDefinition lower_jaw = head.addOrReplaceChild("lower_jaw", CubeListBuilder.create().texOffs(58, 16).addBox(-2.0F, -1.0F, -7.0F, 4.0F, 1.0F, 7.0F, new CubeDeformation(-0.01F))
                .texOffs(36, 16).addBox(-2.0F, 0.0F, -7.0F, 4.0F, 2.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 2.32F, -2.8F));

        lower_jaw.addOrReplaceChild("lip_r1", CubeListBuilder.create().texOffs(36, 25).addBox(-1.5F, -1.0F, 0.0F, 3.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -7.0F, 0.6545F, 0.0F, 0.0F));
        lower_jaw.addOrReplaceChild("lower_whisker", CubeListBuilder.create().texOffs(16, 64).addBox(0.0F, 0.0F, -1.96F, 0.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 2.0F, -4.04F));

        PartDefinition front_fins = body_parts.addOrReplaceChild("front_fins", CubeListBuilder.create(), PartPose.offset(0.0F, 5.04F, -8.88F));

        PartDefinition right_front_fin = front_fins.addOrReplaceChild("right_front_fin", CubeListBuilder.create(), PartPose.offset(-3.0F, 0.0F, 0.0F));
        right_front_fin.addOrReplaceChild("fin_r1", CubeListBuilder.create().texOffs(62, 24).addBox(0.0F, -2.0F, 0.0F, 0.0F, 6.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.0873F, 0.0F));

        PartDefinition left_front_fin = front_fins.addOrReplaceChild("left_front_fin", CubeListBuilder.create(), PartPose.offset(3.0F, 0.0F, 0.0F));
        left_front_fin.addOrReplaceChild("fin_r2", CubeListBuilder.create().texOffs(62, 24).mirror().addBox(0.0F, -2.0F, 0.0F, 0.0F, 6.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0873F, 0.0F));

        PartDefinition back_fins = body_parts.addOrReplaceChild("back_fins", CubeListBuilder.create(), PartPose.offset(0.0F, 4.54F, -1.38F));

        back_fins.addOrReplaceChild("right_back_fin", CubeListBuilder.create().texOffs(63, 39).addBox(0.2288F, 0.0289F, -1.0018F, 0.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-3.0F, 1.0F, 1.0F));
        back_fins.addOrReplaceChild("left_back_fin", CubeListBuilder.create().texOffs(63, 39).mirror().addBox(-0.2288F, 0.0289F, -1.0018F, 0.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(3.0F, 1.0F, 1.0F));

        PartDefinition tail1 = body_parts.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(34, 27).addBox(-2.5F, -5.0F, -1.5F, 5.0F, 10.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -0.46F, 2.62F));

        PartDefinition tail2 = tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(26, 65).addBox(0.0F, -4.5F, 1.5F, 0.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(36, 0).addBox(-2.0F, -2.5F, -2.5F, 4.0F, 5.0F, 11.0F, new CubeDeformation(0.0F))
                .texOffs(46, 61).addBox(0.0F, 2.5F, -3.5F, 0.0F, 5.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 1.5F, 8.0F));

        tail2.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(18, 49).addBox(0.0F, -5.5F, 0.0F, 0.0F, 10.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 6.5F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }
}

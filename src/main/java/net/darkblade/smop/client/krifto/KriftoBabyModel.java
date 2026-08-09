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
 * Kriftognathus chick — a genuinely different skeleton from the adult, not a scaled copy. The bone
 * names now match the adult's (both dropped the {@code g} prefix), but the rig does not: the chick
 * has <b>no {@code calf} and no {@code claws} bones</b>. Its back leg is a single segment and its
 * feet are part of the leg. That is why the entity still picks its clip definition by age through a
 * supplier rather than sharing one set — an adult clip baked against this model throws on the first
 * missing bone, on the first frame the chick is rendered.
 */
public class KriftoBabyModel extends EntityModel<DeluxeEntityRenderState> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(SMOP.id("kriftognathus_baby"), "main");

    private static final Rig<KriftoBabyModel> RIG =
            Rig.<KriftoBabyModel>builder()
                    .resetPoses()
                    .keyframeBlend(220L, 0)
                    .lookAt(m -> m.neck, 35.0F, 30.0F)
                    .build();

    public final ModelPart neck;

    public KriftoBabyModel(ModelPart root) {
        super(root);
        this.neck = root.getChild("piglug").getChild("body_parts").getChild("neck");
    }

    @Override
    public void setupAnim(@NotNull DeluxeEntityRenderState state) {
        RIG.apply(state, this, AnimContext.from(state));
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition piglug = partdefinition.addOrReplaceChild("piglug", CubeListBuilder.create(), PartPose.offset(0.0F, 20.5F, 0.5F));

        PartDefinition body_parts = piglug.addOrReplaceChild("body_parts", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -2.5F, -4.5F, 3.0F, 3.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(16, 27).addBox(0.0F, -4.5F, -2.5F, 0.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.1309F, 0.0F, 0.0F));

        PartDefinition neck = body_parts.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(8, 12).addBox(-1.0F, -5.0F, -1.5F, 2.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.5F, -4.0F, 0.1309F, 0.0F, 0.0F));

        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(28, 0).addBox(0.0F, -4.0F, -3.0F, 0.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(8, 27).addBox(-1.5F, -2.0F, -1.0F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(0, 25).addBox(-1.0F, -2.0F, -6.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(28, 22).addBox(-1.0F, 0.0F, -6.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.01F))
        .texOffs(16, 0).addBox(-1.5F, -2.0F, -4.0F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(2, 9).addBox(-1.5F, 0.0F, -3.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(-0.01F)), PartPose.offset(0.0F, -3.0F, -1.0F));

        PartDefinition cube_r1 = head.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(18, 17).addBox(-1.5F, -2.0F, 0.0F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.0F, -4.0F, -0.2618F, 0.0F, 0.0F));

        PartDefinition eyes = head.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(18, 12).addBox(-0.5F, -1.0F, -1.5F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.01F)), PartPose.offset(-1.0F, -1.0F, -2.5F));

        PartDefinition left_pupil = eyes.addOrReplaceChild("left_pupil", CubeListBuilder.create().texOffs(8, 25).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.02F)), PartPose.offset(2.0F, -0.5F, -1.0F));

        PartDefinition right_pupil = eyes.addOrReplaceChild("right_pupil", CubeListBuilder.create().texOffs(8, 25).mirror().addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.02F)).mirror(false), PartPose.offset(0.0F, -0.5F, -1.0F));

        PartDefinition lower_jaw = head.addOrReplaceChild("lower_jaw", CubeListBuilder.create().texOffs(0, 21).addBox(-1.5F, 0.0F, -3.0F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(16, 5).addBox(-1.0F, 0.0F, -5.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(28, 22).addBox(-1.0F, -1.0F, -4.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.01F))
        .texOffs(0, 21).mirror().addBox(-1.5F, 0.0F, -3.0F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 0.0F, -1.0F));

        PartDefinition goiter = lower_jaw.addOrReplaceChild("goiter", CubeListBuilder.create().texOffs(12, 22).addBox(-1.0F, 0.0F, -1.5F, 2.0F, 1.0F, 3.0F, new CubeDeformation(-0.01F)), PartPose.offset(0.0F, 1.0F, -1.0F));

        PartDefinition tail = body_parts.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offset(0.0F, -2.5F, 0.5F));

        PartDefinition cube_r2 = tail.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(8, 8).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.1309F, 0.0F, 0.0F));

        PartDefinition legs = piglug.addOrReplaceChild("legs", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition front_legs = legs.addOrReplaceChild("front_legs", CubeListBuilder.create(), PartPose.offset(0.0F, -1.0F, -3.5F));

        PartDefinition right_leg1 = front_legs.addOrReplaceChild("right_leg1", CubeListBuilder.create().texOffs(8, 20).mirror().addBox(-1.5F, 4.5F, 0.5F, 3.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
        .texOffs(22, 22).mirror().addBox(-0.5F, -0.5F, -1.0F, 1.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
        .texOffs(0, 29).mirror().addBox(-0.5F, 1.5F, 1.0F, 0.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
        .texOffs(28, 24).mirror().addBox(-0.5F, 0.5F, -2.0F, 0.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-1.5F, 0.0F, 0.0F));

        PartDefinition right_wing = right_leg1.addOrReplaceChild("right_wing", CubeListBuilder.create(), PartPose.offset(-0.5F, 4.5F, 0.0F));

        PartDefinition cube_r3 = right_wing.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(0, 8).mirror().addBox(0.0F, -9.0F, -2.0F, 0.0F, 9.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.1745F));

        PartDefinition left_leg1 = front_legs.addOrReplaceChild("left_leg1", CubeListBuilder.create().texOffs(8, 20).addBox(-1.5F, 4.5F, 0.5F, 3.0F, 0.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(22, 22).addBox(-0.5F, -0.5F, -1.0F, 1.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(0, 29).addBox(0.5F, 1.5F, 1.0F, 0.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(28, 24).addBox(0.5F, 0.5F, -2.0F, 0.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(1.5F, 0.0F, 0.0F));

        PartDefinition left_wing = left_leg1.addOrReplaceChild("left_wing", CubeListBuilder.create(), PartPose.offset(0.5F, 4.5F, 0.0F));

        PartDefinition cube_r4 = left_wing.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(0, 8).addBox(0.0F, -9.0F, -2.0F, 0.0F, 9.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.1745F));

        PartDefinition back_legs = legs.addOrReplaceChild("back_legs", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition right_leg2 = back_legs.addOrReplaceChild("right_leg2", CubeListBuilder.create().texOffs(24, 5).mirror().addBox(-0.5F, -0.5F, -1.0F, 1.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
        .texOffs(8, 20).mirror().addBox(-1.5F, 3.5F, -1.5F, 3.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-1.5F, 0.0F, 0.0F));

        PartDefinition left_leg2 = back_legs.addOrReplaceChild("left_leg2", CubeListBuilder.create().texOffs(24, 5).addBox(-0.5F, -0.5F, -1.0F, 1.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(8, 20).addBox(-1.5F, 3.5F, -1.5F, 3.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(1.5F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }
}

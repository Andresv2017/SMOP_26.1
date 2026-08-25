package net.darkblade.smop.client.tangoftero;

import net.darkblade.deluxelib.client.render.DeluxeEntityRenderState;
import net.darkblade.deluxelib.client.rig.AnimContext;
import net.darkblade.deluxelib.client.rig.Rig;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.darkblade.smop.SMOP;
import org.jetbrains.annotations.NotNull;

public class TangoBabyModel extends EntityModel<DeluxeEntityRenderState> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(SMOP.id("tangoftero_baby"), "main");

    private static final Rig<TangoBabyModel> RIG =
            Rig.<TangoBabyModel>builder()
                    .resetPoses()
                    .keyframeBlend(200L, 0)
                    .lookAt(m -> m.neck, 30.0F, 30.0F)
                    .build();

    public final ModelPart neck;

    public TangoBabyModel(ModelPart root) {
        super(root);
        this.neck = root.getChild("Rat").getChild("body_parts").getChild("neck");
    }

    @Override
    public void setupAnim(@NotNull DeluxeEntityRenderState state) {
        RIG.apply(state, this, AnimContext.from(state));
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition Rat = partdefinition.addOrReplaceChild("Rat", CubeListBuilder.create(), PartPose.offset(0.0F, 20.5F, 1.0F));

        PartDefinition body_parts = Rat.addOrReplaceChild("body_parts", CubeListBuilder.create().texOffs(14, 11).addBox(-1.5F, -1.5F, -4.0F, 3.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(10, 9).addBox(0.0F, -2.5F, -3.0F, 0.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition neck = body_parts.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(14, 17).addBox(-1.0F, -2.5F, -1.5F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(18, 23).addBox(-1.0F, -2.5F, -3.5F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(23, 3).addBox(-1.0F, -4.5F, -0.5F, 2.0F, 2.0F, 2.0F, new CubeDeformation(-0.01F)), PartPose.offset(0.0F, -0.5F, -3.5F));

        PartDefinition upper_jaw = neck.addOrReplaceChild("upper_jaw", CubeListBuilder.create().texOffs(0, 11).addBox(-1.0F, -2.0F, -5.0F, 2.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(18, 10).addBox(-1.0F, 0.0F, -5.0F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -2.5F, 1.5F));

        PartDefinition head_r1 = upper_jaw.addOrReplaceChild("head_r1", CubeListBuilder.create().texOffs(8, 23).addBox(-1.5F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 0.3927F, 0.0F, 0.0F));

        PartDefinition eyes = upper_jaw.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(17, 6).mirror().addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.01F)).mirror(false), PartPose.offset(0.0F, -1.0F, -2.0F));

        PartDefinition pupils = eyes.addOrReplaceChild("pupils", CubeListBuilder.create().texOffs(24, 21).addBox(-1.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.02F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition left_arm = body_parts.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(0, 18).mirror().addBox(-0.5F, -0.5F, -2.5F, 1.0F, 3.0F, 3.0F, new CubeDeformation(0.01F)).mirror(false)
                .texOffs(8, 18).mirror().addBox(-0.5F, 1.5F, -3.5F, 0.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(1.5F, 0.0F, -1.5F));

        PartDefinition tail1 = body_parts.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(0, 6).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.5F, 0.0F));

        PartDefinition tail2 = tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, 0.0F, 0.0F, 6.0F, 0.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 5.0F));

        PartDefinition right_arm = body_parts.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 18).addBox(-0.5F, -0.5F, -2.5F, 1.0F, 3.0F, 3.0F, new CubeDeformation(0.01F))
                .texOffs(8, 18).addBox(0.5F, 1.5F, -3.5F, 0.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.5F, 0.0F, -1.5F));

        PartDefinition legs = Rat.addOrReplaceChild("legs", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition right_leg = legs.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(24, 17).addBox(-0.5F, -0.5F, -1.0F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.01F)), PartPose.offset(-1.0F, 0.0F, 0.0F));

        PartDefinition right_calf = right_leg.addOrReplaceChild("right_calf", CubeListBuilder.create().texOffs(22, 10).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 1.5F, 1.0F));

        PartDefinition right_foot = right_calf.addOrReplaceChild("right_foot", CubeListBuilder.create().texOffs(24, 0).addBox(-1.0F, 0.0F, -2.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(19, 1).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 1.0F, 0.0F));

        PartDefinition left_leg = legs.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(24, 17).mirror().addBox(-0.5F, -0.5F, -1.0F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.01F)).mirror(false), PartPose.offset(1.0F, 0.0F, 0.0F));

        PartDefinition left_calf = left_leg.addOrReplaceChild("left_calf", CubeListBuilder.create().texOffs(22, 10).mirror().addBox(-0.5F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 1.5F, 1.0F));

        PartDefinition left_foot = left_calf.addOrReplaceChild("left_foot", CubeListBuilder.create().texOffs(19, 1).mirror().addBox(-0.5F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(24, 0).mirror().addBox(-1.0F, 0.0F, -2.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 1.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 32, 32);
    }
}

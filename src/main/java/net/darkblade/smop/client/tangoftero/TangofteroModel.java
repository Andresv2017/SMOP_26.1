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

public class TangofteroModel extends EntityModel<DeluxeEntityRenderState> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(SMOP.id("tangoftero"), "main");

    private static final Rig<TangofteroModel> RIG =
            Rig.<TangofteroModel>builder()
                    .resetPoses()
                    .keyframeBlend(200L, 0)
                    .lookAt(m -> m.neck, 30.0F, 30.0F)
                    .build();

    public final ModelPart neck;

    public TangofteroModel(ModelPart root) {
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

        PartDefinition Rat = partdefinition.addOrReplaceChild("Rat", CubeListBuilder.create(), PartPose.offset(0.0F, 19.0F, 3.0F));

        PartDefinition body_parts = Rat.addOrReplaceChild("body_parts", CubeListBuilder.create().texOffs(36, 39).addBox(-2.5F, -4.0F, -3.0F, 5.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(18, 39).addBox(-2.5F, -4.0F, -7.0F, 5.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition neck = body_parts.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 32).addBox(-2.0F, -4.0F, -2.5F, 4.0F, 5.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(46, 4).addBox(-2.0F, -4.0F, -6.5F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(26, 29).addBox(-2.0F, -5.0F, -6.5F, 4.0F, 1.0F, 9.0F, new CubeDeformation(-0.01F)), PartPose.offset(0.0F, -3.0F, -5.5F));

        PartDefinition epiglotis = neck.addOrReplaceChild("epiglotis", CubeListBuilder.create().texOffs(52, 17).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 4.0F, 3.0F, new CubeDeformation(-0.01F)), PartPose.offset(0.0F, -6.0F, 1.0F));

        PartDefinition muscles = neck.addOrReplaceChild("muscles", CubeListBuilder.create().texOffs(0, 50).addBox(-2.0F, -1.0F, -1.5F, 4.0F, 2.0F, 3.0F, new CubeDeformation(-0.001F)), PartPose.offset(0.0F, -5.0F, 1.0F));

        PartDefinition upper_jaw = neck.addOrReplaceChild("upper_jaw", CubeListBuilder.create().texOffs(0, 19).addBox(-2.0F, -4.0F, -9.0F, 4.0F, 4.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(26, 19).addBox(-2.0F, 0.0F, -9.0F, 4.0F, 1.0F, 9.0F, new CubeDeformation(-0.01F))
                .texOffs(36, 17).addBox(-2.0F, 0.0F, -9.0F, 4.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 2.5F));

        PartDefinition feathers_r1 = upper_jaw.addOrReplaceChild("feathers_r1", CubeListBuilder.create().texOffs(46, 0).addBox(-2.5F, 0.0F, 0.0F, 5.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, 0.3927F, 0.0F, 0.0F));

        PartDefinition eyes = upper_jaw.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(0, 42).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, -2.0F, -3.0F));

        PartDefinition pupils = eyes.addOrReplaceChild("pupils", CubeListBuilder.create().texOffs(52, 30).addBox(-2.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.02F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition arms = body_parts.addOrReplaceChild("arms", CubeListBuilder.create(), PartPose.offset(0.0F, -2.0F, -3.0F));

        PartDefinition right_arm = arms.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(36, 47).addBox(-2.0F, 0.0F, -4.0F, 1.0F, 5.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(16, 48).addBox(-1.0F, 3.0F, -6.0F, 0.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.5F, 0.0F, 0.0F));

        PartDefinition left_arm = arms.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(36, 47).mirror().addBox(1.0F, 0.0F, -4.0F, 1.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(16, 48).mirror().addBox(1.0F, 3.0F, -6.0F, 0.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(1.5F, 0.0F, 0.0F));

        PartDefinition tail1 = body_parts.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(36, 10).addBox(-3.5F, 0.0F, 0.0F, 7.0F, 0.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 1.0F));

        PartDefinition tail2 = tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(0, 10).addBox(-4.5F, 0.0F, 0.0F, 9.0F, 0.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 7.0F));

        PartDefinition tail_tip = tail2.addOrReplaceChild("tail_tip", CubeListBuilder.create().texOffs(0, 0).addBox(-6.5F, 0.0F, 1.0F, 13.0F, 0.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 8.0F));

        PartDefinition legs = Rat.addOrReplaceChild("legs", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition right_leg = legs.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(52, 24).addBox(-1.0F, -1.0F, -1.5F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.0F, 0.0F, 0.0F));

        PartDefinition right_calf = right_leg.addOrReplaceChild("right_calf", CubeListBuilder.create().texOffs(18, 37).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 2.0F, 1.5F));

        PartDefinition right_foot = right_calf.addOrReplaceChild("right_foot", CubeListBuilder.create().texOffs(48, 47).addBox(-1.5F, 0.0F, -3.0F, 3.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(18, 32).addBox(0.5F, -1.0F, -4.0F, 1.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 2.0F, 0.0F));

        PartDefinition left_leg = legs.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(52, 24).mirror().addBox(-1.0F, -1.0F, -1.5F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(2.0F, 0.0F, 0.0F));

        PartDefinition left_calf = left_leg.addOrReplaceChild("left_calf", CubeListBuilder.create().texOffs(18, 37).mirror().addBox(-0.5F, 0.0F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 2.0F, 1.5F));

        PartDefinition left_foot = left_calf.addOrReplaceChild("left_foot", CubeListBuilder.create().texOffs(48, 47).mirror().addBox(-1.5F, 0.0F, -3.0F, 3.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(18, 32).mirror().addBox(-1.5F, -1.0F, -4.0F, 1.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 2.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }
}

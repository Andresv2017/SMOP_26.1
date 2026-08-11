package net.darkblade.smop.client.hellhippo;

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
 * Adult Hell Hippo. Geometry is the untouched 1.20.1 Blockbench export; the port is in
 * {@code setupAnim}, which no longer hand-drives a pile of {@code AnimationState}s — the {@link Rig}
 * resets the pose, blends whatever the {@code MobAnimator} says is playing, and layers head tracking
 * on top, the same shape the Kriftognathus and Tangoftero use.
 *
 * <p><b>Bone naming.</b> Every clip in {@code HellHippoAnimations} addresses bones by name at bake
 * time and throws on a name this mesh does not define — no partial match, no fallback — so the two
 * files have to agree exactly. The names are carried over from the 1.20.1 export verbatim, including
 * the Spanish root {@code Hipopotamo_Infernal} and the misspelled {@code troath}: renaming them here
 * would mean editing ~3300 lines of keyframes for no functional gain, and a rename that misses one
 * channel fails at render time rather than at compile time.
 *
 * <p>The look-at drives the {@code neck} rather than {@code head}: on a body this heavy, swivelling
 * only the skull reads as the head moving independently of the animal.
 */
public class HellHippoModel extends EntityModel<DeluxeEntityRenderState> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(SMOP.id("hell_hippo"), "main");

    private static final Rig<HellHippoModel> RIG =
            Rig.<HellHippoModel>builder()
                    .resetPoses()
                    .keyframeBlend(220L, 0)
                    .lookAt(m -> m.neck, 35.0F, 30.0F)
                    .build();

    public final ModelPart neck;

    public HellHippoModel(ModelPart root) {
        super(root);
        this.neck = root.getChild("Hipopotamo_Infernal").getChild("body").getChild("neck");
    }

    @Override
    public void setupAnim(@NotNull DeluxeEntityRenderState state) {
        RIG.apply(state, this, AnimContext.from(state));
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition Hipopotamo_Infernal = partdefinition.addOrReplaceChild("Hipopotamo_Infernal", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition body = Hipopotamo_Infernal.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 119).addBox(0.0F, -20.0F, -10.0F, 0.0F, 5.0F, 13.0F, new CubeDeformation(0.0F))
                .texOffs(0, 44).addBox(-9.0F, -15.0F, -15.0F, 18.0F, 20.0F, 20.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -20.0F, 18.0F));

        PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create(), PartPose.offset(0.0F, -7.0F, -31.0F));

        PartDefinition troath = neck.addOrReplaceChild("troath", CubeListBuilder.create().texOffs(106, 158).addBox(-5.0F, -10.0F, -14.0F, 10.0F, 16.0F, 20.0F, new CubeDeformation(0.01F))
                .texOffs(76, 44).mirror().addBox(-5.0F, -10.0F, -14.0F, 10.0F, 16.0F, 20.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(118, 80).addBox(0.0F, -15.0F, -12.0F, 0.0F, 5.0F, 13.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -3.0F, -5.0F));

        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(174, 115).addBox(-6.5F, -8.0F, -9.0F, 12.0F, 5.0F, 9.0F, new CubeDeformation(0.01F))
                .texOffs(0, 84).addBox(-6.5F, -8.0F, -9.0F, 12.0F, 13.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(180, 183).addBox(-6.5F, -13.0F, -9.0F, 12.0F, 5.0F, 9.0F, new CubeDeformation(0.01F))
                .texOffs(1, 194).addBox(-8.5F, -13.0F, -10.0F, 16.0F, 13.0F, 23.0F, new CubeDeformation(0.0F))
                .texOffs(7, 204).addBox(-9.5F, -5.0F, -9.0F, 2.0F, 5.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(7, 204).mirror().addBox(6.5F, -5.0F, -9.0F, 2.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(89, 5).mirror().addBox(2.0F, -3.0F, -11.0F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(89, 5).addBox(-4.0F, -3.0F, -11.0F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(94, 111).addBox(-3.5F, -7.0F, -17.0F, 6.0F, 5.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(174, 143).addBox(-3.5F, -7.0F, -17.0F, 6.0F, 8.0F, 9.0F, new CubeDeformation(0.01F))
                .texOffs(92, 19).addBox(-5.5F, -7.0F, -26.0F, 10.0F, 7.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(122, 35).addBox(-11.5F, -4.0F, -8.0F, 5.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(122, 35).mirror().addBox(5.5F, -4.0F, -8.0F, 5.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(38, 120).addBox(-4.5F, 0.0F, -25.0F, 8.0F, 4.0F, 7.0F, new CubeDeformation(0.0F))
                .texOffs(108, 125).mirror().addBox(-4.5F, -10.0F, -23.0F, 0.0F, 3.0F, 6.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(26, 119).addBox(-0.5F, -11.0F, -23.0F, 0.0F, 4.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(108, 125).addBox(3.5F, -10.0F, -23.0F, 0.0F, 3.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, -3.0F, -15.0F));

        PartDefinition eyes = head.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(92, 35).addBox(-6.0F, -0.5F, -1.5F, 12.0F, 1.0F, 3.0F, new CubeDeformation(0.01F)), PartPose.offset(-0.5F, -5.5F, -7.5F));

        PartDefinition nose_hairs = head.addOrReplaceChild("nose_hairs", CubeListBuilder.create().texOffs(70, 107).mirror().addBox(-4.0F, -1.0F, -1.5F, 0.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(130, 0).addBox(0.0F, -2.0F, -1.5F, 0.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(70, 107).addBox(4.0F, -1.0F, -1.5F, 0.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, -9.0F, -24.5F));

        PartDefinition nose = head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(92, 39).addBox(-5.0F, -2.0F, -1.5F, 10.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, -7.0F, -24.5F));

        PartDefinition ears = head.addOrReplaceChild("ears", CubeListBuilder.create(), PartPose.offset(0.0F, -5.0F, -3.0F));

        PartDefinition right_ear = ears.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(128, 125).addBox(-2.0F, -4.5F, -1.0F, 4.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.5F, -1.5F, -1.0F));

        PartDefinition left_ear = ears.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(128, 125).mirror().addBox(-2.0F, -4.5F, -1.0F, 4.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(4.5F, -1.5F, -1.0F));

        PartDefinition lower_jaw = head.addOrReplaceChild("lower_jaw", CubeListBuilder.create().texOffs(92, 0).addBox(-3.0F, 0.0F, -10.0F, 6.0F, 6.0F, 13.0F, new CubeDeformation(0.0F))
                .texOffs(0, 106).addBox(-5.0F, 2.0F, -17.0F, 10.0F, 4.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(118, 98).addBox(-4.0F, -2.0F, -16.0F, 8.0F, 4.0F, 7.0F, new CubeDeformation(-0.01F))
                .texOffs(118, 41).addBox(-2.0F, 1.0F, -17.0F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.01F))
                .texOffs(26, 129).mirror().addBox(-5.0F, 6.0F, -11.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(26, 129).addBox(3.0F, 6.0F, -11.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, -2.0F, -9.0F));

        PartDefinition torso = body.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(149, 212).addBox(-11.0F, -10.0F, -16.0F, 22.0F, 20.0F, 24.0F, new CubeDeformation(0.01F))
                .texOffs(0, 0).addBox(-11.0F, -10.0F, -16.0F, 22.0F, 20.0F, 24.0F, new CubeDeformation(0.0F))
                .texOffs(60, 182).addBox(-17.0F, -5.0F, -3.0F, 6.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(58, 185).addBox(-18.0F, -3.0F, 1.0F, 1.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(58, 185).mirror().addBox(17.0F, -3.0F, 1.0F, 1.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(60, 182).mirror().addBox(11.0F, -5.0F, -3.0F, 6.0F, 10.0F, 10.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(0, 137).mirror().addBox(-11.0F, -10.0F, -16.0F, 22.0F, 20.0F, 24.0F, new CubeDeformation(0.02F)).mirror(false), PartPose.offset(0.0F, -7.0F, -22.0F));

        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(94, 125).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 11.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -11.0F, 5.0F));

        PartDefinition tailend = tail.addOrReplaceChild("tailend", CubeListBuilder.create().texOffs(124, 109).addBox(-3.0F, 0.0F, -2.5F, 6.0F, 11.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(70, 95).addBox(-1.0F, 0.0F, 2.5F, 0.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(70, 101).addBox(1.0F, 1.0F, 2.5F, 0.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(70, 95).mirror().addBox(1.0F, 0.0F, 2.5F, 0.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(70, 101).mirror().addBox(-1.0F, 1.0F, 2.5F, 0.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 11.0F, 1.0F));

        PartDefinition legs = Hipopotamo_Infernal.addOrReplaceChild("legs", CubeListBuilder.create(), PartPose.offset(0.0F, -20.0F, 0.0F));

        PartDefinition front_legs = legs.addOrReplaceChild("front_legs", CubeListBuilder.create(), PartPose.offset(0.0F, -5.5F, -13.5F));

        PartDefinition right_leg1 = front_legs.addOrReplaceChild("right_leg1", CubeListBuilder.create().texOffs(70, 84).mirror().addBox(-2.5F, 10.0F, 3.5F, 0.0F, 8.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(42, 84).mirror().addBox(-3.5F, 0.0F, -3.5F, 7.0F, 26.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-9.5F, -0.5F, 0.0F));

        PartDefinition left_leg1 = front_legs.addOrReplaceChild("left_leg1", CubeListBuilder.create().texOffs(70, 84).addBox(2.5F, 10.0F, 3.5F, 0.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(42, 84).addBox(-3.5F, 0.0F, -3.5F, 7.0F, 26.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(9.5F, -0.5F, 0.0F));

        PartDefinition back_legs = legs.addOrReplaceChild("back_legs", CubeListBuilder.create(), PartPose.offset(0.0F, -10.0F, 18.0F));

        PartDefinition right_leg2 = back_legs.addOrReplaceChild("right_leg2", CubeListBuilder.create().texOffs(76, 80).mirror().addBox(-4.0F, 0.0F, -7.0F, 8.0F, 18.0F, 13.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-10.0F, 0.0F, 1.0F));

        PartDefinition right_calf = right_leg2.addOrReplaceChild("right_calf", CubeListBuilder.create().texOffs(70, 111).mirror().addBox(-2.5F, -4.0F, -2.0F, 5.0F, 17.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(120, 125).mirror().addBox(-0.5F, -6.0F, 4.0F, 0.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.5F, 17.0F, 4.0F));

        PartDefinition left_leg2 = back_legs.addOrReplaceChild("left_leg2", CubeListBuilder.create().texOffs(76, 80).addBox(-4.0F, 0.0F, -7.0F, 8.0F, 18.0F, 13.0F, new CubeDeformation(0.0F)), PartPose.offset(10.0F, 0.0F, 1.0F));

        PartDefinition left_calf = left_leg2.addOrReplaceChild("left_calf", CubeListBuilder.create().texOffs(70, 111).addBox(-2.5F, -4.0F, -2.0F, 5.0F, 17.0F, 7.0F, new CubeDeformation(0.0F))
                .texOffs(120, 125).addBox(0.5F, -6.0F, 4.0F, 0.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, 17.0F, 4.0F));

        return LayerDefinition.create(meshdefinition, 256, 256);
    }
}

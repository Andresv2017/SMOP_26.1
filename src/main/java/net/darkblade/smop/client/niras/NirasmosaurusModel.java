package net.darkblade.smop.client.niras;

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
 * Adult Nirasmosaurus, a marine reptile. Geometry is the raw Blockbench export; {@code setupAnim}
 * hands the pose to the {@link Rig}, which resets it, blends whatever the {@code MobAnimator} says is
 * playing and layers head tracking on top — the same shape every other SMOP mob uses.
 *
 * <p><b>Bone naming.</b> Every clip addresses bones by name at bake time and 26.1 <em>throws</em> on a
 * name this mesh does not define — no partial match, no fallback. The names come straight from the
 * export, Hungarian {@code g} prefixes and the misspelled {@code gTroath} included: renaming them
 * would mean editing thousands of keyframe lines for no functional gain, and a rename that misses one
 * channel fails at render time rather than at compile time.
 *
 * <p>The look-at drives {@code gNeck} rather than {@code gHead}: on a long-necked animal, swivelling
 * only the skull reads as the head moving independently of the body.
 */
public class NirasmosaurusModel extends EntityModel<DeluxeEntityRenderState> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(SMOP.id("nirasmosaurus"), "main");

    private static final Rig<NirasmosaurusModel> RIG =
            Rig.<NirasmosaurusModel>builder()
                    .resetPoses()
                    .keyframeBlend(220L, 0)
                    // Layer 1: the water bite, cut down to the gNeck subtree. Applied after layer 0
                    // so it composites additively on top of whatever swim clip is current instead of
                    // replacing it — which is what a same-layer clip does, and it would drop every
                    // bone the clip does not author back to the bind pose. That is precisely what a
                    // biting Nirasmosaurus looked like in the water: the neck struck and the rest of
                    // the animal went still for the length of the clip.
                    .keyframeBlend(80L, 1)
                    .lookAt(m -> m.gNeck, 35.0F, 30.0F)
                    .build();

    public final ModelPart root;
    public final ModelPart gNeck;

    public NirasmosaurusModel(ModelPart root) {
        super(root);
        this.root = root.getChild("gNirasmo");
        this.gNeck = this.root.getChild("gBodyparts").getChild("gNeck");
    }

    @Override
    public void setupAnim(@NotNull DeluxeEntityRenderState state) {
        RIG.apply(state, this, AnimContext.from(state));
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition gNirasmo = partdefinition.addOrReplaceChild("gNirasmo", CubeListBuilder.create(), PartPose.offset(0.0F, 16.5F, 15.0F));

        PartDefinition gBodyparts = gNirasmo.addOrReplaceChild("gBodyparts", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition gChest_n_corals = gBodyparts.addOrReplaceChild("gChest_n_corals", CubeListBuilder.create(), PartPose.offset(0.0F, -2.0F, -4.5F));

        PartDefinition gCorals = gChest_n_corals.addOrReplaceChild("gCorals", CubeListBuilder.create(), PartPose.offset(0.0385F, -12.5F, 0.7021F));

        PartDefinition gChest = gBodyparts.addOrReplaceChild("gChest", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -16.5F, -20.5F, 16.0F, 25.0F, 41.0F, new CubeDeformation(0.0F))
        .texOffs(157, 243).addBox(-8.0F, -18.0F, -18.0F, 16.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(214, 205).addBox(-7.0F, -21.0F, -17.0F, 14.0F, 5.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(210, 222).addBox(-8.0F, -17.0F, -18.0F, 16.0F, 26.0F, 5.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, -2.0F, -4.5F));

        PartDefinition gNeck = gBodyparts.addOrReplaceChild("gNeck", CubeListBuilder.create(), PartPose.offset(0.0F, -6.0F, -25.0F));

        PartDefinition gTroath = gNeck.addOrReplaceChild("gTroath", CubeListBuilder.create().texOffs(0, 66).addBox(-3.5F, -8.5F, -10.0F, 7.0F, 17.0F, 20.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -10.0F));

        PartDefinition gHead = gNeck.addOrReplaceChild("gHead", CubeListBuilder.create(), PartPose.offset(0.0F, 1.5F, -14.0F));

        PartDefinition gUpperjaw = gHead.addOrReplaceChild("gUpperjaw", CubeListBuilder.create().texOffs(108, 96).addBox(-4.5F, -8.0F, -19.0F, 9.0F, 8.0F, 19.0F, new CubeDeformation(0.0F))
        .texOffs(32, 129).addBox(-4.5F, -8.0F, -28.0F, 9.0F, 10.0F, 9.0F, new CubeDeformation(0.0F))
        .texOffs(138, 138).addBox(-4.5F, 2.0F, -28.0F, 9.0F, 6.0F, 9.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition GEyes = gUpperjaw.addOrReplaceChild("GEyes", CubeListBuilder.create().texOffs(141, 86).addBox(-4.5F, -0.5F, -2.5F, 9.0F, 1.0F, 5.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, -3.5F, -9.5F));

        PartDefinition gLowerjaw = gHead.addOrReplaceChild("gLowerjaw", CubeListBuilder.create().texOffs(138, 123).addBox(-4.5F, 2.0F, -28.0F, 9.0F, 6.0F, 9.0F, new CubeDeformation(0.0F))
        .texOffs(114, 0).addBox(-4.5F, 0.0F, -19.0F, 9.0F, 8.0F, 19.0F, new CubeDeformation(0.0F))
        .texOffs(66, 163).addBox(-4.0F, -5.0F, -12.0F, 8.0F, 8.0F, 11.0F, new CubeDeformation(0.0F))
        .texOffs(140, 62).addBox(-4.5F, -2.0F, -28.0F, 9.0F, 4.0F, 9.0F, new CubeDeformation(-0.01F))
        .texOffs(116, 3).addBox(-1.0F, -1.0F, -20.0F, 2.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition gRight_saliva = gLowerjaw.addOrReplaceChild("gRight_saliva", CubeListBuilder.create().texOffs(147, 24).mirror().addBox(0.0F, -4.0F, -9.5F, 0.0F, 8.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-4.25F, -4.0F, -9.5F));

        PartDefinition gLeft_saliva = gLowerjaw.addOrReplaceChild("gLeft_saliva", CubeListBuilder.create().texOffs(147, 16).addBox(0.0F, -4.0F, -9.5F, 0.0F, 8.0F, 19.0F, new CubeDeformation(0.0F)), PartPose.offset(4.25F, -4.0F, -9.5F));

        PartDefinition gChin = gLowerjaw.addOrReplaceChild("gChin", CubeListBuilder.create().texOffs(54, 66).addBox(-2.5F, -1.0F, -12.5F, 5.0F, 5.0F, 25.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 8.0F, -9.5F));

        PartDefinition gTongue = gLowerjaw.addOrReplaceChild("gTongue", CubeListBuilder.create().texOffs(117, 5).addBox(0.0F, -1.5F, -6.5F, 0.0F, 2.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 1.5F, -19.5F));

        PartDefinition gTail = gBodyparts.addOrReplaceChild("gTail", CubeListBuilder.create().texOffs(54, 96).addBox(-3.0F, -6.0F, 0.0F, 6.0F, 12.0F, 21.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -2.5F, 16.0F));

        PartDefinition gFin = gTail.addOrReplaceChild("gFin", CubeListBuilder.create().texOffs(108, 123).addBox(0.0F, -10.0F, -0.5F, 0.0F, 20.0F, 15.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 16.5F));

        PartDefinition gFlippers = gNirasmo.addOrReplaceChild("gFlippers", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition gFrontflippers = gFlippers.addOrReplaceChild("gFrontflippers", CubeListBuilder.create(), PartPose.offset(0.0F, 1.0F, -13.0F));

        PartDefinition gRightfrontflipper = gFrontflippers.addOrReplaceChild("gRightfrontflipper", CubeListBuilder.create().texOffs(114, 27).mirror().addBox(-1.5F, 0.0F, -6.0F, 3.0F, 23.0F, 12.0F, new CubeDeformation(0.0F)).mirror(false)
        .texOffs(0, 103).mirror().addBox(0.5F, 13.0F, -5.0F, 0.0F, 19.0F, 16.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-8.5F, -0.5F, 0.0F));

        PartDefinition gLeftfrontflipper = gFrontflippers.addOrReplaceChild("gLeftfrontflipper", CubeListBuilder.create().texOffs(114, 27).addBox(-1.5F, 0.0F, -6.0F, 3.0F, 23.0F, 12.0F, new CubeDeformation(0.0F))
        .texOffs(0, 103).addBox(-0.5F, 13.0F, -5.0F, 0.0F, 19.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(8.5F, -0.5F, 0.0F));

        PartDefinition gBackflippers = gFlippers.addOrReplaceChild("gBackflippers", CubeListBuilder.create(), PartPose.offset(0.0F, 3.5F, 8.0F));

        PartDefinition gRightbackflipper = gBackflippers.addOrReplaceChild("gRightbackflipper", CubeListBuilder.create().texOffs(114, 62).mirror().addBox(-1.5F, 0.0F, -5.0F, 3.0F, 19.0F, 10.0F, new CubeDeformation(0.0F)).mirror(false)
        .texOffs(0, 138).mirror().addBox(0.5F, 8.0F, -2.0F, 0.0F, 16.0F, 11.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-8.5F, -3.0F, 0.0F));

        PartDefinition gLeftbackflipper = gBackflippers.addOrReplaceChild("gLeftbackflipper", CubeListBuilder.create().texOffs(114, 62).addBox(-1.5F, 0.0F, -5.0F, 3.0F, 19.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(0, 138).addBox(-0.5F, 8.0F, -2.0F, 0.0F, 16.0F, 11.0F, new CubeDeformation(0.0F)), PartPose.offset(8.5F, -3.0F, 0.0F));



        return LayerDefinition.create(meshdefinition, 256, 256);
    }
}

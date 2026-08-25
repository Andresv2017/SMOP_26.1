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
 * Nirasmosaurus calf. A separate, simpler mesh rather than a scaled adult: it drops the chest
 * corals, the saliva strands and the individual coral bones entirely, which is why its clips live in
 * their own files and why the orphan-channel pruning differs between the two rigs.
 *
 * <p><b>Bone naming.</b> Every clip addresses bones by name at bake time and 26.1 <em>throws</em> on a
 * name this mesh does not define — no partial match, no fallback. The names come across from the
 * export verbatim, Hungarian {@code g} prefixes and the misspelled {@code gTroath} included:
 * renaming them would mean editing thousands of keyframe lines for no functional gain, and a rename
 * that misses one channel fails at render time rather than at compile time.
 *
 * <p>The look-at drives {@code gNeck} rather than {@code gHead}: on a long-necked animal, swivelling
 * only the skull reads as the head moving independently of the body.
 */
public class NirasBabyModel extends EntityModel<DeluxeEntityRenderState> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(SMOP.id("nirasmosaurus_baby"), "main");

    private static final Rig<NirasBabyModel> RIG =
            Rig.<NirasBabyModel>builder()
                    .resetPoses()
                    .keyframeBlend(220L, 0)
                    // Layer 1: the water bite overlay. @see NirasmosaurusModel — the calf needs the
                    // same second layer even though it never bites in 1c, because the clip is
                    // registered with an AnimSource that switches by age and the layer belongs to the
                    // rig, not to the animation.
                    .keyframeBlend(80L, 1)
                    .lookAt(m -> m.gNeck, 35.0F, 30.0F)
                    .build();

    public final ModelPart root;
    public final ModelPart gNeck;

    public NirasBabyModel(ModelPart root) {
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

        PartDefinition gNirasmo = partdefinition.addOrReplaceChild("gNirasmo", CubeListBuilder.create(), PartPose.offset(0.0F, 17.5F, 2.5F));

        PartDefinition gBodyparts = gNirasmo.addOrReplaceChild("gBodyparts", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition gNeck = gBodyparts.addOrReplaceChild("gNeck", CubeListBuilder.create(), PartPose.offset(0.0F, -0.5F, -12.5F));

        PartDefinition gTroath = gNeck.addOrReplaceChild("gTroath", CubeListBuilder.create().texOffs(0, 34).addBox(-3.5F, -5.0F, -5.5F, 7.0F, 10.0F, 11.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -5.5F));

        PartDefinition gHead = gNeck.addOrReplaceChild("gHead", CubeListBuilder.create(), PartPose.offset(0.0F, 2.0F, -9.0F));

        PartDefinition gUpperjaw = gHead.addOrReplaceChild("gUpperjaw", CubeListBuilder.create().texOffs(36, 34).addBox(-4.0F, -5.0F, -9.0F, 8.0F, 5.0F, 9.0F, new CubeDeformation(0.0F))
        .texOffs(64, 0).addBox(-4.0F, -5.0F, -14.0F, 8.0F, 6.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(64, 11).addBox(-4.0F, 1.0F, -14.0F, 8.0F, 4.0F, 5.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition gEyes = gUpperjaw.addOrReplaceChild("gEyes", CubeListBuilder.create().texOffs(70, 73).addBox(-4.0F, -0.5F, -1.5F, 8.0F, 1.0F, 3.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, -2.5F, -4.5F));

        PartDefinition gLowerjaw = gHead.addOrReplaceChild("gLowerjaw", CubeListBuilder.create().texOffs(36, 48).addBox(-4.0F, 0.0F, -9.0F, 8.0F, 4.0F, 9.0F, new CubeDeformation(0.0F))
        .texOffs(70, 45).addBox(-3.0F, -4.0F, -7.0F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0.0F))
        .texOffs(70, 37).addBox(-4.0F, 1.0F, -14.0F, 8.0F, 3.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(22, 71).addBox(-1.0F, -1.0F, -10.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(86, 81).addBox(-4.0F, -1.0F, -14.0F, 8.0F, 2.0F, 5.0F, new CubeDeformation(-0.01F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition gTongue = gLowerjaw.addOrReplaceChild("gTongue", CubeListBuilder.create().texOffs(14, 71).addBox(0.0F, -1.25F, -3.0F, 0.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.25F, -10.0F));

        PartDefinition gChin = gLowerjaw.addOrReplaceChild("gChin", CubeListBuilder.create().texOffs(0, 77).addBox(-1.5F, 0.0F, -7.0F, 3.0F, 3.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 3.0F, -5.0F));

        PartDefinition gChest = gBodyparts.addOrReplaceChild("gChest", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -7.0F, -10.0F, 12.0F, 14.0F, 20.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.5F, -2.5F));

        PartDefinition gTail = gBodyparts.addOrReplaceChild("gTail", CubeListBuilder.create().texOffs(0, 55).addBox(-3.0F, -3.5F, 0.0F, 6.0F, 7.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 7.5F));

        PartDefinition gFin = gTail.addOrReplaceChild("gFin", CubeListBuilder.create().texOffs(30, 61).addBox(0.0F, -8.0F, -1.0F, 0.0F, 16.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -0.5F, 7.0F));

        PartDefinition gFlippers = gNirasmo.addOrReplaceChild("gFlippers", CubeListBuilder.create(), PartPose.offset(0.0F, -1.5F, -2.5F));

        PartDefinition gFrontflippers = gFlippers.addOrReplaceChild("gFrontflippers", CubeListBuilder.create(), PartPose.offset(0.0F, 2.0F, -5.5F));

        PartDefinition gRightfrontflipper = gFrontflippers.addOrReplaceChild("gRightfrontflipper", CubeListBuilder.create().texOffs(50, 61).mirror().addBox(-1.5F, 0.0F, -3.5F, 3.0F, 14.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false)
        .texOffs(70, 55).mirror().addBox(0.0F, 8.0F, -2.5F, 0.0F, 10.0F, 8.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(5.5F, 0.0F, 0.0F));

        PartDefinition gLeftfrontflipper = gFrontflippers.addOrReplaceChild("gLeftfrontflipper", CubeListBuilder.create().texOffs(50, 61).addBox(-1.5F, 0.0F, -3.5F, 3.0F, 14.0F, 7.0F, new CubeDeformation(0.0F))
        .texOffs(70, 55).addBox(0.0F, 8.0F, -2.5F, 0.0F, 10.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.5F, 0.0F, 0.0F));

        PartDefinition gBackflippers = gFlippers.addOrReplaceChild("gBackflippers", CubeListBuilder.create(), PartPose.offset(0.0F, 2.0F, 6.0F));

        PartDefinition gRightbackflipper = gBackflippers.addOrReplaceChild("gRightbackflipper", CubeListBuilder.create().texOffs(0, 71).mirror().addBox(0.0F, 5.0F, -2.0F, 0.0F, 10.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false)
        .texOffs(70, 20).mirror().addBox(-1.5F, 0.0F, -3.0F, 3.0F, 11.0F, 6.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(5.5F, 0.0F, 0.0F));

        PartDefinition gLeftbackflipper = gBackflippers.addOrReplaceChild("gLeftbackflipper", CubeListBuilder.create().texOffs(70, 20).addBox(-1.5F, 0.0F, -3.0F, 3.0F, 11.0F, 6.0F, new CubeDeformation(0.0F))
        .texOffs(0, 71).addBox(0.0F, 5.0F, -2.0F, 0.0F, 10.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.5F, 0.0F, 0.0F));



        return LayerDefinition.create(meshdefinition, 128, 128);
    }
}

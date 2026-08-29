package net.darkblade.smop.client.gt;

import net.darkblade.smop.SMOP;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

/**
 * The trophy head's geometry, ported unchanged from the legacy mod's GrandTyrantHeadModel. It is a
 * separate mesh from {@link GTModel}: the live Grand Tyrant's root sits at the feet of a mob some
 * sixty pixels tall, while this one is re-anchored around the neck so the severed head lands on top
 * of a block. Never animated — the head is dead.
 */
public class GTHeadModel extends Model<Unit> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(SMOP.id("gt_head"), "main");

    public static final Identifier TEXTURE = SMOP.id("textures/entity/grand_tyrant_head.png");

    public GTHeadModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition GT = partdefinition.addOrReplaceChild("GT", CubeListBuilder.create(), PartPose.offset(0.0F, 22.0F, 0.0F));

        PartDefinition body_parts = GT.addOrReplaceChild("body_parts", CubeListBuilder.create(), PartPose.offset(0.0F, -9.0F, 9.0F));

        PartDefinition neck = body_parts.addOrReplaceChild("neck", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -1.0F, -3.0F, 0.5672F, 0.0F, 0.0F));

        PartDefinition troath = neck.addOrReplaceChild("troath", CubeListBuilder.create().texOffs(258, 303).addBox(-7.5F, -17.0F, -16.0F, 15.0F, 43.0F, 32.0F, new CubeDeformation(0.0F))
                .texOffs(152, 379).addBox(0.0F, -31.0F, -2.0F, 0.0F, 27.0F, 24.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -19.5036F, 2.6789F, -0.0436F, 0.0F, 0.0F));

        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(376, 107).addBox(-5.5F, -0.0285F, -44.1259F, 11.0F, 0.0F, 26.0F, new CubeDeformation(0.0F))
                .texOffs(372, 78).addBox(-8.5F, -10.0285F, -18.1259F, 17.0F, 10.0F, 19.0F, new CubeDeformation(0.0F))
                .texOffs(352, 359).addBox(-5.5F, -6.0285F, -44.1259F, 11.0F, 11.0F, 26.0F, new CubeDeformation(0.0F))
                .texOffs(376, 133).addBox(-4.5F, -0.0285F, -43.1259F, 9.0F, 10.0F, 24.0F, new CubeDeformation(0.0F))
                .texOffs(244, 404).mirror().addBox(-11.5F, -14.0285F, -20.1259F, 8.0F, 7.0F, 14.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(244, 404).addBox(3.5F, -14.0285F, -20.1259F, 8.0F, 7.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(200, 404).mirror().addBox(-11.5F, -26.0285F, -20.1259F, 8.0F, 12.0F, 14.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(200, 404).addBox(3.5F, -26.0285F, -20.1259F, 8.0F, 12.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(162, 312).addBox(0.0F, -45.0285F, -49.1259F, 0.0F, 39.0F, 28.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -25.7069F, -4.3853F, -0.4625F, 0.0F, 0.0F));

        PartDefinition eyes = head.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(414, 336).addBox(-8.5F, -0.5F, 0.0F, 17.0F, 1.0F, 5.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, -6.5285F, -18.1259F));

        PartDefinition right_pupil = eyes.addOrReplaceChild("right_pupil", CubeListBuilder.create().texOffs(96, 306).mirror().addBox(-0.5F, 0.0F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.02F)).mirror(false), PartPose.offset(-7.0F, -0.5F, 0.5F));

        PartDefinition left_pupil = eyes.addOrReplaceChild("left_pupil", CubeListBuilder.create().texOffs(96, 306).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.02F)), PartPose.offset(7.0F, -0.5F, 0.5F));

        PartDefinition nose = head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(362, 47).addBox(-5.5F, -5.0F, -13.0F, 11.0F, 5.0F, 26.0F, new CubeDeformation(0.0F))
                .texOffs(0, 400).addBox(-5.5F, -15.0064F, -12.001F, 0.0F, 10.0F, 23.0F, new CubeDeformation(0.0F))
                .texOffs(0, 400).mirror().addBox(5.5F, -15.0064F, -12.001F, 0.0F, 10.0F, 23.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, -6.0285F, -31.1259F));

        PartDefinition muscles = head.addOrReplaceChild("muscles", CubeListBuilder.create().texOffs(398, 396).addBox(-8.0F, -3.0F, -10.0F, 15.0F, 12.0F, 15.0F, new CubeDeformation(0.01F)), PartPose.offset(0.5F, 2.9715F, -4.1259F));

        PartDefinition epiglotis = head.addOrReplaceChild("epiglotis", CubeListBuilder.create().texOffs(390, 194).addBox(-7.5F, -7.5F, -5.5F, 14.0F, 15.0F, 11.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, 3.4715F, -6.6259F));

        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 367).addBox(-8.5F, 0.0F, -19.0F, 17.0F, 14.0F, 19.0F, new CubeDeformation(0.0F))
                .texOffs(94, 400).mirror().addBox(8.5F, 4.0F, -15.0F, 0.0F, 14.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(78, 359).addBox(-5.5F, -1.0F, -45.0F, 11.0F, 15.0F, 26.0F, new CubeDeformation(0.0F))
                .texOffs(218, 378).addBox(-5.5F, 5.0F, -45.0F, 11.0F, 0.0F, 26.0F, new CubeDeformation(-0.01F))
                .texOffs(46, 400).addBox(-5.5F, -6.0F, -42.0F, 11.0F, 13.0F, 13.0F, new CubeDeformation(0.1F))
                .texOffs(386, 220).addBox(-4.5F, -2.0F, -44.0F, 9.0F, 7.0F, 25.0F, new CubeDeformation(0.0F))
                .texOffs(312, 182).addBox(-4.5F, 13.0F, -26.0F, 9.0F, 8.0F, 30.0F, new CubeDeformation(0.0F))
                .texOffs(414, 294).addBox(-5.5F, 14.0F, -45.0F, 11.0F, 9.0F, 11.0F, new CubeDeformation(0.01F))
                .texOffs(94, 400).addBox(-8.5F, 4.0F, -15.0F, 0.0F, 14.0F, 19.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -0.0285F, 0.8741F));

        PartDefinition saliva = jaw.addOrReplaceChild("saliva", CubeListBuilder.create().texOffs(78, 312).addBox(-5.5F, -12.0F, -16.0F, 10.0F, 15.0F, 32.0F, new CubeDeformation(-0.01F)), PartPose.offset(0.5F, 2.0F, -28.0F));

        PartDefinition tongue1 = jaw.addOrReplaceChild("tongue1", CubeListBuilder.create().texOffs(218, 363).addBox(-4.5F, 0.0F, -12.0F, 8.0F, 0.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, 0.0F, -15.0F));

        PartDefinition tongue2 = tongue1.addOrReplaceChild("tongue2", CubeListBuilder.create().texOffs(414, 350).addBox(-3.5F, 0.0F, -9.0F, 6.0F, 0.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -12.0F));

        PartDefinition tongue_tip = tongue2.addOrReplaceChild("tongue_tip", CubeListBuilder.create().texOffs(78, 306).addBox(-2.5F, 0.0F, -5.0F, 4.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -9.0F));

        PartDefinition goatee = jaw.addOrReplaceChild("goatee", CubeListBuilder.create().texOffs(355, 306).addBox(-0.5F, 1.0F, -13.0F, 0.0F, 25.0F, 28.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, 12.0F, -21.0F));

        return LayerDefinition.create(meshdefinition, 512, 512);
    }
}

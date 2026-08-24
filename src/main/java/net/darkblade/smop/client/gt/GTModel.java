package net.darkblade.smop.client.gt;

import net.darkblade.deluxelib.client.render.DeluxeEntityRenderState;
import net.darkblade.deluxelib.client.rig.AnimContext;
import net.darkblade.deluxelib.client.rig.Rig;
import net.darkblade.deluxelib.client.rig.component.TurnLeanAdditive;
import net.darkblade.smop.SMOP;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import org.jetbrains.annotations.NotNull;

/**
 * The Grand Tyrant. Geometry is the untouched 1.20.1 Blockbench export — 45 bones on a 512x512 sheet.
 *
 * <p><b>The bone names come across verbatim</b>, misspelled {@code troath} included. Every clip
 * addresses bones by name at bake time and 26.1 <em>throws</em> on a name this mesh does not define,
 * with no partial match and no fallback; renaming would mean editing thousands of keyframe lines for
 * no functional gain, and a rename that misses one channel fails at render time rather than at
 * compile time.
 *
 * <p><b>Verified before writing:</b> the clips animate 42 distinct bones and this rig declares 45, and
 * the 42 are a strict subset — so there is nothing to prune. The three the rig declares and no clip
 * ever touches ({@code legs}, {@code left_beak_r1}, {@code right_beak_r1}) are harmless: an unanimated
 * bone just holds its bind pose.
 *
 * <p>The look-at drives {@code neck} rather than {@code head}, for the same reason the Nirasmosaurus
 * does: on a long-necked animal, swivelling only the skull reads as the head coming loose from the
 * body. The limits are the entity's own {@code getMaxHeadYRot}/{@code getMaxHeadXRot} — 45 and 30.
 */
public class GTModel extends EntityModel<DeluxeEntityRenderState> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(SMOP.id("gt"), "main");

    private static final Rig<GTModel> RIG =
            Rig.<GTModel>builder()
                    .resetPoses()
                    .keyframeBlend(220L, 0)
                    .lookAt(m -> m.neck, 45.0F, 30.0F)
                    // El gap de rumbo, pasado por un muelle. Encaja en este mob mejor que en ningún
                    // otro del mod porque gira a 5 grados por tick, así que un viraje cerrado dura
                    // más de un segundo y el gap se queda abierto todo ese rato; en uno que gire
                    // rápido sería un pico que el suavizado se come.
                    .turnLean(TurnLeanAdditive.<GTModel>builder()
                            // Lento y bastante amortiguado: es un animal pesado, no un látigo.
                            .springFrequency(1.2F)
                            .springDamping(0.6F)
                            // Sin zona muerta, caminar recto le haría vibrar la columna con el ruido
                            // del grado de gap que siempre hay.
                            .gapDeadzone(5.0F)
                            .coil(m -> m.body_parts, 0.25F, 12.0F)
                            .coil(m -> m.neck, 0.35F, 18.0F)
                            // La cola llega TARDE, y con factor descendente: cada eslabón se queda
                            // más atrás que el anterior. Eso es lo que la hace pesar.
                            .lag(m -> m.tail1, 0.50F, 25.0F)
                            .lag(m -> m.tail2, 0.35F, 18.0F)
                            .lag(m -> m.tail3, 0.20F, 12.0F)
                            .bank(m -> m.body_parts, 0.20F, 10.0F)
                            .build())
                    .build();

    public final ModelPart root;
    public final ModelPart body_parts;
    public final ModelPart neck;
    public final ModelPart head;
    public final ModelPart eyes;
    public final ModelPart tail1;
    public final ModelPart tail2;
    public final ModelPart tail3;

    public GTModel(ModelPart root) {
        super(root);
        this.root = root.getChild("GT");
        this.body_parts = this.root.getChild("body_parts");
        this.neck = this.body_parts.getChild("neck");
        this.head = this.neck.getChild("head");
        this.eyes = this.head.getChild("eyes");
        this.tail1 = this.body_parts.getChild("tail1");
        this.tail2 = this.tail1.getChild("tail2");
        this.tail3 = this.tail2.getChild("tail3");
    }

    @Override
    public void setupAnim(@NotNull DeluxeEntityRenderState state) {
        RIG.apply(state, this, AnimContext.from(state));
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition GT = partdefinition.addOrReplaceChild("GT", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition body_parts = GT.addOrReplaceChild("body_parts", CubeListBuilder.create(), PartPose.offset(0.0F, -61.0F, 0.0F));

        PartDefinition neck = body_parts.addOrReplaceChild("neck", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -15.0F, -58.0F, 0.5672F, 0.0F, 0.0F));

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

        PartDefinition chest = body_parts.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(172, 129).addBox(0.0F, -51.0F, -25.5F, 0.0F, 22.0F, 70.0F, new CubeDeformation(0.0F))
                .texOffs(230, 79).addBox(-17.0F, -35.0F, -33.5F, 34.0F, 6.0F, 37.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(-17.0F, -29.0F, -33.5F, 34.0F, 48.0F, 81.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -7.0F, -25.5F));

        PartDefinition arms = body_parts.addOrReplaceChild("arms", CubeListBuilder.create(), PartPose.offset(0.0F, 4.0F, -45.0F));

        PartDefinition right_arm = arms.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(292, 378).addBox(-6.54F, -8.16F, -8.19F, 12.0F, 30.0F, 15.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-16.47F, 0.0F, 0.0F, 0.4224F, -0.5293F, 0.2427F));

        PartDefinition right_forearm = right_arm.addOrReplaceChild("right_forearm", CubeListBuilder.create().texOffs(362, 0).addBox(-8.2F, -2.68F, -9.19F, 15.0F, 30.0F, 17.0F, new CubeDeformation(0.0F))
                .texOffs(132, 400).addBox(-1.2F, -0.41F, 7.85F, 0.0F, 25.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.67F, 20.0F, 1.0F, -1.2654F, 0.0F, 0.0F));

        PartDefinition right_hand = right_forearm.addOrReplaceChild("right_hand", CubeListBuilder.create(), PartPose.offsetAndRotation(2.53F, 23.68F, -0.09F, 0.3491F, 0.0F, 0.0F));

        PartDefinition right_finger1 = right_hand.addOrReplaceChild("right_finger1", CubeListBuilder.create().texOffs(424, 423).addBox(-3.0F, -1.5F, -2.28F, 6.0F, 17.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(230, 122).addBox(-4.0F, 13.5F, -1.28F, 21.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.73F, 0.82F, -5.91F, -0.2182F, 0.0F, 0.0F));

        PartDefinition right_finger2 = right_hand.addOrReplaceChild("right_finger2", CubeListBuilder.create().texOffs(306, 423).addBox(-3.0F, -2.29F, -3.0F, 6.0F, 21.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(414, 343).addBox(-4.0F, 15.71F, -2.0F, 17.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.73F, 2.61F, 1.09F));

        PartDefinition right_finger3 = right_hand.addOrReplaceChild("right_finger3", CubeListBuilder.create().texOffs(200, 379).addBox(-2.09F, -1.99F, -1.82F, 5.0F, 14.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(278, 122).addBox(-3.09F, 10.01F, -0.82F, 14.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.64F, 1.82F, 6.37F, 0.1309F, 0.0F, 0.0F));

        PartDefinition left_arm = arms.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(292, 378).mirror().addBox(-5.46F, -8.16F, -8.19F, 12.0F, 30.0F, 15.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(16.47F, 0.0F, 0.0F, 0.4224F, 0.5293F, -0.2427F));

        PartDefinition left_forearm = left_arm.addOrReplaceChild("left_forearm", CubeListBuilder.create().texOffs(362, 0).mirror().addBox(-6.8F, -2.68F, -9.19F, 15.0F, 30.0F, 17.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(132, 400).mirror().addBox(1.2F, -0.68F, 7.81F, 0.0F, 25.0F, 10.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-0.67F, 20.0F, 1.0F, -1.2654F, 0.0F, 0.0F));

        PartDefinition left_hand = left_forearm.addOrReplaceChild("left_hand", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.53F, 23.68F, -0.09F, 0.3491F, 0.0F, 0.0F));

        PartDefinition left_finger1 = left_hand.addOrReplaceChild("left_finger1", CubeListBuilder.create().texOffs(424, 423).mirror().addBox(-3.0F, -1.5F, -2.28F, 6.0F, 17.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(230, 122).mirror().addBox(-17.0F, 13.5F, -1.28F, 21.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(6.73F, 0.82F, -5.91F, -0.2182F, 0.0F, 0.0F));

        PartDefinition left_finger2 = left_hand.addOrReplaceChild("left_finger2", CubeListBuilder.create().texOffs(306, 423).mirror().addBox(-3.0F, -2.29F, -3.0F, 6.0F, 21.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(414, 343).mirror().addBox(-13.0F, 15.71F, -2.0F, 17.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(5.73F, 2.61F, 1.09F));

        PartDefinition left_finger3 = left_hand.addOrReplaceChild("left_finger3", CubeListBuilder.create().texOffs(200, 379).mirror().addBox(-2.91F, -1.99F, -1.82F, 5.0F, 14.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(278, 122).mirror().addBox(-10.91F, 10.01F, -0.82F, 14.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(6.64F, 1.82F, 6.37F, 0.1309F, 0.0F, 0.0F));

        PartDefinition tail1 = body_parts.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(258, 221).addBox(-10.0F, -17.0F, 1.0F, 20.0F, 38.0F, 44.0F, new CubeDeformation(0.0F))
                .texOffs(0, 306).addBox(0.0F, -39.0F, 6.0F, 0.0F, 22.0F, 39.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -15.0171F, 14.739F, -0.1309F, 0.0F, 0.0F));

        PartDefinition tail2 = tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(230, 0).addBox(-7.0F, -13.5F, -2.0F, 14.0F, 27.0F, 52.0F, new CubeDeformation(0.0F))
                .texOffs(386, 252).addBox(0.0F, -27.5F, 11.0F, 0.0F, 14.0F, 28.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 1.5316F, 38.2158F, -0.2182F, 0.0F, 0.0F));

        PartDefinition tail3 = tail2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(0, 129).addBox(-5.0F, -7.5F, 0.0F, 10.0F, 15.0F, 76.0F, new CubeDeformation(0.0F))
                .texOffs(0, 220).addBox(0.0F, -26.5F, 9.0F, 0.0F, 19.0F, 67.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.8526F, 45.7753F, 0.3054F, 0.0F, 0.0F));

        PartDefinition legs = GT.addOrReplaceChild("legs", CubeListBuilder.create(), PartPose.offset(0.0F, -76.0F, 0.0F));

        PartDefinition right_leg = legs.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(134, 221).addBox(-17.0F, -7.8716F, -8.8577F, 20.0F, 49.0F, 42.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-12.0F, 0.0F, 0.0F, -0.6981F, 0.0F, 0.0F));

        PartDefinition right_calf = right_leg.addOrReplaceChild("right_calf", CubeListBuilder.create().texOffs(312, 122).addBox(-5.0F, -3.5374F, -13.3763F, 10.0F, 38.0F, 22.0F, new CubeDeformation(0.0F))
                .texOffs(218, 312).addBox(0.0F, -10.5374F, 1.6237F, 0.0F, 32.0F, 19.0F, new CubeDeformation(0.0F))
                .texOffs(288, 423).addBox(0.0F, 10.4626F, -22.3763F, 0.0F, 22.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(312, 122).addBox(-5.0F, -3.5374F, -13.3763F, 10.0F, 38.0F, 22.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, 24.0F, 35.0F, 0.3491F, 0.0F, 0.0F));

        PartDefinition right_beak_r1 = right_calf.addOrReplaceChild("right_beak_r1", CubeListBuilder.create().texOffs(328, 423).addBox(0.0F, -3.5F, 0.0F, 0.0F, 9.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0F, 22.9626F, 8.6237F, 0.0F, 0.3927F, 0.0F));

        PartDefinition right_foot = right_calf.addOrReplaceChild("right_foot", CubeListBuilder.create().texOffs(390, 167).addBox(-8.0F, -1.0F, -11.0F, 16.0F, 8.0F, 17.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 31.4626F, -3.3763F, 0.3494F, 0.041F, 0.0149F));

        PartDefinition right_toes = right_foot.addOrReplaceChild("right_toes", CubeListBuilder.create().texOffs(398, 423).addBox(4.0F, -2.0F, -9.0F, 4.0F, 8.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(398, 423).addBox(-8.0F, -2.0F, -9.0F, 4.0F, 8.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(414, 314).addBox(-2.5F, -2.0F, -12.0F, 5.0F, 8.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 1.0F, -11.0F));

        PartDefinition left_leg = legs.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(134, 221).mirror().addBox(-3.0F, -7.8716F, -8.8577F, 20.0F, 49.0F, 42.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(12.0F, 0.0F, 0.0F, -0.6981F, 0.0F, 0.0F));

        PartDefinition left_calf = left_leg.addOrReplaceChild("left_calf", CubeListBuilder.create().texOffs(312, 122).mirror().addBox(-5.0F, -3.5374F, -13.3763F, 10.0F, 38.0F, 22.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(218, 312).mirror().addBox(0.0F, -10.5374F, 1.6237F, 0.0F, 32.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(288, 423).mirror().addBox(0.0F, 10.4626F, -22.3763F, 0.0F, 22.0F, 9.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(7.0F, 24.0F, 35.0F, 0.3491F, 0.0F, 0.0F));

        PartDefinition left_beak_r1 = left_calf.addOrReplaceChild("left_beak_r1", CubeListBuilder.create().texOffs(328, 423).mirror().addBox(0.0F, -3.5F, 0.0F, 0.0F, 9.0F, 9.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-5.0F, 22.9626F, 8.6237F, 0.0F, -0.3927F, 0.0F));

        PartDefinition left_foot = left_calf.addOrReplaceChild("left_foot", CubeListBuilder.create().texOffs(390, 167).mirror().addBox(-8.0F, -1.0F, -11.0F, 16.0F, 8.0F, 17.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 31.4626F, -3.3763F, 0.3494F, -0.041F, -0.0149F));

        PartDefinition left_toes = left_foot.addOrReplaceChild("left_toes", CubeListBuilder.create().texOffs(398, 423).mirror().addBox(-8.0F, -2.0F, -9.0F, 4.0F, 8.0F, 9.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(398, 423).mirror().addBox(4.0F, -2.0F, -9.0F, 4.0F, 8.0F, 9.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(414, 314).mirror().addBox(-2.5F, -2.0F, -12.0F, 5.0F, 8.0F, 12.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 1.0F, -11.0F));

        return LayerDefinition.create(meshdefinition, 512, 512);
    }
}

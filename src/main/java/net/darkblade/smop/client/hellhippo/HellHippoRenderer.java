package net.darkblade.smop.client.hellhippo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.darkblade.deluxelib.anim.Animatable;
import net.darkblade.deluxelib.client.anim.HumanoidPoseApplier;
import net.darkblade.deluxelib.client.render.RiderPoseHandler;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.hellhippo.HellHippoEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;


public class HellHippoRenderer
        extends AgeableMobRenderer<HellHippoEntity, HellHippoRenderState, EntityModel<? super HellHippoRenderState>>
        implements RiderPoseHandler {

    private static final Identifier BABY = SMOP.id("textures/entity/hell_hippo/baby_hell_hippo.png");

    private static Identifier coat(String sex, String suffix) {
        return SMOP.id("textures/entity/hell_hippo/" + sex + "_hell_hippo" + suffix + ".png");
    }

    private static final String SADDLE = "_saddle";
    private static final String ARMORED = "_armored";
    private static final String CHEST = "_chest";
    private static final String SEAWEED = "_seaweed";

    public HellHippoRenderer(EntityRendererProvider.Context context) {
        super(context,
                new HellHippoModel(context.bakeLayer(HellHippoModel.LAYER_LOCATION)),
                new HellHippoBabyModel(context.bakeLayer(HellHippoBabyModel.LAYER_LOCATION)),
                1.2F);
    }

    @Override
    public @NotNull HellHippoRenderState createRenderState() {
        return new HellHippoRenderState();
    }

    @Override
    public void extractRenderState(@NotNull HellHippoEntity entity, @NotNull HellHippoRenderState state,
                                   float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (entity instanceof Animatable<?> animatable) {
            state.animator = animatable.animator();
        }
        state.male = entity.isMale();
        state.seaweed = entity.hasSeaweed();
        state.saddled = entity.isSaddled();
        state.armored = entity.isWearingBodyArmor();
        state.chest = entity.hasChest();
    }

    @Override
    protected float getFlipDegrees() {
        return 0.0F;
    }

    @Override
    public @NotNull Identifier getTextureLocation(@NotNull HellHippoRenderState state) {
        if (state.isBaby) {
            return BABY;
        }
        String sex = state.male ? "male" : "female";
        if (state.seaweed) {
            return coat(sex, SEAWEED);
        }
        StringBuilder suffix = new StringBuilder();
        if (state.saddled) {
            suffix.append(SADDLE);
        }
        if (state.armored) {
            suffix.append(ARMORED);
        }
        if (state.chest && state.saddled) {
            suffix.append(CHEST);
        }
        return coat(sex, suffix.toString());
    }

    // ───────────────────────────────────────────────────── RIDER ─────


    @Override
    public <S extends HumanoidRenderState> void applyRiderPose(@NotNull LivingEntity vehicle,
                                                               @NotNull HumanoidModel<S> model,
                                                               @NotNull S riderState) {
        if (isOwnFirstPersonView(vehicle)) {
            HumanoidPoseApplier.clearIfNeeded(model);
            return;
        }
        HumanoidPoseApplier.applyStatic(HellHippoRiderPose.SEATED, model);
    }


    private static boolean isOwnFirstPersonView(LivingEntity vehicle) {
        Minecraft client = Minecraft.getInstance();
        return client.options.getCameraType().isFirstPerson()
                && vehicle.getControllingPassenger() == client.player;
    }

    @Override
    public <S extends HumanoidRenderState> boolean canApplyTo(@NotNull LivingEntity vehicle, @NotNull S riderState) {
        return true;
    }


    @Override
    public void applyRiderTransform(@NotNull LivingEntityRenderState vehicleState, @NotNull PoseStack poseStack) {
        if (!(this.getModel() instanceof HellHippoModel model)) {
            return;
        }
        model.root.translateAndRotate(poseStack);
        model.body.translateAndRotate(poseStack);
        model.torso.translateAndRotate(poseStack);
        poseStack.translate(0.0F, -1.50F, 0.35F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-5.0F));
    }
}

package net.darkblade.smop.client.salmon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.darkblade.deluxelib.anim.Animatable;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.salmon.SalmonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public class SalmonRenderer extends MobRenderer<SalmonEntity, SalmonRenderState, SalmonModel> {

    private static final Identifier MALE = SMOP.id("textures/entity/salmon/salmon_male.png");
    private static final Identifier FEMALE = SMOP.id("textures/entity/salmon/salmon_female.png");

    private static final float BABY_SCALE = 0.3F;

    public SalmonRenderer(EntityRendererProvider.Context context) {
        super(context, new SalmonModel(context.bakeLayer(SalmonModel.LAYER_LOCATION)), 0.1F);
    }

    @Override
    public @NotNull SalmonRenderState createRenderState() {
        return new SalmonRenderState();
    }

    @Override
    public void extractRenderState(@NotNull SalmonEntity entity, @NotNull SalmonRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        // 26.1's setupAnim only gets the state, and the rig needs the animator to know what is playing.
        if (entity instanceof Animatable<?> animatable) {
            state.animator = animatable.animator();
        }
        state.male = entity.isMale();
        state.swimPitch = Mth.lerp(partialTick, entity.prevSwimPitch, entity.swimPitch);
        state.swimRoll = Mth.lerp(partialTick, entity.prevSwimRoll, entity.swimRoll);
    }

    @Override
    protected void setupRotations(@NotNull SalmonRenderState state, @NotNull PoseStack poseStack,
                                  float bodyRot, float entityScale) {
        super.setupRotations(state, poseStack, bodyRot, entityScale);
        poseStack.mulPose(Axis.XP.rotationDegrees(state.swimPitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-state.swimRoll));
    }

    @Override
    protected float getShadowRadius(@NotNull SalmonRenderState state) {
        return state.isBaby ? super.getShadowRadius(state) * BABY_SCALE : super.getShadowRadius(state);
    }

    @Override
    protected void scale(@NotNull SalmonRenderState state, @NotNull PoseStack poseStack) {
        if (state.isBaby) {
            poseStack.scale(BABY_SCALE, BABY_SCALE, BABY_SCALE);
        }
        super.scale(state, poseStack);
    }

    @Override
    protected float getFlipDegrees() {
        return 0.0F;
    }

    @Override
    public @NotNull Identifier getTextureLocation(@NotNull SalmonRenderState state) {
        return state.male ? MALE : FEMALE;
    }
}

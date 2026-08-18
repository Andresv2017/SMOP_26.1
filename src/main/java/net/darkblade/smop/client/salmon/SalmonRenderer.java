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

/**
 * Renders the salmon, male or female.
 *
 * <p>Plain {@link MobRenderer} rather than {@code AgeableMobRenderer}: fry use the same skeleton as
 * adults, just smaller, so there is no second model to swap in — only the scale below. And not
 * DeluxeLib's {@code CustomMobRenderer}, which pins the render state to {@code DeluxeEntityRenderState}
 * while this mob needs its own subclass for the sex. The two things that base would have given —
 * capturing the animator onto the state and killing the vanilla death flip — are the few lines here.
 */
public class SalmonRenderer extends MobRenderer<SalmonEntity, SalmonRenderState, SalmonModel> {

    private static final Identifier MALE = SMOP.id("textures/entity/salmon/salmon_male.png");
    private static final Identifier FEMALE = SMOP.id("textures/entity/salmon/salmon_female.png");

    /** Fry are the adult model at a third scale, as in 1.20.1. */
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

    /**
     * Body tilt on top of vanilla's rotations.
     *
     * <p>Applied to the whole model here rather than inside the {@code Rig}, which is what keeps it
     * from fighting the head's look-at: this is the trajectory, the rig's own bones are the pose, and
     * they compose instead of competing. Same two lines the Nirasmosaurus and the Kriftognathus use.
     */
    @Override
    protected void setupRotations(@NotNull SalmonRenderState state, @NotNull PoseStack poseStack,
                                  float bodyRot, float entityScale) {
        super.setupRotations(state, poseStack, bodyRot, entityScale);
        poseStack.mulPose(Axis.XP.rotationDegrees(state.swimPitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-state.swimRoll));
    }

    /**
     * 26.1 scales through the render state rather than by pushing a matrix in {@code render} — the
     * override 1.20.1 used no longer receives the entity.
     */
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

    /** Off, so the authored water/land death clips are not fought by vanilla's 90° corpse flop. */
    @Override
    protected float getFlipDegrees() {
        return 0.0F;
    }

    @Override
    public @NotNull Identifier getTextureLocation(@NotNull SalmonRenderState state) {
        return state.male ? MALE : FEMALE;
    }
}

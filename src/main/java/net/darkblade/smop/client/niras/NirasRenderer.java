package net.darkblade.smop.client.niras;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.darkblade.deluxelib.anim.Animatable;
import net.minecraft.util.Mth;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.niras.NirasmosaurusEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Renders the Nirasmosaurus: adult or calf, male or female.
 *
 * <p>{@link AgeableMobRenderer} handles the adult/calf model swap, which matters more here than
 * usual — the two meshes have different skeletons (the calf has no chest corals and no saliva
 * strands) and a clip baked against the wrong one throws rather than degrading.
 */
public class NirasRenderer
        extends AgeableMobRenderer<NirasmosaurusEntity, NirasRenderState, EntityModel<? super NirasRenderState>> {

    private static final Identifier MALE = SMOP.id("textures/entity/niras/niras_male.png");
    private static final Identifier FEMALE = SMOP.id("textures/entity/niras/niras_female.png");
    private static final Identifier BABY = SMOP.id("textures/entity/niras/niras_baby.png");

    public NirasRenderer(EntityRendererProvider.Context context) {
        super(context,
                new NirasmosaurusModel(context.bakeLayer(NirasmosaurusModel.LAYER_LOCATION)),
                new NirasBabyModel(context.bakeLayer(NirasBabyModel.LAYER_LOCATION)),
                1.2F);
    }

    @Override
    public @NotNull NirasRenderState createRenderState() {
        return new NirasRenderState();
    }

    @Override
    public void extractRenderState(@NotNull NirasmosaurusEntity entity, @NotNull NirasRenderState state,
                                   float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        // 26.1's setupAnim only receives the state, and the rig needs the animator to know what is
        // playing — same capture the other SMOP renderers do.
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
     * from fighting the neck's look-at: this is the trajectory, the rig's {@code lookAt} is the head,
     * and they compose instead of competing. Same two lines the Kriftognathus uses for flight.
     */
    @Override
    protected void setupRotations(@NotNull NirasRenderState state, @NotNull PoseStack poseStack,
                                  float bodyRot, float entityScale) {
        super.setupRotations(state, poseStack, bodyRot, entityScale);
        poseStack.mulPose(Axis.XP.rotationDegrees(state.swimPitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-state.swimRoll));
    }

    @Override
    public @NotNull Identifier getTextureLocation(@NotNull NirasRenderState state) {
        // One calf coat for both sexes: niras_baby.png is all the export provides.
        if (state.isBaby) {
            return BABY;
        }
        return state.male ? MALE : FEMALE;
    }
}

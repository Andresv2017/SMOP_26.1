package net.darkblade.smop.client.krifto;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.darkblade.deluxelib.anim.Animatable;
import net.darkblade.deluxelib.client.anim.HumanoidPoseApplier;
import net.darkblade.deluxelib.client.render.PerchPoseHandler;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.krifto.KriftognathusEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import org.jetbrains.annotations.NotNull;

public class KriftognathusRenderer
        extends AgeableMobRenderer<KriftognathusEntity, KriftoRenderState, EntityModel<? super KriftoRenderState>>
        implements PerchPoseHandler {

    private static final Identifier MALE = SMOP.id("textures/entity/krifto/krifto_male.png");
    private static final Identifier FEMALE = SMOP.id("textures/entity/krifto/krifto_female.png");
    private static final Identifier MALE_JUNGLE = SMOP.id("textures/entity/krifto/krifto_male_jungle.png");
    private static final Identifier FEMALE_JUNGLE = SMOP.id("textures/entity/krifto/krifto_female_jungle.png");
    private static final Identifier MALE_ARID = SMOP.id("textures/entity/krifto/krifto_male_arid.png");
    private static final Identifier FEMALE_ARID = SMOP.id("textures/entity/krifto/krifto_female_arid.png");
    private static final Identifier MALE_FROSTY = SMOP.id("textures/entity/krifto/krifto_male_frosty.png");
    private static final Identifier FEMALE_FROSTY = SMOP.id("textures/entity/krifto/krifto_female_frosty.png");
    private static final Identifier BABY = SMOP.id("textures/entity/krifto/krifto_baby.png");

    public KriftognathusRenderer(EntityRendererProvider.Context context) {
        super(context,
                new KriftognathusModel(context.bakeLayer(KriftognathusModel.LAYER_LOCATION)),
                new KriftoBabyModel(context.bakeLayer(KriftoBabyModel.LAYER_LOCATION)),
                0.4F);
        this.addLayer(new StolenItemLayer(this));
    }

    @Override
    public @NotNull KriftoRenderState createRenderState() {
        return new KriftoRenderState();
    }

    @Override
    public void extractRenderState(@NotNull KriftognathusEntity entity, @NotNull KriftoRenderState state,
                                   float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        // 26.1's setupAnim only gets the state, and the rig needs the animator to know what is playing.
        if (entity instanceof Animatable<?> animatable) {
            state.animator = animatable.animator();
        }
        state.male = entity.isMale();
        state.spawnBiome = entity.getSpawnBiomePath();
        state.flightPitch = Mth.lerp(partialTick, entity.prevFlightPitch, entity.flightPitch);
        state.flightRoll = Mth.lerp(partialTick, entity.prevFlightRoll, entity.flightRoll);
        // Resolved unconditionally, same as vanilla's own HoldingEntityRenderState: an empty
        // ItemStack resolves to an empty ItemStackRenderState, which StolenItemLayer skips itself.
        this.itemModelResolver.updateForLiving(state.stolenItem, entity.getStolenItem(),
                ItemDisplayContext.GROUND, entity);
    }

    @Override
    protected void setupRotations(@NotNull KriftoRenderState state, @NotNull PoseStack poseStack,
                                  float bodyRot, float entityScale) {
        super.setupRotations(state, poseStack, bodyRot, entityScale);
        poseStack.mulPose(Axis.XP.rotationDegrees(state.flightPitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-state.flightRoll));
    }

    @Override
    protected float getFlipDegrees() {
        return 0.0F;
    }

    @Override
    public @NotNull Identifier getTextureLocation(@NotNull KriftoRenderState state) {
        if (state.isBaby) {
            return BABY;
        }
        String biome = state.spawnBiome;
        if (biome.contains("jungle")) {
            return state.male ? MALE_JUNGLE : FEMALE_JUNGLE;
        }
        if (biome.contains("badlands")) {
            return state.male ? MALE_ARID : FEMALE_ARID;
        }
        if (biome.contains("snowy_taiga") || biome.contains("grove")) {
            return state.male ? MALE_FROSTY : FEMALE_FROSTY;
        }
        return state.male ? MALE : FEMALE;
    }

    // ------------------------------------------------------------------
    // PerchPoseHandler — a tamed Krifto perches over its owner's head as a parachute, and poses them
    // gripping its legs with BOTH arms rather than the library's default single falconry arm. Where
    // it sits is KriftoPerchPlacement's job; PerchClient does the drawing.
    // ------------------------------------------------------------------

    @Override
    public <S extends HumanoidRenderState> void applyHostPose(@NotNull LivingEntity perched,
                                                              @NotNull HumanoidModel<S> model,
                                                              @NotNull S hostState) {
        // Falling gets the animated pose (legs spread and drifting), driven off a running clock so
        // the loop actually plays; walking about on the ground gets the static grip, which authors
        // no leg channels at all and so leaves the stride to vanilla. isPerchGliding() is synced, so
        // this read is valid here on the client — see the field's own note.
        if (perched instanceof KriftognathusEntity krifto && krifto.isPerchGliding()) {
            HumanoidPoseApplier.apply(KriftoRiderPose.FALLING, model, hostState.ageInTicks / 20.0F);
        } else {
            HumanoidPoseApplier.applyStatic(KriftoRiderPose.GRIPPING, model);
        }
    }
}

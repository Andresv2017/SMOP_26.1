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

/**
 * Renders the Hell Hippo: adult or calf, male or female.
 *
 * <p>{@link AgeableMobRenderer} handles the adult/calf model swap, which matters more here than
 * usual — the two meshes have different skeletons, and a clip baked against the wrong one throws.
 *
 * <p>Resolves all fifteen coats — sex, seaweed, and every legal combination of saddle, barding and
 * panniers — and, as a {@code RiderPoseHandler}, takes over how a rider is posed and placed on its
 * back.
 */
public class HellHippoRenderer
        extends AgeableMobRenderer<HellHippoEntity, HellHippoRenderState, EntityModel<? super HellHippoRenderState>>
        implements RiderPoseHandler {

    private static final Identifier BABY = SMOP.id("textures/entity/hell_hippo/baby_hell_hippo.png");

    /**
     * The seven adult coats, per sex. Built by suffix rather than written out fourteen times, because
     * the file names ARE the state: {@code saddle}, then {@code armored}, then {@code chest}, each
     * appended only when true.
     *
     * <p>Only seven of the eight tack combinations exist, and the missing one is not an oversight: a
     * chest needs a saddle, so {@code armored_chest} without {@code saddle} can never come up. The
     * lookup below is arranged so it never asks for it.
     */
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
        // 26.1's setupAnim only receives the state, and the rig needs the animator to know what is
        // playing — same capture the other SMOP renderers do.
        if (entity instanceof Animatable<?> animatable) {
            state.animator = animatable.animator();
        }
        state.male = entity.isMale();
        state.seaweed = entity.hasSeaweed();
        state.saddled = entity.isSaddled();
        state.armored = entity.isWearingBodyArmor();
        state.chest = entity.hasChest();
    }

    /**
     * Off, so the authored death clip owns the collapse.
     *
     * <p>Vanilla rolls a dying mob itself — {@code LivingEntityRenderer#setupRotations} multiplies
     * the pose stack by {@code Axis.ZP.rotationDegrees(fall * getFlipDegrees())}, and that default is
     * 90°. The Hell Hippo's death clip turns its root bone −82.5° about the same axis, so with both
     * live the two compose to roughly 7.5° and the corpse reads as still standing. Handing the whole
     * rotation to the clip is what makes it flop the way it was animated.
     */
    @Override
    protected float getFlipDegrees() {
        return 0.0F;
    }

    /**
     * Picks the coat from the tack the animal is actually wearing.
     *
     * <p>Order matters, and it mirrors the entity's own rules rather than guessing: the seaweed coats
     * come first because {@code tickSeaweed} refuses to grow any on a saddled hippo, so the two can
     * never both apply; and the chest suffix is only ever reached under the saddle branch, because the
     * chest cannot go on without one.
     */
    @Override
    public @NotNull Identifier getTextureLocation(@NotNull HellHippoRenderState state) {
        if (state.isBaby) {
            // The calf has exactly one coat. No baby seaweed texture exists, which is also why the
            // entity never lets a calf grow any — see HellHippoEntity#tickSeaweed. And it can carry
            // no tack: the trust ritual refuses a calf and the saddle slot demands an adult.
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
        // Guarded on the saddle as well as the flag: without one there is no armored_chest texture to
        // ask for, and a stale chest flag must not be able to name a file that does not exist.
        if (state.chest && state.saddled) {
            suffix.append(CHEST);
        }
        return coat(sex, suffix.toString());
    }

    // ───────────────────────────────────────────────────── RIDER ─────
    //
    // No registration step: RiderRenderEvents finds this by instanceof on the vehicle's renderer, and
    // attaches a RiderPassengerLayer that draws the rider inside this renderer's own pass. Vanilla's
    // default "stand on top facing forward" placement is skipped entirely.

    @Override
    public <S extends HumanoidRenderState> void applyRiderPose(@NotNull LivingEntity vehicle,
                                                               @NotNull HumanoidModel<S> model,
                                                               @NotNull S riderState) {
        if (isOwnFirstPersonView(vehicle)) {
            // Un-apply rather than merely skip: the applier tracks the last pose per model instance,
            // and models are shared across every humanoid a renderer draws. Returning without
            // clearing would leave the seated pose stuck on whatever it touches next.
            HumanoidPoseApplier.clearIfNeeded(model);
            return;
        }
        HumanoidPoseApplier.applyStatic(HellHippoRiderPose.SEATED, model);
    }

    /**
     * Whether this pose is about to be applied to the camera holder's own hands.
     *
     * <p>The event behind {@code applyRiderPose} fires at the tail of <em>every</em>
     * {@code HumanoidModel#setupAnim}, and the first-person hand is drawn through that same call — so
     * without this the seated arm rotation lands on the held-item view and the player's own hands end
     * up shoved across the screen. In first person the body is not drawn at all, so there is nothing
     * else lost by standing down.
     *
     * <p>Checked against the vehicle's controller rather than the render state because the state
     * carries no identity: other players riding their own hippos still get posed normally.
     */
    private static boolean isOwnFirstPersonView(LivingEntity vehicle) {
        Minecraft client = Minecraft.getInstance();
        return client.options.getCameraType().isFirstPerson()
                && vehicle.getControllingPassenger() == client.player;
    }

    /** Every humanoid that gets on. A hippo takes one rider and it is always a player. */
    @Override
    public <S extends HumanoidRenderState> boolean canApplyTo(@NotNull LivingEntity vehicle,
                                                              @NotNull S riderState) {
        return true;
    }

    /**
     * Walks the animal's own bone chain to its back, so the seat follows the body wherever the
     * animation puts it — including while it is swimming, sinking or rearing up mid-intimidation.
     *
     * <p>That is the reason to go through the bones rather than translate a fixed amount from the
     * entity position: a constant offset is only correct while the mob stands still.
     *
     * <p>Seat frame after the walk is the model's, so <b>+Y is DOWN</b> and +Z is toward the tail —
     * which is why the height below is negative. The numbers are eyeballed against the saddle
     * texture; {@code /riderpose} tunes them live with the numpad and prints the result to paste back.
     */
    @Override
    public void applyRiderTransform(@NotNull LivingEntityRenderState vehicleState, @NotNull PoseStack poseStack) {
        if (!(this.getModel() instanceof HellHippoModel model)) {
            // The calf mesh has a different skeleton and no saddle — nothing to sit on.
            return;
        }
        model.root.translateAndRotate(poseStack);
        model.body.translateAndRotate(poseStack);
        model.torso.translateAndRotate(poseStack);
        // Dialled in with /riderpose. The tuner reports its own transform as an addition on top of
        // this method, and two translations with no rotation between them simply sum — so its
        // -0.150 is folded into the height here rather than left as a second call. The 5° nose-down
        // is what makes the rider lean into the animal instead of sitting bolt upright on it.
        poseStack.translate(0.0F, -1.50F, 0.35F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-5.0F));
    }
}

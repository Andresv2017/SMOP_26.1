package net.darkblade.smop.client.hellhippo;

import net.darkblade.deluxelib.anim.Animatable;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.hellhippo.HellHippoEntity;
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
 * <p><b>Phase 1a</b> resolves only the plain coats. The saddle, armour, chest and seaweed variants
 * exist as textures already but are selected on state this mob does not carry yet; they get wired up
 * with the mechanics that produce them, in phases 2 and 3.
 */
public class HellHippoRenderer
        extends AgeableMobRenderer<HellHippoEntity, HellHippoRenderState, EntityModel<? super HellHippoRenderState>> {

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
}

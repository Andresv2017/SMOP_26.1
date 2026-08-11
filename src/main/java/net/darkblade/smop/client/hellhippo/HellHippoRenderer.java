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

    private static final Identifier MALE = SMOP.id("textures/entity/hell_hippo/male_hell_hippo.png");
    private static final Identifier FEMALE = SMOP.id("textures/entity/hell_hippo/female_hell_hippo.png");
    private static final Identifier BABY = SMOP.id("textures/entity/hell_hippo/baby_hell_hippo.png");

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
    }

    @Override
    public @NotNull Identifier getTextureLocation(@NotNull HellHippoRenderState state) {
        if (state.isBaby) {
            return BABY;
        }
        return state.male ? MALE : FEMALE;
    }
}

package net.darkblade.smop.client.krifto;

import net.darkblade.deluxelib.anim.Animatable;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.krifto.KriftognathusEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Renders the Kriftognathus: adult or chick, male or female, in the coat of the biome it hatched in.
 *
 * <p>{@link AgeableMobRenderer} rather than DeluxeLib's {@code CustomMobRenderer} for the same two
 * reasons as the Tangoftero: it handles the adult/chick model swap for free, and it is generic over
 * the render state, which {@code CustomMobRenderer} pins to {@code DeluxeEntityRenderState} while
 * this mob needs its own subclass. The two things that base would have given — capturing the animator
 * onto the state and killing the vanilla death flip — are the few lines below.
 */
public class KriftognathusRenderer
        extends AgeableMobRenderer<KriftognathusEntity, KriftoRenderState, EntityModel<? super KriftoRenderState>> {

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
    }

    /** Off, so the authored death clip is not fought by vanilla's 90° corpse flop. */
    @Override
    protected float getFlipDegrees() {
        return 0.0F;
    }

    /**
     * Coat by hatching biome. Matched on substrings of the biome path, so every jungle/badlands
     * variant lands on the right coat without listing them one by one — chicks are the same either way.
     */
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
}

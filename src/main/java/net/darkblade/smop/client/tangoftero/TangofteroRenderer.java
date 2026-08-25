package net.darkblade.smop.client.tangoftero;

import net.darkblade.deluxelib.anim.Animatable;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.tangoftero.TangofteroEntity;
import net.darkblade.smop.entity.tangoftero.TangofteroVariant;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

public class TangofteroRenderer
        extends AgeableMobRenderer<TangofteroEntity, TangofteroRenderState, EntityModel<? super TangofteroRenderState>> {

    private static final Map<TangofteroVariant, Identifier> TEXTURE_BY_VARIANT =
            new EnumMap<>(TangofteroVariant.class);

    static {
        for (TangofteroVariant variant : TangofteroVariant.values()) {
            TEXTURE_BY_VARIANT.put(variant,
                    SMOP.id("textures/entity/tangoftero/tangoftero_" + variant.name().toLowerCase(java.util.Locale.ROOT) + ".png"));
        }
    }

    private static final Identifier BABY_TEXTURE = SMOP.id("textures/entity/tangoftero/baby_tango.png");

    public TangofteroRenderer(EntityRendererProvider.Context context) {
        super(context,
                new TangofteroModel(context.bakeLayer(TangofteroModel.LAYER_LOCATION)),
                new TangoBabyModel(context.bakeLayer(TangoBabyModel.LAYER_LOCATION)),
                0.4F);
    }

    @Override
    public @NotNull TangofteroRenderState createRenderState() {
        return new TangofteroRenderState();
    }

    @Override
    public void extractRenderState(@NotNull TangofteroEntity entity, @NotNull TangofteroRenderState state,
                                   float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        // 26.1's setupAnim only gets the state, and the rig needs the animator to know what is playing.
        if (entity instanceof Animatable<?> animatable) {
            state.animator = animatable.animator();
        }
        state.variantId = entity.getVariantId();
    }

    @Override
    protected float getFlipDegrees() {
        return 0.0F;
    }

    @Override
    public @NotNull Identifier getTextureLocation(@NotNull TangofteroRenderState state) {
        return state.isBaby ? BABY_TEXTURE : TEXTURE_BY_VARIANT.get(TangofteroVariant.byId(state.variantId));
    }
}

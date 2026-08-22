package net.darkblade.smop.client.projectile;

import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.projectile.TangoArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Draws the tango arrow on vanilla's arrow mesh with the mod's own texture.
 *
 * <p>{@link ArrowRenderer} bakes {@code ModelLayers.ARROW} itself, so there is no model or layer
 * definition to register — the texture is the whole of the difference, and at 32x32 it matches the
 * UV layout that mesh expects. Same shape as vanilla's {@code SpectralArrowRenderer}.
 */
public class TangoArrowRenderer extends ArrowRenderer<TangoArrowEntity, ArrowRenderState> {

    private static final Identifier TEXTURE = SMOP.id("textures/entity/tango_arrow.png");

    public TangoArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected @NotNull Identifier getTextureLocation(@NotNull ArrowRenderState state) {
        return TEXTURE;
    }

    @Override
    public @NotNull ArrowRenderState createRenderState() {
        return new ArrowRenderState();
    }
}

package net.darkblade.smop.client.projectile;

import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.projectile.TangoArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

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

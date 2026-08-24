package net.darkblade.smop.client.gt;

import net.darkblade.deluxelib.client.render.CustomMobRenderer;
import net.darkblade.deluxelib.client.render.DeluxeEntityRenderState;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.gt.GTEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Renders the Grand Tyrant. One coat, no age variants, no sex variants — the 1.20.1 renderer returned
 * the same texture unconditionally and never consulted {@code isMale()}, which is why the port drops
 * the vestigial {@code Gendered} the legacy inherited.
 *
 * <p>{@code CustomMobRenderer} rather than vanilla's {@code MobRenderer}: it captures the animator
 * onto the render state and disables the vanilla death flip, which would otherwise fight the authored
 * {@code death} clip the same way it fought the Hell Hippo's and the Nirasmosaurus'.
 *
 * <p>Shadow radius 2.5 for a body 3.2 blocks wide — large, but a 6-block animal casting a chicken's
 * shadow reads worse than one casting a slightly generous shadow.
 */
public class GTRenderer extends CustomMobRenderer<GTEntity, GTModel> {

    private static final Identifier TEXTURE = SMOP.id("textures/entity/gt/gt.png");

    public GTRenderer(EntityRendererProvider.Context context) {
        super(context, new GTModel(context.bakeLayer(GTModel.LAYER_LOCATION)), 2.5F);
    }

    @Override
    public @NotNull Identifier getTextureLocation(@NotNull DeluxeEntityRenderState state) {
        return TEXTURE;
    }
}

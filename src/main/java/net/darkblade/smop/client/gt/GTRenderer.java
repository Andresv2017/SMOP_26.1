package net.darkblade.smop.client.gt;

import net.darkblade.deluxelib.client.render.CustomMobRenderer;
import net.darkblade.deluxelib.client.render.DeluxeEntityRenderState;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.gt.GTEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;


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

package net.darkblade.smop.client.projectile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.darkblade.smop.entity.projectile.NirasSpearEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ThrownTridentRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Draws the spear in flight, pointed the way it is travelling.
 *
 * <p>The two rotations are vanilla's, from {@code ThrownTridentRenderer}: yaw minus 90 and pitch plus
 * 90 turn a model authored standing upright into one lying along its own flight path. The
 * interpolation happens in {@link #extractRenderState}, and the model never sees the entity.
 *
 * <p>It borrows vanilla's {@link ThrownTridentRenderState} rather than declaring one: the state
 * needed is exactly a yaw and a pitch, which is what that class holds. Its third field, the foil
 * flag, simply goes unread — this spear takes no enchantment glint from anything that would set it.
 */
public class NirasSpearRenderer extends EntityRenderer<NirasSpearEntity, ThrownTridentRenderState> {

    private final NirasSpearModel model;

    public NirasSpearRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new NirasSpearModel(context.bakeLayer(NirasSpearModel.LAYER_LOCATION));
    }

    @Override
    public void submit(@NotNull ThrownTridentRenderState state, @NotNull PoseStack poseStack,
                       @NotNull SubmitNodeCollector collector, @NotNull CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.xRot + 90.0F));
        collector.order(0).submitModel(this.model, Unit.INSTANCE, poseStack, NirasSpearModel.TEXTURE,
                state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    public @NotNull ThrownTridentRenderState createRenderState() {
        return new ThrownTridentRenderState();
    }

    @Override
    public void extractRenderState(@NotNull NirasSpearEntity entity, @NotNull ThrownTridentRenderState state,
                                   float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = entity.getYRot(partialTicks);
        state.xRot = entity.getXRot(partialTicks);
    }
}

package net.darkblade.smop.client.krifto;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jetbrains.annotations.NotNull;

public class StolenItemLayer extends RenderLayer<KriftoRenderState, EntityModel<? super KriftoRenderState>> {

    public StolenItemLayer(@NotNull RenderLayerParent<KriftoRenderState, EntityModel<? super KriftoRenderState>> renderer) {
        super(renderer);
    }

    @Override
    public void submit(@NotNull PoseStack poseStack, @NotNull SubmitNodeCollector submitNodeCollector,
                        int lightCoords, @NotNull KriftoRenderState state, float yRot, float xRot) {
        ItemStackRenderState item = state.stolenItem;
        if (item.isEmpty() || !(this.getParentModel() instanceof KriftognathusModel adultModel)) {
            return;
        }

        poseStack.pushPose();
        adultModel.piglug.translateAndRotate(poseStack);
        adultModel.legs.translateAndRotate(poseStack);
        adultModel.backLegs.translateAndRotate(poseStack);
        // Below and behind the leg pivot, so the item reads as gripped in the talons rather than
        // skewered through the shin. Tune alongside the model in-game, not by calculation.
        poseStack.translate(0.0D, 0.3D, 0.1D);
        poseStack.scale(0.6F, 0.6F, 0.6F);
        item.submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}

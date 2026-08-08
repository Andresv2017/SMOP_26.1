package net.darkblade.smop.client.krifto;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jetbrains.annotations.NotNull;

/**
 * Draws whatever a wild Krifto is carrying off after a heist (see {@code StealFromPlayerGoal}),
 * gripped between the hind legs.
 *
 * <p>Adult-only: the model type parameter is the abstract {@code EntityModel<? super
 * KriftoRenderState>} {@code KriftognathusRenderer} shares between its adult and chick models (an
 * {@code AgeableMobRenderer} requirement), so which concrete model {@link #getParentModel()} returns
 * at any given frame depends on the entity's age. The {@code instanceof} below is that check, not a
 * defensive afterthought — a chick model has no {@code gBack_legs}-equivalent bone to attach to. It
 * never actually has anything to draw regardless, since {@code StealFromPlayerGoal} never engages a
 * baby and {@code state.stolenItem} is only ever populated for an adult.
 *
 * <p>The pose walks {@link KriftognathusModel#piglug} → {@link KriftognathusModel#legs} →
 * {@link KriftognathusModel#backLegs} one bone at a time via
 * {@link net.minecraft.client.model.geom.ModelPart#translateAndRotate}. A single translate by
 * {@code backLegs}'s own offset is not enough: {@code gBack_legs} sits three levels below the model
 * root, and {@code gPiglug} alone carries most of this mob's locomotion — its own offset and rotation
 * move every frame the mob walks, flies, or dances through {@code tamed}. Skipping the walk and
 * translating by {@code backLegs} alone would only be correct at the rest pose.
 */
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

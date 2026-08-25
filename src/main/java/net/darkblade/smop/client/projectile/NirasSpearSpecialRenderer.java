package net.darkblade.smop.client.projectile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3fc;

import java.util.function.Consumer;

public class NirasSpearSpecialRenderer implements NoDataSpecialModelRenderer {

    private final NirasSpearModel model;

    public NirasSpearSpecialRenderer(NirasSpearModel model) {
        this.model = model;
    }

    @Override
    public void submit(@NotNull PoseStack poseStack, @NotNull SubmitNodeCollector collector,
                       int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
        poseStack.pushPose();
        poseStack.scale(1.0F, -1.0F, -1.0F);
        collector.submitModelPart(this.model.root(), poseStack,
                this.model.renderType(NirasSpearModel.TEXTURE), lightCoords, overlayCoords,
                null, false, hasFoil, -1, null, outlineColor);
        poseStack.popPose();
    }

    @Override
    public void getExtents(@NotNull Consumer<Vector3fc> output) {
        this.model.root().getExtentsForGui(new PoseStack(), output);
    }

    public record Unbaked() implements NoDataSpecialModelRenderer.Unbaked {

        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public @NotNull MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public NirasSpearSpecialRenderer bake(SpecialModelRenderer.@NotNull BakingContext context) {
            return new NirasSpearSpecialRenderer(
                    new NirasSpearModel(context.entityModelSet().bakeLayer(NirasSpearModel.LAYER_LOCATION)));
        }
    }
}

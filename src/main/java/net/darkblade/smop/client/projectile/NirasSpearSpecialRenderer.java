package net.darkblade.smop.client.projectile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * Draws the spear's real model in place of its flat icon, everywhere except the GUI.
 *
 * <p><b>Why this exists at all.</b> An item's sprite is a flat quad; a spear held flat looks like a
 * sticker. 1.20.1 solved it with a BEWLR, which 26.1 removed. The replacement is data-driven: the
 * item definition picks this renderer by id for every display context it wants in three dimensions,
 * and the id is bound to the codec below through {@code RegisterSpecialModelRendererEvent}.
 *
 * <p><b>The split is inverted from the legacy's.</b> {@code NirasSpearItemBewlr} drew 3D only in the
 * four hand contexts and fell back to a sprite everywhere else — including dropped on the ground and
 * in item frames. Here only the GUI stays flat, which is the one place a sprite genuinely reads
 * better: an inventory slot is 16 pixels and a foreshortened model in it is mush.
 *
 * <p><b>The negative scale is the flip.</b> The mesh is authored pointing the opposite way from item
 * space, so it has to be turned end for end. Vanilla's trident has exactly the same problem and
 * declares {@code new Transformation(null, null, new Vector3f(1, -1, -1), null)} for it; the legacy
 * BEWLR did the same thing as {@code Axis.XP.rotationDegrees(-180)} plus a nudge. Doing it in the
 * pose stack rather than in the model JSON keeps it next to the mesh it corrects.
 */
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

    /** What the GUI uses to frame the item. Measured off the mesh, like the trident's. */
    @Override
    public void getExtents(@NotNull Consumer<Vector3fc> output) {
        this.model.root().getExtentsForGui(new PoseStack(), output);
    }

    /**
     * The serialised form. {@code MapCodec.unit} because this renderer has nothing to configure — the
     * model and texture are fixed — so {@code {"type": "smop:niras_spear"}} in the item definition is
     * the whole of it. Same shape as {@code TridentSpecialRenderer.Unbaked}.
     */
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

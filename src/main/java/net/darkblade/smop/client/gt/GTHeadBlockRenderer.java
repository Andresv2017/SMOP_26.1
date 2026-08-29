package net.darkblade.smop.client.gt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.darkblade.smop.block.GTHeadBlock;
import net.darkblade.smop.block.GTHeadBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Unit;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the trophy head. The block model itself is empty, so everything visible in world comes from
 * here.
 */
public class GTHeadBlockRenderer
        implements BlockEntityRenderer<GTHeadBlockEntity, GTHeadBlockRenderer.HeadRenderState> {

    /**
     * The mesh is authored at the living Grand Tyrant's scale — 1.44 x 5.24 x 5.61 blocks with the
     * neck and throat attached — which is why the legacy renderer's unscaled 1.0F produced a head
     * that swallowed whatever it was placed next to. At 0.30 it comes out 0.43 wide, 1.57 tall and
     * 1.68 deep: it overhangs its own block, as a trophy should, without eating the room.
     */
    private static final float SCALE = 0.30F;

    /**
     * Puts the base of the throat — the cut end of the neck, and by far the largest piece of the
     * mesh — on the floor of the block.
     *
     * <p>It deliberately does NOT anchor the lowest vertex in the mesh. That vertex belongs to the
     * goatee, a zero-thickness plane dangling off the front of the jaw and forward of the block
     * entirely; hanging the trophy from it left every solid part floating, the throat starting a
     * sixth of a block clear of the ground. The goatee now dips just below the block instead, which
     * is what a beard should do.
     */
    private static final float REST_ON_FLOOR = 0.462F;

    /**
     * Slides the head back along its own snout. The model runs forward from the neck, so centred on
     * the block it sits at z 0.04..1.72 — three quarters of a block of jaw and skull hanging off the
     * front over nothing, which is the other half of why it read as floating. This recentres it to
     * z -0.34..1.34, so the block sits under the middle of the head and not under the back of its
     * neck.
     *
     * <p>Measured along the model's depth, not along world south, so it is applied after the facing
     * rotation — see {@link #submit}.
     */
    private static final float RECENTRE_ALONG_SNOUT = -0.379F;

    private final GTHeadModel model;

    public GTHeadBlockRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new GTHeadModel(context.bakeLayer(GTHeadModel.LAYER_LOCATION));
    }

    @Override
    public @NotNull HeadRenderState createRenderState() {
        return new HeadRenderState();
    }

    @Override
    public void extractRenderState(@NotNull GTHeadBlockEntity blockEntity, @NotNull HeadRenderState state,
                                   float partialTicks, @NotNull Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.facingYRot = blockEntity.getBlockState().getValue(GTHeadBlock.FACING).toYRot();
    }

    @Override
    public void submit(@NotNull HeadRenderState state, @NotNull PoseStack poseStack,
                       @NotNull SubmitNodeCollector collector, @NotNull CameraRenderState camera) {
        poseStack.pushPose();

        // Stand at the centre of the block, at the height that rests the neck stump on the floor.
        poseStack.translate(0.5F, REST_ON_FLOOR, 0.5F);

        // Turn it to face, pivoting about that block centre. This has to happen BEFORE the flip
        // further down: scaling by (1, -1, -1) is a half turn about X, and a half turn about X
        // reverses the sign of any later Y rotation. With the flip applied the model's own forward
        // (-Z) points south, and Direction.toYRot() is also south-zero, so the angle that lines
        // them up is simply its negation.
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.facingYRot));

        // Slide back along the snout. AFTER the rotation on purpose: this frame's +Z is the
        // direction the head is actually looking, so the shift stays "backwards along the head" for
        // all four facings. Hoisted above the rotation it would instead drag the pivot off the
        // block centre and degrade into a fixed drift to the north.
        poseStack.translate(0.0F, 0.0F, RECENTRE_ALONG_SNOUT);

        // Entity models are authored Y-down and Z-back; this puts them the right way up in world,
        // and shrinks the whole thing to trophy size on the way.
        poseStack.scale(SCALE, -SCALE, -SCALE);

        collector.submitModel(this.model, Unit.INSTANCE, poseStack, GTHeadModel.TEXTURE,
                state.lightCoords, OverlayTexture.NO_OVERLAY, 0, state.breakProgress);

        poseStack.popPose();
    }

    /**
     * The head still overhangs its own block — 1.57 tall and 1.68 deep against a unit cube — and the
     * facing decides which way that overhang points, so the scope has to cover it in every
     * direction. Two blocks of slack does, with room to spare if SCALE is nudged upward.
     */
    @Override
    public @NotNull AABB getRenderBoundingBox(@NotNull GTHeadBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return new AABB(pos).inflate(2.0D);
    }

    public static class HeadRenderState extends BlockEntityRenderState {
        public float facingYRot;
    }
}

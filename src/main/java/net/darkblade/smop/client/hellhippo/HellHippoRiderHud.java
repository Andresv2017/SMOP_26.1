package net.darkblade.smop.client.hellhippo;

import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.hellhippo.HellHippoEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.NotNull;

/**
 * Draws the mount's condition bar above the hotbar while the player is riding a Hell Hippo.
 *
 * <p><b>Placeholder art, on purpose.</b> {@code hh_testbar.png} is a sizing probe: it is blitted at
 * its full 256×64 with no slicing and no fill, so the only question it answers is whether those
 * dimensions read well on screen at the player's GUI scale. The real texture is two 182×32 states
 * stacked in the same canvas, and wiring the fill means clipping the lower state horizontally — none
 * of which exists yet, so none of it is pretended at here.
 *
 * <p>Hooked to {@code RenderGuiLayerEvent.Post} on the hotbar rather than registered as a layer of
 * its own so the vertical anchor is unambiguous: the hotbar has just been drawn, its geometry is
 * fixed, and the bar can sit a known gap above it.
 */
@EventBusSubscriber(modid = SMOP.MOD_ID, value = Dist.CLIENT)
public final class HellHippoRiderHud {

    private static final Identifier BAR = SMOP.id("textures/entity/hell_hippo/hh_testbar.png");

    /**
     * Drawn size, 1:1 with the sheet's pixels — see the class note. Four pixels wider each side than
     * vanilla's 182-wide hotbar, so it deliberately overhangs it rather than lining up flush; that
     * reads as a frame around the row beneath instead of a second bar stacked on it.
     */
    private static final int BAR_WIDTH = 190;
    private static final int BAR_HEIGHT = 26;
    /**
     * The file's real size. It stays 256×64 while only the top-left {@link #BAR_WIDTH}×{@link
     * #BAR_HEIGHT} is drawn, and both have to be passed through so the UV maths divides by the true
     * sheet size — get these wrong and the crop silently samples the wrong region. Cropping rather
     * than squashing keeps the probe at 1:1, which is the whole point of a sizing test; the
     * placeholder being flat red is what makes discarding the rest harmless.
     */
    private static final int SHEET_WIDTH = 256;
    private static final int SHEET_HEIGHT = 64;

    /** Vanilla's hotbar: 182×22, flush with the bottom edge of the screen. */
    private static final int HOTBAR_HEIGHT = 22;
    /** Clear air between the bar's bottom edge and the top of the hotbar. */
    private static final int GAP = 2;

    @SubscribeEvent
    public static void onRenderHotbar(RenderGuiLayerEvent.@NotNull Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(player.getVehicle() instanceof HellHippoEntity)) {
            return;
        }
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        int x = (graphics.guiWidth() - BAR_WIDTH) / 2;
        int y = graphics.guiHeight() - HOTBAR_HEIGHT - GAP - BAR_HEIGHT;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BAR, x, y,
                0.0F, 0.0F, BAR_WIDTH, BAR_HEIGHT, SHEET_WIDTH, SHEET_HEIGHT);
    }

    private HellHippoRiderHud() {}
}

package net.darkblade.smop.client.rider;

import net.darkblade.smop.SMOP;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.NotNull;

import java.util.List;


@EventBusSubscriber(modid = SMOP.MOD_ID, value = Dist.CLIENT)
public final class RiderAbilityHud {

    private static final Identifier BAR = SMOP.id("textures/gui/rider_ability_bar.png");

    private static final int SHEET_WIDTH = 256;
    private static final int SHEET_HEIGHT = 64;

    private static final int BAR_WIDTH = 190;
    private static final int BAR_HEIGHT = 26;

    private static final float FILL_ROW_V = BAR_HEIGHT;

    private static final int LEFT_X = 5;
    private static final int RIGHT_X = 98;
    private static final int HALF_WIDTH = 87;
    private static final int INNER_Y = 11;
    private static final int INNER_HEIGHT = 3;

    private static final int FRAME_TINT = 0xFFFF0000;

    private static final int VANILLA_STATUS_HEIGHT = 39;
    private static final int GAP = 2;
    private static final int CONTENT_BOTTOM = 20;

    @SubscribeEvent
    public static void onRenderHotbar(RenderGuiLayerEvent.@NotNull Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) {
            return;
        }
        List<RiderAbilityTracker.Entry> entries = RiderAbilityTracker.entries();
        if (entries.isEmpty()) {
            return;
        }
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        int x = (graphics.guiWidth() - BAR_WIDTH) / 2;
        int y = graphics.guiHeight() - VANILLA_STATUS_HEIGHT - GAP - CONTENT_BOTTOM;

        graphics.blit(RenderPipelines.GUI_TEXTURED, BAR, x, y, 0.0F, 0.0F,
                BAR_WIDTH, BAR_HEIGHT, SHEET_WIDTH, SHEET_HEIGHT, FRAME_TINT);

        RiderAbilityTracker.Entry left = entries.get(0);
        RiderAbilityTracker.Entry right = entries.size() > 1 ? entries.get(1) : left;
        fillHalf(graphics, x, y, LEFT_X, true, left);
        fillHalf(graphics, x, y, RIGHT_X, false, right);
    }


    private static void fillHalf(GuiGraphicsExtractor graphics, int x, int y,
                                 int channelX, boolean growsLeft, RiderAbilityTracker.Entry entry) {
        int filled = Math.round(HALF_WIDTH * entry.progress());
        if (filled <= 0) {
            return;
        }
        int localX = growsLeft ? channelX + HALF_WIDTH - filled : channelX;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BAR, x + localX, y + INNER_Y,
                localX, FILL_ROW_V + INNER_Y, filled, INNER_HEIGHT,
                SHEET_WIDTH, SHEET_HEIGHT, entry.tint());
    }

    private RiderAbilityHud() {}
}

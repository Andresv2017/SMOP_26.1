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

@EventBusSubscriber(modid = SMOP.MOD_ID, value = Dist.CLIENT)
public final class HellHippoRiderHud {

    private static final Identifier BAR = SMOP.id("textures/entity/hell_hippo/hh_testbar.png");

    private static final int BAR_WIDTH = 190;
    private static final int BAR_HEIGHT = 26;
    private static final int SHEET_WIDTH = 256;
    private static final int SHEET_HEIGHT = 64;

    private static final int HOTBAR_HEIGHT = 22;
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

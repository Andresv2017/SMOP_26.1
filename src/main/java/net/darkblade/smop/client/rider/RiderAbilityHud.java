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

/**
 * Una barra de recarga por habilidad, apiladas sobre la hotbar.
 *
 * <p>No sabe nada del Hell Hippo: dibuja lo que haya en {@link RiderAbilityTracker}, así que una
 * montura nueva sale en pantalla con solo declarar sus habilidades del lado servidor.
 */
@EventBusSubscriber(modid = SMOP.MOD_ID, value = Dist.CLIENT)
public final class RiderAbilityHud {

    private static final Identifier BAR = SMOP.id("textures/gui/rider_ability_bar.png");

    private static final int SHEET_WIDTH = 256;
    private static final int SHEET_HEIGHT = 64;

    private static final int BAR_WIDTH = 190;
    private static final int BAR_HEIGHT = 26;

    // El sheet lleva dos filas del mismo encuadre: el marco con la tira apagada arriba, y la tira
    // encendida suelta abajo. Por eso el relleno se recorta con las mismas coordenadas que el marco.
    private static final float FILL_ROW_V = BAR_HEIGHT;

    // El canal interior, en coordenadas locales de la barra. Los mide tools/build-ability-bar.py; no
    // se estiman a ojo. Si se retoca el arte, se vuelve a correr el script y se copian de su salida.
    private static final int INNER_X = 5;
    private static final int INNER_Y = 11;
    private static final int INNER_WIDTH = 180;
    private static final int INNER_HEIGHT = 3;

    // El sheet está en escala de grises para poder teñirlo: una fuente roja pura no se puede llevar a
    // morado por multiplicación. Este tinte devuelve el arte original tal cual se autoreó.
    private static final int FRAME_TINT = 0xFFFF0000;

    private static final int HOTBAR_HEIGHT = 22;
    private static final int HOTBAR_GAP = 2;
    private static final int BAR_GAP = 2;

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
        for (int i = 0; i < entries.size(); i++) {
            int y = graphics.guiHeight() - HOTBAR_HEIGHT - HOTBAR_GAP - (i + 1) * (BAR_HEIGHT + BAR_GAP);
            draw(graphics, entries.get(i), x, y);
        }
    }

    private static void draw(GuiGraphicsExtractor graphics, RiderAbilityTracker.Entry entry, int x, int y) {
        // El marco va entero y debajo, así que los remates de los extremos y la calavera central no se
        // cortan nunca — que es la razón de partir la textura en dos filas en vez de tener una versión
        // vacía y otra llena.
        graphics.blit(RenderPipelines.GUI_TEXTURED, BAR, x, y, 0.0F, 0.0F,
                BAR_WIDTH, BAR_HEIGHT, SHEET_WIDTH, SHEET_HEIGHT, FRAME_TINT);

        int filled = Math.round(INNER_WIDTH * entry.progress());
        if (filled <= 0) {
            return;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, BAR, x + INNER_X, y + INNER_Y,
                INNER_X, FILL_ROW_V + INNER_Y, filled, INNER_HEIGHT,
                SHEET_WIDTH, SHEET_HEIGHT, entry.tint());
    }

    private RiderAbilityHud() {}
}

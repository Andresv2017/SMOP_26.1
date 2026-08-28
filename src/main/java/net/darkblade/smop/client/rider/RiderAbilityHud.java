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
 * Una sola barra para las dos habilidades, partida por la calavera del centro.
 *
 * <p>El arte es simétrico y su canal interior ya viene cortado en dos mitades exactas de 87 px por el
 * adorno central, así que cada habilidad se queda con una y la barra sigue leyéndose como una pieza.
 * Antes eran dos barras apiladas y se comían la altura de la vida de la montura.
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

    // Las dos mitades del canal interior y el alto de la tira, en coordenadas locales de la barra.
    // Los mide tools/build-ability-bar.py; no se estiman a ojo. Si se retoca el arte, se vuelve a
    // correr el script y se copian de su salida. El hueco de la calavera va de 92 a 97.
    private static final int LEFT_X = 5;
    private static final int RIGHT_X = 98;
    private static final int HALF_WIDTH = 87;
    private static final int INNER_Y = 11;
    private static final int INNER_HEIGHT = 3;

    // El sheet está en escala de grises para poder teñirlo: una fuente roja pura no se puede llevar a
    // morado por multiplicación. Este tinte devuelve el arte original tal cual se autoreó.
    private static final int FRAME_TINT = 0xFFFF0000;

    // Vanilla apila hacia arriba desde el borde inferior: la hotbar, la fila de salud a guiHeight-39,
    // otros 10 si el jugador lleva armadura, 10 por cada fila de corazones de la montura y 10 más de
    // burbujas al bucear — y el hipopótamo nada. 59 cubre el peor caso de un jinete con armadura
    // sumergido sobre una montura de una fila de corazones, que es lo que hay hoy.
    private static final int VANILLA_STATUS_HEIGHT = 59;
    private static final int GAP = 2;
    // El arte deja filas transparentes arriba y abajo; su contenido acaba en la fila 19.
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

        // El marco va entero y debajo, así que los remates de los extremos y la calavera central no se
        // cortan nunca — que es la razón de partir la textura en dos filas en vez de tener una versión
        // vacía y otra llena.
        graphics.blit(RenderPipelines.GUI_TEXTURED, BAR, x, y, 0.0F, 0.0F,
                BAR_WIDTH, BAR_HEIGHT, SHEET_WIDTH, SHEET_HEIGHT, FRAME_TINT);

        // Con una sola habilidad se refleja en las dos mitades: la barra es simétrica y media barra
        // encendida se leería como una avería. De la tercera en adelante no caben, y una montura que
        // quiera más habilidades necesita otro arte, no otra fila aquí.
        RiderAbilityTracker.Entry left = entries.get(0);
        RiderAbilityTracker.Entry right = entries.size() > 1 ? entries.get(1) : left;
        fillHalf(graphics, x, y, LEFT_X, true, left);
        fillHalf(graphics, x, y, RIGHT_X, false, right);
    }

    /**
     * Pinta una mitad creciendo desde la calavera hacia su remate: lo último que se enciende es el
     * gancho del extremo, así que el instante en que la habilidad queda lista tiene un remate visible.
     */
    private static void fillHalf(GuiGraphicsExtractor graphics, int x, int y,
                                 int channelX, boolean growsLeft, RiderAbilityTracker.Entry entry) {
        int filled = Math.round(HALF_WIDTH * entry.progress());
        if (filled <= 0) {
            return;
        }
        // La fila del relleno comparte encuadre con la del marco, así que la u de origen es siempre la
        // misma x local que el destino.
        int localX = growsLeft ? channelX + HALF_WIDTH - filled : channelX;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BAR, x + localX, y + INNER_Y,
                localX, FILL_ROW_V + INNER_Y, filled, INNER_HEIGHT,
                SHEET_WIDTH, SHEET_HEIGHT, entry.tint());
    }

    private RiderAbilityHud() {}
}

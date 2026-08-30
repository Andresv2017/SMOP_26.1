package net.darkblade.smop.client.gt;

import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.gt.GTEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * La barra de vida del Gran Tirano, dibujada por el cliente en el hueco de las boss bars.
 *
 * <p>Sustituye al {@code ServerBossEvent} que llevaba antes. Un boss event se enciende con el rango
 * de seguimiento de la entidad y se apaga de golpe, y ninguna de las dos cosas se puede tocar: no
 * tiene radio propio ni transparencia. Aquí el cliente ya tiene el GT y su vida sincronizados, así
 * que la barra se decide en local — un radio de aparición propio y un fundido a la entrada y a la
 * salida — sin gastar un solo paquete.
 */
@EventBusSubscriber(modid = SMOP.MOD_ID, value = Dist.CLIENT)
public final class GTHealthBarHud {

    // Sprites de la boss bar roja vanilla: la barra se ve igual que antes, sólo cambia cuándo y con
    // cuánta opacidad se dibuja.
    private static final Identifier BAR_BACKGROUND = Identifier.withDefaultNamespace("boss_bar/red_background");
    private static final Identifier BAR_PROGRESS = Identifier.withDefaultNamespace("boss_bar/red_progress");

    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;
    private static final int BAR_Y = 12;
    private static final int NAME_OFFSET_Y = 9;

    // Radio al que aparece y radio al que se va. Son distintos a propósito: con un solo umbral, un
    // jugador que camina justo encima de él encendería y apagaría la barra a cada paso. Aparecer a
    // 40 deja el doble del alcance al que el GT ficha objetivo, que son 20: la barra es el aviso de
    // que estás entrando en su terreno, y llega antes de que te vea.
    private static final double SHOW_RADIUS = 40.0D;
    private static final double HIDE_RADIUS = 48.0D;

    private static final float FADE_IN_TICKS = 5.0F;
    private static final float FADE_OUT_TICKS = 14.0F;

    // Por debajo de esto el sprite ya no aporta nada y la fuente descarta el texto igualmente.
    private static final float ALPHA_EPSILON = 0.01F;

    // Cuánto se acerca por tick la barra dibujada a la vida real. Es el suavizado que hacía
    // LerpingBossEvent: sin él cada mordisco es un salto seco.
    private static final float PROGRESS_LERP = 0.35F;

    private static final int NO_GT = -1;

    private static int shownId = NO_GT;
    private static @Nullable Component shownName;

    private static float alpha;
    private static float prevAlpha;
    private static float progress;
    private static float prevProgress;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.@NotNull Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;

        prevAlpha = alpha;
        prevProgress = progress;

        if (level == null || player == null) {
            clear();
            return;
        }

        GTEntity gt = pick(level, player);
        if (gt != null) {
            shownId = gt.getId();
            shownName = gt.getDisplayName();
            float target = Mth.clamp(gt.getHealth() / gt.getMaxHealth(), 0.0F, 1.0F);
            // Al enganchar una barra nueva se entra ya con la vida correcta, o la primera aparición
            // sería una barra llena vaciándose sola.
            progress = alpha <= 0.0F ? target : progress + (target - progress) * PROGRESS_LERP;
            if (alpha <= 0.0F) {
                prevProgress = progress;
            }
            alpha = Math.min(1.0F, alpha + 1.0F / FADE_IN_TICKS);
            return;
        }

        alpha = Math.max(0.0F, alpha - 1.0F / FADE_OUT_TICKS);
        if (alpha <= 0.0F) {
            clear();
        }
    }

    /**
     * El GT que le toca a la barra, o {@code null} si no hay ninguno y toca fundir a negro.
     *
     * <p>Mientras el que ya se está mostrando siga vivo y dentro del radio de salida se queda él,
     * aunque haya otro más cerca: la barra no debe saltar de un animal a otro a mitad de pelea.
     */
    private static @Nullable GTEntity pick(@NotNull ClientLevel level, @NotNull Player player) {
        if (shownId != NO_GT) {
            Entity current = level.getEntity(shownId);
            if (current instanceof GTEntity gt && isEligible(gt, player, HIDE_RADIUS)) {
                return gt;
            }
        }

        AABB box = player.getBoundingBox().inflate(SHOW_RADIUS);
        List<GTEntity> candidates = level.getEntitiesOfClass(GTEntity.class, box,
                gt -> isEligible(gt, player, SHOW_RADIUS));

        GTEntity nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (GTEntity candidate : candidates) {
            double distance = candidate.distanceToSqr(player);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    // isAlive() cae en el mismo tick en que la vida llega a cero, así que la muerte entra por aquí:
    // el fundido arranca con el golpe final y no espera a que la animación de muerte retire la
    // entidad.
    private static boolean isEligible(@NotNull GTEntity gt, @NotNull Player player, double radius) {
        return gt.isAlive() && gt.distanceToSqr(player) <= radius * radius;
    }

    private static void clear() {
        shownId = NO_GT;
        shownName = null;
        alpha = 0.0F;
        prevAlpha = 0.0F;
        progress = 0.0F;
        prevProgress = 0.0F;
    }

    @SubscribeEvent
    public static void onRenderBossOverlay(RenderGuiLayerEvent.@NotNull Post event) {
        if (!event.getName().equals(VanillaGuiLayers.BOSS_OVERLAY)) {
            return;
        }
        Component name = shownName;
        if (name == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float drawnAlpha = Mth.lerp(partialTick, prevAlpha, alpha);
        if (drawnAlpha <= ALPHA_EPSILON) {
            return;
        }
        float drawnProgress = Mth.lerp(partialTick, prevProgress, progress);

        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        // Mismo escalón que usa BossHealthOverlay, para que la barra quede por encima de los iconos
        // de efectos igual que la vanilla.
        graphics.nextStratum();

        int tint = ARGB.white(drawnAlpha);
        int x = graphics.guiWidth() / 2 - BAR_WIDTH / 2;

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_BACKGROUND,
                x, BAR_Y, BAR_WIDTH, BAR_HEIGHT, tint);

        int filled = Mth.lerpDiscrete(drawnProgress, 0, BAR_WIDTH);
        if (filled > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_PROGRESS,
                    BAR_WIDTH, BAR_HEIGHT, 0, 0, x, BAR_Y, filled, BAR_HEIGHT, tint);
        }

        int nameX = graphics.guiWidth() / 2 - minecraft.font.width(name) / 2;
        graphics.text(minecraft.font, name, nameX, BAR_Y - NAME_OFFSET_Y, tint);
    }

    private GTHealthBarHud() {}
}

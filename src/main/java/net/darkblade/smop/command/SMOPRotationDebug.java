package net.darkblade.smop.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.gt.GTEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@code /smop debug rotation [ticks]} — vuelca, tick a tick, los tres ángulos de los que depende
 * cómo gira el Grand Tyrant, y los huecos entre ellos.
 *
 * <p><b>Por qué hacen falta los tres y no basta con mirarlo.</b> Un mob tiene tres yaws que casi
 * nunca coinciden y sólo uno se dibuja:
 *
 * <ul>
 *   <li>{@code rumbo} ({@code getYRot}) — a dónde ha decidido ir. No se dibuja en ninguna parte.</li>
 *   <li>{@code cuerpo} ({@code yBodyRot}) — <b>el que se dibuja</b>. En este bicho persigue al rumbo
 *       ande o esté parado, que es lo que {@code GTBodyRotation} añade al control de la librería.</li>
 *   <li>{@code cabeza} ({@code yHeadRot}) — sólo se separa del cuerpo si algo la mueve, y en este
 *       bicho eso es únicamente el {@code setLookAt} de la persecución.</li>
 * </ul>
 *
 * <p>La columna que importa es <b>{@code hueco}</b>: rumbo menos cuerpo. Es la señal entera que
 * alimenta la cascada de {@code GTSpineTurn}, y si vale cero no hay nada que propagar por muy bien
 * afinada que esté la cadena de muelles. En reposo debe rondar el cero; girando tiene que abrirse y
 * quedarse abierto todo el viraje, y su valor estacionario es
 * {@code velocidad de giro / bodyLagMoving} — o sea que si no se abre, el mando es uno de esos dos y
 * no la cadena.
 *
 * <p>{@code giro} es cuánto se movió el rumbo en ESTE tick, para comprobar contra {@code TURN_SPEED}
 * si de verdad está girando a la velocidad que dice la constante.
 */
@EventBusSubscriber(modid = SMOP.MOD_ID)
public final class SMOPRotationDebug {

    /** Radio en el que se buscan Grand Tyrant alrededor de quien mira. */
    private static final double RADIUS = 48.0D;

    /** Por defecto, un vuelco por tick: girar es un transitorio y muestrear de tarde en tarde lo pierde. */
    private static final int DEFAULT_EVERY = 1;

    /** Tope, para no dejarse un reloj corriendo toda la sesión. */
    private static final int MAX_SECONDS = 60;

    @Nullable
    private static ServerPlayer watcher;
    private static int every = DEFAULT_EVERY;
    private static int ticksLeft;
    private static int counter;
    /** Rumbo del tick anterior, por entidad, para poder dar la velocidad de giro real. */
    private static int lastEntityId = -1;
    private static float lastHeading;

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debug").then(Commands.literal("rotation")
                .executes(ctx -> start(ctx.getSource(), DEFAULT_EVERY, 20))
                .then(Commands.argument("cada_n_ticks", IntegerArgumentType.integer(1, 20))
                        .executes(ctx -> start(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "cada_n_ticks"), 20)))
                .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource()))));
    }

    private static int start(CommandSourceStack source, int everyTicks, int seconds) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Ejecútalo como jugador: el volcado mira a tu alrededor."));
            return 0;
        }
        watcher = player;
        every = everyTicks;
        ticksLeft = Math.min(seconds, MAX_SECONDS) * 20;
        counter = 0;
        lastEntityId = -1;
        source.sendSuccess(() -> Component.literal(
                        "Rotación: volcando cada " + everyTicks + " tick(s) durante " + seconds + " s.")
                .withStyle(ChatFormatting.GREEN), false);
        source.sendSuccess(() -> Component.literal(
                        "  rumbo=a dónde va · cuerpo=lo que SE DIBUJA · hueco=rumbo-cuerpo, que es lo que mueve la columna")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        watcher = null;
        source.sendSuccess(() -> Component.literal("Rotación: volcado parado.")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.@NotNull Post event) {
        if (watcher == null) {
            return;
        }
        if (--ticksLeft <= 0) {
            watcher.sendSystemMessage(Component.literal("Rotación: volcado terminado.")
                    .withStyle(ChatFormatting.GRAY));
            watcher = null;
            return;
        }
        if (++counter < every) {
            return;
        }
        counter = 0;

        List<GTEntity> nearby = watcher.level().getEntitiesOfClass(GTEntity.class,
                new AABB(watcher.blockPosition()).inflate(RADIUS));
        if (nearby.isEmpty()) {
            return;
        }
        GTEntity gt = nearby.get(0);

        float heading = Mth.wrapDegrees(gt.getYRot());
        float body = Mth.wrapDegrees(gt.yBodyRot);
        float head = Mth.wrapDegrees(gt.yHeadRot);
        float headingGap = Mth.degreesDifference(body, heading);
        float headGap = Mth.degreesDifference(body, head);

        float turnRate = gt.getId() == lastEntityId
                ? Mth.degreesDifference(lastHeading, heading)
                : 0.0F;
        lastEntityId = gt.getId();
        lastHeading = heading;

        // Dos medidas de "se mueve", y a propósito las dos: el desplazamiento real por tick es el que
        // usan el control de rotación y el de movimiento para decidir, y walkAnimation.speed() es lo
        // que llega al render. Si alguna vez discrepan, el bicho se estaría animando distinto de como
        // se comporta, y eso hay que verlo, no deducirlo.
        double dx = gt.getX() - gt.xo;
        double dz = gt.getZ() - gt.zo;
        double stepped = Math.sqrt(dx * dx + dz * dz);
        boolean moving = stepped > 0.001D;

        String line = String.format(
                "rumbo %7.1f | cuerpo %7.1f | cabeza %7.1f || hueco %+6.1f | cab-cue %+6.1f | giro %+5.2f/t | "
                        + "avance %.4f b/t (%s) | anim %.3f",
                heading, body, head, headingGap, headGap, turnRate,
                stepped, moving ? "anda" : "parado", gt.walkAnimation.speed());

        // Amarillo cuando el hueco está abierto de verdad: es el único estado en el que la cascada
        // tiene algo que propagar, y así se ve de un vistazo cuándo mirar al bicho.
        ChatFormatting colour = Math.abs(headingGap) >= 8.0F ? ChatFormatting.YELLOW : ChatFormatting.GRAY;
        watcher.sendSystemMessage(Component.literal(line).withStyle(colour));
    }

    private SMOPRotationDebug() {}
}

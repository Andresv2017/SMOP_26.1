package net.darkblade.smop.client.gt;

import net.darkblade.deluxelib.anim.MobAnimator;
import net.darkblade.deluxelib.client.render.DeluxeEntityRenderState;
import net.darkblade.deluxelib.client.rig.AnimContext;
import net.darkblade.deluxelib.client.rig.RigComponent;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * El giro recorre al animal de la cabeza a la cola, en vez de rotarlo entero de una pieza.
 *
 * <p><b>Por qué hacía falta escribirlo.</b> El modelo se dibuja en {@code yBodyRot}, y
 * {@code SmoothBodyRotationControl} hace que ese ángulo persiga al rumbo mientras el animal anda,
 * recortando la cabeza contra el cuerpo con {@code rotateIfNecessary}. O sea que cabeza, tronco y cola
 * comparten un único ángulo y giran a la vez: un bloque. En un animal de seis bloques eso es lo que se
 * lee como "de cartón".
 *
 * <p>{@code TurnLeanAdditive} de la librería no lo resuelve por dos motivos. Uno, aplica el MISMO
 * valor a todos los huesos a la vez, con distinto factor y signo, así que puede inclinar pero no
 * puede propagar — no hay retardo entre un hueso y el siguiente, y el retardo es justo lo que se lee
 * como peso. Dos, lee el hueco entre cabeza y cuerpo, que en este bicho vale casi cero: no tiene
 * goals de mirada, así que la cabeza sólo se separa del cuerpo cuando {@code ChaseTargetBehavior}
 * llama a {@code setLookAt}, o sea únicamente persiguiendo.
 *
 * <p><b>Cómo funciona.</b> Una cadena de muelles: el primer eslabón persigue el hueco de giro, y cada
 * eslabón siguiente persigue <em>la pose del anterior</em>, no la señal original. Eso produce una onda
 * que baja por la columna — la cabeza ya apunta a donde va cuando el tronco aún está girando, y la
 * cola llega la última. Cada eslabón tiene su propia frecuencia, más lenta según se aleja de la
 * cabeza, que es lo que abre el retardo entre uno y otro.
 *
 * <p>La cola transmite en NEGATIVO a propósito: un animal grande que gira a la derecha lanza la cola a
 * la izquierda y la recoge después. Sin ese contragiro la cola parece pegada con cola.
 *
 * <p><b>De dónde sale el hueco: del RUMBO, siempre.</b> {@code GTBodyRotation} hace que el cuerpo
 * persiga al rumbo ande o esté parado, así que rumbo-menos-cuerpo es la señal correcta en los dos
 * estados y no hay nada que elegir.
 *
 * <p>La primera versión sí elegía —rumbo andando, cabeza parado— copiando el reparto que hace el
 * control de rotación de la librería, y eso era un error que costó una sesión entera de pruebas: al
 * cambiar el cuerpo para que persiguiera SIEMPRE al rumbo, esa rama dejó de tener sentido y se quedó.
 * Medido en juego con {@code /smop debug rotation}, con el animal parado el hueco contra el rumbo
 * valía 25 grados y contra la cabeza 2.5 — por debajo de la zona muerta. O sea que <b>la columna
 * entera estaba muerta justo en el estado en el que más se la mira</b>: girando sobre el sitio.
 */
public final class GTSpineTurn implements RigComponent<GTModel> {

    /**
     * Un eslabón de la columna.
     *
     * @param factor qué parte del hueco de giro le toca a ESTE hueso. Negativo = contragiro.
     * @param maxDegrees tope, para que un giro de 180 no le desmonte el cuello
     * @param responseHz frecuencia de su muelle. Más baja = llega más tarde = pesa más.
     */
    private record Link(Function<GTModel, ModelPart> part, float factor, float maxDegrees,
                        float responseHz) {}

    /**
     * De la cabeza a la punta de la cola, en orden de RETARDO: cada uno persigue al anterior.
     *
     * <p><b>Los factores cuentan con que los huesos están anidados.</b> {@code head} cuelga de
     * {@code neck} y éste de {@code body_parts}, así que sus rotaciones se SUMAN en el mundo. Los de
     * delante suman 0.15 + 0.35 + 0.50 = 1.00, o sea que la cabeza acaba apuntando exactamente al
     * rumbo, ni más ni menos. Tratarlos como independientes —el primer intento— ponía la cabeza al
     * 175% del hueco.
     *
     * <p>La cola va en negativo y acumula al revés: 0.15 − 0.30 − 0.30 − 0.25, así que la punta barre
     * unos 0.70 del hueco hacia el lado contrario. Un animal grande que gira a la derecha lanza la cola
     * a la izquierda y la recoge después; sin ese contragiro la cola parece pegada con cola.
     *
     * <p>Simulado con un viraje de 90 grados a la velocidad de {@code TURN_SPEED}: la cabeza llega a
     * 21 grados, la punta de la cola a 15 en sentido contrario, y entre que una y otra alcanzan su
     * pico pasan 11 ticks. Ese medio segundo de diferencia ES el efecto.
     */
    private static final List<Link> CHAIN = List.of(
            new Link(m -> m.head, 0.50F, 30.0F, 2.4F),
            new Link(m -> m.neck, 0.35F, 22.0F, 1.7F),
            new Link(m -> m.body_parts, 0.15F, 10.0F, 1.2F),
            new Link(m -> m.tail1, -0.30F, 18.0F, 0.95F),
            new Link(m -> m.tail2, -0.30F, 18.0F, 0.75F),
            new Link(m -> m.tail3, -0.25F, 16.0F, 0.60F));

    /** Amortiguación de todos los muelles. Por debajo de 1 rebotan al terminar el giro. */
    private static final float DAMPING = 0.55F;

    /**
     * Grados de hueco que se ignoran. Seguir un camino deja siempre unos pocos grados de corrección
     * contra la rejilla de bloques, y amplificar ese ruido hace que andar recto parezca culebrear.
     */
    private static final float DEADZONE = 3.0F;

    /** Paso máximo de integración, para que el muelle no explote a 10 FPS. */
    private static final float MAX_STEP_SECONDS = 1.0F / 120.0F;

    private static final class State {
        final float[] pose = new float[CHAIN.size()];
        final float[] velocity = new float[CHAIN.size()];
        long lastNanos = -1L;
    }

    /** Por entidad, con el animador de clave: es lo estable que el estado de render lleva encima. */
    private final WeakHashMap<MobAnimator<?>, State> states = new WeakHashMap<>();

    @Override
    public void apply(@NotNull DeluxeEntityRenderState renderState, @NotNull GTModel model,
                      @NotNull AnimContext ctx) {
        MobAnimator<?> animator = renderState.animator;
        if (animator == null) {
            return;
        }
        State state = this.states.computeIfAbsent(animator, a -> new State());
        float dt = deltaSeconds(state);

        float gap = headingGap(renderState, animator.getEntity(), ctx.partialTick());

        // Integración de la cadena. Cada eslabón persigue al anterior; el primero, al hueco.
        float upstream = gap;
        for (int i = 0; i < CHAIN.size(); i++) {
            Link link = CHAIN.get(i);
            // El objetivo es la pose del anterior SIN escalar. Escalarla aquí —lo que hacía el primer
            // intento— multiplica los factores a lo largo de la cadena y la señal se apaga: medido, la
            // punta de la cola se quedaba en 1.8 grados. La amplitud se aplica al SALIR, abajo.
            float target = upstream;
            float omega = (float) (2.0 * Math.PI * link.responseHz());
            float remaining = dt;
            while (remaining > 0.0F) {
                float h = Math.min(remaining, MAX_STEP_SECONDS);
                state.velocity[i] += (omega * omega * (target - state.pose[i])
                        - 2.0F * DAMPING * omega * state.velocity[i]) * h;
                state.pose[i] += state.velocity[i] * h;
                remaining -= h;
            }
            // Lo que ve el siguiente es la pose YA alcanzada por éste, no su objetivo: es de ahí de
            // donde sale el retardo que se lee como peso.
            upstream = state.pose[i];
        }

        if (animator.isAdditiveBlocked()) {
            return;
        }
        for (int i = 0; i < CHAIN.size(); i++) {
            Link link = CHAIN.get(i);
            float amount = Mth.clamp(state.pose[i] * link.factor(),
                    -link.maxDegrees(), link.maxDegrees());
            if (Math.abs(amount) < 0.05F) {
                continue;
            }
            link.part().apply(model).yRot += (float) Math.toRadians(amount);
        }
    }

    /**
     * Cuántos grados le faltan al cuerpo visible para alcanzar a donde el animal quiere mirar.
     *
     * <p>Positivo = tiene que girar a la derecha.
     */
    private static float headingGap(@NotNull DeluxeEntityRenderState renderState,
                                    @NotNull LivingEntity entity, float partialTick) {
        // El rumbo, y punto: es a quien persigue el cuerpo en los dos estados. Interpolado con el
        // partial tick igual que el bodyRot contra el que se resta, o el hueco temblaría entre frames.
        float wanted = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float gap = Mth.wrapDegrees(wanted - renderState.bodyRot);
        float magnitude = Math.abs(gap) - DEADZONE;
        return magnitude <= 0.0F ? 0.0F : Math.copySign(magnitude, gap);
    }

    private static float deltaSeconds(@NotNull State state) {
        long now = System.nanoTime();
        if (state.lastNanos < 0L) {
            state.lastNanos = now;
            return 0.0F;
        }
        float dt = (now - state.lastNanos) / 1_000_000_000.0F;
        state.lastNanos = now;
        return Mth.clamp(dt, 0.0F, 0.1F);
    }
}

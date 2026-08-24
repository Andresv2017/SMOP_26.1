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
 * <p><b>De dónde sale el hueco</b>, y por qué son dos fuentes y no una: se copia el criterio del
 * propio control de rotación. Andando, el cuerpo persigue al RUMBO ({@code getYRot}), así que el hueco
 * es rumbo contra cuerpo. Parado, el cuerpo persigue a la CABEZA, así que el hueco es cabeza contra
 * cuerpo — y ése es el caso de plantarse a mirarte en combate. Leer sólo uno de los dos deja medio
 * repertorio sin animar.
 */
public final class GTSpineTurn implements RigComponent<GTModel> {

    /**
     * Un eslabón de la columna.
     *
     * @param transmit cuánto del eslabón anterior recoge éste. Negativo = va al revés (contragiro).
     * @param maxDegrees tope, para que un giro de 180 no le desmonte el cuello
     * @param responseHz frecuencia de su muelle. Más baja = llega más tarde = pesa más.
     */
    private record Link(Function<GTModel, ModelPart> part, float transmit, float maxDegrees,
                        float responseHz) {}

    /**
     * De la cabeza a la punta de la cola. El orden importa: cada uno persigue al de arriba.
     *
     * <p>La cabeza transmite 1.0 y responde rápido, o sea que apunta a donde va el animal casi al
     * instante — es lo que hace que parezca que el bicho decide y el cuerpo obedece. De ahí para abajo
     * la transmisión y la frecuencia bajan, y en {@code tail1} el signo se invierte.
     */
    private static final List<Link> CHAIN = List.of(
            new Link(m -> m.head, 1.00F, 40.0F, 2.2F),
            new Link(m -> m.neck, 0.45F, 22.0F, 1.6F),
            new Link(m -> m.body_parts, 0.30F, 10.0F, 1.1F),
            new Link(m -> m.tail1, -0.85F, 20.0F, 0.9F),
            new Link(m -> m.tail2, 0.85F, 16.0F, 0.7F),
            new Link(m -> m.tail3, 0.85F, 14.0F, 0.55F));

    /** Amortiguación de todos los muelles. Por debajo de 1 rebotan al terminar el giro. */
    private static final float DAMPING = 0.55F;

    /**
     * Grados de hueco que se ignoran. Seguir un camino deja siempre unos pocos grados de corrección
     * contra la rejilla de bloques, y amplificar ese ruido hace que andar recto parezca culebrear.
     */
    private static final float DEADZONE = 3.0F;

    /** Por encima de esto se considera que anda, y el hueco se mide contra el rumbo. */
    private static final float MOVING_THRESHOLD = 0.01F;

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
            float target = upstream * link.transmit();
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
            float amount = Mth.clamp(state.pose[i], -link.maxDegrees(), link.maxDegrees());
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
        boolean moving = renderState.walkAnimationSpeed > MOVING_THRESHOLD;
        // Andando manda el rumbo; parado manda la cabeza. Mismo criterio que el control de rotación
        // usa para decidir a quién persigue el cuerpo, y por eso las dos fuentes casan.
        float wanted = moving
                ? Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot())
                : Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
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

package net.darkblade.smop.entity.gt;

import net.darkblade.deluxelib.entity.ai.rotation.SmoothBodyRotationControl;
import net.minecraft.util.Mth;

/**
 * El control de rotación de la librería, más lo que le falta a un mob sin goals de mirada.
 *
 * <p><b>El agujero que tapa.</b> El control base reparte el trabajo según el animal ande o no:
 * andando, el cuerpo persigue al RUMBO; parado, persigue a la CABEZA. Ese reparto da por hecho que
 * algo mueve la cabeza cuando el animal está quieto, que es lo que hacen los goals de mirada. El
 * Grand Tyrant no tiene ninguno — se le quitaron en el port porque le hacían girar sobre su propio
 * eje — así que parado no hay nada que perseguir y el cuerpo se queda clavado mientras el rumbo gira
 * solo.
 *
 * <p>Medido en juego con {@code /smop debug rotation}: el rumbo se fue <b>cien grados</b> con el
 * cuerpo sin moverse un solo grado. Eso pasa cada vez que decide caminar hacia algo que tiene detrás,
 * y todo ese hueco había que pagarlo después de una sentada.
 *
 * <p><b>Y el techo, que es lo otro.</b> El retraso estacionario del cuerpo no se elige: sale solo, y
 * vale {@code velocidad de giro / factor de seguimiento}. Ese retraso es la señal que
 * {@code GTSpineTurn} propaga por la columna, así que interesa que sea generoso — pero el mismo
 * factor gobierna el paseo y el combate, y el Grand Tyrant gira casi tres veces más rápido
 * persiguiendo. Un cuerpo apuntando setenta grados fuera de su dirección de marcha no se lee como
 * peso: se lee como un animal andando de lado.
 */
public class GTBodyRotation extends SmoothBodyRotationControl<GTEntity> {

    /**
     * Con qué suavidad persigue el cuerpo al rumbo <b>estando parado</b>. Fracción del hueco por tick.
     *
     * <p>Ocho por ciento es lento a propósito: es el giro sobre el sitio, y es donde más se nota que
     * el animal pesa. Con el rumbo girando a los 2.5 grados/tick de {@code TURN_SPEED} esto deja un
     * hueco estacionario de unos 31 grados — justo por debajo del techo, que así queda de red de
     * seguridad y no de mecanismo de uso diario.
     */
    private static final float STILL_FOLLOW = 0.08F;

    /**
     * Grados que el cuerpo puede quedarse por detrás de a dónde va.
     *
     * <p>Treinta y cinco es bastante: en un animal de 3.2 bloques de ancho se ve claramente el tronco
     * atravesado respecto al camino. Subirlo lo hace más dramático y más torpe; bajarlo lo endereza.
     */
    private static final float MAX_LAG_DEGREES = 35.0F;

    /**
     * Lo más que la recuperación del techo puede mover el cuerpo en un tick.
     *
     * <p><b>Existe porque la primera versión no lo tenía, y eso era el bug.</b> Asignaba
     * {@code yBodyRot} de golpe, y medido en juego eso salía como un salto de 67 grados en un solo
     * tick, con la cabeza persiguiendo al cuerpo durante los siete siguientes. El control de la
     * librería lleva un comentario que explica justo por qué él sube el tope de paso en vez de
     * recortar el hueco — <em>"keeps every frame rate-limited, no snap, ever"</em> — y la primera
     * versión metió exactamente el salto que ese diseño evita.
     */
    private static final float CATCH_UP_DEGREES = 6.0F;

    /** Mismo umbral de movimiento que usa el control de la librería para repartir su trabajo. */
    private static final double MOVING_THRESHOLD = 1.0E-3D;

    private final GTEntity gt;

    public GTBodyRotation(GTEntity entity) {
        super(entity);
        this.gt = entity;
    }

    @Override
    public void clientTick() {
        super.clientTick();

        // Andando no se toca nada: ahí el base YA persigue al rumbo, con su propio amortiguado
        // (bodyLagMoving) y sus dos guardas — continuidad de dirección más allá de 120 grados y un
        // tope de paso que se adapta a la velocidad del objetivo. Duplicar el seguimiento aquí haría
        // que el cuerpo alcanzara al rumbo más rápido de lo afinado y se comería la cascada.
        if (this.isMoving()) {
            return;
        }

        float lag = Mth.degreesDifference(this.gt.yBodyRot, this.gt.getYRot());

        // Seguimiento normal: una fracción del hueco por tick. Es lo que hace que, cuando el rumbo
        // deja de girar, el cuerpo termine de encararse en vez de quedarse desviado para siempre —
        // que es lo que pasaba recortando sólo el exceso sobre el techo.
        float step = lag * STILL_FOLLOW;

        // Y si el hueco se ha pasado del techo, se acelera lo justo para volver a meterlo dentro. Es
        // una red de seguridad para huecos que ya vienen grandes (un spawn, un teletransporte), no el
        // mecanismo de todos los días: con STILL_FOLLOW el hueco se estabiliza por debajo del techo.
        float excess = Math.abs(lag) - MAX_LAG_DEGREES;
        if (excess > 0.0F) {
            step = Math.copySign(Math.max(Math.abs(step), Math.min(excess, CATCH_UP_DEGREES)), lag);
        }

        // El tope de paso del propio control, para que ninguna de las dos ramas pueda dar un salto.
        step = Mth.clamp(step, -this.bodyMax, this.bodyMax);
        this.gt.yBodyRot = Mth.wrapDegrees(this.gt.yBodyRot + step);
    }

    private boolean isMoving() {
        double dx = this.gt.getX() - this.gt.xo;
        double dz = this.gt.getZ() - this.gt.zo;
        return dx * dx + dz * dz > MOVING_THRESHOLD;
    }
}

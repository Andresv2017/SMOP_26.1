package net.darkblade.smop.entity.gt;

import net.darkblade.deluxelib.entity.ai.rotation.SmoothBodyRotationControl;
import net.minecraft.util.Mth;

/**
 * El control de rotación de la librería, con un techo al retraso del cuerpo.
 *
 * <p><b>Por qué hace falta el techo.</b> El retraso estacionario del cuerpo contra el rumbo no se
 * elige: sale solo, y vale {@code velocidad de giro / bodyLagMoving}. Ese retraso es justo la señal
 * que {@code GTSpineTurn} propaga por la columna, así que interesa que sea generoso — pero el mismo
 * factor gobierna el paseo y el combate, y el Grand Tyrant gira casi tres veces más rápido
 * persiguiendo. Un factor bajo, elegido para que el paseo pese, deja en combate un retraso enorme, y
 * un cuerpo apuntando setenta grados fuera de su dirección de marcha no se lee como peso: se lee como
 * un animal andando de lado.
 *
 * <p>Separar las dos cosas lo arregla. El factor decide <b>cómo se siente</b> el retraso —con qué
 * suavidad se abre y se cierra— y el techo decide <b>hasta dónde puede llegar</b>, sea cual sea la
 * velocidad de giro. Así se puede pedir un paseo muy pesado sin que el combate se vaya de las manos.
 *
 * <p>Se aplica DESPUÉS del control normal en vez de reescribirlo: el de la librería tiene dos guardas
 * ganadas a base de bugs —continuidad de dirección más allá de los 120 grados, y un tope de paso que
 * se adapta a la velocidad del objetivo— que no hay ninguna razón para volver a escribir.
 */
public class GTBodyRotation extends SmoothBodyRotationControl<GTEntity> {

    /**
     * Grados que el cuerpo puede quedarse por detrás de a dónde va.
     *
     * <p>Treinta y cinco es bastante: en un animal de 3.2 bloques de ancho se ve claramente el tronco
     * atravesado respecto al camino. Subirlo lo hace más dramático y más torpe; bajarlo lo endereza.
     */
    private static final float MAX_LAG_DEGREES = 35.0F;

    /** Mismo umbral que usa el control de la librería para decidir si el animal anda. */
    private static final double MOVING_THRESHOLD = 1.0E-3D;

    private final GTEntity gt;

    public GTBodyRotation(GTEntity entity) {
        super(entity);
        this.gt = entity;
    }

    @Override
    public void clientTick() {
        super.clientTick();

        // A quién persigue el cuerpo depende de si anda, igual que arriba: andando al rumbo, parado a
        // la cabeza. El techo tiene que medirse contra el mismo objetivo o recortaría el hueco
        // equivocado.
        float target = this.isMoving() ? this.gt.getYRot() : this.gt.yHeadRot;
        float lag = Mth.degreesDifference(this.gt.yBodyRot, target);
        if (Math.abs(lag) > MAX_LAG_DEGREES) {
            this.gt.yBodyRot = Mth.wrapDegrees(target - Math.copySign(MAX_LAG_DEGREES, lag));
        }
    }

    private boolean isMoving() {
        double dx = this.gt.getX() - this.gt.xo;
        double dz = this.gt.getZ() - this.gt.zo;
        return dx * dx + dz * dz > MOVING_THRESHOLD;
    }
}

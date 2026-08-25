package net.darkblade.smop.entity.gt;

import net.darkblade.deluxelib.entity.ai.rotation.SmoothBodyRotationControl;
import net.minecraft.util.Mth;

public class GTBodyRotation extends SmoothBodyRotationControl<GTEntity> {

    private static final float STILL_FOLLOW = 0.08F;

    private static final float MAX_LAG_DEGREES = 35.0F;

    private static final float CATCH_UP_DEGREES = 6.0F;

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

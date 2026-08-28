package net.darkblade.smop.entity;

import net.darkblade.smop.entity.rider.RiderAbility;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public interface RiderControllable {

    void onRiderAction(ServerPlayer rider, RiderAction action);

    /**
     * Las habilidades cuya recarga se le dibuja al jinete, de izquierda a derecha: el índice 0 se
     * queda con la mitad izquierda de la barra y el 1 con la derecha. Con una sola, se refleja en
     * ambas; de la tercera en adelante no caben.
     *
     * <p>Vacío por defecto para que una montura que solo quiera {@link #onRiderAction} no tenga que
     * saber nada de esto.
     */
    default List<RiderAbility> riderAbilities() {
        return List.of();
    }

    enum RiderAction {
        ATTACK,
        FEAR,
        OPEN_INVENTORY,
        DESCEND_START,
        DESCEND_STOP
    }
}

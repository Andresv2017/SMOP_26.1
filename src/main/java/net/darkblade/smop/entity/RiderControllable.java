package net.darkblade.smop.entity;

import net.darkblade.smop.entity.rider.RiderAbility;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public interface RiderControllable {

    void onRiderAction(ServerPlayer rider, RiderAction action);

    /**
     * Las habilidades cuya recarga se le dibuja al jinete, de abajo a arriba: el índice 0 es la barra
     * pegada a la hotbar.
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

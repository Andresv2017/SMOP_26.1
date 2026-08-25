package net.darkblade.smop.entity;

import net.minecraft.server.level.ServerPlayer;

public interface RiderControllable {

    void onRiderAction(ServerPlayer rider, RiderAction action);

    enum RiderAction {
        ATTACK,
        FEAR,
        OPEN_INVENTORY,
        DESCEND_START,
        DESCEND_STOP
    }
}

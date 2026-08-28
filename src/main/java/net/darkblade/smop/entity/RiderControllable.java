package net.darkblade.smop.entity;

import net.darkblade.smop.entity.rider.RiderAbility;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public interface RiderControllable {

    void onRiderAction(ServerPlayer rider, RiderAction action);

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

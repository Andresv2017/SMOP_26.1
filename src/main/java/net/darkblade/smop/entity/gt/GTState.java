package net.darkblade.smop.entity.gt;

import net.darkblade.deluxelib.entity.ai.cortex.StateEnum;

public enum GTState implements StateEnum {
    WANDER(0),
    CHASE(1),
    BITE(2),
    HORN_SWING(3),
    CLAW_SWING(4),
    STOMP(5),
    ROAR(6);

    private final int id;

    GTState(int id) {
        this.id = id;
    }

    @Override
    public int id() {
        return this.id;
    }
}

package net.darkblade.smop.entity.gt;

import net.darkblade.deluxelib.entity.ai.cortex.StateEnum;

/**
 * The Grand Tyrant's combat states.
 *
 * <p>All seven are declared from the start even though module 1 only wires {@code WANDER}: the enum
 * costs nothing and it means the later modules add behaviours without touching this file. The ids are
 * explicit and stable because {@code CortexMonster} syncs the current state to the client as an int.
 */
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

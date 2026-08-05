package net.darkblade.smop.entity.sleep;

/**
 * Opt-out for the default "a nearby player wakes me" rule. A mob that does not implement this is
 * woken by players; implementing it and returning {@code false} lets a mob sleep through them
 * (the Tangoftero, which only cares about undead).
 */
public interface ISleepAwareness {

    boolean shouldWakeOnPlayerProximity();
}

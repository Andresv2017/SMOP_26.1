package net.darkblade.smop.entity.sleep;

import net.minecraft.world.entity.EntityType;

import java.util.Set;

public interface ISleepingEntity {

    SleepPhase sleepPhase();

    void setSleepPhase(SleepPhase phase);

    Set<EntityType<?>> getInterruptingEntityTypes();

    int getSittingDuration();

    int sleepPhaseDuration(SleepPhase phase);

    // ───────────────────────────────────────────────────── DERIVED READERS ─────

    boolean isSleeping();

    default boolean isPreparingSleep() {
        return this.sleepPhase() == SleepPhase.PREPARING_SLEEP;
    }

    default boolean isAwakening() {
        return this.sleepPhase() == SleepPhase.AWAKENING;
    }

    default boolean isSitting() {
        return this.sleepPhase() == SleepPhase.SITTING;
    }

    default boolean isInSleepCycle() {
        return this.sleepPhase() != SleepPhase.NONE;
    }

    // ───────────────────────────────────────────────────── ANIMATION HOOK ─────

    default void onSleepPhaseBegin(SleepPhase phase) {}
}

package net.darkblade.smop.entity.sleep;

import net.minecraft.world.entity.EntityType;

import java.util.Set;

/**
 * State contract every sleeping mob exposes, driven by {@link SleepGoal}.
 *
 * <p>The cycle is a list of {@link SleepPhase}s, and a mob runs whichever of them it authored clips
 * for — three for the Tangoftero, six for the Krifto. Nothing here plays animations: a mob's
 * {@code registerAnimations()} binds its clips to the phase with play conditions, so the phase is the
 * only thing that has to be synced.
 *
 * <p><b>One field, not one flag per phase.</b> {@link #sleepPhase()} is the whole state; the boolean
 * readers below are derived from it. That keeps six phases from costing six synced booleans — the
 * same call {@code SMOPAnimal}'s scripted-action state makes — and it means every existing
 * {@code setPlayCondition(a -> this.isSleeping())} kept working untouched when the phases were added.
 */
public interface ISleepingEntity {

    /** {@link SleepPhase#NONE} when awake. The single source of truth for the whole cycle. */
    SleepPhase sleepPhase();

    void setSleepPhase(SleepPhase phase);

    /** Entity types whose presence nearby wakes this mob. Empty means "nothing in particular". */
    Set<EntityType<?>> getInterruptingEntityTypes();

    /**
     * How long this mob sits before lying down, in ticks. Only consulted when it has a {@code sit}
     * clip; a mob without one never enters that phase.
     *
     * <p>Randomised by default so a herd does not lie down in unison — the same reason
     * {@code SleepUrge} staggers when each animal decides it is tired. Override for a species that
     * should linger, or settle straight away.
     */
    int getSittingDuration();

    /**
     * Ticks {@code phase} runs for on this mob, or <b>0 meaning "this mob does not have that phase"</b>
     * — which is how the cycle discovers its own shape (see {@link SleepPhase}).
     *
     * <p>For the transitions that is the length of the registered clip, so the phase and the animation
     * are one number rather than two that can drift; a clip that was never registered measures 0 and
     * the phase drops out. {@link SleepPhase#SLEEPING} is the exception the goal handles itself: it
     * runs until something wakes the mob, not on a timer.
     *
     * <p>Asked at the moment each phase starts, never cached: {@code registerGoals()} runs from
     * {@code Mob}'s constructor, long before {@code registerAnimations()} is fired by
     * {@code EntityJoinLevelEvent}, so at construction time none of these clips exist yet.
     */
    int sleepPhaseDuration(SleepPhase phase);

    // ───────────────────────────────────────────────────── DERIVED READERS ─────

    boolean isSleeping();

    default boolean isPreparingSleep() {
        return this.sleepPhase() == SleepPhase.PREPARING_SLEEP;
    }

    default boolean isAwakening() {
        return this.sleepPhase() == SleepPhase.AWAKENING;
    }

    /** Sitting, whether on the way down or on the way up. */
    default boolean isSitting() {
        return this.sleepPhase() == SleepPhase.SITTING;
    }

    /** True during any phase of the cycle — the usual gate for "can this mob act right now". */
    default boolean isInSleepCycle() {
        return this.sleepPhase() != SleepPhase.NONE;
    }

    // ───────────────────────────────────────────────────── ANIMATION HOOK ─────

    /**
     * Called once as each phase begins, so the mob can start that phase's clip.
     *
     * <p>This exists because {@code MobAnimator}'s auto-start loop only ever starts
     * {@code Loop.REPEATING} animations — a {@code PLAY_ONCE} clip with a play condition is never
     * started by it, no matter how true the condition is. The looping clips work without help; the
     * one-shot transitions have to be triggered, and the goal is the only thing that knows the exact
     * tick each phase starts on.
     *
     * <p>Empty here rather than playing the clip itself: {@code playIfRegistered} is protected on
     * {@code SMOPAnimal}, which a default method cannot reach. {@code SMOPAnimal} supplies the real
     * implementation, and it is entirely mechanical — play the clip named after the phase. Override
     * it to hang a sound or particles off a phase, calling {@code super} to keep the clip.
     *
     * <p>Same shape as {@code AbstractFlyingEntity}'s {@code onTakeoffBegin}/{@code onLandingBegin}
     * hooks in DeluxeLib.
     */
    default void onSleepPhaseBegin(SleepPhase phase) {}
}

package net.darkblade.smop.entity.sleep;

import net.minecraft.world.entity.EntityType;

import java.util.Set;

/**
 * State contract every sleeping mob exposes, driven by {@link SleepCycleController}.
 *
 * <p>The cycle has three phases so the animation system can crossfade them: settling down
 * ({@code preparingSleep}), asleep, and getting up ({@code awakening}). Nothing here plays
 * animations — a mob's {@code registerAnimations()} binds its clips to these flags with play
 * conditions, so the state is the only thing that has to be synced.
 *
 * <p>Port note: 1.20.1 spelled this {@code isAwakeing}/{@code setAwakeing}; corrected here.
 */
public interface ISleepingEntity {

    boolean isSleeping();

    void setSleeping(boolean sleeping);

    boolean isPreparingSleep();

    void setPreparingSleep(boolean preparing);

    boolean isAwakening();

    void setAwakening(boolean awakening);

    /** Entity types whose presence nearby wakes this mob. Empty means "nothing in particular". */
    Set<EntityType<?>> getInterruptingEntityTypes();

    /** True during any phase of the cycle — the usual gate for "can this mob act right now". */
    default boolean isInSleepCycle() {
        return this.isSleeping() || this.isPreparingSleep() || this.isAwakening();
    }

    // ───────────────────────────────────────────────────── ANIMATION HOOKS ─────

    /**
     * Called once when each phase begins, so the mob can start the matching clip.
     *
     * <p>These exist because {@code MobAnimator}'s auto-start loop only ever starts
     * {@code Loop.REPEATING} animations — a {@code PLAY_ONCE} clip with a play condition is never
     * started by it, no matter how true the condition is. The looping {@code sleep} clip works
     * without help; the two one-shot transitions have to be triggered, and the goal is the only
     * thing that knows the exact tick each phase starts on.
     *
     * <p>Same shape as {@code AbstractFlyingEntity}'s {@code onTakeoffBegin}/{@code onLandingBegin}
     * hooks in DeluxeLib. Default no-ops, so a mob with no sleep clips needs nothing.
     */
    default void onPreparingSleepBegin() {}

    default void onSleepBegin() {}

    default void onAwakeningBegin() {}
}

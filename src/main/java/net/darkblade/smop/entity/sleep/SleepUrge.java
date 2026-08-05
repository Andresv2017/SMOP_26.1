package net.darkblade.smop.entity.sleep;

import net.minecraft.world.entity.Mob;

/**
 * The "do I feel like sleeping right now" clock, ticked unconditionally from the entity rather than
 * from {@link SleepGoal}.
 *
 * <p><b>Why this is not inside the goal.</b> {@code GoalSelector.tick()} evaluates
 * {@code goalCanBeReplacedForAllFlags(...) && goal.canUse()} — Java short-circuits, so a goal whose
 * flags are held by a higher-priority goal never gets {@code canUse()} called at all. Since
 * {@code RandomStrollGoal} holds MOVE, a counter living in {@code canUse()} would only advance in
 * the gaps between strolls and the mob would drop off erratically. Keeping the clock on the entity
 * makes it monotonic, and leaves {@code canUse()} a pure predicate.
 */
public final class SleepUrge {

    /** Quiet time required before (re-)entering sleep, so interruptions do not chatter. */
    private static final int SLEEP_DELAY_TICKS = 100;

    private static final long NIGHT_START = 13000L;
    private static final long NIGHT_END = 23000L;

    private final Mob mob;

    /**
     * Per-entity stagger so a herd does not all lie down on the same tick. Applied to how long the
     * mob must stay calm before it wants to sleep — NOT to the animation phase lengths, which have
     * to match their clips exactly.
     */
    private final int stagger;

    private boolean night;
    private int ticksSinceNoTarget = -1;
    private int ticksSinceInterrupted = -1;
    private boolean wakeRequested;

    public SleepUrge(Mob mob) {
        this.mob = mob;
        this.stagger = mob.getId() % 10;
    }

    /** Server-side, once per tick, regardless of what the mob is doing. */
    public void tick() {
        long timeOfDay = this.mob.level().getOverworldClockTime() % 24000L;
        this.night = timeOfDay >= NIGHT_START && timeOfDay <= NIGHT_END;

        if (this.ticksSinceInterrupted >= 0) {
            this.ticksSinceInterrupted++;
        }
        this.ticksSinceNoTarget = this.mob.getTarget() == null
                ? Math.max(this.ticksSinceNoTarget, 0) + 1
                : -1;
    }

    public boolean isNight() {
        return this.night;
    }

    /** Night, no target for a while, and far enough past the last rude awakening. */
    public boolean wantsToSleep() {
        if (!this.night || this.ticksSinceNoTarget < SLEEP_DELAY_TICKS + this.stagger) {
            return false;
        }
        return this.ticksSinceInterrupted < 0 || this.ticksSinceInterrupted >= SLEEP_DELAY_TICKS;
    }

    /** Something woke the mob from outside the goal — taking damage, for instance. */
    public void requestWake() {
        this.wakeRequested = true;
        this.noteWokeUp();
    }

    public boolean consumeWakeRequest() {
        boolean requested = this.wakeRequested;
        this.wakeRequested = false;
        return requested;
    }

    /** Restarts the quiet window, so the mob does not flop straight back down. */
    public void noteWokeUp() {
        this.ticksSinceInterrupted = 0;
        this.ticksSinceNoTarget = -1;
    }
}

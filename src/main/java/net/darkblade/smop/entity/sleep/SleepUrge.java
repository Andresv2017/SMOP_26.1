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

    /** Quiet time required before entering sleep at all, so a mob does not drop the instant it idles. */
    private static final int SLEEP_DELAY_TICKS = 100;

    /**
     * Quiet time required after a wake, before the mob will lie down <em>again</em> — much longer than
     * {@link #SLEEP_DELAY_TICKS}, and deliberately so.
     *
     * <p>Getting up is a production: on a six-phase sleeper it is a wake clip, several seconds sitting,
     * and a stand clip. At five seconds' cooldown a mob roused by something that then wandered off
     * would finish standing and immediately start the sit-down ceremony again — it reads as the
     * animation glitching rather than as an animal deciding to go back to bed. Half a minute is long
     * enough that the second nap looks like a fresh decision.
     */
    private static final int WOKE_UP_DELAY_TICKS = 600;

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

    /** Night, no target for a while, and far enough past the last waking. */
    public boolean wantsToSleep() {
        if (!this.night || this.ticksSinceNoTarget < SLEEP_DELAY_TICKS + this.stagger) {
            return false;
        }
        // Never woken this session: nothing to wait out.
        return this.ticksSinceInterrupted < 0 || this.ticksSinceInterrupted >= WOKE_UP_DELAY_TICKS;
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

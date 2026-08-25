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

    /** @see #forceSleep(boolean) */
    private boolean forced;

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

    /** Night, no target for a while, and far enough past the last waking — or forced outright. */
    public boolean wantsToSleep() {
        if (this.forced) {
            return true;
        }
        if (!this.night || this.ticksSinceNoTarget < SLEEP_DELAY_TICKS + this.stagger) {
            return false;
        }
        // Never woken this session: nothing to wait out.
        return this.ticksSinceInterrupted < 0 || this.ticksSinceInterrupted >= WOKE_UP_DELAY_TICKS;
    }

    /**
     * Puts the mob under, and keeps it under, regardless of the clock or of who is standing over it.
     *
     * <p>Written for the Hell Hippo's weakness potion, which is how a player opens the window to
     * saddle one — but the concept is not hippo-specific and any mob with a "knock it out" item wants
     * the same thing.
     *
     * <p><b>It has to defeat three separate gates, not one.</b> Sleep normally requires night and a
     * quiet spell ({@link #wantsToSleep()}), it refuses to start with anything threatening nearby, and
     * it ends the moment the sun comes up or something walks over — and a player standing next to
     * it always counts as something walking over, which is unavoidable when the whole point is that
     * the player is right there with a saddle. So
     * {@link SleepGoal} reads this flag to skip its threat scan and its daylight check too.
     *
     * <p>1.20.1 solved the same problem with a {@code sleepingDueToEnvironment} boolean whose comment
     * read <em>"importante para evitar que lo despierte el sol"</em>. Same idea, one flag instead of
     * an inverted one.
     *
     * <p>Clearing it does not wake the mob by itself — pair it with {@link #requestWake()} when the
     * reason for the forced sleep has gone.
     */
    public void forceSleep(boolean value) {
        this.forced = value;
    }

    /** Whether this sleep is being held open from outside. @see #forceSleep(boolean) */
    public boolean isForced() {
        return this.forced;
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

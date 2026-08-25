package net.darkblade.smop.entity.sleep;

import net.minecraft.world.entity.Mob;

public final class SleepUrge {

    private static final int SLEEP_DELAY_TICKS = 100;

    private static final int WOKE_UP_DELAY_TICKS = 600;

    private static final long NIGHT_START = 13000L;
    private static final long NIGHT_END = 23000L;

    private final Mob mob;

    private final int stagger;

    private boolean night;
    private int ticksSinceNoTarget = -1;
    private int ticksSinceInterrupted = -1;
    private boolean wakeRequested;

    private boolean forced;

    public SleepUrge(Mob mob) {
        this.mob = mob;
        this.stagger = mob.getId() % 10;
    }

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

    public void forceSleep(boolean value) {
        this.forced = value;
    }

    public boolean isForced() {
        return this.forced;
    }

    public void requestWake() {
        this.wakeRequested = true;
        this.noteWokeUp();
    }

    public boolean consumeWakeRequest() {
        boolean requested = this.wakeRequested;
        this.wakeRequested = false;
        return requested;
    }

    public void noteWokeUp() {
        this.ticksSinceInterrupted = 0;
        this.ticksSinceNoTarget = -1;
    }
}

package net.darkblade.smop.entity.ai.goal;

import net.darkblade.smop.entity.SMOPAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

/**
 * Shakes the water off after a swim. The animal has to have been in the water long enough to
 * actually be soaked; stepping through a puddle does not earn a shake.
 *
 * <pre>{@code
 * this.goalSelector.addGoal(3, new LeaveWaterShakeGoal(this, "shake", 100));
 * }</pre>
 *
 * <p>Reusable rather than hippo-specific — the Nirasmosaurus is amphibious too, and the only things
 * that differ are the clip and how long counts as soaked.
 *
 * <p><b>The 1.20.1 version of this never fired once.</b> Its {@code canUse} required a
 * {@code shouldShake} flag, and that flag was only ever raised inside {@code tick()} — which vanilla
 * calls only on a goal that is already running, which required {@code canUse} to have returned true,
 * which required the flag. Nothing could break the circle, so the behaviour shipped dead. The soak
 * has to be counted somewhere that runs whether or not the goal is active, which here is
 * {@link #canUse()}.
 *
 * <p>That in turn is why the counter accrues by <em>elapsed {@code tickCount}</em> instead of
 * {@code ++}: {@code GoalSelector} only re-evaluates {@code canUse} on alternate ticks (see
 * {@code Mob#serverAiStep}), so incrementing by one per call would quietly stretch a 100-tick
 * threshold into 200 real ticks. Measuring against the clock is exact no matter how often the
 * selector gets round to asking.
 */
public class LeaveWaterShakeGoal extends Goal {

    private final SMOPAnimal mob;
    private final String clip;
    private final int soakTicks;

    /** {@link #lastSeenTick} before the first evaluation, so the first sample sets a baseline. */
    private static final int UNSEEN = -1;

    private int lastSeenTick = UNSEEN;
    private int soakedTicks;
    private boolean playing;

    /**
     * @param clip      the gesture to play, as registered on the animator
     * @param soakTicks how long in the water before a shake is earned
     */
    public LeaveWaterShakeGoal(@NotNull SMOPAnimal mob, @NotNull String clip, int soakTicks) {
        this.mob = mob;
        this.clip = clip;
        this.soakTicks = soakTicks;
        // Unlike IdleAnimationGoal, which starts only when the animal is already standing still and
        // so needs no flags, this one interrupts an animal that has just walked out of the water and
        // is still moving. Claiming MOVE is what lets it take the wheel off the stroll goal.
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        this.accrueSoak();
        return this.soakedTicks >= this.soakTicks
                && !this.mob.isInWater()
                && this.mob.onGround()
                // Same split as IdleAnimationGoal: these belong to starting only. The shake itself
                // raises the movement lock, so re-testing it while running would cancel the gesture
                // on its own first evaluation.
                && !this.mob.isPerformingAction()
                && !this.mob.isMovementLocked()
                && this.mob.getTarget() == null
                && !this.mob.isInSleepCycle();
    }

    /**
     * Tops up the soak against the world clock. Leaving the water before the threshold resets it —
     * a shake is for a real swim, and two brief dips should not add up to one.
     */
    private void accrueSoak() {
        int now = this.mob.tickCount;
        if (this.lastSeenTick == UNSEEN) {
            this.lastSeenTick = now;
            return;
        }
        int elapsed = Math.max(0, now - this.lastSeenTick);
        this.lastSeenTick = now;

        if (this.mob.isInWater()) {
            this.soakedTicks = Math.min(this.soakedTicks + elapsed, this.soakTicks);
        } else if (this.soakedTicks < this.soakTicks) {
            this.soakedTicks = 0;
        }
    }

    @Override
    public boolean canContinueToUse() {
        // Walking back into the water mid-shake makes the gesture pointless; let it be cut.
        return this.playing && this.mob.isPerforming(this.clip) && !this.mob.isInWater();
    }

    @Override
    public void start() {
        this.mob.getNavigation().stop();
        this.mob.startAction(this.clip);
        this.playing = true;
        // Spent on this shake. Getting wet again is what earns the next one.
        this.soakedTicks = 0;
    }

    @Override
    public void stop() {
        // Only ever cancel our own gesture — by now the clip may have ended on its own, or something
        // else may have started an action that has nothing to do with this goal.
        if (this.playing && this.mob.isPerforming(this.clip)) {
            this.mob.stopAction();
        }
        this.playing = false;
    }
}

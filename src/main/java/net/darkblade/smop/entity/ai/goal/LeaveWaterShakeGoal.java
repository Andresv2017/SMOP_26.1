package net.darkblade.smop.entity.ai.goal;

import net.darkblade.smop.entity.SMOPAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public class LeaveWaterShakeGoal extends Goal {

    private final SMOPAnimal mob;
    private final String clip;
    private final int soakTicks;

    private static final int UNSEEN = -1;

    private int lastSeenTick = UNSEEN;
    private int soakedTicks;
    private boolean playing;

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

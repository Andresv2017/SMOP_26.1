package net.darkblade.smop.entity.sleep;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.List;


public class SleepGoal<T extends Mob & ISleepingEntity> extends Goal {

    private static final double THREAT_RADIUS = 4.0D;

    private static final int THREAT_SCAN_INTERVAL = 10;

    private static final SleepPhase[] ENTRY = {
            SleepPhase.SITTING_DOWN, SleepPhase.SITTING, SleepPhase.PREPARING_SLEEP, SleepPhase.SLEEPING
    };


    private static final SleepPhase[] EXIT_CALM = {
            SleepPhase.AWAKENING, SleepPhase.SITTING, SleepPhase.STANDING_UP
    };

    private static final SleepPhase[] EXIT_STARTLED = {
            SleepPhase.AWAKENING, SleepPhase.STANDING_UP
    };

    private final T mob;
    private final SleepUrge urge;

    private int phaseTimer;
    private int threatScanCooldown;
    private boolean leaving;
    private SleepPhase[] exitPath = EXIT_CALM;

    private boolean startled;
    private int sleptTicks;

    private boolean wakePending;

    public SleepGoal(T mob, SleepUrge urge) {
        this.mob = mob;
        this.urge = urge;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (this.mob.isInSleepCycle() || !this.urge.wantsToSleep()) {
            return false;
        }
        this.urge.consumeWakeRequest();
        return this.urge.isForced() || this.findThreats().isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return this.mob.isInSleepCycle();
    }


    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.leaving = false;
        this.startled = false;
        this.threatScanCooldown = 0;
        this.enter(this.firstFrom(ENTRY, 0));
    }

    @Override
    public void tick() {
        this.mob.getNavigation().stop();

        if (this.mob.sleepPhase() == SleepPhase.SLEEPING) {
            this.sleptTicks++;
            if (!this.wakePending && this.shouldWakeUp()) {
                this.wakePending = true;
            }
            if (this.wakePending && this.atSleepLoopSeam()) {
                this.beginLeaving();
            }
            return;
        }

        if (!this.leaving && this.shouldWakeUp()) {
            this.beginLeaving();
            return;
        }

        if (this.leaving && this.mob.sleepPhase() == SleepPhase.SITTING && this.urge.consumeWakeRequest()) {
            this.startled = true;
            this.exitPath = EXIT_STARTLED;
            this.enter(this.firstFrom(EXIT_STARTLED, 1));
            return;
        }

        if (--this.phaseTimer > 0) {
            return;
        }
        this.enter(this.nextAfter(this.mob.sleepPhase()));
    }


    private boolean atSleepLoopSeam() {
        if (this.startled) {
            return true;
        }
        int loopTicks = this.mob.sleepPhaseDuration(SleepPhase.SLEEPING);
        return loopTicks <= 0 || this.sleptTicks % loopTicks == 0;
    }


    private void enter(SleepPhase phase) {
        if (phase == SleepPhase.NONE) {
            this.mob.setSleepPhase(SleepPhase.NONE);
            return;
        }
        if (phase == SleepPhase.SLEEPING) {
            this.sleptTicks = 0;
            this.wakePending = false;
        }
        this.mob.setSleepPhase(phase);
        this.phaseTimer = this.mob.sleepPhaseDuration(phase);
        this.mob.onSleepPhaseBegin(phase);
    }


    private void beginLeaving() {
        this.leaving = true;
        boolean wasAsleep = this.mob.sleepPhase() == SleepPhase.SLEEPING;
        this.exitPath = this.startled ? EXIT_STARTLED : EXIT_CALM;
        if (!wasAsleep) {
            this.exitPath = EXIT_STARTLED;
        }
        this.enter(this.firstFrom(this.exitPath, wasAsleep ? 0 : 1));
    }


    private SleepPhase nextAfter(SleepPhase current) {
        SleepPhase[] list = this.leaving ? this.exitPath : ENTRY;
        for (int i = 0; i < list.length; i++) {
            if (list[i] == current) {
                return this.firstFrom(list, i + 1);
            }
        }
        return SleepPhase.NONE;
    }

    private SleepPhase firstFrom(SleepPhase[] list, int from) {
        for (int i = from; i < list.length; i++) {
            SleepPhase phase = list[i];
            if (!phase.isSkippable() || this.mob.sleepPhaseDuration(phase) > 0) {
                return phase;
            }
        }
        return SleepPhase.NONE;
    }

    private boolean shouldWakeUp() {
        if (this.urge.consumeWakeRequest()) {
            this.startled = true;
            return true;
        }
        if (this.urge.isForced()) {
            return false;
        }
        if (!this.urge.isNight()) {
            return true;
        }
        if (--this.threatScanCooldown > 0) {
            return false;
        }
        this.threatScanCooldown = THREAT_SCAN_INTERVAL;
        return !this.findThreats().isEmpty();
    }

    @Override
    public void stop() {
        this.mob.setSleepPhase(SleepPhase.NONE);
        this.leaving = false;
        this.startled = false;
        this.wakePending = false;
        this.urge.noteWokeUp();
    }


    private List<LivingEntity> findThreats() {
        return this.mob.level().getEntitiesOfClass(LivingEntity.class,
                this.mob.getBoundingBox().inflate(THREAT_RADIUS),
                this::isThreat);
    }

    private boolean isThreat(LivingEntity nearby) {
        if (nearby == this.mob || !nearby.isAlive()) {
            return false;
        }
        if (nearby instanceof Player player) {
            return !player.isSpectator() && !player.isCreative();
        }
        if (this.mob instanceof ISleepThreatEvaluator evaluator) {
            return evaluator.shouldInterruptSleepDueTo(nearby);
        }
        return this.mob.getInterruptingEntityTypes().contains(nearby.getType());
    }
}

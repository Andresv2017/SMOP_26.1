package net.darkblade.smop.entity.sleep;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Sleeping, as an activity the goal selector arbitrates.
 *
 * <p>Holds MOVE, LOOK and JUMP for the whole cycle, so every other movement goal is preempted by
 * the selector instead of each one having to ask "am I asleep?" — the same shape vanilla's fox uses.
 * Register it above the locomotion and combat goals (priority 1 is the usual spot, leaving 0 for
 * {@code FloatGoal} so a sleeping mob still floats instead of drowning).
 *
 * <p><b>It releases itself.</b> A threat walking up, dawn breaking, or a wake request from
 * {@code hurt()} moves the cycle into its awakening phase; once that finishes the goal stops and
 * the combat goals take over. That is why sitting at a high priority is safe — sleep never
 * deadlocks the mob out of reacting.
 *
 * <p>Whether the mob <em>wants</em> to sleep is not decided here: {@link SleepUrge} keeps that clock
 * on the entity, because a goal's {@code canUse()} is skipped entirely while its flags are held by
 * something else.
 */
public class SleepGoal<T extends Mob & ISleepingEntity> extends Goal {

    /** Radius scanned for entities that should keep this mob awake. */
    private static final double THREAT_RADIUS = 4.0D;

    /** Threat sweeps are throttled — one box query per sleeping mob per tick adds up. */
    private static final int THREAT_SCAN_INTERVAL = 10;

    private final T mob;
    private final SleepUrge urge;
    /**
     * Phase lengths as suppliers, not ints: the goal is built from {@code registerGoals()}, which
     * {@code Mob}'s constructor calls long before {@code registerAnimations()} runs — so the clips
     * these lengths come from do not exist yet. Asking at the moment a phase starts lets the
     * duration be read off the clip that is about to play instead of being duplicated as a
     * constant that can drift away from it.
     */
    private final IntSupplier preparingSleepDuration;
    private final IntSupplier awakeningDuration;

    private int phaseTimer;
    private int threatScanCooldown;

    public SleepGoal(T mob, SleepUrge urge, IntSupplier preparingSleepDuration, IntSupplier awakeningDuration) {
        this.mob = mob;
        this.urge = urge;
        this.preparingSleepDuration = preparingSleepDuration;
        this.awakeningDuration = awakeningDuration;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (this.mob.isInSleepCycle() || !this.urge.wantsToSleep()) {
            return false;
        }
        this.urge.consumeWakeRequest();
        return this.findThreats().isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return this.mob.isInSleepCycle();
    }

    @Override
    public void start() {
        this.mob.setPreparingSleep(true);
        this.mob.setSleeping(false);
        this.mob.setAwakening(false);
        // Exactly the clip's length, with no stagger added: the herd is desynced by SleepUrge
        // deciding to sleep at different times, not by stretching the animation phases. Padding the
        // phase past the clip leaves the mob frozen on the last frame for the difference.
        this.phaseTimer = this.preparingSleepDuration.getAsInt();
        this.threatScanCooldown = 0;
        this.mob.onPreparingSleepBegin();
    }

    @Override
    public void tick() {
        this.mob.getNavigation().stop();

        if (this.mob.isAwakening()) {
            if (--this.phaseTimer <= 0) {
                this.mob.setAwakening(false);
            }
            return;
        }

        if (this.shouldWakeUp()) {
            this.beginAwakening();
            return;
        }

        // Settling down finished: actually asleep now.
        if (this.mob.isPreparingSleep() && --this.phaseTimer <= 0) {
            this.mob.setPreparingSleep(false);
            this.mob.setSleeping(true);
            this.mob.onSleepBegin();
        }
    }

    private boolean shouldWakeUp() {
        if (this.urge.consumeWakeRequest() || !this.urge.isNight()) {
            return true;
        }
        if (--this.threatScanCooldown > 0) {
            return false;
        }
        this.threatScanCooldown = THREAT_SCAN_INTERVAL;
        return !this.findThreats().isEmpty();
    }

    private void beginAwakening() {
        this.mob.setSleeping(false);
        this.mob.setPreparingSleep(false);
        this.mob.setAwakening(true);
        this.phaseTimer = this.awakeningDuration.getAsInt();
        this.mob.onAwakeningBegin();
    }

    @Override
    public void stop() {
        // Also covers being preempted outright by a higher-priority goal, which skips the
        // awakening phase — better a snap than a mob left stuck in a half-asleep state.
        this.mob.setSleeping(false);
        this.mob.setPreparingSleep(false);
        this.mob.setAwakening(false);
        this.urge.noteWokeUp();
    }

    /**
     * Nearby entities worth waking for. A {@link Player} counts unless the mob opts out via
     * {@link ISleepAwareness}; otherwise {@link ISleepThreatEvaluator} decides, falling back to the
     * mob's {@link ISleepingEntity#getInterruptingEntityTypes()} set.
     */
    private List<LivingEntity> findThreats() {
        return this.mob.level().getEntitiesOfClass(LivingEntity.class,
                this.mob.getBoundingBox().inflate(THREAT_RADIUS),
                this::isThreat);
    }

    private boolean isThreat(LivingEntity nearby) {
        if (nearby == this.mob || !nearby.isAlive()) {
            return false;
        }
        if (nearby instanceof Player player && !player.isSpectator()) {
            return !(this.mob instanceof ISleepAwareness aware) || aware.shouldWakeOnPlayerProximity();
        }
        if (this.mob instanceof ISleepThreatEvaluator evaluator) {
            return evaluator.shouldInterruptSleepDueTo(nearby);
        }
        return this.mob.getInterruptingEntityTypes().contains(nearby.getType());
    }
}

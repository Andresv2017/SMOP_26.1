package net.darkblade.smop.entity.ai.goal;

import net.darkblade.smop.entity.SMOPAnimal;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Roars once when a mob engages a new target — an announcement, not a per-hit reaction.
 *
 * <p>Three gates keep it from becoming noise: a global cooldown, a one-roar-per-engagement latch
 * keyed on the target's entity id (so re-acquiring the <em>same</em> victim stays quiet), and an
 * optional line-of-sight requirement. A reactive roar staged by {@code triggerRoar()} (damage taken)
 * is also consumed here, so a mob only needs one of the two roar goals.
 *
 * <p>Server-only: the client just renders whatever the synced roar flag says.
 */
public class RoarOnTargetGoal<T extends SMOPAnimal> extends Goal {

    private final T mob;
    private final int cooldownTicks;
    private final double maxTriggerRangeSq;
    private final boolean requireLineOfSight;

    private int nextAllowedTick = 0;
    private int engagedTargetId = -1;
    private boolean roaredThisEngagement = false;
    private int roarEndTick = -1;

    /** Trigger range defaults to the mob's {@code FOLLOW_RANGE}. */
    public RoarOnTargetGoal(T mob, int cooldownTicks, boolean requireLineOfSight) {
        this(mob, cooldownTicks, followRange(mob), requireLineOfSight);
    }

    public RoarOnTargetGoal(T mob, int cooldownTicks, double maxTriggerRange, boolean requireLineOfSight) {
        this.mob = mob;
        this.cooldownTicks = Math.max(0, cooldownTicks);
        double range = Math.max(1.0D, maxTriggerRange);
        this.maxTriggerRangeSq = range * range;
        this.requireLineOfSight = requireLineOfSight;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public static double fractionOfFollowRange(SMOPAnimal mob, double fraction) {
        return Math.max(1.0D, followRange(mob) * Math.max(0.0D, fraction));
    }

    private static double followRange(SMOPAnimal mob) {
        AttributeInstance instance = mob.getAttribute(Attributes.FOLLOW_RANGE);
        return instance != null ? instance.getValue() : 32.0D;
    }

    @Override
    public boolean canUse() {
        if (this.mob.level().isClientSide()) {
            return false;
        }
        // Stay active while roaring so MOVE/LOOK remain held.
        if (this.mob.isRoaring()) {
            return true;
        }
        if (this.mob.isInSleepCycle()) {
            this.resetEngagement();
            return false;
        }
        // Reactive roar (damage): fires with no target at all, still cooldown-gated.
        if (this.mob.shouldRoarNow()) {
            return this.mob.tickCount >= this.nextAllowedTick;
        }

        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            this.resetEngagement();
            return false;
        }
        if (target.getId() != this.engagedTargetId) {
            this.engagedTargetId = target.getId();
            this.roaredThisEngagement = false;
        }
        if (this.roaredThisEngagement || this.mob.tickCount < this.nextAllowedTick) {
            return false;
        }
        if (this.mob.distanceToSqr(target) > this.maxTriggerRangeSq) {
            return false;
        }
        return !this.requireLineOfSight || this.mob.getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        return !this.mob.level().isClientSide() && this.mob.isRoaring();
    }

    @Override
    public void start() {
        this.mob.resetShouldRoar();

        LivingEntity target = this.mob.getTarget();
        if (target != null) {
            this.mob.getNavigation().stop();
            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        this.mob.setRoaring(true);

        this.roaredThisEngagement = true;
        this.nextAllowedTick = this.mob.tickCount + this.cooldownTicks;
        this.roarEndTick = this.mob.tickCount + Math.max(1, this.mob.getRoarDuration());
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target != null) {
            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        this.mob.getNavigation().stop();

        // Walk the body around to meet the head, so the mob does not snap when the chase resumes.
        float yaw = Mth.rotateIfNecessary(this.mob.getYRot(), this.mob.getYHeadRot(), 8.0F);
        this.mob.setYRot(yaw);
        this.mob.yBodyRot = yaw;

        if (this.roarEndTick >= 0 && this.mob.tickCount >= this.roarEndTick) {
            this.mob.setRoaring(false);
            this.roarEndTick = -1;
        }
    }

    @Override
    public void stop() {
        this.mob.setRoaring(false);
        this.roarEndTick = -1;
        // Engagement latch deliberately survives: one roar per target acquisition.
    }

    private void resetEngagement() {
        this.engagedTargetId = -1;
        this.roaredThisEngagement = false;
    }
}

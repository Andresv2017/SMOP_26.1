package net.darkblade.smop.entity.tangoftero;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.List;

/**
 * Wild Tangofteros join a flockmate's fight. Tamed ones stay out of it — a pet answers to its owner,
 * not to whatever the local wild flock has decided to attack.
 */
public class AssistFlockGoal extends Goal {

    private final TangofteroEntity tango;
    private final double radius;

    public AssistFlockGoal(TangofteroEntity tango, double radius) {
        this.tango = tango;
        this.radius = radius;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return !this.tango.isTame()
                && this.tango.getTarget() == null
                && !this.tango.isInSleepCycle()
                && !this.findEngagedFlockmates().isEmpty();
    }

    @Override
    public void start() {
        for (TangofteroEntity ally : this.findEngagedFlockmates()) {
            LivingEntity allyTarget = ally.getTarget();
            if (allyTarget != null) {
                this.tango.setTarget(allyTarget);
                return;
            }
        }
    }

    private List<TangofteroEntity> findEngagedFlockmates() {
        return this.tango.level().getEntitiesOfClass(TangofteroEntity.class,
                this.tango.getBoundingBox().inflate(this.radius),
                other -> other != this.tango
                        && other.isAlive()
                        && !other.isTame()
                        && other.getTarget() != null
                        && this.tango.canAttack(other.getTarget()));
    }
}

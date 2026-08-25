package net.darkblade.smop.entity.ai.goal;

import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SMOPFollowParentGoal extends Goal {

    private static final double DEFAULT_SCAN_RANGE = 8.0D;
    private static final double VERTICAL_SCAN_RANGE = 4.0D;

    private static final double GIVE_UP_DISTANCE = 16.0D;

    private static final int PATH_RECALC_TICKS = 10;

    private final Animal animal;
    private final double speedModifier;
    private final double followDistanceSqr;

    private final double scanRange;

    @Nullable
    private Animal parent;
    private int timeToRecalcPath;

    public SMOPFollowParentGoal(@NotNull Animal animal, double speedModifier, double followDistance) {
        this.animal = animal;
        this.speedModifier = speedModifier;
        this.followDistanceSqr = followDistance * followDistance;
        this.scanRange = Math.max(DEFAULT_SCAN_RANGE, followDistance * 2.0D);
    }

    @Override
    public boolean canUse() {
        if (this.animal.getAge() >= 0) {
            return false;
        }
        Animal closest = this.findClosestAdult();
        if (closest == null || this.animal.distanceToSqr(closest) < this.followDistanceSqr) {
            return false;
        }
        this.parent = closest;
        return true;
    }

    @Nullable
    private Animal findClosestAdult() {
        List<? extends Animal> nearby = this.animal.level().getEntitiesOfClass(this.animal.getClass(),
                this.animal.getBoundingBox().inflate(this.scanRange, VERTICAL_SCAN_RANGE, this.scanRange));
        Animal closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Animal candidate : nearby) {
            if (candidate.getAge() >= 0) {
                double distance = this.animal.distanceToSqr(candidate);
                if (distance <= closestDistance) {
                    closestDistance = distance;
                    closest = candidate;
                }
            }
        }
        return closest;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.animal.getAge() >= 0 || this.parent == null || !this.parent.isAlive()) {
            return false;
        }
        double distance = this.animal.distanceToSqr(this.parent);
        return distance >= this.followDistanceSqr && distance <= GIVE_UP_DISTANCE * GIVE_UP_DISTANCE;
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
    }

    @Override
    public void stop() {
        this.parent = null;
    }

    @Override
    public void tick() {
        if (this.parent != null && --this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = this.adjustedTickDelay(PATH_RECALC_TICKS);
            this.animal.getNavigation().moveTo(this.parent, this.speedModifier);
        }
    }
}

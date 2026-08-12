package net.darkblade.smop.entity.ai.goal;

import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A calf trailing its mother, with the trailing distance left to the caller.
 *
 * <pre>{@code
 * this.goalSelector.addGoal(5, new SMOPFollowParentGoal(this, 1.1D, 5.0D));
 * }</pre>
 *
 * <p><b>Why not vanilla's {@code FollowParentGoal}.</b> It stops the calf at a flat three blocks
 * ({@code DONT_FOLLOW_IF_CLOSER_THAN}), written into {@code canUse} and {@code canContinueToUse} as a
 * literal {@code 9.0}, and its {@code parent} field is private — so there is no way to widen the
 * distance by subclassing without also reimplementing the parent search. This is that class with one
 * number pulled out.
 *
 * <p><b>Three blocks is a cow number.</b> The distance is measured centre to centre, so what it buys
 * depends entirely on how wide the animals are. A cow is 0.9 across and its calf 0.45, so their boxes
 * touch at 0.675 and three blocks leaves a comfortable 2.3 of daylight. Run the same three past a
 * Hell Hippo — 2.5 across, and {@code LivingEntity#getAgeScale} halves that to 1.25 for the calf —
 * and the boxes already touch at 1.875, leaving barely a block. Worse, on these rigs the model hangs
 * well outside the collision box (see {@code HellHippoEntity}'s note on its muzzle sitting 2.1 blocks
 * clear of it), so the calf's head ends up visually inside its mother.
 *
 * <p>The upper bound at which a calf gives up on a parent it is already following stays vanilla's 16
 * blocks. It is a leash length rather than a spacing, so animal size does not bear on it.
 */
public class SMOPFollowParentGoal extends Goal {

    /** How far out to look for an adult, as vanilla does. @see #scanRange */
    private static final double DEFAULT_SCAN_RANGE = 8.0D;
    private static final double VERTICAL_SCAN_RANGE = 4.0D;

    /** Vanilla's {@code 256.0}, kept as the distance it derives from. */
    private static final double GIVE_UP_DISTANCE = 16.0D;

    /** Vanilla's cadence: the path to a moving parent is re-issued every half second. */
    private static final int PATH_RECALC_TICKS = 10;

    private final Animal animal;
    private final double speedModifier;
    private final double followDistanceSqr;

    /**
     * Never below the follow distance, or the goal would be silently dead: the search only ever
     * returns adults inside this radius, and the goal then refuses any that are nearer than the
     * follow distance — so a follow distance past the search radius leaves nothing that can qualify.
     */
    private final double scanRange;

    @Nullable
    private Animal parent;
    private int timeToRecalcPath;

    /**
     * @param followDistance how close the calf gets before it stops, centre to centre. Vanilla's
     *                       value is 3; anything wider than a cow wants more.
     */
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

    /**
     * The nearest grown animal of this one's own species, or null if there is none in range.
     *
     * <p>Picking the nearest and <em>then</em> testing the distance is vanilla's order, and it is not
     * the same as picking the nearest one that is far enough: a calf already tucked in beside its
     * mother stays put rather than setting off after some second adult further away.
     */
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

package net.darkblade.smop.entity.ai.goal;

import net.darkblade.smop.entity.SMOPAnimal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;

import java.util.EnumSet;

/**
 * Tamed mob follows its owner, unless it has been told to stay put or to wander freely.
 *
 * <p>Distinct from vanilla's {@code FollowOwnerGoal} in that it respects SMOP's three-way order
 * state ({@code isOrderedToSit()} / {@code isWandering()} / following) and takes separate start and
 * stop distances, so a mob does not shuffle back and forth at the edge of its follow radius.
 *
 * <p>Teleporting uses {@code TamableAnimal#tryToTeleportToOwner()}, which is maintained by vanilla
 * and has a {@code canFlyToOwner()} hook, rather than a hand-rolled search.
 */
public class FollowOwnerBaseGoal extends Goal {

    private final SMOPAnimal mob;
    private final double speedModifier;
    private final float startDistanceSq;
    private final float stopDistanceSq;

    private PathNavigation navigation;
    private LivingEntity owner;
    private int timeToRecalcPath;

    /**
     * @param startDist how far the owner must get before the mob sets off
     * @param stopDist  how close the mob settles for
     */
    public FollowOwnerBaseGoal(SMOPAnimal mob, double speedModifier, float startDist, float stopDist) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.startDistanceSq = startDist * startDist;
        this.stopDistanceSq = stopDist * stopDist;
        this.navigation = mob.getNavigation();
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = this.mob.getOwner();
        if (owner == null || owner.isSpectator() || this.mob.isWandering() || this.unableToMove()) {
            return false;
        }
        if (this.mob.distanceToSqr(owner) < this.startDistanceSq) {
            return false;
        }
        this.owner = owner;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.owner == null || this.navigation.isDone() || this.mob.isWandering() || this.unableToMove()) {
            return false;
        }
        return this.mob.distanceToSqr(this.owner) > this.stopDistanceSq;
    }

    private boolean unableToMove() {
        return this.mob.isOrderedToSit() || this.mob.isPassenger() || this.mob.isLeashed();
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
    }

    @Override
    public void stop() {
        this.owner = null;
        this.navigation.stop();
    }

    @Override
    public void tick() {
        this.mob.getLookControl().setLookAt(this.owner, 10.0F, this.mob.getMaxHeadXRot());

        if (--this.timeToRecalcPath > 0) {
            return;
        }
        this.timeToRecalcPath = this.adjustedTickDelay(10);

        // Too far to walk back: blink to the owner rather than pathfind across the world.
        if (this.mob.distanceToSqr(this.owner) >= SMOPAnimal.TELEPORT_WHEN_DISTANCE_IS_SQ) {
            this.mob.tryToTeleportToOwner();
            return;
        }

        // Only re-path when the current one is stale — otherwise the mob restarts its route
        // every 10 ticks and never builds momentum.
        BlockPos targetPos = this.navigation.getTargetPos();
        if (!this.navigation.isInProgress() || targetPos == null
                || !targetPos.closerThan(this.owner.blockPosition(), 2.0D)) {
            this.navigation.moveTo(this.owner, this.speedModifier);
        }
    }

    /** Call after swapping the mob's navigation instance (amphibious/flying mobs do this). */
    public void refreshNavigation() {
        this.navigation = this.mob.getNavigation();
    }
}

package net.darkblade.smop.entity.ai.goal.flying;

import net.darkblade.smop.entity.SMOPFlyingAnimal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Follow-the-owner for a mob that can take to the air: it walks after its owner while close, takes
 * off once the gap opens, and teleports when it falls hopelessly behind.
 *
 * <p>Separate from {@code FollowOwnerBaseGoal} because the mode switch has to happen mid-follow, and
 * because the goal caches the navigation object — which {@code switchNavigation} replaces, so the
 * cached reference has to be refreshed or the goal keeps steering the navigator the mob no longer uses.
 */
public class FollowOwnerFlyingGoal extends Goal {

    /** Beyond this the mob gives up pathing and teleports. */
    private static final double TELEPORT_DISTANCE = 12.0D;
    /** Beyond this it takes off rather than trying to keep up on foot. */
    private static final double TAKEOFF_DISTANCE = 8.0D;
    /** Path recalculation interval. */
    private static final int REPATH_INTERVAL = 10;

    private final SMOPFlyingAnimal mob;
    private final double speedModifier;
    private final Level level;
    private final float startDistance;
    private final float stopDistance;

    private PathNavigation navigation;
    @Nullable
    private LivingEntity owner;
    private int timeToRecalcPath;

    public FollowOwnerFlyingGoal(SMOPFlyingAnimal mob, double speedModifier, float startDistance, float stopDistance) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.level = mob.level();
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        this.navigation = mob.getNavigation();
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = this.mob.getOwner();
        if (owner == null
                || this.mob.isOrderedToSit()
                || this.mob.isPassenger()
                || this.mob.isLeashed()
                || this.mob.isWandering()
                || this.mob.isMovementLocked()) {
            return false;
        }
        if (this.mob.distanceToSqr(owner) < this.startDistance * this.startDistance) {
            return false;
        }
        this.owner = owner;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.owner != null
                && !this.navigation.isDone()
                && !this.mob.isOrderedToSit()
                && !this.mob.isPassenger()
                && this.mob.distanceToSqr(this.owner) > this.stopDistance * this.stopDistance;
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
        if (this.owner == null) {
            return;
        }
        this.mob.getLookControl().setLookAt(this.owner, 10.0F, this.mob.getMaxHeadXRot());

        if (--this.timeToRecalcPath > 0) {
            return;
        }
        this.timeToRecalcPath = this.adjustedTickDelay(REPATH_INTERVAL);

        double distance = this.mob.distanceTo(this.owner);
        if (distance >= TELEPORT_DISTANCE) {
            this.teleportToOwner();
            return;
        }

        if (!this.mob.isFlying() && distance > TAKEOFF_DISTANCE && !this.mob.isBaby()) {
            this.mob.switchNavigation();
            // switchNavigation swaps the navigator out; without re-reading it this goal would keep
            // issuing orders to the ground navigation the mob just discarded.
            this.navigation = this.mob.getNavigation();
        }

        if (!this.navigation.isInProgress() || distance > 2.5D) {
            this.navigation.moveTo(this.owner, this.speedModifier);
        }
    }

    private void teleportToOwner() {
        if (this.owner == null) {
            return;
        }
        BlockPos ownerPos = this.owner.blockPosition();
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = this.randomOffset(-3, 3);
            int dy = this.randomOffset(-1, 1);
            int dz = this.randomOffset(-3, 3);
            if (this.tryTeleportTo(ownerPos.offset(dx, dy, dz))) {
                return;
            }
        }
    }

    private boolean tryTeleportTo(BlockPos pos) {
        if (this.owner == null
                || Math.abs(pos.getX() - this.owner.getX()) < 2.0D
                || Math.abs(pos.getZ() - this.owner.getZ()) < 2.0D) {
            return false;
        }
        if (!this.canTeleportTo(pos)) {
            return false;
        }
        this.mob.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, this.mob.getYRot(), this.mob.getXRot());
        this.navigation.stop();
        return true;
    }

    private boolean canTeleportTo(BlockPos pos) {
        PathType type = WalkNodeEvaluator.getPathTypeStatic(this.mob, pos.mutable());
        boolean safe = type == PathType.WALKABLE || this.level.getBlockState(pos.below()).isAir();
        return safe && this.level.noCollision(this.mob,
                this.mob.getBoundingBox().move(Vec3.atCenterOf(pos).subtract(this.mob.position())));
    }

    private int randomOffset(int min, int max) {
        return this.mob.getRandom().nextInt(max - min + 1) + min;
    }
}

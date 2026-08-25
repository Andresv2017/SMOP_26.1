package net.darkblade.smop.entity.ai.goal.flying;

import net.darkblade.smop.entity.SMOPFlyingAnimal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class FollowOwnerFlyingGoal extends Goal {

    private static final double FOLLOW_SPEED = 0.5D;
    private static final double POS_GAIN = 0.04D;
    private static final double DAMPING = 0.4D;
    private static final double FOLLOW_HEIGHT = 2.4D;
    private static final double FOLLOW_SIDE = 1.3D;

    private static final int ORBIT_AFTER_TICKS = 100;
    private static final double ORBIT_RADIUS = 1.8D;
    private static final double ORBIT_HEIGHT = 2.4D;
    private static final float ORBIT_ANGULAR_SPEED = 3.0F;
    private static final double IDLE_MOVE_THRESHOLD_SQ = 0.0025D;

    private static final float TURN_RATE = 10.0F;

    private static final double TELEPORT_DISTANCE_SQ = 24.0D * 24.0D;

    private final SMOPFlyingAnimal mob;
    private final double startDistanceSq;
    private final OrbitFlightController controller = new OrbitFlightController(POS_GAIN, DAMPING, FOLLOW_SPEED, TURN_RATE);

    private int idleTicks;
    private float orbitAngle;
    @Nullable
    private Vec3 lastOwnerPos;

    public FollowOwnerFlyingGoal(SMOPFlyingAnimal mob, float startDistance) {
        this.mob = mob;
        this.startDistanceSq = startDistance * startDistance;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = this.owner();
        if (owner == null || this.mob.isBaby() || this.standingDown()) {
            return false;
        }
        if (this.mob.isFlying()) {
            return this.flightSettled();
        }
        // On the ground and the owner has pulled away: ask for a take-off and let TakeoffGoal do it,
        // rather than forcing the lifecycle from outside. This goal picks up once airborne.
        if (this.mob.distanceToSqr(owner) > this.startDistanceSq) {
            this.mob.requestTakeoff();
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return this.owner() != null && this.mob.isFlying() && this.flightSettled() && !this.standingDown();
    }

    private boolean flightSettled() {
        return !this.mob.isTakingOff() && !this.mob.isLanding();
    }

    private boolean standingDown() {
        return this.mob.isOrderedToSit()
                || this.mob.isWandering()
                || this.mob.isPassenger()
                || this.mob.isLeashed()
                || this.mob.isMovementLocked();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.mob.getNavigation().stop();
        this.idleTicks = 0;
        this.lastOwnerPos = null;
    }

    @Override
    public void stop() {
        this.lastOwnerPos = null;
    }

    @Override
    public void tick() {
        LivingEntity owner = this.owner();
        if (owner == null) {
            return;
        }
        this.mob.getLookControl().setLookAt(owner, 10.0F, this.mob.getMaxHeadXRot());

        if (this.mob.distanceToSqr(owner) > TELEPORT_DISTANCE_SQ) {
            // Maintained by vanilla, complete with a canFlyToOwner() hook. It returns void and may
            // quietly fail, so the escort below runs regardless: if
            // it worked the error is now tiny and the controller has nothing to do, and if it did
            // not the mob simply keeps flying over. Flight state is deliberately left alone — forcing
            // a landing on a teleport that failed would drop the mob out of the sky.
            this.mob.tryToTeleportToOwner();
        }

        Vec3 ownerPos = owner.position();
        boolean moved = this.lastOwnerPos == null
                || ownerPos.subtract(this.lastOwnerPos).horizontalDistanceSqr() > IDLE_MOVE_THRESHOLD_SQ;
        this.lastOwnerPos = ownerPos;
        this.idleTicks = moved ? 0 : this.idleTicks + 1;

        Vec3 target;
        if (this.idleTicks >= ORBIT_AFTER_TICKS) {
            this.orbitAngle += ORBIT_ANGULAR_SPEED;
            double radians = Math.toRadians(this.orbitAngle);
            target = ownerPos.add(Math.cos(radians) * ORBIT_RADIUS, ORBIT_HEIGHT, Math.sin(radians) * ORBIT_RADIUS);
        } else {
            // Off to the owner's side and above head height — flying alongside, not overhead.
            float yawRad = owner.getYRot() * ((float) Math.PI / 180.0F);
            target = ownerPos.add(Math.cos(yawRad) * FOLLOW_SIDE, FOLLOW_HEIGHT, Math.sin(yawRad) * FOLLOW_SIDE);
        }

        this.controller.step(this.mob, target, ownerPos);
    }

    @Nullable
    private LivingEntity owner() {
        LivingEntity owner = this.mob.getOwner();
        if (owner == null || !owner.isAlive() || owner.isSpectator() || owner.level() != this.mob.level()) {
            return null;
        }
        return owner;
    }
}

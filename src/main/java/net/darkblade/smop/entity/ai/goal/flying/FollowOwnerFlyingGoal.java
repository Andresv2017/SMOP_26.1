package net.darkblade.smop.entity.ai.goal.flying;

import net.darkblade.smop.entity.SMOPFlyingAnimal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Escorts the owner through the air: holds a point just off their side and above head height while
 * they move, and drifts into a slow orbit once they have stood still for a while.
 *
 * <p>Adapted from the {@code FollowOwnerGoal} in DeluxeLib's Owl: navigation is stopped and the mob
 * is flown by direct velocity control, so it arcs in and settles instead of stair-stepping. Pathing to
 * the owner and teleporting on a fixed gap teleports constantly, because a flying navigation rarely
 * closes that gap between recalculations.
 *
 * <p>The actual flight is {@link OrbitFlightController}'s critically damped PD loop — see that class
 * for the arithmetic and why the gains below are not independent knobs. They are the Owl's values,
 * unchanged.
 *
 * <p>Grounded, this goal does not steer at all: it asks the mob to take off, via
 * {@link SMOPFlyingAnimal#requestTakeoff()}, and stands down until it is airborne. Close-range
 * following on foot is left to {@code FollowOwnerBaseGoal}.
 */
public class FollowOwnerFlyingGoal extends Goal {

    /** Speed cap in blocks/tick. */
    private static final double FOLLOW_SPEED = 0.5D;
    /** Position-error gain (kp) — see {@link OrbitFlightController} before touching. */
    private static final double POS_GAIN = 0.04D;
    /** Velocity damping (kd) — see {@link OrbitFlightController} before touching. */
    private static final double DAMPING = 0.4D;
    /** Height above the owner the escort point sits at. */
    private static final double FOLLOW_HEIGHT = 2.4D;
    /** Sideways offset of the escort point, so the mob is beside the owner rather than on top. */
    private static final double FOLLOW_SIDE = 1.3D;

    /** Ticks the owner must stand still before the escort becomes an orbit (~5 s). */
    private static final int ORBIT_AFTER_TICKS = 100;
    private static final double ORBIT_RADIUS = 1.8D;
    private static final double ORBIT_HEIGHT = 2.4D;
    private static final float ORBIT_ANGULAR_SPEED = 3.0F;
    /** Squared horizontal movement below which the owner counts as standing still. */
    private static final double IDLE_MOVE_THRESHOLD_SQ = 0.0025D;

    /** Max yaw change per tick while escorting, in degrees. */
    private static final float TURN_RATE = 10.0F;

    /**
     * Last resort only. Vanilla's own follow goal teleports at 12; that number is wrong here because
     * this goal closes ground under power rather than by pathing, so 12 blocks is an ordinary
     * mid-flight gap rather than a failure. At 24 the only thing that still produces it is the owner
     * crossing a chunk boundary the mob cannot follow through.
     */
    private static final double TELEPORT_DISTANCE_SQ = 24.0D * 24.0D;

    private final SMOPFlyingAnimal mob;
    /** Distance at which the mob decides it is worth taking to the air. */
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

    /**
     * Cruising, rather than mid-transition. This is not cosmetic: this goal and the landing goal both
     * hold MOVE, and this one sits above it, so escorting through a landing would starve the landing
     * goal of its flag and leave the mob stuck in the landing state with nothing driving it down.
     * The take-off case is milder — the escort would simply fight the lift — but it is the same rule.
     */
    private boolean flightSettled() {
        return !this.mob.isTakingOff() && !this.mob.isLanding();
    }

    /** Orders and states that say "not now": told to stay, told to roam, carried, tethered, pinned. */
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

    /** The owner, if they exist and are in this level — chasing across dimensions makes no sense. */
    @Nullable
    private LivingEntity owner() {
        LivingEntity owner = this.mob.getOwner();
        if (owner == null || !owner.isAlive() || owner.isSpectator() || owner.level() != this.mob.level()) {
            return null;
        }
        return owner;
    }
}

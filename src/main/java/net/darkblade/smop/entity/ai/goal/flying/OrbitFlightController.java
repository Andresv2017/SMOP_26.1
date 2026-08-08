package net.darkblade.smop.entity.ai.goal.flying;

import net.darkblade.smop.entity.SMOPFlyingAnimal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Flies a {@link SMOPFlyingAnimal} at a moving 3D point by direct velocity control, rather than
 * pathing — extracted out of {@link FollowOwnerFlyingGoal} so more than one goal can share the exact
 * same arithmetic instead of each hand-rolling its own copy.
 *
 * <p><b>The controller.</b> {@code accel = kp·error − kd·velocity} — a critically damped PD loop.
 * The two gains are not independent knobs: raising {@code posGain} or lowering {@code damping} makes
 * the eigenvalues of the underlying recurrence complex, and the mob starts ringing — the "approach,
 * back off, approach again" ping-pong that the naive version (a distance-proportional target velocity
 * fed through a second smoothing pass, i.e. two cascaded first-order lags) produces on every large
 * initial gap. {@link FollowOwnerFlyingGoal} still carries the Owl's original values, unchanged;
 * other callers are free to detune the response for their own use.
 */
public final class OrbitFlightController {

    private final double posGain;
    private final double damping;
    private final double speedCap;
    private final float turnRate;

    /**
     * @param posGain  position-error gain (kp)
     * @param damping  velocity damping (kd)
     * @param speedCap speed cap in blocks/tick
     * @param turnRate max yaw change per tick while under this controller, in degrees
     */
    public OrbitFlightController(double posGain, double damping, double speedCap, float turnRate) {
        this.posGain = posGain;
        this.damping = damping;
        this.speedCap = speedCap;
        this.turnRate = turnRate;
    }

    /**
     * Advances one tick toward {@code target}. When the resulting velocity is too small to imply a
     * heading of its own, faces {@code fallbackFacing} instead — pass {@code null} to just hold the
     * last heading, the right choice whenever {@code target} is never stationary relative to the mob
     * to begin with, such as a point walking around a circle.
     */
    public void step(SMOPFlyingAnimal mob, Vec3 target, @Nullable Vec3 fallbackFacing) {
        Vec3 velocity = mob.getDeltaMovement();
        Vec3 accel = target.subtract(mob.position()).scale(this.posGain).subtract(velocity.scale(this.damping));
        Vec3 next = velocity.add(accel);
        double speed = next.length();
        if (speed > this.speedCap) {
            next = next.scale(this.speedCap / speed);
        }
        mob.setDeltaMovement(next);

        // Face where it is going while it is going somewhere; fall back once it has settled.
        if (next.horizontalDistanceSqr() > 1.0E-4D) {
            mob.faceHeading(next.x, next.z, this.turnRate);
        } else if (fallbackFacing != null) {
            mob.faceHeading(fallbackFacing.x - mob.getX(), fallbackFacing.z - mob.getZ(), this.turnRate);
        }
    }
}

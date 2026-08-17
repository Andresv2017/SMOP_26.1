package net.darkblade.smop.entity.ai.control;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * A look control for a large swimmer: the head glides at its own declared speed, it looks at what it
 * was told to look at, it gives up on anything it cannot face, and it never touches the body.
 *
 * <p>Vanilla's {@code SmoothSwimmingLookControl} is what the aquatic base installs, and every one of
 * its three departures from the plain {@code LookControl} is a fish behaviour that reads wrong on a
 * three-block reptile:
 *
 * <ul>
 *   <li><b>It aims the head twenty degrees off target.</b> Literally {@code yRotD + 20.0F}, plus
 *       {@code xRotD + 10.0F} on the pitch. On a fish that sells the sideways, one-eyed way it
 *       regards things; on a long skull with two forward eyes it just looks like the animal is
 *       staring past whatever it is watching — which is the "sometimes it looks off to one side"
 *       complaint, exactly.</li>
 *   <li><b>It recentres the head at whatever speed the last glance happened to use.</b> The else
 *       branch reuses the stored {@code yMaxRotSpeed} rather than asking the mob, so the release is
 *       as fast as the acquire and a slow, deliberate glance still snaps back.</li>
 *   <li><b>It drags the body after the head</b>, four degrees a tick, whenever the head sits more
 *       than {@code maxYRotFromCenter} off centre. Here the body belongs to {@link SwimSteerControl},
 *       which rewrites {@code yBodyRot} from the steered yaw every tick — so while the animal is
 *       swimming the drag is silently discarded, and the moment the navigator goes idle and the steer
 *       stops writing, it takes over and rotates the body on the spot. A control that does nothing or
 *       something wrong depending on whether the mob is moving is worse than one that does
 *       nothing.</li>
 * </ul>
 *
 * <p>On top of that it enforces two limits the rig actually has, because a demand the model cannot
 * draw is not a look — it is a head held against a stop. See {@link #updateTracking} and
 * {@link #HEAD_PITCH_LIMIT}.
 */
public class SmoothSwimLookControl extends LookControl {

    /**
     * Degrees of head pitch per tick.
     *
     * <p>Not {@code getMaxHeadXRot()}, which vanilla passes into {@code rotateTowards} as the step
     * size: at its default of 40 the head reaches any pitch in a single tick.
     */
    private static final float PITCH_SPEED = 3.0F;

    /** Degrees per tick the pitch levels out over once there is nothing to look at. */
    private static final float LEVEL_SPEED = 1.5F;

    /**
     * How far up or down the head may be asked to look.
     *
     * <p>The rig clamps the neck's pitch at 30 degrees, so every degree past that draws nothing at all
     * and only pins the head against its stop — the same failure {@code getMaxHeadYRot} was lowered to
     * 40 to fix on the yaw axis, and a tick sample caught the pitch equivalent at −35.8. Vanilla has no
     * clamp here because {@code getMaxHeadXRot} is spent as the per-tick step instead, so it has to be
     * applied to the demand by hand.
     */
    private static final float HEAD_PITCH_LIMIT = 30.0F;

    /**
     * Degrees inside {@code getMaxHeadYRot} the target must come back to before the head will pick it
     * up again, once it has been let go.
     *
     * <p>Pure hysteresis. Releasing and re-acquiring on the same threshold would judder every time the
     * demand sat on the boundary, which is precisely where it sits when an animal swims past something
     * it is watching.
     */
    private static final float REACQUIRE_MARGIN = 8.0F;

    /** False while the current look target is outside what the neck can face. */
    private boolean tracking = true;

    public SmoothSwimLookControl(@NotNull Mob mob) {
        super(mob);
    }

    @Override
    public void tick() {
        Optional<Float> wantedYaw = this.lookAtCooldown > 0 ? this.getYRotD() : Optional.empty();
        if (this.lookAtCooldown > 0) {
            this.lookAtCooldown--;
        }
        wantedYaw.ifPresent(this::updateTracking);

        if (wantedYaw.isPresent() && this.tracking) {
            this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, wantedYaw.get(), this.yMaxRotSpeed);
            this.getXRotD().ifPresent(xRotD -> this.mob.setXRot(this.rotateTowards(
                    this.mob.getXRot(),
                    Mth.clamp(xRotD, -HEAD_PITCH_LIMIT, HEAD_PITCH_LIMIT),
                    Math.min(this.xMaxRotAngle, PITCH_SPEED))));
        } else {
            // getHeadRotSpeed(), not the stored yMaxRotSpeed: releasing a glance is the same motion as
            // taking it, and reusing the stale field is what made the head snap back to centre.
            this.mob.yHeadRot =
                    this.rotateTowards(this.mob.yHeadRot, this.mob.yBodyRot, this.mob.getHeadRotSpeed());
            this.mob.setXRot(this.rotateTowards(this.mob.getXRot(), 0.0F, LEVEL_SPEED));
        }
        this.clampHeadRotationToBody();
    }

    /**
     * Lets go of a target the neck cannot reach, and picks it up again when it comes back.
     *
     * <p><b>Otherwise the head simply parks against its stop.</b> A look goal holds its target for the
     * goal's whole duration, and a target off to the side of a moving animal quickly demands more yaw
     * than {@code getMaxHeadYRot} allows; {@code clampHeadRotationToBody} then pins the head at exactly
     * the limit and leaves it there. Tick samples have it welded at 40.000 for 19 and 28 consecutive
     * ticks. Nothing about that reads as looking — it reads as a stuck neck.
     *
     * <p>Giving up instead lets the head glide back to centre, which is what an animal losing interest
     * in something it can no longer face actually does.
     */
    private void updateTracking(float wantedYaw) {
        float demand = Math.abs(Mth.degreesDifference(this.mob.yBodyRot, wantedYaw));
        float limit = this.mob.getMaxHeadYRot();
        if (this.tracking) {
            this.tracking = demand <= limit;
        } else {
            this.tracking = demand < limit - REACQUIRE_MARGIN;
        }
    }
}

package net.darkblade.smop.entity.rider;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Turns a rider's WASD and mouse into a mount's heading. The other half of {@link RiderAbility}: that
 * one covers what a mount can <em>do</em> for its rider, this one covers where it goes.
 *
 * <pre>{@code
 * @Override protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
 *     return RiderSteering.riddenInput(controller);
 * }
 *
 * @Override protected void tickRidden(Player controller, Vec3 riddenInput) {
 *     super.tickRidden(controller, riddenInput);
 *     Vec2 rotation = RiderSteering.riddenRotation(controller);
 *     this.setRot(rotation.y, rotation.x);
 *     this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
 * }
 * }</pre>
 *
 * <p><b>Why static helpers and not an interface, or an owned object.</b> The hooks these feed —
 * {@code getRiddenInput}, {@code tickRidden}, {@code getRiddenSpeed} — are {@code protected} on
 * {@code LivingEntity}, so no interface can supply them as defaults; the mount has to declare the
 * overrides itself either way. And unlike {@code SleepUrge} or {@code TameProgress} there is no state
 * to keep between ticks — every answer is a pure function of what the rider is pressing this instant.
 * An instance per mount would have been a field holding nothing.
 *
 * <p>The numbers are vanilla's, taken from {@code AbstractHorse}, because a mount that handles
 * differently from a horse for no reason is just a mount that handles wrong.
 */
public final class RiderSteering {

    private RiderSteering() {}

    /**
     * Strafing is deliberately weaker than running forward — a big animal does not sidestep as fast
     * as it charges. Vanilla's horse uses the same halving.
     */
    private static final float STRAFE_SCALE = 0.5F;

    /**
     * Backing up is slower still. Quartering it is what stops a mount reversing out of trouble as
     * quickly as it got into it.
     */
    private static final float REVERSE_SCALE = 0.25F;

    /**
     * How much of the rider's pitch the body takes. Halved so the mount leans with the camera rather
     * than pointing straight up when the rider looks at the sky.
     */
    private static final float PITCH_SCALE = 0.5F;

    /** The movement vector to hand back from {@code getRiddenInput}. */
    public static @NotNull Vec3 riddenInput(@NotNull Player controller) {
        float sideways = controller.xxa * STRAFE_SCALE;
        float forward = controller.zza;
        if (forward <= 0.0F) {
            forward *= REVERSE_SCALE;
        }
        return new Vec3(sideways, 0.0D, forward);
    }

    /**
     * Where the rider is pointing, as {@code (pitch, yaw)}.
     *
     * <p><b>The mount has to apply this itself</b>, and there is no way around it: {@code setRot} is
     * {@code protected} on {@code Entity}, so nothing outside the entity hierarchy can call it. Same
     * split vanilla uses — {@code AbstractHorse#getRiddenRotation} computes, {@code tickRidden}
     * applies. Two lines at the call site, and they must be both of these:
     *
     * <pre>{@code
     * Vec2 rotation = this.steering.riddenRotation(controller);
     * this.setRot(rotation.y, rotation.x);
     * this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
     * }</pre>
     *
     * <p>The second line is the one that gets forgotten. Writing only {@code yRot} leaves the body and
     * head rotations to catch up on their own, and the render reads {@code yBodyRot} — so a steered
     * mob that skips it looks like it is being dragged sideways.
     */
    public static @NotNull Vec2 riddenRotation(@NotNull Player controller) {
        return new Vec2(controller.getXRot() * PITCH_SCALE, controller.getYRot());
    }
}

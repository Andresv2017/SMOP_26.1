package net.darkblade.smop.entity.ai.goal.flying;

import net.darkblade.smop.entity.SMOPFlyingAnimal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class OrbitFlightController {

    private final double posGain;
    private final double damping;
    private final double speedCap;
    private final float turnRate;

    public OrbitFlightController(double posGain, double damping, double speedCap, float turnRate) {
        this.posGain = posGain;
        this.damping = damping;
        this.speedCap = speedCap;
        this.turnRate = turnRate;
    }

    public void step(SMOPFlyingAnimal mob, Vec3 target, @Nullable Vec3 fallbackFacing) {
        Vec3 next = this.drive(mob, target);

        // Face where it is going while it is going somewhere; fall back once it has settled.
        if (next.horizontalDistanceSqr() > 1.0E-4D) {
            mob.faceHeading(next.x, next.z, this.turnRate);
        } else if (fallbackFacing != null) {
            mob.faceHeading(fallbackFacing.x - mob.getX(), fallbackFacing.z - mob.getZ(), this.turnRate);
        }
    }

    public void stepFacing(SMOPFlyingAnimal mob, Vec3 target, Vec3 lookAt) {
        this.drive(mob, target);
        mob.faceHeading(lookAt.x - mob.getX(), lookAt.z - mob.getZ(), this.turnRate);
    }

    private Vec3 drive(SMOPFlyingAnimal mob, Vec3 target) {
        Vec3 velocity = mob.getDeltaMovement();
        Vec3 accel = target.subtract(mob.position()).scale(this.posGain).subtract(velocity.scale(this.damping));
        Vec3 next = velocity.add(accel);
        double speed = next.length();
        if (speed > this.speedCap) {
            next = next.scale(this.speedCap / speed);
        }
        mob.setDeltaMovement(next);
        return next;
    }
}

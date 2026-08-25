package net.darkblade.smop.entity.ai.control;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.jetbrains.annotations.NotNull;

public class SwimSteerControl extends MoveControl {

    private static final float DEG_TO_RAD = 0.017453292F;
    private static final double ARRIVED_SQR = 2.5E-7D;

    private static final float FULL_SPEED_ERROR = 15.0F;
    private static final float STALL_ERROR = 90.0F;
    private static final float MIN_TURN_SPEED = 0.35F;

    private static final float ORBIT_MARGIN = 1.5F;
    private static final float ADAPTIVE_TURN_CAP = 30.0F;

    private static final double BUOYANCY = 0.005D;

    private static final float DEFAULT_VERTICAL_GAIN = 6.0F;

    private float verticalGain = DEFAULT_VERTICAL_GAIN;

    private float pitch;

    private float yawRate;

    private static final float DEFAULT_RAMP_TICKS = 15.0F;

    private float rampTicks = DEFAULT_RAMP_TICKS;

    private final float turnSpeed;

    private float combatTurnSpeed;
    private final float maxPitch;
    private final float pitchSpeed;
    private final float speedScale;

    public SwimSteerControl(@NotNull Mob mob, float turnSpeed, float maxPitch, float pitchSpeed, float speedScale) {
        super(mob);
        this.turnSpeed = turnSpeed;
        this.maxPitch = maxPitch;
        this.pitchSpeed = pitchSpeed;
        this.speedScale = speedScale;
    }

    public SwimSteerControl verticalGain(float gain) {
        this.verticalGain = gain;
        return this;
    }

    public SwimSteerControl rampTicks(float ticks) {
        this.rampTicks = ticks;
        return this;
    }

    public SwimSteerControl combatTurnSpeed(float degreesPerTick) {
        this.combatTurnSpeed = degreesPerTick;
        return this;
    }

    private float effectiveTurnSpeed() {
        if (this.combatTurnSpeed <= 0.0F) {
            return this.turnSpeed;
        }
        if (this.mob.isAggressive()) {
            return this.combatTurnSpeed;
        }
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive() ? this.combatTurnSpeed : this.turnSpeed;
    }

    @Override
    public void tick() {
        if (this.mob.isInWater()) {
            this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(0.0D, BUOYANCY, 0.0D));
        }
        if (this.operation != Operation.MOVE_TO || this.mob.getNavigation().isDone()) {
            // Bleed the turn off rather than dropping it: an idle tick between two legs must not
            // discard the ramp and make the next one start from a standstill.
            this.yawRate = Mth.approach(this.yawRate, 0.0F, this.effectiveTurnSpeed() / this.rampTicks);
            this.mob.setSpeed(0.0F);
            this.mob.setXxa(0.0F);
            this.mob.setYya(0.0F);
            this.mob.setZza(0.0F);
            return;
        }

        double dx = this.wantedX - this.mob.getX();
        double dy = this.wantedY - this.mob.getY();
        double dz = this.wantedZ - this.mob.getZ();
        if (dx * dx + dy * dy + dz * dz < ARRIVED_SQR) {
            this.yawRate = Mth.approach(this.yawRate, 0.0F, this.effectiveTurnSpeed() / this.rampTicks);
            this.mob.setZza(0.0F);
            return;
        }
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float wantYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float signedError = Mth.wrapDegrees(wantYaw - this.mob.getYRot());
        float yawError = Math.abs(signedError);

        this.mob.setYRot(this.mob.getYRot() + this.stepYaw(signedError, this.adaptiveTurn(horizontal)));
        // Body only. Leaving yHeadRot alone is what lets the look control keep the head alive while
        // the animal cruises — vanilla overwrites both and the head goes dead.
        this.mob.yBodyRot = this.mob.getYRot();

        // TWO values, deliberately, because vanilla keeps them apart and collapsing them stops the
        // animal dead. `drive` is the unscaled speed and feeds zza/yya — the direction-and-magnitude
        // input. `setSpeed` gets the scaled one, and travel() multiplies the two together via
        // moveRelative(getSpeed(), input). Applying speedScale to both squares it: 0.02 x 0.02 is
        // 0.0004, which is what a debug dump reading "speed=0.020 zza=0.020 delta=0.000" means.
        float drive = (float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED))
                * turningSpeedFactor(yawError);
        this.mob.setSpeed(drive * this.speedScale);

        if (Math.abs(dy) > 1.0E-5D || horizontal > 1.0E-5D) {
            float wantPitch = -((float) (Mth.atan2(dy, horizontal) * (180.0D / Math.PI)));
            wantPitch = Mth.clamp(Mth.wrapDegrees(wantPitch), -this.maxPitch, this.maxPitch);
            this.pitch = this.rotateTowards(this.pitch, wantPitch, this.pitchSpeed);
        }
        // Forward and vertical drive both come off the inclination, so the animal swims along its own
        // body axis rather than sliding sideways through the water.
        float pitchRad = this.pitch * DEG_TO_RAD;
        this.mob.zza = Mth.cos(pitchRad) * drive;
        this.mob.yya = -Mth.sin(pitchRad) * drive * this.verticalGain;
    }

    private float stepYaw(float signedError, float cap) {
        float accel = cap / this.rampTicks;
        float braking = (float) Math.sqrt(2.0F * accel * Math.abs(signedError));
        float wanted = Math.signum(signedError) * Math.min(cap, braking);
        this.yawRate = Mth.clamp(wanted, this.yawRate - accel, this.yawRate + accel);
        // The ramp can still be carrying more rate than the remaining error is worth — coming out of a
        // long turn into a short correction, say. Overshooting there is a wag, so cut it short.
        if (Math.abs(this.yawRate) > Math.abs(signedError)) {
            this.yawRate = signedError;
        }
        return this.yawRate;
    }

    private float adaptiveTurn(double distance) {
        float base = this.effectiveTurnSpeed();
        if (distance < 1.0E-4D) {
            return base;
        }
        // The real horizontal velocity, NOT getSpeed(). Two things were wrong with reading the field:
        // it holds the value setSpeed() was last given, which is the SCALED one (drive x 0.01), so
        // `needed` came out around a hundredth of a degree and the floor could never beat turnSpeed;
        // and it is read here BEFORE this tick's setSpeed(), so it was a tick stale on top. The orbit
        // escape was dead code, and a tick sample proves it — yRot stepping exactly 2.200 for a
        // hundred consecutive ticks, a circle and a half, with no way out of it. The turning radius
        // depends on how fast the body is actually travelling through the water, so ask the body.
        double speed = this.mob.getDeltaMovement().horizontalDistance();
        float needed = (float) Math.toDegrees(speed / distance) * ORBIT_MARGIN;
        return Math.max(base, Math.min(needed, ADAPTIVE_TURN_CAP));
    }

    private static float turningSpeedFactor(float yawError) {
        float t = Mth.clamp((yawError - FULL_SPEED_ERROR) / (STALL_ERROR - FULL_SPEED_ERROR), 0.0F, 1.0F);
        return Mth.lerp(t, 1.0F, MIN_TURN_SPEED);
    }
}

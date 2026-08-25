package net.darkblade.smop.entity.ai.control;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.jetbrains.annotations.NotNull;

/**
 * Swimming steer for something with mass: a real turn rate, speed that falls off while it is turning,
 * and a head left free to look where it likes.
 *
 * <p>Vanilla's {@code SmoothSwimmingMoveControl} is built for fish, and three of its decisions are
 * wrong on a three-block marine reptile:
 *
 * <ul>
 *   <li><b>The turn cap is enormous.</b> It passes {@code maxTurnY} straight to {@code rotlerp}, and
 *       the value the aquatic base hands it is 10 degrees per tick — two hundred a second, a full
 *       reversal inside a second. That is the "turns on a dime" complaint outright.</li>
 *   <li><b>It welds the head to the course.</b> {@code yBodyRot} and {@code yHeadRot} are both forced
 *       to the movement yaw every tick, so the animal cannot look at anything it is not swimming
 *       directly at, and every look control on it is overwritten. Only the body is steered here.</li>
 *   <li><b>It only trades speed for turning out of water.</b> {@code getTurningSpeedFactor} exists in
 *       vanilla and is applied in the land branch alone. Underwater the mob holds full speed through
 *       any turn, which is what lets it pivot rather than carve.</li>
 * </ul>
 *
 * <p><b>The orbit floor.</b> A capped turn rate gives a minimum turning radius of {@code v / omega},
 * and a waypoint inside that radius can never be reached — the animal settles into a stable circle
 * around it. The fix is {@code DirectionalMoveControl}'s: raise the cap toward {@code v / d} as the
 * target gets close, so it spirals in instead of holding the circle, bounded so a point-blank
 * waypoint still cannot whip the body round in one tick.
 */
public class SwimSteerControl extends MoveControl {

    private static final float DEG_TO_RAD = 0.017453292F;
    /** Below this squared distance the waypoint counts as reached and the body coasts. */
    private static final double ARRIVED_SQR = 2.5E-7D;

    /** Yaw error under which the animal runs at full speed. */
    private static final float FULL_SPEED_ERROR = 15.0F;
    /** Yaw error at which the speed cut bottoms out. */
    private static final float STALL_ERROR = 90.0F;
    /**
     * Speed retained at the worst yaw error. Vanilla's land formula reaches zero, which on a body
     * with this much momentum would read as slamming to a halt rather than leaning into the turn.
     */
    private static final float MIN_TURN_SPEED = 0.35F;

    /** @see #tick() — the orbit floor. */
    private static final float ORBIT_MARGIN = 1.5F;
    private static final float ADAPTIVE_TURN_CAP = 30.0F;

    /** Trim that holds the animal in the column, mirroring vanilla's {@code applyGravity} branch. */
    private static final double BUOYANCY = 0.005D;

    /**
     * Extra gain on the vertical drive.
     *
     * <p><b>Without it the animal swims a plane.</b> Vertical drive comes off the pitch as
     * {@code sin(pitch) x drive}, and at a cruise pitch of a few degrees that is roughly a ninth of
     * the forward component — which puts the per-tick vertical velocity under 0.003, the threshold
     * below which the engine zeroes a motion component outright. It is erased every tick before it
     * can accumulate, while the horizontal part clears the same bar comfortably and builds up
     * normally. The result is an animal that moves in X and Z and never in Y.
     *
     * <p>Six. Four cleared the engine's floor but left the climbs so gentle that the body tilt fed
     * from them stayed near zero; this makes a change of depth read as a decision without making a
     * dive faster than the swim itself.
     *
     * <p><b>But it is settable, because that last clause turned out to be a lie once anyone drew the
     * tilt.</b> {@code travel} feeds {@code zza}/{@code yya} through {@code moveRelative}, which
     * NORMALISES the input vector — so the gain does not add vertical speed on top of the forward
     * speed, it shifts the balance BETWEEN them. At six, {@code (cos p, 6 sin p)} is mostly vertical
     * for any pitch worth having: a tick sample of the salmon reads {@code dY} of −0.098 against a
     * horizontal speed near 0.065, which is a dive steeper than 55 degrees. Nothing looks wrong while
     * the model stays level; the moment the body is pitched along its own trajectory, the animal is
     * visibly standing on its tail.
     *
     * <p>A gain of 1 would mean the animal swims exactly where it points, which is the honest
     * relationship — the gain only exists to clear the engine's floor on a slow mover. Anything faster
     * wants far less than six. @see #verticalGain
     */
    private static final float DEFAULT_VERTICAL_GAIN = 6.0F;

    private float verticalGain = DEFAULT_VERTICAL_GAIN;

    /**
     * Swim inclination, held here instead of on the entity.
     *
     * <p><b>Never write this to {@code mob.setXRot}.</b> {@code AnimContext} feeds {@code state.xRot}
     * to the rig's {@code lookAt} as the HEAD pitch, so putting the path inclination there hands the
     * neck a value that is not a look at all — and one that moves in hard steps, because the pitch
     * target jumps every time the navigator advances a node. The result is a staircase copied onto
     * the neck, which is the head jitter.
     *
     * <p>Kept private, the inclination still drives {@code zza}/{@code yya} while {@code xRot} keeps
     * meaning what the rig thinks it means: where the head is looking.
     * Body tilt is {@code swimPitch}'s job, and it is smoothed for the purpose.
     */
    private float pitch;

    /**
     * Signed body-yaw rate in degrees per tick, carried between ticks so a turn can ramp.
     *
     * <p><b>Without it every turn is a square wave.</b> {@code rotlerp} is a hard clamp: the yaw moves
     * at exactly the cap or not at all, so the animal snaps to full turn rate, holds it, and stops
     * dead on the heading. A tick sample of an animal that was supposedly "cruising" reads
     * {@code 120.745, 118.545, 116.345 …} — exactly 2.200 every tick for thirty-seven ticks, then
     * exactly 2.200 the other way for the next twenty-three. That is not a creature leaning into a
     * turn, it is a servo at its limit, and reversing between two saturated rates in one tick is what
     * "it changes sides in a second" looks like from outside.
     *
     * @see #stepYaw
     */
    private float yawRate;

    /**
     * Ticks a turn takes to wind up to its rate — and, symmetrically, to wind down.
     *
     * <p>Expressed as a duration rather than an acceleration on purpose: the cap is not constant, the
     * orbit floor below raises it when a waypoint gets close, and a fixed acceleration would then need
     * two hundred ticks to reach a cap of thirty and the escape would never happen. Deriving the
     * acceleration from the cap keeps the ramp the same length whatever the cap currently is.
     *
     * <p>It is also the number that carries most of the sense of <em>mass</em>, which is why it is
     * settable: fifteen ticks of wind-up is three quarters of a second, right for something that
     * displaces water, and far too stately for a fish. @see #rampTicks
     */
    private static final float DEFAULT_RAMP_TICKS = 15.0F;

    private float rampTicks = DEFAULT_RAMP_TICKS;

    private final float turnSpeed;

    /**
     * Turn rate while the mob is fighting. Zero means "same as cruise", which is the default and what
     * every existing user gets.
     *
     * <p><b>Why a cruising rate cannot also be a combat rate.</b> {@link #turnSpeed} is chosen for
     * how a body of a given length should look moving through open water, and on the Nirasmosaurus
     * that is 2.2 degrees a tick — deliberately stately, because a three-block animal snapping to face
     * each waypoint reads as a puppet. A chase asks the opposite question: the target moves, so the
     * heading demand changes constantly, and at 2.2 a ninety-degree correction costs 41 ticks plus
     * two ramps. The animal is then permanently behind its own target, which looks like a slow chase
     * and lands its bite pointing somewhere the prey is not.
     *
     * <p>{@code DirectionalMoveControl} — the land equivalent, and the reason pursuit ashore already
     * felt right — has had exactly this knob from the start ({@code setCombatTurnSpeed}). This is that
     * idea in the water, down to reading the same two flags.
     */
    private float combatTurnSpeed;
    private final float maxPitch;
    private final float pitchSpeed;
    private final float speedScale;

    /**
     * @param turnSpeed  body yaw degrees per tick — the headline number
     * @param maxPitch   how far it may nose up or down
     * @param pitchSpeed pitch degrees per tick
     * @param speedScale in-water multiplier on the speed attribute, as vanilla's control takes
     */
    public SwimSteerControl(@NotNull Mob mob, float turnSpeed, float maxPitch, float pitchSpeed, float speedScale) {
        super(mob);
        this.turnSpeed = turnSpeed;
        this.maxPitch = maxPitch;
        this.pitchSpeed = pitchSpeed;
        this.speedScale = speedScale;
    }

    /** Balance between vertical and forward drive. @see #DEFAULT_VERTICAL_GAIN */
    public SwimSteerControl verticalGain(float gain) {
        this.verticalGain = gain;
        return this;
    }

    /** Ticks the turn takes to wind up and to wind down. @see #DEFAULT_RAMP_TICKS */
    public SwimSteerControl rampTicks(float ticks) {
        this.rampTicks = ticks;
        return this;
    }

    /**
     * Body yaw degrees per tick while fighting. Opt-in: leave it unset and the control behaves
     * exactly as before. @see #combatTurnSpeed
     */
    public SwimSteerControl combatTurnSpeed(float degreesPerTick) {
        this.combatTurnSpeed = degreesPerTick;
        return this;
    }

    /**
     * The rate in force this tick.
     *
     * <p>Reads BOTH {@code isAggressive()} and a live target, the same pair {@code
     * DirectionalMoveControl} reads, and for the same reason it gives there: the aggressive flag is
     * raised by {@code MeleeAttackGoal#start} and dropped by {@code #stop}, so it covers the swing
     * but goes false in the gaps between goals re-acquiring — while a target that is still alive
     * means the animal is still in a fight whatever the flag says. Either one is enough.
     *
     * <p>The ramp is derived from whichever rate is in force ({@code cap / rampTicks}), so entering
     * combat does not teleport the turn rate: it just raises the ceiling the ramp climbs toward.
     */
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

    /**
     * One tick of body yaw, accelerating into the turn and decelerating out of it.
     *
     * <p>Two limits, and neither is the hard clamp it replaces:
     *
     * <ul>
     *   <li><b>Acceleration</b> bounds how fast the rate itself may change, so a turn winds up over
     *       {@link #rampTicks} instead of appearing at full rate on the tick the waypoint moved. It
     *       also means a reversal has to pass through zero, which costs two full ramps — the animal
     *       physically cannot flip from turning hard left to turning hard right inside a second any
     *       more.</li>
     *   <li><b>Braking</b> bounds the rate by what can still be stopped exactly on the heading under
     *       that same acceleration, {@code sqrt(2 a e)}. This is what makes the last stretch of a turn
     *       ease out rather than end on a step, and it needs no tuning of its own: it falls out of the
     *       ramp length. With a 2.2 cap the ease-out occupies the final sixteen degrees or so.</li>
     * </ul>
     */
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

    /**
     * The configured turn rate, raised toward what the geometry demands when the waypoint is close
     * enough that the minimum turning radius would otherwise trap the animal in a circle around it.
     */
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

    /** Full speed while roughly on course, easing down to {@link #MIN_TURN_SPEED} while hauling round. */
    private static float turningSpeedFactor(float yawError) {
        float t = Mth.clamp((yawError - FULL_SPEED_ERROR) / (STALL_ERROR - FULL_SPEED_ERROR), 0.0F, 1.0F);
        return Mth.lerp(t, 1.0F, MIN_TURN_SPEED);
    }
}

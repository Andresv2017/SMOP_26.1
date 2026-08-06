package net.darkblade.smop.entity;

import net.darkblade.deluxelib.entity.ai.navigation.SmartFlyingNavigation;
import net.darkblade.deluxelib.entity.ai.pathing.SmoothFlyingMoveControl;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.entity.sleep.SleepGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Shared base for SMOP's fliers: a mob that walks and flies, with a real flight lifecycle rather
 * than a binary switch.
 *
 * <h3>The cycle</h3>
 * <pre>
 *   GROUNDED ──(groundRestTimer hits 0)──▶ TakeoffGoal
 *     beginTakeoff()   → FLYING, TAKING_OFF, flight nav, no gravity, onTakeoffBegin()
 *     completeTakeoff()→ TAKING_OFF off, onTakeoffComplete()
 *   SOARING ──(flightDurationTimer ≥ maxFlightTicks)──▶ powered descent
 *     seekingGround    → onSeekGroundBegin()   (the stoop)
 *   ──(within getLandingApproachAltitude())──▶ LandingGoal
 *     beginLanding()   → LANDING, onLandingBegin()
 *     completeLanding()→ grounded, gravity back, rest timer reset, onLandingComplete()
 * </pre>
 *
 * <p><b>Why this is a copy of the system and not a subclass.</b> DeluxeLib's
 * {@code AbstractFlyingEntity} is {@code PathfinderMob implements Enemy}. The blocker is not the
 * {@code Enemy} marker and not ownership — the library's own Owl is an {@code AbstractFlyingEntity}
 * that keeps an owner as a bare {@code UUID}. The blocker is that {@code AbstractFlyingEntity} and
 * {@code AgeableMob} are sibling branches of {@code PathfinderMob}, and SMOP's fliers need the
 * ageable branch: they hatch from eggs as chicks, grow, and breed
 * ({@code TamableAnimal → Animal → AgeableMob → PathfinderMob}). Java has one superclass, so the
 * system is brought in rather than inherited — the same call made for {@code SMOPWaterAnimal}.
 *
 * <p><b>Port note.</b> 1.20.1 flipped between walking and flying every 80 ticks and pushed a
 * bespoke {@code StoCSyncFlying} packet on each switch. Both are gone: the phases below are synced
 * entity data, so the client sees them for free and the play conditions read them on either side.
 *
 * <p>Babies never fly. Enforced in {@link #setFlying} <em>and</em> at the gate of
 * {@link TakeoffGoal}, so a chick can neither be put in the air nor enter a half-started takeoff.
 */
public abstract class SMOPFlyingAnimal extends GenderedSMOPAnimal {

    /** Airborne right now — drives navigation, gravity and the flight clips. */
    private static final EntityDataAccessor<Boolean> FLYING =
            SynchedEntityData.defineId(SMOPFlyingAnimal.class, EntityDataSerializers.BOOLEAN);
    /** Leaving the ground: the window the take-off clip owns. */
    private static final EntityDataAccessor<Boolean> TAKING_OFF =
            SynchedEntityData.defineId(SMOPFlyingAnimal.class, EntityDataSerializers.BOOLEAN);
    /** Final touchdown: the window the landing clip owns. */
    private static final EntityDataAccessor<Boolean> LANDING =
            SynchedEntityData.defineId(SMOPFlyingAnimal.class, EntityDataSerializers.BOOLEAN);
    /**
     * Airborne <em>and</em> travelling. Synced because {@code MobAnimator}'s auto-start loop runs on
     * both sides: a client computing this locally would disagree with the server (it never sees
     * {@link #seekingGround}) and would restart the travel clip every tick the server stopped it.
     */
    private static final EntityDataAccessor<Boolean> FLYING_MOVING =
            SynchedEntityData.defineId(SMOPFlyingAnimal.class, EntityDataSerializers.BOOLEAN);

    // ───────────────────────────────────────────────────── NAVIGATION ─────

    protected PathNavigation groundNavigation;
    protected PathNavigation flyingNavigation;
    private boolean usingGroundNav = true;

    // ───────────────────────────────────────────────────── VISUAL TILT ─────

    /** Read by the renderer each frame, interpolated against its {@code prev} twin. */
    public float flightPitch;
    public float prevFlightPitch;
    public float flightRoll;
    public float prevFlightRoll;
    private float smoothedVerticalSpeed;
    /** Smoothing state for the client half of {@link #tickRotation()} — never written server-side. */
    private float clientYawRate;
    private float clientVerticalSpeed;
    private float clientHorizontalSpeed;

    // ───────────────────────────────────────────────────── ANIM HYSTERESIS ─────

    /** Smoothed squared horizontal speed: slow to rise (momentum), quick to fall (responsive stop). */
    private double smoothedHorizontalSpeed;
    /** Ticks left before the travel clip may drop back to the hover. */
    private int flyAnimHoldTicks;
    /** Server-local mirror of {@link #FLYING_MOVING}, so both sides read one value. */
    private boolean flyingMovingLocal;
    /** Descending under power toward the landing approach — the stoop. */
    protected boolean seekingGround;
    /** Parked mid-air between wander legs. Drops the anti-flicker hold so the hover reads at once. */
    private boolean flightHovering;

    // ───────────────────────────────────────────────────── TIMERS ─────

    /** Counts down while grounded; takeoff triggers at 0. */
    protected int groundRestTimer = 100;
    /** Counts up while actually soaring (not taking off, not landing). */
    protected int flightDurationTimer;
    /** Rolled at the start of each flight; landing begins once the duration passes it. */
    private int maxFlightTicks;

    protected SMOPFlyingAnimal(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        // Fliers path around water and lava rather than over them: a negative malus marks the node
        // as dangerous, not merely expensive.
        this.setPathfindingMalus(PathType.WATER, -8.0F);
        this.setPathfindingMalus(PathType.LAVA, -8.0F);
    }

    // ───────────────────────────────────────────────────── NAVIGATION ─────

    /**
     * Builds both navigators once and returns the ground one. Keeping two live instances is what
     * makes the switch cheap and, more importantly, stable: the old version rebuilt the navigator on
     * every mode change, which invalidated every reference a goal had cached.
     */
    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull Level level) {
        this.groundNavigation = this.createGroundNavigation(level);
        this.flyingNavigation = this.createFlightNavigation(level);
        return this.groundNavigation;
    }

    protected PathNavigation createGroundNavigation(Level level) {
        return new GroundPathNavigation(this, level);
    }

    protected PathNavigation createFlightNavigation(Level level) {
        SmartFlyingNavigation nav = new SmartFlyingNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.getNodeEvaluator().setCanPassDoors(true);
        return nav;
    }

    protected MoveControl createGroundMoveControl() {
        return new MoveControl(this);
    }

    protected MoveControl createFlyMoveControl() {
        return new SmoothFlyingMoveControl(this, 10, true, this.getFlightYawTurnSpeed());
    }

    /**
     * Maximum body-yaw turn per tick, in degrees, while flying. Vanilla's 90°/tick snaps the whole
     * body round in a tick or two on every retarget; a small figure carves arcs the banking roll can
     * read as a lean.
     */
    protected float getFlightYawTurnSpeed() {
        return 8.0F;
    }

    protected void switchToGroundNav() {
        if (!this.usingGroundNav) {
            this.moveControl = this.createGroundMoveControl();
            this.navigation = this.groundNavigation;
            this.usingGroundNav = true;
            this.onNavigationSwapped();
        }
    }

    protected void switchToFlyNav() {
        if (this.usingGroundNav) {
            this.moveControl = this.createFlyMoveControl();
            this.navigation = this.flyingNavigation;
            this.usingGroundNav = false;
            this.onNavigationSwapped();
        }
    }

    /** Fired after {@link #getNavigation()} starts returning a different object, so goals holding a
     *  cached reference can refresh it. */
    protected void onNavigationSwapped() {
    }

    // ───────────────────────────────────────────────────── STATE ─────

    public boolean isFlying() {
        return this.entityData.get(FLYING);
    }

    /** Babies are refused outright — see the class note. */
    public void setFlying(boolean flying) {
        if (flying && this.isBaby()) {
            return;
        }
        this.entityData.set(FLYING, flying);
    }

    public boolean isTakingOff() {
        return this.entityData.get(TAKING_OFF);
    }

    protected void setTakingOff(boolean value) {
        this.entityData.set(TAKING_OFF, value);
    }

    public boolean isLanding() {
        return this.entityData.get(LANDING);
    }

    protected void setLanding(boolean value) {
        this.entityData.set(LANDING, value);
    }

    /** Synced and held — safe to read from a play condition on either side. */
    public boolean isFlyingMoving() {
        return this.entityData.get(FLYING_MOVING);
    }

    /** Descending under power toward the landing approach. */
    public boolean isSeekingGround() {
        return this.seekingGround;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FLYING, false);
        builder.define(TAKING_OFF, false);
        builder.define(LANDING, false);
        builder.define(FLYING_MOVING, false);
    }

    // ───────────────────────────────────────────────────── LIFECYCLE ─────

    protected void beginTakeoff() {
        if (this.isFlying() || this.isBaby()) {
            SMOP.LOGGER.warn("[SMOP] beginTakeoff refused for entity {} (flying={}, baby={})",
                    this.getId(), this.isFlying(), this.isBaby());
            return;
        }
        this.switchToFlyNav();
        this.setFlying(true);
        this.setTakingOff(true);
        this.setNoGravity(true);
        this.flightDurationTimer = 0;
        this.maxFlightTicks = this.computeMaxFlightTicks();
        this.resetFallDistance();
        this.setDeltaMovement(0.0D, this.getTakeoffLiftSpeed() * 3.0D, 0.0D);
        this.onTakeoffBegin();
    }

    protected void completeTakeoff() {
        this.setTakingOff(false);
        this.onTakeoffComplete();
    }

    protected void beginLanding() {
        if (this.isLanding()) {
            return;
        }
        this.setLanding(true);
        this.switchToGroundNav();
        this.getNavigation().stop();
        this.resetFallDistance();
        this.onLandingBegin();
    }

    protected void completeLanding() {
        if (!this.isLanding()) {
            return;
        }
        this.setLanding(false);
        this.resetFlightState();
        this.onLandingComplete();
    }

    /**
     * Puts the mob back on its feet and clears every scrap of flight state. Used by
     * {@link #completeLanding()}, and directly by subclasses for the cases that end a flight without
     * a touchdown to animate — being perched, picked up, or ordered to sit mid-air.
     */
    protected void resetFlightState() {
        this.setLanding(false);
        this.setTakingOff(false);
        this.setFlying(false);
        this.setNoGravity(false);
        this.resetFallDistance();
        this.setDeltaMovement(Vec3.ZERO);
        this.switchToGroundNav();
        this.groundRestTimer = this.computeGroundRestTicks();
        this.flightDurationTimer = 0;
        this.smoothedHorizontalSpeed = 0.0D;
        this.flyingMovingLocal = false;
        this.flyAnimHoldTicks = 0;
        this.seekingGround = false;
        this.flightHovering = false;
        this.entityData.set(FLYING_MOVING, false);
    }

    /**
     * Asks for a takeoff at the next opportunity by expiring the rest timer, rather than calling
     * {@link #beginTakeoff()} from outside. Going through {@link TakeoffGoal} keeps the goal's own
     * {@code canContinueToUse} in step with the state, and keeps the gates (baby, sleeping, perched)
     * in one place.
     */
    public void requestTakeoff() {
        if (!this.isFlying() && !this.isTakingOff() && !this.isBaby()) {
            this.groundRestTimer = 0;
        }
    }

    /**
     * The mirror of {@link #requestTakeoff()}: expires the flight timer so the normal
     * stoop-then-land path runs at the next opportunity. Nothing is forced — if some goal is holding
     * MOVE and keeping {@link FlightWanderGoal} out, the mob keeps flying until that goal lets go,
     * which is the correct outcome for, say, a pet mid-escort.
     */
    public void requestLanding() {
        if (this.isFlying() && !this.isTakingOff() && !this.isLanding()) {
            this.flightDurationTimer = Math.max(this.flightDurationTimer, this.maxFlightTicks);
        }
    }

    // ───────────────────────────────────────────────────── ANIMATION HOOKS ─────

    /** Leaving the ground — play the take-off clip here. */
    protected void onTakeoffBegin() {
    }

    /** Take-off finished, cruising flight begins. */
    protected void onTakeoffComplete() {
    }

    /** The stoop: the powered descent toward the landing approach has begun. */
    protected void onSeekGroundBegin() {
    }

    /** Final approach — play the landing clip here. */
    protected void onLandingBegin() {
    }

    /** Feet on the ground. Looping locomotion clips restart themselves, so this is usually empty. */
    protected void onLandingComplete() {
    }

    // ───────────────────────────────────────────────────── TUNABLES ─────

    /** Minimum altitude in blocks above the terrain beneath the mob. */
    protected double getMinFlightAltitude() {
        return 8.0D;
    }

    /**
     * Maximum altitude in blocks <em>above the terrain</em>, not an absolute world Y. An absolute
     * ceiling silently pushes every wander target underground on tall terrain and pins the mob to
     * the floor.
     */
    protected double getMaxFlightAltitude() {
        return 30.0D;
    }

    /** Horizontal wander radius in blocks. */
    protected double getWanderHorizontalRadius() {
        return 20.0D;
    }

    /** Ticks spent on the ground before the mob tries to take off. */
    protected int computeGroundRestTicks() {
        return 80 + this.random.nextInt(80);
    }

    /** Ticks in the air before the mob starts looking for somewhere to come down. */
    protected int computeMaxFlightTicks() {
        return 200 + this.random.nextInt(200);
    }

    /** Descent speed in blocks/tick during the final approach. */
    protected double getLandingDescentSpeed() {
        return 0.08D;
    }

    /** Upward speed in blocks/tick sustained through the take-off phase. */
    protected double getTakeoffLiftSpeed() {
        return 0.10D;
    }

    /** Height above terrain at which the stoop hands over to the landing transition. */
    protected double getLandingApproachAltitude() {
        return 4.0D;
    }

    /** Forward speed in blocks/tick during the stoop, so it reads as a swoop and not a lift drop. */
    protected double getDescentForwardSpeed() {
        return 0.25D;
    }

    /** Extra nose-up blended in while landing — the flare that sells the touchdown. */
    protected float getFlarePitchUp() {
        return 12.0F;
    }

    /**
     * Whether the physics tilt keeps applying during take-off. Return {@code false} when an authored
     * take-off clip already owns the pose, or the near-vertical climb reads as full nose-up layered
     * on top of the keyframes. The tilt then eases to zero and fades back in once take-off completes.
     */
    protected boolean applyTiltDuringTakeoff() {
        return true;
    }

    /**
     * Hard cap on the landing phase. Past it the landing force-completes and gravity finishes the
     * job (fliers take no fall damage), so neither a strict completion override nor bouncing against
     * a slope can leave the mob stuck in the landing state.
     */
    protected int getMaxLandingTicks() {
        return 100;
    }

    /** Ticks spent hovering between wander legs. */
    protected int computeFlightHoverTicks() {
        return 30 + this.random.nextInt(50);
    }

    // ───────────────────────────────────────────────────── WANDER TARGET ─────

    /**
     * A point to fly to: biased around the current heading, with a climb/descend/hold roll, clamped
     * to the altitude band above whatever terrain is under the target column.
     */
    @Nullable
    protected Vec3 findFlightWanderTarget() {
        for (int attempt = 0; attempt < 30; attempt++) {
            double radius = this.getWanderHorizontalRadius() * (0.5D + this.random.nextDouble() * 0.5D);
            float yawRad = (float) Math.toRadians(this.getYRot());
            double fwdX = -Math.sin(yawRad);
            double fwdZ = Math.cos(yawRad);
            double sideAngle = (this.random.nextDouble() - 0.5D) * Math.PI * 2.0D;
            double cos = Math.cos(sideAngle);
            double sin = Math.sin(sideAngle);
            double x = this.getX() + (fwdX * cos - fwdZ * sin) * radius;
            double z = this.getZ() + (fwdX * sin + fwdZ * cos) * radius;

            double roll = this.random.nextDouble();
            double y;
            if (roll < 0.3D) {
                y = this.getY() + 5.0D + this.random.nextDouble() * 15.0D;
            } else if (roll < 0.6D) {
                y = this.getY() - 5.0D - this.random.nextDouble() * 10.0D;
            } else {
                y = this.getY() + (this.random.nextDouble() - 0.5D) * 10.0D;
            }

            // The heightmap, not findGroundY: that scan starts at the mob's own Y, so on a column
            // taller than the mob (a hillside) it starts inside rock and reports "ground" at the
            // mob's altitude — ratcheting every leg one minimum-altitude higher until the ceiling.
            double groundY = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z);
            y = Math.max(y, groundY + this.getMinFlightAltitude());
            y = Math.min(y, groundY + this.getMaxFlightAltitude());

            BlockPos target = BlockPos.containing(x, y, z);
            if (this.level().hasChunkAt(target) && this.level().getBlockState(target).isAir()) {
                return new Vec3(x, y, z);
            }
        }
        return null;
    }

    /**
     * Y of the first solid block below (x, z), scanning down from the mob.
     *
     * <p>Starts by rising out of whatever the mob may be clipping — foliage while gliding through a
     * canopy — because otherwise the downward scan reports ground at the mob's own feet and triggers
     * a landing in mid-air. Birds perch on canopies, so the top of the thing counts as ground.
     */
    protected double findGroundY(double x, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos((int) x, (int) this.getY(), (int) z);
        int maxY = this.level().getMaxY();
        while (pos.getY() < maxY && !this.level().getBlockState(pos).isAir()) {
            pos.move(Direction.UP);
        }
        int minY = this.level().getMinY();
        while (pos.getY() > minY && this.level().getBlockState(pos).isAir()) {
            pos.move(Direction.DOWN);
        }
        return pos.getY() + 1.0D;
    }

    // ───────────────────────────────────────────────────── DIRECT STEERING ─────

    /**
     * Blends the current velocity toward {@code target} and turns the body at the capped rate.
     * Smooth arcs, none of the stair-stepping that flying path navigation produces — stop the
     * navigation first and call this every tick from a goal.
     *
     * @param accel per-tick blend toward the desired velocity; 0.1–0.15 glides, 0.3+ darts
     */
    protected void steerTowards(Vec3 target, double speed, double accel) {
        Vec3 to = target.subtract(this.position());
        double dist = to.length();
        if (dist < 0.3D) {
            return;
        }
        Vec3 current = this.getDeltaMovement();
        Vec3 desired = to.scale(speed / dist);
        Vec3 velocity = current.add(desired.subtract(current).scale(accel));
        if (this.horizontalCollision) {
            velocity = new Vec3(velocity.x * 0.5D, Math.max(velocity.y, 0.2D), velocity.z * 0.5D);
        }
        this.setDeltaMovement(velocity);
        if (velocity.horizontalDistanceSqr() > 1.0E-4D) {
            this.faceHeading(velocity.x, velocity.z, this.getFlightYawTurnSpeed());
        }
    }

    /** Turns body yaw toward a horizontal heading, at most {@code maxTurnDegrees} per call. The cap
     *  is what feeds a readable banking roll in {@link #tickRotation()}.
     *
     *  <p>Public because the goals that fly by direct velocity control live outside this package. */
    public void faceHeading(double dx, double dz, float maxTurnDegrees) {
        if (dx * dx + dz * dz < 1.0E-4D) {
            return;
        }
        float wantYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float newYaw = Mth.approachDegrees(this.getYRot(), wantYaw, maxTurnDegrees);
        this.setYRot(newYaw);
        this.yBodyRot = newYaw;
    }

    // ───────────────────────────────────────────────────── VISUAL TILT ─────

    /**
     * Pitch and roll for the renderer. Recomputed on <b>both</b> sides from different inputs:
     * {@code deltaMovement} is not synced for mobs, so a client running the server formula would
     * read ~0 and render the flight rigidly level.
     */
    private void tickRotation() {
        if (this.level().isClientSide()) {
            this.tickClientRotation();
            return;
        }
        this.prevFlightPitch = this.flightPitch;
        this.prevFlightRoll = this.flightRoll;

        // isDeadOrDying: the authored death clip owns the corpse pose, and the near-vertical drop of
        // a dying flier would otherwise clamp the pitch full nose-down on top of it.
        if (!this.isFlying() || this.isDeadOrDying()
                || (this.isTakingOff() && !this.applyTiltDuringTakeoff())) {
            this.flightPitch = Mth.lerp(0.1F, this.flightPitch, 0.0F);
            this.flightRoll = Mth.lerp(0.1F, this.flightRoll, 0.0F);
            this.smoothedVerticalSpeed = 0.0F;
            return;
        }

        float vertical = (float) this.getDeltaMovement().y;
        if (Math.abs(vertical) < 0.01F) {
            vertical = 0.0F;
        }
        this.smoothedVerticalSpeed = Mth.lerp(0.08F, this.smoothedVerticalSpeed, vertical);

        float targetPitch = Mth.clamp(this.smoothedVerticalSpeed * 100.0F, -40.0F, 40.0F);
        float pitchLerp = 0.06F;
        if (this.isLanding()) {
            // The flare: extra nose-up while killing sink speed. Faster lerp so the gesture reads
            // inside the short landing window.
            targetPitch += this.getFlarePitchUp();
            pitchLerp = 0.15F;
        }
        this.flightPitch = Mth.lerp(pitchLerp, this.flightPitch, targetPitch);

        float yawDelta = Mth.wrapDegrees(this.getYRot() - this.yRotO);
        if (Math.abs(yawDelta) < 0.3F) {
            yawDelta = 0.0F;
        }
        float speedFactor = (float) Math.min(1.0D, this.getDeltaMovement().horizontalDistance() * 3.0D);
        float targetRoll = Mth.clamp(-yawDelta * 4.0F * speedFactor, -45.0F, 45.0F);
        this.flightRoll = Mth.lerp(0.06F, this.flightRoll, targetRoll);
    }

    /** Client half: pitch from the actual per-tick position delta, roll from the yaw rate, both
     *  smoothed so the jitter of interpolated positions does not shake the model. */
    private void tickClientRotation() {
        float targetPitch = 0.0F;
        float targetRoll = 0.0F;
        if (this.isFlying() && !this.isDeadOrDying()
                && (!this.isTakingOff() || this.applyTiltDuringTakeoff())) {
            float vertical = (float) (this.getY() - this.yo);
            double dx = this.getX() - this.xo;
            double dz = this.getZ() - this.zo;
            float horizontal = (float) Math.sqrt(dx * dx + dz * dz);
            this.clientVerticalSpeed = Mth.lerp(0.2F, this.clientVerticalSpeed, vertical);
            this.clientHorizontalSpeed = Mth.lerp(0.2F, this.clientHorizontalSpeed, horizontal);
            if (this.clientHorizontalSpeed > 0.01F || Math.abs(this.clientVerticalSpeed) > 0.01F) {
                // The 0.08 floor keeps atan2 from swinging to ±90° during a near-vertical hover.
                targetPitch = (float) Math.toDegrees(Math.atan2(this.clientVerticalSpeed,
                        Math.max(this.clientHorizontalSpeed, 0.08F)));
                targetPitch = Mth.clamp(targetPitch, -50.0F, 50.0F);
            }
            if (this.isLanding()) {
                targetPitch += this.getFlarePitchUp();
            }
            float yawDelta = Mth.wrapDegrees(this.getYRot() - this.yRotO);
            this.clientYawRate = Mth.lerp(0.3F, this.clientYawRate, yawDelta);
            float speedFactor = (float) Math.min(1.0D, horizontal * 4.0D);
            targetRoll = Mth.clamp(-this.clientYawRate * 12.0F * speedFactor, -55.0F, 55.0F);
        } else {
            this.clientYawRate = 0.0F;
            this.clientVerticalSpeed = 0.0F;
            this.clientHorizontalSpeed = 0.0F;
        }
        this.prevFlightPitch = this.flightPitch;
        this.prevFlightRoll = this.flightRoll;
        this.flightPitch = Mth.lerp(0.15F, this.flightPitch, targetPitch);
        this.flightRoll = Mth.lerp(0.15F, this.flightRoll, targetRoll);
    }

    // ───────────────────────────────────────────────────── TICK ─────

    @Override
    public void aiStep() {
        super.aiStep();
        this.tickRotation();
        this.tickFlyingMoving();

        if (this.isFlying() && !this.isTakingOff() && !this.isLanding()) {
            this.flightDurationTimer++;
        }
        if (!this.isFlying() && !this.isTakingOff() && !this.isLanding() && this.groundRestTimer > 0) {
            this.groundRestTimer--;
        }

        // Told to stay while airborne. SMOPAnimal's sit clamp only stops the navigation and kills the
        // velocity, which for a mob with gravity off means hanging in the sky until the flight timer
        // happens to run out. Expiring it now sends the mob down the ordinary stoop-and-land path.
        if (!this.level().isClientSide() && this.isOrderedToSit()) {
            this.requestLanding();
        }
    }

    /** Hover versus travel, with the asymmetric hysteresis that keeps the two clips from strobing. */
    private void tickFlyingMoving() {
        // The client reads the synced value; running the logic here would overwrite it with a
        // locally-computed one, and the client never has seekingGround set.
        if (this.level().isClientSide()) {
            return;
        }

        boolean next;
        // The stoop keeps a high horizontal speed, so the travel clip carries through it naturally —
        // only the landing phase itself forces the non-moving state.
        if (!this.isFlying() || this.isTakingOff() || this.isLanding()) {
            this.smoothedHorizontalSpeed = 0.0D;
            this.flyAnimHoldTicks = 0;
            this.flyingMovingLocal = false;
            next = false;
        } else {
            double sq = this.getDeltaMovement().horizontalDistanceSqr();
            this.smoothedHorizontalSpeed = sq > this.smoothedHorizontalSpeed
                    ? this.smoothedHorizontalSpeed * 0.85D + sq * 0.15D
                    : this.smoothedHorizontalSpeed * 0.5D + sq * 0.5D;
            if (this.flyAnimHoldTicks > 0) {
                this.flyAnimHoldTicks--;
            }
            // A hover pause is a deliberate multi-second stop, not a mid-flight slowdown — drop the
            // anti-flicker hold so the hover clip appears as soon as the mob parks.
            if (this.flightHovering) {
                this.flyAnimHoldTicks = 0;
            }

            if (!this.flyingMovingLocal && this.smoothedHorizontalSpeed > 0.002D) {
                this.flyingMovingLocal = true;
                this.flyAnimHoldTicks = 50;
            } else if (this.flyingMovingLocal && this.smoothedHorizontalSpeed < 0.0003D
                    && this.flyAnimHoldTicks <= 0) {
                this.flyingMovingLocal = false;
            }
            next = this.flyingMovingLocal;
        }
        if (next != this.entityData.get(FLYING_MOVING)) {
            this.entityData.set(FLYING_MOVING, next);
        }
    }

    /**
     * The ground walk flag stands down in the air. {@code SMOPAnimal} already owns {@code isMoving()}
     * with the same hold timer, so a second airborne copy of it would be dead API for every flier
     * that follows.
     */
    @Override
    protected boolean isMovingNow() {
        return !this.isFlying() && super.isMovingNow();
    }

    /**
     * A roar pins the mob by zeroing its horizontal movement in {@code SMOPAnimal#travel}. On the
     * ground that reads as bracing; in the air it would freeze the mob mid-flight until the landing
     * goal dragged it down. Airborne, a roar plays but does not pin.
     */
    @Override
    public boolean isMovementLocked() {
        return this.isFlying() ? this.isInSleepCycle() : super.isMovementLocked();
    }

    /**
     * Sleep only on the ground. {@link TakeoffGoal} deliberately holds no flags — that is what lets
     * ground goals keep running while it lifts off — so without this gate the selector would happily
     * launch a sleeping mob into the air.
     */
    @Override
    protected SleepGoal<SMOPAnimal> createSleepGoal() {
        return new SleepGoal<SMOPAnimal>(this, this.sleepUrge(),
                this::getPreparingSleepDuration, this::getAwakeningDuration) {
            @Override
            public boolean canUse() {
                return !SMOPFlyingAnimal.this.isFlying() && super.canUse();
            }
        };
    }

    // ───────────────────────────────────────────────────── PHYSICS ─────

    /** A flier is never hurt by the ground it chose to land on. */
    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, @NotNull DamageSource source) {
        return false;
    }

    /**
     * A mob that dies mid-flight still has {@code noGravity} set: without clearing it the corpse
     * hangs in the air instead of falling. Dead mobs stop steering but physics still applies, so
     * dropping the flag is all it takes.
     */
    @Override
    public void die(@NotNull DamageSource source) {
        super.die(source);
        if (!this.level().isClientSide() && this.isFlying()) {
            this.setNoGravity(false);
            this.getNavigation().stop();
        }
    }

    // ───────────────────────────────────────────────────── GOAL REGISTRATION ─────

    /**
     * Registers the three flight goals. {@link TakeoffGoal} holds no flags on purpose, so ground
     * goals keep running while the mob lifts off; the other two hold MOVE.
     *
     * <pre>{@code
     * this.registerFlightGoals(2, 6, 7);
     * this.goalSelector.addGoal(9, groundStroll);   // never conflicts: it only runs on the ground
     * }</pre>
     */
    protected void registerFlightGoals(int takeoffPriority, int wanderPriority, int landPriority) {
        this.goalSelector.addGoal(takeoffPriority, this.createTakeoffGoal());
        this.goalSelector.addGoal(wanderPriority, new FlightWanderGoal());
        this.goalSelector.addGoal(landPriority, this.createLandingGoal());
    }

    /** Override to return a subclass that waits for the take-off clip to finish. */
    protected TakeoffGoal createTakeoffGoal() {
        return new TakeoffGoal();
    }

    /** Override to return a subclass with a stricter completion test. */
    protected LandingGoal createLandingGoal() {
        return new LandingGoal();
    }

    // ───────────────────────────────────────────────────── TAKEOFF ─────

    protected class TakeoffGoal extends Goal {

        /** Ticks since the take-off began — for time-based {@link #shouldCompleteTakeoff()}. */
        protected int takeoffTicks;

        @Override
        public boolean canUse() {
            return !SMOPFlyingAnimal.this.isBaby()
                    && !SMOPFlyingAnimal.this.isFlying()
                    && !SMOPFlyingAnimal.this.isTakingOff()
                    && !SMOPFlyingAnimal.this.isLanding()
                    && !SMOPFlyingAnimal.this.isOrderedToSit()
                    && !SMOPFlyingAnimal.this.isMovementLocked()
                    && SMOPFlyingAnimal.this.groundRestTimer <= 0;
        }

        @Override
        public boolean canContinueToUse() {
            return SMOPFlyingAnimal.this.isTakingOff();
        }

        @Override
        public void start() {
            this.takeoffTicks = 0;
            SMOPFlyingAnimal.this.beginTakeoff();
        }

        @Override
        public void tick() {
            this.takeoffTicks++;
            // Sustained lift, so the mob visibly rises instead of being braked back down by the move
            // control. Gravity is off, so this is only fighting the controller, not the world.
            Vec3 motion = SMOPFlyingAnimal.this.getDeltaMovement();
            double lift = SMOPFlyingAnimal.this.getTakeoffLiftSpeed();
            if (motion.y < lift) {
                SMOPFlyingAnimal.this.setDeltaMovement(motion.x * 0.6D, lift, motion.z * 0.6D);
            }
            if (this.shouldCompleteTakeoff()) {
                SMOPFlyingAnimal.this.completeTakeoff();
            }
        }

        /** Override to hold the phase open until the take-off clip finishes. Default: one tick. */
        protected boolean shouldCompleteTakeoff() {
            return true;
        }
    }

    // ───────────────────────────────────────────────────── WANDER ─────

    protected class FlightWanderGoal extends Goal {

        /** Ticks left in the current mid-air pause between legs. */
        private int hoverTicksRemaining;

        public FlightWanderGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            // !isMovementLocked(): this is the one flight goal that does not already stand down on
            // its own, so a mob pinned by something else (a Kriftognathus perched on its owner's
            // head, say) would otherwise keep picking wander legs and steering against whatever is
            // holding it in place.
            return SMOPFlyingAnimal.this.isFlying()
                    && !SMOPFlyingAnimal.this.isTakingOff()
                    && !SMOPFlyingAnimal.this.isLanding()
                    && !SMOPFlyingAnimal.this.isMovementLocked();
        }

        @Override
        public boolean canContinueToUse() {
            return SMOPFlyingAnimal.this.isFlying() && !SMOPFlyingAnimal.this.isLanding()
                    && !SMOPFlyingAnimal.this.isMovementLocked();
        }

        @Override
        public void start() {
            SMOPFlyingAnimal.this.seekingGround = false;
            SMOPFlyingAnimal.this.flightHovering = false;
            this.hoverTicksRemaining = 0;
        }

        @Override
        public void tick() {
            if (SMOPFlyingAnimal.this.isTakingOff()) {
                return;
            }
            if (SMOPFlyingAnimal.this.seekingGround) {
                this.tickSeekGround();
                return;
            }

            if (SMOPFlyingAnimal.this.flightDurationTimer >= SMOPFlyingAnimal.this.maxFlightTicks) {
                SMOPFlyingAnimal.this.seekingGround = true;
                SMOPFlyingAnimal.this.flightHovering = false;
                SMOPFlyingAnimal.this.getNavigation().stop();
                SMOPFlyingAnimal.this.onSeekGroundBegin();
                return;
            }

            // The rhythm: fly a leg, hover a beat, fly the next.
            if (!SMOPFlyingAnimal.this.getNavigation().isDone()) {
                return;
            }
            if (this.hoverTicksRemaining > 0) {
                this.hoverTicksRemaining--;
                Vec3 motion = SMOPFlyingAnimal.this.getDeltaMovement();
                SMOPFlyingAnimal.this.setDeltaMovement(motion.x * 0.6D, motion.y * 0.6D, motion.z * 0.6D);
                if (this.hoverTicksRemaining == 0) {
                    SMOPFlyingAnimal.this.flightHovering = false;
                    Vec3 target = SMOPFlyingAnimal.this.findFlightWanderTarget();
                    if (target != null) {
                        SMOPFlyingAnimal.this.getNavigation().moveTo(target.x, target.y, target.z, 1.0D);
                    }
                }
                return;
            }
            // Just arrived, or the last target failed — pause here before the next leg.
            SMOPFlyingAnimal.this.flightHovering = true;
            this.hoverTicksRemaining = SMOPFlyingAnimal.this.computeFlightHoverTicks();
        }

        /**
         * The stoop: keeps flying forward along the current heading while sinking, so the travel clip
         * plays all the way down like a bird swooping in. Sink rate scales with the remaining
         * altitude, so the approach eases in. Hands over to {@link LandingGoal} within
         * {@link #getLandingApproachAltitude()} of the terrain.
         *
         * <p>Velocity is written directly rather than routed through the flying navigation because a
         * nav target below the mob drives the flying move control into an {@code atan2(0, 0)} yaw spin.
         */
        private void tickSeekGround() {
            double groundY = SMOPFlyingAnimal.this.findGroundY(
                    SMOPFlyingAnimal.this.getX(), SMOPFlyingAnimal.this.getZ());
            double distToGround = SMOPFlyingAnimal.this.getY() - groundY;

            if (distToGround <= SMOPFlyingAnimal.this.getLandingApproachAltitude()) {
                SMOPFlyingAnimal.this.seekingGround = false;
                SMOPFlyingAnimal.this.beginLanding();
                return;
            }

            double forward = SMOPFlyingAnimal.this.getDescentForwardSpeed();
            double maxSink = SMOPFlyingAnimal.this.getLandingDescentSpeed() * 2.0D;
            double sink = Mth.clamp(distToGround * 0.03D,
                    SMOPFlyingAnimal.this.getLandingDescentSpeed(), maxSink);
            if (SMOPFlyingAnimal.this.horizontalCollision) {
                // Obstacle ahead: veer off, slow down, and drop at full rate to clear it.
                SMOPFlyingAnimal.this.setYRot(SMOPFlyingAnimal.this.getYRot() + 15.0F);
                forward *= 0.4D;
                sink = maxSink;
            }
            float yawRad = (float) Math.toRadians(SMOPFlyingAnimal.this.getYRot());
            SMOPFlyingAnimal.this.setDeltaMovement(
                    -Math.sin(yawRad) * forward, -sink, Math.cos(yawRad) * forward);
        }
    }

    // ───────────────────────────────────────────────────── LANDING ─────

    protected class LandingGoal extends Goal {

        /** Ticks since the landing began — drives the {@link #getMaxLandingTicks()} safety net. */
        protected int landingTicks;

        public LandingGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return SMOPFlyingAnimal.this.isLanding();
        }

        @Override
        public boolean canContinueToUse() {
            return SMOPFlyingAnimal.this.isLanding();
        }

        @Override
        public void start() {
            this.landingTicks = 0;
        }

        @Override
        public void tick() {
            this.landingTicks++;
            Vec3 motion = SMOPFlyingAnimal.this.getDeltaMovement();
            SMOPFlyingAnimal.this.setDeltaMovement(motion.x * 0.9D, this.computeDescentSpeed(), motion.z * 0.9D);

            if (this.landingTicks >= SMOPFlyingAnimal.this.getMaxLandingTicks() || this.shouldCompleteLanding()) {
                SMOPFlyingAnimal.this.completeLanding();
            }
        }

        private double computeDescentSpeed() {
            double distToGround = SMOPFlyingAnimal.this.getY() - SMOPFlyingAnimal.this.findGroundY(
                    SMOPFlyingAnimal.this.getX(), SMOPFlyingAnimal.this.getZ());
            return -Math.max(0.03D,
                    Math.min(SMOPFlyingAnimal.this.getLandingDescentSpeed(), distToGround * 0.06D));
        }

        /**
         * Default: physical ground contact, water contact (collision flags never fire on fluids, so
         * without this a water touchdown bobs in the landing state forever), or terrain closer than a
         * step — the flare's glide momentum can skim the mob across uneven ground and miss a tick of
         * the collision flags.
         *
         * <p>Override to hold the phase open until the landing clip finishes, but keep this as the
         * floor: the phase is capped by {@link #getMaxLandingTicks()} either way.
         */
        protected boolean shouldCompleteLanding() {
            if (SMOPFlyingAnimal.this.onGround() || SMOPFlyingAnimal.this.verticalCollision
                    || SMOPFlyingAnimal.this.isInWater()) {
                return true;
            }
            return SMOPFlyingAnimal.this.getY() - SMOPFlyingAnimal.this.findGroundY(
                    SMOPFlyingAnimal.this.getX(), SMOPFlyingAnimal.this.getZ()) < 0.1D;
        }
    }

    // ───────────────────────────────────────────────────── NBT ─────

    /**
     * The flight phases are entity data, which is not persisted — so without this a chunk reload
     * drops a mid-flight mob back into grounded logic while it hangs in the air, playing the ground
     * idle until the rest timer happens to force a take-off.
     */
    @Override
    protected void addAdditionalSaveData(@NotNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Flying", this.isFlying());
        output.putInt("FlightDuration", this.flightDurationTimer);
        output.putInt("MaxFlightTicks", this.maxFlightTicks);
        output.putInt("GroundRest", this.groundRestTimer);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.groundRestTimer = input.getIntOr("GroundRest", this.groundRestTimer);
        if (input.getBooleanOr("Flying", false) && !this.isBaby()) {
            // Resumed as plain cruising flight: the take-off and landing phases are deliberately not
            // restored. If the save caught a landing, the restored duration is already past its
            // limit, so the mob simply descends and lands again cleanly.
            this.switchToFlyNav();
            this.setFlying(true);
            this.setNoGravity(true);
            this.flightDurationTimer = input.getIntOr("FlightDuration", 0);
            this.maxFlightTicks = Math.max(1, input.getIntOr("MaxFlightTicks", this.computeMaxFlightTicks()));
        }
    }
}

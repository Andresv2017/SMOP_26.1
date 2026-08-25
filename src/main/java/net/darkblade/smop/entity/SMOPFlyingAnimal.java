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

public abstract class SMOPFlyingAnimal extends GenderedSMOPAnimal {

    private static final EntityDataAccessor<Boolean> FLYING =
            SynchedEntityData.defineId(SMOPFlyingAnimal.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TAKING_OFF =
            SynchedEntityData.defineId(SMOPFlyingAnimal.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> LANDING =
            SynchedEntityData.defineId(SMOPFlyingAnimal.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FLYING_MOVING =
            SynchedEntityData.defineId(SMOPFlyingAnimal.class, EntityDataSerializers.BOOLEAN);

    // ───────────────────────────────────────────────────── NAVIGATION ─────

    protected PathNavigation groundNavigation;
    protected PathNavigation flyingNavigation;
    private boolean usingGroundNav = true;

    // ───────────────────────────────────────────────────── VISUAL TILT ─────

    public float flightPitch;
    public float prevFlightPitch;
    public float flightRoll;
    public float prevFlightRoll;
    private float smoothedVerticalSpeed;
    private float clientYawRate;
    private float clientVerticalSpeed;
    private float clientHorizontalSpeed;

    // ───────────────────────────────────────────────────── ANIM HYSTERESIS ─────

    private double smoothedHorizontalSpeed;
    private int flyAnimHoldTicks;
    private boolean flyingMovingLocal;
    protected boolean seekingGround;
    private int seekGroundTicks;
    private boolean flightHovering;

    // ───────────────────────────────────────────────────── TIMERS ─────

    protected int groundRestTimer = 100;
    protected int flightDurationTimer;
    private int maxFlightTicks;

    protected SMOPFlyingAnimal(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        // Fliers path around water and lava rather than over them: a negative malus marks the node
        // as dangerous, not merely expensive.
        this.setPathfindingMalus(PathType.WATER, -8.0F);
        this.setPathfindingMalus(PathType.LAVA, -8.0F);
    }

    // ───────────────────────────────────────────────────── NAVIGATION ─────

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

    protected void onNavigationSwapped() {
    }

    // ───────────────────────────────────────────────────── STATE ─────

    public boolean isFlying() {
        return this.entityData.get(FLYING);
    }

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

    public boolean isFlyingMoving() {
        return this.entityData.get(FLYING_MOVING);
    }

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
        this.seekGroundTicks = 0;
        this.flightHovering = false;
        this.entityData.set(FLYING_MOVING, false);
    }

    public void requestTakeoff() {
        if (!this.isFlying() && !this.isTakingOff() && !this.isBaby()) {
            this.groundRestTimer = 0;
        }
    }

    public void requestLanding() {
        if (this.isFlying() && !this.isTakingOff() && !this.isLanding()) {
            this.flightDurationTimer = Math.max(this.flightDurationTimer, this.maxFlightTicks);
        }
    }

    public void delayTakeoff(int ticks) {
        if (!this.isFlying() && !this.isTakingOff()) {
            this.groundRestTimer = Math.max(this.groundRestTimer, ticks);
        }
    }

    // ───────────────────────────────────────────────────── ANIMATION HOOKS ─────

    protected void onTakeoffBegin() {
    }

    protected void onTakeoffComplete() {
    }

    protected void onSeekGroundBegin() {
    }

    protected void onLandingBegin() {
    }

    protected void onLandingComplete() {
    }

    // ───────────────────────────────────────────────────── TUNABLES ─────

    protected double getMinFlightAltitude() {
        return 8.0D;
    }

    protected double getMaxFlightAltitude() {
        return 30.0D;
    }

    protected double getWanderHorizontalRadius() {
        return 20.0D;
    }

    protected int computeGroundRestTicks() {
        return 80 + this.random.nextInt(80);
    }

    protected int computeMaxFlightTicks() {
        return 200 + this.random.nextInt(200);
    }

    protected double getLandingDescentSpeed() {
        return 0.08D;
    }

    protected double getTakeoffLiftSpeed() {
        return 0.10D;
    }

    protected double getLandingApproachAltitude() {
        return 4.0D;
    }

    protected double getDescentForwardSpeed() {
        return 0.25D;
    }

    @Nullable
    protected Vec3 getDescentTarget() {
        return null;
    }

    protected double getDescentArrivalRadius() {
        return 1.5D;
    }

    protected int getMaxSeekGroundTicks() {
        return 200;
    }

    protected float getFlarePitchUp() {
        return 12.0F;
    }

    protected boolean applyTiltDuringTakeoff() {
        return true;
    }

    protected int getMaxLandingTicks() {
        return 100;
    }

    protected int computeFlightHoverTicks() {
        return 30 + this.random.nextInt(50);
    }

    // ───────────────────────────────────────────────────── WANDER TARGET ─────

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

    @Override
    protected boolean isMovingNow() {
        return !this.isFlying() && super.isMovingNow();
    }

    @Override
    public boolean isMovementLocked() {
        return this.isFlying() ? this.isInSleepCycle() : super.isMovementLocked();
    }

    @Override
    protected SleepGoal<SMOPAnimal> createSleepGoal() {
        return new SleepGoal<SMOPAnimal>(this, this.sleepUrge()) {
            @Override
            public boolean canUse() {
                return !SMOPFlyingAnimal.this.isFlying() && super.canUse();
            }
        };
    }

    // ───────────────────────────────────────────────────── PHYSICS ─────

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, @NotNull DamageSource source) {
        return false;
    }

    @Override
    public void die(@NotNull DamageSource source) {
        super.die(source);
        if (!this.level().isClientSide() && this.isFlying()) {
            this.setNoGravity(false);
            this.getNavigation().stop();
        }
    }

    // ───────────────────────────────────────────────────── GOAL REGISTRATION ─────

    protected void registerFlightGoals(int takeoffPriority, int wanderPriority, int landPriority) {
        this.goalSelector.addGoal(takeoffPriority, this.createTakeoffGoal());
        this.goalSelector.addGoal(wanderPriority, new FlightWanderGoal());
        this.goalSelector.addGoal(landPriority, this.createLandingGoal());
    }

    protected TakeoffGoal createTakeoffGoal() {
        return new TakeoffGoal();
    }

    protected LandingGoal createLandingGoal() {
        return new LandingGoal();
    }

    // ───────────────────────────────────────────────────── TAKEOFF ─────

    protected class TakeoffGoal extends Goal {

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

        protected boolean shouldCompleteTakeoff() {
            return true;
        }
    }

    // ───────────────────────────────────────────────────── WANDER ─────

    protected class FlightWanderGoal extends Goal {

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
                SMOPFlyingAnimal.this.seekGroundTicks = 0;
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

        private void tickSeekGround() {
            double groundY = SMOPFlyingAnimal.this.findGroundY(
                    SMOPFlyingAnimal.this.getX(), SMOPFlyingAnimal.this.getZ());
            double distToGround = SMOPFlyingAnimal.this.getY() - groundY;

            // An aimed stoop, when the mob is coming down for something — see getDescentTarget().
            // aiming stays false for the ordinary case so nothing below changes shape for it.
            Vec3 aim = SMOPFlyingAnimal.this.getDescentTarget();
            boolean aiming = aim != null
                    && SMOPFlyingAnimal.this.seekGroundTicks++ < SMOPFlyingAnimal.this.getMaxSeekGroundTicks();
            boolean overhead = false;
            if (aiming) {
                double dx = aim.x - SMOPFlyingAnimal.this.getX();
                double dz = aim.z - SMOPFlyingAnimal.this.getZ();
                double radius = SMOPFlyingAnimal.this.getDescentArrivalRadius();
                overhead = dx * dx + dz * dz <= radius * radius;
                if (!overhead) {
                    SMOPFlyingAnimal.this.faceHeading(dx, dz,
                            SMOPFlyingAnimal.this.getFlightYawTurnSpeed());
                }
            }

            if ((!aiming || overhead) && distToGround <= SMOPFlyingAnimal.this.getLandingApproachAltitude()) {
                SMOPFlyingAnimal.this.seekingGround = false;
                SMOPFlyingAnimal.this.beginLanding();
                return;
            }

            double forward = SMOPFlyingAnimal.this.getDescentForwardSpeed();
            double maxSink = SMOPFlyingAnimal.this.getLandingDescentSpeed() * 2.0D;
            double sink = Mth.clamp(distToGround * 0.03D,
                    SMOPFlyingAnimal.this.getLandingDescentSpeed(), maxSink);
            if (aiming) {
                if (overhead) {
                    // On the spot — drop straight down instead of running on past it.
                    forward = 0.0D;
                } else if (distToGround <= SMOPFlyingAnimal.this.getLandingApproachAltitude()) {
                    // Down to hand-over height with ground still to cover: hold this altitude and
                    // keep closing rather than touching down short of the target.
                    sink = 0.0D;
                }
            }
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

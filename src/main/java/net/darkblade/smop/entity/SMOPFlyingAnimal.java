package net.darkblade.smop.entity;

import net.darkblade.deluxelib.entity.ai.navigation.SmartFlyingNavigation;
import net.darkblade.deluxelib.entity.ai.pathing.SmoothFlyingMoveControl;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Shared base for SMOP's fliers: a mob that walks and flies, swapping its navigation and move
 * control between the two rather than being one or the other for life.
 *
 * <p><b>Why not DeluxeLib's {@code AbstractFlyingEntity}.</b> That class is
 * {@code PathfinderMob implements Enemy} — no taming, and flagged hostile. SMOP's fliers are pets:
 * they have an owner, sit and follow, and hatch from eggs, so they descend from {@code TamableAnimal}
 * and Java's single inheritance rules the library base out. What the library does provide standalone,
 * and what is reused here, is {@link SmartFlyingNavigation} and {@link SmoothFlyingMoveControl} —
 * both take a plain {@code Mob}. Between them they replace 1.20.1's hand-written
 * {@code NewFlyingPathNavigation}, whose {@code followThePath} override was solving the same
 * node-acceptance problem.
 *
 * <p><b>Port note.</b> The old version pushed a bespoke {@code StoCSyncFlying} packet on every
 * switch to tell clients about the mode change. That packet is gone: {@link #FLYING} is synced entity
 * data, which the client already receives, so the state travels for free and the play conditions can
 * read it on both sides.
 *
 * <p>Babies never fly. That is enforced in {@link #setFlying} rather than at each call site, so no
 * goal or hook can put a chick in the air by accident.
 */
public abstract class SMOPFlyingAnimal extends GenderedSMOPAnimal {

    /** Whether the mob is airborne right now — drives navigation, gravity and the flight clips. */
    private static final EntityDataAccessor<Boolean> FLYING =
            SynchedEntityData.defineId(SMOPFlyingAnimal.class, EntityDataSerializers.BOOLEAN);
    /** What the AI <em>wants</em>: goals set this, {@link #handleAutoNavigationSwitch()} obeys it. */
    private static final EntityDataAccessor<Boolean> WANTS_TO_FLY =
            SynchedEntityData.defineId(SMOPFlyingAnimal.class, EntityDataSerializers.BOOLEAN);
    /** Airborne and actually travelling, held so the glide/flap clips do not strobe. */
    private static final EntityDataAccessor<Boolean> FLYING_MOVING =
            SynchedEntityData.defineId(SMOPFlyingAnimal.class, EntityDataSerializers.BOOLEAN);

    /** Above this horizontal speed the travelling flight clip takes over from the hover. */
    private static final double FLIGHT_MOVE_THRESHOLD_SQR = 0.05D;

    private final MoveHold flyingMoveHold = new MoveHold();

    private int groundTicks;
    private int groundedWhileFlyingTicks;

    protected SMOPFlyingAnimal(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        // Fliers path around water and lava rather than over them: a negative malus marks the node
        // as dangerous, not merely expensive.
        this.setPathfindingMalus(PathType.WATER, -8.0F);
        this.setPathfindingMalus(PathType.LAVA, -8.0F);
    }

    /** Starts on the ground; {@link #switchNavigation()} swaps in the flying pair. */
    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull Level level) {
        return new GroundPathNavigation(this, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FLYING, false);
        builder.define(WANTS_TO_FLY, false);
        builder.define(FLYING_MOVING, false);
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

    public boolean wantsToFly() {
        return this.entityData.get(WANTS_TO_FLY);
    }

    public void setWantsToFly(boolean wants) {
        this.entityData.set(WANTS_TO_FLY, wants);
    }

    /** Synced and held — safe to read from a play condition on either side. */
    public boolean isFlyingMoving() {
        return this.entityData.get(FLYING_MOVING);
    }

    // ───────────────────────────────────────────────────── MODE SWITCH ─────

    /**
     * Flips between the ground pair (vanilla ground navigation + {@code MoveControl}) and the air
     * pair ({@link SmartFlyingNavigation} + {@link SmoothFlyingMoveControl}), and takes gravity with
     * it. Taking off also fires the jump control, so the mob leaves the ground instead of sliding
     * along it until the flying control gains altitude.
     */
    public void switchNavigation() {
        if (this.isFlying()) {
            this.moveControl = new MoveControl(this);
            this.navigation = new GroundPathNavigation(this, this.level());
            this.setFlying(false);
        } else {
            if (this.isBaby()) {
                return;
            }
            this.moveControl = new SmoothFlyingMoveControl(this, 20, false, 10.0F);
            this.navigation = new SmartFlyingNavigation(this, this.level());
            this.navigation.setCanFloat(true);
            this.jumpControl.jump();
            this.setFlying(true);
        }
        this.setNoGravity(this.isFlying());
    }

    /** True when there is something solid directly underfoot and the mob is not rising. */
    protected boolean isTouchingSolidGround() {
        BlockPos below = this.blockPosition().below();
        return !this.level().getBlockState(below).isAir() && this.getDeltaMovement().y <= 0.0D;
    }

    /** Ticks on the ground before the mob decides to take off again. */
    protected int maxGroundTicks() {
        return 80;
    }

    /** Ticks touching ground while nominally flying before the mob admits it has landed. */
    protected int maxGroundedTicksWhileFlying() {
        return 10;
    }

    /**
     * Keeps the movement mode honest: a flier resting on the ground eventually takes off again, and
     * one that has come to rest while "flying" is switched back to walking. Subclasses that pin the
     * mob (perched, ridden) should skip calling this while pinned.
     */
    protected void handleAutoNavigationSwitch() {
        if (this.isBaby()) {
            // A chick that somehow ended up airborne — grown down by a command, loaded from an old
            // save — is put back on its feet rather than left hovering.
            if (this.isFlying()) {
                this.setFlying(false);
            }
            if (!(this.navigation instanceof GroundPathNavigation)) {
                this.moveControl = new MoveControl(this);
                this.navigation = new GroundPathNavigation(this, this.level());
                this.setNoGravity(false);
            }
            return;
        }

        if (this.isFlying()) {
            this.groundTicks = 0;
            if (this.isTouchingSolidGround()) {
                if (++this.groundedWhileFlyingTicks >= this.maxGroundedTicksWhileFlying()) {
                    this.switchNavigation();
                    this.resetSwitchTimers();
                }
            } else {
                this.groundedWhileFlyingTicks = 0;
            }
            return;
        }

        this.groundedWhileFlyingTicks = 0;
        if (++this.groundTicks >= this.maxGroundTicks()) {
            this.switchNavigation();
            this.resetSwitchTimers();
        }
    }

    private void resetSwitchTimers() {
        this.groundTicks = 0;
        this.groundedWhileFlyingTicks = 0;
    }

    // ───────────────────────────────────────────────────── MOVEMENT ─────

    @Override
    public void travel(@NotNull Vec3 input) {
        // 26.1 renamed isControlledByLocalInstance to isLocalInstanceAuthoritative: only the side
        // that owns this entity's physics integrates the movement, or client and server fight.
        if (!this.isFlying() || !this.isLocalInstanceAuthoritative()) {
            super.travel(input);
            return;
        }
        if (this.isMovementLocked()) {
            this.getNavigation().stop();
            input = Vec3.ZERO;
        }
        // Drag depends on the medium: air keeps most of the momentum, water and lava eat it.
        float acceleration = this.isInWater() || this.isInLava() ? 0.02F : this.getSpeed();
        double drag = this.isInLava() ? 0.5D : this.isInWater() ? 0.8D : 0.91D;
        this.moveRelative(acceleration, input);
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(drag));
    }

    @Override
    public boolean isNoGravity() {
        return !this.isBaby() && this.isFlying();
    }

    /** A flier is never hurt by the ground it chose to land on. */
    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, @NotNull DamageSource source) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        this.entityData.set(FLYING_MOVING, this.flyingMoveHold.tick(this.isFlying()
                && this.getDeltaMovement().horizontalDistanceSqr() > FLIGHT_MOVE_THRESHOLD_SQR));
    }

    // ───────────────────────────────────────────────────── NBT ─────

    @Override
    protected void addAdditionalSaveData(@NotNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Flying", this.isFlying());
        output.putBoolean("WantsToFly", this.wantsToFly());
    }

    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setFlying(input.getBooleanOr("Flying", false));
        this.setWantsToFly(input.getBooleanOr("WantsToFly", false));
        // The navigation pair is not saved — rebuild it from the restored flag, or a mob loaded
        // airborne would fall with ground navigation attached.
        if (this.isFlying()) {
            this.moveControl = new SmoothFlyingMoveControl(this, 20, false, 10.0F);
            this.navigation = new SmartFlyingNavigation(this, this.level());
            this.navigation.setCanFloat(true);
            this.setNoGravity(true);
        }
    }
}

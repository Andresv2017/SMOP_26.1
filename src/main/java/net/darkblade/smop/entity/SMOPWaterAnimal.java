package net.darkblade.smop.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.darkblade.smop.entity.ai.navigation.SmartSwimmingNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SMOPWaterAnimal extends GenderedSMOPAnimal {

    private static final EntityDataAccessor<Boolean> SWIMMING_FAST =
            SynchedEntityData.defineId(SMOPWaterAnimal.class, EntityDataSerializers.BOOLEAN);

    private final MoveHold fastHold = new MoveHold();

    private int outOfWaterMaxTicks = 100;
    private float outOfWaterDamage = 2.0F;
    private int outOfWaterTicks;

    protected SMOPWaterAnimal(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        // Water costs nothing to path through — without this the navigator treats it as a hazard
        // and a fish will not swim anywhere.
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
        this.getNavigation().setCanFloat(true);
    }

    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull Level level) {
        return new SmartSwimmingNavigation(this, level);
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    protected float getWaterSlowDown() {
        return 1.0F;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean checkSpawnObstruction(@NotNull LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return !this.isTame() && !this.hasCustomName();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SWIMMING_FAST, false);
    }

    // ───────────────────────────────────────────────────── TUNING HOOKS ─────

    protected boolean shouldTakeDryDamage() {
        return true;
    }

    protected boolean shouldFlopOnLand() {
        return true;
    }

    protected double getSwimSpeedThreshold() {
        return 0.0D;
    }

    protected SoundEvent getFlopSound() {
        return SoundEvents.SALMON_FLOP;
    }

    // ───────────────────────────────────────────────────── MOVEMENT ─────

    @Override
    public void travel(@NotNull Vec3 input) {
        if (this.isEffectiveAi() && this.isInWater()) {
            // The pinned states (asleep, roaring) have to be honoured here too: this override
            // replaces SMOPAnimal#travel entirely while in water, so its lock would never be
            // consulted. Cutting the input rather than the velocity lets the fish coast to a stop
            // and hang in the water, instead of stopping dead like a land mob planted on the ground.
            if (this.isMovementLocked()) {
                this.getNavigation().stop();
                input = Vec3.ZERO;
            }
            this.moveRelative(this.getSpeed(), input);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
            if (this.getTarget() == null) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.005D, 0.0D));
            }
            return;
        }
        super.travel(input);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.shouldFlopOnLand() && !this.isInWater() && this.onGround() && this.verticalCollision) {
            this.setDeltaMovement(this.getDeltaMovement().add(
                    (this.random.nextFloat() * 2.0F - 1.0F) * 0.05F,
                    0.4F,
                    (this.random.nextFloat() * 2.0F - 1.0F) * 0.05F));
            this.setOnGround(false);
            // 26.1: Entity#hasImpulse was removed; hurtMarked is what forces the velocity to be
            // sent to watching clients, so the hop is seen rather than just simulated.
            this.hurtMarked = true;
            this.playSound(this.getFlopSound(), this.getSoundVolume(), this.getVoicePitch());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        this.entityData.set(SWIMMING_FAST, this.fastHold.tick(this.shouldSwimFast()));
        this.tickDryOut();
    }

    private void tickDryOut() {
        if (!this.shouldTakeDryDamage()) {
            return;
        }
        // Rain and bubble columns count as wet, same as vanilla's own fish.
        if (this.isInWaterOrRain()) {
            this.outOfWaterTicks = 0;
            return;
        }
        if (++this.outOfWaterTicks > this.outOfWaterMaxTicks) {
            this.hurt(this.damageSources().dryOut(), this.outOfWaterDamage);
        }
    }

    protected boolean shouldSwimFast() {
        return this.getDeltaMovement().horizontalDistanceSqr()
                > this.getSwimSpeedThreshold() * this.getSwimSpeedThreshold();
    }

    public boolean isSwimmingFast() {
        return this.entityData.get(SWIMMING_FAST);
    }

    public boolean isSwimmingCruise() {
        return this.isMoving() && !this.isSwimmingFast();
    }

    // ───────────────────────────────────────────────────── DRY-OUT ACCESSORS ─────

    public int getOutOfWaterMaxTicks() {
        return this.outOfWaterMaxTicks;
    }

    public void setOutOfWaterMaxTicks(int ticks) {
        this.outOfWaterMaxTicks = ticks;
    }

    public float getOutOfWaterDamage() {
        return this.outOfWaterDamage;
    }

    public void setOutOfWaterDamage(float damage) {
        this.outOfWaterDamage = damage;
    }

    // ───────────────────────────────────────────────────── EGGS ─────

    private static final int NEST_SEARCH_DEPTH = 6;

    protected boolean nestsAshore() {
        return false;
    }

    @Override
    public @Nullable BlockPos tryLayEgg(@NotNull Block eggBlock) {
        // Straight back to the land algorithm, hooks and all, for the species that nest ashore.
        if (this.nestsAshore()) {
            return super.tryLayEgg(eggBlock);
        }
        if (!this.hasEgg() || this.isMammal()) {
            return null;
        }

        BlockPos nest = this.findNestSite();
        if (nest == null) {
            return null;
        }

        Level level = this.level();
        // Laid INSIDE a water source, so the block has to be told it is under water — otherwise it
        // displaces the source and leaves an air pocket on the sea bed. A block that does not know
        // about waterlogging goes down as-is; that is the caller's choice of block, not our problem.
        BlockState egg = eggBlock.defaultBlockState();
        if (egg.hasProperty(BlockStateProperties.WATERLOGGED)) {
            egg = egg.setValue(BlockStateProperties.WATERLOGGED, true);
        }
        level.setBlock(nest, egg, Block.UPDATE_ALL);
        level.playSound(null, nest, SoundEvents.TURTLE_LAY_EGG, SoundSource.BLOCKS, 1.0F, 1.0F);
        this.setHasEgg(false);
        return nest;
    }

    @Nullable
    private BlockPos findNestSite() {
        Level level = this.level();
        BlockPos.MutableBlockPos cursor = this.blockPosition().mutable();

        for (int i = 0; i <= NEST_SEARCH_DEPTH; i++) {
            FluidState fluid = level.getFluidState(cursor);
            boolean waterSource = fluid.is(FluidTags.WATER) && fluid.getAmount() == 8;
            boolean replaceable = level.getBlockState(cursor).canBeReplaced();
            BlockPos below = cursor.below();
            boolean solidBelow = level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);

            if (waterSource && replaceable && solidBelow) {
                return cursor.immutable();
            }
            // Stop at the first thing that is not open water — a bed we cannot lay on, or air above
            // the surface. Carrying on past it would look for a nest inside solid ground.
            if (!waterSource || !replaceable) {
                return null;
            }
            cursor.move(Direction.DOWN);
        }
        return null;
    }
}

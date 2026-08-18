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

/**
 * Shared base for SMOP's swimmers: water-bound navigation, drowning-in-air damage, the flop on
 * land, and eggs that only go down in water.
 *
 * <p><b>Port note — the animation half of the old {@code WaterEntity} is gone.</b> Roughly half of
 * that class was {@code AnimationState} fields ({@code swimAnimationState},
 * {@code floopingAnimationState}, {@code waterDeathAnimationState}, …), their getters, and a
 * ~70-line {@code updateAquaticAnimations()} cascade of {@code start()}/{@code stop()} calls. All of
 * it is replaced by state exposed here and {@code setPlayCondition} in each mob's
 * {@code registerAnimations()} — the same trade {@code BaseEntity} → {@link SMOPAnimal} made.
 *
 * <p>What that state needs to be is the reason {@link #SWIMMING_FAST} is synced rather than sampled:
 * play conditions run on both sides and {@code getDeltaMovement()} is not synced for mobs, so a
 * direct speed read would put the client on a different clip than the server every few ticks. It
 * rides the same hold timer as {@link SMOPAnimal#isMoving()} so the cruise/sprint clips do not
 * strobe across the threshold.
 */
public abstract class SMOPWaterAnimal extends GenderedSMOPAnimal {

    /** Swimming above {@link #getSwimSpeedThreshold()}, synced and held — see the class note. */
    private static final EntityDataAccessor<Boolean> SWIMMING_FAST =
            SynchedEntityData.defineId(SMOPWaterAnimal.class, EntityDataSerializers.BOOLEAN);

    private final MoveHold fastHold = new MoveHold();

    /** Ticks out of water before it starts hurting, and how much per tick after that. */
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

    /**
     * Drops the "no liquid in the bounding box" half of the vanilla check.
     *
     * <p><b>Without this no SMOP swimmer can spawn naturally underwater at all</b>, which is a silent
     * hole: {@code Mob#checkSpawnObstruction} is {@code !level.containsAnyLiquid(getBoundingBox()) &&
     * level.isUnobstructed(this)}, written for land mobs, and it is reached on every natural spawn —
     * {@code NaturalSpawner#isValidPositionForMob} calls NeoForge's {@code EventHooks.checkSpawnPosition},
     * which on the default result runs {@code checkSpawnRules() && checkSpawnObstruction()}. A position
     * in open water fills the box with water and is refused every single time.
     *
     * <p>Easy to miss because vanilla's {@code NaturalSpawner} never mentions the method: searching the
     * Minecraft sources for callers turns up only the trial spawner, and the natural-spawn call site
     * lives in NeoForge. Every vanilla water mob overrides this the same way — {@code WaterAnimal},
     * {@code AgeableWaterCreature}, {@code AbstractNautilus}, {@code Axolotl}, {@code Guardian},
     * {@code Drowned} — and this base extends {@code GenderedSMOPAnimal} rather than any of them, so it
     * inherited the land version and nobody noticed until a mob was expected to appear in open sea.
     *
     * <p>The salmon was affected too, in rivers, for as long as it has existed.
     */
    @Override
    public boolean checkSpawnObstruction(@NotNull LevelReader level) {
        return level.isUnobstructed(this);
    }

    /**
     * Wild swimmers despawn, which {@code Animal} does not do — its {@code removeWhenFarAway} returns a
     * flat {@code false}, so every SMOP animal is immortal from the moment it spawns.
     *
     * <p>That default is what broke ocean spawning outright, and the measurements are worth keeping
     * because the shape recurs. {@code NaturalSpawner#createState} charges every non-persistent mob to
     * its category's global budget — and it counts {@code level.getAllEntities()}, the WHOLE level, not
     * the player's surroundings. Meanwhile world generation seeds with no budget check at all
     * ({@code spawnOriginalMobs} never consults {@code canSpawnForCategoryGlobal}). An uncapped source
     * feeding a population that never shrinks is a ratchet: measured live, the CREATURE count ran three
     * to eight times over its cap and never recovered, at which point
     * {@code getFilteredSpawningCategories} drops the whole category and nothing in it spawns anywhere.
     *
     * <p>Every vanilla sea creature already behaves this way — squid, dolphins, the fish and the
     * nautilus all inherit {@code Mob}'s {@code true} and cycle out, which is exactly why those water
     * categories sit at their cap without ever jamming. This base extends {@link GenderedSMOPAnimal}
     * rather than any of them, so it inherited the land version.
     *
     * <p>The two guards are the whole point. Persistence — bucket, spawn egg, {@code /summon} — is
     * already handled a level up in {@code Mob#checkDespawn}, which never reaches this method for a mob
     * that {@code isPersistenceRequired()}. What that does NOT cover is taming:
     * {@code TamableAnimal#tame} sets an owner but not persistence, so without {@code isTame()} here a
     * tamed mount would vanish the moment its rider walked 128 blocks away. The custom-name guard is
     * the same courtesy vanilla extends to a name-tagged animal.
     *
     * <p><b>Deliberately on this base and not on {@link SMOPAnimal}.</b> The land animals inherit the
     * same immortality and the same ratchet — it is visible in any spawn debug session as the CREATURE
     * count climbing into the hundreds — but "wild animals persist" is vanilla's own convention for
     * livestock and players build around it. Making a cow-shaped mob evaporate is a design decision;
     * making a wild fish do it is matching the fish next to it.
     */
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

    /** Whether being out of water hurts. Amphibians turn this off. */
    protected boolean shouldTakeDryDamage() {
        return true;
    }

    /** Whether the mob flails when beached. Anything that can walk turns this off. */
    protected boolean shouldFlopOnLand() {
        return true;
    }

    /** Speed (blocks/tick) above which the sprint clip takes over from the cruise clip. */
    protected double getSwimSpeedThreshold() {
        return 0.0D;
    }

    protected SoundEvent getFlopSound() {
        return SoundEvents.SALMON_FLOP;
    }

    // ───────────────────────────────────────────────────── MOVEMENT ─────

    /**
     * In water the mob swims under its own power instead of falling: {@code moveRelative} + a drag
     * scale, with a slow sink when it has nothing to chase so it does not hover at one depth
     * forever. Out of water it falls back to vanilla, which is what makes a beached fish drop.
     */
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

    /** The flop: a beached mob hops, which is both the sound cue and how it can flail back in. */
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

    /**
     * Whether the sprint clip should be running, sampled once a tick on the server and synced.
     *
     * <p>The default is a speed test, and on a fast swimmer that is the wrong question. A cruising
     * animal is already past any threshold worth setting, so the sprint clip becomes the only one ever
     * seen and the cruise clip is dead art; set it higher and it never fires at all. There is no value
     * that works, because speed is not what a sprint <em>means</em> — a sprint is the animal deciding
     * to go somewhere in a hurry, which is a fact about its goals.
     *
     * <p>Override with that fact. Reading {@code getTarget()} here is safe and is the usual answer,
     * even though the target is not synced: this runs on the server and the RESULT is what crosses to
     * the client, in {@link #SWIMMING_FAST}. That is the whole reason the flag is synced rather than
     * sampled in the play condition, and it means a goal-driven sprint costs nothing extra to set up.
     */
    protected boolean shouldSwimFast() {
        return this.getDeltaMovement().horizontalDistanceSqr()
                > this.getSwimSpeedThreshold() * this.getSwimSpeedThreshold();
    }

    /** Synced — safe to read from a play condition on either side. */
    public boolean isSwimmingFast() {
        return this.entityData.get(SWIMMING_FAST);
    }

    /** Moving, but below the sprint threshold. The two are mutually exclusive by construction. */
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

    /**
     * Spawns only in a full water source with something solid underneath, so roe cannot be laid in
     * a puddle, in flowing water, or floating in midwater.
     *
     * <p>Overrides {@link SMOPAnimal#tryLayEgg} wholesale rather than just its two placement hooks
     * because a land mob tests "air above a solid block" — the exact opposite condition.
     */
    /** How far below itself a swimmer will look for a bed to lay on. */
    private static final int NEST_SEARCH_DEPTH = 6;

    /**
     * Whether this swimmer nests out of the water, the way a sea turtle does.
     *
     * <p>Being aquatic and spawning underwater are not the same fact, and the salmon happens to do
     * both — which is why this base assumed it. A marine reptile hauls out to lay: it needs the LAND
     * placement rules, air above a solid block, which is exactly what {@link SMOPAnimal#tryLayEgg}
     * already implements and this class overrides away.
     */
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

    /**
     * The first spot from the mob's own block downwards that is a full water source sitting on
     * something solid.
     *
     * <p>It searches downwards rather than testing only {@code blockPosition()} because a swimmer is
     * almost never standing on the bed — it hovers in midwater, where the block below is more water
     * and the single-position test can only ever fail. That is what left a gravid fish carrying its
     * clutch forever: the lay goal fired on schedule, found nowhere legal at its own depth, and gave
     * up without the mob ever having reason to descend.
     */
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

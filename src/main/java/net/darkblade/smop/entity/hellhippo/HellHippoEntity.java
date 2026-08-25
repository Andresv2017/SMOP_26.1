package net.darkblade.smop.entity.hellhippo;

import net.darkblade.deluxelib.anim.AnimSource;
import net.darkblade.deluxelib.combat.AttackShape;
import net.darkblade.deluxelib.combat.HitWindow;
import net.darkblade.deluxelib.anim.Loop;
import net.darkblade.deluxelib.anim.StandardAnimation;
import net.darkblade.deluxelib.entity.ai.goal.AnimatableMeleeAttackGoal;
import net.darkblade.deluxelib.entity.ai.pathing.DirectionalMoveControl;
import net.darkblade.deluxelib.entity.ai.rotation.SmoothBodyRotationControl;
import net.darkblade.smop.client.hellhippo.HellHippoAnimations;
import net.darkblade.smop.client.hellhippo.HellHippoBabyAnimations;
import net.darkblade.smop.entity.GenderedSMOPAnimal;
import net.darkblade.smop.entity.SMOPEntities;
import net.darkblade.smop.entity.ai.goal.IdleAnimationGoal;
import net.darkblade.smop.entity.ai.goal.SMOPFollowParentGoal;
import net.darkblade.smop.entity.ai.goal.SMOPRandomStrollGoal;
import net.darkblade.smop.effect.SMOPEffects;
import net.darkblade.smop.entity.RiderControllable;
import net.darkblade.smop.entity.rider.RiderAbility;
import net.darkblade.smop.entity.rider.RiderSteering;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.Vec2;
import net.darkblade.smop.entity.sleep.ISleepThreatEvaluator;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.darkblade.smop.entity.ai.goal.LeaveWaterShakeGoal;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.darkblade.smop.entity.ai.navigation.SeabedPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Supplier;

public class HellHippoEntity extends GenderedSMOPAnimal
        implements ISleepThreatEvaluator, HasCustomInventoryScreen, RiderControllable {

    private static final Ingredient FOOD_ITEMS = Ingredient.of(Items.CARROT, Items.BEEF);

    private static final int TRUST_CHANCE_DENOMINATOR = 3;

    private static final String ANIM_INTIMIDATE_IN = "intimidate_in";
    private static final String ANIM_INTIMIDATE_LOOP = "intimidate_loop";
    private static final String ANIM_INTIMIDATE_OUT = "intimidate_out";

    private static final int INTIMIDATION_TICKS = 300;
    private static final double INTIMIDATION_RADIUS = 10.0D;
    private static final double STARE_DOT = 0.95D;
    private static final int STARE_TICKS_TO_FEAR = 100;
    private static final int FEAR_DURATION_TICKS = 300;

    private static final EntityDataAccessor<Boolean> INTIMIDATING =
            SynchedEntityData.defineId(HellHippoEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Boolean> CHEST =
            SynchedEntityData.defineId(HellHippoEntity.class, EntityDataSerializers.BOOLEAN);

    private int intimidationTicks;
    private int staringTicks;

    private boolean standoff;

    private static final TargetingConditions.Selector PREY_SELECTOR = (target, level) ->
            target.getType() == EntityType.SHEEP
                    || target.getType() == EntityType.GOAT
                    || target.getType() == EntityType.COW;

    private static final float ATTACK_SECONDS = 0.7F;
    private static final int ATTACK_WINDOW_START = 7;
    private static final int ATTACK_WINDOW_END = 12;

    private static final float FACE_LOCK_RADIUS = 6.0F;

    private static final float CALF_COMPANION_CHANCE = 0.50F;

    private static final double FOLLOW_PARENT_DISTANCE = 5.0D;

    private static final String ANIM_ATTACK = "attack";

    private static final String ANIM_SHAKE = "shake";
    private static final String ANIM_EAT = "eat";

    private static final int SHAKE_COOLDOWN_TICKS = 900;
    private static final int SHAKE_COOLDOWN_SPREAD_TICKS = 900;

    private static final int SOAKED_TICKS = 100;
    private static final float SWIM_DEPTH_FRACTION = 0.5F;
    private static final int SEAWEED_GROWTH_TICKS = 200;
    private static final int SEAWEED_SHEAR_BLOCK_TICKS = 100;

    private static final double SINK_ACCELERATION = 0.03D;
    private static final double SWIM_CLIMB_GAIN = 0.1D;

    private static final EntityDataAccessor<Boolean> SEAWEED =
            SynchedEntityData.defineId(HellHippoEntity.class, EntityDataSerializers.BOOLEAN);

    private int seaweedTicks;

    public HellHippoEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        this.moveControl = new DirectionalMoveControl<>(this).setTurnSpeed(6.0F).setCombatTurnSpeed(35.0F);
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
    }

    @Override
    protected @NotNull BodyRotationControl createBodyControl() {
        return new SmoothBodyRotationControl<>(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.FOLLOW_RANGE, 28.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.250D)
                .add(Attributes.ATTACK_SPEED, 0.250D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.5D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.1D)
                .add(Attributes.ARMOR)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, this.createSleepGoal());
        this.goalSelector.addGoal(2, new AnimatableMeleeAttackGoal(this, 1.3D, true)
                .reach(3.5F)
                .stopDistance(3.0F)
                .cooldown(18)
                .onAttack((target, animator) -> animator.play(animator.getByName(ANIM_ATTACK))));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.15D));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.2D, FOOD_ITEMS, false));
        this.goalSelector.addGoal(4, new LeaveWaterShakeGoal(this, ANIM_SHAKE, SOAKED_TICKS));
        this.goalSelector.addGoal(5, new SMOPFollowParentGoal(this, 1.1D, FOLLOW_PARENT_DISTANCE));
        this.goalSelector.addGoal(6, new SMOPRandomStrollGoal(this, 1.0D, 120,
                () -> !this.isMovementLocked()));
        this.goalSelector.addGoal(8, new IdleAnimationGoal(this, SHAKE_COOLDOWN_TICKS, SHAKE_COOLDOWN_SPREAD_TICKS)

                .add(ANIM_EAT, 3)
                .add(ANIM_SHAKE, 1)
                .condition(animal -> !animal.isInWater()));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return !HellHippoEntity.this.isInSleepCycle() && super.canUse();
            }
        }.setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<Player>(this, Player.class, true) {
            @Override
            public boolean canUse() {
                return HellHippoEntity.this.picksItsOwnFights() && super.canUse();
            }
        });
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<Animal>(this, Animal.class, false, PREY_SELECTOR) {
            @Override
            public boolean canUse() {
                return HellHippoEntity.this.picksItsOwnFights() && super.canUse();
            }
        });
    }

    private boolean picksItsOwnFights() {
        return !this.isSaddled() && !this.isBaby() && !this.isInSleepCycle();
    }

    // ───────────────────────────────────────────────────── COMBAT ─────

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            this.faceCombatTarget();
            this.tickSeaweed();
            this.tickWeaknessSleep();
            this.tickIntimidation();
            this.tickRiddenState();
            Player rider = RiderAbility.controllerOf(this);
            this.fearPulse.tick(rider);
            this.mountedAttack.tick(rider);
        }
    }

    // ───────────────────────────────────────────────────── THE SADDLING WINDOW ─────

    private void tickWeaknessSleep() {
        boolean weakened = this.hasEffect(MobEffects.WEAKNESS);
        if (weakened && !this.isInSleepCycle() && !this.isSaddled()) {
            this.sleepUrge().forceSleep(true);
            return;
        }
        if (this.sleepUrge().isForced() && (!weakened || this.isSaddled())) {
            this.sleepUrge().forceSleep(false);
            this.sleepUrge().requestWake();
        }
    }

    // ───────────────────────────────────────────────────── INTIMIDATION ─────

    private void tickIntimidation() {
        if (this.isIntimidating()) {
            if (this.isInSleepCycle()) {
                return;
            }
            if (--this.intimidationTicks <= 0) {
                boolean wasStandoff = this.standoff;
                this.stopIntimidating();
                if (wasStandoff) {
                    this.forgetTrust();
                }
                return;
            }
            if (this.standoff && this.getOwner() instanceof Player watcher) {
                this.faceIntimidationTarget(watcher);
                this.tickStare(watcher);
            }
            return;
        }
        if (!this.isTame() || this.isSaddled()) {
            return;
        }
        if (this.getOwner() instanceof Player player
                && this.distanceTo(player) < INTIMIDATION_RADIUS
                && this.hasLineOfSight(player)) {
            this.startIntimidating(INTIMIDATION_TICKS, true);
            this.playSound(SoundEvents.HOGLIN_ANGRY, 1.0F, 0.7F);
        }
    }

    private void tickStare(Player player) {
        Vec3 toHippo = this.position().subtract(player.position()).normalize();
        if (player.getLookAngle().normalize().dot(toHippo) <= STARE_DOT) {
            this.staringTicks = 0;
            return;
        }
        if (++this.staringTicks < STARE_TICKS_TO_FEAR) {
            return;
        }
        this.staringTicks = 0;
        if (!player.hasEffect(SMOPEffects.FEAR)) {
            player.addEffect(new MobEffectInstance(SMOPEffects.FEAR, FEAR_DURATION_TICKS, 0));
        }
    }

    private void faceIntimidationTarget(Player player) {
        if (this.moveControl instanceof DirectionalMoveControl<?> control) {
            control.faceTarget(player);
        }
    }

    @Override
    public boolean isMovementLocked() {
        return super.isMovementLocked() || this.isIntimidating();
    }


    private void startIntimidating(int ticks, boolean standoff) {
        this.entityData.set(INTIMIDATING, true);
        this.intimidationTicks = ticks;
        this.staringTicks = 0;
        this.standoff = standoff;
        this.startAction(ANIM_INTIMIDATE_IN);
    }

    private void stopIntimidating() {
        if (this.isIntimidating()) {
            this.entityData.set(INTIMIDATING, false);
            if (!this.isInSleepCycle()) {
                this.startAction(ANIM_INTIMIDATE_OUT);
            }
        }
        this.intimidationTicks = 0;
        this.staringTicks = 0;
    }

    private void forgetTrust() {
        this.stopIntimidating();
        this.setTame(false, true);
        this.setOwnerReference(null);
        this.level().broadcastEntityEvent(this, (byte) 6);
    }

    public boolean isIntimidating() {
        return this.entityData.get(INTIMIDATING);
    }

    // ───────────────────────────────────────────────────── WATER ─────

    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull Level level) {
        return new SeabedPathNavigation(this, level);
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public double getFluidJumpThreshold() {
        return Double.MAX_VALUE;
    }

    @Override
    public void travel(@NotNull Vec3 travelVector) {
        super.travel(travelVector);
        if (!this.isEffectiveAi() || !this.isInWater() || this.isVehicle()) {
            return;
        }
        Vec3 velocity = this.getDeltaMovement();
        if (this.isSwimmingFallback()) {
            this.setDeltaMovement(velocity.x, velocity.y + this.swimClimbRate(), velocity.z);
        } else if (!this.onGround()) {
            this.setDeltaMovement(velocity.x, velocity.y - SINK_ACCELERATION, velocity.z);
        }
    }

    private boolean isSwimmingFallback() {
        return this.getNavigation() instanceof SeabedPathNavigation nav && nav.isSwimming();
    }

    private double swimClimbRate() {
        MoveControl control = this.getMoveControl();
        double dx = control.getWantedX() - this.getX();
        double dy = control.getWantedY() - this.getY();
        double dz = control.getWantedZ() - this.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1.0E-4D) {
            return 0.0D;
        }
        return this.getSpeed() * (dy / distance) * SWIM_CLIMB_GAIN;
    }

    private void tickSeaweed() {
        if (this.hasSeaweed()) {
            return;
        }
        if (!this.isFullySubmerged() || this.isSaddled() || this.isBaby()) {
            this.seaweedTicks = Math.min(this.seaweedTicks, 0);
            return;
        }
        this.seaweedTicks++;
        if (this.seaweedTicks >= SEAWEED_GROWTH_TICKS) {
            this.setSeaweed(true);
            this.playSound(SoundEvents.TURTLE_EGG_HATCH, 1.0F, 1.0F);
        }
    }

    private boolean isFullySubmerged() {
        return this.isUnderWater();
    }

    private boolean isSwimDeep() {
        return this.getFluidHeight(FluidTags.WATER) >= this.getBbHeight() * SWIM_DEPTH_FRACTION;
    }

    public boolean hasSeaweed() {
        return this.entityData.get(SEAWEED);
    }

    public void setSeaweed(boolean value) {
        this.entityData.set(SEAWEED, value);
    }

    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && this.hasChest() && this.isOwnedBy(player)) {
            this.openCustomInventoryScreen(player);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.SHEARS) && this.hasSeaweed()) {
            if (this.level() instanceof ServerLevel serverLevel) {
                this.setSeaweed(false);
                this.seaweedTicks = -SEAWEED_SHEAR_BLOCK_TICKS;
                this.level().playSound(null, this, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
                this.spawnAtLocation(serverLevel, new ItemStack(Items.KELP, 2));
                stack.hurtAndBreak(1, player,
                        hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.SADDLE)) {
            return this.trySaddle(player, stack);
        }
        if (stack.is(Items.CHEST) && !this.hasChest()) {
            return this.tryChest(player, stack);
        }
        if (this.isEquippableInSlot(stack, EquipmentSlot.BODY) && !this.isWearingBodyArmor()) {
            return this.tryBodyArmor(player, stack);
        }
        if (this.canBeOfferedTrustFood(stack)) {
            return this.offerTrustFood(player, hand, stack);
        }
        if (stack.isEmpty() && this.isSaddled() && this.isOwnedBy(player)) {
            return this.tryRide(player);
        }
        return super.mobInteract(player, hand);
    }

    // ───────────────────────────────────────────────────── TRUST ─────

    private boolean canBeOfferedTrustFood(ItemStack stack) {
        return !this.isTame() && !this.isBaby() && stack.is(Items.BEEF);
    }

    private InteractionResult offerTrustFood(Player player, InteractionHand hand, ItemStack stack) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        this.usePlayerItem(player, hand, stack);
        if (this.getRandom().nextInt(TRUST_CHANCE_DENOMINATOR) == 0) {
            this.tame(player);
            this.level().broadcastEntityEvent(this, (byte) 7);   // hearts
        } else {
            this.level().broadcastEntityEvent(this, (byte) 6);   // smoke
        }
        return InteractionResult.SUCCESS;
    }

    // ───────────────────────────────────────────────────── SADDLE ─────

    private InteractionResult trySaddle(Player player, ItemStack stack) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!this.isKnockedOut() || !this.isOwnedBy(player) || !this.isEquippableInSlot(stack, EquipmentSlot.SADDLE)) {
            return InteractionResult.CONSUME;
        }
        this.setItemSlot(EquipmentSlot.SADDLE, stack.consumeAndReturn(1, player));
        this.guaranteeTackDrops();
        this.stopIntimidating();
        this.removeEffect(MobEffects.WEAKNESS);
        this.sleepUrge().requestWake();
        return InteractionResult.SUCCESS;
    }

    private boolean isKnockedOut() {
        return this.isSleeping() && this.sleepUrge().isForced();
    }

    // ───────────────────────────────────────────────────── RIDING ─────

    private static final double FEAR_PULSE_RADIUS = 10.0D;
    private static final int FEAR_PULSE_DURATION_TICKS = 60;
    private static final int FEAR_COOLDOWN_TICKS = 300;
    private static final int FEAR_POSTURE_TICKS = 60;

    private static final float RIDDEN_SPRINT_MULTIPLIER = 1.6F;

    private static final int MOUNTED_ATTACK_COOLDOWN_TICKS = 60;

    private final RiderAbility fearPulse =
            new RiderAbility(this, "Fear", FEAR_COOLDOWN_TICKS, BossEvent.BossBarColor.PURPLE);

    private final RiderAbility mountedAttack =
            new RiderAbility(this, "Charge", MOUNTED_ATTACK_COOLDOWN_TICKS, BossEvent.BossBarColor.RED);

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return this.isSaddled() && this.getFirstPassenger() instanceof Player rider && this.isOwnedBy(rider)
                ? rider
                : super.getControllingPassenger();
    }

    @Override
    protected @NotNull Vec3 getRiddenInput(@NotNull Player controller, @NotNull Vec3 selfInput) {
        return RiderSteering.riddenInput(controller);
    }

    @Override
    protected void tickRidden(@NotNull Player controller, @NotNull Vec3 riddenInput) {
        super.tickRidden(controller, riddenInput);
        Vec2 rotation = RiderSteering.riddenRotation(controller);
        this.setRot(rotation.y, rotation.x);
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
    }

    @Override
    protected float getRiddenSpeed(@NotNull Player controller) {
        float base = (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return this.isSprinting() ? base * RIDDEN_SPRINT_MULTIPLIER : base;
    }

    @Override
    protected @NotNull Vec3 getPassengerAttachmentPoint(@NotNull Entity passenger,
                                                        @NotNull EntityDimensions dimensions, float scale) {
        return new Vec3(0.0D, dimensions.height() * 1.0D * scale, -0.15D * scale);
    }

    @Override
    public @NotNull Vec3 getDismountLocationForPassenger(@NotNull LivingEntity passenger) {
        Direction direction = this.getMotionDirection();
        if (direction.getAxis() == Direction.Axis.Y) {
            return super.getDismountLocationForPassenger(passenger);
        }

        int[][] offsets = DismountHelper.offsetsForDirection(direction);
        int spread = Mth.ceil(this.getBbWidth() / 2.0F + 0.5F);
        BlockPos origin = this.blockPosition();
        BlockPos.MutableBlockPos candidate = new BlockPos.MutableBlockPos();

        for (Pose pose : passenger.getDismountPoses()) {
            AABB bounds = passenger.getLocalBoundsForPose(pose);
            for (int[] offset : offsets) {
                candidate.set(origin.getX() + offset[0] * spread, origin.getY(), origin.getZ() + offset[1] * spread);
                double floor = this.level().getBlockFloorHeight(candidate);
                if (!DismountHelper.isBlockFloorValid(floor)) {
                    continue;
                }
                Vec3 spot = Vec3.upFromBottomCenterOf(candidate, floor);
                if (DismountHelper.canDismountTo(this.level(), passenger, bounds.move(spot))) {
                    passenger.setPose(pose);
                    return spot;
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    private void tickRiddenState() {
        if (this.getControllingPassenger() instanceof ServerPlayer rider) {
            this.setSprinting(rider.getLastClientInput().sprint());
        } else if (this.isSprinting()) {
            this.setSprinting(false);
        }
    }

    @Override
    protected boolean isMovingNow() {
        if (this.getControllingPassenger() instanceof ServerPlayer rider) {
            Input input = rider.getLastClientInput();
            return input.forward() || input.backward() || input.left() || input.right();
        }
        return super.isMovingNow();
    }

    private boolean isRunning() {
        return this.isAggressive() || this.isSprinting();
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        this.fearPulse.hide();
        this.mountedAttack.hide();
        super.remove(reason);
    }

    private InteractionResult tryRide(Player player) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!this.isSaddled() || !this.isOwnedBy(player) || this.isVehicle() || this.isInSleepCycle()) {
            return InteractionResult.CONSUME;
        }
        player.startRiding(this);
        return InteractionResult.SUCCESS;
    }

    // ───────────────────────────────────────────────────── RIDER ACTIONS ─────

    @Override
    public void onRiderAction(@NotNull ServerPlayer rider, RiderControllable.@NotNull RiderAction action) {
        if (this.getControllingPassenger() != rider) {
            return;
        }
        switch (action) {
            case FEAR -> this.releaseFearPulse();
            case ATTACK -> this.strikeFromSaddle();
            case OPEN_INVENTORY -> this.openCustomInventoryScreen(rider);
            default -> { }
        }
    }

    private void releaseFearPulse() {
        if (!this.fearPulse.tryUse()) {
            return;
        }
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(FEAR_PULSE_RADIUS), this::isAfraidOfMe)) {
            victim.addEffect(new MobEffectInstance(SMOPEffects.FEAR, FEAR_PULSE_DURATION_TICKS, 0));
        }
        this.startIntimidating(FEAR_POSTURE_TICKS, false);
        this.playSound(SoundEvents.HOGLIN_ANGRY, 1.5F, 0.6F);
    }

    private boolean isAfraidOfMe(LivingEntity candidate) {
        if (candidate == this || candidate instanceof HellHippoEntity) {
            return false;
        }
        LivingEntity owner = this.getOwner();
        if (candidate == owner) {
            return false;
        }
        return owner == null || !(candidate instanceof TamableAnimal pet) || !pet.isOwnedBy(owner);
    }

    private void strikeFromSaddle() {
        if (!this.mountedAttack.tryUse()) {
            return;
        }
        this.animator().play(this.animator().getByName(ANIM_ATTACK));
        this.playSound(SoundEvents.HOGLIN_ATTACK, 1.0F, 1.0F);
    }

    // ───────────────────────────────────────────────────── ARMOUR ─────

    private InteractionResult tryBodyArmor(Player player, ItemStack stack) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!this.isOwnedBy(player)) {
            return InteractionResult.CONSUME;
        }
        this.setBodyArmorItem(stack.consumeAndReturn(1, player));
        this.guaranteeTackDrops();
        return InteractionResult.SUCCESS;
    }

    private void guaranteeTackDrops() {
        this.setGuaranteedDrop(EquipmentSlot.SADDLE);
        this.setGuaranteedDrop(EquipmentSlot.BODY);
    }

    // ───────────────────────────────────────────────────── CHEST ─────

    private InteractionResult tryChest(Player player, ItemStack stack) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!this.isSaddled() || !this.isOwnedBy(player)) {
            return InteractionResult.CONSUME;
        }
        this.setChest(true);
        stack.consume(1, player);
        this.level().playSound(null, this, SoundEvents.DONKEY_CHEST, SoundSource.NEUTRAL, 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    public boolean hasChest() {
        return this.entityData.get(CHEST);
    }

    public void setChest(boolean value) {
        this.entityData.set(CHEST, value);
    }

    // ───────────────────────────────────────────────────── INVENTORY ─────

    private static final int INVENTORY_SIZE = 27;

    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE) {
        @Override
        public boolean stillValid(@NotNull Player player) {
            return HellHippoEntity.this.isAlive()
                    && HellHippoEntity.this.hasChest()
                    && player.isWithinEntityInteractionRange(HellHippoEntity.this, 4.0D);
        }
    };

    @Override
    public void openCustomInventoryScreen(@NotNull Player player) {
        if (this.level().isClientSide() || !this.hasChest() || !this.isOwnedBy(player)) {
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInventory, opener) -> ChestMenu.threeRows(containerId, playerInventory, this.inventory),
                this.getDisplayName()));
    }

    @Override
    protected void dropEquipment(@NotNull ServerLevel level) {
        super.dropEquipment(level);
        if (!this.hasChest()) {
            return;
        }
        this.spawnAtLocation(level, new ItemStack(Items.CHEST));
        for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
            ItemStack carried = this.inventory.removeItemNoUpdate(slot);
            if (!carried.isEmpty()) {
                this.spawnAtLocation(level, carried);
            }
        }
    }

    // ───────────────────────────────────────────────────── SADDLE ─────

    @Override
    public boolean canUseSlot(@NotNull EquipmentSlot slot) {
        return switch (slot) {
            case SADDLE -> this.isAlive() && !this.isBaby() && this.isTame();
            case BODY -> this.isAlive() && !this.isBaby() && this.isSaddled();
            default -> super.canUseSlot(slot);
        };
    }

    @Override
    protected @NotNull Holder<SoundEvent> getEquipSound(@NotNull EquipmentSlot slot, @NotNull ItemStack stack,
                                                        @NotNull Equippable equippable) {
        return slot == EquipmentSlot.SADDLE ? SoundEvents.HORSE_SADDLE : super.getEquipSound(slot, stack, equippable);
    }

    @Override
    public boolean canAttack(@NotNull LivingEntity target) {
        if (!super.canAttack(target)) {
            return false;
        }
        LivingEntity owner = this.getOwner();
        return owner == null || !(target instanceof TamableAnimal pet) || !pet.isOwnedBy(owner);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SEAWEED, false);
        builder.define(INTIMIDATING, false);
        builder.define(CHEST, false);
    }

    @Override
    protected void addAdditionalSaveData(@NotNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Seaweed", this.hasSeaweed());
        output.putBoolean("Chest", this.hasChest());
        this.fearPulse.save(output, "FearCooldown");
        this.mountedAttack.save(output, "AttackCooldown");
        if (this.hasChest()) {
            ValueOutput.TypedOutputList<ItemStackWithSlot> carried = output.list("Items", ItemStackWithSlot.CODEC);
            for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
                ItemStack stack = this.inventory.getItem(slot);
                if (!stack.isEmpty()) {
                    carried.add(new ItemStackWithSlot(slot, stack));
                }
            }
        }
        output.putInt("SeaweedTicks", this.seaweedTicks);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setSeaweed(input.getBooleanOr("Seaweed", false));
        this.setChest(input.getBooleanOr("Chest", false));
        this.fearPulse.load(input, "FearCooldown");
        this.mountedAttack.load(input, "AttackCooldown");
        if (this.hasChest()) {
            for (ItemStackWithSlot carried : input.listOrEmpty("Items", ItemStackWithSlot.CODEC)) {
                if (carried.isValidInContainer(this.inventory.getContainerSize())) {
                    this.inventory.setItem(carried.slot(), carried.stack());
                }
            }
        }
        this.seaweedTicks = input.getIntOr("SeaweedTicks", 0);
    }


    private void faceCombatTarget() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || this.isMovementLocked() || this.isDeadOrDying()) {
            return;
        }
        if (this.moveControl instanceof DirectionalMoveControl<?> control
                && this.distanceToSqr(target) <= FACE_LOCK_RADIUS * FACE_LOCK_RADIUS) {
            control.faceTarget(target);
        }
    }

    // ───────────────────────────────────────────────────── SLEEP ─────


    @Override
    public @NotNull Set<EntityType<?>> getInterruptingEntityTypes() {
        return Set.of();
    }

    @Override
    public boolean shouldInterruptSleepDueTo(LivingEntity nearby) {
        return nearby instanceof Mob mob && mob.getTarget() == this;
    }

    // ───────────────────────────────────────────────────── ANIMATIONS ─────

    @Override
    public void registerAnimations() {
        StandardAnimation idle = clip("idle", () -> HellHippoAnimations.idle, () -> HellHippoBabyAnimations.idle,
                Loop.REPEATING, 3, 5.25F);
        StandardAnimation walk = clip("walk", () -> HellHippoAnimations.walk, () -> HellHippoBabyAnimations.walk,
                Loop.REPEATING, 2, 1.5F);
        StandardAnimation sprint = clip("sprint", () -> HellHippoAnimations.sprint, () -> HellHippoBabyAnimations.sprint,
                Loop.REPEATING, 2, 0.75F);
        StandardAnimation waterIdle = adultClip("water_idle", () -> HellHippoAnimations.widle, Loop.REPEATING, 3, 3.0F);
        // 1.15 is the adult's length; the calf's own swim is 1.1667. One number covers both ages
        // here, and on a REPEATING clip the sliver of difference only shifts the logical cycle
        // against the visual one, which loops by time-modulo in the interpolator regardless.
        StandardAnimation swim = clip("swim", () -> HellHippoAnimations.swim, () -> HellHippoBabyAnimations.swim,
                Loop.REPEATING, 2, 1.15F);

        StandardAnimation death = clip("death", () -> HellHippoAnimations.death, () -> HellHippoBabyAnimations.death,
                Loop.PLAY_ONCE, 0, 2.0F);

        // Three sleep phases, not six: the cycle is assembled from whichever clips a mob registers,
        // and this rig has no sitting-down set. See SleepPhase.
        // Lengths read off the clips themselves, and identical for both ages. SleepGoal takes the
        // phase durations straight from these, so a number invented here would leave the mob frozen
        // on a last frame for the difference.
        StandardAnimation preparingSleep = clip("preparing_sleep",
                () -> HellHippoAnimations.sleep_preparing, () -> HellHippoBabyAnimations.sleep_preparing,
                Loop.PLAY_ONCE, 1, 3.0F);
        StandardAnimation sleep = clip("sleep", () -> HellHippoAnimations.sleep, () -> HellHippoBabyAnimations.sleep,
                Loop.REPEATING, 1, 2.0F);
        StandardAnimation awakening = clip("awakening",
                () -> HellHippoAnimations.awakening, () -> HellHippoBabyAnimations.awakening,
                Loop.PLAY_ONCE, 1, 1.5F);
        // Trimmed from the clip's own withLength(3.5F), same reasoning as the bite above: 11 of its
        // 13 channels are already sitting at neutral by 3.0 (head 2.96, neck 3.04), so the last half
        // second was a dead neutral pose held before locomotion could take the frame back — the
        // "stiff at the end". Only torso (3.29) and the tail pair lose any settle, and the rig's
        // 220 ms crossfade covers that.
        StandardAnimation shake = clip(ANIM_SHAKE, () -> HellHippoAnimations.shake, () -> HellHippoBabyAnimations.shake,
                Loop.PLAY_ONCE, 1, 3.05F);
        // Both rigs author eat at withLength(3.5F); verified rather than assumed.
        StandardAnimation eat = clip(ANIM_EAT, () -> HellHippoAnimations.eat, () -> HellHippoBabyAnimations.eat,
                Loop.PLAY_ONCE, 1, 3.5F);
        // The stare-down, in three pieces. It arrived from 1.20.1 as ONE 7.5-second clip and was
        // registered that way at first, which looked wrong for a reason no amount of looping could
        // fix: a 7.5-second clip covering a 15-second window has to restart, and the restart passes
        // through the settle its own last second is made of — so the animal visibly relaxed out of
        // the pose and snapped back into it, twice per standoff.
        //
        // Cutting it the way any looping action wants to be cut solves it outright. The entry and
        // the release are the parts that must not repeat; the middle is the only part that should.
        // Adult only: a calf never gets here, because the trust ritual refuses one.
        StandardAnimation intimidateIn = adultClip(ANIM_INTIMIDATE_IN,
                () -> HellHippoAnimations.intimidate_in, Loop.PLAY_ONCE, 1, 0.65F);
        StandardAnimation intimidateLoop = adultClip(ANIM_INTIMIDATE_LOOP,
                () -> HellHippoAnimations.intimidate_loop, Loop.REPEATING, 1, 2.0F);
        StandardAnimation intimidateOut = adultClip(ANIM_INTIMIDATE_OUT,
                () -> HellHippoAnimations.intimidate_out, Loop.PLAY_ONCE, 1, 0.95F);

        // The lunge AnimatableMeleeAttackGoal looks up by name, and what the HitWindow below is
        // applied to. Priority 0 so it wins over locomotion, which keeps running underneath.
        StandardAnimation attack = clip(ANIM_ATTACK, () -> HellHippoAnimations.attack, () -> HellHippoBabyAnimations.attack,
                Loop.PLAY_ONCE, 0, ATTACK_SECONDS);

        // The damage is here, not in the goal: the goal only decides WHEN to commit, and the window
        // sweeps a box on the frames the jaws are actually closing. The 1.20.1 version instead ran a
        // parallel 13-tick counter inside the goal and called doHurtTarget when it hit zero, which is
        // why the hit and the visible bite drifted apart.
        HitWindow.of(ATTACK_WINDOW_START, ATTACK_WINDOW_END)
                // box3d and not box: box ignores the Y axis outright (AttackShape's interface note
                // says so — "a ground mob's swing reaches whatever is in front of it, at any height"),
                // and the only vertical bound it has is the broad-phase AABB at ±3.8. On land that
                // never showed, because targets stand on the same floor the hippo does. Underwater
                // they do not, and a player swimming three blocks overhead was taking damage from a
                // bite that visibly never reached them.
                //
                // The half-height of 1.5 is measured from the anchor at 0.9, so it covers −0.6 to
                // +2.4 above the hippo's feet — anything standing on its own ground, plus a step or
                // two of slope, and nothing floating clear above it. Note box3d tests the target's
                // CENTRE (position + bbHeight/2) where box tested its feet, so the line sits at the
                // middle of the victim's body.
                //
                // A look number: adjust it against the render.
                .shape(AttackShape.box3d(2.6F, 1.1F, 1.5F))
                .anchor(1.9F, 0.0F, 0.9F)
                .damage((float) this.getAttributeValue(Attributes.ATTACK_DAMAGE))
                .knockback(0.6F)
                // Hippos CAN maul each other, deliberately. The species exclusion that used to sit
                // here spared any hippo that was not the current target, on the reasoning that a bite
                // this wide would otherwise catch the calf standing beside its mother — but it also
                // made a fight between two of them impossible to resolve with anything but the
                // declared target, which is not how a bite that sweeps a box works. Only the rider is
                // still spared, since biting your own passenger is never intended.
                .filter(target -> !this.hasPassenger(target))
                .applyTo(attack);

        // Exactly one of these holds at any moment: deep enough for the water set or not, moving or
        // not. Both ages now have their own walk, so the split is purely by speed.
        idle.setPlayCondition(a -> this.canPlayLocomotion() && !this.isSwimDeep() && !this.isMoving());
        // isRunning(), not isAggressive(): a mount whose rider is holding sprint is running too, and
        // that flag is synced so the condition still agrees on both sides.
        walk.setPlayCondition(a -> this.canPlayLocomotion() && !this.isSwimDeep() && this.isMoving()
                && !this.isRunning());
        sprint.setPlayCondition(a -> this.canPlayLocomotion() && !this.isSwimDeep() && this.isMoving()
                && this.isRunning());
        // The calf has no authored water idle, so it uses the swim clip whenever it is in water.
        waterIdle.setPlayCondition(a -> this.canPlayLocomotion() && this.isSwimDeep() && !this.isMoving()
                && !this.isBaby());
        swim.setPlayCondition(a -> this.canPlayLocomotion() && this.isSwimDeep()
                && (this.isMoving() || this.isBaby()));

        // The two transitions are PLAY_ONCE, and the animator's auto-start loop only ever starts
        // REPEATING clips — so these conditions do NOT start them; SleepGoal does, through
        // onSleepPhaseBegin. What they buy is the reverse: a clip whose condition goes false is cut,
        // so a hippo shaken awake mid-settle drops the clip instead of finishing it.
        preparingSleep.setPlayCondition(a -> this.isPreparingSleep());
        awakening.setPlayCondition(a -> this.isAwakening());
        // Eligible through the settling phase too, so the loop is armed the instant the settle clip
        // ends whichever way the two clocks land — same as the Tangoftero.
        sleep.setPlayCondition(a -> this.isSleeping() || this.isPreparingSleep());
        preparingSleep.setNextAnimation("sleep");

        shake.setPlayCondition(a -> this.isPerforming(ANIM_SHAKE));
        eat.setPlayCondition(a -> this.isPerforming(ANIM_EAT));
        // Not a scripted action like the shake: this one follows a synced STATE, because the mob has
        // to hold the pose for the whole 15-second window rather than play a gesture once. Priority 1
        // so it sits over locomotion but under the bite.
        // Same shape as the sleep chain above, and for the same reason: the two one-shots are started
        // by hand (startAction, from startIntimidating/stopIntimidating) because the animator's
        // auto-start only ever picks up REPEATING clips. Their conditions exist to CUT them — a
        // standoff that ends mid-entry drops the clip instead of finishing it.
        intimidateIn.setPlayCondition(a -> this.isPerforming(ANIM_INTIMIDATE_IN));
        intimidateOut.setPlayCondition(a -> this.isPerforming(ANIM_INTIMIDATE_OUT));
        // The loop is the one that auto-starts, and it must not do so until the entry has finished —
        // hence the explicit exclusion rather than relying on the chain alone.
        // Not while it is out cold either: the state survives the potion so the standoff can resume
        // on waking, but a hippo lying down should be playing the sleep clip, not posturing.
        intimidateLoop.setPlayCondition(a -> this.isIntimidating()
                && !this.isInSleepCycle()
                && !this.isPerforming(ANIM_INTIMIDATE_IN));
        intimidateIn.setNextAnimation(ANIM_INTIMIDATE_LOOP);

        // Stops the rig's look-at from still tracking with a corpse's neck while the death clip runs.
        death.blockAdditive();

        this.animator().register(idle, walk, sprint, waterIdle, swim,
                preparingSleep, sleep, awakening, shake, eat,
                intimidateIn, intimidateLoop, intimidateOut, attack);
        // registerDeath, not register: it also makes the clip non-interruptible and holds the corpse
        // in the world for its full length instead of vanilla's fixed 20 ticks. Priority 0 so it wins
        // over locomotion, which keeps running underneath.
        this.animator().registerDeath(death);
    }

    private boolean canPlayLocomotion() {
        return !this.isDeadOrDying();
    }

    private StandardAnimation clip(String name, Supplier<Object> adult, Supplier<Object> baby,
                                   Loop loop, int priority, float seconds) {
        return new StandardAnimation(name,
                new AnimSource(() -> this.isBaby() ? baby.get() : adult.get()), loop, 0, priority, seconds);
    }

    private StandardAnimation adultClip(String name, Supplier<Object> adult, Loop loop, int priority, float seconds) {
        return new StandardAnimation(name, new AnimSource(adult), loop, 0, priority, seconds);
    }

    // ───────────────────────────────────────────────────── SPAWN ─────

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level,
                                                  @NotNull DifficultyInstance difficulty,
                                                  @NotNull EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData spawnData) {
        this.setMale(this.getRandom().nextBoolean());
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);
        if (!this.isMale() && !this.isBaby() && this.getRandom().nextFloat() < CALF_COMPANION_CHANCE) {
            this.spawnCompanionCalf(level);
        }
        return data;
    }

    private void spawnCompanionCalf(ServerLevelAccessor level) {
        HellHippoEntity calf = SMOPEntities.HELL_HIPPO.get().create(level.getLevel(), EntitySpawnReason.NATURAL);
        if (calf == null) {
            return;
        }
        calf.setMale(this.getRandom().nextBoolean());
        calf.setBaby(true);
        calf.snapTo(this.getX() + (this.getRandom().nextDouble() - 0.5D) * 2.0D, this.getY(),
                this.getZ() + (this.getRandom().nextDouble() - 0.5D) * 2.0D, this.getYRot(), 0.0F);
        level.addFreshEntity(calf);
    }

    public static boolean checkHellHippoSpawnRules(EntityType<HellHippoEntity> type, ServerLevelAccessor level,
                                                   EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return checkAnimalSpawnRules(type, level, reason, pos, random);
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob partner) {
        HellHippoEntity calf = SMOPEntities.HELL_HIPPO.get().create(level, EntitySpawnReason.BREEDING);
        if (calf != null) {
            // Rolled here rather than left to finalizeSpawn: a bred calf never goes through it.
            calf.setMale(this.getRandom().nextBoolean());
        }
        return calf;
    }

    @Override
    public boolean isFood(@NotNull ItemStack stack) {
        return stack.is(Items.CARROT) || stack.is(Items.BEEF);
    }

    // ───────────────────────────────────────────────────── SOUNDS ─────

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return SoundEvents.HOGLIN_AMBIENT;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(@NotNull DamageSource source) {
        return SoundEvents.HOGLIN_HURT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return SoundEvents.HOGLIN_DEATH;
    }
}

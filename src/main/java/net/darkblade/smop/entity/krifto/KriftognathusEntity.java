package net.darkblade.smop.entity.krifto;

import net.darkblade.deluxelib.anim.AnimSource;
import net.darkblade.deluxelib.anim.Loop;
import net.darkblade.deluxelib.anim.StandardAnimation;
import net.darkblade.deluxelib.combat.AttackShape;
import net.darkblade.deluxelib.combat.HitWindow;
import net.darkblade.deluxelib.entity.ai.goal.AnimatableMeleeAttackGoal;
import net.darkblade.smop.block.SMOPBlocks;
import net.darkblade.smop.client.krifto.KriftoAnimations;
import net.darkblade.smop.client.krifto.KriftoBabyAnimations;
import net.darkblade.smop.entity.SMOPFlyingAnimal;
import net.darkblade.smop.entity.ai.goal.FollowOwnerBaseGoal;
import net.darkblade.smop.entity.ai.goal.GenericBreedGoal;
import net.darkblade.smop.entity.ai.goal.SMOPRandomStrollGoal;
import net.darkblade.smop.entity.ai.goal.egg.EggGoalRegistry;
import net.darkblade.smop.entity.ai.goal.egg.ProtectEggBaseGoal;
import net.darkblade.smop.entity.ai.goal.flying.FollowOwnerFlyingGoal;
import net.darkblade.smop.entity.egg.CustomEggBorn;
import net.darkblade.smop.entity.sleep.ISleepAwareness;
import net.darkblade.smop.entity.sleep.ISleepThreatEvaluator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The Kriftognathus: a pterosaur-ish scavenger that wears the colours of the biome it hatched in,
 * and — once tamed — will ride on your head and break your fall.
 */
public class KriftognathusEntity extends SMOPFlyingAnimal
        implements ISleepThreatEvaluator, ISleepAwareness, CustomEggBorn {

    // ───────────────────────────────────────────────────── TUNING ─────

    /** Length of the {@code attack} clip, and the frames its beak connects on. */
    private static final float ATTACK_SECONDS = 0.4F;
    private static final int BITE_WINDOW_START = 2;
    private static final int BITE_WINDOW_END = 4;

    /** Terminal velocity a carried player is allowed — the whole point of the perch. */
    private static final double CARRY_FALL_CLAMP = -0.15D;

    /** Clip names the flight lifecycle drives, by the same convention as {@code ANIM_SLEEP} and co. */
    private static final String ANIM_START_FLIGHT = "start_flight";
    private static final String ANIM_LANDING = "landing";
    private static final String ANIM_SWOOP = "swoop";

    /** Wild nest defence: what it will actually see off. */
    private static final Predicate<LivingEntity> NEST_THREAT_SELECTOR =
            entity -> entity.getType() == EntityType.SNIFFER || entity.getType() == EntityType.FOX;

    private static final EntityDataAccessor<String> SPAWN_BIOME =
            SynchedEntityData.defineId(KriftognathusEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> ON_PLAYERS_HEAD =
            SynchedEntityData.defineId(KriftognathusEntity.class, EntityDataSerializers.BOOLEAN);

    public KriftognathusEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.FOLLOW_RANGE, 28.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.20D)
                .add(Attributes.FLYING_SPEED, 0.25D)
                .add(Attributes.ATTACK_SPEED, 0.4D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.1D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SPAWN_BIOME, "default");
        builder.define(ON_PLAYERS_HEAD, false);
    }

    // ───────────────────────────────────────────────────── GOALS ─────

    /**
     * Flight goals go in via {@link #registerFlightGoals}: take-off at 2, <b>landing at 3</b>, flight
     * wander at 7.
     *
     * <p>The landing goal sits that high on purpose. It holds MOVE, and so do the escort and the
     * melee goal — put those above it and a mob that acquires a target (or an owner) mid-descent
     * starves the landing goal of its flag and hangs in the landing state with nothing driving it
     * down. A bird committed to a touchdown finishes it first. Take-off deliberately holds no flags
     * at all, so the ground goals below keep running while it lifts off.
     *
     * <p>Following the owner is split in two: the flying escort at 4 takes over once the mob is in
     * the air and asks for a take-off when it is not, and {@link FollowOwnerBaseGoal} at 8 walks it
     * over at close range. That one has to sit <b>above</b> the stroll goal, or wandering would win
     * every arbitration and the mob would never actually follow on foot.
     */
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, this.createSleepGoal());
        this.registerFlightGoals(2, 7, 3);
        this.goalSelector.addGoal(4, new FollowOwnerFlyingGoal(this, 8.0F));
        this.goalSelector.addGoal(5, new AnimatableMeleeAttackGoal(this, 1.8D, true)
                .reach(2.0F)
                .cooldown(10)
                .attackCondition(target -> !this.isBaby() && !this.isOnPlayersHead())
                .onAttack((target, animator) -> animator.play(animator.getByName("attack"))));
        this.goalSelector.addGoal(6, new GenericBreedGoal<>(this, 1.2D));

        this.followOwnerOnFoot = new FollowOwnerBaseGoal(this, 1.0D, 6.0F, 2.0F);
        this.goalSelector.addGoal(8, this.followOwnerOnFoot);
        this.goalSelector.addGoal(9, new SMOPRandomStrollGoal(this, 1.0D, 120,
                () -> !this.isFlying() && !this.isBaby() && !this.isMovementLocked()));

        // Solitary nester that actually defends the clutch, unlike the Tangoftero's colony.
        EggGoalRegistry.registerWithOwnGoal(this, SMOPBlocks.KRIFTO_EGG,
                4, 6, true, true,
                ProtectEggBaseGoal.EggBreakReaction.IGNORE, NEST_THREAT_SELECTOR, 10);

        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 3.0F));
        this.goalSelector.addGoal(12, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    /** Held so the navigation swap can tell it to re-read {@link #getNavigation()}. */
    @Nullable
    private FollowOwnerBaseGoal followOwnerOnFoot;

    @Override
    protected void onNavigationSwapped() {
        if (this.followOwnerOnFoot != null) {
            this.followOwnerOnFoot.refreshNavigation();
        }
    }

    // ───────────────────────────────────────────────────── FLIGHT ─────

    /** The chick's pterosaur parents circle low over their nesting ground rather than soaring. */
    @Override
    protected double getMinFlightAltitude() {
        return 6.0D;
    }

    @Override
    protected double getMaxFlightAltitude() {
        return 18.0D;
    }

    @Override
    protected double getWanderHorizontalRadius() {
        return 22.0D;
    }

    @Override
    protected int computeGroundRestTicks() {
        return 200 + this.random.nextInt(200);
    }

    @Override
    protected int computeMaxFlightTicks() {
        return 300 + this.random.nextInt(300);
    }

    @Override
    protected double getLandingDescentSpeed() {
        return 0.07D;
    }

    @Override
    protected double getTakeoffLiftSpeed() {
        return 0.09D;
    }

    /** Low, so the flare and the {@code landing} clip run against the ground rather than over it. */
    @Override
    protected double getLandingApproachAltitude() {
        return 1.6D;
    }

    /** The authored {@code start_flight} already owns the pose — the physics nose-up would stack. */
    @Override
    protected boolean applyTiltDuringTakeoff() {
        return false;
    }

    /**
     * Long enough for the 1.3 s {@code landing} clip plus the glide it is played over. This is a
     * safety net, not the intended exit: {@link KriftoLandingGoal} completes on ground contact.
     */
    @Override
    protected int getMaxLandingTicks() {
        return Math.max(60, this.clipDurationTicks(ANIM_LANDING) * 3);
    }

    @Override
    protected TakeoffGoal createTakeoffGoal() {
        return new KriftoTakeoffGoal();
    }

    @Override
    protected LandingGoal createLandingGoal() {
        return new KriftoLandingGoal();
    }

    /** Holds the take-off phase open for exactly as long as the {@code start_flight} clip runs. */
    private class KriftoTakeoffGoal extends TakeoffGoal {
        @Override
        protected boolean shouldCompleteTakeoff() {
            return !KriftognathusEntity.this.animator().isPlaying(ANIM_START_FLIGHT);
        }
    }

    /**
     * Ground contact still ends the landing — a bird that has touched down is down, whatever the
     * clip thinks — but a mob that has not touched anything yet holds the phase until the
     * {@code landing} clip finishes, so the flare is never cut off mid-gesture.
     */
    private class KriftoLandingGoal extends LandingGoal {
        @Override
        protected boolean shouldCompleteLanding() {
            return super.shouldCompleteLanding()
                    && !KriftognathusEntity.this.animator().isPlaying(ANIM_LANDING);
        }
    }

    // ───────────────────────────────────────────────────── FLIGHT ANIMATION HOOKS ─────

    @Override
    protected void onTakeoffBegin() {
        this.playIfRegistered(ANIM_START_FLIGHT);
    }

    /** The stoop into the landing approach — the one clip 1.20.1 registered and never played. */
    @Override
    protected void onSeekGroundBegin() {
        this.playIfRegistered(ANIM_SWOOP);
    }

    @Override
    protected void onLandingBegin() {
        this.playIfRegistered(ANIM_LANDING);
    }

    // ───────────────────────────────────────────────────── ANIMATIONS ─────

    /**
     * Exclusion is by <b>priority</b>, not by play condition: locomotion sits at 2–3 and every
     * one-shot at 0–1, so {@code BlendLayer#current} renders the one-shot while the locomotion cycle
     * keeps running underneath — which is what gives the frame an attack or a perch ends something
     * to fall back to instead of collapsing to the bind pose. Same arrangement as the Tangoftero.
     *
     * <p>The chick has no flight clips at all, so the air family carries only an adult definition.
     * The loops gate on {@code isFlying()} and the one-shots are fired by the flight lifecycle, and
     * both of those are closed to a baby — {@code SMOPFlyingAnimal} refuses to set the flight flag on
     * one and refuses to start a take-off for one. So no air clip can ever be baked against the chick
     * model, which lacks the bones they animate.
     */
    @Override
    public void registerAnimations() {
        StandardAnimation idle = clip("idle", () -> KriftoAnimations.lidle, () -> KriftoBabyAnimations.l_idle,
                Loop.REPEATING, 3, 2.0F);
        StandardAnimation walk = clip("walk", () -> KriftoAnimations.walk, () -> KriftoBabyAnimations.walk,
                Loop.REPEATING, 2, 0.8F);
        StandardAnimation sprint = clip("sprint", () -> KriftoAnimations.sprint, () -> KriftoBabyAnimations.sprint,
                Loop.REPEATING, 2, 0.4F);
        StandardAnimation swim = clip("swim", () -> KriftoAnimations.swim, () -> KriftoBabyAnimations.swim,
                Loop.REPEATING, 2, 2.4F);

        StandardAnimation preparingSleep = clip("preparing_sleep",
                () -> KriftoAnimations.sleep_preparing, () -> KriftoBabyAnimations.sleep_preparing,
                Loop.PLAY_ONCE, 1, 2.5F);
        StandardAnimation sleep = clip("sleep", () -> KriftoAnimations.sleep, () -> KriftoBabyAnimations.sleep,
                Loop.REPEATING, 1, 4.0F);
        StandardAnimation awakening = clip("awakening", () -> KriftoAnimations.awakening, () -> KriftoBabyAnimations.awakening,
                Loop.PLAY_ONCE, 1, 3.5F);

        StandardAnimation attack = clip("attack", () -> KriftoAnimations.attack, () -> KriftoBabyAnimations.attack,
                Loop.PLAY_ONCE, 0, ATTACK_SECONDS);
        StandardAnimation bite = clip("bite", () -> KriftoAnimations.bite, () -> KriftoBabyAnimations.bite,
                Loop.PLAY_ONCE, 0, 0.75F);
        StandardAnimation death = clip("death", () -> KriftoAnimations.death, () -> KriftoBabyAnimations.death,
                Loop.PLAY_ONCE, 0, 1.5F);
        StandardAnimation onHead = clip("on_players_head",
                () -> KriftoAnimations.on_players_head, () -> KriftoBabyAnimations.on_players_head,
                Loop.REPEATING, 1, 2.4F);

        // Adult-only: the chick model has none of these bones, so there is no baby definition to
        // fall back to and the flight flag can never be true for it.
        StandardAnimation flyIdle = adultClip("fly_idle", () -> KriftoAnimations.aidle, Loop.REPEATING, 2, 0.6F);
        StandardAnimation flight = adultClip("flight", () -> KriftoAnimations.flight, Loop.REPEATING, 2, 0.8F);
        StandardAnimation takeOff = adultClip(ANIM_START_FLIGHT, () -> KriftoAnimations.start_flight, Loop.PLAY_ONCE, 1, 1.0F);
        StandardAnimation landing = adultClip(ANIM_LANDING, () -> KriftoAnimations.landing, Loop.PLAY_ONCE, 1, 1.3F);
        StandardAnimation swoop = adultClip(ANIM_SWOOP, () -> KriftoAnimations.swoop, Loop.PLAY_ONCE, 1, 1.6F);

        idle.blendInMs(300).blendOutMs(250);
        walk.blendInMs(200).blendOutMs(200);
        sprint.blendInMs(200).blendOutMs(200);
        // Aerial poses are broad and slow-moving, so their crossfades breathe more than the ground
        // family's — the same balance the Arpy strikes.
        flyIdle.blendInMs(350).blendOutMs(250);
        flight.blendInMs(250).blendOutMs(250);
        takeOff.blendInMs(100).blendOutMs(300);
        landing.blendInMs(150).blendOutMs(250);
        swoop.blendInMs(200).blendOutMs(300);

        // The beak: a strictly frontal box across the frames it snaps shut.
        HitWindow.of(BITE_WINDOW_START, BITE_WINDOW_END)
                .shape(AttackShape.box(1.5F, 0.5F))
                .anchor(0.6F, 0.0F, 0.6F)
                .damage(2.0F)
                .knockback(0.1F)
                .filter(target -> !(target instanceof KriftognathusEntity))
                .applyTo(attack);

        // Ground locomotion — mutually exclusive by construction, and all of it stands down in the air.
        idle.setPlayCondition(a -> this.canPlayLocomotion() && !this.isFlying() && !this.isInWater() && !this.isMoving());
        walk.setPlayCondition(a -> this.canPlayLocomotion() && !this.isFlying() && !this.isInWater()
                && this.isMoving() && !this.isAggressive());
        sprint.setPlayCondition(a -> this.canPlayLocomotion() && !this.isFlying() && !this.isInWater()
                && this.isMoving() && this.isAggressive());
        swim.setPlayCondition(a -> this.canPlayLocomotion() && !this.isFlying() && this.isInWater());

        // Air: hover versus travel, on the synced held flag so the two do not strobe at the threshold.
        // Deliberately still eligible through take-off, the stoop and landing: those one-shots sit at
        // priority 1 and out-render these at 2, and a clip that outlives its phase (or a phase that
        // outlives its clip) then falls back onto a running cycle instead of onto the bind pose.
        // isFlyingMoving() is forced false during take-off and landing, so what shows underneath
        // them is the hover.
        flyIdle.setPlayCondition(a -> this.canPlayLocomotion() && this.isFlying() && !this.isFlyingMoving());
        flight.setPlayCondition(a -> this.canPlayLocomotion() && this.isFlying() && this.isFlyingMoving());

        // Perched: priority 1 out-renders the locomotion still running underneath, and it chains
        // nothing — dismounting simply lets that locomotion show through again.
        onHead.setPlayCondition(a -> this.isOnPlayersHead());

        preparingSleep.setPlayCondition(a -> this.isPreparingSleep());
        awakening.setPlayCondition(a -> this.isAwakening());
        // Eligible through the settling phase too, so the loop is armed the instant the settle clip
        // ends whichever way the two clocks land — see the Tangoftero for the full reasoning.
        sleep.setPlayCondition(a -> this.isSleeping() || this.isPreparingSleep());
        preparingSleep.setNextAnimation(ANIM_SLEEP);

        death.blockAdditive();

        this.animator().register(idle, walk, sprint, swim, preparingSleep, sleep, awakening,
                attack, bite, onHead, flyIdle, flight, takeOff, landing, swoop);
        this.animator().registerDeath(death);
    }

    /** True when nothing more important owns the pose. */
    private boolean canPlayLocomotion() {
        return !this.isDeadOrDying() && !this.isOnPlayersHead();
    }


    /**
     * Builds a clip whose definition is chosen by age lazily. The suppliers are not a style choice:
     * {@code AnimationDefinition} is {@code @OnlyIn(Dist.CLIENT)} and {@code registerAnimations()}
     * runs on both sides, so reading the field here would load the class and kill a dedicated server.
     */
    private StandardAnimation clip(String name, Supplier<Object> adult, Supplier<Object> baby,
                                   Loop loop, int priority, float seconds) {
        return new StandardAnimation(name,
                new AnimSource(() -> this.isBaby() ? baby.get() : adult.get()), loop, 0, priority, seconds);
    }

    private StandardAnimation adultClip(String name, Supplier<Object> adult, Loop loop, int priority, float seconds) {
        return new StandardAnimation(name, new AnimSource(adult), loop, 0, priority, seconds);
    }

    // ───────────────────────────────────────────────────── TICK ─────

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.isOnPlayersHead()) {
            this.tickPerch();
        }
    }

    /**
     * Rides the owner's head: pinned to their eye position, facing where they face, and slowing their
     * fall to {@link #CARRY_FALL_CLAMP}. Crouching shrugs it off.
     *
     * <p>Position is written every tick rather than through the passenger system because the mob is
     * not a passenger — making it one would put it in the owner's ride stack and hand vanilla control
     * of dismounting, saddle behaviour and rendering.
     */
    private void tickPerch() {
        if (!(this.getOwner() instanceof Player player) || !player.isAlive()) {
            this.setOnPlayersHead(false);
            return;
        }
        if (player.isCrouching()) {
            this.setOnPlayersHead(false);
            this.setOrderedToSit(false);
            this.setNoGravity(false);
            return;
        }

        // A mob perched mid-flight would leave the flight goals steering against the position this
        // method pins every tick. Landing it here is not a touchdown to animate — it climbed onto a
        // head, it did not come down onto the ground — so the state is cleared without the clip.
        if (this.isFlying() || this.isTakingOff() || this.isLanding()) {
            this.resetFlightState();
        }

        this.setPosRaw(player.getX(), player.getEyeY() + 0.05D, player.getZ());
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        this.fallDistance = 0.0F;
        this.setOnGround(false);
        this.getNavigation().stop();
        this.setOrderedToSit(true);

        float headYaw = player.getYHeadRot();
        this.setYRot(headYaw);
        this.setXRot(0.0F);
        this.yBodyRot = headYaw;
        this.yHeadRot = headYaw;

        Vec3 motion = player.getDeltaMovement();
        boolean falling = motion.y < 0.0D && player.fallDistance > 0.0F
                && !player.onGround() && !player.isInWater();
        if (falling) {
            player.setDeltaMovement(new Vec3(motion.x, Math.max(motion.y, CARRY_FALL_CLAMP), motion.z));
            player.fallDistance = 0.0F;
        }
    }

    /** Perched counts as pinned, so wander and follow goals stand down. */
    @Override
    public boolean isMovementLocked() {
        return super.isMovementLocked() || this.isOnPlayersHead();
    }

    // ───────────────────────────────────────────────────── PERCH ─────

    public boolean isOnPlayersHead() {
        return this.entityData.get(ON_PLAYERS_HEAD);
    }

    public void setOnPlayersHead(boolean value) {
        this.entityData.set(ON_PLAYERS_HEAD, value);
    }

    @Override
    public void stopRiding() {
        super.stopRiding();
        this.setOnPlayersHead(false);
    }

    /** A perched mob keeps its own heading (the owner's), so nothing may turn it. */
    @Override
    public void lookAt(@NotNull Entity target, float maxYawChange, float maxPitchChange) {
        if (!this.isOnPlayersHead()) {
            super.lookAt(target, maxYawChange, maxPitchChange);
        }
    }

    @Override
    public boolean isPushable() {
        return !this.isOnPlayersHead();
    }

    @Override
    protected void doPush(@NotNull Entity entity) {
        if (!this.isOnPlayersHead()) {
            super.doPush(entity);
        }
    }

    // ───────────────────────────────────────────────────── INTERACTION ─────

    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Shift-click by the owner is the sit/follow/wander cycle in SMOPAnimal — let it through
        // before the perch branch below, or the orders could never be given.
        if (this.isTame() && this.isOwnedBy(player) && player.isShiftKeyDown()) {
            return super.mobInteract(player, hand);
        }

        if (stack.is(Items.RABBIT) && !this.isTame()) {
            return this.tryTame(player, stack);
        }

        if (stack.is(Items.CHICKEN) && !this.isBaby() && !this.isInLove()) {
            if (!this.level().isClientSide()) {
                this.setInLove(player);
                stack.consume(1, player);
            }
            return InteractionResult.SUCCESS;
        }

        if (this.isTame() && this.isOwnedBy(player) && !this.isPassenger()) {
            if (!this.level().isClientSide()) {
                this.setOnPlayersHead(!this.isOnPlayersHead());
            }
            return InteractionResult.SUCCESS;
        }

        return super.mobInteract(player, hand);
    }

    private InteractionResult tryTame(Player player, ItemStack stack) {
        if (!this.level().isClientSide()) {
            stack.consume(1, player);
            if (this.random.nextInt(3) == 0) {
                this.tame(player);
                this.level().broadcastEntityEvent(this, (byte) 7);
            } else {
                this.level().broadcastEntityEvent(this, (byte) 6);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFood(@NotNull ItemStack stack) {
        return stack.is(Items.CHICKEN);
    }

    // ───────────────────────────────────────────────────── BIOME COAT ─────

    public String getSpawnBiomePath() {
        return this.entityData.get(SPAWN_BIOME);
    }

    public void setSpawnBiomePath(String path) {
        this.entityData.set(SPAWN_BIOME, path);
    }

    /** Records the biome under the mob, which is what its adult coat is picked from. */
    private void assignBiomeCoat(ServerLevelAccessor level) {
        Identifier key = level.registryAccess()
                .lookupOrThrow(Registries.BIOME)
                .getKey(level.getBiome(this.blockPosition()).value());
        this.setSpawnBiomePath(key != null ? key.getPath() : "default");
    }

    /** Hatched rather than spawned: the coat and sex are rolled here instead of in finalizeSpawn. */
    @Override
    public void onEggBorn(@NotNull ServerLevel level, @NotNull BlockPos pos) {
        this.assignBiomeCoat(level);
        this.setMale(this.getRandom().nextBoolean());
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level,
                                                  @NotNull DifficultyInstance difficulty,
                                                  @NotNull EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData spawnData) {
        this.assignBiomeCoat(level);
        this.setMale(this.getRandom().nextBoolean());
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    public static boolean checkKriftoSpawnRules(EntityType<KriftognathusEntity> type, ServerLevelAccessor level,
                                                EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return checkAnimalSpawnRules(type, level, reason, pos, random);
    }

    // ───────────────────────────────────────────────────── SLEEP ─────

    /** Sleeps through players; wakes for the things that would actually eat it. */
    @Override
    public boolean shouldWakeOnPlayerProximity() {
        return false;
    }

    @Override
    public boolean shouldInterruptSleepDueTo(@NotNull LivingEntity nearby) {
        return this.getInterruptingEntityTypes().contains(nearby.getType());
    }

    @Override
    public @NotNull Set<EntityType<?>> getInterruptingEntityTypes() {
        return Set.of(EntityType.ZOMBIE, EntityType.BEE);
    }

    // ───────────────────────────────────────────────────── SOUNDS ─────

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return SoundEvents.PARROT_AMBIENT;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(@NotNull DamageSource source) {
        return SoundEvents.PARROT_HURT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return SoundEvents.PARROT_DEATH;
    }

    // ───────────────────────────────────────────────────── NBT ─────

    @Override
    protected void addAdditionalSaveData(@NotNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("SpawnBiome", this.getSpawnBiomePath());
        output.putBoolean("OnPlayersHead", this.isOnPlayersHead());
    }

    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setSpawnBiomePath(input.getStringOr("SpawnBiome", "default"));
        this.setOnPlayersHead(input.getBooleanOr("OnPlayersHead", false));
    }
}

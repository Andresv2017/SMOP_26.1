package net.darkblade.smop.entity.krifto;

import net.darkblade.deluxelib.anim.AnimSource;
import net.darkblade.deluxelib.anim.Loop;
import net.darkblade.deluxelib.anim.StandardAnimation;
import net.darkblade.deluxelib.combat.AttackAnchor;
import net.darkblade.deluxelib.combat.AttackShape;
import net.darkblade.deluxelib.combat.HitWindow;
import net.darkblade.deluxelib.entity.ai.goal.AnimatableMeleeAttackGoal;
import net.darkblade.deluxelib.entity.perch.PerchManager;
import net.darkblade.deluxelib.entity.perch.PerchPlacement;
import net.darkblade.deluxelib.entity.perch.Perchable;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
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
 * and — once tamed — perches over its owner's head as a parachute (see {@link #tickPerch}), posing
 * them gripping its legs with both arms via {@code KriftognathusRenderer}.
 */
public class KriftognathusEntity extends SMOPFlyingAnimal
        implements ISleepThreatEvaluator, ISleepAwareness, CustomEggBorn, Perchable {

    // ───────────────────────────────────────────────────── TUNING ─────

    /** Length of the {@code attack} clip, and the frames its beak connects on. */
    private static final float ATTACK_SECONDS = 0.4F;
    private static final int BITE_WINDOW_START = 2;
    private static final int BITE_WINDOW_END = 4;

    /** Matches {@code AnimatableMeleeAttackGoal}'s own reach (2.0F) with room to spare — same
     *  face-lock radius the Tangoftero uses, for the identical reason (see {@link #faceCombatTarget()}). */
    private static final double FACE_LOCK_RADIUS = 4.0D;
    /** Fast snap-to-target, only used at melee range. Cruise flight turns at
     *  {@code getFlightYawTurnSpeed()} (8°/tick) for smooth arcs; landing a bite needs far more. */
    private static final float COMBAT_TURN_SPEED = 40.0F;

    /** How long the glide's slow-falling is granted for, and the remaining duration below which it
     *  is topped back up — see {@link #applyGlideEffect}. Comfortably longer than the refresh point
     *  so the effect can never lapse between ticks. */
    private static final int GLIDE_EFFECT_DURATION = 40;
    private static final int GLIDE_EFFECT_REFRESH_BELOW = 20;

    /** Clip names the flight lifecycle drives, by the same convention as {@code ANIM_SLEEP} and co. */
    private static final String ANIM_START_FLIGHT = "start_flight";
    private static final String ANIM_LANDING = "landing";
    private static final String ANIM_SWOOP = "swoop";
    /** Jaw-only overlay {@code attack} swaps for on a mid-air bite — see {@link #registerAnimations()}. */
    private static final String ANIM_BITE_FLIGHT = "bite_flight";

    /** Wild nest defence: what it will actually see off. */
    private static final Predicate<LivingEntity> NEST_THREAT_SELECTOR =
            entity -> entity.getType() == EntityType.SNIFFER || entity.getType() == EntityType.FOX;

    private static final EntityDataAccessor<String> SPAWN_BIOME =
            SynchedEntityData.defineId(KriftognathusEntity.class, EntityDataSerializers.STRING);
    /** Entity id of the player this one is perched on, or -1. Synced because the whole client half
     *  of perching reads it every tick over every rendered entity — see {@link Perchable#getPerchTargetId()}. */
    private static final EntityDataAccessor<Integer> PERCH_TARGET_ID =
            SynchedEntityData.defineId(KriftognathusEntity.class, EntityDataSerializers.INT);
    /**
     * Perched <em>and</em> actually carrying a falling host — the parachute working, as opposed to
     * just riding along on someone walking about.
     *
     * <p>Synced for the same reason {@code FLYING_MOVING} is: play conditions are evaluated on both
     * sides ({@code MobAnimator}'s auto-start loop runs on the client too), and the host's fall state
     * is a server-side read, so a client computing this locally would disagree and fight the sync
     * packets. The host pose picks off it as well — see {@code KriftognathusRenderer#applyHostPose}.
     */
    private static final EntityDataAccessor<Boolean> PERCH_GLIDING =
            SynchedEntityData.defineId(KriftognathusEntity.class, EntityDataSerializers.BOOLEAN);

    public KriftognathusEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    // ───────────────────────────────────────────────────── PERCH ─────

    /**
     * Perching, not riding. The player is never a passenger: they keep their own body, their own
     * physics and their own WASD, and Krifto is welded above their head — which is the whole shape of
     * a parachute, and the reason the vanilla passenger system was the wrong tool here. Riding makes
     * the VEHICLE authoritative and derives the passenger's position from it, so a parachute built
     * that way has to fight the framework at every step: the mob has to be lifted to head height on
     * mount and kept there, the player's input has to be plumbed back down to drive a vehicle that is
     * really just cargo, and the rider's real position and drawn position have to be kept in sync by
     * hand. Perching inverts exactly those three things for free.
     */
    @Override
    public int getPerchTargetId() {
        return this.entityData.get(PERCH_TARGET_ID);
    }

    /** Perched and carrying a falling host — see {@link #PERCH_GLIDING}. */
    public boolean isPerchGliding() {
        return this.entityData.get(PERCH_GLIDING);
    }

    @Override
    public @NotNull PerchPlacement perchPlacement() {
        return KriftoPerchPlacement.current();
    }

    /** Over the head, where the first-person hand chain cannot represent it — and where you would
     *  not see it from inside your own eyes anyway. */
    @Override
    public boolean renderPerchedInFirstPerson() {
        return false;
    }

    private void startPerching(@NotNull Player player) {
        this.entityData.set(PERCH_TARGET_ID, player.getId());
        this.resetFlightState();
        this.getNavigation().stop();
        this.setTarget(null);
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        // Not the slot-occupying overload: this rides the head, so both hands stay free.
        PerchManager.begin(player, this);
    }

    /**
     * Releases the perch. Also the recovery path for a host who logged out or died: the entity id
     * this was perched on is meaningless across a reload, so nothing about the perch is written to
     * NBT and {@link #tickPerch} drops it the moment the host cannot be resolved.
     */
    private void stopPerching() {
        if (this.level().getEntity(this.getPerchTargetId()) instanceof Player host) {
            PerchManager.end(host);
            this.clearGlideEffect(host);
        }
        this.entityData.set(PERCH_TARGET_ID, -1);
        this.entityData.set(PERCH_GLIDING, false);
        this.setNoGravity(false);
        this.resetFallDistance();
        this.groundRestTimer = this.computeGroundRestTicks();
    }

    /**
     * Server-side release, driven by the library's dismount packet. Re-checks ownership rather than
     * trusting the request, as {@link Perchable#tryStopPerching} requires.
     */
    @Override
    public void tryStopPerching(@NotNull ServerPlayer player) {
        if (this.isPerched() && this.isOwnedBy(player)) {
            this.stopPerching();
        }
    }

    /**
     * Pins the mob to its host and slows their fall — the entire mechanic, in one method.
     *
     * <p>Position is written directly rather than through the passenger system on purpose (see
     * {@link #getPerchTargetId()}); {@link KriftoPerchPlacement} is the single source of both this
     * hitbox placement and the client's drawing of it.
     */
    private void tickPerch() {
        if (!(this.level().getEntity(this.getPerchTargetId()) instanceof Player host) || !host.isAlive()) {
            this.stopPerching();
            return;
        }
        if (host.isShiftKeyDown()) {
            this.stopPerching();
            return;
        }

        PerchPlacement placement = this.perchPlacement();
        float yawRad = host.getYRot() * ((float) Math.PI / 180.0F);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        // Host's right is (-cos, -sin), forward is (-sin, cos) — the same basis PerchClient walks in
        // when it draws this, so what you see and what you can touch stay in one place.
        this.setPosRaw(
                host.getX() - cos * placement.side() - sin * placement.forward(),
                host.getY() + placement.height(),
                host.getZ() - sin * placement.side() + cos * placement.forward());
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        this.fallDistance = 0.0F;
        this.setOnGround(false);
        this.getNavigation().stop();

        float yaw = host.getYRot();
        this.setYRot(yaw);
        this.setXRot(0.0F);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;

        this.applyGlideEffect(host);
        this.entityData.set(PERCH_GLIDING,
                !host.onGround() && !host.isInWater() && host.getDeltaMovement().y < 0.0D);
    }

    /**
     * The parachute itself. Slow falling rather than a hand-written velocity clamp, and the
     * difference is not stylistic:
     *
     * <p>A player's movement is client-authoritative. Writing {@code setDeltaMovement} on the
     * server's copy does nothing unless it is force-synced ({@code hurtMarked}), and force-syncing it
     * every tick ships a velocity packet that overwrites whatever the client just simulated — which
     * takes their air control with it. That is exactly why WASD died mid-fall.
     *
     * <p>{@code SLOW_FALLING} is computed inside {@code LivingEntity}'s own physics
     * (gravity clamped to 0.01 while falling, and {@code fallDistance} reset every tick, so fall
     * damage is handled too), so the client applies it itself. No packets, no fight, steering intact.
     *
     * <p>Applied invisibly and refreshed only as it runs low, rather than re-added every tick, so it
     * neither shows up in the HUD nor spams effect-update packets.
     */
    private void applyGlideEffect(@NotNull Player host) {
        MobEffectInstance current = host.getEffect(MobEffects.SLOW_FALLING);
        if (current == null || current.getDuration() < GLIDE_EFFECT_REFRESH_BELOW) {
            host.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, GLIDE_EFFECT_DURATION, 0,
                    false, false, false));
        }
    }

    /** Drops the glide effect, but only if it looks like ours — a potion-brewed slow falling is
     *  visible, this one is not, so an unperch never strips a buff the player actually drank. */
    private void clearGlideEffect(@NotNull Player host) {
        MobEffectInstance current = host.getEffect(MobEffects.SLOW_FALLING);
        if (current != null && !current.isVisible()) {
            host.removeEffect(MobEffects.SLOW_FALLING);
        }
    }

    /** Perched counts as pinned, so every movement goal stands down. */
    @Override
    public boolean isMovementLocked() {
        return super.isMovementLocked() || this.isPerched();
    }

    /** Welded to a host, so nothing may shove it off — {@link #tickPerch} would snap it back anyway,
     *  which reads as jitter rather than a push. */
    @Override
    public boolean isPushable() {
        return !this.isPerched();
    }

    @Override
    protected void doPush(@NotNull net.minecraft.world.entity.Entity entity) {
        if (!this.isPerched()) {
            super.doPush(entity);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.FOLLOW_RANGE, 28.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.20D)
                // Was 0.25 — barely above ground speed, which read as crawling in the air.
                // Arpy keeps flight at ~2.2x its walk speed; matching that ratio here.
                .add(Attributes.FLYING_SPEED, 0.45D)
                .add(Attributes.ATTACK_SPEED, 0.4D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.1D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SPAWN_BIOME, "default");
        builder.define(PERCH_TARGET_ID, -1);
        builder.define(PERCH_GLIDING, false);
    }

    // ───────────────────────────────────────────────────── GOALS ─────

    /**
     * Flight goals go in via {@link #registerFlightGoals}: take-off at 2, <b>landing at 3</b>, flight
     * wander at 7.
     *
     * <p>The landing goal sits that high on purpose. It holds MOVE, and so do the melee goal and the
     * escort — put those above it and a mob that acquires a target (or an owner) mid-descent starves
     * the landing goal of its flag and hangs in the landing state with nothing driving it down. A
     * bird committed to a touchdown finishes it first. Take-off deliberately holds no flags at all,
     * so the ground goals below keep running while it lifts off; {@link KriftoTakeoffGoal} also lets
     * a live target skip the ground-rest timer, so a threat does not have to wait out a nap.
     *
     * <p>The melee goal sits at 4, <b>above</b> the flying escort at 5 — both hold MOVE, and without
     * that ordering the escort would win every arbitration and a defending Krifto would just keep
     * flying formation next to the owner instead of breaking off to bite whatever attacked them (the
     * same ordering {@code OwlEntity} uses: {@code DefendOwnerGoal} at 1, above its own follow goal
     * at 2). The escort takes over once the mob is in the air and asks for a take-off when it is not;
     * {@link FollowOwnerBaseGoal} at 8 walks it over at close range on the ground. That one has to sit
     * <b>above</b> the stroll goal, or wandering would win every arbitration and the mob would never
     * actually follow on foot.
     */
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, this.createSleepGoal());
        this.registerFlightGoals(2, 7, 3);
        // Speed here is what actually governs mid-air pursuit: AnimatableMeleeAttackGoal (via
        // vanilla MeleeAttackGoal) hands this straight to getNavigation().moveTo(target, speed),
        // which SmoothFlyingMoveControl multiplies by the FLYING_SPEED attribute every tick — the
        // one goal with priority over this is landing, so this is the number that decides how fast
        // Krifto closes on whatever it is chasing. 1.8 -> 2.2 for a bit more urgency once it has a
        // target, without touching FLYING_SPEED itself and speeding up ordinary cruising too.
        this.goalSelector.addGoal(4, new AnimatableMeleeAttackGoal(this, 2.4D, true)
                .reach(2.0F)
                .cooldown(10)
                .attackCondition(target -> !this.isBaby())
                // Grounded: the full-body pounce. Airborne: just the jaw snap (ANIM_BITE_FLIGHT) —
                // "attack" crouches and pumps the legs for a takeoff off the ground that isn't there.
                .onAttack((target, animator) -> animator.play(
                        animator.getByName(this.isFlying() ? ANIM_BITE_FLIGHT : "attack"))));
        this.goalSelector.addGoal(5, new FollowOwnerFlyingGoal(this, 8.0F));
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

        // Krifto is a real TamableAnimal (unlike DeluxeLib's Owl, which has to hand-roll owner
        // tracking), so the vanilla owner-defence goals apply directly — same pair the Tangoftero
        // already uses. Whoever last hurt the owner outranks whoever the owner last hurt, and both
        // outrank retaliating for a hit Krifto took itself.
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
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

    /**
     * Scaled from Arpy's own tuning (0.45 on a 1.4-block-tall hitbox, i.e. ~32% of its own height)
     * rather than copied outright: Arpy's value applied unscaled to Krifto's 1.0-block hitbox
     * ({@link net.darkblade.smop.entity.SMOPEntities#KRIFTOGNATHUS}) left the landing flare
     * kicking in over a block and a half up — more than Krifto's whole body height — which read
     * as hovering well short of the ground on a bird this small. 1.0 * (0.45 / 1.4) ≈ 0.32.
     */
    @Override
    protected double getLandingApproachAltitude() {
        return 0.32D;
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
        /**
         * A live target skips the ground-rest timer, same as {@code ArpyTakeoffGoal} in DeluxeLib —
         * without this, a Krifto standing guard on the ground only launches to defend once its nap
         * happens to run out, which reads as not defending at all.
         */
        @Override
        public boolean canUse() {
            if (super.canUse()) {
                return true;
            }
            LivingEntity target = KriftognathusEntity.this.getTarget();
            return !KriftognathusEntity.this.isBaby()
                    && !KriftognathusEntity.this.isFlying()
                    && !KriftognathusEntity.this.isTakingOff()
                    && !KriftognathusEntity.this.isLanding()
                    && !KriftognathusEntity.this.isOrderedToSit()
                    && !KriftognathusEntity.this.isMovementLocked()
                    && target != null && target.isAlive();
        }

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
        // Same hover, hind legs splayed so the host has something to hold — see aidle_perched.
        StandardAnimation flyIdlePerched = adultClip("fly_idle_perched",
                () -> KriftoAnimations.aidle_perched, Loop.REPEATING, 2, 0.6F);
        StandardAnimation flight = adultClip("flight", () -> KriftoAnimations.flight, Loop.REPEATING, 2, 0.8F);
        StandardAnimation takeOff = adultClip(ANIM_START_FLIGHT, () -> KriftoAnimations.start_flight, Loop.PLAY_ONCE, 1, 1.0F);
        StandardAnimation landing = adultClip(ANIM_LANDING, () -> KriftoAnimations.landing, Loop.PLAY_ONCE, 1, 1.3F);
        StandardAnimation swoop = adultClip(ANIM_SWOOP, () -> KriftoAnimations.swoop, Loop.PLAY_ONCE, 1, 1.6F);
        // Layer 1, not layer 0: this is the only animation on that layer (see KriftognathusModel's
        // second keyframeBlend), so it composites additively over flyIdle/flight instead of
        // replacing them the way a same-layer clip would. Adult-only, same as the rest of the air
        // family — a baby can never be flying when this would fire.
        StandardAnimation biteFlight = new StandardAnimation(ANIM_BITE_FLIGHT,
                new AnimSource(() -> KriftoAnimations.bite_flight), Loop.PLAY_ONCE, 1, 0, ATTACK_SECONDS);

        idle.blendInMs(300).blendOutMs(250);
        walk.blendInMs(200).blendOutMs(200);
        sprint.blendInMs(200).blendOutMs(200);
        // Aerial poses are broad and slow-moving, so their crossfades breathe more than the ground
        // family's — the same balance the Arpy strikes.
        flyIdle.blendInMs(350).blendOutMs(250);
        flyIdlePerched.blendInMs(350).blendOutMs(250);
        flight.blendInMs(250).blendOutMs(250);
        // Same knob Arpy uses to pace its wing strokes (flySprint.playbackSpeed(1.2F)) — the
        // authored clip alone read as a lazy flap next to the faster cruise speed above.
        flight.playbackSpeed(1.2F);
        takeOff.blendInMs(100).blendOutMs(300);
        landing.blendInMs(150).blendOutMs(250);
        swoop.blendInMs(200).blendOutMs(300);
        // Fast in/out: layer 1 has nothing else on it to blend from or to, so the overlay should
        // snap in and out rather than linger and read as a stuck jaw between bites.
        biteFlight.blendInMs(60).blendOutMs(100);

        // The beak: a strictly frontal box across the frames it snaps shut.
        //
        // filter excludes other Kriftos EXCEPT the one actually being fought: without the
        // `target == this.getTarget()` escape hatch, this unconditionally vetoed every Krifto
        // candidate before the shape test ever ran — no amount of retuning the box, the anchor or
        // the facing could ever have connected, since the target was thrown out earlier in
        // AttackHitbox#sweep, at the getEntitiesOfClass predicate. It read as a geometry miss (the
        // debug particles are drawn from candidates that DID pass the shape test — a target excluded
        // here never reaches that code at all) but every earlier fix in this file was chasing the
        // wrong layer. The blanket exclusion still stands for bystander Kriftos this mob isn't
        // fighting — a wide swing shouldn't maul a flockmate standing nearby.
        HitWindow.of(BITE_WINDOW_START, BITE_WINDOW_END)
                .shape(AttackShape.box(1.5F, 0.5F))
                .anchor(0.7F, 0.0F, 0.6F)
                .damage(2.0F)
                .knockback(0.1F)
                .filter(target -> target == this.getTarget() || !(target instanceof KriftognathusEntity))
                .applyTo(attack);
        // Independent HitWindow instance (its hitThisSwing/lastSweepAngle state must not be shared
        // with attack's) — same damage as the pounce, but a real 3D cuboid instead of attack's
        // flattened one. A ground pounce is fine testing only the horizontal plane; an airborne bite
        // is not — mid-chase the target is as often above or below as beside, and box() silently
        // ignores the Y axis, so a flattened forward projection near a dive's nose-down angle
        // shrinks toward zero exactly when the strike should land. box3d() plus a full 3D facing
        // (aimAlongLook(), backed by faceCombatTarget()'s yaw lock and vanilla LookControl's own
        // pitch tracking — both run every tick a target is set) tests the real cuboid in front of
        // the beak.
        //
        // The 0.7F forward on attack's anchor is BODY_YAW space — measured from the FEET, where the
        // neck and head add real distance before the beak. AttackAnchor.look is EYES-space: the eyes
        // already sit right behind the beak, so carrying that same 0.7F over here pushed the origin
        // ~0.7 blocks PAST the beak's actual tip — overshooting clean through a target standing at
        // point-blank range (exactly the "target's right there and it still misses" case: forward()
        // in Box3D#contains landed negative, behind the origin, because the origin itself had already
        // sailed past the target's centre). 0.2F keeps the origin just ahead of the eyes instead.
        HitWindow.of(BITE_WINDOW_START, BITE_WINDOW_END)
                .shape(AttackShape.box3d(1.5F, 0.5F, 0.5F))
                .anchor(AttackAnchor.look(0.2F, 0.0F, 0.0F))
                .aimAlongLook()
                .damage(2.0F)
                .knockback(0.1F)
                // Same escape hatch as attack's filter above — the actual bug behind every "misses
                // only against other Kriftos, hits the player and the Tangoftero fine" report.
                .filter(target -> target == this.getTarget() || !(target instanceof KriftognathusEntity))
                .applyTo(biteFlight);

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

        // Carrying a falling host: the same hover, but from the splay-legged copy. isFlying() is
        // deliberately false while perched (startPerching resets the flight lifecycle), so this is
        // mutually exclusive with flyIdle above rather than competing with it.
        flyIdlePerched.setPlayCondition(a -> this.canPlayLocomotion() && this.isPerchGliding());

        // Perched: priority 1 out-renders the locomotion still running underneath, and it chains
        // nothing — unperching simply lets that locomotion show through again. Stands down while the
        // host is falling so the hover above takes the frame; the two are mutually exclusive.
        onHead.setPlayCondition(a -> this.isPerched() && !this.isPerchGliding());

        preparingSleep.setPlayCondition(a -> this.isPreparingSleep());
        awakening.setPlayCondition(a -> this.isAwakening());
        // Eligible through the settling phase too, so the loop is armed the instant the settle clip
        // ends whichever way the two clocks land — see the Tangoftero for the full reasoning.
        sleep.setPlayCondition(a -> this.isSleeping() || this.isPreparingSleep());
        preparingSleep.setNextAnimation(ANIM_SLEEP);

        death.blockAdditive();

        this.animator().register(idle, walk, sprint, swim, preparingSleep, sleep, awakening,
                attack, bite, onHead, flyIdle, flyIdlePerched, flight, takeOff, landing, swoop, biteFlight);
        this.animator().registerDeath(death);
    }

    /** True when nothing more important owns the pose. */
    private boolean canPlayLocomotion() {
        return !this.isDeadOrDying();
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
        if (this.level().isClientSide()) {
            return;
        }
        if (this.isPerched()) {
            this.tickPerch();
        } else {
            this.faceCombatTarget();
        }
    }

    /**
     * Keeps the body pointed at the target for as long as it is in melee range.
     *
     * <p>{@code AnimatableMeleeAttackGoal} stops the navigation the instant the target is within
     * its reach — exactly the ticks the bite's {@code HitWindow} sweeps on — and neither MoveControl
     * (ground {@code MoveControl} or flying {@code SmoothFlyingMoveControl}) turns the body once
     * navigation has stopped. Without this the yaw freezes wherever it last was, and the bite —
     * a body-yaw-facing frontal box, no explicit {@code HitWindow#facing(...)} override — swings at
     * whatever direction that happened to be instead of at the target. That is the whiff the debug
     * hitbox particles were showing on every attack, airborne or grounded, moving target or not:
     * the box itself was landing exactly where the frozen yaw pointed it, just not on anyone.
     *
     * <p>Same bug, same fix as the Tangoftero's own {@code faceCombatTarget()} — this one reuses
     * {@code SMOPFlyingAnimal#faceHeading} instead of a {@code DirectionalMoveControl} swap, since
     * that utility already works from either move state and this mob needs both.
     */
    private void faceCombatTarget() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || this.isMovementLocked() || this.isDeadOrDying()) {
            return;
        }
        if (this.distanceToSqr(target) <= FACE_LOCK_RADIUS * FACE_LOCK_RADIUS) {
            this.faceHeading(target.getX() - this.getX(), target.getZ() - this.getZ(), COMBAT_TURN_SPEED);
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

        // Grab on: perch it over the owner's head as a parachute (see #getPerchTargetId). Owner-only,
        // and adult-only since the chick model has no rig for the grip pose.
        if (this.isTame() && this.isOwnedBy(player) && !this.isBaby() && !this.isPassenger()) {
            if (!this.level().isClientSide()) {
                if (this.isPerched()) {
                    this.stopPerching();
                } else {
                    this.startPerching(player);
                }
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
    }

    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setSpawnBiomePath(input.getStringOr("SpawnBiome", "default"));
    }
}

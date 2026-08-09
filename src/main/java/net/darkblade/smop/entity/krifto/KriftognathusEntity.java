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
import net.darkblade.smop.entity.ai.goal.StealFromPlayerGoal;
import net.darkblade.smop.entity.ai.goal.TameFeedGoal;
import net.darkblade.smop.entity.ai.goal.egg.EggGoalRegistry;
import net.darkblade.smop.entity.ai.goal.egg.ProtectEggBaseGoal;
import net.darkblade.smop.entity.ai.goal.flying.FollowOwnerFlyingGoal;
import net.darkblade.smop.entity.egg.CustomEggBorn;
import net.darkblade.smop.entity.sleep.ISleepAwareness;
import net.darkblade.smop.entity.sleep.ISleepThreatEvaluator;
import net.darkblade.smop.entity.sleep.SleepPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
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
import net.minecraft.world.entity.item.ItemEntity;
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
import java.util.UUID;
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
    /** The committed power dive. Not part of the landing cycle — see {@link #onSeekGroundBegin()}. */
    private static final String ANIM_SWOOP = "swoop";
    /** Jaw-only overlay {@code attack} swaps for on a mid-air bite — see {@link #registerAnimations()}. */
    private static final String ANIM_BITE_FLIGHT = "bite_flight";
    /** Also the {@code startAction} name TameFeedGoal closes the ritual with. */
    public static final String ANIM_TAMED = "tamed";
    /** Extra ground time after the {@code tamed} clip. @see #onActionStart(String) */
    private static final int TAMED_GROUND_HOLD_TICKS = 40;

    /** Wild nest defence: what it will actually see off. */
    private static final Predicate<LivingEntity> NEST_THREAT_SELECTOR =
            entity -> entity.getType() == EntityType.SNIFFER || entity.getType() == EntityType.FOX;

    /** Feedings required to complete the ground-taming ritual — see {@link TameFeedGoal}. */
    private static final int FEED_GOAL_MIN = 3;
    private static final int FEED_GOAL_MAX = 4;

    /**
     * How much longer the sleep-cycle transitions take than they were authored. The clips are 0.3–0.5 s
     * — six to ten ticks — which is quick enough that the mob reads as snapping between poses rather
     * than moving between them. One knob for all four; see {@link #slowTransition}.
     */
    private static final float TRANSITION_SLOWDOWN = 2.5F;

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
    /**
     * What a wild Krifto is currently carrying off in its hind legs after a successful heist — see
     * {@link StealFromPlayerGoal}. {@link ItemStack#EMPTY} when nothing was stolen.
     *
     * <p>Deliberately not written to NBT: a heist runs its course in a matter of seconds (orbit, dive,
     * snatch, flee, drop), so a save/load landing in that narrow window losing the item is an
     * acceptable trade against hand-rolling {@code ItemStack} codec plumbing nothing else in this mod
     * needs yet — same call as the scripted-action state in {@code SMOPAnimal}.
     */
    private static final EntityDataAccessor<ItemStack> STOLEN_ITEM =
            SynchedEntityData.defineId(KriftognathusEntity.class, EntityDataSerializers.ITEM_STACK);

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
        // The entity id above is this session's; the UUID is what survives a reload. @see #tickPerchRestore
        this.perchHostId = player.getUUID();
        this.perchRestoreTicks = 0;
        this.resetFlightState();
        this.getNavigation().stop();
        this.setTarget(null);
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        // Not the slot-occupying overload: this rides the head, so both hands stay free.
        PerchManager.begin(player, this);
    }

    /** Deliberate release — sneaking, or the bird being told to get off. Forgets the host with it. */
    private void stopPerching() {
        this.endPerch(true);
    }

    /**
     * Releases the perch, optionally keeping the host on file.
     *
     * <p>The distinction matters at exactly one moment: a player logging out. {@link #tickPerch} drops
     * the perch as soon as the host stops resolving, and if that tick lands before the world is
     * written, clearing the UUID there would erase the very thing
     * {@link #addAdditionalSaveData} needs — the bird would come back off the head every single time,
     * which is the bug this pairs with. So a host who merely <em>vanished</em> is remembered, and only
     * an explicit dismount forgets.
     */
    private void endPerch(boolean forgetHost) {
        if (this.level().getEntity(this.getPerchTargetId()) instanceof Player host) {
            PerchManager.end(host);
            this.clearGlideEffect(host);
        }
        this.entityData.set(PERCH_TARGET_ID, -1);
        this.entityData.set(PERCH_GLIDING, false);
        if (forgetHost) {
            this.perchHostId = null;
        }
        this.perchRestoreTicks = 0;
        this.setNoGravity(false);
        this.resetFallDistance();
        this.groundRestTimer = this.computeGroundRestTicks();
    }

    // ───────────────────────────────────────────────────── PERCH ACROSS RELOAD ─────

    /**
     * UUID of the host this is perched on, or {@code null}. The synced {@link #PERCH_TARGET_ID} is an
     * <em>entity id</em>, which is assigned per session and means nothing after a reload — this is the
     * half of the perch that can actually be written to NBT.
     */
    @Nullable
    private UUID perchHostId;
    /** Ticks left to find {@link #perchHostId} after a reload before giving up. @see #tickPerchRestore */
    private int perchRestoreTicks;

    /** How long to wait for the host to turn up. Long enough for a login to finish, short enough that
     *  a bird whose owner never returns is not stuck waiting. */
    private static final int PERCH_RESTORE_WINDOW_TICKS = 100;

    /**
     * Puts the bird back on the head it was on before the world was saved.
     *
     * <p>Perching is held in two places, and only one of them survives: the synced entity id (gone —
     * entity data is not persisted) and {@code PerchManager}'s registry (gone — an in-memory
     * {@code Map<UUID, Perchable>}). So the perch has to be rebuilt from the one durable thing, the
     * host's UUID, once that player is resolvable again. The player usually loads before the entity
     * ticks, so in practice this succeeds on the first attempt and the bird is simply still there.
     *
     * <p>{@link #readAdditionalSaveData} has already dropped the no-gravity by this point, so a host
     * who never appears leaves a bird that falls normally rather than one hanging in the sky — which
     * is exactly the bug this pairs with. Failing to restore is therefore safe, and bounded.
     */
    private void tickPerchRestore() {
        if (this.perchHostId == null || this.isPerched() || this.perchRestoreTicks <= 0) {
            return;
        }
        if (this.isBaby()) {
            // Can't happen through mobInteract (adult-only, see there), but this reads the saved
            // UUID back unconditionally, so it gets its own check rather than trusting that gate
            // stays the only path in here forever. Forgets the host outright rather than leaving the
            // countdown to run out on its own: a baby is never getting a host back regardless of how
            // long tickPerchRestore keeps being asked, so there is nothing to wait out.
            this.perchHostId = null;
            this.perchRestoreTicks = 0;
            return;
        }
        Player host = this.level().getPlayerByUUID(this.perchHostId);
        if (host != null && host.isAlive() && this.isOwnedBy(host)) {
            this.startPerching(host);
            return;
        }
        // Window closed without the host turning up. The UUID is deliberately NOT cleared: they may
        // simply be offline, or in another dimension, and the next load gets a fresh window. Nothing
        // is left running in the meantime — the countdown is only ever armed by a load.
        this.perchRestoreTicks--;
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
            // Gone, not dismissed — a logout looks exactly like this. Keep the host on file so the
            // bird is back on their head next time they load in. @see #endPerch
            this.endPerch(false);
            return;
        }
        // Sneaking is THE way off, and the only one. Right-clicking the bird does not toggle it back
        // down (see #mobInteract) — a hitbox welded to your own head is awkward to aim at, and the
        // library's own right-click-anywhere release is the Owl's gesture, gated to the Owl.
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
        builder.define(STOLEN_ITEM, ItemStack.EMPTY);
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
        // Speed modifier for the GROUND chase only — on foot this is a normal pathfinding pursuit and
        // the number is handed to getNavigation().moveTo(target, speed). Airborne, the pursuit is
        // flown by direct steering instead and CHASE_FLY_SPEED is what governs it; see
        // KriftoAttackGoal for why pathing loses the air.
        this.goalSelector.addGoal(4, new KriftoAttackGoal(2.4D)
                .reach(2.0F)
                .cooldown(10)
                .attackCondition(target -> !this.isBaby())
                // Grounded: the full-body pounce. Airborne: just the jaw snap (ANIM_BITE_FLIGHT) —
                // "attack" crouches and pumps the legs for a takeoff off the ground that isn't there.
                .onAttack((target, animator) -> animator.play(
                        animator.getByName(this.isFlying() ? ANIM_BITE_FLIGHT : "attack"))));
        this.goalSelector.addGoal(5, new FollowOwnerFlyingGoal(this, 8.0F));
        // Shares priority 5 with the escort above — mutually exclusive by construction (that one
        // needs isTame() + an owner, this one needs !isTame()), so the two never actually contest
        // MOVE/LOOK. Below LandingGoal/TakeoffGoal (3/2): see StealFromPlayerGoal's class note.
        this.goalSelector.addGoal(5, new StealFromPlayerGoal(this));
        this.goalSelector.addGoal(6, new GenericBreedGoal<>(this, 1.2D));
        // Tied with FlightWanderGoal (7, via registerFlightGoals below): WrappedGoal#canBeReplacedBy
        // only yields the flag on a strict `<`, so a tie means this goal can never steal MOVE/LOOK
        // back from FlightWanderGoal once it is running — see TameFeedGoal's class note for why that
        // matters (an earlier version at priority 6 permanently starved FlightWanderGoal's tick()).
        this.goalSelector.addGoal(7, new TameFeedGoal(this));

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
                // An offering on the ground outranks the wander schedule — launching mid-ritual
                // strands the meal for a whole flight cycle. Only the scheduled take-off is held;
                // the target bypass below still fires, since a fight is worth leaving lunch for
                // (and TameFeedGoal stands down on its own once there is a target).
                return !KriftognathusEntity.this.hasFeedOfferingNearby();
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

    /**
     * The melee goal, with the airborne half of the chase flown rather than pathed.
     *
     * <p><b>Why.</b> {@code MeleeAttackGoal} pursues by {@code getNavigation().moveTo(target, speed)}.
     * On foot that is exactly right. In the air it means {@code SmartFlyingNavigation} pathfinds a
     * polyline through 3D air nodes and the mob then flies that polyline — so it corners around
     * waypoints instead of cutting the straight line, re-plans on a 4-20 tick timer while the target
     * keeps moving, and stair-steps between nodes. That is the clumsy, sluggish pursuit; DeluxeLib's
     * own {@code steerTowards} note calls out the same "stair-step bouncing that flying path
     * navigation causes", and the Owl's dive attack sidesteps it by never pathing at all.
     *
     * <p><b>How.</b> {@code super.tick()} still runs in full — it owns the look-at, the attack
     * interval, the cooldown and {@code checkAndPerformAttack}, and its counters are private, so
     * skipping it would let the Krifto attack exactly once. What changes is only what happens after:
     * while flying, the path it just built is discarded and the mob is steered straight at the target
     * instead. Dropping the path is what keeps {@code SmoothFlyingMoveControl} out of the way — with
     * no path, {@code PathNavigation#tick} never calls {@code setWantedPosition}, and that control
     * flips itself back to {@code WAIT} after a single tick rather than fighting the velocity written
     * here.
     */
    private class KriftoAttackGoal extends AnimatableMeleeAttackGoal {

        /** Blocks per tick of the air chase. Well over cruise: this is a stoop, not a commute. */
        private static final double CHASE_FLY_SPEED = 0.85D;
        /** Heading blend per tick. High enough to stay glued to a dodging target without snapping. */
        private static final double CHASE_ACCEL = 0.35D;

        KriftoAttackGoal(double groundSpeed) {
            super(KriftognathusEntity.this, groundSpeed, true);
        }

        /**
         * Every tick, not every other one. {@code Mob#serverAiStep} only runs the full goal selector
         * on alternate ticks; without this the steering below writes velocity at 10 Hz against 20 Hz
         * physics and the chase visibly stutters between corrections.
         */
        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            super.tick();
            if (!KriftognathusEntity.this.isFlying()) {
                return;
            }
            LivingEntity target = KriftognathusEntity.this.getTarget();
            if (target == null) {
                return;
            }
            KriftognathusEntity.this.getNavigation().stop();
            // Aim at the upper body rather than the feet, so the beak arrives where the hitbox is
            // thickest instead of skimming along the ground under a tall target.
            KriftognathusEntity.this.steerTowards(
                    target.position().add(0.0D, target.getBbHeight() * 0.6D, 0.0D),
                    CHASE_FLY_SPEED, CHASE_ACCEL);
        }
    }

    // ───────────────────────────────────────────────────── FLIGHT ANIMATION HOOKS ─────

    @Override
    protected void onTakeoffBegin() {
        this.playIfRegistered(ANIM_START_FLIGHT);
    }

    /**
     * The descent toward a landing deliberately plays <b>nothing</b>: the ordinary flight cycle is
     * {@code start_flight} → {@code fly_idle}/{@code flight} → {@code landing}, and the hover simply
     * keeps running until the landing flare takes over. {@code swoop} is not part of it — that clip is
     * a committed power dive, reserved for {@link #playSwoopClip()}.
     */
    @Override
    protected void onSeekGroundBegin() {
    }

    /**
     * The power dive, for {@code StealFromPlayerGoal} committing to its run at a player. This is the
     * only thing that plays {@code swoop}; {@code playIfRegistered} is protected, hence this thin
     * opening for the goal to reach it.
     */
    public void playSwoopClip() {
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

        // Six-phase sleep: sitting_down -> sitting -> preparing_sleep -> sleep -> awakening ->
        // standing_up. SleepGoal assembles that from whichever clips are registered here; see
        // SleepPhase. Both rigs author all six now, so chick and adult run the same cycle.
        //
        // Durations are the ADULT clip lengths, and they have to stay that way: BaseAnimation stops a
        // clip when its declared duration runs out, so a number longer than the clip freezes the mob
        // on the last frame and a shorter one cuts the motion. These were 2.5/4.0/3.5 — the lengths
        // from before the re-export — which left the adult held still for two seconds lying down and
        // over three getting up.
        // The four transitions are stretched by TRANSITION_SLOWDOWN; the two holds (sleep, sit) play
        // at their authored rate, since a loop's speed is its own business. slowTransition takes the
        // AUTHORED length — the stretched duration and the playback rate are derived together, and
        // must be, or the clip gets cut partway through.
        StandardAnimation preparingSleep = slowTransition("preparing_sleep",
                () -> KriftoAnimations.sleep_preparing, () -> KriftoBabyAnimations.sleep_preparing, 0.5F);
        StandardAnimation sleep = clip("sleep", () -> KriftoAnimations.sleep, () -> KriftoBabyAnimations.sleep,
                Loop.REPEATING, 1, 2.0F);
        StandardAnimation awakening = slowTransition("awakening",
                () -> KriftoAnimations.awakening, () -> KriftoBabyAnimations.awakening, 0.3F);
        StandardAnimation sittingDown = slowTransition("sitting",
                () -> KriftoAnimations.sitting, () -> KriftoBabyAnimations.sitting, 0.3F);
        StandardAnimation sitting = clip("sit", () -> KriftoAnimations.sit, () -> KriftoBabyAnimations.sit,
                Loop.REPEATING, 1, 2.0F);
        StandardAnimation standingUp = slowTransition("standing_up",
                () -> KriftoAnimations.standing_up, () -> KriftoBabyAnimations.standing_up, 0.3F);

        // The ground pounce is the newer sprint_bite clip on both rigs; the old attack clip it
        // replaces is gone from either animation file. The registered NAME stays "attack" — that is
        // what AnimatableMeleeAttackGoal looks up by, and what the HitWindow below is applied to.
        // Same shape as the clip it replaces (0.4 s, one-shot), so ATTACK_SECONDS and the bite window
        // carry over untouched.
        StandardAnimation attack = clip("attack", () -> KriftoAnimations.sprint_bite, () -> KriftoBabyAnimations.sprint_bite,
                Loop.PLAY_ONCE, 0, ATTACK_SECONDS);
        StandardAnimation bite = clip("bite", () -> KriftoAnimations.bite, () -> KriftoBabyAnimations.bite,
                Loop.PLAY_ONCE, 0, 0.75F);
        StandardAnimation death = clip("death", () -> KriftoAnimations.death, () -> KriftoBabyAnimations.death,
                Loop.PLAY_ONCE, 0, 1.5F);
        StandardAnimation onHead = clip("on_players_head",
                () -> KriftoAnimations.on_players_head, () -> KriftoBabyAnimations.on_players_head,
                Loop.REPEATING, 1, 2.4F);

        // Ground-taming ritual — see TameFeedGoal. Driven by SMOPAnimal's scripted-action state
        // (startAction/isPerforming), not by a dedicated synced flag of its own.
        StandardAnimation eating = clip("eating", () -> KriftoAnimations.eating, () -> KriftoBabyAnimations.eating,
                Loop.PLAY_ONCE, 1, 2.5F);
        StandardAnimation tamed = clip("tamed", () -> KriftoAnimations.tamed, () -> KriftoBabyAnimations.tamed,
                Loop.PLAY_ONCE, 1, 2.5F);
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

        sittingDown.setPlayCondition(a -> this.sleepPhase() == SleepPhase.SITTING_DOWN);
        // Same trick as sleep above: eligible through the clip that leads into it, so the loop is
        // already armed on the tick the one-shot ends.
        sitting.setPlayCondition(a -> this.isSitting() || this.sleepPhase() == SleepPhase.SITTING_DOWN);
        standingUp.setPlayCondition(a -> this.sleepPhase() == SleepPhase.STANDING_UP);
        sittingDown.setNextAnimation("sit");

        eating.setPlayCondition(a -> this.isPerforming("eating"));
        tamed.setPlayCondition(a -> this.isPerforming(ANIM_TAMED));

        death.blockAdditive();

        this.animator().register(idle, walk, sprint, swim, preparingSleep, sleep, awakening,
                sittingDown, sitting, standingUp,
                attack, bite, onHead, eating, tamed, flyIdle, flyIdlePerched, flight, takeOff, landing, swoop, biteFlight);
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

    /**
     * Builds one of the sleep-cycle transitions, stretched by {@link #TRANSITION_SLOWDOWN}.
     *
     * <p><b>Two numbers, one decision — which is why this exists rather than setting them at the call
     * sites.</b> {@code playbackSpeed} is a purely client-side visual rate ({@code BlendState} scales
     * its own clock by it) and has no effect whatsoever on {@code durationTicks}, which is what stops
     * the animation and what {@code SleepGoal} times the phase by. Slowing only the first truncates
     * the motion: the clip is cut at its authored length having played a fraction of the way through.
     * So the authored length goes in once and both come out of it.
     */
    private StandardAnimation slowTransition(String name, Supplier<Object> adult, Supplier<Object> baby,
                                             float authoredSeconds) {
        return slowed(this.clip(name, adult, baby, Loop.PLAY_ONCE, 1, authoredSeconds * TRANSITION_SLOWDOWN));
    }

    /** @see #slowTransition */
    private StandardAnimation slowAdultTransition(String name, Supplier<Object> adult, float authoredSeconds) {
        return slowed(this.adultClip(name, adult, Loop.PLAY_ONCE, 1, authoredSeconds * TRANSITION_SLOWDOWN));
    }

    private static StandardAnimation slowed(StandardAnimation anim) {
        anim.playbackSpeed(1.0F / TRANSITION_SLOWDOWN);
        return anim;
    }

    // ───────────────────────────────────────────────────── TICK ─────

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        this.tickFeedOffering();
        this.tickPerchRestore();
        if (this.isPerched()) {
            this.tickPerch();
        } else {
            this.faceCombatTarget();
        }
    }

    /** Ticks between offering scans. @see #tickFeedOffering() */
    private static final int OFFERING_SCAN_INTERVAL = 10;

    /** Server-only cache of {@link TameFeedGoal#findOffering}. @see #tickFeedOffering() */
    @Nullable
    private ItemEntity feedOffering;

    /**
     * Keeps the taming ritual reachable from the air, and keeps it from being abandoned halfway.
     *
     * <p>{@link TameFeedGoal} stands down completely while airborne — it has to, or it holds MOVE
     * over {@code FlightWanderGoal} and starves the one goal that actually drives a descent. That
     * leaves nobody inside the goal system watching for an offering while the mob is flying, so the
     * entity watches instead and works the two lifecycle levers from out here:
     *
     * <ul>
     *   <li>Airborne: {@link #requestLanding()}, so the ordinary stoop-and-land runs now rather than
     *       whenever {@link #computeMaxFlightTicks()} happens to expire — up to 30 s of the mob
     *       circling over a meal it has no way to notice.</li>
     *   <li>Grounded: {@link #hasFeedOfferingNearby()} pins the take-off (see
     *       {@code KriftoTakeoffGoal#canUse}). The ritual is several bites with a cooldown between
     *       them, easily longer than a ground-rest timer that has usually been draining since
     *       touchdown — without this the mob launches between bites and the whole ritual restarts a
     *       flight cycle later, over and over.</li>
     * </ul>
     *
     * <p>Scanned on an interval, not every tick: it is a radius-16 entity query, and the answer does
     * not meaningfully change inside ten ticks.
     */
    private void tickFeedOffering() {
        if (this.isTame()) {
            this.feedOffering = null;
            return;
        }
        if (this.tickCount % OFFERING_SCAN_INTERVAL == 0) {
            this.feedOffering = TameFeedGoal.findOffering(this);
        }
        if (this.feedOffering != null && this.isFlying()) {
            this.requestLanding();
        }
    }

    /** Whether an untamed krifto has an offering waiting for it. @see #tickFeedOffering() */
    public boolean hasFeedOfferingNearby() {
        return this.feedOffering != null;
    }

    /**
     * Comes down on the offering rather than wherever the heading pointed. The unaimed stoop runs
     * forward the whole way down, so a krifto that spotted a scrap of meat from cruising altitude
     * touched down a long way from it — often outside the radius {@link TameFeedGoal} searches, which
     * meant it landed and then forgot what it came down for.
     */
    @Override
    @Nullable
    protected Vec3 getDescentTarget() {
        ItemEntity offering = this.feedOffering;
        return offering != null && offering.isAlive() ? offering.position() : null;
    }

    /**
     * The taming flourish belongs on the ground. Its clip already pins the mob through
     * {@code isMovementLocked()} while it plays, but that lock lifts on the same tick the clip ends
     * and the ground-rest timer has invariably drained to zero during the feeding ritual that led
     * here — so {@code KriftoTakeoffGoal} fires on the very next tick and the flourish's tail, plus
     * the first flight after the new owner, happen in mid-air. Holding the ground a beat past the
     * clip lets it finish where it was earned, and the mob takes to the air after that.
     */
    @Override
    protected void onActionStart(String name) {
        super.onActionStart(name);
        if (ANIM_TAMED.equals(name)) {
            this.delayTakeoff(this.clipDurationTicks(name) + TAMED_GROUND_HOLD_TICKS);
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

        if (stack.is(Items.CHICKEN) && !this.isBaby() && !this.isInLove()) {
            if (!this.level().isClientSide()) {
                this.setInLove(player);
                stack.consume(1, player);
            }
            return InteractionResult.SUCCESS;
        }

        // Grab on: perch it over the owner's head as a parachute (see #getPerchTargetId). Owner-only,
        // and adult-only since the chick model has no rig for the grip pose.
        //
        // Mounting only — this deliberately does NOT toggle. Sneaking is the one way back off (see
        // #tickPerch), so that letting go is a single, always-available gesture rather than something
        // that also depends on managing to click a hitbox riding your own head.
        if (this.isTame() && this.isOwnedBy(player) && !this.isBaby() && !this.isPassenger()
                && !this.isPerched()) {
            if (!this.level().isClientSide()) {
                this.startPerching(player);
            }
            return InteractionResult.SUCCESS;
        }

        return super.mobInteract(player, hand);
    }

    @Override
    public boolean isFood(@NotNull ItemStack stack) {
        return stack.is(Items.CHICKEN);
    }

    // ───────────────────────────────────────────────────── GROUND TAMING ─────

    /**
     * Feedings logged so far toward {@link #feedGoal}. Not synced — only {@link TameFeedGoal}, which
     * is server-only, ever reads it.
     */
    private int feedProgress;
    /** Rolled on the first feeding, in [{@link #FEED_GOAL_MIN}, {@link #FEED_GOAL_MAX}]. */
    private int feedGoal;

    /**
     * Logs one feeding and returns the new total. Rolls {@link #feedGoal} on the very first call so
     * a fresh krifto's target is not fixed at spawn (and thus not the same for every one of a kind).
     */
    public int incrementFeedProgress() {
        if (this.feedProgress == 0) {
            this.feedGoal = FEED_GOAL_MIN + this.random.nextInt(FEED_GOAL_MAX - FEED_GOAL_MIN + 1);
        }
        return ++this.feedProgress;
    }

    /** Meaningless before the first {@link #incrementFeedProgress()} call. */
    public int getFeedGoal() {
        return this.feedGoal;
    }

    // ───────────────────────────────────────────────────── THEFT ─────

    /** {@link ItemStack#EMPTY} unless a heist is in progress. @see StealFromPlayerGoal */
    public @NotNull ItemStack getStolenItem() {
        return this.entityData.get(STOLEN_ITEM);
    }

    public void setStolenItem(@NotNull ItemStack stack) {
        this.entityData.set(STOLEN_ITEM, stack);
    }

    /** A heist caught short by death still pays out — the loot is recoverable, not just lost. */
    @Override
    protected void dropCustomDeathLoot(@NotNull ServerLevel level, @NotNull DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        ItemStack stolen = this.getStolenItem();
        if (!stolen.isEmpty()) {
            this.spawnAtLocation(level, stolen);
            this.setStolenItem(ItemStack.EMPTY);
        }
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
        output.putInt("FeedProgress", this.feedProgress);
        output.putInt("FeedGoal", this.feedGoal);
        if (this.perchHostId != null) {
            output.store("PerchHost", UUIDUtil.CODEC, this.perchHostId);
        }
    }

    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setSpawnBiomePath(input.getStringOr("SpawnBiome", "default"));
        this.feedProgress = input.getIntOr("FeedProgress", 0);
        this.feedGoal = input.getIntOr("FeedGoal", 0);

        this.perchHostId = input.read("PerchHost", UUIDUtil.CODEC).orElse(null);
        this.perchRestoreTicks = this.perchHostId != null ? PERCH_RESTORE_WINDOW_TICKS : 0;

        // Drop the perch's no-gravity unconditionally, and let tickPerchRestore put it back only if it
        // actually finds the host. Perching is the sole reason a GROUNDED Krifto floats, and unlike the
        // synced perch state, noGravity IS persisted by vanilla — so on its own it comes back set with
        // nothing left to justify it, and the bird hangs in the air playing the ground idle. That is
        // the same failure SMOPFlyingAnimal's own flight restore exists to prevent, reached by the one
        // path it does not cover. The isFlying() guard is what keeps this from undoing that restore,
        // which runs in the super call above and sets no-gravity for a legitimately airborne mob.
        if (!this.isFlying()) {
            this.setNoGravity(false);
        }
    }
}

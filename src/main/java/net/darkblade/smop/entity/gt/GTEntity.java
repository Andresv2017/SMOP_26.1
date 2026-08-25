package net.darkblade.smop.entity.gt;

import net.darkblade.deluxelib.anim.AnimSource;
import net.darkblade.deluxelib.anim.Animatable;
import net.darkblade.deluxelib.anim.Loop;
import net.darkblade.deluxelib.anim.MobAnimator;
import net.darkblade.deluxelib.anim.StandardAnimation;
import net.darkblade.deluxelib.entity.CortexMonster;
import net.darkblade.deluxelib.entity.ai.cortex.Cortex;
import net.darkblade.deluxelib.combat.AttackShape;
import net.darkblade.deluxelib.combat.HitWindow;
import net.darkblade.deluxelib.entity.ai.cortex.behavior.impl.AnimatedMeleeBehavior;
import net.darkblade.deluxelib.entity.ai.cortex.behavior.BehaviorContext;
import net.darkblade.deluxelib.entity.ai.cortex.GlobalRule;
import net.darkblade.deluxelib.entity.ai.cortex.behavior.impl.ChaseTargetBehavior;
import net.darkblade.deluxelib.entity.ai.cortex.behavior.impl.TimedAnimationBehavior;
import net.darkblade.deluxelib.entity.ai.cortex.behavior.impl.WanderBehavior;
import net.darkblade.deluxelib.entity.ai.cortex.target.impl.CompositeTargeting;
import net.darkblade.deluxelib.entity.ai.cortex.target.impl.HurtByAttackerTargeting;
import net.darkblade.deluxelib.entity.ai.cortex.target.impl.NearestEntityTargeting;
import net.darkblade.deluxelib.entity.ai.pathing.DirectionalMoveControl;
import net.darkblade.deluxelib.entity.ai.rotation.SmoothBodyRotationControl;
import net.darkblade.deluxelib.camera.ScreenShake;
import net.darkblade.deluxelib.vfx.ParticleFx;
import net.darkblade.smop.client.gt.GTAnimations;
import net.darkblade.smop.entity.sleep.ISleepingEntity;
import net.darkblade.smop.network.SMOPNetwork;
import net.darkblade.smop.network.packet.StompCrackFxClientPacket;
import net.darkblade.smop.entity.sleep.SleepGoal;
import net.darkblade.smop.entity.sleep.SleepPhase;
import net.darkblade.smop.entity.sleep.SleepUrge;
import net.darkblade.smop.sound.SMOPSounds;
import net.darkblade.smop.client.gt.GTAnimationsBase;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * The Grand Tyrant: a 300-health, six-block boss.
 *
 * <p>Built on {@link CortexMonster}, which owns the FSM wiring: it builds the {@link Cortex}, installs
 * it behind a {@code FloatGoal}, syncs the live state and {@link #isMoving()} to the client, and feeds
 * hurt, death and target changes into the machine.
 *
 * <p><b>It deliberately has no look goals</b>, unlike the 1.20.1 build. Standing still, the body
 * rotation control follows the HEAD, and {@code LookAtPlayerGoal} never stops moving it — which is
 * what made the old one spin on the spot. Adding either one back brings that straight back.
 */
public class GTEntity extends CortexMonster<GTEntity, GTState> implements Animatable<GTEntity>, ISleepingEntity {

    /**
     * A 90-degree turn takes 36 ticks at this rate, which is the time {@code GTSpineTurn}'s wave needs
     * to travel the whole spine. Faster and the turn is over before the tail hears about it.
     *
     * <p><b>Not a solo knob.</b> The gap that feeds the cascade is {@code this / BODY_LAG_MOVING}, so
     * lowering only this number SHRINKS the cascade. The two move together.
     */
    private static final float TURN_SPEED = 2.5F;
    /**
     * Still nearly triple the stroll — it has to be able to face you — but not so fast that the chase
     * snaps around and throws away the weight the stroll just earned. Being the larger of the two, this
     * is the one {@code GTBodyRotation}'s lag ceiling exists to contain.
     */
    private static final float COMBAT_TURN_SPEED = 7.0F;
    /**
     * Generous on purpose: its attacks reach 6 to 9 blocks and the body has to keep orienting inside
     * that radius instead of freezing the moment it arrives.
     */
    private static final double FACE_LOCK_RADIUS = 10.0D;

    /**
     * A quarter of the library default. Standing, the body all but ignores the head and lets the neck
     * do the work — which is what a neck this size is for.
     */
    private static final float BODY_LAG_STILL = 0.02F;
    /** Ten, not thirty: turning the body 30 degrees in a tick contradicts a heading that turns 5. */
    private static final float BODY_MAX_TURN = 10.0F;
    /**
     * How much of the gap to the heading the body closes per tick, a quarter of the library's 0.36.
     *
     * <p>That lag IS the signal {@code GTSpineTurn} propagates: with no gap there is nothing to
     * cascade and the animal turns as one piece however well the spring chain is tuned. At
     * {@code TURN_SPEED}, 0.36 would settle around 7 degrees — invisible — and 0.09 puts it near 28.
     *
     * <p><b>This is the knob to reach for if the turn feels flat or overdone</b>: lower is heavier.
     */
    private static final float BODY_LAG_MOVING = 0.09F;

    private final MobAnimator<GTEntity> animator;

    public GTEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.animator = new MobAnimator<>(this);
        this.moveControl = new DirectionalMoveControl<>(this)
                .setTurnSpeed(TURN_SPEED)
                .setCombatTurnSpeed(COMBAT_TURN_SPEED)
                .setFaceLockRadius(FACE_LOCK_RADIUS);
    }

    @Override
    protected @NotNull BodyRotationControl createBodyControl() {
        SmoothBodyRotationControl<GTEntity> control = new GTBodyRotation(this);
        control.bodyLagStill = BODY_LAG_STILL;
        control.bodyLagMoving = BODY_LAG_MOVING;
        control.bodyMax = BODY_MAX_TURN;
        return control;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.20D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.5D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.STEP_HEIGHT, 2.5D);
    }

    // ───────────────────────────────────────────────────── CORTEX ─────

    @Override
    protected @NotNull GTState defaultState() {
        return GTState.WANDER;
    }

    /**
     * How far a stroll may take it. Two and a half times the library default, because on a body 3.2
     * wide the default reads as shuffling in place rather than going somewhere.
     */
    private static final int WANDER_RANGE_H = 25;
    private static final int WANDER_RANGE_V = 10;

    /** Twice the wander speed: without it the chase moves exactly as fast as the stroll. */
    private static final double CHASE_SPEED = 2.0D;

    /** Matches FOLLOW_RANGE on purpose — it should not lose interest inside its own sight range. */
    private static final double TARGET_RANGE = 40.0D;
    /** Ticks a wound keeps the attacker marked. */
    private static final int GRUDGE_TICKS = 400;

    /**
     * <b>Players only</b>, and only those who can be hurt — hand-rolled targeting has to exclude
     * creative and spectator out loud, where vanilla's would have done it for free. The same test
     * gates waking from sleep, and the two have to agree or it ignores you asleep and hunts you awake.
     */
    @Override
    protected @NotNull Cortex<GTEntity, GTState> buildCortex() {
        return Cortex.<GTEntity, GTState>builder(GTState.WANDER)
                .targeting(new CompositeTargeting<GTEntity>(
                        new NearestEntityTargeting<GTEntity, Player>(Player.class, TARGET_RANGE, 10, true,
                                player -> !player.isCreative() && !player.isSpectator()),
                        new HurtByAttackerTargeting<>(GRUDGE_TICKS)))
                .register(GTState.WANDER, new WanderBehavior<GTEntity, GTState>(1.0D)
                        .wanderRange(WANDER_RANGE_H, WANDER_RANGE_V)
                        .onTargetFound(GTState.CHASE))
                .register(GTState.CHASE, new ChaseTargetBehavior<GTEntity, GTState>(CHASE_SPEED,
                        new GTAttackSelector()))
                .register(GTState.BITE, new AnimatedMeleeBehavior<GTEntity, GTState>(
                        "bite", BITE_TICKS, GTState.CHASE)
                        // Facing has to be committed before the jaws start closing at frame 7, or the
                        // hitbox and the drawn arc diverge.
                        .faceTargetUntil(6))
                .register(GTState.HORN_SWING, new AnimatedMeleeBehavior<GTEntity, GTState>(
                        "horn_swing", HORN_SWING_TICKS, GTState.CHASE)
                        .faceTargetUntil(8))
                .register(GTState.CLAW_SWING, new AnimatedMeleeBehavior<GTEntity, GTState>(
                        "claw_swing", CLAW_SWING_TICKS, GTState.CHASE)
                        .faceTargetUntil(8))
                // No faceTargetUntil: the stomp lands where the animal stands, and dodging it means
                // moving. Tracking the target would make it undodgeable.
                .register(GTState.STOMP, new AnimatedMeleeBehavior<GTEntity, GTState>(
                        "attack_stomp", STOMP_TICKS, GTState.CHASE))
                .register(GTState.ROAR, new TimedAnimationBehavior<GTEntity, GTState>(
                        "roar", ROAR_TICKS, GTState.CHASE).faceTarget())
                .globalRule(this::roarAtNewTarget)
                .build();
    }

    /**
     * One footfall: dust under the foot that lands, and a short jolt for anyone close.
     *
     * <p>The dust always fires and the shake only nearby, and the asymmetry is deliberate — dust is
     * information at a distance and makes nobody ill, whereas a shake twice a second does.
     */
    private void onFootfall(boolean leftFoot, float amplitude) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double yaw = Math.toRadians(this.yBodyRot);
        // (cos, sin) of the yaw points to the animal's RIGHT, so the left foot goes negative.
        double side = leftFoot ? -FOOTFALL_LATERAL_OFFSET : FOOTFALL_LATERAL_OFFSET;
        Vec3 foot = new Vec3(
                this.getX() + Math.cos(yaw) * side,
                this.getY() + 0.1D,
                this.getZ() + Math.sin(yaw) * side);

        ParticleOptions debris = new BlockParticleOption(ParticleTypes.BLOCK, this.getBlockStateOn());
        ParticleFx.burst(serverLevel, debris, foot, FOOTFALL_DUST_COUNT, FOOTFALL_DUST_SPREAD, 0.05D);

        for (ServerPlayer player : serverLevel.players()) {
            double distance = player.position().distanceTo(foot);
            if (distance > FOOTFALL_SHAKE_RADIUS) {
                continue;
            }
            float strength = (float) (amplitude * (1.0D - distance / FOOTFALL_SHAKE_RADIUS));
            ScreenShake.forPlayer(player)
                    .duration(FOOTFALL_SHAKE_TICKS)
                    .fadeOut(2)
                    .frequency(14.0F)
                    .amplitude(strength)
                    .seed(this.getId())
                    .fire();
        }
    }

    private void onStompImpact() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Whatever it is standing on, so stomping sand throws sand.
        ParticleOptions debris = new BlockParticleOption(ParticleTypes.BLOCK, this.getBlockStateOn());
        Vec3 feet = new Vec3(this.getX(), this.getY(), this.getZ());

        // The ring is drawn AT the damage radius, so it reads as the tell for where the hit lands.
        // The height band that makes the stomp jumpable is invisible, so this is the only cue.
        ParticleFx.ring(serverLevel, debris, feet, STOMP_RADIUS);
        ParticleFx.burst(serverLevel, debris, feet, 40, 1.5D, 0.25D);

        // Cracks go to everyone in the level rather than by distance: the effect is client-side and
        // self-limiting, since a distant player has neither the chunk nor the stomp in view.
        SMOPNetwork.INSTANCE.sendToPlayersInLevel(serverLevel,
                new StompCrackFxClientPacket(this.blockPosition(), (int) STOMP_RADIUS));

        // Per player, with the falloff done here: the library's around(level) has no radius of its own
        // and would rattle someone five thousand blocks away.
        for (ServerPlayer player : serverLevel.players()) {
            double distance = player.position().distanceTo(feet);
            if (distance > STOMP_SHAKE_RADIUS) {
                continue;
            }
            float strength = (float) (STOMP_SHAKE_AMPLITUDE * (1.0D - distance / STOMP_SHAKE_RADIUS));
            ScreenShake.forPlayer(player)
                    .duration(STOMP_SHAKE_TICKS)
                    .fadeOut(6)
                    .frequency(12.0F)
                    .amplitude(strength)
                    .seed(this.getId())
                    .fire();
        }
    }

    // ------------------------------------------------------------------- ROAR -----

    /** 5.2 s of clip, measured off it rather than estimated. */
    private static final int ROAR_TICKS = 104;

    /**
     * One roar per thirty seconds, whoever it is at. The new-target check alone is not enough:
     * targeting drops and re-acquires readily, so a player stepping in and out of range had it
     * bellowing on every re-acquisition.
     */
    private static final int ROAR_COOLDOWN_TICKS = 600;

    /** Who it last roared at, so a target it is already chasing does not get roared at every tick. */
    @Nullable
    private LivingEntity lastRoaredAt;

    /** Game time the next roar is allowed. Server-side; the rule only runs there. */
    private long nextRoarTime;

    /**
     * Roars once when it acquires a new target.
     *
     * <p>A {@link GlobalRule} returns the id of the state to jump to, or {@code null} to stay out of
     * the way. Global rules are evaluated <b>before</b> the active behaviour, so the
     * {@code currentStateId} guard is not redundant: without it the rule would re-enter the roar on
     * its own first tick and the animal would bellow forever.
     *
     * <p>The sound is played here rather than from the behaviour because this is the one place that
     * knows the roar is <em>starting</em> — {@code TimedAnimationBehavior} has no enter hook.
     */
    private @Nullable Integer roarAtNewTarget(GTEntity gt, BehaviorContext context, int currentStateId) {
        LivingEntity target = gt.getTarget();
        if (target == null || !target.isAlive()) {
            // Lost it: whoever comes next earns a fresh roar.
            gt.lastRoaredAt = null;
            return null;
        }
        if (target == gt.lastRoaredAt || currentStateId == GTState.ROAR.id() || gt.isInSleepCycle()) {
            return null;
        }
        if (gt.level().getGameTime() < gt.nextRoarTime) {
            // Still hoarse. Note the target anyway, so it does not queue up a roar for the moment the
            // cooldown expires on someone it has been chasing all along.
            gt.lastRoaredAt = target;
            return null;
        }

        gt.lastRoaredAt = target;
        gt.nextRoarTime = gt.level().getGameTime() + ROAR_COOLDOWN_TICKS;
        gt.level().playSound(null, gt.getX(), gt.getY(), gt.getZ(),
                SMOPSounds.GT_ROAR.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        return GTState.ROAR.id();
    }

    // ------------------------------------------------------------------ SLEEP -----

    private static final EntityDataAccessor<Integer> SLEEP_PHASE =
            SynchedEntityData.defineId(GTEntity.class, EntityDataSerializers.INT);

    /**
     * Phase lengths, each read off the clip that phase plays. A phase longer than its clip leaves the
     * animal holding the last frame for the difference; a shorter one cuts the clip mid-motion.
     *
     * <p>{@code SMOPAnimal} derives these from the animator layers; this entity has a different base
     * and cannot, so they are measured and written down. <b>A re-export means re-measuring.</b>
     *
     * <p>{@link #SLEEPING_TICKS} is the exception and is <b>not</b> a phase length: sleeping lasts
     * until dawn or a threat ends it. It is the length of the {@code sleep} loop, and
     * {@code SleepGoal} reads it to start the wake on the loop's seam instead of wherever the cycle
     * happened to be — see {@code SleepGoal#atSleepLoopSeam()}.
     */
    private static final int SITTING_DOWN_TICKS = 38;
    private static final int PREPARING_SLEEP_TICKS = 60;
    private static final int SLEEPING_TICKS = 88;
    private static final int AWAKENING_TICKS = 80;
    private static final int STANDING_UP_TICKS = 80;

    /**
     * How long it stays sat, in WHOLE loops of the {@code sit} clip.
     *
     * <p><b>Whole loops, and that is load-bearing.</b> The last frame of {@code sit} matches the first
     * frame of {@code sleep_preparing} on all 41 shared channels — a seam authored to be seamless,
     * which ending the phase anywhere else throws away, and that showed up in game as a jerk.
     */
    private static final int SIT_CLIP_TICKS = 88;
    private static final int SIT_MIN_LOOPS = 1;
    private static final int SIT_MAX_LOOPS = 2;

    private SleepUrge sleepUrge;

    /** Lazily built: {@code registerGoals} runs from the constructor, before field initialisers. */
    private SleepUrge sleepUrge() {
        if (this.sleepUrge == null) {
            this.sleepUrge = new SleepUrge(this);
        }
        return this.sleepUrge;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SLEEP_PHASE, SleepPhase.NONE.ordinal());
    }

    /**
     * No entity TYPE alarms it — it is at the top of the food chain. What wakes it is a player nearby,
     * which {@code SleepGoal} handles for every mob, and damage, which goes through {@link #hurtServer}.
     */
    @Override
    public @NotNull Set<EntityType<?>> getInterruptingEntityTypes() {
        return Set.of();
    }

    @Override
    public @NotNull SleepPhase sleepPhase() {
        return SleepPhase.byId(this.entityData.get(SLEEP_PHASE));
    }

    @Override
    public void setSleepPhase(@NotNull SleepPhase phase) {
        this.entityData.set(SLEEP_PHASE, phase.ordinal());
    }

    /** Overrides {@code LivingEntity#isSleeping()}, which vanilla defines off a bed it has not got. */
    @Override
    public boolean isSleeping() {
        return this.sleepPhase() == SleepPhase.SLEEPING;
    }

    @Override
    public int getSittingDuration() {
        int loops = SIT_MIN_LOOPS + this.random.nextInt(SIT_MAX_LOOPS - SIT_MIN_LOOPS + 1);
        return loops * SIT_CLIP_TICKS;
    }

    @Override
    public int sleepPhaseDuration(@NotNull SleepPhase phase) {
        return switch (phase) {
            case SITTING_DOWN -> SITTING_DOWN_TICKS;
            case SITTING -> this.getSittingDuration();
            case PREPARING_SLEEP -> PREPARING_SLEEP_TICKS;
            case SLEEPING -> SLEEPING_TICKS;
            case AWAKENING -> AWAKENING_TICKS;
            case STANDING_UP -> STANDING_UP_TICKS;
            case NONE -> 0;
        };
    }

    /**
     * Starts each phase's clip as the phase begins.
     *
     * <p>The animator's auto-start loop only ever starts REPEATING clips, and every phase but the sleep
     * loop is a one-shot, so without this hook the transitions simply would not play.
     */
    @Override
    public void onSleepPhaseBegin(@NotNull SleepPhase phase) {
        String clip = phase.clipName();
        if (clip != null) {
            this.animator().play(this.animator().getByName(clip));
        }
    }

    /**
     * {@code SleepGoal} at priority 0, not the 1 the other SMOP mobs use.
     *
     * <p>{@code CortexMonster} adds its {@code CortexGoal} at priority 1 and calls this afterwards, so
     * a sleep goal at the same priority would queue behind it and never claim MOVE or LOOK. At 0 it
     * preempts the whole FSM — a sleeping animal does not fight — while still sitting behind the
     * base's {@code FloatGoal}, so one that ends up in water floats rather than drowning.
     */
    @Override
    protected void registerExtraGoals() {
        this.goalSelector.addGoal(0, new SleepGoal<>(this, this.sleepUrge()));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide()) {
            this.sleepUrge().tick();
        }
    }

    /** Being hit wakes it. */
    @Override
    public boolean hurtServer(@NotNull ServerLevel level, @NotNull DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && this.isInSleepCycle()) {
            this.sleepUrge().requestWake();
        }
        return hurt;
    }

    // --------------------------------------------------------------- BOSS BAR -----

    /**
     * The bar is what marks this as a boss rather than large fauna.
     *
     * <p><b>Vanilla owns the tracking, not a list of our own.</b> {@code startSeenByPlayer} and
     * {@code stopSeenByPlayer} fire as players enter and leave tracking range; keeping a set by hand
     * would reimplement that worse and leave bars stuck on screen when a player disconnects.
     */
    private final ServerBossEvent bossBar = new ServerBossEvent(UUID.randomUUID(), this.getDisplayName(),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    @Override
    public void startSeenByPlayer(@NotNull ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(@NotNull ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossBar.removePlayer(player);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            this.bossBar.setProgress(this.getHealth() / this.getMaxHealth());
        }
    }

    // ───────────────────────────────────────────────────── ANIMATIONS ─────

    @Override
    public @NotNull MobAnimator<GTEntity> animator() {
        return this.animator;
    }

    /** 0.8333 s of clip, which is 16.7 ticks. Rounded up so the behaviour never ends mid-frame. */
    private static final int BITE_TICKS = 17;

    /**
     * The frames the jaws are actually shut. Three ticks and not one: a one-tick window has to catch a
     * moving target on exactly the right server tick, and {@code HitWindow} interpolates the sweep
     * across the range anyway.
     */
    private static final int BITE_WINDOW_START = 7;
    private static final int BITE_WINDOW_END = 9;

    /** 0.9167 s of clip. */
    private static final int HORN_SWING_TICKS = 19;
    /** 1.6 s of clip: 32 ticks exactly. */
    private static final int CLAW_SWING_TICKS = 32;
    /** 3.35 s of clip: 67 ticks exactly. */
    private static final int STOMP_TICKS = 67;

    /** Both swings land on frame 10, widened by a tick either side like the bite. */
    private static final int SWING_WINDOW_START = 9;
    private static final int SWING_WINDOW_END = 11;

    /**
     * Where a frontal box starts and how far it runs, in blocks ahead of the animal's centre.
     *
     * <p><b>The two must cover {@code GTAttackSelector.ATTACK_RANGE}.</b> The selector commits to an
     * attack at 8 blocks centre to centre, so a box ending short of that lets the animal swing from a
     * distance its own hitbox cannot reach: the animation plays and nothing can be hit.
     *
     * <p><b>Where the box STARTS matters as much as where it ends.</b> This animal's chest sits 2.5
     * blocks ahead of its centre, its neck 4.5 and its head 6.1, so starting the box near zero drapes
     * the damage volume over its own chest and neck — which reads in game as damage landing behind the
     * head. 3.0 + 5.5 runs from mid-neck to 8.5, half a block past the trigger to cover the animal
     * drifting backwards mid-swing. Anything closer than 3 blocks is under its chin: the stomp's job.
     */
    // Package-private on purpose: GTAttackSelector reads these to check the target fits INSIDE the box
    // before committing. If the two ever disagree, it goes back to attacking what it cannot touch.
    static final float FRONTAL_START = 3.0F;
    static final float FRONTAL_LENGTH = 5.5F;

    /** Shared by the bite and the horn: the two are the same swing as far as coverage goes. */
    static final float FRONTAL_HALF_WIDTH = 3.0F;
    private static final float FRONTAL_HALF_HEIGHT = 1.5F;

    /** The stomp's three impacts. */
    private static final int[] STOMP_FRAMES = {14, 26, 46};

    /** Radius of the shockwave on the ground, in blocks. */
    private static final float STOMP_RADIUS = 8.0F;

    /**
     * How far off the ground the shockwave still reaches, measured feet to feet.
     *
     * <p><b>This is what makes the stomp jumpable.</b> A player's jump peaks at about 1.25 blocks, so
     * a band of 1.0 leaves the top of the arc clear. Three impacts twelve and twenty ticks apart means
     * one jump dodges one of them, which is the intended skill.
     */
    private static final double STOMP_MAX_HEIGHT = 1.0D;

    /**
     * How far the ground shake carries — well past the damage circle, because a six-block animal
     * slamming the ground should be felt before it can reach you.
     */
    private static final double STOMP_SHAKE_RADIUS = 20.0D;

    /** Shake strength under the foot; the library's example uses 0.35 for an ordinary impact. */
    private static final float STOMP_SHAKE_AMPLITUDE = 0.5F;
    /** Ten ticks. */
    private static final int STOMP_SHAKE_TICKS = 10;

    /**
     * Contact frames per leg, measured off the clips.
     *
     * <p>No leg animates position: the gait lives entirely in the thigh's X rotation, and the cycle
     * covers the same 37.5-degree sweep in two stretches of very different length. The slow one is the
     * stance — foot planted, body travelling over it — and the fast one is the swing, so contact is the
     * fast-to-slow transition. That reading does not depend on the axis sign convention.
     *
     * <p>The two legs come out exactly half a phase apart in both clips, which is the check that it is
     * read correctly.
     */
    private static final int WALK_LEFT_FOOTFALL = 7;
    private static final int WALK_RIGHT_FOOTFALL = 37;
    private static final int SPRINT_LEFT_FOOTFALL = 3;
    private static final int SPRINT_RIGHT_FOOTFALL = 13;

    /**
     * Well below the stomp's 0.5, and not out of timidity: {@code sprint} lands a foot every 10 ticks —
     * twice a second while it chases you — and at stomp amplitude that is unwatchable.
     */
    private static final float WALK_SHAKE_AMPLITUDE = 0.25F;
    private static final float SPRINT_SHAKE_AMPLITUDE = 0.40F;
    /** Fourteen blocks with a linear falloff. */
    private static final double FOOTFALL_SHAKE_RADIUS = 14.0D;
    private static final int FOOTFALL_SHAKE_TICKS = 3;
    /** Half the gap between feet, so dust comes up under the foot that lands and not under the centre. */
    private static final double FOOTFALL_LATERAL_OFFSET = 1.2D;
    /**
     * Scaled against the stomp's 40 at 1.5 spread. Much less than this is invisible underneath an
     * animal 3.2 blocks wide.
     */
    private static final int FOOTFALL_DUST_COUNT = 18;
    private static final double FOOTFALL_DUST_SPREAD = 0.7D;

    /**
     * Every duration here is read off the clip's own {@code withLength}, never estimated.
     *
     * <p><b>The constructor's argument order misleads:</b> it is
     * {@code (name, data, loop, layer, priority, duration)} — layer BEFORE priority. Swapping the two
     * registers the clip on an additive layer over nothing and it silently never shows, with no error
     * in the log.
     *
     * <p>The two animation files are not split by theme and the names mislead: {@code bite} lives in
     * {@code GTAnimationsBase}, and the stomp clip is called {@code attack_stomp}.
     */
    @Override
    public void registerAnimations() {
        StandardAnimation idle = new StandardAnimation("idle",
                new AnimSource(() -> GTAnimationsBase.idle), Loop.REPEATING, 0, 3, 10.0F);
        StandardAnimation walk = new StandardAnimation("walk",
                new AnimSource(() -> GTAnimationsBase.walk), Loop.REPEATING, 0, 3, 3.0F);

        walk.setPlayCondition(a -> this.isMoving());

        // Asymmetric on purpose: settling into idle reads well, but setting off read too quick. That
        // direction is governed by walk's blend IN and idle's blend OUT, which are the two raised here.
        idle.blendInMs(500).blendOutMs(700);
        walk.blendInMs(700).blendOutMs(400);

        StandardAnimation death = new StandardAnimation("death",
                new AnimSource(() -> GTAnimations.death), Loop.PLAY_ONCE, 0, 0, 2.25F);
        // blockAdditive so the look-at does not keep swinging the neck on a corpse.
        death.blockAdditive();

        // The bite REPLACES on layer 0: it wins the frame over locomotion, which keeps running
        // underneath. A finished PLAY_ONCE clip falls back to the BIND pose and not to its own last
        // frame, so the cycle has to be there to catch it.
        StandardAnimation bite = new StandardAnimation("bite",
                new AnimSource(() -> GTAnimationsBase.bite), Loop.PLAY_ONCE, 0, 0, 0.8333F);
        // Snappy either side: a bite that fades in reads as a yawn.
        bite.blendInMs(80).blendOutMs(150);

        // box3d and NOT box: AttackShape's own note says box ignores the Y axis outright, and on an
        // animal 6.2 blocks tall that means biting whatever is in front at ANY height.
        //
        // READ THE GEOMETRY BEFORE CHANGING THESE. Box3D extends `length` FORWARD from the anchor
        // (fwd runs 0..length), while halfWidth and halfHeight are centred on it. Getting that wrong
        // gives a five-block-tall cube floating at chest height.
        //
        // The bite and the horn deliberately share a box: narrowing the bite made it miss anyone
        // moving sideways, and both are the same "whatever is in front of me" gesture. The claw is
        // told apart by sitting LOWER, not by being narrower.
        HitWindow.of(BITE_WINDOW_START, BITE_WINDOW_END)
                .shape(AttackShape.box3d(FRONTAL_LENGTH, FRONTAL_HALF_WIDTH, FRONTAL_HALF_HEIGHT))
                .anchor(FRONTAL_START, 0.0F, 1.5F)
                .damage(18.0F)
                .knockback(0.35F)
                .filter(target -> !(target instanceof GTEntity))
                .applyTo(bite);

        // The condition hangs off syncedState(): CortexMonster syncs the state to the client for
        // exactly this, and play conditions run on both sides.
        StandardAnimation sprint = new StandardAnimation("sprint",
                new AnimSource(() -> GTAnimationsBase.sprint), Loop.REPEATING, 0, 3, 1.0F);
        sprint.setPlayCondition(a -> this.isMoving() && this.syncedState() == GTState.CHASE);
        sprint.blendInMs(400).blendOutMs(400);

        // onFrame runs server-side only, which is where the particles and the shakes have to come from.
        walk.onFrame(WALK_LEFT_FOOTFALL, entity -> this.onFootfall(true, WALK_SHAKE_AMPLITUDE));
        walk.onFrame(WALK_RIGHT_FOOTFALL, entity -> this.onFootfall(false, WALK_SHAKE_AMPLITUDE));
        sprint.onFrame(SPRINT_LEFT_FOOTFALL, entity -> this.onFootfall(true, SPRINT_SHAKE_AMPLITUDE));
        sprint.onFrame(SPRINT_RIGHT_FOOTFALL, entity -> this.onFootfall(false, SPRINT_SHAKE_AMPLITUDE));

        // Walking stops applying while it chases, or the two cycles fight over the same layer.
        walk.setPlayCondition(a -> this.isMoving() && this.syncedState() != GTState.CHASE);

        StandardAnimation hornSwing = new StandardAnimation("horn_swing",
                new AnimSource(() -> GTAnimationsBase.horn_swing), Loop.PLAY_ONCE, 0, 0, 0.9167F);
        hornSwing.blendInMs(80).blendOutMs(150);

        StandardAnimation clawSwing = new StandardAnimation("claw_swing",
                new AnimSource(() -> GTAnimations.claw_swing), Loop.PLAY_ONCE, 0, 0, 1.6F);
        clawSwing.blendInMs(80).blendOutMs(150);

        StandardAnimation stomp = new StandardAnimation("attack_stomp",
                new AnimSource(() -> GTAnimations.attack_stomp), Loop.PLAY_ONCE, 0, 0, 3.35F);
        stomp.blendInMs(120).blendOutMs(200);

        // The horn: same box as the bite, and the biggest shove of the four.
        HitWindow.of(SWING_WINDOW_START, SWING_WINDOW_END)
                .shape(AttackShape.box3d(FRONTAL_LENGTH, FRONTAL_HALF_WIDTH, FRONTAL_HALF_HEIGHT))
                .anchor(FRONTAL_START, 0.0F, 1.5F)
                .damage(20.0F)
                // It has to SEND you somewhere: that is the whole difference in character between this
                // and the claw, which hits for about the same and barely moves you.
                .knockback(1.80F)
                .filter(target -> !(target instanceof GTEntity))
                .applyTo(hornSwing);

        // The claw: same box, anchored lower — it goes for the legs. That is all that tells it apart.
        HitWindow.of(SWING_WINDOW_START, SWING_WINDOW_END)
                .shape(AttackShape.box3d(FRONTAL_LENGTH, FRONTAL_HALF_WIDTH, FRONTAL_HALF_HEIGHT))
                .anchor(FRONTAL_START, 0.0F, 1.0F)
                .damage(18.0F)
                .knockback(0.60F)
                .filter(target -> !(target instanceof GTEntity))
                .applyTo(clawSwing);

        // The stomp: three impacts, radial, anchored on the animal itself — an area hit around its
        // feet rather than a frontal sweep.
        //
        // A DISC PINNED TO THE GROUND, which no single library shape gives you. It takes both halves:
        //
        //   - Sector(radius, 360) is the FOOTPRINT. It measures horizontal distance only, ignoring Y
        //     entirely, so the radius stays uniform — unlike a sphere, whose reach narrows the further
        //     you are vertically. By the same token it is an infinite cylinder: on its own it hits you
        //     at any height.
        //   - The filter is the CEILING. It receives the target, so it can compare Y with the animal's.
        //
        // Together: radial, constant radius, and dodgeable by jumping. A sphere of this radius would
        // cover you in the air and make jumping pointless.
        //
        // One HitWindow PER FRAME and not a single 14-to-46 window: each instance keeps its own
        // hitThisSwing, so one long window would hit once and swallow the other two impacts.
        for (int frame : STOMP_FRAMES) {
            // ONE tick, not two: onSweep is a per-tick-of-window hook, so a two-tick window would fire
            // the dust and the shake twice per impact. The damage would not double — each HitWindow has
            // its own hitThisSwing — but the effect would.
            HitWindow.of(frame, frame)
                    .shape(AttackShape.sector(STOMP_RADIUS, 360.0F))
                    .anchor(0.0F, 0.0F, 0.0F)
                    .damage(26.0F)
                    .knockback(0.10F)
                    .filter(target -> !(target instanceof GTEntity)
                            && target.getY() - this.getY() <= STOMP_MAX_HEIGHT)
                    // onSweep and not onHit: it fires even when nothing was hit, which is what an
                    // environmental effect needs.
                    .onSweep((attacker, origin, facing, shape, hits) -> this.onStompImpact())
                    .applyTo(stomp);
        }

        // Priority 0 like the attacks: it wins the frame over locomotion, which keeps running under it.
        StandardAnimation roar = new StandardAnimation("roar",
                new AnimSource(() -> GTAnimationsBase.roar), Loop.PLAY_ONCE, 0, 0, 5.2F);
        roar.blendInMs(200).blendOutMs(300);

        // The six sleep phases. They register under THE NAME SleepPhase ASKS FOR, not the clip field's:
        // phase PREPARING_SLEEP looks up "preparing_sleep" while the authored clip is called
        // "sleep_preparing". Registering it under the field name leaves it invisible to the system.
        //
        // Priority 1: under the attacks, over locomotion, so idle cannot fight a sleeping animal for
        // the frame.
        StandardAnimation sittingDown = new StandardAnimation("sitting",
                new AnimSource(() -> GTAnimations.sitting), Loop.PLAY_ONCE, 0, 1, 1.9F);
        StandardAnimation sitting = new StandardAnimation("sit",
                new AnimSource(() -> GTAnimations.sit), Loop.REPEATING, 0, 1, 4.4F);
        StandardAnimation preparingSleep = new StandardAnimation("preparing_sleep",
                new AnimSource(() -> GTAnimations.sleep_preparing), Loop.PLAY_ONCE, 0, 1, 3.0F);
        StandardAnimation sleeping = new StandardAnimation("sleep",
                new AnimSource(() -> GTAnimations.sleep), Loop.REPEATING, 0, 1, 4.4F);
        StandardAnimation awakening = new StandardAnimation("awakening",
                new AnimSource(() -> GTAnimations.awakening), Loop.PLAY_ONCE, 0, 1, 4.0F);
        StandardAnimation standingUp = new StandardAnimation("standing_up",
                new AnimSource(() -> GTAnimations.standing_up), Loop.PLAY_ONCE, 0, 1, 4.0F);

        // BLEND RULE, and it is NOT "a generous number everywhere".
        //
        // A blend only ever hides a pose jump, and it is not free: the outgoing layer FREEZES while the
        // incoming one is multiplied by a smoothstep ramp, so the new clip plays at `weight * progress`
        // rather than at its own speed — nearly still, then overshooting to catch up. A 400 ms blend on
        // a head moving 2.62 deg/tick ran 0.11, 0.71, 1.67 ... 4.32 before settling: motionless for
        // 100 ms and then 65% too fast. That was the reported jerk, over a seam that was PERFECT.
        //
        // So the question per clip is double: which seams can it be entered through, and how much does
        // the clip move on its own first tick. A blend earns its place only when the WORST mismatch it
        // can be entered on is bigger than the clip's own opening motion — otherwise the jump was
        // already invisible and the blend contributes nothing but its own distortion.
        //
        // All six seams of the cycle measure clean channel by channel. The only thing that dirties one
        // is a loop cut mid-way, which is where the two blends come from:
        //
        //   - `sitting_down` is not entered through a cycle seam at all: it comes from idle, cut at any
        //     point of its breathing. Worst mismatch 32.5 against an opening of 4.61. BLEND.
        //   - `awakening` comes off the `sleep` loop. A calm wake cuts on the seam, but a startled one
        //     leaves instantly and lands wherever the breath was: 14.2 against an opening of 0.48. BLEND.
        //   - `standing_up` carries the same risk (14.2, out of mid-`sit`) but opens at 18.12 per tick —
        //     its own first frame is BIGGER than the jump, so 50 ms comes out identical to the raw clip.
        //   - `sit`, `sleep` and `preparing_sleep` are only ever entered from one-shots that always play
        //     in full, or from a phase that lasts WHOLE loops. Always a clean seam. CUT.
        sittingDown.blendInMs(350).blendOutMs(400);
        sitting.blendInMs(50).blendOutMs(400);
        preparingSleep.blendInMs(50).blendOutMs(400);
        sleeping.blendInMs(50).blendOutMs(450);
        awakening.blendInMs(450).blendOutMs(400);
        standingUp.blendInMs(50).blendOutMs(350);

        // onSleepPhaseBegin starts the one-shots; these conditions do the opposite and matter just as
        // much — a condition going false CUTS the clip, which is what makes waking mid-transition end
        // that transition instead of playing it out.
        sittingDown.setPlayCondition(a -> this.sleepPhase() == SleepPhase.SITTING_DOWN);
        sitting.setPlayCondition(a -> this.sleepPhase() == SleepPhase.SITTING);
        preparingSleep.setPlayCondition(a -> this.sleepPhase() == SleepPhase.PREPARING_SLEEP);
        sleeping.setPlayCondition(a -> this.sleepPhase() == SleepPhase.SLEEPING);
        awakening.setPlayCondition(a -> this.sleepPhase() == SleepPhase.AWAKENING);
        standingUp.setPlayCondition(a -> this.sleepPhase() == SleepPhase.STANDING_UP);

        // Locomotion goes quiet for the whole cycle, or idle fights the sleeping pose.
        idle.setPlayCondition(a -> !this.isMoving() && !this.isInSleepCycle());

        this.animator().register(idle, walk, sprint, bite, hornSwing, clawSwing, stomp, roar,
                sittingDown, sitting, preparingSleep, sleeping, awakening, standingUp);
        // registerDeath and not a play condition: MobAnimator hooks LivingDeathEvent itself and holds
        // the corpse for exactly as long as the clip runs.
        this.animator().registerDeath(death);
    }
}

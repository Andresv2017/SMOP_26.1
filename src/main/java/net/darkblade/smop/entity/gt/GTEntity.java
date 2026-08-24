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
 * <p><b>Built on {@link CortexMonster}, not on {@code SMOPAnimal}.</b> The port spec said otherwise,
 * and it was written before this base class was found. The difference is between inheriting the FSM
 * wiring and writing it: {@code CortexMonster} builds the {@link Cortex}, installs it as a goal behind
 * a {@code FloatGoal}, syncs the live state to the client, keeps a synced {@link #isMoving()} off a
 * movement hysteresis, and forwards hurt, death, target changes and effect events into the machine.
 *
 * <p>Three things follow from that choice, all deliberate:
 * <ul>
 *   <li>It is an {@code Enemy} and a {@code PathfinderMob}, not a {@code TamableAnimal}. The
 *       {@code isFood} and {@code getBreedOffspring} stubs the old base demanded are gone — a boss was
 *       never going to use either.</li>
 *   <li>It drops experience, which {@code CortexMonster} switches on because it is not a vanilla
 *       {@code Monster} subclass and would otherwise silently drop none.</li>
 *   <li><b>No look goals.</b> 1.20.1 gave it {@code LookAtPlayerGoal} and {@code RandomLookAroundGoal};
 *       the Minotaur, the reference build on this base, carries neither. They are also what produced
 *       the reported "gira sobre su propio eje": standing still, the body rotation control follows the
 *       HEAD, and those two goals never stop moving it.</li>
 * </ul>
 *
 * <p>The sleep cycle arrives in module 6. {@code SleepGoal} binds to {@code Mob & ISleepingEntity} and
 * not to {@code SMOPAnimal}, so it fits this base unchanged — checked before committing to the move.
 */
public class GTEntity extends CortexMonster<GTEntity, GTState> implements Animatable<GTEntity>, ISleepingEntity {

    /** What the legacy's own {@code GTMoveControl} turned at: 5 degrees per tick, and no more. */
    private static final float TURN_SPEED = 5.0F;
    /** Three times that in combat, still far below the Minotaur's 40 — a boss has to be circleable. */
    private static final float COMBAT_TURN_SPEED = 15.0F;
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

    private final MobAnimator<GTEntity> animator;

    public GTEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.animator = new MobAnimator<>(this);
        // The legacy carried a 60-line GTMoveControl whose whole idea was to turn slowly and
        // accelerate gradually. This does that, and additionally keeps steering at the target inside
        // the face-lock radius — which the Nirasmosaurus port documents as the cause of a melee mob
        // striking thin air: vanilla's MoveControl stops writing yRot the moment the goal halts
        // navigation, and every hitbox is built off that yaw.
        this.moveControl = new DirectionalMoveControl<>(this)
                .setTurnSpeed(TURN_SPEED)
                .setCombatTurnSpeed(COMBAT_TURN_SPEED)
                .setFaceLockRadius(FACE_LOCK_RADIUS);
    }

    @Override
    protected @NotNull BodyRotationControl createBodyControl() {
        SmoothBodyRotationControl<GTEntity> control = new SmoothBodyRotationControl<>(this);
        control.bodyLagStill = BODY_LAG_STILL;
        control.bodyMax = BODY_MAX_TURN;
        return control;
    }

    /**
     * {@code Monster.createMonsterAttributes()} now that this is not an {@code Animal} — the same base
     * every hostile uses, and it already carries {@code ATTACK_DAMAGE}.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.20D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.5D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                // The legacy called setMaxUpStep(2.5F); in 26.1 step height is an attribute. Two and a
                // half blocks is a kerb to a six-block animal, and it is what keeps it from snagging on
                // its own terrain.
                .add(Attributes.STEP_HEIGHT, 2.5D);
    }

    // ───────────────────────────────────────────────────── CORTEX ─────

    @Override
    protected @NotNull GTState defaultState() {
        return GTState.WANDER;
    }

    /**
     * How far a stroll may take it, in blocks. The library defaults to 10 horizontal and 7 vertical,
     * which on a body 3.2 wide reads as shuffling in place — reported from the game as "camina
     * distancias muy cortas".
     *
     * <p>25 and 10 are not invented: they are what the legacy passed to its own
     * {@code CustomWanderGoal(this, 1.0D, 25, 10)}, whose defaults were the same 10 and 7 this
     * behaviour has. 1.20.1 had already found that a six-block animal needs two and a half times the
     * normal radius before a walk looks like going somewhere.
     */
    private static final int WANDER_RANGE_H = 25;
    private static final int WANDER_RANGE_V = 10;

    /** Twice the wander speed, from the legacy's {@code GTAttackGoal.CHASE_SPEED = 2.0}. */
    private static final double CHASE_SPEED = 2.0D;

    /** The legacy's {@code GTTargetPlayerGoal.getFollowDistance()}. Same as FOLLOW_RANGE, on purpose. */
    private static final double TARGET_RANGE = 40.0D;
    /** Ticks a wound keeps the attacker marked. The Minotaur's number, and it suits a slow boss. */
    private static final int GRUDGE_TICKS = 400;

    /**
     * Wander, chase, bite. The other three attacks land in module 4 and only need a behaviour each —
     * the state enum already names them and the selector is the one place that has to learn about them.
     *
     * <p><b>Players only.</b> The legacy carried a {@code GTTargetPreyGoal} whose selector was
     * {@code e instanceof Player}, so despite the name there was never any prey but us. Creative and
     * spectator players are excluded, which the legacy's {@code NearestAttackableTargetGoal} did for
     * free and a hand-rolled targeting has to say out loud.
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
                // 2.0, el doble del deambular: es el CHASE_SPEED del legacy, y sin él la persecución
                // va exactamente igual de rápido que el paseo — reportado desde el juego.
                .register(GTState.CHASE, new ChaseTargetBehavior<GTEntity, GTState>(CHASE_SPEED,
                        new GTAttackSelector()))
                // Duration from the clip, not from the legacy: bite is authored at 0.8333 s, which is
                // 16.7 ticks. The legacy said 17 and was right, but the clip is what decides.
                .register(GTState.BITE, new AnimatedMeleeBehavior<GTEntity, GTState>(
                        "bite", BITE_TICKS, GTState.CHASE)
                        // Commit the facing before the jaws start closing at frame 7, or the hitbox and
                        // the drawn arc diverge — the mistake the Minotaur documents at its own frame 3.
                        .faceTargetUntil(6))
                // Los otros tres. Duraciones leídas del clip: horn_swing 0.9167 s, claw_swing 1.6 s
                // (32 ticks exactos) y attack_stomp 3.35 s (67 exactos). Las tres coinciden con lo que
                // decía el legacy.
                .register(GTState.HORN_SWING, new AnimatedMeleeBehavior<GTEntity, GTState>(
                        "horn_swing", HORN_SWING_TICKS, GTState.CHASE)
                        .faceTargetUntil(8))
                .register(GTState.CLAW_SWING, new AnimatedMeleeBehavior<GTEntity, GTState>(
                        "claw_swing", CLAW_SWING_TICKS, GTState.CHASE)
                        .faceTargetUntil(8))
                // El pisotón NO sigue al objetivo: cae donde el bicho está, y esquivarlo es moverse.
                // Por eso no lleva faceTargetUntil.
                .register(GTState.STOMP, new AnimatedMeleeBehavior<GTEntity, GTState>(
                        "attack_stomp", STOMP_TICKS, GTState.CHASE))
                // El rugido: 104 ticks medidos del clip, no los 100 que decia el legacy.
                .register(GTState.ROAR, new TimedAnimationBehavior<GTEntity, GTState>(
                        "roar", ROAR_TICKS, GTState.CHASE).faceTarget())
                .globalRule(this::roarAtNewTarget)
                .build();
    }

    /**
     * Dust and a jolt, once per stomp impact.
     *
     * <p>Replaces two of the legacy's four hand-rolled packets: {@code StompDustFXPacket} becomes
     * {@link ParticleFx} and {@code ShakeCameraPacket} becomes {@link ScreenShake}, which additionally
     * uses fBm noise instead of frame jitter.
     */
    private void onStompImpact() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Whatever it is standing on, so stomping sand throws sand and stomping grass throws grass.
        ParticleOptions debris = new BlockParticleOption(ParticleTypes.BLOCK, this.getBlockStateOn());
        Vec3 feet = new Vec3(this.getX(), this.getY(), this.getZ());

        // The ring is drawn AT the damage radius, so it reads as the tell for where the hit lands
        // rather than as decoration — and the height band that makes the stomp jumpable is invisible,
        // so the footprint is the only cue the player gets.
        ParticleFx.ring(serverLevel, debris, feet, STOMP_RADIUS);
        ParticleFx.burst(serverLevel, debris, feet, 40, 1.5D, 0.25D);

        // Las grietas van a los jugadores del nivel y no por distancia: el efecto es de cliente y se
        // autolimita solo, porque un jugador lejos ni tiene el chunk ni ve el pisotón.
        SMOPNetwork.INSTANCE.sendToPlayersInLevel(serverLevel,
                new StompCrackFxClientPacket(this.blockPosition(), (int) STOMP_RADIUS));

        // Per player, with the distance falloff done here: the library's around(level) has no radius.
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

    /** 5.2 s of clip. The legacy said 100 ticks; the clip says 104, and the clip decides. */
    private static final int ROAR_TICKS = 104;

    /**
     * Only one roar per this many ticks, whoever it is at.
     *
     * <p>Thirty seconds, the same number the Tangoftero uses for its own roar and for the same kind of
     * reason. The new-target check alone was not enough: {@code NearestEntityTargeting} drops and
     * re-acquires a target readily, so a player stepping in and out of range had it bellowing on every
     * re-acquisition. Reported from the game as simply tiring.
     */
    private static final int ROAR_COOLDOWN_TICKS = 600;

    /** Who it last roared at, so a target it is already chasing does not get roared at every tick. */
    @Nullable
    private LivingEntity lastRoaredAt;

    /** Game time the next roar is allowed. Server-side; the rule only runs there. */
    private long nextRoarTime;

    /**
     * Roars once when it acquires a new target, which is what the legacy's
     * {@code RoarOnTargetGoal(this, 100, true)} did.
     *
     * <p>A {@link GlobalRule} returns the id of the state to jump to, or {@code null} to stay out of
     * the way. Global rules are evaluated <b>before</b> the active behaviour, so the
     * {@code currentStateId} guard is not redundant: without it the rule would re-enter the roar on
     * its own first tick and the animal would bellow forever.
     *
     * <p>The sound is played here rather than from the behaviour because this is the one place that
     * knows the roar is <em>starting</em> — {@code TimedAnimationBehavior} has no enter hook to hang it
     * on. {@code gt_roar} has been registered since Fase 1 with a fixed range of 64 blocks and had
     * never been used until now.
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
     * <p>{@code SMOPAnimal} derives these by walking the animator layers, which this entity cannot do
     * from a different base class. Measured once and written down instead — equivalent as long as a
     * re-export is followed by re-measuring, which is the one thing the derived version bought.
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
     * <p><b>Whole loops, and that is the fix for a reported jerk.</b> The sit clip runs 88 ticks and
     * loops; the duration used to be a random 60 to 160, so the phase ended at an arbitrary point in
     * the cycle and handed the next phase whatever pose it happened to be mid-way through. Measured
     * channel by channel, the last frame of {@code sit} matches the first frame of
     * {@code sleep_preparing} on all 41 shared channels — a seam authored to be seamless, which a cut
     * anywhere else throws away.
     *
     * <p>One or two loops is 4.4 or 8.8 seconds, which still reads as the three-to-eight the rest of
     * the mod uses.
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
     * Nothing alarms it into waking merely by being nearby.
     *
     * <p>Empty for the thing at the top of the food chain, and it matches the legacy, whose
     * {@code getInterruptingEntityTypes} returned an empty set too. What does wake it is damage, and
     * that goes through {@link #hurtServer}.
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
     * <p>{@code CortexMonster} adds its own {@code CortexGoal} at priority 1 and calls this method
     * afterwards, so a sleep goal at the same priority would queue behind it and never claim MOVE or
     * LOOK. At 0 it preempts the whole FSM, which is the intent: a sleeping animal does not fight. It
     * still sits behind the base's {@code FloatGoal}, also at 0 and registered first, so a sleeping
     * Grand Tyrant that ends up in water floats rather than drowning.
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
     * A deliberate deviation from 1.20.1, which had no bar.
     *
     * <p>Three hundred health with no bar is a punching bag with no readout. The bar is also the
     * signal that this is a boss rather than large fauna.
     *
     * <p><b>Vanilla owns the tracking, not a list of our own.</b> {@code startSeenByPlayer} and
     * {@code stopSeenByPlayer} fire as players enter and leave the entity's tracking range — the 16
     * chunks its type declares. Keeping a set by hand would reimplement that worse, and would leave
     * bars stuck on screen when a player disconnects.
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

    /**
     * Locomotion and death, for module 1. Durations are read off each clip's {@code withLength}, never
     * estimated — guessing them was the most repeated failure of the Hell Hippo port.
     *
     * <p><b>The constructor's argument order misleads:</b> it is
     * {@code (name, data, loop, layer, priority, duration)} — layer BEFORE priority. Swapping the two
     * registers locomotion on an additive layer over nothing, and the clip silently never shows, with
     * no error in the log. That happened on this module's first pass.
     *
     * <p>Note that {@code bite} lives in {@code GTAnimationsBase} and the stomp is called
     * {@code attack_stomp}: the two animation files are not split by theme, and both names mislead.
     */
    /** 0.8333 s of clip, which is 16.7 ticks. Rounded up so the behaviour never ends mid-frame. */
    private static final int BITE_TICKS = 17;

    /**
     * The frames the jaws are actually shut, around the legacy's single damage frame of 8.
     *
     * <p>Three ticks rather than that one: a one-tick window has to catch a moving target on exactly
     * the right server tick, and {@code HitWindow} interpolates the sweep across the range anyway.
     */
    private static final int BITE_WINDOW_START = 7;
    private static final int BITE_WINDOW_END = 9;

    /** 0.9167 s of clip. The legacy said 19 and it rounds there. */
    private static final int HORN_SWING_TICKS = 19;
    /** 1.6 s of clip: 32 ticks exactly, which is what the legacy said. */
    private static final int CLAW_SWING_TICKS = 32;
    /** 3.35 s of clip: 67 ticks exactly, again the legacy's number. */
    private static final int STOMP_TICKS = 67;

    /** Both swings land on the legacy's frame 10, widened by a tick either side like the bite. */
    private static final int SWING_WINDOW_START = 9;
    private static final int SWING_WINDOW_END = 11;

    /**
     * Where a frontal box starts and how far it runs, in blocks ahead of the animal's centre.
     *
     * <p><b>These two must cover {@code GTAttackSelector.ATTACK_RANGE}, and that is the whole point.</b>
     * The selector commits to an attack at 8 blocks centre to centre; the first pass gave the boxes a
     * far edge at 6 to 7, so the Grand Tyrant could decide to swing from a distance its own hitbox did
     * not reach and the animation played on a target it could never touch. Reported from the game as
     * standing still and taking no damage.
     *
     * <p><b>Where the box STARTS matters as much as where it ends.</b> The legacy's multipart hitboxes
     * put this animal's chest 2.5 blocks ahead of its centre, its neck at 4.5 and its <b>head at 6.1</b>.
     * The first pass started the box at 1.0, which draped the damage volume over the animal's own chest
     * and neck — reported from the game as the bite and the horn having damage area behind the head,
     * which is exactly what it was.
     *
     * <p>3.0 + 5.5 runs from mid-neck to 8.5: past the trigger by half a block for the animal drifting
     * backwards mid-swing, and no longer covering a torso that has no business biting anyone. Anything
     * closer than 3 blocks is under its chin, and that is what the stomp is for.
     */
    // Package-private a propósito: GTAttackSelector las lee para decidir si el objetivo cabe DENTRO
    // de la caja antes de comprometerse a un ataque. Si estas y aquella comprobación divergen, el bicho
    // vuelve a atacar a cosas que no puede tocar.
    static final float FRONTAL_START = 3.0F;
    static final float FRONTAL_LENGTH = 5.5F;

    /** Shared by the bite and the horn: the two are the same swing as far as coverage goes. */
    static final float FRONTAL_HALF_WIDTH = 3.0F;
    private static final float FRONTAL_HALF_HEIGHT = 1.5F;

    /** The stomp's three impacts, straight from the legacy's {@code damageFrames}. */
    private static final int[] STOMP_FRAMES = {14, 26, 46};

    /** Radius of the shockwave on the ground, in blocks. */
    private static final float STOMP_RADIUS = 8.0F;

    /**
     * How far off the ground the shockwave still reaches, measured feet to feet.
     *
     * <p><b>This is what makes the stomp jumpable.</b> A player's jump peaks at about 1.25 blocks, so a
     * band of 1.0 leaves the top of the arc clear — mistimed and you eat it, well timed and it passes
     * under you. Three impacts twelve and twenty ticks apart means one jump dodges one of them, which
     * is the intended skill.
     */
    private static final double STOMP_MAX_HEIGHT = 1.0D;

    /**
     * How far the ground shake carries, in blocks — well past the damage circle, because a six-block
     * animal slamming the ground should be felt before it can reach you.
     *
     * <p>The radius has to be enforced here by hand: {@code ScreenShake.around(level)} sends to every
     * player in the DIMENSION, with no distance test of its own. Left as it comes, a stomp would rattle
     * someone five thousand blocks away.
     */
    private static final double STOMP_SHAKE_RADIUS = 20.0D;

    /** Shake strength directly under the foot. The library's own example uses 0.35 for an ordinary
     *  impact; this is a boss dropping its weight, so a little above that. */
    private static final float STOMP_SHAKE_AMPLITUDE = 0.5F;
    /** Ten ticks, which is what the legacy's {@code ShakeCameraPacket(10, ...)} used. */
    private static final int STOMP_SHAKE_TICKS = 10;

    @Override
    public void registerAnimations() {
        StandardAnimation idle = new StandardAnimation("idle",
                new AnimSource(() -> GTAnimationsBase.idle), Loop.REPEATING, 0, 3, 10.0F);
        StandardAnimation walk = new StandardAnimation("walk",
                new AnimSource(() -> GTAnimationsBase.walk), Loop.REPEATING, 0, 3, 3.0F);

        walk.setPlayCondition(a -> this.isMoving());

        // Asymmetric on purpose, because the two directions do not feel the same. Settling into idle
        // (walk to idle) reads well at 500/400 and is left alone. Setting off (idle to walk) read
        // quick, and that transition is governed by the OTHER two numbers: walk's blend in and idle's
        // blend out. Those are the two that went up.
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
        // (fwd runs 0..length), while halfWidth and halfHeight are centred on it. The first pass had
        // all three frontal attacks as a five-block-tall cube floating at chest height, which is what
        // "las hitboxes son enormes" was looking at.
        //
        // The legacy's own box is flat and grounded: half-size 4 in X and Z, and Y from the animal's
        // FEET to two blocks up. Kept flat here; the horizontal extent is narrowed per attack, because
        // the legacy gave the bite, the horn and the claw the identical volume and they should not
        // feel the same.
        //
        // El mordisco y la cornada comparten caja, por decisión de juego: estrecharle el mordisco lo
        // hacía fallar contra alguien que se movía de lado, y en la práctica los dos son el mismo
        // gesto de "lo que tenga delante". El zarpazo se distingue por ir más bajo, no por ser distinto
        // de ancho.
        HitWindow.of(BITE_WINDOW_START, BITE_WINDOW_END)
                .shape(AttackShape.box3d(FRONTAL_LENGTH, FRONTAL_HALF_WIDTH, FRONTAL_HALF_HEIGHT))
                .anchor(FRONTAL_START, 0.0F, 1.5F)
                .damage(18.0F)
                .knockback(0.35F)
                .filter(target -> !(target instanceof GTEntity))
                .applyTo(bite);

        // El clip de correr, que el módulo 1 dejó sin gastar. El legacy SÍ lo animaba, con su propio
        // estado de sprint. La condición cuelga de syncedState(): CortexMonster sincroniza el estado
        // al cliente precisamente para esto, y las condiciones de reproducción corren en los dos lados.
        StandardAnimation sprint = new StandardAnimation("sprint",
                new AnimSource(() -> GTAnimationsBase.sprint), Loop.REPEATING, 0, 3, 1.0F);
        sprint.setPlayCondition(a -> this.isMoving() && this.syncedState() == GTState.CHASE);
        sprint.blendInMs(400).blendOutMs(400);

        // Y andar deja de valer mientras persigue, o los dos ciclos se pelean por la misma capa.
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

        // La cornada: misma caja que el mordisco, y la que más empuja de las cuatro.
        HitWindow.of(SWING_WINDOW_START, SWING_WINDOW_END)
                .shape(AttackShape.box3d(FRONTAL_LENGTH, FRONTAL_HALF_WIDTH, FRONTAL_HALF_HEIGHT))
                .anchor(FRONTAL_START, 0.0F, 1.5F)
                .damage(20.0F)
                // 1.8 y no el 0.90 del legacy: es la cornada, y tiene que MANDARTE a algún sitio. Es la
                // diferencia de carácter entre ésta y el zarpazo, que pega parecido y apenas mueve.
                .knockback(1.80F)
                .filter(target -> !(target instanceof GTEntity))
                .applyTo(hornSwing);

        // El zarpazo: la misma caja, anclada más abajo — va a las piernas. Es lo único que lo
        // distingue de los otros dos, y verificado en juego que así queda bien.
        HitWindow.of(SWING_WINDOW_START, SWING_WINDOW_END)
                .shape(AttackShape.box3d(FRONTAL_LENGTH, FRONTAL_HALF_WIDTH, FRONTAL_HALF_HEIGHT))
                .anchor(FRONTAL_START, 0.0F, 1.0F)
                .damage(18.0F)
                .knockback(0.60F)
                .filter(target -> !(target instanceof GTEntity))
                .applyTo(clawSwing);

        // El pisotón: tres impactos, radial y anclado en el propio bicho — es un golpe de área
        // alrededor de sus pies, no un barrido frontal.
        //
        // RADIO 8, no 4. El 4 de la primera pasada salía del constructor de GTAttackController, y ese
        // valor el legacy lo DESCARTA: configureAttackHitboxes() del GTEntity lo sobrescribe con
        // half-size 10. Leí el default en vez del override, y por eso el pisotón se veía diminuto al
        // lado de unos frontales que además estaban inflados.
        //
        // UN DISCO PEGADO AL SUELO, y ninguna forma de la librería lo es por sí sola. Se compone de dos
        // mitades, porque cada una aporta lo que a la otra le falta:
        //
        //   - Sector(radio, 360) da la HUELLA. Mide sólo la distancia horizontal — ignora la Y por
        //     completo — así que el radio es uniforme, a diferencia de una esfera, cuyo alcance se
        //     estrecha conforme te alejas del centro en vertical. Pero por lo mismo es un cilindro
        //     infinito: sin nada más, te alcanza estés a la altura que estés.
        //   - El filtro pone el TECHO. Recibe al objetivo, así que puede comparar su Y con la del bicho.
        //
        // Juntas: radial, de radio constante, y esquivable saltando. La primera pasada usaba una esfera
        // de radio 8 y saltar no servía de nada, porque ocho bloques de radio te cubren en el aire.
        //
        // Una HitWindow POR FRAME, y no una de 14 a 46: cada instancia guarda su propio hitThisSwing,
        // así que una sola ventana larga golpearía una vez y se callaría los otros dos impactos.
        for (int frame : STOMP_FRAMES) {
            // Ventana de UN tick, no de dos. onSweep es un hook por tick de ventana, así que una
            // ventana de dos ticks dispararía el polvo y la sacudida dos veces por impacto. El daño no
            // se duplicaría —cada HitWindow lleva su propio hitThisSwing— pero el efecto sí.
            HitWindow.of(frame, frame)
                    .shape(AttackShape.sector(STOMP_RADIUS, 360.0F))
                    .anchor(0.0F, 0.0F, 0.0F)
                    .damage(26.0F)
                    .knockback(0.10F)
                    .filter(target -> !(target instanceof GTEntity)
                            && target.getY() - this.getY() <= STOMP_MAX_HEIGHT)
                    // onSweep y no onHit: dispara aunque no haya alcanzado a nadie, que es lo que un
                    // efecto ambiental necesita. El legacy emitía el polvo igualmente en cada frame.
                    .onSweep((attacker, origin, facing, shape, hits) -> this.onStompImpact())
                    .applyTo(stomp);
        }

        // El rugido. Prioridad 0 como los ataques: gana el frame a la locomocion, que sigue debajo.
        StandardAnimation roar = new StandardAnimation("roar",
                new AnimSource(() -> GTAnimationsBase.roar), Loop.PLAY_ONCE, 0, 0, 5.2F);
        roar.blendInMs(200).blendOutMs(300);

        // Las seis fases del sueno. Se registran con el NOMBRE QUE PIDE SleepPhase, no con el del campo
        // del clip: la fase PREPARING_SLEEP busca "preparing_sleep" y el clip autorado se llama
        // "sleep_preparing". Registrarlo con su nombre de campo lo dejaria invisible para el sistema.
        //
        // Prioridad 1: por debajo de los ataques, por encima de la locomocion. Un animal dormido no debe
        // tener el ciclo de idle peleandole el frame.
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

        // Los uno-shot los arranca onSleepPhaseBegin; estas condiciones hacen lo contrario, que es
        // igual de necesario: una condicion que se vuelve falsa CORTA el clip, y eso es lo que hace que
        // despertar a mitad de una transicion termine la transicion en vez de reproducirla entera.
        // La regla de los blends del ciclo, que NO es "un numero generoso para todos".
        //
        // Un blend solo sirve para tapar un salto de pose, y no es gratis: la capa saliente se CONGELA
        // (BlendState congela los loops al desplazarlos) mientras la entrante se multiplica por una
        // rampa smoothstep, asi que el clip nuevo no se reproduce a su velocidad, sino a
        // `peso * avance`. Se queda casi quieto y luego corre de mas para alcanzarse. Medido en la
        // cabeza de `preparing_sleep`, que baja a 2.62 grados/tick constantes: con 400 ms la mezcla da
        // 0.11, 0.71, 1.67, 2.76, 3.72, 4.32, 4.30, 3.41 y ya 2.62 — quieta 100 ms y luego un 65%
        // pasada de rosca. Ese era el tiron que se reportaba, sobre una costura que estaba PERFECTA.
        //
        // Asi que la pregunta por clip es doble: por que costuras se puede entrar en el, y cuanto se
        // mueve el clip por si mismo en su primer tick. Un blend vale la pena solo cuando el PEOR
        // desajuste por el que se puede entrar es mayor que el arranque propio del clip — si el clip ya
        // mueve mas que el salto, el salto no se ve y el blend solo aporta su deformacion.
        //
        // Las seis costuras del ciclo se midieron canal por canal y estan limpias (0.00). Lo unico que
        // ensucia es un loop cortado a mitad, y de ahi salen los tres primeros casos:
        //
        //   - `sitting_down` no entra por una costura del ciclo: viene del idle, cortado en cualquier
        //     punto de su respiracion. Peor desajuste 32.5 contra un arranque propio de 4.61 — siete
        //     veces mas. BLEND.
        //   - `awakening` entra desde el loop `sleep`. En el despertar tranquilo `SleepGoal` lo corta en
        //     la costura y sale limpio, pero a quien despierta de un golpe lo saca al instante y ese
        //     corte cae donde caiga del respiro: 14.2 en los brazos contra un arranque de 0.48. BLEND.
        //   - `standing_up` tiene el mismo riesgo — si una amenaza corta la bajada estando sentado,
        //     sale de mitad del loop de `sit`, 14.2 — pero arranca a 18.12 unidades/tick, o sea que su
        //     propio primer frame es MAS grande que el salto, y con 50 ms la mezcla sale identica al
        //     clip en los dos casos. Con 400 ms, en cambio, se quedaba 3 ticks casi parada (0.78, 4.89,
        //     11.54) y luego se pasaba un 30%: el blend no tapaba nada y metia el tiron. CORTE.
        //   - `sit` entra desde `sitting_down` o desde `awakening`, dos uno-shot que siempre suenan
        //     enteros (su fase dura exactamente lo que el clip). Costura limpia siempre. CORTE.
        //   - `sleep` entra solo desde `preparing_sleep`, tambien entero — si algo interrumpe la
        //     bajada, el ciclo se va a `standing_up`, no aqui. Costura limpia siempre. CORTE.
        //   - `preparing_sleep` entra desde `sit`, cuya fase dura loops ENTEROS. Costura limpia. CORTE.
        //
        // Y el blend de los dos que lo conservan sale barato porque los dos arrancan despacio, asi que
        // la deformacion de la rampa se queda por debajo de la velocidad propia del clip: medido en
        // `awakening` sobre costura limpia, +0% hasta 300 ms y +7% a 450.
        sittingDown.blendInMs(350).blendOutMs(400);
        sitting.blendInMs(50).blendOutMs(400);
        preparingSleep.blendInMs(50).blendOutMs(400);
        sleeping.blendInMs(50).blendOutMs(450);
        awakening.blendInMs(450).blendOutMs(400);
        standingUp.blendInMs(50).blendOutMs(350);

        sittingDown.setPlayCondition(a -> this.sleepPhase() == SleepPhase.SITTING_DOWN);
        sitting.setPlayCondition(a -> this.sleepPhase() == SleepPhase.SITTING);
        preparingSleep.setPlayCondition(a -> this.sleepPhase() == SleepPhase.PREPARING_SLEEP);
        sleeping.setPlayCondition(a -> this.sleepPhase() == SleepPhase.SLEEPING);
        awakening.setPlayCondition(a -> this.sleepPhase() == SleepPhase.AWAKENING);
        standingUp.setPlayCondition(a -> this.sleepPhase() == SleepPhase.STANDING_UP);

        // Y la locomocion se calla durante todo el ciclo, o el idle se pelea con la postura.
        idle.setPlayCondition(a -> !this.isMoving() && !this.isInSleepCycle());

        this.animator().register(idle, walk, sprint, bite, hornSwing, clawSwing, stomp, roar,
                sittingDown, sitting, preparingSleep, sleeping, awakening, standingUp);
        // registerDeath and not a play condition: MobAnimator hooks LivingDeathEvent itself and holds
        // the corpse for exactly as long as the clip runs.
        this.animator().registerDeath(death);
    }
}

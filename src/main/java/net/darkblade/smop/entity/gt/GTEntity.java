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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class GTEntity extends CortexMonster<GTEntity, GTState> implements Animatable<GTEntity>, ISleepingEntity {

    private static final float TURN_SPEED = 2.5F;
    private static final float COMBAT_TURN_SPEED = 7.0F;
    private static final double FACE_LOCK_RADIUS = 10.0D;

    private static final float BODY_LAG_STILL = 0.02F;
    private static final float BODY_MAX_TURN = 10.0F;
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

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    // ───────────────────────────────────────────────────── CORTEX ─────

    @Override
    protected @NotNull GTState defaultState() {
        return GTState.WANDER;
    }

    private static final int WANDER_RANGE_H = 25;
    private static final int WANDER_RANGE_V = 10;

    private static final double CHASE_SPEED = 2.0D;

    private static final double TARGET_RANGE = 20.0D;
    private static final int GRUDGE_TICKS = 400;


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
                        .faceTargetUntil(6))
                .register(GTState.HORN_SWING, new AnimatedMeleeBehavior<GTEntity, GTState>(
                        "horn_swing", HORN_SWING_TICKS, GTState.CHASE)
                        .faceTargetUntil(8))
                .register(GTState.CLAW_SWING, new AnimatedMeleeBehavior<GTEntity, GTState>(
                        "claw_swing", CLAW_SWING_TICKS, GTState.CHASE)
                        .faceTargetUntil(8))
                .register(GTState.STOMP, new AnimatedMeleeBehavior<GTEntity, GTState>(
                        "attack_stomp", STOMP_TICKS, GTState.CHASE))
                .register(GTState.ROAR, new TimedAnimationBehavior<GTEntity, GTState>(
                        "roar", ROAR_TICKS, GTState.CHASE).faceTarget())
                .globalRule(this::roarAtNewTarget)
                .build();
    }


    private void onFootfall(boolean leftFoot, float amplitude) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double yaw = Math.toRadians(this.yBodyRot);
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

        ParticleOptions debris = new BlockParticleOption(ParticleTypes.BLOCK, this.getBlockStateOn());
        Vec3 feet = new Vec3(this.getX(), this.getY(), this.getZ());

        ParticleFx.ring(serverLevel, debris, feet, STOMP_RADIUS);
        ParticleFx.burst(serverLevel, debris, feet, 40, 1.5D, 0.25D);

        SMOPNetwork.INSTANCE.sendToPlayersInLevel(serverLevel,
                new StompCrackFxClientPacket(this.blockPosition(), (int) STOMP_RADIUS));

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

    private static final int ROAR_TICKS = 104;

    private static final int ROAR_COOLDOWN_TICKS = 600;

    @Nullable
    private LivingEntity lastRoaredAt;

    private long nextRoarTime;

    private @Nullable Integer roarAtNewTarget(GTEntity gt, BehaviorContext context, int currentStateId) {
        LivingEntity target = gt.getTarget();
        if (target == null || !target.isAlive()) {
            gt.lastRoaredAt = null;
            return null;
        }
        if (target == gt.lastRoaredAt || currentStateId == GTState.ROAR.id() || gt.isInSleepCycle()) {
            return null;
        }
        if (gt.level().getGameTime() < gt.nextRoarTime) {
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


    private static final int SITTING_DOWN_TICKS = 38;
    private static final int PREPARING_SLEEP_TICKS = 60;
    private static final int SLEEPING_TICKS = 88;
    private static final int AWAKENING_TICKS = 80;
    private static final int STANDING_UP_TICKS = 80;

    private static final int SIT_CLIP_TICKS = 88;
    private static final int SIT_MIN_LOOPS = 1;
    private static final int SIT_MAX_LOOPS = 2;

    private SleepUrge sleepUrge;

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


    @Override
    public void onSleepPhaseBegin(@NotNull SleepPhase phase) {
        String clip = phase.clipName();
        if (clip != null) {
            this.animator().play(this.animator().getByName(clip));
        }
    }


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

    @Override
    public boolean hurtServer(@NotNull ServerLevel level, @NotNull DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && this.isInSleepCycle()) {
            this.sleepUrge().requestWake();
        }
        return hurt;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            if (this.pendingLoneCheck && this.level() instanceof ServerLevel serverLevel) {
                this.settleAlone(serverLevel);
            }
        }
    }

    // ───────────────────────────────────────────────────── ONE TO A PLACE ─────

    private static final int LONE_RADIUS = 320;

    private static final String PENDING_LONE_CHECK = "PendingLoneCheck";

    private boolean pendingLoneCheck;

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level,
                                                  @NotNull DifficultyInstance difficulty,
                                                  @NotNull EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData spawnData) {
        this.pendingLoneCheck = reason == EntitySpawnReason.NATURAL
                || reason == EntitySpawnReason.CHUNK_GENERATION;
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    private void settleAlone(ServerLevel level) {
        this.pendingLoneCheck = false;
        if (!GTLandmarks.of(level).claim(this.blockPosition(), LONE_RADIUS)) {
            this.discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(@NotNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (this.pendingLoneCheck) {
            output.putBoolean(PENDING_LONE_CHECK, true);
        }
    }

    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.pendingLoneCheck = input.getBooleanOr(PENDING_LONE_CHECK, false);
    }

    // ───────────────────────────────────────────────────── ANIMATIONS ─────

    @Override
    public @NotNull MobAnimator<GTEntity> animator() {
        return this.animator;
    }

    private static final int BITE_TICKS = 17;

    private static final int BITE_WINDOW_START = 7;
    private static final int BITE_WINDOW_END = 9;

    private static final int HORN_SWING_TICKS = 19;
    private static final int CLAW_SWING_TICKS = 32;
    private static final int STOMP_TICKS = 67;

    private static final int SWING_WINDOW_START = 9;
    private static final int SWING_WINDOW_END = 11;


    static final float FRONTAL_START = 3.0F;
    static final float FRONTAL_LENGTH = 5.5F;

    static final float FRONTAL_HALF_WIDTH = 3.0F;
    private static final float FRONTAL_HALF_HEIGHT = 1.5F;

    private static final int[] STOMP_FRAMES = {14, 26, 46};

    private static final float STOMP_RADIUS = 8.0F;
    private static final double STOMP_MAX_HEIGHT = 1.0D;

    private static final double STOMP_SHAKE_RADIUS = 20.0D;

    private static final float STOMP_SHAKE_AMPLITUDE = 0.5F;
    private static final int STOMP_SHAKE_TICKS = 10;


    private static final int WALK_LEFT_FOOTFALL = 7;
    private static final int WALK_RIGHT_FOOTFALL = 37;
    private static final int SPRINT_LEFT_FOOTFALL = 3;
    private static final int SPRINT_RIGHT_FOOTFALL = 13;


    private static final float WALK_SHAKE_AMPLITUDE = 0.25F;
    private static final float SPRINT_SHAKE_AMPLITUDE = 0.40F;
    private static final double FOOTFALL_SHAKE_RADIUS = 14.0D;
    private static final int FOOTFALL_SHAKE_TICKS = 3;
    private static final double FOOTFALL_LATERAL_OFFSET = 1.2D;
    private static final int FOOTFALL_DUST_COUNT = 18;
    private static final double FOOTFALL_DUST_SPREAD = 0.7D;


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

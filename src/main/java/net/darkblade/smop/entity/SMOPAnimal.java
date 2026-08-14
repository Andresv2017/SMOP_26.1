package net.darkblade.smop.entity;

import net.darkblade.deluxelib.anim.Animatable;
import net.darkblade.deluxelib.anim.Animation;
import net.darkblade.deluxelib.anim.BaseAnimation;
import net.darkblade.deluxelib.anim.BlendLayer;
import net.darkblade.deluxelib.anim.MobAnimator;
import net.darkblade.smop.block.AbstractEggBlock;
import net.darkblade.smop.entity.sleep.ISleepingEntity;
import net.darkblade.smop.entity.sleep.SleepGoal;
import net.darkblade.smop.entity.sleep.SleepPhase;
import net.darkblade.smop.entity.sleep.SleepUrge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared base for every SMOP creature: sleep cycle, roaring, egg laying, and the tame
 * sit/follow/wander order state.
 *
 * <p><b>Port note — this class no longer animates anything.</b> The 1.20.1 {@code BaseEntity} owned
 * a dozen {@code AnimationState} fields plus {@code updateBaseAnimations()}, a ~100-line imperative
 * cascade of {@code start()}/{@code stop()} calls that every subclass overrode and fought with.
 * All of it is gone. Animation is declarative now: this class only exposes <em>state</em> (all of it
 * synced), and each mob's {@code registerAnimations()} binds its Blockbench clips to that state with
 * {@code setPlayCondition}. DeluxeLib's {@link MobAnimator} handles layering, priorities, blending
 * and the auto-start loop.
 *
 * <p>Because play conditions are evaluated on both sides, anything they read has to agree on both
 * sides — which is why {@link #isMoving()} is a synced flag fed by a hold timer rather than a direct
 * read of {@code getDeltaMovement()} (not synced for mobs, so it would flicker client-side).
 */
public abstract class SMOPAnimal extends TamableAnimal implements Animatable<SMOPAnimal>, ISleepingEntity {

    // ───────────────────────────────────────────────────── SYNCED STATE ─────

    /**
     * Ordinal of the current {@link SleepPhase}. One field for the whole cycle, rather than a flag per
     * phase: the cycle is a state machine and only ever occupies one of its states, so three booleans
     * were already able to encode nonsense, and six would have been worse. Same call as {@link #ACTION}
     * below.
     */
    private static final EntityDataAccessor<Integer> SLEEP_PHASE =
            SynchedEntityData.defineId(SMOPAnimal.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> WANDERING =
            SynchedEntityData.defineId(SMOPAnimal.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HAS_EGG =
            SynchedEntityData.defineId(SMOPAnimal.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ROARING =
            SynchedEntityData.defineId(SMOPAnimal.class, EntityDataSerializers.BOOLEAN);
    /**
     * Name of the one-shot scripted action currently playing ({@code "eating"}, {@code "tamed"},
     * {@code "steal"}, {@code "squawk"}, ...), or {@code ""} for none. See {@link #startAction}.
     */
    private static final EntityDataAccessor<String> ACTION =
            SynchedEntityData.defineId(SMOPAnimal.class, EntityDataSerializers.STRING);
    /**
     * Ground movement, held for a few ticks past the last movement so a walk clip does not strobe.
     * Synced because a play condition is evaluated on both sides and {@code getDeltaMovement()} is
     * not synced for mobs.
     *
     * <p>There is deliberately no {@code ATTACKING} flag alongside it: attacks are one-shot clips
     * started imperatively by the attack goal and mirrored to clients by the animation sync, and
     * "am I fighting" is already covered by vanilla's synced {@code isAggressive()}, which
     * {@code MeleeAttackGoal} raises for free.
     */
    private static final EntityDataAccessor<Boolean> MOVING =
            SynchedEntityData.defineId(SMOPAnimal.class, EntityDataSerializers.BOOLEAN);

    private MobAnimator<SMOPAnimal> animator;

    protected SMOPAnimal(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    /** Lazily built for the same initialisation-order reason as {@link #sleepUrge()}. */
    @Override
    public @NotNull MobAnimator<SMOPAnimal> animator() {
        if (this.animator == null) {
            this.animator = new MobAnimator<>(this);
        }
        return this.animator;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SLEEP_PHASE, SleepPhase.NONE.ordinal());
        builder.define(WANDERING, false);
        builder.define(HAS_EGG, false);
        builder.define(ROARING, false);
        builder.define(ACTION, "");
        builder.define(MOVING, false);
    }

    // ───────────────────────────────────────────────────── TICK ─────

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide()) {
            this.sleepUrge().tick();
            this.entityData.set(MOVING, this.moveHold.tick(this.isMovingNow()));
            if (this.actionTicksLeft > 0 && --this.actionTicksLeft <= 0) {
                this.stopAction();
            }
        }

        if (this.isOrderedToSit()) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    /** Corpse lingers for as long as the death clip registered via {@code registerDeath} runs. */
    @Override
    protected void tickDeath() {
        this.animator().tickDeath();
    }

    @Override
    public void aiStep() {
        if (this.isInSleepCycle()) {
            this.setTarget(null);
        }
        super.aiStep();
    }

    /**
     * Drops the horizontal component while the mob is pinned, so momentum and knockback cannot slide
     * a sleeping or roaring mob around. Gravity still applies.
     *
     * <p>Vanilla's {@code isImmobile()} hook is deliberately <em>not</em> used: returning true there
     * makes {@code LivingEntity.aiStep} skip {@code serverAiStep()} altogether, which would stop the
     * goals from ticking — including the very {@link SleepGoal} that has to count down and wake the
     * mob back up. Vanilla's fox avoids it for the same reason.
     */
    @Override
    public void travel(@NotNull Vec3 travelVector) {
        if (this.isMovementLocked()) {
            this.getNavigation().stop();
            super.travel(new Vec3(0.0D, travelVector.y, 0.0D));
            return;
        }
        super.travel(travelVector);
    }

    /**
     * States that pin the mob in place. Subclasses can add their own (grabbed, perched, ...).
     *
     * <p>Public because goals need it too: a movement goal that keeps issuing orders to a pinned mob
     * is not merely wasted work, it fights the lock in {@link #travel} every tick.
     */
    public boolean isMovementLocked() {
        return this.isInSleepCycle() || this.isRoaring()
                || (this.isPerformingAction() && this.actionLocksMovement(this.currentAction()));
    }

    @Override
    public boolean hurtServer(@NotNull ServerLevel level, @NotNull DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && !this.isRoaring() && this.getTarget() == null) {
            this.sleepUrge().requestWake();
        }
        return hurt;
    }

    // ───────────────────────────────────────────────────── MOVEMENT FLAG ─────

    /**
     * Hold timer that keeps {@link #MOVING} true for a few ticks after the mob stops, so a walk
     * clip does not strobe back to idle on every micro-pause in pathing.
     *
     * <p>DeluxeLib has this exact helper ({@code MovementHysteresis}) but it is package-private, so
     * it cannot be reused from here — see PORT_ANALYSIS.md.
     */
    private static final int MOVE_HOLD_TICKS = 6;

    private final MoveHold moveHold = new MoveHold();

    /** Protected so swimmers can run a second one for their sprint threshold — see
     *  {@code SMOPWaterAnimal#isSwimmingFast()}. */
    protected static final class MoveHold {
        private int holdTicks;

        public boolean tick(boolean movingNow) {
            if (movingNow) {
                this.holdTicks = MOVE_HOLD_TICKS;
                return true;
            }
            if (this.holdTicks > 0) {
                this.holdTicks--;
                return true;
            }
            return false;
        }
    }

    /**
     * Server-side sample. Override for mobs whose "moving" means something else (swimming, flying) —
     * or that can be <em>ridden</em>, where neither this nor the position difference works and the
     * answer has to come from the rider's input. See {@code HellHippoEntity#isMovingNow}.
     */
    protected boolean isMovingNow() {
        return this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D;
    }

    /** Synced — safe to read from an animation play condition on either side. */
    public boolean isMoving() {
        return this.entityData.get(MOVING);
    }

    // ───────────────────────────────────────────────────── SIT / FOLLOW / WANDER ─────

    public boolean isWandering() {
        return this.entityData.get(WANDERING);
    }

    public void setWandering(boolean wandering) {
        this.entityData.set(WANDERING, wandering);
    }

    /** Shift-click by the owner cycles wandering → staying → following → wandering. */
    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        if (this.isOwnedBy(player) && player.isShiftKeyDown()) {
            if (!this.isOrderedToSit() && !this.isWandering()) {
                this.setWandering(true);
                this.setOrderedToSit(false);
                this.messageState("wandering", player);
            } else {
                this.setWandering(false);
                boolean willSit = !this.isOrderedToSit();
                this.setOrderedToSit(willSit);
                this.messageState(willSit ? "staying" : "following", player);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    /** 26.1: {@code displayClientMessage(msg, true)} is now {@code sendOverlayMessage} (action bar). */
    protected void messageState(String state, Player player) {
        player.sendOverlayMessage(this.getName().copy().append(" is now ").append(state));
    }

    // ───────────────────────────────────────────────────── SLEEP ─────

    @Override
    public SleepPhase sleepPhase() {
        return SleepPhase.byId(this.entityData.get(SLEEP_PHASE));
    }

    @Override
    public void setSleepPhase(SleepPhase phase) {
        this.entityData.set(SLEEP_PHASE, phase.ordinal());
    }

    /**
     * Derived like the rest of the readers, but declared here rather than defaulted on the interface:
     * this one also overrides {@code LivingEntity#isSleeping()}, which vanilla defines off the bed
     * system. Keeping the override is deliberate — it is what makes vanilla see these mobs as asleep.
     */
    @Override
    public boolean isSleeping() {
        return this.sleepPhase() == SleepPhase.SLEEPING;
    }

    /** ~3 to 8 seconds. @see ISleepingEntity#getSittingDuration() */
    @Override
    public int getSittingDuration() {
        return 60 + this.random.nextInt(100);
    }

    private SleepUrge sleepUrge;

    /**
     * Built on first use, never as a field initializer.
     *
     * <p>{@code Mob}'s constructor calls {@link #registerGoals()} (Mob.java:158), and a subclass's
     * field initializers do not run until the superclass constructor has returned — so anything
     * {@code registerGoals} touches is still {@code null} at that point. An eagerly initialised
     * field here crashes on the first tick of the first mob spawned, with a
     * {@code NullPointerException} inside {@code SleepGoal.canUse}.
     *
     * <p>Same reasoning applies to anything else a goal needs at construction time.
     */
    public SleepUrge sleepUrge() {
        if (this.sleepUrge == null) {
            this.sleepUrge = new SleepUrge(this);
        }
        return this.sleepUrge;
    }

    /**
     * Builds this mob's sleep goal. Add it in {@code registerGoals()} <b>above the locomotion and
     * combat goals</b> — it holds MOVE/LOOK/JUMP, which is what preempts them, so registering it
     * below would let the mob walk around in its sleep:
     *
     * <pre>{@code
     * this.goalSelector.addGoal(0, new FloatGoal(this));   // still float, do not drown
     * this.goalSelector.addGoal(1, this.createSleepGoal());
     * this.goalSelector.addGoal(2, new OneAttackGoal<>(this, 1.8D, true));
     * }</pre>
     *
     * <p>Target goals live in a separate selector with its own flag map, so they are <em>not</em>
     * preempted — gate those on {@link #isInSleepCycle()} by hand, exactly as vanilla's fox does.
     */
    protected SleepGoal<SMOPAnimal> createSleepGoal() {
        return new SleepGoal<>(this, this.sleepUrge());
    }

    /**
     * Phase length from the phase's own clip, which is what keeps the two from drifting: a phase
     * longer than its clip leaves the mob holding the last frame for the difference, a shorter one
     * cuts the clip mid-motion. Same principle as {@code MobAnimator#startStagger} sizing its stun
     * window from the chosen animation — two numbers that must be equal are one number.
     *
     * <p>Sitting is the one phase whose length is a behaviour rather than an animation: the clip
     * loops, so how long to sit is a decision ({@link #getSittingDuration()}). The clip is still what
     * decides whether the phase exists at all.
     */
    @Override
    public int sleepPhaseDuration(SleepPhase phase) {
        String clip = phase.clipName();
        if (clip == null) {
            return 0;
        }
        int clipTicks = this.clipDurationTicks(clip);
        if (phase == SleepPhase.SITTING) {
            return clipTicks > 0 ? this.getSittingDuration() : 0;
        }
        return clipTicks;
    }

    /**
     * Duration in ticks of a registered clip, or 0 if this mob never registered one under that
     * name. {@code MobAnimator#getByName} throws on an unknown name and offers no "is this
     * registered" query, hence walking the layers directly (same reason as {@link #playIfRegistered}).
     */
    protected int clipDurationTicks(String name) {
        for (BlendLayer layer : this.animator().getLayers().values()) {
            if (layer.anims.get(name) instanceof BaseAnimation base) {
                return base.getDuration();
            }
        }
        return 0;
    }

    /** Clip names the sleep cycle drives now live on {@link SleepPhase#clipName()}. */
    public static final String ANIM_SLEEP = SleepPhase.SLEEPING.clipName();
    /** Not driven by the sleep cycle, but its length sizes {@link #getRoarDuration()} the same way. */
    public static final String ANIM_ROAR = "roar";

    /**
     * Plays the clip named after the phase. Entirely mechanical, which is why the three per-phase
     * hooks this replaced were pure indirection — every one of them was this same line.
     */
    @Override
    public void onSleepPhaseBegin(SleepPhase phase) {
        String clip = phase.clipName();
        if (clip != null) {
            this.playIfRegistered(clip);
        }
    }

    /**
     * Starts a clip by name, doing nothing if this mob never registered one under that name — so a
     * species with no sleep animations still sleeps, it just does it without a transition.
     *
     * <p>{@code MobAnimator#getByName} throws when the name is unknown and there is no "is this
     * registered" query, hence walking the layers directly.
     */
    protected void playIfRegistered(String name) {
        MobAnimator<SMOPAnimal> animator = this.animator();
        for (BlendLayer layer : animator.getLayers().values()) {
            Animation anim = layer.anims.get(name);
            if (anim != null) {
                animator.play(anim);
                return;
            }
        }
    }

    // ───────────────────────────────────────────────────── REPRODUCTION ─────

    private boolean isMammal = false;

    /** Mammals give birth directly and never set {@link #hasEgg()}. */
    public boolean isMammal() {
        return this.isMammal;
    }

    public void setMammal(boolean mammal) {
        this.isMammal = mammal;
    }

    public boolean hasEgg() {
        return this.entityData.get(HAS_EGG);
    }

    public void setHasEgg(boolean hasEgg) {
        this.entityData.set(HAS_EGG, hasEgg);
    }

    /**
     * Drops the carried egg at the mob's feet if the spot allows it.
     *
     * @return where the egg landed, or {@code null} if it could not be placed
     */
    @Nullable
    public BlockPos tryLayEgg(Block eggBlock) {
        if (!this.hasEgg() || this.isMammal() || !this.isSettledToLay()) {
            return null;
        }

        BlockPos pos = this.blockPosition();
        Level level = this.level();
        if (!this.canPlaceEggAt(level, pos)) {
            return null;
        }

        // defaultBlockState() would always be a clutch of one — the block decides how many eggs a
        // laying actually produces (see AbstractEggBlock#newClutchState).
        BlockState clutch = eggBlock instanceof AbstractEggBlock egg
                ? egg.newClutchState(this.random)
                : eggBlock.defaultBlockState();
        level.setBlock(pos, clutch, Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.TURTLE_LAY_EGG, SoundSource.BLOCKS, 1.0F, 1.0F);
        this.setHasEgg(false);
        return pos;
    }

    /** Land mobs must be standing still on the ground; swimmers override this. */
    protected boolean isSettledToLay() {
        return this.onGround();
    }

    /** Air above a solid block. Water mobs override with a water-source test. */
    protected boolean canPlaceEggAt(Level level, BlockPos pos) {
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isSolid();
    }

    // ───────────────────────────────────────────────────── ROAR ─────

    /** Minimum gap between roar sounds, so a flurry of hits cannot machine-gun the sound. */
    private static final int ROAR_SOUND_MIN_GAP = 2;

    private int lastRoarSoundTick = -200;

    /**
     * How long the roar pins the mob, taken from the registered {@code roar} clip so the movement
     * lock and the animation cannot drift apart. Falls back to 70 ticks for a mob that roars
     * without an authored clip. @see #sleepPhaseDuration(SleepPhase)
     */
    public int getRoarDuration() {
        int clip = this.clipDurationTicks(ANIM_ROAR);
        return clip > 0 ? clip : 70;
    }

    public boolean isRoaring() {
        return this.entityData.get(ROARING);
    }

    public void setRoaring(boolean roaring) {
        boolean was = this.isRoaring();
        this.entityData.set(ROARING, roaring);

        if (was || !roaring || this.level().isClientSide()) {
            return;
        }
        SoundEvent sound = this.getRoarSound();
        if (sound != null && this.tickCount - this.lastRoarSoundTick >= ROAR_SOUND_MIN_GAP) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.HOSTILE,
                    1.0F, 0.95F + this.getRandom().nextFloat() * 0.10F);
            this.lastRoarSoundTick = this.tickCount;
        }
    }

    @Nullable
    public SoundEvent getRoarSound() {
        return null;
    }

    // ───────────────────────────────────────────────────── SCRIPTED ACTION ─────

    private int actionTicksLeft;

    /**
     * Starts a one-shot scripted action by name — {@code "eating"}, {@code "tamed"}, {@code "steal"},
     * {@code "squawk"}, or any other clip name a subclass registers and gates on
     * {@link #isPerforming(String)}. The action's duration is read straight off the registered clip
     * ({@link #clipDurationTicks(String)}), the same principle {@link #getRoarDuration()} uses, so the
     * movement lock and the animation cannot drift apart. Ends on its own once the clip's length has
     * elapsed; call {@link #stopAction()} to cut it short (a hit landing mid-animation, its target
     * despawning, ...).
     *
     * <p>Deliberately not persisted to NBT — a one-shot action interrupted by a save/load is simply
     * dropped rather than resumed, so a reload can never leave a mob pinned by a stale action.
     */
    public void startAction(String name) {
        this.entityData.set(ACTION, name);
        int durationTicks = this.clipDurationTicks(name);
        this.actionTicksLeft = Math.max(1, durationTicks);
        if (!this.level().isClientSide()) {
            this.onActionStart(name);
        }
        // The synced ACTION string above is what setPlayCondition(a -> isPerforming(name)) reads,
        // but MobAnimator#tick's auto-start loop only ever starts REPEATING clips off a play
        // condition turning true — a one-shot action registered PLAY_ONCE (eating, tamed, ...) would
        // sit there with canPlay()==true and isPlaying()==false forever, never actually starting,
        // while actionTicksLeft still counts down and calls stopAction() as if it had played. The
        // caller then sees isPerforming(name) go false right on schedule with nothing ever shown.
        // play() is the direct trigger every other PLAY_ONCE clip in this mod uses (e.g. the
        // Tangoftero's bite from handleFeeding) — this just makes startAction do it too, once, here,
        // instead of every caller needing to remember it.
        //
        // Gated on durationTicks > 0 (not a bare getByName/play call) because getByName throws on an
        // unregistered name and durationTicks already tells us, for free, whether one was found —
        // same safety clipDurationTicks itself already promises ("or 0 if never registered").
        if (durationTicks > 0) {
            this.animator().play(this.animator().getByName(name));
        }
    }

    /** Ends the current action immediately, if any. Safe to call when none is running. */
    public void stopAction() {
        this.entityData.set(ACTION, "");
        this.actionTicksLeft = 0;
    }

    /** Name of the action in progress, or {@code ""} if none. */
    public String currentAction() {
        return this.entityData.get(ACTION);
    }

    /** Whether the named action is the one currently playing. What {@code setPlayCondition} tests. */
    public boolean isPerforming(String name) {
        return this.currentAction().equals(name);
    }

    /** Whether any scripted action is currently playing. */
    public boolean isPerformingAction() {
        return !this.currentAction().isEmpty();
    }

    /**
     * Whether the named action pins the mob in place while it plays — read by
     * {@link #isMovementLocked()}. Defaults to {@code true}; override to exempt actions that have to
     * keep steering, such as a mid-air snatch.
     */
    protected boolean actionLocksMovement(String name) {
        return true;
    }

    /** Hook for a subclass to play a sound or spawn particles when an action starts. Server-only. */
    protected void onActionStart(String name) {
    }

    // ───────────────────────────────────────────────────── NBT ─────

    /**
     * The sleep phase is deliberately <b>not</b> written here, and restoring it would be a bug rather
     * than a feature. {@link SleepGoal#canUse()} refuses to start while {@code isInSleepCycle()} is
     * already true — so a mob loaded mid-cycle came back with the state set and the one thing that
     * drives it standing down. Nothing then advanced the phase, nothing cleared it, and since
     * {@code isInSleepCycle()} feeds {@link #isMovementLocked()}, the mob was frozen asleep for good;
     * not even damage helped, because {@code requestWake()} only raises a flag the goal has to consume.
     *
     * <p>Dropping the state instead means a mob wakes on load and, if it is still night and still calm,
     * {@code SleepUrge} puts it back down within seconds. Losing a few seconds of nap beats a whole
     * class of stuck states.
     */
    @Override
    protected void addAdditionalSaveData(@NotNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Wandering", this.isWandering());
        output.putBoolean("IsMammal", this.isMammal);
        output.putBoolean("HasEgg", this.hasEgg());
    }

    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setWandering(input.getBooleanOr("Wandering", false));
        this.isMammal = input.getBooleanOr("IsMammal", false);
        this.setHasEgg(input.getBooleanOr("HasEgg", false));
    }
}

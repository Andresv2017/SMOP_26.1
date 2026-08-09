package net.darkblade.smop.entity.tangoftero;

import net.darkblade.deluxelib.anim.AnimSource;
import net.darkblade.deluxelib.anim.Loop;
import net.darkblade.deluxelib.anim.StandardAnimation;
import net.darkblade.smop.block.SMOPBlocks;
import net.darkblade.smop.client.tangoftero.TangoAnimations;
import net.darkblade.smop.client.tangoftero.TangoBabyAnimations;
import net.darkblade.smop.entity.SMOPAnimal;
import net.darkblade.smop.entity.ai.goal.FollowOwnerBaseGoal;
import net.darkblade.smop.entity.ai.goal.GenericBreedGoal;
import net.darkblade.deluxelib.combat.AttackShape;
import net.darkblade.deluxelib.combat.HitWindow;
import net.darkblade.deluxelib.entity.ai.goal.AnimatableMeleeAttackGoal;
import net.darkblade.deluxelib.entity.ai.pathing.DirectionalMoveControl;
import net.darkblade.deluxelib.entity.ai.rotation.SmoothBodyRotationControl;
import net.darkblade.smop.entity.ai.goal.SMOPRandomStrollGoal;
import net.darkblade.smop.entity.ai.goal.egg.EggGoalRegistry;
import net.darkblade.smop.entity.ai.goal.egg.ProtectEggBaseGoal;
import net.darkblade.smop.entity.egg.RandomVariantCapable;
import net.darkblade.smop.entity.sleep.ISleepAwareness;
import net.darkblade.smop.entity.sleep.ISleepThreatEvaluator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The Tangoftero: a small, flocking, nest-guarding scavenger that hates the undead.
 *
 * <p>Tame it with a rabbit, breed it with chicken, and feed it rotten flesh to make it roar — a roar
 * that sends every undead nearby running.
 */
public class TangofteroEntity extends SMOPAnimal
        implements ISleepThreatEvaluator, ISleepAwareness, RandomVariantCapable {

    // ───────────────────────────────────────────────────── TUNING ─────

    /** Only one roar per this many ticks, so feeding a stack of flesh is not a panic button. */
    private static final int ROAR_COOLDOWN_TICKS = 600;
    /** Length of the {@code bite} clip (0.75 s); its last frame is what starts an armed roar. */
    private static final int BITE_CLIP_TICKS = 15;
    /** Frame of the {@code roar} clip on which the undead scatter — the loud part of the bellow. */
    private static final int SCARE_FRAME = 40;
    /** How far the roar reaches, and how far it throws the undead's pathing target. */
    private static final double SCARE_RADIUS = 10.0D;
    private static final double SCARE_FLEE_DISTANCE = 7.0D;

    private static final int BITE_COOLDOWN_TICKS = 20;
    private static final float HEAL_ROTTEN_FLESH = 6.0F;
    private static final float HEAL_OTHER_FOOD = 3.0F;

    /** Matches {@code DirectionalMoveControl}'s own face-lock radius, so its steering and
     *  {@link #faceCombatTarget()} cover one continuous range instead of leaving a gap in which
     *  nothing turns the body. */
    private static final double FACE_LOCK_RADIUS = 4.0D;

    /** Wild flock aggro: undead only. Tamed ones also defend against players who hit their owner. */
    public static final Predicate<LivingEntity> UNDEAD_SELECTOR =
            entity -> entity.is(EntityTypeTags.UNDEAD);
    /** Nest defence is less picky — anything that is not another Tangoftero gets warned off. */
    public static final Predicate<LivingEntity> NEST_THREAT_SELECTOR =
            entity -> entity instanceof Player || entity.is(EntityTypeTags.UNDEAD);

    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(TangofteroEntity.class, EntityDataSerializers.INT);

    // ───────────────────────────────────────────────────── STATE ─────

    private int biteCooldown;
    /** A feeding has earned a roar; the {@code bite} clip's last frame cashes it in. */
    private boolean roarArmed;
    private int roarTicksLeft;
    private long lastRoarTick = -ROAR_COOLDOWN_TICKS;

    public TangofteroEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        // Steering, DeluxeLib-style (same pairing as AthenianEntity). Vanilla's MoveControl only
        // writes yRot while it is actively steering toward a waypoint, and the attack goal calls
        // getNavigation().stop() the moment the target is within reach — so from that tick on the
        // body yaw is frozen wherever the mob happened to arrive. Every melee hitbox is built off
        // that yaw (AttackAnchor.resolveBody / HitWindow.resolveFacings both read getYRot()), so a
        // target that circles is bitten at thin air while the head visibly tracks it.
        // DirectionalMoveControl keeps steering at the target inside its face-lock radius, and
        // faceCombatTarget() below carries that through the stationary ticks.
        this.moveControl = new DirectionalMoveControl<>(this).setTurnSpeed(10.0F).setCombatTurnSpeed(40.0F);
    }

    /**
     * 26.1: {@code Mob#bodyRotationControl} is private/final, so a custom control is supplied by
     * overriding this (called from {@code Mob}'s constructor) rather than assigning the field.
     */
    @Override
    protected @NotNull BodyRotationControl createBodyControl() {
        return new SmoothBodyRotationControl<>(this);
    }

    /**
     * Built from {@link Animal#createAnimalAttributes()}, not {@code createLivingAttributes()}.
     *
     * <p>1.20.1 used the latter and it was fine. In 26.1 vanilla's {@code TemptGoal} reads
     * {@code Attributes.TEMPT_RANGE} (TemptGoal.java:58), and an attribute the supplier never
     * declared throws {@code Can't find attribute minecraft:tempt_range} on the first tick.
     * {@code createAnimalAttributes()} is just {@code Mob.createMobAttributes().add(TEMPT_RANGE, 10)}
     * — the correct base for anything extending {@code Animal}.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.FOLLOW_RANGE, 28.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.4D)
                .add(Attributes.ATTACK_SPEED, 0.6D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.1D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                // Required by DirectionalMoveControl, which replaces vanilla's MoveControl#tick
                // wholesale and with it the auto-jump that lifted mobs over one-block ledges. The
                // pathfinder still routes through those ledges, so without a step height that
                // clears them the mob walks into the step and stalls. Same reason AthenianEntity
                // declares it.
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VARIANT, 0);
    }

    // ───────────────────────────────────────────────────── GOALS ─────

    /**
     * Priorities matter more than they used to: {@code SleepGoal} preempts everything below it
     * through its MOVE/LOOK/JUMP flags, and following the owner now outranks wandering (on 1.20.1
     * wandering sat above it and a {@code isFollowingOwner} flag was needed to break the tie).
     */
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, this.createSleepGoal());
        // The goal only decides WHEN to bite; the damage is in the clip's HitWindow (see
        // registerAnimations) so it lands on the frame the jaws actually close.
        this.goalSelector.addGoal(2, new AnimatableMeleeAttackGoal(this, 1.3D, true)
                .reach(2.0F)
                // Gameplay pacing, deliberately not tied to the clip length: the swing runs 17
                // ticks but the goal is free to wait longer between them. Athenian does the same
                // (a 16-tick attack on a .cooldown(10)).
                .cooldown(17)
                .attackCondition(target -> !this.isBaby() && !this.isRoaring())
                .onAttack((target, animator) -> animator.play(animator.getByName("attack"))));
        this.goalSelector.addGoal(3, new GenericBreedGoal<>(this, 1.2D));

        // Colonial nester: any adult minds any clutch, and they scatter rather than avenge it.
        EggGoalRegistry.registerWithNearestGoal(this, SMOPBlocks.TANGOFTERO_EGG,
                32, 3, 5, false, true,
                ProtectEggBaseGoal.EggBreakReaction.FLEE, NEST_THREAT_SELECTOR, 4);

        this.goalSelector.addGoal(6, new FollowOwnerBaseGoal(this, 1.0D, 10.0F, 2.0F));
        this.goalSelector.addGoal(7, new TemptGoal(this, 1.2D, stack -> stack.is(Items.ROTTEN_FLESH), false));
        this.goalSelector.addGoal(8, new FollowParentGoal(this, 1.1D));
        this.goalSelector.addGoal(9, new SMOPRandomStrollGoal(this, 1.0D, 120, () -> true));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 3.0F));
        this.goalSelector.addGoal(11, new RandomLookAroundGoal(this));

        // Target goals live in their own selector with its own flag map, so SleepGoal does NOT
        // preempt them — they each have to check the sleep state themselves.
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new AssistFlockGoal(this, 10.0D));
        this.targetSelector.addGoal(4, new HurtByTargetGoal(this));
        // The !isBaby() here is not what keeps chicks out of fights — setTarget does that for every
        // route at once. It just spares them a box scan for undead they could never act on.
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                (entity, level) -> !this.isBaby() && !this.isInSleepCycle() && UNDEAD_SELECTOR.test(entity)));
    }

    // ───────────────────────────────────────────────────── ANIMATIONS ─────

    /**
     * Adult and baby have separate Blockbench exports whose skeletons genuinely differ — the baby
     * model has no {@code tail_tip}, {@code epiglotis} or {@code muscles}. Baking an adult clip
     * against the baby model therefore throws {@code Cannot animate tail_tip, which does not exist
     * in model}. {@link AnimSource} resolves its supplier on every query, so each clip picks its
     * own definition by age and stays in lockstep with the model the renderer swapped in (both read
     * {@code isBaby} within the same tick).
     *
     * <p>Exclusion between clips is done by <b>play condition, not priority</b>: {@code MobAnimator}
     * only stops animations whose priority is {@code <=} the incoming one, so a lower-priority idle
     * would otherwise keep playing underneath an attack.
     */
    @Override
    public void registerAnimations() {
        StandardAnimation idle = clip("idle", () -> TangoAnimations.idle, () -> TangoBabyAnimations.idle,
                Loop.REPEATING, 3, 0.8F);
        StandardAnimation walk = clip("walk", () -> TangoAnimations.walk, () -> TangoBabyAnimations.walk,
                Loop.REPEATING, 2, 0.7F);
        StandardAnimation sprint = clip("sprint", () -> TangoAnimations.sprint, () -> TangoBabyAnimations.sprint,
                Loop.REPEATING, 2, 0.4F);
        StandardAnimation swim = clip("swim", () -> TangoAnimations.swim, () -> TangoBabyAnimations.swim,
                Loop.REPEATING, 2, 0.8F);

        StandardAnimation preparingSleep = clip("preparing_sleep",
                () -> TangoAnimations.preparing_to_sleep, () -> TangoBabyAnimations.preparing_to_sleep,
                Loop.PLAY_ONCE, 1, 1.0F);
        StandardAnimation sleep = clip("sleep", () -> TangoAnimations.sleep, () -> TangoBabyAnimations.sleep,
                Loop.REPEATING, 1, 2.6F);
        StandardAnimation awakening = clip("awakening", () -> TangoAnimations.awakening, () -> TangoBabyAnimations.awakening,
                Loop.PLAY_ONCE, 1, 1.0F);

        // PLAY_ONCE and condition-less: the attack goal starts it, and the animation sync mirrors it
        // to clients. Same shape as the Athenian/Spartan attacks in DeluxeLib.
        StandardAnimation attack = clip("attack", () -> TangoAnimations.attack, () -> TangoBabyAnimations.attack,
                Loop.PLAY_ONCE, 0, 0.85F);
        // Triggered imperatively (see handleFeeding / roar windup), so they are PLAY_ONCE and have
        // no play condition — MobAnimator only auto-starts REPEATING clips.
        StandardAnimation bite = clip("bite", () -> TangoAnimations.bite, () -> TangoBabyAnimations.bite,
                Loop.PLAY_ONCE, 0, 0.75F);
        // Adult-only: there is no baby roar clip, and the feeding path gates on !isBaby() so the
        // adult definition can never be baked against the baby model.
        StandardAnimation roar = new StandardAnimation("roar",
                new AnimSource(() -> TangoAnimations.roar), Loop.PLAY_ONCE, 0, 0, 4.0F);
        StandardAnimation death = clip("death", () -> TangoAnimations.death, () -> TangoBabyAnimations.death,
                Loop.PLAY_ONCE, 0, 1.5F);

        idle.blendInMs(300).blendOutMs(250);
        walk.blendInMs(200).blendOutMs(200);
        sprint.blendInMs(200).blendOutMs(200);

        // Both cycles are authored short — idle 0.8 s, sprint 0.4 s — which reads as a twitch and a
        // scurry rather than a breath and a run. Slowed visually instead of re-exporting the clips:
        // playbackSpeed only scales the client-side keyframe clock, so the animations' tick
        // lifecycle — duration, frame events, cycle restarts — is untouched and nothing that keys
        // off it can drift. Same knob Arpy uses to pace its wing strokes
        // (flySprint.playbackSpeed(1.2F)).
        idle.playbackSpeed(0.8F);
        sprint.playbackSpeed(0.8F);

        // Roar chain, driven by the clips instead of by three parallel countdowns — same mechanism
        // HitWindow rides on, and the same one the Athenian uses for its swing sound
        // (attack.onFrame(2, ...)). Frame events only ever fire server-side.
        //
        // The swallow hands off to the rear-up: the last frame of the bite starts the roar, if
        // feeding armed one. The old version restarted a 15-tick countdown that just happened to
        // equal the bite's length — two numbers for one beat.
        bite.onFrame(BITE_CLIP_TICKS - 1, e -> ((TangofteroEntity) e).startArmedRoar());
        // ...and the roar hands off to the scare, on the frame the bellow actually lands rather
        // than 40 ticks after some other timer fired.
        roar.onFrame(SCARE_FRAME, e -> ((TangofteroEntity) e).scareUndead());

        // The bite: a strictly frontal box, live only across the frames where the jaws close
        // (6–8 of the 17-tick clip). A BOX rather than a SECTOR because a mouth is not a sweep —
        // a sector of any usable arc also catches whatever stands beside or slightly behind the
        // mob, which reads as the Tangoftero biting sideways. Box#contains rejects anything off
        // the muzzle no matter how close, so what gets bitten is what the head is pointed at.
        // Length runs from the anchor (0.6 ahead of the feet) out to ~2.2, just past the goal's
        // 2.0 reach; halfWidth 0.6 is roughly the jaw's own width. Tune with /deluxelib debug
        // hitboxes. Damage matches the ATTACK_DAMAGE attribute — a literal, same convention as
        // Athenian and Arpy, which do not track the live attribute either.
        // filter: a wild Tangoftero never bites another Tangoftero — this is what keeps the flock
        // from mauling itself over stray HurtByTargetGoal retaliations. A TAMED one is the one
        // exception: it can end up targeting a wild Tangoftero through the same owner-defence goals
        // (OwnerHurtByTargetGoal/OwnerHurtTargetGoal) that let it defend against anything else, and
        // without this escape hatch the bite would silently veto that target before the shape test
        // ever ran — same bug as the Kriftognathus's identical filter, same fix: let the actual
        // target through, keep the blanket exclusion for everyone else of the species.
        HitWindow.of(6, 8)
                .shape(AttackShape.box(1.6F, 0.6F))
                .anchor(0.6F, 0.0F, 0.5F)
                .damage(2.0F)
                .knockback(0.1F)
                .filter(target -> !(target instanceof TangofteroEntity)
                        || (this.isTame() && target == this.getTarget()))
                .applyTo(attack);

        // Mutually exclusive by construction: exactly one of these four holds at any moment.
        // isAggressive() is vanilla's synced "has a combat target" flag, raised by MeleeAttackGoal.
        idle.setPlayCondition(a -> this.canPlayLocomotion() && !this.isInWater() && !this.isMoving());
        walk.setPlayCondition(a -> this.canPlayLocomotion() && !this.isInWater()
                && this.isMoving() && !this.isAggressive());
        sprint.setPlayCondition(a -> this.canPlayLocomotion() && !this.isInWater()
                && this.isMoving() && this.isAggressive());
        swim.setPlayCondition(a -> this.canPlayLocomotion() && this.isInWater());

        // The two transitions are PLAY_ONCE, and MobAnimator's auto-start loop only ever starts
        // REPEATING clips — so these conditions do NOT start them. SleepGoal does, through the
        // onSleepPhaseBegin hook. What the conditions buy is the reverse: BaseAnimation#tick stops a
        // playing clip whose condition has gone false, so a mob shaken awake mid-settle cuts the clip
        // instead of finishing it. The phase lengths are no longer written out a second time as
        // constants — SleepGoal reads them straight off these clips (see
        // SMOPAnimal#sleepPhaseDuration), so a re-export that changes a clip's length can no longer
        // leave the mob holding a last frame for the difference.
        //
        // Three phases here, six on the Krifto, same goal driving both: the cycle is assembled from
        // whichever clips a mob registers, and this one has no sitting clips. See SleepPhase.
        preparingSleep.setPlayCondition(a -> this.isPreparingSleep());
        awakening.setPlayCondition(a -> this.isAwakening());

        // settle → sleep is the one handoff that must NOT fall through to idle.
        //
        // Everywhere else, a one-shot ending and locomotion resuming underneath it is the right
        // answer (that is what removed the freeze on waking). Here it is wrong twice over: idle is
        // the standing pose, so the frame it shows is the mob getting back up, and the loop that
        // follows then has to snap it down again from standing. That is the lie-down / stand-up /
        // snap-into-the-loop sequence.
        //
        // The two clocks that meet here are independent — the animator runs on EntityTickEvent.Pre,
        // the goal's phase timer on the goal selector — so whether the clip stops the tick before,
        // the same tick as, or the tick after the goal raises isSleeping() is not something this
        // code should have to depend on. Two things make it not matter:
        //
        //   1. sleep is allowed to play through the settling phase as well, so the moment
        //      preparing_sleep ends the loop is already eligible — whether it is started by
        //      triggerNextAnimation below or by MobAnimator's auto-start pass in the same tick.
        //   2. preparing_sleep names it explicitly as its successor, so the handoff happens inside
        //      the same tick the clip ends rather than waiting for the next auto-start sweep.
        //
        // Both flags going false (shaken awake mid-settle) still stops the loop, so an interrupted
        // settle cannot leave the mob asleep on its feet.
        sleep.setPlayCondition(a -> this.isSleeping() || this.isPreparingSleep());
        preparingSleep.setNextAnimation(ANIM_SLEEP);

        // The corpse sticks around for the length of the clip; block additives so a dead Tangoftero
        // does not keep tracking the player with its head.
        death.blockAdditive();

        this.animator().register(idle, walk, sprint, swim, preparingSleep, sleep, awakening, attack, bite, roar);
        this.animator().registerDeath(death);
    }

    /**
     * True when the locomotion family is allowed to run at all.
     *
     * <p><b>It deliberately does not exclude sleeping, roaring or attacking</b>, even though those
     * clips must be the ones on screen. {@code BlendLayer#current} renders the playing animation
     * with the <em>lowest priority number</em>, and every one-shot here sits at 0 or 1 against
     * locomotion's 2–3 — so they already win the frame, and {@code MobAnimator#tick}'s auto-start
     * loop refuses to start locomotion underneath a lower number, so nothing new creeps in either.
     *
     * <p>What excluding them cost was the gap at the far end. The animator runs on
     * {@code EntityTickEvent.Pre}, ahead of the goals, so on the tick a one-shot expires the flag
     * that gates locomotion is still set: the one-shot stops, locomotion cannot start, and the
     * layer is left with nothing playing until the goal clears the flag and the <em>next</em> tick
     * starts idle. {@link Rig} calls {@code resetPose()} unconditionally every frame, so an empty
     * layer is not "hold the last frame", it is the bind pose — the model visibly collapses and
     * snaps back. That is the freeze between {@code awakening} and idle, and between the end of a
     * roar and idle. Leaving idle running underneath means the frame the one-shot ends, idle is
     * already there to render.
     */
    private boolean canPlayLocomotion() {
        return !this.isDeadOrDying();
    }

    /**
     * Builds a clip whose definition is chosen by age <b>lazily</b>.
     *
     * <p>The suppliers are not a style choice. {@code AnimationDefinition} is {@code @OnlyIn(Dist.CLIENT)},
     * and {@code registerAnimations()} runs on BOTH sides (MobAnimator hooks EntityJoinLevelEvent).
     * Passing {@code TangoAnimations.idle} directly would read the static field here, load the
     * class, and kill a dedicated server with
     * {@code ClassNotFoundException: net.minecraft.client.animation.AnimationDefinition$Builder}.
     * Note MobAnimator's {@code catch (Exception)} does not save you: a failing static initialiser
     * throws {@code ExceptionInInitializerError}/{@code NoClassDefFoundError}, which are Errors.
     *
     * <p>Inside the lambda the field is only read during client rendering, which is exactly the
     * shape DeluxeLib's own mobs use ({@code new AnimSource(() -> OwlAnimation.FLY_IDLE)}).
     */
    private StandardAnimation clip(String name, Supplier<Object> adult, Supplier<Object> baby,
                                   Loop loop, int priority, float seconds) {
        return new StandardAnimation(name,
                new AnimSource(() -> this.isBaby() ? baby.get() : adult.get()), loop, 0, priority, seconds);
    }

    // ───────────────────────────────────────────────────── TICK ─────

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            this.faceCombatTarget();
            this.tickFeedingAndRoar();
        }
    }

    /**
     * Chicks of this species never fight — not the undead, not whoever hit them, not alongside the
     * flock.
     *
     * <p>Enforced here rather than in each goal because every route to a target funnels through
     * this one call: the two owner-defence goals, {@link AssistFlockGoal}, {@code HurtByTargetGoal}
     * and the undead scan all end in {@code setTarget}. Gating them one by one would leave
     * {@code HurtByTargetGoal} — a vanilla goal with no predicate hook — as a hole, and any goal
     * added later as another one.
     *
     * <p>Refusing the target, rather than only refusing the bite, is what stops the chick from
     * <em>chasing</em>: the melee goal keys off {@code getTarget()}, and so does the {@code sprint}
     * clip through {@code isAggressive()}. Blocking the swing alone (which is all
     * {@code attackCondition} did) left the baby running the zombie down and snapping at nothing.
     *
     * <p>Clearing a target ({@code null}) is always allowed — growing up mid-fight must not strand
     * an adult's target on a mob that can no longer act on it.
     */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && this.isBaby()) {
            return;
        }
        super.setTarget(target);
    }

    /**
     * Keeps the body pointed at the enemy for as long as one is engaged.
     *
     * <p>{@link DirectionalMoveControl} only steers while it has a waypoint, and
     * {@code AnimatableMeleeAttackGoal} stops the navigation as soon as the target is inside reach —
     * exactly the ticks during which the bite fires. This is the same call
     * {@code GuardedMeleeAttackGoal} makes for the Athenian, hoisted onto the entity because
     * {@code AnimatableMeleeAttackGoal} does not make it itself.
     *
     * <p>Skipped while the mob is pinned (asleep, roaring): those states own the pose, and turning
     * under them would swing the whole body while the clip plays.
     */
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

    private void tickFeedingAndRoar() {
        if (this.biteCooldown > 0) {
            this.biteCooldown--;
        }
        // Only the roar's own end is still counted here — starting it and the scare that follows
        // are frame events on the clips themselves (see registerAnimations). BaseAnimation has no
        // "on stop" hook, so the movement lock has to be released by a timer; it is sized from the
        // clip via getRoarDuration(), so the two cannot drift.
        if (this.roarTicksLeft > 0 && --this.roarTicksLeft <= 0) {
            this.setRoaring(false);
        }
    }

    /**
     * Starts the roar if one was armed by the last feeding. Fired from the final frame of the
     * {@code bite} clip, so the rear-up follows the swallow instead of a second timer guessing when
     * the swallow ended.
     *
     * <p>Raises the shared roaring state, not just the clip: that flag is what pins the mob in place
     * ({@link #isMovementLocked()}). Playing the clip alone would leave the Tangoftero strolling
     * through its own roar.
     */
    private void startArmedRoar() {
        if (!this.roarArmed) {
            return;
        }
        this.roarArmed = false;
        this.setRoaring(true);
        this.roarTicksLeft = this.getRoarDuration();
        this.animator().play(this.animator().getByName(ANIM_ROAR));
    }

    /** Sends every nearby undead pathing away from this mob. */
    private void scareUndead() {
        List<Mob> undead = this.level().getEntitiesOfClass(Mob.class,
                this.getBoundingBox().inflate(SCARE_RADIUS),
                mob -> mob.isAlive() && mob.is(EntityTypeTags.UNDEAD));

        for (Mob mob : undead) {
            double dx = mob.getX() - this.getX();
            double dz = mob.getZ() - this.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < 1.0E-4D) {
                continue;
            }
            double scale = SCARE_FLEE_DISTANCE / distance;
            mob.getNavigation().moveTo(mob.getX() + dx * scale, mob.getY(), mob.getZ() + dz * scale, 1.2D);
        }
    }

    // ───────────────────────────────────────────────────── INTERACTION ─────

    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

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
        if (this.isTame() && this.biteCooldown <= 0 && stack.has(net.minecraft.core.component.DataComponents.FOOD)) {
            if (!this.level().isClientSide()) {
                this.handleFeeding(stack, player);
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
                this.level().broadcastEntityEvent(this, EntityEvent.TAMING_SUCCEEDED);
            } else {
                this.level().broadcastEntityEvent(this, EntityEvent.TAMING_FAILED);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Vanilla's taming particle event ids, named so the call site reads. */
    private static final class EntityEvent {
        static final byte TAMING_FAILED = 6;
        static final byte TAMING_SUCCEEDED = 7;
    }

    private void handleFeeding(ItemStack stack, Player player) {
        boolean rottenFlesh = stack.is(Items.ROTTEN_FLESH);

        if (this.getHealth() < this.getMaxHealth()) {
            this.heal(rottenFlesh ? HEAL_ROTTEN_FLESH : HEAL_OTHER_FOOD);
        }
        stack.consume(1, player);
        this.biteCooldown = BITE_COOLDOWN_TICKS;
        this.animator().play(this.animator().getByName("bite"));

        boolean canRoar = rottenFlesh && this.isTame() && !this.isBaby()
                && this.tickCount - this.lastRoarTick >= ROAR_COOLDOWN_TICKS;
        if (canRoar) {
            this.roarArmed = true;
            this.lastRoarTick = this.tickCount;
        }
    }

    @Override
    public boolean isFood(@NotNull ItemStack stack) {
        return stack.is(Items.CHICKEN);
    }

    /**
     * No live offspring: mating flips {@code hasEgg} instead (see {@code GenericBreedGoal}) and the
     * clutch hatches from the egg block.
     */
    @Override
    public @Nullable net.minecraft.world.entity.AgeableMob getBreedOffspring(
            @NotNull ServerLevel level, @NotNull net.minecraft.world.entity.AgeableMob partner) {
        return null;
    }

    // ───────────────────────────────────────────────────── VARIANTS ─────

    @Override
    public void setRandomVariant(@NotNull RandomSource random) {
        this.setVariantId(random.nextInt(TangofteroVariant.count()));
    }

    @Override
    public int getVariantId() {
        return this.entityData.get(VARIANT);
    }

    public void setVariantId(int id) {
        this.entityData.set(VARIANT, id);
    }

    public TangofteroVariant getVariant() {
        return TangofteroVariant.byId(this.getVariantId());
    }

    @Override
    public int getMaxVariants() {
        return TangofteroVariant.count();
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level,
                                                  @NotNull DifficultyInstance difficulty,
                                                  @NotNull EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData spawnData) {
        this.setRandomVariant(this.random);
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    public static boolean checkTangofteroSpawnRules(EntityType<TangofteroEntity> type, ServerLevelAccessor level,
                                                    EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return checkAnimalSpawnRules(type, level, reason, pos, random);
    }

    // ───────────────────────────────────────────────────── SLEEP ─────

    // No sleepPhaseDuration override: SMOPAnimal derives every phase length from the clip registered
    // under that phase's name, and getRoarDuration() from the roar clip. This mob registers no
    // sitting clips, so it simply never has those phases.

    /** Sleeps right through players — only the undead are worth waking up for. */
    @Override
    public boolean shouldWakeOnPlayerProximity() {
        return false;
    }

    @Override
    public boolean shouldInterruptSleepDueTo(@NotNull LivingEntity nearby) {
        return nearby.is(EntityTypeTags.UNDEAD);
    }

    /** Empty: the undead check above covers everything this mob cares about. */
    @Override
    public @NotNull Set<EntityType<?>> getInterruptingEntityTypes() {
        return Set.of();
    }

    // ───────────────────────────────────────────────────── SOUNDS ─────

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return SoundEvents.FOX_AMBIENT;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(@NotNull DamageSource source) {
        return SoundEvents.FOX_HURT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return SoundEvents.FOX_DEATH;
    }

    // ───────────────────────────────────────────────────── NBT ─────

    @Override
    protected void addAdditionalSaveData(@NotNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Variant", this.getVariantId());
    }

    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setVariantId(input.getIntOr("Variant", 0));
    }
}

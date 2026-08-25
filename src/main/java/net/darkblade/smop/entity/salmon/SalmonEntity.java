package net.darkblade.smop.entity.salmon;

import net.darkblade.deluxelib.anim.AnimSource;
import net.darkblade.deluxelib.anim.Loop;
import net.darkblade.deluxelib.anim.StandardAnimation;
import net.darkblade.deluxelib.combat.AttackShape;
import net.darkblade.deluxelib.combat.HitWindow;
import net.darkblade.deluxelib.entity.ai.goal.AnimatableMeleeAttackGoal;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.block.SMOPBlocks;
import net.darkblade.smop.client.salmon.SalmonAnimations;
import net.darkblade.smop.entity.SMOPWaterAnimal;
import net.darkblade.smop.entity.SwimTilt;
import net.darkblade.smop.entity.ai.control.SwimSteerControl;
import net.darkblade.smop.entity.ai.goal.SwimWanderGoal;
import net.darkblade.smop.entity.ai.navigation.SmartSwimmingNavigation;
import net.darkblade.smop.entity.ai.goal.GenericBreedGoal;
import net.darkblade.smop.entity.ai.goal.egg.EggGoalRegistry;
import net.darkblade.smop.entity.ai.goal.egg.ProtectEggBaseGoal;
import net.darkblade.smop.entity.ai.goal.salmon.SalmonDigGoal;
import net.darkblade.smop.entity.sleep.ISleepThreatEvaluator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Predicate;

/**
 * The salmon: a river-bed forager that digs up whatever the substrate is hiding.
 *
 * <p>Hand it a pufferfish and it goes looking for sand, gravel, mud or dirt to root through; breed
 * it with cod and it spawns roe on the river floor.
 */
public class SalmonEntity extends SMOPWaterAnimal implements ISleepThreatEvaluator, SwimTilt {

    // ───────────────────────────────────────────────────── TUNING ─────

    /** A pufferfish only buys one dig per minute, so a stack is not an excavation machine. */
    private static final int DIG_COMMAND_COOLDOWN_TICKS = 1200;

    /**
     * Swim steer, scaled for a fish rather than for the three-block reptile it was written against.
     *
     * <p>Six degrees a tick against vanilla's ten and the Nirasmosaurus's 2.2, and a five-tick ramp
     * against its fifteen. The point of the shared control is the <em>shape</em> of a turn — wind up,
     * hold, ease out, never a step — not the mass of any particular animal; a salmon that took three
     * quarters of a second to commit to a turn would read as a log. The in-water speed multiplier is
     * 0.02, exactly what {@code SmoothSwimmingMoveControl} was using here, so the cruise speed is
     * unchanged and only the steering is different.
     */
    private static final float SWIM_TURN_SPEED = 6.0F;
    private static final float SWIM_MAX_PITCH = 45.0F;
    private static final float SWIM_PITCH_SPEED = 6.0F;
    private static final float SWIM_SPEED_SCALE = 0.02F;
    private static final float SWIM_RAMP_TICKS = 5.0F;

    /**
     * Vertical-to-forward drive balance, down from the control's default of 6.
     *
     * <p>Measured, not guessed. {@code moveRelative} normalises the drive vector, so the gain decides
     * what FRACTION of the motion is vertical rather than adding to it. At 6 the tick samples show this
     * animal reaching {@code dY} of −0.098 against a horizontal speed near 0.065 — it was descending
     * faster than it swam forward, a dive past 55 degrees. Invisible while the model rendered level;
     * absurd the moment the body followed the trajectory.
     *
     * <p>Two keeps depth changes decisive while leaving the steepest of them inside the tilt clamp, so
     * the body can express the whole angle instead of parking against the stop.
     */
    private static final float SWIM_VERTICAL_GAIN = 2.0F;

    /**
     * Pure-pursuit lookahead, in blocks. Three, against the Nirasmosaurus's seven: the aim point has to
     * sit beyond the turn radius to smooth anything, and this animal's is a fraction of that one's.
     * Longer would just cut corners off its own route.
     */
    private static final double SWIM_LOOKAHEAD = 3.0D;

    /**
     * Wander legs, in blocks, and the heading cone in degrees.
     *
     * <p>12–22 and 70, worked from the rule rather than copied: a leg has to outlast the turn that
     * opens it. Seventy degrees at six a tick plus the ramp is about twenty-two ticks; a leg with the
     * five-block hand-over taken off is seven to seventeen blocks, which this animal crosses in sixty
     * to a hundred and forty. Comfortable margin, where the Nirasmosaurus's first attempt had none.
     *
     * <p>The cone is wider than that animal's 55 on purpose — a fish is allowed to be capricious in a
     * way a marine reptile is not. And 22 stays under {@code FOLLOW_RANGE}, so unlike the
     * Nirasmosaurus this one needs no {@code setRequiredPathLength}: every leg it draws is pathable.
     */
    private static final double WANDER_LEG_MIN = 12.0D;
    private static final double WANDER_LEG_SPREAD = 10.0D;
    private static final float WANDER_CONE_DEGREES = 70.0F;

    /** Frame of the {@code dig} clip on which the snout actually turns the block over. */
    private static final int DIG_BREAK_FRAME = 35;

    /**
     * Nothing is prey. The salmon has no hunting goal at all — its only target route is
     * {@code HurtByTargetGoal}, so it bites back and nothing else. Kept as an explicit constant
     * because {@link EggGoalRegistry} wants a selector and "nobody" is the honest answer.
     */
    private static final Predicate<LivingEntity> NO_PREY = entity -> false;

    // ───────────────────────────────────────────────────── STATE ─────

    private int digCommandCooldown;
    private boolean digCommand;
    /** Where the dig goal sent it; the {@code dig} clip's frame event turns this block over. */
    @Nullable
    private BlockPos digTarget;

    public SalmonEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        this.setOutOfWaterMaxTicks(80);
        this.setOutOfWaterDamage(2.0F);
        this.moveControl = new SwimSteerControl(
                this, SWIM_TURN_SPEED, SWIM_MAX_PITCH, SWIM_PITCH_SPEED, SWIM_SPEED_SCALE)
                .rampTicks(SWIM_RAMP_TICKS)
                .verticalGain(SWIM_VERTICAL_GAIN);
    }

    /**
     * The look control is deliberately left alone — the base's {@code SmoothSwimmingLookControl} stays.
     *
     * <p>The Nirasmosaurus replaced it because its three fish quirks all read wrong on a long skull
     * with forward eyes, and the loudest of them is that it aims the head twenty degrees off whatever
     * it is watching. That one is not a bug here: Mojang wrote it for fish, and a fish regarding
     * something side-on is what it is meant to look like. Sharing the navigation and the steering does
     * not mean sharing everything.
     */
    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull Level level) {
        return new SmartSwimmingNavigation(this, level).setLookahead(SWIM_LOOKAHEAD);
    }

    /**
     * Built from {@link Animal#createAnimalAttributes()} for the same reason the Tangoftero is: in
     * 26.1 vanilla reads {@code Attributes.TEMPT_RANGE} off anything extending {@code Animal}, and
     * an attribute the supplier never declared throws on the first tick.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.FOLLOW_RANGE, 28.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.6D)
                .add(Attributes.ATTACK_SPEED, 0.4D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.1D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D);
    }

    /**
     * The sprint clip runs while it has something to deal with — by goal, not by speed.
     *
     * <p>The base's default is a speed threshold, and the salmon's was 0.105 blocks a tick, which sits
     * right on top of its own cruise: the flag chattered across the line and the two locomotion clips
     * swapped with it. No threshold fixes that, because speed is not what the clip is about. Its only
     * target route is {@code HurtByTargetGoal}, so a target means it is fighting back or running from
     * whatever just bit it — which is exactly when a fish should be flat out.
     *
     * <p>{@code getTarget()} is not synced, and that is fine here: this runs server-side and the base
     * syncs the result. @see SMOPWaterAnimal#shouldSwimFast
     */
    @Override
    protected boolean shouldSwimFast() {
        return this.getTarget() != null;
    }

    // ───────────────────────────────────────────────────── GOALS ─────

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, this.createSleepGoal());
        // The goal decides WHEN to bite; the damage is in the clip's HitWindow (see
        // registerAnimations) so it lands on the frame the jaws close.
        this.goalSelector.addGoal(2, new AnimatableMeleeAttackGoal(this, 1.8D, true)
                .reach(2.0F)
                .cooldown(13)
                .attackCondition(target -> !this.isBaby())
                .onAttack((target, animator) -> animator.play(animator.getByName("bite"))));
        this.goalSelector.addGoal(3, new SalmonDigGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new GenericBreedGoal<>(this, 1.1D));

        // Solitary nester: it minds the roe it laid itself and does nothing about intruders —
        // IGNORE plus a selector that matches nobody.
        EggGoalRegistry.registerWithOwnGoal(this, SMOPBlocks.SALMON_ROE_EGGS,
                4, 6, false, false,
                ProtectEggBaseGoal.EggBreakReaction.IGNORE, NO_PREY, 5);

        // SwimWanderGoal, not RandomSwimmingGoal. That one draws a point ten blocks away in any
        // direction — half of every draw lands behind the fish, so each leg opens with a U-turn — and
        // then ends the moment it arrives, leaving the animal parked until the next interval roll.
        // Swim, stop, spin, wait. Staying at priority 8 keeps digging, breeding and the nest ahead of
        // it, exactly as before.
        this.goalSelector.addGoal(8, new SwimWanderGoal(this, 1.0D, () -> !this.isMovementLocked())
                .legLength(WANDER_LEG_MIN, WANDER_LEG_SPREAD)
                .cone(WANDER_CONE_DEGREES));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    // ───────────────────────────────────────────────────── ANIMATIONS ─────

    /**
     * Exclusion between clips is by <b>priority</b>, not by play condition. Locomotion sits at 2–3
     * and every one-shot at 0–1, so {@code BlendLayer#current} renders the one-shot while the swim
     * cycle keeps running underneath — which is what makes the frame a dig or a bite ends have
     * something to fall back to instead of collapsing to the bind pose. Same arrangement as the
     * Tangoftero; see its {@code canPlayLocomotion} for the full reasoning.
     */
    @Override
    public void registerAnimations() {
        StandardAnimation idle = clip("idle", () -> SalmonAnimations.idle, Loop.REPEATING, 3, 2.0F);
        StandardAnimation swim = clip("swim", () -> SalmonAnimations.swim, Loop.REPEATING, 2, 2.0F);
        StandardAnimation fastSwim = clip("fast_swim", () -> SalmonAnimations.fast_swim, Loop.REPEATING, 2, 0.8F);
        StandardAnimation flopping = clip("flopping", () -> SalmonAnimations.flopping, Loop.REPEATING, 1, 1.2F);

        StandardAnimation bite = clip("bite", () -> SalmonAnimations.bite, Loop.PLAY_ONCE, 0, 0.65F);
        StandardAnimation sniff = clip("sniff", () -> SalmonAnimations.sniff, Loop.PLAY_ONCE, 1, 2.25F);
        StandardAnimation dig = clip("dig", () -> SalmonAnimations.dig, Loop.PLAY_ONCE, 0, 2.7F);

        StandardAnimation waterDeath = clip("water_death", () -> SalmonAnimations.water_death, Loop.PLAY_ONCE, 0, 0.5F);
        StandardAnimation landDeath = clip("land_death", () -> SalmonAnimations.land_death, Loop.PLAY_ONCE, 0, 0.95F);

        idle.blendInMs(300).blendOutMs(250);
        swim.blendInMs(250).blendOutMs(250);
        fastSwim.blendInMs(200).blendOutMs(250);
        flopping.blendInMs(120).blendOutMs(150);

        // The bite: a strictly frontal box across the frames the jaws close. Damage matches the
        // ATTACK_DAMAGE attribute, a literal — same convention as Athenian and Arpy.
        HitWindow.of(5, 7)
                .shape(AttackShape.box(1.4F, 0.5F))
                .anchor(0.5F, 0.0F, 0.3F)
                .damage(1.0F)
                .knockback(0.1F)
                .filter(target -> !(target instanceof SalmonEntity))
                .applyTo(bite);

        // The dig turns the block over on the frame the snout is buried in it, rather than on a
        // countdown running beside the clip — same mechanism as the Tangoftero's roar chain.
        dig.onFrame(DIG_BREAK_FRAME, e -> ((SalmonEntity) e).completeDig());

        // Mutually exclusive by construction: exactly one of these four holds at any moment.
        idle.setPlayCondition(a -> this.isInWater() && !this.isMoving());
        swim.setPlayCondition(a -> this.isInWater() && this.isSwimmingCruise());
        fastSwim.setPlayCondition(a -> this.isInWater() && this.isMoving() && this.isSwimmingFast());
        flopping.setPlayCondition(a -> !this.isInWater() && !this.isDeadOrDying());

        // Death variant chosen by where it died — the conditions are only consulted at the moment
        // of death, so a corpse washing ashore keeps the clip it started.
        waterDeath.setPlayCondition(a -> this.isInWater());
        landDeath.setPlayCondition(a -> !this.isInWater());
        waterDeath.blockAdditive();
        landDeath.blockAdditive();

        this.animator().register(idle, swim, fastSwim, flopping, bite, sniff, dig);
        this.animator().registerDeath(waterDeath, landDeath);
    }

    /**
     * {@code AnimationDefinition} is {@code @OnlyIn(Dist.CLIENT)} and {@code registerAnimations()}
     * runs on both sides, so the definition is read through a supplier that only ever resolves
     * during client rendering. Touching the field directly here would load the class and kill a
     * dedicated server — and {@code MobAnimator}'s {@code catch (Exception)} would not save it,
     * because a failing static initialiser throws an {@code Error}.
     */
    private StandardAnimation clip(String name, java.util.function.Supplier<Object> definition,
                                   Loop loop, int priority, float seconds) {
        return new StandardAnimation(name, new AnimSource(definition), loop, 0, priority, seconds);
    }

    // ───────────────────────────────────────────────────── TICK ─────

    @Override
    public void tick() {
        super.tick();
        // Both sides: the tilt is drawn, and the client cannot recompute it from synced velocity.
        this.tickSwimTilt();
        if (!this.level().isClientSide() && this.digCommandCooldown > 0) {
            this.digCommandCooldown--;
        }
    }


    // ───────────────────────────────────────────────────── VISUAL TILT ─────

    /** Nose up/down and bank, for the renderer. Interpolated against the prev pair. */
    public float swimPitch;
    public float prevSwimPitch;
    public float swimRoll;
    public float prevSwimRoll;

    @Override
    public float swimPitch() {
        return this.swimPitch;
    }

    @Override
    public float swimRoll() {
        return this.swimRoll;
    }

    /**
     * How far the body may nose up or down, and how far it may bank.
     *
     * <p>Both above the Nirasmosaurus's 30 and 35. That animal is three blocks long, so the same angle
     * sweeps far more silhouette and it has to be reined in; a salmon is a hand-span of fish and can
     * throw itself around. 45 is roughly the steepest climb the tick samples show it actually making.
     */
    private static final float MAX_TILT_PITCH = 45.0F;
    private static final float MAX_TILT_ROLL = 25.0F;

    /**
     * Below this vertical speed the body stays level.
     *
     * <p>Six thousandths, and the number is not arbitrary: {@code SMOPWaterAnimal#travel} adds exactly
     * {@code -0.005} per tick whenever the mob has no target, so an idling swimmer is always sinking a
     * little. Without a threshold above that, a fish hanging in the water would hold a permanent
     * four-degree droop — the tick samples show {@code dY} parked at exactly {@code -0.0050} through
     * whole idle stretches, which is that trim and nothing else.
     */
    private static final float TILT_DEAD_ZONE = 0.006F;

    /** Bank per degree of yaw per tick. @see #tickSwimTilt */
    private static final float ROLL_GAIN = 4.5F;

    /** Horizontal speed at which the bank reaches full strength. */
    private static final double ROLL_FULL_SPEED = 15.0D;

    /**
     * How fast the drawn tilt chases its target. Quicker than the Nirasmosaurus's 0.06 — a fish.
     *
     * <p><b>One smoothing stage, not two.</b> The first version smoothed the vertical speed AND then
     * smoothed the resulting angle, and two lerps in series add their lags: a tick sample caught the
     * body pitched {@code +41.6} nose-up while {@code dY} had already gone to {@code −0.0036}, i.e.
     * fully committed to a climb it had stopped making about a second earlier. The angle is what gets
     * drawn, so the angle is the only thing worth filtering.
     *
     * <p><b>But dropping the input stage is what made it read as jerky</b>, and the input stage was not
     * the reason. The lerp on the vertical speed had been hiding the hard edge of
     * {@link #TILT_DEAD_ZONE}: with it gone, a sample shows {@code swimPitch} sitting at {@code -0.000}
     * for entire stretches and then jumping to {@code 1.503} on the single tick {@code dY} crossed from
     * {@code 0.0058} to {@code 0.0083}. Softening the knee fixes the step at its source, so this can
     * stay a single stage — and 0.15 rather than 0.2, since there is no longer a second filter's worth
     * of smoothing to make up for.
     */
    private static final float TILT_SMOOTHING = 0.15F;

    /**
     * Pitch and roll, recomputed on <b>both</b> sides from different inputs.
     *
     * <p>{@code deltaMovement} is not synced for mobs, so a client running the server's formula would
     * read roughly zero and render the fish rigidly level. The server reads its own velocity; the
     * client reads the per-tick position delta it interpolates anyway.
     *
     * <p><b>The pitch is the trajectory angle, not a gain on the vertical speed.</b> The Nirasmosaurus
     * multiplies by 300 and clamps, a constant found by trial; that number is only right for one
     * animal's range of speeds, and this one is four times quicker vertically — the samples reach
     * {@code dY} of −0.093 against that animal's 0.024, so the same gain would peg the clamp and hold
     * it there through every descent. {@code atan2(vertical, horizontal)} is the angle the fish is
     * genuinely travelling along, so it needs no constant: a level cruise gives a couple of degrees and
     * a real dive gives a real angle.
     *
     * <p><b>It can still saturate, and it did.</b> "Cannot saturate wrongly" was too strong — the
     * formula was right and the trajectory was the problem: samples had it pinned within a degree of
     * the 45 clamp for twenty-five ticks at a stretch, because the animal really was diving at 57. That
     * is fixed at the source, in {@link #SWIM_VERTICAL_GAIN}, not by widening the clamp.
     */
    private void tickSwimTilt() {
        this.prevSwimPitch = this.swimPitch;
        this.prevSwimRoll = this.swimRoll;

        // The authored death clips own the corpse pose, and a beached fish has the flop; levelling out
        // keeps this from fighting either.
        if (!this.isInWater() || this.isDeadOrDying()) {
            this.swimPitch = Mth.lerp(0.1F, this.swimPitch, 0.0F);
            this.swimRoll = Mth.lerp(0.1F, this.swimRoll, 0.0F);
            return;
        }

        boolean client = this.level().isClientSide();
        float vertical = client ? (float) (this.getY() - this.yo) : (float) this.getDeltaMovement().y;
        double horizontal = client
                ? Math.hypot(this.getX() - this.xo, this.getZ() - this.zo)
                : this.getDeltaMovement().horizontalDistance();

        // SOFT knee, not a hard cut. Zeroing anything under the threshold makes the angle jump the
        // moment the threshold is crossed: below it the target is 0, above it it is already
        // atan(0.006 / 0.065) — five degrees appearing from nothing. Subtracting the threshold instead
        // passes through zero continuously, which kills the buoyancy trim just as dead without the step.
        vertical = Math.signum(vertical) * Math.max(0.0F, Math.abs(vertical) - TILT_DEAD_ZONE);

        // Sign convention copied from the Nirasmosaurus, NOT re-derived: there, a POSITIVE vertical
        // speed produces a positive swimPitch, and the renderer feeds that straight into
        // Axis.XP.rotationDegrees. That animal's tilt has been watched in-game across many samples and
        // reads correctly, which makes it the ground truth; inverting it here would have nosed the fish
        // down as it climbed.
        float trajectory = (float) Math.toDegrees(
                Mth.atan2(vertical, Math.max(horizontal, 1.0E-4D)));
        float targetPitch = Mth.clamp(trajectory, -MAX_TILT_PITCH, MAX_TILT_PITCH);
        this.swimPitch = Mth.lerp(TILT_SMOOTHING, this.swimPitch, targetPitch);

        // Bank out of the yaw RATE, not the yaw: a steady heading must not hold the fish leaning.
        float yawDelta = Mth.wrapDegrees(this.getYRot() - this.yRotO);
        if (Math.abs(yawDelta) < 0.3F) {
            yawDelta = 0.0F;
        }
        // No speed, no bank — something turning on the spot is not carving a curve. The multiplier is
        // 15 against the Nirasmosaurus's 6 because this animal cruises at about 0.065 blocks a tick,
        // and at 6 that would scale every bank down to a third before it was ever drawn.
        float speedFactor = (float) Math.min(1.0D, horizontal * ROLL_FULL_SPEED);
        // 4.5, scaled from that animal's 12 by the ratio of the turn caps (2.2 against 6): the gain
        // converts degrees of yaw per tick into degrees of lean, so a mob that turns three times
        // faster needs a third of the gain to lean by the same amount.
        float targetRoll = Mth.clamp(-yawDelta * ROLL_GAIN * speedFactor, -MAX_TILT_ROLL, MAX_TILT_ROLL);
        this.swimRoll = Mth.lerp(TILT_SMOOTHING, this.swimRoll, targetRoll);
    }

    // ───────────────────────────────────────────────────── DIGGING ─────

    /** True while a pufferfish is still bought and paid for. Read by {@link SalmonDigGoal}. */
    public boolean wantsToDig() {
        return this.digCommand;
    }

    public void setDigCommand(boolean value) {
        this.digCommand = value;
        if (!value) {
            this.digTarget = null;
        }
    }

    /** The dig goal parks its target here so the clip's frame event knows what to turn over. */
    public void setDigTarget(@Nullable BlockPos pos) {
        this.digTarget = pos;
    }

    @Nullable
    public BlockPos getDigTarget() {
        return this.digTarget;
    }

    /**
     * Fired from the {@code dig} clip's own frame event: drops whatever the substrate was hiding and
     * removes the block. No-op if the target went away mid-clip (another mob mined it, the chunk
     * unloaded), so a stale frame event cannot destroy the wrong block.
     */
    private void completeDig() {
        BlockPos target = this.digTarget;
        if (target == null || !(this.level() instanceof ServerLevel level)) {
            if (SalmonDigGoal.DEBUG) {
                SMOP.LOGGER.info("[SALMON DIG] e{} frame event fired with no target — dig lost", this.getId());
            }
            return;
        }
        if (SalmonDigGoal.DEBUG) {
            SMOP.LOGGER.info("[SALMON DIG] e{} BREAKING {} at {}", this.getId(),
                    level.getBlockState(target).getBlock().getName().getString(), target);
        }
        SalmonDigGoal.dropFor(level, target);
        level.destroyBlock(target, false);
        this.setDigCommand(false);
    }

    // ───────────────────────────────────────────────────── INTERACTION ─────

    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (stack.is(Items.PUFFERFISH)) {
            if (this.digCommandCooldown > 0) {
                if (SalmonDigGoal.DEBUG) {
                    SMOP.LOGGER.info("[SALMON DIG] e{} pufferfish REFUSED — {} ticks of cooldown left",
                            this.getId(), this.digCommandCooldown);
                }
                return InteractionResult.FAIL;
            }
            if (!this.level().isClientSide()) {
                this.setDigCommand(true);
                this.digCommandCooldown = DIG_COMMAND_COOLDOWN_TICKS;
                stack.consume(1, player);
                if (SalmonDigGoal.DEBUG) {
                    SMOP.LOGGER.info("[SALMON DIG] e{} pufferfish accepted at {} — searching",
                            this.getId(), this.blockPosition());
                }
            }
            return InteractionResult.SUCCESS;
        }

        if (stack.is(Items.COD) && !this.isBaby() && !this.isInLove()) {
            if (!this.level().isClientSide()) {
                this.setInLove(player);
                stack.consume(1, player);
            }
            return InteractionResult.SUCCESS;
        }

        return super.mobInteract(player, hand);
    }

    @Override
    public boolean isFood(@NotNull ItemStack stack) {
        return stack.is(Items.COD);
    }

    // ───────────────────────────────────────────────────── SPAWNING ─────

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level,
                                                  @NotNull DifficultyInstance difficulty,
                                                  @NotNull EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData spawnData) {
        this.setMale(this.getRandom().nextBoolean());
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    /** Needs water at its feet and water above, so it spawns in a body of water rather than a film. */
    public static boolean checkSalmonSpawnRules(EntityType<SalmonEntity> type, ServerLevelAccessor level,
                                                EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return level.getFluidState(pos).is(FluidTags.WATER)
                && level.getBlockState(pos.above()).is(Blocks.WATER);
    }

    // ───────────────────────────────────────────────────── SLEEP ─────

    // No sleep clips are authored for this mob, so both phases resolve to 0 through SMOPAnimal's
    // clip lookup and the cycle goes straight from awake to asleep. It still settles at night — it
    // just does it without a transition.

    @Override
    public boolean shouldInterruptSleepDueTo(@NotNull LivingEntity nearby) {
        return false;
    }

    @Override
    public @NotNull Set<EntityType<?>> getInterruptingEntityTypes() {
        return Set.of();
    }

    // ───────────────────────────────────────────────────── SOUNDS ─────

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(@NotNull DamageSource source) {
        return SoundEvents.SALMON_HURT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return SoundEvents.SALMON_DEATH;
    }

    // ───────────────────────────────────────────────────── NBT ─────

    @Override
    protected void addAdditionalSaveData(@NotNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("DigCooldown", this.digCommandCooldown);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.digCommandCooldown = input.getIntOr("DigCooldown", 0);
    }
}

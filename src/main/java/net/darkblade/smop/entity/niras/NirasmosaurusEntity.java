package net.darkblade.smop.entity.niras;

import net.darkblade.deluxelib.anim.AnimSource;
import net.darkblade.deluxelib.anim.Loop;
import net.darkblade.deluxelib.anim.StandardAnimation;
import net.darkblade.smop.client.niras.NirasBabyLandAnimations;
import net.darkblade.smop.client.niras.NirasBabyWaterAnimations;
import net.darkblade.smop.client.niras.NirasLandAnimations;
import net.darkblade.smop.client.niras.NirasWaterAnimations;
import net.darkblade.smop.entity.SMOPWaterAnimal;
import net.darkblade.smop.entity.SwimTilt;
import net.darkblade.smop.entity.SMOPEntities;
import net.darkblade.smop.entity.ai.goal.SMOPRandomStrollGoal;
import net.darkblade.smop.entity.ai.control.SwimSteerControl;
import net.darkblade.smop.entity.ai.goal.SwimWanderGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.darkblade.smop.entity.ai.navigation.SmartSwimmingNavigation;
import net.darkblade.deluxelib.entity.ai.pathing.DirectionalMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.darkblade.smop.entity.ai.control.SmoothSwimLookControl;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Supplier;

/**
 * The Nirasmosaurus: a marine reptile that hunts in the water and hauls out onto the shore.
 *
 * <p><b>Port status — phase 1a of the port spec.</b> Geometry, both animation sets and amphibious
 * locomotion. Sleep, breeding and the idle gestures are 1b; the bite is 1c; the grab, the shake and
 * the death roll — the moves that justify the animal — are 1d; taming and riding are phase 2.
 *
 * <p><b>It extends {@code SMOPWaterAnimal}, the salmon's base, and only overrides what a reptile
 * does differently from a fish.</b> The port spec originally sent it down the Hell Hippo's route —
 * {@code GenderedSMOPAnimal} plus a hand-built water kit — on the grounds that this base "carries the
 * beached-fish flop". It does not: the flop is an opt-out hook whose own javadoc says "anything that
 * can walk turns this off", and it sits next to another that says "amphibians turn this off". The
 * base was written with a case like this in mind.
 *
 * <p>Rebuilding it by hand cost more than the two overrides it saves. It also silently dropped three
 * things this class had no business omitting: the water pathfinding malus (without which the
 * navigator treats water as a hazard and the animal swims nowhere), {@code isPushedByFluid}, and the
 * SYNCED fast-swim flag — play conditions run on both sides and {@code getDeltaMovement()} is not
 * synced for mobs, so reading speed directly desynchronises the clip.
 *
 * <p><b>The one genuine difference is the navigation, and it needs TWO.</b> Swimming the water column
 * requires a {@code WaterBoundPathNavigation}; walking requires a ground one; there is no single
 * navigator that does both. {@code AmphibiousPathNavigation} looks like the answer and is not — it is
 * built for turtles and axolotls, which travel along surfaces rather than through open water, and
 * with it this animal simply sat where it was spawned. The fix is vanilla's own, taken from the
 * Drowned: hold one of each and swap between them as the animal enters and leaves the water.
 */
public class NirasmosaurusEntity extends SMOPWaterAnimal implements SwimTilt {

    /** Bait and breeding food. Cooked fish, as in 1.20.1's lure config. */
    private static final Ingredient FOOD_ITEMS = Ingredient.of(Items.COOKED_COD, Items.COOKED_SALMON);

    /** Above this speed (blocks/tick) the sprint clip takes over from the cruise clip. */
    private static final double SWIM_SPRINT_THRESHOLD = 0.105D;

    /**
     * Both navigators, built once. @see #updateSwimming()
     */
    private final SmartSwimmingNavigation waterNavigation;
    private final GroundPathNavigation groundNavigation;
    /**
     * Water steer. Replaces the {@code SmoothSwimmingMoveControl} the aquatic base installs, which is
     * tuned for fish — see {@link SwimSteerControl} for the three things it does that are wrong on a
     * body this long. 2.2 degrees a tick against vanilla's 10 is the headline, and the rate ramps in
     * and out rather than switching on: a reversal now costs two ramps plus the arc between them,
     * where vanilla could pivot the whole way round inside a second.
     */
    private final MoveControl swimControl;
    private final MoveControl walkControl;

    public NirasmosaurusEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        // Lookahead 7, node slack 3. Both opt-in, so the salmon keeps the tighter defaults it was
        // tuned against: a three-block animal needs more room than a fish, and needs to be steering at
        // something further away than the block in front of its nose.
        this.waterNavigation = new SmartSwimmingNavigation(this, level)
                .setLookahead(7.0D)
                .setNodeAcceptRadius(3.0D);
        // 40, because a leg longer than this simply cannot be pathed. PathNavigation caps a search at
        // max(FOLLOW_RANGE, requiredPathLength) — 28 here — and PathFinder drops every node whose
        // walked distance reaches that cap. Worse, it does not fail loudly: with no target reached it
        // reconstructs a closest-approach path instead, so SwimWanderGoal's 22-36 block legs were
        // silently becoming short stubs, which is the hop-and-stop the goal exists to prevent. This
        // covers the longest leg with margin, and the setter also grows the visited-node budget.
        this.waterNavigation.setRequiredPathLength(40.0F);
        this.groundNavigation = new GroundPathNavigation(this, level);
        this.swimControl = new SwimSteerControl(this, 2.2F, 45.0F, 4.0F, 0.01F);
        this.moveControl = this.swimControl;
        // DirectionalMoveControl on land only, and deliberately NOT in water. It caps the turn rate
        // so a three-block-long body has to commit to a turn instead of snapping to face each
        // waypoint, which is the whole reason the Hell Hippo uses it. In water it would be the wrong
        // tool and was tried there first: it computes dy purely for its "arrived" test and the only
        // thing it ever writes is setSpeed, never yya, so nothing would drive the animal up or down —
        // which is exactly why it sank to the bottom and walked.
        // Not the aquatic base's SmoothSwimmingLookControl — see SmoothSwimLookControl for the three
        // fish behaviours it inherits, all three of which a tick sample caught in the act: the head
        // aimed twenty degrees wide of its target, recentred in exact ~10.000 steps regardless of how
        // slowly it had been asked to look, and left xRot frozen at 20.981 for entire samples because
        // vanilla only levels the pitch out while the navigator is idle.
        this.lookControl = new SmoothSwimLookControl(this);
        this.walkControl = new DirectionalMoveControl<>(this).setTurnSpeed(5.0F).setCombatTurnSpeed(30.0F);
    }

    /**
     * {@code createAnimalAttributes}, not {@code createLivingAttributes} as 1.20.1 had it: 26.1's
     * {@code TemptGoal} reads {@code Attributes.TEMPT_RANGE}, which only the animal supplier defines.
     * Getting this wrong crashes on spawn rather than at load, and it already cost us an evening on
     * the Hell Hippo.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.FOLLOW_RANGE, 28.0D)
                // 1.0, not the 0.20 carried over from 1.20.1. That number belonged to an entity with
                // its own travel and its own controls; here SmoothSwimmingMoveControl multiplies the
                // attribute by its in-water modifier of 0.02, so 0.20 came out as an effective 0.004 —
                // measured, not guessed: the animal was moving at 0.005 blocks/tick, which is a tenth
                // of a block per second and reads as floating in place. The salmon swims on 0.6; this
                // is a larger predator, so it gets more.
                .add(Attributes.MOVEMENT_SPEED, 1.0D)
                .add(Attributes.ATTACK_SPEED, 0.4D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0D);
    }

    @Override
    protected void registerGoals() {
        // No FloatGoal: that goal paddles a mob to the surface and bobs it there, which is the
        // opposite of an animal that swims through the whole column and rests on the bottom. It
        // breathes underwater, so there is nothing to protect it from. Same call as the Hell Hippo.
        // SwimWanderGoal, not the salmon's SMOPRandomSwimmingGoal. That one wraps vanilla's
        // RandomSwimmingGoal, which draws a point ten blocks away in any direction and ends the
        // moment it arrives — fine for a fish, but on a body this long it produced short hops, a
        // full stop, and a U-turn. See that class for what replaces each of those.
        this.goalSelector.addGoal(1, new SwimWanderGoal(this, 1.0D,
                () -> !this.isMovementLocked()));
        // 0.25, measured rather than guessed. The land control is a plain MoveControl, which passes
        // attribute x modifier straight through with no scaling of its own — the debug dump read
        // speed=0.600 at a modifier of 0.6, against roughly 0.25 for a cow. The water-scaled attribute
        // of 1.0 has to be divided back down here, because on land nothing else divides it.
        this.goalSelector.addGoal(2, new SMOPRandomStrollGoal(this, 0.25D, 160,
                () -> !this.isInWater() && !this.isMovementLocked()));
        // Probability 0.10: five times vanilla's 0.02, a third of the 0.35 tried before it.

        //
        // 0.02 left the head pinned dead-centre for 49 consecutive ticks in a tick sample — neither
        // look goal running, so the control simply held it aligned with the body. 0.35 overcorrected
        // into the opposite failure: the head was demanded so constantly that it sat welded against
        // getMaxHeadYRot for stretches of twenty ticks and more. Both read as wrong; a head that
        // rests between glances is the point.
                // Range 8, not 12. Beyond that the player is usually off to one side of a moving animal, the
        // demanded angle exceeds getMaxHeadYRot, and the head simply parks against its stop — a tick
        // sample caught it welded at exactly 40.000 for thirty-seven consecutive ticks. A shorter
        // leash means it only tracks what it can plausibly face.
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F, 0.10F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    /**
     * How far the head may turn off the body, down from vanilla's 75.
     *
     * <p>Chosen against the rig, not by feel: {@code NirasmosaurusModel}'s {@code lookAt} clamps the
     * neck to 35 degrees of yaw, so every degree of demand past that produces no visual change
     * whatsoever — it only holds the head against its stop. Allowing 75 meant the entity happily
     * asked for angles the model could never show, and a tick sample caught the result sitting at
     * exactly 75.000 for twenty ticks at a time. Forty leaves a little headroom over what the neck
     * can actually express and nothing beyond it.
     */
    @Override
    public int getMaxHeadYRot() {
        return 40;
    }

    /**
     * Degrees of head yaw per tick, down from vanilla's ten.
     *
     * <p><b>This is the head snap.</b> Ten degrees a tick is two hundred a second, and the body beside
     * it turns at 2.2 — so the neck angle the rig actually draws, {@code yHeadRot - yBodyRot}, could
     * move twelve degrees in a single tick while the animal itself was gliding. A tick sample has it
     * going from 0.000 to 7.800 between two consecutive ticks and back again a second later: not a
     * glance, a flinch.
     *
     * <p>Three keeps the head marginally faster than the body, which is what makes a look read as the
     * animal choosing to turn its head rather than the head being dragged along. Both edges of a
     * glance honour it, which is the reason {@link net.darkblade.smop.entity.ai.control.SmoothSwimLookControl}
     * exists — vanilla only honours the first.
     */
    @Override
    public int getHeadRotSpeed() {
        return 3;
    }

    // ───────────────────────────────────────────────────── VISUAL TILT ─────

    /** Nose up/down and bank, for the renderer. Interpolated against the prev pair. */
    public float swimPitch;
    public float prevSwimPitch;
    public float swimRoll;
    public float prevSwimRoll;

    // The renderer reads the fields directly, because it needs the prev pair to interpolate against.
    // These are for anything that only wants the current value and does not know the species.
    @Override
    public float swimPitch() {
        return this.swimPitch;
    }

    @Override
    public float swimRoll() {
        return this.swimRoll;
    }
    private float smoothedVerticalSpeed;

    /**
     * How far the body may nose up or down, and how far it may bank.
     *
     * <p>Both under the Kriftognathus' flight equivalents (40 and 45) on purpose. This body is three
     * blocks long, so the same angle sweeps far more silhouette — and the tilt does not act alone:
     * the rig's {@code lookAt} bends {@code gNeck} by up to another 30 degrees off {@code state.xRot},
     * which the swim control drives along the path. The two agree in direction, so the head leads the
     * dive, which is the read we want; they only look wrong if both saturate at once.
     */
    private static final float MAX_SWIM_PITCH = 30.0F;
    private static final float MAX_SWIM_ROLL = 35.0F;

    /**
     * Pitch and roll, recomputed on <b>both</b> sides from different inputs.
     *
     * <p>{@code deltaMovement} is not synced for mobs, so a client running the server's formula would
     * read roughly zero and render the animal rigidly level — the same trap the flying base documents.
     * The server reads its own velocity; the client reads the per-tick position delta it interpolates
     * anyway.
     */
    private void tickSwimTilt() {
        this.prevSwimPitch = this.swimPitch;
        this.prevSwimRoll = this.swimRoll;

        if (!this.isInWater() || this.isDeadOrDying()) {
            // The authored death clip owns the corpse pose; levelling out keeps the tilt from
            // fighting it, and on land there is nothing to bank into.
            this.swimPitch = Mth.lerp(0.1F, this.swimPitch, 0.0F);
            this.swimRoll = Mth.lerp(0.1F, this.swimRoll, 0.0F);
            this.smoothedVerticalSpeed = 0.0F;
            return;
        }

        float vertical = this.level().isClientSide()
                ? (float) (this.getY() - this.yo)
                : (float) this.getDeltaMovement().y;
        // 0.002, not the flier's 0.01. That threshold exists to stop numerical noise holding a tilt,
        // but a swimmer's vertical speeds are an order of magnitude below a flier's: tick samples put
        // this animal between 0.005 and 0.024 when actually climbing, and at a terminal -0.005 when
        // level. At 0.01 it therefore discarded most of the real signal along with the noise, and
        // swimPitch read a flat 0.000 through entire samples.
        if (Math.abs(vertical) < 0.002F) {
            vertical = 0.0F;
        }
        this.smoothedVerticalSpeed = Mth.lerp(0.08F, this.smoothedVerticalSpeed, vertical);
        // 300, not the flier's 100. A tick sample had this peaking at 1.7 degrees out of the 30 it is
        // allowed, because a swimmer's vertical speed is a fraction of a flier's — so the body barely
        // tilted and what little pitch was visible came from the neck instead. The clamp still bounds
        // it; this only decides how quickly it gets there.
        float targetPitch = Mth.clamp(this.smoothedVerticalSpeed * 300.0F, -MAX_SWIM_PITCH, MAX_SWIM_PITCH);
        this.swimPitch = Mth.lerp(0.06F, this.swimPitch, targetPitch);

        // Bank out of the yaw RATE, not the yaw: a steady heading must not hold the animal leaning.
        float yawDelta = Mth.wrapDegrees(this.getYRot() - this.yRotO);
        if (Math.abs(yawDelta) < 0.3F) {
            yawDelta = 0.0F;
        }
        double horizontal = this.level().isClientSide()
                ? Math.hypot(this.getX() - this.xo, this.getZ() - this.zo)
                : this.getDeltaMovement().horizontalDistance();
        // No speed, no bank — an animal turning on the spot is not carving a curve.
        float speedFactor = (float) Math.min(1.0D, horizontal * 6.0D);
        // 12 rather than the flier's 4: the swim turn cap is 2.2 degrees a tick against flight's much
        // larger budget, so the same gain would leave the bank invisible. It now also ramps in and out
        // instead of appearing at full value, which is what turns this from a lean into a roll.
        float targetRoll = Mth.clamp(-yawDelta * 12.0F * speedFactor, -MAX_SWIM_ROLL, MAX_SWIM_ROLL);
        this.swimRoll = Mth.lerp(0.06F, this.swimRoll, targetRoll);
    }

    // ───────────────────────────────────────────────────── WATER ─────

    /**
     * Swaps navigator AND move control with the medium — vanilla's Drowned does the same.
     *
     * <p><b>Both halves matter, and the second one is measured.</b> A single navigator cannot cover
     * both media: free swimming needs {@code WaterBoundPathNavigation}, walking needs a ground one,
     * and {@code AmphibiousPathNavigation} — the obvious-looking middle ground — serves turtles and
     * axolotls that travel along surfaces, not something crossing open water.
     *
     * <p>The move control has to swap too. {@code SmoothSwimmingMoveControl} scales the speed
     * attribute by its own modifiers: fifty times down in water, ten times down outside it. Leaving
     * it installed on land turned an attribute of 1.0 into an effective 0.06, which is the crawl up
     * the beach. That figure comes from the same arithmetic that predicted the 0.004 the debug dump
     * then confirmed, so it is a calculation rather than a hunch. A plain {@link MoveControl} passes
     * the speed through untouched.
     *
     * <p>Driven from {@link #tick()} rather than left to {@code updateSwimming}: whether vanilla calls
     * that hook every tick for this class is not something worth betting the behaviour on, and tick
     * demonstrably runs.
     */
    private void syncControlsToMedium() {
        boolean swimming = this.isInWater();
        this.navigation = swimming ? this.waterNavigation : this.groundNavigation;
        this.moveControl = swimming ? this.swimControl : this.walkControl;
        this.setSwimming(swimming);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            this.syncControlsToMedium();
        }
        this.tickSwimTilt();
    }

    /** It walks. The flail is for something that cannot. */
    @Override
    protected boolean shouldFlopOnLand() {
        return false;
    }

    /** It hauls out to bask; drying out must not kill it for doing what it is meant to do. */
    @Override
    protected boolean shouldTakeDryDamage() {
        return false;
    }

    @Override
    protected double getSwimSpeedThreshold() {
        return SWIM_SPRINT_THRESHOLD;
    }

    // ───────────────────────────────────────────────────── ANIMATIONS ─────

    /**
     * Locomotion only, for 1a. Exactly one of these holds at any moment: in water or not, moving or
     * not.
     *
     * <p><b>Why {@code swim} is registered twice</b> instead of going through {@link #clip}, which is
     * what every other pair of age variants here uses: that helper takes ONE duration for both ages,
     * which is fine when they are close — the Hell Hippo's swim is 1.15 adult against 1.1667 calf, so
     * one number covers it. Here the adult clip is authored at 1.9 s and the calf's at 5.0 s, a
     * factor of 2.6. A shared number would leave one of the two ages either cut short or frozen in a
     * held pose for seconds, which is exactly the "it goes stiff at the end" symptom the Hell Hippo's
     * bite had. Two clips, each with its own real length, gated on age.
     */
    @Override
    public void registerAnimations() {
        StandardAnimation idle = clip("idle",
                () -> NirasLandAnimations.lidle, () -> NirasBabyLandAnimations.l_idle,
                Loop.REPEATING, 3, 4.0F);
        StandardAnimation walk = clip("walk",
                () -> NirasLandAnimations.walking, () -> NirasBabyLandAnimations.walking,
                Loop.REPEATING, 3, 2.5F);
        StandardAnimation sprint = clip("sprint",
                () -> NirasLandAnimations.lsprint, () -> NirasBabyLandAnimations.l_sprint,
                Loop.REPEATING, 3, 1.2F);

        StandardAnimation waterIdle = clip("water_idle",
                () -> NirasWaterAnimations.widle, () -> NirasBabyWaterAnimations.w_idle,
                Loop.REPEATING, 3, 3.0F);
        StandardAnimation swimAdult = adultClip("swim",
                () -> NirasWaterAnimations.swim, Loop.REPEATING, 3, 1.9F);
        StandardAnimation swimBaby = babyClip("swim_baby",
                () -> NirasBabyWaterAnimations.swim, Loop.REPEATING, 3, 5.0F);
        StandardAnimation waterSprint = clip("water_sprint",
                () -> NirasWaterAnimations.wsprint, () -> NirasBabyWaterAnimations.w_sprint,
                Loop.REPEATING, 3, 2.0F);

        // Two deaths, by medium, the way the salmon does it: drowning on the shore and sinking in
        // open water are different silhouettes and the export has a clip for each.
        StandardAnimation landDeath = clip("land_death",
                () -> NirasLandAnimations.ldeath, () -> NirasBabyLandAnimations.l_death,
                Loop.PLAY_ONCE, 0, 1.5F);
        StandardAnimation waterDeath = clip("water_death",
                () -> NirasWaterAnimations.wdeath, () -> NirasBabyWaterAnimations.w_death,
                Loop.PLAY_ONCE, 0, 2.5F);

        idle.setPlayCondition(a -> this.canPlayLocomotion() && !this.isInWater() && !this.isMoving());
        walk.setPlayCondition(a -> this.canPlayLocomotion() && !this.isInWater() && this.isMoving() && !this.isSprinting());
        sprint.setPlayCondition(a -> this.canPlayLocomotion() && !this.isInWater() && this.isMoving() && this.isSprinting());

        waterIdle.setPlayCondition(a -> this.canPlayLocomotion() && this.isInWater() && !this.isMoving());
        // Chasing something, NOT swimming quickly. The base offers isSwimmingFast(), a speed
        // threshold, and on this animal that fired almost permanently — a cruising Nirasmosaurus is
        // already past any sensible cut, so the sprint clip became the only one ever seen. Sprinting
        // is meant to read as intent, so it keys off having prey instead.
        //
        // isAggressive() and not getTarget(): the target is not synced to clients, and play conditions
        // run on both sides, so reading it directly would put the two on different clips. Aggression
        // rides the synced flag byte. Nothing sets it until the attack goal lands in 1c, so until then
        // this clip is correctly dormant rather than wrongly constant.
        swimAdult.setPlayCondition(a -> this.canPlayLocomotion() && this.isInWater() && this.isMoving()
                && !this.isAggressive() && !this.isBaby());
        swimBaby.setPlayCondition(a -> this.canPlayLocomotion() && this.isInWater() && this.isMoving()
                && !this.isAggressive() && this.isBaby());
        waterSprint.setPlayCondition(a -> this.canPlayLocomotion() && this.isInWater() && this.isMoving()
                && this.isAggressive());

        landDeath.setPlayCondition(a -> !this.isInWater());
        waterDeath.setPlayCondition(a -> this.isInWater());
        landDeath.blockAdditive();
        waterDeath.blockAdditive();

        this.animator().register(idle, walk, sprint, waterIdle, swimAdult, swimBaby, waterSprint);
        this.animator().registerDeath(waterDeath, landDeath);
    }

    /** Nothing scripted is running, so locomotion may own the frame. */
    private boolean canPlayLocomotion() {
        return !this.isPerformingAction() && !this.isDeadOrDying();
    }

    private StandardAnimation clip(String name, Supplier<Object> adult, Supplier<Object> baby,
                                   Loop loop, int priority, float seconds) {
        return new StandardAnimation(name,
                new AnimSource(() -> this.isBaby() ? baby.get() : adult.get()), loop, 0, priority, seconds);
    }

    private StandardAnimation adultClip(String name, Supplier<Object> adult, Loop loop, int priority, float seconds) {
        return new StandardAnimation(name, new AnimSource(adult), loop, 0, priority, seconds);
    }

    private StandardAnimation babyClip(String name, Supplier<Object> baby, Loop loop, int priority, float seconds) {
        return new StandardAnimation(name, new AnimSource(baby), loop, 0, priority, seconds);
    }

    // ───────────────────────────────────────────────────── SLEEP ─────

    /**
     * Nothing type-specific rouses it. The sleep cycle itself lands in 1b; this is only the contract
     * {@code ISleepingEntity} requires so the class is concrete, and an empty set is the honest
     * answer for an animal that sleeps in open water with nothing in particular to fear.
     */
    @Override
    public @NotNull Set<EntityType<?>> getInterruptingEntityTypes() {
        return Set.of();
    }

    // ───────────────────────────────────────────────────── SPAWN & BREEDING ─────

    /**
     * In water, or hauled out on a shore it could plausibly have crawled onto. Straight from 1.20.1,
     * which accepted sand, grass, gravel and stone — the shoreline blocks — rather than the usual
     * "on any solid block" so it does not appear inland.
     */
    public static boolean checkNirasSpawnRules(EntityType<NirasmosaurusEntity> type, LevelAccessor level,
                                               EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        boolean inWater = level.getFluidState(pos).is(FluidTags.WATER)
                && level.getFluidState(pos.below()).is(FluidTags.WATER);
        boolean onShore = level.getBlockState(pos).isAir()
                && (level.getBlockState(pos.below()).is(Blocks.SAND)
                || level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)
                || level.getBlockState(pos.below()).is(Blocks.GRAVEL)
                || level.getBlockState(pos.below()).is(Blocks.STONE));
        return inWater || onShore;
    }

    /**
     * Rolls the sex. {@code GenderedSMOPAnimal} deliberately leaves this to each concrete mob rather
     * than doing it in the base — an animal hatched from an egg and one placed by the world generator
     * take different paths into existence. Omitting it is why every Nirasmosaurus came out male: the
     * synched flag simply kept its {@code true} default, so nothing was ever random about it.
     */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level,
                                                  @NotNull DifficultyInstance difficulty,
                                                  @NotNull EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData spawnData) {
        this.setMale(this.getRandom().nextBoolean());
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob partner) {
        return SMOPEntities.NIRASMOSAURUS.get().create(level, EntitySpawnReason.BREEDING);
    }

    @Override
    public boolean isFood(@NotNull ItemStack stack) {
        return FOOD_ITEMS.test(stack);
    }

    // ───────────────────────────────────────────────────── SOUNDS ─────

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return SoundEvents.DOLPHIN_AMBIENT_WATER;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(@NotNull DamageSource source) {
        return SoundEvents.DOLPHIN_HURT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return SoundEvents.DOLPHIN_DEATH;
    }
}

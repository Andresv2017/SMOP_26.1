package net.darkblade.smop.entity.niras;

import net.darkblade.deluxelib.anim.AnimSource;
import net.darkblade.deluxelib.anim.Loop;
import net.darkblade.deluxelib.anim.StandardAnimation;
import net.darkblade.deluxelib.combat.AttackAnchor;
import net.darkblade.deluxelib.combat.AttackShape;
import net.darkblade.deluxelib.combat.HitWindow;
import net.darkblade.deluxelib.entity.ai.goal.AnimatableMeleeAttackGoal;
import net.darkblade.smop.command.SMOPBiteDebug;
import net.darkblade.smop.client.niras.NirasBabyLandAnimations;
import net.darkblade.smop.client.niras.NirasBabyWaterAnimations;
import net.darkblade.smop.client.niras.NirasLandAnimations;
import net.darkblade.smop.client.niras.NirasNeckOverlay;
import net.darkblade.smop.client.niras.NirasWaterAnimations;
import net.darkblade.smop.block.SMOPBlocks;
import net.darkblade.smop.entity.SMOPWaterAnimal;
import net.darkblade.smop.entity.SwimTilt;
import net.darkblade.smop.entity.ai.goal.GenericBreedGoal;
import net.darkblade.smop.entity.ai.goal.HaulOutGoal;
import net.darkblade.smop.entity.ai.goal.SMOPFollowParentGoal;
import net.darkblade.smop.entity.ai.goal.SMOPRandomStrollGoal;
import net.darkblade.smop.entity.ai.control.SwimSteerControl;
import net.darkblade.smop.entity.ai.goal.SwimWanderGoal;
import net.darkblade.smop.entity.ai.goal.egg.EggGoalRegistry;
import net.darkblade.smop.entity.ai.goal.egg.ProtectEggBaseGoal;
import net.darkblade.smop.entity.egg.CustomEggBorn;
import net.darkblade.smop.entity.sleep.SleepPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.DifficultyInstance;
import net.darkblade.smop.entity.ai.goal.SeekNestSiteGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.darkblade.smop.entity.ai.navigation.SmartSwimmingNavigation;
import net.darkblade.deluxelib.entity.ai.pathing.DirectionalMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.darkblade.smop.entity.ai.control.SmoothSwimLookControl;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The Nirasmosaurus: a marine reptile that hunts in the water and hauls out onto the shore.
 *
 * <p><b>Port status — phases 1a, 1b and 1c of the port spec.</b> Geometry, both animation sets,
 * amphibious locomotion, the sleep cycle in both mediums, nesting, and now the two simple bites with
 * the hunting that gives them something to bite. The grab, the shake and the death roll — the moves
 * that justify the animal — are 1d; taming and riding are phase 2. The idle gesture was dropped from
 * 1b on purpose: {@code roar}, {@code goofy} and {@code waiting} are all still unspent, and which of
 * them reads as this animal's resting tic is a decision better made with it alive on screen.
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
public class NirasmosaurusEntity extends SMOPWaterAnimal implements SwimTilt, CustomEggBorn {

    /** Bait and breeding food. Cooked fish, as in 1.20.1's lure config. */
    private static final Ingredient FOOD_ITEMS = Ingredient.of(Items.COOKED_COD, Items.COOKED_SALMON);

    /** Above this speed (blocks/tick) the sprint clip takes over from the cruise clip. */
    private static final double SWIM_SPRINT_THRESHOLD = 0.105D;

    /**
     * How far the calf may drift from its mother, against vanilla's three. @see #registerGoals()
     */
    private static final double FOLLOW_PARENT_DISTANCE = 6.0D;

    /**
     * What a goal's speed modifier is multiplied by once the animal is walking rather than swimming.
     *
     * <p>Measured, not guessed: a debug dump read {@code speed=0.600} at a modifier of 0.6 on land,
     * against roughly 0.25 for a cow. Applied in the land navigator so goals never have to know.
     * @see #NirasmosaurusEntity(EntityType, Level)
     */
    private static final double LAND_SPEED_SCALE = 0.25D;

    /**
     * What it hunts.
     *
     * <p><b>Nothing here is ported.</b> The 1.20.1 selector matches horses and only horses, which is
     * a leftover from testing against whatever was standing in the world, not a design — so the list
     * is chosen rather than carried over. These five share the beach and the warm ocean with it.
     *
     * <p><b>The dolphin is deliberately absent.</b> Not out of mercy: it is the one thing down there
     * that fights back, and the first read of this animal should not be "it kills dolphins". The
     * drowned is absent for the opposite reason — it is a {@code Monster}, and eating it would make
     * this a de facto ally of the player. The turtle IS here, because a shelled reptile thrashing in
     * the jaws is exactly the picture 1d is being built for.
     */
    private static boolean isPrey(@NotNull LivingEntity candidate) {
        EntityType<?> type = candidate.getType();
        return type == EntityType.COD
                || type == EntityType.TROPICAL_FISH
                || type == EntityType.PUFFERFISH
                || type == EntityType.SQUID
                || type == EntityType.TURTLE;
    }

    /**
     * The hunting selector.
     *
     * <p>A {@code TargetingConditions.Selector} rather than a {@code Predicate}, because 26.1 hands
     * the selector the level as well as the candidate — same shape as the Hell Hippo's.
     */
    private static final TargetingConditions.Selector PREY_SELECTOR =
            (target, level) -> isPrey(target);

    /**
     * Who the nest guard is willing to mark, now that there is a bite behind it.
     *
     * <p>Prey plus the player. {@link EggGoalRegistry} wants a plain {@code Predicate}, hence the
     * second form of the same test — and the base goal already excludes the mob itself, so no other
     * Nirasmosaurus can end up here: a mother does not maul the father over the clutch.
     *
     * <p>This replaces 1b's explicit "nobody", which existed only because the guard had no attack to
     * back a target up with.
     */
    private static final Predicate<LivingEntity> NEST_ENEMIES =
            entity -> entity instanceof Player || isPrey(entity);

    /**
     * The bite, in ticks of its own clip: the frames the jaws are actually closing.
     *
     * <p><b>One window covers all four clips.</b> {@code lbite}, {@code wbite}, {@code l_bite} and
     * {@code w_bite} — adult and calf, land and water — are authored to the same beat:
     * {@code gLowerjaw} opens to 32.5° at 0.1 s, holds to 0.4 and shuts at 0.45. Ticks 8 and 9 are
     * that shut in every one of them.
     *
     * <p>1.20.1 declared {@code damageFrames = {9}} and it turns out to be right. The difference is
     * that this was read off the keyframes rather than inherited, which is what the port spec asks
     * for every number that comes from there.
     */
    private static final int BITE_WINDOW_START = 8;
    private static final int BITE_WINDOW_END = 9;

    /** Registered names of the two bites. What {@code onAttack} looks up, and what the two
     * {@link HitWindow}s are applied to. Each covers both ages through its own {@code AnimSource}. */
    private static final String ANIM_BITE = "bite";
    private static final String ANIM_WATER_BITE = "water_bite";

    /**
     * How long each bite is registered for. The two differ, and the reason is what layer they play on.
     *
     * <p><b>The land bite is trimmed to 1.1 from its authored 1.2.</b> Measured, not guessed: of its
     * 19 channels the jaws stop keyframing at 0.85 and hold shut, the chest and back flippers at
     * 0.95, the throat at 1.0, the tail at 1.05, and the body and front flippers land their last
     * keyframe at 1.1 already sitting on their rest values. Only {@code gNeck} and {@code gHead}
     * carry a 1.2 keyframe, and both are easing back to the pose the idle starts from — which is
     * exactly what a 250 ms blend-out is for. Same reasoning that cut the Hell Hippo's lunge.
     *
     * <p><b>The water bite keeps all 1.2, and trimming it would be a bug.</b> It is an ADDITIVE
     * overlay now (layer 1), so it has to run to the frame its channels reach neutral or the blend-out
     * inherits whatever offset is left over. The last one there is {@code gHead}: still 22.5 degrees
     * of roll at 0.9, and only zero at 1.2. Cutting it would create the pop that cutting is normally
     * meant to remove.
     */
    private static final float LAND_BITE_SECONDS = 1.1F;
    private static final float WATER_BITE_SECONDS = 1.2F;

    /**
     * Centre-to-centre strike range.
     *
     * <p>Sized against the rig, not by feel. The muzzle hangs far past the collision box: the chain
     * runs {@code gNirasmo +15} → {@code gNeck −25} → {@code gHead −14} → the snout cube's own
     * {@code −28}, and the bite clip adds another {@code −4} to the head, putting the tip 56 px =
     * <b>3.5 blocks</b> ahead of the entity position against a half-width of 1.5.
     *
     * <p>The rule the Hell Hippo paid two rounds of testing for: the gate that decides whether to
     * swing must never be tighter than the damage it gates, nor wider than the box that applies it.
     * Both shapes below reach 4.0 from centre, so this sits just inside them.
     */
    private static final float ATTACK_REACH = 3.8F;

    /**
     * Where it stops walking in, which is NOT the reach.
     *
     * <p>Driving the approach off the same generous number parks a long-headed animal a body-length
     * short of something it is happily biting. At 3.2 the jaws end up just past the target instead of
     * short of it, and it is still above what two hitboxes this wide physically allow, so it settles
     * rather than shoving.
     */
    private static final float ATTACK_STOP_DISTANCE = 3.2F;

    /** Two ticks of breath after the 24-tick clip, so bites read as separate snaps. */
    private static final int ATTACK_COOLDOWN = 26;

    /**
     * Chase speed, one number for both media.
     *
     * <p><b>The number is for the WATER, and it took three passes to land.</b> It shipped at 1.3,
     * taken from the land goals' range by analogy, which is the one place the analogy does not hold:
     * in water every speed runs through {@code SwimSteerControl}'s {@code speedScale} of 0.01 as a
     * flat multiplier, so all that survives is the RATIO to the wandering goals — and those wander at
     * 1.0. A thirty per cent boost over a patrol is not a hunt. Measured rather than argued:
     * {@code /smop debug bite watch} timed that chase closing 14 blocks in five and a half seconds,
     * roughly 2.5 blocks a second, against a player who swims at 2.2.
     *
     * <p><b>And it is NOT shared with the land chase, which is the mistake this replaces.</b> The
     * original note here argued that 1.20.1's split — 1.2 ashore, 1.6 afloat — should not be ported,
     * because since 1b the per-medium conversion lives in the land navigator and every goal speaks
     * one unit. The first half of that is true and still stands; the conclusion does not follow. The
     * conversion factors differ by twenty-five to one, so the same modifier is the same MULTIPLE of a
     * stroll in both media — and three and a half times a stroll reads as a lunge in water and as a
     * sprint on a beach. What a chase is worth genuinely differs by medium; that is design, not the
     * unit riddle 1b removed. @see #LAND_CHASE_SPEED
     */
    private static final double CHASE_SPEED = 3.5D;

    /**
     * The same chase, ashore, in the units the land goals use — where a stroll is 1.0.
     *
     * <p>Applied in the ground navigator rather than at the goal, because {@code MeleeAttackGoal}
     * takes one speed at construction and hands it to whichever navigator is current. That funnel
     * already existed for the unit conversion; this rides along with it.
     */
    private static final double LAND_CHASE_SPEED = 1.4D;

    /**
     * How far ashore a quarry may be and still be worth beaching for. @see HaulOutGoal
     *
     * <p>Under {@code FOLLOW_RANGE}'s 28 on purpose: hauling out is a commitment — the animal leaves
     * its own element and spends the trip unable to fight back — and it should be made for something
     * standing at the water's edge, not for a player who has walked halfway up the beach.
     */
    private static final double HAUL_OUT_RANGE = 16.0D;

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
        // Every speed handed to the land navigator is scaled down here, in ONE place, and the reason
        // is the attribute: MOVEMENT_SPEED is 1.0 because the water control multiplies it by its own
        // 0.01 (see createAttributes). The land control does no such thing — DirectionalMoveControl
        // does setSpeed(modifier x attribute) flat — so the same modifier means roughly a hundred
        // times more speed ashore than afloat.
        //
        // This used to be handled by writing 0.25 into the one goal that only ran on land, which
        // worked until 1b added goals that run in BOTH: a breeding pair at 1.15 tore across the beach
        // at four and a half times a stroll, which is what "corren rapidísimo" was. Scaling at the
        // navigator means every goal, including the ones 1c and 2 will add, states its speed in the
        // same units the water goals already use and gets a sane number on land for free.
        //
        // moveTo(Path, double) is the single funnel: the position and entity overloads both build a
        // path and delegate to it.
        this.groundNavigation = new GroundPathNavigation(this, level) {
            @Override
            public boolean moveTo(@Nullable Path path, double speed) {
                // A chase ashore runs at its OWN number, not at the water one.
                //
                // Everything else here speaks one unit and is converted by LAND_SPEED_SCALE, which is
                // what 1b moved into this funnel and is still right. The chase is the exception, and
                // collapsing it into a single figure was a mistake this class made twice over: the
                // per-medium scales differ by twenty-five to one, so a modifier tuned until the water
                // hunt looked like a hunt came out ashore as three and a half times a walk.
                //
                // isAggressive() is the melee goal's own flag — raised in its start(), dropped in its
                // stop() — so it is true across exactly the ticks this is meant to cover. A goal that
                // happened to path while aggressive (a gravid female heading for her nest mid-fight)
                // would be swept up too; at 1.4 against her 1.0 that is not worth a second flag.
                double ashore = NirasmosaurusEntity.this.isAggressive() ? LAND_CHASE_SPEED : speed;
                return super.moveTo(path, ashore * LAND_SPEED_SCALE);
            }
        };
        this.swimControl = new SwimSteerControl(this, 2.2F, 45.0F, 4.0F, 0.01F)
                // 2, down from the control's default of 6. moveRelative NORMALISES the drive vector, so
                // the gain sets what fraction of the motion is vertical rather than adding to it: at 6
                // this animal descended faster than it swam forward, which the salmon's samples caught
                // as dives past 55 degrees. It was invisible while the tilt came from a linear gain on
                // the vertical speed; the moment the body follows its true trajectory it is not.
                //
                // Six was originally chosen because at 4 "the climbs were so gentle that the body tilt
                // stayed near zero" — but that was under the old formula, where a small vertical speed
                // produced a small angle no matter how steep the path actually was. The trajectory
                // angle has no such problem, so the gain is free to be honest now.
                .verticalGain(2.0F)
                // 30 while fighting, against 2.2 cruising — the SAME pair of numbers the land control
                // below already had, and the reason pursuit ashore felt right while pursuit in the
                // water did not. 1c shipped without this and the symptoms were exactly what the
                // arithmetic predicts: at 2.2 a ninety-degree correction takes 41 ticks plus two
                // ramps, so a chase never lines up, and turningSpeedFactor cuts the forward drive to
                // as little as 0.35 for the whole of it — the harder it needs to turn, the slower it
                // swims. The bite then fired with the body still pointing where the prey used to be.
                .combatTurnSpeed(30.0F);
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

        // The shoreline ring, made free to cross.
        //
        // The base already zeroes PathType.WATER — without it a swimmer treats water as a hazard and
        // goes nowhere — but it says nothing about WATER_BORDER, which vanilla defaults to a malus of
        // 8.0. That is not a wall, it is worse: it is a costed ring around every piece of water, so
        // A* would rather trail along the beach than step across it, and on a three-block body with
        // vanilla's node budget the search gives up and hands back a closest-approach path that never
        // crosses at all.
        //
        // Measured, not deduced: `/smop debug bite watch` caught the animal ASHORE, six blocks from a
        // player it was chasing, with the ground navigator returning path=null — while every water
        // path it built was one node long and canReach=false. The Hell Hippo sets both of these in
        // its own constructor for exactly this reason, and its comment names the failure: leaving the
        // border costed "would make the animal treat its own entry and exit from the water as an
        // obstacle worth walking around". Vanilla's Drowned sets WATER for the same reason.
        this.setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
    }

    /**
     * {@code createAnimalAttributes}, not {@code createLivingAttributes} as 1.20.1 had it: 26.1's
     * {@code TemptGoal} reads {@code Attributes.TEMPT_RANGE}, which only the animal supplier defines.
     * Getting this wrong crashes on spawn rather than at load, and it already cost us an evening on
     * the Hell Hippo.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
                // 30, not the 10 carried over from 1.20.1. That number was never thought about: it is
                // what a cow has, and what the one-block Tangoftero has, on the largest animal in the
                // mod — half the Hell Hippo's 20 on a body longer than the Hell Hippo's. An apex
                // predator that a stone sword kills in four swings is not one.
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.FOLLOW_RANGE, 28.0D)
                // 1.0, not the 0.20 carried over from 1.20.1. That number belonged to an entity with
                // its own travel and its own controls; here SmoothSwimmingMoveControl multiplies the
                // attribute by its in-water modifier of 0.02, so 0.20 came out as an effective 0.004 —
                // measured, not guessed: the animal was moving at 0.005 blocks/tick, which is a tenth
                // of a block per second and reads as floating in place. The salmon swims on 0.6; this
                // is a larger predator, so it gets more.
                .add(Attributes.MOVEMENT_SPEED, 1.0D)
                .add(Attributes.ATTACK_SPEED, 0.4D)
                // Irrelevant here and left at zero deliberately: the attribute feeds vanilla's
                // doHurtTarget, and nothing on this animal goes through it — the bite's knockback is
                // the HitWindow's own, see registerAnimations.
                .add(Attributes.ATTACK_KNOCKBACK, 0.0D)
                // 4, and NOT more, with 1d in mind: the grab, the shake and the death roll all have
                // to hit harder than a loose snap, and a base bite already worth three hearts leaves
                // them nowhere to grow. The HitWindow reads this attribute rather than carrying a
                // literal, so this is the one place to tune it.
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0D);
    }

    @Override
    protected void registerGoals() {
        // Top of the list, and it has to be: the sleep goal holds MOVE, LOOK and JUMP for the whole
        // cycle, and that hold is the only thing preempting the swim and stroll goals below. Anywhere
        // further down and the animal would go on swimming in its sleep.
        this.goalSelector.addGoal(1, this.createSleepGoal());

        // Beaching itself after a quarry that climbed out.
        //
        // ABOVE the bite, and that placement is the whole reason it works: the melee goal holds MOVE
        // for as long as it has a target, whether or not it can path anywhere with it. Registered
        // below, this would never be handed the flag and would never run — which is exactly the state
        // the animal was stuck in, holding MOVE and going nowhere.
        //
        // It ends the instant the animal is on land, and the melee chase — whose ground navigator can
        // route from there — takes the last few blocks. @see HaulOutGoal for why no navigator could
        // ever have produced this leg.
        // NOT picksItsOwnFights: that one also excludes a tamed animal, and this has to run for
        // retaliation too — a Nirasmosaurus that was hit and whose attacker ran up the beach should
        // follow it out whoever it belongs to. Only age and sleep gate it.
        this.goalSelector.addGoal(2, new HaulOutGoal(this, HAUL_OUT_RANGE,
                () -> !this.isBaby() && !this.isInSleepCycle()));

        // The bite. The goal decides only WHEN to commit; the damage lives in each clip's HitWindow
        // (see registerAnimations) so it lands on the frames the jaws close, instead of on a counter
        // running beside the animation — which is what 1.20.1 did, and why its hit and its visible
        // bite drifted apart.
        //
        // Everything below priority 1 shifted down one to make room here. NOT a tie with the breed
        // goal: WrappedGoal#canBeReplacedBy only yields a flag on a strict `<`, so two goals at the
        // same priority can never take MOVE back off each other once running — the trap 1b documents
        // on the Krifto's side.
        this.goalSelector.addGoal(3, new NirasBiteGoal()
                .reach(ATTACK_REACH)
                .stopDistance(ATTACK_STOP_DISTANCE)
                .cooldown(ATTACK_COOLDOWN)
                .attackCondition(target -> !this.isBaby())
                // Which of the two clips, by medium — the same shape as the Krifto's
                // `isFlying() ? bite_flight : attack`.
                .onAttack((target, animator) -> animator.play(animator.getByName(
                        this.isInSwimmingMedium() ? ANIM_WATER_BITE : ANIM_BITE))));

        // GenericBreedGoal, not the vanilla BreedGoal the Hell Hippo uses: that one ends in a calf.
        // This one flips hasEgg on anything that is not a mammal, and this animal is not one —
        // isMammal defaults to false and nothing here sets it.
        // 16-block partner search against vanilla’s 8. @see GenericBreedGoal
        this.goalSelector.addGoal(4, new GenericBreedGoal<>(this, 1.15D, 16.0D));
        // Six blocks, against vanilla's three. Same problem the Hell Hippo solved with five: on a
        // body this long, three blocks of follow distance puts the baby INSIDE its mother.
        this.goalSelector.addGoal(6, new SMOPFollowParentGoal(this, 1.1D, FOLLOW_PARENT_DISTANCE));

        // Solitary nester: it minds the egg it laid itself. Radii of 6 and 8 against the salmon's and
        // the Krifto's 4 and 6, because the animal is far larger and a 4-block leash would keep it
        // practically on top of the nest.
        //
        // Both attack flags are true now, which is the promise 1b made when it set them false: the
        // guard was mute only because there was no bite to back a marked target up with, and an
        // animal that fixes on an intruder and then does nothing reads as broken rather than placid.
        // The enemy selector grew teeth on the same schedule.
        EggGoalRegistry.registerWithOwnGoal(this, SMOPBlocks.NIRAS_EGG,
                6, 8, true, true,
                ProtectEggBaseGoal.EggBreakReaction.IGNORE, NEST_ENEMIES, 7);

        // Finding the beach AND staying on it. The Tangoftero and the Kriftognathus need no such goal
        // because any solid ground is a nest to them, so wherever the laying countdown ends is legal.
        // This one is fussy — sand or gravel — and without something holding it in place the female
        // wandered off the beach, or straight into the sea, during the two seconds the laying goal
        // spends counting. It reserves MOVE and the laying goal reserves nothing, so they cooperate:
        // this one owns where she stands, that one owns the egg.
        this.nestGoal = new SeekNestSiteGoal(this, 1.0D, pos -> this.canPlaceEggAt(this.level(), pos));
        this.goalSelector.addGoal(5, this.nestGoal);

        // No FloatGoal: that goal paddles a mob to the surface and bobs it there, which is the
        // opposite of an animal that swims through the whole column and rests on the bottom. It
        // breathes underwater, so there is nothing to protect it from. Same call as the Hell Hippo.
        // SwimWanderGoal, not the salmon's SMOPRandomSwimmingGoal. That one wraps vanilla's
        // RandomSwimmingGoal, which draws a point ten blocks away in any direction and ends the
        // moment it arrives — fine for a fish, but on a body this long it produced short hops, a
        // full stop, and a U-turn. See that class for what replaces each of those.
        this.goalSelector.addGoal(9, new SwimWanderGoal(this, 1.0D,
                () -> !this.isMovementLocked()));
        // 1.0, like every other goal here. It used to be 0.25 to divide the water-scaled attribute
        // back down by hand; that division now lives in the land navigator, so the effective speed is
        // unchanged (1.0 x LAND_SPEED_SCALE) and the number here stops being a species-specific
        // riddle. @see #LAND_SPEED_SCALE
        this.goalSelector.addGoal(10, new SMOPRandomStrollGoal(this, 1.0D, 160,
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
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 8.0F, 0.10F));
        this.goalSelector.addGoal(12, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            /**
             * A sleeping Nirasmosaurus does not wake into a fight — {@code SleepGoal} owns when it
             * wakes, and it does wake to a nearby player already. Same guard as the Hell Hippo's.
             */
            @Override
            public boolean canUse() {
                return !NirasmosaurusEntity.this.isInSleepCycle() && super.canUse();
            }
        });
        // No setAlertOthers(), unlike the hippo: that one alerts its family because it lives in one.
        // This animal has no pod — the port spec dropped the 1.20.1 group system along with the
        // hippo's.
        //
        // Explicit type arguments on both: the diamond cannot be inferred for an anonymous subclass.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<Player>(this, Player.class, true) {
            @Override
            public boolean canUse() {
                return NirasmosaurusEntity.this.picksItsOwnFights() && super.canUse();
            }
        });
        // Mob, not Animal. The Hell Hippo's prey goal is typed on Animal and that works for sheep,
        // goats and cows; here it would throw out almost the whole ocean, because the vanilla fish
        // and the squid extend WaterAnimal and only the turtle is an Animal.
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<Mob>(this, Mob.class, false, PREY_SELECTOR) {
            @Override
            public boolean canUse() {
                return NirasmosaurusEntity.this.picksItsOwnFights() && super.canUse();
            }
        });
    }

    /**
     * Whether this animal still chooses its own targets. Retaliation is deliberately exempt — it is
     * {@code HurtByTargetGoal} that answers for being hit, and it answers even here.
     *
     * <p>Calves never start anything, and neither does a sleeping animal. {@code !isTame()} is in
     * from the start even though taming does not exist until phase 2: it costs nothing now and stops
     * a tamed animal wandering off to hunt turtles on its own the day it can be tamed. The saddle
     * check joins it then, as on the Hell Hippo.
     */
    private boolean picksItsOwnFights() {
        return !this.isBaby() && !this.isInSleepCycle() && !this.isTame();
    }

    /**
     * The bite goal, named rather than anonymous for one reason: the builder methods return the goal,
     * so an anonymous body cannot hang off the end of the chain.
     *
     * <p><b>The calf neither bites nor chases.</b> {@code attackCondition(!isBaby())} at the call
     * site is not enough on its own, and the Kriftognathus spells out why: it only reaches as far as
     * {@code checkAndPerformAttack}, so it silences the bite but not the goal. None of the target
     * goals check age either, so a calf with a target would run the whole chase right up to biting
     * range and simply never bite — which from outside reads as "the calf is attacking and it is
     * broken", not as "calves do not fight".
     */
    private class NirasBiteGoal extends AnimatableMeleeAttackGoal {

        NirasBiteGoal() {
            super(NirasmosaurusEntity.this, CHASE_SPEED, true);
        }

        @Override
        public boolean canUse() {
            return !NirasmosaurusEntity.this.isBaby() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !NirasmosaurusEntity.this.isBaby() && super.canContinueToUse();
        }
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

    /** Vertical speed below which the body stays level. @see #tickSwimTilt */
    private static final float TILT_DEAD_ZONE = 0.006F;

    /** How fast the drawn tilt chases its target. Slower than the salmon's 0.15 — more animal. */
    private static final float TILT_SMOOTHING = 0.06F;

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

        if (!this.isInWater() || this.isDeadOrDying() || this.isInSleepCycle()) {
            // The authored clip owns the pose in all three cases; levelling out keeps the tilt from
            // fighting it, and on land there is nothing to bank into.
            //
            // Sleep is here for a reason that is not obvious: a sleeping animal is not still. The
            // goal cuts its input, so it SINKS — travel() trims 0.005 a tick against 0.9 drag, which
            // settles at about a block a second — and it sinks with no horizontal component at all.
            // The trajectory angle of that is straight down: atan2 hands back ninety degrees, the
            // clamp catches it at thirty, and the animal would spend its whole descent to the bed
            // nose-down at full deflection while playing a resting pose authored horizontal.
            this.swimPitch = Mth.lerp(0.1F, this.swimPitch, 0.0F);
            this.swimRoll = Mth.lerp(0.1F, this.swimRoll, 0.0F);
            return;
        }

        boolean client = this.level().isClientSide();
        float vertical = client ? (float) (this.getY() - this.yo) : (float) this.getDeltaMovement().y;
        double horizontal = client
                ? Math.hypot(this.getX() - this.xo, this.getZ() - this.zo)
                : this.getDeltaMovement().horizontalDistance();

        // SOFT knee at 0.006, replacing a hard cut at 0.002. Two separate faults were fixed here, both
        // found on the salmon and both present in the version this replaces:
        //
        // The threshold has to clear the buoyancy trim. SwimSteerControl adds exactly 0.005 a tick and
        // SMOPWaterAnimal#travel takes 0.005 back off when there is no target, so an idling swimmer
        // always reads about -0.005 vertically. At 0.002 that drift counted as real and held the body
        // permanently nose-down; the tick samples show dY parked at exactly -0.0050 for whole stretches.
        //
        // And zeroing below a threshold makes the drawn angle JUMP the tick the threshold is crossed —
        // atan(0.006 / horizontal) appearing out of nothing. Subtracting the threshold instead passes
        // through zero continuously, which is what the salmon needed to stop reading as jerky.
        vertical = Math.signum(vertical) * Math.max(0.0F, Math.abs(vertical) - TILT_DEAD_ZONE);

        // The trajectory angle, not a gain on the vertical speed. The 300 this replaces was found by
        // trial and is only right for one range of speeds: it was chosen when the animal barely moved
        // vertically, and once the vertical drive was working it pegged the clamp instead. atan2 needs
        // no constant and cannot be wrong about the angle — if it saturates, the SWIMMING is too steep
        // and verticalGain is the number to look at, not this.
        float targetPitch = Mth.clamp(
                (float) Math.toDegrees(Mth.atan2(vertical, Math.max(horizontal, 1.0E-4D))),
                -MAX_SWIM_PITCH, MAX_SWIM_PITCH);
        // ONE smoothing stage. This used to lerp the vertical speed and then lerp the angle, and two
        // filters in series add their lags — on the salmon that put the body 42 degrees nose-up while
        // it had already been sinking for about a second. The angle is what gets drawn, so the angle is
        // the only thing worth filtering; 0.06 stands alone here rather than 0.15, because three blocks
        // of animal should take longer to settle than a hand-span of fish.
        this.swimPitch = Mth.lerp(TILT_SMOOTHING, this.swimPitch, targetPitch);

        // Bank out of the yaw RATE, not the yaw: a steady heading must not hold the animal leaning.
        float yawDelta = Mth.wrapDegrees(this.getYRot() - this.yRotO);
        if (Math.abs(yawDelta) < 0.3F) {
            yawDelta = 0.0F;
        }
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
        boolean changed = swimming != this.wasSwimmingLastTick;
        this.wasSwimmingLastTick = swimming;

        this.navigation = swimming ? this.waterNavigation : this.groundNavigation;
        this.moveControl = swimming ? this.swimControl : this.walkControl;
        this.setSwimming(swimming);

        // ── Hand the route over to the navigator that just took charge. ──
        //
        // A path belongs to the navigator that built it, so a swap silently abandons whatever the
        // animal was following: the new navigator starts empty and nothing re-fills it until the goal
        // happens to come round to its own recalculation. `/smop debug bite watch` caught the
        // consequence exactly — a Nirasmosaurus chasing a player who had climbed out oscillated
        // between "now ashore, path=null" and "now in water, path=1 nodes, canReach=false" every two
        // or three seconds, indefinitely, never covering the last few blocks. It was not that the
        // swimming navigator cannot route to dry land; it is that the route died in the handover.
        //
        // Re-issuing it here means the ground navigator inherits the chase the moment the animal
        // touches the beach, which is the same trick SeekNestSiteGoal uses to get a female ashore to
        // nest — only there it costs a re-path every second, and here it costs one only when the
        // medium actually changes.
        if (changed && !this.level().isClientSide()) {
            LivingEntity target = this.getTarget();
            if (target != null && target.isAlive()) {
                this.navigation.moveTo(target, CHASE_SPEED);
            }
        }
    }

    /** Previous tick's medium, so {@link #syncControlsToMedium()} can spot the crossing itself. */
    private boolean wasSwimmingLastTick;

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

        // The sleep cycle, twice over: three phases ashore and the same three under water. The rig
        // authored both sets and the animal uses both, so each phase is registered under its own name
        // and onSleepPhaseBegin picks the pair. Durations read off the clips, never estimated — the
        // two mediums differ by 9 and 14 ticks in the transitions and match exactly in the loop.
        //
        // Priority 1, under the one-shots and above locomotion: a sleeping animal must not have the
        // idle or the swim cycle competing for the frame, which is also why canPlayLocomotion now
        // tests isInSleepCycle.
        // 3.15, not the 3.55 the clip declares. Both land clips —adult and calf— stop keyframing at
        // 3.15 and the remaining 0.4 s is the animal held on its last frame, because a phase lasts
        // exactly as long as its registered clip. That freeze is real and measurable: it is the gap
        // between `withLength` and the last `new Keyframe` in the file, which nothing else checks.
        // The water pair has no such gap (4.0 declared, 4.0 animated) and is registered as authored.
        StandardAnimation preparingSleep = clip("preparing_sleep",
                () -> NirasLandAnimations.lsleep_preparing, () -> NirasBabyLandAnimations.l_sleep_preparing,
                Loop.PLAY_ONCE, 1, 3.15F);
        StandardAnimation sleep = clip("sleep",
                () -> NirasLandAnimations.lsleep, () -> NirasBabyLandAnimations.l_sleep,
                Loop.REPEATING, 1, 2.0F);
        StandardAnimation awakening = clip("awakening",
                () -> NirasLandAnimations.lawakening, () -> NirasBabyLandAnimations.l_awakening,
                Loop.PLAY_ONCE, 1, 2.3F);

        StandardAnimation preparingSleepWater = clip("preparing_sleep_water",
                () -> NirasWaterAnimations.wsleep_preparing, () -> NirasBabyWaterAnimations.w_sleep_preparing,
                Loop.PLAY_ONCE, 1, 4.0F);
        StandardAnimation sleepWater = clip("sleep_water",
                () -> NirasWaterAnimations.wsleep, () -> NirasBabyWaterAnimations.w_sleep,
                Loop.REPEATING, 1, 2.0F);
        StandardAnimation awakeningWater = clip("awakening_water",
                () -> NirasWaterAnimations.wawakening, () -> NirasBabyWaterAnimations.w_awakening,
                Loop.PLAY_ONCE, 1, 3.0F);

        // The land bite REPLACES: layer 0, priority 0, so it wins the frame over locomotion, which
        // keeps running underneath — what a finished PLAY_ONCE clip falls back onto is the BIND pose,
        // not its own last frame, so the cycle has to be there to catch it. That is right on land,
        // where the animal has stopped to bite and the idle underneath is what it should return to.
        StandardAnimation bite = clip(ANIM_BITE,
                () -> NirasLandAnimations.lbite, () -> NirasBabyLandAnimations.l_bite,
                Loop.PLAY_ONCE, 0, LAND_BITE_SECONDS);

        // The water bite OVERLAYS: layer 1, and cut down to the gNeck subtree.
        //
        // As a layer-0 clip it was replacing the swim cycle outright — a same-layer clip drops every
        // bone it does not author back to the bind pose — so for its whole length the animal struck
        // with its neck and went dead still from the chest back. That is the "se queda inmóvil"
        // straight from the game. On layer 1 the rig composites it additively over whatever the body
        // is doing, so the flippers and tail keep swimming through the bite.
        //
        // The clip therefore has to stop authoring the body, or the two sources would fight over the
        // tail — hence NirasNeckOverlay, which keeps the neck, head and jaws and drops the rest. Same
        // shape as the Kriftognathus' bite_flight, which overlays its jaw alone on top of the flight
        // cycle.
        //
        // Note the overlay composites over whatever locomotion clip is CURRENT, which is water_sprint
        // while it is still closing and water_idle once the goal has stopped the navigation to swing.
        // That is deliberate: the point is that the body carries on doing whatever it was doing.
        StandardAnimation waterBite = overlayClip(ANIM_WATER_BITE,
                () -> NirasNeckOverlay.WATER_BITE, () -> NirasNeckOverlay.BABY_WATER_BITE,
                Loop.PLAY_ONCE, 0, WATER_BITE_SECONDS);

        // Two deaths, by medium, the way the salmon does it: drowning on the shore and sinking in
        // open water are different silhouettes and the export has a clip for each.
        StandardAnimation landDeath = clip("land_death",
                () -> NirasLandAnimations.ldeath, () -> NirasBabyLandAnimations.l_death,
                Loop.PLAY_ONCE, 0, 1.5F);
        StandardAnimation waterDeath = clip("water_death",
                () -> NirasWaterAnimations.wdeath, () -> NirasBabyWaterAnimations.w_death,
                Loop.PLAY_ONCE, 0, 2.5F);

        idle.setPlayCondition(a -> this.canPlayLocomotion() && !this.isInSwimmingMedium() && !this.isMoving());
        walk.setPlayCondition(a -> this.canPlayLocomotion() && !this.isInSwimmingMedium() && this.isMoving() && !this.isSprinting());
        sprint.setPlayCondition(a -> this.canPlayLocomotion() && !this.isInSwimmingMedium() && this.isMoving() && this.isSprinting());

        waterIdle.setPlayCondition(a -> this.canPlayLocomotion() && this.isInSwimmingMedium() && !this.isMoving());
        // Chasing something, NOT swimming quickly. The base offers isSwimmingFast(), a speed
        // threshold, and on this animal that fired almost permanently — a cruising Nirasmosaurus is
        // already past any sensible cut, so the sprint clip became the only one ever seen. Sprinting
        // is meant to read as intent, so it keys off having prey instead.
        //
        // isAggressive() and not getTarget(): the target is not synced to clients, and play conditions
        // run on both sides, so reading it directly would put the two on different clips. Aggression
        // rides the synced flag byte, and as of 1c something sets it — vanilla MeleeAttackGoal#start
        // calls setAggressive(true) and #stop clears it (read from 26.1's own source, not assumed).
        // This clip stopped being dormant the moment the attack goal was registered.
        swimAdult.setPlayCondition(a -> this.canPlayLocomotion() && this.isInSwimmingMedium() && this.isMoving()
                && !this.isAggressive() && !this.isBaby());
        swimBaby.setPlayCondition(a -> this.canPlayLocomotion() && this.isInSwimmingMedium() && this.isMoving()
                && !this.isAggressive() && this.isBaby());
        waterSprint.setPlayCondition(a -> this.canPlayLocomotion() && this.isInSwimmingMedium() && this.isMoving()
                && this.isAggressive());

        // Blends, spelled out rather than left on the rig's 220 ms default. The seam that matters is
        // the last one: the waking clip ends on exactly the pose the idle starts from — verified
        // channel by channel, flippers included — so the only thing that can show at that seam is the
        // cross-fade itself, and 350 ms of it reads as the animal settling rather than as a cut.
        preparingSleep.blendInMs(300).blendOutMs(250);
        preparingSleepWater.blendInMs(300).blendOutMs(250);
        sleep.blendInMs(300).blendOutMs(300);
        sleepWater.blendInMs(300).blendOutMs(300);
        awakening.blendInMs(250).blendOutMs(350);
        awakeningWater.blendInMs(250).blendOutMs(350);

        // The one-shots are NOT started by these conditions — the animator's auto-start loop only
        // ever starts REPEATING clips, so SleepGoal starts them through onSleepPhaseBegin. What the
        // conditions buy is the reverse: a clip whose condition goes false is cut, which is what makes
        // a wake-up mid-transition end the transition instead of playing it out. Same shape as the
        // Hell Hippo's chain.
        preparingSleep.setPlayCondition(a -> this.isPreparingSleep() && !this.isSleepingInWater());
        preparingSleepWater.setPlayCondition(a -> this.isPreparingSleep() && this.isSleepingInWater());
        // The loop covers PREPARING_SLEEP too, so the settling clip has the sleeping pose underneath
        // it to hand over to rather than a gap. setNextAnimation chains the two.
        sleep.setPlayCondition(a -> (this.isSleeping() || this.isPreparingSleep()) && !this.isSleepingInWater());
        sleepWater.setPlayCondition(a -> (this.isSleeping() || this.isPreparingSleep()) && this.isSleepingInWater());
        preparingSleep.setNextAnimation("sleep");
        preparingSleepWater.setNextAnimation("sleep_water");
        awakening.setPlayCondition(a -> this.isAwakening() && !this.isSleepingInWater());
        awakeningWater.setPlayCondition(a -> this.isAwakening() && this.isSleepingInWater());

        landDeath.setPlayCondition(a -> !this.isInSwimmingMedium());
        waterDeath.setPlayCondition(a -> this.isInSwimmingMedium());
        landDeath.blockAdditive();
        waterDeath.blockAdditive();

        // Snappy either side: a bite that fades in reads as a yawn.
        bite.blendInMs(80).blendOutMs(150);
        waterBite.blendInMs(80).blendOutMs(150);

        // ── The two bites ─────────────────────────────────────────────────────────────────────
        //
        // Separate HitWindow instances, and that is not tidiness: each carries its own
        // hitThisSwing / lastSweepAngle state, and sharing one between two clips is what the
        // Kriftognathus documents as unshareable.
        //
        // The land bite: a frontal cuboid across the frames the jaws shut. box3d and NOT box —
        // AttackShape's own interface note says box ignores the Y axis outright ("a ground mob's
        // swing reaches whatever is in front of it, at any height"), and on a beach that means
        // biting a player standing on an overhang two blocks up. The hippo found this the hard way
        // the moment it walked into water.
        //
        // Anchor 2.0 forward and 0.9 up, box reaching another 2.0: the muzzle tip sits 3.5 blocks
        // ahead (see ATTACK_REACH for the chain that measures it), so the volume brackets it rather
        // than starting at it. All six numbers are look numbers — tune them against the render with
        // /deluxelib debug hitboxes, the way the hippo's were.
        HitWindow.of(BITE_WINDOW_START, BITE_WINDOW_END)
                .shape(AttackShape.box3d(2.0F, 1.1F, 1.2F))
                .anchor(AttackAnchor.of(2.0F, 0.0F, 0.9F))
                .damage((float) this.getAttributeValue(Attributes.ATTACK_DAMAGE))
                // Low on purpose, and it is 1d that wants it low: what the grab, the shake and the
                // roll all need is prey that stays in the jaws, not prey that gets launched.
                .knockback(0.2F)
                .filter(target -> target == this.getTarget() || !(target instanceof NirasmosaurusEntity))
                // Diagnostic only, and silent unless /smop debug bite watch is running. @see
                // SMOPBiteDebug#reportSweep for why this fork cannot be observed from outside.
                .onSweep((attacker, origin, facing, shape, hits) ->
                        SMOPBiteDebug.reportSweep(attacker, origin, facing, hits.size()))
                .applyTo(bite);

        // The water bite is aimed in 3D where the land one is flattened to the ground plane.
        // Underwater a target is as often above or below as beside, and a flattened box would leave a
        // Nirasmosaurus under a swimming player snapping at nothing for as long as the player stayed
        // there — the goal measures range in 3D and would keep firing.
        //
        // ── It aims with the HEAD, and that correction is the whole of this block's history. ──
        //
        // This shipped as aimAlongLook() and was wrong in the water. That helper is
        // `getViewVector(1.0F)`, which reads getXRot() and getYRot() — and on a mob getYRot() is the
        // BODY yaw, written by the MoveControl. Here that is SwimSteerControl, turning at 2.2 degrees
        // a tick. What actually tracks the target is yHeadRot, written by the LookControl that
        // MeleeAttackGoal#tick points at the prey every tick. So the volume followed the part of the
        // animal that was lagging, and the bite went off aimed where the body happened to be facing:
        // "muerde muy lejos de su objetivo", reported from the game and explained exactly here.
        //
        // What made the original reasoning wrong is worth keeping, because it was half right:
        // SwimSteerControl genuinely does not write xRot — its javadoc records that as the head
        // jitter fix — so the PITCH in that view vector was clean. Only the yaw was not, and the yaw
        // was never checked. DeluxeLib's own javadoc on aimAlongLook warns about this class of
        // mistake and prescribes the cure used here: facing() with the values the AI already tracks.
        //
        // The anchor stays in look space but its forward drops from 2.4 to 0.6, which is the neck's
        // base rather than the middle of the snout — AttackAnchor has no head space, so any forward
        // offset there still swings with the body yaw, and 0.6 makes that error small enough not to
        // matter (0.6 x sin 40 is under half a block). The box then does the reaching, 3.2 long,
        // along the head. Origin at the shoulder, volume along the neck: that IS the rig.
        //
        // The -0.5 of height is not a nicety either. AttackAnchor.look starts at the EYES, and the
        // default eye height of a 1.6-tall entity is 0.85 x 1.6 = 1.36, while the rig's head sits
        // about 0.8 above the feet. Without it the bite is born half a block above the mouth.
        //
        // Known limit, unchanged: the rig bends the neck at most 35 degrees of yaw and 30 of pitch
        // (NirasmosaurusModel's lookAt), so past that the volume goes where the head is AIMED and the
        // model can only lean part of the way. With combatTurnSpeed the body now closes that gap
        // itself instead of leaving the neck to cover it alone.
        HitWindow.of(BITE_WINDOW_START, BITE_WINDOW_END)
                .shape(AttackShape.box3d(3.2F, 1.0F, 1.0F))
                .anchor(AttackAnchor.look(0.6F, 0.0F, -0.5F))
                .facing(e -> Vec3.directionFromRotation(e.getXRot(), e.getYHeadRot()))
                .damage((float) this.getAttributeValue(Attributes.ATTACK_DAMAGE))
                .knockback(0.2F)
                // Same escape hatch as the land bite: the blanket exclusion spares bystanders, but
                // without `target == getTarget()` it also vetoes the one it is actually fighting —
                // and it does so in the broad-phase predicate, before the shape test ever runs, so
                // it reads as a geometry miss no amount of retuning could fix. Three attempts on the
                // Krifto before that was found.
                .filter(target -> target == this.getTarget() || !(target instanceof NirasmosaurusEntity))
                .onSweep((attacker, origin, facing, shape, hits) ->
                        SMOPBiteDebug.reportSweep(attacker, origin, facing, hits.size()))
                .applyTo(waterBite);

        this.animator().register(idle, walk, sprint, waterIdle, swimAdult, swimBaby, waterSprint,
                preparingSleep, sleep, awakening,
                preparingSleepWater, sleepWater, awakeningWater,
                bite, waterBite);
        this.animator().registerDeath(waterDeath, landDeath);
    }

    // ───────────────────────────────────────────────────── NESTING ─────

    /**
     * The goal that walks her to the nest, held so {@link #isSettledToLay()} can ask whether she has
     * got there yet.
     *
     * <p><b>Declared without an initialiser on purpose.</b> {@code registerGoals()} runs from
     * {@code Mob}'s constructor, and a subclass's field initialisers run only after the superclass
     * constructor returns — so writing {@code = null} here would emit an assignment that wipes the
     * instance the constructor already stored. Same trap as {@code SMOPAnimal.sleepUrge}.
     */
    private SeekNestSiteGoal nestGoal;

    /**
     * On the ground <b>and at the nest she set out for</b>.
     *
     * <p>The distance requirement would be decorative without this. {@code GenericLayEggGoal} counts
     * down wherever the animal happens to be and lays the moment the ground under it is legal, so a
     * female crossing the beach on her way somewhere would drop the egg two steps into the journey —
     * which is the "she laid it right where she was standing" this was written to stop.
     */
    @Override
    protected boolean isSettledToLay() {
        return super.isSettledToLay() && this.nestGoal != null && this.nestGoal.hasArrived();
    }

    /**
     * It comes out of the sea to nest, like a turtle. @see SMOPWaterAnimal#nestsAshore()
     */
    @Override
    protected boolean nestsAshore() {
        return true;
    }

    /**
     * Beside the body, either flank first, and only then underneath it.
     *
     * <p>Laid at its own feet the egg is <b>invisible</b>: this animal is three blocks across and its
     * belly covers the block it stands on, so a perfectly successful nesting looked like a failure
     * until the player shoved the mother off it. Two blocks out to the side clears the hitbox; the
     * left flank is offered first and the right next, so a female with a wall on one side still lays.
     * Its own position stays last in the list rather than being dropped, because an egg somewhere
     * awkward beats an egg that never appears.
     */
    @Override
    protected @NotNull List<BlockPos> eggPlacementPositions() {
        Vec3 flank = Vec3.directionFromRotation(0.0F, this.yBodyRot + 90.0F).scale(2.0D);
        return List.of(
                BlockPos.containing(this.position().add(flank)),
                BlockPos.containing(this.position().subtract(flank)),
                this.blockPosition());
    }

    /**
     * Open beach with room to stand: sand or gravel underneath, two blocks of air, and sky overhead.
     *
     * <p>The substrate is the same pair {@link #checkNirasSpawnRules} accepts as a shore — that is
     * what a beach is made of, and without it a female would nest on whatever stone or grass she was
     * standing on, which puts nests inland in a mod whose premise for this animal is the waterline.
     *
     * <p><b>The other two conditions are why nesting worked only sometimes.</b> "Air above sand" is
     * true of a great many blocks that are not a beach: pockets inside the dune, the gap under an
     * overhang, the roof of a sea cave. They are legal spots for an egg and impossible ones for a
     * 3 × 1.6 animal, so {@code SeekNestSiteGoal} would pick one, fail to path into it, and the
     * laying goal would keep counting down over ground that never became legal — every time the
     * random draw happened to land on one. Two blocks of headroom is what the body needs, and
     * {@code canSeeSky} is what separates the beach from everything buried under it.
     */
    @Override
    protected boolean canPlaceEggAt(@NotNull Level level, @NotNull BlockPos pos) {
        return level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()
                && level.canSeeSky(pos)
                && (level.getBlockState(pos.below()).is(BlockTags.SAND)
                || level.getBlockState(pos.below()).is(Blocks.GRAVEL));
    }

    /**
     * Mates ashore only, on top of the sex and love rules it already inherits.
     *
     * <p>Checked at partner selection, which is where {@code BreedGoal} asks: a pair cannot pair off
     * in the water at all. Vanilla's {@code canContinueToUse} does not re-ask, so a couple that
     * courted on the sand and slid into the shallows in the sixty ticks before mating will still
     * mate — deliberately left alone, because the alternative is a courtship that silently aborts
     * when a wave pushes one of them.
     */
    @Override
    public boolean canMate(@NotNull Animal other) {
        return super.canMate(other) && !this.isInWater() && !other.isInWater();
    }

    /**
     * Whether the animal is <b>swimming</b> rather than standing on something, which is the question
     * every clip that comes in a land and a water version actually wants to ask.
     *
     * <p><b>{@code isInWater()} alone is the wrong test for a body this long</b>, and it is what put
     * the capsizing water death on an animal that died on the beach. It is true when ANY part of the
     * box touches water, and a three-block reptile hauled out on the sand almost always has its tail
     * in the sea. The two facts together separate the four cases that actually occur:
     *
     * <ul>
     *   <li>on the sand with a wet tail — in water, on ground, eyes dry → <b>land</b></li>
     *   <li>resting on the sea bed — eyes submerged → <b>water</b></li>
     *   <li>swimming at any depth — in water, nothing underfoot → <b>water</b></li>
     *   <li>inland — neither → <b>land</b></li>
     * </ul>
     *
     * <p><b>Not named {@code isSwimming}</b>: that already exists on {@code Entity} and means the
     * sprint-swim pose, which is a different question with a different answer.
     *
     * <p>Both halves are derivable on the client from synced position and pose, so this is safe in a
     * play condition — unlike a raw {@code getDeltaMovement()} read. The death variant is chosen once
     * server-side and named to the client, so it cannot disagree there at all.
     */
    private boolean isInSwimmingMedium() {
        return this.isUnderWater() || (this.isInWater() && !this.onGround());
    }

    /**
     * Nothing scripted is running, so locomotion may own the frame.
     *
     * <p><b>The sleep cycle is deliberately NOT excluded here</b>, and that is not an oversight — it
     * was tried, and it is what produced the jump out of the waking clip. What shows through a
     * finished {@code PLAY_ONCE} clip is not its last frame but the BIND pose, so the idle has to
     * keep running underneath to have something to fall back to; the sleep clips sit at priority 1
     * against locomotion's 3 and own the frame for as long as they play. Exclusion is by priority,
     * not by play condition — the same arrangement the Hell Hippo and the Tangoftero use, both of
     * which say so in this exact method.
     */
    private boolean canPlayLocomotion() {
        return !this.isPerformingAction() && !this.isDeadOrDying();
    }

    private StandardAnimation clip(String name, Supplier<Object> adult, Supplier<Object> baby,
                                   Loop loop, int priority, float seconds) {
        return new StandardAnimation(name,
                new AnimSource(() -> this.isBaby() ? baby.get() : adult.get()), loop, 0, priority, seconds);
    }

    /**
     * Same as {@link #clip}, but on layer 1 — the rig's second {@code keyframeBlend}, which composites
     * ADDITIVELY over layer 0 instead of replacing it.
     *
     * <p>Only for clips that author a subset of the skeleton on purpose. A layer-0 clip owns every
     * bone, including the ones it says nothing about, which it silently returns to the bind pose; that
     * is correct for a whole-body action and ruinous for an overlay. @see NirasNeckOverlay
     */
    private StandardAnimation overlayClip(String name, Supplier<Object> adult, Supplier<Object> baby,
                                          Loop loop, int priority, float seconds) {
        return new StandardAnimation(name,
                new AnimSource(() -> this.isBaby() ? baby.get() : adult.get()), loop, 1, priority, seconds);
    }

    private StandardAnimation adultClip(String name, Supplier<Object> adult, Loop loop, int priority, float seconds) {
        return new StandardAnimation(name, new AnimSource(adult), loop, 0, priority, seconds);
    }

    private StandardAnimation babyClip(String name, Supplier<Object> baby, Loop loop, int priority, float seconds) {
        return new StandardAnimation(name, new AnimSource(baby), loop, 0, priority, seconds);
    }

    // ───────────────────────────────────────────────────── SLEEP ─────

    /**
     * Nothing type-specific rouses it — an animal that sleeps in open water has nothing in particular
     * to fear. It still wakes to a player walking up, which is {@code SleepGoal}'s default and the
     * reason this does not implement {@code ISleepAwareness}.
     */
    @Override
    public @NotNull Set<EntityType<?>> getInterruptingEntityTypes() {
        return Set.of();
    }

    /**
     * Which medium the CURRENT sleep cycle is being played in, latched when the cycle starts.
     *
     * <p><b>Synced, and it has to be.</b> {@code sleep} is {@code REPEATING}, and the animator
     * auto-starts looping clips from their play conditions <em>on both sides</em>. A condition that
     * read {@link #isInWater()} directly would let an animal dozing right at the waterline run the
     * water clip on one side and the land clip on the other. Latching it to one synced value makes
     * both sides read the same fact.
     *
     * <p>The one-shot transitions do not need it — those the server starts by name and the client
     * receives by name — but they read it anyway so a cycle cannot change medium halfway through.
     *
     * <p>What gets latched is {@link #isInSwimmingMedium()}, not "is any part of me wet". A beached
     * animal with its tail in the sea is asleep on sand and should settle onto sand; reading
     * {@code isInWater()} here would have lain it down with the underwater clip, which is the same
     * mistake that put the capsizing death on a corpse in the dunes.
     */
    private static final EntityDataAccessor<Boolean> SLEEP_IN_WATER =
            SynchedEntityData.defineId(NirasmosaurusEntity.class, EntityDataSerializers.BOOLEAN);

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SLEEP_IN_WATER, false);
    }

    /** Whether this sleep is the underwater one. @see #SLEEP_IN_WATER */
    public boolean isSleepingInWater() {
        return this.entityData.get(SLEEP_IN_WATER);
    }

    /**
     * Starts the phase's clip, picking the medium's variant — and latching the medium on the way in.
     *
     * <p>The latch is set on the first phase of the cycle rather than every phase, so an animal that
     * settles on the shore and is lapped by the tide finishes the cycle it began instead of changing
     * pose mid-clip. {@code SleepPhase#SITTING_DOWN} is checked alongside {@code PREPARING_SLEEP}
     * because whichever of the two this rig grows first is the one the cycle opens on; today it has
     * no sitting clips, so it opens on the latter.
     */
    @Override
    public void onSleepPhaseBegin(@NotNull SleepPhase phase) {
        if (phase == SleepPhase.SITTING_DOWN || phase == SleepPhase.PREPARING_SLEEP) {
            this.entityData.set(SLEEP_IN_WATER, this.isInSwimmingMedium());
        }
        String clip = this.sleepClipName(phase);
        if (clip != null) {
            this.playIfRegistered(clip);
        }
    }

    /**
     * Phase length from the clip of the medium actually being slept in.
     *
     * <p>{@code SMOPAnimal} measures {@code phase.clipName()}, which here only ever names the land
     * variant — so without this a cycle in the water would run its transitions 9 and 14 ticks short
     * and cut both clips mid-motion.
     */
    @Override
    public int sleepPhaseDuration(@NotNull SleepPhase phase) {
        String clip = this.sleepClipName(phase);
        if (clip == null) {
            return 0;
        }
        int clipTicks = this.clipDurationTicks(clip);
        // Sitting keeps the base's rule — the clip decides whether the phase exists, the mob decides
        // how long it holds. Dead code on today's rig, which has no sitting clips, but leaving it out
        // would mean whoever authors them later gets a sit that lasts exactly one loop of the clip.
        if (phase == SleepPhase.SITTING) {
            return clipTicks > 0 ? this.getSittingDuration() : 0;
        }
        return clipTicks;
    }

    /**
     * The clip this phase plays in the medium currently latched, or {@code null} for a phase this
     * animal has no clips for.
     *
     * <p>Six registrations rather than one clip with a supplier that picks: {@code AnimSource} calls
     * its supplier on every render frame rather than once at the start, and a {@code BaseAnimation}
     * carries a single duration fixed at registration which the client reads from its own copy. One
     * registration therefore cannot be 3.55 s ashore and 4.0 s under water.
     */
    @Nullable
    private String sleepClipName(SleepPhase phase) {
        String base = phase.clipName();
        if (base == null) {
            return null;
        }
        return this.isSleepingInWater() ? base + "_water" : base;
    }

    // ───────────────────────────────────────────────────── SPAWN & BREEDING ─────

    /**
     * Two, against {@code Mob}'s default of four. The 1-2 written into the biome modifier is only the
     * size of ONE group: {@code NaturalSpawner#spawnCategoryForPosition} loops three times over
     * {@code groupCount}, re-rolling the spawner entry each pass, and the running {@code clusterSize}
     * is only cut off by this value. So a 1-2 entry with the default cap really produces up to four
     * animals per spawn event — two groups of two — which is what "packs of 1-2" was failing to
     * describe in play. Capping the cluster at two makes the biome modifier's numbers the truth.
     */
    @Override
    public int getMaxSpawnClusterSize() {
        return 2;
    }

    /**
     * In water, or hauled out on a shore it could plausibly have crawled onto. Straight from 1.20.1,
     * which accepted sand, grass, gravel and stone — the shoreline blocks — rather than the usual
     * "on any solid block" so it does not appear inland.
     *
     * <p>The shore half only became reachable once the placement type stopped being plain
     * {@code IN_WATER}: the placement runs first and rejected every dry position before this method
     * was ever called. It is now {@code SMOPSpawnPlacementTypes#IN_WATER_OR_ON_SHORE}, which admits
     * both kinds of block and leaves the choice of WHICH shore to this method — the placement only
     * knows "solid, with headroom", so without the block list below the animal would turn up on any
     * hillside the Y roll happened to land on.
     *
     * <p>Sand and gravel only. The 1.20.1 list also carried grass and stone, and that was never a
     * decision anyone tested — under the old {@code IN_WATER} placement this whole branch was
     * unreachable. Made live as written, it read "any island or slope inside an ocean or beach biome",
     * which is a very large amount of ground and was a real part of how the population ran away. These
     * two are what a shoreline is actually made of.
     */
    public static boolean checkNirasSpawnRules(EntityType<NirasmosaurusEntity> type, LevelAccessor level,
                                               EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        boolean inWater = level.getFluidState(pos).is(FluidTags.WATER)
                && level.getFluidState(pos.below()).is(FluidTags.WATER);
        boolean onShore = level.getBlockState(pos).isAir()
                && (level.getBlockState(pos.below()).is(BlockTags.SAND)
                || level.getBlockState(pos.below()).is(Blocks.GRAVEL));
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

    /**
     * Rolls the sex of a hatchling, which nothing else would.
     *
     * <p>{@code AbstractEggBlock} creates the baby and adds it to the world <b>without ever calling
     * {@code finalizeSpawn}</b> — reasonably, since nothing hatched is being placed by a spawner —
     * and {@link #finalizeSpawn} is the only other place the sex is decided. Without this hook every
     * animal born from an egg would keep the synched flag's {@code true} default and the whole
     * species would come out male one generation later, which is the same bug 1a fixed for naturally
     * spawned ones. The Kriftognathus rolls its coat and its sex here for exactly this reason.
     */
    @Override
    public void onEggBorn(@NotNull ServerLevel level, @NotNull BlockPos nestPos) {
        this.setMale(this.getRandom().nextBoolean());
    }

    // getBreedOffspring is deliberately NOT overridden. GenderedSMOPAnimal returns null for egg
    // layers, and this is one: GenericBreedGoal flips hasEgg, GenericLayEggGoal puts the egg on the
    // sea bed, and AbstractEggBlock creates the baby from the entity type when it hatches. The 1a
    // override that returned a live calf here was never reached — GenericBreedGoal passes a null
    // child to finalizeSpawnChildFromBreeding on purpose — and it claimed the opposite of what the
    // animal actually does.

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

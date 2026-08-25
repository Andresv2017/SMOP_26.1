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
 * <p><b>It extends {@code SMOPWaterAnimal}, the salmon's base, and only overrides what a reptile
 * does differently from a fish.</b> The beached-fish flop is an opt-out hook, not an obstacle. Three
 * things the base brings are easy to omit when rebuilding a water kit by hand and all three break the
 * animal: the water pathfinding malus (without which the navigator treats water as a hazard and it
 * swims nowhere), {@code isPushedByFluid}, and the SYNCED fast-swim flag — play conditions run on both
 * sides and {@code getDeltaMovement()} is not synced for mobs, so reading speed directly
 * desynchronises the clip.
 *
 * <p><b>The one genuine difference is the navigation, and it needs TWO.</b> Swimming the water column
 * requires a {@code WaterBoundPathNavigation}; walking requires a ground one; there is no single
 * navigator that does both. {@code AmphibiousPathNavigation} looks like the answer and is not — it is
 * built for turtles and axolotls, which travel along surfaces rather than through open water, and
 * with it this animal simply sat where it was spawned. The fix is vanilla's own, taken from the
 * Drowned: hold one of each and swap between them as the animal enters and leaves the water.
 */
public class NirasmosaurusEntity extends SMOPWaterAnimal implements SwimTilt, CustomEggBorn {

    /** Bait and breeding food. */
    private static final Ingredient FOOD_ITEMS = Ingredient.of(Items.COOKED_COD, Items.COOKED_SALMON);

    /** Above this speed (blocks/tick) the sprint clip takes over from the cruise clip. */
    private static final double SWIM_SPRINT_THRESHOLD = 0.105D;

    /** How far the calf may drift from its mother, against vanilla's three. */
    private static final double FOLLOW_PARENT_DISTANCE = 6.0D;

    /**
     * What a goal's speed modifier is multiplied by once the animal is walking rather than swimming.
     *
     * <p>Applied in the land navigator, so goals never have to know which medium they are in.
     */
    private static final double LAND_SPEED_SCALE = 0.25D;

    /**
     * What it hunts.
     *
     * <p>Five things that share the beach and the warm ocean with it. <b>The dolphin is deliberately
     * absent</b> — it is the one thing down there that fights back, and the first read of this animal
     * should not be "it kills dolphins". The drowned is out for the opposite reason: it is a
     * {@code Monster}, and eating it would make this a de facto ally of the player.
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
     * A {@code TargetingConditions.Selector} rather than a {@code Predicate}: the selector is handed
     * the level as well as the candidate.
     */
    private static final TargetingConditions.Selector PREY_SELECTOR =
            (target, level) -> isPrey(target);

    /**
     * Who the nest guard will mark: prey plus the player. {@link EggGoalRegistry} wants a plain
     * {@code Predicate}, hence the second form of the same test — and the base goal already excludes
     * the mob itself, so a mother never mauls the father over the clutch.
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
     * <p><b>The land bite is trimmed to 1.1 from its authored 1.2.</b> Every channel but
     * {@code gNeck} and {@code gHead} has landed on its rest value by 1.1, and those two are easing
     * back to the pose the idle starts from, which is what a 250 ms blend-out is for.
     *
     * <p><b>The water bite keeps all 1.2, and trimming it would be a bug.</b> It is an ADDITIVE
     * overlay (layer 1), so it has to run to the frame its channels reach neutral or the blend-out
     * inherits whatever offset is left. {@code gHead} still carries 22.5 degrees of roll at 0.9 and
     * only reaches zero at 1.2, so cutting it creates the pop that cutting is meant to remove.
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
     * <p><b>The gate that decides whether to swing must never be tighter than the damage it gates,
     * nor wider than the box that applies it.</b> Both shapes below reach 4.0 from centre, so this
     * sits just inside them.
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
     * <p><b>This number is for the WATER.</b> Every speed there runs through
     * {@code SwimSteerControl}'s {@code speedScale} of 0.01 as a flat multiplier, so all that survives
     * is the RATIO to the wandering goals, which patrol at 1.0. Measured: this closes 14 blocks in
     * five and a half seconds, against a player who swims at 2.2.
     *
     * <p><b>And it is NOT shared with the land chase.</b> The per-medium conversion factors differ by
     * twenty-five to one, so one modifier is the same MULTIPLE of a stroll in both media — and three
     * and a half times a stroll reads as a lunge in water and as a sprint on a beach.
     * @see #LAND_CHASE_SPEED
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
     * How far ashore a quarry may be and still be worth beaching for.
     *
     * <p>Under {@code FOLLOW_RANGE}'s 28 on purpose: hauling out is a commitment — the animal leaves
     * its own element and spends the trip unable to fight back — and it should be made for something
     * standing at the water's edge, not for a player who has walked halfway up the beach.
     */
    private static final double HAUL_OUT_RANGE = 16.0D;

    /** Both navigators, built once; {@link #updateSwimming()} swaps between them. */
    private final SmartSwimmingNavigation waterNavigation;
    private final GroundPathNavigation groundNavigation;
    /**
     * Water steer, replacing the {@code SmoothSwimmingMoveControl} the aquatic base installs — see
     * {@link SwimSteerControl} for what that one does wrong on a body this long. 2.2 degrees a tick
     * against vanilla's 10, ramping in and out rather than switching on, so a reversal costs two ramps
     * plus the arc between them.
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
        // 40, because a leg longer than this cannot be pathed at all. PathNavigation caps a search at
        // max(FOLLOW_RANGE, requiredPathLength) and PathFinder drops every node past that cap — then
        // fails QUIETLY, reconstructing a closest-approach path, so SwimWanderGoal's 22-36 block legs
        // silently become stubs. This covers the longest leg with margin, and the setter also grows
        // the visited-node budget.
        this.waterNavigation.setRequiredPathLength(40.0F);
        // Every speed handed to the land navigator is scaled down here, in ONE place, and the reason
        // is the attribute: MOVEMENT_SPEED is 1.0 because the water control multiplies it by its own
        // 0.01 (see createAttributes). The land control does no such thing — DirectionalMoveControl
        // does setSpeed(modifier x attribute) flat — so the same modifier means roughly a hundred
        // times more speed ashore than afloat.
        //
        // Scaling at the navigator rather than per goal: a goal that runs in BOTH media can only
        // state one number, and 1.15 ashore is four and a half times a stroll.
        //
        // moveTo(Path, double) is the single funnel: the position and entity overloads both build a
        // path and delegate to it.
        this.groundNavigation = new GroundPathNavigation(this, level) {
            @Override
            public boolean moveTo(@Nullable Path path, double speed) {
                // A chase ashore runs at its OWN number, not at the water one.
                //
                // Everything else speaks one unit and is converted by LAND_SPEED_SCALE. The chase is
                // the exception: the per-medium scales differ by twenty-five to one, so a modifier
                // tuned until the water hunt looked like a hunt comes out ashore as a sprint.
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
                // this gain sets what FRACTION of the motion is vertical rather than adding to it — at
                // 6 the animal descends faster than it swims forward, diving past 55 degrees.
                .verticalGain(2.0F)
                // 30 while fighting, against 2.2 cruising, and a chase does not work without it: at
                // 2.2 a ninety-degree correction takes 41 ticks plus two ramps, and turningSpeedFactor
                // cuts the forward drive to as little as 0.35 throughout — the harder it needs to
                // turn, the slower it swims, and the bite fires pointing where the prey used to be.
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
        // Left costed, the animal treats its own entry and exit from the water as an obstacle worth
        // walking around: measured ashore, six blocks from a player it was chasing, with the ground
        // navigator returning path=null and every water path one node long with canReach=false.
        this.setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
    }

    /**
     * {@code createAnimalAttributes} and not {@code createLivingAttributes}: {@code TemptGoal} reads
     * {@code Attributes.TEMPT_RANGE}, which only the animal supplier defines. Getting this wrong
     * crashes on spawn rather than at load.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
                // A cow has 10, and so does the one-block Tangoftero. An apex predator that a stone
                // sword kills in four swings is not one.
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.FOLLOW_RANGE, 28.0D)
                // The water control multiplies this attribute by its in-water modifier of 0.02, so a
                // value of 0.20 comes out as an effective 0.004 — a tenth of a block per second, which
                // reads as floating in place. The salmon swims on 0.6.
                .add(Attributes.MOVEMENT_SPEED, 1.0D)
                .add(Attributes.ATTACK_SPEED, 0.4D)
                // Irrelevant here and left at zero deliberately: the attribute feeds vanilla's
                // doHurtTarget, and nothing on this animal goes through it — the bite's knockback is
                // the HitWindow's own, see registerAnimations.
                .add(Attributes.ATTACK_KNOCKBACK, 0.0D)
                // 4 and not more: the heavier moves have to hit harder than a loose snap, and a base
                // bite already worth three hearts leaves them nowhere to grow. The HitWindow reads
                // this attribute rather than carrying a literal, so this is the place to tune it.
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
        // It ends the instant the animal is on land, and the melee chase takes the last few blocks.
        //
        // NOT picksItsOwnFights: that also excludes a tamed animal, and this has to run for
        // retaliation — one that was hit should follow its attacker out whoever it belongs to.
        this.goalSelector.addGoal(2, new HaulOutGoal(this, HAUL_OUT_RANGE,
                () -> !this.isBaby() && !this.isInSleepCycle()));

        // The bite. The goal decides only WHEN to commit; the damage lives in each clip's HitWindow
        // (see registerAnimations) so it lands on the frames the jaws close, instead of on a counter
        // running beside the animation, where the hit and the visible bite drift apart.
        //
        // NOT a tie with the breed goal: WrappedGoal#canBeReplacedBy only yields a flag on a strict
        // `<`, so two goals at the same priority can never take MOVE back off each other once running.
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
        // Both attack flags are true: an animal that fixes on an intruder and then does nothing reads
        // as broken rather than placid.
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
        // Probability 0.10, five times vanilla's: at 0.02 the head sits pinned dead-centre for
        // stretches of fifty ticks, and at 0.35 it is demanded so constantly that it welds against
        // getMaxHeadYRot instead. A head that rests between glances is the point.
        //
        // Range 8, not 12: further out the player is usually off to one side of a moving animal, the
        // demanded angle exceeds getMaxHeadYRot, and the head just parks against its stop.
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 8.0F, 0.10F));
        this.goalSelector.addGoal(12, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            /** A sleeping animal does not wake into a fight: {@code SleepGoal} owns when it wakes. */
            @Override
            public boolean canUse() {
                return !NirasmosaurusEntity.this.isInSleepCycle() && super.canUse();
            }
        });
        // No setAlertOthers(), unlike the hippo: that one alerts its family because it lives in one.
        // This animal has no pod.
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
     * <p>Calves never start anything, and neither does a sleeping or a tamed animal.
     */
    private boolean picksItsOwnFights() {
        return !this.isBaby() && !this.isInSleepCycle() && !this.isTame();
    }

    /**
     * The bite goal, named rather than anonymous for one reason: the builder methods return the goal,
     * so an anonymous body cannot hang off the end of the chain.
     *
     * <p><b>The calf neither bites nor chases.</b> {@code attackCondition(!isBaby())} at the call
     * site only reaches as far as {@code checkAndPerformAttack}, so it silences the bite but not the
     * goal — and no target goal checks age, so a calf would run the whole chase to biting range and
     * never bite, which reads as broken rather than as "calves do not fight".
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
     * <p>Chosen against the rig: {@code NirasmosaurusModel}'s {@code lookAt} clamps the neck to 35
     * degrees of yaw, so demand past that produces no visual change at all — it only holds the head
     * against its stop. Forty leaves a little headroom over what the neck can express, and nothing
     * beyond it.
     */
    @Override
    public int getMaxHeadYRot() {
        return 40;
    }

    /**
     * Degrees of head yaw per tick, down from vanilla's ten.
     *
     * <p><b>This is the head snap.</b> Ten degrees a tick is two hundred a second against a body
     * turning at 2.2, so the neck angle the rig draws, {@code yHeadRot - yBodyRot}, can move twelve
     * degrees in one tick while the animal glides: a flinch, not a glance.
     *
     * <p>Three keeps the head marginally faster than the body, which reads as the animal choosing to
     * turn its head rather than being dragged along. Both edges of a glance honour it, which is why
     * {@link net.darkblade.smop.entity.ai.control.SmoothSwimLookControl} exists — vanilla only
     * honours the first.
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

    /** Vertical speed below which the body stays level. */
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
            // Sleep is here for a reason that is not obvious: a sleeping animal is not still. Its
            // input is cut, so it SINKS, with no horizontal component at all — and the trajectory
            // angle of that is straight down. Without this it would descend to the bed nose-down at
            // full deflection while playing a resting pose authored horizontal.
            this.swimPitch = Mth.lerp(0.1F, this.swimPitch, 0.0F);
            this.swimRoll = Mth.lerp(0.1F, this.swimRoll, 0.0F);
            return;
        }

        boolean client = this.level().isClientSide();
        float vertical = client ? (float) (this.getY() - this.yo) : (float) this.getDeltaMovement().y;
        double horizontal = client
                ? Math.hypot(this.getX() - this.xo, this.getZ() - this.zo)
                : this.getDeltaMovement().horizontalDistance();

        // A SOFT knee, and the threshold has to clear the buoyancy trim: the swim control adds 0.005 a
        // tick and travel() takes 0.005 back off with no target, so an idling swimmer always reads
        // about -0.005 vertically and a lower threshold holds the body permanently nose-down.
        //
        // Subtracting the threshold rather than zeroing below it matters just as much: zeroing makes
        // the drawn angle JUMP the tick it is crossed, appearing out of nothing.
        vertical = Math.signum(vertical) * Math.max(0.0F, Math.abs(vertical) - TILT_DEAD_ZONE);

        // The trajectory angle, not a gain on the vertical speed: a gain is only right for one range
        // of speeds. atan2 needs no constant and cannot be wrong about the angle — if it saturates,
        // the SWIMMING is too steep and verticalGain is the number to look at, not this.
        float targetPitch = Mth.clamp(
                (float) Math.toDegrees(Mth.atan2(vertical, Math.max(horizontal, 1.0E-4D))),
                -MAX_SWIM_PITCH, MAX_SWIM_PITCH);
        // ONE smoothing stage. Filtering the vertical speed AND the angle puts two lags in series,
        // which leaves the body nose-up while it has already been sinking for a second. The angle is
        // what gets drawn, so the angle is the only thing worth filtering.
        this.swimPitch = Mth.lerp(TILT_SMOOTHING, this.swimPitch, targetPitch);

        // Bank out of the yaw RATE, not the yaw: a steady heading must not hold the animal leaning.
        float yawDelta = Mth.wrapDegrees(this.getYRot() - this.yRotO);
        if (Math.abs(yawDelta) < 0.3F) {
            yawDelta = 0.0F;
        }
        // No speed, no bank — an animal turning on the spot is not carving a curve.
        float speedFactor = (float) Math.min(1.0D, horizontal * 6.0D);
        // 12 rather than the flier's 4: the swim turn cap is 2.2 degrees a tick, so the same gain
        // would leave the bank invisible. Ramping it in and out is what turns a lean into a roll.
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
     * attribute by its own modifiers — fifty times down in water, ten times down outside it — so
     * leaving it installed on land turns an attribute of 1.0 into an effective 0.06, the crawl up the
     * beach. A plain {@link MoveControl} passes the speed through untouched.
     *
     * <p>Driven from {@link #tick()} rather than from {@code updateSwimming}, which is not guaranteed
     * to run every tick for this class.
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
        // comes round to its own recalculation. Measured, that leaves a chase oscillating between
        // "ashore, path=null" and "in water, path=1 node, canReach=false" indefinitely, never covering
        // the last few blocks — the route dies in the handover, not in either navigator.
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
     * Exactly one locomotion clip holds at any moment: in water or not, moving or not.
     *
     * <p><b>Why {@code swim} is registered twice</b> instead of going through {@link #clip}: that
     * helper takes ONE duration for both ages, and here the adult clip is authored at 1.9 s against
     * the calf's 5.0 — a factor of 2.6. A shared number leaves one age cut short and the other frozen
     * in a held pose for seconds.
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

        // The sleep cycle twice over: three phases ashore and the same three under water, each under
        // its own name, with onSleepPhaseBegin picking the pair.
        //
        // Priority 1, under the one-shots and above locomotion, so the idle and the swim cycle cannot
        // compete for a sleeping animal's frame.
        //
        // preparing_sleep is registered at 3.15 and NOT the 3.55 the clip declares: both land clips
        // stop keyframing at 3.15, and since a phase lasts exactly as long as its registered clip,
        // the remaining 0.4 s would be the animal held on its last frame. Look for that gap between
        // `withLength` and the last keyframe — nothing else checks it. The water pair has none.
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

        // The land bite REPLACES: layer 0, priority 0, winning the frame over locomotion, which keeps
        // running underneath — a finished PLAY_ONCE clip falls back to the BIND pose and not to its
        // own last frame, so the cycle has to be there to catch it.
        StandardAnimation bite = clip(ANIM_BITE,
                () -> NirasLandAnimations.lbite, () -> NirasBabyLandAnimations.l_bite,
                Loop.PLAY_ONCE, 0, LAND_BITE_SECONDS);

        // The water bite OVERLAYS: layer 1, and cut down to the gNeck subtree.
        //
        // On layer 0 it would replace the swim cycle outright — a same-layer clip drops every bone it
        // does not author back to the bind pose — leaving the animal striking with its neck and dead
        // still from the chest back. On layer 1 the rig composites it additively, so the flippers and
        // tail keep swimming through the bite.
        //
        // The clip therefore must not author the body, or the two sources fight over the tail — hence
        // NirasNeckOverlay, which keeps the neck, head and jaws and drops the rest.
        //
        // The overlay composites over whatever locomotion clip is CURRENT, which is deliberate: the
        // body carries on doing whatever it was doing.
        StandardAnimation waterBite = overlayClip(ANIM_WATER_BITE,
                () -> NirasNeckOverlay.WATER_BITE, () -> NirasNeckOverlay.BABY_WATER_BITE,
                Loop.PLAY_ONCE, 0, WATER_BITE_SECONDS);

        // Two deaths, by medium: drowning on the shore and sinking in open water are different
        // silhouettes, and the export has a clip for each.
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
        // Chasing something, NOT swimming quickly. The base's isSwimmingFast() is a speed threshold,
        // and a cruising Nirasmosaurus is already past any sensible cut, so the sprint clip becomes
        // the only one ever seen. Sprinting should read as intent, so it keys off having prey.
        //
        // isAggressive() and NOT getTarget(): the target is not synced to clients and play conditions
        // run on both sides, so reading it directly puts the two on different clips. Aggression rides
        // the synced flag byte, which MeleeAttackGoal raises in start() and clears in stop().
        swimAdult.setPlayCondition(a -> this.canPlayLocomotion() && this.isInSwimmingMedium() && this.isMoving()
                && !this.isAggressive() && !this.isBaby());
        swimBaby.setPlayCondition(a -> this.canPlayLocomotion() && this.isInSwimmingMedium() && this.isMoving()
                && !this.isAggressive() && this.isBaby());
        waterSprint.setPlayCondition(a -> this.canPlayLocomotion() && this.isInSwimmingMedium() && this.isMoving()
                && this.isAggressive());

        // Spelled out rather than left on the rig's 220 ms default. The seam that matters is the last
        // one: the waking clip ends on exactly the pose the idle starts from, verified channel by
        // channel, so the only thing that can show there is the cross-fade itself.
        preparingSleep.blendInMs(300).blendOutMs(250);
        preparingSleepWater.blendInMs(300).blendOutMs(250);
        sleep.blendInMs(300).blendOutMs(300);
        sleepWater.blendInMs(300).blendOutMs(300);
        awakening.blendInMs(250).blendOutMs(350);
        awakeningWater.blendInMs(250).blendOutMs(350);

        // The one-shots are NOT started by these conditions — the animator's auto-start loop only
        // starts REPEATING clips, so SleepGoal starts them through onSleepPhaseBegin. The conditions
        // buy the reverse: a clip whose condition goes false is CUT, which is what makes a wake-up
        // mid-transition end the transition instead of playing it out.
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
        // hitThisSwing / lastSweepAngle state and cannot be shared between two clips.
        //
        // The land bite: a frontal cuboid across the frames the jaws shut. box3d and NOT box —
        // AttackShape's interface note says box ignores the Y axis outright, which on a beach means
        // biting a player standing on an overhang two blocks up.
        //
        // Anchor 2.0 forward and 0.9 up, box reaching another 2.0: the muzzle tip sits 3.5 blocks
        // ahead (see ATTACK_REACH), so the volume brackets it rather than starting at it. All six are
        // look numbers — tune them against the render with /deluxelib debug hitboxes.
        HitWindow.of(BITE_WINDOW_START, BITE_WINDOW_END)
                .shape(AttackShape.box3d(2.0F, 1.1F, 1.2F))
                .anchor(AttackAnchor.of(2.0F, 0.0F, 0.9F))
                .damage((float) this.getAttributeValue(Attributes.ATTACK_DAMAGE))
                // Low on purpose: the heavier moves need prey that stays in the jaws, not prey that
                // gets launched.
                .knockback(0.2F)
                .filter(target -> target == this.getTarget() || !(target instanceof NirasmosaurusEntity))
                // Diagnostic only, and silent unless /smop debug bite watch is running.
                .onSweep((attacker, origin, facing, shape, hits) ->
                        SMOPBiteDebug.reportSweep(attacker, origin, facing, hits.size()))
                .applyTo(bite);

        // The water bite is aimed in 3D where the land one is flattened to the ground plane.
        // Underwater a target is as often above or below as beside, and a flattened box would leave a
        // Nirasmosaurus under a swimming player snapping at nothing for as long as the player stayed
        // there — the goal measures range in 3D and would keep firing.
        //
        // ── IT AIMS WITH THE HEAD, and aimAlongLook() would be wrong here. ──
        //
        // That helper is getViewVector(1.0F), which reads getXRot() and getYRot() — and on a mob
        // getYRot() is the BODY yaw, written by SwimSteerControl at 2.2 degrees a tick. What tracks
        // the target is yHeadRot, written by the LookControl that MeleeAttackGoal points at the prey
        // every tick. Aim off the body and the volume follows the lagging part of the animal, so the
        // bite lands nowhere near its target. The pitch in that view vector IS clean — the swim
        // control never writes xRot — which is what makes the fault easy to miss.
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
        // Known limit: the rig bends the neck at most 35 degrees of yaw and 30 of pitch, so past that
        // the volume goes where the head is AIMED and the model can only lean part of the way.
        HitWindow.of(BITE_WINDOW_START, BITE_WINDOW_END)
                .shape(AttackShape.box3d(3.2F, 1.0F, 1.0F))
                .anchor(AttackAnchor.look(0.6F, 0.0F, -0.5F))
                .facing(e -> Vec3.directionFromRotation(e.getXRot(), e.getYHeadRot()))
                .damage((float) this.getAttributeValue(Attributes.ATTACK_DAMAGE))
                .knockback(0.2F)
                // Same escape hatch as the land bite: the blanket exclusion spares bystanders, but
                // without `target == getTarget()` it also vetoes the one it is fighting — in the
                // broad-phase predicate, before the shape test runs, so it reads as a geometry miss
                // that no amount of retuning fixes.
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

    /** It comes out of the sea to nest, like a turtle. */
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
     * <p><b>{@code isInWater()} alone is the wrong test for a body this long.</b> It is true when ANY
     * part of the box touches water, and a three-block reptile hauled out on the sand almost always
     * has its tail in the sea — which is what puts the capsizing water death on a beached animal. The
     * two facts together separate the four cases that occur:
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
     * play condition — unlike a raw {@code getDeltaMovement()} read.
     */
    private boolean isInSwimmingMedium() {
        return this.isUnderWater() || (this.isInWater() && !this.onGround());
    }

    /**
     * Nothing scripted is running, so locomotion may own the frame.
     *
     * <p><b>The sleep cycle is deliberately NOT excluded here.</b> Excluding it is what produces the
     * jump out of the waking clip: a finished {@code PLAY_ONCE} shows the BIND pose, not its own last
     * frame, so the idle has to keep running underneath to have something to fall back to. The sleep
     * clips sit at priority 1 against locomotion's 3 and own the frame while they play — exclusion is
     * by priority, not by play condition.
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
     * bone, including the ones it says nothing about, which it silently returns to the bind pose —
     * correct for a whole-body action and ruinous for an overlay.
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
     * to fear. It still wakes to a player walking up: every mob does.
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
     * <p>The one-shot transitions do not need it — the server starts those by name — but they read it
     * anyway so a cycle cannot change medium halfway through.
     *
     * <p>What gets latched is {@link #isInSwimmingMedium()} and not "is any part of me wet": a beached
     * animal with its tail in the sea is asleep on sand and should settle onto sand.
     */
    private static final EntityDataAccessor<Boolean> SLEEP_IN_WATER =
            SynchedEntityData.defineId(NirasmosaurusEntity.class, EntityDataSerializers.BOOLEAN);

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SLEEP_IN_WATER, false);
    }

    /** Whether this sleep is the underwater one. */
    public boolean isSleepingInWater() {
        return this.entityData.get(SLEEP_IN_WATER);
    }

    /**
     * Starts the phase's clip, picking the medium's variant — and latching the medium on the way in.
     *
     * <p>The latch is set on the FIRST phase of the cycle and not on every phase, so an animal that
     * settles on the shore and is lapped by the tide finishes the cycle it began instead of changing
     * pose mid-clip. Both openers are checked because whichever this rig grows first is the one the
     * cycle opens on.
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
        // Sitting keeps the base's rule: the clip decides whether the phase exists, the mob decides how
        // long it holds. Unused until this rig grows sitting clips, and leaving it out would give
        // whoever authors them a sit lasting exactly one loop.
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
     * Two, against {@code Mob}'s default of four, and this is what makes the biome modifier's pack
     * size true. The 1-2 written there is the size of ONE group:
     * {@code NaturalSpawner#spawnCategoryForPosition} loops three times over {@code groupCount},
     * re-rolling the entry each pass, and only this value cuts off the running {@code clusterSize} —
     * so a 1-2 entry with the default cap really produces up to four animals per spawn event.
     */
    @Override
    public int getMaxSpawnClusterSize() {
        return 2;
    }

    /**
     * In water, or hauled out on a shore it could plausibly have crawled onto — a block list rather
     * than the usual "on any solid block", so it does not appear inland.
     *
     * <p>The shore half only became reachable once the placement type stopped being plain
     * {@code IN_WATER}: the placement runs first and rejected every dry position before this method
     * was ever called. It is now {@code SMOPSpawnPlacementTypes#IN_WATER_OR_ON_SHORE}, which admits
     * both kinds of block and leaves the choice of WHICH shore to this method — the placement only
     * knows "solid, with headroom", so without the block list below the animal would turn up on any
     * hillside the Y roll happened to land on.
     *
     * <p>Sand and gravel only. Adding grass and stone reads as "any island or slope inside an ocean or
     * beach biome", which is a very large amount of ground and was a real part of how the population
     * ran away. These two are what a shoreline is actually made of.
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
     * Rolls the sex. {@code GenderedSMOPAnimal} leaves this to each concrete mob, because an animal
     * hatched from an egg and one placed by the world generator take different paths into existence.
     * Omit it and the synced flag keeps its {@code true} default, so the whole species comes out male.
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
     * {@code finalizeSpawn}</b>, which is the only other place the sex is decided. Without this hook
     * everything born from an egg keeps the synced flag's {@code true} default.
     */
    @Override
    public void onEggBorn(@NotNull ServerLevel level, @NotNull BlockPos nestPos) {
        this.setMale(this.getRandom().nextBoolean());
    }

    // getBreedOffspring is deliberately NOT overridden. GenderedSMOPAnimal returns null for egg
    // layers, and this is one: GenericBreedGoal flips hasEgg, GenericLayEggGoal puts the egg on the
    // sea bed, and AbstractEggBlock creates the baby from the entity type when it hatches. An override
    // returning a live calf would never even be reached — GenericBreedGoal passes a null child to
    // finalizeSpawnChildFromBreeding on purpose.

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

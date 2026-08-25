package net.darkblade.smop.entity.hellhippo;

import net.darkblade.deluxelib.anim.AnimSource;
import net.darkblade.deluxelib.combat.AttackShape;
import net.darkblade.deluxelib.combat.HitWindow;
import net.darkblade.deluxelib.anim.Loop;
import net.darkblade.deluxelib.anim.StandardAnimation;
import net.darkblade.deluxelib.entity.ai.goal.AnimatableMeleeAttackGoal;
import net.darkblade.deluxelib.entity.ai.pathing.DirectionalMoveControl;
import net.darkblade.deluxelib.entity.ai.rotation.SmoothBodyRotationControl;
import net.darkblade.smop.client.hellhippo.HellHippoAnimations;
import net.darkblade.smop.client.hellhippo.HellHippoBabyAnimations;
import net.darkblade.smop.entity.GenderedSMOPAnimal;
import net.darkblade.smop.entity.SMOPEntities;
import net.darkblade.smop.entity.ai.goal.IdleAnimationGoal;
import net.darkblade.smop.entity.ai.goal.SMOPFollowParentGoal;
import net.darkblade.smop.entity.ai.goal.SMOPRandomStrollGoal;
import net.darkblade.smop.effect.SMOPEffects;
import net.darkblade.smop.entity.RiderControllable;
import net.darkblade.smop.entity.rider.RiderAbility;
import net.darkblade.smop.entity.rider.RiderSteering;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.Vec2;
import net.darkblade.smop.entity.sleep.ISleepThreatEvaluator;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.darkblade.smop.entity.ai.goal.LeaveWaterShakeGoal;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.darkblade.smop.entity.ai.navigation.SeabedPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Supplier;

/**
 * The Hell Hippo: a heavy, solitary amphibious brute that can be befriended, saddled and ridden.
 *
 * <p><b>Port status — phase 1 of the port spec, complete.</b> The animal itself: geometry,
 * attributes, locomotion, the sleep cycle, breeding, its idle gesture, combat, and its life in the
 * water (amphibious navigation, the riverbed walk, the shake on the way out and the algae). The
 * trust-and-saddle chain is phase 2, and the rider systems (steering, intimidation pulse, mounted
 * attack) are phase 3.
 *
 * <p><b>Solitary, not a herd.</b> 1.20.1 had these forming leader-following packs, and the port
 * carried that over before it was dropped: the leader was whichever member a spatial query returned
 * first, and the tiebreak for who got to elect one was the lowest entity id <em>within each member's
 * own neighbourhood</em> — so members at opposite edges of a loose group saw different neighbourhoods
 * and elected different leaders, splitting the herd they existed to hold together. They now spawn
 * alone, except a cow who may bring a calf; see {@link #finalizeSpawn}.
 *
 * <p><b>Why it does not extend {@code AbstractChestedHorse}</b>, as the 1.20.1 version did: that
 * class descends from {@code Animal} while {@link net.darkblade.smop.entity.SMOPAnimal} descends
 * from {@link TamableAnimal}, so they are sibling branches and only one is reachable. Taking the
 * horse meant reimplementing sleep, gender and animation by hand inside the mob — which is exactly
 * why the legacy file grew to 1461 lines in a single class. Inheriting from SMOP's base instead gets
 * all of that for free, at the cost of writing the saddle, the steering and the chest, which phase 2
 * covers. The Nirasmosaurus is rideable too, so that work is used twice.
 */
public class HellHippoEntity extends GenderedSMOPAnimal
        implements ISleepThreatEvaluator, HasCustomInventoryScreen, RiderControllable {

    /** Tempts and breeds. Carrot and beef, as in 1.20.1. */
    private static final Ingredient FOOD_ITEMS = Ingredient.of(Items.CARROT, Items.BEEF);

    /**
     * One in this many hand-fed offerings wins the animal over. Straight from 1.20.1, where the line
     * was {@code this.random.nextInt(3) == 0}.
     *
     * <p><b>A roll, not a tally</b>, and that distinction is worth stating because the port spec's
     * phase 2a claims all three of the mod's taming rituals count attempts toward a target. Two of
     * them do — the Kriftognathus and the Nirasmosaurus, which is what {@code TameProgress} was
     * extracted for. This one does not: every piece of beef is an independent throw of the dice, so
     * the cost has no ceiling. A run of bad luck is part of the design.
     */
    private static final int TRUST_CHANCE_DENOMINATOR = 3;

    /**
     * The stare-down, as three clips rather than one. @see #registerAnimations()
     *
     * <p>The middle one is the only one that repeats; the other two are the entry and the release,
     * and repeating either of those is exactly what made the single-clip version look broken.
     */
    private static final String ANIM_INTIMIDATE_IN = "intimidate_in";
    private static final String ANIM_INTIMIDATE_LOOP = "intimidate_loop";
    private static final String ANIM_INTIMIDATE_OUT = "intimidate_out";

    /** How long a newly trusted hippo gives the player to produce a saddle. @see #tickIntimidation() */
    private static final int INTIMIDATION_TICKS = 300;
    /** It only starts sizing up someone this close, with a clear line to them. */
    private static final double INTIMIDATION_RADIUS = 10.0D;
    /** How square-on the player has to be looking for it to count as staring it down. */
    private static final double STARE_DOT = 0.95D;
    /** Ticks of unbroken staring that earn a face full of {@code smop:fear}. */
    private static final int STARE_TICKS_TO_FEAR = 100;
    private static final int FEAR_DURATION_TICKS = 300;

    /** Set while it is sizing the player up. Synced — the clip's play condition reads it. */
    private static final EntityDataAccessor<Boolean> INTIMIDATING =
            SynchedEntityData.defineId(HellHippoEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * Whether it is carrying panniers. Synced because the renderer picks the coat from it.
     *
     * <p>Not equipment, unlike the saddle and the armour: 26.1 has slots for those two and none for a
     * chest, so this stays a flag of the mob's own — the same shape 1.20.1 used, minus its
     * {@code ChestedHorse} inheritance.
     */
    private static final EntityDataAccessor<Boolean> CHEST =
            SynchedEntityData.defineId(HellHippoEntity.class, EntityDataSerializers.BOOLEAN);

    /** Counts down from {@link #INTIMIDATION_TICKS}. Server-only. */
    private int intimidationTicks;
    /** Consecutive ticks the trusted player has held its gaze. Server-only. */
    private int staringTicks;

    /**
     * Which kind of posturing is running: the pre-saddle standoff, where the clock running out costs
     * the player its trust, or the rider's fear pulse, where it is only for show. Server-only, since
     * the clips do not care which it is.
     */
    private boolean standoff;

    /**
     * What a wild hippo hunts, straight from 1.20.1. A {@code TargetingConditions.Selector} rather
     * than a {@code Predicate}: 26.1 hands the selector the level as well as the candidate.
     */
    private static final TargetingConditions.Selector PREY_SELECTOR = (target, level) ->
            target.getType() == EntityType.SHEEP
                    || target.getType() == EntityType.GOAT
                    || target.getType() == EntityType.COW;

    /**
     * How long the lunge is given, and the ticks of it the bite is live for.
     *
     * <p><b>Deliberately shorter than the clip's own {@code withLength(1.0F)}.</b> Every one of its
     * 23 channels settles back to zero and then has no further keyframes: the last one lands at 0.9,
     * and 20 of the 23 are already sitting at neutral by 0.8. Declaring the full second meant the
     * animal held a dead neutral pose for the remainder before locomotion could take the frame back —
     * which is the "it goes stiff after biting" that this fixes. The three trailing channels (tail
     * and a foreleg) lose the tail of their settle, and the rig's 220 ms crossfade covers it.
     *
     * <p>Trimming here rather than re-authoring keeps the keyframes untouched. If the bite should
     * instead finish on a combat stance rather than passing through neutral at all, that is an
     * animation change, not a number.
     */
    private static final float ATTACK_SECONDS = 0.7F;
    private static final int ATTACK_WINDOW_START = 7;
    private static final int ATTACK_WINDOW_END = 12;

    /** Within this, the body keeps turning to face the target. @see #faceCombatTarget() */
    private static final float FACE_LOCK_RADIUS = 6.0F;

    /** How often a spawning cow brings a calf along. @see #finalizeSpawn */
    private static final float CALF_COMPANION_CHANCE = 0.50F;

    /**
     * How close a calf gets to its mother before it stops following, centre to centre.
     *
     * <p>Vanilla's {@code FollowParentGoal} uses 3, which is a number sized for a cow. On this animal
     * the boxes already touch at 1.875 (2.5 wide, halved to 1.25 for the calf by
     * {@code getAgeScale}), so 3 leaves about a block of daylight and the models — which hang well
     * outside their boxes on this rig — visibly overlap. 5 puts roughly three blocks between the two
     * bodies, which reads as a calf keeping station rather than one wearing its mother.
     *
     * <p>A look number: adjust it against the render, not by arguing about it.
     */
    private static final double FOLLOW_PARENT_DISTANCE = 5.0D;

    /** The lunge. Shared by the melee goal and by the rider's R. @see #registerAnimations() */
    private static final String ANIM_ATTACK = "attack";

    /** The idle gesture: a full-body shake. @see #registerGoals() */
    private static final String ANIM_SHAKE = "shake";
    /**
     * Grazing, as a second resting gesture. 1.20.1 authored this clip and drove it from its own
     * {@code eatAnimationState}, which nothing in the port had taken over, so it sat unused. It is
     * cosmetic — no food is consumed and nothing is healed — which is exactly what
     * {@link IdleAnimationGoal} is for.
     */
    private static final String ANIM_EAT = "eat";
    /** Floor between shakes, plus its spread: 45 to 90 seconds. */
    private static final int SHAKE_COOLDOWN_TICKS = 900;
    private static final int SHAKE_COOLDOWN_SPREAD_TICKS = 900;

    /** Time in the water that earns a shake on the way out. @see LeaveWaterShakeGoal */
    private static final int SOAKED_TICKS = 100;

    /**
     * How far up the body the water has to come before the water clips take over, as a fraction of
     * the animal's height. Half is belly-deep. @see #isSwimDeep()
     *
     * <p>A look number: adjust it against the render, not by arguing about it.
     */
    private static final float SWIM_DEPTH_FRACTION = 0.5F;

    /** Submerged this long and it comes up wearing algae. */
    private static final int SEAWEED_GROWTH_TICKS = 200;
    /** How long shearing keeps it bare, expressed as the negative the counter starts from. */
    private static final int SEAWEED_SHEAR_BLOCK_TICKS = 100;
    /**
     * Downward bias that keeps it planted on the bed, on top of what vanilla already applies.
     *
     * <p>Vanilla's own sink is {@code getFluidFallingAdjustedMovement}'s gravity/16 = 0.005 against a
     * 0.8 damping on Y, which settles at 0.025 blocks a tick — two seconds to fall one block, and it
     * reads as the animal gliding down a step rather than stepping down it. Adding this on top puts
     * the terminal descent at {@code (0.005 + 0.03) / (1 - 0.8)} ≈ 0.175 blocks a tick, about three
     * and a half blocks a second: weighty without being a stone.
     *
     * @see #travel(Vec3)
     */
    private static final double SINK_ACCELERATION = 0.03D;

    /**
     * How much of the swim speed goes into climbing toward the waypoint. Vanilla's own number —
     * {@code DrownedMoveControl} uses exactly this on its vertical term. @see #swimClimbRate()
     */
    private static final double SWIM_CLIMB_GAIN = 0.1D;

    /**
     * Whether it is currently overgrown. Synced because the renderer picks the coat from it.
     */
    private static final EntityDataAccessor<Boolean> SEAWEED =
            SynchedEntityData.defineId(HellHippoEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * Progress toward growing algae. Negative means shorn and blocked, counting back up to zero
     * before growth can resume.
     */
    private int seaweedTicks;

    public HellHippoEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        // Vanilla's MoveControl snaps a mob's yaw to its next waypoint, which on something this
        // heavy reads as the whole animal pivoting on the spot. DirectionalMoveControl caps the turn
        // instead. 6 degrees a tick against the Tangoftero's 10: the hippo is a far bigger body and
        // should feel like it has to commit to a turn.
        // 6 while wandering keeps the weight; 35 in combat is what stops a hippo that has just been
        // hit from spending most of a second swinging its bulk around before it can start closing.
        // DirectionalMoveControl also cuts forward speed as the yaw error grows past 30 degrees, so a
        // slow combat turn does not merely look sluggish — it holds the animal nearly still.
        this.moveControl = new DirectionalMoveControl<>(this).setTurnSpeed(6.0F).setCombatTurnSpeed(35.0F);
        // Makes water free to route through for the ground navigation in createNavigation(), which
        // otherwise would not enter it at all. Vanilla's Drowned sets the first of these for exactly
        // the same reason. WATER_BORDER is the shoreline ring, and leaving it costed would make the
        // animal treat its own entry and exit from the water as an obstacle worth walking around.
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
    }

    /** Pairs with {@link DirectionalMoveControl} — vanilla's body control snaps in the same way. */
    @Override
    protected @NotNull BodyRotationControl createBodyControl() {
        return new SmoothBodyRotationControl<>(this);
    }

    /**
     * Values straight from the 1.20.1 build — deliberately unchanged, so the port is comparable in
     * play. The <em>base</em> is not: 1.20.1 used {@code createLivingAttributes()} and that was fine
     * there, but 26.1's {@code TemptGoal} reads {@code Attributes.TEMPT_RANGE} (TemptGoal.java:58)
     * and an attribute the supplier never declared throws
     * {@code Can't find attribute minecraft:tempt_range} on the first tick the goal is evaluated.
     * {@code createAnimalAttributes()} is just {@code createMobAttributes().add(TEMPT_RANGE, 10)} —
     * the right base for anything extending {@code Animal}. Same correction the Tangoftero carries.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.FOLLOW_RANGE, 28.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.250D)
                .add(Attributes.ATTACK_SPEED, 0.250D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.5D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.1D)
                .add(Attributes.ARMOR)
                .add(Attributes.STEP_HEIGHT, 1.0)
                // This is what makes it walk the bed rather than crawl it, and it replaces a
                // hand-rolled travel() override that used to do the same job badly. Vanilla's water
                // branch interpolates BOTH its drag and its acceleration toward the land values by
                // this attribute (LivingEntity#travelInWater): at 1.0 and standing on the bottom,
                // drag goes 0.8 -> 0.546 and acceleration 0.02 -> getSpeed(), i.e. exactly the
                // numbers the animal walks with on dry land. 1.0 is also the attribute's declared
                // maximum, so this is the ceiling, not an arbitrary pick.
                //
                // It already exists on every living entity by way of createLivingAttributes()
                // (LivingEntity#createLivingAttributes) with a default of 0; re-adding it here overwrites that
                // default, which the builder's backing HashMap allows.
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0D);
    }

    @Override
    protected void registerGoals() {
        // No FloatGoal, deliberately, and 1.20.1 left it off for the same reason. That goal exists to
        // paddle a mob to the surface and bob it there, which is the exact opposite of what this
        // animal does: it breathes underwater (see canBreatheUnderwater) and walks the riverbed, and
        // a Float at priority 0 would fight the sink in tick() every single tick it spent submerged.
        // Nothing is lost by omitting it, because drowning is not a risk this animal has.
        //
        // Sleep therefore sits at the top: it holds MOVE, so anything below it stops being able to
        // steer a sleeping animal.
        this.goalSelector.addGoal(1, this.createSleepGoal());
        // Vanilla's BreedGoal, not GenericBreedGoal: that one exists for the egg layers and finishes
        // mating with finalizeSpawnChildFromBreeding(..., null) — hearts and experience but no
        // offspring, which is exactly what "they breed but no calf appears" looked like. A hippo
        // bears live young, so the plain vanilla goal is the correct one.
        // The goal only decides WHEN to lunge; the damage lives in the clip's HitWindow (see
        // registerAnimations) so it lands on the frame the jaws close.
        this.goalSelector.addGoal(2, new AnimatableMeleeAttackGoal(this, 1.3D, true)
                // Centre-to-centre, NOT surface-to-surface — and on an animal this shape that
                // distinction is the whole ballgame. The hitbox is 2.5 deep (1.25 from centre to its
                // front face) but the head hangs far past it: root 0 -> body +18 -> neck -31 ->
                // head -15 -> the snout cube's own -26 puts the muzzle tip 54px = 3.375 blocks ahead
                // of the entity position, i.e. 2.1 blocks clear of the collision box.
                //
                // So a player standing at the jaws sits ~3.4 blocks from centre. At the old 3.0 the
                // goal refused to swing at a target the bite would comfortably have landed on: the
                // HitWindow below reaches 1.6 + 2.2 + the target's own half-width = 4.1 blocks
                // forward. The goal was the shorter of the two, which is backwards — the gate that
                // decides whether to swing must not be tighter than the damage it gates. That gap is
                // exactly the reported "it walks right up, mouth on me, and does nothing until I
                // shuffle a step", the step being what crossed 3.0.
                //
                // Kept inside the box's 4.1 so it never swings at something unhittable.
                .reach(3.5F)
                // ...but do NOT stand at the reach, which is most of a body-length short of a target
                // it can already bite: driving the walk-up off the same generous number parked it
                // visibly far away. The muzzle sits 3.375 ahead of centre (see above), so stopping at
                // 2.6 puts the jaws about 0.8 PAST the target rather than short of it — the animal
                // closes properly and the mouth ends up where the bite is. Comfortably above the
                // ~1.55 its own 2.5-wide hitbox allows against a player, so it settles rather than
                // shoving, and above the HitWindow's 1.3 near edge, so the target never ends up in
                // the dead zone behind the box's leading face.
                .stopDistance(3.0F)
                // Just over the 16-tick lunge. It was 30, which stacked 1.5 s of standing on top of
                // the bite and read as the animal losing interest between swings — and that sits on
                // top of a delay this cannot control: vanilla's MeleeAttackGoal#canUse throttles
                // itself to one check per 20 ticks (MeleeAttackGoal.java:35), so re-engaging after
                // any interruption already costs up to a second before this goal is even asked.
                .cooldown(18)
                .onAttack((target, animator) -> animator.play(animator.getByName(ANIM_ATTACK))));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.15D));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.2D, FOOD_ITEMS, false));
        // Above the strolls so that stepping onto dry land takes precedence over wandering off still
        // dripping, and it claims MOVE so it can interrupt one mid-stride.
        this.goalSelector.addGoal(4, new LeaveWaterShakeGoal(this, ANIM_SHAKE, SOAKED_TICKS));
        this.goalSelector.addGoal(5, new SMOPFollowParentGoal(this, 1.1D, FOLLOW_PARENT_DISTANCE));
        // ONE stroll for both media, and the land one at that. This used to be two goals split on
        // isInWater(), with RandomSwimmingGoal for the wet half — which was the wrong tool twice
        // over. It picks destinations through BehaviorUtils.getRandomSwimmablePos, i.e. anywhere in
        // the water column within 7 blocks vertically, and a bottom-walker cannot reach mid-water: a
        // path to one either fails outright or forces the swim fallback, so the animal spent its
        // wandering swimming instead of walking.
        //
        // The land goal picks correctly underwater for free, because of the malus set in the
        // constructor. DefaultRandomPos filters every candidate through GoalUtils.hasMalus (which
        // demands malus == 0, so WATER at 0.0 makes submerged spots eligible) and
        // GoalUtils.isNotStable -> isStableDestination (which demands a solid block below, so
        // mid-water spots are rejected and seabed spots are not). The filter therefore lands exactly
        // on the bed. See SeabedPathNavigation#isStableDestination for why that one is deliberately
        // not delegated to the swimming navigation.
        this.goalSelector.addGoal(6, new SMOPRandomStrollGoal(this, 1.0D, 120,
                () -> !this.isMovementLocked()));
        // Flagless, so its priority is presentational — see IdleAnimationGoal's class note.
        // Dry land only. The gesture is an animal shaking water off itself, which is absurd while it
        // is standing in the stuff, and isInWater() is deliberately the same predicate
        // LeaveWaterShakeGoal gates on — the two paths to this one clip should not disagree about
        // when it is allowed. Feeding it through condition() rather than canUse() also means a hippo
        // that walks into water mid-shake has the gesture cut, because IdleAnimationGoal re-tests
        // this one while running.
        this.goalSelector.addGoal(8, new IdleAnimationGoal(this, SHAKE_COOLDOWN_TICKS, SHAKE_COOLDOWN_SPREAD_TICKS)
                // Grazing is the common gesture and the shake the occasional one: a hippo standing
                // around crops grass far more often than it shakes itself off, and the shake already
                // has its own trigger on leaving the water. Both share the one cooldown, so the two
                // can never land back to back.
                .add(ANIM_EAT, 3)
                .add(ANIM_SHAKE, 1)
                .condition(animal -> !animal.isInWater()));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));

        // Retaliation outranks hunting: whoever just hit it matters more than lunch. setAlertOthers
        // is what makes a calf and its mother defend each other — a hurt hippo passes its attacker to
        // the other hippos nearby, in both directions. Calves are big enough to be worth something in
        // a fight; what they still never do is start one (see picksItsOwnFights).
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            /** A sleeping hippo does not wake into a fight — SleepGoal decides when it wakes. */
            @Override
            public boolean canUse() {
                return !HellHippoEntity.this.isInSleepCycle() && super.canUse();
            }
        }.setAlertOthers());
        // Explicit type arguments: the diamond cannot be inferred for an anonymous subclass.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<Player>(this, Player.class, true) {
            @Override
            public boolean canUse() {
                return HellHippoEntity.this.picksItsOwnFights() && super.canUse();
            }
        });
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<Animal>(this, Animal.class, false, PREY_SELECTOR) {
            @Override
            public boolean canUse() {
                return HellHippoEntity.this.picksItsOwnFights() && super.canUse();
            }
        });
    }

    /**
     * Whether this hippo still chooses its own targets.
     *
     * <p>A saddled one does not: from the moment it is tack, the initiative belongs to whoever is on
     * its back, and phase 3's mounted attack is how that gets expressed. Retaliation is deliberately
     * exempt — a saddled hippo that is being hit still fights back. Calves never start anything, and
     * neither does a sleeping animal.
     */
    private boolean picksItsOwnFights() {
        return !this.isSaddled() && !this.isBaby() && !this.isInSleepCycle();
    }

    // ───────────────────────────────────────────────────── COMBAT ─────

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            this.faceCombatTarget();
            this.tickSeaweed();
            this.tickWeaknessSleep();
            this.tickIntimidation();
            this.tickRiddenState();
            Player rider = RiderAbility.controllerOf(this);
            this.fearPulse.tick(rider);
            this.mountedAttack.tick(rider);
        }
    }

    // ───────────────────────────────────────────────────── THE SADDLING WINDOW ─────

    /**
     * A splash of Weakness puts it out, and that is the only practical way to saddle one.
     *
     * <p><b>This is the step that makes the rest of the ritual work,</b> and the port had been
     * missing it: {@link #trySaddle} demands a sleeping animal, and waiting for nightfall while a
     * hippo you have just won over stares you down (see {@link #tickIntimidation()}) is not a plan.
     * A potion is. It comes straight from 1.20.1, where the same two lines opened the window.
     *
     * <p>It stays under until the Weakness runs out — <em>unless it has been saddled in the
     * meantime</em>, in which case the ceremony is over, the potion is spent early, and it gets up.
     * That last part is 1.20.1's too, right down to which of the two ends it.
     */
    private void tickWeaknessSleep() {
        boolean weakened = this.hasEffect(MobEffects.WEAKNESS);
        if (weakened && !this.isInSleepCycle() && !this.isSaddled()) {
            this.sleepUrge().forceSleep(true);
            return;
        }
        if (this.sleepUrge().isForced() && (!weakened || this.isSaddled())) {
            this.sleepUrge().forceSleep(false);
            this.sleepUrge().requestWake();
        }
    }

    // ───────────────────────────────────────────────────── INTIMIDATION ─────

    /**
     * Winning its trust is not the end of the negotiation. For {@link #INTIMIDATION_TICKS} after it
     * decides it likes you, a hippo that is trusted but <em>not yet saddled</em> squares up to its
     * new owner — and if that clock runs out while it is still awake, it forgets you entirely and the
     * beef starts over.
     *
     * <p>Two ways out, both from 1.20.1: get a saddle on it (which needs it asleep, which needs the
     * potion — see {@link #tickWeaknessSleep()}), or simply let it fall asleep, because the timer
     * only takes the trust away from an animal that is awake to withdraw it.
     *
     * <p><b>And do not stare.</b> Holding its gaze square-on for {@link #STARE_TICKS_TO_FEAR} ticks
     * earns {@code smop:fear}. The dot product against the player's own look vector is 1.20.1's test,
     * unchanged.
     *
     * <p><b>The clip is the whole of the signposting.</b> 1.20.1 narrated every beat of this in chat
     * — "is now intimidating", "you are terrified", "has forgotten your trust" — and the port drops
     * chat on purpose. That leaves the {@link #ANIM_INTIMIDATE_IN in}/{@link #ANIM_INTIMIDATE_LOOP
     * loop}/{@link #ANIM_INTIMIDATE_OUT out} clips, ported months ago and left unregistered until
     * now, plus a sound on the way in. If the player cannot tell the clock is running, this mechanic
     * reads as the mob randomly untaming itself.
     */
    private void tickIntimidation() {
        if (this.isIntimidating()) {
            // Out cold, the whole display is suspended: the clock stops, it does not posture, and it
            // certainly does not frighten anyone. That freeze IS the reprieve the potion buys — the
            // state stays set so the negotiation resumes if it wakes without a saddle.
            if (this.isInSleepCycle()) {
                return;
            }
            // The clock runs whether or not the owner is still around, which matters: keying it to
            // the owner being present would mean logging out reset it, and the whole point is that
            // the window is not renewable.
            if (--this.intimidationTicks <= 0) {
                boolean wasStandoff = this.standoff;
                this.stopIntimidating();
                if (wasStandoff) {
                    this.forgetTrust();
                }
                return;
            }
            // Only the standoff hijacks the body and punishes staring. During a rider's pulse the
            // player is steering, and wrenching the mount round to face them would fight the wheel.
            if (this.standoff && this.getOwner() instanceof Player watcher) {
                this.faceIntimidationTarget(watcher);
                this.tickStare(watcher);
            }
            return;
        }
        // Only the pre-saddle standoff ever starts by itself. The rider's pulse is asked for.
        if (!this.isTame() || this.isSaddled()) {
            return;
        }
        // Only the START needs them in the room.
        if (this.getOwner() instanceof Player player
                && this.distanceTo(player) < INTIMIDATION_RADIUS
                && this.hasLineOfSight(player)) {
            this.startIntimidating(INTIMIDATION_TICKS, true);
            this.playSound(SoundEvents.HOGLIN_ANGRY, 1.0F, 0.7F);
        }
    }

    private void tickStare(Player player) {
        Vec3 toHippo = this.position().subtract(player.position()).normalize();
        if (player.getLookAngle().normalize().dot(toHippo) <= STARE_DOT) {
            this.staringTicks = 0;
            return;
        }
        if (++this.staringTicks < STARE_TICKS_TO_FEAR) {
            return;
        }
        this.staringTicks = 0;
        if (!player.hasEffect(SMOPEffects.FEAR)) {
            player.addEffect(new MobEffectInstance(SMOPEffects.FEAR, FEAR_DURATION_TICKS, 0));
        }
    }

    /**
     * Squares up: turns on the spot to keep the player in front of it, and goes nowhere.
     *
     * <p>The turn is the move control's own, so it is the same capped, eased rotation the animal uses
     * everywhere else rather than a snap — it just has no waypoint to walk toward, because
     * {@link #isMovementLocked()} has taken the wheel off every movement goal for the duration.
     */
    private void faceIntimidationTarget(Player player) {
        if (this.moveControl instanceof DirectionalMoveControl<?> control) {
            control.faceTarget(player);
        }
    }

    /**
     * Standing its ground is part of the display. A hippo that wandered off mid-stare would read as
     * having lost interest, and 1.20.1 pinned it for the same reason — {@code travel()} there opened
     * with {@code if (this.isIntimidating()) { setDeltaMovement(ZERO); navigation.stop(); }}.
     *
     * <p>Going through the base class's lock rather than zeroing the motion by hand is what also
     * makes every movement goal stand down instead of fighting it every tick.
     */
    @Override
    public boolean isMovementLocked() {
        return super.isMovementLocked() || this.isIntimidating();
    }

    /**
     * Squares up for {@code ticks}, running the whole {@code in → loop → out} clip chain.
     *
     * <p>Shared by the two things that make this animal posture, which look identical and mean
     * different things: the pre-saddle standoff, where running out of time costs the player its
     * trust, and the rider's fear pulse, where it is pure display. {@code standoff} is which.
     *
     * <p>Going through here rather than firing the entry clip on its own is what fixed the pulse
     * looking like a twitch: {@code intimidate_in} is 0.65 seconds, and the loop that should follow it
     * is gated on this very flag — so a pulse that only called {@code startAction} played the entry
     * and stopped dead.
     */
    private void startIntimidating(int ticks, boolean standoff) {
        this.entityData.set(INTIMIDATING, true);
        this.intimidationTicks = ticks;
        this.staringTicks = 0;
        this.standoff = standoff;
        // Started by hand because the animator only auto-starts REPEATING clips; this one chains
        // into the loop on its own once it finishes. @see #registerAnimations()
        this.startAction(ANIM_INTIMIDATE_IN);
    }

    private void stopIntimidating() {
        if (this.isIntimidating()) {
            this.entityData.set(INTIMIDATING, false);
            // Only on a real standoff ending — this method is also the every-tick "nothing to do
            // here" path, and playing the release on each of those would be a permanent twitch.
            // Skipped while asleep: nothing to release from, the sleep clips own the body.
            if (!this.isInSleepCycle()) {
                this.startAction(ANIM_INTIMIDATE_OUT);
            }
        }
        this.intimidationTicks = 0;
        this.staringTicks = 0;
    }

    /** Back to square one: the beef, and the dice. */
    private void forgetTrust() {
        this.stopIntimidating();
        this.setTame(false, true);
        this.setOwnerReference(null);
        this.level().broadcastEntityEvent(this, (byte) 6);   // smoke, the same "no" as a failed feed
    }

    /** Synced — safe to read from an animation play condition on either side. */
    public boolean isIntimidating() {
        return this.entityData.get(INTIMIDATING);
    }

    // ───────────────────────────────────────────────────── WATER ─────

    /**
     * Walks the bed, swims only when walking cannot get there. See {@link SeabedPathNavigation} for
     * why a plain {@code AmphibiousPathNavigation} — which is what this used to be — routes above the
     * floor and leaves a bottom-walker twitching in place.
     *
     * <p>The malus pair in the constructor is the other half of it: without them this navigation
     * would refuse to enter water at all.
     */
    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull Level level) {
        return new SeabedPathNavigation(this, level);
    }

    /** It lives half its life underwater; drowning would be absurd. */
    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    /**
     * What lets it jump <em>on</em> the bed instead of bobbing above it.
     *
     * <p><b>This reads backwards, so it is worth tracing.</b> The threshold is the fluid depth past
     * which vanilla stops treating a jump as a jump and treats it as swimming up
     * ({@code LivingEntity.aiStep}, the {@code jumping} block). Putting it out of reach means the
     * depth is never past it, so for an animal <em>standing on the bottom</em> the branch that runs
     * is {@code jumpFromGround()} — a real jump, at full jump strength. Only when it has no ground
     * under it does it fall through to {@code jumpInFluid}'s gentle 0.04 nudge upward.
     *
     * <p>At the vanilla default of 0.4 the standing case would take the nudge as well, and a mob
     * whose {@code MoveControl} asks for a jump to climb a block would just bounce in place. So the
     * override is not switching a behaviour off; it is the one thing that lets this animal step up
     * onto a ledge on the seabed and walk out onto a bank under its own power.
     */
    @Override
    public double getFluidJumpThreshold() {
        return Double.MAX_VALUE;
    }

    /**
     * Adds weight to a submerged hippo. The walking itself is vanilla's now.
     *
     * <p><b>This used to reimplement the whole water branch, and that was the mistake.</b> It ran its
     * own {@code moveRelative} + {@code move} + drag, which cost it everything the surrounding
     * vanilla code does for free — {@code calculateEntityAnimation}, the climbable handling, and the
     * {@code horizontalCollision} nudge that shakes a mob off a corner it has snagged on. Worse, the
     * numbers were wrong: {@code Mob#setSpeed} also sets {@code zza}, so the travel vector's length
     * is under 1 and {@code getInputVector} never normalises it, making the real acceleration
     * {@code speed²} ≈ 0.0625 a tick. Against the old 0.9 horizontal drag that settles at 0.56 blocks
     * a tick, roughly seven and a half times what the same animal manages on land. It never showed in
     * play only because the navigation had it pinned in place before it could reach that speed.
     *
     * <p>{@code WATER_MOVEMENT_EFFICIENCY} does the job properly — see {@link #createAttributes()} —
     * so all that is left here is gravity the attribute does not cover.
     *
     * <p><b>{@code isEffectiveAi()} is correctness, not thrift.</b> {@code super.travel} closes over
     * {@code isControlledByLocalInstance()}, and pushing delta movement on the client for an entity
     * it does not control would fight the interpolation.
     *
     * <p><b>{@code isMovementLocked()} is deliberately NOT checked.</b> A hippo asleep on the bed has
     * to keep its weight too, or it drifts off the bottom mid-nap. The lock stops it steering, not
     * being heavy. Ridden is exempt, though — phase 3's steering owns that case.
     */
    @Override
    public void travel(@NotNull Vec3 travelVector) {
        super.travel(travelVector);
        if (!this.isEffectiveAi() || !this.isInWater() || this.isVehicle()) {
            return;
        }
        Vec3 velocity = this.getDeltaMovement();
        if (this.isSwimmingFallback()) {
            this.setDeltaMovement(velocity.x, velocity.y + this.swimClimbRate(), velocity.z);
        } else if (!this.onGround()) {
            this.setDeltaMovement(velocity.x, velocity.y - SINK_ACCELERATION, velocity.z);
        }
    }

    /** Whether the navigation has given up on walking and is swimming this one. */
    private boolean isSwimmingFallback() {
        return this.getNavigation() instanceof SeabedPathNavigation nav && nav.isSwimming();
    }

    /**
     * How hard to climb toward the current waypoint. Zero on the level, negative on the way down.
     *
     * <p><b>Without this the animal cannot rise in water at all</b>, and it took tracing both of
     * vanilla's ascent mechanisms to see why it has neither. A land mob climbs because its
     * {@code MoveControl} calls {@code getJumpControl().jump()} ({@code MoveControl#tick}),
     * which raises {@code jumping}, which {@code aiStep} turns into {@code jumpInFluid}'s upward
     * nudge — but {@link DirectionalMoveControl} is horizontal-only and never requests a jump. A
     * swimming mob climbs because its move control drives Y straight at the waypoint; the shape and
     * the 0.1 here are lifted from {@code DrownedMoveControl}, which is the closest working
     * reference in vanilla ({@code f2 * d1 * 0.1}, with {@code d1} the normalised Y component).
     *
     * <p>And the jump route would not have been enough on its own anyway: against the 0.8 damping on
     * Y, {@code jumpInFluid}'s 0.04 settles at {@code (0.8·0.04 − 0.005 − 0.03) / 0.2} = −0.015 once
     * {@link #SINK_ACCELERATION} is in play. Still sinking. Which is why {@link #travel} swaps the
     * sink out for this rather than adding this on top — the two are alternatives, not layers.
     */
    private double swimClimbRate() {
        MoveControl control = this.getMoveControl();
        double dx = control.getWantedX() - this.getX();
        double dy = control.getWantedY() - this.getY();
        double dz = control.getWantedZ() - this.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1.0E-4D) {
            return 0.0D;
        }
        return this.getSpeed() * (dy / distance) * SWIM_CLIMB_GAIN;
    }

    /**
     * Grows algae on a hippo that stays under long enough.
     *
     * <p><b>The shear block works here, unlike in 1.20.1.</b> That version set the counter to -100 on
     * shearing and then opened its growth branch with "if the counter is negative, set it to zero" —
     * so the very next submerged tick threw the block away and regrowth started immediately. The
     * block only means anything if the negative is allowed to count its way back up, which is what
     * this does.
     */
    private void tickSeaweed() {
        if (this.hasSeaweed()) {
            return;
        }
        // Calves are exempt: only the adult coats have a seaweed variant, so an overgrown calf would
        // carry the flag with nothing to show for it — state the player cannot see and cannot shear
        // off. Worth revisiting the day baby_hell_hippo_seaweed.png exists.
        if (!this.isFullySubmerged() || this.isSaddled() || this.isBaby()) {
            // Surfacing resets progress, but never un-blocks a fresh shear.
            this.seaweedTicks = Math.min(this.seaweedTicks, 0);
            return;
        }
        this.seaweedTicks++;
        if (this.seaweedTicks >= SEAWEED_GROWTH_TICKS) {
            this.setSeaweed(true);
            this.playSound(SoundEvents.TURTLE_EGG_HATCH, 1.0F, 1.0F);
        }
    }

    /**
     * Under the water rather than merely standing in it — algae need the whole animal wet. Vanilla's
     * {@code isUnderWater} is the eye test, which on a body this tall is the right line: eyes under
     * means everything below them is too.
     *
     * <p><b>Deliberately not shared with {@link #isSwimDeep()}</b>, which asks a different question.
     * "The whole animal is wet" wants a strict line and is about growing algae; "which gait to play"
     * wants a looser one and is about how the animal reads on screen. Folding them together would
     * mean tuning the look of an animation silently moved when seaweed grows.
     */
    private boolean isFullySubmerged() {
        return this.isUnderWater();
    }

    /**
     * Deep enough that the water clip set is the right one to play.
     *
     * <p><b>{@code isInWater()} is the wrong line and was what this used to use.</b> It goes true the
     * instant a toe touches a puddle, so a hippo walking through one block of water — walking, with
     * its feet on the ground, at its full land speed — played the swim clip. What actually matters is
     * how far up the body the water comes, and {@code getFluidHeight} is exactly that measurement:
     * {@code EntityFluidInteraction} fills it with
     * {@code max(fluidSurface - entityY)}, in blocks above the feet.
     *
     * <p>Taken as a fraction of the animal's own height rather than a flat number, so it survives a
     * change to the hitbox and lifts cleanly to {@code SMOPAnimal} the day the Nirasmosaurus needs
     * it. At the hippo's 2.5 that puts the line at 1.25: one block of water is walked, two swims.
     *
     * <p><b>Safe to read from a play condition</b>, which is the constraint that ruled out anything
     * cleverer here. Conditions are evaluated on both sides, and the fluid height is recomputed from
     * {@code baseTick} on both, so client and server cannot disagree about it — unlike delta movement,
     * which is why {@code isMoving()} has to be a synced flag.
     *
     * <p><b>No hysteresis, on purpose.</b> A real one needs memory, and that memory would have to be
     * synced. On a flat bed the animal's {@code minY} is constant and the line is crossed once, going
     * in or coming out. If it ever strobes on a sloped shore, the fix already exists in the codebase:
     * {@code SMOPAnimal.MoveHold} is protected for this exact purpose and {@code SMOPWaterAnimal}
     * already runs a second one for its sprint threshold.
     */
    private boolean isSwimDeep() {
        return this.getFluidHeight(FluidTags.WATER) >= this.getBbHeight() * SWIM_DEPTH_FRACTION;
    }

    public boolean hasSeaweed() {
        return this.entityData.get(SEAWEED);
    }

    public void setSeaweed(boolean value) {
        this.entityData.set(SEAWEED, value);
    }

    /**
     * Shears, then the saddle, then the trust ritual. Everything else falls through to the base
     * animal, which is what leaves breeding and calf-feeding working.
     *
     * <p>The order matters: the saddle is tested before the trust ritual so that a saddle in hand is
     * never mistaken for anything else, and the trust ritual stands down once the animal is tamed so
     * that beef goes back to meaning "breed with me".
     */
    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // First, and regardless of what is in hand — same order vanilla's chested mounts use. Crouching
        // at a loaded pack animal means "let me at the bags", not "let me try this item on you".
        if (player.isSecondaryUseActive() && this.hasChest() && this.isOwnedBy(player)) {
            this.openCustomInventoryScreen(player);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.SHEARS) && this.hasSeaweed()) {
            if (this.level() instanceof ServerLevel serverLevel) {
                this.setSeaweed(false);
                this.seaweedTicks = -SEAWEED_SHEAR_BLOCK_TICKS;
                this.level().playSound(null, this, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
                this.spawnAtLocation(serverLevel, new ItemStack(Items.KELP, 2));
                stack.hurtAndBreak(1, player,
                        hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.SADDLE)) {
            return this.trySaddle(player, stack);
        }
        if (stack.is(Items.CHEST) && !this.hasChest()) {
            return this.tryChest(player, stack);
        }
        // Body armour has NO generic equip path, unlike the saddle: Item.Properties#horseArmor builds
        // its Equippable without setEquipOnInteract, so vanilla's own horse equips barding by hand in
        // its mobInteract too. Left to itself, right-clicking with it does nothing at all.
        if (this.isEquippableInSlot(stack, EquipmentSlot.BODY) && !this.isWearingBodyArmor()) {
            return this.tryBodyArmor(player, stack);
        }
        if (this.canBeOfferedTrustFood(stack)) {
            return this.offerTrustFood(player, hand, stack);
        }
        // Last, and only with nothing else to say: an empty hand on a saddled hippo means "get on".
        // Anything held falls through to the base animal instead, so feeding a tamed one still breeds
        // it rather than silently mounting.
        if (stack.isEmpty() && this.isSaddled() && this.isOwnedBy(player)) {
            return this.tryRide(player);
        }
        return super.mobInteract(player, hand);
    }

    // ───────────────────────────────────────────────────── TRUST ─────

    /**
     * Raw beef offered to a wild adult is a bid for its trust; anything else is not.
     *
     * <p><b>Calves are exempt on purpose</b>, which is a small departure from 1.20.1. Beef is also in
     * {@link #FOOD_ITEMS}, so intercepting it for a calf would quietly cost the player vanilla's
     * feed-to-grow-faster affordance in exchange for a bond they cannot act on anyway — the saddle
     * needs an adult (see {@link #canUseSlot}). Carrots still grow a calf either way.
     */
    private boolean canBeOfferedTrustFood(ItemStack stack) {
        return !this.isTame() && !this.isBaby() && stack.is(Items.BEEF);
    }

    /**
     * One throw of the dice. Wins its trust on a {@value #TRUST_CHANCE_DENOMINATOR}, or eats the beef
     * and stays wary.
     *
     * <p>The bond is vanilla's ownership, not a flag of this mob's own. 1.20.1 carried a synced
     * {@code DATA_TRUSTING} boolean beside a {@code trustingPlayerUUID} it had to save and load by
     * hand; {@link TamableAnimal} already has both, already synced and already persisted, and
     * {@code TamableAnimal#canAttack} even refuses to attack its owner for free.
     */
    private InteractionResult offerTrustFood(Player player, InteractionHand hand, ItemStack stack) {
        if (this.level().isClientSide()) {
            // SUCCESS rather than CONSUME so the arm swings on the client that made the offer.
            return InteractionResult.SUCCESS;
        }
        this.usePlayerItem(player, hand, stack);
        if (this.getRandom().nextInt(TRUST_CHANCE_DENOMINATOR) == 0) {
            this.tame(player);
            this.level().broadcastEntityEvent(this, (byte) 7);   // hearts
        } else {
            this.level().broadcastEntityEvent(this, (byte) 6);   // smoke
        }
        return InteractionResult.SUCCESS;
    }

    // ───────────────────────────────────────────────────── SADDLE ─────

    /**
     * Saddling is a second ceremony on top of the first, and it is meant to be awkward: only while
     * the animal is asleep, and only by the one it trusts. Putting it on wakes it.
     *
     * <p>1.20.1 built that window deliberately and it is the best idea the mob has, so it survives
     * the port intact. What changed is only the plumbing: the saddle is real equipment in 26.1
     * ({@link EquipmentSlot#SADDLE}), so vanilla owns its persistence, its render and its drop, and
     * there is no {@code Saddleable} interface left to implement.
     */
    private InteractionResult trySaddle(Player player, ItemStack stack) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        // isEquippableInSlot folds in canUseSlot below, so "already saddled", "still a calf" and
        // "not tamed" are all covered without repeating them here.
        if (!this.isKnockedOut() || !this.isOwnedBy(player) || !this.isEquippableInSlot(stack, EquipmentSlot.SADDLE)) {
            // CONSUME and NOT FAIL, which is a trap worth spelling out: InteractionResult.Fail does
            // not consume the action, so Player#interactOn carries on and calls
            // itemStack.interactLivingEntity(...) — and a vanilla saddle is built with
            // setEquipOnInteract(true), so that call would put it on regardless of everything tested
            // above. Refusing has to consume, or the refusal is decorative.
            return InteractionResult.CONSUME;
        }
        this.setItemSlot(EquipmentSlot.SADDLE, stack.consumeAndReturn(1, player));
        this.guaranteeTackDrops();
        // The negotiation is over: it stops squaring up, and the trust can no longer lapse.
        this.stopIntimidating();
        // Spends the potion early rather than leaving the animal knocked out with a saddle on.
        // tickWeaknessSleep does the rest of the honours on the next tick.
        this.removeEffect(MobEffects.WEAKNESS);
        // Only a request: SleepGoal owns the cycle and will run the waking clip rather than snapping
        // the animal upright. @see SMOPAnimal#hurtServer, which rouses it the same way.
        this.sleepUrge().requestWake();
        return InteractionResult.SUCCESS;
    }

    /**
     * Knocked out by a potion, as opposed to merely having gone to bed.
     *
     * <p>Natural sleep does <b>not</b> open the saddling window, and that is the point of the whole
     * chain: a hippo that dozes off at nightfall is still its own animal, and walking up to a
     * sleeping one to help yourself would make the potion — and the 15-second standoff that forces
     * you to find one — pointless. Only the forced sleep counts.
     */
    private boolean isKnockedOut() {
        return this.isSleeping() && this.sleepUrge().isForced();
    }

    // ───────────────────────────────────────────────────── RIDING ─────

    /** Radius of the intimidation pulse, and how long the fear it leaves lasts. From 1.20.1. */
    private static final double FEAR_PULSE_RADIUS = 10.0D;
    private static final int FEAR_PULSE_DURATION_TICKS = 60;
    private static final int FEAR_COOLDOWN_TICKS = 300;
    /** How long it holds the pose while pulsing. 1.20.1 used the same 60. */
    private static final int FEAR_POSTURE_TICKS = 60;

    /** How much faster it goes with the rider holding sprint. */
    private static final float RIDDEN_SPRINT_MULTIPLIER = 1.6F;

    /** What the rider's bite costs to throw. Its reach and damage are the clip's, not this file's. */
    private static final int MOUNTED_ATTACK_COOLDOWN_TICKS = 60;

    private final RiderAbility fearPulse =
            new RiderAbility(this, "Fear", FEAR_COOLDOWN_TICKS, BossEvent.BossBarColor.PURPLE);

    private final RiderAbility mountedAttack =
            new RiderAbility(this, "Charge", MOUNTED_ATTACK_COOLDOWN_TICKS, BossEvent.BossBarColor.RED);

    /**
     * Only its owner rides it, and only once it is tack. The saddle already implies tamed — see
     * {@link #canUseSlot} — so the ownership test is what stops somebody else climbing onto a hippo
     * that is saddled but not theirs.
     */
    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return this.isSaddled() && this.getFirstPassenger() instanceof Player rider && this.isOwnedBy(rider)
                ? rider
                : super.getControllingPassenger();
    }

    @Override
    protected @NotNull Vec3 getRiddenInput(@NotNull Player controller, @NotNull Vec3 selfInput) {
        return RiderSteering.riddenInput(controller);
    }

    @Override
    protected void tickRidden(@NotNull Player controller, @NotNull Vec3 riddenInput) {
        super.tickRidden(controller, riddenInput);
        Vec2 rotation = RiderSteering.riddenRotation(controller);
        this.setRot(rotation.y, rotation.x);
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
    }

    @Override
    protected float getRiddenSpeed(@NotNull Player controller) {
        float base = (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return this.isSprinting() ? base * RIDDEN_SPRINT_MULTIPLIER : base;
    }

    /**
     * Where the rider logically sits — the camera, their hitbox, and where they land on dismount.
     *
     * <p><b>Not the same thing as where they are drawn.</b> The render seat is walked down the model's
     * own bone chain in {@code HellHippoRenderer#applyRiderTransform}, so it follows the body through
     * every clip. This one cannot: it is server-side geometry and has to be a plain offset. The two
     * only need to agree closely enough that the camera sits inside the drawn rider, which on a body
     * 2.5 tall means somewhere around the shoulders.
     *
     * <p>Vanilla's default would put them at {@code height * 0.75}, which on this animal is buried in
     * its back.
     */
    @Override
    protected @NotNull Vec3 getPassengerAttachmentPoint(@NotNull Entity passenger,
                                                        @NotNull EntityDimensions dimensions, float scale) {
        // THIS IS THE CAMERA, not the visible seat. The two are independent here, which is what
        // makes the number free to choose: HellHippoRenderer#applyRiderTransform re-derives where the
        // rider is DRAWN from the model's own bones (root -> body -> torso) and never reads this
        // point, so raising it lifts the first-person eye and the third-person pivot while the rider
        // stays painted on the saddle exactly where it was.
        //
        // The history: 0.62 put it at 1.55 on a 2.5-tall animal, and the torso cube's top edge — the
        // real back — measures 1.84 above the feet (root 24 -> body -20 -> torso +0.5, cube top -10,
        // so 29.5px up). The camera was therefore 0.29 blocks INSIDE the body and looked out from
        // within the animal's own back. 1.84 cleared that, but the head tops out at 2.69 and the bulk
        // still ate most of the lower screen in third person.
        //
        // 1.0 puts the eye at the top of the hitbox, 2.5, which is above the back and just under the
        // head. Raise it further if the body still crowds the view; the only cost is that the rider's
        // own hitbox floats above where they are drawn, which on a mount nothing depends on.
        return new Vec3(0.0D, dimensions.height() * 1.0D * scale, -0.15D * scale);
    }

    /**
     * Puts a dismounting rider down beside the animal instead of wherever vanilla lands them.
     *
     * <p>This is {@code AbstractHorse}'s algorithm, which 1.20.1 got by inheritance and this port
     * does not: {@link net.darkblade.smop.entity.SMOPAnimal} descends from {@code TamableAnimal}, so
     * the mount-specific half of the horse never came with it. The default is
     * {@code Entity#getDismountLocationForPassenger}, which just hands back the vehicle's own
     * position — fine for something narrow, but this animal is 2.5 blocks wide, so its centre is a
     * place a player can be standing inside a wall or another block entirely.
     *
     * <p>What it does instead: sweep the offsets to either side of the direction of travel, and take
     * the first that has a real floor and enough room for one of the rider's dismount poses (standing,
     * then crouching, then swimming). Falling back to {@code super} when nothing fits is deliberate —
     * a rider trapped on a mount is worse than one nudged into a tight spot.
     */
    @Override
    public @NotNull Vec3 getDismountLocationForPassenger(@NotNull LivingEntity passenger) {
        Direction direction = this.getMotionDirection();
        if (direction.getAxis() == Direction.Axis.Y) {
            return super.getDismountLocationForPassenger(passenger);
        }

        int[][] offsets = DismountHelper.offsetsForDirection(direction);
        // AbstractHorse's offsets are +-1 block, which is fine for a mount about 1.4 wide. This one
        // is 2.5, so half of it alone is 1.25 — every one of those candidates still lands UNDER the
        // animal. DismountHelper only tests terrain, not the vehicle, so they all pass and the rider
        // is placed inside the hippo and shoved out again: exactly the behaviour this was meant to
        // fix. Scaling them out by the animal's own half-width plus the rider's is what makes the
        // candidates actually clear it.
        int spread = Mth.ceil(this.getBbWidth() / 2.0F + 0.5F);
        BlockPos origin = this.blockPosition();
        BlockPos.MutableBlockPos candidate = new BlockPos.MutableBlockPos();

        for (Pose pose : passenger.getDismountPoses()) {
            AABB bounds = passenger.getLocalBoundsForPose(pose);
            for (int[] offset : offsets) {
                candidate.set(origin.getX() + offset[0] * spread, origin.getY(), origin.getZ() + offset[1] * spread);
                double floor = this.level().getBlockFloorHeight(candidate);
                if (!DismountHelper.isBlockFloorValid(floor)) {
                    continue;
                }
                Vec3 spot = Vec3.upFromBottomCenterOf(candidate, floor);
                if (DismountHelper.canDismountTo(this.level(), passenger, bounds.move(spot))) {
                    passenger.setPose(pose);
                    return spot;
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    /**
     * Mirrors the rider's control keys onto the mount, server-side.
     *
     * <p><b>Why the input has to come from {@code getLastClientInput()}</b> and not from
     * {@code controller.xxa}/{@code zza}, which is what vanilla's horse reads: a mount carrying a
     * player is client-authoritative, so the server never simulates its movement and never fills those
     * fields. {@code ServerGamePacketListenerImpl#handlePlayerInput} stores the whole {@code Input}
     * record and forwards exactly one thing out of it — {@code setShiftKeyDown}. Everything else,
     * including which way the rider is pushing and whether they are sprinting, is only readable there.
     *
     * <p>Sprint is mirrored onto the mob's own {@code isSprinting()} rather than a flag of our own,
     * because that one is already a synced shared flag — so the animation condition can read it on
     * both sides for free, which is the whole requirement for a play condition.
     */
    private void tickRiddenState() {
        if (this.getControllingPassenger() instanceof ServerPlayer rider) {
            this.setSprinting(rider.getLastClientInput().sprint());
        } else if (this.isSprinting()) {
            this.setSprinting(false);
        }
    }

    /**
     * While a player is driving, "moving" is whatever they are asking for.
     *
     * <p>The inherited sample cannot work here for the same reason as above: the server is handed the
     * mount's position rather than simulating it, and {@code LivingEntity#travelRidden} sets the delta
     * to {@code Vec3.ZERO} outright on that side. Reading either the delta or the position difference
     * left a ridden hippo reporting that it was standing still, so it never left its idle clip — and
     * that in turn is what made the intimidation gesture drop back to <em>idle</em> instead of
     * <em>walk</em> when it ended.
     */
    @Override
    protected boolean isMovingNow() {
        if (this.getControllingPassenger() instanceof ServerPlayer rider) {
            Input input = rider.getLastClientInput();
            return input.forward() || input.backward() || input.left() || input.right();
        }
        return super.isMovingNow();
    }

    /** Running, whether because it is angry or because its rider is holding sprint. */
    private boolean isRunning() {
        return this.isAggressive() || this.isSprinting();
    }

    /**
     * Takes the cooldown bars off the rider when the animal stops existing — died, unloaded, or was
     * discarded by a command.
     *
     * <p>A {@link net.minecraft.server.level.ServerBossEvent} is not attached to the entity in any
     * way the game cleans up for you: it lives on the players it was added to. Without this, killing
     * a hippo mid-cooldown leaves its rider staring at a sliver of bar that nothing can ever remove.
     */
    @Override
    public void remove(@NotNull RemovalReason reason) {
        this.fearPulse.hide();
        this.mountedAttack.hide();
        super.remove(reason);
    }

    /** Mounting is the plain right-click, once everything else in {@code mobInteract} has passed. */
    private InteractionResult tryRide(Player player) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!this.isSaddled() || !this.isOwnedBy(player) || this.isVehicle() || this.isInSleepCycle()) {
            return InteractionResult.CONSUME;
        }
        player.startRiding(this);
        return InteractionResult.SUCCESS;
    }

    // ───────────────────────────────────────────────────── RIDER ACTIONS ─────

    @Override
    public void onRiderAction(@NotNull ServerPlayer rider, RiderControllable.@NotNull RiderAction action) {
        // The packet already guarantees the sender is riding THIS mount, but not that they are the
        // one steering it — a passenger on a hippo that is not theirs must not fire its abilities.
        if (this.getControllingPassenger() != rider) {
            return;
        }
        switch (action) {
            case FEAR -> this.releaseFearPulse();
            case ATTACK -> this.strikeFromSaddle();
            case OPEN_INVENTORY -> this.openCustomInventoryScreen(rider);
            // Diving belongs to the Nirasmosaurus. A hippo already walks the seabed on its own.
            default -> { }
        }
    }

    /**
     * Everything alive nearby gets a face full of {@code smop:fear} — except the people and animals
     * that are with it.
     *
     * <p>The three exclusions are 1.20.1's, and each earns its place: the rider (obviously), other
     * hell hippos (a herd that panics itself is not intimidating), and anything the rider owns — the
     * pulse should not scatter your own wolves.
     */
    private void releaseFearPulse() {
        if (!this.fearPulse.tryUse()) {
            return;
        }
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(FEAR_PULSE_RADIUS), this::isAfraidOfMe)) {
            victim.addEffect(new MobEffectInstance(SMOPEffects.FEAR, FEAR_PULSE_DURATION_TICKS, 0));
        }
        // Posture for the whole pulse rather than flashing the entry clip — see startIntimidating.
        // Movement locks with it, which is the price of the ability and 1.20.1 charged it too.
        this.startIntimidating(FEAR_POSTURE_TICKS, false);
        this.playSound(SoundEvents.HOGLIN_ANGRY, 1.5F, 0.6F);
    }

    /** Everyone the pulse and the strike are allowed to touch. */
    private boolean isAfraidOfMe(LivingEntity candidate) {
        if (candidate == this || candidate instanceof HellHippoEntity) {
            return false;
        }
        LivingEntity owner = this.getOwner();
        if (candidate == owner) {
            return false;
        }
        return owner == null || !(candidate instanceof TamableAnimal pet) || !pet.isOwnedBy(owner);
    }

    /**
     * The rider asks for the bite the animal already has.
     *
     * <p><b>It does not aim, choose a target, or work out damage</b>, and earlier versions of this
     * doing all three by hand was the mistake. The {@code attack} clip already carries a
     * {@link HitWindow} — a box swept on the frames the jaws close, with the reach, the damage, the
     * knockback and the exclusions all declared in {@link #registerAnimations()}. Playing the clip
     * fires it. Anything this method computed separately would be a second, quietly divergent copy of
     * numbers that already exist a hundred lines up.
     *
     * <p>Aim comes for free too: the window is anchored off body yaw, and {@code tickRidden} has
     * already pointed the body wherever the rider's camera is. Nothing has to stand in front of
     * anything — the bite sweeps its box and catches whatever is in it, exactly as it does when the
     * animal decides to bite on its own.
     *
     * <p>Played through the animator rather than {@code startAction} for the same reason
     * {@code AnimatableMeleeAttackGoal} does: a scripted action would raise the movement lock and take
     * the wheel off the rider for the length of the lunge.
     */
    private void strikeFromSaddle() {
        if (!this.mountedAttack.tryUse()) {
            return;
        }
        this.animator().play(this.animator().getByName(ANIM_ATTACK));
        this.playSound(SoundEvents.HOGLIN_ATTACK, 1.0F, 1.0F);
    }

    // ───────────────────────────────────────────────────── ARMOUR ─────

    /**
     * Straps the barding on. {@link #canUseSlot} has already insisted on a saddled adult, so all this
     * adds is the ownership check and the drop guarantee.
     */
    private InteractionResult tryBodyArmor(Player player, ItemStack stack) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!this.isOwnedBy(player)) {
            return InteractionResult.CONSUME;
        }
        this.setBodyArmorItem(stack.consumeAndReturn(1, player));
        this.guaranteeTackDrops();
        return InteractionResult.SUCCESS;
    }

    /**
     * Makes the tack come back when the animal dies.
     *
     * <p><b>Without this it mostly does not.</b> Equipment leaves a mob through
     * {@code Mob#dropCustomDeathLoot}, which rolls against {@code DropChances.DEFAULT} — <b>0.085</b>,
     * an 8.5% chance — and additionally refuses unless a player landed the killing blow. Those are the
     * right odds for armour a mob spawned wearing, and the wrong ones for a saddle somebody fitted by
     * hand: losing it nine times in ten to a fall or a creeper is not a difficulty choice, it reads as
     * a bug.
     *
     * <p>{@code setGuaranteedDrop} puts the chance at 2.0, which is also above the {@code isPreserved}
     * line of 1.0 — so it clears the killed-by-a-player requirement at the same time.
     */
    private void guaranteeTackDrops() {
        this.setGuaranteedDrop(EquipmentSlot.SADDLE);
        this.setGuaranteedDrop(EquipmentSlot.BODY);
    }

    // ───────────────────────────────────────────────────── CHEST ─────

    /**
     * Panniers, which need a saddle already on. Awake is fine — unlike the saddle, this is not a
     * negotiation, it is luggage on an animal that has already agreed.
     */
    private InteractionResult tryChest(Player player, ItemStack stack) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!this.isSaddled() || !this.isOwnedBy(player)) {
            return InteractionResult.CONSUME;
        }
        this.setChest(true);
        stack.consume(1, player);
        this.level().playSound(null, this, SoundEvents.DONKEY_CHEST, SoundSource.NEUTRAL, 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    public boolean hasChest() {
        return this.entityData.get(CHEST);
    }

    public void setChest(boolean value) {
        this.entityData.set(CHEST, value);
    }

    // ───────────────────────────────────────────────────── INVENTORY ─────

    /**
     * How much the panniers hold. <b>27, where 1.20.1 had 15</b>, and the change is forced rather than
     * chosen: that 15 came from {@code AbstractChestedHorse}'s five columns times three rows, and the
     * horse's inventory screen is not reachable from here — {@code Player#openHorseInventory} is typed
     * to {@code AbstractHorse}. Driving a plain {@link ChestMenu} instead means the sizes it offers
     * with a custom container, which are three rows or six. Three is the closer of the two, and reads
     * as exactly what the player put on the animal: one chest.
     *
     * <p>The legacy's 17 counted its saddle and armour slots. Those are real equipment slots in 26.1
     * and live outside the container entirely, so this is 27 slots of actual storage.
     */
    private static final int INVENTORY_SIZE = 27;

    /**
     * Built on construction rather than when the chest goes on, so nothing ever has to null-check it.
     * An unchested hippo simply has a container nobody can open.
     */
    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE) {
        /**
         * Shuts the screen when the animal is no longer there to carry it. {@code SimpleContainer}
         * answers true unconditionally, which would leave a player stocking the panniers of a hippo
         * that has already died — the items would go into a container attached to nothing.
         */
        @Override
        public boolean stillValid(@NotNull Player player) {
            return HellHippoEntity.this.isAlive()
                    && HellHippoEntity.this.hasChest()
                    && player.isWithinEntityInteractionRange(HellHippoEntity.this, 4.0D);
        }
    };

    /**
     * Shift-right-click opens the panniers, for the owner only.
     *
     * <p>Not {@code ContainerEntity}, which the port spec named: that interface lives in
     * {@code world.entity.vehicle} and is built for minecarts and boats — loot tables,
     * {@code chestVehicleDestroyed}, {@code interactWithContainerVehicle}. Vanilla's own chested
     * mounts do not use it either; they carry a {@code SimpleContainer} and implement
     * {@link HasCustomInventoryScreen}, which is what this does.
     */
    @Override
    public void openCustomInventoryScreen(@NotNull Player player) {
        if (this.level().isClientSide() || !this.hasChest() || !this.isOwnedBy(player)) {
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInventory, opener) -> ChestMenu.threeRows(containerId, playerInventory, this.inventory),
                this.getDisplayName()));
    }

    /**
     * Gives the tack back when it dies. The saddle and the armour are equipment, so vanilla already
     * drops those; the chest is this mob's own flag and has to be handed back by hand.
     */
    @Override
    protected void dropEquipment(@NotNull ServerLevel level) {
        super.dropEquipment(level);
        if (!this.hasChest()) {
            return;
        }
        this.spawnAtLocation(level, new ItemStack(Items.CHEST));
        // And everything in it. Cleared as it goes so a second call — or a corpse that lingers —
        // cannot pay out twice.
        for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
            ItemStack carried = this.inventory.removeItemNoUpdate(slot);
            if (!carried.isEmpty()) {
                this.spawnAtLocation(level, carried);
            }
        }
    }

    // ───────────────────────────────────────────────────── SADDLE ─────

    /**
     * The half of "may this animal be saddled" that does not depend on who is asking — the same split
     * vanilla's horse makes. The other half (knocked out, and by its owner) lives in
     * {@link #trySaddle}, which is the only place that knows the player.
     *
     * <p><b>This is not sufficient on its own.</b> The saddle item restricts itself to the
     * {@code #minecraft:can_equip_saddle} entity type tag ({@code Equippable#saddle}), so the mob has
     * to be added to it in a datapack or {@code isEquippableInSlot} refuses no matter what this
     * returns — which is exactly why saddling did nothing at all before that tag existed.
     */
    @Override
    public boolean canUseSlot(@NotNull EquipmentSlot slot) {
        return switch (slot) {
            case SADDLE -> this.isAlive() && !this.isBaby() && this.isTame();
            // Barding goes over tack, never straight onto the animal — 1.20.1 gated it on the saddle
            // and so does this. Saying so HERE rather than only in mobInteract is what matters:
            // LivingEntity#canUseSlot answers true for everything by default, so without this a
            // dispenser, or a right-click going through the item's own equip-on-interact path, would
            // strap armour to a wild hippo that has never let anyone near it.
            case BODY -> this.isAlive() && !this.isBaby() && this.isSaddled();
            default -> super.canUseSlot(slot);
        };
    }

    /** The saddle's own click, as the horse does it. */
    @Override
    protected @NotNull Holder<SoundEvent> getEquipSound(@NotNull EquipmentSlot slot, @NotNull ItemStack stack,
                                                        @NotNull Equippable equippable) {
        return slot == EquipmentSlot.SADDLE ? SoundEvents.HORSE_SADDLE : super.getEquipSound(slot, stack, equippable);
    }

    /**
     * A tamed hippo will not bite the hand that fed it, nor anything else that hand owns.
     *
     * <p>{@code TamableAnimal#canAttack} already covers the owner, so what is added here is only the
     * owner's other pets — the case that makes a saddled hippo safe to keep beside a wolf.
     */
    @Override
    public boolean canAttack(@NotNull LivingEntity target) {
        if (!super.canAttack(target)) {
            return false;
        }
        LivingEntity owner = this.getOwner();
        return owner == null || !(target instanceof TamableAnimal pet) || !pet.isOwnedBy(owner);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SEAWEED, false);
        builder.define(INTIMIDATING, false);
        builder.define(CHEST, false);
    }

    @Override
    protected void addAdditionalSaveData(@NotNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Seaweed", this.hasSeaweed());
        output.putBoolean("Chest", this.hasChest());
        this.fearPulse.save(output, "FearCooldown");
        this.mountedAttack.save(output, "AttackCooldown");
        if (this.hasChest()) {
            // Same shape vanilla's chested mounts write: a sparse list of slot/stack pairs rather
            // than a fixed-length array, so empty panniers cost nothing on disk.
            ValueOutput.TypedOutputList<ItemStackWithSlot> carried = output.list("Items", ItemStackWithSlot.CODEC);
            for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
                ItemStack stack = this.inventory.getItem(slot);
                if (!stack.isEmpty()) {
                    carried.add(new ItemStackWithSlot(slot, stack));
                }
            }
        }
        // Persisted alongside the flag so a shear block survives a reload — otherwise quitting and
        // rejoining would be a way to skip the cooldown.
        output.putInt("SeaweedTicks", this.seaweedTicks);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setSeaweed(input.getBooleanOr("Seaweed", false));
        this.setChest(input.getBooleanOr("Chest", false));
        this.fearPulse.load(input, "FearCooldown");
        this.mountedAttack.load(input, "AttackCooldown");
        if (this.hasChest()) {
            for (ItemStackWithSlot carried : input.listOrEmpty("Items", ItemStackWithSlot.CODEC)) {
                // Guarded rather than trusted: a world saved when the panniers were bigger would
                // otherwise index past the end of the container.
                if (carried.isValidInContainer(this.inventory.getContainerSize())) {
                    this.inventory.setItem(carried.slot(), carried.stack());
                }
            }
        }
        this.seaweedTicks = input.getIntOr("SeaweedTicks", 0);
    }

    /**
     * Keeps the body pointed at the target while it is in reach.
     *
     * <p>Not cosmetic. {@code AnimatableMeleeAttackGoal} stops the navigation the moment the target
     * is within reach — exactly the ticks the bite's {@code HitWindow} sweeps on — and neither move
     * control turns the body once navigation has stopped. The hitbox is built off body yaw
     * ({@code AttackAnchor} and {@code HitWindow} both read {@code getYRot()}), so without this the
     * yaw freezes wherever the animal happened to arrive and the bite swings at empty ground while
     * the head visibly tracks the target. Same fix, same reason, as the Tangoftero's.
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

    // ───────────────────────────────────────────────────── SLEEP ─────

    /**
     * Nothing type-specific wakes it. The proximity rule below covers the case that matters, and a
     * herd animal this heavy has no particular reason to fear any one species.
     */
    @Override
    public @NotNull Set<EntityType<?>> getInterruptingEntityTypes() {
        return Set.of();
    }

    /** Anything actively hunting it, which is what {@code getTarget} pointing back at it means. */
    @Override
    public boolean shouldInterruptSleepDueTo(LivingEntity nearby) {
        return nearby instanceof Mob mob && mob.getTarget() == this;
    }

    // ───────────────────────────────────────────────────── ANIMATIONS ─────

    @Override
    public void registerAnimations() {
        StandardAnimation idle = clip("idle", () -> HellHippoAnimations.idle, () -> HellHippoBabyAnimations.idle,
                Loop.REPEATING, 3, 5.25F);
        StandardAnimation walk = clip("walk", () -> HellHippoAnimations.walk, () -> HellHippoBabyAnimations.walk,
                Loop.REPEATING, 2, 1.5F);
        StandardAnimation sprint = clip("sprint", () -> HellHippoAnimations.sprint, () -> HellHippoBabyAnimations.sprint,
                Loop.REPEATING, 2, 0.75F);
        StandardAnimation waterIdle = adultClip("water_idle", () -> HellHippoAnimations.widle, Loop.REPEATING, 3, 3.0F);
        // 1.15 is the adult's length; the calf's own swim is 1.1667. One number covers both ages
        // here, and on a REPEATING clip the sliver of difference only shifts the logical cycle
        // against the visual one, which loops by time-modulo in the interpolator regardless.
        StandardAnimation swim = clip("swim", () -> HellHippoAnimations.swim, () -> HellHippoBabyAnimations.swim,
                Loop.REPEATING, 2, 1.15F);

        StandardAnimation death = clip("death", () -> HellHippoAnimations.death, () -> HellHippoBabyAnimations.death,
                Loop.PLAY_ONCE, 0, 2.0F);

        // Three sleep phases, not six: the cycle is assembled from whichever clips a mob registers,
        // and this rig has no sitting-down set. See SleepPhase.
        // Lengths read off the clips themselves, and identical for both ages. SleepGoal takes the
        // phase durations straight from these, so a number invented here would leave the mob frozen
        // on a last frame for the difference.
        StandardAnimation preparingSleep = clip("preparing_sleep",
                () -> HellHippoAnimations.sleep_preparing, () -> HellHippoBabyAnimations.sleep_preparing,
                Loop.PLAY_ONCE, 1, 3.0F);
        StandardAnimation sleep = clip("sleep", () -> HellHippoAnimations.sleep, () -> HellHippoBabyAnimations.sleep,
                Loop.REPEATING, 1, 2.0F);
        StandardAnimation awakening = clip("awakening",
                () -> HellHippoAnimations.awakening, () -> HellHippoBabyAnimations.awakening,
                Loop.PLAY_ONCE, 1, 1.5F);
        // Trimmed from the clip's own withLength(3.5F), same reasoning as the bite above: 11 of its
        // 13 channels are already sitting at neutral by 3.0 (head 2.96, neck 3.04), so the last half
        // second was a dead neutral pose held before locomotion could take the frame back — the
        // "stiff at the end". Only torso (3.29) and the tail pair lose any settle, and the rig's
        // 220 ms crossfade covers that.
        StandardAnimation shake = clip(ANIM_SHAKE, () -> HellHippoAnimations.shake, () -> HellHippoBabyAnimations.shake,
                Loop.PLAY_ONCE, 1, 3.05F);
        // Both rigs author eat at withLength(3.5F); verified rather than assumed.
        StandardAnimation eat = clip(ANIM_EAT, () -> HellHippoAnimations.eat, () -> HellHippoBabyAnimations.eat,
                Loop.PLAY_ONCE, 1, 3.5F);
        // The stare-down, in three pieces. It arrived from 1.20.1 as ONE 7.5-second clip and was
        // registered that way at first, which looked wrong for a reason no amount of looping could
        // fix: a 7.5-second clip covering a 15-second window has to restart, and the restart passes
        // through the settle its own last second is made of — so the animal visibly relaxed out of
        // the pose and snapped back into it, twice per standoff.
        //
        // Cutting it the way any looping action wants to be cut solves it outright. The entry and
        // the release are the parts that must not repeat; the middle is the only part that should.
        // Adult only: a calf never gets here, because the trust ritual refuses one.
        StandardAnimation intimidateIn = adultClip(ANIM_INTIMIDATE_IN,
                () -> HellHippoAnimations.intimidate_in, Loop.PLAY_ONCE, 1, 0.65F);
        StandardAnimation intimidateLoop = adultClip(ANIM_INTIMIDATE_LOOP,
                () -> HellHippoAnimations.intimidate_loop, Loop.REPEATING, 1, 2.0F);
        StandardAnimation intimidateOut = adultClip(ANIM_INTIMIDATE_OUT,
                () -> HellHippoAnimations.intimidate_out, Loop.PLAY_ONCE, 1, 0.95F);

        // The lunge AnimatableMeleeAttackGoal looks up by name, and what the HitWindow below is
        // applied to. Priority 0 so it wins over locomotion, which keeps running underneath.
        StandardAnimation attack = clip(ANIM_ATTACK, () -> HellHippoAnimations.attack, () -> HellHippoBabyAnimations.attack,
                Loop.PLAY_ONCE, 0, ATTACK_SECONDS);

        // The damage is here, not in the goal: the goal only decides WHEN to commit, and the window
        // sweeps a box on the frames the jaws are actually closing. The 1.20.1 version instead ran a
        // parallel 13-tick counter inside the goal and called doHurtTarget when it hit zero, which is
        // why the hit and the visible bite drifted apart.
        HitWindow.of(ATTACK_WINDOW_START, ATTACK_WINDOW_END)
                // box3d and not box: box ignores the Y axis outright (AttackShape's interface note
                // says so — "a ground mob's swing reaches whatever is in front of it, at any height"),
                // and the only vertical bound it has is the broad-phase AABB at ±3.8. On land that
                // never showed, because targets stand on the same floor the hippo does. Underwater
                // they do not, and a player swimming three blocks overhead was taking damage from a
                // bite that visibly never reached them.
                //
                // The half-height of 1.5 is measured from the anchor at 0.9, so it covers −0.6 to
                // +2.4 above the hippo's feet — anything standing on its own ground, plus a step or
                // two of slope, and nothing floating clear above it. Note box3d tests the target's
                // CENTRE (position + bbHeight/2) where box tested its feet, so the line sits at the
                // middle of the victim's body.
                //
                // A look number: adjust it against the render.
                .shape(AttackShape.box3d(2.6F, 1.1F, 1.5F))
                .anchor(1.9F, 0.0F, 0.9F)
                .damage((float) this.getAttributeValue(Attributes.ATTACK_DAMAGE))
                .knockback(0.6F)
                // Hippos CAN maul each other, deliberately. The species exclusion that used to sit
                // here spared any hippo that was not the current target, on the reasoning that a bite
                // this wide would otherwise catch the calf standing beside its mother — but it also
                // made a fight between two of them impossible to resolve with anything but the
                // declared target, which is not how a bite that sweeps a box works. Only the rider is
                // still spared, since biting your own passenger is never intended.
                .filter(target -> !this.hasPassenger(target))
                .applyTo(attack);

        // Exactly one of these holds at any moment: deep enough for the water set or not, moving or
        // not. Both ages now have their own walk, so the split is purely by speed.
        idle.setPlayCondition(a -> this.canPlayLocomotion() && !this.isSwimDeep() && !this.isMoving());
        // isRunning(), not isAggressive(): a mount whose rider is holding sprint is running too, and
        // that flag is synced so the condition still agrees on both sides.
        walk.setPlayCondition(a -> this.canPlayLocomotion() && !this.isSwimDeep() && this.isMoving()
                && !this.isRunning());
        sprint.setPlayCondition(a -> this.canPlayLocomotion() && !this.isSwimDeep() && this.isMoving()
                && this.isRunning());
        // The calf has no authored water idle, so it uses the swim clip whenever it is in water.
        waterIdle.setPlayCondition(a -> this.canPlayLocomotion() && this.isSwimDeep() && !this.isMoving()
                && !this.isBaby());
        swim.setPlayCondition(a -> this.canPlayLocomotion() && this.isSwimDeep()
                && (this.isMoving() || this.isBaby()));

        // The two transitions are PLAY_ONCE, and the animator's auto-start loop only ever starts
        // REPEATING clips — so these conditions do NOT start them; SleepGoal does, through
        // onSleepPhaseBegin. What they buy is the reverse: a clip whose condition goes false is cut,
        // so a hippo shaken awake mid-settle drops the clip instead of finishing it.
        preparingSleep.setPlayCondition(a -> this.isPreparingSleep());
        awakening.setPlayCondition(a -> this.isAwakening());
        // Eligible through the settling phase too, so the loop is armed the instant the settle clip
        // ends whichever way the two clocks land — same as the Tangoftero.
        sleep.setPlayCondition(a -> this.isSleeping() || this.isPreparingSleep());
        preparingSleep.setNextAnimation("sleep");

        shake.setPlayCondition(a -> this.isPerforming(ANIM_SHAKE));
        eat.setPlayCondition(a -> this.isPerforming(ANIM_EAT));
        // Not a scripted action like the shake: this one follows a synced STATE, because the mob has
        // to hold the pose for the whole 15-second window rather than play a gesture once. Priority 1
        // so it sits over locomotion but under the bite.
        // Same shape as the sleep chain above, and for the same reason: the two one-shots are started
        // by hand (startAction, from startIntimidating/stopIntimidating) because the animator's
        // auto-start only ever picks up REPEATING clips. Their conditions exist to CUT them — a
        // standoff that ends mid-entry drops the clip instead of finishing it.
        intimidateIn.setPlayCondition(a -> this.isPerforming(ANIM_INTIMIDATE_IN));
        intimidateOut.setPlayCondition(a -> this.isPerforming(ANIM_INTIMIDATE_OUT));
        // The loop is the one that auto-starts, and it must not do so until the entry has finished —
        // hence the explicit exclusion rather than relying on the chain alone.
        // Not while it is out cold either: the state survives the potion so the standoff can resume
        // on waking, but a hippo lying down should be playing the sleep clip, not posturing.
        intimidateLoop.setPlayCondition(a -> this.isIntimidating()
                && !this.isInSleepCycle()
                && !this.isPerforming(ANIM_INTIMIDATE_IN));
        intimidateIn.setNextAnimation(ANIM_INTIMIDATE_LOOP);

        // Stops the rig's look-at from still tracking with a corpse's neck while the death clip runs.
        death.blockAdditive();

        this.animator().register(idle, walk, sprint, waterIdle, swim,
                preparingSleep, sleep, awakening, shake, eat,
                intimidateIn, intimidateLoop, intimidateOut, attack);
        // registerDeath, not register: it also makes the clip non-interruptible and holds the corpse
        // in the world for its full length instead of vanilla's fixed 20 ticks. Priority 0 so it wins
        // over locomotion, which keeps running underneath.
        this.animator().registerDeath(death);
    }

    /**
     * Locomotion stays eligible under one-shots on purpose: what shows through a finished PLAY_ONCE
     * clip is not its last frame but the bind pose, so leaving idle running underneath means there is
     * always something to fall back to. Same reasoning as the Tangoftero's.
     */
    private boolean canPlayLocomotion() {
        return !this.isDeadOrDying();
    }

    /**
     * A clip whose definition is chosen by age <b>lazily</b>. The suppliers are not stylistic:
     * {@code AnimationDefinition} is {@code @OnlyIn(Dist.CLIENT)} and {@code registerAnimations()}
     * runs on both sides, so naming the field directly here would load a client class and kill a
     * dedicated server.
     */
    private StandardAnimation clip(String name, Supplier<Object> adult, Supplier<Object> baby,
                                   Loop loop, int priority, float seconds) {
        return new StandardAnimation(name,
                new AnimSource(() -> this.isBaby() ? baby.get() : adult.get()), loop, 0, priority, seconds);
    }

    /** For clips the calf mesh has no counterpart for — see {@code HellHippoBabyModel}. */
    private StandardAnimation adultClip(String name, Supplier<Object> adult, Loop loop, int priority, float seconds) {
        return new StandardAnimation(name, new AnimSource(adult), loop, 0, priority, seconds);
    }

    // ───────────────────────────────────────────────────── SPAWN ─────

    /**
     * Hell hippos arrive alone — except a cow, who may bring a calf.
     *
     * <p>This replaces the herd that was here before. That one elected a leader the rest trailed,
     * which never worked reliably: the leader was whichever member a spatial query happened to
     * return first, and the "who elects" tiebreak was the lowest entity id <em>within each member's
     * own neighbourhood</em>, so members on opposite edges of a loose group saw different
     * neighbourhoods, elected different leaders, and split the herd. A mother-and-calf pair gets the
     * same read on screen — hippos are not solitary — out of state that cannot disagree with itself.
     */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level,
                                                  @NotNull DifficultyInstance difficulty,
                                                  @NotNull EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData spawnData) {
        this.setMale(this.getRandom().nextBoolean());
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);
        if (!this.isMale() && !this.isBaby() && this.getRandom().nextFloat() < CALF_COMPANION_CHANCE) {
            this.spawnCompanionCalf(level);
        }
        return data;
    }

    /**
     * Puts a calf beside its mother.
     *
     * <p>Built with {@code create} and added by hand rather than through {@code EntityType#spawn},
     * because that route runs {@link #finalizeSpawn} on the calf — which would roll its sex, find a
     * cow, and spawn a calf of its own, and so on. Going around it makes the recursion structurally
     * impossible rather than merely unlikely, which is why the calf's sex is rolled here explicitly.
     */
    private void spawnCompanionCalf(ServerLevelAccessor level) {
        HellHippoEntity calf = SMOPEntities.HELL_HIPPO.get().create(level.getLevel(), EntitySpawnReason.NATURAL);
        if (calf == null) {
            return;
        }
        calf.setMale(this.getRandom().nextBoolean());
        calf.setBaby(true);
        calf.snapTo(this.getX() + (this.getRandom().nextDouble() - 0.5D) * 2.0D, this.getY(),
                this.getZ() + (this.getRandom().nextDouble() - 0.5D) * 2.0D, this.getYRot(), 0.0F);
        level.addFreshEntity(calf);
    }

    /**
     * Ordinary animal spawn rules — grass-type ground under it, bright enough — same as the
     * Tangoftero's and the Kriftognathus's.
     *
     * <p>Registering this at all is what stops the mob spawning in nonsense places: without an entry
     * in {@code RegisterSpawnPlacementsEvent}, {@code SpawnPlacements#getPlacementType} falls back to
     * {@code NO_RESTRICTIONS} and {@code checkSpawnRules} returns true unconditionally
     * ({@code SpawnPlacements#getPlacementType} y {@code #checkSpawnRules}), so the biome entry alone would put hippos wherever the
     * spawner happened to pick.
     *
     * <p>Deliberately not loosened for the mangrove swamp. Its floor is mud, which is not in
     * {@code #minecraft:animals_spawnable_on}, so hippos will only appear on the grass patches that
     * biome does have. That makes them rarer there than in an open savanna, which is the right
     * outcome for an animal this size — it should not be crawling out of every mudflat.
     */
    public static boolean checkHellHippoSpawnRules(EntityType<HellHippoEntity> type, ServerLevelAccessor level,
                                                   EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return checkAnimalSpawnRules(type, level, reason, pos, random);
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob partner) {
        HellHippoEntity calf = SMOPEntities.HELL_HIPPO.get().create(level, EntitySpawnReason.BREEDING);
        if (calf != null) {
            // Rolled here rather than left to finalizeSpawn: a bred calf never goes through it.
            calf.setMale(this.getRandom().nextBoolean());
        }
        return calf;
    }

    @Override
    public boolean isFood(@NotNull ItemStack stack) {
        return stack.is(Items.CARROT) || stack.is(Items.BEEF);
    }

    // ───────────────────────────────────────────────────── SOUNDS ─────

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return SoundEvents.HOGLIN_AMBIENT;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(@NotNull DamageSource source) {
        return SoundEvents.HOGLIN_HURT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return SoundEvents.HOGLIN_DEATH;
    }
}

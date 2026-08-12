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
import net.darkblade.smop.entity.sleep.ISleepAwareness;
import net.darkblade.smop.entity.sleep.ISleepThreatEvaluator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
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
import net.darkblade.smop.entity.ai.navigation.SeabedPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
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
        implements ISleepThreatEvaluator, ISleepAwareness {

    /** Tempts and breeds. Carrot and beef, as in 1.20.1. */
    private static final Ingredient FOOD_ITEMS = Ingredient.of(Items.CARROT, Items.BEEF);

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

    /** The idle gesture: a full-body shake. @see #registerGoals() */
    private static final String ANIM_SHAKE = "shake";
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
                // this attribute (LivingEntity.java:2233-2241): at 1.0 and standing on the bottom,
                // drag goes 0.8 -> 0.546 and acceleration 0.02 -> getSpeed(), i.e. exactly the
                // numbers the animal walks with on dry land. 1.0 is also the attribute's declared
                // maximum, so this is the ceiling, not an arbitrary pick.
                //
                // It already exists on every living entity by way of createLivingAttributes()
                // (LivingEntity.java:338) with a default of 0; re-adding it here overwrites that
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
                .onAttack((target, animator) -> animator.play(animator.getByName("attack"))));
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
                .add(ANIM_SHAKE)
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
        }
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
     * {@code MoveControl} calls {@code getJumpControl().jump()} ({@code MoveControl.java:104-111}),
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
     * {@code Entity#updateFluidHeightAndDoFluidPushing} fills it with
     * {@code max(fluidSurface - boundingBox.minY)}, in blocks above the feet.
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

    /** Shears strip the algae for 2 kelp. Everything else falls through to the base animal. */
    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
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
        return super.mobInteract(player, hand);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SEAWEED, false);
    }

    @Override
    protected void addAdditionalSaveData(@NotNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Seaweed", this.hasSeaweed());
        // Persisted alongside the flag so a shear block survives a reload — otherwise quitting and
        // rejoining would be a way to skip the cooldown.
        output.putInt("SeaweedTicks", this.seaweedTicks);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setSeaweed(input.getBooleanOr("Seaweed", false));
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

    /** A player walking up is enough to rouse it — it is not a deep sleeper. */
    @Override
    public boolean shouldWakeOnPlayerProximity() {
        return true;
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
        StandardAnimation shake = clip(ANIM_SHAKE, () -> HellHippoAnimations.shake, () -> HellHippoBabyAnimations.shake,
                Loop.PLAY_ONCE, 1, 3.5F);

        // The lunge AnimatableMeleeAttackGoal looks up by name, and what the HitWindow below is
        // applied to. Priority 0 so it wins over locomotion, which keeps running underneath.
        StandardAnimation attack = clip("attack", () -> HellHippoAnimations.attack, () -> HellHippoBabyAnimations.attack,
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
                // A wide bite from a body this size would otherwise maul any hippo standing nearby,
                // including the calf beside its mother. Same exclusion the Krifto carries, and for
                // the same reason: it still allows hitting one that IS the current target.
                .filter(target -> target == this.getTarget() || !(target instanceof HellHippoEntity))
                .applyTo(attack);

        // Exactly one of these holds at any moment: deep enough for the water set or not, moving or
        // not. Both ages now have their own walk, so the split is purely by speed.
        idle.setPlayCondition(a -> this.canPlayLocomotion() && !this.isSwimDeep() && !this.isMoving());
        walk.setPlayCondition(a -> this.canPlayLocomotion() && !this.isSwimDeep() && this.isMoving()
                && !this.isAggressive());
        sprint.setPlayCondition(a -> this.canPlayLocomotion() && !this.isSwimDeep() && this.isMoving()
                && this.isAggressive());
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

        // Stops the rig's look-at from still tracking with a corpse's neck while the death clip runs.
        death.blockAdditive();

        this.animator().register(idle, walk, sprint, waterIdle, swim,
                preparingSleep, sleep, awakening, shake, attack);
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

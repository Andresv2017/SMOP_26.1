package net.darkblade.smop.entity.hellhippo;

import net.darkblade.deluxelib.anim.AnimSource;
import net.darkblade.deluxelib.anim.Loop;
import net.darkblade.deluxelib.anim.StandardAnimation;
import net.darkblade.deluxelib.entity.ai.pathing.DirectionalMoveControl;
import net.darkblade.deluxelib.entity.ai.rotation.SmoothBodyRotationControl;
import net.darkblade.smop.client.hellhippo.HellHippoAnimations;
import net.darkblade.smop.client.hellhippo.HellHippoBabyAnimations;
import net.darkblade.smop.entity.GenderedSMOPAnimal;
import net.darkblade.smop.entity.SMOPEntities;
import net.darkblade.smop.entity.ai.goal.IdleAnimationGoal;
import net.darkblade.smop.entity.ai.goal.SMOPRandomStrollGoal;
import net.darkblade.smop.entity.sleep.ISleepAwareness;
import net.darkblade.smop.entity.sleep.ISleepThreatEvaluator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Supplier;

/**
 * The Hell Hippo: a heavy, solitary amphibious brute that can be befriended, saddled and ridden.
 *
 * <p><b>Port status — phases 1a and 1b of the port spec.</b> The animal itself: geometry, attributes,
 * locomotion, the sleep cycle, breeding and its idle gesture. Combat lands in 1c, the
 * trust-and-saddle chain in phase 2, and the rider systems (steering, intimidation pulse, mounted
 * attack) in phase 3.
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

    /** How often a spawning cow brings a calf along. @see #finalizeSpawn */
    private static final float CALF_COMPANION_CHANCE = 0.50F;

    /** The idle gesture: a full-body shake. @see #registerGoals() */
    private static final String ANIM_SHAKE = "shake";
    /** Floor between shakes, plus its spread: 45 to 90 seconds. */
    private static final int SHAKE_COOLDOWN_TICKS = 900;
    private static final int SHAKE_COOLDOWN_SPREAD_TICKS = 900;

    public HellHippoEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        // Vanilla's MoveControl snaps a mob's yaw to its next waypoint, which on something this
        // heavy reads as the whole animal pivoting on the spot. DirectionalMoveControl caps the turn
        // instead. 6 degrees a tick against the Tangoftero's 10: the hippo is a far bigger body and
        // should feel like it has to commit to a turn.
        this.moveControl = new DirectionalMoveControl<>(this).setTurnSpeed(6.0F).setCombatTurnSpeed(20.0F);
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
                .add(Attributes.ARMOR);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Sleep sits directly under Float and above everything else: it holds MOVE, so anything
        // below it stops being able to steer a sleeping animal. Drowning still wins.
        this.goalSelector.addGoal(1, this.createSleepGoal());
        // Vanilla's BreedGoal, not GenericBreedGoal: that one exists for the egg layers and finishes
        // mating with finalizeSpawnChildFromBreeding(..., null) — hearts and experience but no
        // offspring, which is exactly what "they breed but no calf appears" looked like. A hippo
        // bears live young, so the plain vanilla goal is the correct one.
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.15D));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.2D, FOOD_ITEMS, false));
        this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.1D));
        this.goalSelector.addGoal(7, new SMOPRandomStrollGoal(this, 1.0D, 120, () -> !this.isMovementLocked()));
        // Flagless, so its priority is presentational — see IdleAnimationGoal's class note.
        this.goalSelector.addGoal(8, new IdleAnimationGoal(this, SHAKE_COOLDOWN_TICKS, SHAKE_COOLDOWN_SPREAD_TICKS)
                .add(ANIM_SHAKE));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
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

        // Exactly one of these holds at any moment: in or out of water, moving or not. Both ages now
        // have their own walk, so the split is purely by speed.
        idle.setPlayCondition(a -> this.canPlayLocomotion() && !this.isInWater() && !this.isMoving());
        walk.setPlayCondition(a -> this.canPlayLocomotion() && !this.isInWater() && this.isMoving()
                && !this.isAggressive());
        sprint.setPlayCondition(a -> this.canPlayLocomotion() && !this.isInWater() && this.isMoving()
                && this.isAggressive());
        // The calf has no authored water idle, so it uses the swim clip whenever it is in water.
        waterIdle.setPlayCondition(a -> this.canPlayLocomotion() && this.isInWater() && !this.isMoving()
                && !this.isBaby());
        swim.setPlayCondition(a -> this.canPlayLocomotion() && this.isInWater()
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
                preparingSleep, sleep, awakening, shake);
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

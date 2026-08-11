package net.darkblade.smop.entity.hellhippo;

import net.darkblade.deluxelib.anim.AnimSource;
import net.darkblade.deluxelib.anim.Loop;
import net.darkblade.deluxelib.anim.StandardAnimation;
import net.darkblade.smop.client.hellhippo.HellHippoAnimations;
import net.darkblade.smop.client.hellhippo.HellHippoBabyAnimations;
import net.darkblade.smop.entity.GenderedSMOPAnimal;
import net.darkblade.smop.entity.SMOPEntities;
import net.darkblade.smop.entity.ai.goal.SMOPRandomStrollGoal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
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
 * The Hell Hippo: a heavy, herd-living amphibious brute that can be befriended, saddled and ridden.
 *
 * <p><b>Port status — phase 1a of the port spec.</b> What is here is the animal itself: geometry,
 * attributes, locomotion. Sleep, herd behaviour and combat land in 1b/1c, the trust-and-saddle chain
 * in phase 2, and the rider systems (steering, intimidation pulse, mounted attack) in phase 3.
 *
 * <p><b>Why it does not extend {@code AbstractChestedHorse}</b>, as the 1.20.1 version did: that
 * class descends from {@code Animal} while {@link net.darkblade.smop.entity.SMOPAnimal} descends
 * from {@link TamableAnimal}, so they are sibling branches and only one is reachable. Taking the
 * horse meant reimplementing sleep, gender, animation and herd logic by hand inside the mob — which
 * is exactly why the legacy file grew to 1461 lines in a single class. Inheriting from SMOP's base
 * instead gets all of that for free, at the cost of writing the saddle, the steering and the chest,
 * which phase 2 covers. The Nirasmosaurus is rideable too, so that work is used twice.
 */
public class HellHippoEntity extends GenderedSMOPAnimal {

    public HellHippoEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    /** Straight from the 1.20.1 build — deliberately unchanged, so the port is comparable in play. */
    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createLivingAttributes()
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
        // Phase 1a keeps this deliberately thin: enough for the animal to be alive and observable
        // while the clips are being checked. The herd, sleep and combat goals arrive in 1b and 1c.
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(7, new SMOPRandomStrollGoal(this, 1.0D, 120, () -> !this.isMovementLocked()));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    // ───────────────────────────────────────────────────── ANIMATIONS ─────

    @Override
    public void registerAnimations() {
        StandardAnimation idle = clip("idle", () -> HellHippoAnimations.idle, () -> HellHippoBabyAnimations.idle,
                Loop.REPEATING, 3, 5.25F);
        StandardAnimation walk = adultClip("walk", () -> HellHippoAnimations.walk, Loop.REPEATING, 2, 1.5F);
        StandardAnimation sprint = clip("sprint", () -> HellHippoAnimations.sprint, () -> HellHippoBabyAnimations.sprint,
                Loop.REPEATING, 2, 0.75F);
        StandardAnimation waterIdle = adultClip("water_idle", () -> HellHippoAnimations.widle, Loop.REPEATING, 3, 3.0F);
        // 1.15 is the adult's length; the calf's own swim is 1.1667. One number covers both ages
        // here, and on a REPEATING clip the sliver of difference only shifts the logical cycle
        // against the visual one, which loops by time-modulo in the interpolator regardless.
        StandardAnimation swim = clip("swim", () -> HellHippoAnimations.swim, () -> HellHippoBabyAnimations.swim,
                Loop.REPEATING, 2, 1.15F);

        // Exactly one of these holds at any moment: in or out of water, moving or not. The calf has
        // no authored walk, so it sprints whenever it moves on land — hence walk being adult-only and
        // its condition carrying the extra isBaby() guard rather than the pair splitting on speed.
        idle.setPlayCondition(a -> this.canPlayLocomotion() && !this.isInWater() && !this.isMoving());
        walk.setPlayCondition(a -> this.canPlayLocomotion() && !this.isInWater() && this.isMoving()
                && !this.isBaby() && !this.isAggressive());
        sprint.setPlayCondition(a -> this.canPlayLocomotion() && !this.isInWater() && this.isMoving()
                && (this.isBaby() || this.isAggressive()));
        waterIdle.setPlayCondition(a -> this.canPlayLocomotion() && this.isInWater() && !this.isMoving()
                && !this.isBaby());
        swim.setPlayCondition(a -> this.canPlayLocomotion() && this.isInWater()
                && (this.isMoving() || this.isBaby()));

        this.animator().register(idle, walk, sprint, waterIdle, swim);
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

    /**
     * Empty for now. The sleep cycle proper — including what is allowed to wake this thing up — is
     * phase 1b; nothing here would be meaningful before the herd and threat goals exist.
     */
    @Override
    public @NotNull Set<EntityType<?>> getInterruptingEntityTypes() {
        return Set.of();
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

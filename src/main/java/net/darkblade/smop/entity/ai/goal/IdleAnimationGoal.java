package net.darkblade.smop.entity.ai.goal;

import net.darkblade.smop.entity.SMOPAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Plays a purely cosmetic gesture — a call, a stretch, a shake — every so often while the animal is
 * standing around doing nothing. Reusable: any {@link SMOPAnimal} can register one.
 *
 * <pre>{@code
 * this.goalSelector.addGoal(10, new IdleAnimationGoal(this, 120, 120)
 *         .add("squawk", 10)   // common
 *         .add("stretch", 3)   // occasional
 *         .add("yawn", 1)      // rare
 *         .condition(mob -> !this.isFlying()));
 * }</pre>
 *
 * <p><b>Sound is not here.</b> Each clip carries its own via {@code AnimSound} at registration, so a
 * pool of three gestures has three different sounds (or two sounds inside one gesture, or none)
 * without this goal knowing anything about audio. Volume and pitch are per sound, so a quiet yawn and
 * a loud call coexist. That separation is deliberate — audio driven from a second place is exactly
 * what desynchronised the Kriftognathus call before {@code AnimSound} existed.
 *
 * <p><b>Why a goal and not {@code playAmbientSound()}</b>, which is where the Kriftognathus call used
 * to live: that is vanilla's hook for a mob's ambient <em>noise</em>, and it owns its own cadence —
 * {@code Mob#baseTick} resets its timer as soon as its roll passes, <em>before</em> handing over, so
 * every roll that lands while the mob is walking or flying is silently spent and the real frequency
 * is not the one the interval advertises. Owning the timer here makes the spacing explicit,
 * inspectable and per-animal.
 *
 * <p><b>No goal flags.</b> A cosmetic gesture moves the animal nowhere, so reserving MOVE or LOOK
 * would block real behaviours for nothing; the resting requirement below is the only restriction that
 * actually matters. One consequence: the priority this is registered at does not affect arbitration
 * at all (with no flags, {@code GoalSelector} never has a reason to hold it back). Register it last
 * for readability, not for precedence.
 */
public class IdleAnimationGoal extends Goal {

    private final SMOPAnimal mob;
    private final int cooldownTicks;
    private final int cooldownSpreadTicks;

    private final List<String> clips = new ArrayList<>();
    private final List<Integer> weights = new ArrayList<>();
    private int totalWeight;

    private Predicate<SMOPAnimal> condition = animal -> true;

    /** {@link #nextAllowedTick} before the first evaluation has had a chance to set it. */
    private static final int UNARMED = -1;

    /** Earliest {@code tickCount} the next gesture may start. Not persisted — see the class note. */
    private int nextAllowedTick = UNARMED;
    /** The clip this goal started, while it is still running. */
    @Nullable
    private String playing;

    /**
     * @param cooldownTicks       floor between two gestures, measured from the start of one to the
     *                            start of the next
     * @param cooldownSpreadTicks random extra on top, so the gestures do not come out metronomic
     */
    public IdleAnimationGoal(@NotNull SMOPAnimal mob, int cooldownTicks, int cooldownSpreadTicks) {
        this.mob = mob;
        this.cooldownTicks = cooldownTicks;
        // nextInt demands a positive bound; 0 spread is a legitimate "always exactly this often".
        this.cooldownSpreadTicks = Math.max(1, cooldownSpreadTicks);
    }

    /** Adds a gesture with weight 1. @see #add(String, int) */
    @NotNull
    public IdleAnimationGoal add(@NotNull String clip) {
        return this.add(clip, 1);
    }

    /**
     * Adds a gesture to the pool. One is drawn at random per gesture, in proportion to its weight —
     * so {@code add("a", 3).add("b", 1)} shows "a" three times as often as "b".
     *
     * <p>A single shared cooldown governs the whole pool. That is the difference from registering one
     * goal per gesture, where each would keep its own timer and two different gestures could land back
     * to back.
     */
    @NotNull
    public IdleAnimationGoal add(@NotNull String clip, int weight) {
        if (weight <= 0) {
            throw new IllegalArgumentException("weight must be positive, got " + weight + " for " + clip);
        }
        this.clips.add(clip);
        this.weights.add(weight);
        this.totalWeight += weight;
        return this;
    }

    /**
     * Extra gate on top of the resting requirement, for whatever is specific to this animal — a
     * flyer adding {@code mob -> !isFlying()}, say. Kept as a hook rather than a built-in because
     * {@code isFlying()} lives on {@code SMOPFlyingAnimal}, not on {@link SMOPAnimal}.
     */
    @NotNull
    public IdleAnimationGoal condition(@NotNull Predicate<SMOPAnimal> condition) {
        this.condition = condition;
        return this;
    }

    @Override
    public boolean canUse() {
        if (this.clips.isEmpty()) {
            return false;
        }
        if (this.nextAllowedTick == UNARMED) {
            // Start the clock instead of firing. A mob that has just entered the world has a
            // tickCount of 0, so a cooldown left at its own zero default is already satisfied — and
            // every freshly spawned animal greeted the world with a gesture on its first tick.
            // Arming here rather than in the constructor keeps this off the entity's construction
            // path, which runs registerGoals() before the entity is fully built.
            this.armCooldown();
            return false;
        }
        return this.mob.tickCount >= this.nextAllowedTick
                // "Nothing else is going on." Both of these belong to starting only, never to
                // continuing: the moment this goal starts, the mob IS performing an action and MAY
                // be movement-locked by it — a gesture that pins the animal in place while it plays
                // is perfectly normal, and {@code actionLocksMovement} defaults to true. Testing
                // either of them again while running made the gesture cancel itself on the very next
                // evaluation, which is why nothing was ever seen playing.
                && !this.mob.isPerformingAction()
                && !this.mob.isMovementLocked()
                && this.stillIdle();
    }

    @Override
    public boolean canContinueToUse() {
        return this.playing != null && this.mob.isPerforming(this.playing) && this.stillIdle();
    }

    /**
     * Still got nothing better to do. Deliberately says nothing about action state or the movement
     * lock — see {@link #canUse()} — so a gesture that pins the mob does not read as a reason to
     * abandon that same gesture. Sleep is still covered, because falling asleep mid-gesture should
     * cut it.
     */
    private boolean stillIdle() {
        return !this.mob.isMoving()
                && this.mob.getTarget() == null
                && !this.mob.isInSleepCycle()
                && this.condition.test(this.mob);
    }

    @Override
    public void start() {
        this.playing = this.pickClip();
        this.armCooldown();
        this.mob.startAction(this.playing);
    }

    /** Pushes the next gesture out by the configured floor plus its random spread. */
    private void armCooldown() {
        this.nextAllowedTick = this.mob.tickCount + this.cooldownTicks
                + this.mob.getRandom().nextInt(this.cooldownSpreadTicks);
    }

    @Override
    public void stop() {
        // Only ever cancel our own gesture. By the time this runs the clip may have finished on its
        // own, or something else may have started an action of its own in the meantime — cancelling
        // blind would cut short a meal that has nothing to do with this goal.
        if (this.playing != null && this.mob.isPerforming(this.playing)) {
            this.mob.stopAction();
        }
        this.playing = null;
    }

    private String pickClip() {
        if (this.clips.size() == 1) {
            return this.clips.get(0);
        }
        int roll = this.mob.getRandom().nextInt(this.totalWeight);
        for (int i = 0; i < this.clips.size(); i++) {
            roll -= this.weights.get(i);
            if (roll < 0) {
                return this.clips.get(i);
            }
        }
        // Unreachable: the weights sum to totalWeight and roll starts below it.
        return this.clips.get(this.clips.size() - 1);
    }
}

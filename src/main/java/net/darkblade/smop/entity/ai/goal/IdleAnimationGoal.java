package net.darkblade.smop.entity.ai.goal;

import net.darkblade.smop.entity.SMOPAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class IdleAnimationGoal extends Goal {

    private final SMOPAnimal mob;
    private final int cooldownTicks;
    private final int cooldownSpreadTicks;

    private final List<String> clips = new ArrayList<>();
    private final List<Integer> weights = new ArrayList<>();
    private int totalWeight;

    private Predicate<SMOPAnimal> condition = animal -> true;

    private static final int UNARMED = -1;

    private int nextAllowedTick = UNARMED;
    @Nullable
    private String playing;

    public IdleAnimationGoal(@NotNull SMOPAnimal mob, int cooldownTicks, int cooldownSpreadTicks) {
        this.mob = mob;
        this.cooldownTicks = cooldownTicks;
        // nextInt demands a positive bound; 0 spread is a legitimate "always exactly this often".
        this.cooldownSpreadTicks = Math.max(1, cooldownSpreadTicks);
    }

    @NotNull
    public IdleAnimationGoal add(@NotNull String clip) {
        return this.add(clip, 1);
    }

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

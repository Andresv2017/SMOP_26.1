package net.darkblade.smop.entity.tame;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TameProgress {

    private final TamableAnimal mob;
    private final int goalMin;
    private final int goalMax;

    private int progress;

    private int goal;

    public TameProgress(@NotNull TamableAnimal mob, int goalMin, int goalMax) {
        if (goalMin < 1 || goalMax < goalMin) {
            throw new IllegalArgumentException(
                    "goalMin must be >= 1 and goalMax >= goalMin, got " + goalMin + ".." + goalMax);
        }
        this.mob = mob;
        this.goalMin = goalMin;
        this.goalMax = goalMax;
    }

    public boolean feed(@Nullable Entity feeder) {
        if (this.progress == 0) {
            this.goal = this.goalMin + this.mob.getRandom().nextInt(this.goalMax - this.goalMin + 1);
        }
        this.progress++;
        if (this.progress < this.goal || !(feeder instanceof Player player)) {
            return false;
        }
        this.mob.tame(player);
        // Vanilla's taming hearts. Part of "who owns it now", so it belongs here; the mob's own
        // reaction — a clip, a sound — does not, and stays with the caller.
        this.mob.level().broadcastEntityEvent(this.mob, (byte) 7);
        return true;
    }

    public int progress() {
        return this.progress;
    }

    public int goal() {
        return this.goal;
    }

    public boolean hasStarted() {
        return this.progress > 0;
    }

    public void save(@NotNull ValueOutput output) {
        output.putInt("FeedProgress", this.progress);
        output.putInt("FeedGoal", this.goal);
    }

    public void load(@NotNull ValueInput input) {
        this.progress = input.getIntOr("FeedProgress", 0);
        this.goal = input.getIntOr("FeedGoal", 0);
    }
}

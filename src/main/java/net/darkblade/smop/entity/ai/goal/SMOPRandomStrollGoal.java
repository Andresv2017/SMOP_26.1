package net.darkblade.smop.entity.ai.goal;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;

import java.util.function.BooleanSupplier;

public class SMOPRandomStrollGoal extends RandomStrollGoal {

    private final BooleanSupplier canRun;

    public SMOPRandomStrollGoal(PathfinderMob mob, double speedModifier, int interval, BooleanSupplier canRun) {
        super(mob, speedModifier, interval);
        this.canRun = canRun;
    }

    @Override
    public boolean canUse() {
        return this.canRun.getAsBoolean() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canRun.getAsBoolean() && super.canContinueToUse();
    }
}

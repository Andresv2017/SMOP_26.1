package net.darkblade.smop.entity.ai.goal;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;

import java.util.function.BooleanSupplier;

public class SMOPRandomSwimmingGoal extends RandomSwimmingGoal {

    private final BooleanSupplier canRun;

    public SMOPRandomSwimmingGoal(PathfinderMob mob, double speedModifier, int interval, BooleanSupplier canRun) {
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

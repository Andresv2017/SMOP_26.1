package net.darkblade.smop.entity.ai.goal;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;

import java.util.function.BooleanSupplier;

/**
 * {@link RandomStrollGoal} with one extra caller-supplied condition — perched on a player's head,
 * being ridden, minding a nest, and so on.
 *
 * <p>Sleep is <em>not</em> among those conditions: {@code SleepGoal} holds MOVE, so the selector
 * already keeps this goal from running while the mob is asleep.
 */
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

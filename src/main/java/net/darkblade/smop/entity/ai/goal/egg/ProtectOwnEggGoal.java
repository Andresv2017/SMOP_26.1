package net.darkblade.smop.entity.ai.goal.egg;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public class ProtectOwnEggGoal extends ProtectEggBaseGoal {

    public ProtectOwnEggGoal(Animal mob, int stayNearEggRadius, int defenseRadius,
                             boolean attackOnApproach, boolean attackOnBreak,
                             Predicate<LivingEntity> enemySelector, EggBreakReaction eggBreakReaction) {
        super(mob, stayNearEggRadius, defenseRadius, attackOnApproach, attackOnBreak,
                enemySelector, eggBreakReaction);
    }

    @Override
    protected @Nullable BlockPos findTargetEgg() {
        return this.targetEggPos;
    }

    public void assignEgg(BlockPos pos) {
        this.targetEggPos = pos.immutable();
    }
}

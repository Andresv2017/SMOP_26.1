package net.darkblade.smop.entity.ai.goal.egg;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Guards the one nest this mob laid. It never searches — {@code GenericLayEggGoal} hands it the
 * position at the moment the egg is placed, so a mother stays with her own clutch rather than
 * adopting whichever nest happens to be closest.
 */
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

    /** Called when this mob lays an egg. */
    public void assignEgg(BlockPos pos) {
        this.targetEggPos = pos.immutable();
    }
}

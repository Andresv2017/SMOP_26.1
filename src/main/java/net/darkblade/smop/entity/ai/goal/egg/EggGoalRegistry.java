package net.darkblade.smop.entity.ai.goal.egg;

import net.darkblade.smop.entity.SMOPAnimal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class EggGoalRegistry {

    public static <T extends SMOPAnimal> void registerWithOwnGoal(
            T mob,
            Supplier<? extends Block> eggBlock,
            int stayNearRadius,
            int defenseRadius,
            boolean attackOnApproach,
            boolean attackOnBreak,
            ProtectEggBaseGoal.EggBreakReaction reaction,
            Predicate<LivingEntity> enemySelector,
            int basePriority) {

        ProtectOwnEggGoal guard = new ProtectOwnEggGoal(mob, stayNearRadius, defenseRadius,
                attackOnApproach, attackOnBreak, enemySelector, reaction);

        mob.goalSelector.addGoal(basePriority, guard);
        mob.goalSelector.addGoal(basePriority + 1, new GenericLayEggGoal<>(mob, eggBlock, guard::assignEgg));
    }

    public static <T extends SMOPAnimal> void registerWithNearestGoal(
            T mob,
            Supplier<? extends Block> eggBlock,
            int searchRadius,
            int stayNearRadius,
            int defenseRadius,
            boolean attackOnApproach,
            boolean attackOnBreak,
            ProtectEggBaseGoal.EggBreakReaction reaction,
            Predicate<LivingEntity> enemySelector,
            int basePriority) {

        mob.goalSelector.addGoal(basePriority, new ProtectNearestEggGoal(mob, searchRadius, stayNearRadius,
                defenseRadius, attackOnApproach, attackOnBreak, enemySelector, reaction, List.of(eggBlock)));
        mob.goalSelector.addGoal(basePriority + 1, new GenericLayEggGoal<>(mob, eggBlock, null));
    }

    private EggGoalRegistry() {}
}

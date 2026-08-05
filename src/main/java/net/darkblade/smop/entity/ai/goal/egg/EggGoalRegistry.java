package net.darkblade.smop.entity.ai.goal.egg;

import net.darkblade.smop.entity.SMOPAnimal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Adds the lay-egg and guard-egg goals as a matched pair, so a mob's {@code registerGoals()} states
 * its nesting behaviour in one call instead of wiring two interdependent goals by hand.
 *
 * <p>The guard goal always sits one priority above the laying goal: a mother defends the nest she
 * already has before going looking for somewhere to lay the next one.
 */
public final class EggGoalRegistry {

    /**
     * The mob guards the specific nest it laid. Use for solitary nesters.
     *
     * @param basePriority priority of the guard goal; laying goes at {@code basePriority + 1}
     */
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

    /**
     * The mob guards whichever matching nest is nearest. Use for colonial nesters, where any adult
     * minds any clutch — no assignment needed, so laying reports to nobody.
     */
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

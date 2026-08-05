package net.darkblade.smop.entity.sleep;

import net.minecraft.world.entity.LivingEntity;

/**
 * Custom "is this nearby entity worth waking up for" test, checked before falling back to
 * {@link ISleepingEntity#getInterruptingEntityTypes()}. Lets a mob key off something other than an
 * entity type — a tag, a team, whether the thing is hostile to it.
 */
public interface ISleepThreatEvaluator {

    boolean shouldInterruptSleepDueTo(LivingEntity nearby);
}

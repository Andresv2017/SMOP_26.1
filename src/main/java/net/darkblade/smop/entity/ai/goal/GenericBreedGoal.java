package net.darkblade.smop.entity.ai.goal;

import net.darkblade.smop.entity.SMOPAnimal;
import net.minecraft.world.entity.ai.goal.BreedGoal;

/**
 * {@link BreedGoal} that produces an egg instead of a baby for egg-laying species: mating flips the
 * mother's {@code hasEgg} flag, {@code GenericLayEggGoal} then places the egg block, and the mob
 * hatches from it later. Mammals fall through to vanilla's live birth.
 */
public class GenericBreedGoal<T extends SMOPAnimal> extends BreedGoal {

    private final T entity;

    public GenericBreedGoal(T entity, double speedModifier) {
        super(entity, speedModifier);
        this.entity = entity;
    }

    // No sleep check: this goal holds MOVE/LOOK, so SleepGoal (registered above it) preempts it
    // through the selector's flag map.

    /**
     * Egg instead of a baby, but everything else exactly as vanilla.
     *
     * <p>{@code finalizeSpawnChildFromBreeding} with a {@code null} child is the whole post-mating
     * routine: the heart particles (entity event 18), the experience orb, the {@code ANIMALS_BRED}
     * stat and its advancement trigger, the 6000-tick breeding cooldown on both parents, and
     * {@code resetLove} on both.
     *
     * <p>This used to open-code just the ages and the love reset, which silently dropped the other
     * half — no hearts, no XP, no stat. Deferring to vanilla means anything Mojang adds to the
     * routine arrives for free rather than being noticed as missing later.
     */
    @Override
    protected void breed() {
        if (!this.entity.isMammal()) {
            this.entity.setHasEgg(true);
        }
        this.animal.finalizeSpawnChildFromBreeding(getServerLevel(this.animal), this.partner, null);
    }
}

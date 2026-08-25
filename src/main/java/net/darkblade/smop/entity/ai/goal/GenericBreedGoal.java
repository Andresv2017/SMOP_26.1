package net.darkblade.smop.entity.ai.goal;

import net.darkblade.smop.entity.Gendered;
import net.darkblade.smop.entity.SMOPAnimal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.animal.Animal;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public class GenericBreedGoal<T extends SMOPAnimal> extends BreedGoal {

    private static final double DEFAULT_SEARCH_RADIUS = 8.0D;

    private static final double VANILLA_BREEDING_DISTANCE = 3.0D;

    private static final int COURTSHIP_TICKS = 60;
    private static final int GRACE_TICKS = 60;

    private final T entity;
    private final double searchRadius;
    private final double speedModifier;

    private int loveTicks;

    public GenericBreedGoal(T entity, double speedModifier) {
        this(entity, speedModifier, DEFAULT_SEARCH_RADIUS);
    }

    public GenericBreedGoal(T entity, double speedModifier, double searchRadius) {
        super(entity, speedModifier);
        this.entity = entity;
        this.speedModifier = speedModifier;
        this.searchRadius = searchRadius;
    }

    private double breedingDistanceSqr() {
        double reach = Math.max(VANILLA_BREEDING_DISTANCE,
                (this.animal.getBbWidth() + this.partner.getBbWidth()) * 0.5D + 1.0D);
        return reach * reach;
    }

    @Override
    public boolean canContinueToUse() {
        return this.partner != null
                && this.partner.isAlive()
                && this.partner.isInLove()
                && this.loveTicks < COURTSHIP_TICKS + GRACE_TICKS;
    }

    @Override
    public void start() {
        this.loveTicks = 0;
    }

    @Override
    public void tick() {
        if (this.partner == null) {
            return;
        }
        this.animal.getLookControl().setLookAt(this.partner, 10.0F, this.animal.getMaxHeadXRot());
        this.animal.getNavigation().moveTo(this.partner, this.speedModifier);
        if (++this.loveTicks >= this.adjustedTickDelay(COURTSHIP_TICKS)
                && this.animal.distanceToSqr(this.partner) < this.breedingDistanceSqr()) {
            this.breed();
        }
    }

    @Override
    public boolean canUse() {
        if (!this.animal.isInLove()) {
            return false;
        }
        this.partner = this.findPartner();
        return this.partner != null;
    }

    @Nullable
    private Animal findPartner() {
        return this.animal.level()
                .getEntitiesOfClass(Animal.class, this.animal.getBoundingBox().inflate(this.searchRadius),
                        other -> other.isAlive() && this.animal.canMate(other))
                .stream()
                .min(Comparator.comparingDouble(this.animal::distanceToSqr))
                .orElse(null);
    }

    // No sleep check: this goal holds MOVE/LOOK, so SleepGoal (registered above it) preempts it
    // through the selector's flag map.

    @Override
    protected void breed() {
        if (!this.entity.isMammal()) {
            this.gravidParent().setHasEgg(true);
        }
        this.animal.finalizeSpawnChildFromBreeding(getServerLevel(this.animal), this.partner, null);
    }

    private SMOPAnimal gravidParent() {
        if (this.entity instanceof Gendered self && self.isMale()
                && this.partner instanceof SMOPAnimal mate
                && mate instanceof Gendered other && !other.isMale()) {
            return mate;
        }
        return this.entity;
    }
}

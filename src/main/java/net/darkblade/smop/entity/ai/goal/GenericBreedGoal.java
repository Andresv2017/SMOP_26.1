package net.darkblade.smop.entity.ai.goal;

import net.darkblade.smop.entity.Gendered;
import net.darkblade.smop.entity.SMOPAnimal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.animal.Animal;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * {@link BreedGoal} that produces an egg instead of a baby for egg-laying species: mating flips the
 * mother's {@code hasEgg} flag, {@code GenericLayEggGoal} then places the egg block, and the mob
 * hatches from it later. Mammals fall through to vanilla's live birth.
 */
public class GenericBreedGoal<T extends SMOPAnimal> extends BreedGoal {

    /** Vanilla's, kept as the default so nothing changes for a mob that does not ask. */
    private static final double DEFAULT_SEARCH_RADIUS = 8.0D;

    /** Vanilla's mating reach, kept as the floor. @see #breedingDistanceSqr() */
    private static final double VANILLA_BREEDING_DISTANCE = 3.0D;

    /** Vanilla's courtship length, and how long past it the pair keeps trying to close the gap. */
    private static final int COURTSHIP_TICKS = 60;
    private static final int GRACE_TICKS = 60;

    private final T entity;
    private final double searchRadius;
    private final double speedModifier;

    /** Ours: vanilla's {@code loveTime} is private and the courtship is reimplemented here. */
    private int loveTicks;

    public GenericBreedGoal(T entity, double speedModifier) {
        this(entity, speedModifier, DEFAULT_SEARCH_RADIUS);
    }

    /**
     * @param searchRadius how far to look for a partner, inflated from the animal's own bounding box
     *
     * <p><b>Eight is a cow number.</b> Vanilla hard-codes it, and on a cow — a metre across, wandering
     * a paddock — it is generous. On a three-block marine reptile whose two members were fed by a
     * player standing between them it is not: a pair eight and a half blocks apart on a beach simply
     * never finds each other, and the symptom is two animals in love standing still, which reads as
     * breeding being broken rather than as a search radius being short.
     */
    public GenericBreedGoal(T entity, double speedModifier, double searchRadius) {
        super(entity, speedModifier);
        this.entity = entity;
        this.speedModifier = speedModifier;
        this.searchRadius = searchRadius;
    }

    /**
     * How close the pair must be to mate, <b>measured against their bodies</b> rather than as a flat
     * three blocks.
     *
     * <p><b>Vanilla's {@code distanceToSqr(partner) < 9.0} is unreachable for a large animal.</b>
     * Three blocks centre to centre is roomy for a cow, which is 0.9 across — its calf-making
     * embrace has two metres of daylight in it. Two Nirasmosaurus are 3.0 across EACH: their hitboxes
     * collide at exactly three blocks, so the pair sits pinned at the threshold and only ever slips
     * under it by luck, on whichever tick the angle between them happens to shave a few centimetres
     * off. That is the whole of "sometimes they breed and sometimes they just stand there touching".
     *
     * <p>Half of each body plus a metre of reach, floored at vanilla's three so nothing small gets a
     * stricter rule than it had.
     */
    private double breedingDistanceSqr() {
        double reach = Math.max(VANILLA_BREEDING_DISTANCE,
                (this.animal.getBbWidth() + this.partner.getBbWidth()) * 0.5D + 1.0D);
        return reach * reach;
    }

    /**
     * Vanilla's courtship, with the reach above and a grace period on the end.
     *
     * <p>Reimplemented for the same reason as {@link #canUse()}: the numbers live inside a private
     * method. The grace matters as much as the distance — vanilla arrives at its mating tick exactly
     * once, because {@code canContinueToUse} drops the goal the moment the timer passes sixty, so a
     * pair that is a hand's breadth too far apart on that one tick does not get a second chance and
     * the courtship is silently wasted. Here the attempt repeats while the two remain in love.
     */
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

    /**
     * Vanilla's, with the search radius opened up.
     *
     * <p>Reimplemented rather than extended because {@code getFreePartner()} and the targeting
     * conditions it uses are both private, and the radius lives inside them as a literal. The rule
     * for what counts as a partner is unchanged — it defers to {@link net.minecraft.world.entity.animal.Animal#canMate},
     * which is where the species' own conditions (sex, medium, love) already live.
     */
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
            this.gravidParent().setHasEgg(true);
        }
        this.animal.finalizeSpawnChildFromBreeding(getServerLevel(this.animal), this.partner, null);
    }

    /**
     * Which of the two parents ends up carrying the egg: the female, when the species has sexes.
     *
     * <p><b>This used to be whichever animal's goal happened to tick first</b>, which is a coin flip
     * — both partners run their own {@link BreedGoal} and only the first to reach {@code breed()}
     * matters, because vanilla's post-mating routine resets love on both and the loser's goal stops
     * before its own timer expires. Half the time that left the MALE gravid, and on a species whose
     * sex is visible in its texture that is not a harmless internal detail: it is a male walking off
     * to nest, and a female who is plainly the mother standing there with nothing. Kill the male —
     * which is a perfectly ordinary thing for a player to do — and the clutch dies with him.
     *
     * <p>Falls back to the goal's own animal when either side is not {@link Gendered}, which is the
     * behaviour a species without sexes had all along.
     */
    private SMOPAnimal gravidParent() {
        if (this.entity instanceof Gendered self && self.isMale()
                && this.partner instanceof SMOPAnimal mate
                && mate instanceof Gendered other && !other.isMale()) {
            return mate;
        }
        return this.entity;
    }
}

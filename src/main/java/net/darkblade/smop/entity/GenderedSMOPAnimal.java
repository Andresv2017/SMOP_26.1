package net.darkblade.smop.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link SMOPAnimal} that has a sex. Breeding requires one of each, so a herd will not
 * self-multiply from a single individual.
 *
 * <p>The sex is rolled in {@code finalizeSpawn} by each concrete mob (it also picks the matching
 * texture), not here — a mob hatched from an egg and one spawned by the world generator take
 * different paths into existence.
 */
public abstract class GenderedSMOPAnimal extends SMOPAnimal implements Gendered {

    private static final EntityDataAccessor<Boolean> MALE =
            SynchedEntityData.defineId(GenderedSMOPAnimal.class, EntityDataSerializers.BOOLEAN);

    protected GenderedSMOPAnimal(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MALE, true);
    }

    @Override
    public boolean isMale() {
        return this.entityData.get(MALE);
    }

    @Override
    public void setMale(boolean male) {
        this.entityData.set(MALE, male);
    }

    /**
     * Vanilla's rule <em>plus</em> the sex requirement — not instead of it.
     *
     * <p>This used to test only the sex, which quietly broke breeding: {@code BreedGoal} picks its
     * partner with {@code canMate} but keeps running with {@code canContinueToUse}, which demands
     * {@code partner.isInLove()}. Dropping the love check here let the goal latch onto the nearest
     * opposite-sex animal whether or not it had been fed, and then abort on the very next tick —
     * over and over, so a pair that <em>had</em> both been fed could sit next to each other
     * indefinitely while the goal kept choosing an uninterested third animal.
     *
     * <p>The species check is {@code getClass()}, as vanilla does it, so two different SMOP species
     * can never pair off just because their sexes differ.
     */
    @Override
    public boolean canMate(@NotNull Animal other) {
        return other != this
                && other.getClass() == this.getClass()
                && other instanceof Gendered partner
                && this.isMale() != partner.isMale()
                && this.isInLove()
                && other.isInLove();
    }

    /**
     * Egg layers produce no live offspring — {@code GenericBreedGoal} flips {@code hasEgg} instead
     * and the egg block hatches later. Live-bearing mobs override this.
     */
    @Override
    public @Nullable AgeableMob getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob partner) {
        return null;
    }

    @Override
    protected void addAdditionalSaveData(@NotNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("IsMale", this.isMale());
    }

    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setMale(input.getBooleanOr("IsMale", true));
    }
}

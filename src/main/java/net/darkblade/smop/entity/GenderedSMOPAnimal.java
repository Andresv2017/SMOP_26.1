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

    @Override
    public boolean canMate(@NotNull Animal other) {
        return other != this
                && other.getClass() == this.getClass()
                && other instanceof Gendered partner
                && this.isMale() != partner.isMale()
                && this.isInLove()
                && other.isInLove();
    }

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

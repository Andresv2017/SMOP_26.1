package net.darkblade.smop.entity.projectile;

import net.darkblade.smop.entity.SMOPEntities;
import net.darkblade.smop.entity.UndeadScatter;
import net.darkblade.smop.item.SMOPItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TangoArrowEntity extends AbstractArrow {

    public TangoArrowEntity(EntityType<? extends TangoArrowEntity> type, Level level) {
        super(type, level);
    }

    public TangoArrowEntity(Level level, LivingEntity owner, ItemStack pickupItemStack,
                            @Nullable ItemStack firedFromWeapon) {
        super(SMOPEntities.TANGO_ARROW.get(), owner, level, pickupItemStack, firedFromWeapon);
    }

    public TangoArrowEntity(Level level, double x, double y, double z, ItemStack pickupItemStack,
                            @Nullable ItemStack firedFromWeapon) {
        super(SMOPEntities.TANGO_ARROW.get(), x, y, z, level, pickupItemStack, firedFromWeapon);
    }

    // ─────────────────────────────────────────────── THE ROAR, AT RANGE ─────

    private static final float UNDEAD_BONUS_DAMAGE = 2.5F;

    private static final double SCATTER_RADIUS = 5.0D;
    private static final double SCATTER_FLEE_DISTANCE = 7.0D;
    private static final double SCATTER_SPEED = 1.2D;

    private static final double MIN_SPEED_FOR_BONUS = 1.0E-4D;

    private double knownBaseDamage = 2.0D;

    @Override
    public void setBaseDamage(double baseDamage) {
        super.setBaseDamage(baseDamage);
        this.knownBaseDamage = baseDamage;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput input) {
        super.readAdditionalSaveData(input);
        // Same key and same default AbstractArrow uses, read again rather than inferred.
        this.knownBaseDamage = input.getDoubleOr("damage", 2.0D);
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult hitResult) {
        if (!hitResult.getEntity().is(EntityTypeTags.UNDEAD)) {
            super.onHitEntity(hitResult);
            return;
        }

        double speed = this.getDeltaMovement().length();
        boolean boosted = speed > MIN_SPEED_FOR_BONUS;
        if (boosted) {
            // super, not this: the override above would take this temporary value for the real one.
            super.setBaseDamage(this.knownBaseDamage + UNDEAD_BONUS_DAMAGE / speed);
        }
        try {
            super.onHitEntity(hitResult);
        } finally {
            if (boosted) {
                super.setBaseDamage(this.knownBaseDamage);
            }
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            int scattered = UndeadScatter.scatter(serverLevel, this.position(),
                    SCATTER_RADIUS, SCATTER_FLEE_DISTANCE, SCATTER_SPEED, hitResult.getEntity());
            if (scattered > 0) {
                // Without this the effect is invisible and the arrow reads as doing nothing special.
                serverLevel.sendParticles(ParticleTypes.SOUL,
                        this.getX(), this.getY() + 0.2D, this.getZ(),
                        8, 0.4D, 0.2D, 0.4D, 0.02D);
            }
        }
    }

    @Override
    protected @NotNull ItemStack getDefaultPickupItem() {
        return new ItemStack(SMOPItems.TANGO_ARROW.get());
    }
}

package net.darkblade.smop.entity.projectile;

import net.darkblade.smop.entity.SMOPEntities;
import net.darkblade.smop.item.SMOPItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class NirasSpearEntity extends AbstractArrow {

    private static final float THROWN_DAMAGE = 4.0F;

    private static final float SWIMMING_INERTIA = 0.99F;

    private boolean dealtDamage;

    public NirasSpearEntity(EntityType<? extends NirasSpearEntity> type, Level level) {
        super(type, level);
    }

    public NirasSpearEntity(Level level, LivingEntity owner, ItemStack pickupItemStack) {
        super(SMOPEntities.NIRAS_SPEAR.get(), owner, level, pickupItemStack, null);
    }

    public NirasSpearEntity(Level level, double x, double y, double z, ItemStack pickupItemStack) {
        super(SMOPEntities.NIRAS_SPEAR.get(), x, y, z, level, pickupItemStack, null);
    }

    @Override
    protected @NotNull ItemStack getDefaultPickupItem() {
        return new ItemStack(SMOPItems.NIRAS_SPEAR.get());
    }

    @Override
    public @NotNull ItemStack getWeaponItem() {
        return this.getPickupItemStackOrigin();
    }

    // ─────────────────────────────────────────────────────── UNDERWATER ─────

    @Override
    protected float getWaterInertia() {
        return SWIMMING_INERTIA;
    }

    @Override
    protected double getDefaultGravity() {
        return this.isInWater() ? 0.0D : super.getDefaultGravity();
    }

    // ─────────────────────────────────────────────────────── IMPACT ─────

    @Override
    protected @Nullable EntityHitResult findHitEntity(@NotNull Vec3 from, @NotNull Vec3 to) {
        return this.dealtDamage ? null : super.findHitEntity(from, to);
    }

    @Override
    protected @NotNull Collection<EntityHitResult> findHitEntities(@NotNull Vec3 from, @NotNull Vec3 to) {
        EntityHitResult hit = this.findHitEntity(from, to);
        return hit != null ? List.of(hit) : List.of();
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult hitResult) {
        Entity target = hitResult.getEntity();
        Entity owner = this.getOwner();

        DamageSource source = this.damageSources().trident(this, owner == null ? this : owner);

        float damage = THROWN_DAMAGE;
        if (this.level() instanceof ServerLevel serverLevel) {
            // Lets Sharpness and friends on the spear stack still count, the way the trident's do.
            damage = EnchantmentHelper.modifyDamage(serverLevel, this.getWeaponItem(), target, source, damage);
        }

        this.dealtDamage = true;

        if (target.hurtOrSimulate(source, damage)) {
            if (target.is(EntityType.ENDERMAN)) {
                return;
            }
            if (target instanceof LivingEntity victim) {
                this.doKnockback(victim, source);
                this.doPostHurtEffects(victim);
            }
        }

        // Kill the flight so it drops rather than skidding on through the world.
        this.setDeltaMovement(this.getDeltaMovement().multiply(-0.01D, -0.1D, -0.01D));
        this.playSound(this.getDefaultHitGroundSoundEvent(), 1.0F, 1.0F);
    }
}

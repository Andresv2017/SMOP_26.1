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

/**
 * The Nirasmosaurus spear in flight — a javelin, not a trident.
 *
 * <p><b>What makes it its own thing.</b> Vanilla's thrown weapon is unique, unstackable and looted
 * from drowned; this one is crafted from a beak and carried four at a time, so throwing it is a
 * decision about ammunition rather than about risking your only trident. That niche is the item's
 * identity and it costs nothing to implement — it lives in {@code stacksTo(4)} on the item.
 *
 * <p><b>And it swims.</b> Two overrides below, and only the second is a real invention:
 *
 * <ul>
 *   <li>{@link #getWaterInertia()} — parity, not identity. {@code AbstractArrow} bleeds 40% of a
 *       projectile's speed per tick under water, which is why bows are useless down there. Vanilla's
 *       own {@code ThrownTrident} already overrides this to 0.99, so matching it only keeps a crafted
 *       spear from being worse under water than a looted trident.</li>
 *   <li>{@link #getDefaultGravity()} — <b>the hook.</b> Fully submerged, this spear has no gravity at
 *       all: it travels dead straight instead of arcing. A trident still falls. That is the marine
 *       reptile's strike, it is visible the first time you throw one, and nothing in vanilla does it.
 *       Break the surface and gravity returns on the same tick.</li>
 * </ul>
 *
 * <p>One target per throw, like the trident: {@link #findHitEntities} narrows the
 * sweep to a single result and {@code dealtDamage} closes it afterwards, so a spear cannot rake a
 * crowd on one flight.
 */
public class NirasSpearEntity extends AbstractArrow {

    /** Between an arrow's 2 and a trident's 8, which is where a javelin belongs. */
    private static final float THROWN_DAMAGE = 4.0F;

    /** Vanilla's own trident value. See the class note: this is parity with it, not a novelty. */
    private static final float SWIMMING_INERTIA = 0.99F;

    private boolean dealtDamage;

    public NirasSpearEntity(EntityType<? extends NirasSpearEntity> type, Level level) {
        super(type, level);
    }

    /** Thrown by a player. Matches {@code Projectile.ProjectileFactory}, which is how the item fires it. */
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

    /**
     * The spear in the air <em>is</em> the weapon that threw it.
     *
     * <p>Without this the entity crashes the server on its first hit, and the reason is worth keeping:
     * {@code AbstractArrow#getWeaponItem} returns {@code firedFromWeapon}, which is {@code @Nullable}
     * and which this entity passes as {@code null} — a spear is not fired <em>from</em> anything. But
     * {@link #onHitEntity} hands that value to {@code EnchantmentHelper.modifyDamage}, which
     * dereferences it without a check, and the arrow path vanilla copies from guards the call
     * (<code>if (this.getWeaponItem() != null …)</code>) while the trident path does not.
     *
     * <p>Vanilla's {@code ThrownTrident} closes the same gap the same way, by answering with its own
     * pickup stack. Overriding here rather than passing the stack as {@code firedFromWeapon} in the
     * constructor is deliberate: that parameter also drives Piercing, and a javelin that pierced a
     * line of targets would stop being a javelin — see {@link #findHitEntities}.
     */
    @Override
    public @NotNull ItemStack getWeaponItem() {
        return this.getPickupItemStackOrigin();
    }

    // ─────────────────────────────────────────────────────── UNDERWATER ─────

    @Override
    protected float getWaterInertia() {
        return SWIMMING_INERTIA;
    }

    /**
     * No gravity while submerged.
     *
     * <p>{@code isInWater()} rather than {@code isInWaterOrRain()}: rain should not turn a thrown
     * spear into a laser across an open field. This is about being <em>in</em> the water, which is
     * where the animal it came from does its hunting.
     */
    @Override
    protected double getDefaultGravity() {
        return this.isInWater() ? 0.0D : super.getDefaultGravity();
    }

    // ─────────────────────────────────────────────────────── IMPACT ─────

    /** One victim per throw: once it has bitten, it stops looking. */
    @Override
    protected @Nullable EntityHitResult findHitEntity(@NotNull Vec3 from, @NotNull Vec3 to) {
        return this.dealtDamage ? null : super.findHitEntity(from, to);
    }

    /**
     * {@code AbstractArrow} sweeps for every entity on the path and would pierce a line of them.
     * Narrowing to the single result {@link #findHitEntity} returns is what keeps a javelin a javelin;
     * vanilla's {@code ThrownTrident} does exactly this, for the same reason.
     */
    @Override
    protected @NotNull Collection<EntityHitResult> findHitEntities(@NotNull Vec3 from, @NotNull Vec3 to) {
        EntityHitResult hit = this.findHitEntity(from, to);
        return hit != null ? List.of(hit) : List.of();
    }

    /**
     * Flat damage, not {@code speed * baseDamage}.
     *
     * <p>A thrown weapon is not a drawn one: the arrow formula exists to reward holding the string,
     * and this already gates its own throw on a charge, so the arrow pipeline is bypassed here rather
     * than tuned around.
     */
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

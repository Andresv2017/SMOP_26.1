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

/**
 * The Tangoftero's arrow in flight.
 *
 * <p><b>It is the Tangoftero's roar, fired.</b> Against anything else it is an ordinary arrow; against
 * the undead it hits harder and panics the ones standing around the victim, which is the same thing
 * the animal itself does when you feed it rotten flesh. See {@link #onHitEntity} for the mechanism
 * and {@link net.darkblade.smop.entity.UndeadScatter} for the part both share.
 *
 * <p><b>1.20.1 had none of this.</b> The legacy class overrode exactly one thing beyond the
 * constructors, {@code createArrow}, and used it to call {@code setBaseDamage(2.0)} — which
 * {@link AbstractArrow} already initialises to 2.0 (read from 26.1's own source, not assumed). That
 * call changed nothing, so the old tango arrow flew, pierced, enchanted and hurt exactly like
 * {@code minecraft:arrow}: a re-textured arrow with a crafting cost and no reason to exist. The
 * anti-undead behaviour is new, and deliberate.
 *
 * <p>Note there is still no blanket damage override. The base stays at vanilla's 2.0 and the bonus is
 * applied only for the hit that earns it, so a tango arrow shot at a pig is worth exactly what a
 * normal arrow is worth.
 *
 * <p><b>Port note.</b> Three things moved under this class in 26.1: {@code AbstractArrow} lives in
 * {@code ...projectile.arrow} now, {@code getPickupItem()} became {@link #getDefaultPickupItem()},
 * and the constructors take the pickup stack and the weapon that fired it — the latter is what
 * carries Piercing across, so it must be threaded through rather than dropped.
 */
public class TangoArrowEntity extends AbstractArrow {

    public TangoArrowEntity(EntityType<? extends TangoArrowEntity> type, Level level) {
        super(type, level);
    }

    /** Fired by a living shooter: a player with a bow, or a dispenser's owner. */
    public TangoArrowEntity(Level level, LivingEntity owner, ItemStack pickupItemStack,
                            @Nullable ItemStack firedFromWeapon) {
        super(SMOPEntities.TANGO_ARROW.get(), owner, level, pickupItemStack, firedFromWeapon);
    }

    /** Spawned at a position, which is the shape a dispenser wants. */
    public TangoArrowEntity(Level level, double x, double y, double z, ItemStack pickupItemStack,
                            @Nullable ItemStack firedFromWeapon) {
        super(SMOPEntities.TANGO_ARROW.get(), x, y, z, level, pickupItemStack, firedFromWeapon);
    }

    // ─────────────────────────────────────────────── THE ROAR, AT RANGE ─────

    /**
     * Extra damage this deals to an undead it hits, on top of a normal arrow's — the same 2.5 a
     * Smite I sword adds, and it fills a real hole: Smite cannot go on a bow or a crossbow, so before
     * this arrow the only ranged answer to undead was generic Power.
     */
    private static final float UNDEAD_BONUS_DAMAGE = 2.5F;

    /** Half the Tangoftero's own 10-block roar. This is one feather, not the whole animal. */
    private static final double SCATTER_RADIUS = 5.0D;
    /** Same push the roar gives, so the two read as the same effect at different scale. */
    private static final double SCATTER_FLEE_DISTANCE = 7.0D;
    private static final double SCATTER_SPEED = 1.2D;

    /** Below this the arrow is barely moving and the damage division below would blow up. */
    private static final double MIN_SPEED_FOR_BONUS = 1.0E-4D;

    /**
     * A local copy of {@link AbstractArrow}'s private {@code baseDamage}.
     *
     * <p>{@code setBaseDamage} is public but there is no getter, and {@link #onHitEntity} has to put
     * the field back exactly as it found it — a piercing shot goes on to hit more things, and the
     * undead bonus must not follow it onto the next victim. So the value is mirrored on every path
     * that can change it: the 2.0 default here, {@link #setBaseDamage} below, and
     * {@link #readAdditionalSaveData}, which matters because vanilla's own load writes the private
     * field directly rather than going through the setter.
     */
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

    /**
     * The whole point of the arrow: hit an undead and its neighbours break.
     *
     * <p><b>Why the bonus is divided by the speed.</b> Vanilla does not deal {@code baseDamage}; it
     * deals {@code ceil(speed * baseDamage)}, so a flat 2.5 added to the base would arrive as 7.5 at
     * full draw and as almost nothing on a tap shot. Dividing first cancels that multiply and makes
     * the bonus land as the flat 2.5 it is documented to be, at any draw.
     *
     * <p><b>The one it hit does not run.</b> That exclusion is the design: scattering your own target
     * would fight your aim every time, while scattering the ones <em>around</em> it turns the arrow
     * into a way to break a group — which is exactly what the Tangoftero's roar does, and the reason
     * this ammunition is made from its feather.
     */
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

    /**
     * What it becomes when picked up, and the fallback when a saved arrow has no stored item.
     *
     * <p>1.20.1 returned a fresh stack here too, with a comment that it deliberately dropped any NBT.
     * In 26.1 the stored stack is the norm — the constructors copy it — and this is only consulted
     * when there is none, so returning a plain arrow is both the old behaviour and the right one.
     */
    @Override
    protected @NotNull ItemStack getDefaultPickupItem() {
        return new ItemStack(SMOPItems.TANGO_ARROW.get());
    }
}

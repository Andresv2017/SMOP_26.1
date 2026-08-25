package net.darkblade.smop.item;

import net.darkblade.smop.entity.projectile.TangoArrowEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Ammunition made from a Tangoftero feather.
 *
 * <p>Being in the {@code minecraft:arrows} item tag is what lets a bow or crossbow load it; this
 * class only decides what leaves the string. What makes it worth crafting lives in
 * {@link TangoArrowEntity}: against the undead it hits harder and scatters the ones around whatever
 * it hit, which is the Tangoftero's own roar delivered at range.
 *
 * <p><b>The dispenser path needs its own override.</b> {@code createArrow} covers bows and crossbows
 * but leaves {@code ProjectileItem#asProjectile} on {@link ArrowItem}'s default, so without this a
 * dispenser fires plain {@code minecraft:arrow} entities that drop a tango arrow when collected.
 */
public class TangoArrowItem extends ArrowItem {

    public TangoArrowItem(Properties properties) {
        super(properties);
    }

    /**
     * Bows and crossbows.
     *
     * <p>{@code firedFromWeapon} is passed through rather than dropped: {@link AbstractArrow} reads
     * Piercing off it when the shot is created, so swallowing it here would silently disable that
     * enchantment for this ammunition only.
     */
    @Override
    public @NotNull AbstractArrow createArrow(@NotNull Level level, @NotNull ItemStack itemStack,
                                              @NotNull LivingEntity owner, @Nullable ItemStack firedFromWeapon) {
        return new TangoArrowEntity(level, owner, itemStack.copyWithCount(1), firedFromWeapon);
    }

    /** Dispensers. */
    @Override
    public @NotNull Projectile asProjectile(@NotNull Level level, @NotNull Position position,
                                            @NotNull ItemStack itemStack, @NotNull Direction direction) {
        TangoArrowEntity arrow = new TangoArrowEntity(level, position.x(), position.y(), position.z(),
                itemStack.copyWithCount(1), null);
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        return arrow;
    }
}

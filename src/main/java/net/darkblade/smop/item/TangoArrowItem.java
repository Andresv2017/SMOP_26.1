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

public class TangoArrowItem extends ArrowItem {

    public TangoArrowItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull AbstractArrow createArrow(@NotNull Level level, @NotNull ItemStack itemStack,
                                              @NotNull LivingEntity owner, @Nullable ItemStack firedFromWeapon) {
        return new TangoArrowEntity(level, owner, itemStack.copyWithCount(1), firedFromWeapon);
    }

    @Override
    public @NotNull Projectile asProjectile(@NotNull Level level, @NotNull Position position,
                                            @NotNull ItemStack itemStack, @NotNull Direction direction) {
        TangoArrowEntity arrow = new TangoArrowEntity(level, position.x(), position.y(), position.z(),
                itemStack.copyWithCount(1), null);
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        return arrow;
    }
}

package net.darkblade.smop.item;

import net.darkblade.smop.entity.projectile.NirasSpearEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class NirasSpearItem extends Item {

    private static final int MIN_CHARGE_TICKS = 10;

    private static final float THROW_POWER = 2.5F;
    private static final float THROW_INACCURACY = 1.0F;

    public NirasSpearItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull ItemUseAnimation getUseAnimation(@NotNull ItemStack itemStack) {
        return ItemUseAnimation.TRIDENT;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack itemStack, @NotNull LivingEntity user) {
        return 72000;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean releaseUsing(@NotNull ItemStack itemStack, @NotNull Level level,
                                @NotNull LivingEntity entity, int remainingTime) {
        if (!(entity instanceof Player player)) {
            return false;
        }

        int timeHeld = this.getUseDuration(itemStack, entity) - remainingTime;
        if (timeHeld < MIN_CHARGE_TICKS) {
            return false;
        }

        player.awardStat(Stats.ITEM_USED.get(this));

        if (level instanceof ServerLevel serverLevel) {
            // consumeAndReturn hands back the single spear that is now in the air, and leaves the rest
            // of the stack in the hand. Creative players keep the stack; the projectile knows not to
            // let itself be picked back up in that case.
            ItemStack thrown = itemStack.consumeAndReturn(1, player);
            NirasSpearEntity spear = Projectile.spawnProjectileFromRotation(
                    NirasSpearEntity::new, serverLevel, thrown, player, 0.0F, THROW_POWER, THROW_INACCURACY);

            if (player.hasInfiniteMaterials()) {
                spear.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }

            level.playSound(null, spear, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return true;
    }
}

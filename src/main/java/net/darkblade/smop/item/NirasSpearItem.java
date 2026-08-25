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

/**
 * A javelin cut from a Nirasmosaurus beak: hold to wind up, release to throw, carry four.
 *
 * <p><b>Why it is not a trident subclass.</b> The trident's whole item class is Riptide and Loyalty
 * bookkeeping, and this has neither — it is thrown, it lands, you walk over and pick it up. What is
 * left after removing those is the wind-up and the throw, which is what this class is.
 *
 * <p>See {@link NirasSpearEntity} for what the thrown spear does under water, which is the part that
 * makes it worth crafting.
 */
public class NirasSpearItem extends Item {

    /** Below this the throw is a fumble, not a throw. Vanilla's trident uses the same gate. */
    private static final int MIN_CHARGE_TICKS = 10;

    /** Launch speed and spread. */
    private static final float THROW_POWER = 2.5F;
    private static final float THROW_INACCURACY = 1.0F;

    public NirasSpearItem(Properties properties) {
        super(properties);
    }

    /**
     * {@code TRIDENT}, not {@code SPEAR}.
     *
     * <p>Both exist in 26.1 and they are different animations, which is the whole of a bug this class
     * shipped with. {@code TRIDENT} is the classic wind-up: the arm hauls back over the shoulder and
     * gains a charge shake, in first person and third. {@code SPEAR} is the newer pose and routes
     * through {@code SpearAnimations.firstPersonUse}, which barely reads as a throw at all — reported
     * from the game as "no se nota la diferencia".
     *
     * <p>Taken from the Dori spear in Mythos&Mortals, which throws well and gets its entire throwing
     * pose from this one value: it declares no second display model and no {@code using_item}
     * condition. The animation <em>is</em> the pose.
     */
    @Override
    public @NotNull ItemUseAnimation getUseAnimation(@NotNull ItemStack itemStack) {
        return ItemUseAnimation.TRIDENT;
    }

    /** Held indefinitely; the charge that matters is measured on release. */
    @Override
    public int getUseDuration(@NotNull ItemStack itemStack, @NotNull LivingEntity user) {
        return 72000;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    /**
     * Spawns the spear and eats one from the stack.
     *
     * <p>{@code Projectile.spawnProjectileFromRotation} is the 26.1 way in, and it is not just tidier
     * than building the entity by hand: it aims from the thrower's rotation, applies the spread, and
     * adds the entity to the level in the one call that vanilla's own trident uses. Doing it manually
     * is how a projectile ends up spawning without a spawn packet and being invisible to everyone but
     * the thrower.
     */
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

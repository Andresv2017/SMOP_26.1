package net.darkblade.smop.entity.ai.goal.flying;

import net.darkblade.smop.entity.SMOPFlyingAnimal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The idle rhythm of a wild flier: every so often it decides it would rather be in the air, and a
 * while later that it would rather be down. Purely a coin-flip on a long timer — the actual mode
 * change is {@code SMOPFlyingAnimal#handleAutoNavigationSwitch}'s business.
 *
 * <p>Only wild adults. A tame one follows its owner, a chick cannot fly, and a perched one is busy.
 */
public class FlyFromNowAndThenGoal extends Goal {

    /** Upper bound on the gap between mood changes, in ticks (~100 s). */
    private static final int MAX_INTERVAL = 2000;
    /** Ticks before the switch at which the mob starts looking for somewhere to come down. */
    private static final int LANDING_LEAD = 50;

    private final SMOPFlyingAnimal mob;
    private int timer;
    @Nullable
    private BlockPos landingPos;

    public FlyFromNowAndThenGoal(SMOPFlyingAnimal mob) {
        this.mob = mob;
        this.timer = mob.getRandom().nextInt(MAX_INTERVAL);
    }

    @Override
    public boolean canUse() {
        return !this.mob.isTame()
                && !this.mob.isOrderedToSit()
                && !this.mob.isBaby()
                && !this.mob.isMovementLocked();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void tick() {
        if (--this.timer <= 0) {
            this.mob.setWantsToFly(!this.mob.wantsToFly());
            this.timer = this.mob.getRandom().nextInt(MAX_INTERVAL);
            this.landingPos = null;
        } else if (this.timer < LANDING_LEAD && !this.mob.onGround() && this.mob.wantsToFly()) {
            this.landingPos = this.findLandingSpot();
        }

        if (this.landingPos != null) {
            this.mob.getNavigation().moveTo(
                    this.landingPos.getX(), this.landingPos.getY(), this.landingPos.getZ(), 1.0D);
        }
    }

    /** Straight down from where it is, to the first thing that is not open air. */
    @Nullable
    private BlockPos findLandingSpot() {
        Level level = this.mob.level();
        BlockPos pos = this.mob.blockPosition();
        while (pos.getY() > level.getMinY() && level.isEmptyBlock(pos)) {
            pos = pos.below();
        }
        // Only a solid landing counts. The 1.20.1 version returned the position when the fluid state
        // was NOT empty, i.e. it deliberately steered the mob down into water — almost certainly an
        // inverted test, since a flier that avoids water hard enough to give it a negative
        // pathfinding malus has no business landing in it.
        return level.getFluidState(pos).isEmpty() ? pos : null;
    }
}

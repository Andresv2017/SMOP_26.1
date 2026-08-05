package net.darkblade.smop.entity.ai.goal;

import net.darkblade.smop.entity.SMOPAnimal;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Plays the roar a mob requested via {@code triggerRoar()} — typically after being hit with no
 * target yet. Holds MOVE and LOOK for the duration so the melee goals wait their turn.
 *
 * <p>Use this on mobs that roar only reactively; {@link RoarOnTargetGoal} covers the richer
 * "roar once when engaging a target" case and also consumes reactive triggers.
 */
public class RoarOnHurtGoal<T extends SMOPAnimal> extends Goal {

    protected final T entity;

    private int ticksLeft;

    public RoarOnHurtGoal(T entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /**
     * No "am I attacking" check: this goal and the melee goal both hold MOVE and LOOK, so whichever
     * is registered higher wins through the selector's flag map — the same arbitration that keeps
     * sleep and locomotion apart.
     */
    @Override
    public boolean canUse() {
        return this.entity.shouldRoarNow()
                && !this.entity.isRoaring()
                && !this.entity.isDeadOrDying();
    }

    @Override
    public boolean canContinueToUse() {
        return this.entity.isRoaring() && !this.entity.isDeadOrDying();
    }

    @Override
    public void start() {
        this.ticksLeft = this.entity.getRoarDuration();
        this.entity.setRoaring(true);
        this.entity.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.entity.isDeadOrDying() || this.ticksLeft-- <= 0) {
            this.entity.setRoaring(false);
            this.entity.resetShouldRoar();
        }
    }

    @Override
    public void stop() {
        this.entity.setRoaring(false);
        this.entity.resetShouldRoar();
    }
}

package net.darkblade.smop.entity.ai.goal;

import net.darkblade.smop.entity.krifto.KriftognathusEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

public class TameFeedGoal extends Goal {

    private static final double SEARCH_RADIUS = 16.0D;
    private static final double EAT_REACH = 1.0D;
    private static final int RETRY_COOLDOWN_TICKS = 40;
    private static final double APPROACH_SPEED = 1.0D;
    private static final int REPATH_INTERVAL_TICKS = 10;
    private static final int APPROACH_GIVE_UP_TICKS = 300;

    private final KriftognathusEntity mob;

    @Nullable
    private ItemEntity targetItem;
    private int cooldownUntilTick;
    private boolean biting;
    private int approachTicks;
    private int repathCooldown;

    public TameFeedGoal(KriftognathusEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.mob.level().isClientSide() || this.mob.isTame() || this.mob.isInSleepCycle()
                || this.mob.isMovementLocked() || this.mob.isFlying() || this.mob.getTarget() != null
                || this.mob.tickCount < this.cooldownUntilTick) {
            return false;
        }
        this.targetItem = findOffering(this.mob);
        return this.targetItem != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.targetItem != null && isValidTarget(this.targetItem) && !this.mob.isTame()
                && !this.mob.isFlying();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.biting = false;
        this.approachTicks = 0;
        // Zero, not the full interval: path on the very first tick rather than standing still for
        // half a second deciding to.
        this.repathCooldown = 0;
    }

    @Override
    public void stop() {
        if (this.biting) {
            this.mob.stopAction();
            this.biting = false;
        }
        this.mob.getNavigation().stop();
        this.targetItem = null;
    }

    @Override
    public void tick() {
        ItemEntity item = this.targetItem;
        if (item == null) {
            return;
        }

        if (this.biting) {
            // startAction's own timer ends the clip; this goal just watches for it to finish.
            if (!this.mob.isPerforming("eating")) {
                this.biting = false;
                this.finishBite(item);
            }
            return;
        }

        // isFlying() is excluded by canUse()/canContinueToUse() above, so this only ever runs grounded.
        this.mob.getLookControl().setLookAt(item, 10.0F, this.mob.getMaxHeadXRot());

        if (this.inEatRange(item)) {
            this.mob.getNavigation().stop();
            this.approachTicks = 0;
            this.biting = true;
            this.mob.startAction("eating");
            return;
        }

        // The last stretch is walked by hand. The pathfinder stops up to a block short and considers
        // that arrival (see EAT_REACH), so re-issuing the path changes nothing — it hands back
        // another already-complete path and the mob stands there, one step outside its own reach,
        // until something physically shoves it closer. Mob#serverAiStep runs goals, then the
        // navigation, then the move control, so this wanted position is overwritten while a real
        // path is being followed and only takes effect once the navigation has given up — exactly
        // the case that was stalling.
        this.mob.getMoveControl().setWantedPosition(item.getX(), item.getY(), item.getZ(), APPROACH_SPEED);

        // Re-pathed on a plain countdown, NOT gated on the navigation having finished. Gating on
        // isDone() was what made the walk look sluggish: a path that ends a block short completes
        // constantly, and the mob then waited out the rest of the interval — up to nine idle ticks —
        // before asking for the next one. Repeated the whole way over, that stop-start reads as a
        // much slower animal, even though the speed modifier was always the stroll goal's. Keeping a
        // live path at all times means it just walks.
        if (--this.repathCooldown <= 0) {
            this.repathCooldown = REPATH_INTERVAL_TICKS;
            this.mob.getNavigation().moveTo(item.getX(), item.getY(), item.getZ(), APPROACH_SPEED);
        }

        if (++this.approachTicks > APPROACH_GIVE_UP_TICKS) {
            // Walled off, on a ledge, across water — whatever it is, it is not getting there. Drop
            // the offering instead of standing on it forever; the cooldown lets it try again later,
            // and the mob is free to do something else meanwhile.
            this.targetItem = null;
            this.approachTicks = 0;
            this.cooldownUntilTick = this.mob.tickCount + RETRY_COOLDOWN_TICKS;
        }
    }

    private boolean inEatRange(ItemEntity item) {
        return this.mob.getBoundingBox().inflate(EAT_REACH, 0.0D, EAT_REACH).intersects(item.getBoundingBox());
    }

    private void finishBite(ItemEntity item) {
        if (item.isRemoved() || item.getItem().isEmpty()) {
            this.targetItem = null;
            this.cooldownUntilTick = this.mob.tickCount + RETRY_COOLDOWN_TICKS;
            return;
        }

        Entity thrower = item.getOwner();
        item.getItem().shrink(1);
        if (item.getItem().isEmpty()) {
            item.discard();
        }

        ((ServerLevel) this.mob.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER,
                this.mob.getX(), this.mob.getY(0.5D), this.mob.getZ(), 4, 0.3D, 0.3D, 0.3D, 0.0D);

        // The count, the target and who ends up owning it all live in TameProgress; what stays here
        // is the krifto's own reaction to being tamed, which is nobody else's business.
        if (this.mob.tameProgress().feed(thrower)) {
            this.mob.startAction(KriftognathusEntity.ANIM_TAMED);
        }

        this.targetItem = null;
        this.cooldownUntilTick = this.mob.tickCount + RETRY_COOLDOWN_TICKS;
    }

    @Nullable
    public static ItemEntity findOffering(KriftognathusEntity mob) {
        List<ItemEntity> candidates = mob.level().getEntitiesOfClass(ItemEntity.class,
                mob.getBoundingBox().inflate(SEARCH_RADIUS), TameFeedGoal::isValidTarget);

        ItemEntity nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (ItemEntity candidate : candidates) {
            double distSq = mob.distanceToSqr(candidate);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private static boolean isValidTarget(ItemEntity item) {
        if (!item.isAlive() || item.hasPickUpDelay() || !item.getItem().is(Items.RABBIT)) {
            return false;
        }
        Entity thrower = item.getOwner();
        return thrower instanceof Player player && player.isAlive() && !player.isSpectator();
    }
}

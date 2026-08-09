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

/**
 * The wild-taming ritual: a krifto that finds rabbit meat a player dropped on the ground walks over
 * to it — landing first if it was flying, since it never eats mid-air — and bites it with the
 * {@code eating} clip. {@link KriftognathusEntity#incrementFeedProgress()} feedings later (rolled 3
 * or 4 on the first bite) it tames to whoever threw the last one and plays {@code tamed}.
 *
 * <p>Replaces the old right-click-with-rabbit tame roll entirely; see
 * {@code KriftognathusEntity#mobInteract}. Progress is never lost on interruption — only the current
 * bite is — so a hit taken mid-{@code eating} just ends that one bite, not the ritual.
 *
 * <p><b>Priority.</b> Registered at the same priority as {@code FlightWanderGoal} (7) — tied, not just
 * below, because {@code WrappedGoal#canBeReplacedBy} only yields a locked flag on a strict {@code <},
 * so a tie is what stops this goal from stealing MOVE/LOOK back the instant it re-evaluates {@code
 * canUse()}. This goal never drives the descent itself: while flying it stands down entirely (see
 * {@link #canUse()}/{@link #canContinueToUse()}) so {@code FlightWanderGoal} is free to actually tick
 * and run its own {@code flightDurationTimer}-driven stoop into {@code beginLanding()} — the only code
 * path that does. An earlier version sat at priority 6 (strictly above FlightWanderGoal) and called
 * {@code requestLanding()} from mid-air while still holding the flags itself; that starved
 * FlightWanderGoal's {@code tick()} completely; per {@code requestLanding()}'s own contract ("the mob
 * keeps flying until that goal lets go"), the timer expiry was never noticed and Krifto hovered at
 * the item's ground-level position forever, never actually landing. See {@code FollowOwnerFlyingGoal}'s
 * class note for the mirror-image case (yielding to {@code TakeoffGoal} on the way up).
 */
public class TameFeedGoal extends Goal {

    private static final double SEARCH_RADIUS = 16.0D;
    /**
     * Horizontal reach of the bite, added to the mob's own bounding box. This is vanilla's convention
     * for a mob reaching a ground item — {@code Mob#ITEM_PICKUP_REACH}, the {@code Vec3i(1, 0, 1)}
     * the loot pickup in {@code Mob#aiStep} inflates by — and not an arbitrary number.
     *
     * <p>A centre-to-centre distance check cannot work here.
     * {@code PathNavigation#moveTo(x, y, z, speed)} hardcodes {@code reachRange = 1}, and
     * {@code PathFinder} marks a target reached at {@code distanceManhattan(target) <= reachRange} —
     * so the path legitimately ends a whole block short of the item's block, and the mob then parks
     * there with navigation done and centre distance well past 1.5. Nothing ever closes the gap: the
     * goal re-issues the same {@code moveTo} every tick, gets the same already-satisfied path, and
     * waits forever — until something shoves the mob physically closer. Growing the bounding box
     * instead measures from the mob's edge, so it scales with the mob (a baby krifto's box is
     * smaller) and lines up with where the pathfinder actually leaves it standing.
     */
    private static final double EAT_REACH = 1.0D;
    /** Ticks before the next search after a bite that did not finish the ritual. */
    private static final int RETRY_COOLDOWN_TICKS = 40;
    /**
     * Walking speed of the approach. The same modifier {@code SMOPRandomStrollGoal} is registered
     * with, so crossing the ground for a meal looks exactly like crossing it for nothing — which is
     * the point: anything less reads as the mob dawdling toward food it supposedly wants.
     */
    private static final double APPROACH_SPEED = 1.0D;
    /** Ticks between re-paths while closing on the offering. @see #tick() */
    private static final int REPATH_INTERVAL_TICKS = 10;
    /** Ticks of failing to reach the offering before giving up on it. @see #tick() */
    private static final int APPROACH_GIVE_UP_TICKS = 300;

    private final KriftognathusEntity mob;

    @Nullable
    private ItemEntity targetItem;
    private int cooldownUntilTick;
    private boolean biting;
    /** Ticks spent closing on {@link #targetItem} without reaching it. @see #tick() */
    private int approachTicks;
    /** Counts down to the next re-path. @see #tick() */
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

    /**
     * Every tick, not every other one. The last stretch to the offering is steered by hand through
     * {@code MoveControl#setWantedPosition} (see {@link #tick()}), and {@code Mob#serverAiStep} only
     * runs the full goal selector on alternate ticks — so without this that steering, and the re-path
     * countdown with it, run at half rate. Not wrong, but it is the other half of why the walk over
     * looked sluggish.
     */
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

    /** Close enough to bite — see {@link #EAT_REACH} for why this is a box test and not a distance. */
    private boolean inEatRange(ItemEntity item) {
        return this.mob.getBoundingBox().inflate(EAT_REACH, 0.0D, EAT_REACH).intersects(item.getBoundingBox());
    }

    /** Consumes one unit of the meat, logs the feeding, and closes the ritual if that was the last one. */
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

        int progress = this.mob.incrementFeedProgress();
        if (progress >= this.mob.getFeedGoal() && thrower instanceof Player player) {
            this.mob.tame(player);
            this.mob.level().broadcastEntityEvent(this.mob, (byte) 7);
            this.mob.startAction(KriftognathusEntity.ANIM_TAMED);
        }

        this.targetItem = null;
        this.cooldownUntilTick = this.mob.tickCount + RETRY_COOLDOWN_TICKS;
    }

    /**
     * The nearest player-thrown offering within {@link #SEARCH_RADIUS}, or {@code null}.
     *
     * <p>Static because {@code KriftognathusEntity} needs the same answer from outside the goal:
     * while airborne this goal stands down entirely (see the class note), so it is not running to
     * spot an offering, ask for the descent, and aim that descent at it. The entity does all three
     * on its behalf, and both callers have to agree on what counts — hence one implementation.
     */
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

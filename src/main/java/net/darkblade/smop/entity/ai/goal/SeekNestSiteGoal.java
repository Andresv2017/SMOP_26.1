package net.darkblade.smop.entity.ai.goal;

import net.darkblade.smop.entity.SMOPAnimal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Sends a gravid animal <b>away</b> to nest, and holds it on the spot it chose until it lays.
 *
 * <p>This is the piece {@code GenericLayEggGoal} assumes rather than provides. That goal waits forty
 * ticks and then asks the mob to lay where it stands, which is exactly right for the Tangoftero and
 * the Kriftognathus: they nest on air above any solid block, so wherever the countdown ends is
 * somewhere legal. It stops being right for a species that is fussy about the ground — and it never
 * looked right for any of them, because an animal that mates and immediately lays under its own feet
 * reads as a glitch rather than as nesting.
 *
 * <p><b>The journey is mandatory, not incidental.</b> A site is only a candidate if it is at least
 * {@link #MIN_TRAVEL_DISTANCE} blocks off, and the one taken is picked at random among those so two
 * nestings never look like the same scripted walk. Only when the animal has no such site anywhere in
 * range does it fall back to the nearest legal spot, so a female on a small island still lays rather
 * than carrying the clutch forever.
 *
 * <p><b>Arrival gates the laying, which is why {@link #hasArrived()} is public.</b> Nothing else can
 * stop the countdown from firing mid-journey the moment the animal happens to cross legal ground:
 * the mob's {@code isSettledToLay()} has to consult this goal. Walking to the nest is the easy half;
 * standing still on it for the two seconds the laying goal needs is the half that was missing, and
 * it is why this reserves MOVE while {@code GenericLayEggGoal} reserves nothing — this one owns
 * where the animal is, that one owns the egg.
 *
 * <p><b>Why the path is re-issued rather than set once.</b> An amphibious mob swaps navigators as it
 * enters and leaves the water, and the water-bound one cannot path onto dry land: asked to, it
 * returns the closest approach it can manage, which is the shallows. That is the right first leg.
 * Re-pathing on an interval lets the ground navigator pick up the second leg the moment the animal
 * is standing on the beach, without this goal having to know that any of it happened.
 */
public class SeekNestSiteGoal extends Goal {

    /** Box searched around the animal: horizontal reach, then vertical. */
    private static final int SCAN_RADIUS = 16;
    private static final int SCAN_HEIGHT = 6;

    /** How far the animal must travel before it will nest. @see SeekNestSiteGoal */
    private static final int MIN_TRAVEL_DISTANCE = 8;

    /** Ticks between two sweeps for a site, and between two re-paths on the way there. */
    private static final int SCAN_INTERVAL = 20;
    private static final int REPATH_INTERVAL = 20;

    /**
     * Slack added to half the animal's width to decide it is standing on the site.
     *
     * <p><b>Sized off the body, not fixed.</b> A path is finished when the navigator says so, and how
     * far that leaves the FEET block from the target scales with how wide the animal is: a flat
     * couple of blocks is fine for a chicken and unreachable for something three blocks across, which
     * simply never registered as having arrived and so never laid. That the egg still lands correctly
     * from a couple of blocks out is not luck — {@code tryLayEgg} places it at the animal's own
     * position and re-tests the ground there, so this only has to be generous enough to end the walk.
     */
    private static final double ARRIVED_SLACK = 1.5D;

    private final SMOPAnimal mob;
    private final double speedModifier;
    private final Predicate<BlockPos> isNestSite;

    /** Re-paths that ended with the navigator idle and the animal still not there. */
    private static final int FAILED_PATHS_BEFORE_RECHOOSE = 3;

    /** Sites this run has already failed to reach. @see #tick() */
    private final Set<BlockPos> rejected = new HashSet<>();

    private int scanCooldown;
    private int repathCooldown;
    private int failedPaths;
    @Nullable
    private BlockPos target;
    private boolean arrived;

    public SeekNestSiteGoal(@NotNull SMOPAnimal mob, double speedModifier,
                            @NotNull Predicate<BlockPos> isNestSite) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.isNestSite = isNestSite;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    /** Whether the animal is standing on the site it set out for. Gates {@code isSettledToLay()}. */
    public boolean hasArrived() {
        return this.arrived;
    }

    /** The site being walked to, or {@code null} when none has been chosen. For tooling. */
    @Nullable
    public BlockPos target() {
        return this.target;
    }

    /** Sites struck off this run as unreachable. For tooling. */
    public int rejectedCount() {
        return this.rejected.size();
    }

    @Override
    public boolean canUse() {
        if (!this.mob.hasEgg() || this.mob.isMammal()) {
            return false;
        }
        // A sweep is expensive and this is polled every other tick, so it is rationed. Nothing pays
        // for it but an animal that is actually carrying an egg.
        if (--this.scanCooldown > 0) {
            return false;
        }
        this.scanCooldown = SCAN_INTERVAL;
        this.target = this.chooseNestSite();
        return this.target != null;
    }

    /** Until the egg is out. The laying goal is what ends this one, by emptying {@code hasEgg}. */
    @Override
    public boolean canContinueToUse() {
        return this.mob.hasEgg() && !this.mob.isMammal() && this.target != null;
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
        this.failedPaths = 0;
        this.arrived = false;
    }

    @Override
    public void stop() {
        this.target = null;
        this.arrived = false;
        // Cleared per run: a spot unreachable from this beach may be the obvious one from the next.
        this.rejected.clear();
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.target == null) {
            return;
        }
        double reach = this.mob.getBbWidth() * 0.5D + ARRIVED_SLACK;
        if (this.mob.blockPosition().distSqr(this.target) <= reach * reach) {
            // Arrived. Stand still and let the laying goal have its forty ticks.
            this.arrived = true;
            this.mob.getNavigation().stop();
            return;
        }
        // The ground can change under a chosen site — a wave, a player, another nest laid on it.
        if (!this.isNestSite.test(this.target)) {
            this.target = this.chooseNestSite();
            this.repathCooldown = 0;
            return;
        }
        if (--this.repathCooldown > 0) {
            return;
        }
        this.repathCooldown = REPATH_INTERVAL;

        // Not moving and not there: the site cannot be reached from here — across a channel, up a
        // cliff, behind a wall. Try somewhere else rather than stand offshore staring at it, which
        // is the other way a female ends up gravid forever.
        if (this.mob.getNavigation().isDone() && ++this.failedPaths >= FAILED_PATHS_BEFORE_RECHOOSE) {
            this.failedPaths = 0;
            // Struck off for the rest of this run, or the random draw keeps offering the same
            // unreachable spot back and the animal never converges on one it can actually walk to.
            this.rejected.add(this.target);
            this.target = this.chooseNestSite();
            if (this.target == null) {
                return;
            }
        }
        this.mob.getNavigation().moveTo(
                this.target.getX() + 0.5D, this.target.getY(), this.target.getZ() + 0.5D,
                this.speedModifier);
    }

    /**
     * A random legal site at least {@link #MIN_TRAVEL_DISTANCE} away, or the nearest legal one of any
     * distance when the animal is somewhere too cramped to walk.
     */
    @Nullable
    private BlockPos chooseNestSite() {
        BlockPos origin = this.mob.blockPosition();
        List<BlockPos> distant = new ArrayList<>();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        double minTravelSqr = MIN_TRAVEL_DISTANCE * MIN_TRAVEL_DISTANCE;

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SCAN_RADIUS, -SCAN_HEIGHT, -SCAN_RADIUS),
                origin.offset(SCAN_RADIUS, SCAN_HEIGHT, SCAN_RADIUS))) {
            if (this.rejected.contains(pos) || !this.isNestSite.test(pos)) {
                continue;
            }
            double distance = pos.distSqr(origin);
            // betweenClosed reuses one mutable position for the whole sweep.
            if (distance >= minTravelSqr) {
                distant.add(pos.immutable());
            } else if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = pos.immutable();
            }
        }

        if (!distant.isEmpty()) {
            return distant.get(this.mob.getRandom().nextInt(distant.size()));
        }
        return nearest;
    }
}

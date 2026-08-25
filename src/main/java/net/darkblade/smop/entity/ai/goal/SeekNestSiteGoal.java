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

public class SeekNestSiteGoal extends Goal {

    private static final int SCAN_RADIUS = 16;
    private static final int SCAN_HEIGHT = 6;

    private static final int MIN_TRAVEL_DISTANCE = 8;

    private static final int SCAN_INTERVAL = 20;
    private static final int REPATH_INTERVAL = 20;

    private static final double ARRIVED_SLACK = 1.5D;

    private final SMOPAnimal mob;
    private final double speedModifier;
    private final Predicate<BlockPos> isNestSite;

    private static final int FAILED_PATHS_BEFORE_RECHOOSE = 3;

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

    public boolean hasArrived() {
        return this.arrived;
    }

    @Nullable
    public BlockPos target() {
        return this.target;
    }

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

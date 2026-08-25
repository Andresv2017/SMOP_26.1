package net.darkblade.smop.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class SmartSwimmingNavigation extends WaterBoundPathNavigation {

    private static final double DEFAULT_NODE_ACCEPT_RADIUS_SQR = 4.0D;

    private double nodeAcceptRadiusSqr = DEFAULT_NODE_ACCEPT_RADIUS_SQR;
    private double lookahead;

    public SmartSwimmingNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    public SmartSwimmingNavigation setNodeAcceptRadius(double blocks) {
        this.nodeAcceptRadiusSqr = blocks * blocks;
        return this;
    }

    public SmartSwimmingNavigation setLookahead(double blocks) {
        this.lookahead = blocks;
        return this;
    }

    @Override
    protected boolean canMoveDirectly(@NotNull Vec3 start, @NotNull Vec3 end) {
        BlockPos endBlock = BlockPos.containing(end);
        if (this.level.getBlockState(endBlock).isSolid()) {
            return false;
        }
        HitResult hit = this.level.clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.mob));
        return hit.getType() == HitResult.Type.MISS;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.path == null || this.path.isDone()) {
            return;
        }
        // Intermediate nodes only. Applying the slack to the FINAL node advances the path past its
        // own destination, so the navigator reports "done" with the mob still two blocks short —
        // it looks like arriving, but anything that then measures the real distance (a goal waiting
        // to be in reach) waits forever. Smoothing the route and stopping short of the destination
        // are different things; this margin is only meant to do the first.
        if (this.path.getNextNodeIndex() >= this.path.getNodeCount() - 1) {
            return;
        }
        Vec3 next = this.path.getNextEntityPos(this.mob);
        if (this.mob.distanceToSqr(next.x, next.y, next.z) < this.nodeAcceptRadiusSqr) {
            this.path.advance();
        }
        this.steerAhead();
    }

    private void steerAhead() {
        if (this.lookahead <= 0.0D || this.path == null || this.path.isDone()) {
            return;
        }
        double wanted = this.lookahead * this.lookahead;
        int last = this.path.getNodeCount() - 1;
        for (int i = this.path.getNextNodeIndex(); i <= last; i++) {
            Vec3 candidate = this.path.getEntityPosAtNode(this.mob, i);
            // The first node far enough ahead wins; the final node is the floor, so the animal always
            // still ends up steering at its actual destination rather than past it.
            if (i == last || this.mob.distanceToSqr(candidate.x, candidate.y, candidate.z) >= wanted) {
                this.mob.getMoveControl().setWantedPosition(
                        candidate.x, candidate.y, candidate.z, this.speedModifier);
                return;
            }
        }
    }
}

package net.darkblade.smop.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * {@link WaterBoundPathNavigation} with the two fixes DeluxeLib's {@code SmartFlyingNavigation}
 * applies to flight, transplanted to water.
 *
 * <p>They are transplanted rather than reused because the library's 3D navigation is not usable by a
 * swimmer as it stands: {@code SmartFlyingNavigation} extends {@code FlyingPathNavigation}, and
 * {@code PathCarrot}'s shortcut scan explicitly requires sturdy ground and <em>rejects fluids</em>,
 * so it would refuse every shortcut a fish could take. The ideas are a few lines each; the classes
 * are not portable. Promoting a shared "open-medium navigation" out of the library would fix that
 * properly — see PORT_ANALYSIS.md, which already flags the aquatic base as a candidate to push
 * upstream.
 *
 * <p><b>Direct routes.</b> Vanilla's A* returns block-centre nodes, so even a clear straight swim
 * comes back as a staircase and the mob visibly corrects course at every node.
 * {@link #canMoveDirectly} lets the navigator collapse a stretch it can cross in a straight line,
 * clipping against blocks only — {@code ClipContext.Fluid.NONE} is what makes water not count as an
 * obstruction, which is the whole point for a fish.
 *
 * <p><b>Forgiving arrival.</b> A swimmer carries momentum and cannot stop on a block centre, so the
 * default one-block node acceptance leaves it circling a node it keeps overshooting. Two blocks is
 * enough slack to keep it flowing down the path.
 */
public class SmartSwimmingNavigation extends WaterBoundPathNavigation {

    /** Squared node-acceptance radius: 4 = two blocks. The default, kept for the salmon. */
    private static final double DEFAULT_NODE_ACCEPT_RADIUS_SQR = 4.0D;

    private double nodeAcceptRadiusSqr = DEFAULT_NODE_ACCEPT_RADIUS_SQR;
    private double lookahead;

    public SmartSwimmingNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    /**
     * How close a node counts as reached, in blocks. Bigger suits a bigger animal: the slack is what
     * stops something with momentum circling a node it keeps overshooting.
     */
    public SmartSwimmingNavigation setNodeAcceptRadius(double blocks) {
        this.nodeAcceptRadiusSqr = blocks * blocks;
        return this;
    }

    /**
     * Steer at a point this far along the path instead of at the next node. Zero keeps the vanilla
     * behaviour, which is what the salmon still uses.
     *
     * <p><b>What this fixes.</b> A* returns block-centre nodes, so the heading the move control is
     * asked for changes discontinuously every time the path advances one. On a mob with a generous
     * turn budget that is invisible; on one capped at three degrees a tick it is not. A tick sample
     * of this animal showed yaw moving in runs of exactly 3.000 — the cap saturated — separated by
     * near-flat stretches, which is the signature of chasing a target that teleports. Aiming further
     * down the path means the steer point slides continuously instead of jumping, so the same cap
     * produces a smooth arc.
     *
     * <p>This is pure pursuit, and the lookahead is the usual trade: too short and the jumps return,
     * too long and the animal cuts corners because it stops tracking the route it was given.
     */
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

    /**
     * Re-points the move control at a node further along the path. @see #setLookahead(double)
     *
     * <p>Runs after {@code super.tick()} has already set the wanted position to the next node, and
     * simply overwrites it — cheaper and far less fragile than reimplementing the base's own path
     * following just to change which node it aims at.
     */
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

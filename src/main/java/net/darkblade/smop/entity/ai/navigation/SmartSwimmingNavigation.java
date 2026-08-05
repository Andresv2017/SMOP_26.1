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

    /** Squared node-acceptance radius: 4 = two blocks. */
    private static final double NODE_ACCEPT_RADIUS_SQR = 4.0D;

    public SmartSwimmingNavigation(Mob mob, Level level) {
        super(mob, level);
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
        if (this.mob.distanceToSqr(next.x, next.y, next.z) < NODE_ACCEPT_RADIUS_SQR) {
            this.path.advance();
        }
    }
}

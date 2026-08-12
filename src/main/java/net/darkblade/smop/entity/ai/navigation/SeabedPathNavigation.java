package net.darkblade.smop.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Walks the bottom of the water, and only swims when walking will not get there.
 *
 * <p>Built for the Hell Hippo, but nothing here is hippo-specific — the Nirasmosaurus is amphibious
 * too, and this is the shape it will want.
 *
 * <p><b>Why not {@code AmphibiousPathNavigation} on its own,</b> which is the obvious pick and was
 * what the hippo used before: its evaluator opens by setting {@code WATER} malus to <b>0.0</b> and
 * {@code WALKABLE} malus to <b>6.0</b> ({@code AmphibiousNodeEvaluator#prepare}). It does not merely
 * permit swimming, it <em>prefers</em> swimming to walking by a factor of six, so a route through
 * water comes back floating several blocks above the bed. That is fatal rather than merely ugly,
 * because {@code PathNavigation#followThePath} will only advance to the next waypoint when
 *
 * <pre>{@code Math.abs(mob.getY() - node.getY()) < 1.0}</pre>
 *
 * and an animal that sinks to the bottom never gets within a block of a node three above it. The
 * waypoint never advances, {@code timeoutPath()} fires, the path is recomputed, and the whole thing
 * starts again — which on screen is a mob planted on the seabed twitching in place.
 *
 * <p><b>The fix is {@link #setCanFloat(boolean) canFloat(false)} on a ground navigation.</b>
 * {@code WalkNodeEvaluator#getStart} opens with {@code if (this.canFloat() && this.mob.isInWater())}
 * and in that case walks the start node up to the surface; {@code GroundPathNavigation#getSurfaceY}
 * does the same. With the flag off, both anchor at the feet instead, the route hugs the floor, and
 * the Y gate above is satisfied on every waypoint. It is already {@code false} by default on
 * {@code NodeEvaluator} — it is set here explicitly because it is load-bearing and silent, and a
 * future change to a base class should not be able to switch it back without anyone noticing.
 *
 * <p>The owner still has to make water traversable at all, which is one line in its constructor and
 * is what vanilla's Drowned does for the same reason:
 *
 * <pre>{@code
 * this.setPathfindingMalus(PathType.WATER, 0.0F);
 * this.setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
 * }</pre>
 *
 * <p><b>The fallback.</b> A pure bottom-walker cannot cross a trench or scale a wall, so an inner
 * {@link AmphibiousPathNavigation} is kept for exactly those cases. Every {@code moveTo} tries the
 * walk first and only swims when the walk produced no route that {@link Path#canReach() reaches} —
 * so the animal never swims for the sake of it, and never gets stuck for the lack of it either.
 * Control returns to walking as soon as the swim finishes or the animal leaves the water.
 */
public class SeabedPathNavigation extends GroundPathNavigation {

    private final AmphibiousPathNavigation swimNav;

    /** Which of the two navigations is currently driving. @see #tick() */
    private boolean swimming;

    public SeabedPathNavigation(@NotNull Mob mob, @NotNull Level level) {
        super(mob, level);
        // See the class note: the single most load-bearing line in the file.
        this.setCanFloat(false);
        this.swimNav = new AmphibiousPathNavigation(mob, level);
    }

    // ───────────────────────────────────────────────────── ORDERS ─────

    @Override
    public boolean moveTo(double x, double y, double z, double speed) {
        Path walk = this.createPath(x, y, z, 1);
        return this.reaches(walk) ? this.walk(walk, speed) : this.swim(this.swimNav.moveTo(x, y, z, speed));
    }

    @Override
    public boolean moveTo(double x, double y, double z, int accuracy, double speed) {
        Path walk = this.createPath(x, y, z, accuracy);
        return this.reaches(walk)
                ? this.walk(walk, speed)
                : this.swim(this.swimNav.moveTo(x, y, z, accuracy, speed));
    }

    @Override
    public boolean moveTo(@NotNull Entity entity, double speed) {
        Path walk = this.createPath(entity, 1);
        return this.reaches(walk) ? this.walk(walk, speed) : this.swim(this.swimNav.moveTo(entity, speed));
    }

    /**
     * A caller handing over a finished path has already chosen its route, so there is nothing left to
     * fall back to — this one always walks. Every {@code moveTo} that names a <em>destination</em>
     * rather than a path is overridden above, so the fallback is not lost by going through here.
     */
    @Override
    public boolean moveTo(@Nullable Path path, double speed) {
        return this.walk(path, speed);
    }

    /**
     * A route that merely gets closer is not good enough to pick walking over swimming: {@code
     * canReach()} is false when the pathfinder ran out of options and returned its best partial
     * effort, which is precisely the trench-and-wall case the swim exists for.
     */
    private boolean reaches(@Nullable Path path) {
        return path != null && path.canReach();
    }

    private boolean walk(@Nullable Path path, double speed) {
        this.swimNav.stop();
        this.swimming = false;
        return super.moveTo(path, speed);
    }

    /** @param started what the swim navigation said when it was handed the destination */
    private boolean swim(boolean started) {
        super.stop();
        this.swimming = started;
        return started;
    }

    /**
     * Whether the swimming navigation is the one driving.
     *
     * <p>Public because the owner has to know: nothing in the mob's own physics produces upward
     * motion in water, so the entity has to add a vertical term while this is true — and must
     * <em>not</em> add it while walking the bed, where every step onto a ledge would otherwise lift
     * the animal off the floor.
     */
    public boolean isSwimming() {
        return this.swimming;
    }

    // ───────────────────────────────────────────────────── DELEGATION ─────

    /**
     * Hands the tick to whichever navigation is driving, and is also where control comes back: a swim
     * that has finished, or an animal that has left the water, has no business still being steered by
     * the swimming navigation, and dropping the mode here means the next order starts from walking.
     */
    @Override
    public void tick() {
        if (this.swimming) {
            if (!this.mob.isInWater() || this.swimNav.isDone()) {
                this.swimNav.stop();
                this.swimming = false;
                return;
            }
            this.swimNav.tick();
            return;
        }
        super.tick();
    }

    /**
     * {@code isDone} and {@link #getPath()} are not optional delegations, and getting them wrong is
     * invisible until the animal happens to swim: {@code DirectionalMoveControl} cuts speed to zero
     * the moment {@code getNavigation().isDone()} returns true, and its {@code PathCarrot} lookahead
     * steers off {@code getNavigation().getPath()}. Left undelegated, both would read the empty
     * ground navigation and the mob would stall mid-water with a live path it could not see.
     *
     * <p>{@code isInProgress()} is vanilla's {@code !isDone()}, so it follows from this one.
     */
    @Override
    public boolean isDone() {
        return this.swimming ? this.swimNav.isDone() : super.isDone();
    }

    @Override
    public @Nullable Path getPath() {
        return this.swimming ? this.swimNav.getPath() : super.getPath();
    }

    @Override
    public void stop() {
        this.swimNav.stop();
        this.swimming = false;
        super.stop();
    }

    @Override
    public void recomputePath() {
        if (this.swimming) {
            this.swimNav.recomputePath();
            return;
        }
        super.recomputePath();
    }

    @Override
    public boolean shouldRecomputePath(@NotNull BlockPos pos) {
        return this.swimming ? this.swimNav.shouldRecomputePath(pos) : super.shouldRecomputePath(pos);
    }

    @Override
    public boolean isStuck() {
        return this.swimming ? this.swimNav.isStuck() : super.isStuck();
    }

    @Override
    public void setSpeedModifier(double speed) {
        this.swimNav.setSpeedModifier(speed);
        super.setSpeedModifier(speed);
    }

    @Override
    public @Nullable BlockPos getTargetPos() {
        return this.swimming ? this.swimNav.getTargetPos() : super.getTargetPos();
    }

    /**
     * Deliberately <b>not</b> delegated, even while swimming.
     *
     * <p>This is what {@code GoalUtils#isNotStable} asks when a wander goal is filtering candidate
     * destinations, and the ground answer — <em>the block below is solid</em> — is the one that makes
     * a stroll goal pick spots on the seabed instead of spots in mid-water. The swimming navigation
     * loosens it to "the block below is not air", which is right for a swimmer and wrong for the
     * animal this class exists to serve. Answering with the strict rule at all times is what lets the
     * hippo share one plain stroll goal across land and water.
     */
    @Override
    public boolean isStableDestination(@NotNull BlockPos pos) {
        return super.isStableDestination(pos);
    }
}

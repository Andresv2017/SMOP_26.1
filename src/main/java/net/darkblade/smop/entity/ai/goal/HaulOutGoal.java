package net.darkblade.smop.entity.ai.goal;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;

/**
 * Drags an amphibious hunter out of the water after a target that has left it.
 *
 * <p><b>Why this cannot be a pathfinding problem.</b> A mob that swims and walks carries one
 * navigator for each medium, and when its quarry climbs onto the beach <em>neither of them can
 * produce that route</em> — this was measured with {@code /smop debug bite watch} rather than
 * reasoned about:
 *
 * <ul>
 *   <li>The swimming navigator's node evaluator only ever emits nodes in water. Asked for a target
 *       on dry land it returns the start node and nothing else: {@code path=1 nodes, canReach=false}.
 *   <li>The ground navigator never even runs the search.
 *       {@code GroundPathNavigation#canUpdatePath()} is {@code onGround() || isInLiquid() ||
 *       isPassenger()}, and {@code PathNavigation#createPath} bails out with {@code null} before
 *       pathfinding when that is false. An animal floating at the surface is none of the three.
 * </ul>
 *
 * <p>So the reading on the log was an animal sitting at <b>exactly</b> the same distance, to the
 * centimetre, for twenty seconds, flipping medium every couple of seconds as it bobbed — because the
 * buoyancy trim and {@code travel}'s counter-trim cancel around the waterline. Nothing was wrong with
 * the route. There was no behaviour that beaches the animal, and no amount of tuning maluses,
 * re-issuing paths or swapping navigators can invent one.
 *
 * <p><b>So it steers, and does not path.</b> Same answer the Kriftognathus reached for its aerial
 * chase — discard the path and drive the velocity straight at the quarry — for the same underlying
 * reason: the pathfinder is the wrong tool for a mob crossing a medium it has no nodes in. Once the
 * animal is on solid ground the goal ends and the ordinary melee chase, which now has a ground
 * navigator that will happily path, takes the last few blocks.
 */
public class HaulOutGoal extends Goal {

    /** Blocks per tick aimed for while hauling out. */
    private static final double SPEED = 0.22D;
    /** How fast the velocity blends toward the desired heading. High: this is a lunge, not a cruise. */
    private static final double ACCEL = 0.30D;
    /**
     * Upward push held while still in the water.
     *
     * <p>Without it the animal drives horizontally into the face of the beach and grinds there: the
     * shoreline is a step, and a swimmer with no vertical component has nothing to climb it with.
     */
    private static final double RISE = 0.06D;
    /** Extra lift while actually pressed against the shore, so a steeper lip still gets climbed. */
    private static final double CLIMB = 0.28D;

    /** Body yaw degrees per tick while hauling out — free of the swim control's stately cap. */
    private static final float TURN_SPEED = 12.0F;

    /** Gives up after this long, so a mob under an overhang does not shove at it forever. */
    private static final int MAX_TICKS = 120;
    /** And then leaves it alone for this long before trying again. */
    private static final int COOLDOWN_TICKS = 100;

    private final Mob mob;
    private final double maxRange;
    private final BooleanSupplier allowed;

    private int ticksRunning;
    private int cooldown;

    /**
     * @param maxRange how far ashore a target may be and still be worth beaching for
     * @param allowed  species gate — age, sleep, tameness; whatever should stop it happening
     */
    public HaulOutGoal(@NotNull Mob mob, double maxRange, @NotNull BooleanSupplier allowed) {
        this.mob = mob;
        this.maxRange = maxRange;
        this.allowed = allowed;
        // MOVE only. LOOK is left to the attack goal's look control so the head keeps tracking the
        // quarry through the haul-out, which is both what it should look like and what the water
        // bite's aim reads from.
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        return target != null
                && target.isAlive()
                && this.allowed.getAsBoolean()
                // The quarry is out and the hunter is in: the one case the navigators cannot answer.
                && !target.isInWater()
                && this.mob.isInWater()
                && this.mob.distanceToSqr(target) <= this.maxRange * this.maxRange;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive() || target.isInWater() || !this.allowed.getAsBoolean()) {
            return false;
        }
        // Done the moment it is standing on land: from here the ground navigator can route, so the
        // melee goal below takes the chase back.
        if (this.mob.onGround() && !this.mob.isInWater()) {
            return false;
        }
        return this.ticksRunning < MAX_TICKS;
    }

    @Override
    public void start() {
        this.ticksRunning = 0;
        // The navigator holds a path it cannot follow — to a node in the water, or none at all. Left
        // running it would keep writing a wanted position that fights the steering below.
        this.mob.getNavigation().stop();
    }

    @Override
    public void stop() {
        // Only the timeout earns a cooldown. Arriving ashore is a success and must be free to happen
        // again the moment the quarry goes back in and out.
        this.cooldown = this.ticksRunning >= MAX_TICKS ? COOLDOWN_TICKS : 0;
        this.ticksRunning = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        this.ticksRunning++;
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        this.mob.getNavigation().stop();
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        Vec3 to = target.position().subtract(this.mob.position());
        double distance = to.length();
        if (distance < 0.3D) {
            return;
        }

        Vec3 desired = to.scale(SPEED / distance);
        Vec3 velocity = this.mob.getDeltaMovement();
        velocity = velocity.add(desired.subtract(velocity).scale(ACCEL));

        if (this.mob.isInWater()) {
            // Pressed against the shore: the horizontal push is going nowhere, so trade it for lift.
            double lift = this.mob.horizontalCollision ? CLIMB : RISE;
            velocity = new Vec3(velocity.x, Math.max(velocity.y, lift), velocity.z);
        }
        this.mob.setDeltaMovement(velocity);

        // Face the heading by hand: with the navigation stopped the swim control has bowed out, and
        // nothing else is turning the body.
        if (velocity.horizontalDistanceSqr() > 1.0E-4D) {
            float wantYaw = (float) (Mth.atan2(velocity.z, velocity.x) * (180.0D / Math.PI)) - 90.0F;
            float yaw = Mth.approachDegrees(this.mob.getYRot(), wantYaw, TURN_SPEED);
            this.mob.setYRot(yaw);
            this.mob.yBodyRot = yaw;
        }
    }
}

package net.darkblade.smop.entity.ai.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;

/**
 * Open-water wandering for something large: long legs, a heading it commits to, and a depth it
 * patrols on purpose.
 *
 * <p><b>Why not {@code RandomSwimmingGoal}.</b> That one asks for
 * {@code BehaviorUtils.getRandomSwimmablePos(mob, 10, 7)} — a point up to ten blocks away in a
 * completely random direction, so roughly half of every draw lands <em>behind</em> the animal and the
 * next leg opens with a U-turn. Worse, it inherits {@code RandomStrollGoal#canContinueToUse}, which is
 * {@code !navigation.isDone()}: the goal <b>ends the moment it arrives</b> and the animal is parked
 * until the interval roll comes up again. Swim, stop, wait, spin around. On a three-block marine
 * reptile that reads as a fish twitching, which is exactly the complaint.
 *
 * <p>This fixes the three causes separately:
 *
 * <ul>
 *   <li><b>Heading.</b> The destination is drawn inside a cone around the way the animal is already
 *       pointing, so consecutive legs chain into a course instead of contradicting each other. A
 *       U-turn becomes something it chooses occasionally rather than the default.</li>
 *   <li><b>Length.</b> Legs are far longer than vanilla's ten and each one ends before it is
 *       reached, so the animal covers water instead of shuffling around in it. How long is
 *       per-animal — see {@link #legLength}.</li>
 *   <li><b>Continuity.</b> The next leg is queued <em>before</em> the current one ends — see
 *       {@link #tick()} — so the animal never arrives, never stops, and never has to be restarted by
 *       a random roll. That single change is what turns a series of hops into a swim.</li>
 * </ul>
 *
 * <p><b>Depth is chosen, not inherited.</b> Each leg rolls climb, descend or hold, then the result is
 * clamped into the band between the seabed and the surface. The animal therefore drifts up and down
 * over time and occasionally runs shallow, instead of settling at whatever depth it happened to spawn
 * at and staying there.
 */
public class SwimWanderGoal extends Goal {

    /** Tries per leg before giving up and leaving the current course alone. */
    private static final int ATTEMPTS = 24;

    /**
     * Fraction of the full leg length the last attempt is allowed to shrink to.
     *
     * <p><b>Without this the goal simply never starts in anything smaller than open ocean.</b> A leg
     * is drawn at sixteen to twenty-four blocks and rejected unless it lands in water; in a test pool,
     * a river, or a lake, every one of those draws falls on dry land and the animal stands still
     * forever. Later attempts therefore ask for progressively shorter legs and a wider cone, so the
     * long forward course is a preference rather than a requirement — the animal still crosses an
     * ocean in straight lines, and still finds somewhere to go in a pond.
     */
    private static final double MIN_RADIUS_SCALE = 0.18D;

    /** Cone half-angle the last attempt widens to, so a cornered animal may turn back. */
    private static final float MAX_SPREAD_DEGREES = 180.0F;

    /**
     * Leg length: this plus up to {@link #RADIUS_SPREAD} more.
     *
     * <p><b>A leg has to outlast the turn that opens it, and 12–18 did not.</b> Those were tried in
     * the belief that longer legs were making the course too straight to see the bank; the tick
     * samples that followed showed the opposite failure and it is much worse. Take off the five
     * blocks {@link #RETARGET_DISTANCE_SQR} hands over early and a 12–18 leg is seven to thirteen
     * blocks of water, which this animal crosses in ten to thirty-five ticks. Turning through the
     * {@link #HEADING_SPREAD_DEGREES} cone costs about forty. The leg therefore expired mid-turn,
     * every time, and the animal spent every tick of its life at its maximum turn rate reversing
     * direction roughly once a second — a weave with almost no net displacement, which is why it
     * appeared to sit in one place.
     *
     * <p>22–36 puts fifty to a hundred ticks of swimming behind each turn, so the shape of a leg is a
     * banked arc followed by a long committed run, which is the thing being aimed at. The bank is
     * visible because the arc is now smooth and sustained, not because the animal turns often.
     *
     * <p>These are the Nirasmosaurus's numbers. {@link #legLength} exists because the rule they come
     * from — a leg must outlast the turn that opens it — resolves to a different length for every
     * animal: it depends on the turn cap, the cone, and how fast the thing swims. A salmon turns
     * three times quicker and travels slower, so the same rule gives it shorter legs, not these.
     */
    private static final double DEFAULT_MIN_RADIUS = 22.0D;
    private static final double DEFAULT_RADIUS_SPREAD = 14.0D;

    /**
     * Half-angle of the cone the destination is drawn from, in degrees.
     *
     * <p>55. This has been both ways: 70 held the heading so well the course came out near-straight
     * and the bank was never visible, and 90 fixed that but overshot — a tick sample caught the animal
     * holding its turn cap for nineteen consecutive ticks, swinging ninety degrees in a second and a
     * half, which reads as a fish darting rather than a reptile cruising. The cone is what bounds how
     * big a course change a new leg can demand, so it is the number that decides how sharp the turns
     * are; the turn cap only decides how fast they are executed.
     *
     * <p>It is also the number that sets how long a turn takes, and therefore how long a leg has to be
     * to survive one — 55 degrees at the Nirasmosaurus's cap is roughly forty ticks including the ramp
     * in and out. Widen this and {@link #legLength} has to grow with it or the animal goes back to
     * weaving.
     *
     * <p>The occasional genuine turnaround comes from a leg being rejected against terrain, which is
     * a reason to turn rather than a coin flip.
     */
    private static final float DEFAULT_HEADING_SPREAD_DEGREES = 55.0F;

    /**
     * Distance from the destination at which the next leg is queued, squared.
     *
     * <p>Five blocks. The hand-over has to stay well clear of the navigator's node slack, or the path
     * reports itself finished before this fires and the stop it exists to prevent happens anyway — but
     * every block spent here is a block taken off the end of the leg, and {@link #DEFAULT_MIN_RADIUS}
     * explains at length what happens when the leg gets short. It is fixed rather than scaled because
     * it is set by the navigator's slack, which is a property of the path, not of the animal — so the
     * shortest leg any caller sets still has to leave several blocks on top of it.
     */
    private static final double RETARGET_DISTANCE_SQR = 25.0D;

    /** Vertical intent per leg: climb below the first, descend below the second, else hold. */
    private static final double CLIMB_CHANCE = 0.25D;
    private static final double DESCEND_CHANCE = 0.5D;
    private static final double CLIMB_MIN = 3.0D;
    private static final double CLIMB_SPREAD = 8.0D;
    private static final double HOLD_SPREAD = 4.0D;

    /** How far off the seabed and off the surface a destination is allowed to sit. */
    private static final double BED_CLEARANCE = 2.0D;
    private static final double SURFACE_CLEARANCE = 1.0D;

    /** Ceiling on the surface/seabed scans, so a deep ocean column cannot walk the whole world height. */
    private static final int SCAN_LIMIT = 48;

    private final PathfinderMob mob;
    private final double speedModifier;
    private final BooleanSupplier canRun;

    private double minRadius = DEFAULT_MIN_RADIUS;
    private double radiusSpread = DEFAULT_RADIUS_SPREAD;
    private float headingSpread = DEFAULT_HEADING_SPREAD_DEGREES;

    private double wantedX;
    private double wantedY;
    private double wantedZ;

    public SwimWanderGoal(@NotNull PathfinderMob mob, double speedModifier, @NotNull BooleanSupplier canRun) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.canRun = canRun;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    /**
     * How far a leg reaches: {@code min} blocks plus up to {@code spread} more.
     *
     * <p>The rule is in {@link #DEFAULT_MIN_RADIUS} and it is about <em>time</em>, not distance: the
     * leg has to last longer than the turn that opens it, or the animal is handed a new heading before
     * it has finished taking the last one and never does anything but weave. Working it out for a new
     * animal means three numbers — the cone below, the move control's turn cap and ramp, and how fast
     * the thing actually travels — not copying someone else's blocks.
     */
    public SwimWanderGoal legLength(double min, double spread) {
        this.minRadius = min;
        this.radiusSpread = spread;
        return this;
    }

    /** Half-angle of the heading cone, in degrees. @see #DEFAULT_HEADING_SPREAD_DEGREES */
    public SwimWanderGoal cone(float degrees) {
        this.headingSpread = degrees;
        return this;
    }

    @Override
    public boolean canUse() {
        return this.available() && this.pickLeg();
    }

    /**
     * Deliberately NOT {@code !navigation.isDone()}, which is what vanilla's stroll goal uses and what
     * makes it surrender the moment it arrives. This goal holds MOVE for as long as the animal is in
     * open water and has nothing better to do; {@link #tick()} keeps feeding it somewhere to be.
     */
    @Override
    public boolean canContinueToUse() {
        return this.available();
    }

    private boolean available() {
        return this.canRun.getAsBoolean() && this.mob.getTarget() == null && this.mob.isInWater();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.issue();
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    /**
     * Queues the next leg while the current one is still running.
     *
     * <p>This is the whole point of the class. Waiting for arrival — or for the navigator to report
     * itself done — means a visible stop, and a stop on an animal with this much momentum reads as a
     * stall. Handing over early lets the navigator blend one course into the next.
     */
    @Override
    public void tick() {
        boolean nearlyThere = this.mob.distanceToSqr(this.wantedX, this.wantedY, this.wantedZ)
                < RETARGET_DISTANCE_SQR;
        if (nearlyThere || this.mob.getNavigation().isDone()) {
            if (this.pickLeg()) {
                this.issue();
            }
        }
    }

    private void issue() {
        this.mob.getNavigation().moveTo(this.wantedX, this.wantedY, this.wantedZ, this.speedModifier);
    }

    /** Draws a destination and stores it. False leaves the current course untouched. */
    private boolean pickLeg() {
        double surface = this.surfaceY();
        double bed = this.bedY();

        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            // Relax with each failure: shorter leg, wider cone. The first draws are the ones that
            // produce the long committed courses; the last are there so confined water still works.
            double relax = (double) attempt / (ATTEMPTS - 1);
            double scale = Mth.lerp(relax, 1.0D, MIN_RADIUS_SCALE);
            double radius = (this.minRadius + this.mob.getRandom().nextDouble() * this.radiusSpread) * scale;
            float cone = (float) Mth.lerp(relax, this.headingSpread, MAX_SPREAD_DEGREES);
            // Rotate the current heading by a bounded amount rather than picking a world direction:
            // that bound IS the course-keeping.
            float spread = (this.mob.getRandom().nextFloat() * 2.0F - 1.0F) * cone;
            float yaw = this.mob.getYRot() + spread;
            double rad = Math.toRadians(yaw);
            double x = this.mob.getX() - Math.sin(rad) * radius;
            double z = this.mob.getZ() + Math.cos(rad) * radius;

            double y = this.mob.getY() + this.verticalIntent();
            double floor = bed + BED_CLEARANCE;
            double ceiling = surface - SURFACE_CLEARANCE;
            // A shallow pool can invert the band; centring on what room there is beats clamping to a
            // bound that no longer exists.
            y = ceiling <= floor ? (bed + surface) * 0.5D : Mth.clamp(y, floor, ceiling);

            BlockPos target = BlockPos.containing(x, y, z);
            if (!this.mob.level().hasChunkAt(target)) {
                continue;
            }
            if (!this.mob.level().getFluidState(target).is(FluidTags.WATER)) {
                continue;
            }
            this.wantedX = x;
            this.wantedY = y;
            this.wantedZ = z;
            return true;
        }
        return false;
    }

    private double verticalIntent() {
        double roll = this.mob.getRandom().nextDouble();
        if (roll < CLIMB_CHANCE) {
            return CLIMB_MIN + this.mob.getRandom().nextDouble() * CLIMB_SPREAD;
        }
        if (roll < DESCEND_CHANCE) {
            return -CLIMB_MIN - this.mob.getRandom().nextDouble() * CLIMB_SPREAD;
        }
        return (this.mob.getRandom().nextDouble() - 0.5D) * HOLD_SPREAD;
    }

    /** Y of the first non-water block above the animal — the top of the water it is in. */
    private double surfaceY() {
        BlockPos.MutableBlockPos pos = this.mob.blockPosition().mutable();
        for (int i = 0; i < SCAN_LIMIT && this.mob.level().getFluidState(pos).is(FluidTags.WATER); i++) {
            pos.move(Direction.UP);
        }
        return pos.getY();
    }

    /** Y of the first block below the animal that is not water — the bed it is swimming over. */
    private double bedY() {
        BlockPos.MutableBlockPos pos = this.mob.blockPosition().mutable();
        for (int i = 0; i < SCAN_LIMIT && this.mob.level().getFluidState(pos).is(FluidTags.WATER); i++) {
            pos.move(Direction.DOWN);
        }
        return pos.getY() + 1.0D;
    }
}

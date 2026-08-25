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

public class SwimWanderGoal extends Goal {

    private static final int ATTEMPTS = 24;

    private static final double MIN_RADIUS_SCALE = 0.18D;

    private static final float MAX_SPREAD_DEGREES = 180.0F;

    private static final double DEFAULT_MIN_RADIUS = 22.0D;
    private static final double DEFAULT_RADIUS_SPREAD = 14.0D;

    private static final float DEFAULT_HEADING_SPREAD_DEGREES = 55.0F;

    private static final double RETARGET_DISTANCE_SQR = 25.0D;

    private static final double CLIMB_CHANCE = 0.25D;
    private static final double DESCEND_CHANCE = 0.5D;
    private static final double CLIMB_MIN = 3.0D;
    private static final double CLIMB_SPREAD = 8.0D;
    private static final double HOLD_SPREAD = 4.0D;

    private static final double BED_CLEARANCE = 2.0D;
    private static final double SURFACE_CLEARANCE = 1.0D;

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

    public SwimWanderGoal legLength(double min, double spread) {
        this.minRadius = min;
        this.radiusSpread = spread;
        return this;
    }

    public SwimWanderGoal cone(float degrees) {
        this.headingSpread = degrees;
        return this;
    }

    @Override
    public boolean canUse() {
        return this.available() && this.pickLeg();
    }

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

    private double surfaceY() {
        BlockPos.MutableBlockPos pos = this.mob.blockPosition().mutable();
        for (int i = 0; i < SCAN_LIMIT && this.mob.level().getFluidState(pos).is(FluidTags.WATER); i++) {
            pos.move(Direction.UP);
        }
        return pos.getY();
    }

    private double bedY() {
        BlockPos.MutableBlockPos pos = this.mob.blockPosition().mutable();
        for (int i = 0; i < SCAN_LIMIT && this.mob.level().getFluidState(pos).is(FluidTags.WATER); i++) {
            pos.move(Direction.DOWN);
        }
        return pos.getY() + 1.0D;
    }
}

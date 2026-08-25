package net.darkblade.smop.entity.ai.goal;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;

public class HaulOutGoal extends Goal {

    private static final double SPEED = 0.22D;
    private static final double ACCEL = 0.30D;
    private static final double RISE = 0.06D;
    private static final double CLIMB = 0.28D;

    private static final float TURN_SPEED = 12.0F;

    private static final int MAX_TICKS = 120;
    private static final int COOLDOWN_TICKS = 100;

    private final Mob mob;
    private final double maxRange;
    private final BooleanSupplier allowed;

    private int ticksRunning;
    private int cooldown;

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

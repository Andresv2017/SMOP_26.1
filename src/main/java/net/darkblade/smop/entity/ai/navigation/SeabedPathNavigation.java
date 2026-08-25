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

public class SeabedPathNavigation extends GroundPathNavigation {

    private final AmphibiousPathNavigation swimNav;

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

    @Override
    public boolean moveTo(@Nullable Path path, double speed) {
        return this.walk(path, speed);
    }

    private boolean reaches(@Nullable Path path) {
        return path != null && path.canReach();
    }

    private boolean walk(@Nullable Path path, double speed) {
        this.swimNav.stop();
        this.swimming = false;
        return super.moveTo(path, speed);
    }

    private boolean swim(boolean started) {
        super.stop();
        this.swimming = started;
        return started;
    }

    public boolean isSwimming() {
        return this.swimming;
    }

    // ───────────────────────────────────────────────────── DELEGATION ─────

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

    @Override
    public boolean isStableDestination(@NotNull BlockPos pos) {
        return super.isStableDestination(pos);
    }
}

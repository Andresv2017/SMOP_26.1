package net.darkblade.smop.entity.ai.control;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class SmoothSwimLookControl extends LookControl {

    private static final float PITCH_SPEED = 3.0F;

    private static final float LEVEL_SPEED = 1.5F;

    private static final float HEAD_PITCH_LIMIT = 30.0F;

    private static final float REACQUIRE_MARGIN = 8.0F;

    private boolean tracking = true;

    public SmoothSwimLookControl(@NotNull Mob mob) {
        super(mob);
    }

    @Override
    public void tick() {
        Optional<Float> wantedYaw = this.lookAtCooldown > 0 ? this.getYRotD() : Optional.empty();
        if (this.lookAtCooldown > 0) {
            this.lookAtCooldown--;
        }
        wantedYaw.ifPresent(this::updateTracking);

        if (wantedYaw.isPresent() && this.tracking) {
            this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, wantedYaw.get(), this.yMaxRotSpeed);
            this.getXRotD().ifPresent(xRotD -> this.mob.setXRot(this.rotateTowards(
                    this.mob.getXRot(),
                    Mth.clamp(xRotD, -HEAD_PITCH_LIMIT, HEAD_PITCH_LIMIT),
                    Math.min(this.xMaxRotAngle, PITCH_SPEED))));
        } else {
            // getHeadRotSpeed(), not the stored yMaxRotSpeed: releasing a glance is the same motion as
            // taking it, and reusing the stale field is what made the head snap back to centre.
            this.mob.yHeadRot =
                    this.rotateTowards(this.mob.yHeadRot, this.mob.yBodyRot, this.mob.getHeadRotSpeed());
            this.mob.setXRot(this.rotateTowards(this.mob.getXRot(), 0.0F, LEVEL_SPEED));
        }
        this.clampHeadRotationToBody();
    }

    private void updateTracking(float wantedYaw) {
        float demand = Math.abs(Mth.degreesDifference(this.mob.yBodyRot, wantedYaw));
        float limit = this.mob.getMaxHeadYRot();
        if (this.tracking) {
            this.tracking = demand <= limit;
        } else {
            this.tracking = demand < limit - REACQUIRE_MARGIN;
        }
    }
}

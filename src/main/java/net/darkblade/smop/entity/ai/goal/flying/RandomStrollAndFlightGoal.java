package net.darkblade.smop.entity.ai.goal.flying;

import net.darkblade.smop.entity.SMOPFlyingAnimal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.HoverRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One wander goal for both modes: on the ground it is vanilla's water-avoiding stroll, in the air it
 * picks a hover position instead. Having a single goal own both means the mob does not have to
 * arbitrate between a ground wander and an air wander every time it takes off.
 */
public class RandomStrollAndFlightGoal extends WaterAvoidingRandomStrollGoal {

    private final SMOPFlyingAnimal flier;

    public RandomStrollAndFlightGoal(SMOPFlyingAnimal mob, double speedModifier) {
        super(mob, speedModifier);
        this.flier = mob;
    }

    @Override
    public boolean canUse() {
        return !this.flier.isBaby() && !this.flier.isMovementLocked() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return !this.flier.isBaby() && !this.flier.isMovementLocked() && super.canContinueToUse();
    }

    @Override
    protected @Nullable Vec3 getPosition() {
        if (!this.flier.isFlying()) {
            return super.getPosition();
        }
        Vec3 forward = this.mob.getViewVector(0.0F);
        // A hover position first — it keeps the mob at a sane altitude instead of drifting up
        // forever — falling back to any reachable air/water position when there is none.
        Vec3 hover = HoverRandomPos.getPos(this.mob, 8, 7, forward.x, forward.z, (float) Math.PI / 2F, 3, 1);
        return hover != null
                ? hover
                : AirAndWaterRandomPos.getPos(this.mob, 8, 4, -2, forward.x, forward.z, (float) Math.PI / 2F);
    }
}

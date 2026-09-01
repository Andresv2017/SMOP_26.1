package net.darkblade.smop.entity.gt;

import net.darkblade.deluxelib.entity.ai.cortex.Blackboard;
import net.darkblade.deluxelib.entity.ai.cortex.routine.StrikePicker;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class GTStrikePicker implements StrikePicker<GTEntity, GTState> {

    private static final double ATTACK_RANGE = 8.0D;

    private static final double STOMP_RANGE = 6.0D;

    private static final int STOMP_WEIGHT = 30;
    private static final int TOTAL_WEIGHT = 100;

    private static final int STOMP_GAP_TICKS = 60;

    private static final double STOMP_MAX_Y_DIFF = 1.0D;

    private static final double FRONTAL_SLACK = 1.0D;

    private final GTState[] frontalAttacks =
            {GTState.BITE, GTState.HORN_SWING, GTState.CLAW_SWING};

    private long nextStompTime;

    @Override
    public @Nullable GTState pick(GTEntity gt, Blackboard bb) {
        LivingEntity target = gt.getTarget();
        if (target == null || !target.isAlive()) {
            return null;
        }

        double distance = gt.distanceTo(target);
        if (distance > ATTACK_RANGE) {
            return null;
        }

        boolean inFront = this.insideFrontalBox(gt, target);
        boolean stompReady = distance <= STOMP_RANGE && this.canStomp(gt, target);

        // Outside the frontal box — behind, or simply off to one side: none of the three frontal
        // attacks can reach, so the stomp is the answer. It is radial and does not care which way the
        // animal is pointing.
        if (!inFront) {
            if (stompReady) {
                this.nextStompTime = gt.level().getGameTime() + STOMP_GAP_TICKS;
                return GTState.STOMP;
            }
            // Nothing viable. Returning null keeps PursueRoutine running, which keeps steering —
            // the animal turns instead of swinging at empty air.
            return null;
        }

        if (stompReady && gt.getRandom().nextInt(TOTAL_WEIGHT) < STOMP_WEIGHT) {
            this.nextStompTime = gt.level().getGameTime() + STOMP_GAP_TICKS;
            return GTState.STOMP;
        }

        return this.frontalAttacks[gt.getRandom().nextInt(this.frontalAttacks.length)];
    }

    private boolean insideFrontalBox(GTEntity gt, LivingEntity target) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, gt.getYRot());
        double dx = target.getX() - gt.getX();
        double dz = target.getZ() - gt.getZ();

        // Forward component along the facing axis, and the perpendicular offset from it.
        double forwardDistance = forward.x * dx + forward.z * dz;
        double lateralOffset = Math.abs(forward.x * dz - forward.z * dx);

        double slack = FRONTAL_SLACK + target.getBbWidth() / 2.0D;
        return forwardDistance >= GTEntity.FRONTAL_START - slack
                && forwardDistance <= GTEntity.FRONTAL_START + GTEntity.FRONTAL_LENGTH + slack
                && lateralOffset <= GTEntity.FRONTAL_HALF_WIDTH + slack;
    }

    private boolean canStomp(GTEntity gt, LivingEntity target) {
        return gt.onGround()
                && Math.abs(target.getY() - gt.getY()) < STOMP_MAX_Y_DIFF
                && gt.getSensing().hasLineOfSight(target)
                && gt.level().getGameTime() >= this.nextStompTime;
    }
}

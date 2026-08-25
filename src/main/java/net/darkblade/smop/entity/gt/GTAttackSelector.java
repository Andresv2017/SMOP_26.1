package net.darkblade.smop.entity.gt;

import net.darkblade.deluxelib.entity.ai.cortex.StateEnum;
import net.darkblade.deluxelib.entity.ai.cortex.behavior.BehaviorContext;
import net.darkblade.deluxelib.entity.ai.cortex.behavior.impl.AttackSelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Decides which attack the Grand Tyrant throws, and is the only place its ranges, weights and angles
 * live.
 *
 * <p>Ticking and damage are the animation's job — see the {@code HitWindow}s in
 * {@code GTEntity.registerAnimations} — so all that is left here is the choice.
 *
 * <p>Returning {@code null} means "no attack is viable right now"; {@code ChaseTargetBehavior} then
 * keeps closing and, crucially, keeps turning.
 */
public final class GTAttackSelector implements AttackSelector<GTEntity> {

    /**
     * Reach of the three frontal attacks.
     *
     * <p>Kept in step with {@code GTEntity.FRONTAL_START + FRONTAL_LENGTH}, which is 8.5. The two are a
     * pair: raise this without raising those and the animal commits to swings its own hitbox cannot
     * cover.
     */
    private static final double ATTACK_RANGE = 8.0D;

    /**
     * The stomp reaches <b>less</b> far than the others, not further — {@code STOMP_RANGE = 6.0}
     * against 8. It is a close slam with a wide area, not a long-reach attack.
     */
    private static final double STOMP_RANGE = 6.0D;

    /** Legacy weights: in front and inside stomp range, 30 in 100 stomp and 70 in 100 something else. */
    private static final int STOMP_WEIGHT = 30;
    private static final int TOTAL_WEIGHT = 100;

    /**
     * Minimum ticks between stomps. Without it the weighted roll can come up stomp several times
     * running, and at 67 ticks a throw that is nine seconds of the fight being one move.
     */
    private static final int STOMP_GAP_TICKS = 60;

    /** Height difference above which a stomp would hit nothing. */
    private static final double STOMP_MAX_Y_DIFF = 1.0D;

    /**
     * Slack added to the frontal box when deciding whether a target is inside it, in blocks.
     *
     * <p>Covers the target's own half-width and a tick or two of its movement, so the animal is not
     * paralysed by someone hovering exactly on the boundary.
     */
    private static final double FRONTAL_SLACK = 1.0D;

    private final StateEnum[] frontalAttacks =
            {GTState.BITE, GTState.HORN_SWING, GTState.CLAW_SWING};

    /** Game time the stomp becomes available again. Server-side; the selector only runs there. */
    private long nextStompTime;

    @Override
    public @Nullable StateEnum select(GTEntity gt, BehaviorContext context) {
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
            // Nothing viable. Returning null keeps ChaseTargetBehavior running, which keeps steering —
            // the animal turns instead of swinging at empty air.
            return null;
        }

        if (stompReady && gt.getRandom().nextInt(TOTAL_WEIGHT) < STOMP_WEIGHT) {
            this.nextStompTime = gt.level().getGameTime() + STOMP_GAP_TICKS;
            return GTState.STOMP;
        }

        return this.frontalAttacks[gt.getRandom().nextInt(this.frontalAttacks.length)];
    }

    /**
     * Whether the target actually sits inside the volume a frontal attack would sweep.
     *
     * <p><b>This replaced an angular cone, and the difference is the whole point.</b> The cone allowed
     * anything within about 70 degrees, while the box is 3 blocks either side of the facing axis — at
     * six blocks out, a target four blocks to one side is 34 degrees off, comfortably inside that cone
     * and comfortably outside the box. The animal committed to a swing, the animation locked its
     * movement for 19 to 67 ticks, and it only turned in the gaps: five or six attacks before it
     * happened to line up. Reported from the game exactly that way.
     *
     * <p>So the gate is the box itself, mirroring {@code AttackShape.Box3D#contains}: forward distance
     * inside the box's span, lateral offset inside its half-width. Measured against {@code getYRot()} —
     * the BODY yaw — because that is the axis {@code HitWindow} builds the box on. The head yaw would
     * let it commit to a swing its body has not turned into yet.
     */
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

    /**
     * Three conditions beyond range, all about the ground actually being shared: the animal has to be
     * standing on it, the target has to be at substantially the same height, and it has to be able to
     * see it. Stomping at someone on a ledge two blocks up is what this rules out.
     */
    private boolean canStomp(GTEntity gt, LivingEntity target) {
        return gt.onGround()
                && Math.abs(target.getY() - gt.getY()) < STOMP_MAX_Y_DIFF
                && gt.getSensing().hasLineOfSight(target)
                && gt.level().getGameTime() >= this.nextStompTime;
    }
}

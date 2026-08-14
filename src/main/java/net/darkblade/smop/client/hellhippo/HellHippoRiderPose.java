package net.darkblade.smop.client.hellhippo;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * How a player sits on a Hell Hippo, in the {@code HumanoidPoseApplier} format (bones {@code torso},
 * {@code head}, {@code right_arm}, {@code left_arm}, {@code right_leg}, {@code left_leg}).
 * {@code HellHippoRenderer#applyRiderPose} applies it.
 *
 * <p><b>Which channels this authors is the whole design</b>, the same lesson {@code KriftoRiderPose}
 * spells out: the applier resets only the channels a definition actually declares and leaves the rest
 * to vanilla. An all-zero rotation channel is <em>not</em> a no-op — it pins the bone to its bind
 * pose. So this authors:
 *
 * <ul>
 *   <li><b>Legs</b>, rotation only — swung forward and splayed outward to straddle a body two and a
 *       half blocks wide. Replacing vanilla's walk swing is the point; a rider whose legs keep
 *       striding in mid-air is the thing this exists to stop.</li>
 *   <li><b>Arms</b>, rotation only — forward and slightly inward, hands where reins would be.</li>
 *   <li><b>Torso</b>, a small forward lean, so the rider reads as leaning into the animal rather than
 *       sitting to attention on it.</li>
 * </ul>
 *
 * <p>The head is deliberately left alone. Authoring nothing for it keeps vanilla's head-look running,
 * so the rider still looks where the player is looking.
 *
 * <p>Hand-authored rather than exported from Blockbench, so the numbers are readable and tunable in
 * place. Re-posing it on a player-replica rig and exporting over this file works exactly as well.
 */
public final class HellHippoRiderPose {

    public static final AnimationDefinition SEATED = AnimationDefinition.Builder.withLength(0.0F)
            .addAnimation("torso", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(10.0F, 0.0F, 0.0F),
                            AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("right_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-55.0F, 0.0F, 12.5F),
                            AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("left_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-55.0F, 0.0F, -12.5F),
                            AnimationChannel.Interpolations.LINEAR)
            ))
            // ── The legs, and the axis that took two tries to get right ──
            //
            // The splay is the MIDDLE number, Y — not Z. ModelPart composes its rotations as
            // Rz · Ry · Rx, so X is applied first: by the time the leg has swung 81° forward it is
            // pointing almost straight down the Z axis, and rotating a Z-aligned limb about Z only
            // twists it in place. That is why the first two attempts here read as knees clamped
            // together no matter which sign they carried — the number was doing nothing.
            //
            // Vanilla's own riding pose says the same thing plainly (HumanoidModel#setupAnim, the
            // isPassenger branch): xRot -81°, yRot ±18°, plus a 4.5° roll on Z. Those are the numbers
            // below, with the yaw opened from 18° to 30° because a hippo is 2.5 blocks across where a
            // horse is nearer 1.4 — the rider has considerably more animal to straddle.
            //
            // Positive Y on the right leg, negative on the left, opens the pair outward.
            .addAnimation("right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-81.0F, 30.0F, 4.5F),
                            AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-81.0F, -30.0F, -4.5F),
                            AnimationChannel.Interpolations.LINEAR)
            ))
            .build();

    private HellHippoRiderPose() {}
}

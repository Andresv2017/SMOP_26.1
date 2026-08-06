package net.darkblade.smop.client.krifto;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * The two poses a player is held in while a tamed Kriftognathus is perched over their head, in the
 * {@code HumanoidPoseApplier} format (bones {@code right_arm}/{@code left_arm}/{@code right_leg}/
 * {@code left_leg}). {@code KriftognathusRenderer#applyHostPose} picks between them.
 *
 * <p><b>Which channels each one authors is the whole design.</b> {@code HumanoidPoseApplier} resets
 * only the channels a definition actually declares and leaves the rest to vanilla, so:
 * <ul>
 *   <li>{@link #GRIPPING} authors arms only — the legs stay on vanilla's walk swing, so you still
 *       stride normally while carrying it around on the ground.</li>
 *   <li>{@link #FALLING} authors arms <em>and</em> legs, replacing that swing with its own spread,
 *       drifting kick for the descent.</li>
 * </ul>
 * An all-zero rotation channel would NOT be a no-op — it pins the bone to its bind pose — which is
 * exactly why {@link #GRIPPING} omits the leg channels rather than zeroing them.
 */
public final class KriftoRiderPose {

    /**
     * Both arms swung up overhead, gripping the bird's HIND feet — its front limbs are the wings
     * ({@code gRight_wing} hangs off {@code gRight_leg1} in {@code KriftognathusModel}, pterosaur
     * fashion), so those are not something anyone can hold on to.
     *
     * <p>Aimed at where those hind claws actually land in {@code on_players_head}: ~1.87 blocks above
     * the host's feet and ~±0.30 out, once the back legs' 30° splay is applied. Shared by both poses
     * below.
     *
     * <p><b>Sign convention:</b> the arm starts hanging straight down and {@code xRot} swings it in
     * the ZY plane, where {@code -Z} is forward — vanilla aims a bow at {@code -90°}, horizontal and
     * forward. So {@code -180°} is dead vertical (what this uses), anything between 0 and -180 leans
     * FORWARD, and going past -180 tips it BACK. The two sides are symmetric: {@code -210°} and
     * {@code -150°} reach the same height and differ only in which way they lean.
     */
    private static final float ARM_X_ROT = -180.0F;
    private static final float ARM_Z_OUT = 0.0F;

    /**
     * On the ground: arms up, legs left alone so they walk. Zero-length single-keyframe export, so
     * {@code applyStatic} samples it as a static pose.
     */
    public static final AnimationDefinition GRIPPING = AnimationDefinition.Builder.withLength(0.0F)
        .addAnimation("right_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(ARM_X_ROT, 0.0F, -ARM_Z_OUT), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("left_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(ARM_X_ROT, 0.0F, ARM_Z_OUT), AnimationChannel.Interpolations.LINEAR)
        ))
        .build();

    /**
     * Mid-air: the same grip, plus legs spread apart and drifting back and forth — hanging weight,
     * not a walk cycle. Looping and a full second long, sampled against a running clock by
     * {@code KriftognathusRenderer}, so the sway actually plays instead of holding one frame.
     *
     * <p>Sign convention for the spread: on the RIGHT side of the body a positive {@code zRot} opens
     * the limb outward and a negative one folds it in across the centreline; the left side mirrors
     * it. The legs sway in opposite phase so they never look glued together.
     */
    public static final AnimationDefinition FALLING = AnimationDefinition.Builder.withLength(1.0F).looping()
        .addAnimation("right_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(ARM_X_ROT, 0.0F, -ARM_Z_OUT), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("left_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(ARM_X_ROT, 0.0F, ARM_Z_OUT), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-12.0F, 0.0F, 12.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.5F, KeyframeAnimations.degreeVec(10.0F, 0.0F, 14.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.0F, KeyframeAnimations.degreeVec(-12.0F, 0.0F, 12.0F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .addAnimation("left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(10.0F, 0.0F, -14.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(0.5F, KeyframeAnimations.degreeVec(-12.0F, 0.0F, -12.0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.0F, KeyframeAnimations.degreeVec(10.0F, 0.0F, -14.0F), AnimationChannel.Interpolations.CATMULLROM)
        ))
        .build();

    private KriftoRiderPose() {}
}

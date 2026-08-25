package net.darkblade.smop.client.krifto;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

public final class KriftoRiderPose {

    private static final float ARM_X_ROT = -180.0F;
    private static final float ARM_Z_OUT = 0.0F;

    public static final AnimationDefinition GRIPPING = AnimationDefinition.Builder.withLength(0.0F)
        .addAnimation("right_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(ARM_X_ROT, 0.0F, -ARM_Z_OUT), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("left_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(ARM_X_ROT, 0.0F, ARM_Z_OUT), AnimationChannel.Interpolations.LINEAR)
        ))
        .build();

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

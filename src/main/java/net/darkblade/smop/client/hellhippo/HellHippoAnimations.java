package net.darkblade.smop.client.hellhippo;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * Adult Hell Hippo clips, addressed against the bone names in {@link HellHippoModel}.
 *
 * <p><b>Dropped channels.</b> The export animated a {@code neck2} bone in {@code attack}, {@code bite},
 * {@code eat} and {@code shake} that {@link HellHippoModel} does not define. A channel naming a bone
 * the mesh lacks throws {@code "Cannot animate X, which does not exist in model"} at bake time, on the
 * very first frame the mob renders — so they are dropped rather than carried.
 */
public final class HellHippoAnimations {

          public static final AnimationDefinition pose = AnimationDefinition.Builder.withLength(0.0F)
                  .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 4.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-27.9712F, -18.5459F, 19.7072F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.SCALE,
                          new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 0.6F, 1.0F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(31.2822F, -9.298F, 14.8915F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("left_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(9.6658F, 2.5759F, -14.7822F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("left_calf", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-25.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("right_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-14.9864F, 0.6469F, 7.4149F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("right_calf", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(25.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("left_leg1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-17.3593F, -2.2494F, -12.1566F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("right_leg1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 12.5F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("tail", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(26.6946F, 6.8638F, -13.3696F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("tailend", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(17.5F, 0.0F, -20.0F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .build();

          public static final AnimationDefinition idle = AnimationDefinition.Builder.withLength(5.25F).looping()
                  .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.98F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.degreeVec(2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.8F, KeyframeAnimations.degreeVec(-2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.15F, KeyframeAnimations.degreeVec(2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(4.45F, KeyframeAnimations.degreeVec(-2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(5.25F, KeyframeAnimations.degreeVec(0.98F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.35F, KeyframeAnimations.degreeVec(7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.65F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(4.0F, KeyframeAnimations.degreeVec(7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(5.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.35F, KeyframeAnimations.posVec(0.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.65F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(4.0F, KeyframeAnimations.posVec(0.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(5.25F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("tail", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 2.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -2.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 2.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -2.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(4.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 2.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(4.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -1.9F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(5.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 2.5F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("tailend", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -5.16F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 7.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -7.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 7.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -7.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(4.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 7.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(5.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -5.16F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.35F, KeyframeAnimations.degreeVec(2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.65F, KeyframeAnimations.degreeVec(-2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(4.0F, KeyframeAnimations.degreeVec(2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(5.25F, KeyframeAnimations.degreeVec(-2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.SCALE,
                          new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.85F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.0F, KeyframeAnimations.scaleVec(1.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.15F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(4.55F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(4.7F, KeyframeAnimations.scaleVec(1.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(4.9F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_ear", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.65F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.75F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.9F, KeyframeAnimations.degreeVec(-15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.0F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.15F, KeyframeAnimations.degreeVec(-15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.25F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.4F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_ear", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.65F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.75F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.9F, KeyframeAnimations.degreeVec(-15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.0F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.15F, KeyframeAnimations.degreeVec(-15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.25F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.4F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("torso", new AnimationChannel(AnimationChannel.Targets.SCALE,
                          new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.35F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.0F, KeyframeAnimations.scaleVec(1.05F, 1.05F, 1.05F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.6F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(4.65F, KeyframeAnimations.scaleVec(1.05F, 1.05F, 1.05F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(5.25F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .build();

          public static final AnimationDefinition walk = AnimationDefinition.Builder.withLength(1.5F).looping()
                  .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.degreeVec(2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.degreeVec(2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.degreeVec(7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.posVec(0.0F, 1.5F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.posVec(0.0F, 1.5F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-1.2963F, 0.0F, -4.0741F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -5.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5F, KeyframeAnimations.degreeVec(-5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.9F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 5.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.25F, KeyframeAnimations.degreeVec(-5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.degreeVec(-1.2963F, 0.0F, -4.0741F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.posVec(0.0F, 3.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.posVec(0.0F, 3.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("tail", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -15.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 15.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -15.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-25.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(17.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.degreeVec(-25.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_leg2", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, -3.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.posVec(0.0F, 2.5F, 0.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_leg1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(12.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(-25.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.degreeVec(12.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_leg1", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.posVec(0.0F, 3.0F, -1.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, -2.0F, -3.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_leg1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-25.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(12.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.degreeVec(-25.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_leg1", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -2.0F, -3.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.posVec(0.0F, 3.0F, -1.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, -2.0F, -3.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("tailend", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -7.22F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -15.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 15.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -7.22F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(17.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(-25.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.degreeVec(17.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_leg2", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -3.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.posVec(0.0F, 2.5F, 0.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, -3.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5F, KeyframeAnimations.degreeVec(5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.9F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.25F, KeyframeAnimations.degreeVec(5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.degreeVec(-5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5F, KeyframeAnimations.degreeVec(5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.25F, KeyframeAnimations.degreeVec(7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_calf", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(25.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.degreeVec(2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.degreeVec(-25.31F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.degreeVec(25.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_calf", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.25F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.posVec(0.0F, 2.38F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 1.84F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, 0.25F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_calf", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.degreeVec(-25.31F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(25.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.degreeVec(2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_calf", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, 1.84F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.25F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.posVec(0.0F, 2.38F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.SCALE,
                          new Keyframe(0.3F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.scaleVec(1.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.55F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.9F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.05F, KeyframeAnimations.scaleVec(1.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("torso", new AnimationChannel(AnimationChannel.Targets.SCALE,
                          new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.9F, KeyframeAnimations.scaleVec(1.05F, 1.05F, 1.05F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .build();

          public static final AnimationDefinition sprint = AnimationDefinition.Builder.withLength(0.75F).looping()
                  .addAnimation("Hipopotamo_Infernal", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 4.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(1.39F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.degreeVec(-2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5F, KeyframeAnimations.degreeVec(12.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(1.39F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.posVec(0.0F, -1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(12.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.degreeVec(-17.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(12.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_ear", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -13.04F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.05F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -12.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -27.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.7F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -12.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -13.04F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_ear", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 20.54F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.05F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 17.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 32.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.7F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 17.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 20.54F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(41.75F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.degreeVec(25.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(41.75F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("left_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(3.11F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.degreeVec(25.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.55F, KeyframeAnimations.degreeVec(-27.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(3.11F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_leg2", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -4.33F, -4.56F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.posVec(0.0F, -8.0F, -4.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.35F, KeyframeAnimations.posVec(0.0F, 6.33F, -0.44F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.55F, KeyframeAnimations.posVec(0.0F, 0.0F, -1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, -4.33F, -4.56F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-29.77F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.degreeVec(3.11F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.3F, KeyframeAnimations.degreeVec(25.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.degreeVec(-35.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(-29.77F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_leg2", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -1.6F, -0.9F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.posVec(0.0F, -1.33F, -0.56F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.3F, KeyframeAnimations.posVec(0.0F, 1.0F, -3.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.posVec(0.0F, 3.33F, -0.44F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.posVec(0.0F, -2.0F, -1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, -1.6F, -0.9F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_leg1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-30.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.degreeVec(30.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(-30.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_leg1", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -4.0F, -2.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.posVec(0.0F, -5.19F, -3.51F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.2F, KeyframeAnimations.posVec(0.0F, -2.0F, -4.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.posVec(0.0F, -0.64F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.6F, KeyframeAnimations.posVec(0.0F, 3.0F, -4.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, -4.0F, -2.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_leg1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-14.44F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.degreeVec(-30.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5F, KeyframeAnimations.degreeVec(17.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(-14.44F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_leg1", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 2.01F, -4.35F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.posVec(0.0F, -3.0F, -4.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.35F, KeyframeAnimations.posVec(0.0F, -3.0F, -4.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, -4.64F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.7F, KeyframeAnimations.posVec(0.0F, 3.0F, -4.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 2.01F, -4.35F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("tail", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(17.86F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.1F, KeyframeAnimations.degreeVec(12.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.degreeVec(55.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(17.86F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("tailend", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(11.28F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.degreeVec(-20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.55F, KeyframeAnimations.degreeVec(55.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(11.28F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.degreeVec(10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -3.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.posVec(0.0F, -3.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.55F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, -3.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.SCALE,
                          new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 0.6F, 1.0F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("left_calf", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.degreeVec(17.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.degreeVec(-20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.55F, KeyframeAnimations.degreeVec(10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(-2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_calf", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.55F, KeyframeAnimations.posVec(0.0F, -4.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_calf", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.2F, KeyframeAnimations.degreeVec(-2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.degreeVec(17.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.degreeVec(-20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_calf", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -4.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.2F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, -4.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("torso", new AnimationChannel(AnimationChannel.Targets.SCALE,
                          new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.25F, KeyframeAnimations.scaleVec(1.05F, 1.05F, 1.05F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.scaleVec(1.05F, 1.05F, 1.05F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .build();

          public static final AnimationDefinition widle = AnimationDefinition.Builder.withLength(3.0F).looping()
    .addAnimation("Hipopotamo_Infernal", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    	new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -5.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 5.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(3.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -5.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("Hipopotamo_Infernal", new AnimationChannel(AnimationChannel.Targets.POSITION,
    	new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 2F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, 0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(2.25F, KeyframeAnimations.posVec(0.0F, -2F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(3.0F, KeyframeAnimations.posVec(0.0F, 0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.SCALE,
    	new Keyframe(1.5F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(1.8F, KeyframeAnimations.scaleVec(1.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(2.1F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    	new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(1.5F, KeyframeAnimations.degreeVec(7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(3.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("torso", new AnimationChannel(AnimationChannel.Targets.SCALE,
    	new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(0.35F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(2.0F, KeyframeAnimations.scaleVec(1.05F, 1.05F, 1.05F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(2.6F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(3.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_leg1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    	new Keyframe(0.0F, KeyframeAnimations.degreeVec(-10.57F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(0.35F, KeyframeAnimations.degreeVec(6.25F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(1.1F, KeyframeAnimations.degreeVec(32.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(2.6F, KeyframeAnimations.degreeVec(-20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(3.0F, KeyframeAnimations.degreeVec(-10.57F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_leg1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    	new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.25F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(0.75F, KeyframeAnimations.degreeVec(32.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(2.25F, KeyframeAnimations.degreeVec(-20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(3.0F, KeyframeAnimations.degreeVec(6.25F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    	new Keyframe(0.0F, KeyframeAnimations.degreeVec(15.25F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                  new Keyframe(0.35F, KeyframeAnimations.degreeVec(17.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                  new Keyframe(1.85F, KeyframeAnimations.degreeVec(-12.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                  new Keyframe(3.0F, KeyframeAnimations.degreeVec(15.25F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("right_calf", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    	new Keyframe(0.0F, KeyframeAnimations.degreeVec(33.28F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(0.35F, KeyframeAnimations.degreeVec(45.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(1.85F, KeyframeAnimations.degreeVec(-40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(3.0F, KeyframeAnimations.degreeVec(33.28F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    	new Keyframe(0.0F, KeyframeAnimations.degreeVec(17.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                  new Keyframe(1.5F, KeyframeAnimations.degreeVec(-12.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                  new Keyframe(3.0F, KeyframeAnimations.degreeVec(17.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("left_calf", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    	new Keyframe(0.0F, KeyframeAnimations.degreeVec(45.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(1.5F, KeyframeAnimations.degreeVec(-40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(3.0F, KeyframeAnimations.degreeVec(45.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    	new Keyframe(0.0F, KeyframeAnimations.degreeVec(5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(1.5F, KeyframeAnimations.degreeVec(-5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(3.0F, KeyframeAnimations.degreeVec(5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    	new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(0.75F, KeyframeAnimations.degreeVec(7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(2.25F, KeyframeAnimations.degreeVec(-5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                  new Keyframe(3.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .build();

          public static final AnimationDefinition swim = AnimationDefinition.Builder.withLength(1.15F).looping()
                  .addAnimation("Hipopotamo_Infernal", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.6F, KeyframeAnimations.degreeVec(27.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(19.67F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.25F, KeyframeAnimations.degreeVec(27.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.7F, KeyframeAnimations.degreeVec(-15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.3F, KeyframeAnimations.degreeVec(27.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-30.62F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.degreeVec(-40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.degreeVec(-30.62F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_leg2", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 3.71F, 0.7F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.posVec(0.0F, -2.0F, 0.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 3.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.05F, KeyframeAnimations.posVec(0.0F, 4.38F, 1.63F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.posVec(0.0F, 3.71F, 0.7F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-12.7F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.degreeVec(-30.62F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.3F, KeyframeAnimations.degreeVec(-40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.9F, KeyframeAnimations.degreeVec(20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.degreeVec(-12.7F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_leg2", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 4.38F, 1.63F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.posVec(0.0F, 3.71F, 0.7F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.3F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.6F, KeyframeAnimations.posVec(0.0F, -2.0F, 0.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.9F, KeyframeAnimations.posVec(0.0F, 0.0F, 3.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.posVec(0.0F, 4.38F, 1.63F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_leg1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.6F, KeyframeAnimations.degreeVec(20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.degreeVec(-40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_leg1", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.3F, KeyframeAnimations.posVec(0.0F, -2.0F, 0.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.6F, KeyframeAnimations.posVec(0.0F, 0.0F, 3.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.9F, KeyframeAnimations.posVec(0.0F, 4.38F, 1.63F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_leg1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-36.68F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.degreeVec(-40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.degreeVec(20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.degreeVec(-36.68F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_leg1", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 3.0F, 0.32F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.posVec(0.0F, -2.0F, 0.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.posVec(0.0F, 0.0F, 3.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.95F, KeyframeAnimations.posVec(0.0F, 4.38F, 1.63F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.posVec(0.0F, 3.0F, 0.32F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("tail", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(32.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(60.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.degreeVec(32.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("tail", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -3.0F, -1.0F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .addAnimation("tailend", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(2.86F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.2F, KeyframeAnimations.degreeVec(-12.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.95F, KeyframeAnimations.degreeVec(7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.degreeVec(2.86F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.55F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.degreeVec(-7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, -1.93F, -0.12F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.55F, KeyframeAnimations.posVec(0.0F, -3.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.8F, KeyframeAnimations.posVec(0.0F, -1.75F, 2.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.posVec(0.0F, -1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.SCALE,
                          new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.25F, KeyframeAnimations.scaleVec(1.0F, 0.4F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.8F, KeyframeAnimations.scaleVec(1.0F, 0.4F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("nose_hairs", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.55F, KeyframeAnimations.posVec(0.0F, -0.6F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.8F, KeyframeAnimations.posVec(0.0F, -0.6F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.05F, KeyframeAnimations.posVec(0.0F, 1.6F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("nose", new AnimationChannel(AnimationChannel.Targets.SCALE,
                          new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.55F, KeyframeAnimations.scaleVec(1.0F, 0.7F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.8F, KeyframeAnimations.scaleVec(1.0F, 0.7F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.05F, KeyframeAnimations.scaleVec(1.0F, 1.8F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(5.56F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.95F, KeyframeAnimations.degreeVec(7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.degreeVec(5.56F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("torso", new AnimationChannel(AnimationChannel.Targets.SCALE,
                          new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.05F, 1.05F, 1.05F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.6F, KeyframeAnimations.scaleVec(1.05F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.scaleVec(1.05F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.scaleVec(1.05F, 1.05F, 1.05F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_calf", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-2.43F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.degreeVec(-37.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5F, KeyframeAnimations.degreeVec(-37.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.9F, KeyframeAnimations.degreeVec(52.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.degreeVec(-2.43F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_calf", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.78F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.9F, KeyframeAnimations.posVec(0.0F, 0.0F, 2.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.78F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_calf", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-37.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.35F, KeyframeAnimations.degreeVec(-37.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(52.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.degreeVec(-37.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_calf", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.35F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 2.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .build();

          public static final AnimationDefinition shake = AnimationDefinition.Builder.withLength(3.5F)
                  .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.4167F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.7083F, KeyframeAnimations.degreeVec(17.8938F, -4.7593F, -1.5345F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.1667F, KeyframeAnimations.degreeVec(7.4305F, 14.8796F, 1.9181F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.4167F, KeyframeAnimations.degreeVec(-2.4786F, -0.3262F, -7.4929F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.7083F, KeyframeAnimations.degreeVec(4.9981F, -7.8192F, -7.1665F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.0F, KeyframeAnimations.degreeVec(8.358F, 7.2995F, -11.8003F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.7917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.9167F, KeyframeAnimations.degreeVec(-2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.0417F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5417F, KeyframeAnimations.degreeVec(-16.4326F, 6.6108F, -18.9182F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(-8.8309F, 5.3343F, 19.9008F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.0F, KeyframeAnimations.degreeVec(-9.6151F, -3.7275F, -9.9994F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.25F, KeyframeAnimations.degreeVec(-8.8309F, 5.3343F, 19.9008F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.degreeVec(-8.8309F, -5.3343F, -19.9008F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.75F, KeyframeAnimations.degreeVec(-8.8309F, 5.3343F, 19.9008F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.0F, KeyframeAnimations.degreeVec(-8.8309F, -5.3343F, -19.9008F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.25F, KeyframeAnimations.degreeVec(2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.5F, KeyframeAnimations.degreeVec(10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.7083F, KeyframeAnimations.degreeVec(-2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.8333F, KeyframeAnimations.degreeVec(5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.9583F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5417F, KeyframeAnimations.posVec(0.0F, -1.0F, -1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_ear", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.0833F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.625F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -10.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.8333F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 30.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.0833F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -30.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.3333F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 30.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5833F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -30.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.8333F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 30.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.0833F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -30.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.3333F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 30.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.5833F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -10.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.8333F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.0F, KeyframeAnimations.degreeVec(3.13F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.degreeVec(3.13F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.7083F, KeyframeAnimations.degreeVec(12.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.8333F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                          new Keyframe(2.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.7083F, KeyframeAnimations.posVec(0.0F, -1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.8333F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("tail", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                          new Keyframe(0.5417F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.7917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -2.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.0833F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 7.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.2917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -15.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 7.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.7083F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -15.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.9167F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 7.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.125F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -15.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.3333F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 7.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.5833F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -2.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.8333F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(4.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("tailend", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                          new Keyframe(0.5417F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.7917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -7.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.9583F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -17.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.1667F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 15.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.375F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -17.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5833F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 15.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.7917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -17.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 15.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.2083F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -17.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.4167F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 15.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.5833F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -7.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.8333F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(4.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.625F, KeyframeAnimations.degreeVec(-4.9811F, 0.4352F, 4.9811F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.875F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 2.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.0833F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.3333F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 2.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5417F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -2.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.7917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 5.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.0417F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.4167F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.625F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.3333F, KeyframeAnimations.posVec(0.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.4167F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.5833F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.SCALE,
                          new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4167F, KeyframeAnimations.scaleVec(1.0F, 1.25F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.625F, KeyframeAnimations.scaleVec(1.0F, 0.5F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.5F, KeyframeAnimations.scaleVec(1.0F, 0.5F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.7083F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.8333F, KeyframeAnimations.scaleVec(1.0F, 0.5F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.9583F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_ear", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.0833F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.625F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -10.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.8333F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 30.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.0833F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -30.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.3333F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 30.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5833F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -30.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.8333F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 30.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.0833F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -30.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.3333F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 30.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.5833F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -10.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.8333F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("torso", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5417F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -7.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 7.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.2083F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -7.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.4583F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 7.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.6667F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -7.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.9167F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 7.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.1667F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -7.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.7917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.9583F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -2.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.0417F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 2.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.125F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -0.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.2083F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.2917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .build();

              public static final AnimationDefinition sleep_preparing = AnimationDefinition.Builder.withLength(3.0F)
                      .addAnimation("Hipopotamo_Infernal", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.98F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.75F, KeyframeAnimations.degreeVec(-14.02F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.3F, KeyframeAnimations.degreeVec(-14.02F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(2.0F, KeyframeAnimations.degreeVec(20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(2.6F, KeyframeAnimations.degreeVec(20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(-2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.3F, KeyframeAnimations.degreeVec(-32.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(2.4F, KeyframeAnimations.degreeVec(-15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(1.3F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(2.4F, KeyframeAnimations.posVec(0.0F, -1.0F, -2.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.SCALE,
                              new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.3F, KeyframeAnimations.scaleVec(1.0F, 0.5F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(2.0F, KeyframeAnimations.scaleVec(1.0F, 0.5F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(2.6F, KeyframeAnimations.scaleVec(1.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("right_ear", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.3F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("left_ear", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.3F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(0.6F, KeyframeAnimations.degreeVec(45.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(0.7F, KeyframeAnimations.degreeVec(52.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(0.8F, KeyframeAnimations.degreeVec(45.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(0.9F, KeyframeAnimations.degreeVec(52.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.0F, KeyframeAnimations.degreeVec(45.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.1F, KeyframeAnimations.degreeVec(52.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.2F, KeyframeAnimations.degreeVec(45.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(0.6F, KeyframeAnimations.posVec(0.0F, -4.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.2F, KeyframeAnimations.posVec(0.0F, -4.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("tail", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.65F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 17.5F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.3F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -17.5F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(2.0F, KeyframeAnimations.degreeVec(23.7263F, 23.2456F, -12.37F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("tailend", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.15F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.8F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 27.5F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.45F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -27.5F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(2.15F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -50.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("right_leg1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.15F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.45F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.85F, KeyframeAnimations.degreeVec(-75.0F, -10.0F, 72.5F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("right_leg1", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.15F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.45F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.85F, KeyframeAnimations.posVec(0.0F, 0.0F, 5.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("left_leg1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.3F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.85F, KeyframeAnimations.degreeVec(-75.0F, 10.0F, -72.5F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("left_leg1", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.3F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.85F, KeyframeAnimations.posVec(0.0F, 0.0F, 5.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("right_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.3F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.7F, KeyframeAnimations.degreeVec(-52.5F, 0.0F, 27.5F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("right_leg2", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.3F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.7F, KeyframeAnimations.posVec(0.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("right_calf", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.3F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.5F, KeyframeAnimations.degreeVec(-58.75F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.7F, KeyframeAnimations.degreeVec(-42.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("left_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.15F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.45F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.65F, KeyframeAnimations.degreeVec(-23.6258F, -11.7842F, -38.777F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.85F, KeyframeAnimations.degreeVec(-52.5F, 0.0F, -27.5F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("left_leg2", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.15F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.45F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.65F, KeyframeAnimations.posVec(0.0F, 4.5F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.85F, KeyframeAnimations.posVec(0.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("left_calf", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.15F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.45F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.85F, KeyframeAnimations.degreeVec(-42.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.7F, KeyframeAnimations.degreeVec(-2.4976F, 0.109F, 2.4976F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.4F, KeyframeAnimations.degreeVec(-4.5426F, -0.4066F, -5.0227F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.65F, KeyframeAnimations.degreeVec(-7.4366F, -0.9762F, -7.4366F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.9F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(2.7F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(1.3F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.7F, KeyframeAnimations.posVec(0.0F, -17.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("legs", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(1.3F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.7F, KeyframeAnimations.posVec(0.0F, -17.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .build();
              public static final AnimationDefinition sleep = AnimationDefinition.Builder.withLength(2.0F).looping()
                      .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("troath", new AnimationChannel(AnimationChannel.Targets.SCALE,
                              new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.0F, KeyframeAnimations.scaleVec(1.1F, 1.1F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(2.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(-15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.5F, KeyframeAnimations.degreeVec(-17.5F, 0.0F, 5.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.0F, KeyframeAnimations.degreeVec(-20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.5F, KeyframeAnimations.degreeVec(-17.5F, 0.0F, -5.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(2.0F, KeyframeAnimations.degreeVec(-15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -1.0F, -2.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.SCALE,
                              new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("nose_hairs", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.6F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.85F, KeyframeAnimations.posVec(0.0F, 0.415F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.9F, KeyframeAnimations.posVec(0.0F, 1.015F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.815F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.1F, KeyframeAnimations.posVec(0.0F, 1.21F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.15F, KeyframeAnimations.posVec(0.0F, 0.815F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(2.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("nose", new AnimationChannel(AnimationChannel.Targets.SCALE,
                              new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.75F, KeyframeAnimations.scaleVec(1.0F, 1.3F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.85F, KeyframeAnimations.scaleVec(1.0F, 1.2F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.9F, KeyframeAnimations.scaleVec(1.0F, 1.5F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.0F, KeyframeAnimations.scaleVec(1.0F, 1.4F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.1F, KeyframeAnimations.scaleVec(1.0F, 1.6F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.15F, KeyframeAnimations.scaleVec(1.0F, 1.4F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(2.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.0F, KeyframeAnimations.degreeVec(2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("torso", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.5F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -0.5F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("torso", new AnimationChannel(AnimationChannel.Targets.SCALE,
                              new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.0F, KeyframeAnimations.scaleVec(1.05F, 1.05F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(2.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("left_leg1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(-75.0F, 10.0F, -72.5F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("left_leg1", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 5.0F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("left_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(-52.5F, 0.0F, -27.5F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("left_leg2", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("left_calf", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(-42.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("tail", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(23.7263F, 23.2456F, -12.37F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("tailend", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -50.0F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("right_leg1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(-75.0F, -10.0F, 72.5F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("right_leg1", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 5.0F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("right_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(-52.5F, 0.0F, 27.5F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("right_leg2", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("right_calf", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(-42.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -17.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("legs", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -17.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .build();

              public static final AnimationDefinition awakening = AnimationDefinition.Builder.withLength(1.5F)
                      .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.3F, KeyframeAnimations.degreeVec(-7.5F, 2.5F, -7.5F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.7F, KeyframeAnimations.degreeVec(-14.8242F, 1.6189F, 12.3964F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -17.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.65F, KeyframeAnimations.degreeVec(23.48F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.2F, KeyframeAnimations.degreeVec(0.98F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(-15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.5F, KeyframeAnimations.degreeVec(3.75F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.0F, KeyframeAnimations.degreeVec(-2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.5F, KeyframeAnimations.degreeVec(-2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -1.0F, -2.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.SCALE,
                              new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.7F, KeyframeAnimations.scaleVec(1.0F, 0.5F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.2F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.5F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("right_ear", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("left_ear", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.6F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("tail", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(23.7263F, 23.2456F, -12.37F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.45F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 17.5F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -17.5F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("tailend", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -50.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.6F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 27.5F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.15F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -27.5F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("right_leg1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(-75.0F, -10.0F, 72.5F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.3F, KeyframeAnimations.degreeVec(-74.0379F, -22.0503F, 69.035F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.75F, KeyframeAnimations.degreeVec(-20.3251F, -61.0821F, 17.3831F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.1F, KeyframeAnimations.degreeVec(10.0563F, -35.6744F, 5.987F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("right_leg1", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 5.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.3F, KeyframeAnimations.posVec(0.0F, 0.0F, 5.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 8.0F, -6.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.1F, KeyframeAnimations.posVec(0.0849F, 6.8421F, -3.6643F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("left_leg1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(-75.0F, 10.0F, -72.5F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.3F, KeyframeAnimations.degreeVec(-68.4109F, 48.9467F, -59.2286F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.6F, KeyframeAnimations.degreeVec(2.1749F, 61.0821F, -17.3831F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.35F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("left_leg1", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 5.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.45F, KeyframeAnimations.posVec(0.0F, 11.5F, -2.87F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.6F, KeyframeAnimations.posVec(0.0F, 8.0F, -6.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.35F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("right_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.15F, KeyframeAnimations.degreeVec(-52.5F, 0.0F, 27.5F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.5F, KeyframeAnimations.degreeVec(-6.2074F, 0.0F, 21.2761F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.8F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("right_leg2", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.15F, KeyframeAnimations.posVec(0.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 12.5F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.8F, KeyframeAnimations.posVec(0.0F, 6.58F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("right_calf", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.15F, KeyframeAnimations.degreeVec(-42.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.5F, KeyframeAnimations.degreeVec(-5.95F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("left_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(-52.5F, 0.0F, -27.5F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.35F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("left_leg2", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.65F, KeyframeAnimations.posVec(0.0F, 3.58F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.05F, KeyframeAnimations.posVec(-0.0825F, 2.5938F, 0.159F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.3F, KeyframeAnimations.posVec(-0.0498F, 2.0598F, 0.0988F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("left_calf", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                              new Keyframe(0.0F, KeyframeAnimations.degreeVec(-42.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.35F, KeyframeAnimations.degreeVec(32.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(0.7F, KeyframeAnimations.degreeVec(23.86F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                              new Keyframe(1.35F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                      ))
                      .addAnimation("left_calf", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(0.35F, KeyframeAnimations.posVec(0.0F, 4.217F, -2.6865F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(0.7F, KeyframeAnimations.posVec(0.0F, 4.0F, -2.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.35F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .addAnimation("legs", new AnimationChannel(AnimationChannel.Targets.POSITION,
                              new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -17.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                              new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
                      ))
                      .build();
          public static final AnimationDefinition bite = AnimationDefinition.Builder.withLength(1.125F)
                  .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.degreeVec(-7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.35F, KeyframeAnimations.degreeVec(5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.15F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.3F, KeyframeAnimations.degreeVec(-15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.degreeVec(7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.1F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.35F, KeyframeAnimations.degreeVec(-29.1018F, -11.3134F, -33.3441F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -42.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -42.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.6F, KeyframeAnimations.degreeVec(-17.5F, 0.0F, -42.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.SCALE,
                          new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.35F, KeyframeAnimations.scaleVec(1.0F, 0.775F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.scaleVec(1.0F, 0.775F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.55F, KeyframeAnimations.scaleVec(1.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.15F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.degreeVec(72.782F, 2.3242F, -10.3361F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5F, KeyframeAnimations.degreeVec(0.5409F, -0.0592F, 12.4879F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.1F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.35F, KeyframeAnimations.posVec(0.0F, -4.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.posVec(0.0F, -4.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("torso", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.55F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -5.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 5.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.8F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -5.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.9F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .build();

          public static final AnimationDefinition attack = AnimationDefinition.Builder.withLength(1.0F)
                  .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.3F, KeyframeAnimations.degreeVec(2.8108F, 17.1932F, 13.0442F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.degreeVec(7.8281F, -14.676F, -13.1419F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.3F, KeyframeAnimations.degreeVec(-4.8969F, 17.2579F, 10.1246F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.degreeVec(-4.8442F, -15.1309F, 12.8479F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.55F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.15F, KeyframeAnimations.degreeVec(2.6678F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.3F, KeyframeAnimations.degreeVec(-6.8576F, 48.0562F, 26.1976F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.degreeVec(10.2851F, -55.3502F, -25.9059F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.7F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.SCALE,
                          new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.3F, KeyframeAnimations.scaleVec(1.0F, 0.5F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.scaleVec(1.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.scaleVec(1.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.3F, KeyframeAnimations.degreeVec(10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.degreeVec(35.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.55F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.posVec(0.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.6F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("torso", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.05F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.35F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 12.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -12.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.6F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 15.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.8F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("tail", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.3F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 15.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -57.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 32.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.8F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("tailend", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.15F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.4F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 15.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.6F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -57.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 32.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.9F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("front_legs", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.degreeVec(0.0F, -25.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("front_legs", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.3F, KeyframeAnimations.posVec(-11.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.posVec(10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_leg1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.3F, KeyframeAnimations.degreeVec(0.0F, -15.0F, -12.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.degreeVec(0.0F, 7.5F, 32.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_leg1", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.posVec(-6.0F, -3.0F, 4.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_leg1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.3F, KeyframeAnimations.degreeVec(0.0F, 32.5F, -37.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 22.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.6F, KeyframeAnimations.degreeVec(0.4924F, -2.2201F, 4.9394F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.7F, KeyframeAnimations.degreeVec(0.0001F, -1.2792F, 13.8752F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.85F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_leg1", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.3F, KeyframeAnimations.posVec(2.0F, -4.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.posVec(5.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.posVec(3.15F, 3.96F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.85F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.05F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.35F, KeyframeAnimations.degreeVec(0.0F, 22.5F, -7.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 10.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_leg2", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.05F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.35F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_calf", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.05F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.35F, KeyframeAnimations.degreeVec(-10.2258F, -17.7493F, 20.4462F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -22.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("right_calf", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.05F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.35F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, -1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.3F, KeyframeAnimations.degreeVec(0.0F, 22.5F, -7.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 10.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_leg2", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.3F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.posVec(0.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_calf", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.3F, KeyframeAnimations.degreeVec(-10.2258F, -17.7493F, 20.4462F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -22.5F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("left_calf", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.3F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.posVec(0.0F, -1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .build();

          public static final AnimationDefinition eat = AnimationDefinition.Builder.withLength(3.5F)
                  .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.degreeVec(37.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.75F, KeyframeAnimations.degreeVec(37.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.15F, KeyframeAnimations.degreeVec(-7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.45F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.degreeVec(10.2928F, 7.1314F, -34.3569F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.0F, KeyframeAnimations.degreeVec(-4.7072F, 7.1314F, -34.3569F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.35F, KeyframeAnimations.degreeVec(10.2928F, 7.1314F, -34.3569F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.6F, KeyframeAnimations.degreeVec(-4.7072F, 7.1314F, -34.3569F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.9F, KeyframeAnimations.degreeVec(10.2928F, 7.1314F, -34.3569F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.25F, KeyframeAnimations.degreeVec(-4.7072F, 7.1314F, -34.3569F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.6F, KeyframeAnimations.degreeVec(-4.7072F, 7.1314F, -34.3569F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.0F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.2F, KeyframeAnimations.degreeVec(-7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.1F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.15F, KeyframeAnimations.posVec(0.0F, 0.0F, -2.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.75F, KeyframeAnimations.posVec(0.0F, 0.0F, -2.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.1F, KeyframeAnimations.posVec(0.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.4F, KeyframeAnimations.posVec(0.0F, 0.0F, -2.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.1F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.SCALE,
                          new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.9F, KeyframeAnimations.scaleVec(1.0F, 0.4F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.85F, KeyframeAnimations.scaleVec(1.0F, 0.4F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.15F, KeyframeAnimations.scaleVec(1.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.5F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.0F, KeyframeAnimations.degreeVec(50.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.35F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.6F, KeyframeAnimations.degreeVec(50.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.9F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.25F, KeyframeAnimations.degreeVec(50.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.6F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(3.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("torso", new AnimationChannel(AnimationChannel.Targets.SCALE,
                          new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.9F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.35F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.5F, KeyframeAnimations.scaleVec(1.025F, 1.025F, 1.025F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.65F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.9F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.1F, KeyframeAnimations.scaleVec(1.025F, 1.025F, 1.025F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.25F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.5F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.65F, KeyframeAnimations.scaleVec(1.025F, 1.025F, 1.025F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.85F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .build();

          public static final AnimationDefinition intimidate_in = AnimationDefinition.Builder.withLength(0.65F)
                  .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.35F, KeyframeAnimations.degreeVec(-10F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.degreeVec(-10F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.2F, KeyframeAnimations.degreeVec(-7.5F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.55F, KeyframeAnimations.degreeVec(10F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.degreeVec(10F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.2F, KeyframeAnimations.posVec(0F, 4F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.posVec(0F, 2F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.posVec(0F, 2F, 0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                          new Keyframe(0.35F, KeyframeAnimations.degreeVec(-15F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                          new Keyframe(0.65F, KeyframeAnimations.degreeVec(2.4905F, -0.2178F, 4.9953F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.SCALE,
                          new Keyframe(0.0F, KeyframeAnimations.scaleVec(1F, 1F, 1F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.25F, KeyframeAnimations.scaleVec(1F, 0.6F, 1F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.scaleVec(1F, 0.6F, 1F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                          new Keyframe(0.3F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                          new Keyframe(0.5F, KeyframeAnimations.degreeVec(35F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.degreeVec(35F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.5F, KeyframeAnimations.posVec(0F, -4F, 0F), AnimationChannel.Interpolations.LINEAR),
                          new Keyframe(0.65F, KeyframeAnimations.posVec(0F, -4F, 0F), AnimationChannel.Interpolations.LINEAR)
                  ))
                  .build();

          public static final AnimationDefinition intimidate_loop = AnimationDefinition.Builder.withLength(2.0F).looping()
                  .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-10F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.0F, KeyframeAnimations.degreeVec(-10F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.0F, KeyframeAnimations.posVec(0F, 1F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(10F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.0F, KeyframeAnimations.degreeVec(17.5F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.0F, KeyframeAnimations.degreeVec(10F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0F, 2F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.0F, KeyframeAnimations.posVec(0F, 2F, 0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(2.4905F, -0.2178F, 4.9953F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.0F, KeyframeAnimations.degreeVec(2.4905F, 0.2178F, -4.9953F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.0F, KeyframeAnimations.degreeVec(2.4905F, -0.2178F, 4.9953F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.SCALE,
                          new Keyframe(0.0F, KeyframeAnimations.scaleVec(1F, 0.6F, 1F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.0F, KeyframeAnimations.scaleVec(1F, 0.6F, 1F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(35F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.0F, KeyframeAnimations.degreeVec(45F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.0F, KeyframeAnimations.degreeVec(35F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0F, -4F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.0F, KeyframeAnimations.posVec(0F, -4F, 0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("torso", new AnimationChannel(AnimationChannel.Targets.SCALE,
                          new Keyframe(0.0F, KeyframeAnimations.scaleVec(1F, 1F, 1F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.25F, KeyframeAnimations.scaleVec(1.05F, 1.05F, 1.05F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(1.7F, KeyframeAnimations.scaleVec(1F, 1F, 1F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(2.0F, KeyframeAnimations.scaleVec(1F, 1F, 1F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .build();

          public static final AnimationDefinition intimidate_out = AnimationDefinition.Builder.withLength(0.95F)
                  .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(-10F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.35F, KeyframeAnimations.degreeVec(5F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.85F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.95F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(10F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.95F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0F, 2F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.95F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(2.4905F, -0.2178F, 4.9953F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.95F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.SCALE,
                          new Keyframe(0.0F, KeyframeAnimations.scaleVec(1F, 0.6F, 1F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.35F, KeyframeAnimations.scaleVec(1F, 0F, 1F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.65F, KeyframeAnimations.scaleVec(1F, 1F, 1F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.95F, KeyframeAnimations.scaleVec(1F, 1F, 1F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                          new Keyframe(0.0F, KeyframeAnimations.degreeVec(35F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.degreeVec(35F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.95F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.POSITION,
                          new Keyframe(0.0F, KeyframeAnimations.posVec(0F, -4F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.45F, KeyframeAnimations.posVec(0F, -4F, 0F), AnimationChannel.Interpolations.CATMULLROM),
                          new Keyframe(0.95F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM)
                  ))
                  .build();

        public static final AnimationDefinition death = AnimationDefinition.Builder.withLength(2.0F)
                .addAnimation("Hipopotamo_Infernal", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                        new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 20.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -82.5F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.7F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -82.5F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("Hipopotamo_Infernal", new AnimationChannel(AnimationChannel.Targets.POSITION,
                        new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 7.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.7F, KeyframeAnimations.posVec(0.0F, 7.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                        new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("neck", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                        new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.25F, KeyframeAnimations.degreeVec(-2.613F, -9.6559F, 15.2207F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.5F, KeyframeAnimations.degreeVec(-17.5F, -27.5F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.6F, KeyframeAnimations.degreeVec(-15.474F, 1.3232F, -8.6497F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.7F, KeyframeAnimations.degreeVec(-15.648F, 8.5491F, -10.6678F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.85F, KeyframeAnimations.degreeVec(-15.4993F, -3.4955F, -7.3148F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.0F, KeyframeAnimations.degreeVec(-15.648F, 8.5491F, -10.6678F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.2F, KeyframeAnimations.degreeVec(-15.648F, 8.5491F, -10.6678F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                        new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.25F, KeyframeAnimations.degreeVec(-3.9671F, -14.4775F, 15.5041F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.5F, KeyframeAnimations.degreeVec(-4.2201F, -24.4522F, 16.2603F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.7F, KeyframeAnimations.degreeVec(-3.8433F, -2.0063F, 14.6456F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.9F, KeyframeAnimations.degreeVec(-3.9267F, -11.9834F, 15.3273F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.1F, KeyframeAnimations.degreeVec(-3.8433F, -2.0063F, 14.6456F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.35F, KeyframeAnimations.degreeVec(-3.8433F, -2.0063F, 14.6456F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                        new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.25F, KeyframeAnimations.degreeVec(22.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.7F, KeyframeAnimations.degreeVec(18.7423F, -12.6796F, 32.8991F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.9F, KeyframeAnimations.degreeVec(32.7454F, -7.3711F, 24.2894F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.1F, KeyframeAnimations.degreeVec(18.7423F, -12.6796F, 32.8991F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.35F, KeyframeAnimations.degreeVec(18.7423F, -12.6796F, 32.8991F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("lower_jaw", new AnimationChannel(AnimationChannel.Targets.POSITION,
                        new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.25F, KeyframeAnimations.posVec(1.1371F, -1.641F, -0.1188F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("torso", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                        new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.3F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 12.5F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.6F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -12.5F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.8F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 12.5F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -12.5F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.2F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 5.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.4F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -12.5F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.65F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -12.5F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("torso", new AnimationChannel(AnimationChannel.Targets.POSITION,
                        new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.55F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.75F, KeyframeAnimations.posVec(1.9526F, 0.4329F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.95F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.15F, KeyframeAnimations.posVec(1.1F, 0.24F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.35F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.6F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("tail", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                        new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -25.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.55F, KeyframeAnimations.degreeVec(10.0F, 0.0F, -30.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.7F, KeyframeAnimations.degreeVec(24.2933F, -7.3556F, 17.0628F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.9F, KeyframeAnimations.degreeVec(25.7721F, -4.5198F, 12.7199F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.05F, KeyframeAnimations.degreeVec(24.2933F, -7.3556F, 17.0628F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.45F, KeyframeAnimations.degreeVec(24.2933F, -7.3556F, 17.0628F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("tailend", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                        new Keyframe(0.05F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.3F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -25.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.6F, KeyframeAnimations.degreeVec(10.0F, 0.0F, -30.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.75F, KeyframeAnimations.degreeVec(25.3177F, 0.034F, 1.1557F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.95F, KeyframeAnimations.degreeVec(23.4914F, 11.7622F, -22.2637F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.1F, KeyframeAnimations.degreeVec(25.3177F, 0.034F, 1.1557F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.5F, KeyframeAnimations.degreeVec(25.3177F, 0.034F, 1.1557F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("right_leg1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                        new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.25F, KeyframeAnimations.degreeVec(-18.6344F, 0.5227F, 27.6354F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.5F, KeyframeAnimations.degreeVec(-16.8502F, -8.363F, -14.6664F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.7F, KeyframeAnimations.degreeVec(4.9485F, -4.9492F, -10.0607F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.9F, KeyframeAnimations.degreeVec(5.3603F, -4.4997F, -15.0575F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.05F, KeyframeAnimations.degreeVec(4.9485F, -4.9492F, -10.0607F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.5F, KeyframeAnimations.degreeVec(4.9485F, -4.9492F, -10.0607F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("right_leg1", new AnimationChannel(AnimationChannel.Targets.POSITION,
                        new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, 0.5F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.5F, KeyframeAnimations.posVec(1.7746F, -5.8181F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("left_leg1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                        new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 7.5F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -35.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.65F, KeyframeAnimations.degreeVec(-19.8423F, 2.5587F, 39.5524F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.85F, KeyframeAnimations.degreeVec(-20.0F, 0.0F, 32.5F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.0F, KeyframeAnimations.degreeVec(-19.8423F, 2.5587F, 39.5524F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.45F, KeyframeAnimations.degreeVec(-19.8423F, 2.5587F, 39.5524F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("left_leg1", new AnimationChannel(AnimationChannel.Targets.POSITION,
                        new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.25F, KeyframeAnimations.posVec(5.2961F, 2.9918F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.65F, KeyframeAnimations.posVec(0.5331F, -5.9763F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("right_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                        new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -7.5F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -17.5F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.65F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -7.5F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.85F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -12.5F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -7.5F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.45F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -7.5F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("right_leg2", new AnimationChannel(AnimationChannel.Targets.POSITION,
                        new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.5F, KeyframeAnimations.posVec(1.5035F, -4.7686F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.65F, KeyframeAnimations.posVec(3.4074F, -4.1686F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.0F, KeyframeAnimations.posVec(3.4074F, -4.1686F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.45F, KeyframeAnimations.posVec(3.4074F, -4.1686F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("right_calf", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                        new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.25F, KeyframeAnimations.degreeVec(7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.55F, KeyframeAnimations.degreeVec(7.3873F, 1.2988F, -9.9162F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.7F, KeyframeAnimations.degreeVec(7.3873F, -1.2988F, 9.9162F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.9F, KeyframeAnimations.degreeVec(7.3242F, 1.6189F, -12.3964F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.05F, KeyframeAnimations.degreeVec(7.3873F, -1.2988F, 9.9162F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.5F, KeyframeAnimations.degreeVec(7.3873F, -1.2988F, 9.9162F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("right_calf", new AnimationChannel(AnimationChannel.Targets.POSITION,
                        new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, -3.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.55F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.7F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.05F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("left_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                        new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.2F, KeyframeAnimations.degreeVec(0.0F, -17.5F, -13.75F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.3F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -35.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.55F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -35.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.7F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 10.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.85F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 10.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.45F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 10.0F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("left_leg2", new AnimationChannel(AnimationChannel.Targets.POSITION,
                        new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.2F, KeyframeAnimations.posVec(-1.4107F, 4.7754F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.3F, KeyframeAnimations.posVec(-0.9235F, 1.774F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.55F, KeyframeAnimations.posVec(-0.9235F, 1.774F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("left_calf", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                        new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.35F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 22.5F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.9F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -2.5F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.05F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 22.5F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 22.5F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .addAnimation("left_calf", new AnimationChannel(AnimationChannel.Targets.POSITION,
                        new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.35F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
                ))
                .build();

    private HellHippoAnimations() {}
}

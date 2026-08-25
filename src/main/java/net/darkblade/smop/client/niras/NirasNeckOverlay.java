package net.darkblade.smop.client.niras;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NirasNeckOverlay {

    private static final Set<String> NECK_SUBTREE = Set.of(
            "gNeck", "gTroath", "gHead",
            "gUpperjaw", "gLowerjaw",
            "GEyes", "gEyes",
            "gChin", "gTongue",
            "gLeft_saliva", "gRight_saliva");

    public static final AnimationDefinition WATER_BITE = onlyNeck(NirasWaterAnimations.wbite);
    public static final AnimationDefinition BABY_WATER_BITE = onlyNeck(NirasBabyWaterAnimations.w_bite);

    private static AnimationDefinition onlyNeck(AnimationDefinition source) {
        AnimationDefinition.Builder builder = AnimationDefinition.Builder.withLength(source.lengthInSeconds());
        if (source.looping()) {
            builder.looping();
        }
        for (Map.Entry<String, List<AnimationChannel>> bone : source.boneAnimations().entrySet()) {
            if (!NECK_SUBTREE.contains(bone.getKey())) {
                continue;
            }
            for (AnimationChannel channel : bone.getValue()) {
                builder.addAnimation(bone.getKey(), channel);
            }
        }
        return builder.build();
    }

    private NirasNeckOverlay() {}
}

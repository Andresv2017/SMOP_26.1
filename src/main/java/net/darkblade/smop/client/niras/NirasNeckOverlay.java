package net.darkblade.smop.client.niras;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The water bite, reduced to the neck.
 *
 * <p><b>Why a derived clip rather than a re-export.</b> The authored {@code wbite} animates the whole
 * animal: neck and jaws, but also the chest, the tail, the fin and all four flippers. Played as a
 * layer-0 clip that is correct — it owns the frame. Played as an <b>overlay</b> on top of the swim
 * cycle it is not: the body channels would fight the cycle underneath, and the animal would strike
 * with a tail that stutters between two sources. Keeping only the {@code gNeck} subtree leaves the
 * strike to the front end and lets the swim cycle keep driving everything behind it, which is what a
 * real animal does — it does not stop swimming to bite.
 *
 * <p><b>The subtree, not the single bone.</b> {@code gNeck} is a group: {@code gTroath} and
 * {@code gHead} hang off it, the two jaws off the head, and the eyes, chin, tongue and saliva planes
 * off those. Keeping the bone alone would swing a neck with its mouth shut.
 *
 * <p><b>Both rigs in one set.</b> The calf names its eye bone {@code gEyes} where the adult uses
 * {@code GEyes}, and has no saliva planes at all. Listing every name from both is harmless: a filter
 * only ever removes, so a name the source clip does not carry costs nothing.
 *
 * <p><b>Client-only, and reached only through a lambda.</b> {@link AnimationDefinition} is
 * {@code @OnlyIn(Dist.CLIENT)}; this class is touched exclusively from the suppliers inside
 * {@code NirasmosaurusEntity.registerAnimations}, which the server builds but never invokes — the
 * same arrangement every other clip reference in that method relies on.
 */
public final class NirasNeckOverlay {

    /** Every bone at or under {@code gNeck}, across both rigs. */
    private static final Set<String> NECK_SUBTREE = Set.of(
            "gNeck", "gTroath", "gHead",
            "gUpperjaw", "gLowerjaw",
            "GEyes", "gEyes",
            "gChin", "gTongue",
            "gLeft_saliva", "gRight_saliva");

    public static final AnimationDefinition WATER_BITE = onlyNeck(NirasWaterAnimations.wbite);
    public static final AnimationDefinition BABY_WATER_BITE = onlyNeck(NirasBabyWaterAnimations.w_bite);

    /**
     * Copies the clip, dropping every channel outside the neck.
     *
     * <p>The declared length is carried over untouched, and deliberately: this clip is additive now,
     * so it has to run all the way to the frame where its channels reach neutral or the blend-out has
     * to eat whatever offset is left. In {@code wbite} the last of those is {@code gHead}, which is
     * still 22.5 degrees off at 0.9 s and only lands on zero at 1.2. Trimming it would create the pop
     * that trimming is normally there to remove.
     */
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

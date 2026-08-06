package net.darkblade.smop.entity.krifto;

import net.darkblade.deluxelib.entity.perch.PerchPlacement;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Where a tamed Kriftognathus sits while carrying its owner: directly above their head, wings out,
 * as a parachute rather than the falconry "bird on the arm" the perch system was first built for.
 *
 * <p>Common-side and free of client imports for the same reason {@code OwlPerchPlacement} is — the
 * server reads {@code side}/{@code height}/{@code forward} to place the hitbox while the client reads
 * the same three to draw it, and keeping one copy is what stops the two from silently disagreeing.
 *
 * <p><b>The arm rotations below are unused.</b> {@code KriftognathusRenderer} implements
 * {@code PerchPoseHandler}, which replaces the library's single-arm raise with the full two-armed
 * {@code KriftoRiderPose} — the record still needs the three values, so they are left at the neutral
 * hanging-arm zero rather than a falconry angle nothing will read.
 */
public final class KriftoPerchPlacement {

    /** Overhead and a touch forward, dialled in live with {@code /smop debug kriftoperch}. */
    static final float PERCH_SIDE = 0.000F;
    static final float PERCH_HEIGHT = 3.260F;
    static final float PERCH_FORWARD = 0.240F;

    /** Unused — see the class note. */
    static final float ARM_X_ROT = 0.0F;
    static final float ARM_Y_ROT = 0.0F;
    static final float ARM_Z_ROT = 0.0F;

    /**
     * First-person values are unused too: {@code KriftognathusEntity#renderPerchedInFirstPerson()}
     * returns false, because the first-person chain can only anchor to the hand and this creature is
     * over the head — where you would not see it from inside your own eyes anyway.
     */
    static final float FP_X = 0.0F;
    static final float FP_Y = 0.0F;
    static final float FP_Z = 0.0F;
    static final float FP_X_ROT = 0.0F;
    static final float FP_Y_ROT = 0.0F;
    static final float FP_Z_ROT = 0.0F;
    static final float FP_SCALE = 1.0F;

    /** Drawing the entity inside its host's render pass skips {@code setupRotations}, so the
     *  renderer's own shrink never runs on that path — the scale rides the placement instead. */
    public static final float MODEL_SCALE = 1.0F;

    private static final PerchPlacement COMPILED = new PerchPlacement(
            PERCH_SIDE, PERCH_HEIGHT, PERCH_FORWARD,
            ARM_X_ROT, ARM_Y_ROT, ARM_Z_ROT,
            FP_X, FP_Y, FP_Z,
            FP_X_ROT, FP_Y_ROT, FP_Z_ROT,
            MODEL_SCALE, FP_SCALE);

    /** Set by the client-only tuner while it is active; {@code null} otherwise. {@code volatile}
     *  because the render thread reads it while the tuner's key handler writes it. */
    private static volatile @Nullable Supplier<PerchPlacement> override;

    /**
     * Installs (or clears, with {@code null}) a live override of the compiled values — the hook the
     * client-side tuner feeds its in-progress numbers through, so this common class never has to name
     * a client class a dedicated server would fail to load. Nothing else should call this.
     */
    public static void setOverride(@Nullable Supplier<PerchPlacement> supplier) {
        override = supplier;
    }

    /** The placement to use right now: the tuner's live values during a debug session, the compiled
     *  constants otherwise. */
    public static PerchPlacement current() {
        Supplier<PerchPlacement> supplier = override;
        return supplier != null ? supplier.get() : COMPILED;
    }

    /** The compiled values, ignoring any live override — what the tuner seeds itself from and what
     *  {@code .} resets it back to. */
    public static PerchPlacement compiled() {
        return COMPILED;
    }

    private KriftoPerchPlacement() {}
}

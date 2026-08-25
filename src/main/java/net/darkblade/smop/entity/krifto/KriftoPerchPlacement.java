package net.darkblade.smop.entity.krifto;

import net.darkblade.deluxelib.entity.perch.PerchPlacement;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class KriftoPerchPlacement {

    static final float PERCH_SIDE = 0.000F;
    static final float PERCH_HEIGHT = 3.260F;
    static final float PERCH_FORWARD = 0.240F;

    static final float ARM_X_ROT = 0.0F;
    static final float ARM_Y_ROT = 0.0F;
    static final float ARM_Z_ROT = 0.0F;

    static final float FP_X = 0.0F;
    static final float FP_Y = 0.0F;
    static final float FP_Z = 0.0F;
    static final float FP_X_ROT = 0.0F;
    static final float FP_Y_ROT = 0.0F;
    static final float FP_Z_ROT = 0.0F;
    static final float FP_SCALE = 1.0F;

    public static final float MODEL_SCALE = 1.0F;

    private static final PerchPlacement COMPILED = new PerchPlacement(
            PERCH_SIDE, PERCH_HEIGHT, PERCH_FORWARD,
            ARM_X_ROT, ARM_Y_ROT, ARM_Z_ROT,
            FP_X, FP_Y, FP_Z,
            FP_X_ROT, FP_Y_ROT, FP_Z_ROT,
            MODEL_SCALE, FP_SCALE);

    private static volatile @Nullable Supplier<PerchPlacement> override;

    public static void setOverride(@Nullable Supplier<PerchPlacement> supplier) {
        override = supplier;
    }

    public static PerchPlacement current() {
        Supplier<PerchPlacement> supplier = override;
        return supplier != null ? supplier.get() : COMPILED;
    }

    public static PerchPlacement compiled() {
        return COMPILED;
    }

    private KriftoPerchPlacement() {}
}

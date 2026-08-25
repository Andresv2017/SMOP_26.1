package net.darkblade.smop.entity.rider;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public final class RiderSteering {

    private RiderSteering() {}

    private static final float STRAFE_SCALE = 0.5F;

    private static final float REVERSE_SCALE = 0.25F;

    private static final float PITCH_SCALE = 0.5F;

    public static @NotNull Vec3 riddenInput(@NotNull Player controller) {
        float sideways = controller.xxa * STRAFE_SCALE;
        float forward = controller.zza;
        if (forward <= 0.0F) {
            forward *= REVERSE_SCALE;
        }
        return new Vec3(sideways, 0.0D, forward);
    }

    public static @NotNull Vec2 riddenRotation(@NotNull Player controller) {
        return new Vec2(controller.getXRot() * PITCH_SCALE, controller.getYRot());
    }
}

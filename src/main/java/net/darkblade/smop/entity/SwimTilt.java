package net.darkblade.smop.entity;

/**
 * A swimmer that banks and noses up and down — body tilt on top of the animation, in degrees.
 *
 * <p>Separate from {@link SMOPWaterAnimal} because it is optional and cosmetic: the tilt is computed
 * on the entity but only ever drawn, so a swimmer that does not implement this still swims correctly,
 * it just stays level. The Nirasmosaurus has it; the salmon does not yet.
 *
 * <p>It exists as a type at all so tooling can ask without knowing the species — see
 * {@code SMOPSwimDebug}, which reads it off whatever aquatic mob it happens to be watching.
 */
public interface SwimTilt {

    /** Nose up/down. Positive is nose-down, matching the renderer's {@code Axis.XP} rotation. */
    float swimPitch();

    /** Bank. Fed by the yaw RATE, not the yaw, so a steady heading holds no lean. */
    float swimRoll();
}

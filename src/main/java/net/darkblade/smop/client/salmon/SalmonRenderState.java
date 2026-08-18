package net.darkblade.smop.client.salmon;

import net.darkblade.deluxelib.client.render.DeluxeEntityRenderState;

/**
 * Carries the sex across to the renderer, which picks the texture from it.
 *
 * <p>26.1's {@code getTextureLocation} receives the render state rather than the entity, so
 * anything the texture depends on has to be extracted onto the state first — the same reason the
 * Tangoftero's state carries its coat variant.
 */
public class SalmonRenderState extends DeluxeEntityRenderState {

    public boolean male;

    /** Body tilt in degrees, already interpolated against the previous tick. @see SalmonRenderer */
    public float swimPitch;
    public float swimRoll;
}

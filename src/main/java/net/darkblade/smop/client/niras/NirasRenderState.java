package net.darkblade.smop.client.niras;

import net.darkblade.deluxelib.client.render.DeluxeEntityRenderState;

/**
 * Carries what the Nirasmosaurus' texture choice depends on.
 *
 * <p>26.1's {@code getTextureLocation} receives the render state rather than the entity, so anything
 * the coat is picked from has to be extracted onto the state first. Today that is the sex; the saddle
 * variants join it in phase 2, and the {@code sup_dude} morph in phase 3.
 */
public class NirasRenderState extends DeluxeEntityRenderState {

    public boolean male;

    /** Body tilt, already interpolated. @see NirasRenderer#setupRotations */
    public float swimPitch;
    public float swimRoll;
}

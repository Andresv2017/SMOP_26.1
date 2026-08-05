package net.darkblade.smop.client.tangoftero;

import net.darkblade.deluxelib.client.render.DeluxeEntityRenderState;

/**
 * Carries the coat variant across to the renderer.
 *
 * <p>26.1's {@code getTextureLocation} receives the render state, not the entity, so anything the
 * texture choice depends on has to be extracted onto the state first — the same pattern DeluxeLib
 * uses for its {@code armored} flag.
 */
public class TangofteroRenderState extends DeluxeEntityRenderState {

    public int variantId;
}

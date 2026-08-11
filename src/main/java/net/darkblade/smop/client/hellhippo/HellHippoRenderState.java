package net.darkblade.smop.client.hellhippo;

import net.darkblade.deluxelib.client.render.DeluxeEntityRenderState;

/**
 * Carries what the Hell Hippo's texture choice depends on.
 *
 * <p>26.1's {@code getTextureLocation} receives the render state rather than the entity, so anything
 * the coat is picked from has to be extracted onto the state first. Today that is just the sex;
 * saddle, armour, chest and seaweed join it as the phases that introduce them land.
 */
public class HellHippoRenderState extends DeluxeEntityRenderState {

    public boolean male;
}

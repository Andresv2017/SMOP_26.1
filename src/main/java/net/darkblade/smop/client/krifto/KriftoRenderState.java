package net.darkblade.smop.client.krifto;

import net.darkblade.deluxelib.client.render.DeluxeEntityRenderState;

/**
 * Carries the two things the Krifto's texture depends on — its sex and the biome it hatched in.
 *
 * <p>26.1's {@code getTextureLocation} receives the render state rather than the entity, so anything
 * the texture choice reads has to be extracted onto the state first.
 */
public class KriftoRenderState extends DeluxeEntityRenderState {

    public boolean male;
    public String spawnBiome = "default";
}
